/*
 *    This file is part of the LOD Mod, licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.seibel.lod.handlers;

import com.seibel.lod.objects.*;
import com.seibel.lod.proxy.ClientProxy;
import com.seibel.lod.util.LodThreadFactory;
import com.seibel.lod.util.LodUtil;
import net.minecraft.util.FileUtil;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This object handles creating LodRegions
 * from files and saving LodRegion objects
 * to file.
 *
 * @author James Seibel
 * @version 8-14-2021
 */
public class LodDimensionFileHandler
{
    /**
     * This is what separates each piece of data
     */
    public static final char DATA_DELIMITER = ',';


    private LodDimension loadedDimension = null;
    public long regionLastWriteTime[][];

    private File dimensionDataSaveFolder;

    /**
     * lod
     */
    private static final String FILE_NAME_PREFIX = "lod";
    /**
     * .txt
     */
    private static final String FILE_EXTENSION = ".txt";
    /**
     * .tmp <br>
     * Added to the end of the file path when saving to prevent
     * nulling a currently existing file. <br>
     * After the file finishes saving it will end with
     * FILE_EXTENSION.
     */
    private static final String TMP_FILE_EXTENSION = ".tmp";

    /**
     * This is the file version currently accepted by this
     * file handler, older versions (smaller numbers) will be deleted and overwritten,
     * newer versions (larger numbers) will be ignored and won't be read.
     */
    public static final int LOD_SAVE_FILE_VERSION = 4;

    /**
     * This is the string written before the file version
     */
    private static final String LOD_FILE_VERSION_PREFIX = "lod_save_file_version";

    /**
     * Allow saving asynchronously, but never try to save multiple regions
     * at a time
     */
    private ExecutorService fileWritingThreadPool = Executors.newSingleThreadExecutor(new LodThreadFactory(this.getClass().getSimpleName()));


    public LodDimensionFileHandler(File newSaveFolder, LodDimension newLoadedDimension)
    {
        if (newSaveFolder == null)
            throw new IllegalArgumentException("LodDimensionFileHandler requires a valid File location to read and write to.");

        dimensionDataSaveFolder = newSaveFolder;

        loadedDimension = newLoadedDimension;
        // these two variable are used in sync with the LodDimension
        regionLastWriteTime = new long[loadedDimension.getWidth()][loadedDimension.getWidth()];
        for (int i = 0; i < loadedDimension.getWidth(); i++)
            for (int j = 0; j < loadedDimension.getWidth(); j++)
                regionLastWriteTime[i][j] = -1;
    }


    //================//
    // read from file //
    //================//


    /**
     * Return the LodRegion region at the given coordinates.
     * (null if the file doesn't exist)
     */
    public LodRegion loadRegionFromFile(RegionPos regionPos)
    {
        int regionX = regionPos.x;
        int regionZ = regionPos.z;
        String fileName = getFileNameAndPathForRegion(regionX, regionZ);

        File f = new File(fileName);

        if (!f.exists())
        {
            // there wasn't a file, don't
            // return anything
            return null;
        }
        String data = "";
        try
        {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(f));
            data = bufferedReader.readLine();
            int fileVersion = -1;

            if (data != null && !data.isEmpty())
            {
                // try to get the file version
                try
                {
                    fileVersion = Integer.parseInt(data.substring(data.indexOf(' ')).trim());
                } catch (NumberFormatException | StringIndexOutOfBoundsException e)
                {
                    // this file doesn't have a version
                    // keep the version as -1
                    fileVersion = -1;
                }

                // check if this file can be read by this file handler
                if (fileVersion < LOD_SAVE_FILE_VERSION)
                {
                    // the file we are reading is an older version,
                    // close the reader and delete the file.
                    bufferedReader.close();
                    f.delete();
                    ClientProxy.LOGGER.info("Outdated LOD region file for region: (" + regionX + "," + regionZ + ") version: " + fileVersion +
                            ", version requested: " + LOD_SAVE_FILE_VERSION +
                            " File was been deleted.");

                    return null;
                } else if (fileVersion > LOD_SAVE_FILE_VERSION)
                {
                    // the file we are reading is a newer version,
                    // close the reader and ignore the file, we don't
                    // want to accidently delete anything the user may want.
                    bufferedReader.close();
                    ClientProxy.LOGGER.info("Newer LOD region file for region: (" + regionX + "," + regionZ + ") version: " + fileVersion +
                            ", version requested: " + LOD_SAVE_FILE_VERSION +
                            " this region will not be written to in order to protect the newer file.");

                    return null;
                }
            } else
            {
                // there is no data in this file
                bufferedReader.close();
                return null;
            }


            // this file is a readable version, begin reading the file
            data = bufferedReader.readLine();

            bufferedReader.close();
        } catch (IOException e)
        {
            // the buffered reader encountered a
            // problem reading the file
            return null;
        }
        return new LodRegion(new LevelContainer(data), regionPos);
    }


    //==============//
    // Save to File //
    //==============//

    /**
     * Save all dirty regions in this LodDimension to file.
     */
    public void saveDirtyRegionsToFileAsync()
    {
        fileWritingThreadPool.execute(saveDirtyRegionsThread);
    }

    private Thread saveDirtyRegionsThread = new Thread(() ->
    {
        for (int i = 0; i < loadedDimension.getWidth(); i++)
        {
            for (int j = 0; j < loadedDimension.getWidth(); j++)
            {
                if (loadedDimension.isRegionDirty[i][j] && loadedDimension.regions[i][j] != null)
                {
                    saveRegionToFile(loadedDimension.regions[i][j]);
                    loadedDimension.isRegionDirty[i][j] = false;
                }
            }
        }
    });

    /**
     * Save a specific region to disk.<br>
     * Note: <br>
     * 1. If a file already exists for a newer version
     * the file won't be written.<br>
     * 2. This will save to the LodDimension that this
     * handler is associated with.
     */
    private void saveRegionToFile(LodRegion region)
    {
        // convert to region coordinates
        int x = region.regionPosX;
        int z = region.regionPosZ;

        // get minimum level

        byte minDetailLevel = region.getMinDetailLevel();

        File oldFile = new File(getFileNameAndPathForRegion(x, z));
        try
        {
            // make sure the file and folder exists
            if (!oldFile.exists())
            {
                // the file doesn't exist,
                // create it and the folder if need be
                if (!oldFile.getParentFile().exists())
                    oldFile.getParentFile().mkdirs();
                oldFile.createNewFile();
            } else
            {
                // the file exists, make sure it
                // is the correct version.
                // (to make sure we don't overwrite a newer
                // version file if it exists)

                BufferedReader br = new BufferedReader(new FileReader(oldFile));
                String s = br.readLine();
                int fileVersion = LOD_SAVE_FILE_VERSION;

                if (s != null && !s.isEmpty())
                {
                    // try to get the file version
                    try
                    {
                        fileVersion = Integer.parseInt(s.substring(s.indexOf(' ')).trim());
                    } catch (NumberFormatException | StringIndexOutOfBoundsException e)
                    {
                        // this file doesn't have a correctly formated version
                        // just overwrite the file
                    }
                }
                br.close();

                // check if this file can be written to by the file handler
                if (fileVersion <= LOD_SAVE_FILE_VERSION)
                {
                    // we are good to continue and overwrite the old file
                } else //if(fileVersion > LOD_SAVE_FILE_VERSION)
                {
                    // the file we are reading is a newer version,
                    // don't write anything, we don't want to accidently
                    // delete anything the user may want.
                    return;
                }
            }

            // the old file is good, now create a new save file
            File newFile = new File(getFileNameAndPathForRegion(x, z) + TMP_FILE_EXTENSION);

            FileWriter fw = new FileWriter(newFile);

            // add the version of this file
            fw.write(LOD_FILE_VERSION_PREFIX + " " + LOD_SAVE_FILE_VERSION + "\n");

            // add each LodChunk to the file
            fw.write(region.getLevel((byte) 0).toString());
            fw.close();

            // overwrite the old file with the new one
            Files.move(newFile.toPath(), oldFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e)
        {
            ClientProxy.LOGGER.error("LOD file write error: ");
            e.printStackTrace();
        }
    }


    //================//
    // helper methods //
    //================//


    /**
     * Return the name of the file that should contain the
     * region at the given x and z. <br>
     * Returns null if this object isn't ready to read and write. <br><br>
     * <p>
     * example: "lod.0.0.txt"
     */
    private String getFileNameAndPathForRegion(int regionX, int regionZ)
    {
        try
        {
            // saveFolder is something like
            // ".\Super Flat\DIM-1\data"
            // or
            // ".\Super Flat\data"
            return dimensionDataSaveFolder.getCanonicalPath() + File.separatorChar +
                    FILE_NAME_PREFIX + "." + regionX + "." + regionZ + FILE_EXTENSION;
        } catch (IOException e)
        {
            return null;
        }
    }

}
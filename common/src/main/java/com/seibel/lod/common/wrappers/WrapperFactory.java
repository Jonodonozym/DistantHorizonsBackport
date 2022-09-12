/*
 *    This file is part of the Distant Horizons mod (formerly the LOD Mod),
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2022  James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.lod.common.wrappers;

import com.seibel.lod.common.wrappers.block.BlockStateWrapper;
import com.seibel.lod.common.wrappers.block.BiomeWrapper;
import com.seibel.lod.core.level.ILevel;
import com.seibel.lod.core.level.IServerLevel;
import com.seibel.lod.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.lod.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.lod.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.lod.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvionmentWrapper;
import com.seibel.lod.common.wrappers.worldGeneration.BatchGenerationEnvironment;

import java.io.IOException;

/**
 * This handles creating abstract wrapper objects.
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class WrapperFactory implements IWrapperFactory
{
	public static final WrapperFactory INSTANCE = new WrapperFactory();

	@Override
	public AbstractBatchGenerationEnvionmentWrapper createBatchGenerator(ILevel targetLevel) {
		if (targetLevel instanceof IServerLevel)
		{
			return new BatchGenerationEnvironment((IServerLevel) targetLevel);
		}
		else
		{
			throw new IllegalArgumentException("The target level must be a server-side level.");
		}
	}

	@Override
	public IBiomeWrapper deserializeBiomeWrapper(String str) throws IOException {
		return BiomeWrapper.deserialize(str);
	}

	@Override
	public IBlockStateWrapper deserializeBlockStateWrapper(String str) throws IOException {
		return BlockStateWrapper.deserialize(str);
	}

	@Override
	public IBlockStateWrapper getAirBlockStateWrapper() {
		return BlockStateWrapper.AIR;
	}
}

/*
 *    This file is part of the Distant Horizon mod (formerly the LOD Mod),
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2021 Tom Lee (TomTheFurry)
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

package com.seibel.lod.common.wrappers.worldGeneration;

import com.seibel.lod.core.api.ClientApi;
import com.seibel.lod.core.builders.lodBuilding.LodBuilder;
import com.seibel.lod.core.builders.lodBuilding.LodBuilderConfig;
import com.seibel.lod.core.enums.config.DistanceGenerationMode;
import com.seibel.lod.core.objects.lod.LodDimension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import com.seibel.lod.common.wrappers.chunk.ChunkWrapper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.WorldData;

/*
Total:                   3.135214124s
=====================================
Empty Chunks:            0.000558328s
StructureStart Step:     0.025177207s
StructureReference Step: 0.00189559s
Biome Step:              0.13789155s
Noise Step:              1.570347555s
Surface Step:            0.741238194s
Carver Step:             0.000009923s
Feature Step:            0.389072425s
Lod Generation:          0.269023348s
*/

public final class WorldGenerationStep {
	public static final boolean ENABLE_PERF_LOGGING = false;
	
	
	public static class Rolling {

		private int size;
		private double total = 0d;
		private int index = 0;
		private double samples[];

		public Rolling(int size) {
			this.size = size;
			samples = new double[size];
			for (int i = 0; i < size; i++)
				samples[i] = 0d;
		}

		public void add(double x) {
			total -= samples[index];
			samples[index] = x;
			total += x;
			if (++index == size)
				index = 0; // cheaper than modulus
		}

		public double getAverage() {
			return total / size;
		}
	}

	public static class PrefEvent {
		long beginNano = 0;
		long emptyNano = 0;
		long structStartNano = 0;
		long structRefNano = 0;
		long biomeNano = 0;
		long noiseNano = 0;
		long surfaceNano = 0;
		long carverNano = 0;
		long featureNano = 0;
		long endNano = 0;
	}

	public static class PerfCalculator {
		public static final int SIZE = 50;
		Rolling totalTime = new Rolling(SIZE);
		Rolling emptyTime = new Rolling(SIZE);
		Rolling structStartTime = new Rolling(SIZE);
		Rolling structRefTime = new Rolling(SIZE);
		Rolling biomeTime = new Rolling(SIZE);
		Rolling noiseTime = new Rolling(SIZE);
		Rolling surfaceTime = new Rolling(SIZE);
		Rolling carverTime = new Rolling(SIZE);
		Rolling featureTime = new Rolling(SIZE);
		Rolling lodTime = new Rolling(SIZE);

		public void recordEvent(PrefEvent e) {
			totalTime.add(e.endNano - e.beginNano);
			emptyTime.add(e.emptyNano - e.beginNano);
			structStartTime.add(e.structStartNano - e.emptyNano);
			structRefTime.add(e.structRefNano - e.structStartNano);
			biomeTime.add(e.biomeNano - e.structRefNano);
			noiseTime.add(e.noiseNano - e.biomeNano);
			surfaceTime.add(e.surfaceNano - e.noiseNano);
			carverTime.add(e.carverNano - e.surfaceNano);
			featureTime.add(e.featureNano - e.carverNano);
			lodTime.add(e.endNano - e.featureNano);
		}

		public String toString() {
			return "Total: " + Duration.ofNanos((long) totalTime.getAverage()) + ", Empty: "
					+ Duration.ofNanos((long) emptyTime.getAverage()) + ", StructStart: "
					+ Duration.ofNanos((long) structStartTime.getAverage()) + ", StructRef: "
					+ Duration.ofNanos((long) structRefTime.getAverage()) + ", Biome: "
					+ Duration.ofNanos((long) biomeTime.getAverage()) + ", Noise: "
					+ Duration.ofNanos((long) noiseTime.getAverage()) + ", Surface: "
					+ Duration.ofNanos((long) surfaceTime.getAverage()) + ", Carver: "
					+ Duration.ofNanos((long) carverTime.getAverage()) + ", Feature: "
					+ Duration.ofNanos((long) featureTime.getAverage()) + ", Lod: "
					+ Duration.ofNanos((long) lodTime.getAverage());
		}
	}

	public static final int TIMEOUT_SECONDS = 30;

	enum Steps {
		Empty, StructureStart, StructureReference, Biomes, Noise, Surface, Carvers, LiquidCarvers, Features, Light,
	}

	public static final class GridList<T> extends ArrayList<T> implements List<T> {

		public static class Pos {
			public int x;
			public int y;

			public Pos(int xx, int yy) {
				x = xx;
				y = yy;
			}
		}

		private static final long serialVersionUID = 1585978374811888116L;
		public final int gridCentreToEdge;
		public final int gridSize;

		public GridList(int gridCentreToEdge) {
			super((gridCentreToEdge * 2 + 1) * (gridCentreToEdge * 2 + 1));
			gridSize = gridCentreToEdge * 2 + 1;
			this.gridCentreToEdge = gridCentreToEdge;
		}

		public final T getOffsetOf(int index, int x, int y) {
			return get(index + x + y * gridSize);
		}

		public final int offsetOf(int index, int x, int y) {
			return index + x + y * gridSize;
		}

		public final Pos posOf(int index) {
			return new Pos(index % gridSize, index / gridSize);
		}

		public final int calculateOffset(int x, int y) {
			return x + y * gridSize;
		}

		public final GridList<T> subGrid(int gridCentreToEdge) {
			int centreIndex = size() / 2;
			GridList<T> subGrid = new GridList<T>(gridCentreToEdge);
			for (int oy = -gridCentreToEdge; oy <= gridCentreToEdge; oy++) {
				int begin = offsetOf(centreIndex, -gridCentreToEdge, oy);
				int end = offsetOf(centreIndex, gridCentreToEdge, oy);
				subGrid.addAll(this.subList(begin, end + 1));
			}
			// System.out.println("========================================\n"+
			// this.toDetailString() + "\nTOOOOOOOOOOOOO\n"+subGrid.toDetailString()+
			// "==========================================\n");
			return subGrid;
		}

		@Override
		public String toString() {
			return "GridList " + gridSize + "*" + gridSize + "[" + size() + "]";
		}

		public String toDetailString() {
			StringBuilder str = new StringBuilder("\n");
			int i = 0;
			for (T t : this) {
				str.append(t.toString());
				str.append(", ");
				i++;
				if (i % gridSize == 0) {
					str.append("\n");
				}
			}
			return str.toString();
		}
	}

	public static final class GlobalParameters {
		final ChunkGenerator generator;
		final StructureManager structures;
		final BiomeManager biomeManager;
		final WorldGenSettings worldGenSettings;
		final ThreadedLevelLightEngine lightEngine;
		final LodBuilder lodBuilder;
		final LodDimension lodDim;
		final Registry<Biome> biomes;
		final RegistryAccess registry;
		final long worldSeed;
		final ChunkScanAccess chunkScanner;
		final ServerLevel level; // TODO: Figure out a way to remove this. Maybe ClientLevel also works?
		final DataFixer fixerUpper;

		public GlobalParameters(ServerLevel level, LodBuilder lodBuilder, LodDimension lodDim) {
			this.lodBuilder = lodBuilder;
			this.lodDim = lodDim;
			this.level = level;
			lightEngine = (ThreadedLevelLightEngine) level.getLightEngine();
			MinecraftServer server = level.getServer();
			WorldData worldData = server.getWorldData();
			worldGenSettings = worldData.worldGenSettings();
			registry = server.registryAccess();
			biomes = registry.registryOrThrow(Registry.BIOME_REGISTRY);
			worldSeed = worldGenSettings.seed();
			biomeManager = new BiomeManager(level, BiomeManager.obfuscateSeed(worldSeed));
			structures = server.getStructureManager();
			generator = level.getChunkSource().getGenerator();
			chunkScanner = level.getChunkSource().chunkScanner();
			fixerUpper = server.getFixerUpper();
		}
	}

	public static final class ThreadedParameters {
		private static ThreadLocal<ThreadedParameters> localParam = new ThreadLocal<ThreadedParameters>();
		final ServerLevel level;
		final StructureFeatureManager structFeat;
		final StructureCheck structCheck;
		public final PerfCalculator perf = new PerfCalculator();

		public static final ThreadedParameters getOrMake(GlobalParameters param) {
			ThreadedParameters tParam = localParam.get();
			if (tParam != null && tParam.level == param.level)
				return tParam;
			tParam = new ThreadedParameters(param);
			localParam.set(tParam);
			return tParam;
		}

		private ThreadedParameters(GlobalParameters param) {
			level = param.level;
			structCheck = new StructureCheck(param.chunkScanner, param.registry, param.structures,
					param.level.dimension(), param.generator, level, param.generator.getBiomeSource(), param.worldSeed,
					param.fixerUpper);
			structFeat = new StructureFeatureManager(level, param.worldGenSettings, structCheck);
		}
	}

	public static final class GenerationEvent {
		private static int generationFutureDebugIDs = 0;
		final ThreadedParameters tParam;
		final ChunkPos pos;
		final int range;
		final Future<?> future;
		long nanotime;
		final int id;
		final Steps target;
		final PrefEvent pEvent = new PrefEvent();

		public GenerationEvent(ChunkPos pos, int range, WorldGenerationStep generationGroup, Steps target) {
			nanotime = System.nanoTime();
			this.pos = pos;
			this.range = range;
			id = generationFutureDebugIDs++;
			this.target = target;
			this.tParam = ThreadedParameters.getOrMake(generationGroup.params);
			future = generationGroup.executors.submit(() -> {
				generationGroup.generateLodFromList(this);
			});
		}

		public final boolean isCompleted() {
			return future.isDone();
		}

		public final boolean hasTimeout(int duration, TimeUnit unit) {
			long currentTime = System.nanoTime();
			long delta = currentTime - nanotime;
			return (delta > TimeUnit.NANOSECONDS.convert(duration, unit));
		}

		public final void terminate() {
			future.cancel(true);
		}

		public final void join() {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		public final boolean tooClose(int cx, int cz, int cr) {
			int dist = Math.min(Math.abs(cx - pos.x), Math.abs(cz - pos.z));
			return dist < range + cr;
		}

		public final void refreshTimeout() {
			nanotime = System.nanoTime();
		}

		@Override
		public String toString() {
			return id + ":" + range + "@" + pos + "(" + target + ")";
		}
	}

	private final static <T> T joinAsync(CompletableFuture<T> f) {
		return f.join();
	}

	final LinkedList<GenerationEvent> events = new LinkedList<GenerationEvent>();
	final GlobalParameters params;
	final StepStructureStart stepStructureStart = new StepStructureStart();
	final StepStructureReference stepStructureReference = new StepStructureReference();
	final StepBiomes stepBiomes = new StepBiomes();
	final StepNoise stepNoise = new StepNoise();
	final StepSurface stepSurface = new StepSurface();
	final StepCarvers stepCarvers = new StepCarvers();
	final StepFeatures stepFeatures = new StepFeatures();

	public final ExecutorService executors = Executors
			.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("Gen-Worker-Thread-%d").build());

	public final boolean tryAddPoint(int px, int pz, int range, Steps target) {
		int boxSize = range * 2 + 1;
		int x = Math.floorDiv(px, boxSize) * boxSize + range;
		int z = Math.floorDiv(pz, boxSize) * boxSize + range;

		for (GenerationEvent event : events) {
			if (event.tooClose(x, z, range))
				return false;
		}
		// System.out.println(x + ", "+z);
		events.add(new GenerationEvent(new ChunkPos(x, z), range, this, target));
		return true;
	}

	public final void updateAllFutures() {
		// Update all current out standing jobs
		Iterator<GenerationEvent> iter = events.iterator();
		while (iter.hasNext()) {
			GenerationEvent event = iter.next();
			if (event.isCompleted()) {
				try {
					event.join();
				} catch (RuntimeException e) {
					// Ignore.
				} finally {
					iter.remove();
				}
			} else if (event.hasTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				System.err.println(event.id + ": Timed out and terminated!");
				try {
					event.terminate();
				} finally {
					iter.remove();
				}
			}
		}
	}

	public WorldGenerationStep(ServerLevel level, LodBuilder lodBuilder, LodDimension lodDim) {
		System.out.println("================WORLD_GEN_STEP_INITING=============");
		params = new GlobalParameters(level, lodBuilder, lodDim);
	}

	public final void generateLodFromList(GenerationEvent event) {
		try {
			// System.out.println("Started event: "+event);
			event.pEvent.beginNano = System.nanoTime();
			GridList<ChunkAccess> referencedChunks;
			DistanceGenerationMode generationMode;
			referencedChunks = generateDirect(event, event.range, event.target);

			switch (event.target) {
			case Empty:
				return;
			case StructureStart:
				generationMode = DistanceGenerationMode.NONE;
				break;
			case StructureReference:
				generationMode = DistanceGenerationMode.NONE;
				break;
			case Biomes:
				generationMode = DistanceGenerationMode.BIOME_ONLY_SIMULATE_HEIGHT;
				break;
			case Noise:
				generationMode = DistanceGenerationMode.BIOME_ONLY_SIMULATE_HEIGHT;
				break;
			case Surface:
				generationMode = DistanceGenerationMode.SURFACE;
				break;
			case Carvers:
				generationMode = DistanceGenerationMode.SURFACE;
				break;
			case Features:
				generationMode = DistanceGenerationMode.FEATURES;
				break;
			case LiquidCarvers:
				return;
			case Light:
				return;
			default:
				return;
			}
			int centreIndex = referencedChunks.size() / 2;

			// System.out.println("Lod Generate Event: "+event);
			for (int oy = -event.range; oy <= event.range; oy++) {
				for (int ox = -event.range; ox <= event.range; ox++) {
					int targetIndex = referencedChunks.offsetOf(centreIndex, ox, oy);
					ChunkAccess target = referencedChunks.get(targetIndex);
					params.lodBuilder.generateLodNodeFromChunk(params.lodDim, new ChunkWrapper(target),
							new LodBuilderConfig(generationMode));
				}
			}
			event.pEvent.endNano = System.nanoTime();
			event.refreshTimeout();
			if (ENABLE_PERF_LOGGING) {
				event.tParam.perf.recordEvent(event.pEvent);
				ClientApi.LOGGER.info(event.tParam.perf);
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
			throw e;
		}
	}

	public final GridList<ChunkAccess> generateDirect(GenerationEvent e, int range, Steps step) {
		int cx = e.pos.x;
		int cy = e.pos.z;
		int rangeEmpty = range + 3;
		if (rangeEmpty < 7)
			rangeEmpty = 7; // For some reason the Blender needs at least range 7???
		GridList<ChunkAccess> chunks = new GridList<ChunkAccess>(rangeEmpty);

		for (int oy = -rangeEmpty; oy <= rangeEmpty; oy++) {
			for (int ox = -rangeEmpty; ox <= rangeEmpty; ox++) {
				// ChunkAccess target = getCachedChunk(new ChunkPos(cx+ox, cy+oy));
				ChunkAccess target = new ProtoChunk(new ChunkPos(cx + ox, cy + oy), UpgradeData.EMPTY, params.level,
						params.biomes, null);
				chunks.add(target);
			}
		}
		e.pEvent.emptyNano = System.nanoTime();
		e.refreshTimeout();
		WorldGenRegion region = new WorldGenRegion(params.level, chunks, ChunkStatus.STRUCTURE_STARTS, range + 1);
		GridList<ChunkAccess> subRange = chunks.subGrid(range);
		stepStructureStart.generateGroup(e.tParam, region, subRange);
		e.pEvent.structStartNano = System.nanoTime();
		e.refreshTimeout();
		if (step == Steps.StructureStart)
			return subRange;
		stepStructureReference.generateGroup(e.tParam, region, subRange);
		e.pEvent.structRefNano = System.nanoTime();
		e.refreshTimeout();
		if (step == Steps.StructureReference)
			return subRange;
		stepBiomes.generateGroup(e.tParam, region, subRange);
		e.pEvent.biomeNano = System.nanoTime();
		e.refreshTimeout();
		if (step == Steps.Biomes)
			return subRange;
		stepNoise.generateGroup(e.tParam, region, subRange);
		e.pEvent.noiseNano = System.nanoTime();
		e.refreshTimeout();
		if (step == Steps.Noise)
			return subRange;
		stepSurface.generateGroup(e.tParam, region, subRange);
		e.pEvent.surfaceNano = System.nanoTime();
		e.refreshTimeout();
		if (step == Steps.Surface)
			return subRange;
		stepCarvers.generateGroup(e.tParam, region, subRange);
		e.pEvent.carverNano = System.nanoTime();
		e.refreshTimeout();
		if (step == Steps.Carvers)
			return subRange;
		stepFeatures.generateGroup(e.tParam, region, subRange);
		e.pEvent.featureNano = System.nanoTime();
		e.refreshTimeout();
		return subRange;
	}

	public final class StepStructureStart {
		public final ChunkStatus STATUS = ChunkStatus.STRUCTURE_STARTS;

		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				List<ChunkAccess> chunks) {
			if (params.worldGenSettings.generateFeatures()) {
				for (ChunkAccess chunk : chunks) {
					// System.out.println("StepStructureStart: "+chunk.getPos());
					params.generator.createStructures(params.registry, tParams.structFeat, chunk, params.structures,
							params.worldSeed);
					((ProtoChunk) chunk).setStatus(STATUS);
					tParams.structCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts());
				}
			}
		}
	}

	public final class StepStructureReference {
		public final ChunkStatus STATUS = ChunkStatus.STRUCTURE_REFERENCES;

		private void createReferences(WorldGenRegion worldGenLevel, StructureFeatureManager structureFeatureManager,
				ChunkAccess chunkAccess) {
			ChunkPos chunkPos = chunkAccess.getPos();
			int j = chunkPos.x;
			int k = chunkPos.z;
			int l = chunkPos.getMinBlockX();
			int m = chunkPos.getMinBlockZ();

			SectionPos sectionPos = SectionPos.bottomOf(chunkAccess);

			for (int n = j - 8; n <= j + 8; n++) {
				for (int o = k - 8; o <= k + 8; o++) {
					if (!worldGenLevel.hasChunk(n, o))
						continue;
					long p = ChunkPos.asLong(n, o);
					for (StructureStart<?> structureStart : worldGenLevel.getChunk(n, o).getAllStarts().values()) {
						try {
							if (structureStart.isValid()
									&& structureStart.getBoundingBox().intersects(l, m, l + 15, m + 15)) {
								structureFeatureManager.addReferenceForFeature(sectionPos, structureStart.getFeature(),
										p, chunkAccess);
							}
						} catch (Exception exception) {
							CrashReport crashReport = CrashReport.forThrowable(exception,
									"Generating structure reference");
							CrashReportCategory crashReportCategory = crashReport.addCategory("Structure");
							crashReportCategory.setDetail("Id",
									() -> Registry.STRUCTURE_FEATURE.getKey(structureStart.getFeature()).toString());
							crashReportCategory.setDetail("Name", () -> structureStart.getFeature().getFeatureName());
							crashReportCategory.setDetail("Class",
									() -> structureStart.getFeature().getClass().getCanonicalName());
							throw new ReportedException(crashReport);
						}
					}
				}
			}
		}

		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				List<ChunkAccess> chunks) {
			for (ChunkAccess chunk : chunks) {
				// System.out.println("StepStructureReference: "+chunk.getPos());
				createReferences(worldGenRegion, tParams.structFeat.forWorldGenRegion(worldGenRegion), chunk);
				((ProtoChunk) chunk).setStatus(STATUS);
			}
		}
	}

	public final class StepBiomes {
		public final ChunkStatus STATUS = ChunkStatus.BIOMES;

		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				List<ChunkAccess> chunks) {
			for (ChunkAccess chunk : chunks) {
				// System.out.println("StepBiomes: "+chunk.getPos());
				chunk = joinAsync(params.generator.createBiomes(params.biomes, Runnable::run,
						Blender.of(worldGenRegion), tParams.structFeat.forWorldGenRegion(worldGenRegion), chunk));
				((ProtoChunk) chunk).setStatus(STATUS);
			}
		}
	}

	public final class StepNoise {
		public final ChunkStatus STATUS = ChunkStatus.NOISE;
		
		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				List<ChunkAccess> chunks) {
			for (ChunkAccess chunk : chunks) {
				// System.out.println("StepNoise: "+chunk.getPos());
				chunk = joinAsync(params.generator.fillFromNoise(Runnable::run, Blender.of(worldGenRegion),
						tParams.structFeat.forWorldGenRegion(worldGenRegion), chunk));
				((ProtoChunk) chunk).setStatus(STATUS);
			}
		}
	}

	public final class StepSurface {
		public final ChunkStatus STATUS = ChunkStatus.SURFACE;

		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				List<ChunkAccess> chunks) {
			for (ChunkAccess chunk : chunks) {
				// System.out.println("StepSurface: "+chunk.getPos());
				params.generator.buildSurface(worldGenRegion, tParams.structFeat.forWorldGenRegion(worldGenRegion),
						chunk);
				((ProtoChunk) chunk).setStatus(STATUS);
			}
		}
	}

	public final class StepCarvers {
		public final ChunkStatus STATUS = ChunkStatus.CARVERS;

		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				List<ChunkAccess> chunks) {
			for (ChunkAccess chunk : chunks) {
				// DISABLED CURRENTLY!
				// System.out.println("StepCarvers: "+chunk.getPos());
				// Blender.addAroundOldChunksCarvingMaskFilter((WorldGenLevel) worldGenRegion,
				// (ProtoChunk) chunk);
				// params.generator.applyCarvers(worldGenRegion, params.worldSeed,
				// params.biomeManager, tParams.structFeat.forWorldGenRegion(worldGenRegion),
				// chunk,
				// GenerationStep.Carving.AIR);
				((ProtoChunk) chunk).setStatus(STATUS);
			}
		}
	}

	public final class StepFeatures {
		public final ChunkStatus STATUS = ChunkStatus.FEATURES;

		public final void generateGroup(ThreadedParameters tParams, WorldGenRegion worldGenRegion,
				GridList<ChunkAccess> chunks) {
			for (ChunkAccess chunk : chunks) {
				ProtoChunk protoChunk = (ProtoChunk) chunk;
				try {
					protoChunk.setLightEngine(params.lightEngine);
					params.generator.applyBiomeDecoration(worldGenRegion, chunk,
							tParams.structFeat.forWorldGenRegion(worldGenRegion));
					Blender.generateBorderTicks(worldGenRegion, chunk);
				} catch (ReportedException e) {
					// e.printStackTrace();
					// FIXME: Features concurrent modification issue. Something about cocobeans just
					// aren't happy
					// For now just retry.
				} finally {
					Heightmap.primeHeightmaps(chunk,
							EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
									Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
					protoChunk.setStatus(STATUS);
				}
			}
		}
	}
}

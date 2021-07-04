package com.seibel.lod.builders.worldGeneration;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.seibel.lod.objects.LodChunk;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particles.IParticleData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EmptyTickList;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.ITickList;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.Heightmap.Type;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.IWorldInfo;


/**
 * This is a fake ServerWorld used when generating features.
 * This allows us to keep each LodChunk generation independent
 * of the actual ServerWorld, allowing us
 * to multithread generation.
 * 
 * @author James Seibel
 * @version 6-27-2021
 */
public class LodServerWorld implements ISeedReader {
	
	public HashMap<Heightmap.Type, Heightmap> heightmaps = new HashMap<>();
	
	public IChunk chunk;
	
	public LodServerWorld(IChunk newChunk)
	{
		chunk = newChunk;
	}
	
	
	
	@Override
	public int getHeight(Type heightmapType, int x, int z)
	{
		// make sure the block position is set relative to the chunk
		x = x % LodChunk.WIDTH;
		x = (x < 0) ? x + 16 : x;
		
		z = z % LodChunk.WIDTH;
		z = (z < 0) ? z + 16 : z;
		
		return chunk.getOrCreateHeightmapUnprimed(LodChunk.DEFAULT_HEIGHTMAP).getFirstAvailable(x, z);
	}
	
	@Override
	public Biome getBiome(BlockPos pos)
	{
		return chunk.getBiomes().getNoiseBiome(pos.getX(), pos.getY(), pos.getZ());
	}
	
	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft)
	{
		return chunk.setBlockState(pos, state, false) == state;
	}
	
	@Override
	public BlockState getBlockState(BlockPos pos)
	{
		return chunk.getBlockState(pos);
	}
	
	@Override
	public FluidState getFluidState(BlockPos pos)
	{
		return chunk.getFluidState(pos);
	}
	
//	@Override
//	public int getLightFor(LightType type, BlockPos pos)
//	{
//		// this needs to be low for snow generation to work
//		return 0;
//	}
	
	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state)
	{
		return state.test(chunk.getBlockState(pos));
	}
	
	@Override
	public ITickList<Block> getBlockTicks()
	{
		return EmptyTickList.empty();
	}
	
	@Override
	public IChunk getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull) {
		return chunk;
	}
	
	
	
	
	/**
	 * 
	 * All methods below shouldn't be needed
	 * and thus have been left unimplemented. 
	 * 
	 */
	
	
	
	@Override
	public Random getRandom() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public void playSound(PlayerEntity player, BlockPos pos, SoundEvent soundIn, SoundCategory category, float volume,
			float pitch) {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public void addParticle(IParticleData particleData, double x, double y, double z, double xSpeed, double ySpeed,
			double zSpeed) {
		throw new UnsupportedOperationException("Not Implemented");
	}
	@Override
	public DynamicRegistries registryAccess() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	@Override
	public BiomeManager getBiomeManager() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	@Override
	public int getSeaLevel() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public float getShade(Direction p_230487_1_, boolean p_230487_2_) {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public WorldBorder getWorldBorder() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public boolean removeBlock(BlockPos pos, boolean isMoving) {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft) {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public long getSeed() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
	@Override
	public Stream<? extends StructureStart<?>> startsForFeature(SectionPos p_241827_1_, Structure<?> p_241827_2_) {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public ServerWorld getLevel() {
		throw new UnsupportedOperationException("Not Implemented");
	}
	



	@Override
	public AbstractChunkProvider getChunkSource() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos arg0) {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public IWorldInfo getLevelData() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public ITickList<Fluid> getLiquidTicks() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public void levelEvent(PlayerEntity arg0, int arg1, BlockPos arg2, int arg3) {
		throw new UnsupportedOperationException("Not Implemented");
		
	}



	@Override
	public List<Entity> getEntities(Entity arg0, AxisAlignedBB arg1, Predicate<? super Entity> arg2) {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public <T extends Entity> List<T> getEntitiesOfClass(Class<? extends T> arg0, AxisAlignedBB arg1,
			Predicate<? super T> arg2) {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public List<? extends PlayerEntity> players() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public int getSkyDarken() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public Biome getUncachedNoiseBiome(int p_225604_1_, int p_225604_2_, int p_225604_3_) {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public boolean isClientSide() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public DimensionType dimensionType() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public WorldLightManager getLightEngine() {
		throw new UnsupportedOperationException("Not Implemented");
	}



	@Override
	public TileEntity getBlockEntity(BlockPos p_175625_1_) {
		throw new UnsupportedOperationException("Not Implemented");
	}
	
}
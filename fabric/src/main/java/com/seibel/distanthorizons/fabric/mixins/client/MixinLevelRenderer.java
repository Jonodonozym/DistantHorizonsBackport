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

package com.seibel.distanthorizons.fabric.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
#if PRE_MC_1_19_4
import com.mojang.math.Matrix4f;
#else
import org.joml.Matrix4f;
#endif
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This class is used to mix in my rendering code
 * before Minecraft starts rendering blocks.
 * If this wasn't done, and we used Forge's
 * render last event, the LODs would render on top
 * of the normal terrain.
 *
 * This is also the mixin for rendering the clouds
 *
 * @author coolGi
 * @author James Seibel
 * @version 12-31-2021
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
	@Shadow
	private ClientLevel level;
	@Unique
	private static float previousPartialTicks = 0;
	
	// Inject rendering at first call to renderChunkLayer
	// HEAD or RETURN
	#if PRE_MC_1_17_1
	@Inject(at = @At("RETURN"), method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")
	private void renderSky(PoseStack matrixStackIn, float partialTicks, CallbackInfo callback)
	{
		// get the partial ticks since renderBlockLayer doesn't
		// have access to them
		previousPartialTicks = partialTicks;
	}
	#else
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	public void renderClouds(PoseStack poseStack, Matrix4f projectionMatrix, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci)
	{
		// get the partial ticks since renderChunkLayer doesn't
		// have access to them
		previousPartialTicks = tickDelta;
	}
    #endif
	
	#if PRE_MC_1_17_1
    @Inject(at = @At("HEAD"),
			method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack matrixStackIn, double xIn, double yIn, double zIn, CallbackInfo callback)
	#elif PRE_MC_1_19_4
	@Inject(at = @At("HEAD"),
			method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLcom/mojang/math/Matrix4f;)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double cameraXBlockPos, double cameraYBlockPos, double cameraZBlockPos, Matrix4f projectionMatrix, CallbackInfo callback)
	#else
	@Inject(at = @At("HEAD"),
			method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double cameraXBlockPos, double cameraYBlockPos, double cameraZBlockPos, Matrix4f projectionMatrix, CallbackInfo callback)
    #endif
	{
		// FIXME completely disables rendering when sodium is installed
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			callback.cancel();
		}
	}
	
	@Redirect(method =
			"Lnet/minecraft/client/renderer/LevelRenderer;" +
					"renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;" +
					"FJZLnet/minecraft/client/Camera;" +
					"Lnet/minecraft/client/renderer/GameRenderer;" +
					"Lnet/minecraft/client/renderer/LightTexture;" +
                #if PRE_MC_1_19_4
					"Lcom/mojang/math/Matrix4f;)V"
			#else
				"Lorg/joml/Matrix4f;)V"
		#endif
			,
			at = @At(
					value = "INVOKE",
					#if PRE_MC_1_20_1
					target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runUpdates(IZZ)I"
					#else
					target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"
					#endif
			))
	private int callAfterRunUpdates(LevelLightEngine light #if PRE_MC_1_20_1 , int pos, boolean isQueueEmpty, boolean updateBlockLight #endif )
	{
        #if PRE_MC_1_20_1
		int r = light.runUpdates(pos, isQueueEmpty, updateBlockLight);
        #else
		int r = light.runLightUpdates();
        #endif
		ChunkWrapper.syncedUpdateClientLightStatus();
		return r;
	}
	
}

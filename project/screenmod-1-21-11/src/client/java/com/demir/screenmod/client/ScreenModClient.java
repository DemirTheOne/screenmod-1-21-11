package com.demir.screenmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.demir.screenmod.ModBlocks;
import com.demir.screenmod.ScreenBlock;
import com.demir.screenmod.network.ImageChunkS2C;
import com.demir.screenmod.network.ImageListS2C;

public class ScreenModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlocks.SCREEN_ENTITY, ScreenRenderer::new);

		ScreenBlock.clientOpener = pos -> Minecraft.getInstance().setScreen(new ScreenConfigScreen(pos));

		ClientPlayNetworking.registerGlobalReceiver(ImageListS2C.TYPE,
				(payload, context) -> ClientImageManager.setServerImages(payload.names()));
		ClientPlayNetworking.registerGlobalReceiver(ImageChunkS2C.TYPE,
				(payload, context) -> ClientImageManager.onChunk(payload));

		ClientTickEvents.END_CLIENT_TICK.register(client -> ImageUploader.tick());

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientImageManager.clear();
			ImageUploader.reset();
		});
	}
}

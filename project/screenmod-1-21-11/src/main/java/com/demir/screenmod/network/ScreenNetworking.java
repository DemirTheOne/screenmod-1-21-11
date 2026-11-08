package com.demir.screenmod.network;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.demir.screenmod.ScreenBlockEntity;
import com.demir.screenmod.ScreenLayout;
import com.demir.screenmod.ScreenNetworkData;
import com.demir.screenmod.ServerImageStore;

/** Payload registration and server-side handling (uploads, downloads, screen settings). */
public final class ScreenNetworking {
	/** Max bytes per chunk. C2S custom payloads are capped at 32767 bytes, so stay well below. */
	public static final int CHUNK_SIZE = 24000;

	private static final int MAX_CHUNKS = ServerImageStore.MAX_IMAGE_BYTES / CHUNK_SIZE + 2;
	private static final int CHUNKS_PER_TICK = 4;

	private static final class Upload {
		final String name;
		final int total;
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int next;

		Upload(String name, int total) {
			this.name = name;
			this.total = total;
		}
	}

	private static final Map<UUID, Upload> UPLOADS = new ConcurrentHashMap<>();
	private static final Map<UUID, ArrayDeque<ImageChunkS2C>> OUTGOING = new ConcurrentHashMap<>();

	private ScreenNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.playC2S().register(ListRequestC2S.TYPE, ListRequestC2S.CODEC);
		PayloadTypeRegistry.playC2S().register(UploadChunkC2S.TYPE, UploadChunkC2S.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestImageC2S.TYPE, RequestImageC2S.CODEC);
		PayloadTypeRegistry.playC2S().register(SetScreenC2S.TYPE, SetScreenC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(ImageListS2C.TYPE, ImageListS2C.CODEC);
		PayloadTypeRegistry.playS2C().register(ImageChunkS2C.TYPE, ImageChunkS2C.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(ListRequestC2S.TYPE, (payload, context) ->
				sendList(context.player(), context.server()));

		ServerPlayNetworking.registerGlobalReceiver(UploadChunkC2S.TYPE, (payload, context) ->
				handleUploadChunk(payload, context.player(), context.server()));

		ServerPlayNetworking.registerGlobalReceiver(RequestImageC2S.TYPE, (payload, context) ->
				handleImageRequest(payload, context.player(), context.server()));

		ServerPlayNetworking.registerGlobalReceiver(SetScreenC2S.TYPE, (payload, context) ->
				handleSetScreen(payload, context.player(), context.server()));

		ServerTickEvents.END_SERVER_TICK.register(ScreenNetworking::drainOutgoing);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			UPLOADS.remove(id);
			OUTGOING.remove(id);
		});
	}

	private static void sendList(ServerPlayer player, MinecraftServer server) {
		ServerPlayNetworking.send(player, new ImageListS2C(ServerImageStore.list(server)));
	}

	private static void handleUploadChunk(UploadChunkC2S p, ServerPlayer player, MinecraftServer server) {
		UUID id = player.getUUID();
		if (p.total() < 1 || p.total() > MAX_CHUNKS || p.index() < 0 || p.index() >= p.total()
				|| p.data().length > CHUNK_SIZE) {
			UPLOADS.remove(id);
			return;
		}
		String name = ServerImageStore.sanitize(p.name());
		if (name == null || !name.equals(p.name())) {
			UPLOADS.remove(id);
			player.sendSystemMessage(Component.literal("[Simply Screens] Invalid image name (use png/jpg, letters, digits, . _ -)."));
			return;
		}
		Upload upload;
		if (p.index() == 0) {
			upload = new Upload(name, p.total());
			UPLOADS.put(id, upload);
		} else {
			upload = UPLOADS.get(id);
			if (upload == null || !upload.name.equals(name) || upload.total != p.total() || upload.next != p.index()) {
				UPLOADS.remove(id);
				return;
			}
		}
		upload.buffer.write(p.data(), 0, p.data().length);
		if (upload.buffer.size() > ServerImageStore.MAX_IMAGE_BYTES) {
			UPLOADS.remove(id);
			player.sendSystemMessage(Component.literal("[Simply Screens] Image is larger than 8 MB."));
			return;
		}
		upload.next++;
		if (upload.next == upload.total) {
			UPLOADS.remove(id);
			if (ServerImageStore.write(server, upload.name, upload.buffer.toByteArray())) {
				sendList(player, server);
			} else {
				player.sendSystemMessage(Component.literal("[Simply Screens] Upload rejected (not a valid png/jpg, or the image limit was reached)."));
			}
		}
	}

	private static void handleImageRequest(RequestImageC2S p, ServerPlayer player, MinecraftServer server) {
		byte[] data = ServerImageStore.read(server, p.name());
		if (data == null) {
			return;
		}
		ArrayDeque<ImageChunkS2C> queue = OUTGOING.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		if (queue.size() > MAX_CHUNKS * 3) {
			return;
		}
		int total = Math.max(1, (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
		for (int i = 0; i < total; i++) {
			int from = i * CHUNK_SIZE;
			int to = Math.min(data.length, from + CHUNK_SIZE);
			queue.add(new ImageChunkS2C(p.name(), i, total, Arrays.copyOfRange(data, from, to)));
		}
	}

	private static void drainOutgoing(MinecraftServer server) {
		for (Map.Entry<UUID, ArrayDeque<ImageChunkS2C>> entry : OUTGOING.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			ArrayDeque<ImageChunkS2C> queue = entry.getValue();
			if (player == null) {
				OUTGOING.remove(entry.getKey());
				continue;
			}
			for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
				ServerPlayNetworking.send(player, queue.poll());
			}
			if (queue.isEmpty()) {
				OUTGOING.remove(entry.getKey());
			}
		}
	}

	private static String sanitizeId(String raw) {
		String cleaned = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9_-]", "");
		return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
	}

	private static void handleSetScreen(SetScreenC2S p, ServerPlayer player, MinecraftServer server) {
		Level playerLevel = player.level();
		if (!(playerLevel instanceof ServerLevel level)) {
			return;
		}
		BlockPos clicked = p.pos();
		if (player.distanceToSqr(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5) > 64.0
				|| !level.isLoaded(clicked)) {
			return;
		}
		BlockPos anchor = ScreenLayout.walkToAnchor(level, clicked);
		if (anchor == null || !(level.getBlockEntity(anchor) instanceof ScreenBlockEntity screen)) {
			return;
		}

		String screenId = sanitizeId(p.screenId());
		boolean setImage = p.setImage();
		String image = p.image();
		if (setImage && !image.isEmpty() && !ServerImageStore.exists(server, image)) {
			setImage = false;
			player.sendSystemMessage(Component.literal("[Simply Screens] That image no longer exists on the server."));
		}

		screen.setScreenId(screenId);
		screen.setKeepAspect(p.keepAspect());
		ScreenNetworkData network = ScreenNetworkData.get(server);
		if (setImage) {
			screen.setImage(image);
			if (!screenId.isEmpty()) {
				network.set(screenId, image);
			}
		} else if (!screenId.isEmpty() && !network.has(screenId) && !screen.getImage().isEmpty()) {
			network.set(screenId, screen.getImage());
		}

		screen.markSynced();
		if (!screenId.isEmpty()) {
			ScreenBlockEntity.refreshNetwork(screenId);
		}
	}
}

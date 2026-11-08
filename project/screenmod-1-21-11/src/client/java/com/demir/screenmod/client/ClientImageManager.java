package com.demir.screenmod.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.demir.screenmod.ServerImageStore;
import com.demir.screenmod.ScreenMod;
import com.demir.screenmod.network.ImageChunkS2C;
import com.demir.screenmod.network.RequestImageC2S;

/** Downloads images from the server on demand and keeps them as GPU textures. All state is main-thread only. */
public final class ClientImageManager {
	public record Tex(Identifier id, int width, int height) {
	}

	private static final class Assembly {
		final int total;
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int next;

		Assembly(int total) {
			this.total = total;
		}
	}

	private static final int MAX_TEXTURES = 32;
	private static final int MAX_SIDE = 8192;
	private static final long MAX_PIXELS = 16L * 1024 * 1024;
	private static final long RETRY_MS = 10_000;

	private static final Map<String, Tex> READY = new LinkedHashMap<>();
	private static final Map<String, Assembly> ASSEMBLING = new HashMap<>();
	private static final Map<String, Long> REQUESTED = new HashMap<>();
	private static final Set<String> FAILED = new HashSet<>();

	private static List<String> serverImages = List.of();
	private static int listVersion;

	private ClientImageManager() {
	}

	public static List<String> getServerImages() {
		return serverImages;
	}

	public static int getListVersion() {
		return listVersion;
	}

	public static void setServerImages(List<String> names) {
		serverImages = List.copyOf(names);
		listVersion++;
	}

	/** Returns the texture if it is ready, otherwise starts a download and returns null. */
	@Nullable
	public static Tex get(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		Tex ready = READY.get(name);
		if (ready != null) {
			return ready;
		}
		if (FAILED.contains(name) || ASSEMBLING.containsKey(name)) {
			return null;
		}
		long now = System.currentTimeMillis();
		Long last = REQUESTED.get(name);
		if (last != null && now - last < RETRY_MS) {
			return null;
		}
		REQUESTED.put(name, now);
		if (ClientPlayNetworking.canSend(RequestImageC2S.TYPE)) {
			ClientPlayNetworking.send(new RequestImageC2S(name));
		}
		return null;
	}

	public static void onChunk(ImageChunkS2C chunk) {
		String name = chunk.name();
		if (chunk.total() < 1 || chunk.total() > 400 || chunk.index() < 0 || chunk.index() >= chunk.total()) {
			ASSEMBLING.remove(name);
			return;
		}
		Assembly assembly;
		if (chunk.index() == 0) {
			assembly = new Assembly(chunk.total());
			ASSEMBLING.put(name, assembly);
		} else {
			assembly = ASSEMBLING.get(name);
			if (assembly == null || assembly.total != chunk.total() || assembly.next != chunk.index()) {
				ASSEMBLING.remove(name);
				return;
			}
		}
		assembly.buffer.write(chunk.data(), 0, chunk.data().length);
		if (assembly.buffer.size() > ServerImageStore.MAX_IMAGE_BYTES) {
			ASSEMBLING.remove(name);
			FAILED.add(name);
			return;
		}
		assembly.next++;
		if (assembly.next == assembly.total) {
			ASSEMBLING.remove(name);
			decodeAsync(name, assembly.buffer.toByteArray());
		}
	}

	private static void decodeAsync(String name, byte[] bytes) {
		CompletableFuture.supplyAsync(() -> {
			try {
				return decode(bytes);
			} catch (IOException | RuntimeException e) {
				return null;
			}
		}).thenAccept(image -> Minecraft.getInstance().execute(() -> {
			if (image == null) {
				FAILED.add(name);
			} else {
				register(name, image);
			}
		}));
	}

	@Nullable
	private static NativeImage decode(byte[] bytes) throws IOException {
		if (!ServerImageStore.looksLikeImage(bytes)) {
			return null;
		}
		boolean png = (bytes[0] & 0xFF) == 0x89;
		if (png) {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
			if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE
					|| (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
				image.close();
				return null;
			}
			return image;
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				int w = reader.getWidth(0);
				int h = reader.getHeight(0);
				if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) {
					return null;
				}
			} finally {
				reader.dispose();
			}
		}
		BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
		if (buffered == null) {
			return null;
		}
		NativeImage image = new NativeImage(buffered.getWidth(), buffered.getHeight(), false);
		for (int y = 0; y < buffered.getHeight(); y++) {
			for (int x = 0; x < buffered.getWidth(); x++) {
				// NativeImage.setPixel takes ARGB in 1.21.11, same layout as BufferedImage.getRGB.
				image.setPixel(x, y, buffered.getRGB(x, y));
			}
		}
		return image;
	}

	private static void register(String name, NativeImage image) {
		Minecraft minecraft = Minecraft.getInstance();
		int width = image.getWidth();
		int height = image.getHeight();
		Identifier id = ScreenMod.id("img/" + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
		DynamicTexture texture = new DynamicTexture(() -> "Simply Screens " + name, image);
		minecraft.getTextureManager().register(id, texture);
		READY.put(name, new Tex(id, width, height));
		while (READY.size() > MAX_TEXTURES) {
			String oldest = READY.keySet().iterator().next();
			Tex removed = READY.remove(oldest);
			minecraft.getTextureManager().release(removed.id());
		}
	}

	public static void clear() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Tex tex : READY.values()) {
			minecraft.getTextureManager().release(tex.id());
		}
		READY.clear();
		ASSEMBLING.clear();
		REQUESTED.clear();
		FAILED.clear();
		serverImages = List.of();
		listVersion++;
	}
}

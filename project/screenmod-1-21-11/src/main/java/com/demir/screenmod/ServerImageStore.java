package com.demir.screenmod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Images live in <world>/screenmod_images. Only png/jpg/jpeg with valid magic bytes are accepted. */
public final class ServerImageStore {
	public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
	public static final int MAX_IMAGES = 256;

	private ServerImageStore() {
	}

	public static Path dir(MinecraftServer server) throws IOException {
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve("screenmod_images");
		Files.createDirectories(dir);
		return dir;
	}

	/** Returns a safe file name, or null if the name can't be used. */
	@Nullable
	public static String sanitize(String name) {
		if (name == null) {
			return null;
		}
		String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
		if (cleaned.length() > 80 || cleaned.startsWith(".")) {
			return null;
		}
		String lower = cleaned.toLowerCase(Locale.ROOT);
		if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
			return null;
		}
		return cleaned;
	}

	public static boolean looksLikeImage(byte[] data) {
		if (data.length < 8) {
			return false;
		}
		boolean png = (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
		boolean jpg = (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
		return png || jpg;
	}

	public static List<String> list(MinecraftServer server) {
		List<String> names = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir(server))) {
			files.filter(Files::isRegularFile)
					.map(p -> p.getFileName().toString())
					.filter(n -> sanitize(n) != null && sanitize(n).equals(n))
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.limit(MAX_IMAGES)
					.forEach(names::add);
		} catch (IOException ignored) {
			// empty list
		}
		return names;
	}

	public static boolean exists(MinecraftServer server, String name) {
		return sanitize(name) != null && sanitize(name).equals(name) && list(server).contains(name);
	}

	@Nullable
	public static byte[] read(MinecraftServer server, String name) {
		if (sanitize(name) == null || !sanitize(name).equals(name)) {
			return null;
		}
		try {
			Path file = dir(server).resolve(name).normalize();
			if (!file.startsWith(dir(server)) || !Files.isRegularFile(file) || Files.size(file) > MAX_IMAGE_BYTES) {
				return null;
			}
			return Files.readAllBytes(file);
		} catch (IOException e) {
			return null;
		}
	}

	/** @return true if the image is stored (also when an identical name already exists) */
	public static boolean write(MinecraftServer server, String name, byte[] data) {
		if (sanitize(name) == null || !sanitize(name).equals(name) || !looksLikeImage(data) || data.length > MAX_IMAGE_BYTES) {
			return false;
		}
		try {
			Path dir = dir(server);
			Path file = dir.resolve(name).normalize();
			if (!file.startsWith(dir)) {
				return false;
			}
			if (Files.exists(file)) {
				return true;
			}
			if (list(server).size() >= MAX_IMAGES) {
				return false;
			}
			Files.write(file, data);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}

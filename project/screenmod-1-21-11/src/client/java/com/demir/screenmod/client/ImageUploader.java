package com.demir.screenmod.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.demir.screenmod.ServerImageStore;
import com.demir.screenmod.network.ScreenNetworking;
import com.demir.screenmod.network.UploadChunkC2S;

/**
 * Reads an image from a local path or an http(s) URL on the CLIENT, validates it, and uploads it to the server in
 * paced chunks. URLs are fetched by the client so the server never makes outgoing requests (no SSRF surface).
 */
public final class ImageUploader {
	private static final ArrayDeque<UploadChunkC2S> QUEUE = new ArrayDeque<>();
	private static final int CHUNKS_PER_TICK = 4;

	/** Human readable status shown in the GUI. */
	public static volatile String status = "";

	private ImageUploader() {
	}

	public static void reset() {
		QUEUE.clear();
		status = "";
	}

	/** Called every client tick; sends a few queued chunks per tick. */
	public static void tick() {
		if (QUEUE.isEmpty()) {
			return;
		}
		if (!ClientPlayNetworking.canSend(UploadChunkC2S.TYPE)) {
			QUEUE.clear();
			return;
		}
		for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
			ClientPlayNetworking.send(QUEUE.poll());
		}
		if (QUEUE.isEmpty()) {
			status = "Upload finished.";
		}
	}

	public static void uploadFromSource(String rawSource) {
		String source = rawSource.trim();
		if (source.length() >= 2 && source.startsWith("\"") && source.endsWith("\"")) {
			source = source.substring(1, source.length() - 1);
		}
		final String finalSource = source;
		status = "Reading " + finalSource + " ...";
		Thread thread = new Thread(() -> {
			try {
				String lower = finalSource.toLowerCase(Locale.ROOT);
				byte[] data;
				String baseName;
				if (lower.startsWith("http://") || lower.startsWith("https://")) {
					data = download(finalSource);
					baseName = baseNameOfUrl(finalSource);
				} else {
					Path path = Path.of(finalSource);
					if (!Files.isRegularFile(path)) {
						throw new IOException("File not found");
					}
					if (Files.size(path) > ServerImageStore.MAX_IMAGE_BYTES) {
						throw new IOException("Image is larger than 8 MB");
					}
					data = Files.readAllBytes(path);
					baseName = path.getFileName().toString();
				}
				enqueue(data, baseName);
			} catch (IOException | RuntimeException | InterruptedException e) {
				status = "Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			}
		}, "ScreenMod-Import");
		thread.setDaemon(true);
		thread.start();
	}

	private static byte[] download(String url) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "ScreenMod-Fabric")
				.GET()
				.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		try (InputStream in = response.body()) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[16384];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				if (out.size() > ServerImageStore.MAX_IMAGE_BYTES) {
					throw new IOException("Image is larger than 8 MB");
				}
			}
			return out.toByteArray();
		}
	}

	private static String baseNameOfUrl(String url) {
		String path = URI.create(url).getPath();
		if (path == null || path.isEmpty() || path.endsWith("/")) {
			return "image";
		}
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private static void enqueue(byte[] data, String baseName) throws IOException {
		if (!ServerImageStore.looksLikeImage(data)) {
			throw new IOException("Only PNG and JPEG images are supported");
		}
		boolean png = (data[0] & 0xFF) == 0x89;
		String base = baseName;
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		base = base.replaceAll("[^A-Za-z0-9_-]", "_");
		if (base.isEmpty()) {
			base = "image";
		}
		if (base.length() > 40) {
			base = base.substring(0, 40);
		}
		String name = base + "-" + shortHash(data) + (png ? ".png" : ".jpg");

		int total = Math.max(1, (data.length + ScreenNetworking.CHUNK_SIZE - 1) / ScreenNetworking.CHUNK_SIZE);
		ArrayDeque<UploadChunkC2S> chunks = new ArrayDeque<>();
		for (int i = 0; i < total; i++) {
			int from = i * ScreenNetworking.CHUNK_SIZE;
			int to = Math.min(data.length, from + ScreenNetworking.CHUNK_SIZE);
			chunks.add(new UploadChunkC2S(name, i, total, Arrays.copyOfRange(data, from, to)));
		}
		Minecraft.getInstance().execute(() -> {
			QUEUE.addAll(chunks);
			status = "Uploading " + name + " ...";
		});
	}

	private static String shortHash(byte[] data) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				sb.append(String.format("%02x", digest[i]));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(Arrays.hashCode(data));
		}
	}
}

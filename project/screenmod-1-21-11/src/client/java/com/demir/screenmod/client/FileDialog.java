package com.demir.screenmod.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import net.minecraft.client.Minecraft;

/** Native "open file" dialog (tinyfd ships with Minecraft's LWJGL). Runs off the render thread. */
public final class FileDialog {
	private FileDialog() {
	}

	public static void pickImage(Consumer<Path> onPicked) {
		Thread thread = new Thread(() -> {
			String result;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filters = stack.mallocPointer(3);
				filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).flip();
				result = TinyFileDialogs.tinyfd_openFileDialog("Select an image", "", filters, "Images (png, jpg)", false);
			} catch (Throwable t) {
				ImageUploader.status = "Error: file dialog unavailable, paste the path instead";
				return;
			}
			if (result != null) {
				String picked = result;
				Minecraft.getInstance().execute(() -> onPicked.accept(Path.of(picked)));
			}
		}, "ScreenMod-FileDialog");
		thread.setDaemon(true);
		thread.start();
	}
}

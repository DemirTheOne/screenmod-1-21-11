package com.demir.screenmod;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent Screen ID -> image name map (one per world, stored with the overworld). */
public class ScreenNetworkData extends SavedData {
	private static final Codec<ScreenNetworkData> CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(ScreenNetworkData::new, data -> data.images);
	private static final SavedDataType<ScreenNetworkData> TYPE =
			new SavedDataType<>("screenmod_networks", ScreenNetworkData::new, CODEC, null);

	private final Map<String, String> images;

	public ScreenNetworkData() {
		this.images = new HashMap<>();
	}

	private ScreenNetworkData(Map<String, String> loaded) {
		this.images = new HashMap<>(loaded);
	}

	public static ScreenNetworkData get(MinecraftServer server) {
		ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);
		if (level == null) {
			return new ScreenNetworkData();
		}
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	@Nullable
	public String get(String screenId) {
		return images.get(screenId);
	}

	public boolean has(String screenId) {
		return images.containsKey(screenId);
	}

	public void set(String screenId, String image) {
		images.put(screenId, image);
		setDirty();
	}
}

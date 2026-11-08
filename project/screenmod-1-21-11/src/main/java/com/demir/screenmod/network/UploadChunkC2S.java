package com.demir.screenmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.demir.screenmod.ScreenMod;

public record UploadChunkC2S(String name, int index, int total, byte[] data) implements CustomPacketPayload {
	public static final Type<UploadChunkC2S> TYPE = new Type<>(ScreenMod.id("upload_chunk"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UploadChunkC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, UploadChunkC2S::name,
			ByteBufCodecs.VAR_INT, UploadChunkC2S::index,
			ByteBufCodecs.VAR_INT, UploadChunkC2S::total,
			ByteBufCodecs.byteArray(ScreenNetworking.CHUNK_SIZE), UploadChunkC2S::data,
			UploadChunkC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

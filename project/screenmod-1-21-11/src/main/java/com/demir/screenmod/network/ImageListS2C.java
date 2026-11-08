package com.demir.screenmod.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.demir.screenmod.ScreenMod;

public record ImageListS2C(List<String> names) implements CustomPacketPayload {
	public static final Type<ImageListS2C> TYPE = new Type<>(ScreenMod.id("image_list"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ImageListS2C> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), ImageListS2C::names,
			ImageListS2C::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

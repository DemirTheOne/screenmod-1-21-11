package com.demir.screenmod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.demir.screenmod.ScreenMod;

/** @param setImage true when the player picked (or cleared) an image in the GUI */
public record SetScreenC2S(BlockPos pos, String screenId, String image, boolean setImage, boolean keepAspect)
		implements CustomPacketPayload {
	public static final Type<SetScreenC2S> TYPE = new Type<>(ScreenMod.id("set_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SetScreenC2S> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SetScreenC2S::pos,
			ByteBufCodecs.STRING_UTF8, SetScreenC2S::screenId,
			ByteBufCodecs.STRING_UTF8, SetScreenC2S::image,
			ByteBufCodecs.BOOL, SetScreenC2S::setImage,
			ByteBufCodecs.BOOL, SetScreenC2S::keepAspect,
			SetScreenC2S::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

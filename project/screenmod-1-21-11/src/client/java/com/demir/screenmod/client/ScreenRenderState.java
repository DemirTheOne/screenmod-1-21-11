package com.demir.screenmod.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public class ScreenRenderState extends BlockEntityRenderState {
	public boolean visible;
	public Identifier texture;
	public Direction facing = Direction.NORTH;
	public Direction right = Direction.EAST;
	public Direction up = Direction.UP;
	/** Image rectangle in tile units, relative to the centre of the anchor tile (u along right, v along up). */
	public float u0;
	public float u1;
	public float v0;
	public float v1;
}

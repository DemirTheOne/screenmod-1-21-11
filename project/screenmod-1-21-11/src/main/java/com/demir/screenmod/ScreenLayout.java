package com.demir.screenmod;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multi-block layout helper shared by client and server.
 *
 * Screens that touch each other, share the same facing and lie in the same plane form one display.
 * The display is anchored at its top-left tile (as seen by a viewer in front of it) and owns the data.
 * Invariant: right x up == facing.
 */
public final class ScreenLayout {
	public static final int MAX_SIZE = 64;

	/** @param anchor top-left tile, width/height in tiles, right/up are the screen-space axes */
	public record Rect(BlockPos anchor, int width, int height, Direction facing, Direction right, Direction up) {
	}

	private ScreenLayout() {
	}

	public static Direction rightOf(Direction facing) {
		return switch (facing) {
			case UP -> Direction.EAST;
			case DOWN -> Direction.WEST;
			default -> facing.getCounterClockWise();
		};
	}

	public static Direction upOf(Direction facing) {
		return facing == Direction.UP || facing == Direction.DOWN ? Direction.NORTH : Direction.UP;
	}

	private static boolean isTile(BlockGetter level, BlockPos pos, Direction facing) {
		BlockState state = level.getBlockState(pos);
		return state.is(ModBlocks.SCREEN) && state.getValue(ScreenBlock.FACING) == facing;
	}

	/** Walks left/up from a tile until no further neighbour exists. Used by the server to find the data owner. */
	@Nullable
	public static BlockPos walkToAnchor(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.SCREEN)) {
			return null;
		}
		Direction facing = state.getValue(ScreenBlock.FACING);
		Direction left = rightOf(facing).getOpposite();
		Direction up = upOf(facing);
		BlockPos current = pos;
		for (int i = 0; i < MAX_SIZE * 2; i++) {
			BlockPos l = current.relative(left);
			BlockPos t = current.relative(up);
			if (isTile(level, l, facing)) {
				current = l;
			} else if (isTile(level, t, facing)) {
				current = t;
			} else {
				break;
			}
		}
		return current;
	}

	/**
	 * Cheap renderer test: only a tile with no neighbour to its left and none above is a candidate anchor.
	 * Returns the measured rectangle (1x1 when the group is not a full rectangle), or null for non-anchors.
	 */
	@Nullable
	public static Rect measureIfAnchor(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.SCREEN)) {
			return null;
		}
		Direction facing = state.getValue(ScreenBlock.FACING);
		Direction right = rightOf(facing);
		Direction up = upOf(facing);
		Direction down = up.getOpposite();
		if (isTile(level, pos.relative(right.getOpposite()), facing) || isTile(level, pos.relative(up), facing)) {
			return null;
		}
		int w = 1;
		while (w < MAX_SIZE && isTile(level, pos.relative(right, w), facing)) {
			w++;
		}
		int h = 1;
		while (h < MAX_SIZE && isTile(level, pos.relative(down, h), facing)) {
			h++;
		}
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				if (!isTile(level, pos.relative(right, i).relative(down, j), facing)) {
					return new Rect(pos, 1, 1, facing, right, up);
				}
			}
		}
		return new Rect(pos, w, h, facing, right, up);
	}
}

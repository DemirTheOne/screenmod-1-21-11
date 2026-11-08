package com.demir.screenmod.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.demir.screenmod.ScreenBlockEntity;
import com.demir.screenmod.ScreenLayout;

/** Draws the whole multi-block image from its anchor (top-left) tile; all other tiles draw nothing. */
public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity, ScreenRenderState> {
	private static final float OFFSET = 0.002f;
	private static final int FULL_BRIGHT = 15728880;

	public ScreenRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public ScreenRenderState createRenderState() {
		return new ScreenRenderState();
	}

	@Override
	public void extractRenderState(ScreenBlockEntity entity, ScreenRenderState state, float tickProgress, Vec3 cameraPos,
			@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
		state.visible = false;

		Level level = entity.getLevel();
		if (level == null) {
			return;
		}
		ScreenLayout.Rect rect = entity.getLayout(level);
		if (rect == null) {
			return;
		}
		ClientImageManager.Tex tex = ClientImageManager.get(entity.getShown());
		if (tex == null) {
			return;
		}

		float w = rect.width();
		float h = rect.height();
		float drawW = w;
		float drawH = h;
		if (entity.isKeepAspect() && tex.width() > 0 && tex.height() > 0) {
			float imageAspect = (float) tex.width() / tex.height();
			if (imageAspect > w / h) {
				drawH = w / imageAspect;
			} else {
				drawW = h * imageAspect;
			}
		}
		float centerU = (w - 1f) / 2f;
		float centerV = 0.5f - h / 2f;

		state.visible = true;
		state.texture = tex.id();
		state.facing = rect.facing();
		state.right = rect.right();
		state.up = rect.up();
		state.u0 = centerU - drawW / 2f;
		state.u1 = centerU + drawW / 2f;
		state.v0 = centerV - drawH / 2f;
		state.v1 = centerV + drawH / 2f;
	}

	@Override
	public void submit(ScreenRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
		if (!state.visible || state.texture == null) {
			return;
		}
		// The callback runs later, so snapshot everything it needs.
		final Direction facing = state.facing;
		final Direction right = state.right;
		final Direction up = state.up;
		final float u0 = state.u0;
		final float u1 = state.u1;
		final float v0 = state.v0;
		final float v1 = state.v1;
		final Identifier texture = state.texture;

		collector.submitCustomGeometry(poseStack, RenderTypes.text(texture), (pose, consumer) -> {
			// top-left, bottom-left, bottom-right, top-right as seen by a viewer in front of the screen
			vertex(consumer, pose, facing, right, up, u0, v1, 0f, 0f);
			vertex(consumer, pose, facing, right, up, u0, v0, 0f, 1f);
			vertex(consumer, pose, facing, right, up, u1, v0, 1f, 1f);
			vertex(consumer, pose, facing, right, up, u1, v1, 1f, 0f);
		});
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Direction facing, Direction right, Direction up,
			float u, float v, float texU, float texV) {
		float d = 0.5f + OFFSET;
		float x = 0.5f + facing.getStepX() * d + right.getStepX() * u + up.getStepX() * v;
		float y = 0.5f + facing.getStepY() * d + right.getStepY() * u + up.getStepY() * v;
		float z = 0.5f + facing.getStepZ() * d + right.getStepZ() * u + up.getStepZ() * v;
		consumer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(texU, texV)
				.setLight(FULL_BRIGHT)
				.setNormal(pose, facing.getStepX(), facing.getStepY(), facing.getStepZ());
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 192;
	}
}

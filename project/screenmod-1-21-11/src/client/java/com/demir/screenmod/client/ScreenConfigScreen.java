package com.demir.screenmod.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.demir.screenmod.ScreenBlockEntity;
import com.demir.screenmod.ScreenLayout;
import com.demir.screenmod.network.ListRequestC2S;
import com.demir.screenmod.network.SetScreenC2S;

/** Plain vanilla settings screen: Screen ID, image import (file or URL), server image list, aspect ratio. */
public class ScreenConfigScreen extends Screen {
	private static final int ROWS = 4;
	private static final int PANEL_WIDTH = 280;

	private final BlockPos pos;
	private String idText = "";
	private String sourceText = "";
	private String selected = "";
	private boolean selectionChanged;
	private boolean keepAspect = true;
	private int page;
	private int seenListVersion = -1;

	private EditBox idBox;
	private EditBox sourceBox;

	public ScreenConfigScreen(BlockPos pos) {
		super(Component.translatable("screenmod.gui.title"));
		this.pos = pos;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			BlockPos anchor = ScreenLayout.walkToAnchor(minecraft.level, pos);
			BlockEntity be = anchor == null ? null : minecraft.level.getBlockEntity(anchor);
			if (be instanceof ScreenBlockEntity screen) {
				idText = screen.getScreenId();
				selected = screen.getShown();
				keepAspect = screen.isKeepAspect();
			}
		}
		if (ClientPlayNetworking.canSend(ListRequestC2S.TYPE)) {
			ClientPlayNetworking.send(new ListRequestC2S());
		}
	}

	@Override
	protected void init() {
		int left = this.width / 2 - PANEL_WIDTH / 2;
		int top = 6;

		idBox = new EditBox(this.font, left, top + 22, PANEL_WIDTH, 18, Component.translatable("screenmod.gui.screen_id"));
		idBox.setMaxLength(32);
		idBox.setValue(idText);
		idBox.setResponder(text -> idText = text);
		this.addRenderableWidget(idBox);

		sourceBox = new EditBox(this.font, left, top + 54, PANEL_WIDTH - 150, 18, Component.translatable("screenmod.gui.source"));
		sourceBox.setMaxLength(1024);
		sourceBox.setValue(sourceText);
		sourceBox.setResponder(text -> sourceText = text);
		this.addRenderableWidget(sourceBox);

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.browse"), button ->
				FileDialog.pickImage(path -> {
					sourceText = path.toString();
					if (sourceBox != null) {
						sourceBox.setValue(sourceText);
					}
					ImageUploader.uploadFromSource(sourceText);
				})).bounds(left + PANEL_WIDTH - 146, top + 54, 72, 18).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.import"), button -> {
			if (!sourceText.isBlank()) {
				ImageUploader.uploadFromSource(sourceText);
			}
		}).bounds(left + PANEL_WIDTH - 72, top + 54, 72, 18).build());

		List<String> images = ClientImageManager.getServerImages();
		int maxPage = Math.max(0, (images.size() - 1) / ROWS);
		page = Math.min(page, maxPage);
		for (int i = 0; i < ROWS; i++) {
			int index = page * ROWS + i;
			if (index >= images.size()) {
				break;
			}
			String name = images.get(index);
			String label = (name.equals(selected) ? "> " : "") + this.font.plainSubstrByWidth(name, PANEL_WIDTH - 24);
			this.addRenderableWidget(Button.builder(Component.literal(label), button -> {
				selected = name;
				selectionChanged = true;
				this.rebuildWidgets();
			}).bounds(left, top + 86 + i * 19, PANEL_WIDTH, 18).build());
		}

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.prev"), button -> {
			page = Math.max(0, page - 1);
			this.rebuildWidgets();
		}).bounds(left, top + 164, 40, 18).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.none"), button -> {
			selected = "";
			selectionChanged = true;
			this.rebuildWidgets();
		}).bounds(left + 46, top + 164, PANEL_WIDTH - 92, 18).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.next"), button -> {
			if ((page + 1) * ROWS < ClientImageManager.getServerImages().size()) {
				page++;
			}
			this.rebuildWidgets();
		}).bounds(left + PANEL_WIDTH - 40, top + 164, 40, 18).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable(keepAspect ? "screenmod.gui.aspect_on" : "screenmod.gui.aspect_off"), button -> {
					keepAspect = !keepAspect;
					this.rebuildWidgets();
				}).bounds(left, top + 186, PANEL_WIDTH, 18).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.apply"), button -> {
			if (ClientPlayNetworking.canSend(SetScreenC2S.TYPE)) {
				ClientPlayNetworking.send(new SetScreenC2S(pos, idText, selected, selectionChanged, keepAspect));
			}
			this.onClose();
		}).bounds(left, top + 208, PANEL_WIDTH / 2 - 2, 18).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screenmod.gui.cancel"), button ->
				this.onClose()).bounds(left + PANEL_WIDTH / 2 + 2, top + 208, PANEL_WIDTH / 2 - 2, 18).build());
	}

	@Override
	public void tick() {
		super.tick();
		int version = ClientImageManager.getListVersion();
		if (version != seenListVersion) {
			seenListVersion = version;
			this.rebuildWidgets();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		int left = this.width / 2 - PANEL_WIDTH / 2;
		int top = 6;
		String title = this.title.getString();
		graphics.drawString(this.font, title, this.width / 2 - this.font.width(title) / 2, top, 0xFFFFFFFF, true);
		graphics.drawString(this.font, Component.translatable("screenmod.gui.screen_id").getString(), left, top + 12, 0xFFA0A0A0, true);
		graphics.drawString(this.font, Component.translatable("screenmod.gui.source").getString(), left, top + 44, 0xFFA0A0A0, true);
		graphics.drawString(this.font, Component.translatable("screenmod.gui.images").getString(), left, top + 76, 0xFFA0A0A0, true);
		String status = ImageUploader.status;
		if (status != null && !status.isEmpty()) {
			graphics.drawString(this.font, this.font.plainSubstrByWidth(status, PANEL_WIDTH), left, top + 232, 0xFFFFFF55, true);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

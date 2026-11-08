package com.demir.screenmod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ScreenBlockEntity extends BlockEntity {
	/** All loaded server-side screen block entities. Used to refresh Screen ID networks. */
	private static final Set<ScreenBlockEntity> SERVER_INSTANCES = ConcurrentHashMap.newKeySet();

	private String screenId = "";
	private String image = "";
	private boolean keepAspect = true;
	/** Effective image sent to clients (network image when a Screen ID is set). Not saved to disk. */
	private String shown = "";

	// client-side layout cache
	private ScreenLayout.Rect cachedRect;
	private long cachedRectTime = Long.MIN_VALUE;

	public ScreenBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SCREEN_ENTITY, pos, state);
	}

	public String getScreenId() {
		return screenId;
	}

	public String getImage() {
		return image;
	}

	public boolean isKeepAspect() {
		return keepAspect;
	}

	/** Image the client should draw / the GUI should show as current. */
	public String getShown() {
		return shown;
	}

	public void setScreenId(String screenId) {
		this.screenId = screenId;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public void setKeepAspect(boolean keepAspect) {
		this.keepAspect = keepAspect;
	}

	/** Client only: measured rectangle if this tile is the anchor of a display, cached for one second. */
	@Nullable
	public ScreenLayout.Rect getLayout(Level level) {
		long now = level.getGameTime();
		if (cachedRectTime == Long.MIN_VALUE || now - cachedRectTime >= 20 || now < cachedRectTime) {
			cachedRect = ScreenLayout.measureIfAnchor(level, worldPosition);
			cachedRectTime = now;
		}
		return cachedRect;
	}

	/** Server only: pushes current data to tracking clients. */
	public void markSynced() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
		}
	}

	/** Server only: re-sync every loaded screen with the given Screen ID. */
	public static void refreshNetwork(String screenId) {
		for (ScreenBlockEntity be : SERVER_INSTANCES) {
			if (be.screenId.equals(screenId) && !be.isRemoved()) {
				be.markSynced();
			}
		}
	}

	public static void clearServerRegistry() {
		SERVER_INSTANCES.clear();
	}

	private String effectiveImage() {
		if (level instanceof ServerLevel serverLevel && !screenId.isEmpty()) {
			String networkImage = ScreenNetworkData.get(serverLevel.getServer()).get(screenId);
			if (networkImage != null) {
				return networkImage;
			}
		}
		return image;
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level != null && !level.isClientSide()) {
			SERVER_INSTANCES.add(this);
		}
	}

	@Override
	public void setRemoved() {
		SERVER_INSTANCES.remove(this);
		super.setRemoved();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("ScreenId", screenId);
		output.putString("Image", image);
		output.putBoolean("KeepAspect", keepAspect);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		screenId = input.getStringOr("ScreenId", "");
		image = input.getStringOr("Image", "");
		keepAspect = input.getBooleanOr("KeepAspect", true);
		shown = input.getStringOr("Shown", image);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = saveWithoutMetadata(registries);
		tag.putString("Shown", effectiveImage());
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}

package com.demir.screenmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

public final class ModBlocks {
	public static final ResourceKey<Block> SCREEN_KEY =
			ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ScreenMod.MOD_ID, "screen"));
	public static final ResourceKey<Item> SCREEN_ITEM_KEY =
			ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ScreenMod.MOD_ID, "screen"));

	public static final Block SCREEN = Registry.register(
			BuiltInRegistries.BLOCK,
			SCREEN_KEY,
			new ScreenBlock(BlockBehaviour.Properties.of()
					.strength(1.5f, 6.0f)
					.sound(SoundType.METAL)
					.setId(SCREEN_KEY)));

	static {
		Registry.register(
				BuiltInRegistries.ITEM,
				SCREEN_ITEM_KEY,
				new BlockItem(SCREEN, new Item.Properties().setId(SCREEN_ITEM_KEY).useBlockDescriptionPrefix()));
	}

	public static final BlockEntityType<ScreenBlockEntity> SCREEN_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(ScreenMod.MOD_ID, "screen"),
			FabricBlockEntityTypeBuilder.<ScreenBlockEntity>create(ScreenBlockEntity::new, SCREEN).build());

	private ModBlocks() {
	}

	/** Forces class initialisation (and therefore registration). */
	public static void initialize() {
	}
}

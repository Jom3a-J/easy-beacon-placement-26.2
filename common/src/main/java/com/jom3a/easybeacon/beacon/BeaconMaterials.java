package com.jom3a.easybeacon.beacon;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks count as beacon base material, and how many of them the player is carrying.
 *
 * <p>Membership is decided entirely by the {@code #minecraft:beacon_base_blocks} block tag, so
 * modded base blocks added to that tag work without this mod knowing anything about them.
 */
public final class BeaconMaterials {
	/** Hotbar slots are the first nine entries of the inventory's non-equipment item list. */
	public static final int HOTBAR_SIZE = 9;

	private BeaconMaterials() {
	}

	public static boolean isBeaconBase(BlockState state) {
		return state.is(BlockTags.BEACON_BASE_BLOCKS);
	}

	public static boolean isBeaconBase(Block block) {
		return isBeaconBase(block.defaultBlockState());
	}

	/** The block this stack would place, or {@code null} if it is not a beacon base block. */
	public static Block baseBlockOf(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return null;
		}

		Block block = blockItem.getBlock();
		return isBeaconBase(block) ? block : null;
	}

	/** Total beacon base blocks carried anywhere in the main inventory, including the hotbar. */
	public static int countInInventory(Inventory inventory) {
		int total = 0;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (baseBlockOf(stack) != null) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** Total beacon base blocks in the hotbar, which is all the placer can actually reach. */
	public static int countInHotbar(Inventory inventory) {
		int total = 0;

		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (baseBlockOf(stack) != null) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/**
	 * The block the preview should show as a ghost: whatever base block the player would actually
	 * place first. Prefers the hotbar, since that is all the placer can use, and falls back to
	 * the rest of the inventory so the preview still shows something useful.
	 */
	public static Block previewBlock(Inventory inventory) {
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			Block block = baseBlockOf(inventory.getItem(slot));

			if (block != null) {
				return block;
			}
		}

		for (int slot = HOTBAR_SIZE; slot < inventory.getContainerSize(); slot++) {
			Block block = baseBlockOf(inventory.getItem(slot));

			if (block != null) {
				return block;
			}
		}

		return null;
	}

	/** First hotbar slot holding a beacon base block, or {@code -1} if the hotbar has none. */
	public static int findHotbarSlot(Inventory inventory) {
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (baseBlockOf(inventory.getItem(slot)) != null) {
				return slot;
			}
		}

		return -1;
	}
}

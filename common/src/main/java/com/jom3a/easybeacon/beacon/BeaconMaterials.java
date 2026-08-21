package com.jom3a.easybeacon.beacon;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks count as beacon base material, and how many of them the player is carrying.
 *
 * <p>Membership is decided entirely by the {@code #minecraft:beacon_base_blocks} block tag, so
 * modded base blocks added to that tag work without this mod knowing anything about them.
 *
 * <h2>Hotbar, offhand, backpack</h2>
 *
 * <p>Three different totals matter, because the two placement paths can reach different things.
 * The server-side builder empties the whole inventory; the client-side placer can only use what is
 * in a hand, which means the hotbar plus the offhand. Sizing a preview to blocks the placer cannot
 * reach is what leaves a pyramid with holes in it, so the caller picks the right one — see
 * {@link PlacementPlan#compute(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos,
 * Player, boolean, int)}.
 */
public final class BeaconMaterials {
	/** Hotbar slots are the first entries of the inventory, and vanilla owns how many there are. */
	public static final int HOTBAR_SIZE = Inventory.SELECTION_SIZE;

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

	/** Total beacon base blocks carried anywhere in the inventory, hotbar and offhand included. */
	public static int countInInventory(Inventory inventory) {
		return countBetween(inventory, 0, inventory.getContainerSize());
	}

	/**
	 * Total beacon base blocks the client-side placer can actually reach: the hotbar, plus the
	 * offhand, which it can place from without switching slots at all.
	 */
	public static int countReachable(Player player) {
		int offhand = baseBlockOf(player.getOffhandItem()) != null
				? player.getOffhandItem().getCount()
				: 0;

		return countBetween(player.getInventory(), 0, HOTBAR_SIZE) + offhand;
	}

	private static int countBetween(Inventory inventory, int fromSlot, int toSlot) {
		int total = 0;

		for (int slot = fromSlot; slot < toSlot; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (baseBlockOf(stack) != null) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/**
	 * The block the preview should show as a ghost: whatever base block the player would actually
	 * place first. Prefers what is already reachable — hotbar, then offhand — and falls back to the
	 * rest of the inventory so the preview still shows something useful when the server is building.
	 */
	public static Block previewBlock(Player player) {
		Inventory inventory = player.getInventory();
		int hotbarSlot = findHotbarSlot(inventory);

		if (hotbarSlot >= 0) {
			return baseBlockOf(inventory.getItem(hotbarSlot));
		}

		Block offhand = baseBlockOf(player.getOffhandItem());

		if (offhand != null) {
			return offhand;
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

	/** First hotbar slot holding {@code item}, or {@code -1} if the hotbar has none. */
	public static int findHotbarSlot(Inventory inventory, Item item) {
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (inventory.getItem(slot).is(item)) {
				return slot;
			}
		}

		return -1;
	}
}

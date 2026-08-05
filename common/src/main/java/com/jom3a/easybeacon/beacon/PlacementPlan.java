package com.jom3a.easybeacon.beacon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A resolved decision about what to build at a given beacon position: which tier, and what the
 * mod intends to do with every block of that pyramid.
 *
 * <p>Instances are immutable snapshots. {@link com.jom3a.easybeacon.client.EbpClient} recomputes
 * one per client tick rather than per frame, and the renderer just reads it.
 */
public final class PlacementPlan {
	/** One position in the pyramid, and what will happen to it. */
	public record Slot(BlockPos pos, SlotState state) {
	}

	private final BlockPos beaconPos;
	private final int tier;
	private final List<Slot> slots;
	private final Map<SlotState, Integer> counts;

	private PlacementPlan(BlockPos beaconPos, int tier, List<Slot> slots) {
		this.beaconPos = beaconPos;
		this.tier = tier;
		this.slots = List.copyOf(slots);

		Map<SlotState, Integer> tally = new EnumMap<>(SlotState.class);
		for (SlotState state : SlotState.values()) {
			tally.put(state, 0);
		}
		for (Slot slot : slots) {
			tally.merge(slot.state(), 1, Integer::sum);
		}
		this.counts = Map.copyOf(tally);
	}

	public BlockPos beaconPos() {
		return beaconPos;
	}

	public int tier() {
		return tier;
	}

	public List<Slot> slots() {
		return slots;
	}

	public int count(SlotState state) {
		return counts.getOrDefault(state, 0);
	}

	/** True when terrain is in the way, so the pyramid would not actually power a beacon. */
	public boolean hasObstructions() {
		return count(SlotState.OBSTRUCTED) > 0;
	}

	/**
	 * The tier the beacon will <em>actually</em> end up at once this plan is built.
	 *
	 * <p>A beacon's tier is set by how many complete layers it has counting up from the bottom of
	 * the pyramid, so a single blocked slot in a lower layer caps the whole thing. {@link #tier()}
	 * is what the preview is sized to; this is what you will really get, and returns {@code 0} if
	 * even the first layer cannot be completed.
	 *
	 * <p>This is the number worth showing the player: the pyramid is usually far too big to check
	 * by eye, and much of it sits outside their field of view.
	 */
	public int effectiveTier() {
		boolean[] layerBlocked = new boolean[BeaconPyramid.MAX_TIER + 2];

		for (Slot slot : slots) {
			int layer = beaconPos.getY() - slot.pos().getY();

			if (layer < 1 || layer >= layerBlocked.length) {
				continue;
			}

			if (slot.state() == SlotState.OBSTRUCTED || slot.state() == SlotState.MISSING_MATERIAL) {
				layerBlocked[layer] = true;
			}
		}

		int effective = 0;

		// Layers have to be complete from the bottom up; the first gap caps the tier.
		for (int layer = 1; layer <= tier; layer++) {
			if (layerBlocked[layer]) {
				break;
			}

			effective = layer;
		}

		return effective;
	}

	/** True when building this plan yields the tier the preview is showing. */
	public boolean isFullyBuildable() {
		return effectiveTier() == tier;
	}

	/** Positions that still need a block placed, in bottom-up order. */
	public List<BlockPos> positionsToPlace() {
		List<BlockPos> out = new ArrayList<>(count(SlotState.PLACEABLE));

		for (Slot slot : slots) {
			if (slot.state() == SlotState.PLACEABLE) {
				out.add(slot.pos());
			}
		}

		return out;
	}

	/**
	 * Work out the best pyramid to show at {@code beaconPos}.
	 *
	 * <p>The tier is the largest one the player can pay for, ignoring obstructions. Obstructed
	 * slots are deliberately still reported (in red) rather than silently shrinking the tier,
	 * because the useful feedback is "here is what is in your way", not a quietly smaller pyramid.
	 *
	 * @param available how many base blocks the player can spend
	 * @param maxTier   upper bound from config
	 */
	public static PlacementPlan compute(BlockGetter level, BlockPos beaconPos, int available, int maxTier) {
		int clampedMax = Math.clamp(maxTier, BeaconPyramid.MIN_TIER, BeaconPyramid.MAX_TIER);
		int chosenTier = BeaconPyramid.MIN_TIER;

		// Walk down from the biggest pyramid to the first one the player can actually fill.
		for (int tier = clampedMax; tier >= BeaconPyramid.MIN_TIER; tier--) {
			if (neededFor(level, beaconPos, tier) <= available) {
				chosenTier = tier;
				break;
			}
		}

		List<BlockPos> positions = BeaconPyramid.basePositions(beaconPos, chosenTier);
		List<Slot> slots = new ArrayList<>(positions.size());
		int budget = available;

		for (BlockPos pos : positions) {
			BlockState state = level.getBlockState(pos);
			SlotState slotState;

			if (BeaconMaterials.isBeaconBase(state)) {
				slotState = SlotState.ALREADY_VALID;
			} else if (!state.canBeReplaced()) {
				slotState = SlotState.OBSTRUCTED;
			} else if (budget > 0) {
				slotState = SlotState.PLACEABLE;
				budget--;
			} else {
				slotState = SlotState.MISSING_MATERIAL;
			}

			slots.add(new Slot(pos, slotState));
		}

		return new PlacementPlan(beaconPos, chosenTier, slots);
	}

	/** How many blocks the player would have to supply to complete this tier as it stands. */
	private static int neededFor(BlockGetter level, BlockPos beaconPos, int tier) {
		int needed = 0;

		for (BlockPos pos : BeaconPyramid.basePositions(beaconPos, tier)) {
			BlockState state = level.getBlockState(pos);

			// Blocks already in place cost nothing, and obstructed slots cannot be filled at all,
			// so neither counts against the player's material budget.
			if (!BeaconMaterials.isBeaconBase(state) && state.canBeReplaced()) {
				needed++;
			}
		}

		return needed;
	}

	/** Convenience overload that reads the budget straight off the player's inventory. */
	public static PlacementPlan compute(BlockGetter level, BlockPos beaconPos, Inventory inventory,
			boolean hotbarOnly, int maxTier) {
		int available = hotbarOnly
				? BeaconMaterials.countInHotbar(inventory)
				: BeaconMaterials.countInInventory(inventory);

		return compute(level, beaconPos, available, maxTier);
	}
}

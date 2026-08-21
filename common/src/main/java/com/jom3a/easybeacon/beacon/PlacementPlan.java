package com.jom3a.easybeacon.beacon;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A resolved decision about what to build at a given beacon position: which tier, and what the
 * mod intends to do with every block of that pyramid.
 *
 * <p>Instances are immutable snapshots, and everything worth knowing about one is worked out while
 * it is built. {@link com.jom3a.easybeacon.client.EbpClient} recomputes a plan per client tick and
 * the renderer reads the same one every frame, so the reads have to be free and the build itself
 * has to be cheap.
 */
public final class PlacementPlan {
	/** One position in the pyramid, and what will happen to it. */
	public record Slot(BlockPos pos, SlotState state) {
	}

	private static final int STATE_COUNT = SlotState.values().length;

	private final BlockPos beaconPos;
	private final int tier;
	private final int effectiveTier;
	private final List<Slot> slots;
	private final int[] counts;

	private PlacementPlan(BlockPos beaconPos, int tier, List<Slot> slots) {
		this.beaconPos = beaconPos;
		this.tier = tier;
		this.slots = List.copyOf(slots);
		this.counts = new int[STATE_COUNT];

		// A beacon's tier is how many complete layers sit immediately beneath it: tier n needs
		// layers 1..n, numbered downwards from the beacon. So it is the blocked layer *nearest the
		// beacon* that caps the result - a hole in the bottom layer of a tier-4 pyramid still
		// leaves a working tier 3, while a hole directly under the beacon leaves nothing at all.
		// "Nearest" is the smallest layer number, hence the min. Tracking it here costs nothing on
		// top of the tally that has to happen anyway.
		int nearestBlockedLayer = Integer.MAX_VALUE;

		for (Slot slot : this.slots) {
			counts[slot.state().ordinal()]++;

			if (slot.state() == SlotState.OBSTRUCTED || slot.state() == SlotState.MISSING_MATERIAL) {
				nearestBlockedLayer = Math.min(nearestBlockedLayer, beaconPos.getY() - slot.pos().getY());
			}
		}

		this.effectiveTier = Math.min(tier, nearestBlockedLayer - 1);
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
		return counts[state.ordinal()];
	}

	/** True when terrain is in the way, so the pyramid would not actually power a beacon. */
	public boolean hasObstructions() {
		return count(SlotState.OBSTRUCTED) > 0;
	}

	/**
	 * The tier the beacon will <em>actually</em> end up at once this plan is built.
	 *
	 * <p>A single blocked slot in a lower layer caps the whole thing. {@link #tier()} is what the
	 * preview is sized to; this is what you will really get, and is {@code 0} if even the first
	 * layer cannot be completed.
	 *
	 * <p>This is the number worth showing the player: the pyramid is usually far too big to check
	 * by eye, and much of it sits outside their field of view.
	 */
	public int effectiveTier() {
		return effectiveTier;
	}

	/** True when building this plan yields the tier the preview is showing. */
	public boolean isFullyBuildable() {
		return effectiveTier == tier;
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
		return compute(readTerrain(level), beaconPos, available, maxTier);
	}

	/**
	 * What the world already holds at one position, before the player's material budget is
	 * considered.
	 *
	 * <p>Answering this means reading a block state, which means block tags, which means a loaded
	 * datapack — so asking the question directly is what would otherwise force every test of the
	 * planning arithmetic to boot most of the game. Taking the answer as a parameter keeps
	 * {@link #compute(TerrainReader, BlockPos, int, int)} a pure function of geometry and budget,
	 * which is the half with the interesting edge cases.
	 */
	@FunctionalInterface
	public interface TerrainReader {
		/**
		 * @return {@link SlotState#ALREADY_VALID} when a base block is in place,
		 *         {@link SlotState#OBSTRUCTED} when something is in the way, or
		 *         {@link SlotState#PLACEABLE} when the space is free. Never
		 *         {@link SlotState#MISSING_MATERIAL} — that is a verdict on the player's inventory
		 *         rather than on the terrain, and {@code compute} is the one that reaches it.
		 */
		SlotState at(BlockPos pos);
	}

	/** How the live world answers {@link TerrainReader}. */
	private static TerrainReader readTerrain(BlockGetter level) {
		return pos -> {
			BlockState state = level.getBlockState(pos);

			if (BeaconMaterials.isBeaconBase(state)) {
				// Already in place: costs nothing.
				return SlotState.ALREADY_VALID;
			}

			// Obstructed slots cannot be filled at all, so they cost nothing either.
			return state.canBeReplaced() ? SlotState.PLACEABLE : SlotState.OBSTRUCTED;
		};
	}

	/**
	 * The planning itself, over whatever {@code terrain} reports.
	 *
	 * @see #compute(BlockGetter, BlockPos, int, int) for the version that reads a real world
	 */
	public static PlacementPlan compute(TerrainReader terrain, BlockPos beaconPos, int available, int maxTier) {
		int clampedMax = Math.clamp(maxTier, BeaconPyramid.MIN_TIER, BeaconPyramid.MAX_TIER);

		List<BlockPos> positions = BeaconPyramid.basePositions(beaconPos, clampedMax);
		int total = positions.size();

		// Classify every slot of the largest candidate pyramid in one pass. Picking the tier first
		// would mean re-reading the terrain once per tier tried, and this runs every client tick.
		SlotState[] states = new SlotState[total];
		int[] costPerLayer = new int[clampedMax + 1];
		int index = 0;

		for (int layer = clampedMax; layer >= BeaconPyramid.MIN_TIER; layer--) {
			int width = BeaconPyramid.baseWidth(layer);

			for (int end = index + width * width; index < end; index++) {
				SlotState state = terrain.at(positions.get(index));

				states[index] = state;

				// Free space is the only kind of slot the player has to pay for.
				if (state == SlotState.PLACEABLE) {
					costPerLayer[layer]++;
				}
			}
		}

		int chosenTier = chooseTier(costPerLayer, clampedMax, available);

		// Layers were scanned widest first, so the chosen pyramid is exactly the tail of the scan.
		int from = total - BeaconPyramid.blockCount(chosenTier);
		List<Slot> slots = new ArrayList<>(total - from);
		int budget = available;

		for (int i = from; i < total; i++) {
			SlotState state = states[i];

			// Spend the budget bottom-up, so a shortfall shows at the top where it is visible
			// rather than hollowing out a layer the player cannot see.
			if (state == SlotState.PLACEABLE) {
				if (budget > 0) {
					budget--;
				} else {
					state = SlotState.MISSING_MATERIAL;
				}
			}

			slots.add(new Slot(positions.get(i), state));
		}

		return new PlacementPlan(beaconPos, chosenTier, slots);
	}

	/**
	 * The largest tier whose free slots the player can pay for, falling back to
	 * {@link BeaconPyramid#MIN_TIER} when they cannot afford even that.
	 */
	private static int chooseTier(int[] costPerLayer, int clampedMax, int available) {
		int chosen = BeaconPyramid.MIN_TIER;
		int cumulative = 0;

		// Layers stack from the bottom up, so tier n costs whatever layers 1..n cost. That is
		// monotonic, so the last tier that fits is the largest one that fits.
		for (int tier = BeaconPyramid.MIN_TIER; tier <= clampedMax; tier++) {
			cumulative += costPerLayer[tier];

			if (cumulative <= available) {
				chosen = tier;
			}
		}

		return chosen;
	}

	/**
	 * Convenience overload that reads the budget straight off the player.
	 *
	 * @param reachableOnly count only what the client-side placer can reach — the hotbar and the
	 *                      offhand — rather than the whole inventory. The server-side builder can
	 *                      empty the backpack too, so this is false whenever it will be doing the
	 *                      work; sizing a preview to blocks the placer cannot reach is what leaves
	 *                      a pyramid unfinished.
	 */
	public static PlacementPlan compute(BlockGetter level, BlockPos beaconPos, Player player,
			boolean reachableOnly, int maxTier) {
		int available = reachableOnly
				? BeaconMaterials.countReachable(player)
				: BeaconMaterials.countInInventory(player.getInventory());

		return compute(level, beaconPos, available, maxTier);
	}
}

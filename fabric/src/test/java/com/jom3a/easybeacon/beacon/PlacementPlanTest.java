package com.jom3a.easybeacon.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.jom3a.easybeacon.beacon.PlacementPlan.TerrainReader;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The planning arithmetic: which tier gets chosen, and what happens to each slot.
 *
 * <p>These are the cases the client game tests cannot cheaply reach. Those run with a creative
 * inventory against terrain that is either wholly free or wholly obstructed, so the branch that
 * decides <em>"you cannot afford tier 4, drop to tier 2"</em> never runs there and
 * {@code MISSING_MATERIAL} is only ever asserted to be zero.
 *
 * <p>{@link TerrainReader} is what makes this possible without booting the game — see its javadoc.
 */
class PlacementPlanTest {
	private static final BlockPos BEACON = new BlockPos(0, 100, 0);

	/** Layer of the pyramid a position belongs to: 1 is directly under the beacon. */
	private static int layerOf(BlockPos pos) {
		return BEACON.getY() - pos.getY();
	}

	private static final TerrainReader ALL_FREE = pos -> SlotState.PLACEABLE;
	private static final TerrainReader ALL_BUILT = pos -> SlotState.ALREADY_VALID;

	private static TerrainReader blockedInLayer(int layer) {
		return pos -> layerOf(pos) == layer ? SlotState.OBSTRUCTED : SlotState.PLACEABLE;
	}

	// --- tier selection ---------------------------------------------------------------------

	@Test
	@DisplayName("picks the largest tier the budget covers")
	void picksLargestAffordableTier() {
		assertEquals(4, PlacementPlan.compute(ALL_FREE, BEACON, 164, 4).tier(), "exactly enough for 4");
		assertEquals(3, PlacementPlan.compute(ALL_FREE, BEACON, 163, 4).tier(), "one short of 4");
		assertEquals(3, PlacementPlan.compute(ALL_FREE, BEACON, 83, 4).tier(), "exactly enough for 3");
		assertEquals(2, PlacementPlan.compute(ALL_FREE, BEACON, 82, 4).tier(), "one short of 3");
		assertEquals(2, PlacementPlan.compute(ALL_FREE, BEACON, 34, 4).tier(), "exactly enough for 2");
		assertEquals(1, PlacementPlan.compute(ALL_FREE, BEACON, 33, 4).tier(), "one short of 2");
		assertEquals(1, PlacementPlan.compute(ALL_FREE, BEACON, 9, 4).tier(), "exactly enough for 1");
	}

	@Test
	@DisplayName("falls back to tier 1 rather than nothing when you cannot afford even that")
	void tierOneIsTheFloor() {
		// Showing the smallest pyramid with its shortfall marked is more use than showing nothing:
		// the player still learns what they would need.
		PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 0, 4);

		assertEquals(1, plan.tier(), "tier");
		assertEquals(9, plan.count(SlotState.MISSING_MATERIAL), "every slot unaffordable");
		assertEquals(0, plan.count(SlotState.PLACEABLE), "nothing placeable");
		assertEquals(0, plan.effectiveTier(), "will not work at all");
	}

	@Test
	@DisplayName("never exceeds the configured maximum tier")
	void respectsMaxTier() {
		assertEquals(2, PlacementPlan.compute(ALL_FREE, BEACON, 9999, 2).tier(), "capped at 2");
		assertEquals(1, PlacementPlan.compute(ALL_FREE, BEACON, 9999, 1).tier(), "capped at 1");
		assertEquals(4, PlacementPlan.compute(ALL_FREE, BEACON, 9999, 99).tier(), "clamped down to 4");
		assertEquals(1, PlacementPlan.compute(ALL_FREE, BEACON, 9999, 0).tier(), "clamped up to 1");
		assertEquals(1, PlacementPlan.compute(ALL_FREE, BEACON, 9999, -5).tier(), "negative clamped up");
	}

	@Test
	@DisplayName("blocks already in place cost nothing")
	void alreadyPlacedBlocksAreFree() {
		// The half-built-pyramid case: no materials at all, but a finished tier 4 already there.
		PlacementPlan plan = PlacementPlan.compute(ALL_BUILT, BEACON, 0, 4);

		assertEquals(4, plan.tier(), "affordable because nothing needs buying");
		assertEquals(164, plan.count(SlotState.ALREADY_VALID), "every slot already correct");
		assertEquals(0, plan.count(SlotState.MISSING_MATERIAL), "nothing missing");
		assertEquals(4, plan.effectiveTier(), "a finished pyramid works");
		assertTrue(plan.isFullyBuildable(), "fully buildable");
	}

	@Test
	@DisplayName("obstructions cost nothing but still cap the tier that will work")
	void obstructionsAreFreeButCapTheResult() {
		// Layer 4 fully blocked: 81 slots that cannot be bought, so tier 4 is still "affordable"
		// on the 83 blocks the other three layers need, and the preview deliberately shows it at
		// full size with the blockage marked rather than quietly shrinking.
		PlacementPlan plan = PlacementPlan.compute(blockedInLayer(4), BEACON, 83, 4);

		assertEquals(4, plan.tier(), "tier still sized to 4");
		assertEquals(81, plan.count(SlotState.OBSTRUCTED), "the whole bottom layer");
		assertEquals(3, plan.effectiveTier(), "layers 1-3 are complete, so a tier 3 still lights");
		assertTrue(plan.hasObstructions(), "obstructions reported");
		assertFalse(plan.isFullyBuildable(), "not fully buildable");
	}

	// --- effective tier ---------------------------------------------------------------------

	/**
	 * Which direction the layers count, which is the easiest thing here to get backwards.
	 *
	 * <p>Layer 1 is the 3x3 directly under the beacon and layer 4 is the 9x9 at the bottom. A
	 * beacon needs layers 1..n complete for tier n, so it is the blocked layer <em>nearest the
	 * beacon</em> that caps the result — not the one lowest in the world. Losing the bottom layer
	 * of a tier-4 pyramid still leaves a working tier 3; losing the 3x3 under the beacon leaves
	 * nothing, however perfect everything beneath it is.
	 */
	@Test
	@DisplayName("the blocked layer nearest the beacon caps the tier")
	void nearestBlockedLayerCapsEffectiveTier() {
		assertEquals(3, PlacementPlan.compute(blockedInLayer(4), BEACON, 164, 4).effectiveTier(),
				"bottom layer blocked, tier 3 survives");
		assertEquals(2, PlacementPlan.compute(blockedInLayer(3), BEACON, 164, 4).effectiveTier(), "layer 3");
		assertEquals(1, PlacementPlan.compute(blockedInLayer(2), BEACON, 164, 4).effectiveTier(), "layer 2");
		assertEquals(0, PlacementPlan.compute(blockedInLayer(1), BEACON, 164, 4).effectiveTier(),
				"the 3x3 under the beacon blocked, nothing works");
	}

	@Test
	@DisplayName("a blockage nearer the beacon wins over one further away")
	void nearestBlockageWinsWhenSeveralAreBlocked() {
		TerrainReader twoBlocked = pos -> {
			int layer = layerOf(pos);
			return layer == 2 || layer == 4 ? SlotState.OBSTRUCTED : SlotState.PLACEABLE;
		};

		assertEquals(1, PlacementPlan.compute(twoBlocked, BEACON, 164, 4).effectiveTier(),
				"layer 2 is nearer than layer 4, so it is the one that caps");
	}

	@Test
	@DisplayName("a single blocked slot is enough to cap a layer")
	void oneBlockedSlotCapsTheLayer() {
		BlockPos corner = new BlockPos(BEACON.getX() + 2, BEACON.getY() - 2, BEACON.getZ() + 2);
		TerrainReader oneCorner = pos -> pos.equals(corner) ? SlotState.OBSTRUCTED : SlotState.PLACEABLE;

		PlacementPlan plan = PlacementPlan.compute(oneCorner, BEACON, 164, 4);

		assertEquals(1, plan.count(SlotState.OBSTRUCTED), "exactly one obstruction");
		assertEquals(1, plan.effectiveTier(), "layer 2 incomplete, so only layer 1 counts");
	}

	@Test
	@DisplayName("running out of material caps the tier just as an obstruction does")
	void missingMaterialCapsEffectiveTier() {
		// Enough for tier 3 (83) plus part of what tier 4 would need, but tier 4 is unaffordable,
		// so the plan sizes to 3 and completes it.
		PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 100, 4);

		assertEquals(3, plan.tier(), "tier");
		assertEquals(0, plan.count(SlotState.MISSING_MATERIAL), "tier 3 is fully affordable");
		assertEquals(3, plan.effectiveTier(), "so it reaches tier 3");
	}

	// --- budget spending --------------------------------------------------------------------

	/**
	 * The plan never sizes itself to something it cannot pay for, so a shortfall is not a normal
	 * outcome — it is what happens only when even the smallest pyramid is out of reach.
	 *
	 * <p>Worth stating as a test because it is easy to assume otherwise: give a tier-2 preview 30 of
	 * the 34 blocks it needs and you do not get a tier 2 with four gaps, you get a complete tier 1.
	 */
	@Test
	@DisplayName("a partial budget shrinks the tier rather than leaving gaps")
	void underfundedPlansShrinkInsteadOfGapping() {
		PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 30, 2);

		assertEquals(1, plan.tier(), "dropped to the tier it can complete");
		assertEquals(9, plan.count(SlotState.PLACEABLE), "and completes it");
		assertEquals(0, plan.count(SlotState.MISSING_MATERIAL), "with nothing missing");
		assertTrue(plan.isFullyBuildable(), "fully buildable");
	}

	@Test
	@DisplayName("whatever the budget, the chosen tier is one the player can finish")
	void chosenTierIsAlwaysAffordable() {
		for (int available = 0; available <= 200; available++) {
			PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, available, 4);
			int needed = BeaconPyramid.blockCount(plan.tier());

			// The one exception is the floor: below nine blocks there is no tier to fall back to.
			if (available >= BeaconPyramid.blockCount(BeaconPyramid.MIN_TIER)) {
				assertTrue(needed <= available,
						"tier " + plan.tier() + " needs " + needed + " with " + available + " available");
				assertEquals(0, plan.count(SlotState.MISSING_MATERIAL), "no gaps at " + available);
			} else {
				assertEquals(BeaconPyramid.MIN_TIER, plan.tier(), "floor at " + available);
			}
		}
	}

	@Test
	@DisplayName("below the floor, the shortfall is marked rather than hidden")
	void shortfallIsMarkedWhenEvenTierOneIsOutOfReach() {
		PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 5, 4);

		assertEquals(1, plan.tier(), "the floor");
		assertEquals(5, plan.count(SlotState.PLACEABLE), "what the budget covers");
		assertEquals(4, plan.count(SlotState.MISSING_MATERIAL), "and what it does not");
		assertEquals(0, plan.effectiveTier(), "an incomplete layer 1 powers nothing");
	}

	@Test
	@DisplayName("obstructed slots do not consume budget")
	void obstructionsDoNotEatTheBudget() {
		// Layer 1 blocked entirely: 9 slots. A tier-1 plan then needs nothing at all.
		PlacementPlan plan = PlacementPlan.compute(blockedInLayer(1), BEACON, 0, 1);

		assertEquals(9, plan.count(SlotState.OBSTRUCTED), "all obstructed");
		assertEquals(0, plan.count(SlotState.MISSING_MATERIAL), "and none reported as unaffordable");
	}

	// --- shape of the result ----------------------------------------------------------------

	@Test
	@DisplayName("every slot of the chosen tier is accounted for exactly once")
	void countsCoverTheWholePyramid() {
		for (int tier = BeaconPyramid.MIN_TIER; tier <= BeaconPyramid.MAX_TIER; tier++) {
			PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 9999, tier);
			int summed = 0;

			for (SlotState state : SlotState.values()) {
				summed += plan.count(state);
			}

			assertEquals(BeaconPyramid.blockCount(tier), summed, "tier " + tier + " total");
			assertEquals(BeaconPyramid.blockCount(tier), plan.slots().size(), "tier " + tier + " slots");
		}
	}

	@Test
	@DisplayName("the plan is centred on the beacon it was asked about")
	void planKeepsItsBeaconPosition() {
		PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 9999, 4);

		assertEquals(BEACON, plan.beaconPos(), "beacon position");

		for (PlacementPlan.Slot slot : plan.slots()) {
			assertTrue(slot.pos().getY() < BEACON.getY(), "every slot is below the beacon: " + slot.pos());
		}
	}

	@Test
	@DisplayName("positionsToPlace returns exactly the placeable slots, bottom-up")
	void positionsToPlaceMatchesPlaceableSlots() {
		PlacementPlan plan = PlacementPlan.compute(ALL_FREE, BEACON, 30, 2);
		List<BlockPos> toPlace = plan.positionsToPlace();

		assertEquals(plan.count(SlotState.PLACEABLE), toPlace.size(), "one position per placeable slot");

		int previousY = toPlace.get(0).getY();

		for (BlockPos pos : toPlace) {
			assertTrue(pos.getY() >= previousY, "bottom-up order: " + pos);
			previousY = pos.getY();
		}
	}

	@Test
	@DisplayName("a mixed pyramid classifies every slot correctly at once")
	void mixedTerrainIsClassifiedCorrectly() {
		// Layer 2 already built, layer 1 obstructed, layers 3 and 4 free.
		TerrainReader mixed = pos -> switch (layerOf(pos)) {
			case 2 -> SlotState.ALREADY_VALID;
			case 1 -> SlotState.OBSTRUCTED;
			default -> SlotState.PLACEABLE;
		};

		PlacementPlan plan = PlacementPlan.compute(mixed, BEACON, 9999, 4);

		assertEquals(4, plan.tier(), "tier");
		assertEquals(25, plan.count(SlotState.ALREADY_VALID), "layer 2 is 5x5");
		assertEquals(9, plan.count(SlotState.OBSTRUCTED), "layer 1 is 3x3");
		assertEquals(130, plan.count(SlotState.PLACEABLE), "layers 3 and 4 are 49 + 81");
		assertEquals(0, plan.effectiveTier(),
				"layer 1 is blocked, so the three good layers beneath it power nothing");
	}
}

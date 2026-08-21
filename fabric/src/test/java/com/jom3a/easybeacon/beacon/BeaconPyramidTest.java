package com.jom3a.easybeacon.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The geometry every other part of the mod is built on.
 *
 * <p>{@link BeaconPyramid} is pure arithmetic over {@link BlockPos}, so it needs no world, no
 * registries and no game client — which is the whole reason it is worth testing here rather than
 * indirectly through a two-minute client game test.
 */
class BeaconPyramidTest {
	private static final BlockPos BEACON = new BlockPos(10, 64, -20);

	@Test
	@DisplayName("tier sizes are 9 / 34 / 83 / 164")
	void blockCountsPerTier() {
		assertEquals(9, BeaconPyramid.blockCount(1), "tier 1");
		assertEquals(34, BeaconPyramid.blockCount(2), "tier 2");
		assertEquals(83, BeaconPyramid.blockCount(3), "tier 3");
		assertEquals(164, BeaconPyramid.blockCount(4), "tier 4");
		assertEquals(0, BeaconPyramid.blockCount(0), "no pyramid at all");
	}

	@Test
	@DisplayName("each layer is (2i+1) blocks across")
	void baseWidthPerTier() {
		assertEquals(3, BeaconPyramid.baseWidth(1), "tier 1");
		assertEquals(5, BeaconPyramid.baseWidth(2), "tier 2");
		assertEquals(7, BeaconPyramid.baseWidth(3), "tier 3");
		assertEquals(9, BeaconPyramid.baseWidth(4), "tier 4");
	}

	@Test
	@DisplayName("block count is the sum of its layers")
	void blockCountAgreesWithBaseWidth() {
		// The lookup table in blockCount could drift from baseWidth without either looking wrong.
		for (int tier = BeaconPyramid.MIN_TIER; tier <= BeaconPyramid.MAX_TIER; tier++) {
			int summed = 0;

			for (int layer = 1; layer <= tier; layer++) {
				int width = BeaconPyramid.baseWidth(layer);
				summed += width * width;
			}

			assertEquals(summed, BeaconPyramid.blockCount(tier), "tier " + tier);
		}
	}

	@Test
	@DisplayName("positions run widest layer first, from the bottom up")
	void positionsAreOrderedWidestLayerFirst() {
		List<BlockPos> positions = BeaconPyramid.basePositions(BEACON, 4);

		assertEquals(164, positions.size(), "total positions");

		// Layer 4 sits lowest, at beaconY - 4, and must come first: it is what the layer above is
		// clicked against, so building in this order always leaves a valid support face.
		assertEquals(BEACON.getY() - 4, positions.get(0).getY(), "first position is the bottom layer");
		assertEquals(BEACON.getY() - 1, positions.get(163).getY(), "last position is the top layer");

		int previousY = positions.get(0).getY();

		for (BlockPos pos : positions) {
			assertTrue(pos.getY() >= previousY, "y never goes back down: " + pos);
			previousY = pos.getY();
		}
	}

	@Test
	@DisplayName("layer i sits at beaconY - i and spans +/-i")
	void everyPositionIsInsideItsLayer() {
		for (BlockPos pos : BeaconPyramid.basePositions(BEACON, 4)) {
			int layer = BEACON.getY() - pos.getY();

			assertTrue(layer >= 1 && layer <= 4, "layer in range for " + pos);
			assertTrue(Math.abs(pos.getX() - BEACON.getX()) <= layer, "x inside layer " + layer + ": " + pos);
			assertTrue(Math.abs(pos.getZ() - BEACON.getZ()) <= layer, "z inside layer " + layer + ": " + pos);
		}
	}

	/**
	 * The invariant {@link PlacementPlan#compute} leans on.
	 *
	 * <p>Rather than scanning the terrain once per candidate tier, {@code compute} scans the largest
	 * pyramid once and then treats the chosen tier as the <em>tail</em> of that scan, starting at
	 * {@code blockCount(max) - blockCount(chosen)}. That is only correct while positions come out
	 * widest layer first. Reordering this method would leave {@code compute} silently building the
	 * wrong layers, and no game test asserts it.
	 */
	@Test
	@DisplayName("a smaller tier is exactly the tail of a larger one")
	void smallerTierIsTheTailOfLarger() {
		List<BlockPos> tier4 = BeaconPyramid.basePositions(BEACON, 4);

		for (int tier = BeaconPyramid.MIN_TIER; tier <= BeaconPyramid.MAX_TIER; tier++) {
			List<BlockPos> expected = BeaconPyramid.basePositions(BEACON, tier);
			int from = tier4.size() - BeaconPyramid.blockCount(tier);

			assertEquals(expected, tier4.subList(from, tier4.size()), "tier " + tier + " as a tail");
		}
	}

	@Test
	@DisplayName("positions are centred on the beacon, not offset by one")
	void positionsAreCentredOnTheBeacon() {
		List<BlockPos> layerOne = BeaconPyramid.basePositions(BEACON, 1);

		assertEquals(9, layerOne.size(), "a tier-1 base is 3x3");
		assertTrue(layerOne.contains(BEACON.below()), "the block directly under the beacon");
		assertTrue(layerOne.contains(BEACON.below().north().west()), "a corner");
		assertTrue(layerOne.contains(BEACON.below().south().east()), "the opposite corner");
	}
}

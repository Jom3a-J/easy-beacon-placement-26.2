package com.jom3a.easybeacon.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wire format shared between the mod and this plugin.
 *
 * <p>Paper cannot see Minecraft's own classes, so {@link PyramidGeometry} decodes a packed
 * {@code BlockPos} by hand. That makes it the one place in the project where a silent mistake is
 * both easy and invisible: get a shift wrong and the plugin builds a pyramid somewhere else
 * entirely, or nowhere, with no error anywhere. Nothing else in the codebase would notice.
 *
 * <p>The packed values below are <em>golden values</em>, produced by running vanilla's real
 * {@code BlockPos.asLong()} over each coordinate rather than by re-deriving the same shifts here.
 * A test that recomputed the encoding would only prove this file agrees with itself; these prove it
 * agrees with the protocol.
 */
class PyramidGeometryTest {
	/** {@code { packed, x, y, z }}, straight out of {@code new BlockPos(x, y, z).asLong()}. */
	private static final long[][] GOLDEN = {
		{ 0L, 0, 0, 0 },
		{ 274877911140L, 1, 100, 1 },
		{ -64L, -1, -64, -1 },
		{ 549755822400L, 2, 320, 2 },
		// The world border, where the 26-bit horizontal fields are closest to overflowing.
		{ 8246332933153423679L, 29999984, 319, 29999984 },
		{ -8246332658275512384L, -29999984, -64, -29999984 },
		{ 33935599077486792L, 123456, 200, -654321 },
		{ -1924145319936L, -7, 0, 7 },
		{ 515396342717910980L, 1875000, -60, -1875000 },
	};

	@Test
	@DisplayName("decodes positions vanilla actually encoded")
	void decodesVanillaPackedPositions() {
		for (long[] row : GOLDEN) {
			long packed = row[0];
			String where = "packed " + packed;

			assertEquals((int) row[1], PyramidGeometry.unpackX(packed), where + " x");
			assertEquals((int) row[2], PyramidGeometry.unpackY(packed), where + " y");
			assertEquals((int) row[3], PyramidGeometry.unpackZ(packed), where + " z");
		}
	}

	@Test
	@DisplayName("negative coordinates sign-extend rather than wrapping")
	void handlesNegativeCoordinates() {
		// Worth calling out separately: the shifts rely on arithmetic right-shift to sign-extend,
		// and a stray >>> instead of >> would pass every non-negative case above.
		long packed = -8246332658275512384L;

		assertEquals(-29999984, PyramidGeometry.unpackX(packed), "x");
		assertEquals(-64, PyramidGeometry.unpackY(packed), "y");
		assertEquals(-29999984, PyramidGeometry.unpackZ(packed), "z");
	}

	@Test
	@DisplayName("block counts match the tiers the mod advertises")
	void blockCountsMatchTiers() {
		assertEquals(9, PyramidGeometry.blockCount(1), "tier 1");
		assertEquals(34, PyramidGeometry.blockCount(2), "tier 2");
		assertEquals(83, PyramidGeometry.blockCount(3), "tier 3");
		assertEquals(164, PyramidGeometry.blockCount(4), "tier 4");
	}

	@Test
	@DisplayName("this plugin and the mod agree on how big a pyramid is")
	void agreesWithTheModOnSize() {
		// The two sides are deliberately separate code, so nothing but a test keeps them in step.
		// Divergence here would mean the plugin charging for a different structure than the client
		// previewed. The mod's own values are asserted in BeaconPyramidTest.
		int[] expected = { 0, 9, 34, 83, 164 };

		for (int tier = PyramidGeometry.MIN_TIER; tier <= PyramidGeometry.MAX_TIER; tier++) {
			assertEquals(expected[tier], PyramidGeometry.blockCount(tier), "tier " + tier);
		}
	}
}

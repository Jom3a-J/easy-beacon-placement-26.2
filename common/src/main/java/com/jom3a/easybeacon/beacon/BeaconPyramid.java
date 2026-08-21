package com.jom3a.easybeacon.beacon;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Pure geometry of a beacon base pyramid.
 *
 * <p>A tier-{@code n} beacon needs {@code n} stepped layers underneath it. Layer {@code i}
 * (counting down from the beacon) sits at {@code beaconY - i} and spans {@code +/-i} on both
 * horizontal axes, so it is {@code (2i+1)} blocks across.
 */
public final class BeaconPyramid {
	public static final int MIN_TIER = 1;
	public static final int MAX_TIER = 4;

	/** Running totals for tiers 0-4, so {@link #blockCount} is a lookup on a per-tick path. */
	private static final int[] BLOCK_COUNTS = blockCounts();

	private BeaconPyramid() {
	}

	private static int[] blockCounts() {
		int[] counts = new int[MAX_TIER + 1];

		for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
			int width = baseWidth(tier);
			counts[tier] = counts[tier - 1] + width * width;
		}

		return counts;
	}

	/**
	 * Total number of base blocks in a complete pyramid of the given tier.
	 * Tiers 1-4 are 9, 34, 83 and 164 blocks.
	 */
	public static int blockCount(int tier) {
		return BLOCK_COUNTS[Math.clamp(tier, 0, MAX_TIER)];
	}

	/** Width, in blocks, of the widest (bottom) layer of a pyramid of the given tier. */
	public static int baseWidth(int tier) {
		return 2 * tier + 1;
	}

	/**
	 * Every base block position for the given tier, widest layer first.
	 *
	 * <p>Bottom-up ordering matters for placement: the lower layers are what the upper ones get
	 * clicked against, so building from the bottom keeps a valid support face available.
	 */
	public static List<BlockPos> basePositions(BlockPos beaconPos, int tier) {
		List<BlockPos> positions = new ArrayList<>(blockCount(tier));

		for (int layer = tier; layer >= 1; layer--) {
			int y = beaconPos.getY() - layer;

			for (int dx = -layer; dx <= layer; dx++) {
				for (int dz = -layer; dz <= layer; dz++) {
					positions.add(new BlockPos(beaconPos.getX() + dx, y, beaconPos.getZ() + dz));
				}
			}
		}

		return positions;
	}
}

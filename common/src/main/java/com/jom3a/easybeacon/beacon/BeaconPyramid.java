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

	private BeaconPyramid() {
	}

	/**
	 * Total number of base blocks in a complete pyramid of the given tier.
	 * Tiers 1-4 are 9, 34, 83 and 164 blocks.
	 */
	public static int blockCount(int tier) {
		int total = 0;
		for (int layer = 1; layer <= tier; layer++) {
			int width = 2 * layer + 1;
			total += width * width;
		}
		return total;
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

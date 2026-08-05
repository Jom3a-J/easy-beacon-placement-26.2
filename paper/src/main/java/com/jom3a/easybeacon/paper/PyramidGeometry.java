package com.jom3a.easybeacon.paper;

/**
 * Beacon pyramid geometry and the wire format of the build request.
 *
 * <p>Deliberately duplicated from the mod rather than shared. Paper is not a mod loader — it is a
 * fork of the vanilla server exposing the Bukkit API — so this plugin cannot see Minecraft's own
 * classes the way the Fabric and NeoForge modules do. The only thing the two sides genuinely share
 * is the shape of the packet, which is pinned down here.
 */
public final class PyramidGeometry {
	public static final int MIN_TIER = 1;
	public static final int MAX_TIER = 4;

	// Vanilla packs a BlockPos into one long: X in the top 26 bits, Z in the next 26, Y in the
	// low 12. These offsets are derived from BlockPos.PACKED_HORIZONTAL_LENGTH (26), so they are
	// fixed by the protocol rather than chosen here.
	private static final int PACKED_HORIZONTAL_BITS = 26;
	private static final int PACKED_Y_BITS = 12;
	private static final int X_SHIFT = PACKED_Y_BITS + PACKED_HORIZONTAL_BITS;
	private static final int Z_SHIFT = PACKED_Y_BITS;

	private PyramidGeometry() {
	}

	public static int unpackX(long packed) {
		return (int) (packed << 64 - X_SHIFT - PACKED_HORIZONTAL_BITS >> 64 - PACKED_HORIZONTAL_BITS);
	}

	public static int unpackY(long packed) {
		return (int) (packed << 64 - PACKED_Y_BITS >> 64 - PACKED_Y_BITS);
	}

	public static int unpackZ(long packed) {
		return (int) (packed << 64 - Z_SHIFT - PACKED_HORIZONTAL_BITS >> 64 - PACKED_HORIZONTAL_BITS);
	}

	/** Total base blocks in a complete pyramid: 9, 34, 83 or 164 for tiers 1-4. */
	public static int blockCount(int tier) {
		int total = 0;

		for (int layer = 1; layer <= tier; layer++) {
			int width = 2 * layer + 1;
			total += width * width;
		}

		return total;
	}
}

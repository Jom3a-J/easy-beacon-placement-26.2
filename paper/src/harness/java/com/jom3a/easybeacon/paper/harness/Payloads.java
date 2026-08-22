package com.jom3a.easybeacon.paper.harness;

import java.io.ByteArrayOutputStream;

/**
 * Encodes a build request the way the mod's {@code StreamCodec} does.
 *
 * <p>Deliberately written as an <em>encoder</em>, mirroring the plugin's hand-written decoder rather
 * than reusing it. A harness that round-tripped through the same code could not tell a correct
 * implementation from two matching mistakes; this one starts from the protocol — vanilla's packed
 * {@code BlockPos} layout and Minecraft's VarInt — so the two halves have to agree with the format
 * rather than merely with each other.
 *
 * <p>The layout itself is pinned separately by {@code PyramidGeometryTest}, which checks the
 * plugin's decode against packed values produced by vanilla's real {@code BlockPos.asLong()}.
 */
final class Payloads {
	private Payloads() {
	}

	static byte[] buildPyramid(int x, int y, int z, int tier) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		writeLong(out, packBlockPos(x, y, z));
		writeVarInt(out, tier);

		return out.toByteArray();
	}

	/** X in the top 26 bits, Z in the next 26, Y in the low 12 — vanilla's own packing. */
	private static long packBlockPos(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38
				| ((long) z & 0x3FFFFFFL) << 12
				| ((long) y & 0xFFFL);
	}

	/** Big-endian, as DataOutputStream writes it and the plugin's DataInputStream reads it. */
	private static void writeLong(ByteArrayOutputStream out, long value) {
		for (int shift = 56; shift >= 0; shift -= 8) {
			out.write((int) (value >>> shift) & 0xFF);
		}
	}

	/** Seven bits per byte, high bit set while more follow. */
	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}

		out.write(value);
	}
}

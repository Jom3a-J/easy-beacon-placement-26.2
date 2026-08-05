package com.jom3a.easybeacon.net;

import com.jom3a.easybeacon.EasyBeaconPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asking the server to build a beacon pyramid for it.
 *
 * <p>The request carries only where the beacon goes and how big the pyramid should be. It is a
 * <em>request</em>, not an instruction: the server recomputes the whole structure, re-checks the
 * terrain, and charges the player for the blocks. Nothing the client says is taken on trust —
 * see {@link ServerPyramidBuilder}.
 */
public record BuildPyramidPayload(BlockPos beaconPos, int tier) implements CustomPacketPayload {
	public static final Type<BuildPyramidPayload> TYPE =
			new Type<>(EasyBeaconPlacement.id("build_pyramid"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BuildPyramidPayload> CODEC =
			StreamCodec.composite(
					BlockPos.STREAM_CODEC, BuildPyramidPayload::beaconPos,
					ByteBufCodecs.VAR_INT, BuildPyramidPayload::tier,
					BuildPyramidPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

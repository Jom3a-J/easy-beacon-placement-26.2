package com.jom3a.easybeacon.neoforge;

import com.jom3a.easybeacon.EasyBeaconPlacement;
import com.jom3a.easybeacon.net.BuildPyramidPayload;
import com.jom3a.easybeacon.net.ServerBlockPlacer;
import com.jom3a.easybeacon.net.ServerPyramidBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The part of the mod that also runs on a dedicated server.
 *
 * <p>Installing this jar server-side is optional — the payload is registered as
 * {@linkplain PayloadRegistrar#optional() optional}, so clients without it (and vanilla clients)
 * can still connect. When the server does have it, the client hands the whole structure over in
 * one request and the server places it, which is what lifts the interaction-range limit.
 *
 * <p>Split from the client class because that one is {@code Dist.CLIENT} and so would never load
 * on a server. In singleplayer the integrated server runs this too, so the fast path is automatic.
 */
@Mod(EasyBeaconPlacement.MOD_ID)
public final class EasyBeaconPlacementNeoForgeCommon {
	public EasyBeaconPlacementNeoForgeCommon(IEventBus modBus) {
		modBus.addListener(this::onRegisterPayloads);
		ServerBlockPlacer.setStrategy(EasyBeaconPlacementNeoForgeCommon::placeThroughEvent);
	}

	/**
	 * Places one block the way NeoForge expects a mod to: snapshot, set, then let
	 * {@code BlockEvent.EntityPlaceEvent} run so land-claim mods can refuse it.
	 *
	 * <p>Fired per block rather than as a single {@code EntityMultiPlaceEvent}, which would be
	 * all-or-nothing. Per block matches what the Paper plugin does, and it is the better answer for
	 * a pyramid: a base that clips the corner of someone's claim loses that corner and keeps the
	 * rest, instead of silently refusing the whole build.
	 *
	 * <p>{@link Direction#UP} is the face this claims to have been placed against, which makes the
	 * event's {@code placedAgainst} the block underneath — true for every layer of a pyramid, each
	 * of which sits on the one below it or on the ground.
	 *
	 * <p>The plain {@link BlockSnapshot#restore()} is deliberate. {@code CommonHooks} restores with
	 * {@code getFlags() | UPDATE_CLIENTS} because it also handles snapshots taken with custom
	 * flags; the three-argument {@code create} used here already defaults to
	 * {@code UPDATE_NEIGHBORS | UPDATE_CLIENTS}, so the revert reaches clients as it stands. Nor is
	 * NeoForge's {@code restoringBlockSnapshots} guard needed: that exists to stop its automatic
	 * capture re-entering during a restore, and this snapshot is taken by hand.
	 */
	private static boolean placeThroughEvent(ServerPlayer player, ServerLevel level, BlockPos pos,
			BlockState state) {
		BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
		level.setBlockAndUpdate(pos, state);

		if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
			snapshot.restore();
			return false;
		}

		return true;
	}

	private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1").optional();

		registrar.playToServer(
				BuildPyramidPayload.TYPE,
				BuildPyramidPayload.CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					if (context.player() instanceof ServerPlayer serverPlayer) {
						ServerPyramidBuilder.handle(serverPlayer, payload);
					}
				}));
	}
}

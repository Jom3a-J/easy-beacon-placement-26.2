package com.jom3a.easybeacon.neoforge;

import com.jom3a.easybeacon.EasyBeaconPlacement;
import com.jom3a.easybeacon.net.BuildPyramidPayload;
import com.jom3a.easybeacon.net.ServerPyramidBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
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

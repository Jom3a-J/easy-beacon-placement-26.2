package com.jom3a.easybeacon.fabric;

import com.jom3a.easybeacon.net.BuildPyramidPayload;
import com.jom3a.easybeacon.net.ServerPyramidBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * The part of the mod that also runs on a dedicated server.
 *
 * <p>Installing this jar server-side is optional. When it is present the client hands the whole
 * structure over in one request and the server places it, which is what lifts the interaction-range
 * limit. When it is absent the client simply never sees the channel advertised and falls back to
 * placing blocks itself, so vanilla, Paper and Spigot servers keep working untouched.
 *
 * <p>In singleplayer the integrated server runs this too, so the fast path is automatic.
 */
public final class EasyBeaconPlacementFabricCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(BuildPyramidPayload.TYPE, BuildPyramidPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BuildPyramidPayload.TYPE, (payload, context) ->
				// Hop to the server thread before touching the world.
				context.server().execute(() -> ServerPyramidBuilder.handle(context.player(), payload)));
	}
}

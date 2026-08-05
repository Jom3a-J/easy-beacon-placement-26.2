package com.jom3a.easybeacon.fabric;

import com.jom3a.easybeacon.client.EbpClient;
import com.jom3a.easybeacon.client.EbpKeybinds;
import com.jom3a.easybeacon.client.HologramRenderer;
import com.jom3a.easybeacon.net.BuildPyramidPayload;
import com.jom3a.easybeacon.net.EbpNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

/**
 * Fabric wiring. Everything here is registration; the behaviour lives in the shared
 * {@code com.jom3a.easybeacon} sources.
 *
 * <p>This is a client-only mod, so it is a {@link ClientModInitializer} and declares
 * {@code "environment": "client"} in {@code fabric.mod.json}. It never has to be installed on a
 * server, which is what lets it work against vanilla, Paper and Spigot.
 */
public final class EasyBeaconPlacementFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EbpClient.init(FabricLoader.getInstance().getConfigDir());

		// canSend is only true once the server has advertised the channel, which is exactly the
		// "does the server have this mod?" test - no custom handshake needed.
		EbpNetworking.setClientSender(new EbpNetworking.ClientSender() {
			@Override
			public boolean canSend() {
				return ClientPlayNetworking.canSend(BuildPyramidPayload.TYPE);
			}

			@Override
			public void send(BuildPyramidPayload payload) {
				ClientPlayNetworking.send(payload);
			}
		});

		KeyMappingHelper.registerKeyMapping(EbpKeybinds.SHOW_HOLOGRAM);
		KeyMappingHelper.registerKeyMapping(EbpKeybinds.CYCLE_TIER);

		ClientTickEvents.END_CLIENT_TICK.register(EbpClient::onClientTick);

		// 26.2 replaced WorldRenderEvents with the level extraction/submit events. Drawing after
		// translucent terrain keeps the hologram visible through water and glass.
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context ->
				HologramRenderer.render(context.poseStack(), context.submitNodeCollector()));

		// Consume the right-click that triggers a build, so vanilla does not also place a single
		// beacon into the world. Both paths are needed: vanilla sends a click on a block through
		// UseBlockCallback and a click on empty air through UseItemCallback.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide() && EbpClient.onUseRequest(Minecraft.getInstance())) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});

		// While the preview key is held the wheel moves the beacon instead of the hotbar.
		// Returning false here suppresses the hotbar change.
		ClientHotbarScrollEvents.ALLOW.register((inventory, currentSlot, nextSlot, scrollX, scrollY) ->
				!EbpClient.onScroll(scrollY));

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && EbpClient.onUseRequest(Minecraft.getInstance())) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}
}

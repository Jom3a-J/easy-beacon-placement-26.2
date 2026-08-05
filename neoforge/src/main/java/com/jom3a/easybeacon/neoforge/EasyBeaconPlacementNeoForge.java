package com.jom3a.easybeacon.neoforge;

import com.jom3a.easybeacon.EasyBeaconPlacement;
import com.jom3a.easybeacon.client.EbpClient;
import com.jom3a.easybeacon.client.EbpKeybinds;
import com.jom3a.easybeacon.client.HologramRenderer;
import com.jom3a.easybeacon.net.BuildPyramidPayload;
import com.jom3a.easybeacon.net.EbpNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * NeoForge wiring. Mirrors {@code EasyBeaconPlacementFabric} one-for-one; the behaviour itself
 * lives in the shared {@code com.jom3a.easybeacon} sources.
 *
 * <p>Client-only, so the whole mod is scoped to {@link Dist#CLIENT} and is never needed on a
 * dedicated server.
 */
@Mod(value = EasyBeaconPlacement.MOD_ID, dist = Dist.CLIENT)
public final class EasyBeaconPlacementNeoForge {
	public EasyBeaconPlacementNeoForge(IEventBus modBus) {
		EbpClient.init(FMLPaths.CONFIGDIR.get());

		// hasChannel is only true once the server has advertised the payload, which is exactly
		// the "does the server have this mod?" test - no custom handshake needed.
		EbpNetworking.setClientSender(new EbpNetworking.ClientSender() {
			@Override
			public boolean canSend() {
				ClientPacketListener connection = Minecraft.getInstance().getConnection();

				return connection != null
						&& NetworkRegistry.hasChannel(connection, BuildPyramidPayload.TYPE.id());
			}

			@Override
			public void send(BuildPyramidPayload payload) {
				ClientPacketListener connection = Minecraft.getInstance().getConnection();

				if (connection != null) {
					connection.send(new ServerboundCustomPayloadPacket(payload));
				}
			}
		});

		modBus.addListener(this::onRegisterKeyMappings);

		IEventBus gameBus = NeoForge.EVENT_BUS;
		gameBus.addListener(this::onClientTick);
		gameBus.addListener(this::onSubmitCustomGeometry);
		gameBus.addListener(this::onRightClickBlock);
		gameBus.addListener(this::onRightClickItem);
		gameBus.addListener(this::onMouseScroll);
	}

	/** While the preview key is held the wheel moves the beacon instead of the hotbar. */
	private void onMouseScroll(InputEvent.MouseScrollingEvent event) {
		if (EbpClient.onScroll(event.getScrollDeltaY())) {
			event.setCanceled(true);
		}
	}

	private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(EbpKeybinds.SHOW_HOLOGRAM);
		event.register(EbpKeybinds.CYCLE_TIER);
	}

	private void onClientTick(ClientTickEvent.Post event) {
		EbpClient.onClientTick(Minecraft.getInstance());
	}

	/**
	 * 26.2's submit-node equivalent of the old level-render hook. NeoForge hands back the same
	 * vanilla {@code PoseStack} and {@code SubmitNodeCollector} that Fabric does, so the renderer
	 * itself is shared.
	 */
	private void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
		HologramRenderer.render(event.getPoseStack(), event.getSubmitNodeCollector());
	}

	/**
	 * Swallow the click that starts a build so vanilla does not also place a lone beacon.
	 *
	 * <p>Two listeners are needed because vanilla routes a click on a block and a click on empty
	 * air down different paths; without the item one, the preview would be unbuildable whenever
	 * the player is aiming at open sky.
	 */
	private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().isClientSide() && EbpClient.onUseRequest(Minecraft.getInstance())) {
			event.setCanceled(true);
		}
	}

	private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (event.getLevel().isClientSide() && EbpClient.onUseRequest(Minecraft.getInstance())) {
			event.setCanceled(true);
		}
	}
}

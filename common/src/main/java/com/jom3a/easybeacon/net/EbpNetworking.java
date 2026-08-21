package com.jom3a.easybeacon.net;

import net.minecraft.core.BlockPos;

/**
 * The seam between the loader-agnostic code and each platform's networking.
 *
 * <p>Registering payloads and testing whether the other side understands them is loader-specific,
 * so each platform module installs a {@link ClientSender} here at start-up. Everything else can
 * then just ask "can the server build this for me?" without knowing which loader it is on.
 */
public final class EbpNetworking {
	/** Implemented by each platform module. */
	public interface ClientSender {
		/**
		 * Whether the server has this mod installed and has declared it understands the payload.
		 * False on vanilla, Paper and Spigot servers, which is what drives the fallback.
		 */
		boolean canSend();

		void send(BuildPyramidPayload payload);
	}

	private static ClientSender clientSender;

	private EbpNetworking() {
	}

	public static void setClientSender(ClientSender sender) {
		clientSender = sender;
	}

	/**
	 * Whether the server has the mod and can build on the client's behalf.
	 *
	 * <p>This is asked while the preview is still being drawn, not only at the moment of the click:
	 * the two paths can spend different blocks — the server empties the whole inventory, the
	 * client-side placer only what is in a hand — so the preview has to know which one it is sizing
	 * itself for.
	 */
	public static boolean canServerBuild() {
		return clientSender != null && clientSender.canSend();
	}

	/**
	 * Asks the server to build the pyramid. Only call this when {@link #canServerBuild()} is true.
	 */
	public static void sendBuildRequest(BlockPos beaconPos, int tier) {
		if (clientSender != null) {
			clientSender.send(new BuildPyramidPayload(beaconPos, tier));
		}
	}
}

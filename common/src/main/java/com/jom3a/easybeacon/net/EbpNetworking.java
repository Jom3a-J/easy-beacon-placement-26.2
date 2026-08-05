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
	 * Asks the server to build the pyramid.
	 *
	 * @return true if the request was sent, meaning the client should not build it itself; false
	 *         when the server does not have the mod, leaving the client-side placer to do the job
	 */
	public static boolean tryServerBuild(BlockPos beaconPos, int tier) {
		if (clientSender == null || !clientSender.canSend()) {
			return false;
		}

		clientSender.send(new BuildPyramidPayload(beaconPos, tier));
		return true;
	}
}

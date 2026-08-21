package com.jom3a.easybeacon.net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a server-side placement reaches the world — and how the rest of the server gets to object.
 *
 * <p>{@link ServerPyramidBuilder} already refuses to overwrite terrain, honours the world border,
 * world height and spawn protection, and charges the player for every block. None of that helps
 * against a land-claim mod, which enforces a rule the server itself knows nothing about. The only
 * way to respect those is to offer each placement to whatever event the platform uses for
 * hand-placed blocks, and put the block back if it is refused.
 *
 * <p>That event is loader-specific, so it cannot live in this package. Each platform installs a
 * {@link Strategy} at start-up instead, exactly as {@link EbpNetworking} handles sending:
 *
 * <ul>
 *   <li><b>NeoForge</b> fires {@code BlockEvent.EntityPlaceEvent} per block, which is what
 *       protection mods listen to, so claims are enforced.</li>
 *   <li><b>Paper</b> does the same with {@code BlockPlaceEvent} — but that lives in the separate
 *       plugin, which shares no code with this package, so it does not go through here.</li>
 *   <li><b>Fabric</b> keeps {@link #DIRECT}. Fabric API has no block-place event to fire; mods
 *       that want one hook the vanilla call themselves, and there is nothing standard to offer a
 *       placement to. This is a gap in the platform rather than a decision made here.</li>
 * </ul>
 */
public final class ServerBlockPlacer {
	/** Places one block, and reports whether it was allowed to stay. */
	@FunctionalInterface
	public interface Strategy {
		/**
		 * @return true if the block is now in the world; false if the platform refused it, in
		 *         which case the implementation has already put the world back as it was and the
		 *         caller still owes the player a refund
		 */
		boolean place(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);
	}

	/** Straight to the world, with nothing given a chance to object. */
	private static final Strategy DIRECT = (player, level, pos, state) -> {
		level.setBlockAndUpdate(pos, state);
		return true;
	};

	private static volatile Strategy strategy = DIRECT;

	private ServerBlockPlacer() {
	}

	/** Installed once at start-up by the platform module, if it has an event worth firing. */
	public static void setStrategy(Strategy replacement) {
		strategy = replacement == null ? DIRECT : replacement;
	}

	public static boolean place(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		return strategy.place(player, level, pos, state);
	}
}

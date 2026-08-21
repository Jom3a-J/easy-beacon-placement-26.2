package com.jom3a.easybeacon.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jom3a.easybeacon.EbpConfig;
import com.jom3a.easybeacon.beacon.BeaconMaterials;
import com.jom3a.easybeacon.beacon.PlacementPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a planned pyramid by issuing ordinary vanilla place interactions.
 *
 * <p>Every block goes through {@link MultiPlayerGameMode#useItemOn}, which sends exactly the
 * packet a manual right-click sends. That is what lets this mod work on vanilla, Paper and Spigot
 * servers with nothing installed server-side.
 *
 * <h2>Why placement is verified rather than fired and forgotten</h2>
 *
 * <p>Vanilla placement is <em>predicted</em>: the client immediately shows the block, tags the
 * packet with a sequence number, and only finds out the real answer when the server acknowledges
 * it. A rejected placement is silently rolled back. So "I sent the packet" is not the same as
 * "the block is there", and assuming otherwise loses blocks — which is exactly how a pyramid ends
 * up with holes in it.
 *
 * <p>This executor therefore keeps every position until it has <em>seen</em> the right block in
 * the world, and re-sends the ones that did not take. Because the client shows the predicted
 * block straight away, verification has to wait out a round trip, so the delay scales with ping.
 *
 * <p>Pacing is by how many placements are unverified rather than a fixed rate. On a local server
 * confirmations come back next tick and the build runs quickly; on a laggy server it throttles
 * itself. That is strictly better than a fixed per-tick rate, which is either too slow locally or
 * too aggressive remotely.
 *
 * <p>Three limits are inherent to going through the vanilla protocol and cannot be engineered away
 * client-side: the server reach-checks every placement, every block needs an existing face to be
 * placed against, and only what is in a hand can be placed at all — the hotbar and the offhand,
 * never the backpack. Removing any of them would require a server-side component, which would cost
 * the "works on any server with nothing installed" property. The last one is why the preview sizes
 * itself to reachable blocks whenever this class is the one doing the building.
 */
public final class PlacementExecutor {
	/**
	 * Ticks of zero progress, by a player who is also standing still, before we conclude the rest
	 * cannot be placed.
	 *
	 * <p>Walking toward the far side of the pyramid is progress even though no block is landing —
	 * the whole reason blocks go out of reach is that they are on the other side of a base up to
	 * nine blocks across, and crossing that takes longer than this timer. So movement resets it,
	 * and only a player who has actually stopped trying is given up on.
	 */
	private static final int IDLE_LIMIT = 60;

	/**
	 * Hard ceiling on a build that is placing nothing at all, however much the player moves.
	 *
	 * <p>{@link #IDLE_LIMIT} alone would let a build follow someone around forever once they lose
	 * interest and walk off, quietly holding their hotbar slot hostage.
	 */
	private static final int NO_PROGRESS_LIMIT = 400;

	/**
	 * Squared distance a player has to cover in one tick to count as moving. Walking is about
	 * 0.2 blocks a tick, so this only has to clear the jitter of standing still.
	 */
	private static final double MOVEMENT_EPSILON = 1.0E-4D;

	/** Give a position this many tries before writing it off. */
	private static final int MAX_ATTEMPTS = 3;

	/** One position in the build, and how far along it is. */
	private static final class Target {
		final BlockPos pos;
		final boolean isBeacon;

		/** Tick this was last sent on, or -1 when it is waiting to be sent. */
		int sentOnTick = -1;
		int attempts;

		Target(BlockPos pos, boolean isBeacon) {
			this.pos = pos;
			this.isBeacon = isBeacon;
		}
	}

	private final List<Target> targets = new ArrayList<>();

	private boolean running;
	private int restoreSlot = -1;
	private int tickCounter;
	private int idleTicks;
	private int noProgressTicks;
	private int placedTotal;
	private int failedTotal;

	private double lastX;
	private double lastY;
	private double lastZ;

	public boolean isRunning() {
		return running;
	}

	/** Begins building {@code plan}. Any in-flight build is discarded. */
	public void start(PlacementPlan plan, LocalPlayer player) {
		targets.clear();

		for (BlockPos pos : plan.positionsToPlace()) {
			targets.add(new Target(pos, false));
		}

		targets.add(new Target(plan.beaconPos(), true));

		running = true;
		restoreSlot = player.getInventory().getSelectedSlot();
		tickCounter = 0;
		idleTicks = 0;
		noProgressTicks = 0;
		placedTotal = 0;
		failedTotal = 0;

		lastX = player.getX();
		lastY = player.getY();
		lastZ = player.getZ();
	}

	public void tick(Minecraft minecraft) {
		if (!running) {
			return;
		}

		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		MultiPlayerGameMode gameMode = minecraft.gameMode;

		if (player == null || level == null || gameMode == null) {
			reset(null);
			return;
		}

		tickCounter++;

		// Sampled once a tick, before anything can return early, so the movement test never
		// compares against a position several ticks stale.
		boolean moved = consumeMovement(player);

		int settled = verifySentTargets(minecraft, level);
		int dispatched = dispatch(minecraft, player, level, gameMode);

		if (targets.isEmpty()) {
			finish(minecraft, player, failedTotal == 0
					? Component.translatable("msg.easy_beacon_placement.done", placedTotal)
					: Component.translatable("msg.easy_beacon_placement.partial", placedTotal, failedTotal));
			return;
		}

		if (settled == 0 && dispatched == 0) {
			noProgressTicks++;
			idleTicks = moved ? 0 : idleTicks + 1;

			if (idleTicks >= IDLE_LIMIT || noProgressTicks >= NO_PROGRESS_LIMIT) {
				finish(minecraft, player,
						Component.translatable("msg.easy_beacon_placement.unreachable", targets.size()));
				return;
			}

			showProgress(minecraft,
					Component.translatable("msg.easy_beacon_placement.waiting", targets.size()));
		} else {
			idleTicks = 0;
			noProgressTicks = 0;
			showProgress(minecraft,
					Component.translatable("msg.easy_beacon_placement.placing", targets.size()));
		}
	}

	/** Whether the player has moved since the last tick, and remembers where they are now. */
	private boolean consumeMovement(LocalPlayer player) {
		double dx = player.getX() - lastX;
		double dy = player.getY() - lastY;
		double dz = player.getZ() - lastZ;

		lastX = player.getX();
		lastY = player.getY();
		lastZ = player.getZ();

		return dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON;
	}

	/**
	 * Checks placements whose round trip has elapsed. Confirmed ones are done; the rest go back
	 * in the queue, because the server rejected them and the client has rolled the block back.
	 *
	 * @return how many targets reached a final state this tick
	 */
	private int verifySentTargets(Minecraft minecraft, ClientLevel level) {
		int verifyDelay = verifyDelayTicks(minecraft);
		int settled = 0;

		Iterator<Target> iterator = targets.iterator();

		while (iterator.hasNext()) {
			Target target = iterator.next();

			if (target.sentOnTick < 0 || tickCounter - target.sentOnTick < verifyDelay) {
				continue;
			}

			if (hasExpectedBlock(level, target)) {
				iterator.remove();
				placedTotal++;
				settled++;
				continue;
			}

			if (target.attempts >= MAX_ATTEMPTS) {
				iterator.remove();
				failedTotal++;
				settled++;
				continue;
			}

			// Rejected: put it back in the queue for another go.
			target.sentOnTick = -1;
		}

		return settled;
	}

	/** Sends placements while fewer than {@code maxInFlight} are awaiting confirmation. */
	private int dispatch(Minecraft minecraft, LocalPlayer player, ClientLevel level,
			MultiPlayerGameMode gameMode) {
		int inFlight = 0;

		for (Target target : targets) {
			if (target.sentOnTick >= 0) {
				inFlight++;
			}
		}

		int budget = Math.max(1, EbpConfig.get().maxInFlight) - inFlight;
		int dispatched = 0;

		while (budget > 0) {
			Candidate candidate = nextReachableTarget(player, level);

			if (candidate == null) {
				break;
			}

			Target target = candidate.target();
			Source source = findSource(player, target);

			if (source == null) {
				if (!target.isBeacon) {
					// Deliberately not the same message the server-side builder uses: that one has
					// emptied the whole inventory, whereas this one may be standing next to a
					// backpack full of blocks it simply cannot reach from here.
					finish(minecraft, player,
							Component.translatable("msg.easy_beacon_placement.out_of_reachable_material"));
					return dispatched;
				}

				break;
			}

			// Changing the selected slot only reaches the server via the carried-item packet the
			// player sends on its own tick. Placing in the same tick races that packet and the
			// server uses whatever it still thinks is held, so switch now and place later. An
			// offhand placement needs no switch at all, which is part of why it is worth using.
			if (source.needsSwitch(player)) {
				player.getInventory().setSelectedSlot(source.hotbarSlot());
				return dispatched;
			}

			gameMode.useItemOn(player, source.hand(), candidate.hit());

			target.sentOnTick = tickCounter;
			target.attempts++;
			dispatched++;
			budget--;
		}

		return dispatched;
	}

	/** One position that can be placed right now, together with the face to click to do it. */
	private record Candidate(Target target, BlockHitResult hit) {
	}

	/**
	 * The closest unsent position that can actually be placed right now. Nearest-first keeps the
	 * build inside reach for as long as possible and makes it read as growing outward from you.
	 *
	 * <p>The support face is handed back rather than left for the caller to find again: locating it
	 * costs up to six block lookups, and this runs over every remaining target on every dispatch.
	 */
	private Candidate nextReachableTarget(LocalPlayer player, ClientLevel level) {
		Vec3 eye = player.getEyePosition();
		Candidate best = null;
		double bestDistance = Double.MAX_VALUE;

		for (Target target : targets) {
			if (target.sentOnTick >= 0) {
				continue;
			}

			double distance = distanceSqrToCentre(eye, target.pos);

			// Distance first: a target that cannot beat the current best never has to be checked
			// against the world at all, and that check is the expensive half of this loop.
			if (distance >= bestDistance) {
				continue;
			}

			if (!player.isWithinBlockInteractionRange(target.pos, 0.0D)
					|| !level.getBlockState(target.pos).canBeReplaced()) {
				continue;
			}

			BlockHitResult hit = findSupportFace(level, player, target.pos);

			if (hit == null) {
				continue;
			}

			bestDistance = distance;
			best = new Candidate(target, hit);
		}

		return best;
	}

	private static double distanceSqrToCentre(Vec3 from, BlockPos pos) {
		double dx = from.x - (pos.getX() + 0.5D);
		double dy = from.y - (pos.getY() + 0.5D);
		double dz = from.z - (pos.getZ() + 0.5D);

		return dx * dx + dy * dy + dz * dz;
	}

	private static boolean hasExpectedBlock(ClientLevel level, Target target) {
		BlockState state = level.getBlockState(target.pos);

		return target.isBeacon ? state.is(Blocks.BEACON) : BeaconMaterials.isBeaconBase(state);
	}

	/**
	 * How long to wait before believing what the world says about a placement. The client shows
	 * its own prediction immediately, so checking too early always reports success.
	 */
	private static int verifyDelayTicks(Minecraft minecraft) {
		int pingMillis = 0;

		if (minecraft.getConnection() != null && minecraft.player != null) {
			PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());

			if (info != null) {
				pingMillis = Math.max(0, info.getLatency());
			}
		}

		// Two ticks of slack plus the round trip, converted from milliseconds to 50ms ticks.
		return Math.clamp(2 + pingMillis / 50, 2, 40);
	}

	/** Aborts an in-flight build, leaving whatever has already been placed in the world. */
	public void cancel(Minecraft minecraft) {
		if (running) {
			finish(minecraft, minecraft.player,
					Component.translatable("msg.easy_beacon_placement.cancelled", placedTotal));
		}
	}

	private void finish(Minecraft minecraft, LocalPlayer player, Component message) {
		reset(player);

		if (message != null && EbpConfig.get().showStatusText && minecraft.gui != null) {
			minecraft.gui.hud.setOverlayMessage(message, false);
		}
	}

	private void showProgress(Minecraft minecraft, Component message) {
		if (EbpConfig.get().showStatusText && minecraft.gui != null) {
			minecraft.gui.hud.setOverlayMessage(message, false);
		}
	}

	private void reset(LocalPlayer player) {
		targets.clear();
		running = false;
		idleTicks = 0;
		noProgressTicks = 0;

		if (player != null && restoreSlot >= 0) {
			player.getInventory().setSelectedSlot(restoreSlot);
		}

		restoreSlot = -1;
	}

	/**
	 * Finds an existing block face adjacent to {@code pos} that the player can legitimately click
	 * to place a block into {@code pos}, or {@code null} if there is nothing to build against yet.
	 */
	private static BlockHitResult findSupportFace(ClientLevel level, LocalPlayer player, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = pos.relative(direction);
			BlockState neighbourState = level.getBlockState(neighbour);

			// Needs to be something solid enough to right-click.
			if (neighbourState.isAir() || neighbourState.canBeReplaced()) {
				continue;
			}

			// The server reach-checks the block we claim to have clicked, not just the target.
			if (!player.isWithinBlockInteractionRange(neighbour, 0.0D)) {
				continue;
			}

			// From the neighbour, `pos` lies in the opposite direction, so that is the face hit.
			Direction face = direction.getOpposite();
			Vec3 centre = Vec3.atCenterOf(neighbour);
			Vec3 location = centre.add(
					face.getStepX() * 0.5D,
					face.getStepY() * 0.5D,
					face.getStepZ() * 0.5D);

			return new BlockHitResult(location, face, neighbour, false);
		}

		return null;
	}

	/**
	 * Where a target's item is coming from: which hand to place with, and the hotbar slot that has
	 * to be selected first, or {@code -1} when there is nothing to select.
	 */
	private record Source(InteractionHand hand, int hotbarSlot) {
		static final Source OFFHAND = new Source(InteractionHand.OFF_HAND, -1);

		static Source mainHand(int slot) {
			return new Source(InteractionHand.MAIN_HAND, slot);
		}

		boolean needsSwitch(LocalPlayer player) {
			return hotbarSlot >= 0 && player.getInventory().getSelectedSlot() != hotbarSlot;
		}
	}

	/**
	 * Which hand to place {@code target} from, or {@code null} when the player is carrying nothing
	 * that would do it.
	 *
	 * <p>The hotbar is preferred, but the offhand is a real fallback rather than a nicety. Parking
	 * the beacon in the offhand and filling the hotbar with base blocks is the obvious way to carry
	 * both, and vanilla is perfectly happy to place from either hand — so ignoring the offhand
	 * meant the beacon at the top of the pyramid could never be placed and the whole build stalled
	 * out as "unreachable".
	 */
	private static Source findSource(LocalPlayer player, Target target) {
		Inventory inventory = player.getInventory();

		int slot = target.isBeacon
				? BeaconMaterials.findHotbarSlot(inventory, Items.BEACON)
				: BeaconMaterials.findHotbarSlot(inventory);

		if (slot >= 0) {
			return Source.mainHand(slot);
		}

		ItemStack offhand = player.getOffhandItem();
		boolean offhandFits = target.isBeacon
				? offhand.is(Items.BEACON)
				: BeaconMaterials.baseBlockOf(offhand) != null;

		return offhandFits ? Source.OFFHAND : null;
	}
}

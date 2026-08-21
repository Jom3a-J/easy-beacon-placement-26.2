package com.jom3a.easybeacon.client;

import java.nio.file.Path;

import com.jom3a.easybeacon.EbpConfig;
import com.jom3a.easybeacon.beacon.BeaconPyramid;
import com.jom3a.easybeacon.beacon.PlacementPlan;
import com.jom3a.easybeacon.beacon.SlotState;
import com.jom3a.easybeacon.net.EbpNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side brain of the mod: owns the current preview and drives the placement executor.
 *
 * <p>The platform modules call into here — {@link #init}, {@link #onClientTick} and
 * {@link #onRightClickBlock} — and never the other way around. That is what keeps this whole
 * package free of loader-specific imports.
 */
public final class EbpClient {
	private static final PlacementExecutor EXECUTOR = new PlacementExecutor();

	private static PlacementPlan currentPlan;

	/** 0 means "use the largest tier the inventory allows"; otherwise a manual override. */
	private static int tierOverride;

	/** Mouse-wheel nudge along the direction you are looking, in blocks. */
	private static int depthOffset;

	/** Mouse-wheel nudge straight up or down, in blocks (sneak + wheel). */
	private static int heightOffset;

	private EbpClient() {
	}

	public static void init(Path configDir) {
		EbpConfig.load(configDir);
	}

	/** The preview the renderer should draw, or {@code null} when nothing should be shown. */
	public static PlacementPlan currentPlan() {
		return currentPlan;
	}

	public static void onClientTick(Minecraft minecraft) {
		boolean cyclePressed = false;

		while (EbpKeybinds.CYCLE_TIER.consumeClick()) {
			cyclePressed = true;
		}

		if (EXECUTOR.isRunning()) {
			// While a build is running the same key aborts it, so a misjudged placement can be
			// stopped part-way instead of having to wait it out.
			if (cyclePressed) {
				EXECUTOR.cancel(minecraft);
			} else {
				EXECUTOR.tick(minecraft);
			}

			// Don't draw a stale preview over a build that is already in progress.
			currentPlan = null;
			return;
		}

		if (cyclePressed) {
			cycleTier();
		}

		currentPlan = computePlan(minecraft);

		if (currentPlan != null) {
			showStatus(minecraft, currentPlan);
		}
	}

	/**
	 * Called when the player right-clicks, whether or not they were aiming at a block.
	 *
	 * <p>Both cases have to be wired up: vanilla routes a click on a block and a click on empty
	 * air down different paths (use-on-block vs use-item), so hooking only the block one would
	 * leave the preview unbuildable whenever the player is aiming at open sky.
	 *
	 * @return true if the mod consumed the click, in which case the platform module must cancel
	 *         the vanilla interaction so a single beacon is not placed as well
	 */
	public static boolean onUseRequest(Minecraft minecraft) {
		if (currentPlan == null || EXECUTOR.isRunning()) {
			return false;
		}

		LocalPlayer player = minecraft.player;

		if (player == null) {
			return false;
		}

		PlacementPlan plan = currentPlan;
		currentPlan = null;

		// When the server has the mod it does the building: no interaction-range check to satisfy
		// and no need for every block to have a face to be clicked against. In singleplayer the
		// integrated server is running this same code, so that path is taken automatically.
		if (willBuildServerSide()) {
			EbpNetworking.sendBuildRequest(plan.beaconPos(), plan.tier());
			return true;
		}

		// Vanilla, Paper or Spigot: nothing installed server-side, so place it the long way.
		EXECUTOR.start(plan, player);
		return true;
	}

	/**
	 * Mouse-wheel input, used to nudge the beacon up and down while the preview key is held.
	 *
	 * @param delta wheel movement; positive is scroll-up
	 * @return true if the mod consumed the scroll, in which case the platform module must stop it
	 *         from also changing the selected hotbar slot
	 */
	public static boolean onScroll(double delta) {
		if (delta == 0.0D || !EbpKeybinds.SHOW_HOLOGRAM.isDown()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return false;
		}

		EbpConfig config = EbpConfig.get();
		int step = delta > 0.0D ? 1 : -1;

		if (config.invertScroll) {
			step = -step;
		}

		int limit = config.maxScrollOffset;

		// Two axes are enough to reach anywhere: aiming supplies the other two degrees of
		// freedom, so pushing the beacon along your line of sight covers every direction. Sneak
		// switches to plain vertical, which is what you want for lifting it out of the ground.
		if (minecraft.player.isShiftKeyDown()) {
			heightOffset = Math.clamp(heightOffset + step, -limit, limit);
		} else {
			depthOffset = Math.clamp(depthOffset + step, -limit, limit);
		}

		return true;
	}

	private static void cycleTier() {
		// 0 (auto) -> 1 -> 2 -> 3 -> 4 -> 0 ...
		tierOverride = tierOverride >= BeaconPyramid.MAX_TIER ? 0 : tierOverride + 1;
	}

	/**
	 * Works out what to preview right now, or {@code null} if the hologram should be hidden:
	 * the keybind must be held, a beacon must be in hand, and the player must be looking at a
	 * block face where a beacon could go.
	 */
	private static PlacementPlan computePlan(Minecraft minecraft) {
		if (!EbpKeybinds.SHOW_HOLOGRAM.isDown()) {
			// Start from wherever you are aiming each time the preview comes up, rather than
			// silently carrying a nudge over from a previous placement.
			depthOffset = 0;
			heightOffset = 0;
			return null;
		}

		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null || !isHoldingBeacon(player)) {
			return null;
		}

		BlockPos beaconPos = adjustedTarget(minecraft, player);

		// Layer i sits at beaconY - i, so the world floor caps how deep the pyramid can go.
		int tierByRoom = beaconPos.getY() - minecraft.level.getMinY();

		if (tierByRoom < BeaconPyramid.MIN_TIER) {
			// No room below for even a single layer.
			return null;
		}

		EbpConfig config = EbpConfig.get();
		int maxTier = tierOverride == 0 ? config.maxTier : Math.min(tierOverride, config.maxTier);
		maxTier = Math.min(maxTier, tierByRoom);

		// The two placement paths can spend different blocks: the server empties the whole
		// inventory, while the client-side placer can only ever use a hand. Sizing the preview to
		// blocks the placer cannot reach is what leaves a pyramid half-built and then aborts with
		// "out of base blocks", so the budget follows whichever path this click will actually take.
		boolean reachableOnly = config.countHotbarOnly || !willBuildServerSide();

		return PlacementPlan.compute(minecraft.level, beaconPos, player, reachableOnly, maxTier);
	}

	/** Whether a build started right now would be handed to the server rather than placed by hand. */
	private static boolean willBuildServerSide() {
		return EbpConfig.get().preferServerPlacement && EbpNetworking.canServerBuild();
	}

	private static boolean isHoldingBeacon(LocalPlayer player) {
		return isBeacon(player.getMainHandItem()) || isBeacon(player.getOffhandItem());
	}

	private static boolean isBeacon(ItemStack stack) {
		return stack.is(Items.BEACON);
	}

	/**
	 * Where the beacon should be previewed.
	 *
	 * <p>When the crosshair is on a block this mirrors vanilla placement: inside the block if it
	 * is replaceable (tall grass, snow layers, ...), otherwise against the face that was hit.
	 *
	 * <p>When the player is aiming at open sky there is nothing to anchor to, so the position is
	 * projected a fixed distance along the look vector instead. The preview still works there;
	 * whether the structure can actually be built is a separate question the placer answers, since
	 * blocks in mid-air have no face to be placed against.
	 */
	private static BlockPos targetPosition(Minecraft minecraft, LocalPlayer player) {
		if (minecraft.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			BlockPos clicked = hit.getBlockPos();
			BlockState clickedState = minecraft.level.getBlockState(clicked);

			return clickedState.canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		}

		Vec3 aim = player.getEyePosition()
				.add(player.getLookAngle().scale(EbpConfig.get().airPreviewDistance));

		return BlockPos.containing(aim);
	}

	/** The aimed-at position, moved by however far the mouse wheel has nudged it. */
	private static BlockPos adjustedTarget(Minecraft minecraft, LocalPlayer player) {
		BlockPos base = targetPosition(minecraft, player);

		if (depthOffset != 0) {
			Vec3 along = player.getLookAngle().scale(depthOffset);
			base = base.offset(
					Math.round((float) along.x),
					Math.round((float) along.y),
					Math.round((float) along.z));
		}

		return base.above(heightOffset);
	}

	private static void showStatus(Minecraft minecraft, PlacementPlan plan) {
		EbpConfig config = EbpConfig.get();

		if (!config.showStatusText || minecraft.gui == null) {
			return;
		}

		int missing = plan.count(SlotState.MISSING_MATERIAL);
		int blocked = plan.count(SlotState.OBSTRUCTED);
		int effective = plan.effectiveTier();
		Component message;

		// The verdict matters more than the raw counts: the pyramid is too big to check by eye
		// and most of it is off-screen, so the player needs to be told whether it will work.
		if (effective == plan.tier()) {
			message = Component.translatable(
					"hud.easy_beacon_placement.ready", plan.tier(), plan.count(SlotState.PLACEABLE));
		} else if (effective == 0) {
			message = Component.translatable(
					"hud.easy_beacon_placement.unusable", blocked + missing);
		} else {
			message = Component.translatable(
					"hud.easy_beacon_placement.downgraded", plan.tier(), effective, blocked + missing);
		}

		if (depthOffset != 0 || heightOffset != 0) {
			StringBuilder nudge = new StringBuilder();

			if (depthOffset != 0) {
				nudge.append(depthOffset > 0 ? "→+" : "→").append(depthOffset);
			}

			if (heightOffset != 0) {
				if (nudge.length() > 0) {
					nudge.append(' ');
				}

				nudge.append(heightOffset > 0 ? "↑+" : "↑").append(heightOffset);
			}

			message = Component.translatable("hud.easy_beacon_placement.offset", message, nudge.toString());
		}

		minecraft.gui.hud.setOverlayMessage(message, false);
	}
}

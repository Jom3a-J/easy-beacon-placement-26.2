package com.jom3a.easybeacon.neoforge.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.jom3a.easybeacon.beacon.BeaconMaterials;
import com.jom3a.easybeacon.beacon.BeaconPyramid;
import com.jom3a.easybeacon.net.BuildPyramidPayload;
import com.jom3a.easybeacon.net.ServerPyramidBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automated tests for the NeoForge half of the mod, run on a real dedicated server.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Fabric has a client game test harness, so that loader's wiring can be driven unattended. The
 * NeoForge side had nothing: it was only ever compiled, and the one thing unique to it — firing
 * {@link BlockEvent.EntityPlaceEvent} so land-claim mods can veto a placement — had never been
 * executed at all. Verifying it by hand means installing a protection mod and building inside a
 * claim, which is not something to repeat on every change.
 *
 * <p>{@link FakePlayer} is what makes this possible without a client: it is a real
 * {@link ServerPlayer}, so {@link ServerPyramidBuilder} cannot tell the difference, and its
 * inventory can be inspected afterwards to prove blocks were charged or refunded.
 *
 * <p>The harness stops the server when it finishes and exits non-zero if anything failed, so
 * {@code ./gradlew :neoforge:runHarness} works unattended and in CI.
 */
@Mod("ebp_harness")
public final class EbpHarness {
	private static final Logger LOG = LoggerFactory.getLogger("ebp-harness");

	/** Where the test pyramids go: clear air above a superflat floor, inside the spawn chunks. */
	private static final BlockPos ORIGIN = new BlockPos(0, -50, 0);

	/**
	 * Stands in for a land-claim mod. A test points this at the region it wants refused, and the
	 * listener below cancels exactly those placements.
	 */
	private static volatile Predicate<BlockPos> veto = pos -> false;

	private final List<String> failures = new ArrayList<>();
	private int checks;

	public EbpHarness(IEventBus modBus) {
		NeoForge.EVENT_BUS.addListener(EbpHarness::onBlockPlace);
		NeoForge.EVENT_BUS.addListener(this::onServerStarted);
	}

	private static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
		if (veto.test(event.getPos())) {
			event.setCanceled(true);
		}
	}

	private void onServerStarted(ServerStartedEvent event) {
		MinecraftServer server = event.getServer();
		ServerLevel level = server.overworld();

		LOG.info("=== EBP harness starting ===");

		try {
			buildsACompletePyramid(level);
			vetoedPlacementsAreRolledBackAndRefunded(level);
			aPartialVetoLosesOnlyTheRefusedBlocks(level);
			refusesToBuildTooFarAway(level);
			refusesWithoutABeaconInHand(level);
			alreadyCorrectBlocksAreNotChargedFor(level);
		} catch (Throwable t) {
			LOG.error("harness threw", t);
			failures.add("harness threw: " + t);
		} finally {
			veto = pos -> false;
		}

		finish(server);
	}

	// --- tests -------------------------------------------------------------------------------

	private void buildsACompletePyramid(ServerLevel level) {
		BlockPos beacon = clearArea(level, 0);
		ServerPlayer player = player(level, "complete", beacon, 9, true);

		ServerPyramidBuilder.handle(player, new BuildPyramidPayload(beacon, 1));

		check("tier 1 places 9 base blocks", 9, ironCount(level, beacon, 1));
		check("the beacon itself is placed", true, level.getBlockState(beacon).is(Blocks.BEACON));
		check("base blocks are consumed", 0, BeaconMaterials.countInInventory(player.getInventory()));
	}

	/** The case this whole harness exists for. */
	private void vetoedPlacementsAreRolledBackAndRefunded(ServerLevel level) {
		BlockPos beacon = clearArea(level, 64);
		ServerPlayer player = player(level, "vetoed", beacon, 9, true);

		veto = pos -> true;
		ServerPyramidBuilder.handle(player, new BuildPyramidPayload(beacon, 1));
		veto = pos -> false;

		check("a refused placement leaves nothing behind", 0, ironCount(level, beacon, 1));
		check("a refused beacon is not placed", false, level.getBlockState(beacon).is(Blocks.BEACON));
		check("refused base blocks are refunded", 9, BeaconMaterials.countInInventory(player.getInventory()));
		check("a refused beacon is refunded", 1, countBeacons(player));
	}

	private void aPartialVetoLosesOnlyTheRefusedBlocks(ServerLevel level) {
		BlockPos beacon = clearArea(level, 128);
		ServerPlayer player = player(level, "partial", beacon, 9, true);
		BlockPos refused = beacon.below().north().west();

		veto = refused::equals;
		ServerPyramidBuilder.handle(player, new BuildPyramidPayload(beacon, 1));
		veto = pos -> false;

		check("the other eight still go in", 8, ironCount(level, beacon, 1));
		check("the refused slot stays empty", true, level.getBlockState(refused).isAir());
		check("only the refused block is refunded", 1, BeaconMaterials.countInInventory(player.getInventory()));
	}

	private void refusesToBuildTooFarAway(ServerLevel level) {
		BlockPos beacon = clearArea(level, 192);
		// The player stands at the region, but asks for a pyramid far outside the 48-block limit.
		ServerPlayer player = player(level, "distant", beacon, 9, true);
		BlockPos faraway = beacon.offset(400, 0, 400);

		ServerPyramidBuilder.handle(player, new BuildPyramidPayload(faraway, 1));

		check("a distant request builds nothing", 0, ironCount(level, faraway, 1));
		check("and costs the player nothing", 9, BeaconMaterials.countInInventory(player.getInventory()));
	}

	private void refusesWithoutABeaconInHand(ServerLevel level) {
		BlockPos beacon = clearArea(level, 256);
		ServerPlayer player = player(level, "nobeacon", beacon, 9, false);

		ServerPyramidBuilder.handle(player, new BuildPyramidPayload(beacon, 1));

		check("no beacon in hand builds nothing", 0, ironCount(level, beacon, 1));
		check("and costs the player nothing", 9, BeaconMaterials.countInInventory(player.getInventory()));
	}

	private void alreadyCorrectBlocksAreNotChargedFor(ServerLevel level) {
		BlockPos beacon = clearArea(level, 320);

		// Pre-place the whole base, then ask for it with an empty inventory.
		for (BlockPos pos : BeaconPyramid.basePositions(beacon, 1)) {
			level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
		}

		ServerPlayer player = player(level, "prebuilt", beacon, 0, true);
		ServerPyramidBuilder.handle(player, new BuildPyramidPayload(beacon, 1));

		check("an already-complete base stays complete", 9, ironCount(level, beacon, 1));
		check("and the beacon still goes on top", true, level.getBlockState(beacon).is(Blocks.BEACON));
	}

	// --- helpers -----------------------------------------------------------------------------

	/** Wipes a working area and returns the beacon position at its centre. */
	private static BlockPos clearArea(ServerLevel level, int offsetX) {
		BlockPos beacon = ORIGIN.offset(offsetX, 0, 0);

		for (int dx = -6; dx <= 6; dx++) {
			for (int dy = -6; dy <= 2; dy++) {
				for (int dz = -6; dz <= 6; dz++) {
					level.setBlockAndUpdate(beacon.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}

		return beacon;
	}

	/**
	 * A survival-mode {@link FakePlayer} standing next to {@code beacon}, carrying {@code ironBlocks}
	 * base blocks and optionally a beacon.
	 *
	 * <p>Each test uses its own name, and so its own UUID. That matters: the builder rate-limits per
	 * player, and sharing one identity would have later tests silently throttled rather than run.
	 */
	private static ServerPlayer player(ServerLevel level, String name, BlockPos beacon,
			int ironBlocks, boolean holdingBeacon) {
		GameProfile profile = new GameProfile(
				UUID.nameUUIDFromBytes(("ebp-harness:" + name).getBytes()), "harness_" + name);

		FakePlayer player = FakePlayerFactory.get(level, profile);

		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().mayBuild = true;
		player.setPos(beacon.getX() + 0.5D, beacon.getY(), beacon.getZ() + 2.5D);
		player.getInventory().clearContent();

		// The beacon goes in first, on purpose. setItemInHand writes to the selected slot, which is
		// also where add() puts the first stack it is given - so filling the inventory first and
		// then arming the hand silently overwrites the base blocks with the beacon, and the builder
		// correctly reports having nothing to build with.
		player.setItemInHand(InteractionHand.MAIN_HAND,
				holdingBeacon ? new ItemStack(Items.BEACON) : ItemStack.EMPTY);

		if (ironBlocks > 0) {
			player.getInventory().add(new ItemStack(Items.IRON_BLOCK, ironBlocks));
		}

		return player;
	}

	private static int ironCount(ServerLevel level, BlockPos beacon, int tier) {
		int found = 0;

		for (BlockPos pos : BeaconPyramid.basePositions(beacon, tier)) {
			if (level.getBlockState(pos).is(Blocks.IRON_BLOCK)) {
				found++;
			}
		}

		return found;
	}

	private static int countBeacons(ServerPlayer player) {
		int found = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.is(Items.BEACON)) {
				found += stack.getCount();
			}
		}

		return found;
	}

	private void check(String what, Object expected, Object actual) {
		checks++;

		if (expected.equals(actual)) {
			LOG.info("  PASS  {}", what);
		} else {
			LOG.error("  FAIL  {} -- expected {}, got {}", what, expected, actual);
			failures.add(what + " (expected " + expected + ", got " + actual + ")");
		}
	}

	/**
	 * Reports and stops the server.
	 *
	 * <p>A failure halts the JVM outright rather than shutting down cleanly. The exit code is the
	 * only thing Gradle and CI look at, and a clean shutdown would return zero and bury the result.
	 */
	private void finish(MinecraftServer server) {
		if (failures.isEmpty()) {
			LOG.info("=== EBP harness PASSED: {} checks ===", checks);
			server.halt(false);
			return;
		}

		LOG.error("=== EBP harness FAILED: {} of {} checks ===", failures.size(), checks);
		failures.forEach(f -> LOG.error("    {}", f));
		Runtime.getRuntime().halt(1);
	}
}

package com.jom3a.easybeacon.fabric.gametest;

import com.jom3a.easybeacon.EbpConfig;
import com.jom3a.easybeacon.beacon.PlacementPlan;
import com.jom3a.easybeacon.beacon.SlotState;
import com.jom3a.easybeacon.client.EbpKeybinds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * End-to-end tests that drive a real client.
 *
 * <p>These cover the things a unit test cannot: that the preview classifies real terrain
 * correctly, that the hologram actually renders, and that a click really puts blocks in the
 * world through the vanilla interaction path.
 *
 * <p>Both scenarios build a deterministic arena in the air rather than relying on world
 * generation, so the expected block counts are exact.
 */
public class EasyBeaconClientGameTest implements FabricClientGameTest {
	/** Where the player looks. Clicking its east face puts the beacon one block further east. */
	private static final BlockPos AIM_BLOCK_OBSTRUCTED = new BlockPos(1, 100, 0);
	private static final BlockPos BEACON_OBSTRUCTED = new BlockPos(2, 100, 0);

	private static final BlockPos AIM_BLOCK_OPEN = new BlockPos(1, 99, 0);
	private static final BlockPos BEACON_OPEN = new BlockPos(2, 99, 0);

	/**
	 * Aiming at air from eye height 99.62 at (5.5, ·, 0.5), 2.5 blocks west, lands here. The
	 * pyramid's single layer then sits at y=98, directly on the stone floor at y=97.
	 */
	private static final BlockPos BEACON_IN_AIR = new BlockPos(3, 99, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			testObstructedPreview(context, singleplayer);
			testOneClickBuild(context, singleplayer);
			testBuildWhileAimingAtAir(context, singleplayer);
			testServerSideBuildIgnoresReachAndSupport(context, singleplayer);
		}

		testAgainstDedicatedServer(context);
	}

	/**
	 * The same server-side build, but over a real network connection to a separate dedicated
	 * server process.
	 *
	 * <p>Everything else in this class runs against the integrated server, which shares a JVM with
	 * the client — so it proves the server <em>logic</em> but never that the payload survives an
	 * actual round trip, gets registered on a real server, or that the client's channel detection
	 * agrees with what the server advertises. This is the test that covers the wire.
	 */
	private void testAgainstDedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();

				String[] setup = {
					"gamemode creative @a",
					"fill -12 90 -12 16 97 16 minecraft:stone",
					"fill -12 98 -12 16 130 16 minecraft:air",
					"clear @a",
					"give @a minecraft:beacon 1",
					"give @a minecraft:iron_block 200",
					"tp @a 0.5 98 0.5 0 -90",
				};

				for (String command : setup) {
					server.runCommand(command);
				}

				connection.waitForClientboundPackets();
				context.waitTicks(20);

				context.runOnClient(minecraft -> {
					EbpConfig.get().maxTier = 4;
					EbpConfig.get().airPreviewDistance = 8.0D;
					EbpConfig.get().preferServerPlacement = true;
				});

				context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
				context.waitTicks(5);
				context.getInput().pressMouse(1);

				context.waitTicks(40);
				context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
				connection.waitForServerboundPackets();
				context.waitTicks(20);

				server.runOnServer(minecraftServer -> {
					var level = minecraftServer.overworld();
					BlockPos beacon = findBeaconAbove(level, new BlockPos(0, 99, 0));

					if (beacon == null) {
						throw new AssertionError("no beacon overhead: the build request did not "
								+ "survive the trip to a real dedicated server");
					}

					int placed = 0;

					for (int layer = 1; layer <= 4; layer++) {
						int y = beacon.getY() - layer;

						for (int dx = -layer; dx <= layer; dx++) {
							for (int dz = -layer; dz <= layer; dz++) {
								if (level.getBlockState(new BlockPos(dx, y, dz)).is(Blocks.IRON_BLOCK)) {
									placed++;
								}
							}
						}
					}

					assertEquals(164, placed, "blocks built by a real dedicated server");
				});

				context.takeScreenshot("dedicated-server-build");
			}
		}
	}

	/**
	 * Proves the server-side path is really being used.
	 *
	 * <p>The arena asks for a full tier-4 pyramid floating in mid-air, well beyond arm's length.
	 * Every part of that is impossible through the client-side placer: the server reach-checks
	 * each placement, and each block would need an existing face to be clicked against. If all
	 * 164 blocks appear, the server built it.
	 *
	 * <p>This works in a test because the integrated server runs the mod too — which is exactly
	 * why singleplayer gets the fast path with nothing extra installed.
	 */
	private void testServerSideBuildIgnoresReachAndSupport(ClientGameTestContext context,
			TestSingleplayerContext sp) {
		String[] setup = {
			"fill -12 90 -12 16 97 16 minecraft:stone",
			"fill -12 98 -12 16 130 16 minecraft:air",
			"clear @a",
			// Beacon first so it lands in slot 0 and is the held item.
			"give @a minecraft:beacon 1",
			"give @a minecraft:iron_block 200",
			// Stand on the floor looking straight up, so the preview projects into open sky.
			"tp @a 0.5 98 0.5 0 -90",
		};

		for (String command : setup) {
			sp.getServer().runCommand(command);
		}

		sp.getConnection().waitForClientboundPackets();
		context.waitTicks(20);

		context.runOnClient(minecraft -> {
			EbpConfig.get().maxTier = 4;
			// Puts the beacon ~8 blocks overhead: nothing below it, nothing within reach.
			EbpConfig.get().airPreviewDistance = 8.0D;
			// Explicitly the server path - the whole point of this test.
			EbpConfig.get().preferServerPlacement = true;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(5);
		context.getInput().pressMouse(1);

		context.waitTicks(20);
		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
		sp.getConnection().waitForServerboundPackets();
		context.waitTicks(10);

		sp.getServer().runOnServer(server -> {
			var level = server.overworld();
			BlockPos beacon = findBeaconAbove(level, new BlockPos(0, 99, 0));

			if (beacon == null) {
				throw new AssertionError("no beacon was placed overhead - the server-side path did "
						+ "not run, or the request was rejected");
			}

			int placed = 0;

			for (int layer = 1; layer <= 4; layer++) {
				int y = beacon.getY() - layer;

				for (int dx = -layer; dx <= layer; dx++) {
					for (int dz = -layer; dz <= layer; dz++) {
						if (level.getBlockState(new BlockPos(dx, y, dz)).is(Blocks.IRON_BLOCK)) {
							placed++;
						}
					}
				}
			}

			// 9 + 25 + 49 + 81. Reaching all of these by hand from one spot is impossible.
			assertEquals(164, placed, "blocks in a floating, out-of-reach tier-4 pyramid");
		});

		context.takeScreenshot("server-side-build");
	}

	private static BlockPos findBeaconAbove(net.minecraft.server.level.ServerLevel level, BlockPos from) {
		for (int y = from.getY(); y < from.getY() + 40; y++) {
			BlockPos pos = new BlockPos(from.getX(), y, from.getZ());

			if (level.getBlockState(pos).is(Blocks.BEACON)) {
				return pos;
			}
		}

		return null;
	}

	/**
	 * Solid ground under the beacon: the top layer is free but everything below is rock, so the
	 * preview should show 9 placeable slots and 155 obstructed ones drawn in red.
	 */
	private void testObstructedPreview(ClientGameTestContext context, TestSingleplayerContext sp) {
		// Commands run from the server console, which has no executing entity, so every selector
		// has to be explicit - `@s` resolves to nothing here.
		String[] setup = {
			"gamemode creative @a",
			// Solid rock up to y=98, clear air above it.
			"fill -12 90 -12 16 98 16 minecraft:stone",
			"fill -12 99 -12 16 120 16 minecraft:air",
			// The block the player aims at.
			"setblock 1 100 0 minecraft:stone",
			"clear @a",
			"give @a minecraft:beacon 1",
			"give @a minecraft:iron_block 200",
			// Stand on the rock, look due west (yaw 90) and level (pitch 0).
			"tp @a 5.5 99 0.5 90 0",
		};

		for (String command : setup) {
			sp.getServer().runCommand(command);
		}

		// The arena is built server-side; the client needs the block updates before its copy of
		// the level matches what the plan is asserted against.
		sp.getConnection().waitForClientboundPackets();
		context.waitTicks(20);

		context.runOnClient(minecraft -> {
			EbpConfig.get().maxTier = 4;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(5);

		context.runOnClient(minecraft -> {
			PlacementPlan plan = PlacementPlan.compute(
					minecraft.level,
					BEACON_OBSTRUCTED,
					minecraft.player,
					false,
					4);

			assertEquals(4, plan.tier(), "tier");
			assertEquals(9, plan.count(SlotState.PLACEABLE), "placeable slots");
			assertEquals(155, plan.count(SlotState.OBSTRUCTED), "obstructed slots");
			assertEquals(0, plan.count(SlotState.MISSING_MATERIAL), "missing-material slots");

			// Only the layer directly under the beacon is free, and a beacon's tier is capped by the
			// first incomplete layer counting *down* from the beacon - so this arena powers a tier 1
			// and no more, however much is dug out lower down.
			assertEquals(1, plan.effectiveTier(), "effective tier");

			if (plan.isFullyBuildable()) {
				throw new AssertionError("a plan with 155 obstructions must not report as buildable");
			}

			// The whole point of the feature: obstructions must be reported, not hidden.
			if (!plan.hasObstructions()) {
				throw new AssertionError("expected the plan to report obstructions");
			}

			// Confirm the crosshair really is on the block the arena put there.
			BlockPos looked = minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
					? hit.getBlockPos()
					: null;

			if (!AIM_BLOCK_OBSTRUCTED.equals(looked)) {
				throw new AssertionError("expected to be looking at " + AIM_BLOCK_OBSTRUCTED
						+ " but hit " + looked);
			}
		});

		context.takeScreenshot("hologram-obstructed");
		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(2);
	}

	/**
	 * Open air above a stone floor, capped to tier 1: the click should actually place all nine
	 * base blocks plus the beacon.
	 */
	private void testOneClickBuild(ClientGameTestContext context, TestSingleplayerContext sp) {
		String[] setup = {
			"fill -12 90 -12 16 97 16 minecraft:stone",
			"fill -12 98 -12 16 120 16 minecraft:air",
			"setblock 1 99 0 minecraft:stone",
			"clear @a",
			"give @a minecraft:beacon 1",
			"give @a minecraft:iron_block 64",
			"tp @a 5.5 98 0.5 90 0",
		};

		for (String command : setup) {
			sp.getServer().runCommand(command);
		}

		// The arena is built server-side; the client needs the block updates before its copy of
		// the level matches what the plan is asserted against.
		sp.getConnection().waitForClientboundPackets();
		context.waitTicks(20);

		context.runOnClient(minecraft -> {
			// Cap the tier so the whole pyramid is comfortably inside reach; the reach limit
			// itself is a server rule and is not what this test is checking.
			EbpConfig.get().maxTier = 1;

			// Force the client-side placer. Without this the integrated server would build it,
			// and PlacementExecutor - the path every vanilla/Paper/Spigot player uses - would
			// never be exercised by any test.
			EbpConfig.get().preferServerPlacement = false;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(5);

		context.runOnClient(minecraft -> {
			PlacementPlan plan = PlacementPlan.compute(
					minecraft.level, BEACON_OPEN, minecraft.player, false, 1);

			assertEquals(1, plan.tier(), "tier");
			assertEquals(9, plan.count(SlotState.PLACEABLE), "placeable slots");
			assertEquals(0, plan.count(SlotState.OBSTRUCTED), "obstructed slots");
			assertEquals(1, plan.effectiveTier(), "effective tier");

			if (!plan.isFullyBuildable()) {
				throw new AssertionError("an unobstructed, affordable plan must report as buildable");
			}
		});

		context.takeScreenshot("hologram-buildable");

		// Right-click: this goes through UseBlockCallback exactly as a real click would.
		context.getInput().pressMouse(1);

		// One block per tick, plus a tick for each hotbar switch, the beacon, and slack.
		context.waitTicks(80);
		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
		sp.getConnection().waitForServerboundPackets();
		context.waitTicks(10);

		sp.getServer().runOnServer(server -> {
			var level = server.overworld();
			int placed = 0;

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = new BlockPos(BEACON_OPEN.getX() + dx, 98, BEACON_OPEN.getZ() + dz);

					if (level.getBlockState(pos).is(Blocks.IRON_BLOCK)) {
						placed++;
					}
				}
			}

			assertEquals(9, placed, "iron blocks placed in the base layer");

			if (!level.getBlockState(BEACON_OPEN).is(Blocks.BEACON)) {
				throw new AssertionError("expected a beacon at " + BEACON_OPEN
						+ " but found " + level.getBlockState(BEACON_OPEN));
			}
		});

		context.takeScreenshot("beacon-built");
	}

	/**
	 * Regression test for clicking while aiming at open sky.
	 *
	 * <p>Vanilla routes a click on a block and a click on air down different paths, so hooking
	 * only the block one left the preview visible but unbuildable in the air. The arena has no
	 * block in front of the player, so the crosshair genuinely misses and the click has to travel
	 * through the use-item path instead.
	 */
	private void testBuildWhileAimingAtAir(ClientGameTestContext context, TestSingleplayerContext sp) {
		String[] setup = {
			"fill -12 90 -12 16 97 16 minecraft:stone",
			// Deliberately no aim block: the player must be looking at nothing.
			"fill -12 98 -12 16 120 16 minecraft:air",
			"clear @a",
			"give @a minecraft:beacon 1",
			"give @a minecraft:iron_block 64",
			"tp @a 5.5 98 0.5 90 0",
		};

		for (String command : setup) {
			sp.getServer().runCommand(command);
		}

		sp.getConnection().waitForClientboundPackets();
		context.waitTicks(20);

		context.runOnClient(minecraft -> {
			EbpConfig.get().maxTier = 1;
			// 2.5 blocks ahead lands the pyramid on the floor and inside reach.
			EbpConfig.get().airPreviewDistance = 2.5D;
			// Client-side placer again: this test is about how the click is routed, and the
			// server path would bypass the very code being checked.
			EbpConfig.get().preferServerPlacement = false;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(5);

		context.runOnClient(minecraft -> {
			if (minecraft.hitResult != null
					&& minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				throw new AssertionError("arena is wrong: the player should be aiming at air, but hit "
						+ minecraft.hitResult);
			}
		});

		context.takeScreenshot("hologram-in-air");
		context.getInput().pressMouse(1);

		context.waitTicks(80);
		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
		sp.getConnection().waitForServerboundPackets();
		context.waitTicks(10);

		sp.getServer().runOnServer(server -> {
			var level = server.overworld();
			int placed = 0;

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = new BlockPos(BEACON_IN_AIR.getX() + dx, 98, BEACON_IN_AIR.getZ() + dz);

					if (level.getBlockState(pos).is(Blocks.IRON_BLOCK)) {
						placed++;
					}
				}
			}

			assertEquals(9, placed, "iron blocks placed after clicking at air");

			if (!level.getBlockState(BEACON_IN_AIR).is(Blocks.BEACON)) {
				throw new AssertionError("expected a beacon at " + BEACON_IN_AIR
						+ " but found " + level.getBlockState(BEACON_IN_AIR));
			}
		});
	}

	private static void assertEquals(int expected, int actual, String what) {
		if (expected != actual) {
			throw new AssertionError("expected " + expected + " " + what + " but got " + actual);
		}
	}
}

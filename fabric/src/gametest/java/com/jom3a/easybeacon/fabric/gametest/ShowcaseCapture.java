package com.jom3a.easybeacon.fabric.gametest;

import java.nio.file.Path;

import com.jom3a.easybeacon.EbpConfig;
import com.jom3a.easybeacon.client.EbpKeybinds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * Captures the images used in the README.
 *
 * <p>Kept as a test rather than done by hand so the documentation stays reproducible: the
 * screenshots are generated from the same code they document, and regenerating them after a
 * visual change is one command rather than a manual re-shoot.
 *
 * <p>Frames land in {@code build/run/clientGameTest/showcase/}. The stills are named for the order
 * they appear in the README and on the Modrinth gallery, so the output drops straight into
 * {@code docs/media/}; the loose {@code build-frame-*} and {@code scroll-frame-*} sequences are
 * stitched afterwards into {@code 3-build.gif} and {@code 5-scroll.gif}. {@code banner-plate} is
 * outside that sequence - it is raw material for cover art rather than a gallery image.
 */
public class ShowcaseCapture implements FabricClientGameTest {

	/** Long enough for the "game mode updated" and recipe toasts to clear out of shot. */
	private static final int SETTLE_TICKS = 120;

	private Path showcaseDir;

	@Override
	public void runTest(ClientGameTestContext context) {
		showcaseDir = Path.of("showcase");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			buildArena(context, singleplayer);

			captureObstructed(context, singleplayer);
			captureReadyAndBuild(context, singleplayer);
			captureScroll(context, singleplayer);
			captureBannerPlate(context, singleplayer);
		}
	}

	/**
	 * A lit beacon and its beam against a black backdrop, with the HUD hidden.
	 *
	 * <p>Shot on black on purpose: the beam is emissive, so treating brightness as opacity
	 * afterwards gives a cut-out that composites additively over any background — which is how a
	 * glow actually behaves, and is far cleaner than trying to key a sky gradient out of it.
	 *
	 * <p>The beacon still needs an unobstructed column to the sky to light up, so the backdrop
	 * goes behind it rather than over it.
	 */
	private void captureBannerPlate(ClientGameTestContext context, TestSingleplayerContext sp) {
		// Move there before building anything: /fill and /setblock refuse unloaded chunks with
		// "That position is not loaded", and this scene sits hundreds of blocks from the others.
		sp.getServer().runCommand("gamemode spectator @a");
		sp.getServer().runCommand("tp @a 211.5 152 211.5 135 -12");
		sp.getConnection().waitForChunksRender();
		context.waitTicks(40);

		String[] setup = {
			"time set midnight",
			"weather clear",
			// Somewhere far from the earlier scenes, floored and walled in black.
			// High above any terrain, so the beacon is guaranteed an unobstructed view of the sky
			// and lights up. Every region stays under /fill's 32768-block limit - exceed it and
			// the command fails silently and the whole set dressing never appears.
			"fill 185 150 185 214 179 214 minecraft:air",
			"fill 185 149 185 214 149 214 minecraft:black_concrete",
			// The view looks diagonally towards -X/-Z, so the backdrop is a corner of two walls,
			// tall enough to sit behind the stretch of beam that ends up in frame.
			"fill 185 150 185 214 179 186 minecraft:black_concrete",
			"fill 185 150 187 186 179 214 minecraft:black_concrete",
			// A full tier-4 base so the beam is at its brightest.
			"fill 196 150 196 204 150 204 minecraft:iron_block",
			"fill 197 151 197 203 151 203 minecraft:iron_block",
			"fill 198 152 198 202 152 202 minecraft:iron_block",
			"fill 199 153 199 201 153 201 minecraft:iron_block",
			"setblock 200 154 200 minecraft:beacon",
			// Re-issued so the camera is definitely where it should be after the terrain changes.
			// Three-quarter view roughly level with the beacon, tilted up just enough to put the
			// base low in frame and let the beam run up through it.
			"tp @a 211.5 152 211.5 135 -12",
		};

		for (String command : setup) {
			sp.getServer().runCommand(command);
		}

		sp.getConnection().waitForClientboundPackets();

		// The scene is hundreds of blocks from the earlier ones, so the client has to load and
		// render those chunks before there is anything to photograph. Without this the shot is
		// just empty sky.
		sp.getConnection().waitForChunksRender();

		// The beacon revalidates its pyramid on a slow tick, so give it time to light up.
		context.waitTicks(160);

		sp.getServer().runOnServer(server -> {
			var level = server.overworld();

			if (!level.getBlockState(new net.minecraft.core.BlockPos(200, 154, 200))
					.is(net.minecraft.world.level.block.Blocks.BEACON)) {
				throw new AssertionError("banner scene did not build - no beacon at 200,154,200 "
						+ "(check that no /fill exceeded the 32768-block limit)");
			}
		});

		// Spectator already hides the hotbar and the held item, and 26.2 no longer exposes a
		// hide-HUD flag on Options, so that is all the cleanup the shot needs.
		context.waitTicks(40);
		shot(context, "banner-plate");
		context.waitTicks(5);
	}

	/** A flat grass plain in permanent noon, so shots are consistent and read clearly. */
	private void buildArena(ClientGameTestContext context, TestSingleplayerContext sp) {
		String[] setup = {
			"gamemode creative @a",
			"gamerule doDaylightCycle false",
			"gamerule doWeatherCycle false",
			"gamerule doMobSpawning false",
			"time set noon",
			"weather clear",
			"fill -24 60 -24 24 63 24 minecraft:stone",
			"fill -24 64 -24 24 64 24 minecraft:grass_block",
			// Split: 49x49 is 2401 blocks a layer, so anything past ~13 layers busts /fill's
			// 32768-block cap and fails silently.
			"fill -24 65 -24 24 77 24 minecraft:air",
			"fill -24 78 -24 24 90 24 minecraft:air",
		};

		for (String command : setup) {
			sp.getServer().runCommand(command);
		}

		sp.getConnection().waitForClientboundPackets();
		context.waitTicks(SETTLE_TICKS);
	}

	/** Aiming at flat ground: the pyramid wants to go into the earth, so it is nearly all red. */
	private void captureObstructed(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runCommand("clear @a");
		sp.getServer().runCommand("give @a minecraft:beacon 1");
		sp.getServer().runCommand("give @a minecraft:iron_block 200");
		// Stand on the grass and look down at it.
		sp.getServer().runCommand("tp @a 0.5 65 7.5 180 28");

		context.waitTicks(SETTLE_TICKS);

		context.runOnClient(minecraft -> {
			EbpConfig.get().maxTier = 4;
			EbpConfig.get().preferServerPlacement = true;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(10);
		shot(context, "2-obstructions");
		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(5);
	}

	/**
	 * A tier-1 pyramid sitting on the surface: everything is free, in reach and supported, so it
	 * previews green and can be built block by block for the animation.
	 */
	private void captureReadyAndBuild(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runCommand("fill -6 65 -6 6 70 6 minecraft:air");
		sp.getServer().runCommand("clear @a");
		sp.getServer().runCommand("give @a minecraft:beacon 1");
		sp.getServer().runCommand("give @a minecraft:iron_block 9");
		// A three-quarter view from (2.4, ., 2.4) facing the origin. 2.7 blocks along that line
		// from eye height 66.62 lands the beacon at (0, 66, 0): one above the grass, so its layer
		// rests on the surface and every slot stays inside interaction range for a live build.
		sp.getServer().runCommand("tp @a 2.4 65 2.4 135 0");

		context.waitTicks(SETTLE_TICKS);

		context.runOnClient(minecraft -> {
			EbpConfig.get().maxTier = 1;
			EbpConfig.get().airPreviewDistance = 2.7D;
			// Client-side, one at a time: that is what makes a watchable animation.
			EbpConfig.get().preferServerPlacement = false;
			EbpConfig.get().maxInFlight = 1;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(10);
		shot(context, "1-ghost-blocks");

		context.getInput().pressMouse(1);

		// Frames through the build. Flat filenames: the harness writes into one directory and
		// will not create nested ones.
		for (int frame = 0; frame < 44; frame++) {
			context.waitTicks(2);
			shot(context, "build-frame-" + String.format(java.util.Locale.ROOT, "%03d", frame));
		}

		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);

		// Step back for a wide shot of the finished beacon and its beam.
		sp.getServer().runCommand("tp @a 7.5 66 7.5 135 12");
		context.waitTicks(60);
		shot(context, "4-finished-beacon");
	}

	/** The mouse wheel moving the preview around. */
	private void captureScroll(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runCommand("fill -8 65 -8 8 80 8 minecraft:air");
		sp.getServer().runCommand("clear @a");
		sp.getServer().runCommand("give @a minecraft:beacon 1");
		sp.getServer().runCommand("give @a minecraft:iron_block 64");
		sp.getServer().runCommand("tp @a 6.5 65 0.5 90 5");

		context.waitTicks(SETTLE_TICKS);

		context.runOnClient(minecraft -> {
			EbpConfig.get().maxTier = 2;
			EbpConfig.get().airPreviewDistance = 4.0D;
		});

		context.getInput().holdKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(10);

		int frame = 0;

		// Push it away, then pull it back, so the loop returns to where it started.
		for (int step = 0; step < 8; step++) {
			context.getInput().scroll(1.0D);
			context.waitTicks(3);
			shot(context, "scroll-frame-" + String.format(java.util.Locale.ROOT, "%03d", frame++));
		}

		for (int step = 0; step < 8; step++) {
			context.getInput().scroll(-1.0D);
			context.waitTicks(3);
			shot(context, "scroll-frame-" + String.format(java.util.Locale.ROOT, "%03d", frame++));
		}

		context.getInput().releaseKey(EbpKeybinds.SHOW_HOLOGRAM);
		context.waitTicks(5);
	}

	/**
	 * Captures at the window's own size. Forcing a different size re-renders into an off-screen
	 * target and leaves a visible seam where the two framebuffers meet.
	 */
	private void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
				.disableCounterPrefix()
				.withDestinationDir(showcaseDir));
	}
}

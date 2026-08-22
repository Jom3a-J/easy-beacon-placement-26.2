package com.jom3a.easybeacon.paper.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Automated tests for the Paper half of Easy Beacon Placement, run inside a real Paper server.
 *
 * <p>Its counterpart on the mod side is the NeoForge harness. Both exist for the same reason: the
 * Fabric client game tests cover the shared logic, but each platform's own wiring — the bit that
 * differs — had nothing driving it.
 *
 * <p>Requests are fed in through {@link org.bukkit.plugin.messaging.Messenger#dispatchIncomingMessage},
 * which is the same route a real client's plugin message takes. That means the channel registration
 * itself is under test: if the plugin ever stopped registering it, dispatch would fail rather than
 * silently do nothing.
 *
 * <p>The player is synthetic — see {@link HarnessPlayer} for why it has to be, and what stays real.
 */
public final class EbpPaperHarness extends JavaPlugin implements Listener {
	private static final String CHANNEL = "easy_beacon_placement:build_pyramid";

	/** Stands in for a land-claim plugin: whatever this matches gets refused. */
	private volatile Predicate<Block> veto = block -> false;

	private final List<String> failures = new ArrayList<>();
	private int checks;

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);

		// One tick after the server finishes loading, so the world is genuinely ready.
		Bukkit.getScheduler().runTaskLater(this, this::runAll, 20L);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (veto.test(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	private void runAll() {
		getLogger().info("=== EBP Paper harness starting ===");
		World world = Bukkit.getWorlds().get(0);

		try {
			buildsACompletePyramid(world);
			vetoedPlacementsAreRolledBackAndRefunded(world);
			aPartialVetoLosesOnlyTheRefusedBlocks(world);
			refusesToBuildTooFarAway(world);
			refusesWithoutABeaconInHand(world);
			refusesInAdventureMode(world);
			malformedPayloadsAreIgnored(world);
		} catch (Throwable t) {
			getLogger().severe("harness threw: " + t);
			failures.add("harness threw: " + t);
		} finally {
			veto = block -> false;
		}

		finish();
	}

	// --- tests -------------------------------------------------------------------------------

	private void buildsACompletePyramid(World world) {
		Setup s = setup(world, 0, 9, true, GameMode.SURVIVAL);
		dispatch(s, 1);

		check("tier 1 places 9 base blocks", 9, ironCount(world, s.x, s.y, s.z));
		check("the beacon itself is placed", Material.BEACON, world.getBlockAt(s.x, s.y, s.z).getType());
		check("base blocks are consumed", 0, s.state.count(Material.IRON_BLOCK));
	}

	/** The case the plugin description promises, and nothing had ever exercised. */
	private void vetoedPlacementsAreRolledBackAndRefunded(World world) {
		Setup s = setup(world, 64, 9, true, GameMode.SURVIVAL);

		veto = block -> true;
		dispatch(s, 1);
		veto = block -> false;

		check("a refused placement leaves nothing behind", 0, ironCount(world, s.x, s.y, s.z));
		check("a refused beacon is not placed", true, world.getBlockAt(s.x, s.y, s.z).getType().isAir());
		check("refused base blocks are refunded", 9, s.state.count(Material.IRON_BLOCK));
		check("a refused beacon is refunded", 1, s.state.count(Material.BEACON));
	}

	private void aPartialVetoLosesOnlyTheRefusedBlocks(World world) {
		Setup s = setup(world, 128, 9, true, GameMode.SURVIVAL);
		Block refused = world.getBlockAt(s.x - 1, s.y - 1, s.z - 1);

		veto = block -> block.getX() == refused.getX()
				&& block.getY() == refused.getY()
				&& block.getZ() == refused.getZ();
		dispatch(s, 1);
		veto = block -> false;

		check("the other eight still go in", 8, ironCount(world, s.x, s.y, s.z));
		check("the refused slot stays empty", true, refused.getType().isAir());
		check("only the refused block is refunded", 1, s.state.count(Material.IRON_BLOCK));
	}

	private void refusesToBuildTooFarAway(World world) {
		Setup s = setup(world, 192, 9, true, GameMode.SURVIVAL);
		dispatchAt(s, s.x + 400, s.y, s.z + 400, 1);

		check("a distant request builds nothing", 0, ironCount(world, s.x + 400, s.y, s.z + 400));
		check("and costs the player nothing", 9, s.state.count(Material.IRON_BLOCK));
	}

	private void refusesWithoutABeaconInHand(World world) {
		Setup s = setup(world, 256, 9, false, GameMode.SURVIVAL);
		dispatch(s, 1);

		check("no beacon in hand builds nothing", 0, ironCount(world, s.x, s.y, s.z));
		check("and costs the player nothing", 9, s.state.count(Material.IRON_BLOCK));
	}

	private void refusesInAdventureMode(World world) {
		Setup s = setup(world, 320, 9, true, GameMode.ADVENTURE);
		dispatch(s, 1);

		check("adventure mode builds nothing", 0, ironCount(world, s.x, s.y, s.z));
		check("and costs the player nothing", 9, s.state.count(Material.IRON_BLOCK));
	}

	/**
	 * A hostile or mismatched client should be dropped, not crash the server thread.
	 *
	 * <p>Paper deprecates the {@code Player} overload of {@code dispatchIncomingMessage} in favour
	 * of one taking a {@code PlayerConnection}, which a synthetic player does not have. The
	 * deprecated form is the one that models a real client message here.
	 */
	@SuppressWarnings("deprecation")
	private void malformedPayloadsAreIgnored(World world) {
		Setup s = setup(world, 384, 9, true, GameMode.SURVIVAL);

		for (byte[] junk : new byte[][] { {}, { 1, 2, 3 }, new byte[64] }) {
			Bukkit.getMessenger().dispatchIncomingMessage(s.player, CHANNEL, junk);
		}

		check("malformed payloads build nothing", 0, ironCount(world, s.x, s.y, s.z));
		check("and the server is still running", true, Bukkit.getWorlds().size() > 0);
	}

	// --- helpers -----------------------------------------------------------------------------

	private record Setup(HarnessPlayer.State state, org.bukkit.entity.Player player,
			int x, int y, int z) {
	}

	/** Clears a working area and returns a fresh player standing beside it. */
	private Setup setup(World world, int offsetX, int ironBlocks, boolean holdingBeacon, GameMode mode) {
		int x = offsetX;
		int y = 0;
		int z = 0;

		for (int dx = -6; dx <= 6; dx++) {
			for (int dy = -6; dy <= 2; dy++) {
				for (int dz = -6; dz <= 6; dz++) {
					world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
				}
			}
		}

		HarnessPlayer.State state = new HarnessPlayer.State();
		state.world = world;
		state.location = new Location(world, x + 0.5D, y, z + 2.5D);
		state.gameMode = mode;
		state.clear();

		// Slot 0 is the main hand; the plugin looks there and in the offhand for the beacon.
		if (holdingBeacon) {
			state.set(0, new ItemStack(Material.BEACON, 1));
		}

		if (ironBlocks > 0) {
			state.set(1, new ItemStack(Material.IRON_BLOCK, ironBlocks));
		}

		return new Setup(state, HarnessPlayer.create(state), x, y, z);
	}

	private void dispatch(Setup s, int tier) {
		dispatchAt(s, s.x, s.y, s.z, tier);
	}

	/**
	 * Encodes a request exactly as the mod's StreamCodec does, and feeds it in as a real message.
	 *
	 * <p>See {@link #malformedPayloadsAreIgnored} for why the deprecated overload is the right one.
	 */
	@SuppressWarnings("deprecation")
	private void dispatchAt(Setup s, int x, int y, int z, int tier) {
		byte[] payload = Payloads.buildPyramid(x, y, z, tier);
		Bukkit.getMessenger().dispatchIncomingMessage(s.player, CHANNEL, payload);
	}

	private static int ironCount(World world, int beaconX, int beaconY, int beaconZ) {
		int found = 0;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (world.getBlockAt(beaconX + dx, beaconY - 1, beaconZ + dz).getType() == Material.IRON_BLOCK) {
					found++;
				}
			}
		}

		return found;
	}

	private void check(String what, Object expected, Object actual) {
		checks++;

		if (expected.equals(actual)) {
			getLogger().info("  PASS  " + what);
		} else {
			getLogger().severe("  FAIL  " + what + " -- expected " + expected + ", got " + actual);
			failures.add(what + " (expected " + expected + ", got " + actual + ")");
		}
	}

	/**
	 * Writes the verdict where Gradle can read it, then stops the server.
	 *
	 * <p>Through a file rather than an exit code: Bukkit owns the shutdown path, and forcing a
	 * non-zero exit out of a plugin means halting the JVM mid-save. The build task treats a missing
	 * file as a failure too, so a server that dies before reaching this cannot pass by accident.
	 */
	private void finish() {
		String verdict = failures.isEmpty()
				? "PASS - " + checks + " checks"
				: "FAIL - " + failures.size() + " of " + checks + " checks\n  " + String.join("\n  ", failures);

		getLogger().info("=== EBP Paper harness " + verdict + " ===");

		try {
			Files.writeString(Path.of("harness-result.txt"), verdict + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		Bukkit.shutdown();
	}
}

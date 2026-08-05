package com.jom3a.easybeacon.paper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Server half of Easy Beacon Placement, for Paper and Spigot.
 *
 * <p>Paper cannot load the Fabric or NeoForge jar — it is not a mod loader — so without this the
 * client falls back to placing every block itself and is limited by interaction range. Installing
 * this lifts that limit, exactly as installing the mod on a Fabric/NeoForge server does.
 *
 * <p>No client-side change is needed. Registering an incoming plugin channel makes the server
 * advertise it through vanilla's {@code minecraft:register} mechanism, which is the same signal
 * the client already uses to decide whether the server can build for it.
 */
public final class EasyBeaconPlacementPlugin extends JavaPlugin implements PluginMessageListener {
	/** Must match {@code BuildPyramidPayload.TYPE} in the mod. */
	private static final String CHANNEL = "easy_beacon_placement:build_pyramid";

	/**
	 * Furthest the beacon may be from the player. Matches the mod's server-side limit: generous
	 * enough that reach never gets in the way, small enough that a tampered client cannot use
	 * this to build at a distance.
	 */
	private static final int MAX_BUILD_DISTANCE = 48;

	/**
	 * Whether the first build has been announced. Admins otherwise have no way to tell whether
	 * clients are actually reaching the plugin, since a client that fails to detect the channel
	 * silently falls back to placing blocks itself and looks identical from the server's side.
	 */
	private boolean announcedFirstBuild;

	/**
	 * Shortest gap between accepted build requests from one player.
	 *
	 * <p>A single request can move 164 blocks, so an unthrottled client could spam requests and
	 * make the server do unbounded work. Rate limiting is the server's job precisely because the
	 * client cannot be trusted to do it.
	 */
	private static final long BUILD_COOLDOWN_MILLIS = 500L;

	private final Map<UUID, Long> lastBuildAt = new HashMap<>();

	@Override
	public void onEnable() {
		Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
		getLogger().info("Listening on " + CHANNEL + "; clients with the mod will build server-side.");
	}

	@Override
	public void onDisable() {
		Bukkit.getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
	}

	@Override
	public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
		if (!CHANNEL.equals(channel)) {
			return;
		}

		long packedPos;
		int tier;

		// The payload is whatever the mod's StreamCodec wrote: a packed BlockPos long, then a
		// VarInt tier. Anything else is a malformed or hostile packet and is simply dropped.
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
			packedPos = in.readLong();
			tier = readVarInt(in);
		} catch (IOException e) {
			return;
		}

		int x = PyramidGeometry.unpackX(packedPos);
		int y = PyramidGeometry.unpackY(packedPos);
		int z = PyramidGeometry.unpackZ(packedPos);
		int clampedTier = Math.max(PyramidGeometry.MIN_TIER, Math.min(tier, PyramidGeometry.MAX_TIER));

		// Bukkit dispatches plugin messages on the main thread, but touching the world off it
		// would corrupt chunk state, so this never relies on that being true. The rate-limit
		// check has to happen inside this hop too - it mutates shared state, so running it on
		// the calling thread would be a data race for the sake of an early return.
		Runnable task = () -> {
			if (allowBuild(player)) {
				build(player, x, y, z, clampedTier);
			}
		};

		if (Bukkit.isPrimaryThread()) {
			task.run();
		} else {
			Bukkit.getScheduler().runTask(this, task);
		}
	}

	/** Per-player rate limit. Also forgets players who have left, so the map cannot grow forever. */
	private boolean allowBuild(Player player) {
		long now = System.currentTimeMillis();
		lastBuildAt.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);

		Long previous = lastBuildAt.get(player.getUniqueId());

		if (previous != null && now - previous < BUILD_COOLDOWN_MILLIS) {
			return false;
		}

		lastBuildAt.put(player.getUniqueId(), now);
		return true;
	}

	/**
	 * Builds the pyramid, re-checking everything the client claimed.
	 *
	 * <p>Nothing in the request is trusted. Every block is additionally offered to the server as a
	 * {@link BlockPlaceEvent}, so land-claim and protection plugins get to veto it exactly as they
	 * would a hand-placed block — that matters far more on Paper than on a modded server.
	 */
	private void build(Player player, int beaconX, int beaconY, int beaconZ, int tier) {
		World world = player.getWorld();

		// Spectators and adventure-mode players cannot place blocks by hand, so they cannot here.
		if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) {
			return;
		}

		if (!isCloseEnough(player, beaconX, beaconY, beaconZ)) {
			return;
		}

		// Keeps this tied to the action the player could perform by hand, rather than becoming a
		// general-purpose remote block placer.
		if (!holdsBeacon(player)) {
			return;
		}

		boolean creative = player.getGameMode() == GameMode.CREATIVE;
		boolean ranOut = false;
		int placed = 0;

		outer:
		for (int layer = tier; layer >= 1; layer--) {
			int y = beaconY - layer;

			for (int dx = -layer; dx <= layer; dx++) {
				for (int dz = -layer; dz <= layer; dz++) {
					if (!canBuildAt(player, world, beaconX + dx, y, beaconZ + dz)) {
						continue;
					}

					Block block = world.getBlockAt(beaconX + dx, y, beaconZ + dz);

					if (isBeaconBase(block.getType()) || !isReplaceable(block.getType())) {
						continue;
					}

					Material material = takeBaseBlock(player, creative);

					if (material == null) {
						ranOut = true;
						break outer;
					}

					if (placeBlock(player, block, material)) {
						placed++;
					} else if (!creative) {
						// Refunded: a protection plugin refused this position.
						giveBack(player, material);
					}
				}
			}
		}

		placeBeacon(player, world.getBlockAt(beaconX, beaconY, beaconZ), creative);

		if (!announcedFirstBuild) {
			announcedFirstBuild = true;
			getLogger().info("Server-side placement is working: built a tier " + tier + " pyramid ("
					+ placed + " blocks) for " + player.getName()
					+ ". This message is not repeated.");
		}

		if (ranOut) {
			player.sendActionBar(net.kyori.adventure.text.Component.text(
					"Out of beacon base blocks"));
		}
	}

	/** World height and world border still apply; the client supplies these coordinates. */
	private boolean canBuildAt(Player player, World world, int x, int y, int z) {
		if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
			return false;
		}

		return world.getWorldBorder().isInside(new Location(world, x + 0.5D, y + 0.5D, z + 0.5D));
	}

	private void placeBeacon(Player player, Block block, boolean creative) {
		if (!canBuildAt(player, block.getWorld(), block.getX(), block.getY(), block.getZ())
				|| !isReplaceable(block.getType())) {
			return;
		}

		if (!creative && !consume(player.getInventory(), Material.BEACON)) {
			return;
		}

		if (!placeBlock(player, block, Material.BEACON) && !creative) {
			giveBack(player, Material.BEACON);
		}
	}

	/**
	 * Sets the block, first asking the rest of the server for permission.
	 *
	 * @return false if a protection plugin cancelled it, in which case nothing was changed
	 */
	private boolean placeBlock(Player player, Block block, Material material) {
		BlockState previous = block.getState();
		block.setType(material, true);

		BlockPlaceEvent event = new BlockPlaceEvent(
				block, previous, block, new ItemStack(material), player, true, EquipmentSlot.HAND);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled() || !event.canBuild()) {
			previous.update(true, false);
			return false;
		}

		return true;
	}

	private boolean isCloseEnough(Player player, int x, int y, int z) {
		double dx = player.getLocation().getX() - (x + 0.5D);
		double dy = player.getLocation().getY() - (y + 0.5D);
		double dz = player.getLocation().getZ() - (z + 0.5D);

		return dx * dx + dy * dy + dz * dz <= (double) MAX_BUILD_DISTANCE * MAX_BUILD_DISTANCE;
	}

	private boolean holdsBeacon(Player player) {
		PlayerInventory inventory = player.getInventory();

		return inventory.getItemInMainHand().getType() == Material.BEACON
				|| inventory.getItemInOffHand().getType() == Material.BEACON;
	}

	/**
	 * Consumes one beacon base block and returns which it was, or null if there are none left.
	 *
	 * <p>Written back through {@link PlayerInventory#setItem} rather than by mutating the array
	 * from {@code getContents()}. Those stacks are live mirrors on CraftBukkit today, but that is
	 * an implementation detail rather than an API guarantee — and if it ever stopped holding, the
	 * decrement would silently vanish and hand players free blocks.
	 */
	private Material takeBaseBlock(Player player, boolean creative) {
		PlayerInventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack == null || stack.getAmount() <= 0 || !isBeaconBase(stack.getType())) {
				continue;
			}

			Material material = stack.getType();

			if (!creative) {
				takeOne(inventory, slot, stack);
			}

			return material;
		}

		return null;
	}

	private boolean consume(PlayerInventory inventory, Material material) {
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack != null && stack.getAmount() > 0 && stack.getType() == material) {
				takeOne(inventory, slot, stack);
				return true;
			}
		}

		return false;
	}

	private static void takeOne(PlayerInventory inventory, int slot, ItemStack stack) {
		if (stack.getAmount() <= 1) {
			inventory.setItem(slot, null);
			return;
		}

		stack.setAmount(stack.getAmount() - 1);
		inventory.setItem(slot, stack);
	}

	private void giveBack(Player player, Material material) {
		player.getInventory().addItem(new ItemStack(material));
	}

	private static boolean isBeaconBase(Material material) {
		Tag<Material> tag = Bukkit.getTag(
				Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("beacon_base_blocks"), Material.class);

		return tag != null && tag.isTagged(material);
	}

	private static boolean isReplaceable(Material material) {
		if (material.isAir()) {
			return true;
		}

		Tag<Material> tag = Bukkit.getTag(
				Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("replaceable"), Material.class);

		return tag != null && tag.isTagged(material);
	}

	/** VarInt as Minecraft encodes it: 7 bits per byte, high bit signals continuation. */
	private static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;

		for (int position = 0; position < 32; position += 7) {
			byte current = in.readByte();
			value |= (current & 0x7F) << position;

			if ((current & 0x80) == 0) {
				return value;
			}
		}

		throw new IOException("VarInt too long");
	}
}

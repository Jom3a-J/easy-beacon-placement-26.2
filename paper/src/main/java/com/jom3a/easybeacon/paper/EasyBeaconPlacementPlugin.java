package com.jom3a.easybeacon.paper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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

	/**
	 * Block tags, looked up once instead of per block.
	 *
	 * <p>{@link Bukkit#getTag} is a registry lookup, and a tier-4 build asks about membership for
	 * every one of 164 positions plus every inventory slot it walks. Resolved lazily rather than in
	 * {@link #onEnable()} so that a server which has not finished building its registries when
	 * plugins enable still ends up with the real tag rather than a cached null.
	 */
	private Tag<Material> beaconBaseTag;
	private Tag<Material> replaceableTag;

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

		BuildBounds bounds = new BuildBounds(player, world);

		outer:
		for (int layer = tier; layer >= 1; layer--) {
			int y = beaconY - layer;

			for (int dx = -layer; dx <= layer; dx++) {
				for (int dz = -layer; dz <= layer; dz++) {
					if (!bounds.allows(beaconX + dx, y, beaconZ + dz)) {
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

		placeBeacon(player, world.getBlockAt(beaconX, beaconY, beaconZ), creative, bounds);

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

	/**
	 * Where in the world this player is allowed to have blocks put for them, resolved once for a
	 * whole build.
	 *
	 * <p>A tier-4 build asks the question 165 times, and most of the answer does not vary between
	 * positions: the spawn-protection radius, whether it applies to this player at all, and where
	 * spawn is. Those are settled up front, leaving a per-block check that is arithmetic plus one
	 * world-border call — which reuses a single {@link Location} rather than allocating one per
	 * block, since the border API takes nothing else.
	 */
	private static final class BuildBounds {
		private final World world;
		private final Location scratch;

		/** Chebyshev radius around spawn this player may not build inside; 0 when unrestricted. */
		private final int spawnRadius;
		private final int spawnX;
		private final int spawnZ;

		BuildBounds(Player player, World world) {
			this.world = world;
			this.scratch = new Location(world, 0.0D, 0.0D, 0.0D);

			int radius = Bukkit.getSpawnRadius();
			List<World> worlds = Bukkit.getWorlds();

			// Vanilla only protects the spawn of the main world, and never against operators.
			boolean applies = radius > 0
					&& !player.isOp()
					&& !worlds.isEmpty()
					&& worlds.get(0).equals(world);

			this.spawnRadius = applies ? radius : 0;

			Location spawn = applies ? world.getSpawnLocation() : null;
			this.spawnX = spawn == null ? 0 : spawn.getBlockX();
			this.spawnZ = spawn == null ? 0 : spawn.getBlockZ();
		}

		/**
		 * World height, chunk loading, spawn protection and the world border, none of which the
		 * client's coordinates are trusted to have respected.
		 *
		 * <p>Spawn protection is spelled out here rather than left to {@link BlockPlaceEvent}:
		 * protection plugins hook that event, but vanilla spawn protection does not go through it.
		 * Without this check the plugin would hand players a way to build somewhere they could not
		 * reach by hand, which is the one thing server-side placement must never do.
		 */
		boolean allows(int x, int y, int z) {
			if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
				return false;
			}

			// Never pull a chunk in — or generate one — as a side effect of a build request.
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				return false;
			}

			if (spawnRadius > 0
					&& Math.max(Math.abs(x - spawnX), Math.abs(z - spawnZ)) <= spawnRadius) {
				return false;
			}

			scratch.setX(x + 0.5D);
			scratch.setY(y + 0.5D);
			scratch.setZ(z + 0.5D);

			return world.getWorldBorder().isInside(scratch);
		}
	}

	private void placeBeacon(Player player, Block block, boolean creative, BuildBounds bounds) {
		if (!bounds.allows(block.getX(), block.getY(), block.getZ())
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

	/**
	 * Returns a block that was charged for but never placed.
	 *
	 * <p>{@link org.bukkit.inventory.Inventory#addItem} hands back whatever did not fit rather than
	 * throwing, and dropping that on the floor of this method would quietly delete a player's
	 * blocks — the inventory is at its fullest precisely when a build has just failed. Anything
	 * that will not fit goes on the ground at their feet instead, which is what vanilla does with
	 * items a full inventory cannot take.
	 */
	private void giveBack(Player player, Material material) {
		for (ItemStack leftover : player.getInventory().addItem(new ItemStack(material)).values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), leftover);
		}
	}

	private boolean isBeaconBase(Material material) {
		if (beaconBaseTag == null) {
			beaconBaseTag = blockTag("beacon_base_blocks");
		}

		return beaconBaseTag != null && beaconBaseTag.isTagged(material);
	}

	private boolean isReplaceable(Material material) {
		if (material.isAir()) {
			return true;
		}

		if (replaceableTag == null) {
			replaceableTag = blockTag("replaceable");
		}

		return replaceableTag != null && replaceableTag.isTagged(material);
	}

	private static Tag<Material> blockTag(String name) {
		return Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(name), Material.class);
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

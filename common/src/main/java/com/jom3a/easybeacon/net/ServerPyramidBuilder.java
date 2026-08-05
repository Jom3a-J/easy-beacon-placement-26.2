package com.jom3a.easybeacon.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jom3a.easybeacon.beacon.BeaconMaterials;
import com.jom3a.easybeacon.beacon.BeaconPyramid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side handling of a {@link BuildPyramidPayload}.
 *
 * <p>This is what removes the reach limit: the server is the authority on block placement, so
 * when it does the building there is no interaction-range check to satisfy and no need for each
 * block to have an existing face to be clicked against.
 *
 * <h2>Trusting the client</h2>
 *
 * <p>The request arrives from a client, which may be modified, so none of it is trusted. The
 * server independently:
 *
 * <ul>
 *   <li>clamps the tier to the legal 1-4 range;</li>
 *   <li>rejects anything further than {@link #MAX_BUILD_DISTANCE} from the player, so a tampered
 *       client cannot build across the world;</li>
 *   <li>requires the player to be holding a beacon and able to build at all;</li>
 *   <li>honours world border, world height and spawn protection per block;</li>
 *   <li>only replaces blocks that are genuinely replaceable — it never overwrites terrain;</li>
 *   <li>consumes the blocks from the player's inventory, and stops when they run out.</li>
 * </ul>
 *
 * <p>In other words this grants no ability the player did not already have by hand; it only saves
 * them the walking. That matters because the mod is meant to be installable on real servers.
 */
public final class ServerPyramidBuilder {
	/**
	 * Furthest the beacon may be from the player. Generous enough that reach never gets in the
	 * way, small enough that a tampered client cannot use this to grief at a distance.
	 */
	public static final int MAX_BUILD_DISTANCE = 48;

	/**
	 * Shortest gap between accepted build requests from one player.
	 *
	 * <p>A single request can move 164 blocks, so an unthrottled client could spam requests and
	 * make the server do unbounded work. Rate limiting belongs here precisely because the client
	 * is the thing that cannot be trusted.
	 */
	private static final long BUILD_COOLDOWN_MILLIS = 500L;

	private static final Map<UUID, Long> LAST_BUILD_AT = new HashMap<>();

	private ServerPyramidBuilder() {
	}

	public static void handle(ServerPlayer player, BuildPyramidPayload payload) {
		if (!allowBuild(player)) {
			return;
		}

		buildChecked(player, payload);
	}

	/** Per-player rate limit. Entries for absent players are dropped so the map cannot grow forever. */
	private static boolean allowBuild(ServerPlayer player) {
		long now = System.currentTimeMillis();
		MinecraftServer server = player.level().getServer();

		LAST_BUILD_AT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);

		Long previous = LAST_BUILD_AT.get(player.getUUID());

		if (previous != null && now - previous < BUILD_COOLDOWN_MILLIS) {
			return false;
		}

		LAST_BUILD_AT.put(player.getUUID(), now);
		return true;
	}

	private static void buildChecked(ServerPlayer player, BuildPyramidPayload payload) {
		ServerLevel level = player.level();
		BlockPos beaconPos = payload.beaconPos();
		int tier = Math.clamp(payload.tier(), BeaconPyramid.MIN_TIER, BeaconPyramid.MAX_TIER);

		if (!player.mayBuild() || !isCloseEnough(player, beaconPos)) {
			return;
		}

		// Requiring the beacon in hand keeps this tied to the same action the player could
		// perform manually, rather than becoming a general-purpose remote block placer.
		if (!player.getMainHandItem().is(Items.BEACON) && !player.getOffhandItem().is(Items.BEACON)) {
			return;
		}

		List<BlockPos> placed = new ArrayList<>();
		BlockState placedState = null;
		boolean ranOut = false;

		for (BlockPos pos : BeaconPyramid.basePositions(beaconPos, tier)) {
			if (!canBuildAt(player, level, pos)) {
				continue;
			}

			BlockState existing = level.getBlockState(pos);

			// Already correct, or solid terrain we must not overwrite.
			if (BeaconMaterials.isBeaconBase(existing) || !existing.canBeReplaced()) {
				continue;
			}

			Block block = takeBaseBlock(player);

			if (block == null) {
				ranOut = true;
				break;
			}

			BlockState state = block.defaultBlockState();
			level.setBlockAndUpdate(pos, state);
			placed.add(pos);
			placedState = state;
		}

		placeBeacon(player, level, beaconPos);

		if (placedState != null && !placed.isEmpty()) {
			playPlacementSound(level, placed.getLast(), placedState);
		}

		if (ranOut) {
			player.sendSystemMessage(
					Component.translatable("msg.easy_beacon_placement.out_of_material"), true);
		}
	}

	private static void placeBeacon(ServerPlayer player, ServerLevel level, BlockPos beaconPos) {
		if (!canBuildAt(player, level, beaconPos) || !level.getBlockState(beaconPos).canBeReplaced()) {
			return;
		}

		if (!player.isCreative() && !takeItem(player.getInventory(), stack -> stack.is(Items.BEACON))) {
			return;
		}

		BlockState state = Blocks.BEACON.defaultBlockState();
		level.setBlockAndUpdate(beaconPos, state);
		playPlacementSound(level, beaconPos, state);
	}

	private static boolean isCloseEnough(ServerPlayer player, BlockPos beaconPos) {
		return player.blockPosition().distSqr(beaconPos) <= (double) MAX_BUILD_DISTANCE * MAX_BUILD_DISTANCE;
	}

	/** World bounds, world border and spawn protection all still apply. */
	private static boolean canBuildAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return level.isInWorldBounds(pos)
				&& level.mayInteract(player, pos)
				&& !level.getServer().isUnderSpawnProtection(level, pos, player);
	}

	/**
	 * Consumes one beacon base block from the player's inventory and returns which block it was,
	 * or {@code null} if they have none left. Creative players are not charged.
	 */
	private static Block takeBaseBlock(ServerPlayer player) {
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			Block block = BeaconMaterials.baseBlockOf(stack);

			if (block == null) {
				continue;
			}

			if (!player.isCreative()) {
				stack.shrink(1);
			}

			return block;
		}

		return null;
	}

	private static boolean takeItem(Inventory inventory, java.util.function.Predicate<ItemStack> match) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (match.test(stack)) {
				stack.shrink(1);
				return true;
			}
		}

		return false;
	}

	/**
	 * One sound for the whole build rather than 164 of them.
	 *
	 * <p>NeoForge deprecates the plain {@code getSoundType()} in favour of a position-aware
	 * overload it adds; Fabric has no such overload, so the vanilla call is the only one that
	 * compiles on both. A beacon base block's sound does not vary by position anyway.
	 */
	@SuppressWarnings("deprecation")
	private static void playPlacementSound(ServerLevel level, BlockPos pos, BlockState state) {
		SoundType sound = state.getSoundType();

		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
				(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
	}
}

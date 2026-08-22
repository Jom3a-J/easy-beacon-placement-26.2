package com.jom3a.easybeacon.paper.harness;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A stand-in {@link Player} for the harness.
 *
 * <h2>Why this is a proxy</h2>
 *
 * <p>NeoForge ships {@code FakePlayer}, a real {@code ServerPlayer} subclass, so its harness can
 * drive the server-side builder with something the code under test cannot distinguish from a
 * person. Bukkit has no equivalent, and a plugin cannot reach the server internals needed to
 * fabricate one — the module compiles against {@code paper-api} alone, by design.
 *
 * <p>{@link Player} is an interface, though, so it can be proxied. Only a handful of its methods
 * are ever called by the plugin under test — position, game mode, op status, inventory, and the
 * action-bar message — and those are backed by real state here. Everything else throws, loudly and
 * on purpose: a quiet {@code null} would let the plugin start depending on something the harness
 * never actually exercises, and the failure would look like a mod bug rather than a gap in the
 * test.
 *
 * <p>What stays real is the important half. The world, the blocks, the events and the plugin logic
 * are all genuine; only the player object is synthetic.
 */
final class HarnessPlayer {
	private HarnessPlayer() {
	}

	/** Backing state, so assertions can read the inventory after a build. */
	static final class State {
		final ItemStack[] slots = new ItemStack[41];
		final UUID uuid = UUID.randomUUID();

		World world;
		Location location;
		GameMode gameMode = GameMode.SURVIVAL;
		boolean op;
		String name = "HarnessPlayer";

		/** Total items of one material anywhere in the inventory. */
		int count(Material material) {
			int total = 0;

			for (ItemStack stack : slots) {
				if (stack != null && stack.getType() == material) {
					total += stack.getAmount();
				}
			}

			return total;
		}

		void set(int slot, ItemStack stack) {
			slots[slot] = stack;
		}

		void clear() {
			Arrays.fill(slots, null);
		}
	}

	static Player create(State state) {
		PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
				HarnessPlayer.class.getClassLoader(),
				new Class<?>[] { PlayerInventory.class },
				new InventoryHandler(state));

		return (Player) Proxy.newProxyInstance(
				HarnessPlayer.class.getClassLoader(),
				new Class<?>[] { Player.class },
				new PlayerHandler(state, inventory));
	}

	private record PlayerHandler(State state, PlayerInventory inventory) implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getWorld" -> state.world;
				case "getLocation" -> state.location.clone();
				case "getGameMode" -> state.gameMode;
				case "getInventory" -> inventory;
				case "isOp" -> state.op;
				case "getUniqueId" -> state.uuid;
				case "getName" -> state.name;
				// Feedback the plugin sends the player. Swallowed: there is nobody to read it, and
				// the harness asserts on world and inventory state instead.
				case "sendActionBar", "sendMessage" -> null;
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "toString" -> "HarnessPlayer[" + state.name + "]";
				default -> throw new UnsupportedOperationException(
						"HarnessPlayer does not implement Player." + method.getName()
								+ " - add it here if the plugin now needs it");
			};
		}
	}

	private record InventoryHandler(State state) implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "getSize" -> state.slots.length;
				case "getItem" -> state.slots[(int) args[0]];
				case "setItem" -> {
					state.slots[(int) args[0]] = (ItemStack) args[1];
					yield null;
				}
				case "getItemInMainHand" -> orEmpty(state.slots[0]);
				case "getItemInOffHand" -> orEmpty(state.slots[40]);
				case "addItem" -> addItem((ItemStack[]) args[0]);
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "toString" -> "HarnessInventory";
				default -> throw new UnsupportedOperationException(
						"HarnessPlayer does not implement PlayerInventory." + method.getName()
								+ " - add it here if the plugin now needs it");
			};
		}

		private static ItemStack orEmpty(ItemStack stack) {
			return stack == null ? new ItemStack(Material.AIR) : stack;
		}

		/**
		 * Mirrors {@code Inventory.addItem}: fills the first free slots and hands back whatever did
		 * not fit, keyed by argument index. The plugin relies on that return value to avoid voiding
		 * a refund, so the harness has to model it rather than always succeed.
		 */
		private Object addItem(ItemStack[] items) {
			Map<Integer, ItemStack> leftovers = new HashMap<>();

			for (int i = 0; i < items.length; i++) {
				ItemStack stack = items[i];
				boolean placed = false;

				for (int slot = 0; slot < 36 && !placed; slot++) {
					if (state.slots[slot] == null) {
						state.slots[slot] = stack;
						placed = true;
					}
				}

				if (!placed) {
					leftovers.put(i, stack);
				}
			}

			return leftovers;
		}
	}
}

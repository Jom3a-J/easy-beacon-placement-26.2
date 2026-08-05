package com.jom3a.easybeacon.client;

import com.jom3a.easybeacon.EasyBeaconPlacement;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's key mappings.
 *
 * <p>{@link KeyMapping} itself is vanilla, so the objects live here; only the act of registering
 * them differs per loader, and each platform module does that.
 */
public final class EbpKeybinds {
	/**
	 * Vanilla marks {@code Category.register} deprecated in favour of loader-specific APIs, but
	 * only NeoForge actually has one ({@code RegisterKeyMappingsEvent#registerCategory}); Fabric's
	 * {@code KeyMappingHelper} exposes no equivalent. The vanilla call is the only route that
	 * works on both, and it is what puts the category into the controls screen's sort order.
	 * Registering twice throws, so this stays a single static initialiser.
	 */
	@SuppressWarnings("deprecation")
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(EasyBeaconPlacement.id("main"));

	/** Held down to show the hologram. Right-clicking while it is shown builds the pyramid. */
	public static final KeyMapping SHOW_HOLOGRAM = new KeyMapping(
			"key.easy_beacon_placement.show_hologram",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_LEFT_ALT,
			CATEGORY);

	/** Steps the previewed tier down, for deliberately building a smaller beacon. */
	public static final KeyMapping CYCLE_TIER = new KeyMapping(
			"key.easy_beacon_placement.cycle_tier",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			CATEGORY);

	private EbpKeybinds() {
	}
}

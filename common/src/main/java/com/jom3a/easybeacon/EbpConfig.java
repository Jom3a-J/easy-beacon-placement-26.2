package com.jom3a.easybeacon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Plain JSON config, loaded once at client start from {@code config/easy_beacon_placement.json}.
 *
 * <p>Deliberately hand-rolled with Gson (which ships with the game) rather than pulling in a
 * config library: it is a handful of fields, and a config library would be an extra hard
 * dependency users must install, on a Minecraft version where those libraries are themselves
 * freshly ported.
 */
public final class EbpConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static EbpConfig instance = new EbpConfig();

	// --- placement -------------------------------------------------------------------------

	/**
	 * How many placements may be awaiting the server's confirmation at once.
	 *
	 * <p>This paces the build by outstanding work rather than by a fixed rate, so it runs quickly
	 * against a local server and throttles itself on a laggy one. Keep it small: every placement
	 * is a prediction the server has to acknowledge, and a large backlog of unacknowledged
	 * predictions is what corrupts the structure when any of them are rejected.
	 *
	 * <p>Set to 1 for a strict one-block-at-a-time build.
	 */
	public int maxInFlight = 4;

	/**
	 * Let the server build the structure when it has the mod installed, which removes the
	 * interaction-range limit and the need for every block to have a face to be placed against.
	 *
	 * <p>Turn this off to always place blocks client-side, exactly as the mod behaves against a
	 * vanilla, Paper or Spigot server. Useful if you would rather watch the pyramid go up block by
	 * block, or to reproduce what players without the mod on their server actually experience.
	 */
	public boolean preferServerPlacement = true;

	/** Upper bound on the pyramid tier the preview will size itself to (1-4). */
	public int maxTier = 4;

	/**
	 * Count only hotbar blocks when picking a tier. The placer can only ever use the hotbar, so
	 * turning this on makes the preview strictly match what can actually be built right now.
	 * Off by default, so the preview reflects everything you are carrying.
	 */
	public boolean countHotbarOnly = false;

	// --- display ---------------------------------------------------------------------------

	/**
	 * Draw obstructed slots through solid terrain. On by default: obstructions are nearly always
	 * buried, and being able to see what is in the way is the entire reason they are shown.
	 */
	public boolean obstructionsThroughWalls = true;

	/**
	 * How far ahead to put the beacon when you are aiming at open sky rather than at a block,
	 * in blocks from the eye.
	 */
	public double airPreviewDistance = 4.0D;

	/**
	 * How far the mouse wheel can nudge the beacon up or down from where you are aiming, in
	 * blocks, while the preview key is held.
	 */
	public int maxScrollOffset = 16;

	/** Invert which way the wheel moves the beacon. */
	public boolean invertScroll = false;

	/**
	 * How solid the ghost blocks look, 0-1. Ghosts are tinted white rather than a state colour so
	 * the real block texture stays recognisable; the state is carried by the outline instead.
	 */
	public double ghostOpacity = 0.72D;

	/** Draw a wireframe outline around each previewed block in addition to the filled box. */
	public boolean drawOutlines = true;

	/** Show status text ("Tier 3 - 12 blocks missing") above the hotbar. */
	public boolean showStatusText = true;

	/** Shrink each hologram box slightly so adjacent boxes stay visually separable. */
	public double boxInset = 0.03D;

	// --- colours (0xAARRGGBB) ---------------------------------------------------------------

	/** Free space that will be filled. Used for the outline; the ghost itself is white-tinted. */
	public String colorPlaceable = "#FF33FF66";

	/** A correct block is already here. */
	public String colorAlreadyValid = "#20FFFFFF";

	/**
	 * Something is in the way. Kept fairly transparent because these are drawn through terrain
	 * and stack up behind one another - a whole tier-4 footprint of them would otherwise read as
	 * a solid wall of red.
	 */
	public String colorObstructed = "#78FF2A2A";

	/** Free space, but you have run out of blocks. */
	public String colorMissingMaterial = "#40FFC53D";

	/** The beacon's own position. Used for the outline; the ghost itself is white-tinted. */
	public String colorBeacon = "#FF40C4FF";

	// ---------------------------------------------------------------------------------------

	public static EbpConfig get() {
		return instance;
	}

	/** Loads the config from {@code configDir}, writing defaults if it is absent or broken. */
	public static void load(Path configDir) {
		Path file = configDir.resolve(EasyBeaconPlacement.MOD_ID + ".json");

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				EbpConfig loaded = GSON.fromJson(reader, EbpConfig.class);

				if (loaded != null) {
					instance = loaded;
					instance.clamp();
					return;
				}
			} catch (IOException | JsonSyntaxException e) {
				EasyBeaconPlacement.LOGGER.warn("Could not read {}, falling back to defaults", file, e);
			}
		}

		instance = new EbpConfig();
		save(configDir);
	}

	public static void save(Path configDir) {
		Path file = configDir.resolve(EasyBeaconPlacement.MOD_ID + ".json");

		try {
			Files.createDirectories(configDir);

			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			EasyBeaconPlacement.LOGGER.warn("Could not write {}", file, e);
		}
	}

	private void clamp() {
		// Capped hard: beyond a handful the prediction desync described above sets in.
		maxInFlight = Math.clamp(maxInFlight, 1, 16);
		maxScrollOffset = Math.clamp(maxScrollOffset, 1, 32);
		maxTier = Math.clamp(maxTier, 1, 4);
		boxInset = Math.clamp(boxInset, 0.0D, 0.4D);
		airPreviewDistance = Math.clamp(airPreviewDistance, 1.0D, 16.0D);
		ghostOpacity = Math.clamp(ghostOpacity, 0.05D, 1.0D);
	}

	// --- colour helpers ---------------------------------------------------------------------

	public int placeableColor() {
		return parseColor(colorPlaceable, 0xFF33FF66);
	}

	/**
	 * Tint applied to ghost blocks: white at {@link #ghostOpacity}, so the block's own texture
	 * survives and a previewed iron block still looks like iron.
	 */
	public int ghostTint() {
		int alpha = (int) Math.round(Math.clamp(ghostOpacity, 0.05D, 1.0D) * 255.0D);
		return (alpha << 24) | 0x00FFFFFF;
	}

	public int alreadyValidColor() {
		return parseColor(colorAlreadyValid, 0x20FFFFFF);
	}

	public int obstructedColor() {
		return parseColor(colorObstructed, 0x78FF2A2A);
	}

	public int missingMaterialColor() {
		return parseColor(colorMissingMaterial, 0x40FFC53D);
	}

	public int beaconColor() {
		return parseColor(colorBeacon, 0xFF40C4FF);
	}

	/** Parses {@code #AARRGGBB}, falling back to {@code fallback} on anything malformed. */
	private static int parseColor(String value, int fallback) {
		if (value == null) {
			return fallback;
		}

		String hex = value.startsWith("#") ? value.substring(1) : value;

		try {
			return (int) Long.parseLong(hex, 16);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}

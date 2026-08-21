package com.jom3a.easybeacon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

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

	private static final String COLOR_PLACEABLE = "#FF33FF66";
	private static final String COLOR_ALREADY_VALID = "#20FFFFFF";
	private static final String COLOR_OBSTRUCTED = "#78FF2A2A";
	private static final String COLOR_MISSING_MATERIAL = "#40FFC53D";

	// Written on the loader's thread at start-up, read from the client tick and render threads.
	private static volatile EbpConfig instance = new EbpConfig();

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
	 * Count only the base blocks in your hotbar and offhand when picking a tier, ignoring the rest
	 * of your inventory.
	 *
	 * <p>This is forced on whenever the client is doing the placing, because the placer can only
	 * ever use a hand — so the preview always matches what can really be built. Turning it on by
	 * hand is only worth it when the server has the mod and you would still rather the preview
	 * ignored your backpack.
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

	// --- colours ----------------------------------------------------------------------------
	//
	// Written as #AARRGGBB, or #RRGGBB for a fully opaque colour. Anything else is reported in the
	// log at load and replaced with the default, rather than drawn as an invisible box.

	/** Free space that will be filled. Used for the outline; the ghost itself is white-tinted. */
	public String colorPlaceable = COLOR_PLACEABLE;

	/** A correct block is already here. */
	public String colorAlreadyValid = COLOR_ALREADY_VALID;

	/**
	 * Something is in the way. Kept fairly transparent because these are drawn through terrain
	 * and stack up behind one another - a whole tier-4 footprint of them would otherwise read as
	 * a solid wall of red.
	 */
	public String colorObstructed = COLOR_OBSTRUCTED;

	/** Free space, but you have run out of blocks. */
	public String colorMissingMaterial = COLOR_MISSING_MATERIAL;

	// ---------------------------------------------------------------------------------------

	public static EbpConfig get() {
		return instance;
	}

	/**
	 * Loads the config from {@code configDir}, writing defaults if it is absent or broken.
	 *
	 * <p>A file that loaded cleanly is written straight back out, so a config saved by an older
	 * version picks up any fields added since — and any value that had to be repaired is repaired
	 * on disk too, rather than being silently corrected on every launch.
	 */
	public static void load(Path configDir) {
		Path file = configFile(configDir);

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				EbpConfig loaded = GSON.fromJson(reader, EbpConfig.class);

				if (loaded != null) {
					loaded.clamp();
					instance = loaded;
					save(configDir);
					return;
				}
			} catch (IOException | JsonParseException e) {
				EasyBeaconPlacement.LOGGER.warn("Could not read {}, falling back to defaults", file, e);
			}
		}

		instance = new EbpConfig();
		save(configDir);
	}

	public static void save(Path configDir) {
		Path file = configFile(configDir);

		try {
			Files.createDirectories(configDir);

			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			EasyBeaconPlacement.LOGGER.warn("Could not write {}", file, e);
		}
	}

	private static Path configFile(Path configDir) {
		return configDir.resolve(EasyBeaconPlacement.MOD_ID + ".json");
	}

	private void clamp() {
		// Capped hard: beyond a handful the prediction desync described above sets in.
		maxInFlight = Math.clamp(maxInFlight, 1, 16);
		maxScrollOffset = Math.clamp(maxScrollOffset, 1, 32);
		maxTier = Math.clamp(maxTier, 1, 4);
		boxInset = Math.clamp(boxInset, 0.0D, 0.4D);
		airPreviewDistance = Math.clamp(airPreviewDistance, 1.0D, 16.0D);
		ghostOpacity = Math.clamp(ghostOpacity, 0.05D, 1.0D);

		// Repaired once, at load, rather than at every read: a typo that only ever surfaces as an
		// invisible hologram gives the player nothing to go on.
		colorPlaceable = checkedColor(colorPlaceable, COLOR_PLACEABLE);
		colorAlreadyValid = checkedColor(colorAlreadyValid, COLOR_ALREADY_VALID);
		colorObstructed = checkedColor(colorObstructed, COLOR_OBSTRUCTED);
		colorMissingMaterial = checkedColor(colorMissingMaterial, COLOR_MISSING_MATERIAL);
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

	private static String checkedColor(String value, String fallback) {
		if (tryParseColor(value) >= 0L) {
			return value;
		}

		EasyBeaconPlacement.LOGGER.warn(
				"Config colour \"{}\" is not #RRGGBB or #AARRGGBB; using {} instead", value, fallback);

		return fallback;
	}

	/** Parses {@code #AARRGGBB} or {@code #RRGGBB}, falling back on anything malformed. */
	private static int parseColor(String value, int fallback) {
		long parsed = tryParseColor(value);

		return parsed < 0L ? fallback : (int) parsed;
	}

	/**
	 * The packed colour, or {@code -1} when {@code value} is not a colour at all.
	 *
	 * <p>{@code #RRGGBB} is the form people reach for, and reading six digits as {@code AARRGGBB}
	 * hands them alpha 0 — an invisible hologram that reads as a broken mod rather than as a
	 * missing pair of digits. So six digits means opaque, and any other length is rejected outright
	 * instead of being truncated into something that happens to parse.
	 */
	private static long tryParseColor(String value) {
		if (value == null) {
			return -1L;
		}

		String hex = value.startsWith("#") ? value.substring(1) : value;

		if (hex.length() != 6 && hex.length() != 8) {
			return -1L;
		}

		try {
			long parsed = Integer.parseUnsignedInt(hex, 16) & 0xFFFFFFFFL;

			return hex.length() == 6 ? parsed | 0xFF000000L : parsed;
		} catch (NumberFormatException e) {
			return -1L;
		}
	}
}

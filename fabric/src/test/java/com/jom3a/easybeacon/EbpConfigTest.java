package com.jom3a.easybeacon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Colour parsing, which is worth pinning down because every way it can go wrong goes wrong
 * <em>silently</em>.
 *
 * <p>A misparsed colour does not throw; it produces a number, and that number gets drawn. The
 * original bug here read {@code #RRGGBB} as {@code AARRGGBB}, handing six-digit colours an alpha of
 * zero — so the hologram simply did not appear, which reads as a broken mod rather than as two
 * missing digits.
 */
class EbpConfigTest {
	private static final int DEFAULT_PLACEABLE = 0xFF33FF66;

	@Test
	@DisplayName("#AARRGGBB is taken as written")
	void parsesEightDigitColours() {
		EbpConfig config = new EbpConfig();

		config.colorPlaceable = "#8033FF66";
		assertEquals(0x8033FF66, config.placeableColor(), "explicit alpha");

		config.colorPlaceable = "#0033FF66";
		assertEquals(0x0033FF66, config.placeableColor(), "deliberately transparent stays transparent");

		config.colorPlaceable = "#FFFFFFFF";
		assertEquals(0xFFFFFFFF, config.placeableColor(), "all bits set does not overflow to the fallback");
	}

	@Test
	@DisplayName("#RRGGBB means fully opaque, not invisible")
	void sixDigitColoursBecomeOpaque() {
		EbpConfig config = new EbpConfig();

		config.colorPlaceable = "#33FF66";
		assertEquals(0xFF33FF66, config.placeableColor(), "six digits gain full alpha");

		config.colorPlaceable = "#000000";
		assertEquals(0xFF000000, config.placeableColor(), "black is opaque black, not transparent");
	}

	@Test
	@DisplayName("the leading # is optional")
	void hashIsOptional() {
		EbpConfig config = new EbpConfig();

		config.colorPlaceable = "8033FF66";
		assertEquals(0x8033FF66, config.placeableColor(), "eight digits, no hash");

		config.colorPlaceable = "33FF66";
		assertEquals(0xFF33FF66, config.placeableColor(), "six digits, no hash");
	}

	@Test
	@DisplayName("lower case hex works")
	void hexIsCaseInsensitive() {
		EbpConfig config = new EbpConfig();

		config.colorPlaceable = "#8033ff66";
		assertEquals(0x8033FF66, config.placeableColor());
	}

	@Test
	@DisplayName("anything malformed falls back rather than drawing nonsense")
	void malformedColoursFallBack() {
		EbpConfig config = new EbpConfig();

		for (String bad : new String[] {
			null,
			"",
			"#",
			"nonsense",
			"#GGGGGG",          // right length, not hex
			"#FFF",             // three-digit shorthand is not supported
			"#33FF6",           // five digits
			"#33FF666",         // seven digits
			"#FF33FF666",       // nine digits: previously truncated into something plausible
			"#-33FF6",          // a sign that happens to fit the length check
		}) {
			config.colorPlaceable = bad;
			assertEquals(DEFAULT_PLACEABLE, config.placeableColor(), "fallback for <" + bad + ">");
		}
	}

	@Test
	@DisplayName("each colour falls back to its own default, not a shared one")
	void eachColourHasItsOwnFallback() {
		EbpConfig config = new EbpConfig();

		config.colorPlaceable = "bad";
		config.colorAlreadyValid = "bad";
		config.colorObstructed = "bad";
		config.colorMissingMaterial = "bad";

		assertEquals(0xFF33FF66, config.placeableColor(), "placeable");
		assertEquals(0x20FFFFFF, config.alreadyValidColor(), "already valid");
		assertEquals(0x78FF2A2A, config.obstructedColor(), "obstructed");
		assertEquals(0x40FFC53D, config.missingMaterialColor(), "missing material");
	}

	@Test
	@DisplayName("defaults parse to the values they document")
	void defaultsAreValid() {
		EbpConfig config = new EbpConfig();

		assertEquals(0xFF33FF66, config.placeableColor(), "placeable");
		assertEquals(0x20FFFFFF, config.alreadyValidColor(), "already valid");
		assertEquals(0x78FF2A2A, config.obstructedColor(), "obstructed");
		assertEquals(0x40FFC53D, config.missingMaterialColor(), "missing material");
	}

	@Test
	@DisplayName("ghost tint is white at the configured opacity")
	void ghostTintTracksOpacity() {
		EbpConfig config = new EbpConfig();

		config.ghostOpacity = 1.0D;
		assertEquals(0xFFFFFFFF, config.ghostTint(), "fully opaque");

		config.ghostOpacity = 0.0D;
		assertEquals(0x0DFFFFFF, config.ghostTint(), "clamped up to the 0.05 floor, never invisible");

		config.ghostOpacity = 2.0D;
		assertEquals(0xFFFFFFFF, config.ghostTint(), "clamped down to 1.0");

		// The RGB half stays white whatever the alpha: the block's own texture has to survive, and
		// the slot's meaning is carried by the outline drawn around it instead.
		config.ghostOpacity = 0.5D;
		assertEquals(0x00FFFFFF, config.ghostTint() & 0x00FFFFFF, "rgb stays white");
	}
}

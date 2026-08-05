package com.jom3a.easybeacon;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constants shared by the Fabric and NeoForge modules.
 *
 * <p>Everything in this package is loader-agnostic: it only touches vanilla classes, so both
 * platform modules can compile it directly out of {@code common/src/main/java}.
 */
public final class EasyBeaconPlacement {
	public static final String MOD_ID = "easy_beacon_placement";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private EasyBeaconPlacement() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

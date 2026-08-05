package com.jom3a.easybeacon.beacon;

/** What the mod intends to do with one position in the planned pyramid. */
public enum SlotState {
	/** A valid beacon base block is already here. Nothing to do. */
	ALREADY_VALID,

	/** Free space, and there is material for it. This is what actually gets placed. */
	PLACEABLE,

	/** Something is in the way that is not a valid base block. Drawn in red. */
	OBSTRUCTED,

	/** Free space, but the player has run out of base blocks to fill it with. */
	MISSING_MATERIAL
}

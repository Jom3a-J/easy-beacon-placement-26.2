# Changelog

## 1.0.0

First release. Minecraft 26.2.

### Features

- **Hologram preview.** Hold a beacon and hold the preview key to see the pyramid before you
  build it, drawn as translucent ghosts of the actual block that will be placed.
- **Sized to your inventory.** Picks the largest tier you can complete (9 / 34 / 83 / 164 blocks
  for tiers 1–4). Blocks already correctly placed count towards it, so a half-built pyramid gets
  finished rather than rebuilt.
- **Obstructions shown through terrain.** Anything in the way is red and visible through solid
  blocks, because it is almost always buried and seeing the volume to dig is the whole point.
- **A verdict, not a puzzle.** The beacon's outline is green / amber / red for "will reach this
  tier" / "will only manage a lower one" / "won't work" — a tier-4 footprint is 9×9 and mostly
  off-screen, so you should not have to inspect it by eye.
- **Positioning.** Mouse wheel pushes the beacon along your line of sight; sneak + wheel moves it
  vertically. Works when aiming at open sky, not just at a block.
- **One-click build**, with placement verified against the world and retried rather than fired
  and forgotten.
- **Modded base blocks** work automatically via the `#minecraft:beacon_base_blocks` tag.

### Platforms

- Fabric and NeoForge, client and server. The Fabric jar also loads under Quilt.
- Paper/Spigot server plugin, so the reach limit can be lifted there too.
- Installing server-side is optional everywhere: without it the mod still works client-side
  against vanilla, Paper and Spigot, just limited by interaction range.

### Notes

- The client half is what you need; server-side installs only remove the reach limit.
- Server-side placement grants nothing a player could not do by hand — beacon still required in
  hand, blocks still consumed, world border, world height and spawn protection all enforced, and
  on Paper every block is offered as a `BlockPlaceEvent` so protection plugins can veto it.

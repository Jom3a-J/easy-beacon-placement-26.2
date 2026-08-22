# Changelog

## Unreleased

### Added

- **Every loader can now be tested without a person at the keyboard.** Fabric already had client
  game tests; NeoForge and Paper had nothing, so their own wiring was only ever compiled — and the
  block-place event both offer to land-claim mods had never once fired.
  - `./gradlew :neoforge:runHarness` boots a headless dedicated server and drives the real builder
    with NeoForge's `FakePlayer`, which extends `ServerPlayer`, so the code under test cannot tell
    the difference. 16 checks.
  - `./gradlew :paper:runHarness` downloads a Paper server, verifies its published checksum, and
    runs a harness plugin against it. Bukkit has no `FakePlayer`, so the player is a proxy backed
    by real state — the world, blocks, events and plugin logic all stay real. 18 checks.
- Both prove the veto path the docs promise: a refused build leaves nothing behind and refunds
  every block, and refusing a single position loses only that one — the per-block behaviour chosen
  over an all-or-nothing multi-place event, now demonstrated rather than argued.
- Both run in CI, and both were verified by mutation: deleting the refund fails exactly the refund
  checks and nothing else.

## 1.1.0

Minecraft 26.2. Fixes, a large performance pass, protection-mod support on NeoForge, and a
unit-test suite for the parts that had none.

### Fixed

- **A beacon held in your offhand could never be placed.** The preview accepted a beacon in either
  hand, but the client-side placer only ever looked at the hotbar — so parking the beacon in the
  offhand and filling the hotbar with base blocks, which is the obvious way to carry both, built
  the entire pyramid and then stalled out with "couldn't be placed" where the beacon should go.
  Either hand now works, for base blocks as well as the beacon.
- **Builds ran out of material halfway through.** By default the preview sized itself to every base
  block you were carrying, but the client-side placer can only use what is in a hand. A pyramid
  sized to your backpack therefore aborted partway with "out of base blocks". The preview now sizes
  itself to what the path it will actually take can reach: the whole inventory when the server has
  the mod and is doing the building, the hotbar and offhand when the client is.
- **Paper: spawn protection was not enforced**, despite the mod, the plugin description and this
  changelog all saying it was. A player could build inside spawn protection through the plugin
  where they could not by hand. It now applies the same test vanilla does.
- **Paper: blocks vanished when a refund did not fit.** If a protection plugin vetoed a placement
  and the player's inventory was full, the refunded block was silently dropped on the floor of
  `addItem` — and the inventory is at its fullest precisely when a build has just failed. Leftovers
  are now dropped at the player's feet.
- **`#RRGGBB` config colours came out invisible.** Six-digit colours were read as `AARRGGBB`, giving
  them alpha 0. Six digits now means fully opaque, over-long values are rejected instead of being
  truncated into something that happens to parse, and a malformed colour is reported in the log at
  load rather than silently drawn as nothing.
- Server-side builds no longer load — or generate — a chunk as a side effect of a request whose
  edge falls outside the loaded area.
- **Client-side builds gave up while you were still walking over to them.** The build ended after
  three seconds without a block landing — but a tier-4 base is nine blocks across, and crossing it
  to reach the far side takes longer than that, so walking to the rest of your own pyramid could
  end the build. Moving now counts as progress and only a player who has actually stopped is given
  up on, with a twenty-second ceiling so a build cannot follow someone who has wandered off.
- The claim that a beacon's tier is capped by the first incomplete layer *counting up from the
  bottom* had it backwards, and had been copied into four places: `PlacementPlan`, the game test,
  the README and the Modrinth page copy — the last two player-facing. Layers are numbered
  downwards from the beacon, so the blocked layer **nearest the beacon** caps the result: losing the
  bottom layer of a tier-4 pyramid still leaves a working tier 3, while losing the 3x3 directly
  underneath leaves nothing at all. The code was always right and is unchanged; the comment was
  wrong in the direction that invites someone to "fix" working code, and it is what sent four of
  these new tests down the wrong path before they were corrected against real in-game behaviour.
  The player-facing copies now say a beacon counts only the complete layers directly beneath it.

### Changed

- The config file is rewritten after a successful load, so a file saved by an older version picks
  up fields added since instead of leaving them to be discovered from this changelog.
- `colorBeacon` is gone. It never did anything: the beacon's outline carries the green/amber/red
  verdict, which has been true since the first release. Leaving it in the file only invited people
  to set a value and wonder why nothing changed.
- "Out of base blocks" now says which blocks it means — the two placement paths can run out for
  quite different reasons, and only one of them means you are actually out.

### Added

- **NeoForge server-side placement now fires `BlockEvent.EntityPlaceEvent` per block**, so
  land-claim and protection mods can veto individual positions exactly as they can a hand-placed
  block — and as they already could through the Paper plugin. Refused blocks are rolled back and
  refunded. Fired per block rather than as one `EntityMultiPlaceEvent` on purpose: a pyramid that
  clips the corner of someone's claim loses that corner and keeps the rest, instead of the whole
  build being silently refused. Fabric keeps direct placement — Fabric API has no block-place event
  to fire, so there is nothing standard to offer a placement to.
- A proper README: what it does, controls, install (and what installing server-side actually buys
  you), the full config reference, and how to build and test the thing.

### Internal

- The screenshot-capture test was still using the pre-26.2 game rule names (`doDaylightCycle`,
  `doWeatherCycle`, `doMobSpawning`, now `advance_time`, `advance_weather` and `spawn_mobs`). They
  failed as unknown arguments, and `/gamerule` reports that to chat rather than to the test, so the
  arena had quietly kept its day cycle and its mob spawning the whole time.
- **Unit tests**, in a new `src/test` source set on the fabric and paper modules. They cover the
  pure arithmetic the client game tests can only reach slowly and indirectly, and run in about a
  second with no game client: pyramid geometry, colour parsing, and the packed-position wire format
  the Paper plugin decodes by hand. That last one is the case worth having — get a shift wrong and
  the plugin silently builds somewhere else entirely, and nothing else in the project would notice,
  so the expected values are golden values taken from vanilla's real `BlockPos.asLong()` rather
  than re-derived from the same shifts under test.
- One test pins the ordering `PlacementPlan.compute` depends on: it scans the largest pyramid once
  and treats a smaller tier as the tail of that scan, which only holds while positions come out
  widest layer first. Reordering them would otherwise build the wrong layers, silently.
- **`PlacementPlan.compute` is now testable**, and tested. It takes the terrain question as a
  `TerrainReader` rather than reaching for block tags itself, which is what previously forced any
  test of the planning arithmetic to boot most of the game. Seventeen tests now cover tier
  selection, the material budget, and the effective-tier rules — the cases the client game tests
  cannot cheaply reach, since those run with a creative inventory against terrain that is either
  wholly free or wholly obstructed. Behaviour is unchanged; the live path hands in a reader that
  reads the world.

`build` depends on `check`, so the unit tests run in CI with no workflow change; the client
game tests still need `./gradlew :fabric:runClientGameTest` and a display.

### Performance

Nothing here changes what you see; it changes how much work is done to show it.

- The preview is worked out in **one pass over the terrain instead of up to four.** Choosing a tier
  used to re-read every block of every candidate pyramid, largest first, until one fit — roughly
  300 block lookups per tick at tier 4, plus a fresh list of positions for each attempt. It is now
  a single scan of at most 164, with the tier chosen from per-layer running totals.
- The hologram no longer **re-bakes the block model once per block, every frame.** A tier-4 preview
  was building 165 model-part lists, 165 random sources and 165 vertex-setting objects per frame,
  for two distinct blocks. Those are now built once per frame and shared. Colour parsing moved out
  of the per-block loop as well.
- `effectiveTier()` is computed once when a plan is built rather than on every call — it is read
  once per tick and once per frame, and each call was allocating and walking the whole slot list.
- The block-placement queue no longer searches for a support face twice per block, and picks its
  next target without allocating a vector per candidate.
- Server-side builds no longer restart the inventory search at slot zero for each of up to 164
  blocks, and the Paper plugin looks up its block tags once instead of once per block.

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

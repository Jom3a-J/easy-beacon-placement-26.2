![banner](https://cdn.modrinth.com/data/cached_images/a44b021902af1298e3a46b6c7a0b9785eef8bf82.png)
Beacons are the most tedious block in the game to place. A tier-4 base is **164 blocks** in a
stepped 9×9 pyramid, and you usually only discover something was in the way after you have started
digging.

This mod shows you the whole structure before you commit, then builds it for you.

## Hold a beacon. Hold a key. See it.

The preview is sized automatically to the base blocks you are carrying — it works out the largest
tier you can actually complete, and counts blocks you have already placed towards it, so a
half-finished pyramid gets finished rather than restarted.

Slots that will be filled are shown as a **translucent ghost of the real block**. A previewed iron
block looks like iron.

![sd](https://cdn.modrinth.com/data/cached_images/348f04a82826d6094b11a0b8a77ba0cd98078ca1.png)

## It tells you what is in the way — through the ground

The pyramid goes *below* the beacon, so aiming at flat terrain means digging. Everything blocking
it is drawn **red and visible straight through solid blocks**, so you can see the exact volume you
need to clear without breaking anything first.

![d](https://cdn.modrinth.com/data/cached_images/9cb320de52e56f245cee1cece18953aef4b351cc.png)

## And whether it will actually work

A tier-4 footprint is 9×9 and mostly behind you. You should not have to inspect it block by block,
so the mod gives you a verdict instead:

| | |
|---|---|
| 🟢 **Green** | will reach the tier shown |
| 🟡 **Amber** | will only manage a lower tier |
| 🔴 **Red** | will not work at all |

The status line spells it out — *"Tier 4 blocked — you'll only get Tier 2"* — because a beacon only
counts the complete layers directly beneath it. One gap near the top caps everything, however
perfect the wider layers underneath are.

## Then one click

Blocks are placed for you, **verified against the world, and retried** if the server rejects one,
so you do not end up with holes in the base.

![c](https://cdn.modrinth.com/data/cached_images/d7fe42bfe543fb549d74abe4d9b9859caa859c3c.gif)

![f](https://cdn.modrinth.com/data/cached_images/f1d23f4629658c57e9d01cdeac9e2e24afb47056.png)

## Position it exactly

- **Mouse wheel** — push the preview along your line of sight
- **Sneak + wheel** — move it straight up and down
- Works aiming at open sky, not just at a block

![d](https://cdn.modrinth.com/data/cached_images/107a5ebe89a280675240e00638a1c62a5b292b94.gif)

## Controls

| Action | Default |
|---|---|
| Show the hologram (hold) | `Left Alt` |
| Move nearer / further | Mouse wheel |
| Move up / down | Sneak + wheel |
| Cycle tier / cancel a build | `V` |
| Build it | Right-click while the hologram is shown |

All rebindable. Any block in the `#minecraft:beacon_base_blocks` tag works, so **modded base blocks
are supported automatically**.

## Installing

You only need the **client** file. Installing on a server is optional and does exactly one thing:
removes the reach limit.

| Your setup | File | Also needs |
|---|---|---|
| Fabric / Quilt | `easy_beacon_placement-fabric-*.jar` → `mods/` | **Fabric API** |
| NeoForge | `easy_beacon_placement-neoforge-*.jar` → `mods/` | nothing |
| Paper / Spigot server | `easy_beacon_placement-paper-*.jar` → `plugins/` | nothing |

Mismatched setups are fine in both directions — a client with the mod can join a server without it,
and vice versa. Nothing is registered as required, so nobody gets kicked.

## About reach

With nothing installed server-side, blocks are placed through the same call vanilla makes when you
right-click. The server cannot tell the difference, which is why this works on **vanilla, Paper,
Spigot, Purpur and Folia untouched** — but the server's own reach limit (4.5 blocks) then applies,
so large pyramids need you to walk around. Out-of-reach blocks stay queued and you are told about
them rather than losing them. Only what is in a hand can be placed this way — your hotbar and your
offhand — so the preview sizes itself to those, and shows you the pyramid you can actually finish
from where you are standing.

Install the mod or the Paper plugin server-side and that limit disappears: the server places the
structure directly.

It grants nothing you could not already do by hand. The beacon must still be in your hand, blocks
are still consumed from your inventory, and world border, world height, chunk loading and spawn
protection are all enforced. On Paper and NeoForge every block is offered to the server's own
block-place event, so **WorldGuard, GriefPrevention and similar can veto individual blocks** —
anything refused is refunded. (Fabric API has no block-place event to fire, so there is nothing
standard to offer a placement to there.)

> ⚠️ This is an auto-build mod, the same category as Litematica's printer or building wands. Some
> servers forbid client-side automation regardless of how ordinary the packets look. Check your
> server's rules.

## Configuration

Written to `config/easy_beacon_placement.json` on first launch. Everything is optional — the
defaults are the intended experience. Colours are `#AARRGGBB`, or `#RRGGBB` for fully opaque.

| Option | Default | What it does |
|---|---|---|
| `maxInFlight` | `4` | Placements awaiting server confirmation at once. `1` builds strictly one block at a time. Keep it small — a large backlog of unacknowledged predictions is what leaves holes. |
| `preferServerPlacement` | `true` | Let the server build when it has the mod. Turn off to always place client-side, exactly as players without it experience. |
| `maxTier` | `4` | Upper bound on the previewed tier (1–4). |
| `countHotbarOnly` | `false` | Ignore your backpack when picking a tier. Forced on whenever the client is doing the placing, since it can only use a hand. |
| `obstructionsThroughWalls` | `true` | Draw blockers through solid terrain. |
| `airPreviewDistance` | `4.0` | How far ahead the beacon sits when you aim at open sky. |
| `maxScrollOffset` | `16` | How far the wheel can nudge the beacon. |
| `invertScroll` | `false` | Flip the wheel direction. |
| `ghostOpacity` | `0.72` | How solid the ghost blocks look (0–1). |
| `drawOutlines` | `true` | Wireframe outline around each previewed block. |
| `showStatusText` | `true` | Status line above the hotbar. |
| `boxInset` | `0.03` | Shrink each box so neighbours stay separable. |
| `colorPlaceable` | `#FF33FF66` | Free space that will be filled. |
| `colorAlreadyValid` | `#20FFFFFF` | A correct block is already here. |
| `colorObstructed` | `#78FF2A2A` | Something is in the way. |
| `colorMissingMaterial` | `#40FFC53D` | Free space, but you are out of blocks. |

Out-of-range values are clamped and malformed colours are reported in the log and replaced, both
written back to the file. The file is rewritten on load, so upgrading picks up newly added options
rather than leaving you to find them in the changelog.

## Building from source

Requires **JDK 25**.

```bash
./gradlew build
```

Jars land in `fabric/build/libs/`, `neoforge/build/libs/` and `paper/build/libs/`.

### Testing

The test scaffolding is not in this repository. It is kept alongside the source on the machines
that run it, and covers three levels:

- **Unit tests** over the pure arithmetic — pyramid geometry, the packed-position wire format the
  Paper plugin decodes by hand, and colour parsing. About a second, no game involved.
- **A harness per loader**, each booting a real server and driving the real server-side builder.
  NeoForge's uses its `FakePlayer`; Paper's downloads a Paper server, verifies its published
  SHA-256, and runs a second plugin against it. Between them they cover the block-place event that
  land-claim mods hook, including that a refused build is rolled back and every block refunded.
- **Fabric client game tests**, which drive a real client and a real dedicated server — the only
  level that can prove the hologram renders and that a click really puts blocks in the world.

They run before a release rather than on every push, which is why the build here is just
`./gradlew build`.

### Project layout

`common/src/main/java` is a plain source directory, **not** a Gradle subproject. Both mod loaders
pull it in via `sourceSets.main.java.srcDir`, so each builds the shared code with its own native
toolchain and there is no cross-loader build plugin to break on a new Minecraft version.

This project deliberately does not use Architectury Loom: as of 1.17.491 it still hard-requires
official Mojang mappings, which no longer exist now that 26.x ships unobfuscated.

The Paper plugin shares nothing with the mod. Paper is not a mod loader — it is a fork of the
vanilla server exposing the Bukkit API — so it cannot see Minecraft's own classes. The only thing
the two sides share is the shape of the packet, pinned down in `PyramidGeometry`.

See the [changelog](CHANGELOG.md) for what has changed between versions.

MIT licensed. By **Jom3a**.

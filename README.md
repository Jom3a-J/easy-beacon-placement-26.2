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

The status line spells it out — *"Tier 4 blocked — you'll only get Tier 2"* — because a beacon's
tier is capped by the first incomplete layer counting up from the bottom.

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
them rather than losing them.

Install the mod or the Paper plugin server-side and that limit disappears: the server places the
structure directly.

It grants nothing you could not already do by hand. The beacon must still be in your hand, blocks
are still consumed from your inventory, and world border, world height and spawn protection are all
enforced. On Paper every block is offered as a normal block-place event, so **WorldGuard,
GriefPrevention and similar can veto individual blocks** — anything refused is refunded.

> ⚠️ This is an auto-build mod, the same category as Litematica's printer or building wands. Some
> servers forbid client-side automation regardless of how ordinary the packets look. Check your
> server's rules.

## Links

- Source: TODO — your repository URL
- Issues: TODO — your issue tracker URL

MIT licensed. By **Jom3a**.

# Modrinth page copy

Everything below is ready to paste as it stands. The five image URLs point at the copies already
uploaded to Modrinth’s CDN — the same ones the README renders — so nothing needs filling in
first.

---

## Summary (the one-line field, shown in search)

> Preview a beacon pyramid as a colour-coded hologram, then build the whole thing in one click.

---

## Description (the markdown body)

Copy everything between the rules.

The five images are already linked, each sitting next to the text it illustrates. If you ever
re-upload one, swap its URL here too — see [Replacing an image](#replacing-an-image) below.

---

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

![Ghost blocks of the real material, green when it will work](https://cdn.modrinth.com/data/cached_images/348f04a82826d6094b11a0b8a77ba0cd98078ca1.png)

## It tells you what is in the way — through the ground

The pyramid goes *below* the beacon, so aiming at flat terrain means digging. Everything blocking
it is drawn **red and visible straight through solid blocks**, so you can see the exact volume you
need to clear without breaking anything first.

![Blockers shown through the ground](https://cdn.modrinth.com/data/cached_images/9cb320de52e56f245cee1cece18953aef4b351cc.png)

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

![One click, placed and verified block by block](https://cdn.modrinth.com/data/cached_images/d7fe42bfe543fb549d74abe4d9b9859caa859c3c.gif)

![Finished beacon](https://cdn.modrinth.com/data/cached_images/f1d23f4629658c57e9d01cdeac9e2e24afb47056.png)

## Position it exactly

- **Mouse wheel** — push the preview along your line of sight
- **Sneak + wheel** — move it straight up and down
- Works aiming at open sky, not just at a block

![Mouse wheel positioning](https://cdn.modrinth.com/data/cached_images/107a5ebe89a280675240e00638a1c62a5b292b94.gif)

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
offhand — so the preview sizes itself to those rather than to your whole inventory, and shows you
the pyramid you can actually finish from where you are standing.

Install the mod or the Paper plugin server-side and that limit disappears: the server places the
structure directly.

It grants nothing you could not already do by hand. The beacon must still be in your hand, blocks
are still consumed from your inventory, and world border, world height, chunk loading and spawn
protection are all enforced. On Paper and NeoForge every block is offered to the server's own
block-place event, so **WorldGuard, GriefPrevention and similar can veto individual blocks** —
anything refused is refunded. (Fabric API has no block-place event to fire, so there is nothing
standard to offer it to there.)

> ⚠️ This is an auto-build mod, the same category as Litematica's printer or building wands. Some
> servers forbid client-side automation regardless of how ordinary the packets look. Check your
> server's rules.

## Links

- Source: https://github.com/Jom3a-J/easy-beacon-placement-26.2
- Issues: https://github.com/Jom3a-J/easy-beacon-placement-26.2/issues

MIT licensed. By **Jom3a**.

---

## Page settings

| Field | Value |
|---|---|
| Environments | Client **required**, server **optional** |
| Loaders | Fabric, Quilt, NeoForge, Paper, Spigot |
| Game versions | 26.2 |
| Licence | MIT |
| Categories | Utility, Game Mechanics *(consider Management)* |

Setting the server environment to **optional** matters. Mark it required and people on vanilla or
Paper servers will assume they cannot use it, when in fact the client half works everywhere.

## Screenshots

The five images are already uploaded and linked in the description above, so this section is a
record of what came from where rather than a task list.

They still want uploading to the Modrinth **gallery** as well, which serves a second purpose: the
gallery is its own carousel on the page, separate from the images embedded in the description.

The files in `docs/media/` are named in upload order, so sort the folder by name and work down it.

| Order | File | Gallery caption |
|---|---|---|
| 1 | `docs/media/1-ghost-blocks.png` | Ghost blocks of the real material, green when it will work |
| 2 | `docs/media/2-obstructions.png` | Blockers shown through the ground — the volume you need to dig out |
| 3 | `docs/media/3-build.gif` | One click, placed and verified block by block |
| 4 | `docs/media/4-finished-beacon.png` | Finished tier-1 beacon |
| 5 | `docs/media/5-scroll.gif` | Mouse wheel positioning |

The other files in that folder — `icon.png`, `beacon-beam.png`, `beacon-beam-diagonal.png` — are
unnumbered because they are not gallery images.

Set image **1** as the **featured** image — it shows the ghost blocks and the verdict text,
which is the clearest single frame of what the mod does. Featured is what appears on search
results and in Discord embeds.

### Replacing an image

The description already links all five. To swap one out, upload the replacement, open it in the
gallery, copy its address (a `cdn.modrinth.com/data/...` URL) and replace the matching line:

| Image # | Where it appears |
|---|---|
| 1 | ghost blocks and the verdict |
| 2 | blockers seen through the ground |
| 3 | the one-click build |
| 4 | the finished beacon |
| 5 | wheel positioning |

The order here matches the order they appear in the description above.

Two things to know about the GIFs:

- **The URL may not end in `.gif`.** Modrinth re-encodes some gallery uploads, so a GIF can come
  back as `.webp`. Animation survives either way — just use whatever URL you are given rather
  than editing the extension back.
- Preview the description before publishing. A GIF that does not animate almost always means the
  URL points at Modrinth's *thumbnail*, which is a still frame; take the URL from the full-size
  image instead.

If you would rather not depend on Modrinth's CDN, push the repository first and link the
`docs/media/` files through `raw.githubusercontent.com` — the placeholders work the same way.

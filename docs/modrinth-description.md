# Modrinth page copy

Everything below is ready to paste. Nothing here invents a URL: the five image slots are marked
`PASTE_URL_1` … `PASTE_URL_5` and the two link lines are marked `TODO`.

---

## Summary (the one-line field, shown in search)

> Preview a beacon pyramid as a colour-coded hologram, then build the whole thing in one click.

---

## Description (the markdown body)

Copy everything between the rules.

The five `![...](PASTE_URL_...)` lines are the image slots, already sitting next to the text
they illustrate. They will render as broken images until you replace each `PASTE_URL_*` with a
real URL — see [Filling in the image URLs](#filling-in-the-image-urls) below.

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

![Ghost blocks of the real material, green when it will work](PASTE_URL_1)

## It tells you what is in the way — through the ground

The pyramid goes *below* the beacon, so aiming at flat terrain means digging. Everything blocking
it is drawn **red and visible straight through solid blocks**, so you can see the exact volume you
need to clear without breaking anything first.

![Blockers shown through the ground](PASTE_URL_2)

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

![One click, placed and verified block by block](PASTE_URL_3)

![Finished beacon](PASTE_URL_4)

## Position it exactly

- **Mouse wheel** — push the preview along your line of sight
- **Sneak + wheel** — move it straight up and down
- Works aiming at open sky, not just at a block

![Mouse wheel positioning](PASTE_URL_5)

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

Do this **first** — the description above will not render correctly until the URLs exist.

Upload all five to the Modrinth **gallery**. That serves two purposes at once: the gallery is its
own carousel on the page, *and* it hosts the files, giving you the CDN URLs the description needs.
Modrinth has no upload button inside the description editor, so there is no other way to get an
image in there short of hosting it yourself.

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

### Filling in the image URLs

After uploading, open each gallery image, copy its address (a `cdn.modrinth.com/data/...` URL),
and swap it into the matching placeholder:

| Upload # | Placeholder in the description |
|---|---|
| 1 | `PASTE_URL_1` |
| 2 | `PASTE_URL_2` |
| 3 | `PASTE_URL_3` |
| 4 | `PASTE_URL_4` |
| 5 | `PASTE_URL_5` |

The numbers line up, so `PASTE_URL_3` takes the URL of the third image you uploaded.

Two things to know about the GIFs:

- **The URL may not end in `.gif`.** Modrinth re-encodes some gallery uploads, so a GIF can come
  back as `.webp`. Animation survives either way — just use whatever URL you are given rather
  than editing the extension back.
- Preview the description before publishing. A GIF that does not animate almost always means the
  URL points at Modrinth's *thumbnail*, which is a still frame; take the URL from the full-size
  image instead.

If you would rather not depend on Modrinth's CDN, push the repository first and link the
`docs/media/` files through `raw.githubusercontent.com` — the placeholders work the same way.

# PocketMap
A minimap without a client mod. For Paper 1.21+ and 26.x.

Every player gets their own vanilla map, kept centred on them and placed in the offhand — where
the client draws it as a HUD all by itself. No packets, no NMS, no external dependency: one jar
in `plugins/`, and `/pocketmap` turns it on.

- **Nothing to install for players.** It is a normal vanilla map, so it works on the plain
  client, on Bedrock via Geyser, and on any launcher.
- **No dependencies.** Not PacketEvents, not ProtocolLib, not a database library. One jar.
- **Your offhand item is safe.** Whatever was in that slot is stored with all its data and handed
  back when the minimap goes off, on quit, on death, and when the plugin is disabled. The store
  survives a restart and is never overwritten.
- **It refuses to cost you a life.** A Totem of Undying in the offhand does not save you while
  something else occupies the slot, so PocketMap will not turn on over a totem. That is a
  configurable refusal, not a silent one.
- **One map id per player, reused forever.** Creating a map burns a permanent id in the world
  data; a plugin that made a new one every session would fill your world with dead map ids.
- **The map item cannot be dropped, moved or swapped** into the main hand while it is in use.

Commands: `/pocketmap [on|off|status|reload]` (alias `/pmap`) · Permissions: `pocketmap.use`
(everyone), `pocketmap.admin` (reload, op)

## Honest limits
The offhand is a real slot, and PocketMap takes it. If your players need the offhand for shields
or totems, this is the wrong plugin for them — turn it off with `/pocketmap off` and the slot
comes straight back. The map is a vanilla map, so it shows terrain, not players or mobs; a
packet-based minimap can draw things this one cannot.

## Tested on
Paper 1.21.11 and Paper 26.2 — runtime-tested, not just "it loads". 29 assertions run against the
**shipped** `dev.pocketmap.Hud` through the plugin's own classloader, so the test cannot drift
from a copy: the follow-the-player threshold on both axes and across zero, telling our map apart
from a player's own map (including after a round trip through a chest), the config fallbacks, and
every path that could lose a stored offhand item — corrupt bytes, empty bytes, null. 29/29 green
on both versions, plus 19 further assertions on the map API itself. Loaded together with the
other 13 plugins of the portfolio on both servers, 0 errors.

## Niche
The established plugin for this (VanillaMinimaps, ~60,000 downloads) shipped its last version in
July 2024 and **no longer starts on either current Minecraft version** — it crashes on
`org.bukkit.craftbukkit.v1_21_R1.CraftServer` on 1.21.11 and on `net.minecraft.world.level.World`
on 26.2. It died of exactly the NMS coupling this plugin does not have. There is one active
alternative; it works, but needs a mandatory dependency hand-downloaded from GitHub.
Numbers and evidence: [LISTING.md](LISTING.md).

## Install
Drop the jar in `plugins/`, start, `/pocketmap`. Defaults: closest zoom, re-centre every 8 blocks,
player marker on, offhand item protected.

## AI disclosure
Human-led development: Arvid Grün directs the work, sets the requirements, tests and approves
every release. Code and documentation are written with AI assistance (Claude). No AI-generated
images are used; the icon is drawn geometrically in code.

## License
MIT — see [LICENSE](LICENSE).

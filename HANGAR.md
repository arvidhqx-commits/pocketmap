# PocketMap

**A minimap without a client mod. One jar, no dependencies.**

---

## The problem

Minimaps are one of the most wanted things in Minecraft — Xaero's Minimap alone has over
105 million downloads. But every one of those is a **client mod**. If your server takes players
on vanilla launchers, on Bedrock through Geyser, or simply does not want to tell people what to
install, none of that reaches them.

The plugin that solved this properly, VanillaMinimaps, has about 60,000 downloads and has not
shipped a version since July 2024. It does not start any more. On Paper 1.21.11 it dies on
`org.bukkit.craftbukkit.v1_21_R1.CraftServer`; on Paper 26.2 it dies on
`net.minecraft.world.level.World`. It was built on NMS, and NMS moved.

## What PocketMap does

Every player gets one map of their own. It stays centred on them, and it sits in the offhand —
where the client already draws a held map as a HUD, all by itself. That is vanilla behaviour, not
a trick.

Which means the whole plugin is three ordinary Bukkit calls: create a map, move its centre, put
it in a slot. No packets. No NMS. No version string is ever parsed. There is nothing in here for
a Minecraft update to break — which is the entire point.

## What it costs, said plainly

The offhand is a real slot and PocketMap takes it. A Totem of Undying in that slot will not save
you while a map is there, so PocketMap **refuses to turn on over a totem** rather than quietly
costing you a life. And because it is a vanilla map, it draws terrain — not players, not mobs.
A packet-based minimap can show you things this one cannot.

If your players need their offhand, `/pocketmap off` gives it straight back.

## Your offhand item is not a casualty

Whatever was in the slot is stored with all of its data — enchantments, custom name, stack size —
and handed back when the minimap goes off, when the player quits, when they die, and when the
plugin is disabled. The store is written to the player's persistent data, so it survives a
server restart, and it is never overwritten. There is deliberately no setting to keep the item.

On death the map is taken out of the drops and the stored item is dropped in its place, exactly
as it would have been without the plugin.

## One map id per player

Creating a map burns a permanent id into your world data. A plugin that made a new one per
session would fill the world with dead ids until the counter stopped meaning anything. PocketMap
creates one id per player, remembers it on the player, and reuses it forever — across worlds too,
because the same map can be pointed at a different world.

## Commands and permissions

`/pocketmap` — toggle · `/pocketmap on` · `/pocketmap off` · `/pocketmap status` ·
`/pocketmap reload`
Alias `/pmap`. Permission `pocketmap.use` (everyone), `pocketmap.admin` (op) for the reload.
Worlds can be excluded with `disabled-worlds`; the minimap comes back on its own when the player
leaves them.

## Compatibility

Paper 1.21.x and 26.x. No dependencies at all — not PacketEvents, not ProtocolLib, not a database
library. Runtime-tested on Paper 1.21.11 and 26.2 with 29 assertions against the shipped code and
19 more against the map API itself, green on both, and loaded alongside 13 other plugins with no
errors.

## AI disclosure

Human-led development: Arvid Grün directs the work, sets the requirements, tests and approves each
release. Code and documentation are written with AI assistance. No AI-generated images.

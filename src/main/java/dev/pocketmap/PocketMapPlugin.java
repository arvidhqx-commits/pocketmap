package dev.pocketmap;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PocketMapPlugin extends JavaPlugin implements Listener {

    /** One live minimap. The map id is the durable part; this is only the cache. */
    private static final class Session {
        final MapView view;
        int centerX, centerZ;
        Session(MapView view, int centerX, int centerZ) {
            this.view = view; this.centerX = centerX; this.centerZ = centerZ;
        }
    }

    private NamespacedKey keyMark;   // marks the HUD item itself
    private NamespacedKey keyMapId;  // the player's own map id — reused forever, never leaked
    private NamespacedKey keyStash;  // what was in the offhand before
    private NamespacedKey keyOn;     // the player's wish, kept across restarts

    private final Map<UUID, Session> live = new HashMap<>();
    private final Set<UUID> reenableOnRespawn = new HashSet<>();

    private MapView.Scale scale;
    private boolean marker, unlimited, forceSend, refuseOverTotem, protectItem;
    private boolean handleDeath, reenableAfterRespawn, remember;
    private int intervalTicks, recenterDistance;
    private Set<String> disabledWorlds = new HashSet<>();
    private int taskId = -1;

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyMark = new NamespacedKey(this, "hud");
        keyMapId = new NamespacedKey(this, "mapid");
        keyStash = new NamespacedKey(this, "stash");
        keyOn = new NamespacedKey(this, "on");
        applyConfig();
        getServer().getPluginManager().registerEvents(this, this);
        // Players are already online after /reload.
        for (Player p : getServer().getOnlinePlayers()) restoreOnJoin(p);
        getLogger().info("PocketMap " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // The offhand belongs to the player: hand everything back before we go.
        for (UUID id : new ArrayList<>(live.keySet())) {
            Player p = getServer().getPlayer(id);
            if (p != null) takeDown(p, true);
        }
        live.clear();
        stopTask();
    }

    private void applyConfig() {
        reloadConfig();
        scale = Hud.scaleOf(getConfig().getString("map.scale"), MapView.Scale.CLOSEST);
        marker = getConfig().getBoolean("map.show-player-marker", true);
        unlimited = getConfig().getBoolean("map.unlimited-tracking", false);
        intervalTicks = Math.max(1, getConfig().getInt("update.interval-ticks", 10));
        recenterDistance = Math.max(1, getConfig().getInt("update.recenter-distance", 8));
        forceSend = getConfig().getBoolean("update.force-send", true);
        refuseOverTotem = getConfig().getBoolean("offhand.refuse-over-totem", true);
        protectItem = getConfig().getBoolean("offhand.protect-item", true);
        handleDeath = getConfig().getBoolean("offhand.handle-death", true);
        reenableAfterRespawn = getConfig().getBoolean("offhand.reenable-after-respawn", true);
        remember = getConfig().getBoolean("remember", true);
        disabledWorlds = new HashSet<>();
        for (String w : getConfig().getStringList("disabled-worlds")) {
            disabledWorlds.add(w.toLowerCase(Locale.ROOT));
        }
        for (Session s : live.values()) configure(s.view);
        startTask();
    }

    private void startTask() {
        stopTask();
        taskId = getServer().getScheduler().runTaskTimer(this, this::follow, intervalTicks, intervalTicks)
                .getTaskId();
    }

    private void stopTask() {
        if (taskId != -1) { getServer().getScheduler().cancelTask(taskId); taskId = -1; }
    }

    // ------------------------------------------------------------------ the map

    private void configure(MapView view) {
        view.setScale(scale);
        view.setTrackingPosition(marker);
        view.setUnlimitedTracking(unlimited);
        view.setLocked(false); // a locked map never redraws — it would freeze mid-walk
    }

    /**
     * One map id per player, forever. createMap() burns a permanent id in the
     * world data, so making a new one per session would leak ids until the map
     * counter is meaningless.
     */
    private MapView mapFor(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Integer stored = pdc.get(keyMapId, PersistentDataType.INTEGER);
        MapView view = stored == null ? null : getServer().getMap(stored);
        if (view == null) {
            view = getServer().createMap(p.getWorld());
            pdc.set(keyMapId, PersistentDataType.INTEGER, view.getId());
        }
        if (!p.getWorld().equals(view.getWorld())) view.setWorld(p.getWorld());
        configure(view);
        return view;
    }

    private void follow() {
        if (live.isEmpty()) return;
        for (Map.Entry<UUID, Session> e : new ArrayList<>(live.entrySet())) {
            Player p = getServer().getPlayer(e.getKey());
            if (p == null || !p.isOnline()) { live.remove(e.getKey()); continue; }
            Session s = e.getValue();
            Location loc = p.getLocation();
            boolean worldChanged = !p.getWorld().equals(s.view.getWorld());
            if (worldChanged) s.view.setWorld(p.getWorld());
            int px = loc.getBlockX(), pz = loc.getBlockZ();
            if (worldChanged || Hud.needsRecenter(s.centerX, s.centerZ, px, pz, recenterDistance)) {
                s.view.setCenterX(px);
                s.view.setCenterZ(pz);
                s.centerX = px;
                s.centerZ = pz;
                if (forceSend) p.sendMap(s.view);
            }
        }
    }

    // ----------------------------------------------------------- on / off / back

    private boolean isOn(Player p) {
        return live.containsKey(p.getUniqueId());
    }

    private boolean wantsOn(Player p) {
        return p.getPersistentDataContainer().has(keyOn, PersistentDataType.BYTE);
    }

    /** @return null on success, otherwise the config key of the refusal message. */
    private String putUp(Player p, boolean recordWish) {
        if (disabledWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return "world-disabled";
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (refuseOverTotem && off.getType() == Material.TOTEM_OF_UNDYING) {
            return "totem-blocked";
        }
        MapView view = mapFor(p);
        Location loc = p.getLocation();
        view.setCenterX(loc.getBlockX());
        view.setCenterZ(loc.getBlockZ());

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        // A leftover store from an earlier session must go back FIRST. Writing over
        // it would delete a player's item without a trace — the worst bug this
        // plugin could have.
        if (pdc.has(keyStash, PersistentDataType.BYTE_ARRAY)) {
            returnStash(p);
            off = p.getInventory().getItemInOffHand();
        }
        // Store whatever was there — but never store our own map on top of itself.
        if (Hud.isRealItem(off) && !Hud.isPocketMap(off, keyMark)) {
            byte[] blob = Hud.encodeStash(off);
            if (blob != null) pdc.set(keyStash, PersistentDataType.BYTE_ARRAY, blob);
        }
        p.getInventory().setItemInOffHand(
                Hud.buildHudItem(view, keyMark, getConfig().getString("map.item-name", "")));
        if (recordWish && remember) pdc.set(keyOn, PersistentDataType.BYTE, (byte) 1);
        live.put(p.getUniqueId(), new Session(view, loc.getBlockX(), loc.getBlockZ()));
        p.sendMap(view);
        return null;
    }

    /**
     * Removes the HUD item and hands the stashed item back.
     * {@code forgetWish} separates "the player turned it off" from "the player
     * logged out with it on" — the second must come back on next login.
     */
    private void takeDown(Player p, boolean keepWish) {
        live.remove(p.getUniqueId());
        // Sweep the whole inventory: with protect-item off the map can have moved.
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (Hud.isPocketMap(contents[i], keyMark)) p.getInventory().setItem(i, null);
        }
        if (Hud.isPocketMap(p.getInventory().getItemInOffHand(), keyMark)) {
            p.getInventory().setItemInOffHand(null);
        }
        if (!keepWish) p.getPersistentDataContainer().remove(keyOn);
        returnStash(p);
    }

    private void returnStash(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        byte[] blob = pdc.get(keyStash, PersistentDataType.BYTE_ARRAY);
        if (blob == null) return;
        pdc.remove(keyStash);
        ItemStack back = Hud.decodeStash(blob);
        if (back == null) return;
        if (!Hud.isRealItem(p.getInventory().getItemInOffHand())) {
            p.getInventory().setItemInOffHand(back);
            send(p, "item-returned");
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(back);
        if (left.isEmpty()) {
            send(p, "item-returned");
        } else {
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            send(p, "item-dropped");
        }
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        restoreOnJoin(e.getPlayer());
    }

    private void restoreOnJoin(Player p) {
        // A leftover HUD map from a crash is worthless (its state is gone) — drop it.
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (Hud.isPocketMap(contents[i], keyMark)) p.getInventory().setItem(i, null);
        }
        if (remember && wantsOn(p) && p.hasPermission("pocketmap.use")) {
            String refusal = putUp(p, false);
            if (refusal != null) p.getPersistentDataContainer().remove(keyOn);
        } else {
            // Not coming back on: the stash would otherwise sit there forever.
            returnStash(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isOn(p)) takeDown(p, true); // keep the wish, restore the item
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean blocked = disabledWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
        if (isOn(p)) {
            if (blocked) {
                takeDown(p, true); // the wish survives
                send(p, "world-disabled");
            }
            return;
        }
        // ... and it has to come BACK when the player leaves that world again.
        if (!blocked && remember && wantsOn(p) && p.hasPermission("pocketmap.use")) putUp(p, false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!handleDeath || !isOn(p)) return;
        live.remove(p.getUniqueId());
        if (e.getKeepInventory()) {
            // Everything stays where it is — including our map. Put the session back
            // on the next tick, once the respawn has settled.
            if (reenableAfterRespawn) reenableOnRespawn.add(p.getUniqueId());
            return;
        }
        e.getDrops().removeIf(item -> Hud.isPocketMap(item, keyMark));
        // The stashed item would have dropped with the rest, so it does.
        byte[] blob = p.getPersistentDataContainer().get(keyStash, PersistentDataType.BYTE_ARRAY);
        if (blob != null) {
            p.getPersistentDataContainer().remove(keyStash);
            ItemStack back = Hud.decodeStash(blob);
            if (back != null) e.getDrops().add(back);
        }
        if (reenableAfterRespawn) reenableOnRespawn.add(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!reenableOnRespawn.remove(p.getUniqueId())) return;
        getServer().getScheduler().runTask(this, () -> {
            if (p.isOnline() && !isOn(p) && p.hasPermission("pocketmap.use")) putUp(p, false);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (protectItem && Hud.isPocketMap(e.getItemDrop().getItemStack(), keyMark)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!protectItem) return;
        if (Hud.isPocketMap(e.getOffHandItem(), keyMark) || Hud.isPocketMap(e.getMainHandItem(), keyMark)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!protectItem) return;
        if (Hud.isPocketMap(e.getCurrentItem(), keyMark)
                || Hud.isPocketMap(e.getCursor(), keyMark)
                || (e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                    && e.getWhoClicked() instanceof Player who
                    && Hud.isPocketMap(who.getInventory().getItemInOffHand(), keyMark))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (protectItem && Hud.isPocketMap(e.getOldCursor(), keyMark)) e.setCancelled(true);
    }

    // -------------------------------------------------------------------- command

    private void send(CommandSender to, String key, String... pairs) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw == null || raw.isEmpty()) return;
        String prefix = getConfig().getString("messages.prefix", "");
        to.sendMessage(Msg.parse((prefix == null ? "" : prefix) + raw, pairs));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("pocketmap.admin")) { send(sender, "no-permission"); return true; }
            applyConfig();
            send(sender, "reloaded");
            return true;
        }
        if (!(sender instanceof Player p)) { send(sender, "players-only"); return true; }
        if (!p.hasPermission("pocketmap.use")) { send(sender, "no-permission"); return true; }

        switch (sub) {
            case "status" -> {
                if (isOn(p)) {
                    send(p, "status-on",
                            "id", String.valueOf(live.get(p.getUniqueId()).view.getId()),
                            "scale", scale.name());
                } else {
                    send(p, "status-off");
                }
            }
            case "off" -> {
                if (!isOn(p)) { send(p, "already-off"); return true; }
                takeDown(p, false);
                send(p, "disabled");
            }
            case "on" -> {
                if (isOn(p)) { send(p, "already-on"); return true; }
                String refusal = putUp(p, true);
                send(p, refusal == null ? "enabled" : refusal);
            }
            default -> {
                if (isOn(p)) {
                    takeDown(p, false);
                    send(p, "disabled");
                } else {
                    String refusal = putUp(p, true);
                    send(p, refusal == null ? "enabled" : refusal);
                }
            }
        }
        return true;
    }

    // Used by tools/prober to assert against the shipped code, not a copy.
    public List<String> debugState() {
        List<String> out = new ArrayList<>();
        out.add("live=" + live.size());
        out.add("scale=" + scale);
        out.add("recenter=" + recenterDistance);
        return out;
    }
}

package dev.pocketmap;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

/**
 * The decisions PocketMap makes, kept free of server state so they can be
 * asserted directly (tools/prober loads THIS class out of the shipped jar).
 */
public final class Hud {

    private Hud() {}

    /**
     * Re-centring forces a full 128x128 redraw, so it must not happen every
     * tick. True once the player is at least {@code threshold} blocks off the
     * current map centre, measured per axis (the map is square).
     */
    public static boolean needsRecenter(int centerX, int centerZ, int playerX, int playerZ, int threshold) {
        if (threshold < 1) return true;
        return Math.abs(playerX - centerX) >= threshold || Math.abs(playerZ - centerZ) >= threshold;
    }

    /**
     * Our HUD map carries a mark in its persistent data. Without it we could not
     * tell it apart from a map the player made themselves — and would delete
     * other people's maps while cleaning up.
     */
    public static boolean isPocketMap(ItemStack stack, NamespacedKey mark) {
        if (stack == null || stack.getType() != Material.FILLED_MAP) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }

    /** Builds the marked FILLED_MAP that the client draws as a HUD in the offhand. */
    public static ItemStack buildHudItem(MapView view, NamespacedKey mark, String displayName) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        org.bukkit.inventory.meta.MapMeta meta = (org.bukkit.inventory.meta.MapMeta) stack.getItemMeta();
        meta.setMapView(view);
        meta.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
        if (displayName != null && !displayName.isEmpty()) {
            meta.displayName(Msg.parse(displayName));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** True for anything that actually occupies the offhand slot. */
    public static boolean isRealItem(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0;
    }

    /** Reads a scale name from the config; an unknown name must not disable the plugin. */
    public static MapView.Scale scaleOf(String name, MapView.Scale fallback) {
        if (name == null) return fallback;
        try {
            return MapView.Scale.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** The offhand item goes into the player's persistent data, so it survives a restart. */
    public static byte[] encodeStash(ItemStack stack) {
        return isRealItem(stack) ? stack.serializeAsBytes() : null;
    }

    /** Never throws: a corrupt or outdated blob must not cost the player their login. */
    public static ItemStack decodeStash(byte[] blob) {
        if (blob == null || blob.length == 0) return null;
        try {
            ItemStack out = ItemStack.deserializeBytes(blob);
            return isRealItem(out) ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }
}

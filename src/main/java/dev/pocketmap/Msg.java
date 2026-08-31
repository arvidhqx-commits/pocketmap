package dev.pocketmap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Message rendering. MiniMessage when the text looks like MiniMessage, legacy
 * ampersand codes otherwise, and a legacy fallback when MiniMessage throws —
 * a broken line in the config must never break the plugin.
 */
public final class Msg {

    private Msg() {}

    /** Replaces {key} placeholders, then renders. Pairs are key, value, key, value ... */
    public static Component parse(String raw, String... pairs) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String out = raw;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        if (out.indexOf('<') >= 0 && out.indexOf('>') > out.indexOf('<')) {
            try {
                return MiniMessage.miniMessage().deserialize(out);
            } catch (Exception ignored) {
                // fall through to legacy
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(out);
    }
}

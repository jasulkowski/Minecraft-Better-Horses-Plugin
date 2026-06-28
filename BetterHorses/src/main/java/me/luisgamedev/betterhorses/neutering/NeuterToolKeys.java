package me.luisgamedev.betterhorses.neutering;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;

/**
 * Persistent data keys used to identify and track veterinary shears.
 */
public final class NeuterToolKeys {

    public static final NamespacedKey TOOL_MARKER = key("neuter_tool");
    public static final NamespacedKey TOOL_ID = key("neuter_tool_id");
    public static final NamespacedKey USES_REMAINING = key("neuter_tool_uses");

    private NeuterToolKeys() {
    }

    private static NamespacedKey key(String value) {
        return new NamespacedKey(BetterHorses.getInstance(), value);
    }
}

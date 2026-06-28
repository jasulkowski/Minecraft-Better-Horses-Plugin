package me.luisgamedev.betterhorses.statistics;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;

/**
 * Persistent keys used to distinguish plugin-issued inspection books from
 * ordinary Minecraft books.
 */
public final class HorseStatsBookKeys {

    public static final NamespacedKey BOOK_MARKER = key("horse_stats_book");
    public static final NamespacedKey BOOK_ID = key("horse_stats_book_id");

    private HorseStatsBookKeys() {
    }

    private static NamespacedKey key(String value) {
        return new NamespacedKey(BetterHorses.getInstance(), value);
    }
}

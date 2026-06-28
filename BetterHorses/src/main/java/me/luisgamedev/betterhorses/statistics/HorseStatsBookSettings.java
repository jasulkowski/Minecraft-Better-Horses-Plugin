package me.luisgamedev.betterhorses.statistics;

import me.luisgamedev.betterhorses.BetterHorses;

import java.util.Optional;

/**
 * Immutable configuration snapshot for the physical horse inspection book.
 */
public record HorseStatsBookSettings(
        String displayName,
        double price,
        Optional<Integer> customModelData
) {

    public static HorseStatsBookSettings load(BetterHorses plugin) {
        String basePath = "statistics.book";
        String displayName = plugin.getLang().getRaw("messages.statistics.book-name");
        double price = Math.max(0.0D, plugin.getConfig().getDouble(basePath + ".price", 0.0D));

        Optional<Integer> customModelData = Optional.empty();
        if (plugin.getConfig().getBoolean(basePath + ".custom-model.enabled", false)) {
            customModelData = Optional.of(plugin.getConfig().getInt(basePath + ".custom-model.data", 10002));
        }

        return new HorseStatsBookSettings(displayName, price, customModelData);
    }
}

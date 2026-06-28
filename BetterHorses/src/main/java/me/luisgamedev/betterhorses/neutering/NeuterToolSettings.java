package me.luisgamedev.betterhorses.neutering;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.OptionalInt;

/**
 * Immutable snapshot of the veterinary shears configuration.
 */
public record NeuterToolSettings(
        String displayName,
        double price,
        int uses,
        boolean requireOwner,
        OptionalInt customModelData
) {

    public static NeuterToolSettings load(BetterHorses plugin) {
        FileConfiguration config = plugin.getConfig();
        String path = "neutering.tool.";

        String displayName = plugin.getLang().getRaw("messages.neutering.tool-name");
        double price = Math.max(0.0D, config.getDouble(path + "price", 0.0D));
        int uses = Math.max(1, config.getInt(path + "uses", 1));
        boolean requireOwner = config.getBoolean("neutering.require-owner", true);

        boolean customModelEnabled = config.getBoolean(path + "custom-model.enabled", false);
        int configuredModelData = config.getInt(path + "custom-model.data", 10001);
        OptionalInt customModelData = customModelEnabled && configuredModelData >= 0
                ? OptionalInt.of(configuredModelData)
                : OptionalInt.empty();

        return new NeuterToolSettings(displayName, price, uses, requireOwner, customModelData);
    }
}

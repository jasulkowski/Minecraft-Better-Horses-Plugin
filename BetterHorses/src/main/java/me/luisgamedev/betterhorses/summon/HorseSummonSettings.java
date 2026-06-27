package me.luisgamedev.betterhorses.summon;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of horse summon configuration.
 */
public record HorseSummonSettings(
        boolean enabled,
        boolean requireOwner,
        boolean preventCallWithPlayerPassenger,
        boolean autoMount,
        boolean loadUnloadedChunk,
        int unloadedSearchRadiusChunks,
        int cooldownSeconds,
        int defaultHornUses,
        boolean trackingEnabled,
        int trackingUpdateIntervalSeconds,
        HorseSummonNotificationMode notificationMode,
        int failedUseCooldownTicks,
        Set<String> disabledWorlds,
        double bindDistance,
        double summonDistanceBehind,
        int summonSideSearchRadius,
        Material hornMaterial,
        OptionalInt requiredCustomModelData
) {

    public static HorseSummonSettings load(BetterHorses plugin) {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("horse-summon.horn.material", "GOAT_HORN");
        Material material = Material.matchMaterial(materialName == null ? "GOAT_HORN" : materialName);
        if (material == null || !material.isItem()) {
            material = Material.GOAT_HORN;
        }

        int customModelData = config.getInt("horse-summon.horn.custom-model-data", -1);

        return new HorseSummonSettings(
                config.getBoolean("horse-summon.enabled", true),
                config.getBoolean("horse-summon.require-owner", true),
                config.getBoolean("horse-summon.prevent-call-with-player-passenger", true),
                config.getBoolean("horse-summon.auto-mount", false),
                config.getBoolean("horse-summon.load-unloaded-chunk", true),
                Math.max(0, config.getInt("horse-summon.unloaded-search-radius-chunks", 1)),
                Math.max(0, config.getInt("horse-summon.cooldown-seconds", 10)),
                Math.max(1, config.getInt("horse-summon.default-uses", 5)),
                config.getBoolean("horse-summon.tracking.enabled", true),
                Math.max(5, config.getInt("horse-summon.tracking.update-interval-seconds", 30)),
                HorseSummonNotificationMode.fromConfig(config.getString("horse-summon.notifications.mode", "ACTION_BAR")),
                Math.max(0, config.getInt("horse-summon.notifications.repeat-cooldown-ticks", 40)),
                config.getStringList("horse-summon.disabled-worlds").stream()
                        .map(world -> world.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet()),
                Math.max(1.0D, config.getDouble("horse-summon.bind-distance", 10.0D)),
                Math.max(1.5D, config.getDouble("horse-summon.summon-distance-behind", 3.0D)),
                Math.max(1, config.getInt("horse-summon.summon-side-search-radius", 3)),
                material,
                customModelData >= 0 ? OptionalInt.of(customModelData) : OptionalInt.empty()
        );
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}

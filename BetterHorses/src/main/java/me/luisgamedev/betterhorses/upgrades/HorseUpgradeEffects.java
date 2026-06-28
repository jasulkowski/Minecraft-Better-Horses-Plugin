package me.luisgamedev.betterhorses.upgrades;

import me.luisgamedev.betterhorses.abilities.HorseAbilityStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataContainer;

/** Cheap static reads used by listeners and stat recalculation. */
public final class HorseUpgradeEffects {

    public static final String STABLE_SPEED = "stable_speed";
    public static final String VITALITY = "vitality";
    public static final String JUMP_TRAINING = "jump_training";
    public static final String SURE_FOOTED = "sure_footed";
    public static final String TANDEM_SEAT = "tandem_seat";
    public static final String HORN_MASTERY = "horn_mastery";
    public static final String LOYAL_MOUNT = "loyal_mount";

    private HorseUpgradeEffects() {
    }

    public static int level(PersistentDataContainer data, String upgradeKey) {
        return HorseAbilityStorage.getLevel(data, upgradeKey);
    }

    public static boolean has(PersistentDataContainer data, String upgradeKey) {
        return level(data, upgradeKey) > 0;
    }

    /** Returns the configured total effect of the horse's current level. */
    public static double effect(FileConfiguration config, PersistentDataContainer data, String upgradeKey) {
        int level = level(data, upgradeKey);
        if (level <= 0 || !config.getBoolean("upgrades.enabled", true)
                || !config.getBoolean("upgrades." + upgradeKey + ".enabled", true)) {
            return 0.0D;
        }
        return config.getDouble("upgrades." + upgradeKey + ".levels." + level + ".effect", 0.0D);
    }
}

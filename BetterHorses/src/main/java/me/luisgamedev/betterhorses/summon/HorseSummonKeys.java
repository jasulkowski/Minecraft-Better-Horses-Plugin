package me.luisgamedev.betterhorses.summon;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;

/**
 * Namespaced keys stored on bound summon horns.
 */
public final class HorseSummonKeys {

    public static final NamespacedKey BOUND_HORSE_UUID = key("summon_horse_uuid");
    public static final NamespacedKey BOUND_OWNER_UUID = key("summon_owner_uuid");
    public static final NamespacedKey BOUND_HORSE_NAME = key("summon_horse_name");
    public static final NamespacedKey LAST_WORLD = key("summon_last_world");
    public static final NamespacedKey LAST_X = key("summon_last_x");
    public static final NamespacedKey LAST_Y = key("summon_last_y");
    public static final NamespacedKey LAST_Z = key("summon_last_z");
    public static final NamespacedKey REGISTERED_HORSE = key("summon_registered_horse");

    private HorseSummonKeys() {
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey(BetterHorses.getInstance(), key);
    }
}

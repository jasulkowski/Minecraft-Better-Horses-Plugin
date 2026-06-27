package me.luisgamedev.betterhorses.summon;

import java.util.UUID;

/**
 * Immutable data stored inside a summon horn.
 *
 * <p>The record deliberately contains only plain values so it is safe to pass
 * between the SQLite worker and the Bukkit main thread.</p>
 */
public record BoundHornData(
        UUID horseUuid,
        UUID ownerUuid,
        UUID hornUuid,
        String horseName,
        int usesRemaining,
        UUID lastWorldUuid,
        double lastX,
        double lastY,
        double lastZ
) {
}

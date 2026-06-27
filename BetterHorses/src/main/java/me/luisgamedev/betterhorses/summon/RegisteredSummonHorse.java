package me.luisgamedev.betterhorses.summon;

import java.util.UUID;

/**
 * Persistent database representation of a horse bound to a summon horn.
 */
public record RegisteredSummonHorse(
        UUID horseUuid,
        UUID ownerUuid,
        String horseName,
        UUID worldUuid,
        double x,
        double y,
        double z
) {
}

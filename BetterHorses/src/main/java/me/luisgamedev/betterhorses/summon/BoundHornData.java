package me.luisgamedev.betterhorses.summon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;

/**
 * Data stored inside a summon horn.
 */
public record BoundHornData(
        UUID horseUuid,
        UUID ownerUuid,
        String horseName,
        UUID lastWorldUuid,
        double lastX,
        double lastY,
        double lastZ
) {

    public Optional<Location> lastKnownLocation() {
        World world = Bukkit.getWorld(lastWorldUuid);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, lastX, lastY, lastZ));
    }
}

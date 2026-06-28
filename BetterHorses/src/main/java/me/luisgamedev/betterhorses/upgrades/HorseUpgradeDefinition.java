package me.luisgamedev.betterhorses.upgrades;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record HorseUpgradeDefinition(
        String key,
        String displayName,
        List<String> description,
        boolean enabled,
        int maxLevel,
        Map<Integer, HorseUpgradeLevel> levels
) {
    public HorseUpgradeDefinition {
        description = List.copyOf(description);
        levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
        maxLevel = Math.max(1, maxLevel);
    }

    public Optional<HorseUpgradeLevel> level(int level) {
        return Optional.ofNullable(levels.get(level));
    }
}

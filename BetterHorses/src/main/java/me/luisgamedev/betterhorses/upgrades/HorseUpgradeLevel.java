package me.luisgamedev.betterhorses.upgrades;

import java.util.List;

public record HorseUpgradeLevel(
        int level,
        double money,
        double effect,
        String effectDescription,
        List<UpgradeItemRequirement> items
) {
    public HorseUpgradeLevel {
        level = Math.max(1, level);
        money = Math.max(0.0D, money);
        effectDescription = effectDescription == null ? "" : effectDescription;
        items = List.copyOf(items);
    }
}

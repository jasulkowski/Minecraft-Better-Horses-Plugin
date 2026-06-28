package me.luisgamedev.betterhorses.upgrades;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * One configured item requirement for a horse-upgrade level.
 * A non-negative customModelData value requires an exact match.
 */
public record UpgradeItemRequirement(
        Material material,
        int amount,
        int customModelData,
        String displayName
) {

    public UpgradeItemRequirement {
        amount = Math.max(1, amount);
        displayName = displayName == null ? "" : displayName;
    }

    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != material || item.getAmount() <= 0) {
            return false;
        }
        if (customModelData < 0) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.hasCustomModelData()
                && meta.getCustomModelData() == customModelData;
    }

    public String fallbackDisplayName() {
        if (!displayName.isBlank()) {
            return displayName;
        }
        String normalized = material.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean upperNext = true;
        for (char character : normalized.toCharArray()) {
            if (Character.isWhitespace(character)) {
                result.append(character);
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(character));
                upperNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}

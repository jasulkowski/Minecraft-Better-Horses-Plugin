package me.luisgamedev.betterhorses.upgrades;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Main-thread inventory transaction for upgrade item costs. */
final class HorseUpgradeInventoryCost {

    private HorseUpgradeInventoryCost() {
    }

    static boolean has(Player player, List<UpgradeItemRequirement> requirements) {
        return createPlan(player.getInventory(), requirements) != null;
    }

    static List<ItemStack> consume(Player player, List<UpgradeItemRequirement> requirements) {
        Plan plan = createPlan(player.getInventory(), requirements);
        if (plan == null) {
            return List.of();
        }
        player.getInventory().setStorageContents(plan.storageContents());
        player.getInventory().setItemInOffHand(plan.offHand());
        return plan.removedItems();
    }

    static void refund(Player player, List<ItemStack> removedItems) {
        for (ItemStack removed : removedItems) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(removed);
            leftovers.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover)
            );
        }
    }

    private static Plan createPlan(PlayerInventory inventory, List<UpgradeItemRequirement> requirements) {
        ItemStack[] storage = cloneContents(inventory.getStorageContents());
        ItemStack offHand = cloneOrAir(inventory.getItemInOffHand());
        List<ItemStack> removed = new ArrayList<>();

        List<UpgradeItemRequirement> ordered = requirements.stream()
                .sorted(Comparator.comparingInt(requirement -> requirement.customModelData() >= 0 ? 0 : 1))
                .toList();

        for (UpgradeItemRequirement requirement : ordered) {
            int remaining = requirement.amount();
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                ItemStack item = storage[slot];
                if (!requirement.matches(item)) {
                    continue;
                }
                int taken = Math.min(remaining, item.getAmount());
                removed.add(copyAmount(item, taken));
                item.setAmount(item.getAmount() - taken);
                if (item.getAmount() <= 0) {
                    storage[slot] = new ItemStack(Material.AIR);
                }
                remaining -= taken;
            }

            if (remaining > 0 && requirement.matches(offHand)) {
                int taken = Math.min(remaining, offHand.getAmount());
                removed.add(copyAmount(offHand, taken));
                offHand.setAmount(offHand.getAmount() - taken);
                if (offHand.getAmount() <= 0) {
                    offHand = new ItemStack(Material.AIR);
                }
                remaining -= taken;
            }

            if (remaining > 0) {
                return null;
            }
        }
        return new Plan(storage, offHand, List.copyOf(removed));
    }

    private static ItemStack[] cloneContents(ItemStack[] original) {
        ItemStack[] copy = new ItemStack[original.length];
        for (int index = 0; index < original.length; index++) {
            copy[index] = cloneOrAir(original[index]);
        }
        return copy;
    }

    private static ItemStack cloneOrAir(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    private static ItemStack copyAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    private record Plan(ItemStack[] storageContents, ItemStack offHand, List<ItemStack> removedItems) {
    }
}

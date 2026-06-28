package me.luisgamedev.betterhorses.neutering;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.events.BetterHorseEntityNeuterEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.HorseIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates, sells, validates and consumes the physical veterinary shears item.
 */
public final class NeuterToolService {

    private final BetterHorses plugin;
    private final EconomyProvider economy;

    public NeuterToolService(BetterHorses plugin, EconomyProvider economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    /**
     * Purchases (or freely obtains) one veterinary shears item for the player.
     */
    public boolean giveTool(Player player) {
        NeuterToolSettings settings = NeuterToolSettings.load(plugin);
        LanguageManager lang = plugin.getLang();

        ItemStack tool = createTool(player, settings);

        if (settings.price() > 0.0D) {
            if (!economy.isAvailable()) {
                lang.send(player, "messages.neutering.economy-unavailable");
                return true;
            }
            if (!economy.has(player, settings.price())) {
                lang.sendFormatted(
                        player,
                        "messages.neutering.not-enough-money",
                        "%price%", economy.format(settings.price())
                );
                return true;
            }
            if (!economy.withdraw(player, settings.price())) {
                lang.send(player, "messages.neutering.payment-failed");
                return true;
            }
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(tool);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        lang.sendFormatted(
                player,
                "messages.neutering.tool-received",
                "%price%", economy.format(settings.price()),
                "%uses%", settings.uses()
        );
        return true;
    }

    public ItemStack createTool(OfflinePlayer viewer, NeuterToolSettings settings) {
        ItemStack tool = new ItemStack(Material.SHEARS);
        ItemMeta meta = tool.getItemMeta();

        meta.setDisplayName(plugin.getLang().parseToString(viewer, settings.displayName()));
        settings.customModelData().ifPresent(meta::setCustomModelData);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(NeuterToolKeys.TOOL_MARKER, PersistentDataType.BYTE, (byte) 1);
        data.set(NeuterToolKeys.TOOL_ID, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(NeuterToolKeys.USES_REMAINING, PersistentDataType.INTEGER, settings.uses());

        meta.setLore(buildLore(viewer, settings.uses(), settings.price()));
        tool.setItemMeta(meta);
        return tool;
    }

    public boolean isNeuterTool(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(NeuterToolKeys.TOOL_MARKER, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /**
     * Applies neutering to one specific horse and consumes one tool use only on success.
     */
    public void neuter(Player player, Horse horse, EquipmentSlot hand, ItemStack tool) {
        LanguageManager lang = plugin.getLang();
        NeuterToolSettings settings = NeuterToolSettings.load(plugin);

        if (!player.hasPermission("betterhorses.neuter")) {
            lang.sendFormatted(player, "messages.insufficient-permission", "%command%", "veterinary shears");
            return;
        }

        if (!horse.isTamed()) {
            lang.send(player, "messages.neutering.horse-not-tamed");
            return;
        }

        if (settings.requireOwner() && (horse.getOwner() == null || !horse.getOwner().getUniqueId().equals(player.getUniqueId()))) {
            lang.send(player, "messages.not-horse-owner");
            return;
        }

        if (HorseIdentity.isNeutered(horse.getPersistentDataContainer())) {
            lang.send(player, "messages.already-castrated");
            return;
        }

        BetterHorseEntityNeuterEvent neuterEvent = new BetterHorseEntityNeuterEvent(player, horse, tool);
        Bukkit.getPluginManager().callEvent(neuterEvent);
        if (neuterEvent.isCancelled()) {
            return;
        }

        HorseIdentity.markNeutered(horse.getPersistentDataContainer());
        consumeUse(player, hand, tool, settings.price());

        String horseName = horse.getCustomName();
        if (horseName == null || horseName.isBlank()) {
            horseName = lang.getRaw(player, "messages.horse");
        }
        lang.sendFormatted(player, "messages.successfully-castrated", "%horse%", horseName);
    }

    private void consumeUse(Player player, EquipmentSlot hand, ItemStack expectedTool, double configuredPrice) {
        ItemStack current = player.getInventory().getItem(hand);
        if (!sameTool(current, expectedTool)) {
            return;
        }

        ItemMeta meta = current.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        int remaining = Math.max(0, data.getOrDefault(
                NeuterToolKeys.USES_REMAINING,
                PersistentDataType.INTEGER,
                1
        ) - 1);

        if (remaining <= 0) {
            player.getInventory().setItem(hand, null);
            return;
        }

        data.set(NeuterToolKeys.USES_REMAINING, PersistentDataType.INTEGER, remaining);
        meta.setLore(buildLore(player, remaining, configuredPrice));
        current.setItemMeta(meta);
        player.getInventory().setItem(hand, current);
    }

    private boolean sameTool(ItemStack first, ItemStack second) {
        if (!isNeuterTool(first) || !isNeuterTool(second)) {
            return false;
        }

        String firstId = first.getItemMeta().getPersistentDataContainer()
                .get(NeuterToolKeys.TOOL_ID, PersistentDataType.STRING);
        String secondId = second.getItemMeta().getPersistentDataContainer()
                .get(NeuterToolKeys.TOOL_ID, PersistentDataType.STRING);
        return firstId != null && firstId.equals(secondId);
    }

    private List<String> buildLore(OfflinePlayer viewer, int uses, double price) {
        LanguageManager lang = plugin.getLang();
        List<String> configured = lang.getConfig().getStringList("messages.neutering.tool-lore");
        List<String> result = new ArrayList<>(configured.size());
        String formattedPrice = economy.format(price);

        for (String line : configured) {
            result.add(lang.parseToString(
                    viewer,
                    line.replace("%uses%", Integer.toString(uses))
                            .replace("%price%", formattedPrice)
            ));
        }
        return result;
    }
}

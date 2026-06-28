package me.luisgamedev.betterhorses.statistics;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.abilities.HorseAbilityProfile;
import me.luisgamedev.betterhorses.abilities.HorseAbilityStorage;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.neutering.EconomyProvider;
import me.luisgamedev.betterhorses.utils.HorseIdentity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and sells physical inspection books and opens read-only virtual books
 * containing the same player-facing statistics as a BetterHorses saddle item.
 */
public final class HorseStatsBookService {

    private static final int MAX_BOOK_PAGE_LENGTH = 1024;
    private static final int MAX_BOOK_TITLE_LENGTH = 32;

    private final BetterHorses plugin;
    private final EconomyProvider economy;

    public HorseStatsBookService(BetterHorses plugin, EconomyProvider economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    /**
     * Purchases or freely gives one plugin-issued inspection book.
     */
    public boolean giveBook(Player player) {
        HorseStatsBookSettings settings = HorseStatsBookSettings.load(plugin);
        LanguageManager lang = plugin.getLang();

        if (settings.price() > 0.0D) {
            if (!economy.isAvailable()) {
                lang.send(player, "messages.statistics.economy-unavailable");
                return true;
            }
            if (!economy.has(player, settings.price())) {
                lang.sendFormatted(
                        player,
                        "messages.statistics.not-enough-money",
                        "%price%", economy.format(settings.price())
                );
                return true;
            }
            if (!economy.withdraw(player, settings.price())) {
                lang.send(player, "messages.statistics.payment-failed");
                return true;
            }
        }

        ItemStack book = createInspectionBook(player, settings);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(book);
        leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover)
        );

        lang.sendFormatted(
                player,
                "messages.statistics.book-received",
                "%price%", economy.format(settings.price())
        );
        return true;
    }

    public ItemStack createInspectionBook(Player viewer, HorseStatsBookSettings settings) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getLang().parseToString(viewer, settings.displayName()));
        meta.setEnchantmentGlintOverride(true);
        settings.customModelData().ifPresent(meta::setCustomModelData);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(HorseStatsBookKeys.BOOK_MARKER, PersistentDataType.BYTE, (byte) 1);
        data.set(HorseStatsBookKeys.BOOK_ID, PersistentDataType.STRING, UUID.randomUUID().toString());

        List<String> configuredLore = plugin.getLang().getConfig()
                .getStringList("messages.statistics.book-lore");
        List<String> lore = new ArrayList<>(configuredLore.size());
        String formattedPrice = economy.format(settings.price());
        for (String line : configuredLore) {
            lore.add(plugin.getLang().parseToString(
                    viewer,
                    line.replace("%price%", formattedPrice)
            ));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isInspectionBook(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) {
            return false;
        }

        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(HorseStatsBookKeys.BOOK_MARKER, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /**
     * Opens the statistics of the horse currently ridden by the player.
     */
    public boolean openMountedHorseStats(Player player) {
        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            plugin.getLang().send(player, "messages.statistics.not-riding");
            return true;
        }

        openHorseStats(player, horse);
        return true;
    }

    /**
     * Opens a two-page virtual written book. No item is added to or removed from
     * the player's inventory.
     */
    public void openHorseStats(Player player, AbstractHorse horse) {
        ItemStack previewItem = BetterHorsesAPI.toPreviewItem(horse, player);
        if (previewItem == null || !previewItem.hasItemMeta()) {
            plugin.getLang().send(player, "messages.statistics.invalid-horse");
            return;
        }

        ItemMeta previewMeta = previewItem.getItemMeta();
        String horseName = previewMeta.hasDisplayName()
                ? previewMeta.getDisplayName()
                : plugin.getLang().getRaw(player, "messages.horse");

        List<String> lore = previewMeta.hasLore() && previewMeta.getLore() != null
                ? previewMeta.getLore()
                : List.of();

        String header = plugin.getLang().getFormattedRaw(
                player,
                "messages.statistics.virtual-header",
                "%horse%", escapeMiniMessageTags(horseName)
        );

        StringBuilder page = new StringBuilder(header);

        // Keep this identity-bound status near the top of the first page. Long
        // training lore and an additional trait line could previously push the
        // neuter marker past Minecraft's page-length limit.
        String neuteredLine = null;
        boolean neutered = HorseIdentity.isNeutered(horse.getPersistentDataContainer())
                || HorseIdentity.isNeutered(previewMeta.getPersistentDataContainer());
        if (neutered) {
            neuteredLine = plugin.getLang().parseToString(
                    player,
                    plugin.getLang().getRaw(player, "messages.lore-neutered")
            );
            page.append("\n\n").append(neuteredLine);
        }

        if (!lore.isEmpty()) {
            page.append("\n\n");
            String strippedNeutered = neuteredLine == null
                    ? null
                    : ChatColor.stripColor(neuteredLine);
            boolean wroteLine = false;
            for (String loreLine : lore) {
                String strippedLoreLine = ChatColor.stripColor(loreLine);
                if (strippedNeutered != null && strippedNeutered.equals(strippedLoreLine)) {
                    continue;
                }
                if (wroteLine) {
                    page.append('\n');
                }
                page.append(loreLine);
                wroteLine = true;
            }
        }

        ItemStack virtualBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) virtualBook.getItemMeta();

        String plainHorseName = ChatColor.stripColor(horseName);
        if (plainHorseName == null || plainHorseName.isBlank()) {
            plainHorseName = plugin.getLang().getRaw(player, "messages.horse");
        }

        String title = plugin.getLang().getFormattedRaw(
                player,
                "messages.statistics.virtual-title",
                "%horse%", plainHorseName
        );
        String author = plugin.getLang().getRaw(player, "messages.statistics.virtual-author");

        bookMeta.setTitle(trimPlain(ChatColor.stripColor(title), MAX_BOOK_TITLE_LENGTH));
        bookMeta.setAuthor(trimPlain(ChatColor.stripColor(author), MAX_BOOK_TITLE_LENGTH));
        bookMeta.addPage(trimLegacy(page.toString(), MAX_BOOK_PAGE_LENGTH));
        bookMeta.addPage(trimLegacy(buildAbilitiesPage(player, horse), MAX_BOOK_PAGE_LENGTH));
        virtualBook.setItemMeta(bookMeta);

        player.openBook(virtualBook);
    }

    private String buildAbilitiesPage(Player player, AbstractHorse horse) {
        LanguageManager lang = plugin.getLang();
        HorseAbilityProfile profile = HorseAbilityProfile.from(horse);

        String passive = profile.passiveAbility()
                .map(key -> formatAbilityName(player, key, true))
                .orElseGet(() -> lang.getRaw(player, "messages.statistics.abilities-none"));
        String active = profile.activeAbility()
                .map(key -> formatAbilityName(player, key, true))
                .orElseGet(() -> lang.getRaw(player, "messages.statistics.abilities-none"));
        String other = formatOtherAbilities(player, horse, profile.otherAbilities());

        StringBuilder page = new StringBuilder();
        page.append(lang.getRaw(player, "messages.statistics.abilities-header"));
        page.append("\n\n");
        page.append(lang.getRaw(player, "messages.statistics.passive-label"));
        page.append("\n").append(passive);
        page.append("\n\n");
        page.append(lang.getRaw(player, "messages.statistics.active-label"));
        page.append("\n").append(active);
        if (profile.activeAbility().isPresent()) {
            page.append("\n").append(lang.getRaw(player, "messages.statistics.active-hint"));
        }
        page.append("\n\n");
        page.append(lang.getRaw(player, "messages.statistics.other-label"));
        page.append("\n").append(other);
        return page.toString();
    }

    private String formatOtherAbilities(Player player, AbstractHorse horse, List<String> abilityKeys) {
        if (abilityKeys.isEmpty()) {
            return plugin.getLang().getRaw(player, "messages.statistics.abilities-none");
        }

        List<String> lines = new ArrayList<>(abilityKeys.size());
        for (String abilityKey : abilityKeys) {
            int level = HorseAbilityStorage.getLevel(horse.getPersistentDataContainer(), abilityKey);
            lines.add(plugin.getLang().getFormattedRaw(
                    player,
                    "messages.statistics.other-ability-level",
                    "%ability%", formatAbilityName(player, abilityKey, false),
                    "%level%", Math.max(1, level)
            ));
        }
        return String.join("\n", lines);
    }

    private String formatAbilityName(Player player, String abilityKey, boolean traitAbility) {
        LanguageManager lang = plugin.getLang();
        if (!traitAbility && plugin.getHorseUpgradeService() != null) {
            var configuredUpgrade = plugin.getHorseUpgradeService().registry().find(abilityKey);
            if (configuredUpgrade.isPresent()) {
                return lang.parseToString(player, configuredUpgrade.get().displayName());
            }
        }
        String path = (traitAbility ? "traits." : "abilities.") + abilityKey.toLowerCase();
        if (lang.getConfig().isString(path)) {
            return lang.getRaw(player, path);
        }

        String normalized = abilityKey.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return lang.getRaw(player, "messages.statistics.abilities-none");
        }

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

    private static String escapeMiniMessageTags(String value) {
        return value.replace("<", "\\<");
    }

    private static String trimPlain(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private static String trimLegacy(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }

        int end = maximumLength;
        if (end > 0 && value.charAt(end - 1) == ChatColor.COLOR_CHAR) {
            end--;
        }
        return value.substring(0, Math.max(0, end));
    }
}

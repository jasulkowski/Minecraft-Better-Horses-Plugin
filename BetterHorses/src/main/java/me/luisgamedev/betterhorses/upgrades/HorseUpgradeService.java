package me.luisgamedev.betterhorses.upgrades;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.abilities.HorseAbilityStorage;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.neutering.EconomyProvider;
import me.luisgamedev.betterhorses.training.TrainingManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Purchase, administration and virtual-book display logic for permanent horse upgrades. */
public final class HorseUpgradeService {

    private static final int MAX_BOOK_PAGE_LENGTH = 1024;
    private static final int MAX_BOOK_TITLE_LENGTH = 32;

    private final BetterHorses plugin;
    private final EconomyProvider economy;
    private final HorseUpgradeRegistry registry;

    public HorseUpgradeService(BetterHorses plugin, EconomyProvider economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.registry = new HorseUpgradeRegistry(plugin);
    }

    public HorseUpgradeRegistry registry() {
        return registry;
    }

    public void reload() {
        registry.reload();
        reapplyLoadedHorses();
    }

    /** Opens a read-only virtual book for the horse currently ridden by the player. */
    public boolean showUpgrades(Player player) {
        AbstractHorse horse = requireMountedHorse(player);
        if (horse == null) {
            return true;
        }

        Collection<HorseUpgradeDefinition> definitions = registry.all();
        if (!registry.isEnabled() || definitions.stream().noneMatch(HorseUpgradeDefinition::enabled)) {
            plugin.getLang().send(player, "messages.upgrades.disabled");
            return true;
        }

        ItemStack virtualBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) virtualBook.getItemMeta();
        LanguageManager lang = plugin.getLang();

        String horseName = horse.getCustomName() == null || horse.getCustomName().isBlank()
                ? lang.getRaw(player, "messages.horse")
                : horse.getCustomName();
        String plainHorseName = ChatColor.stripColor(horseName);
        if (plainHorseName == null || plainHorseName.isBlank()) {
            plainHorseName = lang.getRaw(player, "messages.horse");
        }

        String title = lang.getFormattedRaw(
                player,
                "messages.upgrades.book-title",
                "%horse%", plainHorseName
        );
        meta.setTitle(trimPlain(ChatColor.stripColor(title), MAX_BOOK_TITLE_LENGTH));
        meta.setAuthor(trimPlain(
                ChatColor.stripColor(lang.getRaw(player, "messages.upgrades.book-author")),
                MAX_BOOK_TITLE_LENGTH
        ));

        PersistentDataContainer data = horse.getPersistentDataContainer();
        addPaginatedPages(meta, buildOverviewPage(player, horseName, data, definitions));
        for (HorseUpgradeDefinition definition : definitions) {
            if (definition.enabled()) {
                addUpgradePages(meta, player, data, definition);
            }
        }

        virtualBook.setItemMeta(meta);
        player.openBook(virtualBook);
        return true;
    }

    public boolean purchase(Player player, String rawUpgradeKey) {
        if (!registry.isEnabled()) {
            plugin.getLang().send(player, "messages.upgrades.disabled");
            return true;
        }

        AbstractHorse horse = requireMountedHorse(player);
        if (horse == null) {
            return true;
        }
        if (!isOwner(player, horse) && !player.hasPermission("betterhorses.upgrade.admin")) {
            plugin.getLang().send(player, "messages.upgrades.not-owner");
            return true;
        }

        HorseUpgradeDefinition definition = registry.find(rawUpgradeKey).orElse(null);
        if (definition == null || !definition.enabled()) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.upgrades.unknown",
                    "%upgrade%", rawUpgradeKey
            );
            return true;
        }

        PersistentDataContainer data = horse.getPersistentDataContainer();
        int currentLevel = HorseAbilityStorage.getLevel(data, definition.key());
        if (currentLevel >= definition.maxLevel()) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.upgrades.already-max",
                    "%upgrade%", parseDisplayName(player, definition)
            );
            return true;
        }

        int nextLevel = currentLevel + 1;
        HorseUpgradeLevel targetLevel = definition.level(nextLevel).orElse(null);
        if (targetLevel == null) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.upgrades.level-not-configured",
                    "%upgrade%", parseDisplayName(player, definition),
                    "%level%", nextLevel
            );
            return true;
        }

        if (targetLevel.money() > 0.0D) {
            if (!economy.isAvailable()) {
                plugin.getLang().send(player, "messages.upgrades.economy-unavailable");
                return true;
            }
            if (!economy.has(player, targetLevel.money())) {
                plugin.getLang().sendFormatted(
                        player,
                        "messages.upgrades.not-enough-money",
                        "%money%", economy.format(targetLevel.money())
                );
                return true;
            }
        }

        if (!HorseUpgradeInventoryCost.has(player, targetLevel.items())) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.upgrades.missing-items",
                    "%items%", formatItems(player, targetLevel.items())
            );
            return true;
        }

        if (!economy.withdraw(player, targetLevel.money())) {
            plugin.getLang().send(player, "messages.upgrades.transaction-failed");
            return true;
        }

        List<ItemStack> removedItems = HorseUpgradeInventoryCost.consume(player, targetLevel.items());
        if (!targetLevel.items().isEmpty() && removedItems.isEmpty()) {
            economy.deposit(player, targetLevel.money());
            plugin.getLang().send(player, "messages.upgrades.transaction-failed");
            return true;
        }

        try {
            HorseAbilityStorage.setLevel(data, definition.key(), nextLevel);
            TrainingManager.recalculateAndApplyBonuses(horse);
        } catch (RuntimeException exception) {
            HorseAbilityStorage.setLevel(data, definition.key(), currentLevel);
            HorseUpgradeInventoryCost.refund(player, removedItems);
            economy.deposit(player, targetLevel.money());
            plugin.getLogger().warning("Failed to apply horse upgrade " + definition.key() + ": " + exception.getMessage());
            plugin.getLang().send(player, "messages.upgrades.transaction-failed");
            return true;
        }

        plugin.getLang().sendFormatted(
                player,
                "messages.upgrades.purchased",
                "%upgrade%", parseDisplayName(player, definition),
                "%level%", nextLevel,
                "%money%", economy.format(targetLevel.money()),
                "%items%", formatItems(player, targetLevel.items())
        );
        return true;
    }

    public boolean setLevel(Player player, String rawUpgradeKey, int requestedLevel) {
        AbstractHorse horse = requireMountedHorse(player);
        if (horse == null) {
            return true;
        }

        HorseUpgradeDefinition definition = registry.find(rawUpgradeKey).orElse(null);
        if (definition == null) {
            plugin.getLang().sendFormatted(player, "messages.upgrades.unknown", "%upgrade%", rawUpgradeKey);
            return true;
        }

        int level = Math.max(0, Math.min(definition.maxLevel(), requestedLevel));
        HorseAbilityStorage.setLevel(horse.getPersistentDataContainer(), definition.key(), level);
        TrainingManager.recalculateAndApplyBonuses(horse);
        plugin.getLang().sendFormatted(
                player,
                "messages.upgrades.admin-set",
                "%upgrade%", parseDisplayName(player, definition),
                "%level%", level
        );
        return true;
    }

    public boolean remove(Player player, String rawUpgradeKey) {
        return setLevel(player, rawUpgradeKey, 0);
    }

    public int level(AbstractHorse horse, String upgradeKey) {
        return HorseAbilityStorage.getLevel(horse.getPersistentDataContainer(), upgradeKey);
    }

    public boolean has(AbstractHorse horse, String upgradeKey) {
        return level(horse, upgradeKey) > 0;
    }

    public boolean isOwner(Player player, AbstractHorse horse) {
        if (player.hasPermission("betterhorses.bypass")) {
            return true;
        }
        String storedOwner = horse.getPersistentDataContainer().get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (storedOwner != null && !storedOwner.isBlank()) {
            return storedOwner.equals(player.getUniqueId().toString());
        }
        AnimalTamer owner = horse.getOwner();
        return horse.isTamed() && owner != null && owner.getUniqueId().equals(player.getUniqueId());
    }

    public void reapplyLoadedHorses() {
        Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClass(AbstractHorse.class).forEach(TrainingManager::recalculateAndApplyBonuses)
        );
    }

    private String buildOverviewPage(
            Player player,
            String horseName,
            PersistentDataContainer data,
            Collection<HorseUpgradeDefinition> definitions
    ) {
        LanguageManager lang = plugin.getLang();
        int enabledCount = 0;
        int unlockedCount = 0;
        int currentLevels = 0;
        int maximumLevels = 0;

        for (HorseUpgradeDefinition definition : definitions) {
            if (!definition.enabled()) {
                continue;
            }
            enabledCount++;
            int level = Math.min(
                    definition.maxLevel(),
                    HorseAbilityStorage.getLevel(data, definition.key())
            );
            if (level > 0) {
                unlockedCount++;
            }
            currentLevels += Math.max(0, level);
            maximumLevels += definition.maxLevel();
        }

        StringBuilder page = new StringBuilder();
        page.append(lang.getFormattedRaw(
                player,
                "messages.upgrades.book-header",
                "%horse%", horseName
        ));
        page.append("\n\n");
        appendConfiguredParagraphs(page, player, lang.getConfig().getStringList("messages.upgrades.book-introduction"));
        page.append("\n\n");
        page.append(lang.getFormattedRaw(
                player,
                "messages.upgrades.book-progress",
                "%unlocked%", unlockedCount,
                "%available%", enabledCount,
                "%levels%", currentLevels,
                "%max-levels%", maximumLevels
        ));
        page.append("\n\n");
        page.append(lang.getRaw(player, "messages.upgrades.book-navigation"));
        return page.toString();
    }

    private void addUpgradePages(
            BookMeta meta,
            Player player,
            PersistentDataContainer data,
            HorseUpgradeDefinition definition
    ) {
        LanguageManager lang = plugin.getLang();
        int currentLevel = Math.min(
                definition.maxLevel(),
                HorseAbilityStorage.getLevel(data, definition.key())
        );

        StringBuilder summary = new StringBuilder();
        summary.append(lang.getFormattedRaw(
                player,
                "messages.upgrades.book-upgrade-header",
                "%upgrade%", parseDisplayName(player, definition),
                "%id%", definition.key()
        ));
        if (!definition.description().isEmpty()) {
            summary.append("\n\n");
            appendConfiguredParagraphs(summary, player, definition.description());
        }
        summary.append("\n\n");
        summary.append(lang.getFormattedRaw(
                player,
                "messages.upgrades.book-current-level",
                "%level%", currentLevel,
                "%max%", definition.maxLevel()
        ));
        summary.append("\n\n");
        summary.append(lang.getRaw(player, "messages.upgrades.book-navigation"));
        addPaginatedPages(meta, summary.toString());

        for (int levelNumber = 1; levelNumber <= definition.maxLevel(); levelNumber++) {
            StringBuilder levelPage = new StringBuilder();
            levelPage.append(lang.getFormattedRaw(
                    player,
                    "messages.upgrades.book-upgrade-header",
                    "%upgrade%", parseDisplayName(player, definition),
                    "%id%", definition.key()
            ));
            levelPage.append("\n\n");
            levelPage.append(lang.getRaw(player, "messages.upgrades.book-levels-header"));
            levelPage.append('\n');

            HorseUpgradeLevel configuredLevel = definition.level(levelNumber).orElse(null);
            if (configuredLevel == null) {
                levelPage.append(lang.getFormattedRaw(
                        player,
                        "messages.upgrades.book-level-unconfigured",
                        "%level%", levelNumber
                ));
                addPaginatedPages(meta, levelPage.toString());
                continue;
            }

            String stateKey;
            if (levelNumber <= currentLevel) {
                stateKey = "messages.upgrades.book-state-unlocked";
            } else if (levelNumber == currentLevel + 1) {
                stateKey = "messages.upgrades.book-state-next";
            } else {
                stateKey = "messages.upgrades.book-state-locked";
            }

            String effectDescription = configuredLevel.effectDescription().isBlank()
                    ? lang.getFormattedRaw(
                            player,
                            "messages.upgrades.book-effect-fallback",
                            "%effect%", formatEffect(configuredLevel.effect())
                    )
                    : lang.parseToString(player, configuredLevel.effectDescription());

            levelPage.append(lang.getFormattedRaw(
                    player,
                    "messages.upgrades.book-level-line",
                    "%state%", lang.getRaw(player, stateKey),
                    "%level%", levelNumber,
                    "%effect%", effectDescription
            ));
            levelPage.append("\n\n");
            levelPage.append(lang.getFormattedRaw(
                    player,
                    "messages.upgrades.book-level-cost",
                    "%cost%", formatCost(player, configuredLevel)
            ));

            if (levelNumber == currentLevel + 1) {
                levelPage.append("\n\n");
                levelPage.append(lang.getFormattedRaw(
                        player,
                        "messages.upgrades.book-purchase-hint",
                        "%upgrade%", definition.key()
                ));
            } else if (levelNumber == definition.maxLevel() && currentLevel >= definition.maxLevel()) {
                levelPage.append("\n\n");
                levelPage.append(lang.getRaw(player, "messages.upgrades.book-maximum-reached"));
            }

            addPaginatedPages(meta, levelPage.toString());
        }
    }

    private void appendConfiguredParagraphs(StringBuilder target, Player player, List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            target.append(plugin.getLang().parseToString(player, lines.get(index)));
            if (index + 1 < lines.size()) {
                target.append('\n');
            }
        }
    }

    private void addPaginatedPages(BookMeta meta, String content) {
        String[] lines = content.split("\\n", -1);
        StringBuilder page = new StringBuilder();
        String carriedFormatting = "";

        for (String line : lines) {
            String candidate = page.isEmpty() ? line : page + "\n" + line;
            if (candidate.length() <= MAX_BOOK_PAGE_LENGTH) {
                if (!page.isEmpty()) {
                    page.append('\n');
                }
                page.append(line);
                carriedFormatting = ChatColor.getLastColors(page.toString());
                continue;
            }

            if (!page.isEmpty()) {
                meta.addPage(trimLegacy(page.toString(), MAX_BOOK_PAGE_LENGTH));
                carriedFormatting = ChatColor.getLastColors(page.toString());
                page.setLength(0);
            }

            String remaining = carriedFormatting + line;
            while (remaining.length() > MAX_BOOK_PAGE_LENGTH) {
                int end = safeLegacyEnd(remaining, MAX_BOOK_PAGE_LENGTH);
                String chunk = remaining.substring(0, end);
                meta.addPage(chunk);
                String colors = ChatColor.getLastColors(chunk);
                remaining = colors + remaining.substring(end);
            }
            page.append(remaining);
        }

        if (!page.isEmpty()) {
            meta.addPage(trimLegacy(page.toString(), MAX_BOOK_PAGE_LENGTH));
        }
    }

    private AbstractHorse requireMountedHorse(Player player) {
        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            plugin.getLang().send(player, "messages.upgrades.not-riding");
            return null;
        }
        return horse;
    }

    private String formatCost(Player player, HorseUpgradeLevel level) {
        String money = level.money() <= 0.0D
                ? plugin.getLang().getRaw(player, "messages.upgrades.free")
                : economy.format(level.money());
        String items = level.items().isEmpty()
                ? plugin.getLang().getRaw(player, "messages.upgrades.no-items")
                : formatItems(player, level.items());
        return plugin.getLang().getFormattedRaw(
                player,
                "messages.upgrades.cost-format",
                "%money%", money,
                "%items%", items
        );
    }

    private String formatItems(Player player, List<UpgradeItemRequirement> requirements) {
        if (requirements.isEmpty()) {
            return plugin.getLang().getRaw(player, "messages.upgrades.no-items");
        }
        List<String> parts = new ArrayList<>(requirements.size());
        for (UpgradeItemRequirement requirement : requirements) {
            String itemName = requirement.displayName().isBlank()
                    ? requirement.fallbackDisplayName()
                    : plugin.getLang().parseToString(player, requirement.displayName());
            String model = requirement.customModelData() < 0
                    ? ""
                    : plugin.getLang().getFormattedRaw(
                            player,
                            "messages.upgrades.custom-model-suffix",
                            "%model%", requirement.customModelData()
                    );
            parts.add(plugin.getLang().getFormattedRaw(
                    player,
                    "messages.upgrades.item-format",
                    "%amount%", requirement.amount(),
                    "%item%", itemName,
                    "%model%", model
            ));
        }
        return String.join(plugin.getLang().getRaw(player, "messages.upgrades.item-separator"), parts);
    }

    private String parseDisplayName(Player player, HorseUpgradeDefinition definition) {
        return plugin.getLang().parseToString(player, definition.displayName());
    }

    private static String formatEffect(double effect) {
        if (Math.rint(effect) == effect) {
            return String.format("%.0f", effect);
        }
        return String.format("%.2f", effect);
    }

    private static String trimPlain(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private static int safeLegacyEnd(String value, int requestedEnd) {
        int end = Math.min(requestedEnd, value.length());
        if (end > 0 && value.charAt(end - 1) == ChatColor.COLOR_CHAR) {
            end--;
        }
        return Math.max(0, end);
    }

    private static String trimLegacy(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, safeLegacyEnd(value, maximumLength));
    }
}

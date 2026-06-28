package me.luisgamedev.betterhorses.upgrades;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads upgrade definitions from config.yml and keeps their order stable. */
public final class HorseUpgradeRegistry {

    private final BetterHorses plugin;
    private volatile Map<String, HorseUpgradeDefinition> definitions = Map.of();
    private volatile boolean enabled;

    public HorseUpgradeRegistry(BetterHorses plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        FileConfiguration language = plugin.getLang().getConfig();
        enabled = config.getBoolean("upgrades.enabled", true);
        ConfigurationSection root = config.getConfigurationSection("upgrades");
        if (root == null) {
            definitions = Map.of();
            return;
        }

        LinkedHashMap<String, HorseUpgradeDefinition> loaded = new LinkedHashMap<>();
        for (String rawKey : root.getKeys(false)) {
            if (rawKey.equalsIgnoreCase("enabled")) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(rawKey);
            if (section == null) {
                continue;
            }

            String key = normalize(rawKey);
            String languageBase = "upgrade-definitions." + key;
            String displayName = language.getString(languageBase + ".display-name", key);
            List<String> description = language.getStringList(languageBase + ".description");
            boolean definitionEnabled = section.getBoolean("enabled", true);
            ConfigurationSection levelSection = section.getConfigurationSection("levels");
            LinkedHashMap<Integer, HorseUpgradeLevel> levels = new LinkedHashMap<>();
            if (levelSection != null) {
                for (String rawLevel : levelSection.getKeys(false)) {
                    int level;
                    try {
                        level = Integer.parseInt(rawLevel);
                    } catch (NumberFormatException exception) {
                        plugin.getLogger().warning("Ignoring invalid upgrade level upgrades." + key + ".levels." + rawLevel);
                        continue;
                    }
                    ConfigurationSection configuredLevel = levelSection.getConfigurationSection(rawLevel);
                    if (configuredLevel == null || level <= 0) {
                        continue;
                    }
                    levels.put(level, new HorseUpgradeLevel(
                            level,
                            configuredLevel.getDouble("money", 0.0D),
                            configuredLevel.getDouble("effect", 0.0D),
                            language.getString(languageBase + ".levels." + level + ".effect-description", ""),
                            loadItems(key, level, configuredLevel, languageBase, language)
                    ));
                }
            }

            int configuredMax = section.getInt("max-level", levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1));
            int maxLevel = Math.max(1, configuredMax);
            loaded.put(key, new HorseUpgradeDefinition(
                    key,
                    displayName,
                    description,
                    definitionEnabled,
                    maxLevel,
                    levels
            ));
        }
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    private List<UpgradeItemRequirement> loadItems(
            String upgradeKey,
            int level,
            ConfigurationSection configuredLevel,
            String languageBase,
            FileConfiguration language
    ) {
        List<Map<?, ?>> configuredItems = configuredLevel.getMapList("items");
        List<String> localizedItemNames = language.getStringList(
                languageBase + ".levels." + level + ".item-names"
        );
        List<UpgradeItemRequirement> items = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < configuredItems.size(); itemIndex++) {
            Map<?, ?> configuredItem = configuredItems.get(itemIndex);
            Object rawMaterial = configuredItem.containsKey("material") ? configuredItem.get("material") : "AIR";
            String materialName = String.valueOf(rawMaterial);
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning(
                        "Ignoring invalid material '" + materialName + "' for upgrade " + upgradeKey + " level " + level
                );
                continue;
            }

            int amount = asInt(configuredItem.get("amount"), 1);
            int customModelData = asInt(configuredItem.get("custom-model-data"), -1);
            String displayName = itemIndex < localizedItemNames.size()
                    ? localizedItemNames.get(itemIndex)
                    : "";
            items.add(new UpgradeItemRequirement(material, amount, customModelData, displayName));
        }
        return List.copyOf(items);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<HorseUpgradeDefinition> find(String key) {
        return Optional.ofNullable(definitions.get(normalize(key)));
    }

    public Collection<HorseUpgradeDefinition> all() {
        return definitions.values();
    }

    public List<String> enabledKeys() {
        return definitions.values().stream()
                .filter(HorseUpgradeDefinition::enabled)
                .map(HorseUpgradeDefinition::key)
                .toList();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}

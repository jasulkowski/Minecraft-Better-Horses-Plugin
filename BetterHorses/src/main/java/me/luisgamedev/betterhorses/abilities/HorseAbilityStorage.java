package me.luisgamedev.betterhorses.abilities;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stores permanent non-trait horse abilities and upgrade levels in PDC.
 *
 * <p>The legacy format stored one plain ability key per line. The current
 * format stores {@code key=level}. Legacy entries are read as level 1 so old
 * horses remain compatible.</p>
 */
public final class HorseAbilityStorage {

    private static final String SEPARATOR = "\n";
    private static final String LEVEL_SEPARATOR = "=";

    private HorseAbilityStorage() {
    }

    public static List<String> read(PersistentDataContainer data) {
        return List.copyOf(readLevels(data).keySet());
    }

    public static Map<String, Integer> readLevels(PersistentDataContainer data) {
        String encoded = data.get(BetterHorseKeys.OTHER_ABILITIES, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }

        LinkedHashMap<String, Integer> abilities = new LinkedHashMap<>();
        for (String rawEntry : encoded.split(SEPARATOR)) {
            String entry = rawEntry == null ? "" : rawEntry.trim();
            if (entry.isBlank()) {
                continue;
            }

            String key = entry;
            int level = 1;
            int separatorIndex = entry.lastIndexOf(LEVEL_SEPARATOR);
            if (separatorIndex > 0 && separatorIndex + 1 < entry.length()) {
                key = entry.substring(0, separatorIndex);
                try {
                    level = Math.max(1, Integer.parseInt(entry.substring(separatorIndex + 1).trim()));
                } catch (NumberFormatException ignored) {
                    level = 1;
                }
            }

            String normalized = normalize(key);
            if (!normalized.isBlank()) {
                abilities.merge(normalized, level, Math::max);
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(abilities));
    }

    public static void write(PersistentDataContainer data, Collection<String> abilityKeys) {
        LinkedHashMap<String, Integer> levels = new LinkedHashMap<>();
        for (String abilityKey : abilityKeys) {
            String value = normalize(abilityKey);
            if (!value.isBlank()) {
                levels.put(value, 1);
            }
        }
        writeLevels(data, levels);
    }

    public static void writeLevels(PersistentDataContainer data, Map<String, Integer> abilityLevels) {
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : abilityLevels.entrySet()) {
            String key = normalize(entry.getKey());
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (!key.isBlank() && level > 0) {
                normalized.merge(key, level, Math::max);
            }
        }

        if (normalized.isEmpty()) {
            data.remove(BetterHorseKeys.OTHER_ABILITIES);
            return;
        }

        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, Integer> entry : normalized.entrySet()) {
            if (!encoded.isEmpty()) {
                encoded.append(SEPARATOR);
            }
            encoded.append(entry.getKey())
                    .append(LEVEL_SEPARATOR)
                    .append(entry.getValue());
        }
        data.set(BetterHorseKeys.OTHER_ABILITIES, PersistentDataType.STRING, encoded.toString());
    }

    public static int getLevel(PersistentDataContainer data, String abilityKey) {
        return readLevels(data).getOrDefault(normalize(abilityKey), 0);
    }

    public static boolean setLevel(PersistentDataContainer data, String abilityKey, int level) {
        String normalizedKey = normalize(abilityKey);
        if (normalizedKey.isBlank()) {
            return false;
        }

        LinkedHashMap<String, Integer> levels = new LinkedHashMap<>(readLevels(data));
        int previous = levels.getOrDefault(normalizedKey, 0);
        if (level <= 0) {
            levels.remove(normalizedKey);
        } else {
            levels.put(normalizedKey, level);
        }
        writeLevels(data, levels);
        return previous != Math.max(0, level);
    }

    public static boolean add(PersistentDataContainer data, String abilityKey) {
        if (getLevel(data, abilityKey) > 0) {
            return false;
        }
        return setLevel(data, abilityKey, 1);
    }

    public static boolean remove(PersistentDataContainer data, String abilityKey) {
        if (getLevel(data, abilityKey) <= 0) {
            return false;
        }
        return setLevel(data, abilityKey, 0);
    }

    public static boolean contains(PersistentDataContainer data, String abilityKey) {
        return getLevel(data, abilityKey) > 0;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }
}

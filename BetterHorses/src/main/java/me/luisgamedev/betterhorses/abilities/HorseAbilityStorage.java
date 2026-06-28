package me.luisgamedev.betterhorses.abilities;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Stores permanent, non-trait horse abilities in PDC. These entries are meant
 * for future upgrades purchased from a stable master, such as tandem riding or
 * permanent stat perks.
 */
public final class HorseAbilityStorage {

    private static final String SEPARATOR = "\n";

    private HorseAbilityStorage() {
    }

    public static List<String> read(PersistentDataContainer data) {
        String encoded = data.get(BetterHorseKeys.OTHER_ABILITIES, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> abilities = new LinkedHashSet<>();
        for (String raw : encoded.split(SEPARATOR)) {
            String normalized = normalize(raw);
            if (!normalized.isBlank()) {
                abilities.add(normalized);
            }
        }
        return List.copyOf(abilities);
    }

    public static void write(PersistentDataContainer data, Collection<String> abilityKeys) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String abilityKey : abilityKeys) {
            String value = normalize(abilityKey);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }

        if (normalized.isEmpty()) {
            data.remove(BetterHorseKeys.OTHER_ABILITIES);
            return;
        }

        data.set(
                BetterHorseKeys.OTHER_ABILITIES,
                PersistentDataType.STRING,
                String.join(SEPARATOR, normalized)
        );
    }

    public static boolean add(PersistentDataContainer data, String abilityKey) {
        String normalizedKey = normalize(abilityKey);
        if (normalizedKey.isBlank()) {
            return false;
        }
        LinkedHashSet<String> abilities = new LinkedHashSet<>(read(data));
        boolean changed = abilities.add(normalizedKey);
        if (changed) {
            write(data, abilities);
        }
        return changed;
    }

    public static boolean remove(PersistentDataContainer data, String abilityKey) {
        String normalizedKey = normalize(abilityKey);
        if (normalizedKey.isBlank()) {
            return false;
        }
        LinkedHashSet<String> abilities = new LinkedHashSet<>(read(data));
        boolean changed = abilities.remove(normalizedKey);
        if (changed) {
            write(data, abilities);
        }
        return changed;
    }

    public static boolean contains(PersistentDataContainer data, String abilityKey) {
        return read(data).contains(normalize(abilityKey));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }
}

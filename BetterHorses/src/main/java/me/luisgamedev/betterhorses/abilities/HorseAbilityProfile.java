package me.luisgamedev.betterhorses.abilities;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only ability view used by the statistics book.
 */
public record HorseAbilityProfile(
        Optional<String> passiveAbility,
        Optional<String> activeAbility,
        List<String> otherAbilities
) {

    private static final Set<String> PASSIVE_TRAITS = Set.of(
            "fireheart",
            "featherhooves",
            "frosthooves",
            "skyburst",
            "heavenhooves"
    );

    private static final Set<String> ACTIVE_TRAITS = Set.of(
            "hellmare",
            "dashboost",
            "kickback",
            "ghosthorse",
            "revenantcurse"
    );

    public static HorseAbilityProfile from(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        String trait = normalize(data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING));

        Optional<String> passive = PASSIVE_TRAITS.contains(trait)
                ? Optional.of(trait)
                : Optional.empty();
        Optional<String> active = ACTIVE_TRAITS.contains(trait)
                ? Optional.of(trait)
                : Optional.empty();

        LinkedHashSet<String> other = new LinkedHashSet<>(HorseAbilityStorage.read(data));
        if (!trait.isBlank() && passive.isEmpty() && active.isEmpty()) {
            // Keep custom traits from third-party integrations visible instead
            // of silently dropping them from the statistics book.
            other.add(trait);
        }

        return new HorseAbilityProfile(passive, active, List.copyOf(other));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}

package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Maintains a stable identity for a BetterHorses mount across entity/item conversions.
 * Neutering is tied to that identity instead of relying on a shared boolean flag alone.
 */
public final class HorseIdentity {

    private HorseIdentity() {
    }

    /**
     * Returns the stable horse id, creating one when it is missing.
     */
    public static String ensureHorseId(PersistentDataContainer data) {
        String current = data.get(BetterHorseKeys.HORSE_ID, PersistentDataType.STRING);
        if (current != null && !current.isBlank()) {
            return current;
        }

        String generated = UUID.randomUUID().toString();
        data.set(BetterHorseKeys.HORSE_ID, PersistentDataType.STRING, generated);
        return generated;
    }

    /**
     * Returns true only when the neuter marker belongs to this exact horse id.
     */
    public static boolean isNeutered(PersistentDataContainer data) {
        String horseId = data.get(BetterHorseKeys.HORSE_ID, PersistentDataType.STRING);
        String neuteredHorseId = data.get(BetterHorseKeys.NEUTERED_HORSE_ID, PersistentDataType.STRING);
        return horseId != null && !horseId.isBlank() && horseId.equals(neuteredHorseId);
    }

    /**
     * Marks only this horse identity as neutered.
     */
    public static void markNeutered(PersistentDataContainer data) {
        String horseId = ensureHorseId(data);
        data.set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);
        data.set(BetterHorseKeys.NEUTERED_HORSE_ID, PersistentDataType.STRING, horseId);
    }

    /**
     * Copies the stable identity and identity-bound neuter state between an entity and item container.
     */
    public static void copyIdentity(PersistentDataContainer source, PersistentDataContainer target) {
        String horseId = ensureHorseId(source);
        target.set(BetterHorseKeys.HORSE_ID, PersistentDataType.STRING, horseId);

        if (isNeutered(source)) {
            target.set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);
            target.set(BetterHorseKeys.NEUTERED_HORSE_ID, PersistentDataType.STRING, horseId);
        } else {
            target.remove(BetterHorseKeys.NEUTERED);
            target.remove(BetterHorseKeys.NEUTERED_HORSE_ID);
        }
    }

    /**
     * Migrates a legacy neutered item when it is explicitly handled as a horse item.
     * This is intentionally not run for arbitrary live horses, preventing a stale shared flag
     * from making every horse infertile.
     */
    public static void migrateLegacyNeuteredItem(PersistentDataContainer data) {
        Byte legacy = data.get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE);
        if (legacy != null && legacy == (byte) 1
                && !data.has(BetterHorseKeys.NEUTERED_HORSE_ID, PersistentDataType.STRING)) {
            markNeutered(data);
        }
    }
}

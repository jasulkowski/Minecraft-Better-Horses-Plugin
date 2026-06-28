package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.abilities.HorseAbilityStorage;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper around an in-world BetterHorses mount to simplify reading and
 * writing its custom stats while keeping persistent data synchronized.
 */
public final class BetterHorse {

    private final AbstractHorse handle;

    public BetterHorse(AbstractHorse handle) {
        this.handle = handle;
    }

    public AbstractHorse getHandle() {
        return handle;
    }

    public double getMaxHealth() {
        AttributeInstance attr = handle.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    public void setMaxHealth(double value) {
        setAttribute(AttributeResolver.generic("MAX_HEALTH"), BetterHorseKeys.HEALTH, value);
    }

    public double getSpeed() {
        AttributeInstance attr = handle.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    public void setSpeed(double value) {
        setAttribute(AttributeResolver.generic("MOVEMENT_SPEED"), BetterHorseKeys.SPEED, value);
    }

    public double getJump() {
        AttributeInstance attr = handle.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    public void setJump(double value) {
        setAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"), BetterHorseKeys.JUMP, value);
    }

    public Optional<String> getTrait() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING));
    }

    public void setTrait(String trait) {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        if (trait == null || trait.isBlank()) {
            data.remove(BetterHorseKeys.TRAIT);
        } else {
            data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, trait.toLowerCase());
        }
    }

    /**
     * Returns permanent non-trait abilities, prepared for future stable-master
     * upgrades such as tandem riding or special movement perks.
     */
    public List<String> getOtherAbilities() {
        return HorseAbilityStorage.read(handle.getPersistentDataContainer());
    }

    public void setOtherAbilities(Collection<String> abilityKeys) {
        HorseAbilityStorage.write(handle.getPersistentDataContainer(), abilityKeys);
    }

    public boolean addOtherAbility(String abilityKey) {
        return HorseAbilityStorage.add(handle.getPersistentDataContainer(), abilityKey);
    }

    public boolean removeOtherAbility(String abilityKey) {
        return HorseAbilityStorage.remove(handle.getPersistentDataContainer(), abilityKey);
    }

    public boolean hasOtherAbility(String abilityKey) {
        return HorseAbilityStorage.contains(handle.getPersistentDataContainer(), abilityKey);
    }

    public Optional<Integer> getGrowthStage() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER));
    }

    public void setGrowthStage(int stage) {
        handle.getPersistentDataContainer().set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, stage);
    }

    private void setAttribute(Attribute attribute, NamespacedKey key, double value) {
        AttributeInstance attr = handle.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(value);
        }
        handle.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
    }
}

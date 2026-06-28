package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.upgrades.HorseUpgradeEffects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.EquipmentSlot;

/** Applies the global owner restriction and the permanent Loyal Mount upgrade. */
public final class HorseMountListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHorseMount(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("betterhorses.bypass")) {
            return;
        }

        BetterHorses plugin = BetterHorses.getInstance();
        PersistentDataContainer data = horse.getPersistentDataContainer();
        String ownerUuid = data.get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (ownerUuid == null || ownerUuid.isBlank()) {
            AnimalTamer vanillaOwner = horse.getOwner();
            ownerUuid = vanillaOwner == null ? null : vanillaOwner.getUniqueId().toString();
        }
        if (ownerUuid == null || ownerUuid.equals(player.getUniqueId().toString())) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        boolean loyalMount = HorseUpgradeEffects.effect(config, data, HorseUpgradeEffects.LOYAL_MOUNT) > 0.0D;
        if (loyalMount) {
            event.setCancelled(true);
            plugin.getLang().send(player, "messages.upgrades.loyal-mount-denied");
            return;
        }

        if (!config.getBoolean("settings.restrict-mounting-to-owner", false)) {
            return;
        }

        // A non-owner may still become the explicitly unlocked second passenger.
        boolean tandemPassengerAttempt = HorseUpgradeEffects.effect(
                config,
                data,
                HorseUpgradeEffects.TANDEM_SEAT
        ) > 0.0D && horse.getPassengers().stream().anyMatch(Player.class::isInstance);
        if (tandemPassengerAttempt) {
            return;
        }

        event.setCancelled(true);
        plugin.getLang().send(player, "messages.not-horse-owner");
    }
}

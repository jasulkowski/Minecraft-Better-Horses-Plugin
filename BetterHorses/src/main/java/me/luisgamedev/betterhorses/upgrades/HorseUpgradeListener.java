package me.luisgamedev.betterhorses.upgrades;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class HorseUpgradeListener implements Listener {

    private final BetterHorses plugin;
    private final TandemRideManager tandemRideManager;

    public HorseUpgradeListener(BetterHorses plugin, TandemRideManager tandemRideManager) {
        this.plugin = plugin;
        this.tandemRideManager = tandemRideManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHorseRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().isSneaking()) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }
        if (HorseUpgradeEffects.effect(
                plugin.getConfig(),
                horse.getPersistentDataContainer(),
                HorseUpgradeEffects.TANDEM_SEAT
        ) <= 0.0D || horse.getPassengers().stream().noneMatch(Player.class::isInstance)) {
            return;
        }
        event.setCancelled(true);
        tandemRideManager.tryMount(event.getPlayer(), horse);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && tandemRideManager.isTandemPassenger(event.getPlayer())) {
            tandemRideManager.removeByPassenger(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tandemRideManager.removeByPassenger(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof AbstractHorse horse) {
            tandemRideManager.removeByHorse(horse.getUniqueId());
        } else if (tandemRideManager.isSeat(entity)) {
            entity.remove();
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof AbstractHorse horse) {
                tandemRideManager.removeByHorse(horse.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protectSeat(EntityDamageEvent event) {
        if (tandemRideManager.isSeat(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protectSeatInteraction(PlayerInteractAtEntityEvent event) {
        if (tandemRideManager.isSeat(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        AbstractHorse horse = null;
        if (event.getEntity() instanceof AbstractHorse damagedHorse) {
            horse = damagedHorse;
        } else if (event.getEntity() instanceof Player player) {
            if (player.getVehicle() instanceof AbstractHorse mountedHorse) {
                horse = mountedHorse;
            } else {
                horse = tandemRideManager.getHorseForPassenger(player);
            }
        }
        if (horse == null) {
            return;
        }

        double reduction = HorseUpgradeEffects.effect(
                plugin.getConfig(),
                horse.getPersistentDataContainer(),
                HorseUpgradeEffects.SURE_FOOTED
        );
        if (reduction <= 0.0D) {
            return;
        }
        if (reduction >= 1.0D) {
            event.setCancelled(true);
        } else {
            event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D - reduction)));
        }
    }
}

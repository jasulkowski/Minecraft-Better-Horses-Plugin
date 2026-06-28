package me.luisgamedev.betterhorses.statistics;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Opens horse statistics when a player sneak-right-clicks a horse with a
 * plugin-issued inspection book.
 */
public final class HorseStatsBookListener implements Listener {

    private final HorseStatsBookService service;

    public HorseStatsBookListener(HorseStatsBookService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHorseInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItem(event.getHand());
        if (!player.isSneaking() || !service.isInspectionBook(heldItem)) {
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission("betterhorses.book")) {
            BetterHorses.getInstance().getLang().sendFormatted(
                    player,
                    "messages.insufficient-permission",
                    "%command%", "horse inspection book"
            );
            return;
        }

        service.openHorseStats(player, horse);
    }
}

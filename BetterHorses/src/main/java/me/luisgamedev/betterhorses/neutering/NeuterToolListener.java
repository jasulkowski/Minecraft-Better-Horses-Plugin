package me.luisgamedev.betterhorses.neutering;

import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles Shift + right-click neutering with plugin-issued veterinary shears.
 */
public final class NeuterToolListener implements Listener {

    private final NeuterToolService service;

    public NeuterToolListener(NeuterToolService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHorseInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItem(event.getHand());
        if (!service.isNeuterTool(heldItem)) {
            return;
        }

        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        service.neuter(player, horse, event.getHand(), heldItem.clone());
    }
}

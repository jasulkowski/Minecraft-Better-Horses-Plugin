package me.luisgamedev.betterhorses.summon;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * User-facing interaction layer for the summon horn feature.
 */
public final class HorseSummonListener implements Listener {

    private final BetterHorses plugin;
    private final HorseSummonService summonService;

    public HorseSummonListener(BetterHorses plugin, HorseSummonService summonService) {
        this.plugin = plugin;
        this.summonService = summonService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }

        Player player = event.getPlayer();
        HorseSummonSettings settings = HorseSummonSettings.load(plugin);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!summonService.isSummonHornCandidate(item, settings)) {
            return;
        }
        if (!player.hasPermission("betterhorses.summon")) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.insufficient-permission",
                    "%command%", plugin.getLang().getRaw(player, "messages.permission-targets.horse-summon")
            );
            return;
        }
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        summonService.bindHorn(player, horse, item, settings);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        HorseSummonSettings settings = HorseSummonSettings.load(plugin);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!summonService.isSummonHornCandidate(item, settings)) {
            return;
        }
        if (!player.hasPermission("betterhorses.summon")) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.insufficient-permission",
                    "%command%", plugin.getLang().getRaw(player, "messages.permission-targets.horse-summon")
            );
            return;
        }

        if (player.isSneaking() && !summonService.isBound(item)) {
            summonService.findLookedAtMount(player, settings).ifPresent(horse -> {
                event.setCancelled(true);
                summonService.bindHorn(player, horse, item, settings);
            });
            return;
        }

        if (summonService.isBound(item)) {
            event.setCancelled(true);
            summonService.callBoundHorse(player, item, settings);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHornFinishedUse(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        HorseSummonSettings settings = HorseSummonSettings.load(plugin);

        ItemStack item = event.getItem();
        if (!summonService.isSummonHornCandidate(item, settings) || !summonService.isBound(item)) {
            return;
        }

        if (!player.hasPermission("betterhorses.summon")) {
            plugin.getLang().sendFormatted(
                    player,
                    "messages.insufficient-permission",
                    "%command%", plugin.getLang().getRaw(player, "messages.permission-targets.horse-summon")
            );
            return;
        }

        // Goat horns are not consumed, but cancelling keeps the server-side interaction under plugin control.
        event.setCancelled(true);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (summonService.isSummonHornCandidate(mainHand, settings) && summonService.isBound(mainHand)) {
            summonService.callBoundHorse(player, mainHand, settings);
            return;
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (summonService.isSummonHornCandidate(offHand, settings) && summonService.isBound(offHand)) {
            summonService.callBoundHorse(player, offHand, settings);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        summonService.clearPlayerState(event.getPlayer().getUniqueId());
    }

}

package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.statistics.HorseStatsBookService;
import org.bukkit.entity.Player;

/** Opens a virtual statistics book for the horse currently being ridden. */
public final class HorseStatsCommand {

    private final HorseStatsBookService service;

    public HorseStatsCommand(HorseStatsBookService service) {
        this.service = service;
    }

    public boolean handle(Player player) {
        return service.openMountedHorseStats(player);
    }
}

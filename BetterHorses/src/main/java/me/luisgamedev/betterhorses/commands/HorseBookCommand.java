package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.statistics.HorseStatsBookService;
import org.bukkit.entity.Player;

/** Gives or sells the physical horse inspection book. */
public final class HorseBookCommand {

    private final HorseStatsBookService service;

    public HorseBookCommand(HorseStatsBookService service) {
        this.service = service;
    }

    public boolean handle(Player player) {
        return service.giveBook(player);
    }
}

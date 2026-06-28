package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.neutering.NeuterToolService;
import org.bukkit.entity.Player;

/**
 * Gives or sells the physical veterinary shears item. Neutering itself is handled
 * through Shift + right-click on a horse and is no longer performed by a command.
 */
public final class HorseNeuterCommand {

    private final NeuterToolService service;

    public HorseNeuterCommand(NeuterToolService service) {
        this.service = service;
    }

    public boolean handle(Player player) {
        return service.giveTool(player);
    }
}

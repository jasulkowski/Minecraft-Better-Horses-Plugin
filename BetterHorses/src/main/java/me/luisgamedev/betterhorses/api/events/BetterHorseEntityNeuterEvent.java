package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired immediately before a live horse is neutered with veterinary shears.
 * Cancelling the event prevents both the operation and tool-use consumption.
 */
public final class BetterHorseEntityNeuterEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Horse horse;
    private final ItemStack tool;
    private boolean cancelled;

    public BetterHorseEntityNeuterEvent(
            @NotNull Player player,
            @NotNull Horse horse,
            @NotNull ItemStack tool
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.horse = Objects.requireNonNull(horse, "horse");
        this.tool = Objects.requireNonNull(tool, "tool").clone();
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull Horse getHorse() {
        return horse;
    }

    public @NotNull ItemStack getTool() {
        return tool.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

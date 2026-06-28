package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class HorseCommandCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();
        if (sender.hasPermission("betterhorses.base")) {
            suggestions.add("spawn");
            suggestions.add("stats");
        }
        if (sender.hasPermission("betterhorses.book")) {
            suggestions.add("book");
        }
        if (sender.hasPermission("betterhorses.neuter")) {
            suggestions.add("neuter");
        }
        if (sender.hasPermission("betterhorses.reload")) {
            suggestions.add("reload");
        }
        if (BetterHorses.getInstance().isDebugModeEnabled()) {
            suggestions.add("info");
        }

        String prefix = args[0].toLowerCase();
        return suggestions.stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }
}

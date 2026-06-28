package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.upgrades.HorseUpgradeService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class HorseCommandCompleter implements TabCompleter {

    private final HorseUpgradeService upgradeService;

    public HorseCommandCompleter(HorseUpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("betterhorses.base")) {
                suggestions.add("spawn");
                suggestions.add("stats");
            }
            if (sender.hasPermission("betterhorses.book")) suggestions.add("book");
            if (sender.hasPermission("betterhorses.neuter")) suggestions.add("neuter");
            if (sender.hasPermission("betterhorses.upgrade.use")) {
                suggestions.add("upgrade");
                suggestions.add("upgrades");
            }
            if (sender.hasPermission("betterhorses.reload")) suggestions.add("reload");
            if (BetterHorses.getInstance().isDebugModeEnabled()) suggestions.add("info");
            return filter(suggestions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("upgrade")) {
            List<String> suggestions = new ArrayList<>(upgradeService.registry().enabledKeys());
            if (sender.hasPermission("betterhorses.upgrade.admin")) {
                suggestions.add("set");
                suggestions.add("remove");
            }
            return filter(suggestions, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("upgrade")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
            return filter(upgradeService.registry().enabledKeys(), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("upgrade") && args[1].equalsIgnoreCase("set")) {
            int maximum = upgradeService.registry().find(args[2])
                    .map(definition -> definition.maxLevel())
                    .orElse(3);
            List<String> levels = new ArrayList<>();
            for (int level = 0; level <= maximum; level++) {
                levels.add(String.valueOf(level));
            }
            return filter(levels, args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lowered = prefix.toLowerCase();
        return values.stream().filter(value -> value.startsWith(lowered)).toList();
    }
}

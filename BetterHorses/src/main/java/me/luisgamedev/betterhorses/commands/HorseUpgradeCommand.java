package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.upgrades.HorseUpgradeService;
import org.bukkit.entity.Player;

public final class HorseUpgradeCommand {

    private final HorseUpgradeService service;

    public HorseUpgradeCommand(HorseUpgradeService service) {
        this.service = service;
    }

    public boolean list(Player player) {
        if (!player.hasPermission("betterhorses.upgrade.use")) {
            BetterHorses.getInstance().getLang().sendFormatted(
                    player,
                    "messages.insufficient-permission",
                    "%command%", "/horse upgrades"
            );
            return true;
        }
        return service.showUpgrades(player);
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            BetterHorses.getInstance().getLang().send(player, "messages.upgrades.usage");
            return true;
        }

        if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("betterhorses.upgrade.admin")) {
                BetterHorses.getInstance().getLang().sendFormatted(
                        player,
                        "messages.insufficient-permission",
                        "%command%", "/horse upgrade " + args[1]
                );
                return true;
            }

            if (args[1].equalsIgnoreCase("remove")) {
                if (args.length < 3) {
                    BetterHorses.getInstance().getLang().send(player, "messages.upgrades.admin-usage");
                    return true;
                }
                return service.remove(player, args[2]);
            }

            if (args.length < 4) {
                BetterHorses.getInstance().getLang().send(player, "messages.upgrades.admin-usage");
                return true;
            }
            try {
                return service.setLevel(player, args[2], Integer.parseInt(args[3]));
            } catch (NumberFormatException exception) {
                BetterHorses.getInstance().getLang().send(player, "messages.invalid-number-format");
                return true;
            }
        }

        if (!player.hasPermission("betterhorses.upgrade.use")) {
            BetterHorses.getInstance().getLang().sendFormatted(
                    player,
                    "messages.insufficient-permission",
                    "%command%", "/horse upgrade"
            );
            return true;
        }
        return service.purchase(player, args[1]);
    }
}

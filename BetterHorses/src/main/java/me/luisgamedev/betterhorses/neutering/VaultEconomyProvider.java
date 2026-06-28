package me.luisgamedev.betterhorses.neutering;

import me.luisgamedev.betterhorses.BetterHorses;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * Vault-backed economy adapter. The implementation is loaded only when Vault is present.
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    public static Optional<EconomyProvider> create(BetterHorses plugin) {
        RegisteredServiceProvider<Economy> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            return Optional.empty();
        }
        return Optional.of(new VaultEconomyProvider(registration.getProvider()));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean has(Player player, double amount) {
        return amount <= 0.0D || economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}

package me.luisgamedev.betterhorses.neutering;

import org.bukkit.entity.Player;

/**
 * Small abstraction around an optional server economy implementation.
 */
public interface EconomyProvider {

    boolean isAvailable();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    boolean deposit(Player player, double amount);

    String format(double amount);

    static EconomyProvider unavailable() {
        return UnavailableEconomyProvider.INSTANCE;
    }

    enum UnavailableEconomyProvider implements EconomyProvider {
        INSTANCE;

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public boolean has(Player player, double amount) {
            return amount <= 0.0D;
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            return amount <= 0.0D;
        }

        @Override
        public boolean deposit(Player player, double amount) {
            return amount <= 0.0D;
        }

        @Override
        public String format(double amount) {
            return String.format("%.2f", amount);
        }
    }
}

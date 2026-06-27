package me.luisgamedev.betterhorses.summon;

import me.luisgamedev.betterhorses.BetterHorses;

/**
 * Periodically refreshes SQLite coordinates for loaded horses that are bound to summon horns.
 *
 * <p>The task itself runs on the Bukkit main thread because entity access must stay synchronous.
 * Actual SQLite writes are delegated to the repository worker by {@link HorseSummonService}.</p>
 */
public final class HorseSummonTrackingTask implements Runnable {

    private final BetterHorses plugin;
    private final HorseSummonService summonService;

    public HorseSummonTrackingTask(BetterHorses plugin, HorseSummonService summonService) {
        this.plugin = plugin;
        this.summonService = summonService;
    }

    @Override
    public void run() {
        HorseSummonSettings settings = HorseSummonSettings.load(plugin);
        if (!settings.enabled() || !settings.trackingEnabled()) {
            return;
        }
        summonService.refreshLoadedRegisteredHorseLocations();
    }
}

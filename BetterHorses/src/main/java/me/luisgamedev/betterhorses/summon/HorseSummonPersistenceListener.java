package me.luisgamedev.betterhorses.summon;

import org.bukkit.Chunk;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Keeps the summon-horse SQLite index close to the real world state.
 */
public final class HorseSummonPersistenceListener implements Listener {

    private final HorseSummonService summonService;

    public HorseSummonPersistenceListener(HorseSummonService summonService) {
        this.summonService = summonService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof AbstractHorse horse) {
                summonService.updateRegisteredHorseLocationAsync(horse);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHorseDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof AbstractHorse horse) {
            summonService.deleteRegisteredHorseAsync(horse);
        }
    }
}

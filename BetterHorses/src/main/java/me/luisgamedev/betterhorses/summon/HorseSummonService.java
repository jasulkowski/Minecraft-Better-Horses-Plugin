package me.luisgamedev.betterhorses.summon;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Handles binding goat horns to mounts and calling those mounts back to their owner.
 *
 * <p>Bukkit entity access is kept on the main thread. SQLite and chunk loading are
 * performed asynchronously.</p>
 */
public final class HorseSummonService {

    private final BetterHorses plugin;
    private final LanguageManager lang;
    private final HorseSummonRepository repository;
    private final ConcurrentMap<UUID, Long> cooldownUntilMillis = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> activeCallsUntilMillis = new ConcurrentHashMap<>();

    public HorseSummonService(BetterHorses plugin, HorseSummonRepository repository) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
        this.repository = repository;
    }

    public boolean isSummonHornCandidate(ItemStack item, HorseSummonSettings settings) {
        if (item == null || item.getType() == Material.AIR || item.getType() != settings.hornMaterial()) {
            return false;
        }
        if (settings.requiredCustomModelData().isEmpty()) {
            return true;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData()
                && meta.getCustomModelData() == settings.requiredCustomModelData().getAsInt();
    }

    public boolean isBound(ItemStack item) {
        return readBoundData(item).isPresent();
    }

    public Optional<AbstractHorse> findLookedAtMount(Player player, HorseSummonSettings settings) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                settings.bindDistance(),
                entity -> entity instanceof AbstractHorse && SupportedMountType.isSupported(entity)
        );

        if (result == null || !(result.getHitEntity() instanceof AbstractHorse horse)) {
            return Optional.empty();
        }
        return Optional.of(horse);
    }

    public boolean bindHorn(Player player, AbstractHorse horse, ItemStack horn, HorseSummonSettings settings) {
        if (!settings.enabled()) {
            return false;
        }
        if (!SupportedMountType.isSupported(horse)) {
            return false;
        }
        if (settings.requireOwner() && !isOwner(player, horse)) {
            notifyAndThrottle(player, settings, "messages.summon.not-owner");
            return true;
        }

        ItemMeta meta = horn.getItemMeta();
        if (meta == null) {
            lang.send(player, "messages.summon.invalid-horn");
            return true;
        }

        String horseName = resolveHorseName(horse);
        Location location = horse.getLocation();
        if (location.getWorld() == null) {
            notify(player, settings, "messages.summon.failed");
            return true;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(HorseSummonKeys.BOUND_HORSE_UUID, PersistentDataType.STRING, horse.getUniqueId().toString());
        data.set(HorseSummonKeys.BOUND_OWNER_UUID, PersistentDataType.STRING, player.getUniqueId().toString());
        data.set(HorseSummonKeys.BOUND_HORSE_NAME, PersistentDataType.STRING, horseName);
        data.set(HorseSummonKeys.LAST_WORLD, PersistentDataType.STRING, location.getWorld().getUID().toString());
        data.set(HorseSummonKeys.LAST_X, PersistentDataType.DOUBLE, location.getX());
        data.set(HorseSummonKeys.LAST_Y, PersistentDataType.DOUBLE, location.getY());
        data.set(HorseSummonKeys.LAST_Z, PersistentDataType.DOUBLE, location.getZ());

        meta.setDisplayName(lang.getFormattedRaw(player, "messages.summon.horn-name", "%horse%", horseName));
        meta.setLore(List.of(
                lang.getFormattedRaw(player, "messages.summon.horn-lore-owner", "%player%", player.getName()),
                lang.getFormattedRaw(player, "messages.summon.horn-lore-use", "%horse%", horseName)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        horn.setItemMeta(meta);

        horse.getPersistentDataContainer().set(HorseSummonKeys.REGISTERED_HORSE, PersistentDataType.BYTE, (byte) 1);
        saveRegisteredHorseAsync(horse, player.getUniqueId(), horseName);

        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, horse.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.35D, 0.35D, 0.35D, 0.01D);
        lang.sendFormatted(player, "messages.summon.bound", "%horse%", horseName);
        plugin.debugLog("HORSE_SUMMON", "BIND", true, "Bound horn of " + player.getName() + " to horse " + horse.getUniqueId() + ".");
        return true;
    }

    public void callBoundHorse(Player player, ItemStack horn, HorseSummonSettings settings) {
        if (!settings.enabled()) {
            return;
        }
        if (player.hasCooldown(settings.hornMaterial())) {
            return;
        }

        Optional<BoundHornData> maybeData = readBoundData(horn);
        if (maybeData.isEmpty()) {
            notifyAndThrottle(player, settings, "messages.summon.unbound-horn");
            return;
        }

        BoundHornData data = maybeData.get();
        if (settings.requireOwner() && !data.ownerUuid().equals(player.getUniqueId())) {
            notifyAndThrottle(player, settings, "messages.summon.not-horn-owner");
            return;
        }

        if (settings.isWorldDisabled(player.getWorld().getName())) {
            notifyAndThrottle(player, settings, "messages.summon.disabled-world", "%world%", player.getWorld().getName());
            return;
        }

        long now = System.currentTimeMillis();
        long availableAt = cooldownUntilMillis.getOrDefault(player.getUniqueId(), 0L);
        if (availableAt > now) {
            long remainingSeconds = Math.max(1L, (availableAt - now + 999L) / 1000L);
            sendCooldown(player, settings, remainingSeconds);
            applyHornItemCooldown(player, settings, (int) Math.min(Integer.MAX_VALUE, remainingSeconds * 20L));
            return;
        }

        long activeUntil = activeCallsUntilMillis.getOrDefault(player.getUniqueId(), 0L);
        if (activeUntil > now) {
            notifyAndThrottle(player, settings, "messages.summon.already-searching");
            return;
        }
        activeCallsUntilMillis.put(player.getUniqueId(), now + 3000L);
        applyHornItemCooldown(player, settings, Math.max(5, settings.failedUseCooldownTicks()));

        playHornFeedback(player);
        notify(player, settings, "messages.summon.searching");

        findHorse(data, settings).thenAccept(maybeHorse -> Bukkit.getScheduler().runTask(plugin, () -> {
            activeCallsUntilMillis.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (maybeHorse.isEmpty()) {
                notify(player, settings, "messages.summon.not-found");
                return;
            }
            summonHorse(player, maybeHorse.get(), horn, settings);
        })).exceptionally(throwable -> {
            activeCallsUntilMillis.remove(player.getUniqueId());
            plugin.getLogger().log(Level.WARNING, "Failed to call summon horse.", throwable);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    notify(player, settings, "messages.summon.failed");
                }
            });
            return null;
        });
    }

    public void updateRegisteredHorseLocationAsync(AbstractHorse horse) {
        if (!isRegisteredSummonHorse(horse)) {
            return;
        }
        Location location = horse.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        repository.updateLocationAsync(
                horse.getUniqueId(),
                world.getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public void deleteRegisteredHorseAsync(AbstractHorse horse) {
        if (!isRegisteredSummonHorse(horse)) {
            return;
        }
        repository.deleteAsync(horse.getUniqueId());
    }

    private CompletableFuture<Optional<AbstractHorse>> findHorse(BoundHornData hornData, HorseSummonSettings settings) {
        Entity loadedEntity = Bukkit.getEntity(hornData.horseUuid());
        if (loadedEntity instanceof AbstractHorse loadedHorse) {
            updateRegisteredHorseLocationAsync(loadedHorse);
            return CompletableFuture.completedFuture(Optional.of(loadedHorse));
        }

        if (!settings.loadUnloadedChunk()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return repository.findByHorseUuidAsync(hornData.horseUuid())
                .thenCompose(maybeRegistered -> {
                    RegisteredSummonHorse registered = maybeRegistered.orElseGet(() -> fromHornData(hornData));
                    return loadHorseChunkAndResolve(registered);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to resolve summon horse from SQLite.", throwable);
                    return Optional.empty();
                });
    }

    private CompletableFuture<Optional<AbstractHorse>> loadHorseChunkAndResolve(RegisteredSummonHorse registered) {
        World world = Bukkit.getWorld(registered.worldUuid());
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        int chunkX = ((int) Math.floor(registered.x())) >> 4;
        int chunkZ = ((int) Math.floor(registered.z())) >> 4;

        CompletableFuture<Optional<AbstractHorse>> result = new CompletableFuture<>();

        // Datapack does the same idea with temporary forceload: first load the chunk,
        // then resolve the exact entity by UUID. Bukkit.getEntity(UUID) is not reliable
        // immediately after loading a previously-unloaded chunk, so we also inspect
        // Chunk#getEntities() on the main thread.
        world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null || chunk == null) {
                        plugin.getLogger().warning("Failed to load summon horse chunk: " + (throwable == null ? "chunk is null" : throwable.getMessage()));
                        result.complete(Optional.empty());
                        return;
                    }

                    Optional<AbstractHorse> resolved = resolveLoadedHorseByUuid(registered.horseUuid(), chunk);
                    if (resolved.isPresent()) {
                        result.complete(resolved);
                        return;
                    }

                    result.complete(Optional.empty());
                })
        );
        return result;
    }

    private Optional<AbstractHorse> resolveLoadedHorseByUuid(UUID horseUuid, Chunk chunk) {
        Entity bukkitEntity = Bukkit.getEntity(horseUuid);
        if (bukkitEntity instanceof AbstractHorse horse && horse.isValid()) {
            return Optional.of(horse);
        }

        for (Entity entity : chunk.getEntities()) {
            if (entity.getUniqueId().equals(horseUuid) && entity instanceof AbstractHorse horse && horse.isValid()) {
                return Optional.of(horse);
            }
        }
        return Optional.empty();
    }

    private void summonHorse(Player player, AbstractHorse horse, ItemStack horn, HorseSummonSettings settings) {
        if (settings.preventCallWithPlayerPassenger() && hasPlayerPassenger(horse)) {
            notifyAndThrottle(player, settings, "messages.summon.has-passenger");
            return;
        }
        if (settings.requireOwner() && !isOwner(player, horse)) {
            notifyAndThrottle(player, settings, "messages.summon.not-owner");
            return;
        }

        Location target = findSafeSummonLocation(player, settings)
                .orElseGet(() -> player.getLocation().clone());

        Location from = horse.getLocation().clone();
        horse.eject();
        boolean teleported = horse.teleport(target);
        if (!teleported) {
            notify(player, settings, "messages.summon.failed");
            return;
        }

        TrainingManager.recalculateAndApplyBonuses(horse);
        updateLastKnownLocation(horn, horse.getLocation());
        updateRegisteredHorseLocationAsync(horse);
        applyCooldown(player, settings);

        from.getWorld().spawnParticle(Particle.CLOUD, from.add(0.0D, 0.6D, 0.0D), 16, 0.45D, 0.35D, 0.45D, 0.02D);
        target.getWorld().spawnParticle(Particle.CLOUD, target.clone().add(0.0D, 0.6D, 0.0D), 24, 0.55D, 0.35D, 0.55D, 0.02D);
        target.getWorld().playSound(target, "entity.horse.gallop", SoundCategory.PLAYERS, 0.9F, 1.05F);

        if (settings.autoMount()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && horse.isValid() && horse.getPassengers().isEmpty()) {
                    horse.addPassenger(player);
                }
            }, 2L);
        }

        notify(player, settings, "messages.summon.success", "%horse%", resolveHorseName(horse));
        plugin.debugLog("HORSE_SUMMON", "CALL", true, "Player " + player.getName() + " called horse " + horse.getUniqueId() + ".");
    }

    private void saveRegisteredHorseAsync(AbstractHorse horse, UUID ownerUuid, String horseName) {
        Location location = horse.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        repository.saveAsync(new RegisteredSummonHorse(
                horse.getUniqueId(),
                ownerUuid,
                horseName,
                world.getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        ));
    }

    private RegisteredSummonHorse fromHornData(BoundHornData data) {
        return new RegisteredSummonHorse(
                data.horseUuid(),
                data.ownerUuid(),
                data.horseName(),
                data.lastWorldUuid(),
                data.lastX(),
                data.lastY(),
                data.lastZ()
        );
    }

    private void applyCooldown(Player player, HorseSummonSettings settings) {
        if (settings.cooldownSeconds() <= 0) {
            return;
        }
        cooldownUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + settings.cooldownSeconds() * 1000L);
        applyHornItemCooldown(player, settings, settings.cooldownSeconds() * 20);
    }

    private void sendCooldown(Player player, HorseSummonSettings settings, long remainingSeconds) {
        notify(player, settings, "messages.summon.cooldown", "%seconds%", remainingSeconds);
    }

    private void notifyAndThrottle(Player player, HorseSummonSettings settings, String key, Object... replacements) {
        notify(player, settings, key, replacements);
        applyHornItemCooldown(player, settings, settings.failedUseCooldownTicks());
    }

    private void notify(Player player, HorseSummonSettings settings, String key, Object... replacements) {
        HorseSummonNotificationMode mode = settings.notificationMode();
        if (mode.sendsChat()) {
            lang.sendFormatted(player, key, replacements);
        }
        if (mode.sendsActionBar()) {
            Component component = lang.getFormattedRawComponent(player, key, replacements);
            player.sendActionBar(component);
        }
    }

    private void applyHornItemCooldown(Player player, HorseSummonSettings settings, int ticks) {
        if (ticks <= 0) {
            return;
        }
        player.setCooldown(settings.hornMaterial(), ticks);
    }

    private void updateLastKnownLocation(ItemStack horn, Location location) {
        ItemMeta meta = horn.getItemMeta();
        if (meta == null || location.getWorld() == null) {
            return;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(HorseSummonKeys.LAST_WORLD, PersistentDataType.STRING, location.getWorld().getUID().toString());
        data.set(HorseSummonKeys.LAST_X, PersistentDataType.DOUBLE, location.getX());
        data.set(HorseSummonKeys.LAST_Y, PersistentDataType.DOUBLE, location.getY());
        data.set(HorseSummonKeys.LAST_Z, PersistentDataType.DOUBLE, location.getZ());
        horn.setItemMeta(meta);
    }

    private Optional<BoundHornData> readBoundData(ItemStack horn) {
        if (horn == null || !horn.hasItemMeta()) {
            return Optional.empty();
        }

        PersistentDataContainer data = horn.getItemMeta().getPersistentDataContainer();
        String horseUuidRaw = data.get(HorseSummonKeys.BOUND_HORSE_UUID, PersistentDataType.STRING);
        String ownerUuidRaw = data.get(HorseSummonKeys.BOUND_OWNER_UUID, PersistentDataType.STRING);
        String worldUuidRaw = data.get(HorseSummonKeys.LAST_WORLD, PersistentDataType.STRING);
        Double x = data.get(HorseSummonKeys.LAST_X, PersistentDataType.DOUBLE);
        Double y = data.get(HorseSummonKeys.LAST_Y, PersistentDataType.DOUBLE);
        Double z = data.get(HorseSummonKeys.LAST_Z, PersistentDataType.DOUBLE);

        if (horseUuidRaw == null || ownerUuidRaw == null || worldUuidRaw == null || x == null || y == null || z == null) {
            return Optional.empty();
        }

        try {
            String horseName = data.getOrDefault(HorseSummonKeys.BOUND_HORSE_NAME, PersistentDataType.STRING, lang.getRaw("messages.horse"));
            return Optional.of(new BoundHornData(
                    UUID.fromString(horseUuidRaw),
                    UUID.fromString(ownerUuidRaw),
                    horseName,
                    UUID.fromString(worldUuidRaw),
                    x,
                    y,
                    z
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Location> findSafeSummonLocation(Player player, HorseSummonSettings settings) {
        Location base = player.getLocation().clone();
        Vector backward = base.getDirection().setY(0.0D);
        if (backward.lengthSquared() < 0.0001D) {
            backward = new Vector(0.0D, 0.0D, 1.0D);
        }
        backward.normalize().multiply(-settings.summonDistanceBehind());

        List<Location> candidates = new ArrayList<>();
        Location preferred = base.clone().add(backward);
        candidates.add(preferred);

        Vector side = new Vector(-backward.getZ(), 0.0D, backward.getX()).normalize();
        for (int offset = 1; offset <= settings.summonSideSearchRadius(); offset++) {
            candidates.add(preferred.clone().add(side.clone().multiply(offset)));
            candidates.add(preferred.clone().add(side.clone().multiply(-offset)));
            candidates.add(base.clone().add(backward.clone().normalize().multiply(-(settings.summonDistanceBehind() + offset))));
        }

        return candidates.stream()
                .map(this::snapToSafeGround)
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(location -> location.distanceSquared(preferred)));
    }

    private Optional<Location> snapToSafeGround(Location candidate) {
        World world = candidate.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        int x = candidate.getBlockX();
        int z = candidate.getBlockZ();
        int startY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, candidate.getBlockY() + 2));
        int minY = Math.max(world.getMinHeight() + 1, candidate.getBlockY() - 4);

        for (int y = startY; y >= minY; y--) {
            Location location = new Location(world, x + 0.5D, y, z + 0.5D, candidate.getYaw(), candidate.getPitch());
            if (isSafeForHorse(location)) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    private boolean isSafeForHorse(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && ground.getType().isSolid();
    }

    private boolean isOwner(Player player, AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        String storedOwner = data.get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (storedOwner != null) {
            return storedOwner.equals(player.getUniqueId().toString());
        }
        AnimalTamer owner = horse.getOwner();
        return horse.isTamed() && owner != null && owner.getUniqueId().equals(player.getUniqueId());
    }

    private boolean hasPlayerPassenger(AbstractHorse horse) {
        return horse.getPassengers().stream().anyMatch(Player.class::isInstance);
    }

    private boolean isRegisteredSummonHorse(AbstractHorse horse) {
        return horse.getPersistentDataContainer().has(HorseSummonKeys.REGISTERED_HORSE, PersistentDataType.BYTE);
    }

    private String resolveHorseName(AbstractHorse horse) {
        if (horse.getCustomName() != null && !horse.getCustomName().isBlank()) {
            return horse.getCustomName();
        }
        return SupportedMountType.fromEntity(horse)
                .map(type -> type.getDisplayName(lang))
                .orElse(lang.getRaw("messages.horse"));
    }

    private void playHornFeedback(Player player) {
        player.getWorld().playSound(player.getLocation(), "item.goat_horn.sound.0", SoundCategory.PLAYERS, 1.0F, 1.0F);
    }
}

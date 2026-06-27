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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;
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
    /**
     * Prevents notification spam while the player keeps holding the horn use button.
     * This does not apply a vanilla item cooldown, so the goat horn sound remains usable.
     */
    private final ConcurrentMap<UUID, Long> nextNotificationMillis = new ConcurrentHashMap<>();
    private final Set<UUID> pendingHorseBindings = ConcurrentHashMap.newKeySet();

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
        if (isBound(horn) || hasHornIdentity(horn)) {
            notifyAndThrottle(player, settings, "messages.summon.already-bound-horn");
            return true;
        }
        if (settings.requireOwner() && !isOwner(player, horse)) {
            notifyAndThrottle(player, settings, "messages.summon.not-owner");
            return true;
        }

        ItemMeta meta = horn.getItemMeta();
        if (meta == null) {
            notifyAndThrottle(player, settings, "messages.summon.invalid-horn");
            return true;
        }

        Location location = horse.getLocation();
        World world = location.getWorld();
        if (world == null) {
            notify(player, settings, "messages.summon.failed");
            return true;
        }

        UUID horseUuid = horse.getUniqueId();
        if (!pendingHorseBindings.add(horseUuid)) {
            notifyAndThrottle(player, settings, "messages.summon.binding-in-progress");
            return true;
        }

        String horseName = resolveHorseName(horse);
        UUID hornUuid = UUID.randomUUID();
        int maxUses = resolveHornUses(player, settings);
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack originalHorn = horn.clone();

        RegisteredSummonHorse registration = new RegisteredSummonHorse(
                horseUuid,
                player.getUniqueId(),
                hornUuid,
                maxUses,
                horseName,
                world.getUID(),
                location.getX(),
                location.getY(),
                location.getZ()
        );

        repository.replaceActiveHornAsync(registration).whenComplete((previousRegistration, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingHorseBindings.remove(horseUuid);

                    if (throwable != null) {
                        plugin.getLogger().log(Level.WARNING, "Failed to register summon horn.", throwable);
                        if (player.isOnline()) {
                            notify(player, settings, "messages.summon.failed");
                        }
                        return;
                    }

                    if (!player.isOnline() || !horse.isValid() || (settings.requireOwner() && !isOwner(player, horse))) {
                        repository.restorePreviousIfCurrentMatchesAsync(horseUuid, hornUuid, previousRegistration);
                        return;
                    }

                    ItemStack currentHorn = player.getInventory().getItem(heldSlot);
                    if (!isSameUnboundHorn(currentHorn, originalHorn, settings)) {
                        repository.restorePreviousIfCurrentMatchesAsync(horseUuid, hornUuid, previousRegistration);
                        notifyAndThrottle(player, settings, "messages.summon.binding-cancelled");
                        return;
                    }

                    applyBoundHornData(player, horse, currentHorn, hornUuid, maxUses, horseName);
                    horse.getPersistentDataContainer().set(HorseSummonKeys.REGISTERED_HORSE, PersistentDataType.BYTE, (byte) 1);

                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, horse.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.35D, 0.35D, 0.35D, 0.01D);
                    String messageKey = previousRegistration.isPresent()
                            ? "messages.summon.rebound"
                            : "messages.summon.bound";
                    lang.sendFormatted(player, messageKey, "%horse%", horseName);
                    plugin.debugLog("HORSE_SUMMON", "BIND", true,
                            "Bound horn " + hornUuid + " of " + player.getName() + " to horse " + horseUuid
                                    + (previousRegistration.isPresent() ? ", replacing the previous active horn." : "."));
                })
        );
        return true;
    }

    private void applyBoundHornData(
            Player player,
            AbstractHorse horse,
            ItemStack horn,
            UUID hornUuid,
            int maxUses,
            String horseName
    ) {
        ItemMeta meta = horn.getItemMeta();
        if (meta == null) {
            return;
        }

        Location location = horse.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(HorseSummonKeys.BOUND_HORSE_UUID, PersistentDataType.STRING, horse.getUniqueId().toString());
        data.set(HorseSummonKeys.BOUND_OWNER_UUID, PersistentDataType.STRING, player.getUniqueId().toString());
        data.set(HorseSummonKeys.HORN_UUID, PersistentDataType.STRING, hornUuid.toString());
        data.set(HorseSummonKeys.USES_REMAINING, PersistentDataType.INTEGER, maxUses);
        data.set(HorseSummonKeys.BOUND_HORSE_NAME, PersistentDataType.STRING, horseName);
        data.set(HorseSummonKeys.LAST_WORLD, PersistentDataType.STRING, world.getUID().toString());
        data.set(HorseSummonKeys.LAST_X, PersistentDataType.DOUBLE, location.getX());
        data.set(HorseSummonKeys.LAST_Y, PersistentDataType.DOUBLE, location.getY());
        data.set(HorseSummonKeys.LAST_Z, PersistentDataType.DOUBLE, location.getZ());

        meta.setDisplayName(lang.getFormattedRaw(player, "messages.summon.horn-name", "%horse%", horseName));
        meta.setLore(buildHornLore(player, horseName, maxUses));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        horn.setItemMeta(meta);
    }

    private boolean isSameUnboundHorn(ItemStack currentHorn, ItemStack originalHorn, HorseSummonSettings settings) {
        return currentHorn != null
                && currentHorn.getAmount() == originalHorn.getAmount()
                && currentHorn.isSimilar(originalHorn)
                && isSummonHornCandidate(currentHorn, settings)
                && !isBound(currentHorn)
                && !hasHornIdentity(currentHorn);
    }

    private boolean hasHornIdentity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(HorseSummonKeys.HORN_UUID, PersistentDataType.STRING);
    }

    public void callBoundHorse(Player player, ItemStack horn, HorseSummonSettings settings) {
        if (!settings.enabled()) {
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

        if (data.usesRemaining() <= 0) {
            notifyAndThrottle(player, settings, "messages.summon.no-uses");
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
            return;
        }

        long activeUntil = activeCallsUntilMillis.getOrDefault(player.getUniqueId(), 0L);
        if (activeUntil > now) {
            notifyAndThrottle(player, settings, "messages.summon.already-searching");
            return;
        }
        activeCallsUntilMillis.put(player.getUniqueId(), now + 3000L);

        playHornFeedback(player);
        notify(player, settings, "messages.summon.searching");

        findHorse(data, settings).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            activeCallsUntilMillis.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (result.inactiveHorn()) {
                notify(player, settings, "messages.summon.inactive-horn");
                return;
            }
            if (result.horse().isEmpty()) {
                consumeUse(player, horn, data, settings);
                notify(player, settings, "messages.summon.not-found");
                return;
            }
            summonHorse(player, result.horse().get(), horn, data, settings);
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

    public void clearPlayerState(UUID playerUuid) {
        cooldownUntilMillis.remove(playerUuid);
        activeCallsUntilMillis.remove(playerUuid);
        nextNotificationMillis.remove(playerUuid);
    }

    /**
     * Refreshes last known locations for all loaded summon-registered horses.
     *
     * <p>This method must be called on the Bukkit main thread because it reads
     * worlds and entities. SQLite writes are still delegated to the repository
     * executor by {@link #updateRegisteredHorseLocationAsync(AbstractHorse)}.</p>
     */
    public void refreshLoadedRegisteredHorseLocations() {
        for (World world : Bukkit.getWorlds()) {
            for (AbstractHorse horse : world.getEntitiesByClass(AbstractHorse.class)) {
                updateRegisteredHorseLocationAsync(horse);
            }
        }
    }

    private CompletableFuture<SummonLookupResult> findHorse(BoundHornData hornData, HorseSummonSettings settings) {
        return repository.findByHorseUuidAsync(hornData.horseUuid())
                .thenCompose(maybeRegistered -> resolveHorseOnMainThread(hornData, maybeRegistered, settings))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to resolve summon horse from SQLite.", throwable);
                    return SummonLookupResult.notFound();
                });
    }

    /**
     * Switches from the SQLite worker back to the Bukkit main thread before touching
     * worlds, chunks or entities.
     */
    private CompletableFuture<SummonLookupResult> resolveHorseOnMainThread(
            BoundHornData hornData,
            Optional<RegisteredSummonHorse> maybeRegistered,
            HorseSummonSettings settings
    ) {
        CompletableFuture<SummonLookupResult> result = new CompletableFuture<>();

        Runnable resolver = () -> {
            try {
                if (maybeRegistered.isEmpty()) {
                    result.complete(SummonLookupResult.inactive());
                    return;
                }

                RegisteredSummonHorse registered = maybeRegistered.get();
                if (registered.hornUuid() == null || !registered.hornUuid().equals(hornData.hornUuid())) {
                    result.complete(SummonLookupResult.inactive());
                    return;
                }

                Entity loadedEntity = Bukkit.getEntity(hornData.horseUuid());
                if (loadedEntity instanceof AbstractHorse loadedHorse && loadedHorse.isValid()) {
                    updateRegisteredHorseLocationAsync(loadedHorse);
                    result.complete(SummonLookupResult.found(loadedHorse));
                    return;
                }

                if (!settings.loadUnloadedChunk()) {
                    result.complete(SummonLookupResult.notFound());
                    return;
                }

                loadHorseChunkAndResolve(registered, settings).whenComplete((horse, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                        return;
                    }
                    result.complete(SummonLookupResult.fromHorse(horse));
                });
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            resolver.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, resolver);
        }
        return result;
    }

    private CompletableFuture<Optional<AbstractHorse>> loadHorseChunkAndResolve(RegisteredSummonHorse registered, HorseSummonSettings settings) {
        World world = Bukkit.getWorld(registered.worldUuid());
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        int originChunkX = ((int) Math.floor(registered.x())) >> 4;
        int originChunkZ = ((int) Math.floor(registered.z())) >> 4;
        int radius = settings.unloadedSearchRadiusChunks();
        List<int[]> chunksToCheck = buildChunkSearchOrder(originChunkX, originChunkZ, radius);
        return loadChunksAndResolveSequentially(world, registered.horseUuid(), chunksToCheck, 0);
    }

    private CompletableFuture<Optional<AbstractHorse>> loadChunksAndResolveSequentially(World world, UUID horseUuid, List<int[]> chunksToCheck, int index) {
        if (index >= chunksToCheck.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        int[] chunkCoordinates = chunksToCheck.get(index);
        int chunkX = chunkCoordinates[0];
        int chunkZ = chunkCoordinates[1];

        CompletableFuture<Optional<AbstractHorse>> result = new CompletableFuture<>();

        // Datapack does the same idea with temporary forceload: first load the chunk,
        // then resolve the exact entity by UUID. Bukkit.getEntity(UUID) is not reliable
        // immediately after loading a previously-unloaded chunk, so we also inspect
        // Chunk#getEntities() on the main thread.
        world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null || chunk == null) {
                        plugin.getLogger().warning("Failed to load summon horse chunk: " + (throwable == null ? "chunk is null" : throwable.getMessage()));
                        loadChunksAndResolveSequentially(world, horseUuid, chunksToCheck, index + 1)
                                .whenComplete((resolved, nestedThrowable) -> completeResolvedHorse(result, resolved, nestedThrowable));
                        return;
                    }

                    Optional<AbstractHorse> resolved = resolveLoadedHorseByUuid(horseUuid, chunk);
                    if (resolved.isPresent()) {
                        updateRegisteredHorseLocationAsync(resolved.get());
                        result.complete(resolved);
                        return;
                    }

                    loadChunksAndResolveSequentially(world, horseUuid, chunksToCheck, index + 1)
                            .whenComplete((nestedResolved, nestedThrowable) -> completeResolvedHorse(result, nestedResolved, nestedThrowable));
                })
        );
        return result;
    }

    private void completeResolvedHorse(CompletableFuture<Optional<AbstractHorse>> result, Optional<AbstractHorse> resolved, Throwable throwable) {
        if (throwable != null) {
            result.completeExceptionally(throwable);
            return;
        }
        result.complete(resolved == null ? Optional.empty() : resolved);
    }

    private List<int[]> buildChunkSearchOrder(int originChunkX, int originChunkZ, int radius) {
        List<int[]> chunks = new ArrayList<>();
        chunks.add(new int[]{originChunkX, originChunkZ});

        for (int currentRadius = 1; currentRadius <= radius; currentRadius++) {
            for (int dx = -currentRadius; dx <= currentRadius; dx++) {
                for (int dz = -currentRadius; dz <= currentRadius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != currentRadius) {
                        continue;
                    }
                    chunks.add(new int[]{originChunkX + dx, originChunkZ + dz});
                }
            }
        }
        return chunks;
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

    private void summonHorse(Player player, AbstractHorse horse, ItemStack horn, BoundHornData data, HorseSummonSettings settings) {
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
        consumeUse(player, horn, data, settings);
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

    private void saveRegisteredHorseAsync(AbstractHorse horse, UUID ownerUuid, UUID hornUuid, int usesRemaining, String horseName) {
        Location location = horse.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        repository.saveAsync(new RegisteredSummonHorse(
                horse.getUniqueId(),
                ownerUuid,
                hornUuid,
                usesRemaining,
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
                data.hornUuid(),
                data.usesRemaining(),
                data.horseName(),
                data.lastWorldUuid(),
                data.lastX(),
                data.lastY(),
                data.lastZ()
        );
    }

    private void applyCooldown(Player player, HorseSummonSettings settings) {
        int cooldownSeconds = resolveCooldownSeconds(player, settings);
        if (cooldownSeconds <= 0) {
            return;
        }
        cooldownUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    /**
     * Resolves the player's summon cooldown. The default value comes from config.yml.
     * A permission in the format betterhorses.summoncooldown.<seconds> overrides it.
     * If the player has multiple such permissions, the lowest value is used because it is the most beneficial.
     */
    private int resolveCooldownSeconds(Player player, HorseSummonSettings settings) {
        int resolved = settings.cooldownSeconds();
        final String prefix = "betterhorses.summoncooldown.";

        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }

            String permission = permissionInfo.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }

            String rawSeconds = permission.substring(prefix.length());
            try {
                int seconds = Integer.parseInt(rawSeconds);
                if (seconds < 0) {
                    continue;
                }
                resolved = Math.min(resolved, seconds);
            } catch (NumberFormatException ignored) {
                // Ignore malformed permissions like betterhorses.summoncooldown.vip.
            }
        }

        return resolved;
    }

    /**
     * Resolves maximum uses for a newly-bound horn.
     * A permission in the format betterhorses.summonuses.<amount> overrides the config value.
     * If the player has multiple such permissions, the highest value is used.
     */
    private int resolveHornUses(Player player, HorseSummonSettings settings) {
        int resolved = settings.defaultHornUses();
        final String prefix = "betterhorses.summonuses.";

        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }

            String permission = permissionInfo.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }

            String rawUses = permission.substring(prefix.length());
            try {
                int uses = Integer.parseInt(rawUses);
                if (uses <= 0) {
                    continue;
                }
                resolved = Math.max(resolved, uses);
            } catch (NumberFormatException ignored) {
                // Ignore malformed permissions like betterhorses.summonuses.vip.
            }
        }

        return Math.max(1, resolved);
    }

    private List<String> buildHornLore(Player player, String horseName, int usesRemaining) {
        return List.of(
                lang.getFormattedRaw(player, "messages.summon.horn-lore-owner", "%player%", player.getName()),
                lang.getFormattedRaw(player, "messages.summon.horn-lore-use", "%horse%", horseName),
                lang.getFormattedRaw(player, "messages.summon.horn-lore-uses", "%uses%", usesRemaining)
        );
    }

    private void consumeUse(Player player, ItemStack horn, BoundHornData data, HorseSummonSettings settings) {
        int remaining = Math.max(0, data.usesRemaining() - 1);

        if (remaining > 0) {
            updateHornUses(player, horn, data.hornUuid(), data.horseName(), remaining);
            repository.updateUsesAsync(data.horseUuid(), data.hornUuid(), remaining);
            return;
        }

        // The final charge destroys the exact horn that was used. The lookup is based
        // on the horn UUID, so moving it to another inventory slot during an async
        // horse lookup cannot preserve or duplicate the exhausted horn.
        removeHornFromInventory(player, data.hornUuid());
        repository.deleteIfHornMatchesAsync(data.horseUuid(), data.hornUuid());
        clearLoadedHorseRegistration(data.horseUuid());
        notify(player, settings, "messages.summon.uses-depleted", "%horse%", data.horseName());
    }

    private void updateHornUses(
            Player player,
            ItemStack originalHorn,
            UUID hornUuid,
            String horseName,
            int usesRemaining
    ) {
        ItemStack horn = findHornInInventory(player, hornUuid).orElse(originalHorn);
        if (!hasHornUuid(horn, hornUuid)) {
            return;
        }

        ItemMeta meta = horn.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(HorseSummonKeys.USES_REMAINING, PersistentDataType.INTEGER, usesRemaining);
        meta.setLore(buildHornLore(player, horseName, usesRemaining));
        horn.setItemMeta(meta);
    }

    /**
     * Removes one exact bound horn from the player's hands or storage inventory.
     * Armor slots are intentionally ignored.
     */
    private boolean removeHornFromInventory(Player player, UUID hornUuid) {
        PlayerInventory inventory = player.getInventory();

        ItemStack mainHand = inventory.getItemInMainHand();
        if (hasHornUuid(mainHand, hornUuid)) {
            inventory.setItemInMainHand(new ItemStack(Material.AIR));
            return true;
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (hasHornUuid(offHand, hornUuid)) {
            inventory.setItemInOffHand(new ItemStack(Material.AIR));
            return true;
        }

        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!hasHornUuid(item, hornUuid)) {
                continue;
            }
            inventory.setItem(slot, new ItemStack(Material.AIR));
            return true;
        }
        return false;
    }

    private Optional<ItemStack> findHornInInventory(Player player, UUID hornUuid) {
        PlayerInventory inventory = player.getInventory();

        ItemStack mainHand = inventory.getItemInMainHand();
        if (hasHornUuid(mainHand, hornUuid)) {
            return Optional.of(mainHand);
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (hasHornUuid(offHand, hornUuid)) {
            return Optional.of(offHand);
        }

        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (hasHornUuid(item, hornUuid)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private boolean hasHornUuid(ItemStack item, UUID hornUuid) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        String storedUuid = item.getItemMeta().getPersistentDataContainer()
                .get(HorseSummonKeys.HORN_UUID, PersistentDataType.STRING);
        return hornUuid.toString().equals(storedUuid);
    }

    private void clearLoadedHorseRegistration(UUID horseUuid) {
        Entity entity = Bukkit.getEntity(horseUuid);
        if (!(entity instanceof AbstractHorse horse)) {
            return;
        }
        horse.getPersistentDataContainer().remove(HorseSummonKeys.REGISTERED_HORSE);
    }

    private void sendCooldown(Player player, HorseSummonSettings settings, long remainingSeconds) {
        notifyAndThrottle(player, settings, "messages.summon.cooldown", "%seconds%", remainingSeconds);
    }

    private void notifyAndThrottle(Player player, HorseSummonSettings settings, String key, Object... replacements) {
        long now = System.currentTimeMillis();
        long allowedAt = nextNotificationMillis.getOrDefault(player.getUniqueId(), 0L);
        if (allowedAt > now) {
            return;
        }

        long throttleMillis = Math.max(0, settings.failedUseCooldownTicks()) * 50L;
        if (throttleMillis > 0) {
            nextNotificationMillis.put(player.getUniqueId(), now + throttleMillis);
        }
        notify(player, settings, key, replacements);
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
        String hornUuidRaw = data.get(HorseSummonKeys.HORN_UUID, PersistentDataType.STRING);
        Integer usesRemaining = data.get(HorseSummonKeys.USES_REMAINING, PersistentDataType.INTEGER);
        String worldUuidRaw = data.get(HorseSummonKeys.LAST_WORLD, PersistentDataType.STRING);
        Double x = data.get(HorseSummonKeys.LAST_X, PersistentDataType.DOUBLE);
        Double y = data.get(HorseSummonKeys.LAST_Y, PersistentDataType.DOUBLE);
        Double z = data.get(HorseSummonKeys.LAST_Z, PersistentDataType.DOUBLE);

        if (horseUuidRaw == null || ownerUuidRaw == null || hornUuidRaw == null || usesRemaining == null || worldUuidRaw == null || x == null || y == null || z == null) {
            return Optional.empty();
        }

        try {
            String horseName = data.getOrDefault(HorseSummonKeys.BOUND_HORSE_NAME, PersistentDataType.STRING, lang.getRaw("messages.horse"));
            return Optional.of(new BoundHornData(
                    UUID.fromString(horseUuidRaw),
                    UUID.fromString(ownerUuidRaw),
                    UUID.fromString(hornUuidRaw),
                    horseName,
                    usesRemaining,
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


    private record SummonLookupResult(Optional<AbstractHorse> horse, boolean inactiveHorn) {
        private static SummonLookupResult found(AbstractHorse horse) {
            return new SummonLookupResult(Optional.of(horse), false);
        }

        private static SummonLookupResult notFound() {
            return new SummonLookupResult(Optional.empty(), false);
        }

        private static SummonLookupResult inactive() {
            return new SummonLookupResult(Optional.empty(), true);
        }

        private static SummonLookupResult fromHorse(Optional<AbstractHorse> horse) {
            return new SummonLookupResult(horse, false);
        }
    }
}

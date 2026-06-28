package me.luisgamedev.betterhorses.upgrades;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight two-rider implementation inspired by DualHorse. The second rider
 * sits on an invisible armor stand that is repositioned behind the horse every tick.
 */
public final class TandemRideManager {

    private static final double DEFAULT_SEAT_HEIGHT = 1.15D;
    private static final double LEGACY_DEFAULT_HEIGHT = 0.45D;
    private static final double MAX_PREDICTION_TICKS = 2.0D;

    private final BetterHorses plugin;
    private final NamespacedKey standMarker;
    private final Map<UUID, RideLink> ridesByHorse = new HashMap<>();
    private final Map<UUID, UUID> horseByPassenger = new HashMap<>();
    private BukkitTask updateTask;

    public TandemRideManager(BetterHorses plugin) {
        this.plugin = plugin;
        this.standMarker = new NamespacedKey(plugin, "tandem_passenger_seat");
    }

    public void start() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (UUID horseUuid : ridesByHorse.keySet().toArray(UUID[]::new)) {
            removeByHorse(horseUuid);
        }
    }

    public boolean tryMount(Player passenger, AbstractHorse horse) {
        if (HorseUpgradeEffects.effect(
                plugin.getConfig(),
                horse.getPersistentDataContainer(),
                HorseUpgradeEffects.TANDEM_SEAT
        ) <= 0.0D) {
            return false;
        }
        if (ridesByHorse.containsKey(horse.getUniqueId())) {
            plugin.getLang().send(passenger, "messages.upgrades.tandem-occupied");
            return true;
        }
        if (horse.getPassengers().stream().noneMatch(Player.class::isInstance)) {
            return false;
        }
        if (passenger.isInsideVehicle()) {
            return true;
        }
        if (HorseUpgradeEffects.effect(
                plugin.getConfig(),
                horse.getPersistentDataContainer(),
                HorseUpgradeEffects.LOYAL_MOUNT
        ) > 0.0D && !plugin.getHorseUpgradeService().isOwner(passenger, horse)) {
            plugin.getLang().send(passenger, "messages.upgrades.loyal-mount-denied");
            return true;
        }

        ArmorStand stand = horse.getWorld().spawn(passengerLocation(horse, false), ArmorStand.class, seat -> {
            seat.setVisible(false);
            seat.setGravity(false);
            seat.setSmall(true);
            seat.setBasePlate(false);
            seat.setInvulnerable(true);
            seat.setSilent(true);
            seat.setCollidable(false);
            seat.setPersistent(false);
            seat.getPersistentDataContainer().set(standMarker, PersistentDataType.BYTE, (byte) 1);
        });

        if (!stand.addPassenger(passenger)) {
            stand.remove();
            plugin.getLang().send(passenger, "messages.upgrades.tandem-failed");
            return true;
        }

        RideLink link = new RideLink(horse.getUniqueId(), stand.getUniqueId(), passenger.getUniqueId());
        ridesByHorse.put(horse.getUniqueId(), link);
        horseByPassenger.put(passenger.getUniqueId(), horse.getUniqueId());
        plugin.getLang().send(passenger, "messages.upgrades.tandem-mounted");
        return true;
    }

    public boolean hasPassenger(AbstractHorse horse) {
        RideLink link = ridesByHorse.get(horse.getUniqueId());
        return link != null && Bukkit.getEntity(link.passengerUuid()) instanceof Player;
    }

    public boolean isSeat(Entity entity) {
        return entity instanceof ArmorStand stand
                && stand.getPersistentDataContainer().has(standMarker, PersistentDataType.BYTE);
    }

    public boolean isTandemPassenger(Player player) {
        return horseByPassenger.containsKey(player.getUniqueId());
    }

    public AbstractHorse getHorseForPassenger(Player player) {
        UUID horseUuid = horseByPassenger.get(player.getUniqueId());
        if (horseUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(horseUuid);
        return entity instanceof AbstractHorse horse ? horse : null;
    }

    public void removeByPassenger(UUID passengerUuid) {
        UUID horseUuid = horseByPassenger.get(passengerUuid);
        if (horseUuid != null) {
            removeByHorse(horseUuid);
        }
    }

    public void removeByHorse(UUID horseUuid) {
        RideLink link = ridesByHorse.remove(horseUuid);
        if (link == null) {
            return;
        }
        horseByPassenger.remove(link.passengerUuid());
        Entity standEntity = Bukkit.getEntity(link.standUuid());
        if (standEntity != null) {
            standEntity.eject();
            standEntity.remove();
        }
    }

    private void tick() {
        for (RideLink link : ridesByHorse.values().toArray(RideLink[]::new)) {
            Entity horseEntity = Bukkit.getEntity(link.horseUuid());
            Entity standEntity = Bukkit.getEntity(link.standUuid());
            Entity passengerEntity = Bukkit.getEntity(link.passengerUuid());
            if (!(horseEntity instanceof AbstractHorse horse)
                    || !(standEntity instanceof ArmorStand stand)
                    || !(passengerEntity instanceof Player passenger)
                    || !horse.isValid() || !stand.isValid() || !passenger.isOnline()) {
                removeByHorse(link.horseUuid());
                continue;
            }
            if (HorseUpgradeEffects.effect(
                    plugin.getConfig(),
                    horse.getPersistentDataContainer(),
                    HorseUpgradeEffects.TANDEM_SEAT
            ) <= 0.0D || horse.getPassengers().stream().noneMatch(Player.class::isInstance)
                    || passenger.getVehicle() == null
                    || !passenger.getVehicle().getUniqueId().equals(stand.getUniqueId())) {
                removeByHorse(link.horseUuid());
                continue;
            }

            Location target = passengerLocation(horse, true);
            stand.teleport(target);
            stand.setRotation(horse.getLocation().getYaw(), 0.0F);

            // Keep the client-side seat moving with the horse between server position packets.
            // This adds no extra task or world scan and is only executed for active tandem rides.
            Vector horseVelocity = horse.getVelocity();
            stand.setVelocity(horseVelocity.clone());
        }
    }

    private Location passengerLocation(AbstractHorse horse, boolean predictMovement) {
        double distance = plugin.getConfig().getDouble("upgrades.tandem_seat.passenger-offset", 0.65D);
        double height = configuredSeatHeight();
        Location horseLocation = horse.getLocation().clone();
        double radians = Math.toRadians(horseLocation.getYaw());
        double offsetX = Math.sin(radians) * distance;
        double offsetZ = -Math.cos(radians) * distance;
        Location target = horseLocation.add(offsetX, height, offsetZ);

        if (predictMovement) {
            double predictionTicks = Math.max(
                    0.0D,
                    Math.min(
                            MAX_PREDICTION_TICKS,
                            plugin.getConfig().getDouble(
                                    "upgrades.tandem_seat.movement-prediction-ticks",
                                    1.0D
                            )
                    )
            );
            if (predictionTicks > 0.0D) {
                target.add(horse.getVelocity().clone().multiply(predictionTicks));
            }
        }
        return target;
    }

    /**
     * Migrates the old default passenger-height value automatically. Custom old
     * values are still respected, while an untouched 0.45 config receives the
     * corrected seat height without requiring the administrator to delete config.yml.
     */
    private double configuredSeatHeight() {
        String newPath = "upgrades.tandem_seat.seat-height";
        if (plugin.getConfig().contains(newPath)) {
            return plugin.getConfig().getDouble(newPath, DEFAULT_SEAT_HEIGHT);
        }

        String oldPath = "upgrades.tandem_seat.passenger-height";
        double legacyHeight = plugin.getConfig().getDouble(oldPath, LEGACY_DEFAULT_HEIGHT);
        if (Math.abs(legacyHeight - LEGACY_DEFAULT_HEIGHT) < 0.0001D) {
            return DEFAULT_SEAT_HEIGHT;
        }
        return legacyHeight;
    }

    private record RideLink(UUID horseUuid, UUID standUuid, UUID passengerUuid) {
    }
}

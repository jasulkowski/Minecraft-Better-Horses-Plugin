package me.luisgamedev.betterhorses.summon;

import me.luisgamedev.betterhorses.BetterHorses;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Async SQLite repository used by the summon horn system.
 *
 * <p>All JDBC operations are executed on a dedicated worker thread. Bukkit API objects
 * must not be accessed inside callbacks running on this executor.</p>
 */
public final class HorseSummonRepository implements AutoCloseable {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS summon_horses (
                horse_uuid TEXT PRIMARY KEY,
                owner_uuid TEXT NOT NULL,
                horse_name TEXT NOT NULL,
                world_uuid TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO summon_horses (horse_uuid, owner_uuid, horse_name, world_uuid, x, y, z, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(horse_uuid) DO UPDATE SET
                owner_uuid = excluded.owner_uuid,
                horse_name = excluded.horse_name,
                world_uuid = excluded.world_uuid,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                updated_at = excluded.updated_at
            """;

    private static final String UPDATE_LOCATION_SQL = """
            UPDATE summon_horses
            SET world_uuid = ?, x = ?, y = ?, z = ?, updated_at = ?
            WHERE horse_uuid = ?
            """;

    private static final String FIND_BY_HORSE_SQL = """
            SELECT horse_uuid, owner_uuid, horse_name, world_uuid, x, y, z
            FROM summon_horses
            WHERE horse_uuid = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM summon_horses WHERE horse_uuid = ?";

    private final BetterHorses plugin;
    private final ExecutorService executor;
    private final String jdbcUrl;

    public HorseSummonRepository(BetterHorses plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "BetterHorses-SummonSQLite");
            thread.setDaemon(true);
            return thread;
        });

        File databaseFile = new File(plugin.getDataFolder(), "summon-horses.db");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    public CompletableFuture<Void> initializeAsync() {
        return runAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException exception) {
                throw new SQLException("SQLite JDBC driver is not available. Check plugin.yml libraries section.", exception);
            }

            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute(CREATE_TABLE_SQL);
            }
        });
    }

    public CompletableFuture<Void> saveAsync(RegisteredSummonHorse horse) {
        return runAsync(() -> {
            long now = System.currentTimeMillis();
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                statement.setString(1, horse.horseUuid().toString());
                statement.setString(2, horse.ownerUuid().toString());
                statement.setString(3, horse.horseName());
                statement.setString(4, horse.worldUuid().toString());
                statement.setDouble(5, horse.x());
                statement.setDouble(6, horse.y());
                statement.setDouble(7, horse.z());
                statement.setLong(8, now);
                statement.setLong(9, now);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateLocationAsync(UUID horseUuid, UUID worldUuid, double x, double y, double z) {
        return runAsync(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(UPDATE_LOCATION_SQL)) {
                statement.setString(1, worldUuid.toString());
                statement.setDouble(2, x);
                statement.setDouble(3, y);
                statement.setDouble(4, z);
                statement.setLong(5, System.currentTimeMillis());
                statement.setString(6, horseUuid.toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<RegisteredSummonHorse>> findByHorseUuidAsync(UUID horseUuid) {
        return supplyAsync(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(FIND_BY_HORSE_SQL)) {
                statement.setString(1, horseUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new RegisteredSummonHorse(
                            UUID.fromString(resultSet.getString("horse_uuid")),
                            UUID.fromString(resultSet.getString("owner_uuid")),
                            resultSet.getString("horse_name"),
                            UUID.fromString(resultSet.getString("world_uuid")),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z")
                    ));
                }
            }
        });
    }

    public CompletableFuture<Void> deleteAsync(UUID horseUuid) {
        return runAsync(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
                statement.setString(1, horseUuid.toString());
                statement.executeUpdate();
            }
        });
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return connection;
    }

    private CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }, executor).whenComplete((ignored, throwable) -> logFailure(throwable));
    }

    private <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }, executor).whenComplete((ignored, throwable) -> logFailure(throwable));
    }

    private void logFailure(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        plugin.getLogger().log(Level.WARNING, "Summon horse SQLite operation failed.", throwable);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}

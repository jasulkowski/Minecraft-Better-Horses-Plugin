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
                horn_uuid TEXT,
                uses_remaining INTEGER NOT NULL DEFAULT 5,
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
            INSERT INTO summon_horses (horse_uuid, owner_uuid, horn_uuid, uses_remaining, horse_name, world_uuid, x, y, z, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(horse_uuid) DO UPDATE SET
                owner_uuid = excluded.owner_uuid,
                horn_uuid = excluded.horn_uuid,
                uses_remaining = excluded.uses_remaining,
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
            SELECT horse_uuid, owner_uuid, horn_uuid, uses_remaining, horse_name, world_uuid, x, y, z
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
                addColumnIfMissing(connection, "horn_uuid", "TEXT");
                addColumnIfMissing(connection, "uses_remaining", "INTEGER NOT NULL DEFAULT 5");
            }
        });
    }

    public CompletableFuture<Void> saveAsync(RegisteredSummonHorse horse) {
        return runAsync(() -> {
            try (Connection connection = openConnection()) {
                save(connection, horse);
            }
        });
    }


    /**
     * Atomically replaces the currently active horn for a horse and returns the previous
     * registration, if one existed.
     *
     * <p>The table keeps one active horn per horse. Binding a new horn therefore invalidates
     * every older physical horn for that horse without requiring the old item to still exist.</p>
     */
    public CompletableFuture<Optional<RegisteredSummonHorse>> replaceActiveHornAsync(RegisteredSummonHorse horse) {
        return supplyAsync(() -> {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<RegisteredSummonHorse> previous = findByHorseUuid(connection, horse.horseUuid());
                    save(connection, horse);
                    connection.commit();
                    return previous;
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    /**
     * Restores the previous active-horn registration only when the row still belongs to the
     * newly-bound horn. This prevents a late rollback from overwriting a newer successful bind.
     */
    public CompletableFuture<Void> restorePreviousIfCurrentMatchesAsync(
            UUID horseUuid,
            UUID currentHornUuid,
            Optional<RegisteredSummonHorse> previous
    ) {
        return runAsync(() -> {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<RegisteredSummonHorse> current = findByHorseUuid(connection, horseUuid);
                    if (current.isPresent() && current.get().hornUuid() != null
                            && current.get().hornUuid().equals(currentHornUuid)) {
                        if (previous.isPresent()) {
                            save(connection, previous.get());
                        } else {
                            try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
                                statement.setString(1, horseUuid.toString());
                                statement.executeUpdate();
                            }
                        }
                    }
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
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
                    String hornUuidRaw = resultSet.getString("horn_uuid");
                    return Optional.of(new RegisteredSummonHorse(
                            UUID.fromString(resultSet.getString("horse_uuid")),
                            UUID.fromString(resultSet.getString("owner_uuid")),
                            hornUuidRaw == null ? null : UUID.fromString(hornUuidRaw),
                            resultSet.getInt("uses_remaining"),
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

    public CompletableFuture<Void> updateUsesAsync(UUID horseUuid, UUID hornUuid, int usesRemaining) {
        return runAsync(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                    "UPDATE summon_horses SET uses_remaining = ?, updated_at = ? WHERE horse_uuid = ? AND horn_uuid = ?")) {
                statement.setInt(1, usesRemaining);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, horseUuid.toString());
                statement.setString(4, hornUuid.toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteIfHornMatchesAsync(UUID horseUuid, UUID hornUuid) {
        return runAsync(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM summon_horses WHERE horse_uuid = ? AND horn_uuid = ?")) {
                statement.setString(1, horseUuid.toString());
                statement.setString(2, hornUuid.toString());
                statement.executeUpdate();
            }
        });
    }

    private Optional<RegisteredSummonHorse> findByHorseUuid(Connection connection, UUID horseUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_BY_HORSE_SQL)) {
            statement.setString(1, horseUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                String hornUuidRaw = resultSet.getString("horn_uuid");
                return Optional.of(new RegisteredSummonHorse(
                        UUID.fromString(resultSet.getString("horse_uuid")),
                        UUID.fromString(resultSet.getString("owner_uuid")),
                        hornUuidRaw == null ? null : UUID.fromString(hornUuidRaw),
                        resultSet.getInt("uses_remaining"),
                        resultSet.getString("horse_name"),
                        UUID.fromString(resultSet.getString("world_uuid")),
                        resultSet.getDouble("x"),
                        resultSet.getDouble("y"),
                        resultSet.getDouble("z")
                ));
            }
        }
    }

    private void save(Connection connection, RegisteredSummonHorse horse) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, horse.horseUuid().toString());
            statement.setString(2, horse.ownerUuid().toString());
            statement.setString(3, horse.hornUuid().toString());
            statement.setInt(4, horse.usesRemaining());
            statement.setString(5, horse.horseName());
            statement.setString(6, horse.worldUuid().toString());
            statement.setDouble(7, horse.x());
            statement.setDouble(8, horse.y());
            statement.setDouble(9, horse.z());
            statement.setLong(10, now);
            statement.setLong(11, now);
            statement.executeUpdate();
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "summon_horses", columnName)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE summon_horses ADD COLUMN " + columnName + " " + definition);
        }
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

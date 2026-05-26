package io.rcw.vibemine.ai.plugin.runtime.database;

import io.rcw.vibemine.Vibemine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Small JavaScript-safe key/value store for vibed plugins.
 */
public final class VibeDatabase {
    private final String namespace;

    public VibeDatabase(String namespace) {
        this.namespace = namespace == null || namespace.isBlank() ? "global" : namespace;
        migrate();
    }

    public void set(String key, String value) {
        requireKey(key);
        String sql = """
                INSERT INTO vibed_plugin_store(namespace, key, value, updated_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(namespace, key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save database value", exception);
        }
    }

    public String get(String key) {
        requireKey(key);
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM vibed_plugin_store WHERE namespace = ? AND key = ?"
        )) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getString("value") : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read database value", exception);
        }
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public void delete(String key) {
        requireKey(key);
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM vibed_plugin_store WHERE namespace = ? AND key = ?"
        )) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not delete database value", exception);
        }
    }

    public List<String> keys() {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT key FROM vibed_plugin_store WHERE namespace = ? ORDER BY key"
        )) {
            statement.setString(1, namespace);
            try (ResultSet results = statement.executeQuery()) {
                List<String> keys = new ArrayList<>();
                while (results.next()) keys.add(results.getString("key"));
                return keys;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list database keys", exception);
        }
    }

    public void clear() {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM vibed_plugin_store WHERE namespace = ?"
        )) {
            statement.setString(1, namespace);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not clear database namespace", exception);
        }
    }

    public void setJson(String key, Object value) {
        set(key, Vibemine.GSON.toJson(value));
    }

    public Object getJson(String key) {
        String value = get(key);
        return value == null ? null : Vibemine.GSON.fromJson(value, Object.class);
    }

    private void migrate() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vibed_plugin_store (
                        namespace TEXT NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(namespace, key)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize vibed plugin database", exception);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + Vibemine.getInstance().getDatabasePath());
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Database key cannot be blank");
    }
}

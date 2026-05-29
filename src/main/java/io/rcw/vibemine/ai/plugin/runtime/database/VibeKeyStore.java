package io.rcw.vibemine.ai.plugin.runtime.database;

import io.rcw.vibemine.Vibemine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * JavaScript-safe plugin-scoped persistent storage.
 * Keeps the original key/value API and also creates JSON object tables.
 */
public final class VibeKeyStore {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final String namespace;

    public VibeKeyStore(String namespace) {
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
            throw new IllegalStateException("Could not save key/value", exception);
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
            throw new IllegalStateException("Could not read key/value", exception);
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
            throw new IllegalStateException("Could not delete key/value", exception);
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
            throw new IllegalStateException("Could not list keys", exception);
        }
    }

    public void clear() {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM vibed_plugin_store WHERE namespace = ?"
        )) {
            statement.setString(1, namespace);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not clear key/value namespace", exception);
        }
    }

    public void setJson(String key, Object value) {
        set(key, Vibemine.GSON.toJson(value));
    }

    public Object getJson(String key) {
        String value = get(key);
        return value == null ? null : Vibemine.GSON.fromJson(value, Object.class);
    }

    /** Create/open a persistent JSON object table for this plugin namespace. */
    public VibeTable createTable(String name) {
        requireTableName(name);
        VibeTable table = new VibeTable(namespace, normalizedTableName(name));
        table.create();
        return table;
    }

    /** Open a table, creating it if needed. Alias for createTable for generated scripts. */
    public VibeTable table(String name) {
        return createTable(name);
    }

    public void dropTable(String name) {
        requireTableName(name);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS " + sqlTableName(namespace, name));
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not drop table", exception);
        }
    }

    public List<String> tables() {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE ? ORDER BY name"
        )) {
            statement.setString(1, tablePrefix(namespace) + "%");
            try (ResultSet results = statement.executeQuery()) {
                List<String> tables = new ArrayList<>();
                while (results.next()) tables.add(results.getString("name").substring(tablePrefix(namespace).length()));
                return tables;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list tables", exception);
        }
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
            throw new IllegalStateException("Could not initialize key store", exception);
        }
    }

    static Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + Vibemine.getInstance().getDatabasePath());
    }

    static String sqlTableName(String namespace, String table) {
        return tablePrefix(namespace) + normalizedTableName(table);
    }

    private static String tablePrefix(String namespace) {
        return "vibed_obj_" + normalizedTableName(namespace) + "_";
    }

    static String normalizedTableName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return Character.isLetter(normalized.charAt(0)) ? normalized : "n_" + normalized;
    }

    static void requireTableName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Table names must start with a letter and contain only letters, numbers, and underscores (max 64 chars)");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Key cannot be blank");
    }
}

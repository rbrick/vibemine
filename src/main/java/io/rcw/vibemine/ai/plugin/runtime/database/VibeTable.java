package io.rcw.vibemine.ai.plugin.runtime.database;

import io.rcw.vibemine.Vibemine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Persistent JavaScript-safe table of JSON objects keyed by string id. */
public final class VibeTable {
    private final String namespace;
    private final String name;

    VibeTable(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void create() {
        try (Connection connection = VibeKeyStore.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id TEXT PRIMARY KEY,
                        data TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.formatted(sqlName()));
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create table", exception);
        }
    }

    public void put(String id, Object object) {
        requireId(id);
        create();
        String json = Vibemine.GSON.toJson(object);
        String sql = """
                INSERT INTO %s(id, data, created_at, updated_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    data = excluded.data,
                    updated_at = excluded.updated_at
                """.formatted(sqlName());
        long now = System.currentTimeMillis();
        try (Connection connection = VibeKeyStore.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, json);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save object", exception);
        }
    }

    public Object get(String id) {
        requireId(id);
        create();
        try (Connection connection = VibeKeyStore.connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT data FROM " + sqlName() + " WHERE id = ?"
        )) {
            statement.setString(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? decode(results.getString("data")) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read object", exception);
        }
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    public void delete(String id) {
        requireId(id);
        create();
        try (Connection connection = VibeKeyStore.connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + sqlName() + " WHERE id = ?"
        )) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not delete object", exception);
        }
    }

    public List<String> ids() {
        create();
        try (Connection connection = VibeKeyStore.connection(); Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT id FROM " + sqlName() + " ORDER BY id")) {
            List<String> ids = new ArrayList<>();
            while (results.next()) ids.add(results.getString("id"));
            return ids;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list ids", exception);
        }
    }

    public List<Object> all() {
        create();
        try (Connection connection = VibeKeyStore.connection(); Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT data FROM " + sqlName() + " ORDER BY id")) {
            List<Object> objects = new ArrayList<>();
            while (results.next()) objects.add(decode(results.getString("data")));
            return objects;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list objects", exception);
        }
    }

    /** Simple in-memory JSON field equality query, e.g. factions.where("owner", player.getName()). */
    public List<Object> where(String field, Object expected) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("Field cannot be blank");
        List<Object> matches = new ArrayList<>();
        for (Object object : all()) {
            if (object instanceof Map<?, ?> map) {
                Object actual = map.get(field);
                if (expected == null ? actual == null : expected.equals(actual)) matches.add(object);
            }
        }
        return matches;
    }

    public int count() {
        create();
        try (Connection connection = VibeKeyStore.connection(); Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) AS count FROM " + sqlName())) {
            return results.next() ? results.getInt("count") : 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not count objects", exception);
        }
    }

    public void clear() {
        create();
        try (Connection connection = VibeKeyStore.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + sqlName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not clear table", exception);
        }
    }

    private Object decode(String json) {
        return Vibemine.GSON.fromJson(json, Object.class);
    }

    private String sqlName() {
        return VibeKeyStore.sqlTableName(namespace, name);
    }

    private void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Object id cannot be blank");
    }
}

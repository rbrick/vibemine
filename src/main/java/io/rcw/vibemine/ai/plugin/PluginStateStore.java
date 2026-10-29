package io.rcw.vibemine.ai.plugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Source-only checkpoints. Runtime memory, databases and world changes are not versioned. */
public final class PluginStateStore implements AutoCloseable {
    public static final int MAX_FILE = 128_000;
    private static final Gson JSON = new Gson();
    private final Connection db;
    private final Path live;

    public record Workspace(String plugin, String owner, String branch, String base, long version,
                            boolean dirty, Map<String, String> files) {}
    public record Checkpoint(String id, String parent, String message, long timestamp) {
        public String display() { return id + " | " + message; }
    }

    public PluginStateStore(Path database, Path live) throws IOException {
        this.live = live.toAbsolutePath().normalize();
        try {
            Files.createDirectories(database.toAbsolutePath().getParent());
            db = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            try (Statement statement = db.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("CREATE TABLE IF NOT EXISTS checkpoints (plugin TEXT NOT NULL, id TEXT PRIMARY KEY, parent TEXT, message TEXT NOT NULL, timestamp INTEGER NOT NULL, files TEXT NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS checkpoint_history ON checkpoints(plugin, timestamp)");
                statement.execute("CREATE TABLE IF NOT EXISTS branches (plugin TEXT NOT NULL, name TEXT NOT NULL, head TEXT, PRIMARY KEY(plugin,name))");
                statement.execute("CREATE TABLE IF NOT EXISTS workspaces (plugin TEXT NOT NULL, owner TEXT NOT NULL, branch TEXT NOT NULL, base TEXT, version INTEGER NOT NULL, dirty INTEGER NOT NULL, files TEXT NOT NULL, PRIMARY KEY(plugin,owner))");
                statement.execute("CREATE TABLE IF NOT EXISTS active_checkpoints (plugin TEXT PRIMARY KEY, checkpoint TEXT NOT NULL)");
            }
        } catch (SQLException exception) { throw new IOException("Could not open plugin state database", exception); }
    }

    public static String name(String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,80}")) throw new IllegalArgumentException("Plugin name must use lowercase letters, digits or underscores (max 80)");
        return value;
    }

    public static String sourcePath(String value) {
        if (value == null || !(value.equals("plugin.json") || value.equals("globals.js")
                || value.matches("(?:commands|events)/[a-zA-Z0-9_/-]+\\.js"))) {
            throw new IllegalArgumentException("Source path must be plugin.json, globals.js, commands/*.js or events/*.js");
        }
        if (value.contains("//") || value.contains("..")) throw new IllegalArgumentException("Invalid source path");
        return value;
    }

    public synchronized Workspace workspace(String plugin, String owner) throws IOException {
        name(plugin);
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Workspace owner is required");
        return transaction(() -> {
            Workspace existing = findWorkspace(plugin, owner);
            if (existing != null) return existing;
            String base = activeInternal(plugin);
            Map<String, String> files;
            if (base == null) {
                files = importFiles(plugin);
                if (!files.isEmpty()) {
                    base = insertCheckpoint(plugin, null, "Imported existing source", files);
                    update("INSERT INTO active_checkpoints VALUES (?,?)", plugin, base);
                }
            } else files = snapshotInternal(plugin, base);
            String branch = "workspace-" + owner;
            update("INSERT INTO branches VALUES (?,?,?)", plugin, branch, base);
            Workspace created = new Workspace(plugin, owner, branch, base, 0, false, Map.copyOf(files));
            saveWorkspace(created);
            return created;
        });
    }

    public synchronized Workspace write(String plugin, String owner, long expectedVersion, String path, String text) throws IOException {
        sourcePath(path);
        if (text == null || text.length() > MAX_FILE) throw new IllegalArgumentException("Source file exceeds 128,000 characters or is null");
        return change(plugin, owner, expectedVersion, files -> files.put(path, text));
    }

    public synchronized Workspace edit(String plugin, String owner, long expectedVersion, String path, String oldText, String newText) throws IOException {
        sourcePath(path);
        return change(plugin, owner, expectedVersion, files -> {
            String text = files.get(path);
            if (text == null) throw new IllegalArgumentException("File not found");
            if (oldText == null || oldText.isEmpty()) throw new IllegalArgumentException("oldText is required");
            int first = text.indexOf(oldText);
            if (first < 0 || text.indexOf(oldText, first + oldText.length()) >= 0) throw new IllegalArgumentException("oldText must match exactly once");
            String updated = text.substring(0, first) + Objects.requireNonNullElse(newText, "") + text.substring(first + oldText.length());
            if (updated.length() > MAX_FILE) throw new IllegalArgumentException("Source file too large");
            files.put(path, updated);
        });
    }

    public synchronized Workspace delete(String plugin, String owner, long expectedVersion, String path) throws IOException {
        sourcePath(path);
        return change(plugin, owner, expectedVersion, files -> {
            if (files.remove(path) == null) throw new IllegalArgumentException("File not found");
        });
    }

    private Workspace change(String plugin, String owner, long expectedVersion, java.util.function.Consumer<Map<String,String>> edit) throws IOException {
        return transaction(() -> {
            Workspace workspace = requireVersion(plugin, owner, expectedVersion);
            Map<String,String> files = new TreeMap<>(workspace.files());
            edit.accept(files);
            checkSize(files);
            Workspace changed = new Workspace(plugin, owner, workspace.branch(), workspace.base(), workspace.version() + 1,
                    workspace.dirty() || !files.equals(workspace.files()), Map.copyOf(files));
            saveWorkspace(changed);
            return changed;
        });
    }

    public synchronized String checkpoint(String plugin, String owner, long expectedVersion, String message) throws IOException {
        if (message == null || message.isBlank() || message.length() > 500) throw new IllegalArgumentException("Checkpoint message is required (max 500)");
        return transaction(() -> {
            Workspace workspace = requireVersion(plugin, owner, expectedVersion);
            String head = branchHead(plugin, workspace.branch());
            if (!Objects.equals(head, workspace.base())) throw new IOException("Branch advanced; fork your workspace onto a new branch before checkpointing");
            if (!workspace.dirty() && workspace.base() != null) return workspace.base();
            String id = insertCheckpoint(plugin, workspace.base(), message, workspace.files());
            update("UPDATE branches SET head=? WHERE plugin=? AND name=?", id, plugin, workspace.branch());
            saveWorkspace(new Workspace(plugin, owner, workspace.branch(), id, workspace.version() + 1, false, workspace.files()));
            return id;
        });
    }

    public synchronized void branch(String plugin, String branch, String checkpoint) throws IOException {
        name(plugin);
        if (branch == null || !branch.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid branch name");
        transaction(() -> {
            snapshotInternal(plugin, checkpoint);
            update("INSERT INTO branches VALUES (?,?,?)", plugin, branch, checkpoint);
            return null;
        });
    }

    public synchronized Workspace fork(String plugin, String owner, long expectedVersion, String branch) throws IOException {
        if (branch == null || !branch.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid branch name");
        return transaction(() -> {
            Workspace workspace = requireVersion(plugin, owner, expectedVersion);
            update("INSERT INTO branches VALUES (?,?,?)", plugin, branch, workspace.base());
            Workspace fork = new Workspace(plugin, owner, branch, workspace.base(), workspace.version() + 1, workspace.dirty(), workspace.files());
            saveWorkspace(fork);
            return fork;
        });
    }

    public synchronized Workspace checkout(String plugin, String owner, long expectedVersion, String branch) throws IOException {
        return transaction(() -> {
            Workspace workspace = requireVersion(plugin, owner, expectedVersion);
            if (workspace.dirty()) throw new IOException("Checkpoint your edits before switching branches");
            String head = branchHead(plugin, branch);
            Map<String,String> files = head == null ? Map.of() : snapshotInternal(plugin, head);
            Workspace changed = new Workspace(plugin, owner, branch, head, workspace.version() + 1, false, files);
            saveWorkspace(changed);
            return changed;
        });
    }

    public synchronized Map<String,String> branches(String plugin) throws IOException {
        name(plugin);
        try (PreparedStatement query = prepare("SELECT name,head FROM branches WHERE plugin=? ORDER BY name", plugin);
             ResultSet rows = query.executeQuery()) {
            Map<String,String> branches = new LinkedHashMap<>();
            while (rows.next()) branches.put(rows.getString(1), rows.getString(2));
            return branches;
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    public synchronized List<Checkpoint> history(String plugin, int limit) throws IOException {
        name(plugin);
        try (PreparedStatement query = prepare("SELECT id,parent,message,timestamp FROM checkpoints WHERE plugin=? ORDER BY timestamp DESC, rowid DESC LIMIT ?", plugin, Math.min(Math.max(limit, 1), 100));
             ResultSet rows = query.executeQuery()) {
            List<Checkpoint> result = new ArrayList<>();
            while (rows.next()) result.add(new Checkpoint(rows.getString(1), rows.getString(2), rows.getString(3), rows.getLong(4)));
            return result;
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    public synchronized Map<String,String> snapshot(String plugin, String checkpoint) throws IOException {
        name(plugin);
        try { return snapshotInternal(plugin, checkpoint); }
        catch (SQLException exception) { throw new IOException(exception); }
    }

    public synchronized List<String> workspacePlugins(String owner) throws IOException {
        try (PreparedStatement query = prepare("SELECT plugin FROM workspaces WHERE owner=? ORDER BY plugin", owner);
             ResultSet rows = query.executeQuery()) {
            List<String> names = new ArrayList<>();
            while (rows.next()) names.add(rows.getString(1));
            return names;
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    public synchronized List<String> activePlugins() throws IOException {
        try (Statement query = db.createStatement(); ResultSet rows = query.executeQuery("SELECT plugin FROM active_checkpoints ORDER BY plugin")) {
            List<String> names = new ArrayList<>();
            while (rows.next()) names.add(rows.getString(1));
            return names;
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    public synchronized void forgetActive(String plugin) throws IOException {
        name(plugin);
        transaction(() -> { update("DELETE FROM active_checkpoints WHERE plugin=?", plugin); return null; });
    }

    public synchronized String active(String plugin) throws IOException {
        name(plugin);
        try { return activeInternal(plugin); }
        catch (SQLException exception) { throw new IOException(exception); }
    }

    public synchronized void markActive(String plugin, String checkpoint, String expectedActive) throws IOException {
        transaction(() -> {
            snapshotInternal(plugin, checkpoint);
            if (!Objects.equals(activeInternal(plugin), expectedActive)) throw new IOException("Active checkpoint changed; refresh before activating");
            update("INSERT INTO active_checkpoints VALUES (?,?) ON CONFLICT(plugin) DO UPDATE SET checkpoint=excluded.checkpoint", plugin, checkpoint);
            return null;
        });
    }

    private Map<String,String> importFiles(String plugin) throws IOException {
        Path directory = live.resolve(plugin);
        if (!Files.exists(directory)) return Map.of();
        if (Files.isSymbolicLink(directory)) throw new IOException("Symlink plugin directory is not allowed");
        Map<String,String> files = new TreeMap<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attributes) {
                String relative = directory.relativize(path).toString().replace('\\', '/');
                return path.equals(directory) || relative.equals("commands") || relative.equals("events")
                        || relative.startsWith("commands/") || relative.startsWith("events/")
                        ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
            }

            @Override public FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                String relative = directory.relativize(path).toString().replace('\\', '/');
                try { sourcePath(relative); } catch (IllegalArgumentException ignored) { return FileVisitResult.CONTINUE; }
                if (!attributes.isRegularFile() || !path.toRealPath().equals(path.toAbsolutePath().normalize())) throw new IOException("Symlink source is not allowed");
                if (attributes.size() > MAX_FILE * 4L) throw new IOException("Source file too large");
                String text = Files.readString(path);
                if (text.length() > MAX_FILE) throw new IOException("Source file too large");
                files.put(relative, text);
                checkSize(files);
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static void checkSize(Map<String,String> files) throws IOException {
        if (files.size() > 256 || files.values().stream().mapToLong(String::length).sum() > 2_000_000) {
            throw new IOException("Workspace exceeds 256 files or 2,000,000 characters");
        }
    }

    private Workspace requireVersion(String plugin, String owner, long version) throws SQLException, IOException {
        name(plugin);
        Workspace workspace = findWorkspace(plugin, owner);
        if (workspace == null) throw new IOException("Open the workspace first");
        if (version != workspace.version()) throw new IOException("Workspace changed; read it again before editing");
        return workspace;
    }

    private Workspace findWorkspace(String plugin, String owner) throws SQLException {
        try (PreparedStatement query = prepare("SELECT branch,base,version,dirty,files FROM workspaces WHERE plugin=? AND owner=?", plugin, owner);
             ResultSet rows = query.executeQuery()) {
            if (!rows.next()) return null;
            return new Workspace(plugin, owner, rows.getString(1), rows.getString(2), rows.getLong(3), rows.getBoolean(4), decode(rows.getString(5)));
        }
    }

    // shortcut: rewrite bounded source JSON per edit; split file storage if measured write latency grows.
    private void saveWorkspace(Workspace workspace) throws SQLException {
        update("INSERT INTO workspaces VALUES (?,?,?,?,?,?,?) ON CONFLICT(plugin,owner) DO UPDATE SET branch=excluded.branch,base=excluded.base,version=excluded.version,dirty=excluded.dirty,files=excluded.files",
                workspace.plugin(), workspace.owner(), workspace.branch(), workspace.base(), workspace.version(), workspace.dirty(), JSON.toJson(workspace.files()));
    }

    private String insertCheckpoint(String plugin, String parent, String message, Map<String,String> files) throws SQLException {
        String id = UUID.randomUUID().toString();
        update("INSERT INTO checkpoints VALUES (?,?,?,?,?,?)", plugin, id, parent, message, System.currentTimeMillis(), JSON.toJson(files));
        return id;
    }

    private Map<String,String> snapshotInternal(String plugin, String id) throws SQLException, IOException {
        try (PreparedStatement query = prepare("SELECT files FROM checkpoints WHERE plugin=? AND id=?", plugin, id);
             ResultSet rows = query.executeQuery()) {
            if (!rows.next()) throw new IOException("Checkpoint not found for this plugin");
            return decode(rows.getString(1));
        }
    }

    private String activeInternal(String plugin) throws SQLException {
        try (PreparedStatement query = prepare("SELECT checkpoint FROM active_checkpoints WHERE plugin=?", plugin);
             ResultSet rows = query.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
    }

    private String branchHead(String plugin, String branch) throws SQLException, IOException {
        try (PreparedStatement query = prepare("SELECT head FROM branches WHERE plugin=? AND name=?", plugin, branch);
             ResultSet rows = query.executeQuery()) {
            if (!rows.next()) throw new IOException("Branch not found");
            return rows.getString(1);
        }
    }

    private static Map<String,String> decode(String json) {
        return Map.copyOf(JSON.fromJson(json, new TypeToken<Map<String,String>>() {}.getType()));
    }

    private PreparedStatement prepare(String sql, Object... args) throws SQLException {
        PreparedStatement query = db.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) query.setObject(i + 1, args[i]);
        return query;
    }

    private void update(String sql, Object... args) throws SQLException {
        try (PreparedStatement query = prepare(sql, args)) { query.executeUpdate(); }
    }

    private <T> T transaction(Operation<T> operation) throws IOException {
        try {
            db.setAutoCommit(false);
            try {
                T result = operation.run();
                db.commit();
                return result;
            } catch (Exception exception) {
                try { db.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                if (exception instanceof IOException io) throw io;
                throw new IOException(exception.getMessage(), exception);
            } finally { db.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IOException(exception); }
    }

    @FunctionalInterface private interface Operation<T> { T run() throws Exception; }

    @Override public synchronized void close() throws IOException {
        try { db.close(); } catch (SQLException exception) { throw new IOException(exception); }
    }
}

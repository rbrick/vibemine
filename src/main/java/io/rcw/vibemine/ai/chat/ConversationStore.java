package io.rcw.vibemine.ai.chat;

import io.rcw.vibemine.Vibemine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ConversationStore implements AutoCloseable {
    public record SessionInfo(UUID sessionId, UUID playerId, long updatedAt, int messageCount) {}

    private final Connection connection;

    public ConversationStore(Path databasePath) throws SQLException, IOException {
        Files.createDirectories(databasePath.getParent());
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        migrate();
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        session_id TEXT PRIMARY KEY,
                        player_id TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        message_count INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conversations_player_updated ON conversations(player_id, updated_at DESC)");
        }
    }

    public synchronized void save(Conversation conversation) {
        String sql = """
                INSERT INTO conversations(session_id, player_id, payload, updated_at, message_count)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                    player_id = excluded.player_id,
                    payload = excluded.payload,
                    updated_at = excluded.updated_at,
                    message_count = excluded.message_count
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversation.getSessionId().toString());
            statement.setString(2, conversation.getPlayerId().toString());
            statement.setString(3, Vibemine.GSON.toJson(conversation));
            statement.setLong(4, System.currentTimeMillis());
            statement.setInt(5, conversation.getMessages().size());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save conversation", exception);
        }
    }

    public synchronized Optional<Conversation> load(UUID playerId, UUID sessionId) {
        String sql = "SELECT payload FROM conversations WHERE player_id = ? AND session_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, sessionId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return Optional.empty();
                return Optional.of(Vibemine.GSON.fromJson(results.getString("payload"), Conversation.class));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load conversation", exception);
        }
    }

    public synchronized Optional<Conversation> latest(UUID playerId) {
        String sql = "SELECT payload FROM conversations WHERE player_id = ? ORDER BY updated_at DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return Optional.empty();
                return Optional.of(Vibemine.GSON.fromJson(results.getString("payload"), Conversation.class));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load latest conversation", exception);
        }
    }

    public synchronized List<SessionInfo> list(UUID playerId, int limit) {
        String sql = "SELECT session_id, player_id, updated_at, message_count FROM conversations WHERE player_id = ? ORDER BY updated_at DESC LIMIT ?";
        List<SessionInfo> sessions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    sessions.add(new SessionInfo(
                            UUID.fromString(results.getString("session_id")),
                            UUID.fromString(results.getString("player_id")),
                            results.getLong("updated_at"),
                            results.getInt("message_count")
                    ));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not list conversations", exception);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}

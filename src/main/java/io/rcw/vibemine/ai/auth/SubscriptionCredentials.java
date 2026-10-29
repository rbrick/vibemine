package io.rcw.vibemine.ai.auth;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class SubscriptionCredentials {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Credentials(String issuer, String subject, String email, String client_id, String ext_agent_host_id,
                              String id_token, String access_token, String refresh_token, String token_type,
                              long expires_at, List<String> scopes) {
        @Override public String toString() { return "ChatGPT credentials [redacted]"; }
        public boolean connected() { return access_token != null && refresh_token != null; }
    }
    private final Path directory;
    private final String hostId;
    private Credentials credentials;
    private boolean pendingSave;

    public SubscriptionCredentials(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        Path host = this.directory.resolve("host-id");
        if (Files.exists(host)) hostId = Files.readString(host).strip();
        else {
            hostId = "urn:uuid:" + UUID.randomUUID();
            CredentialFiles.write(host, hostId);
        }
        if (!hostId.matches("urn:uuid:[0-9a-f-]{36}")) throw new IOException("Invalid saved ChatGPT host ID");
        if (Files.exists(file())) {
            credentials = parse(CredentialFiles.read(file()));
            validate(credentials, hostId);
        }
    }

    private Path file() { return directory.resolve("credentials.json"); }
    public String hostId() { return hostId; }
    public synchronized boolean connected() { return credentials != null && credentials.connected(); }
    public synchronized String status() {
        if (credentials == null) return "ChatGPT: not connected";
        return "ChatGPT: " + (credentials.connected() ? "connected" : "signed out") + " | account: " + credentials.email()
                + " | plan permission: " + credentials.scopes().contains("chatgpt.tokens.use.direct");
    }

    public synchronized void importCredentials() throws IOException {
        Path incoming = directory.resolve("import.json");
        Credentials imported = parse(CredentialFiles.read(incoming));
        validate(imported, hostId);
        if (!imported.connected()) throw new IOException("Import has no renewable session");
        IdToken.Identity identity = IdToken.verify(imported.id_token(), imported.client_id(), null);
        if (!identity.subject().equals(imported.subject())) throw new IOException("Imported account identity does not match");
        save(imported);
        Files.delete(incoming);
    }

    public synchronized String accessToken() throws IOException {
        if (pendingSave) save(credentials);
        if (!connected()) throw new IOException("ChatGPT is not connected; import owner credentials or configure an API key");
        if (!credentials.scopes().contains("chatgpt.tokens.use.direct")) throw new IOException("ChatGPT plan usage was not authorized; sign in again with plan permission");
        if (credentials.expires_at() > Instant.now().getEpochSecond() + 60) return credentials.access_token();
        try {
            // Fetch verification keys before rotating a refresh token, not after a possible network failure.
            JsonObject keys = OAuthHttp.get(OAuthHttp.ISSUER + "/.well-known/jwks.json");
            JsonObject tokens = OAuthHttp.token(Map.of("grant_type", "refresh_token", "client_id", credentials.client_id(),
                    "refresh_token", credentials.refresh_token(), "resource", OAuthHttp.RESOURCE));
            Credentials replacement = fromTokens(tokens, credentials, null);
            if (tokens.has("id_token")) {
                var identity = IdToken.verify(replacement.id_token(), replacement.client_id(), null, keys, Instant.now().getEpochSecond());
                if (!credentials.subject().equals(identity.subject())) throw new IOException("Refreshed account identity changed");
            }
            // A rotated refresh token must survive in memory even if its atomic disk write fails.
            credentials = replacement;
            pendingSave = true;
            save(replacement);
            if (!replacement.scopes().contains("chatgpt.tokens.use.direct")) throw new IOException("ChatGPT plan permission was removed");
            return replacement.access_token();
        } catch (OAuthHttp.Failure failure) {
            if (Set.of("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused").contains(failure.code)) {
                clearTokens();
            }
            throw failure;
        }
    }

    public synchronized String models() throws IOException {
        var response = OAuthHttp.json(java.net.http.HttpRequest.newBuilder(java.net.URI.create(OAuthHttp.RESOURCE + "/models"))
                .timeout(java.time.Duration.ofSeconds(30)).header("Authorization", "Bearer " + accessToken()).GET().build());
        StringBuilder models = new StringBuilder();
        for (var element : response.getAsJsonArray("models")) {
            var model = element.getAsJsonObject();
            if (model.has("visibility") && "list".equals(model.get("visibility").getAsString())) {
                models.append(model.get("slug").getAsString()).append(" — ").append(model.get("display_name").getAsString()).append('\n');
            }
        }
        return models.isEmpty() ? "No subscription models available for this account." : models.toString();
    }

    public synchronized String logout() throws IOException {
        boolean revoked = !connected();
        if (connected()) {
            try {
                JsonObject discovery = OAuthHttp.get(OAuthHttp.ISSUER + "/.well-known/openid-configuration");
                String endpoint = discovery.get("revocation_endpoint").getAsString();
                if (!endpoint.startsWith(OAuthHttp.ISSUER + "/")) throw new IOException("Invalid revocation endpoint");
                OAuthHttp.json(java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint))
                        .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(OAuthHttp.form(Map.of("token", credentials.refresh_token(),
                                "token_type_hint", "refresh_token", "client_id", credentials.client_id())))).build());
                revoked = true;
            } catch (IOException | RuntimeException ignored) { }
        }
        clearTokens();
        return revoked ? "ChatGPT signed out and renewable session revoked. API fallback remains configured separately."
                : "Signed out locally. Remote revocation was not confirmed; disconnect VibeMine in ChatGPT Settings. API fallback remains configured separately.";
    }

    private void clearTokens() throws IOException {
        if (credentials == null) return;
        credentials = new Credentials(credentials.issuer(), credentials.subject(), credentials.email(), credentials.client_id(), hostId,
                null, null, null, "Bearer", 0, credentials.scopes());
        pendingSave = true;
        save(credentials);
    }

    private void save(Credentials value) throws IOException {
        CredentialFiles.write(file(), JSON.toJson(value));
        credentials = value;
        pendingSave = false;
    }

    public static Credentials parse(String json) throws IOException {
        try { return JSON.fromJson(json, Credentials.class); }
        catch (RuntimeException exception) { throw new IOException("Invalid credential JSON"); }
    }

    public static void validate(Credentials value, String hostId) throws IOException {
        if (value == null || !OAuthHttp.ISSUER.equals(value.issuer()) || !Objects.equals(hostId, value.ext_agent_host_id())
                || value.subject() == null || value.subject().isBlank() || value.client_id() == null || !value.client_id().startsWith("oaiapp_")
                || value.scopes() == null || !"Bearer".equalsIgnoreCase(value.token_type())) throw new IOException("Invalid credentials or wrong server host ID");
        if ((value.access_token() == null) != (value.refresh_token() == null)) throw new IOException("Incomplete credential session");
        if (value.connected() && (value.id_token() == null || value.expires_at() <= 0 || value.access_token().isBlank() || value.refresh_token().isBlank())) {
            throw new IOException("Incomplete credential session");
        }
    }

    public static Credentials fromTokens(JsonObject response, Credentials previous, IdToken.Identity identity) throws IOException {
        try {
            var scopes = response.has("scope") ? Arrays.asList(response.get("scope").getAsString().split("\\s+")) : previous.scopes();
            Credentials value = new Credentials(OAuthHttp.ISSUER, identity == null ? previous.subject() : identity.subject(),
                    identity == null ? previous.email() : identity.email(), previous.client_id(), previous.ext_agent_host_id(),
                    response.has("id_token") ? response.get("id_token").getAsString() : previous.id_token(),
                    response.get("access_token").getAsString(), response.get("refresh_token").getAsString(),
                    response.get("token_type").getAsString(), Instant.now().getEpochSecond() + response.get("expires_in").getAsLong(), scopes);
            validate(value, previous.ext_agent_host_id());
            return value;
        } catch (RuntimeException exception) { throw new IOException("Invalid OAuth token response"); }
    }

    public static String encode(Credentials credentials) { return JSON.toJson(credentials); }
}

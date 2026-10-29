package io.rcw.vibemine.ai.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import static org.junit.jupiter.api.Assertions.*;

class CredentialFilesTest {
    @TempDir Path directory;
    @Test void writesAtomicallyWithPrivatePermissionsAndRejectsPublicOrSymlinkFiles() throws IOException {
        Path file = directory.resolve("credentials.json");
        CredentialFiles.write(file, "first");
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        CredentialFiles.write(file, "second");
        assertEquals("second", CredentialFiles.read(file));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IOException.class, () -> CredentialFiles.read(file));
        Path link = directory.resolve("link.json"); Files.createSymbolicLink(link, file);
        assertThrows(IOException.class, () -> CredentialFiles.write(link, "overwrite"));
        assertThrows(IOException.class, () -> CredentialFiles.read(link));
        assertEquals("second", Files.readString(file));
    }

    @Test void serverHostIdentitySurvivesRestartsWithoutCredentials() throws IOException {
        var first = new SubscriptionCredentials(directory);
        var reopened = new SubscriptionCredentials(directory);
        assertEquals(first.hostId(), reopened.hostId());
        assertFalse(reopened.connected());
        assertThrows(IOException.class, reopened::accessToken);
        assertFalse(reopened.status().contains("Bearer"));
    }

    @Test void refreshResponseReplacesTokensAndPreservesScopeWhenOmitted() throws IOException {
        var previous = new SubscriptionCredentials.Credentials(OAuthHttp.ISSUER, "owner", "owner@example.com", "oaiapp_test", "host-a",
                "old-id", "old-access", "old-refresh", "Bearer", 123, java.util.List.of("chatgpt.tokens.use.direct"));
        var response = com.google.gson.JsonParser.parseString("{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"token_type\":\"Bearer\",\"expires_in\":3600}").getAsJsonObject();
        var replacement = SubscriptionCredentials.fromTokens(response, previous, null);
        assertEquals("new-refresh", replacement.refresh_token());
        assertEquals("new-access", replacement.access_token());
        assertEquals("old-id", replacement.id_token());
        assertEquals(previous.scopes(), replacement.scopes());
        assertTrue(replacement.expires_at() > java.time.Instant.now().getEpochSecond());
        response.addProperty("scope", "openid profile");
        assertFalse(SubscriptionCredentials.fromTokens(response, previous, null).scopes().contains("chatgpt.tokens.use.direct"));
        response.remove("refresh_token");
        assertThrows(IOException.class, () -> SubscriptionCredentials.fromTokens(response, previous, null));
    }

    @Test void rejectsIncompleteCredentialsAndAnotherServersHostId() throws IOException {
        var credentials = new SubscriptionCredentials.Credentials(OAuthHttp.ISSUER, "owner", "owner@example.com", "oaiapp_test", "host-a",
                "private-id", "private-access", "private-refresh", "Bearer", 123, java.util.List.of("chatgpt.tokens.use.direct"));
        SubscriptionCredentials.validate(credentials, "host-a");
        assertThrows(IOException.class, () -> SubscriptionCredentials.validate(credentials, "host-b"));
        assertFalse(credentials.toString().contains("private"));
    }
}

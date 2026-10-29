package io.rcw.vibemine.ai.auth;

import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Run locally with the shaded plugin jar; never opens a callback listener on the Minecraft server. */
public final class ChatGptLogin {
    private ChatGptLogin() {}
    public static void main(String[] args) {
        try {
            if (args.length != 4 || !args[0].equals("--host-id") || !args[2].equals("--output")) {
                throw new IOException("Usage: ChatGptLogin --host-id urn:uuid:<server-host-id> --output <credentials.json>");
            }
            login(args[1], Path.of(args[3]).toAbsolutePath());
        } catch (Exception exception) {
            System.err.println("Sign-in failed: " + (exception instanceof IOException ? exception.getMessage() : "OAuth could not be completed; try again"));
            System.exit(1);
        }
    }

    static void login(String hostId, Path output) throws Exception {
        if (!hostId.matches("urn:uuid:[0-9a-f-]{36}")) throw new IOException("Supply the host ID printed by the server's vibe auth host command");
        if (!Files.isDirectory(output.getParent())) throw new IOException("Output directory must already exist");
        Path registrationPath = output.resolveSibling(output.getFileName() + ".registration.json");
        var registration = Files.exists(registrationPath) ? SubscriptionCredentials.parse(CredentialFiles.read(registrationPath)) : null;
        if (registration != null) SubscriptionCredentials.validate(registration, hostId);
        String client = registration == null ? "dynamic_agent_client" : registration.client_id();
        String state = random();
        String nonce = random();
        String verifier = random();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/callback";
        CompletableFuture<Map<String,String>> callback = new CompletableFuture<>();
        server.createContext("/auth/callback", exchange -> {
            boolean accepted = false;
            try {
                if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/auth/callback")
                        && exchange.getRequestURI().toString().length() < 16000) {
                    var values = query(exchange.getRequestURI().getRawQuery());
                    if (MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), values.getOrDefault("state", "").getBytes(StandardCharsets.UTF_8))) {
                        accepted = callback.complete(values);
                    }
                }
            } catch (IllegalArgumentException ignored) { }
            byte[] body = (accepted ? "VibeMine received the sign-in response. Return to the terminal to finish." : "Invalid or expired sign-in callback.").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(accepted ? 200 : 400, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            Map<String,String> fields = new LinkedHashMap<>(Map.of("client_id", client, "ext_agent_host_id", hostId,
                    "response_type", "code", "redirect_uri", redirect, "scope", "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
                    "resource", OAuthHttp.RESOURCE, "state", state, "nonce", nonce, "code_challenge_method", "S256", "code_challenge", challenge));
            if (registration == null) fields.put("agent_name_hint", "VibeMine");
            URI authorization = URI.create(OAuthHttp.ISSUER + "/api/accounts/authorize?" + OAuthHttp.form(fields));
            System.out.println("Continue with ChatGPT. This authorizes the server owner's account, subject to its plan limits.");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try { Desktop.getDesktop().browse(authorization); }
                catch (IOException exception) { System.out.println("Open in a browser on THIS computer:\n" + authorization); }
            } else System.out.println("Open in a browser on THIS computer:\n" + authorization);
            Map<String,String> values;
            try { values = callback.get(5, TimeUnit.MINUTES); }
            catch (TimeoutException exception) { throw new IOException("Sign-in timed out; start a fresh attempt"); }
            if (values.containsKey("error")) throw new IOException("Authorization was declined or failed; existing server credentials are unchanged");
            String issued = issuedClient(client, values.get("client_id"));
            String code = values.get("code");
            if (code == null || code.isBlank()) throw new IOException("Callback has no authorization code");
            var tokens = OAuthHttp.token(Map.of("grant_type", "authorization_code", "client_id", issued, "code", code,
                    "code_verifier", verifier, "redirect_uri", redirect, "resource", OAuthHttp.RESOURCE));
            var identity = IdToken.verify(tokens.get("id_token").getAsString(), issued, nonce);
            if (registration != null && !registration.subject().equals(identity.subject())) throw new IOException("Returned account differs from the selected registration");
            var metadata = new SubscriptionCredentials.Credentials(OAuthHttp.ISSUER, identity.subject(), identity.email(), issued, hostId,
                    null, null, null, "Bearer", 0, List.of());
            var credentials = SubscriptionCredentials.fromTokens(tokens, metadata, identity);
            CredentialFiles.write(registrationPath, SubscriptionCredentials.encode(metadata));
            CredentialFiles.write(output, SubscriptionCredentials.encode(credentials));
            System.out.println("Protected credentials saved to " + output + ". Transfer over SSH/SFTP to the server's auth/import.json, then run vibe auth import in the server console.");
            if (!credentials.scopes().contains("chatgpt.tokens.use.direct")) System.out.println("WARNING: Sign-in succeeded but ChatGPT plan usage was not granted. Enable plan permission before subscription inference.");
            System.out.println("Delete the local credential export after import. Keep its token-free .registration.json for later sign-ins.");
        } finally { server.stop(0); }
    }

    static String issuedClient(String requested, String returned) throws IOException {
        if (requested.equals("dynamic_agent_client")) {
            if (returned == null || !returned.startsWith("oaiapp_")) throw new IOException("Registration did not return an issued client ID");
            return returned;
        }
        if (returned != null && !returned.equals(requested)) throw new IOException("Callback client ID did not match this sign-in");
        return requested;
    }

    static Map<String,String> query(String raw) {
        Map<String,String> values = new HashMap<>();
        if (raw == null) return values;
        for (String field : raw.split("&")) {
            String[] pair = field.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate callback field");
        }
        return values;
    }

    private static String random() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package io.rcw.vibemine.ai.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class OAuthHttp {
    public static final String ISSUER = "https://auth.openai.com";
    public static final String RESOURCE = "https://api.openai.com/v1";
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private OAuthHttp() {}

    public static String form(Map<String,String> values) {
        return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    static JsonObject get(String endpoint) throws IOException {
        return json(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30)).GET().build());
    }

    public static JsonObject token(Map<String,String> fields) throws IOException {
        return json(HttpRequest.newBuilder(URI.create(ISSUER + "/api/accounts/oauth/token"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build());
    }

    static JsonObject json(HttpRequest request) throws IOException {
        try {
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String code = "oauth_error";
                try {
                    var body = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (body.has("error") && body.get("error").isJsonPrimitive()) code = body.get("error").getAsString();
                } catch (RuntimeException ignored) { }
                throw new Failure(response.statusCode(), code);
            }
            if (response.body().length() > 1_000_000) throw new IOException("OAuth response too large");
            return response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("OAuth request interrupted");
        } catch (RuntimeException exception) { throw new IOException("Invalid OAuth response"); }
    }

    public static final class Failure extends IOException {
        public final String code;
        Failure(int status, String code) {
            super("OAuth HTTP " + status + " (" + (code.matches("[a-z_]{1,80}") ? code : "oauth_error") + ")");
            this.code = code;
        }
    }
}

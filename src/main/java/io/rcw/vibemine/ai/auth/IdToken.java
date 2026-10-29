package io.rcw.vibemine.ai.auth;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;

/** OIDC ID tokens are verified, never merely decoded. */
public final class IdToken {
    private IdToken() {}
    public record Identity(String subject, String email) {}

    public static Identity verify(String jwt, String client, String nonce) throws IOException {
        return verify(jwt, client, nonce, OAuthHttp.get(OAuthHttp.ISSUER + "/.well-known/jwks.json"), Instant.now().getEpochSecond());
    }

    static Identity verify(String jwt, String client, String nonce, JsonObject jwks, long now) throws IOException {
        try {
            if (jwt == null || jwt.length() > 64000 || client == null || client.equals("dynamic_agent_client")) throw new IllegalArgumentException();
            String[] parts = jwt.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            var decoder = Base64.getUrlDecoder();
            JsonObject header = JsonParser.parseString(new String(decoder.decode(parts[0]), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"RS256".equals(header.get("alg").getAsString()) || header.has("crit")) throw new IllegalArgumentException();
            PublicKey key = null;
            for (JsonElement element : jwks.getAsJsonArray("keys")) {
                var jwk = element.getAsJsonObject();
                if (!header.get("kid").getAsString().equals(jwk.get("kid").getAsString()) || !"RSA".equals(jwk.get("kty").getAsString())) continue;
                if (jwk.has("use") && !"sig".equals(jwk.get("use").getAsString())) continue;
                if (jwk.has("alg") && !"RS256".equals(jwk.get("alg").getAsString())) continue;
                key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, decoder.decode(jwk.get("n").getAsString())), new BigInteger(1, decoder.decode(jwk.get("e").getAsString()))));
                break;
            }
            if (key == null) throw new IllegalArgumentException();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(decoder.decode(parts[2]))) throw new IllegalArgumentException();
            JsonObject claims = JsonParser.parseString(new String(decoder.decode(parts[1]), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!OAuthHttp.ISSUER.equals(claims.get("iss").getAsString()) || claims.get("exp").getAsLong() <= now) throw new IllegalArgumentException();
            if (claims.has("nbf") && claims.get("nbf").getAsLong() > now + 30) throw new IllegalArgumentException();
            if (claims.get("iat").getAsLong() > now + 30) throw new IllegalArgumentException();
            JsonElement audience = claims.get("aud");
            boolean matches = audience.isJsonArray() ? audience.getAsJsonArray().asList().stream().anyMatch(value -> client.equals(value.getAsString())) : client.equals(audience.getAsString());
            if (!matches || (audience.isJsonArray() && audience.getAsJsonArray().size() > 1 && !claims.has("azp"))) throw new IllegalArgumentException();
            if (claims.has("azp") && !client.equals(claims.get("azp").getAsString())) throw new IllegalArgumentException();
            if (nonce != null && !nonce.equals(claims.get("nonce").getAsString())) throw new IllegalArgumentException();
            String subject = claims.get("sub").getAsString();
            if (subject.isBlank()) throw new IllegalArgumentException();
            return new Identity(subject, claims.has("email") ? claims.get("email").getAsString() : "");
        } catch (GeneralSecurityException | RuntimeException exception) { throw new IOException("OpenAI identity token validation failed"); }
    }
}

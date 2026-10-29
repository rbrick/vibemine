package io.rcw.vibemine.ai.auth;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class IdTokenTest {
    private static final KeyPair KEYS = keys();
    private static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static JsonObject claims() {
        return JsonParser.parseString("{\"iss\":\"https://auth.openai.com\",\"sub\":\"owner\",\"email\":\"owner@example.com\",\"aud\":\"oaiapp_test\",\"iat\":900,\"exp\":2000,\"nonce\":\"attempt\"}").getAsJsonObject();
    }
    private static String jwt(JsonObject claims) throws Exception {
        String data = b64("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8)) + "." + b64(claims.toString().getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(KEYS.getPrivate()); signer.update(data.getBytes(StandardCharsets.US_ASCII));
        return data + "." + b64(signer.sign());
    }
    private static JsonObject jwks() {
        RSAPublicKey key = (RSAPublicKey) KEYS.getPublic();
        JsonObject jwk = new JsonObject(); jwk.addProperty("kid", "test"); jwk.addProperty("kty", "RSA"); jwk.addProperty("use", "sig");
        jwk.addProperty("n", b64(unsigned(key.getModulus().toByteArray()))); jwk.addProperty("e", b64(unsigned(key.getPublicExponent().toByteArray())));
        JsonArray keys = new JsonArray(); keys.add(jwk); JsonObject result = new JsonObject(); result.add("keys", keys); return result;
    }
    private static byte[] unsigned(byte[] bytes) { return bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes; }

    @Test void validatesSignatureAudienceIssuerExpiryAndNonce() throws Exception {
        String token = jwt(claims());
        assertEquals("owner", IdToken.verify(token, "oaiapp_test", "attempt", jwks(), 1000).subject());
        assertThrows(IOException.class, () -> IdToken.verify(token, "oaiapp_other", "attempt", jwks(), 1000));
        assertThrows(IOException.class, () -> IdToken.verify(token, "oaiapp_test", "wrong", jwks(), 1000));
        assertThrows(IOException.class, () -> IdToken.verify(token, "oaiapp_test", "attempt", jwks(), 2000));
        JsonObject wrongIssuer = claims(); wrongIssuer.addProperty("iss", "https://attacker.example");
        assertThrows(IOException.class, () -> IdToken.verify(jwt(wrongIssuer), "oaiapp_test", "attempt", jwks(), 1000));
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + b64("{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThrows(IOException.class, () -> IdToken.verify(forged, "oaiapp_test", "attempt", jwks(), 1000));
        assertThrows(IOException.class, () -> IdToken.verify("eyJhbGciOiJub25lIn0.e30.", "oaiapp_test", "attempt", jwks(), 1000));
    }

    @Test void multipleAudiencesRequireMatchingAuthorizedParty() throws Exception {
        JsonObject claims = claims(); claims.add("aud", JsonParser.parseString("[\"oaiapp_test\",\"other\"]"));
        assertThrows(IOException.class, () -> IdToken.verify(jwt(claims), "oaiapp_test", "attempt", jwks(), 1000));
        claims.addProperty("azp", "oaiapp_test");
        assertEquals("owner", IdToken.verify(jwt(claims), "oaiapp_test", "attempt", jwks(), 1000).subject());
    }
}

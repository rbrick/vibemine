package io.rcw.vibemine.ai.auth;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptLoginTest {
    @Test void initialRegistrationRequiresIssuedClientAndReturningCallbackCannotSwitchIt() throws IOException {
        assertEquals("oaiapp_test", ChatGptLogin.issuedClient("dynamic_agent_client", "oaiapp_test"));
        assertThrows(IOException.class, () -> ChatGptLogin.issuedClient("dynamic_agent_client", null));
        assertThrows(IOException.class, () -> ChatGptLogin.issuedClient("dynamic_agent_client", "dynamic_agent_client"));
        assertEquals("oaiapp_test", ChatGptLogin.issuedClient("oaiapp_test", null));
        assertThrows(IOException.class, () -> ChatGptLogin.issuedClient("oaiapp_test", "oaiapp_other"));
    }
    @Test void parsesEncodedCallbackAndRejectsAmbiguousParameters() {
        assertEquals("a+b", ChatGptLogin.query("code=a%2Bb&state=attempt").get("code"));
        assertThrows(IllegalArgumentException.class, () -> ChatGptLogin.query("state=one&state=two"));
    }
}

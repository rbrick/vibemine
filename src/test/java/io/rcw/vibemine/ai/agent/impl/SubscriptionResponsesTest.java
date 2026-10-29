package io.rcw.vibemine.ai.agent.impl;

import com.google.gson.*;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletion;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionResponsesTest {
    private static JsonObject object(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static JsonObject output() {
        return object("""
                {"id":"resp_1","model":"subscription-model","status":"completed","output":[
                  {"type":"reasoning","id":"rs_1","encrypted_content":"opaque"},
                  {"type":"function_call","id":"fc_1","call_id":"call_1","namespace":"vibemine","name":"file_read","arguments":"{}"}]}
                """);
    }

    @Test void acceptsTheInstalledSdkSerialization() throws Exception {
        var params = com.openai.models.chat.completions.ChatCompletionCreateParams.builder().model("api-model")
                .addSystemMessage("instructions").addUserMessage("hello").build();
        var serialized = object(ObjectMappers.jsonMapper().writeValueAsString(params._body()));
        var request = SubscriptionResponses.request(serialized, new SubscriptionResponses.Session(), "subscription-model");
        assertEquals("subscription-model", request.get("model").getAsString());
        assertEquals(1, request.getAsJsonArray("input").size());
        assertEquals("hello", request.getAsJsonArray("input").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test void sendsSubscriptionRequirementsAndRetainsReasoningAcrossToolCalls() {
        var session = new SubscriptionResponses.Session();
        JsonObject chat = object("""
                {"messages":[{"role":"system","content":"instructions"},{"role":"user","content":"hello"}],
                 "tools":[{"type":"function","function":{"name":"file_read","description":"Read source","parameters":{"type":"object"}}}]}
                """);
        var first = SubscriptionResponses.request(chat, session, "subscription-model");
        assertFalse(first.get("store").getAsBoolean());
        assertTrue(first.get("stream").getAsBoolean());
        assertEquals("subscription-model", first.get("model").getAsString());
        assertEquals(1, first.getAsJsonArray("input").size());
        assertEquals("user", first.getAsJsonArray("input").get(0).getAsJsonObject().get("role").getAsString());
        assertTrue(first.get("instructions").getAsString().contains("instructions"));
        assertEquals("namespace", first.getAsJsonArray("tools").get(0).getAsJsonObject().get("type").getAsString());
        SubscriptionResponses.retainOutput(output(), session);
        chat.getAsJsonArray("messages").add(object("{\"role\":\"assistant\",\"tool_calls\":[]}"));
        chat.getAsJsonArray("messages").add(object("{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"source\"}"));
        var next = SubscriptionResponses.request(chat, session, "subscription-model");
        var input = next.getAsJsonArray("input");
        assertEquals(4, input.size());
        assertEquals("opaque", input.get(1).getAsJsonObject().get("encrypted_content").getAsString());
        assertEquals("function_call_output", input.get(3).getAsJsonObject().get("type").getAsString());
        assertEquals("call_1", input.get(3).getAsJsonObject().get("call_id").getAsString());
    }

    @Test void doesNotExecuteToolsFromPartialOrFailedStreams() throws IOException {
        assertThrows(SubscriptionResponses.Failure.class, () -> SubscriptionResponses.completed("data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: [DONE]\n\n"));
        var quota = assertThrows(SubscriptionResponses.Failure.class, () -> SubscriptionResponses.completed(
                "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n"));
        assertTrue(quota.allowFallback);
        var incomplete = assertThrows(SubscriptionResponses.Failure.class, () -> SubscriptionResponses.completed("data: {\"type\":\"response.incomplete\"}\n\n"));
        assertFalse(incomplete.allowFallback);
        JsonObject event = new JsonObject(); event.addProperty("type", "response.completed"); event.add("response", output());
        assertEquals("resp_1", SubscriptionResponses.completed("data: " + event + "\r\n\r\n").get("id").getAsString());
    }

    @Test void mapsCompletedToolCallsAndTextToExistingAgentProtocol() throws Exception {
        var mapper = ObjectMappers.jsonMapper();
        ChatCompletion calls = mapper.readValue(SubscriptionResponses.chatCompletion(output()).toString(), ChatCompletion.class).validate();
        assertEquals("call_1", calls.choices().getFirst().message().toolCalls().orElseThrow().getFirst().asFunction().id());
        JsonObject response = object("{\"id\":\"resp_2\",\"model\":\"subscription-model\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"hello\"}]}]}");
        ChatCompletion text = mapper.readValue(SubscriptionResponses.chatCompletion(response).toString(), ChatCompletion.class).validate();
        assertEquals("hello", text.choices().getFirst().message().content().orElseThrow());
    }

    @Test void policyAndInvalidRequestErrorsDoNotSilentlySwitchBilling() {
        assertFalse(SubscriptionResponses.failure(403, "{\"detail\":\"regional restriction\"}", "req_test").allowFallback);
        assertFalse(SubscriptionResponses.failure(400, "{\"error\":{\"code\":\"subscription_sharing_unsupported_capability\"}}", "req_test").allowFallback);
        assertTrue(SubscriptionResponses.failure(429, "{}", "req_test").allowFallback);
        assertTrue(SubscriptionResponses.failure(503, "{}", "req_test").allowFallback);
    }
}

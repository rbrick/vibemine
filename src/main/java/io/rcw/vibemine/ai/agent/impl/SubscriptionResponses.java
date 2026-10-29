package io.rcw.vibemine.ai.agent.impl;

import com.google.gson.*;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.rcw.vibemine.ai.auth.SubscriptionCredentials;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Public subscription Responses endpoint, not ChatGPT's private backend API. */
public final class SubscriptionResponses {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final SubscriptionCredentials credentials;
    private final String model;
    public SubscriptionResponses(SubscriptionCredentials credentials, String model) {
        this.credentials = credentials;
        this.model = model;
    }

    public static final class Session {
        private final JsonArray input = new JsonArray();
        private int consumed;
        public boolean apiOnly;
    }

    public CompletableFuture<ChatCompletion> complete(ChatCompletionCreateParams params, Session session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (credentials == null) throw new IOException("ChatGPT authentication storage is unavailable; check server auth file permissions");
                String token = credentials.accessToken();
                JsonObject chat = JsonParser.parseString(ObjectMappers.jsonMapper().writeValueAsString(params._body())).getAsJsonObject();
                JsonObject request = request(chat, session, model);
                var pending = HTTP.sendAsync(HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                        .timeout(Duration.ofSeconds(120)).header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request.toString())).build(),
                        HttpResponse.BodyHandlers.ofString());
                HttpResponse<String> response;
                try { response = pending.get(120, TimeUnit.SECONDS); }
                catch (InterruptedException exception) {
                    pending.cancel(true);
                    Thread.currentThread().interrupt();
                    throw new IOException("Subscription request interrupted");
                } catch (TimeoutException exception) {
                    pending.cancel(true);
                    throw new IOException("Subscription request timed out");
                } catch (ExecutionException exception) {
                    throw new IOException("Subscription transport unavailable", exception);
                }
                String requestId = response.headers().firstValue("x-request-id").orElse("unavailable");
                if (response.statusCode() != 200) throw failure(response.statusCode(), response.body(), requestId);
                if (response.body().length() > 16_000_000) throw new Failure("Subscription response exceeded the size limit", false);
                JsonObject completed = completed(response.body(), requestId);
                retainOutput(completed, session);
                return ObjectMappers.jsonMapper().readValue(chatCompletion(completed).toString(), ChatCompletion.class);
            } catch (IOException exception) { throw new CompletionException(exception); }
        });
    }

    static void retainOutput(JsonObject response, Session session) {
        for (JsonElement item : response.getAsJsonArray("output")) session.input.add(item.deepCopy());
    }

    static JsonObject request(JsonObject chat, Session session, String model) {
        JsonArray messages = chat.getAsJsonArray("messages");
        StringBuilder instructions = new StringBuilder();
        for (JsonElement element : messages) {
            JsonObject message = element.getAsJsonObject();
            if ("system".equals(message.get("role").getAsString())) instructions.append(message.get("content").getAsString()).append('\n');
        }
        for (int i = session.consumed; i < messages.size(); i++) {
            JsonObject message = messages.get(i).getAsJsonObject();
            String role = message.get("role").getAsString();
            if (role.equals("system")) continue;
            // The full preceding Responses output is already retained, including encrypted reasoning.
            if (i == session.consumed && session.consumed > 0 && role.equals("assistant")) continue;
            if (role.equals("tool")) {
                JsonObject output = new JsonObject();
                output.addProperty("type", "function_call_output");
                output.addProperty("call_id", message.get("tool_call_id").getAsString());
                output.add("output", message.get("content"));
                session.input.add(output);
            } else {
                JsonObject input = new JsonObject();
                input.addProperty("role", role.equals("system") ? "developer" : role);
                input.add("content", message.get("content"));
                session.input.add(input);
            }
        }
        session.consumed = messages.size();
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("instructions", instructions.toString());
        request.addProperty("store", false);
        request.addProperty("stream", true);
        request.add("input", session.input.deepCopy());
        JsonArray include = new JsonArray();
        include.add("reasoning.encrypted_content");
        request.add("include", include);
        if (chat.has("tools") && !chat.getAsJsonArray("tools").isEmpty()) {
            JsonArray functions = new JsonArray();
            for (JsonElement element : chat.getAsJsonArray("tools")) {
                JsonObject function = element.getAsJsonObject().getAsJsonObject("function").deepCopy();
                function.addProperty("type", "function");
                functions.add(function);
            }
            JsonObject namespace = new JsonObject();
            namespace.addProperty("type", "namespace");
            namespace.addProperty("name", "vibemine");
            namespace.addProperty("description", "VibeMine server harness tools");
            namespace.add("tools", functions);
            JsonArray tools = new JsonArray();
            tools.add(namespace);
            request.add("tools", tools);
        }
        return request;
    }

    static JsonObject completed(String stream) throws IOException { return completed(stream, "unavailable"); }

    private static JsonObject completed(String stream, String requestId) throws IOException {
        StringBuilder data = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    JsonObject result = event(data.toString(), requestId);
                    data.setLength(0);
                    if (result != null) return result;
                } else if (line.startsWith("data:")) data.append(line.substring(5).stripLeading()).append('\n');
            }
            JsonObject result = event(data.toString(), requestId);
            if (result != null) return result;
        } catch (RuntimeException exception) {
            throw new Failure("Invalid subscription event stream", true);
        }
        throw new Failure("Subscription stream ended without response.completed", true);
    }

    private static JsonObject event(String data, String requestId) throws IOException {
        if (data.isBlank() || data.strip().equals("[DONE]")) return null;
        JsonObject event = JsonParser.parseString(data).getAsJsonObject();
        String type = event.get("type").getAsString();
        if (type.equals("response.failed") || type.equals("error")) throw failure(0, data, requestId);
        if (type.equals("response.incomplete")) throw new Failure("Subscription response was incomplete; try a smaller request", false);
        if (!type.equals("response.completed")) return null;
        JsonObject response = event.getAsJsonObject("response");
        if (!response.has("status") || !"completed".equals(response.get("status").getAsString()) || !response.has("output")) {
            throw new Failure("Subscription completion did not contain a completed response", true);
        }
        return response;
    }

    static JsonObject chatCompletion(JsonObject response) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        StringBuilder text = new StringBuilder();
        JsonArray calls = new JsonArray();
        for (JsonElement element : response.getAsJsonArray("output")) {
            JsonObject item = element.getAsJsonObject();
            String type = item.get("type").getAsString();
            if (type.equals("message")) {
                for (JsonElement part : item.getAsJsonArray("content")) {
                    JsonObject content = part.getAsJsonObject();
                    if ("output_text".equals(content.get("type").getAsString())) text.append(content.get("text").getAsString());
                    if ("refusal".equals(content.get("type").getAsString())) text.append(content.get("refusal").getAsString());
                }
            } else if (type.equals("function_call")) {
                if (item.has("namespace") && !item.get("namespace").isJsonNull() && !item.get("namespace").getAsString().equals("vibemine")) {
                    throw new Failure("Unexpected subscription tool namespace", false);
                }
                JsonObject call = new JsonObject();
                call.addProperty("type", "function");
                call.add("id", item.get("call_id"));
                JsonObject function = new JsonObject();
                function.add("name", item.get("name"));
                function.add("arguments", item.get("arguments"));
                call.add("function", function);
                calls.add(call);
            }
        }
        message.addProperty("content", text.toString());
        if (!calls.isEmpty()) message.add("tool_calls", calls);
        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.addProperty("finish_reason", calls.isEmpty() ? "stop" : "tool_calls");
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject chat = new JsonObject();
        chat.add("id", response.get("id"));
        chat.addProperty("object", "chat.completion");
        chat.addProperty("created", 0);
        chat.add("model", response.get("model"));
        chat.add("choices", choices);
        return chat;
    }

    static Failure failure(int status, String body, String requestId) {
        String code = "subscription_error";
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject();
            if (error.has("response")) error = error.getAsJsonObject("response");
            if (error.has("error") && error.get("error").isJsonObject()) error = error.getAsJsonObject("error");
            if (error.has("code") && error.get("code").isJsonPrimitive()) code = error.get("code").getAsString();
        } catch (RuntimeException ignored) { }
        boolean fallback = status == 401 || status == 429 || status >= 500 || Set.of(
                "subscription_sharing_user_not_eligible", "subscription_sharing_usage_limit_exceeded", "subscription_sharing_usage_unavailable",
                "subscription_sharing_invalid_user", "subscription_sharing_user_unavailable").contains(code);
        if (!code.matches("[a-z0-9_]{1,100}")) code = "subscription_error";
        if (!requestId.matches("[a-zA-Z0-9_-]{1,100}")) requestId = "unavailable";
        return new Failure("ChatGPT plan request failed: " + (status == 0 ? "stream event" : "HTTP " + status) + ", " + code + ", request " + requestId, fallback);
    }

    public static final class Failure extends IOException {
        public final boolean allowFallback;
        public Failure(String message, boolean allowFallback) { super(message); this.allowFallback = allowFallback; }
    }
}

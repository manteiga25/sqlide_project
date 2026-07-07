package com.example.sqlide.Assistant;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.google.customsearch.GoogleCustomWebSearchEngine;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public class AiAgentService {

    public interface Assistant {
        String chat(@dev.langchain4j.service.V("system") String systemMessage, @dev.langchain4j.service.UserMessage String userMessage);
        Result<String> chat(UserMessage userMessage);
    }

    private Assistant assistant;
    private ChatLanguageModel model;
    private String systemInstruction;

    public void initialize(ChatLanguageModel model, List<Object> tools) {
        initialize(model, tools, null);
    }

    public void initialize(ChatLanguageModel model, List<Object> tools, String systemInstruction) {
        this.model = model;
        this.systemInstruction = systemInstruction;
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20));

        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools);
        }

        this.assistant = builder.build();
    }

    public Result<String> chat(String message) {
        if (assistant == null) {
            throw new IllegalStateException("AiAgentService not initialized");
        }
        String response;
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            response = assistant.chat(systemInstruction, message);
        } else {
            response = assistant.chat("You are a helpful assistant.", message);
        }
        return Result.<String>builder()
                .content(response)
                .build();
    }

    public Result<String> chat(UserMessage userMessage) {
        if (assistant == null) {
            throw new IllegalStateException("AiAgentService not initialized");
        }
        return assistant.chat(userMessage);
    }

    public Result<String> chatWithImage(String message, String imageUrl) {
        UserMessage userMessage = UserMessage.from(
                TextContent.from(message),
                ImageContent.from(imageUrl)
        );
        return chat(userMessage);
    }

    public Result<String> chatWithImage(String message, String base64Data, String mimeType) {
        UserMessage userMessage = UserMessage.from(
                TextContent.from(message),
                ImageContent.from(base64Data, mimeType)
        );
        return chat(userMessage);
    }

    public static WebSearchEngine createWebSearchEngine(String googleApiKey, String googleCseId) {
        return GoogleCustomWebSearchEngine.builder()
                .apiKey(googleApiKey)
                .csi(googleCseId)
                .build();
    }

    public static List<String> fetchModels(String provider, String apiKey, String baseUrl) {
        List<String> models = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();

        try {
            switch (provider.toLowerCase()) {
                case "openai" -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.openai.com/v1/models"))
                            .header("Authorization", "Bearer " + apiKey)
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JSONObject json = new JSONObject(response.body());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        models.add(data.getJSONObject(i).getString("id"));
                    }
                }
                case "gemini" -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey))
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JSONObject json = new JSONObject(response.body());
                    JSONArray data = json.getJSONArray("models");
                    for (int i = 0; i < data.length(); i++) {
                        models.add(data.getJSONObject(i).getString("name").replace("models/", ""));
                    }
                }
                case "claude" -> {
                    // Anthropic doesn't have a simple "list models" API that returns all available ones for a key easily without pagination/params
                    // but we can provide some defaults or just common ones.
                    models.addAll(List.of("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-haiku-20240307"));
                }
                case "ollama" -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create((baseUrl != null ? baseUrl : "http://localhost:11434") + "/api/tags"))
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JSONObject json = new JSONObject(response.body());
                    JSONArray data = json.getJSONArray("models");
                    for (int i = 0; i < data.length(); i++) {
                        models.add(data.getJSONObject(i).getString("name"));
                    }
                }
                case "lmstudio" -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create((baseUrl != null ? baseUrl : "http://localhost:1234/v1") + "/models"))
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JSONObject json = new JSONObject(response.body());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        models.add(data.getJSONObject(i).getString("id"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return models;
    }

    public static ChatLanguageModel createModel(String provider, String apiKey, String modelName, String baseUrl) {
        return switch (provider.toLowerCase()) {
            case "openai" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName != null ? modelName : "gpt-4o")
                    .build();
            case "gemini" -> GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName != null ? modelName : "gemini-1.5-flash")
                    .build();
            case "claude" -> AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName != null ? modelName : "claude-3-5-sonnet-20240620")
                    .build();
            case "ollama" -> OllamaChatModel.builder()
                    .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                    .modelName(modelName != null ? modelName : "llama3")
                    .build();
            case "lmstudio" -> OpenAiChatModel.builder()
                    .baseUrl(baseUrl != null ? baseUrl : "http://localhost:1234/v1")
                    .apiKey("lm-studio")
                    .modelName(modelName)
                    .build();
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }
}

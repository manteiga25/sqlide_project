package com.example.sqlide.Assistant.llm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Pergunta ao provedor que modelos tem disponíveis.
 *
 * <p>Antes a lista era estática e envelhecia sozinha. Agora o Ollama devolve os modelos que
 * estão mesmo instalados na máquina, o LM Studio os que tem carregados, e os provedores
 * cloud a lista associada à chave. Se o pedido falhar volta-se às sugestões fixas, para o
 * painel nunca ficar vazio.</p>
 */
public final class ModelCatalog {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private ModelCatalog() {
    }

    /** O que a descoberta encontrou, ou porque não encontrou. */
    public record Result(List<String> models, boolean fromProvider, String message) {

        public boolean isEmpty() {
            return models.isEmpty();
        }
    }

    /**
     * Lista os modelos do provedor configurado.
     *
     * <p>Chamada a partir de uma thread de fundo: faz I/O de rede.</p>
     */
    public static Result discover(LlmConfig config) {
        try {
            List<String> models = switch (config.getProvider()) {
                case OLLAMA -> fromOllama(config);
                case OPENAI, LM_STUDIO, OPENAI_COMPATIBLE -> fromOpenAiCompatible(config);
                case ANTHROPIC -> fromAnthropic(config);
                case GEMINI -> fromGemini(config);
            };

            if (models.isEmpty()) {
                return new Result(config.getProvider().getSuggestedModels(), false,
                        "The provider returned no models; showing the built-in suggestions.");
            }
            return new Result(models, true, models.size() + " model(s) found.");

        } catch (Exception e) {
            return new Result(config.getProvider().getSuggestedModels(), false,
                    "Could not reach " + config.getProvider().getDisplayName() + ": " + describe(e));
        }
    }

    /** {@code /api/tags} devolve os modelos descarregados para a máquina. */
    private static List<String> fromOllama(LlmConfig config) throws Exception {
        String base = trimSlash(orDefault(config.getBaseUrl(), config.getProvider().getDefaultBaseUrl()));
        JSONObject body = new JSONObject(get(base + "/api/tags", null));

        List<String> models = new ArrayList<>();
        JSONArray array = body.optJSONArray("models");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String name = array.getJSONObject(i).optString("name", null);
                if (name != null && !name.isBlank()) models.add(name);
            }
        }
        return models;
    }

    /** O endpoint {@code /models} da OpenAI, que o LM Studio também serve. */
    private static List<String> fromOpenAiCompatible(LlmConfig config) throws Exception {
        String base = trimSlash(orDefault(config.getBaseUrl(), config.getProvider().getDefaultBaseUrl()));
        String key = config.getApiKey();
        JSONObject body = new JSONObject(get(base + "/models",
                request -> {
                    if (key != null && !key.isBlank()) request.header("Authorization", "Bearer " + key);
                }));

        List<String> models = new ArrayList<>();
        JSONArray array = body.optJSONArray("data");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String id = array.getJSONObject(i).optString("id", null);
                if (id != null && !id.isBlank()) models.add(id);
            }
        }
        models.sort(String::compareTo);
        return models;
    }

    private static List<String> fromAnthropic(LlmConfig config) throws Exception {
        JSONObject body = new JSONObject(get("https://api.anthropic.com/v1/models?limit=100",
                request -> request
                        .header("x-api-key", orDefault(config.getApiKey(), ""))
                        .header("anthropic-version", "2023-06-01")));

        List<String> models = new ArrayList<>();
        JSONArray array = body.optJSONArray("data");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String id = array.getJSONObject(i).optString("id", null);
                if (id != null && !id.isBlank()) models.add(id);
            }
        }
        return models;
    }

    private static List<String> fromGemini(LlmConfig config) throws Exception {
        JSONObject body = new JSONObject(get(
                "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key="
                        + orDefault(config.getApiKey(), ""), null));

        List<String> models = new ArrayList<>();
        JSONArray array = body.optJSONArray("models");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject model = array.getJSONObject(i);
                // Só interessam os que sabem gerar conteúdo; o resto são embeddings.
                JSONArray methods = model.optJSONArray("supportedGenerationMethods");
                if (methods != null && !methods.toList().contains("generateContent")) continue;

                String name = model.optString("name", "");
                // A API devolve "models/gemini-2.5-flash"; o builder quer só o nome.
                if (name.startsWith("models/")) name = name.substring("models/".length());
                if (!name.isBlank()) models.add(name);
            }
        }
        return models;
    }

    private interface HeaderDecorator {
        void apply(HttpRequest.Builder request);
    }

    private static String get(String url, HeaderDecorator headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET();

        if (headers != null) headers.apply(request);

        HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " — " + shorten(response.body()));
        }
        return response.body();
    }

    /**
     * Uma frase legível para o erro.
     *
     * <p>Uma ligação recusada chega como {@code ConnectException} sem mensagem nenhuma, e
     * mostrar "null" ao utilizador não diz nada — nesse caso usa-se o nome do tipo.</p>
     */
    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();

        String message = root.getMessage();
        if (message != null && !message.isBlank()) return message;

        // Um servidor local desligado chega por vários caminhos consoante o sistema.
        if (root instanceof java.net.ConnectException
                || root instanceof java.nio.channels.ClosedChannelException) {
            return "connection refused — is the server running?";
        }
        if (root instanceof java.net.http.HttpConnectTimeoutException) return "connection timed out";
        return root.getClass().getSimpleName();
    }

    private static String shorten(String body) {
        if (body == null) return "";
        String text = body.strip().replaceAll("\\s+", " ");
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

}

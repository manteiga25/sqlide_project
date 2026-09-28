package com.example.sqlide.Assistant.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

/**
 * Constrói o {@link ChatModel} correspondente à configuração escolhida pelo utilizador.
 *
 * <p>LM Studio e qualquer outro servidor OpenAI-compatible reutilizam o cliente da OpenAI,
 * mudando só a URL base — é assim que o LM Studio expõe a sua API.</p>
 */
public final class ChatModelFactory {

    private ChatModelFactory() {
    }

    public static ChatModel create(LlmConfig config) {
        String problem = config.validate();
        if (problem != null) throw new IllegalStateException(problem);

        Duration timeout = Duration.ofSeconds(config.getTimeoutSeconds());

        return switch (config.getProvider()) {
            case ANTHROPIC -> {
                var builder = AnthropicChatModel.builder()
                        .apiKey(config.getApiKey())
                        .modelName(config.getModelName())
                        .maxTokens(config.getMaxTokens())
                        .timeout(timeout);

                if (config.isThinkingEnabled()) {
                    // Com raciocínio ligado a API exige temperatura 1 e um orçamento de
                    // tokens menor que o máximo da resposta.
                    builder.thinkingType("enabled")
                            .thinkingBudgetTokens(Math.min(config.getThinkingBudgetTokens(),
                                    Math.max(1024, config.getMaxTokens() - 1024)))
                            .returnThinking(true)
                            .sendThinking(true);
                } else {
                    builder.temperature(config.getTemperature());
                }

                yield builder.build();
            }

            case GEMINI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .temperature(config.getTemperature())
                    .maxOutputTokens(config.getMaxTokens())
                    .timeout(timeout)
                    .build();

            case OLLAMA -> {
                var builder = OllamaChatModel.builder()
                        .baseUrl(config.getBaseUrl())
                        .modelName(config.getModelName())
                        .temperature(config.getTemperature())
                        .timeout(timeout);

                // Só os modelos preparados para isso respondem ao think; nos outros o
                // Ollama ignora o campo em vez de falhar.
                if (config.isThinkingEnabled()) builder.think(true).returnThinking(true);

                yield builder.build();
            }

            // OPENAI, LM_STUDIO e OPENAI_COMPATIBLE partilham o mesmo cliente.
            case OPENAI, LM_STUDIO, OPENAI_COMPATIBLE -> OpenAiChatModel.builder()
                    .baseUrl(resolveBaseUrl(config))
                    .apiKey(resolveApiKey(config))
                    .modelName(config.getModelName())
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .timeout(timeout)
                    .build();
        };
    }

    private static String resolveBaseUrl(LlmConfig config) {
        String url = config.getBaseUrl();
        if (url == null || url.isBlank()) return config.getProvider().getDefaultBaseUrl();
        return url;
    }

    /**
     * Servidores locais aceitam qualquer chave mas rejeitam a ausência do cabeçalho,
     * por isso mandamos um valor de preenchimento quando o utilizador não indica nada.
     */
    private static String resolveApiKey(LlmConfig config) {
        String key = config.getApiKey();
        if (key != null && !key.isBlank()) return key;
        return "not-needed";
    }

}

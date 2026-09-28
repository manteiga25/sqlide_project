package com.example.sqlide.Assistant.llm;

import java.util.List;

/**
 * Provedores de LLM suportados pelo assistente.
 *
 * <p>Cada constante descreve o que o painel de definições precisa de pedir ao utilizador
 * (chave, URL base) e sugere alguns modelos. As sugestões são apenas isso — o campo do
 * modelo é livre, para não ficar preso a uma lista que envelhece.</p>
 */
public enum LlmProvider {

    ANTHROPIC(
            "Claude (Anthropic)",
            true,
            false,
            "https://api.anthropic.com/v1",
            "claude-sonnet-4-5",
            List.of("claude-opus-4-5", "claude-sonnet-4-5", "claude-haiku-4-5")),

    OPENAI(
            "OpenAI",
            true,
            false,
            "https://api.openai.com/v1",
            "gpt-4o",
            List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "o4-mini")),

    GEMINI(
            "Google Gemini",
            true,
            false,
            null, // o SDK do Gemini gere o endpoint por si
            "gemini-2.5-flash",
            List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash")),

    OLLAMA(
            "Ollama (local)",
            false,
            true,
            "http://localhost:11434",
            "llama3.1",
            List.of("llama3.1", "qwen2.5-coder", "mistral", "gemma3")),

    LM_STUDIO(
            "LM Studio (local)",
            false,
            true,
            "http://localhost:1234/v1",
            "local-model",
            List.of("local-model")),

    OPENAI_COMPATIBLE(
            "Outro (OpenAI-compatible)",
            true,
            true,
            "http://localhost:8000/v1",
            "",
            List.of());

    private final String displayName;
    private final boolean requiresApiKey;
    private final boolean requiresBaseUrl;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final List<String> suggestedModels;

    LlmProvider(String displayName, boolean requiresApiKey, boolean requiresBaseUrl,
                String defaultBaseUrl, String defaultModel, List<String> suggestedModels) {
        this.displayName = displayName;
        this.requiresApiKey = requiresApiKey;
        this.requiresBaseUrl = requiresBaseUrl;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.suggestedModels = suggestedModels;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** True se o provedor não funciona sem chave — o painel bloqueia o "guardar" nesse caso. */
    public boolean requiresApiKey() {
        return requiresApiKey;
    }

    /** True se o endereço do servidor faz parte da configuração (locais e compatíveis). */
    public boolean requiresBaseUrl() {
        return requiresBaseUrl;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public List<String> getSuggestedModels() {
        return suggestedModels;
    }

    /** Provedores servidos pelo cliente OpenAI, mudando apenas a URL base. */
    public boolean isOpenAiCompatible() {
        return this == OPENAI || this == LM_STUDIO || this == OPENAI_COMPATIBLE;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static LlmProvider fromName(String name, LlmProvider fallback) {
        if (name == null) return fallback;
        for (LlmProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(name)) return provider;
        }
        return fallback;
    }

}

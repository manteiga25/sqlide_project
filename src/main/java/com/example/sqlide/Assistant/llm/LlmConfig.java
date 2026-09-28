package com.example.sqlide.Assistant.llm;

import org.json.JSONObject;

/**
 * Definições que o utilizador escolhe para o assistente: provedor, modelo, chave e endpoint.
 *
 * <p>Nada aqui é imposto pela aplicação — o modelo é texto livre e a chave é sempre
 * introduzida à mão, para que qualquer combinação provedor/modelo funcione.</p>
 */
public class LlmConfig {

    private LlmProvider provider = LlmProvider.OLLAMA;
    private String modelName = LlmProvider.OLLAMA.getDefaultModel();
    private String apiKey = "";
    private String baseUrl = LlmProvider.OLLAMA.getDefaultBaseUrl();
    private double temperature = 0.2;
    private int maxTokens = 4096;
    private int timeoutSeconds = 120;
    private boolean toolsEnabled = true;
    private boolean thinkingEnabled = false;
    private int thinkingBudgetTokens = 4096;
    private boolean webSearchEnabled = false;

    public LlmConfig() {
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public void setProvider(LlmProvider provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    public void setToolsEnabled(boolean toolsEnabled) {
        this.toolsEnabled = toolsEnabled;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public int getThinkingBudgetTokens() {
        return thinkingBudgetTokens;
    }

    public void setThinkingBudgetTokens(int thinkingBudgetTokens) {
        this.thinkingBudgetTokens = thinkingBudgetTokens;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    /**
     * True se o provedor sabe mesmo pensar em voz alta.
     *
     * <p>Só o Anthropic e o Ollama expõem isto no builder. Nos outros o interruptor não
     * teria efeito nenhum, e é melhor dizê-lo do que fingir que funciona.</p>
     */
    public boolean supportsThinking() {
        return provider == LlmProvider.ANTHROPIC || provider == LlmProvider.OLLAMA;
    }

    /**
     * Descreve o que falta para a configuração poder ser usada, ou null se estiver pronta.
     * É isto que o painel de definições mostra por baixo do botão de guardar.
     */
    public String validate() {
        if (provider == null) return "Choose a provider.";
        if (modelName == null || modelName.isBlank()) return "Choose or type a model name.";
        if (provider.requiresApiKey() && (apiKey == null || apiKey.isBlank())) {
            return provider.getDisplayName() + " needs an API key.";
        }
        if (provider.requiresBaseUrl() && (baseUrl == null || baseUrl.isBlank())) {
            return provider.getDisplayName() + " needs a server URL.";
        }
        return null;
    }

    public boolean isUsable() {
        return validate() == null;
    }

    public LlmConfig copy() {
        LlmConfig copy = new LlmConfig();
        copy.provider = provider;
        copy.modelName = modelName;
        copy.apiKey = apiKey;
        copy.baseUrl = baseUrl;
        copy.temperature = temperature;
        copy.maxTokens = maxTokens;
        copy.timeoutSeconds = timeoutSeconds;
        copy.toolsEnabled = toolsEnabled;
        copy.thinkingEnabled = thinkingEnabled;
        copy.thinkingBudgetTokens = thinkingBudgetTokens;
        copy.webSearchEnabled = webSearchEnabled;
        return copy;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("provider", provider.name());
        json.put("model", modelName == null ? "" : modelName);
        json.put("apiKey", apiKey == null ? "" : apiKey);
        json.put("baseUrl", baseUrl == null ? "" : baseUrl);
        json.put("temperature", temperature);
        json.put("maxTokens", maxTokens);
        json.put("timeoutSeconds", timeoutSeconds);
        json.put("tools", toolsEnabled);
        json.put("thinking", thinkingEnabled);
        json.put("thinkingBudget", thinkingBudgetTokens);
        json.put("webSearch", webSearchEnabled);
        return json;
    }

    public static LlmConfig fromJson(JSONObject json) {
        LlmConfig config = new LlmConfig();
        config.provider = LlmProvider.fromName(json.optString("provider", null), LlmProvider.OLLAMA);
        config.modelName = json.optString("model", config.provider.getDefaultModel());
        config.apiKey = json.optString("apiKey", "");
        config.baseUrl = json.optString("baseUrl", config.provider.getDefaultBaseUrl());
        config.temperature = json.optDouble("temperature", 0.2);
        config.maxTokens = json.optInt("maxTokens", 4096);
        config.timeoutSeconds = json.optInt("timeoutSeconds", 120);
        config.toolsEnabled = json.optBoolean("tools", true);
        config.thinkingEnabled = json.optBoolean("thinking", false);
        config.thinkingBudgetTokens = json.optInt("thinkingBudget", 4096);
        config.webSearchEnabled = json.optBoolean("webSearch", false);
        return config;
    }

    /** Repõe modelo e URL nos valores por omissão do provedor, ao trocar de provedor no painel. */
    public void applyProviderDefaults(LlmProvider newProvider) {
        this.provider = newProvider;
        this.modelName = newProvider.getDefaultModel();
        this.baseUrl = newProvider.getDefaultBaseUrl();
    }

}

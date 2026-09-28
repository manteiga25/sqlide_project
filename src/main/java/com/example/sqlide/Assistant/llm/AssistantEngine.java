package com.example.sqlide.Assistant.llm;

import com.example.sqlide.requestInterface;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ponto único de contacto entre a UI do assistente e o LangChain4j.
 *
 * <p>Guarda a configuração atual, constrói o modelo à medida que é preciso e reconstrói-o
 * quando o utilizador troca de provedor. O modelo só é instanciado no primeiro pedido:
 * abrir a janela do assistente sem chave configurada não deve rebentar nada.</p>
 */
public class AssistantEngine {

    private static final int MEMORY_SIZE = 40;

    private final SqlAssistantTools tools;
    private final WebSearchTools webSearch = new WebSearchTools();
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(MEMORY_SIZE);

    private LlmConfig config = LlmConfigStore.load();
    private SqlAssistant assistant;
    private boolean stale = true;

    public AssistantEngine(requestInterface request) {
        this.tools = new SqlAssistantTools(request);
    }

    /** Recebe o nome da ferramenta em execução, para a UI mostrar o progresso. */
    public void setActivityListener(Consumer<String> listener) {
        tools.setActivityListener(listener);
        webSearch.setActivityListener(listener);
    }

    public LlmConfig getConfig() {
        return config;
    }

    /** Aplica novas definições e força a reconstrução do modelo no próximo pedido. */
    public synchronized void updateConfig(LlmConfig newConfig) {
        this.config = newConfig;
        this.stale = true;
    }

    public boolean isConfigured() {
        return config.isUsable();
    }

    /** Descreve o que falta configurar, ou null se estiver pronto a usar. */
    public String describeMissingConfiguration() {
        return config.validate();
    }

    /**
     * Envia a mensagem ao modelo e devolve a resposta final, já depois de resolvido
     * qualquer ciclo de chamada de ferramentas.
     *
     * @throws IllegalStateException se ainda não houver provedor configurado
     */
    public String ask(String userMessage) {
        return service().chat(userMessage);
    }

    private synchronized SqlAssistant service() {
        if (assistant == null || stale) {
            String problem = config.validate();
            if (problem != null) throw new IllegalStateException(problem);

            ChatModel model = ChatModelFactory.create(config);

            AiServices<SqlAssistant> builder = AiServices.builder(SqlAssistant.class)
                    .chatModel(model)
                    .chatMemory(memory);

            // As duas famílias de ferramentas ligam-se de forma independente: dá para ter
            // pesquisa sem dar acesso de escrita à base de dados, e vice-versa.
            List<Object> active = new ArrayList<>();
            if (config.isToolsEnabled()) active.add(tools);
            if (config.isWebSearchEnabled()) active.add(webSearch);
            if (!active.isEmpty()) builder = builder.tools(active);

            assistant = builder.build();
            stale = false;
        }
        return assistant;
    }

    /**
     * Repõe a memória com uma conversa já gravada, para que o modelo continue de onde ficou
     * ao reabrir um chat antigo.
     */
    public void restoreUserTurn(String message) {
        memory.add(UserMessage.from(message));
    }

    public void restoreAssistantTurn(String message) {
        memory.add(AiMessage.from(message));
    }

    public void clearMemory() {
        memory.clear();
    }

}

package com.example.sqlide.Assistant.llm;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Pesquisa na web, disponibilizada ao modelo como ferramenta.
 *
 * <p>O botão "Search" da versão original só funcionava com o Gemini, porque usava o
 * <em>grounding</em> nativo da Google. Como ferramenta normal, funciona com qualquer
 * provedor que saiba chamar ferramentas — incluindo os modelos locais do Ollama e do
 * LM Studio, que não têm pesquisa nativa nenhuma.</p>
 */
public class WebSearchTools {

    private static final String ENDPOINT = "https://lite.duckduckgo.com/lite/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36";

    private static final int MAX_RESULTS = 8;
    private static final int TIMEOUT_MS = 15_000;

    private Consumer<String> activityListener = _ -> {
    };

    public void setActivityListener(Consumer<String> activityListener) {
        this.activityListener = activityListener == null ? _ -> {
        } : activityListener;
    }

    @Tool("""
            Searches the web and returns the top results with their titles, links and snippets.
            Use it for things outside the database: documentation, SQL syntax for a specific
            engine version, error messages, or anything you are not sure about.
            Search once with a focused query rather than many times with vague ones.""")
    public String searchWeb(@P("What to search for") String query) {
        if (query == null || query.isBlank()) return "Empty query.";
        activityListener.accept("Searching the web for \"" + query + "\"");

        try {
            Document document = Jsoup.connect(ENDPOINT)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .data("q", query)
                    .post();

            String results = extractResults(document);
            return results.isEmpty()
                    ? "No results for \"" + query + "\"."
                    : results;
        } catch (IOException e) {
            // Devolver o erro ao modelo em vez de rebentar deixa-o dizer ao utilizador
            // que a pesquisa falhou, em vez de a conversa morrer.
            return "Web search failed: " + e.getMessage();
        }
    }

    /**
     * A versão "lite" do DuckDuckGo devolve uma tabela simples: uma linha com a ligação,
     * outra com o excerto. É o formato mais estável para ler sem JavaScript.
     */
    private String extractResults(Document document) {
        StringBuilder text = new StringBuilder();
        Elements links = document.select("a.result-link");
        Elements snippets = document.select("td.result-snippet");

        int count = Math.min(links.size(), MAX_RESULTS);
        for (int i = 0; i < count; i++) {
            Element link = links.get(i);
            text.append(i + 1).append(". ").append(link.text().strip()).append('\n');
            text.append("   ").append(resolveHref(link.attr("href"))).append('\n');
            if (i < snippets.size()) {
                text.append("   ").append(snippets.get(i).text().strip()).append('\n');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** O DuckDuckGo embrulha os destinos num redireccionador; devolve-se o URL real. */
    private String resolveHref(String href) {
        if (href == null || href.isBlank()) return "";
        int marker = href.indexOf("uddg=");
        if (marker < 0) return href;

        String encoded = href.substring(marker + "uddg=".length());
        int ampersand = encoded.indexOf('&');
        if (ampersand > 0) encoded = encoded.substring(0, ampersand);

        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return href;
        }
    }

}

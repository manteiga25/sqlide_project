package com.example.sqlide.Assistant;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface AssistantCodeInterface {

    String url = "http://127.0.0.1:8000/coder/";

    HttpClient client = HttpClient.newHttpClient();

    default String predict(String prompt) {

        String jsonInputString = String.format("{\"code\": \"%s\"}", escapeJson(prompt));

        JSONObject json = new JSONObject();
        json.put("code", prompt);

        System.out.println(prompt);

        // Criar a requisição HTTP POST com JSON body
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json") // ⚠️ Definir o Content-Type como JSON é crucial!
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {

            // Enviar a requisição e obter a resposta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Imprimir status e resposta
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Resposta: " + response.body());

            return new JSONObject(response.body()).getString("predict");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String escapeJson(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}

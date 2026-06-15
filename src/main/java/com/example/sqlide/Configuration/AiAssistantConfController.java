package com.example.sqlide.Configuration;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class AiAssistantConfController {

    @FXML
    private ComboBox<String> providerCombo;
    @FXML
    private TextField apiKeyField;
    @FXML
    private TextField modelNameField;
    @FXML
    private TextField baseUrlField;

    private static final String SETTINGS_FILE = "ai_settings.json";

    @FXML
    public void initialize() {
        providerCombo.getItems().addAll("OpenAI", "Gemini", "Claude", "Ollama", "LM Studio");
        loadSettings();
    }

    private void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try {
                String content = Files.readString(file.toPath());
                JSONObject json = new JSONObject(content);
                providerCombo.setValue(json.optString("provider", "Gemini"));
                apiKeyField.setText(json.optString("apiKey", ""));
                modelNameField.setText(json.optString("modelName", "gemini-1.5-flash"));
                baseUrlField.setText(json.optString("baseUrl", ""));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleSave() {
        JSONObject json = new JSONObject();
        json.put("provider", providerCombo.getValue());
        json.put("apiKey", apiKeyField.getText());
        json.put("modelName", modelNameField.getText());
        json.put("baseUrl", baseUrlField.getText());

        try (FileWriter writer = new FileWriter(SETTINGS_FILE)) {
            writer.write(json.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

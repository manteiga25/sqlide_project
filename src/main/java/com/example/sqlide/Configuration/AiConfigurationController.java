package com.example.sqlide.Configuration;

import com.example.sqlide.Assistant.AiAgentService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import java.io.*;
import java.util.List;
import java.util.Properties;

public class AiConfigurationController {

    @FXML
    private ComboBox<String> ProviderCombo;

    @FXML
    private PasswordField ApiKeyField;

    @FXML
    private ComboBox<String> ModelCombo;

    @FXML
    private TextField BaseUrlField;

    @FXML
    private TextField GoogleCseIdField;

    private static final String CONFIG_FILE = "ai_config.properties";

    @FXML
    public void initialize() {
        ProviderCombo.getItems().addAll("OpenAI", "Gemini", "Claude", "Ollama", "LMStudio");
        ProviderCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateModels();
            }
        });
        load();
    }

    private void updateModels() {
        String provider = ProviderCombo.getValue();
        String apiKey = ApiKeyField.getText();
        String baseUrl = BaseUrlField.getText();

        if (provider != null && !provider.isEmpty()) {
            Thread.ofVirtual().start(() -> {
                List<String> models = AiAgentService.fetchModels(provider, apiKey, baseUrl);
                Platform.runLater(() -> {
                    String current = ModelCombo.getValue();
                    ModelCombo.getItems().clear();
                    ModelCombo.getItems().addAll(models);
                    if (current != null && !current.isEmpty()) {
                        ModelCombo.setValue(current);
                    }
                });
            });
        }
    }

    @FXML
    private void save() {
        Properties props = new Properties();
        props.setProperty("provider", ProviderCombo.getValue() != null ? ProviderCombo.getValue() : "");
        props.setProperty("apiKey", ApiKeyField.getText());
        props.setProperty("modelName", ModelCombo.getValue() != null ? ModelCombo.getValue() : "");
        props.setProperty("baseUrl", BaseUrlField.getText());
        props.setProperty("googleCseId", GoogleCseIdField.getText());

        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            props.store(output, null);
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    private void load() {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            props.load(input);
            ProviderCombo.setValue(props.getProperty("provider"));
            ApiKeyField.setText(props.getProperty("apiKey"));
            ModelCombo.setValue(props.getProperty("modelName"));
            BaseUrlField.setText(props.getProperty("baseUrl"));
            GoogleCseIdField.setText(props.getProperty("googleCseId"));
            updateModels();
        } catch (IOException ex) {
            // File not found, use defaults
        }
    }

    private static Properties cachedProps;

    public static Properties getAIConfig() {
        if (cachedProps != null) return cachedProps;
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            props.load(input);
            cachedProps = props;
        } catch (IOException ex) {
        }
        return props;
    }

    public static void clearCache() {
        cachedProps = null;
    }
}

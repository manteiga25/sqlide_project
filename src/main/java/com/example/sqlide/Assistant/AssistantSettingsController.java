package com.example.sqlide.Assistant;

import com.example.sqlide.Assistant.llm.ChatModelFactory;
import com.example.sqlide.Assistant.llm.LlmConfig;
import com.example.sqlide.Assistant.llm.LlmConfigStore;
import com.example.sqlide.Assistant.llm.LlmProvider;
import com.example.sqlide.Assistant.llm.ModelCatalog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

/**
 * Janela de definições do assistente.
 *
 * <p>Vive por si, fora da janela de conversa: escolher o modelo é uma decisão de
 * configuração, não algo que se faz a meio de um diálogo. O estado é sempre relido do
 * disco quando a janela abre — antes ficava preso ao que estava em memória, por isso
 * fechar e reabrir mostrava valores desatualizados.</p>
 */
public class AssistantSettingsController {

    @FXML
    private ChoiceBox<LlmProvider> providerBox;
    @FXML
    private ComboBox<String> modelBox;
    @FXML
    private PasswordField apiKeyField;
    @FXML
    private TextField apiKeyPlainField, baseUrlField;
    @FXML
    private ToggleButton revealKeyButton;
    @FXML
    private HBox apiKeyRow, baseUrlRow, thinkingBudgetRow;
    @FXML
    private Label modelsHintLabel, thinkingHintLabel, statusLabel, storagePathLabel;
    @FXML
    private CheckBox toolsCheck, webSearchCheck, thinkingCheck;
    @FXML
    private Spinner<Double> temperatureSpinner;
    @FXML
    private Spinner<Integer> maxTokensSpinner, timeoutSpinner, thinkingBudgetSpinner;
    @FXML
    private Button saveButton, testButton, refreshModelsButton;

    private Stage dialogStage;
    private LlmConfig working;
    private LlmConfig saved;

    /** Evita que os listeners disparem enquanto os campos estão a ser preenchidos. */
    private boolean loading = false;

    @FXML
    private void initialize() {
        providerBox.setItems(FXCollections.observableArrayList(LlmProvider.values()));

        temperatureSpinner.setValueFactory(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 2.0, 0.2, 0.1));
        maxTokensSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(256, 200_000, 4096, 256));
        timeoutSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 900, 120, 10));
        thinkingBudgetSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 64_000, 4096, 1024));

        apiKeyPlainField.textProperty().bindBidirectional(apiKeyField.textProperty());
        revealKeyButton.selectedProperty().addListener((_, _, revealed) -> {
            apiKeyPlainField.setVisible(revealed);
            apiKeyPlainField.setManaged(revealed);
            apiKeyField.setVisible(!revealed);
            apiKeyField.setManaged(!revealed);
            revealKeyButton.setText(revealed ? "Hide" : "Show");
        });

        providerBox.getSelectionModel().selectedItemProperty().addListener((_, previous, provider) -> {
            if (provider == null || loading) return;
            if (previous != null && previous != provider) {
                working.applyProviderDefaults(provider);
                modelBox.setValue(provider.getDefaultModel());
                baseUrlField.setText(nullToEmpty(provider.getDefaultBaseUrl()));
            }
            modelBox.setItems(FXCollections.observableArrayList(provider.getSuggestedModels()));
            applyProviderVisibility(provider);
            refreshStatus();
        });

        thinkingCheck.selectedProperty().addListener((_, _, _) -> {
            applyThinkingVisibility();
            refreshStatus();
        });

        modelBox.getEditor().textProperty().addListener((_, _, _) -> refreshStatus());
        apiKeyField.textProperty().addListener((_, _, _) -> refreshStatus());
        baseUrlField.textProperty().addListener((_, _, _) -> refreshStatus());

        storagePathLabel.setText("Stored in " + LlmConfigStore.getFile());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /** Preenche o painel com a configuração indicada. */
    public void setConfig(LlmConfig config) {
        loading = true;
        try {
            this.working = config.copy();

            providerBox.getSelectionModel().select(working.getProvider());
            modelBox.setItems(FXCollections.observableArrayList(working.getProvider().getSuggestedModels()));
            modelBox.setValue(working.getModelName());
            apiKeyField.setText(nullToEmpty(working.getApiKey()));
            baseUrlField.setText(nullToEmpty(working.getBaseUrl()));

            toolsCheck.setSelected(working.isToolsEnabled());
            webSearchCheck.setSelected(working.isWebSearchEnabled());
            thinkingCheck.setSelected(working.isThinkingEnabled());

            temperatureSpinner.getValueFactory().setValue(working.getTemperature());
            maxTokensSpinner.getValueFactory().setValue(working.getMaxTokens());
            timeoutSpinner.getValueFactory().setValue(working.getTimeoutSeconds());
            thinkingBudgetSpinner.getValueFactory().setValue(working.getThinkingBudgetTokens());

            applyProviderVisibility(working.getProvider());
            applyThinkingVisibility();
        } finally {
            loading = false;
        }
        refreshStatus();
    }

    /** A configuração gravada, ou null se o utilizador cancelou. */
    public LlmConfig getSavedConfig() {
        return saved;
    }

    private void applyProviderVisibility(LlmProvider provider) {
        setRowVisible(apiKeyRow, provider.requiresApiKey());
        setRowVisible(baseUrlRow, provider.requiresBaseUrl());

        boolean thinkingSupported = provider == LlmProvider.ANTHROPIC || provider == LlmProvider.OLLAMA;
        thinkingCheck.setDisable(!thinkingSupported);
        thinkingHintLabel.setText(thinkingSupported
                ? "Extended reasoning. Supported by Claude and by Ollama models built for it."
                : provider.getDisplayName() + " does not expose a thinking switch, so this has no effect.");
        if (!thinkingSupported) thinkingCheck.setSelected(false);
        applyThinkingVisibility();
    }

    private void applyThinkingVisibility() {
        boolean show = thinkingCheck.isSelected() && !thinkingCheck.isDisable();
        setRowVisible(thinkingBudgetRow, show);
    }

    /** Esconder e desocupar: um nó só invisível continuaria a ocupar espaço na coluna. */
    private static void setRowVisible(HBox row, boolean visible) {
        row.setVisible(visible);
        row.setManaged(visible);
    }

    private LlmConfig collect() {
        working.setProvider(providerBox.getValue());

        String typed = modelBox.getEditor().getText();
        working.setModelName(typed == null || typed.isBlank() ? modelBox.getValue() : typed.trim());

        working.setApiKey(apiKeyField.getText());
        working.setBaseUrl(baseUrlField.getText());
        working.setTemperature(temperatureSpinner.getValue());
        working.setMaxTokens(maxTokensSpinner.getValue());
        working.setTimeoutSeconds(timeoutSpinner.getValue());
        working.setToolsEnabled(toolsCheck.isSelected());
        working.setWebSearchEnabled(webSearchCheck.isSelected());
        working.setThinkingEnabled(thinkingCheck.isSelected() && !thinkingCheck.isDisable());
        working.setThinkingBudgetTokens(thinkingBudgetSpinner.getValue());
        return working;
    }

    private void refreshStatus() {
        if (working == null || loading) return;
        String problem = collect().validate();
        setStatus(problem == null ? "" : problem, problem == null ? "settings-hint" : "settings-error");
        saveButton.setDisable(problem != null);
    }

    private void setStatus(String message, String styleClass) {
        statusLabel.getStyleClass().removeAll("settings-hint", "settings-ok", "settings-error", "settings-busy");
        statusLabel.getStyleClass().add(styleClass);
        statusLabel.setText(message);
    }

    /** Pergunta ao provedor que modelos tem, em vez de mostrar uma lista fixa. */
    @FXML
    private void refreshModels() {
        final LlmConfig config = collect().copy();
        refreshModelsButton.setDisable(true);
        modelsHintLabel.setText("Asking " + config.getProvider().getDisplayName() + "...");

        Thread.ofVirtual().start(() -> {
            ModelCatalog.Result result = ModelCatalog.discover(config);
            Platform.runLater(() -> {
                refreshModelsButton.setDisable(false);
                String current = modelBox.getEditor().getText();
                modelBox.setItems(FXCollections.observableArrayList(result.models()));
                // Não perder o que o utilizador já tinha escrito só porque a lista mudou.
                if (current != null && !current.isBlank()) modelBox.getEditor().setText(current);
                modelsHintLabel.setText(result.message());
            });
        });
    }

    @FXML
    private void save() {
        LlmConfig config = collect();
        String problem = config.validate();
        if (problem != null) {
            setStatus(problem, "settings-error");
            return;
        }
        try {
            LlmConfigStore.save(config);
            saved = config;
            dialogStage.close();
        } catch (Exception e) {
            ShowError("Error", "Could not save the assistant settings.", e.getMessage());
        }
    }

    @FXML
    private void cancel() {
        saved = null;
        dialogStage.close();
    }

    /** Pedido curto ao provedor, para apanhar chave ou URL errados antes de conversar. */
    @FXML
    private void testConnection() {
        LlmConfig config = collect().copy();
        String problem = config.validate();
        if (problem != null) {
            setStatus(problem, "settings-error");
            return;
        }

        testButton.setDisable(true);
        setStatus("Testing " + config.getProvider().getDisplayName() + "...", "settings-busy");

        Thread.ofVirtual().start(() -> {
            String message;
            String style;
            try {
                String reply = ChatModelFactory.create(config).chat("Reply with the single word: ok");
                message = "Connected. The model replied: " + reply.strip();
                style = "settings-ok";
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                message = "Failed: " + root.getMessage();
                style = "settings-error";
            }
            final String finalMessage = message;
            final String finalStyle = style;
            Platform.runLater(() -> {
                testButton.setDisable(false);
                setStatus(finalMessage, finalStyle);
            });
        });
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Abre a janela e devolve a configuração gravada, ou null se foi cancelada.
     *
     * <p>Lê sempre do disco à entrada, para o que se vê ser o que está mesmo em vigor.</p>
     */
    public static LlmConfig open(Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AssistantSettingsController.class.getResource("AssistantSettings.fxml"));
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("Assistant settings");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) stage.initOwner(owner);

            AssistantSettingsController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setConfig(LlmConfigStore.reload());

            stage.showAndWait();
            return controller.getSavedConfig();
        } catch (Exception e) {
            ShowError("Error", "Could not open the assistant settings.", e.getMessage());
            return null;
        }
    }

}

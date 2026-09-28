package com.example.sqlide.Assistant;

import com.example.sqlide.Assistant.llm.AssistantEngine;
import com.example.sqlide.Assistant.llm.LlmConfig;
import com.example.sqlide.Assistant.llm.LlmConfigStore;
import com.example.sqlide.Assistant.speech.MicrophoneService;
import com.example.sqlide.AssistantSpeatchInterface;
import com.example.sqlide.Container.Assistant.AssistantBoxCode;
import com.example.sqlide.requestInterface;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Janela de conversa do assistente.
 *
 * <p>Fala com o {@link AssistantEngine}, que usa o LangChain4j. Os três interruptores da
 * barra — ferramentas, pesquisa e raciocínio — escrevem na configuração partilhada, e o
 * seu estado é relido do disco de cada vez que a conversa abre, para não ficar preso ao
 * que estava em memória.</p>
 */
public class AssistantController implements AssistantSpeatchInterface {

    /** Um anexo individual não passa disto; acima é cortado com aviso. */
    private static final int MAX_FILE_BYTES = 128 * 1024;

    /** Nem a soma de todos os anexos de uma mensagem. */
    private static final int MAX_TOTAL_BYTES = 384 * 1024;

    @FXML
    private JFXButton SendButton, MicrophoneButton, AttachButton;
    @FXML
    private Hyperlink BackButton;
    @FXML
    private Button FuncButton, SearchButton, DeepButton;
    @FXML
    private Label ModelLabel;

    @FXML
    private ScrollPane MessagesScroll;
    @FXML
    private VBox MessagesBox;
    @FXML
    private TextArea MessageBox;
    @FXML
    private FlowPane AttachmentsBox;

    private JSONArray context;

    private requestInterface AssistantFunctionsInterface;

    private AssistantEngine engine;

    private final MicrophoneService microphoneService = new MicrophoneService();

    private final StringProperty action = new SimpleStringProperty();

    /** Ficheiros que vão junto com a próxima mensagem. */
    private final Set<File> attachments = new LinkedHashSet<>();

    private File file;

    private AssistantMain assistantMainController;

    public AssistantController() throws IOException {
    }

    // ==== Persistência da conversa ====

    private void WriteUserToJson(final String content) throws IOException {
        JSONObject novoItem = new JSONObject();
        novoItem.put("User", content);
        context.put(novoItem);
        writeContext();
    }

    private void WriteAssistantToJson(final String content, final boolean status) throws IOException {
        JSONObject novoItem = new JSONObject();
        novoItem.put("Assistant", content);
        novoItem.put("status", status);
        context.put(novoItem);
        writeContext();
    }

    private void writeContext() throws IOException {
        if (file == null || context == null) return;
        try (FileWriter fileWriter = new FileWriter(file, StandardCharsets.UTF_8)) {
            fileWriter.write(context.toString(4));
        }
    }

    // ==== Arranque ====

    /**
     * Liga o assistente à base de dados aberta. Tem de ser chamado antes da primeira
     * mensagem, porque é daqui que saem as ferramentas que o modelo pode invocar.
     */
    public void setAssistantFunctionsInterface(final requestInterface assistantFunctionsInterface) {
        this.AssistantFunctionsInterface = assistantFunctionsInterface;
        this.engine = new AssistantEngine(assistantFunctionsInterface);
        this.engine.setActivityListener(message -> Platform.runLater(() -> action.set(message)));
        syncFromConfig();
    }

    @FXML
    private void initialize() {
        MicrophoneButton.setUserData(new SimpleBooleanProperty(false));
        // A conversa segue sempre a última mensagem.
        MessagesBox.heightProperty().addListener((_, _, _) -> MessagesScroll.setVvalue(1.0));
    }

    /** Repõe os interruptores e a etiqueta do modelo a partir da configuração gravada. */
    private void syncFromConfig() {
        if (engine == null) return;
        LlmConfig config = engine.getConfig();

        paintToggle(FuncButton, config.isToolsEnabled());
        paintToggle(SearchButton, config.isWebSearchEnabled());
        paintToggle(DeepButton, config.isThinkingEnabled());
        DeepButton.setDisable(!config.supportsThinking());

        ModelLabel.setText(config.isUsable()
                ? config.getProvider().getDisplayName() + " · " + config.getModelName()
                : "no model configured");
    }

    private void paintToggle(Button button, boolean on) {
        button.setId(on ? "sel" : "");
    }

    // ==== Envio ====

    @FXML
    synchronized private void SendMessage() {

        final String typed = MessageBox.getText();
        final boolean hasAttachments = !attachments.isEmpty();

        if ((typed == null || typed.isBlank()) && !hasAttachments) return;

        if (engine == null) {
            ShowError("Not ready", "The assistant is not connected to a database yet.");
            return;
        }

        if (!engine.isConfigured()) {
            ShowInformation("No model configured", engine.describeMissingConfiguration()
                    + "\n\nOpen Settings to pick a provider, model and key.");
            openModelSettings();
            return;
        }

        // O que se mostra ao utilizador e o que vai para o modelo são diferentes: no ecrã
        // aparece um resumo dos anexos, no prompt vai o conteúdo.
        final List<File> sending = new ArrayList<>(attachments);
        final String shown = describeForDisplay(typed, sending);
        final String prompt = buildPrompt(typed, sending);

        clearAttachments();

        Task<String> senderTask = new Task<>() {

            private final ProgressIndicator progress = createProgress();
            private final Label actionLabel = new Label();
            private final AssistantBoxCode box = new AssistantBoxCode();
            private String reply;
            private boolean status = false;

            @Override
            protected void running() {
                super.running();
                MessagesBox.getChildren().add(createUserMessageBox(shown));
                MessageBox.setText("");
                SendButton.setDisable(true);
                BackButton.setDisable(true);
                action.set("Thinking...");
                actionLabel.setTextFill(Color.WHITE);
                actionLabel.textProperty().bind(action);
                MessagesBox.getChildren().addAll(progress, actionLabel);
            }

            @Override
            protected String call() {
                return engine.ask(prompt);
            }

            @Override
            protected void failed() {
                super.failed();
                Throwable cause = getException();
                while (cause.getCause() != null) cause = cause.getCause();
                reply = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                box.addErrorMessage("Error generating response\n" + reply);
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                reply = "Assistant:\n" + getValue();
                styleAiMessage(reply, box);
                status = true;
            }

            @Override
            protected void done() {
                super.done();
                Platform.runLater(() -> {
                    MessagesBox.getChildren().add(box);
                    actionLabel.textProperty().unbind();
                    MessagesBox.getChildren().removeAll(progress, actionLabel);
                    BackButton.setDisable(false);
                    SendButton.setDisable(false);
                    try {
                        WriteUserToJson(shown);
                        WriteAssistantToJson(reply, status);
                    } catch (IOException _) {
                        // Falhar a gravação do histórico não deve apagar a resposta do ecrã.
                    }
                });
            }
        };

        Thread.ofVirtual().start(senderTask);
    }

    public void SendMessage(final String code) {
        MessageBox.setText(code);
        SendMessage();
    }

    // ==== Anexos ====

    @FXML
    private void attachFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text and data",
                        "*.sql", "*.csv", "*.tsv", "*.txt", "*.json", "*.xml", "*.md", "*.log",
                        "*.yaml", "*.yml", "*.ini", "*.conf", "*.java", "*.py", "*.js", "*.ts"),
                new FileChooser.ExtensionFilter("All files", "*.*"));

        List<File> chosen = chooser.showOpenMultipleDialog(
                MessageBox.getScene() == null ? null : MessageBox.getScene().getWindow());
        if (chosen == null) return;

        for (File candidate : chosen) addAttachment(candidate);
        refreshAttachmentChips();
    }

    private void addAttachment(File candidate) {
        if (!candidate.isFile()) return;
        if (candidate.length() > MAX_FILE_BYTES) {
            ShowInformation("File too large",
                    candidate.getName() + " is " + humanSize(candidate.length())
                            + ". Only the first " + humanSize(MAX_FILE_BYTES) + " will be sent.");
        }
        attachments.add(candidate);
    }

    /** Uma etiqueta por ficheiro, cada uma com o seu botão de remover. */
    private void refreshAttachmentChips() {
        AttachmentsBox.getChildren().clear();

        for (File attached : attachments) {
            Label name = new Label(attached.getName() + "  (" + humanSize(attached.length()) + ")");
            name.setTextFill(Color.web("#D6D6D6"));

            Button remove = new Button("✕");
            remove.setStyle("-fx-background-color: transparent; -fx-text-fill: #9A9A9A; -fx-padding: 0 2 0 2;");
            remove.setOnAction(_ -> {
                attachments.remove(attached);
                refreshAttachmentChips();
            });

            HBox chip = new HBox(6, name, remove);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setPadding(new Insets(3, 6, 3, 8));
            chip.setStyle("-fx-background-color: #4A4A4A; -fx-background-radius: 12px;");
            AttachmentsBox.getChildren().add(chip);
        }

        boolean any = !attachments.isEmpty();
        AttachmentsBox.setVisible(any);
        AttachmentsBox.setManaged(any);
    }

    private void clearAttachments() {
        attachments.clear();
        refreshAttachmentChips();
    }

    /** O que o utilizador vê no seu balão: o texto mais a lista de ficheiros. */
    private String describeForDisplay(String typed, List<File> files) {
        StringBuilder text = new StringBuilder(typed == null ? "" : typed.strip());
        if (!files.isEmpty()) {
            if (!text.isEmpty()) text.append("\n\n");
            text.append("Attached: ");
            text.append(files.stream().map(File::getName).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        return text.toString();
    }

    /**
     * Monta o prompt com o conteúdo dos ficheiros embutido.
     *
     * <p>O conteúdo vai como texto delimitado em vez de ir por um canal de anexos: é a única
     * forma que funciona igual em todos os provedores, incluindo os modelos locais. Ficheiros
     * binários são identificados e referidos pelo nome, sem despejar bytes ilegíveis.</p>
     */
    private String buildPrompt(String typed, List<File> files) {
        StringBuilder prompt = new StringBuilder(typed == null ? "" : typed.strip());
        int budget = MAX_TOTAL_BYTES;

        for (File attached : files) {
            prompt.append("\n\n--- file: ").append(attached.getName()).append(" ---\n");

            if (budget <= 0) {
                prompt.append("(not included: the attachments exceeded the size budget)\n");
                continue;
            }

            try {
                byte[] bytes = Files.readAllBytes(attached.toPath());
                if (looksBinary(bytes)) {
                    prompt.append("(binary file, ").append(humanSize(attached.length()))
                            .append(" — content not included)\n");
                    continue;
                }

                int take = Math.min(bytes.length, Math.min(MAX_FILE_BYTES, budget));
                prompt.append(new String(bytes, 0, take, StandardCharsets.UTF_8));
                budget -= take;

                if (take < bytes.length) {
                    prompt.append("\n(truncated at ").append(humanSize(take))
                            .append(" of ").append(humanSize(bytes.length)).append(")\n");
                }
            } catch (IOException e) {
                prompt.append("(could not be read: ").append(e.getMessage()).append(")\n");
            }
            prompt.append("--- end of ").append(attached.getName()).append(" ---\n");
        }

        return prompt.toString();
    }

    /** Bytes nulos dentro do início do ficheiro é o sinal mais fiável de conteúdo binário. */
    private static boolean looksBinary(byte[] bytes) {
        int sample = Math.min(bytes.length, 8000);
        for (int i = 0; i < sample; i++) if (bytes[i] == 0) return true;
        return false;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    // ==== Apresentação ====

    private void styleAiMessage(String messageAi, final AssistantBoxCode container) {
        if (messageAi == null || messageAi.isBlank()) {
            container.addMessage("(empty response)");
            return;
        }

        if (!messageAi.contains("```")) {
            container.addMessage(messageAi);
            return;
        }

        String copy = messageAi;
        while (true) {
            int startIndex = copy.indexOf("```");
            if (startIndex == -1) {
                if (!copy.isBlank()) container.addMessage(copy);
                break;
            }

            String beforeCode = copy.substring(0, startIndex);
            if (!beforeCode.isBlank()) container.addMessage(beforeCode);

            copy = copy.substring(startIndex + 3);

            int endIndex = copy.indexOf("```");
            if (endIndex == -1) {
                if (!copy.isBlank()) container.addCode(copy);
                break;
            }

            // O bloco vai inteiro: é o AssistantBoxCode que decide se a primeira linha é
            // a linguagem. Retirá-la aqui também comeria a primeira linha de código.
            container.addCode(copy.substring(0, endIndex));
            copy = copy.substring(endIndex + 3);
        }
    }

    private ProgressIndicator createProgress() {
        final ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progress.setPrefSize(24, 24);
        return progress;
    }

    private TextArea createUserMessageBox(final String message) {
        final TextArea messageBox = new TextArea("User:\n" + message);
        messageBox.setEditable(false);
        messageBox.setWrapText(true);
        VBox.setMargin(messageBox, new Insets(0, 0, 0, 100));
        messageBox.setPadding(new Insets(10, 12, 10, 12));
        VBox.setVgrow(messageBox, Priority.ALWAYS);
        messageBox.setStyle("-fx-control-inner-background: #424242; " +
                "-fx-border-color: #505050; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 12px; " +
                "-fx-background-radius: 12px; " +
                "-fx-region-background: #424242; " +
                "-fx-text-fill: #ECECEC; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent; " +
                "-fx-display-caret: false;");

        Text text = new Text(message);
        text.setFont(Font.font("JetBrains Mono Medium", 14)); // Mesmo font do CSS

        // Definir a largura máxima para o cálculo de quebra de linha
        // Subtrair o padding e as bordas para obter a largura real do texto
        double maxWidth = messageBox.prefWidthProperty().getValue() - 40; // Ajuste para padding e bordas
        text.setWrappingWidth(maxWidth);

        // Calcular a altura necessária com base no layout do texto
        double textHeight = text.getLayoutBounds().getHeight();

        // Adicionar espaço para padding e bordas
        double totalHeight = textHeight + 30; // Ajuste para padding e bordas

        // Definir uma altura mínima
        totalHeight = Math.max(totalHeight, 80);

        // Aplicar a altura calculada
        messageBox.setPrefHeight(totalHeight + 30);

        // Adicionar um listener para ajustar a altura quando o tamanho da janela mudar
        messageBox.widthProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            text.setWrappingWidth(newVal.doubleValue() - 40);
            double newTextHeight = text.getLayoutBounds().getHeight();
            double newTotalHeight = newTextHeight + 40;
            newTotalHeight = Math.max(newTotalHeight, 70);
            messageBox.setPrefHeight(newTotalHeight);
        }));

        return messageBox;
    }

    // ==== Interruptores ====

    @FXML
    private void HandleFunc() {
        LlmConfig config = updateConfig(current -> current.setToolsEnabled(!current.isToolsEnabled()));
        paintToggle(FuncButton, config.isToolsEnabled());
    }

    @FXML
    private void HandleSearch() {
        LlmConfig config = updateConfig(current -> current.setWebSearchEnabled(!current.isWebSearchEnabled()));
        paintToggle(SearchButton, config.isWebSearchEnabled());
    }

    @FXML
    private void HandleDeep() {
        LlmConfig config = engine == null ? null : engine.getConfig();
        if (config != null && !config.supportsThinking()) {
            ShowInformation("Not supported",
                    config.getProvider().getDisplayName() + " does not expose a thinking switch.");
            return;
        }
        LlmConfig updated = updateConfig(current -> current.setThinkingEnabled(!current.isThinkingEnabled()));
        paintToggle(DeepButton, updated.isThinkingEnabled());
    }

    private interface ConfigChange {
        void apply(LlmConfig config);
    }

    /** Altera a configuração, guarda-a e reconstrói o modelo no próximo pedido. */
    private LlmConfig updateConfig(ConfigChange change) {
        if (engine == null) return new LlmConfig();
        LlmConfig config = engine.getConfig().copy();
        change.apply(config);
        engine.updateConfig(config);
        try {
            LlmConfigStore.save(config);
        } catch (IOException _) {
            // Os interruptores valem para esta sessão mesmo que não se consigam gravar.
        }
        return config;
    }

    @FXML
    private void openModelSettings() {
        LlmConfig saved = AssistantSettingsController.open(
                MessageBox.getScene() == null ? null : MessageBox.getScene().getWindow());
        if (saved != null && engine != null) engine.updateConfig(saved);
        syncFromConfig();
    }

    @FXML
    private void DeleteConversation() {
        MessagesBox.getChildren().clear();
        if (engine != null) engine.clearMemory();
        context = new JSONArray();
        try {
            writeContext();
        } catch (IOException _) {
        }
    }

    @FXML
    private void record() {
        final SimpleBooleanProperty recording = (SimpleBooleanProperty) MicrophoneButton.getUserData();
        boolean state = false;

        if (!recording.get()) {
            Thread.ofVirtual().start(() -> {
                try {
                    microphoneService.refresh();
                    microphoneService.start();
                } catch (Exception _) {
                }
            });
            state = true;
        } else {
            Thread.ofVirtual().start(() -> {
                final String path = microphoneService.finish();
                final String pred = this.predict(path);
                Platform.runLater(() -> MessageBox.setText(pred));
            });
        }
        recording.set(state);
        MicrophoneButton.setUserData(recording);
    }

    /** Repõe uma conversa gravada, tanto no ecrã como na memória do modelo. */
    public void inflate(final JSONArray content) {
        context = content;
        MessagesBox.getChildren().clear();
        clearAttachments();
        syncFromConfig();

        for (int objectIndex = 0; objectIndex < content.length(); objectIndex++) {
            final JSONObject turn = content.getJSONObject(objectIndex);
            if (turn.has("User")) {
                final String message = turn.getString("User");
                MessagesBox.getChildren().add(createUserMessageBox(message));
                if (engine != null) engine.restoreUserTurn(message);
            } else {
                final AssistantBoxCode box = new AssistantBoxCode();
                final String message = turn.getString("Assistant");
                if (turn.optBoolean("status", true)) {
                    styleAiMessage(message, box);
                    if (engine != null) engine.restoreAssistantTurn(message);
                } else {
                    // Turnos que falharam ficam visíveis mas fora da memória do modelo.
                    box.addErrorMessage(message);
                }
                MessagesBox.getChildren().add(box);
            }
        }
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setBackPort(AssistantMain assistantMainController) {
        this.assistantMainController = assistantMainController;
    }

    @FXML
    private void back() {
        assistantMainController.backPort();
    }

}

package com.example.sqlide.Assistant;

import com.example.sqlide.Assistant.speech.MicrophoneService;
import com.example.sqlide.AssistantSpeatchInterface;
import com.example.sqlide.Container.Assistant.AssistantBoxCode;
import com.example.sqlide.requestInterface;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.json.JSONArray;
import org.json.JSONObject;
import dev.langchain4j.service.Result;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class AssistantController implements AssistantSpeatchInterface {

    @FXML
    private JFXButton SendButton, MicrophoneButton, AttachButton;
    @FXML
    private Hyperlink BackButton;
    @FXML
    private Button FuncButton, SearchButton, DeepButton;

    @FXML
    private VBox MessagesBox;

    @FXML
    private TextArea MessageBox;

    private JSONArray context;

    private requestInterface AssistantFunctionsInterface;

    private final MicrophoneService microphoneService = new MicrophoneService();

    private final StringProperty action = new SimpleStringProperty();

    private final BooleanProperty search = new SimpleBooleanProperty(false), function = new SimpleBooleanProperty(false), deep = new SimpleBooleanProperty(false);

    private File file;

    private AssistantMain assistantMainController;

    private final AiAgentService aiAgentService = new AiAgentService();

    private File attachedImage;

    private boolean aiServiceInitialized = false;

    public AssistantController() throws IOException {
    }

    private void WriteUserToJson(final String content) throws IOException {
        JSONObject novoItem = new JSONObject();
        novoItem.put("User", content);

        context.put(novoItem);

        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(context.toString(4));
        fileWriter.close();
    }

    private void WriteAssistantToJson(final String content, final boolean status) throws IOException {
        JSONObject novoItem = new JSONObject();
        novoItem.put("Assistant", content);
        novoItem.put("status", status);

        context.put(novoItem);

        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write(context.toString(4));
        fileWriter.close();
    }

    public void setAssistantFunctionsInterface(final requestInterface assistantFunctionsInterface) {
        this.AssistantFunctionsInterface = assistantFunctionsInterface;
    }

    @FXML
    private void initialize() throws IOException {
        function.addListener(_->setFuncButton());
        search.addListener(_->setSearchButton());
        deep.addListener(_->setDeepButton());
        MicrophoneButton.setUserData(new SimpleBooleanProperty(false));
    }

    private void initializeAiService() {
        java.util.Properties props = com.example.sqlide.Configuration.AiConfigurationController.getAIConfig();
        String provider = props.getProperty("provider", "OpenAI");
        String apiKey = props.getProperty("apiKey", "");
        String modelName = props.getProperty("modelName");
        String baseUrl = props.getProperty("baseUrl");
        String googleCseId = props.getProperty("googleCseId");

        dev.langchain4j.model.chat.ChatLanguageModel model = AiAgentService.createModel(provider, apiKey, modelName, baseUrl);

        java.util.List<Object> tools = new java.util.ArrayList<>();
        if (function.get()) {
            tools.add(new DatabaseTools(AssistantFunctionsInterface));
        }

        if (search.get() && googleCseId != null && !googleCseId.isEmpty()) {
            tools.add(AiAgentService.createWebSearchEngine(apiKey, googleCseId));
        }

        String systemInstruction = "You are a SQL Assistant. Your name is Aida.";
        if (deep.get()) {
            systemInstruction += " Think deeply and explain your reasoning.";
        }

        aiAgentService.initialize(model, tools, systemInstruction);
    }

    public void SendMessage(final String code) {
        MessageBox.setText(code);
        SendMessage();
    }

    @FXML
    synchronized private void SendMessage() {

        final String message = MessageBox.getText();

        if ((message != null && !message.isEmpty()) || attachedImage != null) {

            final File imageToSend = attachedImage;
            attachedImage = null;
            AttachButton.setStyle("-fx-background-color: #3574F0; -fx-border-radius: 30px;");

            Task<Void> senderTask = new Task<Void>() {

                private final ProgressIndicator progress = createProgress();
                private final Label actionLabel = new Label();
                private final AssistantBoxCode box = new AssistantBoxCode();
                private String assistantResponse;
                private boolean status = false;

                @Override
                protected void running() {
                    super.running();
                    String userMsg = message != null ? message : "";
                    if (imageToSend != null) userMsg += " [Image Attached: " + imageToSend.getName() + "]";
                    MessagesBox.getChildren().add(createUserMessageBox(userMsg));
                    MessageBox.setText("");
                    SendButton.setDisable(true);
                    BackButton.setDisable(true);
                    actionLabel.setTextFill(Color.WHITE);
                    actionLabel.textProperty().bind(action);
                    MessagesBox.getChildren().addAll(progress, actionLabel);
                }

                @Override
                protected Void call() throws Exception {
                    if (!aiServiceInitialized) {
                        initializeAiService();
                        aiServiceInitialized = true;
                    }
                    Result<String> result;
                    if (imageToSend != null) {
                        byte[] fileContent = java.nio.file.Files.readAllBytes(imageToSend.toPath());
                        String base64Image = java.util.Base64.getEncoder().encodeToString(fileContent);
                        String mimeType = java.nio.file.Files.probeContentType(imageToSend.toPath());
                        result = aiAgentService.chatWithImage(message, base64Image, mimeType);
                    } else {
                        result = aiAgentService.chat(message);
                    }
                    assistantResponse = result.content();
                    return null;
                }

                @Override
                protected void failed() {
                    super.failed();
                    box.addErrorMessage("Error to generate response\n" + getException().getMessage());
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    styleAiMessage("Assistant:\n" + assistantResponse, box);
                    status = true;
                }

                @Override
                protected void done() {
                    super.done();
                    Platform.runLater(()->{
                        MessagesBox.getChildren().add(box);
                        MessagesBox.getChildren().removeAll(progress, actionLabel);
                        BackButton.setDisable(false);
                        SendButton.setDisable(false);
                    });
                    try {
                        WriteUserToJson(message);
                        WriteAssistantToJson("Assistant:\n" + assistantResponse, status);
                    } catch (IOException _) {
                    }

                }

            };

            Thread.ofVirtual().start(senderTask);
        }
    }

    private void styleAiMessage(String messageAi, final AssistantBoxCode container) {
        if (messageAi.contains("```")) {
            String copy = messageAi;
            while (true) {
                int startIndex = copy.indexOf("```");
                if (startIndex == -1) {
                    if (!copy.trim().isEmpty()) {
                        container.addMessage(copy);
                    }
                    break;
                }

                String beforeCode = copy.substring(0, startIndex);
                if (!beforeCode.trim().isEmpty()) {
                    container.addMessage(beforeCode);
                }

                copy = copy.substring(startIndex + 3);

                int endIndex = copy.indexOf("```");
                if (endIndex == -1) {
                    if (!copy.trim().isEmpty()) {
                        container.addCode(copy);
                    }
                    break;
                }

                String codeBlock = copy.substring(0, endIndex);
                container.addCode(codeBlock);

                copy = copy.substring(endIndex + 3).replaceFirst("\n", "");
            }
        } else {
            container.addMessage(messageAi);
        }
    }

    private ProgressIndicator createProgress() {
        final ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        return progress;
    }

    private String parseMessage(final String message) {
        return message.replace("\"", "").replace("\n", "\\\\n");
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
        text.setFont(Font.font("JetBrains Mono Medium", 14));

        double maxWidth = messageBox.prefWidthProperty().getValue() - 40;
        text.setWrappingWidth(maxWidth);

        double textHeight = text.getLayoutBounds().getHeight();
        double totalHeight = textHeight + 30;
        totalHeight = Math.max(totalHeight, 80);

        messageBox.setPrefHeight(totalHeight+30);

        messageBox.widthProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                text.setWrappingWidth(newVal.doubleValue() - 40);
                double newTextHeight = text.getLayoutBounds().getHeight();
                double newTotalHeight = newTextHeight + 40;
                newTotalHeight = Math.max(newTotalHeight, 70);
                messageBox.setPrefHeight(newTotalHeight);
            });
        });

        return messageBox;
    }

    @FXML
    private void HandleFunc() {
        function.set(!function.get());
    }

    @FXML
    private void HandleSearch() {
        search.set(!search.get());
    }

    @FXML
    private void HandleDeep() {
        deep.set(!deep.get());
    }

    private void setFuncButton() {
        if (function.get()) {
            FuncButton.setId("sel");
            search.set(false);
        } else {
            FuncButton.setId("");
        }
    }

    private void setSearchButton() {
        if (search.get()) {
            SearchButton.setId("sel");
            function.set(false);
        } else {
            SearchButton.setId("");
        }
    }

    private void setDeepButton() {
        if (deep.get()) {
            DeepButton.setId("sel");
        } else {
            DeepButton.setId("");
        }
    }

    @FXML
    private void DeleteConversation() {
        MessagesBox.getChildren().clear();
    }

    @FXML
    private void record() throws Exception {
        final SimpleBooleanProperty recording = (SimpleBooleanProperty) MicrophoneButton.getUserData();
        boolean state = false;

        if (!recording.get()) {
            Thread.ofVirtual().start(()-> {
                try {
                    microphoneService.refresh();
                    microphoneService.start();
                } catch (Exception _) {
                }
            });
            state = true;
        } else {
            Thread.ofVirtual().start(()->{
                final String path = microphoneService.finish();
                // Since this was previously calling a python-based predict,
                // and we are refactoring to Java, we might need a Java-based transcription service.
                // For now, I will leave a placeholder or suggest using an LLM for speech-to-text if needed.
                Platform.runLater(()->MessageBox.setText("Speech-to-text not implemented in Java refactor yet."));
            });
        }
        recording.set(state);
        MicrophoneButton.setUserData(recording);
    }

    public void inflate(final JSONArray content) {
        context = content;
        for (int objectIndex = 0; objectIndex < content.length(); objectIndex++) {
            if (content.getJSONObject(objectIndex).keys().next().equals("User")) {
                MessagesBox.getChildren().add(createUserMessageBox(content.getJSONObject(objectIndex).getString("User")));
            } else {
                final AssistantBoxCode box = new AssistantBoxCode();
                final String message = content.getJSONObject(objectIndex).getString("Assistant");
                if (content.getJSONObject(objectIndex).getBoolean("status")) {
                    styleAiMessage(message, box);
                } else {
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

    @FXML
    private void attachImage() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File selectedFile = fileChooser.showOpenDialog(AttachButton.getScene().getWindow());
        if (selectedFile != null) {
            attachedImage = selectedFile;
            AttachButton.setStyle("-fx-background-color: #4CAF50; -fx-border-radius: 30px;");
        }
    }
}

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
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AssistantController implements AssistantSpeatchInterface {

    @FXML
    private JFXButton SendButton, MicrophoneButton;
    @FXML
    private Hyperlink BackButton;
    @FXML
    private Button FuncButton, SearchButton, DeepButton, AttachImageButton;

    @FXML
    private Label imageStatusLabel;

    @FXML
    private VBox MessagesBox;

    @FXML
    private TextArea MessageBox;

    private JSONArray context;

    private requestInterface AssistantFunctionsInterface;

    private final MicrophoneService microphoneService = new MicrophoneService();

    private final StringProperty action = new SimpleStringProperty();

    private final BooleanProperty search = new SimpleBooleanProperty(false), function = new SimpleBooleanProperty(false), deep = new SimpleBooleanProperty(false);

    final BlockingQueue<JSONObject> sender = new LinkedBlockingQueue<>(), reciver = new LinkedBlockingQueue<>();
    private File file;

    private String base64Image = null;

    Process process;

    private AssistantMain assistantMainController;

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
        initializeProcess();
        function.addListener(_->setFuncButton());
        search.addListener(_->setSearchButton());
        deep.addListener(_->setDeepButton());
        MicrophoneButton.setUserData(new SimpleBooleanProperty(false));
    }

    private void initializeProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("python", "src/main/java/com/example/sqlide/Assistant/service/aida.py");
      //  pb.directory(new File(System.getProperty("user.dir")));
        process = pb.start();

        initializeErr(process);
        initializeInput(process);
        initializeOutput(process);

    }

    private void initializeInput(final Process process) {
        final Thread readerT = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    System.out.println("Python: " + linha);
                    computeMessage(new JSONObject(linha));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        readerT.setDaemon(true);
        readerT.start();
    }

    private JSONObject getAiSettings() {
        File settingsFile = new File("ai_settings.json");
        if (settingsFile.exists()) {
            try {
                return new JSONObject(Files.readString(settingsFile.toPath()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new JSONObject();
    }

    private void initializeOutput(final Process process) {
        final Thread writerT = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                JSONObject payload;
                while ((payload = sender.take()) != null) {
                    writer.write(payload.toString());
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        writerT.setDaemon(true);
        writerT.start();
    }

    private void initializeErr(final Process process) {
        final Thread readerT = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    final JSONObject reciverJson = new JSONObject();
                    reciverJson.put("message", linha);
                    reciverJson.put("status", false);
                    reciver.put(reciverJson.toString());
                    System.out.println(linha);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        readerT.setDaemon(true);
        readerT.start();
    }

    private void sendContext() {
        Thread.ofVirtual().start(()-> {
            try {
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream()));
                JSONObject jsonSender = new JSONObject();
                jsonSender.put("content", context);
                jsonSender.put("type", false);
                writer.write(jsonSender.toString());
                writer.newLine();
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void computeMessage(final JSONObject json) throws InterruptedException {
        if (json.getString("status").equals("request")) {
            System.out.println("[Java] Resposta do Python: " + json.getString("message"));

            if (json.has("function")) {
                final String function = json.getString("function");
                Platform.runLater(()->action.set(json.getString("message")));
                JSONArray parameters = json.getJSONArray("parameters");

                System.out.println("função " + function);

                JSONObject response = new JSONObject();
                response.put("type", "response");
                switch (function) {
                    case "type":
                        response.put("content", AssistantFunctionsInterface.getSQLType().name());
                        break;
                    case "Show_Data":
                        response.put("content", String.valueOf(AssistantFunctionsInterface.ShowData(parameters.getString(0), parameters.getString(1))));
                        break;
                    case "Request_Data":
                        response.put("content", AssistantFunctionsInterface.getData(parameters.getString(0), parameters.getString(1)).toString());
                        break;
                    case "GetTableMeta":
                        response.put("content", AssistantFunctionsInterface.getTableMetadata().toString());
                        break;
                    case "CreateTable":
                        final ArrayList<HashMap<String, String>> Table = new ArrayList<>();
                        final JSONArray jsonArray = parameters.getJSONArray(1);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            final JSONObject obj = jsonArray.getJSONObject(i);
                            final HashMap<String, String> map = new HashMap<>();
                            for (String key : obj.keySet()) map.put(key, obj.getString(key));
                            Table.add(map);
                        }
                        response.put("content", Boolean.toString(AssistantFunctionsInterface.createTable(parameters.getString(0), Table, parameters.getString(2))));
                        break;
                    case "CreateView":
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createView(parameters.getString(0), parameters.getString(1), parameters.getString(2))));
                        break;
                    case "table":
                        response.put("content", AssistantFunctionsInterface.currentTable());
                        break;
                    case "InsertData":
                        final ArrayList<LinkedHashMap<String, String>> Rows = new ArrayList<>();
                        final JSONArray Data = parameters.getJSONArray(1);
                        for (int i = 0; i < Data.length(); i++) {
                            final JSONObject obj = Data.getJSONObject(i);
                            final LinkedHashMap<String, String> map = new LinkedHashMap<>();
                            for (String key : obj.keySet()) map.put(key, obj.getString(key));
                            Rows.add(map);
                        }
                        response.put("content", AssistantFunctionsInterface.insertData(parameters.getString(0), Rows));
                        break;
                    case "createReport":
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createReport(parameters.getString(0), parameters.getString(1))));
                        break;
                    case "sendEmail":
                        response.put("content", String.valueOf(AssistantFunctionsInterface.sendEmail(parameters.getString(0))));
                        break;
                    case "CreateGraphic":
                        final ArrayList<HashMap<String, String>> chart = new ArrayList<>();
                        final JSONArray ChartData = parameters.getJSONArray(4);
                        for (int i = 0; i < ChartData.length(); i++) {
                            final JSONObject obj = ChartData.getJSONObject(i);
                            final HashMap<String, String> map = new HashMap<>();
                            for (String key : obj.keySet()) map.put(key, obj.getString(key));
                            chart.add(map);
                        }
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createGraphic(parameters.getString(0), parameters.getString(1), parameters.getString(2), parameters.getString(3), chart)));
                        break;
                    case "CreateTrigger":
                        final HashMap<String, String> trigger = new HashMap<>();
                        final JSONObject objTrigger = parameters.getJSONObject(0);
                        for (String key : objTrigger.keySet()) trigger.put(key, objTrigger.getString(key));
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createTriggers(trigger)));
                        break;
                    case "CreateFunction":
                        final HashMap<String, String> functions = new HashMap<>();
                        final JSONObject functionObject = parameters.getJSONObject(0);
                        for (String key : functionObject.keySet()) functions.put(key, functionObject.getString(key));
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createFunction(functions)));
                        break;
                    case "CreateProcedure":
                        final HashMap<String, String> procedures = new HashMap<>();
                        final JSONObject procedure = parameters.getJSONObject(0);
                        for (String key : procedure.keySet()) procedures.put(key, procedure.getString(key));
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createProcedure(procedures)));
                        break;
                    case "CreateEvent":
                        final HashMap<String, String> event = new HashMap<>();
                        final JSONObject child = parameters.getJSONObject(0);
                        for (String key : child.keySet()) event.put(key, child.getString(key));
                        response.put("content", String.valueOf(AssistantFunctionsInterface.createEvents(event)));
                        break;
                    default:
                        System.out.println("Função não reconhecida: " + function);
                        response.put("content", "Error: Function not recognized");
                }
                sender.put(response);
            }

        }
        else if (json.getString("status").equals("success")) {
            // Processar funções e parâmetros
            final JSONObject reciverJson = new JSONObject();
            reciverJson.put("message", json.getString("message") != null ? json.getString("message") : "");
            reciverJson.put("status", true);
            reciver.put(reciverJson.toString());
            System.out.println("sucess");
        }
    }

    public void SendMessage(final String code) {
        MessageBox.setText(code);
        SendMessage();
    }

    @FXML
    synchronized private void SendMessage() {

        final String message = MessageBox.getText();

        if (message != null && !message.isEmpty()) {

            Task<Void> senderTask = new Task<Void>() {

                private final ProgressIndicator progress = createProgress();
                private final Label actionLabel = new Label();
                private final AssistantBoxCode box = new AssistantBoxCode();
                private JSONObject Assistant_message;
                private boolean status = false;

                @Override
                protected void running() {
                    super.running();
                //    long num = message.chars().filter(ch -> ch == '\n').count() + 2;
                    MessagesBox.getChildren().add(createUserMessageBox(message));
                    MessageBox.setText("");
                    SendButton.setDisable(true);
                    BackButton.setDisable(true);
                    actionLabel.setTextFill(Color.WHITE);
                    actionLabel.textProperty().bind(action);
                    MessagesBox.getChildren().addAll(progress, actionLabel);
                }

                @Override
                protected Void call() throws Exception {
                    JSONObject payload = new JSONObject();
                    payload.put("type", "message");
                    payload.put("content", message);
                    payload.put("image", base64Image);
                    payload.put("search", search.get());
                    payload.put("deep", deep.get());
                    payload.put("command", function.get());
                    payload.put("settings", getAiSettings());

                    sender.put(payload);

                    Assistant_message = new JSONObject(reciver.take());
                    if (!Assistant_message.getBoolean("status")) throw new Exception(Assistant_message.getString("message"));
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
                    styleAiMessage("Assistant:\n" + Assistant_message.getString("message"), box);
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
                        base64Image = null;
                        imageStatusLabel.setText("");
                    });
                    try {
                        WriteUserToJson(message);
                        WriteAssistantToJson("Assistant:\n" + Assistant_message.getString("message"), status);
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
                    // Não há mais delimitadores, adiciona o restante como mensagem
                    if (!copy.trim().isEmpty()) {
                        container.addMessage(copy);
                    }
                    break;
                }

                // Texto antes do bloco de código
                String beforeCode = copy.substring(0, startIndex);
                if (!beforeCode.trim().isEmpty()) {
                    container.addMessage(beforeCode);
                }

                // Remove o texto processado e o delimitador de abertura
                copy = copy.substring(startIndex + 3);

                int endIndex = copy.indexOf("```");
                if (endIndex == -1) {
                    // Se não encontrar o delimitador de fechamento, trata o restante como código
                    if (!copy.trim().isEmpty()) {
                        container.addCode(copy);
                    }
                    break;
                }

                // Extrai o bloco de código
                String codeBlock = copy.substring(0, endIndex);
                container.addCode(codeBlock);

                // Remove o bloco de código e o delimitador de fechamento
                copy = copy.substring(endIndex + 3).replaceFirst("\n", "");
                //       int indexInnit = messageAi.indexOf("```");
                //     int indexEnd = messageAi.lastIndexOf("```");
                //   container.addMessage(messageAi.substring(0, indexInnit));
                // container.addCode(messageAi.substring(indexInnit, indexEnd - 1));
                // container.addMessage(messageAi.substring(indexEnd));
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
        //   return message;
    }

    private TextArea createUserMessageBox(final String message) {
        final TextArea messageBox = new TextArea("User:\n" + message);
        messageBox.setEditable(false);
        messageBox.setWrapText(true);
      /*  messageBox.setPrefRowCount(2);
        messageBox.setPrefHeight(lines * 20.0 + 14 + 12);
        messageBox.setPrefWidth(lines * 20.0 + 14 + 12);
        messageBox.setText("User:\n" + message); */
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
        messageBox.setPrefHeight(totalHeight+30);

        // Adicionar um listener para ajustar a altura quando o tamanho da janela mudar
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
                final String pred = this.predict(path);
                Platform.runLater(()->MessageBox.setText(pred));
            });
        }
        recording.set(state);
        MicrophoneButton.setUserData(recording);
    }

    public void inflate(final JSONArray content) {
        context = content;
        sendContext();
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
    private void handleAttachImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );
        File selectedFile = fileChooser.showOpenDialog(BackButton.getScene().getWindow());
        if (selectedFile != null) {
            try {
                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                base64Image = Base64.getEncoder().encodeToString(fileContent);
                imageStatusLabel.setText("Image: " + selectedFile.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

   /* @FXML
    private void ExecuteMicrophone() {
        MicroButton.setOnAction(_->StopMicrophone());
        Thread.ofVirtual().start(()->{
            try {
                microphoneService.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void StopMicrophone() {
        microphoneService.finish();

    }*/
}

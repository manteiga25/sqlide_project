package com.example.sqlide.Assistant;

import com.example.sqlide.Assistant.service.AssistantRequest;
import com.example.sqlide.Container.Assistant.AssistantBoxCode;
import com.example.sqlide.requestInterface;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AssistantController {

    @FXML
    private Button FuncButton, SearchButton, DeepButton;

    @FXML
    private VBox MessagesBox;

    @FXML
    private TextArea MessageBox;

    private AssistantRequest request;

    private BufferedReader processOutput, processErr;
    private BufferedWriter processInput;

    private requestInterface AssistantFunctionsInterface;

    private final BooleanProperty search = new SimpleBooleanProperty(false), function = new SimpleBooleanProperty(false), deep = new SimpleBooleanProperty(false);

    final BlockingQueue<String> sender = new LinkedBlockingQueue<>(), reciver = new LinkedBlockingQueue<>();

    public void setAssistantFunctionsInterface(final requestInterface assistantFunctionsInterface) {
        this.AssistantFunctionsInterface = assistantFunctionsInterface;
    }

    @FXML
    private void initialize() throws IOException {
        initializeProcess();
        function.addListener(_->setFuncButton());
        search.addListener(_->setSearchButton());
        deep.addListener(_->setDeepButton());
    }

    private void initializeProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("python3", "src/main/java/com/example/sqlide/Assistant/service/aida.py");
      //  pb.directory(new File(System.getProperty("user.dir")));
        Process process = pb.start();

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
                    JSONObject json = new JSONObject(linha);

                    if (json.getString("status").equals("request")) {
                        System.out.println("[Java] Resposta do Python: " + json.getString("message"));

                        if (json.has("function")) {
                            final String function = json.getString("function");
                            JSONArray parameters = json.getJSONArray("parameters");

                            switch (function) {
                                case "Show_Data":
                                        final boolean status = AssistantFunctionsInterface.ShowData(parameters.getString(0), parameters.getString(1));
                                        sender.put(String.valueOf(status));
                                    break;

                                    case "FetchData":
                                        final HashMap<String, ArrayList<Object>> data = AssistantFunctionsInterface.getData(parameters.getString(0), parameters.getString(1));
                                        sender.put(data.toString());
                                        break;

                                    case "GetTableMeta":
                                        final ArrayList<HashMap<String, String>> meta = AssistantFunctionsInterface.getTableMetadata(parameters.getString(0));
                                        sender.put(meta.toString());
                                        break;

                                case "sendEmail":
                                    sender.put(String.valueOf(AssistantFunctionsInterface.sendEmail(parameters.getString(0))));
                                    break;

                                    default:
                                        System.out.println("Função não reconhecida: " + function);
                                }
                        }

                    }
                    else if (json.getString("status").equals("success")) {
                        // Processar funções e parâmetros
                        final JSONObject reciverJson = new JSONObject();
                        reciverJson.put("message", json.getString("message"));
                        reciverJson.put("status", true);
                        reciver.put(reciverJson.toString());
                        System.out.println("sucess");
                    }
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

    private void initializeOutput(final Process process) {
        final Thread writerT = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream()))) {
                String linha;
                while (!(linha = sender.take()).isEmpty()) {
                    JSONObject jsonSender = new JSONObject();
                    jsonSender.put("content", linha);
                    jsonSender.put("search", search.get());
                    jsonSender.put("deep", deep.get());
                    jsonSender.put("command", function.get());
                    writer.write(jsonSender.toString());
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


    @FXML
    public void SendMessage(ActionEvent event) {
        final Button sender = (Button) event.getSource();
        final String message = MessageBox.getText();
        if (message != null && !message.isEmpty()) {
            long num = message.chars().filter(ch -> ch == '\n').count() + 2;
            MessagesBox.getChildren().add(createUserMessageBox(message, num));
            MessageBox.setText("");
            sender.setDisable(true);
            final ProgressIndicator progress = createProgress();
            MessagesBox.getChildren().add(progress);
            final Thread Generator = new Thread(() -> {
                final AssistantBoxCode box = new AssistantBoxCode();
                try {
                    System.out.println(parseMessage(message));
                    // final String generated = removeDeepSeekThink(TakToAi(parseMessage(message) + " (use '```' for programing code)"));
                  //  final String generated = removeDeepSeekThink(TakToAi(parseMessage(message)));
                   // System.out.println(generated);
                 //   styleAiMessage("Assistant:" + generated, box);
                    this.sender.put(message);

                    final JSONObject Assistant_message = new JSONObject(reciver.take());
                    System.out.println("as " + Assistant_message);
                    if (Assistant_message.getBoolean("status")) {
                        Platform.runLater(()->styleAiMessage("Assistant:\n" + Assistant_message.getString("message"), box));
                    } else {
                        Platform.runLater(()->box.addErrorMessage("Assistant:\n" + Assistant_message.getString("message")));
                    }

                     //  Platform.runLater(()->MessagesBox.getChildren().add(box));
                    //messageAi += "User: " + message + "\n" + "ChatBot:" + generated + "\n";
                } catch (Exception e) {
                    //   Platform.runLater(()->MessagesBox.getChildren().add(box.addErrorMessage("Error to generate response\n" + e.getMessage())));
                    Platform.runLater(() -> box.addErrorMessage("Error to generate response\n" + e.getMessage()));

                } finally {
                    Platform.runLater(() -> {
                        MessagesBox.getChildren().add(box);
                        MessagesBox.getChildren().remove(progress);
                    });
                    sender.setDisable(false);
                }
            });
            Generator.setDaemon(true);
            Generator.start();
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

    private TextArea createUserMessageBox(final String message, final long lines) {
        final TextArea messageBox = new TextArea();
        messageBox.setEditable(false);
        messageBox.setPrefRowCount(2);
        messageBox.setPrefHeight(lines * 20.0 + 14);
        messageBox.setPrefWidth(lines * 20.0 + 14);
        messageBox.setText("User:\n" + message);
        VBox.setMargin(messageBox, new Insets(0, 0, 0, 100));
        VBox.setVgrow(messageBox, Priority.ALWAYS);
        messageBox.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #3A3A3A; -fx-border-width: 5; -fx-control-inner-background: #3A3A3A; -fx-background-color: #3A3A3A;");
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
            deep.set(false);
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
            function.set(false);
        } else {
            DeepButton.setId("");
        }
    }

}

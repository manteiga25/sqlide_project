package com.example.sqlide.Assistant;

import atlantafx.base.controls.Spacer;
import com.example.sqlide.mainController;
import com.example.sqlide.misc.Dialog;
import com.example.sqlide.requestInterface;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.example.sqlide.popupWindow.handleWindow.*;

public class AssistantMainController implements AssistantMain {

    private final ArrayList<String> filesNames = new ArrayList<>();

    private final Path dir;

    private requestInterface request;

    @FXML
    private Pane MainContainer;

    @FXML
    private VBox Container;

    private VBox currentBox = Container;

    public AssistantMainController() throws IOException {
        dir = createDir();
    }

    @FXML
    private void initialize() {

        readConversations();
        if (!filesNames.isEmpty()) for (String file : filesNames) createConversationBox(file.substring(0, file.indexOf(".")));
    }

    private Path createDir() throws IOException {
        Path dir = Path.of("Assistant");
        if (!Files.exists(dir)) Files.createDirectory(dir);
        return dir;
    }

    private void readConversations() {
        File folder = new File("Assistant");
        filesNames.addAll(List.of(folder.list()));
    }

    @FXML
    private void create() {
        String name = Dialog.TextDialog("Nova Conversa", "Criar nova conversa", "Nome da conversa:");
        if (name != null) {
            try {
                if (filesNames.contains(name + ".json")) {
                    ShowInformation("Found", "You need to insert a different name of conversation.");
                    return;
                }
                createConversation(name);
            } catch (Exception e) {
                ShowError("Error", "Error to create conversation.", e.getMessage());
            }
        }
    }

    private void createConversation(final String name) throws IOException {
        File file = new File(dir.toAbsolutePath()+File.separator+name+".json");
        if (!file.createNewFile()) throw new IOException();
        final String nameConversation = file.getName().substring(0, file.getName().indexOf("."));
        filesNames.add(nameConversation);
        createConversationBox(nameConversation);
    }

    private void createConversationBox(final String name) {
        Container.getChildren().add(2, new ConversationBox(name));
    }

    private void enterConversation(final String id) {
        try {
            File file = new File(dir.toAbsolutePath() + File.separator + id + ".json");
            JSONArray content = readConversion(file);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("AssistantStage.fxml"));
            //    VBox miniWindow = loader.load();
            currentBox = loader.load();

            VBox.setVgrow(currentBox, Priority.ALWAYS);

            AssistantController currentController = loader.getController();
            currentController.inflate(content);
            currentController.setFile(file);
            currentController.setBackPort(this);
            currentController.setAssistantFunctionsInterface(request);
            changeToChat();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void changeToChat() {
        MainContainer.getChildren().removeFirst();
        MainContainer.getChildren().add(currentBox);
    }

    private void deleteConversation(final ConversationBox id) {
        try {
            if (ShowConfirmation("Delete", "Delete the conversation?")) {
                Files.delete(Path.of(dir.toAbsolutePath() + File.separator + id.getId() + ".json"));
                filesNames.remove(id.getId());
                Container.getChildren().remove(id);
            }
        } catch (Exception e) {
            ShowError("Error delete", "Error to delete conversation", e.getMessage());
        }
    }

    private JSONArray readConversion(final File file) throws IOException {

        final String data = Files.readString(file.toPath());

        final JSONArray array = data.isEmpty() ? new JSONArray() : new JSONArray(data);

        System.out.println(data);

        return array;
    }

    @Override
    public void backPort() {
            MainContainer.getChildren().removeFirst();
            MainContainer.getChildren().add(Container);
    }

    public void setAssistantFunctionsInterface(requestInterface request) {
        this.request = request;
    }

    private class ConversationBox extends HBox {
        public ConversationBox(final String name) {
            super();
            setId(name);
            getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/Assistant/BoxStyle.css")).toExternalForm());
            getStyleClass().add("HBox");
            setOnMouseClicked(_->AssistantMainController.this.enterConversation(name));
            setPadding(new Insets(10, 30, 10, 30));

            Button delete = new Button("delete");
            delete.setOnAction(_->deleteConversation(this));

            getChildren().addAll(new Label(name), new Spacer(), delete);

        }
    }

}

interface AssistantMain {
    public void backPort();
}
package com.example.sqlide.EventLayout;

import com.example.sqlide.Container.Editor.TextAreaAutocomplete;
import com.example.sqlide.Container.Editor.Words.SQLWords;
import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.Container.loading.loadingInterface;
import com.example.sqlide.Notification.NotificationInterface;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.misc.Dialog;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.sqlide.popupWindow.handleWindow.*;
import static java.nio.file.Files.exists;

public class EventController implements loadingInterface, NotificationInterface {

    @FXML
    private ChoiceBox<String> comboEvent;
    @FXML
    private VBox container;

    @FXML
    TextField ScriptPath;

    @FXML
    Button search;

    @FXML
    private CheckComboBox<String> EventsBox;

    private DataBase db;

    private Stage stage;

    private final ObservableMap<String, String> eventsFound = FXCollections.observableHashMap();

    private final TextAreaAutocomplete codeArea = new TextAreaAutocomplete();

    private TaskInterface taskInterface;

    public void initEventController(DataBase db, final TaskInterface taskInterface, Stage subStage) {
        this.db = db;
        this.taskInterface = taskInterface;
        this.stage = subStage;
        codeArea.setAutoCompleteWords(new ArrayList<>(List.of(SQLWords.getWords(db.getSQLType().ordinal()))));
    }

    @FXML
    private void initialize() {
        comboEvent.setItems(EventsBox.getCheckModel().getCheckedItems());
        comboEvent.getSelectionModel().selectedItemProperty().addListener((_,_, value)->{
            codeArea.textProperty().removeListener(_->{});
            codeArea.setText(eventsFound.get(value));
            codeArea.textProperty().addListener((_,_, text)->eventsFound.put(value, text));
        });
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        container.getChildren().add(4, codeArea);
    }

    public void addEvent(final HashMap<String, String> events) {
        eventsFound.putAll(events);
        EventsBox.getItems().addAll(eventsFound.keySet());
    }

    @FXML
    private void searchFile() {
        FileChooser selectFileWindow = new FileChooser();
        selectFileWindow.getExtensionFilters().add(new FileChooser.ExtensionFilter("script SQL", "*.sql"));

        final File selectedFile = selectFileWindow.showOpenDialog(stage);
        if (selectedFile != null) {
            ScriptPath.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void execute() {
        /*if (db == null) {
            System.out.println("error");
        }
        new Thread(()-> {
            File file = new File(ScriptPath.getText());
            try {
                FileReader reader = new FileReader(file);
                BufferedReader buffer = new BufferedReader(reader);
                db.executeLittleScript(buffer);
            } catch (SQLException | IOException e) {
                Platform.runLater(()->ShowError("Error SQL", "Error to add Event to database.\n" + e.getMessage()));
            }
        }).start(); */

        final List<String> events = EventsBox.getCheckModel().getCheckedItems();
        if (events != null && !events.isEmpty()) {
            //final loadingController progressWin = createProgress(progress);

            Task<Void> task = new Task<Void>() {

                private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
                private boolean mode = false;
                final int total = events.size();
                int count = 1;

                @Override
                protected void running() {
                    super.running();
                    updateProgress(0, 100);
                    updateTitle("Add Event's");
                    updateMessage("Adding Events.");
                    progress.addListener((_, _, number) -> {
                        updateProgress(number.doubleValue(), 100);
                    });
                }

                @Override
                protected Void call() throws Exception {
                    mode = db.getCommitMode();
                    if (mode) db.changeCommitMode(false);
                    for (final String event : events) {

                        final double newProgress = (double) count / total;

                        progress.set(progress.get() + newProgress);

                        db.executeCode(eventsFound.get(event));
                        count++;
                    }
                    db.commit();
                    return null;
                }

                @Override
                protected void failed() {
                    super.failed();
                    try {
                        db.back();
                    } catch (SQLException _) {
                    }
                    createErrorNotification(db.getDatabaseName() + "-event-1", "Event Error", "Error to add Event. " + getException().getMessage());
                    ShowError("Error SQL", "Error to add Event to database.", getException().getMessage());
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    createSuccessNotification(db.getDatabaseName() + "-event-1", "Event Success", "Success to add Event.", "");
                }

                @Override
                protected void done() {
                    super.done();
                    try {
                        if (mode) db.changeCommitMode(true);
                    } catch (SQLException _) {
                    }
                }

            };
            taskInterface.addTask(task);
            Thread.ofVirtual().start(task);
        }

    }

    private loadingController createProgress(final DoubleProperty progressRef) {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/loadingPane/loadingLayout.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            loadingController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Loading");
            subStage.setScene(new Scene(root));
            subStage.setResizable(false);
            secondaryController.setAttr(progressRef, "Importing Triggers", subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
            return secondaryController;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(getClass());
        }
        return null;
    }

    @FXML
    private void SearchEvents() {
        if (!exists(Path.of(ScriptPath.getText()))) {
            ShowError("Error file", "File do not exists.");
            return;
        }
        final Stage loading = LoadingStage("Fetching events", "Search the events on file.\nThis operation can be slower.");
        Thread.ofVirtual().start(()-> {
            File file = new File(ScriptPath.getText());
            try (BufferedReader buffer = new BufferedReader(new FileReader(file))) { // Try-with-resources
                String line;
                StringBuilder triggerCode = new StringBuilder();
                String triggerName = null;
                int endClausses = 0;
                boolean inTrigger = false;

                Pattern triggerNamePattern = Pattern.compile("CREATE\\s+EVENT\\s+([\\w\\.]+)"); // Regex para nome do trigger

                while ((line = buffer.readLine()) != null) {
                    String trimmedLine = line.trim();

                    if (trimmedLine.startsWith("--")) continue; // Ignora comentários

                    if (trimmedLine.toUpperCase().startsWith("CREATE EVENT")) {
                        endClausses++;
                        inTrigger = true;
                        String l = trimmedLine;
                        if (trimmedLine.toUpperCase().contains("IF NOT EXISTS")) {
                            l = trimmedLine.substring(trimmedLine.indexOf("IF NOT EXISTS") + "IF NOT EXISTS".length() + 1);
                            System.out.println("found " + l);
                            triggerName = l.replace(" ", "");
                        }
                        triggerCode.setLength(0); // Limpa o código anterior
                        triggerCode.append(line).append("\n");

                        Matcher matcher = triggerNamePattern.matcher(l);
                        if (matcher.find()) {
                            triggerName = matcher.group(1);
                        }
                    } else if (inTrigger) {
                        triggerCode.append(line).append("\n");

                        if (trimmedLine.toUpperCase().contains("WHEN")) {
                            endClausses++;
                        }

                        if (trimmedLine.toUpperCase().contains("END")) { // Detecção mais robusta do fim do trigger
                            endClausses--;
                        }
                        if (endClausses == 0) {
                            inTrigger = false;
                            if (triggerName != null) {
                                eventsFound.put(triggerName, triggerCode.toString());
                                triggerName = null;
                            }
                        }
                    }
                }
                Platform.runLater(()->EventsBox.getItems().addAll(eventsFound.keySet()));
            } catch (IOException e) {
                ShowError("Error", "Error to perform read.", e.getMessage());
            } finally {
                Platform.runLater(loading::close);
            }
        });
    }

    @FXML
    public void close() {
        stage.close();
    }

    public void cancelTask() {

    }

    @FXML
    private void addEvent() {

        String event = Dialog.TextDialog("Event interface", "Add event", "Name of the event:");

        if (event == null) return;

        if (!event.isEmpty() || eventsFound.containsKey(event)) {
            eventsFound.put(event, "");
        } else ShowInformation("Null", "You need to insert a valid name.");
    }

}

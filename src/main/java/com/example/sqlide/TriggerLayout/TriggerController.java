package com.example.sqlide.TriggerLayout;

import com.example.sqlide.Configuration.DatabaseConf;
import com.example.sqlide.Container.Editor.TextAreaAutocomplete;
import com.example.sqlide.Container.Editor.Words.SQLWords;
import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.Container.loading.loadingInterface;
import com.example.sqlide.Notification.NotificationInterface;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.exporter.JSON.JSONController;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.docx4j.wml.Tr;

import java.io.*;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static java.nio.file.Files.exists;

public class TriggerController implements loadingInterface, NotificationInterface {

    @FXML
    private VBox container;

    @FXML
    private ChoiceBox<String> comboTrigger;
    @FXML
    private TextField ScriptPath;

    @FXML
    private CheckComboBox<String> triggersSelected;

    private final ObservableMap<String, String> triggersFound = FXCollections.observableHashMap();

    private TaskInterface taskInterface;

    private DataBase db;

    private Stage stage;

    private final TextAreaAutocomplete codeArea = new TextAreaAutocomplete();

    public void initTriggerController(DataBase db, final TaskInterface taskInterface, Stage subStage) {
        this.db = db;
        this.taskInterface = taskInterface;
        this.stage = subStage;
        codeArea.setAutoCompleteWords(new ArrayList<>(List.of(SQLWords.getWords(db.getSQLType().ordinal()))));
    }

    @FXML
    private void initialize() {
        comboTrigger.setItems(triggersSelected.getCheckModel().getCheckedItems());
        comboTrigger.getSelectionModel().selectedItemProperty().addListener((_,_, value)->{
            codeArea.textProperty().removeListener(_->{});
            codeArea.setText(triggersFound.get(value));
            codeArea.textProperty().addListener((_,_, text)->triggersFound.put(value, text));
        });
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        container.getChildren().add(4, codeArea);
    }

    public void addTrigger(final HashMap<String, String> triggers) {
        triggersFound.putAll(triggers);
        triggersSelected.getItems().addAll(triggersFound.keySet());
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
        final List<String> triggers = triggersSelected.getCheckModel().getCheckedItems();
        if (triggers != null && !triggers.isEmpty()) {
            Task<Void> TriggerTask = new Task<Void>() {

                private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
                private final int total = triggers.size();
                private int count = 1;
                private boolean mode = false;

                @Override
                protected void running() {
                    super.running();
                    updateProgress(0, 100);
                    updateTitle("Add Triggers");
                    updateMessage("Adding Triggers.");
                    progress.addListener((_, _, number) -> {
                        updateProgress(number.doubleValue(), 100);
                    });
                }

                @Override
                protected Void call() throws Exception {
                    mode = db.getCommitMode();
                    if (mode) db.changeCommitMode(false);
                    for (final String trigger : triggers) {

                        final double newProgress = (double) count / total;

                        progress.set(progress.get() + newProgress);

                        db.executeCode(triggersFound.get(trigger));
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
                    createErrorNotification(db.getDatabaseName() + "-trigger-1", "Trigger Error", "Error to add Trigger. " + getException().getMessage());
                    ShowError("Error SQL", "Error to add Trigger to database.", getException().getMessage());
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    createSuccessNotification(db.getDatabaseName() + "-trigger-1", "Trigger Success", "Success to add Trigger.", "");
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
            taskInterface.addTask(TriggerTask);
            Thread.ofVirtual().start(TriggerTask);
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
    private void searchTriggers() {
        if (!exists(Path.of(ScriptPath.getText()))) {
            ShowError("Error file", "File do not exists.");
            return;
        }
        Thread.ofVirtual().start(()->{
            File file = new File(ScriptPath.getText());
            try (BufferedReader buffer = new BufferedReader(new FileReader(file))) { // Try-with-resources
                String line;
                StringBuilder triggerCode = new StringBuilder();
                String triggerName = null;
                int endClausses = 0;
                boolean inTrigger = false;

                Pattern triggerNamePattern = Pattern.compile("CREATE\\s+TRIGGER\\s+([\\w\\.]+)"); // Regex para nome do trigger

                while ((line = buffer.readLine()) != null) {
                    String trimmedLine = line.trim();

                    if (trimmedLine.startsWith("--")) continue; // Ignora comentários

                    if (trimmedLine.toUpperCase().startsWith("CREATE TRIGGER")) {
                        endClausses++;
                        inTrigger = true;
                        String l = trimmedLine;
                        if (trimmedLine.toUpperCase().contains("IF NOT EXISTS")) {
                            l = trimmedLine.substring(trimmedLine.indexOf("IF NOT EXISTS")+"IF NOT EXISTS".length()+1);
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
                                triggersFound.put(triggerName, triggerCode.toString());
                                triggerName = null;
                            }
                        }
                    }
                }

                //  triggersSelected.getItems().clear();

          /*  for (final String name : triggersFound.keySet()) {
                triggersSelected.getItems().add(name);
            } */

                Platform.runLater(()->triggersSelected.getItems().addAll(triggersFound.keySet()));
            } catch (IOException e) {
                ShowError("Error", "Error to perform read.", e.getMessage());
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
    private void addTrigger() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("addTrigger.fxml"));
            Parent root = loader.load();

            AddTrigger secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Add Trigger");
            subStage.setScene(new Scene(root));
            secondaryController.setNames(triggersFound);
            secondaryController.setStage(subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.showAndWait();

            triggersSelected.getItems().clear();
            triggersSelected.getItems().addAll(triggersFound.keySet());

        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }
    }
}

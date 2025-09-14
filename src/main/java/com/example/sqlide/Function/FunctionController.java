package com.example.sqlide.Function;

import com.example.sqlide.Container.Editor.TextAreaAutocomplete;
import com.example.sqlide.Container.Editor.Words.SQLWords;
import com.example.sqlide.Notification.NotificationInterface;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.misc.Dialog;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.sqlide.popupWindow.handleWindow.*;
import static java.nio.file.Files.exists;

public class FunctionController implements NotificationInterface {

    @FXML
    private Label userLabel;
    @FXML
    private CheckComboBox<Function> FunctionSelectedBox;
    @FXML
    private JFXTextField pathField;
    @FXML
    private VBox FunctionContainer;
    @FXML
    private ChoiceBox<Function> FunctionBox;

    private DataBase db;

    private Stage stage;

    private final ObservableList<Function> FunctionList = FXCollections.observableArrayList();

    private List<Function> DefaultList;

    private final TextAreaAutocomplete codeArea = new TextAreaAutocomplete();

    private TaskInterface taskInterface;

    public void initFunctionController(DataBase db, TaskInterface taskInterface, Stage subStage) throws SQLException {
        this.db = db;
        this.taskInterface = taskInterface;
        this.stage = subStage;
        codeArea.setAutoCompleteWords(new ArrayList<>(List.of(SQLWords.getWords(db.getSQLType().ordinal()))));
        FunctionList.setAll(db.getFunctions());
        DefaultList = FunctionList.stream().toList();
    }

    @FXML
    private void initialize() {
        FunctionBox.setItems(FunctionList);

        final AtomicReference<ChangeListener<String>> currentCodeListener = new AtomicReference<>();

        FunctionBox.getSelectionModel().selectedItemProperty().addListener((_,_, value)->{
            if (value != null) {
                currentCodeListener.set((_, _, text) -> {
                    value.code.set(text);
                    System.out.println(value.code.get());});
                codeArea.textProperty().addListener(currentCodeListener.get());
                codeArea.setText(value.code.get());
                userLabel.setText(value.user.get());
            } else {
                codeArea.setText("");
            }
        });
        FunctionBox.getItems().addListener((ListChangeListener<? super Function>) val->{
            while (val.next()) {
                if (val.wasAdded()) {
                    FunctionBox.getSelectionModel().select(val.getAddedSubList().getFirst());
                }
            }
        });

        FunctionSelectedBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<? super Function>) (val)->{
            while (val.next()) {
                if (val.wasAdded()) {
                    FunctionList.add(val.getAddedSubList().getFirst());
                } else {
                    FunctionList.remove(val.getRemoved().getFirst());
                    FunctionBox.getSelectionModel().select(null);
                }
            }
        });
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        FunctionContainer.getChildren().add(codeArea);
    }

    @FXML
    private void searchFunction() {
        if (!exists(Path.of(pathField.getText()))) {
            ShowError("Error file", "File do not exists.");
            return;
        }
        final Stage loading = LoadingStage("Fetching functions", "Search the functions on file.\nThis operation can be slower.");
        Thread.ofVirtual().start(()->{
            File file = new File(pathField.getText());
            try (BufferedReader buffer = new BufferedReader(new FileReader(file))) { // Try-with-resources
                String line;
                StringBuilder triggerCode = new StringBuilder();
                String triggerName = null;
                int endClausses = 0;
                boolean inTrigger = false;

                final ArrayList<Function> FunctionFound = new ArrayList<>();

                Pattern viewNamePattern = Pattern.compile("CREATE\\s+FUNCTION\\s+([\\w\\.]+)", Pattern.CASE_INSENSITIVE); // Regex para nome do trigger

                while ((line = buffer.readLine()) != null) {
                    String trimmedLine = line.trim();

                    if (trimmedLine.startsWith("--")) continue; // Ignora comentários

                    if (trimmedLine.toUpperCase().startsWith("CREATE FUNCTION")) {
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

                        Matcher matcher = viewNamePattern.matcher(l);
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
                                FunctionFound.add(new Function(triggerName, triggerCode.toString(), db.getUsername()));
                                triggerName = null;
                            }
                        }
                    }
                }

                Platform.runLater(()->FunctionSelectedBox.getItems().addAll(FunctionFound));

            } catch (IOException e) {
                ShowError("Error", "Error to perform read.", e.getMessage());
            } finally {
                Platform.runLater(loading::close);
            }
        });

    }

    @FXML
    private void add() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("AddFunction.fxml"));
            //    VBox miniWindow = loader.load();
            Pane root = loader.load();

            Stage subStage = new Stage();
            subStage.setTitle("Add function");
            subStage.setResizable(false);
            subStage.initModality(Modality.APPLICATION_MODAL);
            subStage.setScene(new Scene(root));

            AddFunction controller = loader.getController();
            controller.setStage(subStage);
            controller.setUser(db.getUsername());
            controller.setList(FunctionList);

            subStage.show();
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.", e.getMessage());
        }
    }

    @FXML
    private void remove() {
        final Function index = FunctionBox.getValue();
        if (index != null) {
            FunctionList.remove(index);
            FunctionBox.getSelectionModel().select(null);
        } else {
            ShowInformation("Null", "You need to select a value to remove.");
            FunctionBox.requestFocus();
        }
    }

    @FXML
    private void confirm() {

        final List<Function> removed = DefaultList.stream().filter(view -> !FunctionList.contains(view)).toList();
        final List<Function> added = FunctionList.stream().filter(view -> !DefaultList.contains(view) && !view.Name.get().isEmpty()).toList();

        Task<Void> addViewTask = new Task<Void>() {

            private boolean mode = false;
            private final SimpleDoubleProperty progress = new SimpleDoubleProperty(0);
            private final double iteract = (double) (removed.size() + added.size()) / 100;

            @Override
            protected void running() {
                super.running();
                updateProgress(0, 100);
                updateTitle("Add functions");
                updateMessage("Adding or deleting functions.");
                progress.addListener((_, _, number) -> {
                    updateProgress(number.doubleValue(), 100);
                });
            }

            @Override
            protected Void call() throws Exception {
                mode = db.getCommitMode();
                if (mode) db.changeCommitMode(false);
                for (final Function function : removed) if (!db.dropFunction(function.Name.get())) throw new SQLException(db.GetException()); else progress.set(progress.get()+iteract);
                for (final Function function : added) if (!db.createFunction(function)) throw new SQLException(db.GetException()); else progress.set(progress.get()+iteract);
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
                createErrorNotification(db.getDatabaseName() + "-function-1", "Function Error", "Error to manage Function.\n"+getException().getMessage());
                ShowError("SQL", "Error to add function", getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                createSuccessNotification(db.getDatabaseName() + "-function-1", "Function Success", "Success to manage Function.", "");
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
        taskInterface.addTask(addViewTask);
        Thread.ofVirtual().start(addViewTask);
    }

    @FXML
    private void searchFile() {
        FileChooser selectFileWindow = new FileChooser();
        selectFileWindow.getExtensionFilters().add(new FileChooser.ExtensionFilter("script SQL", "*.sql"));

        final File selectedFile = selectFileWindow.showOpenDialog(stage);
        if (selectedFile != null) {
            pathField.setText(selectedFile.getAbsolutePath());
        }
    }

    public void addFunction(Function function) {
        FunctionList.add(function);
    }

    public static class Function {
        public final SimpleStringProperty Name, code, user;

        public Function(final String Name, final String code, final String user) {
            this.Name = new SimpleStringProperty(Name);
            this.code = new SimpleStringProperty(code);
            this.user = new SimpleStringProperty(user);
        }

        @Override
        public String toString() {
            return Name.get();
        }
    }

}

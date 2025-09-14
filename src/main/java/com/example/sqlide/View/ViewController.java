package com.example.sqlide.View;

import com.example.sqlide.Container.Editor.TextAreaAutocomplete;
import com.example.sqlide.Container.Editor.Words.SQLWords;
import com.example.sqlide.Notification.NotificationInterface;
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

public class ViewController implements NotificationInterface {

    @FXML
    private CheckComboBox<View> ViewSelectedBox;
    @FXML
    private JFXTextField pathField;
    @FXML
    private TextArea CommentArea;
    @FXML
    private VBox ViewContainer;
    @FXML
    private ChoiceBox<View> ViewBox;

    private DataBase db;

    private Stage stage;

    private final ObservableList<View> ViewList = FXCollections.observableArrayList();

    private List<View> DefaultList;

    private final TextAreaAutocomplete codeArea = new TextAreaAutocomplete();

    private TaskInterface taskInterface;

    public void initViewController(DataBase db, TaskInterface taskInterface, Stage subStage) throws SQLException {
        this.db = db;
        this.taskInterface = taskInterface;
        this.stage = subStage;
        codeArea.setAutoCompleteWords(new ArrayList<>(List.of(SQLWords.getWords(db.getSQLType().ordinal()))));
        ViewList.setAll(db.getViews());
        DefaultList = ViewList.stream().toList();
    }

    @FXML
    private void initialize() {
        ViewBox.setItems(ViewList);

        final AtomicReference<ChangeListener<String>>[] currentCommentListener = new AtomicReference[]{new AtomicReference<>()};
        final AtomicReference<ChangeListener<String>>[] currentCodeListener = new AtomicReference[]{new AtomicReference<>()};

        ViewBox.getSelectionModel().selectedItemProperty().addListener((_,_, value)->{
            if (currentCommentListener[0].get() != null) {
                codeArea.textProperty().removeListener(currentCodeListener[0].get());
                CommentArea.textProperty().removeListener(currentCommentListener[0].get());
            }
            if (value != null) {
                currentCommentListener[0].set((_, _, text) -> value.comment.set(text));
                currentCodeListener[0].set((_, _, text) -> {
                    value.code.set(text);
                    System.out.println(value.code.get());});
                CommentArea.textProperty().addListener(currentCommentListener[0].get());
                codeArea.textProperty().addListener(currentCodeListener[0].get());
                CommentArea.setText(value.comment.get());
                codeArea.setText(value.code.get());
            } else {
                codeArea.setText("");
                CommentArea.setText("");
            }
        });
        ViewBox.getItems().addListener((ListChangeListener<? super View>) val->{
            while (val.next()) {
                if (val.wasAdded()) {
                    ViewBox.getSelectionModel().select(val.getAddedSubList().getFirst());
                }
            }
        });

        ViewSelectedBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<? super View>) (val)->{
            while (val.next()) {
                if (val.wasAdded()) {
                    ViewList.add(val.getAddedSubList().getFirst());
                } else {
                    ViewList.remove(val.getRemoved().getFirst());
                    ViewBox.getSelectionModel().select(null);
                }
            }
        });
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        ViewContainer.getChildren().add(codeArea);
    }

    @FXML
    private void searchView() {
        if (!exists(Path.of(pathField.getText()))) {
            ShowError("Error file", "File do not exists.");
            return;
        }
        final Stage loading = LoadingStage("Fetching views", "Search the views on file.\nThis operation can be slower.");
        Thread.ofVirtual().start(()->{
            File file = new File(pathField.getText());
            try (BufferedReader buffer = new BufferedReader(new FileReader(file))) { // Try-with-resources
                String line;
                StringBuilder triggerCode = new StringBuilder();
                String triggerName = null;
                int endClausses = 0;
                boolean inTrigger = false;

                final ArrayList<View> ViewFound = new ArrayList<>();

                Pattern viewNamePattern = Pattern.compile("CREATE\\s+VIEW\\s+([\\w\\.]+)"); // Regex para nome do trigger

                while ((line = buffer.readLine()) != null) {
                    String trimmedLine = line.trim();

                    if (trimmedLine.startsWith("--")) continue; // Ignora comentários

                    if (trimmedLine.toUpperCase().startsWith("CREATE VIEW")) {
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
                                ViewFound.add(new View(triggerName, "", triggerCode.toString()));
                                triggerName = null;
                            }
                        }
                    }
                }

                Platform.runLater(()->ViewSelectedBox.getItems().addAll(ViewFound));

            } catch (IOException e) {
                ShowError("Error", "Error to perform read.", e.getMessage());
            } finally {
                Platform.runLater(loading::close);
            }
        });

    }

    @FXML
    private void add() {
        String view = Dialog.TextDialog("View interface", "Add view", "Name of the view:");

        if (view == null) return;

        if (view.isEmpty() || ViewList.stream().noneMatch(view_name -> view_name.Name.get().equals(view))) {
            ViewList.add(new ViewController.View(view, "", ""));
        } else ShowInformation("Null", "You need to insert a valid name.");
    }

    @FXML
    private void remove() {
        final View index = ViewBox.getValue();
        if (index != null) {
            ViewList.remove(index);
            ViewBox.getSelectionModel().select(null);
        } else {
            ShowInformation("Null", "You need to select a value to remove.");
            ViewBox.requestFocus();
        }
    }

    @FXML
    private void confirm() {

        final List<View> removed = DefaultList.stream().filter(view -> !ViewList.contains(view)).toList();
        final List<View> added = ViewList.stream().filter(view -> !DefaultList.contains(view) && !view.Name.get().isEmpty()).toList();

        Task<Void> addViewTask = new Task<Void>() {

            private boolean mode = false;
            private final SimpleDoubleProperty progress = new SimpleDoubleProperty(0);
            private final double iteract = (double) (removed.size() + added.size()) / 100;

            @Override
            protected void running() {
                super.running();
                updateProgress(0, 100);
                updateTitle("Add views");
                updateMessage("Adding or deleting views.");
                progress.addListener((_, _, number) -> {
                    updateProgress(number.doubleValue(), 100);
                });
            }

            @Override
            protected Void call() throws Exception {
                mode = db.getCommitMode();
                if (mode) db.changeCommitMode(false);
                for (final View view : removed) if (!db.dropView(view.Name.get())) throw new SQLException(db.GetException()); else progress.set(progress.get()+iteract);
                for (final View view : added) if (!db.createView(view)) throw new SQLException(db.GetException()); else progress.set(progress.get()+iteract);
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
                createErrorNotification(db.getDatabaseName() + "-view-1", "View Error", "Error to manage View.\n"+getException().getMessage());
                ShowError("SQL", "Error to add view", getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                createSuccessNotification(db.getDatabaseName() + "-view-1", "View Success", "Success to manage View.", "");
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

    public void addView(View view) {
        ViewList.add(view);
    }

    public static class View {
        public final SimpleStringProperty Name, comment, code;

        public View(final String Name, final String comment, final String code) {
            this.Name = new SimpleStringProperty(Name);
            this.comment = new SimpleStringProperty(comment);
            this.code = new SimpleStringProperty(code);
        }

        @Override
        public String toString() {
            return Name.get();
        }
    }

}

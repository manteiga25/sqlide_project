package com.example.sqlide.Editor;

import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import static com.example.sqlide.popupWindow.handleWindow.ShowConfirmation;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class EditorController {
    @FXML
    private TabPane TabContainer;
    @FXML
    private VBox Container;
    @FXML
    private ChoiceBox<String> SchemaBox;
    @FXML
    private Button TaskButton;

    private final ImageView imgPause, imgPlay;

    private final static String playColor = "#4E8752", pauseColor = "#B54747";

    private final static String playTool = "Execute code", StopCode = "Stop code";

    private final AtomicReference<Thread> work = new AtomicReference<>();

    private ObservableList<DataBase> SchemasOpened;

    private ObservableList<String> SchemasName = FXCollections.observableArrayList();

    private final ArrayList<FileEditor> editors = new ArrayList<>();

    public Node getContainer() {
        return Container;
    }

    public EditorController() {
        imgPause = new ImageView(Objects.requireNonNull(getClass().getResource("/img/stop.png")).toExternalForm());
        imgPause.setPreserveRatio(true);
        imgPause.setFitHeight(15);
        imgPause.setFitWidth(15);

        imgPlay = new ImageView(Objects.requireNonNull(getClass().getResource("/img/play.png")).toExternalForm());
        imgPlay.setPreserveRatio(true);
        imgPlay.setFitHeight(15);
        imgPlay.setFitWidth(15);

    }

    public void setList(final ObservableList<DataBase> list, final ObservableList<String> names) {
        SchemasOpened = list;
        SchemasName = names;
        SchemaBox.setItems(SchemasName);
    }

    @FXML
    private void RunButton() {
        BooleanProperty state = (SimpleBooleanProperty) TaskButton.getUserData();
        state.set(!state.get());
        if (state.get()) {
            TaskButton.setGraphic(imgPause);
            TaskButton.setStyle("-fx-background-color: " + pauseColor + ";");
            TaskButton.getTooltip().setText(StopCode);

            work.set(new Thread(() -> {
                try {
                    final int index = SchemaBox.getSelectionModel().getSelectedIndex();
                    if (index != -1) {
                        System.out.println(index);
                        SchemasOpened.forEach(System.out::println);
                        final DataBase selectedDB = SchemasOpened.get(index);
                        selectedDB.executeCode(editors.get(TabContainer.getSelectionModel().getSelectedIndex()-1).getText());
                    } else {
                        ShowError("No selected", "No Database selected to execute code.");
                        Platform.runLater(() -> SchemaBox.requestFocus());
                    }
                } catch (SQLException e) {
                    ShowError("SQL Error", "Error to execute code.\n" + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error executing: " + e.getMessage());
                } finally {
                    Platform.runLater(() -> {
                        TaskButton.setGraphic(imgPlay);
                        TaskButton.setStyle("-fx-background-color: " + playColor + ";");
                        TaskButton.getTooltip().setText(playTool);
                        state.set(false); // Resetar o estado
                    });
                }
            }));

            work.get().start();
        } else {
            TaskButton.setGraphic(imgPlay);
            TaskButton.setStyle("-fx-background-color: " + playColor + ";");
            TaskButton.getTooltip().setText(playTool);
            work.get().interrupt();
        }
    }

    @FXML
    private void initialize() {
        SchemaBox.setItems(SchemasName);
        TaskButton.setUserData(new SimpleBooleanProperty(false));
        TaskButton.setTooltip(new Tooltip(playTool));
        final Tab newTab = new Tab("");
        FileEditor editor = new FileEditor(null);
        TabContainer.getTabs().add(newTab);
        editor.putContainer(newTab);
        editors.add(editor);
        TabHandler();
    }

    private void TabHandler() {
        TabContainer.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    final Tab addedTab = change.getAddedSubList().getFirst();
                    TabContainer.getSelectionModel().select(addedTab);
                }
                else if (change.wasRemoved()) {
                        final FileEditor editor = editors.remove(TabContainer.getSelectionModel().getSelectedIndex()-1);
                        if (editor != null) {
                            if (editor.isChanged()) {
                                if (ShowConfirmation("No saved", "The content is not saved.\nDo you want to save?")) {
                                    editor.save();
                                }
                            }
                        }
                }

                if (TabContainer.getTabs().size() == 1) {
                    final Tab tab = TabContainer.getTabs().getFirst();
                    tab.setDisable(false);
                    TabContainer.getSelectionModel().select(tab);
                } else {
                    TabContainer.getTabs().getFirst().setDisable(true);
                }

            }
        });
    }

    public void setOpenScript() {
        try {
            FileChooser selectFileWindow = new FileChooser();
            selectFileWindow.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("script SQL", "*.sql"));

            final File selectedFile = selectFileWindow.showOpenDialog(Container.getScene().getWindow());
            if (selectedFile != null) {
                final String ScriptPath = selectedFile.getAbsolutePath();
                final String ScriptName = selectedFile.getName();

                Optional<Tab> tabs = TabContainer.getTabs().stream()
                        .filter(tab -> ScriptPath.equals(tab.getId()))
                        .findFirst();

                if (tabs.isPresent()) {
                    TabContainer.getSelectionModel().select(tabs.get());
                } else {
                    createFolderEditor(ScriptPath, ScriptName);
                }
                    //  ScriptsOpened.add(ScriptPath);
            }
        } catch (Exception e) {
            ShowError("Script", "Error to open SQL file.\n" + e.getMessage());
        }
    }

    @FXML
    public void createScript() {
        final FileEditor editor = new FileEditor(null);
        final Tab newTab = new Tab("");
        TabContainer.getTabs().add(newTab);
        editor.putContainer(newTab);
        editor.createScript();
        editors.add(editor);
       /*     final String path = editor.getPath();
            final String name = path.substring(path.lastIndexOf("/"));
            tab.setText(name);
            tab.setId(path); */
    }

    @FXML
    private void createFolderEditor(final String path, final String name) {
        Tab folderTab = new Tab();
        folderTab.setId(path);
        folderTab.setText(name);
        try {
            FileEditor editor = new FileEditor(path);
            //    editor.loadFile();
            //  editor.attachToTab(folderTab);
            editor.readScript();
            editor.putContainer(folderTab);
            editors.add(editor);
            TabContainer.getTabs().add(folderTab);
        } catch (IOException e) {
            ShowError("Error to read", "Error to read script " + path + ".\n");
        }
    }

}

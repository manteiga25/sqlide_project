package com.example.sqlide.Editor;

import com.example.sqlide.Container.Editor.TextAreaAutocomplete;
import com.example.sqlide.Container.Editor.Words.SQLWords;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class FileEditor {

    private String path;

    private final VBox EditorContainer = new VBox(5);

    private final KeyCombination cntrlS = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);

    private final TextAreaAutocomplete editorText = new TextAreaAutocomplete();

    private Tab tab;

    public String getPath() {
        return path;
    }

    public boolean isChanged() {
        return editorText.isChanged();
    }

    public String getText() {
        return editorText.getText();
    }

    public FileEditor(final String path) {
        this.path = path;

        HBox toolsBox = new HBox(5);
        toolsBox.setPadding(new Insets(0,0,5,10));

        Label dialetLabel = new Label("Script dialet:");
        dialetLabel.setTextFill(Color.WHITE);

        ComboBox<String> selectDialet = new ComboBox<>();
        selectDialet.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ComboboxModern.css")).toExternalForm());

        selectDialet.getItems().addAll("SQLite", "MySQL", "Postgre");

        selectDialet.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue)->{
            final int dialet1 = oldValue.intValue();
            final int dialet2 = newValue.intValue();
            if (dialet2 != dialet1) {
                editorText.setAutoCompleteWords(new ArrayList<>(List.of((SQLWords.getWords(dialet2)))));
            }
        });

        selectDialet.getSelectionModel().select(0);

        toolsBox.getChildren().addAll(dialetLabel, selectDialet);
        toolsBox.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(EditorContainer, Priority.ALWAYS);

        EditorContainer.getChildren().addAll(editorText, toolsBox);

        VBox.setVgrow(editorText, Priority.ALWAYS);

        initializeHandler();
    }

    private void initializeHandler() {
        editorText.setOnKeyPressed(e-> {
            if (cntrlS.match(e)) {
                save();
            }
        });
        editorText.getChanged().addListener((_,_,value)->{
            if (tab != null) {
                final String name = value ? tab.getText() + "*" : tab.getText().substring(0, tab.getText().lastIndexOf("*"));
                tab.setText(name);
            }
        });
    }

    public void readScript() throws IOException {
        File file = new File(path);
        try (BufferedReader buffer = new BufferedReader(new FileReader(file))) {
            String line = null;
            StringBuilder s = new StringBuilder();
            while ((line = buffer.readLine()) != null) {
                s.append(line).append("\n");
            }
            editorText.setText(s.toString());
            editorText.setChanged(false);
        }
    }

    public void putContainer(final Tab tab) {
        this.tab = tab;
        tab.setContent(EditorContainer);
    }

    void save() {
        if (editorText.isChanged()) {
            editorText.setChanged(false);

            if (path == null || path.isEmpty()) {
                createScript();
                tab.setText(path.substring(path.lastIndexOf("\\")+1));
                tab.setId(path);
            }

            try {
                Path tempFile = Files.write(
                        Paths.get(path + ".bak"),
                        editorText.getText().getBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );

                Files.move(tempFile, Paths.get(path),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                ShowError("Error I/O", "Error to save.\n" + e.getMessage());
            }
        } else {
            System.out.println("not");
        }
    }

    @FXML
    public void createScript() {
        try {
            FileChooser selectFileWindow = new FileChooser();
            selectFileWindow.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("script SQL", "*.sql"));

            final File selectedFile = selectFileWindow.showSaveDialog(EditorContainer.getScene().getWindow());
            if (selectedFile != null) {
                if (selectedFile.exists()) {
                    if (!selectedFile.delete() || !selectedFile.createNewFile()) {
                        throw new Exception("");
                    }
                } else if (!selectedFile.createNewFile()) {
                    throw new Exception("");
                }
                path = selectedFile.getPath();
                tab.setId(path);
                tab.setText(selectedFile.getName());
              //  createFolderEditor(selectedFile.getAbsolutePath(), selectedFile.getName());
            }
        } catch (Exception e) {
            ShowError("Script", "Error to create SQL file.\n" + e.getMessage());
        }
    }

}

package com.example.sqlide.editor;

import com.example.sqlide.Container.Editor.TextAreaAutocompleteLines;
import com.example.sqlide.Container.Editor.Words.SQLWords;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class FileEditor {

    private final String path;

    private final VBox EditorContainer = new VBox();

    private final KeyCombination cntrlS = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);

    private final TextAreaAutocompleteLines editorText = new TextAreaAutocompleteLines();

    public FileEditor(final String path) {
        this.path = path;

        HBox toolsBox = new HBox(10);
        toolsBox.setPadding(new Insets(0,0,0,10));

        Label dialetLabel = new Label("Script dialet");
        dialetLabel.setTextFill(Color.WHITE);

        ComboBox<String> selectDialet = new ComboBox<>();

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

        VBox.setVgrow(toolsBox, Priority.NEVER);

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
        }
    }

    public void putContainer(final Tab tab) {
        tab.setContent(EditorContainer);
    }

    private void save() {
        if (editorText.isChanged()) {
            editorText.setChanged(false);
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

}

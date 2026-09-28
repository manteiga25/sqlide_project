package com.example.sqlide;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class MS_accessController {

    @FXML
    private TextField TextDBName, SaveDBPath;

    @FXML
    private Button Submit;

    private mainController ref;

    private Stage stage;

    @FXML
    private void selectDir() {
        DirectoryChooser selectFolderWindow = new DirectoryChooser();

        final File selectedDir = selectFolderWindow.showDialog(stage);
        if (selectedDir != null) {
            SaveDBPath.setText(selectedDir.getAbsolutePath());
        }
    }

    public void initWin(final mainController ref, final Stage stage) {
        this.ref = ref;
        this.stage = stage;

        Submit.setOnAction(e -> {
            try {
                createDB();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @FXML
    public void createDB() throws IOException {
        String path = SaveDBPath.getText();
        path += path.isEmpty() ? TextDBName.getText() : File.separator + TextDBName.getText();
        ref.createDB(path, TextDBName.getText(), new HashMap<>());
    }
}

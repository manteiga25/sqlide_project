package com.example.sqlide.misc;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.docx4j.vml.officedrawing.STRType;

import java.io.File;
import java.io.IOException;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class path {

    public static String createFile(final Stage stage) throws IOException {
            String path = null;
            FileChooser selectFileWindow = new FileChooser();
            selectFileWindow.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("script SQL", "*.sql"));

            final File selectedFile = selectFileWindow.showSaveDialog(stage);
            if (selectedFile != null) {
                if (selectedFile.exists()) {
                    if (!selectedFile.delete() || !selectedFile.createNewFile()) {
                        throw new IOException("");
                    }
                } else if (!selectedFile.createNewFile()) {
                    throw new IOException("");
                }
                path = selectedFile.getPath();
                //  createFolderEditor(selectedFile.getAbsolutePath(), selectedFile.getName());
            }
            return path;
    }

}

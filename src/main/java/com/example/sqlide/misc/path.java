package com.example.sqlide.misc;

import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.docx4j.vml.officedrawing.STRType;

import java.io.File;
import java.io.IOException;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class path {

    public static String createFile(final Stage stage, final String[] name, final String[] extension) throws IOException {
        String path = null;
        FileChooser selectFileWindow = new FileChooser();
        final ObservableList<FileChooser.ExtensionFilter> filter = selectFileWindow.getExtensionFilters();
        for (int i = 0; i < extension.length; i++) {
            filter.add(new FileChooser.ExtensionFilter(name[i], extension[i]));
        }

            final File selectedFile = selectFileWindow.showSaveDialog(stage);
            if (selectedFile != null) {
                if (selectedFile.exists()) {
                    if (!selectedFile.delete()) {
                        throw new IOException("");
                    }
                    path = selectedFile.getAbsolutePath();
                    //  createFolderEditor(selectedFile.getAbsolutePath(), selectedFile.getName());
                }
            }
            return path;
    }

}

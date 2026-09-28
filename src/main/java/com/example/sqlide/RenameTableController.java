package com.example.sqlide;

import com.example.sqlide.drivers.model.DataBase;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;

import static com.example.sqlide.popupWindow.handleWindow.*;

public class RenameTableController {

    @FXML
    TextField NameField;

    private String oldName;

    private StringProperty name;

    private DataBase db = null;

 //   private Tab tableCol;

    public void createController(final DataBase db, final StringProperty name) {
        oldName = name.get();
        this.db = db;
        this.name = name;
        //   tableCol = col;
        NameField.setText(name.get());
    }

    @FXML
    private void save() {
        final String name = NameField.getText() == null ? "" : NameField.getText().trim();

        if (name.isEmpty() || name.equals(oldName)) {
            ShowError("Invalid", "You need to write a new name for your table.");
            NameField.requestFocus();
            return;
        }

        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_ ]*")) {
            ShowError("Invalid name", "Use letters (accents are fine), digits, spaces and underscore, starting with a letter.");
            NameField.requestFocus();
            return;
        }

        if (!db.renameTable(oldName, name)) {
            ShowError("SQL Error", "Error to rename Table " + oldName + " to " + name + ".\n" + db.GetException());
        } else {
          //  tableCol.setText(name);
            this.name.set(name);
            // Sem isto, um segundo rename na mesma janela usava o nome original.
            oldName = name;
            ShowSucess("Success", "Success to switch name.");
            close();
        }

    }

    @FXML
    private void close() {
        if (NameField.getScene() != null && NameField.getScene().getWindow() != null) {
            NameField.getScene().getWindow().hide();
        }
    }

}

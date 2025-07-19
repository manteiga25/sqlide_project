package com.example.sqlide;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.model.DataBase;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowSucess;

public class RenameColumnController {
    @FXML
    private TextField NameField;

    private String oldName, table;

    private ColumnMetadata name;

    private DataBase db = null;

    public void createController(final DataBase db, final String table, final ColumnMetadata name) {
        oldName = name.Name;
        this.db = db;
        this.name = name;
        //   tableCol = col;
        this.table = table;
        NameField.setText(name.Name);
    }

    @FXML
    private void save() {
        final String name = NameField.getText();

        if (name == null || name.isEmpty() || name.equals(oldName)) {
            ShowError("Invalid", "You need to write a name for your table.");
            return;
        }

        if (!db.renameColumn(table, oldName, name)) {
            ShowError("SQL Error", "Error to rename Table " + oldName + " to " + name + ".\n" + db.GetException());
        } else {
            //  tableCol.setText(name);
            this.name.Name = name;
            ShowSucess("Success", "Success to switch name.");
        }
    }

}

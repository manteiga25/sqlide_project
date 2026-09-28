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
        final String name = NameField.getText() == null ? "" : NameField.getText().trim();

        if (name.isEmpty() || name.equals(oldName)) {
            ShowError("Invalid", "You need to write a new name for your column.");
            NameField.requestFocus();
            return;
        }

        // Os drivers já põem o nome entre aspas, mas um nome só com letras, algarismos,
        // espaços e _ continua a ser o que qualquer motor e qualquer consulta escrita à mão
        // aceitam sem surpresas. Letras acentuadas (descrição) são letras.
        if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_ ]*")) {
            ShowError("Invalid name", "Use letters (accents are fine), digits, spaces and underscore, starting with a letter.");
            NameField.requestFocus();
            return;
        }

        if (!db.renameColumn(table, oldName, name)) {
            ShowError("SQL Error", "Error to rename column " + oldName + " to " + name + ".\n" + db.GetException());
        } else {
            //  tableCol.setText(name);
            this.name.Name = name;
            // O oldName ficava no valor inicial: renomear duas vezes seguidas na mesma
            // janela mandava um ALTER sobre uma coluna que já não existia.
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

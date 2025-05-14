package com.example.sqlide;

import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.ArrayList;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class DeleteColumn {

    private Stage win;

    private TableInterface ref;

    String Table;

    @FXML
    Label LabelTable;

    @FXML
    ComboBox<String> choice;

    public void DeleteColumnInnit(final String Table, final ArrayList<String> columns, final Stage subStage, final TableInterface ref) {
        this.win = subStage;
        this.ref = ref;
        this.Table = Table;
        if (columns == null || columns.isEmpty()) {
            ShowInformation("No data", "No have column to delete.");
            closeWindow();
            return;
        }
        LabelTable.setText("Table " + Table);
        choice.getItems().addAll(columns);
    }

    @FXML
    private void deleteColumn() {
        final String column = choice.getValue();
        if (column == null) {
            ShowError("No data", "Choose a column to delete.");
            return;
        }
        final int col = choice.getItems().indexOf(column);
        if (ref.deleteColumn(Table, column, col)) {
            closeWindow();
        }
    }

    @FXML
    private void closeWindow() {
        win.close();
    }

}

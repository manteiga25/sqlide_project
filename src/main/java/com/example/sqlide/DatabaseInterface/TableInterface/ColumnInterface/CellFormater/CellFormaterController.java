package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater;

import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class CellFormaterController {

    private CellFormater format;

    private Stage stage;

    private String CellColorHex, TextColorHex;

    @FXML
    private ColorPicker CellColor, TextColor;

    @FXML
    private TextField valueText;

    @FXML
    private ComboBox<String> ConditionBox;

    @FXML
    private void initialize() {
        ConditionBox.getItems().addAll("==", "<", ">", "<=", ">=", "!=");
    }

    @FXML
    private void SetCellColor() {
        Color color = CellColor.getValue();
        CellColorHex = String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    @FXML
    private void SetTextColor() {
        Color color = TextColor.getValue();
        TextColorHex = String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    @FXML
    private void save() {
        final String value = valueText.getText();
        final byte condition = (byte) ConditionBox.getSelectionModel().getSelectedIndex();

        if ((value != null && !value.isEmpty()) || condition != -1) {
            format.setStyle("-fx-background-color: " + CellColorHex + "; -fx-text-fill: " + TextColorHex + ";");
            format.format = true;
            format.value = value;
            format.operation = condition;
            close();
        } else {
            ShowError("Error parameters", "You need to write all parameters for your condition.");
        }
    }

    public void createWindow(final CellFormater format, final Stage stage) {
        this.format = format;
        this.stage = stage;
    }

    private void close() {stage.close();}

}

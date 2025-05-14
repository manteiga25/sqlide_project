package com.example.sqlide;

import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;

import java.util.ArrayList;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class AdvancedSearchController {

    @FXML
    HBox container;

    private CheckComboBox<String> checkComboBox;

    private ArrayList<String> columns;
    private Stage stage;
    private TableInterface context;

    @FXML
    private void initialize() {
        checkComboBox = new CheckComboBox<>();
        container.getChildren().add(checkComboBox);
    }

    public void initWin(final ArrayList<String> columns, final Stage stage, final TableInterface context) {
        this.columns = columns;
        this.stage = stage;
        this.context = context;
        checkComboBox.getItems().addAll(columns);
    }

    @FXML
    private void fetchData() {

        final ArrayList<String> selectedColumns = new ArrayList<>(checkComboBox.getCheckModel().getCheckedItems());

        if (selectedColumns.isEmpty()) {
            ShowInformation("Incomplete", "You need to select column to fetch.");
            return;
        }

        if (!context.fetchDataCallback(selectedColumns)) {
            ShowError("Error", "Failed to fetch data.");
        } else {
            closeWindow();
        }

    }

    private void closeWindow() {
        stage.close();
    }

}

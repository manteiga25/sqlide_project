package com.example.sqlide.AdvancedSearch;

import com.example.sqlide.NewTable;
import com.jfoenix.controls.JFXToggleButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class TableAdvancedSearchController {

    @FXML
    private TabPane TabContainer;

    private boolean ClosedByUser = false;

    private Stage stage;

    private final HashMap<String, String> queryList = new HashMap<>();

    private final HashMap<String, AdvancedSearchController> controllers = new HashMap<>();

    public boolean isClosedByUser() {
        return ClosedByUser;
    }

    public void setStage(final Stage stage) {
        this.stage = stage;
    }

    public HashMap<String, String> getQueryList() {
        return queryList;
    }

    public void setTables(final HashMap<String, ArrayList<String>> data) {
        for (final String table : data.keySet()) {
            try {
                final Tab TableTab = new Tab(table);

                final VBox container = new VBox(5);
                container.setPadding(new Insets(5,0,0,0));

                final JFXToggleButton tableState = new JFXToggleButton();
                tableState.setText("Fetch Table");
                tableState.setPadding(new Insets(0,5,0,0));
                tableState.selectedProperty().set(true);

                // Carrega o arquivo FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
                //    VBox miniWindow = loader.load();
                Parent root = loader.load();

                tableState.selectedProperty().addListener(((_, _, state) -> {
                    root.setDisable(!state);
                }));

                AdvancedSearchController secondaryController = loader.getController();

                secondaryController.setTable(table);
                secondaryController.setCode("SELECT");
                secondaryController.setColumns(data);
                secondaryController.removeBottomContainer();

                container.getChildren().addAll(tableState, root);
                TableTab.setContent(container);
                TabContainer.getTabs().add(TableTab);
                controllers.put(table, secondaryController);
                queryList.put(table, "SELECT * FROM " + table + ";");

            } catch (Exception e) {
                ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void close() {

        queryList.replaceAll((table, _) -> controllers.get(table).getQuery());

        ClosedByUser = true;
        stage.close();
    }

}

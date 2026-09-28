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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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

    /** Rotinas do esquema, repassadas a cada construtor de consulta criado por tabela. */
    private final ArrayList<com.example.sqlide.Metadata.RoutineMetadata> routines = new ArrayList<>();

    public void setRoutines(final java.util.List<com.example.sqlide.Metadata.RoutineMetadata> routines) {
        this.routines.clear();
        if (routines != null) this.routines.addAll(routines);
    }

    /** Dialeto da base de dados, para as aspas e os literais das consultas de cada tabela. */
    private com.example.sqlide.drivers.model.SQLTypes dialect = com.example.sqlide.drivers.model.SQLTypes.SQLITE;

    public void setDialect(final com.example.sqlide.drivers.model.SQLTypes dialect) {
        this.dialect = dialect;
        for (final AdvancedSearchController controller : controllers.values()) controller.setDialect(dialect);
        queryList.replaceAll((table, query) -> controllers.containsKey(table) ? controllers.get(table).getQuery() : query);
    }

    public void setTables(final HashMap<String, ArrayList<String>> data) {
        for (final String table : data.keySet()) {
            createTab(table, data);
        }
    }

    private void createTab(final String table, final HashMap<String, ArrayList<String>> data) {
        try {
            final Tab TableTab = new Tab(table);

            final VBox container = new VBox();
            container.setStyle("-fx-background-color: #2C2C2C;");
            container.setPadding(new Insets(5,0,0,0));

            final JFXToggleButton tableState = new JFXToggleButton();
            tableState.setText("Fetch Table");
            tableState.setTextFill(Color.WHITE);
            tableState.setSize(8);
            tableState.setToggleColor(Color.valueOf("#3574F0"));
            tableState.setPadding(new Insets(0,5,0,0));
            tableState.selectedProperty().set(true);

            // Carrega o arquivo FXML
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
            System.out.println("uhm");
            //    VBox miniWindow = loader.load();
            final Parent root = loader.load();
            VBox.setVgrow(root, Priority.ALWAYS);

            final AdvancedSearchController secondaryController = loader.getController();

            tableState.selectedProperty().addListener(((_, _, state) -> {
                root.setDisable(!state);
                secondaryController.setDisabled(!state);
            }));



            secondaryController.setDialect(dialect);
            secondaryController.setTable(table);
            secondaryController.setCode("SELECT");
            secondaryController.setColumns((HashMap<String, ArrayList<String>>) data.clone());
            secondaryController.setRoutines(routines);
            secondaryController.removeBottomContainer();

            System.out.println("2");

            container.getChildren().addAll(tableState, root);
            TableTab.setContent(container);
            System.out.println("3");
            TabContainer.getTabs().add(TableTab);
            controllers.put(table, secondaryController);
            System.out.println("4");
            queryList.put(table, secondaryController.getQuery());
            System.out.println("5");

        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

 /*   public HashMap<String, ArrayList<String>> getSelected() {
        final HashMap<String, ArrayList<String>> selected = new HashMap<>();
        for (final String controllerName : controllers.keySet()) {
            selected.put(controllerName, controllers.get(controllerName).isDisabled() ? null : controllers.get(controllerName).getSelected());
        }
        return selected;
    } */

    @FXML
    private void close() {

        queryList.replaceAll((table, _) -> controllers.get(table).isDisabled() ? "" : controllers.get(table).getQuery());

        ClosedByUser = true;
        stage.close();
    }

}

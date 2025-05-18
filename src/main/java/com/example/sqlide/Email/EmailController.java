package com.example.sqlide.Email;

import com.example.sqlide.AdvancedSearch.TableAdvancedSearchController;
import com.example.sqlide.EditSetController;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.paint.Color;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class EmailController {

    @FXML
    private JFXToggleButton FetchEmailTool;
    @FXML
    private JFXTextField EmailField, SenderField;
    @FXML
    private ComboBox<String> ColumnEmailBox;
    @FXML
    private HTMLEditor EmailEditor;

    private WebView EmailWeb;

    private HashMap<String, String> QueryList = new HashMap<>();

    private final ObservableList<String> emails = FXCollections.observableArrayList();

    private HashMap<String, ArrayList<String>> TablesAndColumns;

    public void setText(final String content) {
        EmailEditor.setHtmlText(content);
    }

    public void setTablesAndColumns(final HashMap<String, ArrayList<String>> TablesAndColumns) {
        this.TablesAndColumns = TablesAndColumns;
        for (final String table : TablesAndColumns.keySet()) {
            QueryList.put(table, "SELECT * FROM " + table + ";");
        }
    }

    @FXML
    private void initialize() {
        EmailWeb = (WebView) EmailEditor.lookup(".web-view");
        EmailWeb.setPageFill(Color.valueOf("#1E1F22"));
        ToolBar toolbar = (ToolBar) EmailEditor.lookup(".tool-bar");

        final Button dataButton = new Button("Add Data");
        dataButton.setOnAction(_->loadInterface());

        toolbar.getItems().addAll(dataButton, new Separator());

        EmailEditor.setOnKeyReleased(_->{
            System.out.println(EmailEditor.getHtmlText());
        });
    }

    @FXML
    private void addEmail() {
        final String email = SenderField.getText();
        if (email.isEmpty() || emails.contains(email)) {
            ShowInformation("Invalid email", "You need to insert a valid email or new email.");
            SenderField.requestFocus();
            return;
        }
        emails.add(email);
    }

    @FXML
    private void loadEditEmail() {
        if (emails.isEmpty()) {
            SenderField.requestFocus();
            ShowInformation("No Data", "You need to insert emails to edit");
            return;
        }
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/EditSet.fxml"));
            Parent root = loader.load();

            EditSetController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.InitializeController(emails);
            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    private void loadFetchStage() {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedTableSearchStage.fxml"));
            Parent root = loader.load();

            TableAdvancedSearchController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.setStage(subStage);
            secondaryController.setTables(TablesAndColumns);

            subStage.showingProperty().addListener(_->{
                if (secondaryController.isClosedByUser()) {
                    QueryList = secondaryController.getQueryList();
                }
            });

            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    private void setFetchEmail() {
        ColumnEmailBox.setDisable(!FetchEmailTool.isDisable());
    }

    @FXML
    private void loadInterface() {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Email/insertDataStage.fxml"));
            Parent root = loader.load();

            insertDataController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.setData(TablesAndColumns);
            secondaryController.setInterface(this, subStage);
            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void insertData(final String data) {
        final String insertScript =
                "var selection = window.getSelection();" +
                        "var range = selection.getRangeAt(0);" +
                        "var textNode = document.createTextNode('" + data + "');" +
                        "range.insertNode(textNode);";

        EmailWeb.getEngine().executeScript(insertScript);
    }

    @FXML
    private void send() {

    }

    private void optimize() {

    }

}

package com.example.sqlide;

import com.example.sqlide.drivers.MySQL.MySQLDB;
import com.example.sqlide.drivers.PostegreSQL.PostreSQLDB;
import com.example.sqlide.drivers.model.DataBase;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import static com.example.sqlide.drivers.model.DBnames.DBNamesList;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class OpenDatabaseSelectedController {
    @FXML
    TextField UrlFiels, UserNameField, portField, NameField;

    @FXML
    PasswordField passwordField;

    @FXML
    Label DatabaseLabel;

    private Stage window;
    private mainController context;
    private int indexDB;

    public void initialize(final mainController context, final int indexDB, final Stage window) {
        this.context = context;
        this.indexDB = indexDB;
        this.window = window;
        DatabaseLabel.setText(DBNamesList[indexDB]);
    }

    @FXML
    private void connect() {
        DataBase Database = null;
        String URL = UrlFiels.getText();
        final String User = UserNameField.getText();
        final String pass = passwordField.getText();
        final String port = portField.getText();
        final String DBname = NameField.getText();
        if (!port.isEmpty()) {
            URL += ":" + port;
        }
        if (URL == null || User == null || pass == null || DBname == null) {
            ShowError("Invalid arguments", "You need to insert all values for access Database.");
            return;
        }
        switch (indexDB) {
            case 1:
                Database = new MySQLDB();
                break;
            case 2:
                Database = new PostreSQLDB();
                break;
        }
        context.openDB(Database, URL + "/", DBname, User, pass);
            closeWindow();
    }

    @FXML
    public void closeWindow() {
        window.close();
    }

}

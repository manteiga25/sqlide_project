package com.example.sqlide;

import com.example.sqlide.drivers.MySQL.MySQLDB;
import com.example.sqlide.drivers.PostegreSQL.PostreSQLDB;
import com.example.sqlide.drivers.model.DataBase;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.example.sqlide.drivers.model.DBnames.DBNamesList;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class CreateDatabaseSelectedController {

    @FXML
    private JFXTextField TextDBName, UrlField, PortField, userField, ScriptPath;

    @FXML
    private JFXPasswordField PasswordField;

    @FXML
    private RadioButton sharedMode, privateMode, normalMode, ExclusiveMode;

    @FXML
    private Spinner<Integer> cacheSize, Pagesize, mapSpinner;

    @FXML
    private ChoiceBox<String> CharMode, JournalMode, syncBox;

    private DataBase dataBase;

    private mainController ref;

    private Stage stage;

    @FXML
    private void switchRadioSyncNormal() {
        normalMode.setSelected(true);
        ExclusiveMode.setSelected(false);
    }

    @FXML
    private void switchRadioSyncExclusive() {
        normalMode.setSelected(false);
        ExclusiveMode.setSelected(true);
    }

    @FXML
    private void switchRadioShared() {
        sharedMode.setSelected(true);
        privateMode.setSelected(false);
    }

    @FXML
    private void switchRadioPrivate() {
        sharedMode.setSelected(false);
        privateMode.setSelected(true);
    }

    @FXML
    private void openFile() {
        FileChooser selectFileWindow = new FileChooser();
        selectFileWindow.getExtensionFilters().add(new FileChooser.ExtensionFilter("script SQL", "*.sql"));

        final File selectedFile = selectFileWindow.showOpenDialog(stage);
        if (selectedFile != null) {
            ScriptPath.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void initialize() {
        SpinnerValueFactory.IntegerSpinnerValueFactory cacheFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(-2147483648, 2147483647, 10, 4);

        cacheFactory.setValue(-2000);
        cacheSize.setValueFactory(cacheFactory);

        SpinnerValueFactory.IntegerSpinnerValueFactory pageFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 65536, 10, 4);

        pageFactory.setValue(4096);
        Pagesize.setValueFactory(pageFactory);

        SpinnerValueFactory.IntegerSpinnerValueFactory mapFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(-2147483648, 2147483647, 8, 8);

        mapFactory.setValue(268435456);
        mapSpinner.setValueFactory(mapFactory);

    }

    public void initWin(final mainController ref, final DataBase dataBase, final Stage stage) {
            this.ref = ref;
            this.stage = stage;
            this.dataBase = dataBase;
    }

    @FXML
    public void createDB() throws IOException {
        HashMap<String, String> modes = new HashMap<>();

        modes.put("encoding", "'" + CharMode.getValue() + "'");
        modes.put("journal_mode", JournalMode.getValue());
        final String innit = ScriptPath.getText();
        if (!innit.isEmpty()) {
            modes.put("innit", ScriptPath.getText());
        }
        modes.put("cache_spill", sharedMode.isSelected() ? "SHARED" : "PRIVATE");
        modes.put("cache_size", cacheSize.getValue().toString());
        modes.put("page_size", Pagesize.getValue().toString());
        modes.put("mmap_size", mapSpinner.getValue().toString());
        modes.put("locking_mode", normalMode.isSelected() ? "NORMAL" : "EXCLUSIVE");
        modes.put("synchronous", syncBox.getValue());
        modes.put("cache_mode", sharedMode.isSelected() ? "shared" : "private");

      //  dataBase.CreateSchema(UrlField.getText()+PortField.getText(), TextDBName.getText(), userField.getText(), PasswordField.getText());

        ref.createDB(dataBase, UrlField.getText()+":"+PortField.getText(), TextDBName.getText(), userField.getText(), PasswordField.getText(), modes);

        //   modes.put("page_size", Pagesize.getValue().toString());

    }

}

package com.example.sqlide;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SQLiteController {

    @FXML
    private TextField TextDBName, SaveDBPath, ScriptPath;

    @FXML
    private RadioButton sharedMode, privateMode, normalMode, ExclusiveMode;

    @FXML
    private Spinner<Integer> cacheSize, Pagesize, mapSpinner;

    @FXML
    private ChoiceBox<String> CharMode, JournalMode, syncBox;

    @FXML
    private Button Submit;

    mainController ref;

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
    private void selectDir() {
        DirectoryChooser selectFolderWindow = new DirectoryChooser();

        final File selectedDir = selectFolderWindow.showDialog(stage);
        if (selectedDir != null) {
            SaveDBPath.setText(selectedDir.getAbsolutePath());
        }
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
    public void initialize() {
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

    public void initWin(final mainController ref, final Stage stage) {
        try {
            this.ref = ref;
            this.stage = stage;

            Submit.setOnAction(e->{
                try {
                    createDB();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });

            // Carrega o arquivo FXML
        //    FXMLLoader loader = new FXMLLoader(getClass().getResource("DBLiteInterface.fxml"));
            //    VBox miniWindow = loader.load();
          //  Parent root = loader.load();

            // Criar um novo Stage para a subjanela
          /*  Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show(); */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void createDB() throws IOException {
        Map<String, String> modes = new HashMap<>();

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


        ref.createDB(TextDBName.getText(), modes);

        //   modes.put("page_size", Pagesize.getValue().toString());

    }

    }

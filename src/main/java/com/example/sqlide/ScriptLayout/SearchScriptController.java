package com.example.sqlide.ScriptLayout;

import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class SearchScriptController {

    @FXML
    TextField ScriptPath;

    @FXML
    Button search;

    @FXML
    CheckBox TransationBox;

    @FXML
    public void initialize() {
   //     Image img = new Image("C:\\Users\\alexm\\Documents\\assets\\folder.jpg");
     //   ImageView view = new ImageView(img);
       // search.setGraphic(view);
    }

    private DataBase db = null;
    private Stage stage;
    private HashMap<String, DataBase> dbList = null;

    private ProgressController secondaryController = null;

    public void initScriptController(final DataBase db, final Stage stage) {
        this.db = db;
        this.stage = stage;
        try {
            final boolean commit = db.getCommitMode();
            TransationBox.setSelected(!commit);
            TransationBox.setDisable(!commit);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void initScriptController(HashMap<String, DataBase> databaseOpened, Stage subStage) {
        this.dbList = databaseOpened;
        this.stage = subStage;
    }

    @FXML
    private void searchFile() {
        FileChooser selectFileWindow = new FileChooser();
        selectFileWindow.getExtensionFilters().add(new FileChooser.ExtensionFilter("script SQL", "*.sql"));

        final File selectedFile = selectFileWindow.showOpenDialog(stage);
        if (selectedFile != null) {
            ScriptPath.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void execute() {
        if (ScriptPath.getText().isEmpty()) {
            ShowError("Error", "You need to specify a file to read.");
            return;
        }
        try {
            Path file = Paths.get(ScriptPath.getText());
            final long numberOfLines = Files.lines(file).count();
            if (dbList != null) {
                executeAllInstance(file, numberOfLines);
            } else {
                executeThisInstance(file, numberOfLines);
            }
        } catch (IOException _) {

        }
    }

    private void executeAllInstance(final Path file, final long numberOfLines) {
        for (final String key : dbList.keySet()) {
            new Thread(()-> {
                final DataBase cursor = dbList.get(key);
                try {
                    final boolean commitMode = cursor.getCommitMode();
                    cursor.openScript(ScriptPath.getText());
                    if (TransationBox.isSelected() && commitMode) {
                            cursor.changeCommitMode(false);
                    }
                    cursor.executeScript(ScriptPath.getText());
                    Platform.runLater(this::closeWindow);
                    cursor.commit();
                    if (TransationBox.isSelected() && commitMode) {
                        cursor.changeCommitMode(true);
                    }
                } catch (FileNotFoundException e) {
                    Platform.runLater(() -> {
                        if (!TransationBox.isSelected()) {
                            try {
                                if (!cursor.getCommitMode()) {
                                    db.back();
                                    cursor.changeCommitMode(false);
                                }
                            } catch (SQLException ex) {

                            }
                        }
                        ShowError("Error I/O", "Error to open script.\n" + e.getMessage());
                    });
                } catch (SQLException e) {
                    if (!TransationBox.isSelected()) {
                        try {
                            cursor.back();
                            if (!cursor.getCommitMode()) {
                                db.back();
                                cursor.changeCommitMode(false);
                            }
                        } catch (SQLException ex) {

                        }
                    }
                    Platform.runLater(() -> {
                        cursor.back();
                        ShowError("Error script", "Error to execute script.\n" + e.getMessage());
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    private void executeThisInstance(final Path file, final long numberOfLines) {
        new Thread(()-> {
            try {
                //db.executeScript(ScriptPath.getText());
                final boolean commitMode = db.getCommitMode();
                db.openScript(ScriptPath.getText());
                if (TransationBox.isSelected() && commitMode) {
                    db.changeCommitMode(false);
                }
                Platform.runLater(this::closeWindow);
                prepareInit();
                long line = 0;
                while ((line = db.executeNextCommand()) != -1) {
                    final double perc = (double) line / numberOfLines;
                    Platform.runLater(() -> {
                        secondaryController.incrementPerc(perc);
                    });
                }
                db.commit();
                if (TransationBox.isSelected() && commitMode) {
                    db.changeCommitMode(true);
                }
            } catch (FileNotFoundException e) {
                Platform.runLater(() -> {
                    ShowError("Error I/O", "Error to open script.\n" + e.getMessage());
                });
            } catch (SQLException e) {
                if (TransationBox.isSelected()) {
                    System.out.println("entrei");
                    try {
                        if (!db.getCommitMode()) {
                            db.back();
                            System.out.println("volteio");
                            db.changeCommitMode(true);
                        }
                    } catch (SQLException ex) {
                        // nothing
                    }
                }
                Platform.runLater(() -> {
                    ShowError("Error script", "Error to execute script.\n" + e.getMessage());
                    secondaryController.closeWindow();
                });
            } catch (IOException e) {
                if (TransationBox.isSelected()) {
                    try {
                        if (!db.getCommitMode()) {
                            db.back();
                            db.changeCommitMode(true);
                        }
                    } catch (SQLException ex) {
                        // nothing
                    }
                }
                ShowError("Error script", "Error to execute script.\n" + e.getMessage());
            }
        }).start();
    }

    private void prepareInit() {
        Platform.runLater(() -> {
            try {
                initWindowProgress();
            } catch (Exception e) {
                ShowError("Error", "Error to initialize.\n" + e.getMessage());
            }
        });
    }

    private void initWindowProgress() throws IOException {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ProgressLayout.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initProgController(subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
    }

    @FXML
    private void closeWindow() {
        stage.close();
    }

}

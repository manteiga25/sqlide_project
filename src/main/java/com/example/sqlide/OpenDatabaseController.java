package com.example.sqlide;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class OpenDatabaseController {

    @FXML
    private GridPane container;
    private mainController context;

    @FXML
    ImageView sqliteImage;


    private VBox VboxRef = null;
    private int cellRef = -1;
    private Stage stage;

    public void initWin(final mainController ref, final Stage stage) {
        this.context = ref;
        this.stage = stage;
        File file = new File("/images/sqlite.png");
        Image i = new Image(file.toURI().toString());
     //   sqliteImage.setImage(i);
    }

    @FXML
    public void initialize() {
        // Itera pelas células no GridPane
        AtomicInteger index = new AtomicInteger();
        container.getChildren().forEach(node -> {
            if (node instanceof VBox cell) {
                final int i = index.get();

                // Adiciona evento de clique
                cell.setOnMouseClicked(event -> {
                    // Restaura o estilo da célula previamente selecionada
                    if (i == cellRef) {
                        cell.setStyle("");
                        VboxRef = null;
                        cellRef = -1;
                        return;
                    } else if (VboxRef != null) {
                        VboxRef.setStyle("");
                    }

                    // Atualiza a célula selecionada e altera o estilo
                    VboxRef = cell;
                    cellRef = i;
                    System.out.println(cellRef);
                    cell.setStyle("-fx-border-color: blue; -fx-border-width: 2;");
                });
                index.getAndIncrement();
            }
        });
    }

    @FXML
    private void openDBSelected() throws IOException {
        switch (cellRef) {
            case -1:
                ShowError("No selected", "You need to select a database to open.");
                break;
            case 0:
                FileChooser selectFileWindow = new FileChooser();
                selectFileWindow.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Database SQL", "*.db"),
                        new FileChooser.ExtensionFilter("script SQL", "*.sql"));

                final File selectedFile = selectFileWindow.showOpenDialog(stage);
                if (selectedFile != null) {
                    context.openDB(selectedFile.getAbsolutePath(), selectedFile.getName());
                }
                closeWindow();
                break;
            default:
                try {
                    // Carrega o arquivo FXML
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("openDatabaseServer.fxml"));
                    //    VBox miniWindow = loader.load();
                    Parent root = loader.load();

                    OpenDatabaseSelectedController secondaryController = loader.getController();

                    // Criar um novo Stage para a subjanela
                    Stage subStage = new Stage();
                    subStage.setTitle("Subjanela");
                    subStage.setScene(new Scene(root));
                    secondaryController.initialize(context, cellRef, subStage);

                    // Opcional: definir a modalidade da subjanela
                    subStage.initModality(Modality.APPLICATION_MODAL);

                    // Mostrar a subjanela
                    subStage.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                closeWindow();
                break;
        }
    }

    @FXML
    private void closeWindow() {
        stage.close();
    }

}

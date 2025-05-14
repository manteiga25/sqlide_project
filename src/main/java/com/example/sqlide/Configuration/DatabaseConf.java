package com.example.sqlide.Configuration;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class DatabaseConf {

    @FXML
    TreeView<String> TreeViewContainer;

    @FXML
    AnchorPane MenuContainer;

    private String currentMenu = "";

    @FXML
    public void initialize() {

        TreeItem<String> conf = new TreeItem<>("Configuration");

        TreeItem<String> mem = new TreeItem<>("Memory");

        conf.getChildren().add(mem);

        TreeViewContainer.setRoot(conf);

        TreeViewContainer.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                String selected = newItem.getValue();
                switch (selected) {
                    case "Memory":
                        if (!currentMenu.equals("Memory")) {
                            initializeMem();
                            currentMenu = "Memory";
                        }
                        break;
                    default:
                        System.out.println("Nenhuma ação definida para: " + selected);
                }
            }
        });

    }

    public void initializeMem() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("memoryConf.fxml"));
            //    VBox miniWindow = loader.load();
            Node root = loader.load();

            memoryConfController secondaryController = loader.getController();

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

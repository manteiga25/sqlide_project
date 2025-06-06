package com.example.sqlide;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("MainLayout.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), screenSize.getWidth() * 0.9, screenSize.getHeight() * 0.9);
        mainController controller = fxmlLoader.getController();
        controller.setPrimaryStage(stage);
        // stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("SQL IDE");
        stage.getIcons().add(new Image(getClass().getResource("/img/SQLIDE.png").toExternalForm()));
        stage.setScene(scene);
        // Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
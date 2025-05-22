package com.example.sqlide;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.io.IOException;

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
        stage.setScene(scene);
       // Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
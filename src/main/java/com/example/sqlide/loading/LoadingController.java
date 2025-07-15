package com.example.sqlide.loading;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class LoadingController extends HBox {

    public LoadingController(final String title, final String content) {
      //  HBox root = new HBox(30);
        super(30);
        super.setPadding(new Insets(10,10,10,10));
        super.setFillHeight(true);
        super.setStyle("-fx-background-color: #2C2C2C;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        VBox messageBox = new VBox(10);

        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.valueOf("#f2f2f2"));
        titleLabel.setFont(new Font("JetBrains Mono SemiBold", 18));

        Label contentLabel = new Label(content);
        contentLabel.setTextFill(Color.valueOf("#f2f2f2"));
        contentLabel.setFont(new Font("JetBrains Mono SemiBold", 14));

        messageBox.getChildren().addAll(titleLabel, contentLabel);

        super.getChildren().addAll(progress, messageBox);

    }

}

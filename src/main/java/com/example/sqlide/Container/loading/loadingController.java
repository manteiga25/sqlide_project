package com.example.sqlide.Container.loading;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import java.text.Format;

public class loadingController {
    @FXML
    private Label ProgressLabel, ContextLabel;
    @FXML
    private ProgressBar Progress;

    private Stage stage;

    private loadingInterface loadingInterface;

    public void setAttr(DoubleProperty progressRef, final String label, Stage stage, final loadingInterface loadingInterface) {
        this.stage = stage;
        this.loadingInterface = loadingInterface;
        ContextLabel.setText(label);
        progressRef.addListener((_, _, newProgress) -> {
            final double value = newProgress.doubleValue();
            Progress.setProgress(value);
            ProgressLabel.setText((int) value*100 + "%");
        });
    }

    @FXML
    private void cancel() {
        loadingInterface.close();
    }

    public void close() {
        stage.close();
    }

}

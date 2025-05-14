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

    private BooleanProperty TaskState;

    public void setAttr(DoubleProperty progressRef, final String label, Stage stage, final BooleanProperty TaskState) {
        this.stage = stage;
        this.TaskState = TaskState;
        ContextLabel.setText(label);
        progressRef.addListener((observableValue, number, newProgress) -> {
            final double value = newProgress.doubleValue();
            Progress.setProgress(value);
            ProgressLabel.setText(value*100 + "%");
        });
    }

    @FXML
    private void cancel() {
        TaskState.set(false);
    }

    public void close() {
        stage.close();
    }

}

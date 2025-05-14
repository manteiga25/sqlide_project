package com.example.sqlide.ScriptLayout;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public class ProgressController {
    @FXML
    ProgressBar ProgressScript;

    @FXML
    Label Percentage;

    Stage stage;

    public void incrementPerc(final double perc) {
        ProgressScript.setProgress(perc);
        Percentage.setText((perc * 100) + "%");
    }

    public void initProgController(final Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void closeWindow() {
        stage.close();
    }

}

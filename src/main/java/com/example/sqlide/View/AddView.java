package com.example.sqlide.View;

import com.jfoenix.controls.JFXTextField;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.stage.Stage;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class AddView {

    @FXML
    private JFXTextField ViewField;

    private Stage stage;

    private ObservableList<ViewController.View> views;

    public void setStage(final Stage stage) {
        this.stage = stage;
    }

    public void setList(final ObservableList<ViewController.View> views) {
        this.views = views;
    }

    @FXML
    private void add() {
        final String name = ViewField.getText();
        if (!name.isEmpty() && views.stream().noneMatch(view -> view.Name.get().equals(name))) {
            views.add(new ViewController.View(name, "", ""));
            stage.close();
        } else ShowInformation("Null", "You need to insert a valid name.");
    }

    @FXML
    private void close() {
        stage.close();
    }

}

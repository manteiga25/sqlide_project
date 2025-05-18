package com.example.sqlide;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;

import java.util.List;
import java.util.Objects;

public class EditSetController {

    @FXML
    private JFXTextField SearchField;
    @FXML
    private JFXListView<String> ListView;

    private ObservableList<String> items, filter;

    @FXML
    private void initialize() {
        SearchField.textProperty().addListener((_, _, text)->{
            System.out.println(items.filtered(item->item.contains(text)));
            filter.clear();
            filter.addAll(items.filtered(item->item.contains(text)));
        });
        ListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListView.setCellFactory(TextFieldListCell.forListView());
        ListView.setOnEditStart(_ -> {
            ListView.setUserData(ListView.getSelectionModel().getSelectedItems().getFirst());
        });
        ListView.setOnEditCommit(e->{
            final String old = ListView.getUserData().toString();
            items.set(items.indexOf(old), e.getNewValue());
            filter.set(filter.indexOf(old), e.getNewValue());
        });
    }

    public void InitializeController(final ObservableList<String> list) {
        this.items = list;
        filter = FXCollections.observableArrayList(items);
        ListView.setItems(filter);
    }

    @FXML
    private void deleteData() {
        final ObservableList<String> seted = ListView.getSelectionModel().getSelectedItems();
        items.removeAll(seted);
        ListView.getItems().removeAll(seted);
    }

}

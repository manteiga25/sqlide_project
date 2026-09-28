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
            filter.clear();
            filter.addAll(items.filtered(item->item.contains(text)));
        });
        ListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListView.setCellFactory(TextFieldListCell.forListView());
        // O valor antigo vem da linha editada; antes vinha do primeiro item selecionado e,
        // com vários selecionados, era substituído o valor errado.
        ListView.setOnEditCommit(e->{
            final String old = filter.get(e.getIndex());
            final String value = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (value.isEmpty() || (!value.equals(old) && items.contains(value))) return;
            final int position = items.indexOf(old);
            if (position >= 0) items.set(position, value);
            filter.set(e.getIndex(), value);
        });
    }

    public void InitializeController(final ObservableList<String> list) {
        this.items = list;
        filter = FXCollections.observableArrayList(items);
        ListView.setItems(filter);
    }

    @FXML
    private void deleteData() {
        // Cópia: a seleção muda enquanto se tiram os itens.
        final List<String> seted = List.copyOf(ListView.getSelectionModel().getSelectedItems());
        items.removeAll(seted);
        ListView.getItems().removeAll(seted);
    }

}

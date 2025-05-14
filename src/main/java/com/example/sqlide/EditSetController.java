package com.example.sqlide;

import com.jfoenix.controls.JFXListView;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;

import java.util.Objects;

public class EditSetController {

    @FXML
    private JFXListView<String> ListView;

    public void InitializeController(final ObservableList<String> list) {
        ListView.setItems(list);
        ListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ListView.setCellFactory(TextFieldListCell.forListView());
        ListView.setOnEditCommit(e->list.set(e.getIndex(), e.getNewValue()));
    }

    @FXML
    private void deleteData() {
        ListView.getItems().removeAll(ListView.getSelectionModel().getSelectedItems());
    }

    private class TextFieldListCellList extends ListCell<String> {
        private final TextField textField = new TextField();

        @Override
        public void startEdit() {
            textField.setEditable(true);
            super.startEdit();
            if (getItem() != null) {
                textField.setText(getItem());
                setGraphic(textField);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                textField.selectAll();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setContentDisplay(ContentDisplay.TEXT_ONLY);
        }

        @Override
        public void commitEdit(String s) {
            super.commitEdit(s);
            setText(s);
         //   list[getIndex()] = s;
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    textField.setText(item);
                    setGraphic(textField);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                } else {

                    setText(item);
                    setContentDisplay(ContentDisplay.TEXT_ONLY);
                }
            }
        }
    }

}

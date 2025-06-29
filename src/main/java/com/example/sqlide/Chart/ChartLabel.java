package com.example.sqlide.Chart;

import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import org.apache.xmlbeans.impl.soap.Text;

import java.util.ArrayList;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class ChartLabel {

    @FXML
    private TextField NameField, CategoryField;
    @FXML
    private ChoiceBox<String> FuncBox, ColumnBox;

 //   private ObservableMap<String, String> list = null;
    private ObservableList<ChartController.Label> list = null;

   // private String key = null;

    private int key;

    private boolean mode;

  /*  public void setList(final ObservableMap<String, String> list) {
        mode = false;
        this.list = list;
    } */

  /*  public void setEditList(final ObservableMap<String, String> list, final String key) {
        mode = true;
        this.list = list;
        this.key = key;
        NameField.setText(key);
    } */

    public void setList(final ObservableList<ChartController.Label> list, final ArrayList<String> columns) {
        mode = false;
        this.list = list;
        ColumnBox.getItems().addAll(columns);
        ColumnBox.setValue(columns.getFirst());
    }

      public void setEditList(final ObservableList<ChartController.Label> list, final int key, final ArrayList<String> columns) {
        mode = true;
        this.list = list;
        this.key = key;
        NameField.setText(list.get(key).Name.get());
        CategoryField.setText(list.get(key).Category.get());
        ColumnBox.setValue(list.get(key).Column.get());
        FuncBox.setValue(list.get(key).Func.get());
        ColumnBox.getItems().addAll(columns);
    }

    @FXML
    private void initialize() {
        FuncBox.getItems().addAll("SUM", "AVG", "COUNT", "MIN", "MAX");
        FuncBox.setValue("SUM");
    }

  /*  @FXML
    private void confirm() {
        if (!NameField.getText().isEmpty()) {
            if (mode) list.remove(key);
            list.put(NameField.getText(), FuncBox.getValue());
        }
    } */
  @FXML
  private void confirm() {
      if (!NameField.getText().isEmpty() && !CategoryField.getText().isEmpty()) {
          if (!mode) {
              if (list.stream().noneMatch(p -> p.Category.get().equals(CategoryField.getText()))) {
                  list.add(new ChartController.Label(NameField.getText(), CategoryField.getText(), FuncBox.getValue(), ColumnBox.getValue(), "SELECT " + FuncBox.getValue() + "(" + ColumnBox.getValue() + ")"));
              } else {
                  CategoryField.requestFocus();
                  ShowInformation("Exists", "The category " + CategoryField.getText() + " already exists.");
              }
              } else {
              ChartController.Label label = list.get(key);
              label.Name.set(NameField.getText());
              label.Category.set(CategoryField.getText());
              label.Query.set(label.Query.get().replace(label.Func.get(), FuncBox.getValue()));
              label.Func.set(FuncBox.getValue());
          }
          } else ShowInformation("No data", "You need to insert data.");
  }

}

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
        // getFirst() numa lista vazia lanca NoSuchElementException e a janela nem abria.
        if (!columns.isEmpty()) ColumnBox.setValue(columns.getFirst());
    }

      public void setEditList(final ObservableList<ChartController.Label> list, final int key, final ArrayList<String> columns) {
        mode = true;
        this.list = list;
        this.key = key;
        NameField.setText(list.get(key).Name.get());
        CategoryField.setText(list.get(key).Category.get());
        // Os itens tem de entrar antes do setValue: a ordem inversa deixava a caixa a
        // mostrar um valor que ainda nao pertencia a lista.
        ColumnBox.getItems().addAll(columns);
        ColumnBox.setValue(list.get(key).Column.get());
        FuncBox.setValue(list.get(key).Func.get());
    }

    @FXML
    private void initialize() {
        // NONE traz a coluna em bruto. Obrigar sempre a um agregado reduz a serie a um
        // ponto por categoria, o que nao serve a um grafico de linhas.
        FuncBox.getItems().addAll(ChartController.Label.NO_FUNCTION, "SUM", "AVG", "COUNT", "MIN", "MAX");
        FuncBox.setValue("SUM");
    }

    /** Monta o SELECT da etiqueta, com ou sem agregado. */
    private String buildQuery(final String function, final String column) {
        final boolean aggregate = function != null && !function.isBlank()
                && !ChartController.Label.NO_FUNCTION.equalsIgnoreCase(function);
        return "SELECT " + (aggregate ? function + "(" + column + ")" : column);
    }

    /**
     * Verifica os campos e devolve o que esta em falta, ou null se estiver tudo bem.
     *
     * @param ignoreIndex indice a ignorar na verificacao de categoria repetida, ao editar
     */
    private String validate(final int ignoreIndex) {
        final String name = NameField.getText();
        final String category = CategoryField.getText();

        if (name == null || name.isBlank()) return "Give the series a name.";
        if (category == null || category.isBlank()) return "Give the point a category.";
        if (ColumnBox.getValue() == null || ColumnBox.getValue().isBlank()) return "Choose a column.";
        if (FuncBox.getValue() == null) return "Choose a function, or NONE for the raw column.";

        // A categoria e o eixo X de um ponto: repetida, o segundo ponto tapa o primeiro.
        // Ao criar ja era verificado; ao editar nao era, e dava para duplicar.
        for (int index = 0; index < list.size(); index++) {
            if (index == ignoreIndex) continue;
            final ChartController.Label other = list.get(index);
            if (other.Category.get().equals(category.trim()) && other.Name.get().equals(name.trim())) {
                return "The series " + name.trim() + " already has a point in category " + category.trim() + ".";
            }
        }

        return null;
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
      final String problem = validate(mode ? key : -1);
      if (problem != null) {
          ShowInformation("Check the fields", problem);
          return;
      }

      final String name = NameField.getText().trim();
      final String category = CategoryField.getText().trim();
      final String function = FuncBox.getValue();
      final String column = ColumnBox.getValue();

      if (!mode) {
          list.add(new ChartController.Label(name, category, function, column,
                  buildQuery(function, column)));
      } else {
          ChartController.Label label = list.get(key);
          label.Name.set(name);
          label.Category.set(category);
          // A coluna nunca era actualizada ao editar, e a query era corrigida por um
          // replace do nome da funcao dentro dela: numa coluna chamada "SUMMARY" o
          // replace de "SUM" partia o proprio nome da coluna. Monta-se de novo.
          label.Column.set(column);
          label.Func.set(function);
          label.Query.set(buildQuery(function, column));
      }
  }

}

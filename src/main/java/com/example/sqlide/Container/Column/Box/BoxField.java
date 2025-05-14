package com.example.sqlide.Container.Column.Circle;

import com.example.sqlide.Container.Geometry.Box.BoxGeometry;
import com.example.sqlide.Container.Geometry.Box.BoxWindowController;
import com.example.sqlide.Container.Geometry.Circle.CircleGeometry;
import com.example.sqlide.Container.Geometry.Circle.CircleWindowController;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BoxField extends HBox {

    private final TextField textField = new TextField();
    private final Button button = new Button("Edit");
    private final BoxGeometry box = new BoxGeometry(0,0,0,0);

    //   private String value = "";

    private final StringProperty value = new SimpleStringProperty(this, "", "");

    public final String getValue() {
        return value.get();
    }

    public StringProperty valueProperty() {
        return value;
    }


    // private final Pattern PAREN_PATTERN = Pattern.compile( "^\(\(\-?\d+(\.\d+)?\s*,\s*\-?\d+(\.\d+)?\),\s*\-?\d+(\.\d+)?\)$" );


    public BoxField(){
        super();
        initializeButton();
        this.getChildren().addAll(textField, button);
    }

    private boolean checkValue(final String value) {
        try {
            String semParenteses = value.replace("(", "").replace(")", "").replace("<", "").replace(">", "").replace(" ", "").replace("'", "");
            System.out.println(semParenteses);
            String[] partes = semParenteses.split(",");
            for (String parte : partes) {
                System.out.println(parte);
            }
            if (partes.length != 4) {
                return false;
            } else {
                box.X1 = Double.parseDouble(partes[0]);
                box.Y1 = Double.parseDouble(partes[1]);
                box.X2 = Double.parseDouble(partes[2]);
                box.Y2 = Double.parseDouble(partes[3]);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeButton() {
        button.setOnAction(e -> {
            openEngine();
        });
        textField.setOnAction(e-> {
            if (checkValue(textField.getText())) {
                value.set(textField.getText());
            }
        });
        textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                value.set(textField.getText());
            }
        });
    }

    public void setValue(String value) {
        //  String ValueWithoutSpace = value.replaceAll(" ","");
        // Matcher matcher = PAREN_PATTERN.matcher(value);
        if (value == null || value.isEmpty()) {
            return;
        }
        if (checkValue(value)) {
            textField.setText(value);
            this.value.set(value);
        }
        if (true) {
     /*       textField.setText(this.value);
            int val1Index = value.indexOf(",");
            int val2Index = value.indexOf(",", val1Index+1);
            int val3Index = value.indexOf(")");
            circle.X = Double.parseDouble(value.substring(1, val1Index-1));
            circle.Y = Double.parseDouble(value.substring(val2Index+1, value.indexOf(")")-1));
            circle.Radius = Double.parseDouble(value.substring(val3Index+1, value.length()-1)); */
        }
    }

    private void openEngine() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/GeometryContainer/BoxWindow.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            BoxWindowController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            subStage.setResizable(false);
            subStage.setOnHidden(e-> {
                textField.setText(box.toString());
                value.set(box.toString());
            });
//            secondaryController.initEventWindow(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()));
            secondaryController.initWin(box);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
            //  textField.setText();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

package com.example.sqlide.Container.Geometry.Box;

import com.example.sqlide.Container.Geometry.Circle.CircleGeometry;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

public class BoxWindowController {

    @FXML
    Rectangle box;

    @FXML
    TextField X1Text, Y1Text, X2Text, Y2Text;

    @FXML
    ColorPicker colorPicker;

    BoxGeometry boxGeometry;

    public void initWin(BoxGeometry boxGeometry) {
        this.boxGeometry = boxGeometry;
        box.setArcHeight(Math.abs(boxGeometry.Y1 - boxGeometry.Y2));
        box.setArcWidth(Math.abs(boxGeometry.X1 - boxGeometry.X2));
        box.setHeight(boxGeometry.Y2);
        box.setWidth(boxGeometry.X2);
        colorPicker.valueProperty().addListener(new ChangeListener<Color>() {
            @Override public void changed(ObservableValue<? extends Color> obs, Color oldColor, Color newColor) {
                if (newColor != oldColor) {
                    defineColor();
                }
            }
        });
        X1Text.setText((boxGeometry.X1 + ""));
        Y1Text.setText((boxGeometry.Y1 + ""));
        X2Text.setText((boxGeometry.X2 + ""));
        Y2Text.setText((boxGeometry.Y2 + ""));
        colorPicker.setValue(Color.RED);
    }

    @FXML
    private void defineX1() {
        try {
            double x1 = Double.parseDouble(X1Text.getText());
            boxGeometry.X1 = x1;
            box.setArcWidth(Math.abs(boxGeometry.X1 - boxGeometry.X2));
        } catch (NumberFormatException e) {
            X1Text.setText((boxGeometry.X1 + ""));
        }
    }

    @FXML
    private void defineX2() {
        try {
            double x2 = Double.parseDouble(X2Text.getText());
            boxGeometry.X2 = x2;
            box.setWidth(x2);
        } catch (NumberFormatException e) {
            X2Text.setText((boxGeometry.X2 + ""));
        }
    }

    @FXML
    private void defineY1() {
        try {
            double y1 = Double.parseDouble(Y1Text.getText());
            boxGeometry.Y1 = y1;
            box.setArcWidth(Math.abs(boxGeometry.Y1 - boxGeometry.Y2));
        } catch (NumberFormatException e) {
            Y1Text.setText((boxGeometry.Y1 + ""));
        }
    }

    @FXML
    private void defineY2() {
        try {
            double y2 = Double.parseDouble(Y2Text.getText());
            boxGeometry.Y2 = y2;
            box.setHeight(y2);
        } catch (NumberFormatException e) {
            Y1Text.setText((boxGeometry.Y2 + ""));
        }
    }

    private void defineColor() {
        box.setFill(colorPicker.getValue());
    }

}

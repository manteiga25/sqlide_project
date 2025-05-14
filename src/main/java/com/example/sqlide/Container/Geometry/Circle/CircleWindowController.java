package com.example.sqlide.Container.Geometry.Circle;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;

import javafx.scene.paint.Color;

public class CircleWindowController {

    @FXML
    Circle circle;

    @FXML
    TextField radText, yText, xText;

    @FXML
    ColorPicker colorPicker;

    CircleGeometry circleGeometry;

    public void initWin(CircleGeometry circleGeometry) {
        this.circleGeometry = circleGeometry;
        circle.setRadius(circleGeometry.Radius);
        circle.setCenterX(circleGeometry.X);
        circle.setCenterY(circleGeometry.Y);
        colorPicker.valueProperty().addListener(new ChangeListener<Color>() {
            @Override public void changed(ObservableValue<? extends Color> obs, Color oldColor, Color newColor) {
                if (newColor != oldColor) {
                    defineColor();
                }
            }
        });
        radText.setText((circleGeometry.Radius + ""));
        xText.setText((circleGeometry.X + ""));
        yText.setText((circleGeometry.Y + ""));
        colorPicker.setValue(Color.RED);
    }

    @FXML
    private void defineRadius() {
        try {
            double rad = Double.parseDouble(radText.getText());
            circle.setRadius(rad);
            circleGeometry.Radius = rad;
        } catch (NumberFormatException e) {
            radText.setText((circle.getRadius() + ""));
        }
    }

    @FXML
    private void defineX() {
        try {
            double x = Double.parseDouble(xText.getText());
            circle.setCenterX(x);
            circleGeometry.X = x;
        } catch (NumberFormatException e) {
            xText.setText((circle.getCenterX() + ""));
        }
    }

    @FXML
    private void defineY() {
        try {
            double y = Double.parseDouble(yText.getText());
            circle.setCenterY(y);
            circleGeometry.Y = y;
        } catch (NumberFormatException e) {
            yText.setText((circle.getCenterY() + ""));
        }
    }

    private void defineColor() {
        circle.setFill(colorPicker.getValue());
    }

}

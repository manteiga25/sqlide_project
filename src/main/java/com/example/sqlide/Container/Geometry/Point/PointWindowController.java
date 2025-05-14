package com.example.sqlide.Container.Geometry.Point;

import com.example.sqlide.Container.Geometry.Box.BoxGeometry;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

public class PointWindowController {
    @FXML
    Circle point;

    @FXML
    TextField XText, YText;

    @FXML
    ColorPicker colorPicker;

    PointGeometry pointGeometry;

    public void initWin(PointGeometry pointGeometry) {
        this.pointGeometry = pointGeometry;
        point.setCenterX(pointGeometry.X);
        point.setCenterY(pointGeometry.Y);
        colorPicker.valueProperty().addListener(new ChangeListener<Color>() {
            @Override public void changed(ObservableValue<? extends Color> obs, Color oldColor, Color newColor) {
                if (newColor != oldColor) {
                    defineColor();
                }
            }
        });
        XText.setText((pointGeometry.X + ""));
        YText.setText((pointGeometry.Y + ""));
        colorPicker.setValue(Color.RED);
    }

    @FXML
    private void defineX() {
        try {
            double x = Double.parseDouble(XText.getText());
            pointGeometry.X = x;
            point.setCenterX(x);
        } catch (NumberFormatException e) {
            XText.setText((pointGeometry.X + ""));
        }
    }

    @FXML
    private void defineY() {
        try {
            double y = Double.parseDouble(YText.getText());
            pointGeometry.Y = y;
            point.setCenterY(y);
        } catch (NumberFormatException e) {
            YText.setText((pointGeometry.Y + ""));
        }
    }

    private void defineColor() {
        point.setFill(colorPicker.getValue());
    }

}

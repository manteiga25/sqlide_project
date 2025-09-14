package com.example.sqlide.Container.LongField;

import com.jfoenix.controls.JFXTextField;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.TextFieldSkin;

import java.util.function.UnaryOperator;

public class LongField extends JFXTextField {

    private final LongProperty number = new SimpleLongProperty();

    public ReadOnlyLongProperty readOnlyNumberProperty() {
        return number;
    }

    public ReadOnlyLongProperty numberProperty() {
        return number;
    }

    public long getNumber() {
        return number.get();
    }

    public void setNumber(long value) {
        textProperty().set(String.valueOf(value));
    }

    public LongField() {
        super();

        UnaryOperator<TextFormatter.Change> filter = change -> {
            String newText = change.getControlNewText();
            return newText.matches("-?\\d*") ? change : null;
        };
        setTextFormatter(new TextFormatter<>(filter));

        textProperty().addListener((obs, oldText, newText) -> {
            try {
                if (newText == null || newText.isEmpty() || newText.equals("-")) {
                    number.set(0L);
                } else {
                    number.set(Long.parseLong(newText));
                }
            } catch (NumberFormatException e) {
                setText(oldText);
            }
        });


    }

}

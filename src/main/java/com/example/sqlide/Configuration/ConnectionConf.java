package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.Enum.TempStore;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.ConnectionPragmaInterface;
import com.example.sqlide.misc.ThrowingConsumer;
import com.example.sqlide.misc.ThrowingRunnable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;

import java.util.HashMap;

public class ConnectionConf implements LoadSettings {

    public Spinner<Integer> timeOutSpinner;
    public GridPane Container;
    private ConnectionPragmaInterface connection = null;

    private final HashMap<String, Object> values_map = new HashMap<>(3), new_values_map = new HashMap<>(3);
    private final HashMap<String, ThrowingRunnable> routines_map = new HashMap<>(3);
    HashMap<String, ThrowingConsumer<Object, Exception>> routines_set = new HashMap<>(3);

    public void setConnection(ConnectionPragmaInterface connectionPragmaInterface) {
        connection = connectionPragmaInterface;
        initialize_routines_map();
        initialize_configuration();
    }

    @FXML
    private void initialize() {
        timeOutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE));
    }

    @Override
    public void initialize_configuration() {
        Thread.ofVirtual().start(()->{

            for (Node node : Container.getChildren()) {
                if (node.getId() == null || node.getId().isEmpty()) continue;
                try {
                    values_map.put(node.getId(), routines_map.get(node.getId()).run());
                    node.setDisable(false);
                } catch (Exception e) {
                    e.printStackTrace();
                    node.setDisable(true);
                }

            }

            timeOutSpinner.getValueFactory().setValue((Integer) values_map.get(timeOutSpinner.getId()));
            new_values_map.put(timeOutSpinner.getId(), timeOutSpinner.getValueFactory());

        });
    }

    private void initialize_routines_map() {
        routines_map.put(timeOutSpinner.getId(), connection::getTimeout);

        routines_set.put(timeOutSpinner.getId(), v -> connection.setTimeout((Integer) v));

    }

    @Override
    public void save_settings() {
        for (String key : values_map.keySet()) {
            Object oldVal = values_map.get(key), newVal = null;

            if (new_values_map.get(key) instanceof ObjectProperty<?> objectProperty) {
                newVal = objectProperty.get();
            } else if (new_values_map.get(key) instanceof BooleanProperty booleanProperty) {
                newVal = booleanProperty.asObject().get();
            }

            if (newVal != null && !oldVal.equals(newVal)) {
                try {
                    routines_set.get(key).accept(newVal);
                    values_map.put(key, newVal);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }
}

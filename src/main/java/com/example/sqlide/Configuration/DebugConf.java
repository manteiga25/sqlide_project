package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Enum.Lock;
import com.example.sqlide.drivers.model.Enum.Synchronization;
import com.example.sqlide.drivers.model.Enum.TempStore;
import com.example.sqlide.drivers.model.Enum.VACUUM;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.DebugPragmaInterface;
import com.example.sqlide.misc.ThrowingConsumer;
import com.example.sqlide.misc.ThrowingRunnable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Objects;

public class DebugConf implements LoadSettings {
    public CheckBox parseCheck;
    public ChoiceBox<TempStore> tempBox;
    public TextField directoryField;



    private final HashMap<String, Object> values_map = new HashMap<>(3), new_values_map = new HashMap<>(3);
    private final HashMap<String, ThrowingRunnable> routines_map = new HashMap<>(3);
    HashMap<String, ThrowingConsumer<Object, Exception>> routines_set = new HashMap<>(3);
    public GridPane Container;

    private DebugPragmaInterface debug = null;

    public void setDebug(DebugPragmaInterface debug) {
        this.debug = debug;
        initialize_routines_map();
        initialize_configuration();
    }

    @FXML
    private void initialize() {
        tempBox.getItems().addAll(TempStore.values());
    }

    private void initialize_routines_map() {
            routines_map.put(parseCheck.getId(), debug::getParserTrace);
            routines_map.put(tempBox.getId(), debug::getTempMode);
            routines_map.put(directoryField.getId(), debug::getDirectory);

        routines_set.put(parseCheck.getId(), v -> debug.setParserTrace((Boolean) v));
        routines_set.put(tempBox.getId(), v -> debug.setTempMode((TempStore) v));
        routines_set.put(directoryField.getId(), v -> debug.setDirectory(String.valueOf(v)));

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

                parseCheck.setSelected((Boolean) values_map.get(parseCheck.getId()));
                new_values_map.put(parseCheck.getId(), parseCheck.selectedProperty());

                tempBox.getSelectionModel().select((TempStore) values_map.get(tempBox.getId()));
                new_values_map.put(tempBox.getId(), tempBox.valueProperty());

                directoryField.setText((String) values_map.get(directoryField.getId()));
                new_values_map.put(directoryField.getId(), directoryField.textProperty());

        });
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

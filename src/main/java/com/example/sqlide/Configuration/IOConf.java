package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.IOPragma_Interface;
import com.example.sqlide.misc.ThrowingConsumer;
import com.example.sqlide.misc.ThrowingRunnable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;

import java.util.HashMap;

public class IOConf implements LoadSettings {
    public CheckBox fullCheck;
    public CheckBox cellCheck;
    public CheckBox syncCheck;
    public CheckBox deleteCheck;

    private IOPragma_Interface io = null;

    private final HashMap<String, Object> values_map = new HashMap<>(4), new_values_map = new HashMap<>(4);
    private final HashMap<String, ThrowingRunnable> routines_map = new HashMap<>(4);
    HashMap<String, ThrowingConsumer<Object, Exception>> routines_set = new HashMap<>(4);
    public GridPane Container;

    public void setIo(IOPragma_Interface io) {
        this.io = io;
        initialize_routines_map();
        initialize_configuration();
    }

    private void initialize_routines_map() {
        routines_map.put(fullCheck.getId(), io::getFullSync);
        routines_map.put(cellCheck.getId(), io::getCellSize);
        routines_map.put(syncCheck.getId(), io::getFullSyncCheckpoint);
        routines_map.put(deleteCheck.getId(), io::getDelete);

        routines_set.put(fullCheck.getId(), v -> io.setFullSync((Boolean) v));
        routines_set.put(cellCheck.getId(), v -> io.setCellSize((Boolean) v));
        routines_set.put(syncCheck.getId(), v -> io.setFullSyncCheckpoint((Boolean) v));
        routines_set.put(deleteCheck.getId(), v -> io.setDelete((Boolean) v));
    }

    @Override
    public void initialize_configuration() {
        Thread.ofVirtual().start(()->{
            try {

                for (Node node : Container.getChildren()) {
                    if (node.getId() == null || node.getId().isEmpty()) continue;
                    try {
                        serialize(node);
                        node.setDisable(false);
                    } catch (Exception e) {
                        e.printStackTrace();
                        node.setDisable(true);
                    }

                }

            /*    fullCheck.setSelected((Boolean) values_map.get(fullCheck.getId()));
                new_values_map.put(fullCheck.getId(), fullCheck.selectedProperty());

                cellCheck.setSelected((Boolean) values_map.get(cellCheck.getId()));
                new_values_map.put(cellCheck.getId(), cellCheck.selectedProperty());

                syncCheck.setSelected((Boolean) values_map.get(syncCheck.getId()));
                new_values_map.put(syncCheck.getId(), syncCheck.selectedProperty());

                deleteCheck.setSelected((Boolean) values_map.get(deleteCheck.getId()));
                new_values_map.put(deleteCheck.getId(), deleteCheck.selectedProperty()); */
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    private void serialize(Node node) throws Exception {
        if (node instanceof CheckBox checkBox) {
            values_map.put(checkBox.getId(), routines_map.get(checkBox.getId()).run());
            checkBox.setSelected((Boolean) values_map.get(checkBox.getId()));
            new_values_map.put(checkBox.getId(), checkBox.selectedProperty());
        } else if (node instanceof Spinner<?> spinner) {
            Spinner<Integer> spinner1 = (Spinner<Integer>) spinner;
            values_map.put(spinner.getId(), routines_map.get(spinner.getId()).run());
            spinner1.getValueFactory().setValue((Integer) values_map.get(spinner.getId()));
            new_values_map.put(spinner.getId(), spinner.getValueFactory());
        } else if (node instanceof ChoiceBox<?> choiceBox) {
         /*   values_map.put(choiceBox.getId(), routines_map.get(choiceBox.getId()).run());
            choiceBox.getSelectionModel().select((Object) values_map.get(choiceBox.getId()));
            new_values_map.put(choiceBox.getId(), choiceBox.getSelectionModel()); */
        }
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

package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.MemoryPragmaInterface;
import com.example.sqlide.misc.ThrowingConsumer;
import com.example.sqlide.misc.ThrowingRunnable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

public class memoryConfController implements LoadSettings {

    public CheckBox spillCheck;
    public Spinner<Integer> hardSpinner;
    public Spinner<Integer> maxPagesSpinner;
    public Label totalPagesLbl;
    public Spinner<Integer> softSpinner;
    public GridPane Container;
    @FXML
    private TextField cacheSizeField;

    @FXML
    private TextField pageSizeField;

    @FXML
    private TextField mmapSizeField;

    private MemoryPragmaInterface memory;

    private final HashMap<String, Object> values_map = new HashMap<>(8), new_values_map = new HashMap<>(8);
    private final HashMap<String, ThrowingRunnable> routines_map = new HashMap<>(8);
    HashMap<String, ThrowingConsumer<Object, Exception>> routines_set = new HashMap<>(8);

    public void setDatabase(MemoryPragmaInterface memory) {
        this.memory = memory;
        initialize_routines_map();
        initialize_configuration();
    }

    @FXML
    private void initialize() {
        hardSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE));
        softSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE));
        maxPagesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE));
    }

    private void initialize_routines_map() {
        routines_map.put(spillCheck.getId(), memory::getCacheSpill);
        routines_map.put(totalPagesLbl.getId(), memory::getTotalPages);
        routines_map.put(hardSpinner.getId(), memory::getHardHeapSize);
        routines_map.put(softSpinner.getId(), memory::getSoftHeapSize);
        routines_map.put(maxPagesSpinner.getId(), memory::getMaxPages);
        routines_map.put(cacheSizeField.getId(), memory::getCacheSize);
        routines_map.put(pageSizeField.getId(), memory::getPageSize);
        routines_map.put(mmapSizeField.getId(), memory::getMMapSize);

        routines_set.put(spillCheck.getId(), v -> memory.setCacheSpill((Boolean) v));
        routines_set.put(hardSpinner.getId(), v -> memory.setHardHeapSize((Integer) v));
        routines_set.put(softSpinner.getId(), v -> memory.setSoftHeapSize((Integer) v));
        routines_set.put(maxPagesSpinner.getId(), v -> memory.setMaxPages((Integer) v));
        routines_set.put(cacheSizeField.getId(), v -> memory.setCacheSize((Integer) v));
        routines_set.put(pageSizeField.getId(), v -> memory.setPageSize((Integer) v));
        routines_set.put(mmapSizeField.getId(), v -> memory.setMMapSize((Integer) v));

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
                    // Platform.runLater(() -> WidgetsState(false));
                }

                spillCheck.setSelected((Boolean) values_map.get(spillCheck.getId()));
                new_values_map.put(spillCheck.getId(), spillCheck.selectedProperty());

                totalPagesLbl.setText(String.valueOf(values_map.get(totalPagesLbl.getId())));

                hardSpinner.getValueFactory().setValue((Integer) values_map.get(hardSpinner.getId()));
                new_values_map.put(hardSpinner.getId(), hardSpinner.getValueFactory());

                softSpinner.getValueFactory().setValue((Integer) values_map.get(softSpinner.getId()));
                new_values_map.put(softSpinner.getId(), softSpinner.getValueFactory());

                maxPagesSpinner.getValueFactory().setValue((Integer) values_map.get(maxPagesSpinner.getId()));
                new_values_map.put(maxPagesSpinner.getId(), maxPagesSpinner.getValueFactory());

                cacheSizeField.setText(String.valueOf(values_map.get(cacheSizeField.getId())));
                new_values_map.put(cacheSizeField.getId(), cacheSizeField.textProperty());

                pageSizeField.setText(String.valueOf(values_map.get(pageSizeField.getId())));
                new_values_map.put(pageSizeField.getId(), pageSizeField.textProperty());

                mmapSizeField.setText(String.valueOf(values_map.get(mmapSizeField.getId())));
                new_values_map.put(mmapSizeField.getId(), mmapSizeField.textProperty());
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

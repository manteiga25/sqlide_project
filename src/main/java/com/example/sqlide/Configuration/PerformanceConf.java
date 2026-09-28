package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.Enum.Lock;
import com.example.sqlide.drivers.model.Enum.Optimize;
import com.example.sqlide.drivers.model.Enum.Synchronization;
import com.example.sqlide.drivers.model.Enum.VACUUM;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.PerformancePragmaInterface;
import com.example.sqlide.misc.ThrowingConsumer;
import com.example.sqlide.misc.ThrowingRunnable;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.docx4j.wml.Tr;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class PerformanceConf implements LoadSettings {
    public ChoiceBox<Synchronization> syncBox;
    public Spinner<Integer> threadSpinner;
    public ChoiceBox<VACUUM> vacuumBox;
    public CheckBox indexCheck;
    public ChoiceBox<Lock> lockBox;
    public ChoiceBox<Optimize> optimizeBox;
    public GridPane Container;


    private PerformancePragmaInterface performance;

    private final HashMap<String, Object> values_map = new HashMap<>(6), new_values_map = new HashMap<>(6);
    private final HashMap<String, ThrowingRunnable> routines_map = new HashMap<>(6);
    HashMap<String, ThrowingConsumer<Object, Exception>> routines_set = new HashMap<>(6);

    public void setPerformance(PerformancePragmaInterface performance) {
        this.performance = performance;
        initialize_routines_map();
        initialize_configuration();
    }

    @FXML
    private void initialize() {
        syncBox.getItems().addAll(Synchronization.values());
        vacuumBox.getItems().addAll(VACUUM.values());
        lockBox.getItems().addAll(Lock.values());
        optimizeBox.getItems().addAll(Optimize.values());
        threadSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1));
    }

    private void WidgetsState(boolean state) {
        syncBox.setDisable(state);
        vacuumBox.setDisable(state);
        lockBox.setDisable(state);
        optimizeBox.setDisable(state);
        threadSpinner.setDisable(state);
        indexCheck.setDisable(state);
    }

    @FXML
    private void optimize() {
        try {
            performance.setOptimizer(optimizeBox.getValue());
        } catch (SQLException e) {
            ShowError("Error optimize", "Error to optimize Database.", e.getMessage());
        }

    }

    private void initialize_routines_map() {
        routines_map.put(syncBox.getId(), performance::getSynchronizationMode);
        routines_map.put(vacuumBox.getId(), performance::getVacuumMode);
        routines_map.put(lockBox.getId(), performance::getLockMode);
        routines_map.put(optimizeBox.getId(), optimizeBox.getItems()::getFirst);
        routines_map.put(threadSpinner.getId(), performance::getThreads);
        routines_map.put(indexCheck.getId(), performance::getAutoIndex);

        routines_set.put(syncBox.getId(), v -> performance.setSynchronizationMode((Synchronization) v));
        routines_set.put(vacuumBox.getId(), v -> performance.setVacuum((VACUUM) v));
        routines_set.put(lockBox.getId(), v -> performance.setLock((Lock) v));
        routines_set.put(optimizeBox.getId(), v -> {});
        routines_set.put(threadSpinner.getId(), v -> performance.setThreads((Integer) v));
        routines_set.put(indexCheck.getId(), v -> performance.setAutoIndex((Boolean) v));

    }

    @Override
    public void initialize_configuration() {
        Thread.ofVirtual().start(() -> {

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
            syncBox.setValue((Synchronization) values_map.get(syncBox.getId()));
            new_values_map.put(syncBox.getId(), syncBox.valueProperty());

            vacuumBox.setValue((VACUUM) values_map.get(vacuumBox.getId()));
            new_values_map.put(vacuumBox.getId(), vacuumBox.valueProperty());

            lockBox.setValue((Lock) values_map.get(lockBox.getId()));
            new_values_map.put(lockBox.getId(), lockBox.valueProperty());

            optimizeBox.setValue((Optimize) values_map.get(optimizeBox.getId()));
            new_values_map.put(optimizeBox.getId(), optimizeBox.valueProperty());

            threadSpinner.getValueFactory().setValue((Integer) values_map.get(threadSpinner.getId()));
            new_values_map.put(threadSpinner.getId(), threadSpinner.valueProperty());

            indexCheck.setSelected((Boolean) values_map.get(indexCheck.getId()));
            new_values_map.put(indexCheck.getId(), indexCheck.selectedProperty());
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

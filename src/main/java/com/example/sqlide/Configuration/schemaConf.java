package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.Enum.Lock;
import com.example.sqlide.drivers.model.Enum.Optimize;
import com.example.sqlide.drivers.model.Enum.Synchronization;
import com.example.sqlide.drivers.model.Enum.VACUUM;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.SchemaPragmaInterface;
import com.example.sqlide.misc.ThrowingConsumer;
import com.example.sqlide.misc.ThrowingRunnable;
import com.sun.jna.platform.win32.ShTypes;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.spreadsheet.Grid;

import java.sql.SQLException;
import java.util.HashMap;

public class schemaConf implements LoadSettings {
    public TextField IdField;
    public ChoiceBox<String> encodingBox;
    public CheckBox foreignCheck;
    public CheckBox checkCheck;
    public ChoiceBox<String> journalBox;
    public Spinner<Integer> journalSize;
    public TextField schemaField;
    public CheckBox writableCheck;
    public TextField userField;

    @FXML
    private GridPane Container;

    private SchemaPragmaInterface schemaInterface = null;
    private final HashMap<String, Object> values_map = new HashMap<>(8), new_values_map = new HashMap<>();
    private final HashMap<String, ThrowingRunnable> routines_map = new HashMap<>(8);
    HashMap<String, ThrowingConsumer<Object, Exception>> routines_set = new HashMap<>(8);

    public void setSchemaInterface(SchemaPragmaInterface schemaInterface) {
        this.schemaInterface = schemaInterface;
        initialize_routines_map();
        initialize_configuration();
    }

    @FXML
    private void initialize() {
        journalSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE));
    }

    private void initialize_routines_map() {
        routines_map.put(IdField.getId(), schemaInterface::getAppID);
        routines_map.put(encodingBox.getId(), schemaInterface::getEncoding);
        routines_map.put(foreignCheck.getId(), schemaInterface::getForeign);
        routines_map.put(checkCheck.getId(), schemaInterface::getIgnoreCheck);
        routines_map.put(journalBox.getId(), schemaInterface::getJournal);
        routines_map.put(journalSize.getId(), schemaInterface::getJournalSize);
        routines_map.put(schemaField.getId(), schemaInterface::getSchemaVersion);
        routines_map.put(writableCheck.getId(), schemaInterface::getWritable);
        routines_map.put(userField.getId(), schemaInterface::getUserVersion);

        routines_set.put(IdField.getId(), v -> schemaInterface.setAppID(Integer.parseInt(String.valueOf(v))));
        routines_set.put(encodingBox.getId(), v -> schemaInterface.setEncoding(String.valueOf(v)));
        routines_set.put(foreignCheck.getId(), v -> schemaInterface.setForeign((Boolean) v));
        routines_set.put(checkCheck.getId(), v -> schemaInterface.setIgnoreCheck((Boolean) v));
        routines_set.put(journalBox.getId(), v -> schemaInterface.setJournal(String.valueOf(v)));
        routines_set.put(journalSize.getId(), v -> schemaInterface.setJournalSize((Integer) v));
        routines_set.put(schemaField.getId(), v -> schemaInterface.setSchemaVersion(Integer.parseInt(String.valueOf(v))));
        routines_set.put(writableCheck.getId(), v -> schemaInterface.setWritable((Boolean) v));
        routines_set.put(userField.getId(), v -> schemaInterface.setUserVersion(Integer.parseInt(String.valueOf(v))));

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

                IdField.setText(String.valueOf(values_map.get(IdField.getId())));
                new_values_map.put(IdField.getId(), IdField.textProperty());

                encodingBox.getSelectionModel().select(String.valueOf(values_map.get(encodingBox.getId())));
                new_values_map.put(encodingBox.getId(), encodingBox.getSelectionModel().selectedItemProperty());

                foreignCheck.setSelected((Boolean) values_map.get(foreignCheck.getId()));
                new_values_map.put(foreignCheck.getId(), foreignCheck.selectedProperty());

                checkCheck.setSelected((Boolean) values_map.get(checkCheck.getId()));
                new_values_map.put(checkCheck.getId(), checkCheck.selectedProperty());

                journalBox.getSelectionModel().select(String.valueOf(values_map.get(journalBox.getId())));
                new_values_map.put(journalBox.getId(), journalBox.getSelectionModel().selectedItemProperty());

                journalSize.getValueFactory().setValue((Integer) values_map.get(journalSize.getId()));
                new_values_map.put(journalSize.getId(), journalSize.valueProperty());

                schemaField.setText(String.valueOf(values_map.get(schemaField.getId())));
                new_values_map.put(schemaField.getId(), schemaField.textProperty());

                userField.setText(String.valueOf(values_map.get(userField.getId())));
                new_values_map.put(userField.getId(), userField.textProperty());

                writableCheck.setSelected((Boolean) values_map.get(writableCheck.getId()));
                new_values_map.put(writableCheck.getId(), writableCheck.selectedProperty());

              /*  IdField.setText(String.valueOf(schemaInterface.getAppID()));
                encodingBox.getSelectionModel().select(schemaInterface.getEncoding());
                userField.setText(String.valueOf(schemaInterface.getUserVersion()));
                schemaField.setText(String.valueOf(schemaInterface.getSchemaVersion()));
                journalBox.getSelectionModel().select(schemaInterface.getJournal());
                writableCheck.setSelected(schemaInterface.getWritable());
                checkCheck.setSelected(schemaInterface.getIgnoreCheck());
                foreignCheck.setSelected(schemaInterface.getForeign()); */
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
            } else if (new_values_map.get(key) instanceof StringProperty stringProperty) {
                newVal = stringProperty.get();
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

package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormater;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class DateFieldCell {

    public static void createColumn(final TableColumn<DataForDB, String> ColumnCellType, final DatabaseUpdaterInterface Updater, final String rowID, final ColumnMetadata Metadata, final ArrayList<SimpleStringProperty> tablePrimeKey, final StringProperty TableName, CellFormater format) {
        final AtomicBoolean updateByUser = new AtomicBoolean(false);
        ColumnCellType.setCellFactory(column -> {
            return new TableCell<DataForDB, String>() {
                    private final DatePicker Calendar = new DatePicker();
             //   private final JFXDatePicker Calendar = new JFXDatePicker();
                {
                    Calendar.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/DatePicker.css")).toExternalForm());
                    Calendar.valueProperty().addListener((obs, oldValue, newValue) -> {
                        final DataForDB item = getTableRow().getItem();
                        if (newValue != null && item != null && !newValue.equals(oldValue) && updateByUser.get()) {
                            //  System.out.println("prime " + ColumnPrimaryKey.get(TableName).getFirst());
                            Calendar.setValue(newValue);
                            //    Object valueFormated = formatValue(newValue.toString());
                            //   final String indexStr = item.GetData("ROWID");
                            //     final long index = indexStr != null ? Long.parseLong(indexStr) : 0;
                            final String[] indexStr = new String[1];
                            indexStr[0] = item.GetData(rowID);
                            final ArrayList<String> keys = tablePrimeKey.stream()
                                    .map(SimpleStringProperty::get)
                                    .collect(Collectors.toCollection(ArrayList::new));
                            if (!Updater.updateData(TableName.get(), Metadata.Name, newValue.toString(), indexStr, Metadata.Type, keys, item.GetData(keys))) {
                                // if (!Database.updateData(TableName, ColName, valueFormated, pageNum.get()*tab.getSelectionModel().getSelectedIndex()+1, tablePrimeKey.get(), getTableRow().getItem().GetData(tablePrimeKey.get()))) {
                                //  if (!Database.updateData(TableName, ColName, valueFormated, index, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                ShowError("Error SQL", "Error to update data", Updater.getException());
                                return;
                            }
                            item.SetData(rowID, indexStr[0]);
                            getTableRow().getItem().SetData(Metadata.Name, newValue.toString());
                        }
                    });
                    setMaxWidth(Calendar.getMaxWidth());
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    updateByUser.set(false);
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        try {
                            if (!item.equals("null")) {
                                Calendar.setValue(LocalDate.parse(item));
                            }
                        } catch (Exception e) {
                            setMaxWidth(USE_COMPUTED_SIZE);
                            StringFieldCell.createColumn(ColumnCellType, Updater, rowID, Metadata, tablePrimeKey, TableName, format);
                        }
                        setGraphic(Calendar);
                    }
                    updateByUser.set(true);
                }
            };
        });
    }

}

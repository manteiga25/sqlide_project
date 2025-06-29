package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormater;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import javafx.beans.property.StringProperty;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class EnumFieldCell {

    public static void createColumn(final TableColumn<DataForDB, String> ColumnCellType, final DatabaseUpdaterInterface Updater, final ColumnMetadata Metadata, final StringProperty tablePrimeKey, final StringProperty TableName, CellFormater format) {
        final AtomicBoolean updateByUser = new AtomicBoolean(false);
        ColumnCellType.setCellFactory(column -> {
            return new TableCell<DataForDB, String>() {
                private final ComboBox<String> comboBox = new ComboBox<String>();
                {
                    comboBox.getItems().addAll(Metadata.items);
                    comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
                        final DataForDB item = getTableRow().getItem();
                        if (newValue != null && item != null && !newValue.equals(oldValue) && updateByUser.get()) {
                            //  System.out.println("prime " + ColumnPrimaryKey.get(TableName).getFirst());
                            //Object valueFormated = formatValue(newValue);
                           // comboBox.setValue(newValue);
                            // if (!Database.updateData(TableName, ColName, valueFormated, pageNum.get()*tab.getSelectionModel().getSelectedIndex()+1, tablePrimeKey.get(), getTableRow().getItem().GetData(tablePrimeKey.get()))) {
                            //final String indexStr = item.GetData("ROWID");
                            //  final long index = indexStr != null ? Long.parseLong(indexStr) : 0;
                            final String[] indexStr = new String[1];
                            indexStr[0] = item.GetData("ROWID");
                            if (!Updater.updateData(TableName.get(), Metadata.Name, newValue, indexStr, Metadata.Type, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                //  if (!Database.updateData(TableName, ColName, valueFormated, index, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                ShowError("Error SQL", "Error to update data\n" + Updater.getException());
                                return;
                            }
                            item.SetData("ROWID", indexStr[0]);
                            getTableRow().getItem().SetData(Metadata.Name, newValue);
                        }
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    updateByUser.set(false);
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        comboBox.setValue(item);
                        setGraphic(comboBox);
                    }
                    updateByUser.set(true);
                }
            };
        });
    }

}

package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.Container.Column.Point.PointField;
import com.example.sqlide.DataForDB;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormater;
import com.example.sqlide.drivers.model.DataBase;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class PointFieldCell {

    public static void createColumn(final TableColumn<DataForDB, String> ColumnCellType, final DataBase Database, final ColumnMetadata Metadata, final StringProperty tablePrimeKey, final StringProperty TableName, CellFormater format) {
        final AtomicBoolean updateByUser = new AtomicBoolean(false);
        ColumnCellType.setCellFactory(column -> {
            return new TableCell<DataForDB, String>() {
                //    private final DatePicker Calendar = new DatePicker();
                private final PointField point = new PointField();
                {
                    point.valueProperty().addListener((obs, oldValue, newValue) -> {
                        final DataForDB item = getTableRow().getItem();
                        if (newValue != null && item != null && !newValue.equals(oldValue) && updateByUser.get()) {
                            //  System.out.println("prime " + ColumnPrimaryKey.get(TableName).getFirst());
                            point.setValue(newValue);
                            //     Object valueFormated = formatValue(newValue.toString());
                            //    final String indexStr = item.GetData("ROWID");
                            //  final long index = indexStr != null ? Long.parseLong(indexStr) : 0;
                            final String[] indexStr = new String[1];
                            indexStr[0] = item.GetData("ROWID");
                            if (!Database.updateData(TableName.get(), Metadata.Name, newValue, indexStr, Metadata.Type, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                //  if (!Database.updateData(TableName, ColName, valueFormated, index, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                //   if (!Database.updateData(TableName, ColName, valueFormated, pageNum.get()*tab.getSelectionModel().getSelectedIndex()+1, tablePrimeKey.get(), getTableRow().getItem().GetData(tablePrimeKey.get()))) {
                                ShowError("Error SQL", "Error to update data\n" + Database.GetException());
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
                        System.out.println("circle " + item);
                        point.setValue(item);
                        setGraphic(point);
                    }
                    updateByUser.set(true);
                }
            };
        });
    }

}

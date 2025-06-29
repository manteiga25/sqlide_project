package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.Container.DateTimePicker;
import com.example.sqlide.DataForDB;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormater;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class DateTimeFieldCell {

    public static void createColumn(final TableColumn<DataForDB, String> ColumnCellType, final DatabaseUpdaterInterface Updater, final ColumnMetadata Metadata, final StringProperty tablePrimeKey, final StringProperty TableName, CellFormater format) {
        final AtomicBoolean updateByUser = new AtomicBoolean(false);
        ColumnCellType.setCellFactory(column -> {
            return new TableCell<DataForDB, String>() {
                private final DateTimePicker Calendar = new DateTimePicker();
                {
                    Calendar.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/DatePicker.css")).toExternalForm());
                    Calendar.valueProperty().addListener((obs, oldValue, newValue) -> {
                        final DataForDB item = getTableRow().getItem();
                        if (newValue != null && item != null && !newValue.equals(oldValue) && updateByUser.get()) {
                            //  System.out.println("prime " + ColumnPrimaryKey.get(TableName).getFirst());
                            Calendar.setValue(newValue);
                            //     Object valueFormated = formatValue(newValue.toString());
                            //    final String indexStr = item.GetData("ROWID");
                            //  final long index = indexStr != null ? Long.parseLong(indexStr) : 0;
                            final String[] indexStr = new String[1];
                            indexStr[0] = item.GetData("ROWID");
                            if (!Updater.updateData(TableName.get(), Metadata.Name, newValue.toString(), indexStr, Metadata.Type, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                //  if (!Database.updateData(TableName, ColName, valueFormated, index, tablePrimeKey.get(), item.GetData(tablePrimeKey.get()))) {
                                //   if (!Database.updateData(TableName, ColName, valueFormated, pageNum.get()*tab.getSelectionModel().getSelectedIndex()+1, tablePrimeKey.get(), getTableRow().getItem().GetData(tablePrimeKey.get()))) {
                                ShowError("Error SQL", "Error to update data\n" + Updater.getException());
                                return;
                            }
                            item.SetData("ROWID", indexStr[0]);
                            getTableRow().getItem().SetData(Metadata.Name, newValue.toString());
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
                        if (!item.equals("null")) {
                            try {
                                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                                // item = item.replace("/", "-");
                                //   item = item.replace(" ", "T");
                                System.out.println("sdcz " + item);
                                //Calendar.setDateTime(LocalDateTime.parse(item));
                                //   Calendar.setDateTime(LocalDateTime.parse(item, dateTimeFormatter)); //.setValue(LocalDate.parse(item));
                                Calendar.setDateTimeValue(LocalDateTime.parse(item, dateTimeFormatter));
                            } catch (Exception e) {
                                try {
                                    //  item = item.replace("-", "/");
                                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                                    //   Calendar.setDateTime(LocalDate.parse(item, dateFormatter));
                                    Calendar.setDateTimeValue(LocalDate.parse(item, dateFormatter).atStartOfDay());
                                    //    Calendar.removeWidgets();
                                } catch (Exception ex) {
                                    try {
                                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                                        Calendar.setDateTimeValue(LocalDate.parse(item, dateFormatter).atStartOfDay());
                                    } catch (Exception exc) {
                                        try {
                                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                            Calendar.setDateTimeValue(LocalDateTime.parse(item, formatter));
                                        } catch (Exception e1) {
                                            StringFieldCell.createColumn(ColumnCellType, Updater, Metadata, tablePrimeKey, TableName, format);
                                        }
                                    }
                                }
                            }
                            setGraphic(Calendar);
                        }
                    }
                    updateByUser.set(true);
                }
            };
        });
    }

}

package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormater;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public abstract class StringFieldCell {

    public static void createColumn(final TableColumn<DataForDB, String> ColumnCellType, final DatabaseUpdaterInterface Updater, final String rowID, final ColumnMetadata Metadata, final ArrayList<SimpleStringProperty> tablePrimeKey, final StringProperty TableName, final CellFormater format) {
        ColumnCellType.setCellFactory(TextFieldTableCell.forTableColumn());
       /* ColumnCellType.setCellFactory(new Callback<TableColumn<DataForDB, String>, TableCell<DataForDB, String>>() {
            @Override
            public javafx.scene.control.TableCell<DataForDB, String> call(TableColumn<DataForDB, String> param) {
                return new javafx.scene.control.TableCell<DataForDB, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        updateByUser[0] = false;
                        super.updateItem(item, empty);

                        // Verifique se a célula não está vazia
                        if (!empty) {
                            // Exemplo de estilização baseado no valor da célula
                            if (item != null && format.format) {
                                setCellStyle(item);
                            }

                            // Atualizando o valor da célula
                            setText(item);
                        } else {
                            setText(null); // Se estiver vazio, remova o texto
                        }
                        updateByUser[0] = true;
                    }
                    private void setCellStyle(final String value) {
                        if (!check(value)) {
                            setStyle(format.getStyle());
                        } else {
                            setStyle("");
                        }
                    }

                    private boolean check(final String value) {
                        switch (format.operation) {
                            case 0:
                                System.out.println("a0");
                                return value.equals(format.value);
                            case 1:
                                System.out.println("a1");
                                try {
                                    final int number = Integer.parseInt(format.value);
                                    return format.value.length() < number;
                                } catch (NumberFormatException _) {
                                    return value.compareTo(format.value) < 0;
                                }
                            case 2:
                                System.out.println("a2");
                                try {
                                    final int number = Integer.parseInt(format.value);
                                   // return value.length() > number;
                                    return Integer.parseInt(value) > number;
                                } catch (NumberFormatException _) {
                                    return value.compareTo(format.value) > 0;
                                }
                            case 3:
                                System.out.println("a3");
                                try {
                                    final int number = Integer.parseInt(format.value);
                                    return value.length() <= number;
                                } catch (NumberFormatException _) {
                                    return value.compareTo(format.value) <= 0;
                                }
                            case 4:
                                System.out.println("a4");
                                try {
                                    final int number = Integer.parseInt(format.value);
                                    return value.length() >= number;
                                } catch (NumberFormatException _) {
                                    return value.compareTo(format.value) >= 0;
                                }
                            case 5:
                                System.out.println("a5");
                                return !value.equals(format.value);
                        }
                        return false;
                    }

                };
            }
        }); */

        ColumnCellType.setOnEditCommit(e->{
            final String newValue = e.getNewValue();
            System.out.println("hi");
            if (!Objects.equals(newValue, e.getOldValue())) {
                final DataForDB item = e.getRowValue();
              /*  if (!Updater.types.checkValue(Metadata.Type, newValue, Metadata.size, !Metadata.NOT_NULL)) {
                    ShowError("Error SQL", "Error to update data\n" + Updater.types.getException());
                    return;
                } */
                //  System.out.println("prime " + ColumnPrimaryKey.get(TableName).getFirst());
                //   Object valueFormated = formatValue(newValue);
                //  System.out.println("é inteiro " + (valueFormated instanceof Long));
                // System.out.println("key " + tablePrimeKey.get());
                final String[] indexStr = new String[1];
                indexStr[0] = item.GetData(rowID);
                //   final long index = indexStr != null ? Long.parseLong(indexStr) : 0;
                final ArrayList<String> keys = tablePrimeKey.stream()
                        .map(SimpleStringProperty::get)
                        .collect(Collectors.toCollection(ArrayList::new));
                if (!Updater.updateData(TableName.get(), Metadata.Name, newValue, indexStr, Metadata.Type, keys, item.GetData(keys))) {
                    ShowError("Error SQL", "Error to update data", Updater.getException());
                    return;
                }
                item.SetData(rowID, indexStr[0]); // workaround for postgreSQL
                item.SetData(Metadata.Name, newValue);
            }
        });
    }

}

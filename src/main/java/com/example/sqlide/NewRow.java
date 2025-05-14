package com.example.sqlide;

import com.example.sqlide.Container.DateTimePicker;
import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class NewRow {
    public AnchorPane NewRowContainer;
    public GridPane GridContainer;
    public Label DBInfo;

    private TableInterface ref;

    private final ArrayList<Object> WidgetsList = new ArrayList<>();

    private LinkedHashMap<String, ColumnMetadata> type;

    private Stage window;

    private SQLiteTypes routines;

    public void NewRowWin(final String DBName, final String Table, final TableInterface ref, final Stage subStage, final LinkedHashMap<String, ColumnMetadata> type, final SQLiteTypes types) {
        DBInfo.setText("Database: " + DBName + "\n" + "Table: " + Table);
        this.ref = ref;
        this.type = type;
        this.routines = types;
        window = subStage;
        createWidget();
    }

    private void createWidget() {
        int column = 0, row = 0;
        for (final String col : type.keySet()) {
            Object widget = WidgetGenericType(col);
            Label columnLabel = new Label(col + ":");
            columnLabel.setTextFill(Color.WHITE);
            columnLabel.setWrapText(true);
            HBox columnBox = new HBox(5);
            columnBox.setAlignment(Pos.CENTER_LEFT);
            columnBox.setPadding(new Insets(0,0,0,10));
            columnBox.getChildren().addAll(columnLabel, (Node) widget);
            GridPane.setHalignment(columnBox, HPos.CENTER);
            GridContainer.add(columnBox, column, row);
       //     Object s = WidgetGenericType(col);
            WidgetsList.add(widget);
          //  GridContainer.add((Node) s, ++column, row);
            ++column;
            if (column % 2 == 0) {
                column = 0;
                row++;
            }
        }

    }

    private Object WidgetGenericType(final String column) {
        Object widget = null;
        final String Type = type.get(column).Type;
        if (type.get(column).items != null) {
            widget = createComboBox(column);
        }
        else if (Type.equals("DATETIME")) {
            widget = createCalendarTime(column);
        }
        else if (Type.equals("DATE")) {
            widget = createCalendar(column);
        }
        else {
            widget = createTextField(column);
        }
        return widget;
    }

    private JFXTextField createTextField(final String column) {
        JFXTextField tmpText = new JFXTextField();
        tmpText.setId(column);
        tmpText.setLabelFloat(true);
        tmpText.setPromptText(type.get(column).Type);
        tmpText.setText(type.get(column).defaultValue);
        tmpText.setStyle("-fx-text-fill: white;");
        return tmpText;
    }

    private ComboBox<String> createComboBox(final String column) {
        ComboBox<String> box = new ComboBox<>();
        box.setId(column);
        box.setValue(type.get(column).defaultValue != null ? type.get(column).defaultValue : "");
        return box;
    }

    private DateTimePicker createCalendarTime(final String column) {
        DateTimePicker calendar = new DateTimePicker();
        calendar.setId(column);
        try {
            calendar.setValue(LocalDate.parse(type.get(column).defaultValue));
        } catch (Exception _) {
        }
        return calendar;
    }

    private DatePicker createCalendar(final String column) {
        DatePicker calendar = new DatePicker();
        calendar.setId(column);
        try {
            calendar.setValue(LocalDate.parse(type.get(column).defaultValue));
        } catch (Exception _) {
        }
        return calendar;
    }

    @FXML
    private void addData() {
        HashMap<String, String> data = new HashMap<>();
        for (final Object widget : WidgetsList) {
            if (widget instanceof JFXTextField) {
                if (!treatTextField((JFXTextField) widget, data)) {
                    return;
                }
            }
            else if (widget instanceof DateTimePicker) {
                if (!treatCalendarTime((DateTimePicker) widget, data)) {
                    return;
                }
            }
            else if (widget instanceof DatePicker) {
                if (!treatCalendar((DatePicker) widget, data)) {
                    return;
                }
            }
            else {
                if (!treatComboBox((ComboBox<String>) widget, data)) {
                    return;
                }
            }
        }
        if (ref.insertData(data)) {
            freeText();
        }
    }

    private boolean treatTextField(JFXTextField widget, HashMap<String, String> data) {
        final String value = widget.getText();
        final String columnName = widget.getId();
        final ColumnMetadata meta = type.get(columnName);
        if (!routines.checkValue(meta.Type, value, meta.size, !meta.NOT_NULL)) {
            System.out.println(routines.getException());
            widget.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            widget.requestFocus();
            ShowError("Invalid data", "Please insert data on column " + columnName + ".");
            return false;
        }
        data.put(columnName, value);
        return true;
    }

    private boolean treatComboBox(ComboBox<String> widget, HashMap<String, String> data) {
        final String value = widget.getValue();
        final String columnName = widget.getId();
        final ColumnMetadata meta = type.get(columnName);
        if ((!meta.NOT_NULL && value == null)) {
            widget.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            widget.requestFocus();
            ShowError("Invalid data", "Please insert data on column " + columnName + ".\n" + routines.getException());
            return false;
        }
        data.put(columnName, value);
        return true;
    }

    private boolean treatCalendarTime(DateTimePicker widget, HashMap<String, String> data) {
        final LocalDateTime value = widget.getDateTimeValue();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        final String columnName = widget.getId();
        final ColumnMetadata meta = type.get(columnName);
        if ((!meta.NOT_NULL && value == null)) {
            widget.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            widget.requestFocus();
            ShowError("Invalid data", "Please insert data on column " + columnName + ".\n" + routines.getException());
            return false;
        }
        data.put(columnName, value.format(formatter));
        return true;
    }

    private boolean treatCalendar(DatePicker widget, HashMap<String, String> data) {
        final LocalDate value = widget.getValue();
        final String columnName = widget.getId();
        final ColumnMetadata meta = type.get(columnName);
        if ((!meta.NOT_NULL && value == null)) {
            widget.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            widget.requestFocus();
            ShowError("Invalid data", "Please insert data on column " + columnName + ".\n" + routines.getException());
            return false;
        }
        data.put(columnName, value.toString());
        return true;
    }

    private void freeText() {
        for (final Object widget : WidgetsList) {
            if (widget instanceof JFXTextField w) {
                final ColumnMetadata meta = type.get(w.getId());
                 w.setText(meta.defaultValue);
            }
            else if (widget instanceof DateTimePicker w) {
                final ColumnMetadata meta = type.get(w.getId());
                LocalDateTime date;
                if (meta != null && !meta.defaultValue.isEmpty()) {
                    date = LocalDateTime.from(LocalTime.parse(meta.defaultValue));
                }
                else {
                    date = LocalDateTime.now();
                }
                w.setDateTimeValue(date);
               // w.setDateTimeValue(date);
            }
            else if (widget instanceof DatePicker w) {
                final ColumnMetadata meta = type.get(w.getId());
                LocalDate date;
                if (meta != null && !meta.defaultValue.isEmpty()) {
                    date = LocalDate.parse(meta.defaultValue);
                }
                else {
                    date = LocalDate.now();
                }
                w.setValue(date);
                // w.setDateTimeValue(date);
            }
            else {
                ComboBox<String> box = (ComboBox<String>) widget;
                final ColumnMetadata meta = type.get(box.getId());
                box.setValue(meta.defaultValue);
            }
        }
    }

    @FXML
    private void exit() {
        window.close();
    }

}

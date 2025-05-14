package com.example.sqlide.Container;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class DateTimePickerSkin extends DatePickerSkin {
    private final ObjectProperty<LocalTime> timeObjectProperty;
    private final Node popupContent;
    private final DateTimePicker dateTimePicker;
    private final Node timeSpinner;

    DateTimePickerSkin(DateTimePicker dateTimePicker) {
        super(dateTimePicker);
        this.dateTimePicker = dateTimePicker;
        timeObjectProperty = new SimpleObjectProperty<>(this, "displayedTime", LocalTime.from(dateTimePicker.getDateTimeValue()));

        CustomBinding.bindBidirectional(dateTimePicker.dateTimeValueProperty(), timeObjectProperty,
                LocalDateTime::toLocalTime,
                lt -> dateTimePicker.getDateTimeValue().withHour(lt.getHour()).withMinute(lt.getMinute()).withSecond(lt.getSecond())
        );

        popupContent = super.getPopupContent();
        popupContent.getStyleClass().add("date-time-picker-popup");

        timeSpinner = getTimeSpinner();

        ((VBox) popupContent).getChildren().add(timeSpinner);

    }

    private Node getTimeSpinner() {
        if (timeSpinner != null) {
            return timeSpinner;
        }
        final HourMinuteSecondSpinner spinnerHours =
                new HourMinuteSecondSpinner(0, 23, dateTimePicker.getDateTimeValue().getHour());
        CustomBinding.bindBidirectional(timeObjectProperty, spinnerHours.valueProperty(),
                LocalTime::getHour,
                hour -> timeObjectProperty.get().withHour(hour)
        );
        final HourMinuteSecondSpinner spinnerMinutes =
                new HourMinuteSecondSpinner(0, 59, dateTimePicker.getDateTimeValue().getMinute());
        CustomBinding.bindBidirectional(timeObjectProperty, spinnerMinutes.valueProperty(),
                LocalTime::getMinute,
                minute -> timeObjectProperty.get().withMinute(minute)
        );
        final HourMinuteSecondSpinner spinnerSeconds =
                new HourMinuteSecondSpinner(0, 59, dateTimePicker.getDateTimeValue().getSecond());
        CustomBinding.bindBidirectional(timeObjectProperty, spinnerSeconds.valueProperty(),
                LocalTime::getSecond,
                second -> timeObjectProperty.get().withSecond(second)
        );
        final Label labelTimeSeperator = new Label("Minutes:");
        final Label labelTimeSeparator2 = new Label("Seconds:");
        HBox hBox = new HBox(5, new Label("Hours:"), spinnerHours, labelTimeSeperator, spinnerMinutes, labelTimeSeparator2, spinnerSeconds);
        hBox.setPadding(new Insets(8));
        hBox.setAlignment(Pos.CENTER_LEFT);
        registerChangeListener(dateTimePicker.valueProperty(), e -> {
            LocalDateTime dateTimeValue = dateTimePicker.getDateTimeValue();
            timeObjectProperty.set((dateTimeValue != null) ? LocalTime.from(dateTimeValue) : LocalTime.MIDNIGHT);
            dateTimePicker.fireEvent(new ActionEvent());
        });
        ObservableList<String> styleClass = hBox.getStyleClass();
        styleClass.add("month-year-pane");
        styleClass.add("time-selector-pane");
        styleClass.add("time-selector-spinner-pane");
        return hBox;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getPopupContent() {
        return popupContent;
    }


}
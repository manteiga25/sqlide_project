package com.example.sqlide.Task;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import org.controlsfx.control.TaskProgressView;

import java.util.Objects;

public class TaskManager implements TaskInterface {

    private final TaskProgressView<Task<?>> view;

    private final Popup popup = new Popup();

    public Popup getPopup() {
        return popup;
    }

    public TaskManager() {
        view = new TaskProgressView<>();
        view.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TaskStyle.css")).toExternalForm());

        view.setPrefSize(300, 200);

        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        final VBox box = new VBox(5);
        box.setStyle("-fx-background-color: #3C3C3C; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0.1, 0, 2); -fx-background-radius: 8px;");
        box.setAlignment(Pos.CENTER_RIGHT);

        final ObjectProperty<Point2D> mouseLoc = new SimpleObjectProperty<>();

        box.setOnMousePressed(ev ->
                mouseLoc.set(new Point2D(ev.getScreenX(), ev.getScreenY()))
        );

        box.setOnMouseDragged(ev -> {
            Point2D prev = mouseLoc.get();
            if (prev != null) {
                double deltaX = ev.getScreenX() - prev.getX();
                double deltaY = ev.getScreenY() - prev.getY();
                popup.setX(popup.getX() + deltaX);
                popup.setY(popup.getY() + deltaY);
                mouseLoc.set(new Point2D(ev.getScreenX(), ev.getScreenY()));
            }
        });

        box.setOnMouseReleased(_ -> mouseLoc.set(null));

        final Button hide = createHideButton();

        hide.setOnAction(_->popup.hide());

        box.getChildren().addAll(hide, view);

        popup.getContent().add(box);

    }

    private Button createHideButton() {
        final FontAwesomeIconView iconView = new FontAwesomeIconView(FontAwesomeIcon.MINUS);
        iconView.setSize("1.2em");
        iconView.setFill(Color.LIGHTGRAY);

        final Button button = new Button();
        button.setGraphic(iconView);
        button.setTooltip(new Tooltip("hide"));
        button.setStyle("-fx-background-color: transparent;");

        return button;
    }

    public TaskProgressView<Task<?>> getTaskView() {
        return view;
    }

    public ObservableList<Task<?>> getTaskList() {
        return view.getTasks();
    }

    @Override
    public void addTask(Task<?> task) {
        view.getTasks().addFirst(task);
    }
}

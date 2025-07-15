package com.example.sqlide;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.controlsfx.control.TaskProgressView;
import org.controlsfx.control.action.Action;

public class TaskManager implements TaskInterface {

    private final TaskProgressView<Task<?>> view;

    public TaskManager() {
        view = new TaskProgressView<>();

        view.setPrefSize(300, 200);
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

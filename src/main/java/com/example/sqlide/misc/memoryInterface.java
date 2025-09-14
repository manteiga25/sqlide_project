package com.example.sqlide.misc;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.util.Timer;
import java.util.TimerTask;

public interface memoryInterface {

    SystemInfo si = new SystemInfo();
    GlobalMemory memory = si.getHardware().getMemory();

    SimpleBooleanProperty ignore = new SimpleBooleanProperty(false);

    SimpleLongProperty memory_value = new SimpleLongProperty();

    default long getTotalMemory() {
        return (long) (memory.getTotal() * 0.8);
    }

    default SimpleLongProperty getAvalaibleMemoryProp() {
        return memory_value;
    }

    default void setIgnore(final boolean ignore) {
        memoryInterface.ignore.set(ignore);
    }

    default void initializeMemory() {

        final long ratio = (long) (memory.getTotal() * 0.8);

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                memory_value.set(memory.getAvailable());
                if (!ignore.get() && memory_value.get() > ratio) onLowMemory();
                else if (ignore.get() && memory_value.get() < ratio) ignore.set(false);
            }
        };

        new Timer().scheduleAtFixedRate(task, 0, 5000);

    }

    void onLowMemory();

}

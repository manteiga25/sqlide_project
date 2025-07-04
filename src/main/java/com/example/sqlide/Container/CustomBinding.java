package com.example.sqlide.Container;

import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;

import java.util.function.Function;

public class CustomBinding {

    public static <A, B> void bindBidirectional(Property<A> propertyA, Property<B> propertyB, Function<A, B> updateB, Function<B, A> updateA) {
        addFlaggedChangeListener(propertyA, propertyB, updateB);
        addFlaggedChangeListener(propertyB, propertyA, updateA);
    }

    private static <X, Y> void addFlaggedChangeListener(ObservableValue<X> propertyX, WritableValue<Y> propertyY, Function<X, Y> updateY) {
        propertyX.addListener(new ChangeListener<>() {
            private boolean alreadyCalled = false;

            @Override
            public void changed(ObservableValue<? extends X> observable, X oldValue, X newValue) {
                if (alreadyCalled) return;
                try {
                    alreadyCalled = true;
                    propertyY.setValue(updateY.apply(newValue));
                } finally {
                    alreadyCalled = false;
                }
            }
        });
    }
}

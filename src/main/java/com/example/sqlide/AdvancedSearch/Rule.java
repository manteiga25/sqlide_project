package com.example.sqlide.AdvancedSearch;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Rule {

        private final StringProperty column = new SimpleStringProperty();
        private final StringProperty rule = new SimpleStringProperty();
        private final BooleanProperty status = new SimpleBooleanProperty();

        public Rule(String column, String rule, boolean status) {
            this.column.set(column);
            this.rule.set(rule);
            this.status.set(status);
        }

    public BooleanProperty statusProperty() {
        return status;
    }

    public StringProperty ruleProperty() {
        return rule;
    }

    public StringProperty columnProperty() {
        return column;
    }

    public boolean isStatus() {
        return status.get();
    }

    public boolean getStatus() {
        return status.get();
    }

    public String getRule() {
        return rule.get();
    }

    public String getColumn() {
        return column.get();
    }

    public void setRule(final String rule) {
            this.rule.set(rule);
    }

    public void setColumn(final String column) {
            this.column.set(column);
    }

    public void setStatus(final boolean status) {
            this.status.set(status);
    }

}

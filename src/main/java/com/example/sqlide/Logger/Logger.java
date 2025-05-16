package com.example.sqlide.Logger;

import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType.DateTimeFieldCell;

import java.time.LocalTime;

public class Logger {

    private String Query, User, Warning;
    private LocalTime time;

    public LocalTime getTime() {
        return time;
    }

    public String getQuery() {
        return Query;
    }

    public String getWarning() {
        return Warning;
    }

    public String getUser() {
        return User;
    }

    public void setQuery(String query) {
        Query = query;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public void setUser(String user) {
        User = user;
    }

    public void setWarning(String warning) {
        Warning = warning;
    }

    public Logger(final String User, final String Query, final String warning, final LocalTime time) {
        this.User = User;
        this.Query = Query;
        this.Warning = warning;
        this.time = time;
    }
}

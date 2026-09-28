package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import java.sql.SQLException;

public interface SchemaPragmaInterface {

    int getAppID() throws SQLException;
    int getSchemaVersion() throws SQLException;
    int getUserVersion() throws SQLException;
    String getEncoding() throws SQLException;
    boolean getForeign() throws SQLException;
    boolean getIgnoreCheck() throws SQLException;
    String getJournal() throws SQLException;
    int getJournalSize() throws SQLException;
    boolean getWritable() throws SQLException;

    void setAppID(int id) throws SQLException;
    void setSchemaVersion(int id) throws SQLException;
    void setUserVersion(int id) throws SQLException;
    void setEncoding(String encoding) throws SQLException;
    void setForeign(boolean status) throws SQLException;
    void setIgnoreCheck(boolean status) throws SQLException;
    void setJournal(String mode) throws SQLException;
    void setJournalSize(int size) throws SQLException;
    void setWritable(boolean status) throws SQLException;


}

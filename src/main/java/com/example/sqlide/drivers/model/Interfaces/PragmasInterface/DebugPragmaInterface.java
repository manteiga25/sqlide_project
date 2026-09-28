package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import com.example.sqlide.drivers.model.Enum.TempStore;

import java.sql.SQLException;

public interface DebugPragmaInterface {

    boolean getParserTrace() throws SQLException;
    String getDirectory();
    TempStore getTempMode() throws SQLException;

    void setParserTrace(boolean status) throws SQLException;
    void setDirectory(String path) throws SQLException;
    void setTempMode(TempStore tempMode) throws SQLException;

    void actionQuick();

}

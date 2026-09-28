package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import java.sql.SQLException;

public interface IOPragma_Interface {

    boolean getFullSync() throws SQLException;
    boolean getFullSyncCheckpoint() throws SQLException;
    boolean getDelete() throws SQLException;
    boolean getCellSize() throws SQLException;

    String getFlushMethod() throws SQLException;
    int getBinlogSync() throws SQLException;
    int getIOCapacity() throws SQLException;
    int getIOCapacityMax() throws SQLException;
    boolean getFlushNeighbors() throws SQLException;
    boolean getNativeAIO() throws SQLException;


    void setFullSync(boolean state) throws SQLException;
    void setFullSyncCheckpoint(boolean state) throws SQLException;
    void setDelete(boolean state) throws SQLException;
    void setCellSize(boolean state) throws SQLException;

    void setFlushMethod(String method) throws SQLException;
    void setBinlogSync(int value) throws SQLException;
    void setIOCapacity(int value) throws SQLException;
    void setIOCapacityMax(int value) throws SQLException;
    void setFlushNeighbors(boolean state) throws SQLException;
    void setNativeAIO(boolean state) throws SQLException;

}

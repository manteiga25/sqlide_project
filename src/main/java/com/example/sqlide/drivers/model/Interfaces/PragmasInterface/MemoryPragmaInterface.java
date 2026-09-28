package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import java.sql.SQLException;

public interface MemoryPragmaInterface {

    int getCacheSize() throws SQLException;
    int getPageSize() throws SQLException;
    int getMMapSize() throws SQLException;
    int getHardHeapSize() throws SQLException;
    int getSoftHeapSize() throws SQLException;
    int getMaxPages() throws SQLException;
    boolean getCacheSpill() throws SQLException;
    long getTotalPages() throws SQLException;

    void setCacheSize(int size) throws SQLException;
    void setPageSize(int size) throws SQLException;
    void setMMapSize(int size) throws SQLException;
    void setHardHeapSize(int size) throws SQLException;
    void setSoftHeapSize(int size) throws SQLException;
    void setMaxPages(int size) throws SQLException;
    void setCacheSpill(boolean state) throws SQLException;



    int getLogBufferSize() throws SQLException;
    int getSortBufferSize() throws SQLException;
    int getJoinBufferSize() throws SQLException;
    int getReadBufferSize() throws SQLException;
    int getReadRndBufferSize() throws SQLException;
    int getThreadStackSize() throws SQLException;

    void setLogBufferSize(int size) throws SQLException;
    void setSortBufferSize(int size) throws SQLException;
    void setJoinBufferSize(int size) throws SQLException;
    void setReadBufferSize(int size) throws SQLException;
    void setReadRndBufferSize(int size) throws SQLException;
    void setThreadStackSize(int size) throws SQLException;



}

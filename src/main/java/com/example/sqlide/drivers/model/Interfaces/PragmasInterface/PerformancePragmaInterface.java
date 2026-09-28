package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import com.example.sqlide.drivers.model.Enum.Lock;
import com.example.sqlide.drivers.model.Enum.Optimize;
import com.example.sqlide.drivers.model.Enum.Synchronization;
import com.example.sqlide.drivers.model.Enum.VACUUM;

import java.sql.SQLException;

public interface PerformancePragmaInterface {

    Synchronization getSynchronizationMode() throws SQLException;
    int getThreads() throws SQLException;
    Lock getLockMode() throws SQLException;
    VACUUM getVacuumMode() throws SQLException;
    boolean getAutoIndex() throws SQLException;

    void setSynchronizationMode(Synchronization synchronizationMode) throws SQLException;
    void setThreads(int threads) throws SQLException;
    void setLock(Lock lock) throws SQLException;
    void setVacuum(VACUUM vacuum) throws SQLException;
    void setOptimizer(Optimize optimizer) throws SQLException;
    void setAutoIndex(boolean autoIndex) throws SQLException;

}

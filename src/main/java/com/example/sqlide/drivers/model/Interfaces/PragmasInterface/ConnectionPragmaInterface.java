package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import java.sql.SQLException;

public interface ConnectionPragmaInterface {

    int getTimeout() throws SQLException;

    void setTimeout(int timeout) throws SQLException;

}

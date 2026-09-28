package com.example.sqlide.drivers.model.Interfaces;

import java.sql.SQLException;

/**
 * Executa comandos de escrita arbitrários na ligação aberta.
 *
 * <p>Os {@code Updater}/{@code Inserter} cobrem a edição de linhas feita pela grelha, onde
 * a chave primária é conhecida. Isto existe para as operações em bloco do Data Science —
 * um UPDATE de imputação ou um DELETE de outliers atinge milhares de linhas de uma vez,
 * e fazer isso linha a linha pela grelha seria impraticável.</p>
 */
public interface DatabaseExecutorInterface {

    /**
     * Corre um comando de escrita (UPDATE, DELETE, INSERT, DDL).
     *
     * @return número de linhas afetadas
     */
    int executeUpdate(String command) throws SQLException;

    /** Identificador citado para o dialeto em causa, para nomes com espaços ou reservados. */
    String quoteIdentifier(String identifier);

}

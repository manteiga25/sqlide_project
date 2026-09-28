package com.example.sqlide.drivers.model.Interfaces.PragmasInterface;

import com.example.sqlide.Configuration.permissionConfController;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Gestão de utilizadores e privilégios do motor.
 *
 * <p>Mantém os métodos que já existiam. O que foi acrescentado resolve buracos que a UI
 * tinha: não havia forma de saber se o utilizador ligado pode sequer administrar contas,
 * a lista de privilégios estava fixa no FXML com os nomes do MySQL, os privilégios eram
 * lidos globalmente mas gravados por tabela, e o campo da palavra-passe não fazia nada.</p>
 */
public interface PermissionPragmaInterface {

    /** Contas existentes, indexadas pela sua representação textual. */
    Map<String, permissionConfController.userInformation> getUsers() throws SQLException;

    /** Instruções GRANT em vigor para a conta, tal como o motor as descreve. */
    List<String> getPermissions(String user) throws SQLException;

    /**
     * Privilégios da conta no âmbito indicado, já resolvidos para ligado/desligado.
     *
     * <p>É isto que a UI precisa: antes lia os grants globais e escrevia por tabela, por
     * isso a diferença entre o estado mostrado e o estado real fazia com que o "guardar"
     * emitisse GRANT e REVOKE errados.</p>
     *
     * @param table nome da tabela, ou null para o âmbito da base de dados inteira
     */
    Map<String, Boolean> getPermissions(String user, String db, String table) throws SQLException;

    /** True se a ligação atual tem autoridade para criar, apagar e alterar contas. */
    boolean canManageUsers() throws SQLException;

    /** Privilégios que este motor entende, por ordem de apresentação. */
    List<String> supportedPrivileges();

    /** Métodos de autenticação aceites, para a caixa de criação de conta. */
    List<String> authenticationMethods();

    void addUser(permissionConfController.userInformation user) throws SQLException;

    void dropUser(String user) throws SQLException;

    /** Muda a palavra-passe de uma conta existente. */
    void changePassword(permissionConfController.userInformation user) throws SQLException;

    void grant(String privilege, String db, String table, String user) throws SQLException;

    void revoke(String privilege, String db, String table, String user) throws SQLException;

}

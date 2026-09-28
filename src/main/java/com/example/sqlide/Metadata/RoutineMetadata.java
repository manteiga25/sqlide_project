package com.example.sqlide.Metadata;

/**
 * Descrição de uma rotina do esquema: função, procedimento, agregado ou vista.
 *
 * <p>É preenchida quando a base de dados é aberta e guardada com o resto dos metadados,
 * para o catálogo e o construtor de consultas poderem oferecer o que o motor tem sem
 * voltar a interrogá-lo de cada vez.</p>
 */
public class RoutineMetadata {

    public enum Kind {
        FUNCTION("Function"),
        AGGREGATE("Aggregate"),
        PROCEDURE("Procedure"),
        VIEW("View");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Nome invocável da rotina. */
    public String Name = "";

    public Kind kind = Kind.FUNCTION;

    /** Agrupamento para a árvore do catálogo: "String", "Math", "Date", "User defined"... */
    public String category = "Other";

    /** Assinatura dos parâmetros tal como o motor a descreve, ex: {@code (x REAL, y REAL)}. */
    public String parameters = "";

    /** Tipo devolvido, vazio nos procedimentos que não devolvem nada. */
    public String returnType = "";

    /** Descrição do que faz, vinda do comentário da rotina ou da documentação do motor. */
    public String comment = "";

    /** True para as que vêm com o motor, false para as criadas por código SQL do utilizador. */
    public boolean builtIn = true;

    public RoutineMetadata() {
    }

    public RoutineMetadata(String name, Kind kind, String category, String parameters,
                           String returnType, String comment, boolean builtIn) {
        this.Name = name;
        this.kind = kind;
        this.category = category;
        this.parameters = parameters;
        this.returnType = returnType;
        this.comment = comment;
        this.builtIn = builtIn;
    }

    /** Como a rotina se lê numa lista: {@code nome(params) -> tipo}. */
    public String signature() {
        StringBuilder text = new StringBuilder(Name);
        if (kind != Kind.VIEW) text.append(parameters == null || parameters.isBlank() ? "()" : parameters);
        if (returnType != null && !returnType.isBlank()) text.append(" -> ").append(returnType);
        return text.toString();
    }

    /**
     * Modelo de chamada para colar numa consulta, com os nomes dos parâmetros por preencher.
     * Nas vistas devolve só o nome, porque uma vista entra no FROM e não se invoca.
     */
    public String callTemplate() {
        if (kind == Kind.VIEW) return Name;
        if (parameters == null || parameters.isBlank()) return Name + "()";
        return Name + parameters;
    }

    @Override
    public String toString() {
        return signature();
    }

}

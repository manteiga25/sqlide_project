package com.example.sqlide.Metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Um índice existente numa tabela.
 *
 * <p>Lido do catálogo do motor, para o painel de índices poder listar o que lá está em vez
 * de só saber criar coisas novas.</p>
 */
public class IndexMetadata {

    public String Name = "";
    public String table = "";
    public final List<String> columns = new ArrayList<>();
    public boolean unique = false;

    /** Método do índice (btree, hash, gin...). Só o PostgreSQL o distingue. */
    public String method = "";

    /** True para os índices que o motor cria sozinho por causa de uma chave. */
    public boolean implicit = false;

    public IndexMetadata() {
    }

    public IndexMetadata(String name, String table, boolean unique) {
        this.Name = name;
        this.table = table;
        this.unique = unique;
    }

    public String describeColumns() {
        return String.join(", ", columns);
    }

    @Override
    public String toString() {
        return Name + " (" + describeColumns() + ")" + (unique ? "  ·  unique" : "");
    }

}

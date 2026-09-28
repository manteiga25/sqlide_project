package com.example.sqlide.Metadata;

import com.example.sqlide.View.ViewController;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class TableMetadata {

    private final SimpleStringProperty Name;

    private String check;

    private final ObservableList<ColumnMetadata> columnMetadata = FXCollections.observableArrayList();

    private final ArrayList<ViewController.View> views = new ArrayList<>();

    private final ArrayList<SimpleStringProperty> PrimaryKey = new ArrayList<>();

    public ArrayList<SimpleStringProperty> getPrimaryKeyProperty() {
        return PrimaryKey;
    }

    public void setCheck(final String check) {
        this.check = check;
    }

    public String getCheck() {
        return check;
    }

    /** Primeira coluna da chave primária, ou "" quando a tabela não tem chave (antes rebentava). */
    public String getPrimaryKey() {
        return PrimaryKey.isEmpty() ? "" : PrimaryKey.getFirst().get();
    }

    /**
     * Refaz a lista da chave primária a partir das colunas, depois de uma coluna mudar. A lista
     * é a mesma que as células da grelha usam para gravar, por isso muda-se o conteúdo e não
     * o objeto.
     */
    public void refreshPrimaryKeys() {
        PrimaryKey.clear();
        for (final ColumnMetadata column : columnMetadata) {
            if (column.IsPrimaryKey) PrimaryKey.add(new SimpleStringProperty(column.Name));
        }
    }

    public ArrayList<String> getPrimaryKeys() {
        return PrimaryKey.stream()
                .map(SimpleStringProperty::get)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public boolean hasPrimaryKey() {
        return !PrimaryKey.isEmpty();
    }

    public TableMetadata(final String Name) {
        this.Name = new SimpleStringProperty(Name);
    }

    public void addColumn(final ColumnMetadata meta) {
        if (meta.IsPrimaryKey) PrimaryKey.add(new SimpleStringProperty(meta.Name));
        columnMetadata.add(meta);
    }

    public void removeColumn(final ColumnMetadata meta) {
        if (meta.IsPrimaryKey) PrimaryKey.removeIf(p->p.get().equals(meta.Name));
        columnMetadata.remove(meta);
    }

    public void addColumns(final Collection<ColumnMetadata> meta) {
       /* PrimaryKey.set(meta.stream()
                .filter(column -> column.IsPrimaryKey)
                .map(column -> column.Name)
                .findFirst()
                .orElse(PrimaryKey.get())); */
        PrimaryKey.addAll(meta.stream()
                .filter(column -> column.IsPrimaryKey)
                .map(column -> new SimpleStringProperty(column.Name))
                .toList());
        columnMetadata.addAll(meta);
    }

    public ObservableList<ColumnMetadata> getColumnMetadata() {
        return columnMetadata;
    }

    public ColumnMetadata getColumnMetadata(final String name) {
        return columnMetadata.stream()
                .filter(column -> column.Name.equals(name))
                .findFirst()
                .orElse(null);
    }

    public void setPrimaryKey(String oldName, String newName) {
        PrimaryKey.stream()
                .filter(p -> p.get().equals(oldName))
                .forEach(p -> p.set(newName));
    }

    public ArrayList<ViewController.View> getViews() {
        return views;
    }

    public void addViews(final Collection<ViewController.View> views) {
        this.views.addAll(views);
    }

    public SimpleStringProperty getNameProperty() {
        return Name;
    }

    public String getName() {
        return Name.get();
    }
}

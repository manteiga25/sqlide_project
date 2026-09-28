package com.example.sqlide.Metadata;

import java.util.ArrayList;
import java.util.List;

public class SchemaMetadata {

    private final List<TableMetadata> tableMetadataList = new ArrayList<>();

    public void addTable(final TableMetadata meta) {
        tableMetadataList.add(meta);
    }

    public List<TableMetadata> getTableMetadataList() {
        return tableMetadataList;
    }

}

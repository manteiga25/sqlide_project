package com.example.sqlide.Report;

import java.util.List;

public class ReportData {

    private final String title;
    private final List<String> columnHeaders;
    private final List<List<String>> dataRows;

    public ReportData(String title, List<String> columnHeaders, List<List<String>> dataRows) {
        this.title = title;
        this.columnHeaders = columnHeaders;
        this.dataRows = dataRows;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getColumnHeaders() {
        return columnHeaders;
    }

    public List<List<String>> getDataRows() {
        return dataRows;
    }
}

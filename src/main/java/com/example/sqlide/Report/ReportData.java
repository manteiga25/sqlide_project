package com.example.sqlide.Report;

import java.util.List;

public class ReportData {

    private final String title;
    private final List<String> columnHeaders;
    private final List<List<String>> dataRows;
    private final ReportStyleConfig reportStyleConfig;

    public ReportData(String title, List<String> columnHeaders, List<List<String>> dataRows, ReportStyleConfig reportStyleConfig) {
        this.title = title;
        this.columnHeaders = columnHeaders;
        this.dataRows = dataRows;
        this.reportStyleConfig = reportStyleConfig;
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

    public ReportStyleConfig getReportStyleConfig() {
        return reportStyleConfig;
    }
}

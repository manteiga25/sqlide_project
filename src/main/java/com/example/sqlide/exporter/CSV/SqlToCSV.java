package com.example.sqlide.exporter.CSV;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.util.ArrayList;

public class SqlToCSV {

    private FileWriter CSVFile;

    private final CSVFormat[] CSVList = {
            CSVFormat.DEFAULT,
            CSVFormat.EXCEL,
            CSVFormat.INFORMIX_UNLOAD,
            CSVFormat.INFORMIX_UNLOAD_CSV,
            CSVFormat.MONGODB_CSV,
            CSVFormat.MONGODB_TSV,
            CSVFormat.MYSQL,
            CSVFormat.ORACLE,
            CSVFormat.POSTGRESQL_CSV,
            CSVFormat.POSTGRESQL_TEXT,
            CSVFormat.RFC4180,
            CSVFormat.TDF
    };

    private CSVPrinter csvPrinter;

    public void createCSV(final String path, final int format) throws IOException {
            CSVFile = new FileWriter(path);
            csvPrinter = new CSVPrinter(CSVFile, CSVList[format]);
    }

    public void createCSVHeader(final ArrayList<String> ColumnsHeader) throws IOException {
        csvPrinter.printRecord(ColumnsHeader);
    }

    public void writeCSVData(final ArrayList<ArrayList<Object>> data) throws IOException {
            csvPrinter.printRecords(data);
    }

    public void SaveAndClose() throws IOException {
            CSVFile.flush();
            csvPrinter.close();
            CSVFile.close();
    }

}

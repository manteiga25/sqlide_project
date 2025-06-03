package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.DataBase;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface FileImporter {

    /**
     * Opens and validates the specified import file.
     * This method can also be used to perform initial parsing or setup.
     *
     * @param file The file to be imported.
     * @throws java.io.IOException If the file is not found, not readable, or an I/O error occurs.
     * @throws IllegalArgumentException If the file format is not supported or invalid.
     */
    void openFile(File file) throws java.io.IOException, IllegalArgumentException;

    /**
     * (Optional) Generates a preview of the data from the file.
     * This could be the first few rows of a specific table.
     *
     * @param file The file to preview.
     * @param tableName The name of the table/sheet to preview (if applicable).
     * @return A list of maps, where each map represents a row (column name -> value).
     * @throws java.io.IOException If an I/O error occurs.
     */
    List<Map<String, String>> previewData(File file, String tableName) throws java.io.IOException;

    /**
     * Detects and returns a list of table names or data sections within the import file.
     * For Excel, these would be sheet names. For JSON/XML, these could be top-level keys or specific element names.
     * For CSV, this might be the filename itself or a predefined name if the CSV represents a single table.
     *
     * @param file The file to analyze.
     * @return A list of detected table names.
     * @throws java.io.IOException If an I/O error occurs.
     */
    List<String> getDetectedTableNames(File file) throws java.io.IOException;

    /**
     * Retrieves the column headers for a specific table/section within the import file.
     *
     * @param file The file to analyze.
     * @param tableName The name of the table/sheet from which to get headers.
     * @return A list of column header strings.
     * @throws java.io.IOException If an I/O error occurs.
     * @throws IllegalArgumentException If the tableName is not found in the file.
     */
    List<String> getColumnHeaders(File file, String tableName) throws java.io.IOException, IllegalArgumentException;

    /**
     * Imports data from a specific table/section in the file into the target database table.
     *
     * @param file The file to import data from.
     * @param sourceTableName The name of the table/sheet in the file to import.
     * @param db The database instance to import data into.
     * @param targetTableName The name of the table in the database where data will be inserted.
     *                        This table might be created or data appended/merged based on user choice.
     * @param createNewTable If true, a new table should be created. Otherwise, data is imported into an existing table.
     * @param columnMapping A map where keys are source column names and values are target column names.
     * @return A summary message of the import operation (e.g., number of rows imported).
     * @throws java.io.IOException If an I/O error occurs.
     * @throws IllegalArgumentException If sourceTableName or targetTableName is invalid, or if column mapping is problematic.
     * @throws java.sql.SQLException If a database error occurs during import.
     */
    String importData(File file, String sourceTableName, DataBase db, String targetTableName, boolean createNewTable, Map<String, String> columnMapping) throws java.io.IOException, IllegalArgumentException, java.sql.SQLException;

    /**
     * Gets the current progress of the import operation (e.g., percentage complete).
     * This is useful for long-running imports.
     *
     * @return A double value between 0.0 and 1.0 representing the progress, or -1 if progress is not determinate.
     */
    double getImportProgress();

    /**
     * Retrieves a list of errors or warnings encountered during the last import operation.
     *
     * @return A list of error/warning messages.
     */
    List<String> getErrors();

}

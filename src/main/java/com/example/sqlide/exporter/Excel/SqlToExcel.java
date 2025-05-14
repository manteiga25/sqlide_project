package com.example.sqlide.exporter.Excel;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SqlToExcel {

    private String MsgException = "";
    private FileOutputStream out;
    private SXSSFWorkbook workbook;
    private Sheet currentSheet;
    private int rowNum = 0;
    private int sheetPart = 0;
    private ArrayList<String> columns; // Armazenar cabeçalhos para replicar

    public void createFile(String path) throws IOException {
        out = new FileOutputStream(path);
    }

    public void createWorkbook(final int buffer) {
        workbook = new SXSSFWorkbook(buffer); // Mantém 100 linhas em memória
    }

    public void createSheet(String sheetName, ArrayList<String> columns) {
        sheetPart = 0;
        this.columns = columns;
        createNewSheet(sheetName);
        createHeaders(); // Criar cabeçalhos na nova sheet
    }

    private void createNewSheet(String baseName) {
        currentSheet = workbook.createSheet(baseName + (sheetPart > 0 ? "_" + sheetPart : ""));
        sheetPart++;
        rowNum = 0; // Reiniciar contador de linhas
    }

    private void createHeaders() {
        Row headerRow = currentSheet.createRow(rowNum++);
        int cellNum = 0;
        for (String column : columns) {
            headerRow.createCell(cellNum++).setCellValue(column);
        }
    }

    public void writeData(ArrayList<ArrayList<Object>> data) {
        if (data == null) return;

        for (ArrayList<Object> rowData : data) {
            // Verificar limite máximo de linhas (1.048.576)
            if (rowNum >= 1048576) {
                createNewSheet(currentSheet.getSheetName());
                createHeaders(); // Adicionar cabeçalhos na nova sheet
            }

            Row row = currentSheet.createRow(rowNum++);
            int cellNum = 0;
            for (Object value : rowData) {
                System.out.println(value);
                writeCell(row.createCell(cellNum++), value);
            }
        }
    }

    private void writeCell(Cell cell, Object Value) {
        switch (Value) {
            case null -> cell.setBlank();
            case Number value -> cell.setCellValue(value.doubleValue());
            case Boolean value -> cell.setCellValue(value);
            case java.sql.Date value -> {
                final Calendar val = Calendar.getInstance();
                val.setTime(value);
                cell.setCellValue(val);
            }
            case Date value -> cell.setCellValue(value);
            case LocalDate value -> cell.setCellValue(value);
            case LocalDateTime value -> cell.setCellValue(value);
            default -> {
                try {
                    cell.setCellValue(Value.toString()); // string or other
                } catch (Exception e) {
                    System.out.println("null\n" + e.getMessage());
                }
            }
        }

    }

    public void saveAndClose() {
        try {
            if (workbook != null) {
                workbook.write(out); // Escreve os dados no stream
                workbook.close(); // Fecha o workbook primeiro
            }
            if (out != null) {
                out.flush(); // Libera qualquer buffer pendente
                out.close(); // Fecha o stream após o workbook
            }
        } catch (IOException e) {
            MsgException = "Erro ao salvar: " + e.getMessage();
        } finally {
            if (workbook != null) {
                workbook.dispose(); // Limpa recursos temporários
            }
        }
    }

    public String GetException() {
        final String tmp = MsgException;
        MsgException = "";
        return tmp;
    }
}


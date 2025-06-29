package com.example.sqlide.exporter.XML;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SqlToXml {

    private XMLStreamWriter writer;
    private OutputStream outputStream;
    private String currentChild;

    private ArrayList<String> columns;

    private boolean rowid = false;

    public void createXML(String dbName, String path) throws Exception {
        outputStream = new FileOutputStream(path + ".xml");
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        writer = factory.createXMLStreamWriter(outputStream, "utf-8");

        // Escrever o cabeçalho e abrir a raiz
        writer.writeStartDocument("utf-8", "1.0");
        writer.writeCharacters("\n");
        writer.writeStartElement(dbName);
    }

    public void createTableChild(String child) {
        this.currentChild = child;
    }

    public void setStructure(final ArrayList<String> columns) {
        this.columns = columns;
    }

    public void setRow(final boolean rowid) {
        this.rowid = rowid;
    }

    public void addData(ArrayList<ArrayList<Object>> data) throws Exception {
        if (data == null) return;

        if (rowid) {
            addDataRow(data);
        } else {
            addDataNoRow(data);
        }

    }

    private void addDataNoRow(ArrayList<ArrayList<Object>> data) throws Exception {
        if (data == null) return;

        for (List<Object> row : data) {
            writer.writeCharacters("\n\t");
            writer.writeStartElement(currentChild);

            // Start from 1 if ROWID skipped, else 0
            int startIndex = rowid ? 1 : 0;
            for (int i = startIndex; i < columns.size(); i++) {
                String colName = columns.get(i);
                Object value = (i < row.size()) ? row.get(i) : null;

                writer.writeCharacters("\n\t\t");
                writer.writeStartElement(colName);
                writer.writeCharacters(value != null ? value.toString() : "null");
                writer.writeEndElement();
            }
            writer.writeCharacters("\n\t");
            writer.writeEndElement();
        }
    }

    private void addDataRow(ArrayList<ArrayList<Object>> data) throws Exception {
        if (data == null) return;

        for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
            final ArrayList<Object> row = data.get(rowIndex);
            writer.writeCharacters("\n\t");
            writer.writeStartElement(currentChild);

            // Handle ROWID attribute if enabled
            if (rowid && !row.isEmpty()) {
                writer.writeAttribute("ROWID", String.valueOf(rowIndex));
            }

            // Start from 1 if ROWID skipped, else 0
            int startIndex = rowid ? 1 : 0;
            for (int i = startIndex; i < columns.size(); i++) {
                String colName = columns.get(i);
                Object value = (i < row.size()) ? row.get(i) : null;

                writer.writeCharacters("\n\t\t");
                writer.writeStartElement(colName);
                writer.writeCharacters(value != null ? value.toString() : "null");
                writer.writeEndElement();
            }
            writer.writeCharacters("\n\t");
            writer.writeEndElement();
        }
    }

    public void flushIntermediate() throws Exception {
        if (writer != null) {
            writer.flush(); // Força a escrita no arquivo, mas mantém o stream aberto
        }
    }

    public void close() throws Exception {
        if (writer != null) {
            writer.writeCharacters("\n");
            writer.writeEndElement(); // Fecha a raiz
            writer.writeEndDocument();
            flushIntermediate();
            writer.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
    }

    public void abort() throws Exception {
        if (writer != null) {
            writer.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
    }

}

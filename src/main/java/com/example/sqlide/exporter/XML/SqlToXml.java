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

    private String buffer = "";

    private boolean rowid = false;

    public void createXML(String dbName, String path, final String charset) throws Exception {
        outputStream = new FileOutputStream(path + ".xml");
        XMLOutputFactory factory = XMLOutputFactory.newInstance();;
        writer = factory.createXMLStreamWriter(outputStream, charset);

        // Escrever o cabeçalho e abrir a raiz
        writer.writeStartDocument(charset, "1.0");
        writer.writeCharacters("\n");
        writer.writeStartElement(dbName);
    }

    public void createTableChild(String child) {
        this.currentChild = child;
    }

    public void setStructure(final ArrayList<String> columns) {
        this.columns = columns;
    }

    public void addData3(ArrayList<ArrayList<Object>> data) throws Exception {
        if (data != null) {
            for (ArrayList<Object> row : data) {
               // writer.writeAttribute("ROWID", String.valueOf(row.hashCode())); // Exemplo de ROWID

                for (int i = 0; i < columns.size(); i++) {
                    buffer += "\n\t\t";//writer.writeCharacters("\n\t\t");
                    String columnName = columns.get(i);
                    Object value = row.get(i);
                    buffer += "<" + columnName + ">"; //writer.writeStartElement(columnName);
                    buffer += value != null ? value.toString() : "null </" + columnName + ">"; //writer.writeCharacters(value != null ? value.toString() : "null");
                    //writer.writeEndElement(); // Fecha a coluna
                }

                buffer += "\n\t</" + currentChild + ">\n\t";//writer.writeEndElement(); // Fecha a linha (currentChild)
                //writer.writeCharacters("\n\t");
            }
            writer.writeCharacters(buffer);
            buffer = "";
        }
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
        if (data != null) {
            for (ArrayList<Object> row : data) {
                writer.writeCharacters("\n\t");
                writer.writeStartElement(currentChild);

                for (int i = 0; i < columns.size(); i++) {
                    writer.writeCharacters("\n\t\t");
                    String columnName = columns.get(i);
                    Object value = row.get(i);
                    writer.writeStartElement(columnName);
                    writer.writeCharacters(value != null ? value.toString() : "null");
                    writer.writeEndElement(); // Fecha a coluna
                }

                writer.writeEndElement(); // Fecha a linha (currentChild)
            }
         //   writer.writeCharacters(buffer);
            buffer = "";
        }
    }

    private void addDataRow(ArrayList<ArrayList<Object>> data) throws Exception {
        if (data != null) {
            for (ArrayList<Object> row : data) {
                writer.writeCharacters("\n\t");
                writer.writeStartElement(currentChild);
                    writer.writeAttribute("ROWID", row.getFirst().toString()); // Exemplo de ROWID

                for (int i = 1; i < columns.size(); i++) {
                    writer.writeCharacters("\n\t\t");
                    String columnName = columns.get(i);
                    Object value = row.get(i);
                    writer.writeStartElement(columnName);
                    writer.writeCharacters(value != null ? value.toString() : "null");
                    writer.writeEndElement(); // Fecha a coluna
                }

                writer.writeEndElement(); // Fecha a linha (currentChild)
            }
            //   writer.writeCharacters(buffer);
            buffer = "";
        }
    }

    public void addData2(ArrayList<ArrayList<Object>> data, ArrayList<String> columns) throws Exception {
        if (data == null || columns == null) return;

        for (ArrayList<Object> row : data) {
            writer.writeCharacters("\t");
            writer.writeStartElement(currentChild);

            for (int i = 0; i < columns.size(); i++) {
                String columnName = columns.get(i);
                Object value = row.get(i);

                writer.writeStartElement(columnName);
                if (value != null) {
                    writer.writeCharacters(value.toString());
                }
                writer.writeEndElement();
            }

            writer.writeEndElement();
            writer.writeCharacters("\n");

            // Flush periódico para liberar memória
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

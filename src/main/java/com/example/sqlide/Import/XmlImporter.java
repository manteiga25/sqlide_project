package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import javafx.beans.property.DoubleProperty;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class XmlImporter implements FileImporter {

    private List<String> errors = new ArrayList<>();
    private double progress = 0.0;
    // Store detected table names (root child elements) after first parse
    private List<String> detectedTableNamesCache = null;
    private File lastOpenedFile = null;


    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        this.errors.clear();
        this.progress = 0.0;

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        if (!file.getName().toLowerCase().endsWith(".xml")) {
            throw new IllegalArgumentException("Invalid file format. Only XML files are supported.");
        }

        // If it's a new file, clear the cache
        if (this.lastOpenedFile == null || !this.lastOpenedFile.equals(file)) {
            this.detectedTableNamesCache = null;
            this.lastOpenedFile = file;
        }

        // Basic validation by trying to create an XMLInputFactory and an XMLEventReader
        // This doesn't parse the whole doc but checks if basics are OK.
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Configure factory to be safe against XXE
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            try (FileReader fileReader = new FileReader(file)) {
                XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
                // Check if there's at least one start element (the root)
                boolean rootFound = false;
                while(eventReader.hasNext()) {
                    XMLEvent event = eventReader.nextEvent();
                    if (event.isStartElement()) {
                        rootFound = true;
                        break;
                    }
                }
                if (!rootFound) {
                    throw new IllegalArgumentException("XML file appears to be empty or lacks a root element.");
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("Failed to initialize XML parser or read basic structure: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, String>> previewData(File file, String tableName) throws IOException {
        openFile(file); // Validate
        List<Map<String, String>> previewRows = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
            boolean inTable = false;
            int rowCount = 0;

            while (eventReader.hasNext() && rowCount < 5) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    String elementName = startElement.getName().getLocalPart();
                    if (elementName.equals(tableName)) {
                        inTable = true;
                        Map<String, String> rowMap = new HashMap<>();
                        // Check for ROWID attribute as per exporter
                        Iterator<Attribute> attributes = startElement.getAttributes();
                        while(attributes.hasNext()){
                            Attribute attribute = attributes.next();
                            if(attribute.getName().getLocalPart().equalsIgnoreCase("ROWID")){
                                rowMap.put("ROWID", attribute.getValue());
                            }
                        }

                        // Now read child elements for columns
                        while(eventReader.hasNext()){
                            XMLEvent innerEvent = eventReader.nextEvent();
                            if(innerEvent.isStartElement()){
                                StartElement columnElement = innerEvent.asStartElement();
                                String columnName = columnElement.getName().getLocalPart();
                                // Get element text
                                innerEvent = eventReader.nextEvent(); // Should be Characters event
                                String columnValue = "";
                                if(innerEvent.isCharacters()){
                                    columnValue = innerEvent.asCharacters().getData();
                                }
                                rowMap.put(columnName, columnValue);
                                // Consume corresponding EndElement for column
                                innerEvent = eventReader.nextEvent();
                                if (!innerEvent.isEndElement() || !innerEvent.asEndElement().getName().getLocalPart().equals(columnName)) {
                                    // Unexpected structure
                                    break;
                                }
                            } else if (innerEvent.isEndElement() && innerEvent.asEndElement().getName().getLocalPart().equals(tableName)){
                                // Reached end of this table record
                                break;
                            }
                        }
                        if (!rowMap.isEmpty()) {
                            previewRows.add(rowMap);
                            rowCount++;
                        }
                    }
                } else if (event.isEndElement()) {
                    if (event.asEndElement().getName().getLocalPart().equals(tableName)) {
                        inTable = false;
                    }
                }
            }
        } catch (XMLStreamException e) {
            errors.add("Error previewing XML data: " + e.getMessage());
            throw new IOException("Error previewing XML data from table '" + tableName + "': " + e.getMessage(), e);
        }
        return previewRows;
    }

    @Override
    public List<String> getDetectedTableNames(File file) throws IOException {
        openFile(file); // Validate
        if (this.detectedTableNamesCache != null && this.lastOpenedFile != null && this.lastOpenedFile.equals(file)) {
            return new ArrayList<>(this.detectedTableNamesCache);
        }

        List<String> tableNames = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
            String rootElementName = null;

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    if (rootElementName == null) {
                        rootElementName = startElement.getName().getLocalPart();
                        // Skip the root element itself as a "table"
                    } else {
                        // These are children of the root, potential table names
                        // SqlToXml creates <dbname><tablename>...</tablename></dbname>
                        // Or for multiple files: <dbname><tablename>...</tablename></dbname> per file.
                        // We are interested in the <tablename> elements.
                        // The exporter's single file output is <dbName><sheetName>...</sheetName><sheetName>...</sheetName></dbName>
                        // So children of the root element are the table names.
                        String elementName = startElement.getName().getLocalPart();
                        if (!tableNames.contains(elementName)) {
                            // Check if this element actually contains data by peeking ahead for non-empty content or child elements
                            if (elementContainsData(eventReader, elementName)) {
                                tableNames.add(elementName);
                            } else {
                                // Skip elements that are empty or just containers without actual row data structure
                                consumeElement(eventReader, elementName); // consume the rest of this empty/structural element
                            }
                        } else {
                            consumeElement(eventReader, elementName); // Already seen, skip
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            errors.add("Error reading table names from XML: " + e.getMessage());
            throw new IOException("Error reading table names from XML: " + e.getMessage(), e);
        }
        this.detectedTableNamesCache = new ArrayList<>(tableNames);
        return tableNames;
    }

    // Helper to consume an element and its children if we decide to skip it
    private void consumeElement(XMLEventReader eventReader, String elementName) throws XMLStreamException {
        int depth = 1;
        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();
            if (event.isStartElement() && event.asStartElement().getName().getLocalPart().equals(elementName)) {
                depth++;
            } else if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals(elementName)) {
                depth--;
                if (depth == 0) return;
            }
        }
    }

    // Helper to check if an element likely contains data records vs being a simple value or empty
    private boolean elementContainsData(XMLEventReader eventReader, String parentElementName) throws XMLStreamException {
        // This is tricky. We're looking for elements that have child elements,
        // rather than just character data.
        // Let's peek at the next few events.
        // The SqlToXml exporter for a table typically has: <TableName><Column1>data</Column1>...</TableName>
        // Or <TableName ROWID="x"><Column1>data</Column1>...</TableName>
        // So, a <TableName> should contain other StartElements (columns).
        boolean foundChildElement = false;
        while(eventReader.hasNext()){
            XMLEvent event = eventReader.peek(); // Peek, don't consume yet from main loop
            if(event.isStartElement()){
                // If the child element is not the same as parent (e.g. not <Table><Table>), it's probably data
                if(!event.asStartElement().getName().getLocalPart().equals(parentElementName)){
                    foundChildElement = true;
                }
                break;
            } else if (event.isCharacters() && !event.asCharacters().isWhiteSpace()){
                // Contains text directly, so not a container of rows in the way we expect for a table
                break;
            } else if (event.isEndElement() && event.asEndElement().getName().getLocalPart().equals(parentElementName)){
                // End of the parent element, no children found
                break;
            }
            eventReader.nextEvent(); // Consume the peeked event if it wasn't what we were looking for or breaking on
        }
        return foundChildElement;
    }


    @Override
    public List<String> getColumnHeaders(File file, String tableName) throws IOException, IllegalArgumentException {
        openFile(file); // Validate
        List<String> headers = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
            boolean inTable = false;
            boolean firstRecordInTableFound = false;

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    String elementName = startElement.getName().getLocalPart();

                    if (elementName.equals(tableName)) {
                        inTable = true;
                        if (!firstRecordInTableFound) {
                            // Check for ROWID attribute as per exporter
                            Iterator<Attribute> attributes = startElement.getAttributes();
                            boolean rowIdFound = false;
                            while(attributes.hasNext()){
                                Attribute attribute = attributes.next();
                                if(attribute.getName().getLocalPart().equalsIgnoreCase("ROWID")){
                                    headers.add("ROWID"); // Add ROWID as first header if present
                                    rowIdFound = true;
                                    break;
                                }
                            }
                            // Now find first actual child element for column names
                            while(eventReader.hasNext()){
                                XMLEvent columnEvent = eventReader.peek();
                                if(columnEvent.isStartElement()){
                                    headers.add(columnEvent.asStartElement().getName().getLocalPart());
                                    // Consume this column start element and its content to find next sibling
                                    eventReader.nextEvent(); // consume column start
                                    XMLEvent content = eventReader.peek();
                                    if (content.isCharacters()) eventReader.nextEvent(); // consume content
                                    XMLEvent end_col = eventReader.peek();
                                    if (end_col.isEndElement()) eventReader.nextEvent(); // consume column end
                                } else if (columnEvent.isEndElement() && columnEvent.asEndElement().getName().getLocalPart().equals(tableName)){
                                    // Reached end of this record before finding any column elements
                                    break;
                                } else {
                                    eventReader.nextEvent(); // Consume other events (whitespace etc)
                                }
                                if (columnEvent.isEndElement() && columnEvent.asEndElement().getName().getLocalPart().equals(tableName)) break; // Safety break
                            }
                            firstRecordInTableFound = true;
                            break; // Headers found from the first record
                        }
                    }
                }
                if (firstRecordInTableFound) break;
            }
            if (!firstRecordInTableFound && !getDetectedTableNames(file).contains(tableName)) {
                throw new IllegalArgumentException("Table '" + tableName + "' not found or is empty in the XML file.");
            }

        } catch (XMLStreamException e) {
            errors.add("Error reading XML headers for table '" + tableName + "': " + e.getMessage());
            throw new IOException("Error reading XML headers for table '" + tableName + "': " + e.getMessage(), e);
        }
        return headers;
    }


    @Override
    public String importData(File file, String sourceTableName, DatabaseInserterInterface inserter, final int buffer, String targetTableName, boolean createNewTable, Map<String, String> columnMapping) throws IOException, IllegalArgumentException, SQLException {
        openFile(file); // Validate
        errors.clear();
        progress = 0.0;

        // TODO: Implement actual data insertion using StAX parser
        // 1. Locate the start element for sourceTableName.
        // 2. For each child element (representing a row):
        //    - Extract column data (child elements of the row element or attributes).
        //    - Map to target columns.
        //    - Add to batch insert.
        //    - Update progress.

        long totalRowsProcessed = 0;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
            boolean inTable = false;
            while(eventReader.hasNext()){
                XMLEvent event = eventReader.nextEvent();
                if(event.isStartElement()){
                    StartElement startElement = event.asStartElement();
                    if(startElement.getName().getLocalPart().equals(sourceTableName)){
                        inTable = true;
                        totalRowsProcessed++; // Counting each <tableName> instance as a row
                    }
                } else if (event.isEndElement()){
                    if(event.asEndElement().getName().getLocalPart().equals(sourceTableName)){
                        inTable = false;
                    }
                }
            }
        } catch (XMLStreamException e) {
            errors.add("Error during XML import from table '" + sourceTableName + "': " + e.getMessage());
            throw new IOException("Error during XML import from table '" + sourceTableName + "': " + e.getMessage(), e);
        }


        progress = 1.0;
        return String.format("Successfully processed approximately %d row elements for table '%s' in %s into %s (actual import pending implementation).",
                totalRowsProcessed, sourceTableName, file.getName(), targetTableName);
    }

    @Override
    public double getImportProgress() {
        return progress;
    }

    @Override
    public void setImportProprerty(DoubleProperty proprerty) {

    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

}

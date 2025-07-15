package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class XmlImporter implements FileImporter {

    private List<String> errors = new ArrayList<>();
    private DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private File lastOpenedFile = null;
    private List<String> detectedTableElementNamesCache = null; // Cache for detected "table" (row) element names

    private static final String ATTRIBUTE_PREFIX = "@";


    private XMLInputFactory createXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true); // Important for simplifying text handling
        return factory;
    }

    private void ensureFileOpened(File file) throws IOException {
         if (this.lastOpenedFile == null || !this.lastOpenedFile.equals(file) || this.detectedTableElementNamesCache == null) {
            openFile(file); // This will also trigger table name detection if cache is cleared
        }
    }


    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        this.errors.clear();
        this.progress.set(0.0);

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        if (!file.getName().toLowerCase().endsWith(".xml")) {
            throw new IllegalArgumentException("Invalid file format. Only XML files are supported.");
        }

        if (this.lastOpenedFile == null || !this.lastOpenedFile.equals(file)) {
            this.detectedTableElementNamesCache = null; // Clear cache for new file
            this.lastOpenedFile = file;
        }

        // Basic validation: try to read the first start element.
        XMLInputFactory factory = createXmlInputFactory();
        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
            boolean rootFound = false;
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    rootFound = true;
                    break;
                }
            }
            if (!rootFound) {
                throw new IllegalArgumentException("XML file appears to be empty or lacks a root element.");
            }
        } catch (XMLStreamException e) {
            this.lastOpenedFile = null; // Invalidate on error
            this.detectedTableElementNamesCache = null;
            throw new IOException("Failed to initialize XML parser or read basic structure: " + e.getMessage(), e);
        }
        // Trigger table name detection if cache is still null (e.g. first open)
        if(this.detectedTableElementNamesCache == null) {
            detectTableNamesInternal(file);
        }
    }
    private void detectTableNamesInternal(File file) throws IOException {
        List<String> tableNames = new ArrayList<>();
        XMLInputFactory factory = createXmlInputFactory();
        Map<String, Integer> elementCounts = new HashMap<>();
        String rootElementName = null;

        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);
            Stack<String> parentStack = new Stack<>();

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    String currentElementName = startElement.getName().getLocalPart();
                    if (rootElementName == null) {
                        rootElementName = currentElementName;
                    } else {
                        // Consider direct children of root as potential repeating row elements
                        // Or elements that are children of the same parent and repeat
                        if (parentStack.size() == 1 && parentStack.peek().equals(rootElementName)) {
                             elementCounts.put(currentElementName, elementCounts.getOrDefault(currentElementName, 0) + 1);
                        }
                        // More sophisticated detection could analyze deeper structures
                        // For now, focusing on children of root or elements that repeat frequently.
                        // This simple approach considers any element that's not the root as a potential table.
                        // A better heuristic: elements that appear multiple times as direct children of the *same* parent.
                        // The current `SqlToXml` produces `<db><table_name>...</table_name></db>`
                        // So `table_name` is a child of `db` (the root).
                        if(!tableNames.contains(currentElementName)){ // Add if not already, count will help filter later.
                             tableNames.add(currentElementName);
                        }
                    }
                    parentStack.push(currentElementName);
                } else if (event.isEndElement()) {
                    if (!parentStack.isEmpty()) parentStack.pop();
                }
            }
        } catch (XMLStreamException e) {
            errors.add("Error scanning XML for table names: " + e.getMessage());
            throw new IOException("Error scanning XML for table names: " + e.getMessage(), e);
        }
        
        // Refine: only keep names that appear more than once if there are many,
        // or if only one distinct child of root, assume it's the repeating element.
        List<String> refinedTableNames = new ArrayList<>();
        if(tableNames.size() == 1 && rootElementName != null && !rootElementName.equals(tableNames.get(0))) {
            // If there's only one type of element under the root, assume it's the row element.
            refinedTableNames.add(tableNames.get(0));
        } else {
            for(String name : tableNames){
                if(elementCounts.getOrDefault(name, 0) > 1){ // Must appear at least twice to be a "table" of rows
                    refinedTableNames.add(name);
                } else if (elementCounts.isEmpty() && tableNames.size() == 1 && !name.equals(rootElementName)) {
                    // Fallback: if no counts (e.g. flat structure) but only one candidate under root.
                    refinedTableNames.add(name);
                }
            }
        }
        if (refinedTableNames.isEmpty() && !tableNames.isEmpty() && !tableNames.get(0).equals(rootElementName)) {
            // If filtering removed everything, but there was one candidate, take it.
            // This handles cases like <root><row>data</row></root> (only one row)
            refinedTableNames.add(tableNames.get(0));
        }


        this.detectedTableElementNamesCache = refinedTableNames;
        if (this.detectedTableElementNamesCache.isEmpty() && rootElementName != null) {
             errors.add("No repeating data row elements found under the root '" + rootElementName + "'. Please ensure XML has a repeating element for rows.");
        }
    }


    @Override
    public List<String> getDetectedTableNames(File file) throws IOException {
        ensureFileOpened(file); // Ensures openFile logic (including cache init) is run
        if (this.detectedTableElementNamesCache == null) {
            // This should ideally not be reached if ensureFileOpened works correctly
            detectTableNamesInternal(file);
        }
        return new ArrayList<>(this.detectedTableElementNamesCache != null ? this.detectedTableElementNamesCache : Collections.emptyList());
    }

    @Override
    public List<String> getColumnHeaders(File file, String rowElementName) throws IOException, IllegalArgumentException {
        ensureFileOpened(file);
        Set<String> headers = new LinkedHashSet<>(); // Use LinkedHashSet to preserve order of appearance
        XMLInputFactory factory = createXmlInputFactory();
        int recordsScanned = 0;
        final int SCAN_LIMIT = 10; // Scan up to 10 records for headers

        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);

            while (eventReader.hasNext() && recordsScanned < SCAN_LIMIT) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    if (startElement.getName().getLocalPart().equals(rowElementName)) {
                        recordsScanned++;
                        // Collect attributes of the row element
                        Iterator<Attribute> attributes = startElement.getAttributes();
                        while (attributes.hasNext()) {
                            Attribute attribute = attributes.next();
                            headers.add(ATTRIBUTE_PREFIX + attribute.getName().getLocalPart());
                        }
                        // Collect child element names
                        while (eventReader.hasNext()) {
                            XMLEvent innerEvent = eventReader.nextEvent();
                            if (innerEvent.isStartElement()) {
                                StartElement childElement = innerEvent.asStartElement();
                                headers.add(childElement.getName().getLocalPart());
                                // Consume this child element to move to the next sibling
                                consumeRestOfElement(eventReader, childElement.getName().getLocalPart());
                            } else if (innerEvent.isEndElement() && innerEvent.asEndElement().getName().getLocalPart().equals(rowElementName)) {
                                break; // End of this row element
                            }
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            errors.add("Error reading XML headers for row element '" + rowElementName + "': " + e.getMessage());
            throw new IOException("Error reading XML headers for row element '" + rowElementName + "': " + e.getMessage(), e);
        }
        if (headers.isEmpty() && getDetectedTableNames(file).contains(rowElementName)) {
             errors.add("Warning: No headers found for row element '" + rowElementName + "'. It might be empty or contain no parseable child elements/attributes.");
        }
        return new ArrayList<>(headers);
    }
    
    private void consumeRestOfElement(XMLEventReader eventReader, String elementName) throws XMLStreamException {
        int depth = 1;
        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();
            if (event.isStartElement()) {
                depth++;
            } else if (event.isEndElement()) {
                depth--;
                if (depth == 0 && event.asEndElement().getName().getLocalPart().equals(elementName)) {
                    return;
                }
            }
        }
    }


    @Override
    public List<Map<String, String>> previewData(File file, String rowElementName) throws IOException {
        ensureFileOpened(file);
        List<Map<String, String>> previewRows = new ArrayList<>();
        XMLInputFactory factory = createXmlInputFactory();
        int rowsProcessed = 0;
        final int PREVIEW_LIMIT = 5;
        
        List<String> unifiedHeaders = getColumnHeaders(file, rowElementName);


        try (FileReader fileReader = new FileReader(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fileReader);

            while (eventReader.hasNext() && rowsProcessed < PREVIEW_LIMIT) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    if (startElement.getName().getLocalPart().equals(rowElementName)) {
                        Map<String, String> rowMap = new LinkedHashMap<>();
                        // Initialize with nulls for all unified headers
                        for(String header : unifiedHeaders) rowMap.put(header, null);

                        // Process attributes
                        Iterator<Attribute> attributes = startElement.getAttributes();
                        while (attributes.hasNext()) {
                            Attribute attribute = attributes.next();
                            String attrName = ATTRIBUTE_PREFIX + attribute.getName().getLocalPart();
                            if(unifiedHeaders.contains(attrName)) { // only if it's a known header
                                rowMap.put(attrName, attribute.getValue());
                            }
                        }

                        // Process child elements
                        while (eventReader.hasNext()) {
                            XMLEvent innerEvent = eventReader.nextEvent();
                            if (innerEvent.isStartElement()) {
                                StartElement childElement = innerEvent.asStartElement();
                                String childName = childElement.getName().getLocalPart();
                                String childValue = "";
                                // Peek next event for Characters
                                XMLEvent dataEvent = eventReader.peek();
                                if (dataEvent.isCharacters()) {
                                    childValue = dataEvent.asCharacters().getData().trim();
                                    eventReader.nextEvent(); // Consume characters event
                                }
                                 // Consume the corresponding EndElement for the child
                                XMLEvent endChildEvent = eventReader.peek();
                                if(endChildEvent.isEndElement() && endChildEvent.asEndElement().getName().getLocalPart().equals(childName)){
                                    eventReader.nextEvent(); // consume end child
                                } else if (endChildEvent.isStartElement()){
                                    // Complex content, for preview, might take first text or mark as complex
                                    // For now, current childValue is fine (might be empty if complex)
                                }


                                if(unifiedHeaders.contains(childName)){
                                     rowMap.put(childName, childValue);
                                }

                            } else if (innerEvent.isEndElement() && innerEvent.asEndElement().getName().getLocalPart().equals(rowElementName)) {
                                break; // End of this row element
                            }
                        }
                        previewRows.add(rowMap);
                        rowsProcessed++;
                    }
                }
            }
        } catch (XMLStreamException e) {
            errors.add("Error previewing XML data for row element '" + rowElementName + "': " + e.getMessage());
            throw new IOException("Error previewing XML data: " + e.getMessage(), e);
        }
        return previewRows;
    }


    @Override
    public String importData(File file, String rowElementName, DatabaseInserterInterface inserter,
                             final int bufferSize, String targetTableName, boolean createNewTable,
                             Map<String, String> columnMapping) throws IOException {
        ensureFileOpened(file);
        this.errors.clear();
        this.progress.set(0.0);

// Validações iniciais
        if (inserter == null) throw new IllegalArgumentException("Database inserter is null.");
        if (targetTableName == null || targetTableName.trim().isEmpty())
            throw new IllegalArgumentException("Target table name must be specified.");

        List<String> sourceXmlHeaders = getColumnHeaders(file, rowElementName);
        if (sourceXmlHeaders.isEmpty()) {
            errors.add("Warning: No column headers found for XML row element '" + rowElementName + "'");
        }

// Construir mapeamento efetivo de colunas
        Map<String, String> effectiveColumnMapping = new HashMap<>();
        List<String> finalTargetDbColumnNames = new ArrayList<>();

        if (columnMapping != null && !columnMapping.isEmpty()) {
            for (Map.Entry<String, String> entry : columnMapping.entrySet()) {
                if (!entry.getValue().equalsIgnoreCase("(Skip Import)") &&
                        !entry.getValue().trim().isEmpty()) {
                    effectiveColumnMapping.put(entry.getValue(), entry.getKey());
                    finalTargetDbColumnNames.add(entry.getValue());
                }
            }
        } else {
            // Mapeamento automático quando não fornecido
            for (String header : sourceXmlHeaders) {
                effectiveColumnMapping.put(header, header);
                finalTargetDbColumnNames.add(header);
            }
        }

        if (finalTargetDbColumnNames.isEmpty()) {
            this.progress.set(1.0);
            return "No valid columns selected for import. Operation aborted.";
        }

// Contagem total de registros para progresso

// Processamento principal
        long recordsProcessed = 0;
        ArrayList<HashMap<String, String>> batchData = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();

        try (FileInputStream fis = new FileInputStream(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fis);
            long totalRecords = countXmlRecords(file, rowElementName);
            final double progressIncrement = 1.0 / totalRecords;

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();

                if (event.isStartElement() &&
                        event.asStartElement().getName().getLocalPart().equals(rowElementName)) {

                    Map<String, String> currentRecord = new HashMap<>();
                    StartElement startElement = event.asStartElement();

                    // Processar atributos
                    Iterator<Attribute> attributes = startElement.getAttributes();
                    while (attributes.hasNext()) {
                        Attribute attr = attributes.next();
                        String attrName = "@" + attr.getName().getLocalPart();
                        currentRecord.put(attrName, attr.getValue());
                    }

                    // Processar elementos filhos
                    while (eventReader.hasNext()) {
                        event = eventReader.nextEvent();

                        if (event.isStartElement()) {
                            StartElement childElement = event.asStartElement();
                            String childName = childElement.getName().getLocalPart();
                            String childValue = getElementText(eventReader, childName);
                            currentRecord.put(childName, childValue);
                        }

                        if (event.isEndElement() &&
                                event.asEndElement().getName().getLocalPart().equals(rowElementName)) {
                            break;
                        }
                    }

                    // Mapear para colunas de destino
                    HashMap<String, String> dbRecord = new HashMap<>();
                    for (Map.Entry<String, String> mapping : effectiveColumnMapping.entrySet()) {
                        String sourceKey = mapping.getKey();
                        String targetKey = mapping.getValue();
                        dbRecord.put(targetKey, currentRecord.getOrDefault(sourceKey, null));
                    }

                    batchData.add(dbRecord);
                    recordsProcessed++;

                    // Inserir em lotes
                    if (batchData.size() >= bufferSize) {
                        if (!inserter.insertData(targetTableName, batchData)) {
                            errors.add("Batch insert failed: " + inserter.getException());
                        }
                        batchData.clear();
                    }

                    // Atualizar progresso
                    progress.set(progress.get() + progressIncrement);
                }
            }

            // Inserir dados restantes
            if (!batchData.isEmpty()) {
                if (!inserter.insertData(targetTableName, batchData)) {
                    errors.add("Final batch insert failed: " + inserter.getException());
                }
            }

        } catch (Exception e) {
            errors.add("XML import error: " + e.getMessage());
            throw new IOException("XML processing failed", e);
        }

        progress.set(1.0);
        return String.format("Successfully imported %d records from %s", recordsProcessed, file.getName());
    }

    private long countXmlRecords(File file, String rowElementName) throws Exception {
        long count = 0;
        XMLInputFactory factory = XMLInputFactory.newInstance();

        try (FileInputStream fis = new FileInputStream(file)) {
            XMLEventReader eventReader = factory.createXMLEventReader(fis);

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isStartElement() &&
                        event.asStartElement().getName().getLocalPart().equals(rowElementName)) {
                    count++;
                }
            }
        }
        return Math.max(count, 1); // Evitar divisão por zero
    }

    private String getElementText(XMLEventReader eventReader, String elementName) throws XMLStreamException {
        StringBuilder content = new StringBuilder();
        int depth = 0;

        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();

            if (event.isStartElement() &&
                    event.asStartElement().getName().getLocalPart().equals(elementName)) {
                depth++;
            }

            if (event.isCharacters() && depth == 0) {
                content.append(event.asCharacters().getData());
            }

            if (event.isEndElement()) {
                if (event.asEndElement().getName().getLocalPart().equals(elementName)) {
                    if (depth == 0) break;
                    depth--;
                }
            }
        }

        return content.toString().trim();
    }

    @Override
    public double getImportProgress() {
        return progress.get();
    }

    @Override
    public void setImportProprerty(final DoubleProperty property) {
        if (property != null) {
            this.progress.unbind();
            property.bind(this.progress);
        }
    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}

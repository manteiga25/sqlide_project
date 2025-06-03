package com.example.sqlide.Report;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfPreviewService {

    // Helper method to get PDType1Font (can be similar to the one in ReportService)
    private PDType1Font getPdFont(String fontFamilyName, Standard14Fonts.FontName defaultFont) {
        if (fontFamilyName != null && !fontFamilyName.isEmpty()) {
            try {
                Standard14Fonts.FontName fontNameEnum = Standard14Fonts.FontName.valueOf(fontFamilyName.toUpperCase().replace("-", "_"));
                return new PDType1Font(fontNameEnum);
            } catch (IllegalArgumentException e) {
                System.err.println("Preview Warning: Font " + fontFamilyName + " not found. Using default.");
            }
        }
        return new PDType1Font(defaultFont);
    }

    public WritableImage generatePreviewImage(ReportData reportData, float previewWidth, float previewHeight) {
        ReportStyleConfig styleConfig = reportData.getReportStyleConfig();
        if (styleConfig == null) {
            // Fallback to default style config if not provided, though it should always be there
            styleConfig = new ReportStyleConfig();
        }

        try (PDDocument document = new PDDocument()) {
            // Use PDRectangle to define page size based on previewWidth and previewHeight
            // Ensure width and height are positive
            if (previewWidth <=0) previewWidth = 300; // Default fallback width
            if (previewHeight <=0) previewHeight = 400; // Default fallback height

            PDRectangle pageSize = new PDRectangle(previewWidth, previewHeight);
            PDPage page = new PDPage(pageSize);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // --- Start Drawing Logic (Simplified from ReportService) ---
                float currentY = page.getMediaBox().getHeight() - styleConfig.getPageMargin();
                float currentX = styleConfig.getPageMargin();
                float tableWidth = page.getMediaBox().getWidth() - (2 * styleConfig.getPageMargin());
                if (tableWidth <= 0) tableWidth = page.getMediaBox().getWidth() - 20; // Fallback if margins too large

                // 1. Draw Title (Sample)
                String titleText = reportData.getTitle() != null && !reportData.getTitle().isEmpty() ? reportData.getTitle() : "Sample Report Title";
                PDType1Font titleFont = getPdFont(styleConfig.getTitleFontFamily(), Standard14Fonts.FontName.HELVETICA_BOLD);
                float titleFontSize = styleConfig.getTitleFontSize();
                contentStream.setFont(titleFont, titleFontSize);
                if (styleConfig.getTitleColor() != null) {
                    contentStream.setNonStrokingColor(styleConfig.getTitleColor());
                } else {
                    contentStream.setNonStrokingColor(Color.BLACK);
                }
                contentStream.beginText();
                contentStream.newLineAtOffset(currentX, currentY);
                contentStream.showText(titleText);
                contentStream.endText();
                currentY -= titleFontSize * 1.5f; // Dynamic leading

                // 2. Draw Headers (Sample)
                List<String> sampleHeaders = reportData.getColumnHeaders() != null && !reportData.getColumnHeaders().isEmpty() ?
                        new ArrayList<>(reportData.getColumnHeaders()) :
                        List.of("Header A", "Header B", "Header C");
                if (sampleHeaders.isEmpty()) { // Ensure there's at least one header for calculation if needed
                    sampleHeaders = List.of("Header");
                }


                if (!sampleHeaders.isEmpty()) {
                    PDType1Font headerFont = getPdFont(styleConfig.getHeaderFontFamily(), Standard14Fonts.FontName.HELVETICA_BOLD);
                    float headerFontSize = styleConfig.getHeaderFontSize();
                    Color headerTextColor = styleConfig.getHeaderTextColor() != null ? styleConfig.getHeaderTextColor() : Color.BLACK;
                    Color headerBgColor = styleConfig.getHeaderBackgroundColor(); // Can be null

                    float columnWidth = tableWidth / sampleHeaders.size();
                    float headerRowY = currentY - headerFontSize; // Y for the bottom of the text
                    float headerRowHeight = headerFontSize * 1.2f + styleConfig.getCellPadding();


                    // Draw header background if specified
                    if (headerBgColor != null) {
                        contentStream.setNonStrokingColor(headerBgColor);
                        contentStream.addRect(currentX, currentY - headerFontSize - (styleConfig.getCellPadding()/2), tableWidth, headerRowHeight);
                        contentStream.fill();
                    }

                    // Draw header text
                    contentStream.setFont(headerFont, headerFontSize);
                    contentStream.setNonStrokingColor(headerTextColor);

                    float textY = currentY - styleConfig.getCellPadding(); // Text baseline from top of cell

                    for (int i = 0; i < sampleHeaders.size(); i++) {
                        contentStream.beginText();
                        // Center text in column (simplified)
                        String headerText = sampleHeaders.get(i);
                        float textWidth = headerFont.getStringWidth(headerText) / 1000 * headerFontSize;
                        float textX = currentX + (i * columnWidth) + (columnWidth - textWidth) / 2;
                        if (textX < currentX + (i*columnWidth) + styleConfig.getCellPadding()) textX = currentX + (i*columnWidth) + styleConfig.getCellPadding();

                        contentStream.newLineAtOffset(textX , textY);
                        contentStream.showText(headerText);
                        contentStream.endText();
                    }
                    currentY -= headerRowHeight;
                }

                // 3. Draw Data Rows (Sample - 2 rows)
                List<List<String>> sampleDataRows = reportData.getDataRows() != null && !reportData.getDataRows().isEmpty() ?
                        new ArrayList<>(reportData.getDataRows()) :
                        List.of( List.of("Data 1A", "Data 1B", "Data 1C"), List.of("Data 2A", "Data 2B", "Data 2C"));
                if (sampleDataRows.isEmpty() && !sampleHeaders.isEmpty()) { // Add dummy data if headers exist but data doesn't
                    List<String> dummyRow = new ArrayList<>();
                    for (int i=0; i < sampleHeaders.size(); i++) dummyRow.add("Cell " + (i+1));
                    sampleDataRows.add(dummyRow);
                    sampleDataRows.add(dummyRow); // Add two dummy rows
                }


                PDType1Font dataFont = getPdFont(styleConfig.getDataFontFamily(), Standard14Fonts.FontName.HELVETICA);
                float dataFontSize = styleConfig.getDataFontSize();
                Color dataTextColor = styleConfig.getDataTextColor() != null ? styleConfig.getDataTextColor() : Color.BLACK;
                Color altRowBgColor = styleConfig.getAlternatingRowBackgroundColor(); // Can be null

                contentStream.setFont(dataFont, dataFontSize);
                float columnWidth = tableWidth / sampleHeaders.size();

                for (int rowIndex = 0; rowIndex < Math.min(sampleDataRows.size(), 5) ; rowIndex++) { // Limit to 5 rows for preview
                    List<String> row = sampleDataRows.get(rowIndex);
                    float rowHeight = dataFontSize * 1.2f + styleConfig.getCellPadding();
                    float dataRowY = currentY - dataFontSize; // Y for the bottom of text

                    // Alternating row background
                    if (rowIndex % 2 != 0 && altRowBgColor != null) {
                        contentStream.setNonStrokingColor(altRowBgColor);
                        contentStream.addRect(currentX, currentY - dataFontSize - (styleConfig.getCellPadding()/2), tableWidth, rowHeight);
                        contentStream.fill();
                    }

                    // Data text
                    contentStream.setNonStrokingColor(dataTextColor);
                    float textY = currentY - styleConfig.getCellPadding();

                    for (int i = 0; i < row.size(); i++) {
                        if (i >= sampleHeaders.size()) break; // Don't draw more data cells than headers
                        contentStream.beginText();
                        String cellText = row.get(i);
                        float textWidth = dataFont.getStringWidth(cellText) / 1000 * dataFontSize;
                        float textX = currentX + (i * columnWidth) + (columnWidth - textWidth) / 2; // Centered
                        if (textX < currentX + (i*columnWidth) + styleConfig.getCellPadding()) textX = currentX + (i*columnWidth) + styleConfig.getCellPadding();

                        contentStream.newLineAtOffset(textX, textY);
                        contentStream.showText(cellText);
                        contentStream.endText();
                    }
                    currentY -= rowHeight;
                    if (currentY < styleConfig.getPageMargin() + (dataFontSize * 1.2f)) break; // Stop if no more space
                }

                // 4. Draw Page Number (Sample)
                String pageNumText = "Page 1";
                PDType1Font pageNumFont = getPdFont(styleConfig.getPageNumberFontFamily(), Standard14Fonts.FontName.HELVETICA);
                float pageNumFontSize = styleConfig.getPageNumberFontSize();
                Color pageNumColor = styleConfig.getPageNumberColor() != null ? styleConfig.getPageNumberColor() : Color.BLACK;
                float pageNumY = styleConfig.getPageMargin() / 2f;
                float pageNumTextWidth = pageNumFont.getStringWidth(pageNumText) / 1000 * pageNumFontSize;
                float pageNumX = (page.getMediaBox().getWidth() - pageNumTextWidth) / 2;

                contentStream.setFont(pageNumFont, pageNumFontSize);
                contentStream.setNonStrokingColor(pageNumColor);
                contentStream.beginText();
                contentStream.newLineAtOffset(pageNumX, pageNumY);
                contentStream.showText(pageNumText);
                contentStream.endText();
                // --- End Drawing Logic ---
            }

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(0, 96);

            return SwingFXUtils.toFXImage(bufferedImage, null);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }  catch (Exception e) { // Catch any other unexpected errors during PDF generation
            System.err.println("Error generating PDF preview: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
}

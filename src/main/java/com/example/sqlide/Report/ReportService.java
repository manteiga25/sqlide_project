package com.example.sqlide.Report;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public class ReportService {

    private static final float MARGIN = 50;
    private static final float FONT_SIZE_TITLE = 16;
    private static final float FONT_SIZE_HEADER = 10;
    private static final float FONT_SIZE_TEXT = 8;
    private static final float LEADING_TEXT = 10; // Line spacing for text
    private static final float LEADING_HEADER = 12; // Line spacing for header
    private static final float LEADING_TITLE = 18; // Line spacing for title
    private static final float MARGIN_BOTTOM = 70; // For footer space

    private float drawHeaders(PDPageContentStream contentStream, List<String> columnHeaders, float yPosition, float startX, float tableWidth, float columnWidth, PDPage currentPage) throws IOException {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FONT_SIZE_HEADER);
        contentStream.newLineAtOffset(startX, yPosition);
        float currentX = startX;
        for (String header : columnHeaders) {
            String shortHeader = header.length() > 15 ? header.substring(0, 12) + "..." : header;
            if (currentX + columnWidth <= currentPage.getMediaBox().getWidth() - MARGIN) {
                contentStream.showText(shortHeader);
            }
            currentX += columnWidth;
            // Check if there is space for the next header text itself, not just the offset
            if (currentX < startX + tableWidth - columnWidth / 2 && (currentX + columnWidth <= currentPage.getMediaBox().getWidth() - MARGIN)) {
                contentStream.newLineAtOffset(columnWidth, 0);
            } else {
                if (!header.equals(columnHeaders.get(columnHeaders.size() -1))) { // If not the last header, break
                    break;
                }
            }
        }
        contentStream.endText();
        return yPosition - (LEADING_HEADER * 1.5f);
    }

    private void drawPageNumber(PDDocument document, PDPageContentStream contentStream, PDPage currentPage) throws IOException {
        int pageNum = document.getNumberOfPages();
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_TEXT);
        float pageNumX = (currentPage.getMediaBox().getWidth() - 60) / 2; // Example for center
        float pageNumY = MARGIN_BOTTOM / 2 - (FONT_SIZE_TEXT / 2); // Adjust Y to be more centered in margin
        contentStream.newLineAtOffset(pageNumX, pageNumY);
        contentStream.showText("Page " + pageNum);
        contentStream.endText();
    }

    public void generatePdfReport(ReportData reportData, String filePath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage currentPage = new PDPage();
            document.addPage(currentPage);
            PDPageContentStream contentStream = new PDPageContentStream(document, currentPage);

            float yPosition = currentPage.getMediaBox().getHeight() - MARGIN;
            final float startX = MARGIN;
            final float tableWidth = currentPage.getMediaBox().getWidth() - 2 * MARGIN;
            final List<String> columnHeaders = reportData.getColumnHeaders();
            final float columnWidth = columnHeaders.isEmpty() ? tableWidth : tableWidth / columnHeaders.size();

            // Title
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FONT_SIZE_TITLE);
            contentStream.newLineAtOffset(startX, yPosition);
            contentStream.showText(reportData.getTitle() != null ? reportData.getTitle() : "Report");
            contentStream.endText();
            yPosition -= LEADING_TITLE * 4;

            // Initial Headers
            if (columnHeaders != null && !columnHeaders.isEmpty()) {
                yPosition = drawHeaders(contentStream, columnHeaders, yPosition, startX, tableWidth, columnWidth, currentPage);
            }

            int rowCount = 0; // Contador para linhas
            // Data Rows
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_TEXT);
            for (List<String> row : reportData.getDataRows()) {
                rowCount++;
                if (yPosition < MARGIN_BOTTOM + LEADING_TEXT) { // Check if new page is needed
                    drawPageNumber(document, contentStream, currentPage); // Draw page number on current page
                    contentStream.close(); // Close stream for current page

                    currentPage = new PDPage(); // Create new page
                    document.addPage(currentPage);
                    contentStream = new PDPageContentStream(document, currentPage); // New stream for new page
                    yPosition = currentPage.getMediaBox().getHeight() - MARGIN; // Reset Y to top

                    if (columnHeaders != null && !columnHeaders.isEmpty()) { // Redraw headers on new page
                        yPosition = drawHeaders(contentStream, columnHeaders, yPosition, startX, tableWidth, columnWidth, currentPage);
                    }
                    // Reset font for data rows on new page
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE_TEXT);
                }

                if (rowCount % 2 != 0) {
                    contentStream.saveGraphicsState();
                    contentStream.setNonStrokingColor(new Color(240, 240, 240)); // Cinza claro

                    // Desenha um retângulo cobrindo a linha
                    float rectY = yPosition - LEADING_TEXT+8; // Ajuste conforme necessidade
                    contentStream.addRect(startX, rectY, tableWidth, LEADING_TEXT);
                    contentStream.fill();
                    contentStream.restoreGraphicsState();
                }

                contentStream.beginText();
                contentStream.newLineAtOffset(startX, yPosition);
                float currentX = startX;
                for (String cellData : row) {
                    String shortCellData = cellData != null ? (cellData.length() > 20 ? cellData.substring(0, 17) + "..." : cellData) : "";
                    if (currentX + columnWidth <= currentPage.getMediaBox().getWidth() - MARGIN) {
                        contentStream.showText(shortCellData);
                    }
                    currentX += columnWidth;
                    if (currentX < startX + tableWidth - columnWidth/2 && (currentX + columnWidth <= currentPage.getMediaBox().getWidth() - MARGIN)) {
                        contentStream.newLineAtOffset(columnWidth, 0);
                    } else {
                        if (!cellData.equals(row.get(row.size() - 1))) { // If not the last cell, break
                            break;
                        }
                    }
                }
                contentStream.endText();
                yPosition -= LEADING_TEXT;
                contentStream.fillEvenOdd();
            }

            drawPageNumber(document, contentStream, currentPage); // Draw page number on the last page
            contentStream.close(); // Close the final content stream
            document.save(filePath);
        }
    }

}

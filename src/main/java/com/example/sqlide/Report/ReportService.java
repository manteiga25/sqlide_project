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

    // private static final float MARGIN = 50; // Replaced by styleConfig.getPageMargin()
    // private static final float FONT_SIZE_TITLE = 16; // Replaced
    // private static final float FONT_SIZE_HEADER = 10; // Replaced
    // private static final float FONT_SIZE_TEXT = 8; // Replaced
    // private static final float LEADING_TEXT = 10; // Will be dynamic
    // private static final float LEADING_HEADER = 12; // Will be dynamic
    // private static final float LEADING_TITLE = 18; // Will be dynamic
    private static final float DEFAULT_MARGIN_BOTTOM = 70; // For footer space if not covered by overall margin logic

    private PDType1Font getPdFont(String fontFamilyName, Standard14Fonts.FontName defaultFontName) {
        if (fontFamilyName != null && !fontFamilyName.isEmpty()) {
            try {
                // Standard14Fonts.FontName enum might not exactly match string if it contains hyphens.
                // The names in Standard14Fonts.FontName are like HELVETICA, HELVETICA_BOLD.
                // Assuming fontFamilyName matches the enum constant names (e.g., "HELVETICA_BOLD").
                Standard14Fonts.FontName fontNameEnum = Standard14Fonts.FontName.valueOf(
                        fontFamilyName.toUpperCase().replace("-", "_")
                );
                return new PDType1Font(fontNameEnum);
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Font " + fontFamilyName + " not found in Standard14Fonts. Using default " + defaultFontName.name());
            }
        }
        return new PDType1Font(defaultFontName);
    }

    private float drawHeaders(PDPageContentStream contentStream, List<String> columnHeaders, float yPosition,
                              float startX, float tableWidth, float columnWidth, PDPage currentPage,
                              ReportStyleConfig styleConfig) throws IOException {

        PDType1Font headerFont = getPdFont(styleConfig.getHeaderFontFamily(), Standard14Fonts.FontName.HELVETICA_BOLD);
        float headerFontSize = styleConfig.getHeaderFontSize();
        float headerLeading = headerFontSize * 1.2f; // Dynamic leading
        float cellPadding = styleConfig.getCellPadding();

        // Draw header background if color is specified
        if (styleConfig.getHeaderBackgroundColor() != null) {
            contentStream.saveGraphicsState();
            contentStream.setNonStrokingColor(styleConfig.getHeaderBackgroundColor());
            // Rectangle Y is bottom-left, so adjust from yPosition (which is baseline of text)
            contentStream.addRect(startX, yPosition - headerFontSize + (headerLeading - headerFontSize) / 2 - cellPadding / 2, tableWidth, headerLeading + cellPadding);
            contentStream.fill();
            contentStream.restoreGraphicsState();
        }

        contentStream.beginText();
        contentStream.setFont(headerFont, headerFontSize);
        contentStream.setNonStrokingColor(styleConfig.getHeaderTextColor() != null ? styleConfig.getHeaderTextColor() : Color.BLACK);
        contentStream.newLineAtOffset(startX + cellPadding, yPosition);

        float currentX = startX;
        for (String header : columnHeaders) {
            // Simplified shortening, real text wrapping would be complex
            String shortHeader = header.length() > 15 ? header.substring(0, 12) + "..." : header;

            // Check if text fits within the column width minus padding
            float textWidth = headerFont.getStringWidth(shortHeader) / 1000 * headerFontSize;
            if (textWidth > columnWidth - (2 * cellPadding)) {
                // Basic shortening if still too long
                int maxChars = (int) (((columnWidth - (2 * cellPadding)) / headerFontSize) * 1.5) -3; // Estimate
                if (maxChars < 1) maxChars = 1;
                if (shortHeader.length() > maxChars) {
                    shortHeader = shortHeader.substring(0, maxChars) + "...";
                }
            }

            contentStream.showText(shortHeader);

            currentX += columnWidth;
            if (currentX < startX + tableWidth - (columnWidth / 2.0f)) { // Ensure not to offset for the last cell
                contentStream.newLineAtOffset(columnWidth, 0);
            } else if (!header.equals(columnHeaders.getLast())) {
                break;
            }
        }
        contentStream.endText();
        return yPosition - (headerLeading + cellPadding * 2); // Position for next line after header block
    }

    private void drawPageNumber(PDDocument document, PDPageContentStream contentStream, PDPage currentPage,
                                ReportStyleConfig styleConfig) throws IOException {
        int pageNum = document.getNumberOfPages();
        PDType1Font pageNumFont = getPdFont(styleConfig.getPageNumberFontFamily(), Standard14Fonts.FontName.HELVETICA);
        float pageNumFontSize = styleConfig.getPageNumberFontSize();
        Color pageNumColor = styleConfig.getPageNumberColor() != null ? styleConfig.getPageNumberColor() : Color.BLACK;
        float pageMargin = styleConfig.getPageMargin();

        contentStream.beginText();
        contentStream.setFont(pageNumFont, pageNumFontSize);
        contentStream.setNonStrokingColor(pageNumColor);

        String text = "Page " + pageNum;
        float textWidth = pageNumFont.getStringWidth(text) / 1000 * pageNumFontSize;
        float pageNumX = (currentPage.getMediaBox().getWidth() - textWidth) / 2; // Centered
        float pageNumY = pageMargin / 2 - (pageNumFontSize / 2); // Centered in bottom margin

        contentStream.newLineAtOffset(pageNumX, pageNumY);
        contentStream.showText(text);
        contentStream.endText();
    }

    public void generatePdfReport(ReportData reportData, String filePath) throws IOException {
        ReportStyleConfig styleConfig = reportData.getReportStyleConfig();
        if (styleConfig == null) {
            styleConfig = new ReportStyleConfig(); // Use defaults if not provided
        }

        float pageMargin = styleConfig.getPageMargin();
        float cellPadding = styleConfig.getCellPadding();

        try (PDDocument document = new PDDocument()) {
            PDPage currentPage = new PDPage();
            document.addPage(currentPage);
            PDPageContentStream contentStream = new PDPageContentStream(document, currentPage);

            float yPosition = currentPage.getMediaBox().getHeight() - pageMargin;
            final float startX = pageMargin;
            final float tableWidth = currentPage.getMediaBox().getWidth() - 2 * pageMargin;
            final List<String> columnHeaders = reportData.getColumnHeaders();
            final float columnWidth = columnHeaders.isEmpty() ? tableWidth : tableWidth / columnHeaders.size();

            // Title
            PDType1Font titleFont = getPdFont(styleConfig.getTitleFontFamily(), Standard14Fonts.FontName.HELVETICA_BOLD);
            float titleFontSize = styleConfig.getTitleFontSize();
            float titleLeading = titleFontSize * 1.2f;
            Color titleColor = styleConfig.getTitleColor() != null ? styleConfig.getTitleColor() : Color.BLACK;

            contentStream.beginText();
            contentStream.setFont(titleFont, titleFontSize);
            contentStream.setNonStrokingColor(titleColor);
            contentStream.newLineAtOffset(startX, yPosition);
            contentStream.showText(reportData.getTitle() != null ? reportData.getTitle() : "Report");
            contentStream.endText();
            yPosition -= titleLeading * 2; // Space after title

            // Initial Headers
            if (columnHeaders != null && !columnHeaders.isEmpty()) {
                yPosition = drawHeaders(contentStream, columnHeaders, yPosition, startX, tableWidth, columnWidth, currentPage, styleConfig);
            }

            // Data Rows
            PDType1Font dataFont = getPdFont(styleConfig.getDataFontFamily(), Standard14Fonts.FontName.HELVETICA);
            float dataFontSize = styleConfig.getDataFontSize();
            float dataLeading = dataFontSize * 1.2f;
            Color dataTextColor = styleConfig.getDataTextColor() != null ? styleConfig.getDataTextColor() : Color.BLACK;
            Color altRowBgColor = styleConfig.getAlternatingRowBackgroundColor();

            int rowCount = 0;
            for (List<String> row : reportData.getDataRows()) {
                rowCount++;
                // Check if new page is needed (considering bottom margin for page number)
                if (yPosition < pageMargin + dataLeading) {
                    drawPageNumber(document, contentStream, currentPage, styleConfig);
                    contentStream.close();

                    currentPage = new PDPage();
                    document.addPage(currentPage);
                    contentStream = new PDPageContentStream(document, currentPage);
                    yPosition = currentPage.getMediaBox().getHeight() - pageMargin;

                    if (columnHeaders != null && !columnHeaders.isEmpty()) {
                        yPosition = drawHeaders(contentStream, columnHeaders, yPosition, startX, tableWidth, columnWidth, currentPage, styleConfig);
                    }
                }

                // Alternating Row Background
                if (altRowBgColor != null && rowCount % 2 != 0) { // Odd rows get background
                    contentStream.saveGraphicsState();
                    contentStream.setNonStrokingColor(altRowBgColor);
                    // Rectangle Y is bottom-left, so adjust from yPosition (baseline of text)
                    contentStream.addRect(startX, yPosition - dataFontSize + (dataLeading - dataFontSize)/2 - cellPadding / 2, tableWidth, dataLeading + cellPadding);
                    contentStream.fill();
                    contentStream.restoreGraphicsState();
                }

                contentStream.beginText();
                contentStream.setFont(dataFont, dataFontSize);
                contentStream.setNonStrokingColor(dataTextColor);
                contentStream.newLineAtOffset(startX + cellPadding, yPosition);

                float currentX = startX;
                for (String cellData : row) {
                    String shortCellData = cellData != null ? (cellData.length() > 20 ? cellData.substring(0, 17) + "..." : cellData) : "";

                    // Check if text fits
                    float textWidth = dataFont.getStringWidth(shortCellData) / 1000 * dataFontSize;
                    if (textWidth > columnWidth - (2 * cellPadding)) {
                        int maxChars = (int) (((columnWidth - (2 * cellPadding)) / dataFontSize) * 1.5) -3; // Estimate
                        if (maxChars < 1) maxChars = 1;
                        if (shortCellData.length() > maxChars) {
                            shortCellData = shortCellData.substring(0, Math.min(shortCellData.length(), maxChars)) + "...";
                        }
                    }
                    contentStream.showText(shortCellData);

                    currentX += columnWidth;
                    if (currentX < startX + tableWidth - (columnWidth / 2.0f)) {
                        contentStream.newLineAtOffset(columnWidth, 0);
                    } else if (!cellData.equals(row.get(row.size() -1))) {
                        break;
                    }
                }
                contentStream.endText();
                yPosition -= dataLeading; // Move to next line position
            }

            drawPageNumber(document, contentStream, currentPage, styleConfig);
            contentStream.close();
            document.save(filePath);
        }
    }

}

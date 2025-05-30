package com.example.sqlide.Report;

import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.*;

public class ReportStyleConfig {

    // Title Styling
    private String titleFontFamily;
    private float titleFontSize;
    private Color titleColor;

    // Header Styling
    private String headerFontFamily;
    private float headerFontSize;
    private Color headerTextColor;
    private Color headerBackgroundColor;

    // Data Cell Styling
    private String dataFontFamily;
    private float dataFontSize;
    private Color dataTextColor;

    // Row Styling
    private Color alternatingRowBackgroundColor;

    // Page Number Styling
    private String pageNumberFontFamily;
    private float pageNumberFontSize;
    private Color pageNumberColor;

    // Layout
    private float pageMargin;
    private float cellPadding;

    // Constructor with default values
    public ReportStyleConfig() {
        // Title Styling Defaults
        this.titleFontFamily = Standard14Fonts.FontName.HELVETICA_BOLD.toString();
        this.titleFontSize = 16f;
        this.titleColor = Color.BLACK;

        // Header Styling Defaults
        this.headerFontFamily = Standard14Fonts.FontName.HELVETICA_BOLD.toString();
        this.headerFontSize = 10f;
        this.headerTextColor = Color.BLACK;
        this.headerBackgroundColor = Color.WHITE; // Or null for no fill

        // Data Cell Styling Defaults
        this.dataFontFamily = Standard14Fonts.FontName.HELVETICA.toString();
        this.dataFontSize = 8f;
        this.dataTextColor = Color.BLACK;

        // Row Styling Defaults
        this.alternatingRowBackgroundColor = new Color(240, 240, 240); // Light gray

        // Page Number Styling Defaults
        this.pageNumberFontFamily = Standard14Fonts.FontName.HELVETICA.toString();
        this.pageNumberFontSize = 8f;
        this.pageNumberColor = Color.BLACK;

        // Layout Defaults
        this.pageMargin = 50f;
        this.cellPadding = 5f;
    }

    // Getters and Setters

    public String getTitleFontFamily() {
        return titleFontFamily;
    }

    public void setTitleFontFamily(String titleFontFamily) {
        this.titleFontFamily = titleFontFamily;
    }

    public float getTitleFontSize() {
        return titleFontSize;
    }

    public void setTitleFontSize(float titleFontSize) {
        this.titleFontSize = titleFontSize;
    }

    public Color getTitleColor() {
        return titleColor;
    }

    public void setTitleColor(Color titleColor) {
        this.titleColor = titleColor;
    }

    public String getHeaderFontFamily() {
        return headerFontFamily;
    }

    public void setHeaderFontFamily(String headerFontFamily) {
        this.headerFontFamily = headerFontFamily;
    }

    public float getHeaderFontSize() {
        return headerFontSize;
    }

    public void setHeaderFontSize(float headerFontSize) {
        this.headerFontSize = headerFontSize;
    }

    public Color getHeaderTextColor() {
        return headerTextColor;
    }

    public void setHeaderTextColor(Color headerTextColor) {
        this.headerTextColor = headerTextColor;
    }

    public Color getHeaderBackgroundColor() {
        return headerBackgroundColor;
    }

    public void setHeaderBackgroundColor(Color headerBackgroundColor) {
        this.headerBackgroundColor = headerBackgroundColor;
    }

    public String getDataFontFamily() {
        return dataFontFamily;
    }

    public void setDataFontFamily(String dataFontFamily) {
        this.dataFontFamily = dataFontFamily;
    }

    public float getDataFontSize() {
        return dataFontSize;
    }

    public void setDataFontSize(float dataFontSize) {
        this.dataFontSize = dataFontSize;
    }

    public Color getDataTextColor() {
        return dataTextColor;
    }

    public void setDataTextColor(Color dataTextColor) {
        this.dataTextColor = dataTextColor;
    }

    public Color getAlternatingRowBackgroundColor() {
        return alternatingRowBackgroundColor;
    }

    public void setAlternatingRowBackgroundColor(Color alternatingRowBackgroundColor) {
        this.alternatingRowBackgroundColor = alternatingRowBackgroundColor;
    }

    public String getPageNumberFontFamily() {
        return pageNumberFontFamily;
    }

    public void setPageNumberFontFamily(String pageNumberFontFamily) {
        this.pageNumberFontFamily = pageNumberFontFamily;
    }

    public float getPageNumberFontSize() {
        return pageNumberFontSize;
    }

    public void setPageNumberFontSize(float pageNumberFontSize) {
        this.pageNumberFontSize = pageNumberFontSize;
    }

    public Color getPageNumberColor() {
        return pageNumberColor;
    }

    public void setPageNumberColor(Color pageNumberColor) {
        this.pageNumberColor = pageNumberColor;
    }

    public float getPageMargin() {
        return pageMargin;
    }

    public void setPageMargin(float pageMargin) {
        this.pageMargin = pageMargin;
    }

    public float getCellPadding() {
        return cellPadding;
    }

    public void setCellPadding(float cellPadding) {
        this.cellPadding = cellPadding;
    }

}

package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater;

public class CellFormater {

    public boolean format = false;

    public String value;
    // 0 = ==
    // 1 = <
    // 2 = >
    // 3 = <=
    // 4 = >=
    // 5 = !=
    public byte operation;

    private String style;

    public String getStyle() {return style;}

    public void setStyle(final String style) {
        this.style = style;
    }


}

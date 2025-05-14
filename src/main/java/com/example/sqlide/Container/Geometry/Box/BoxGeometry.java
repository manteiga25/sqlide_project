package com.example.sqlide.Container.Geometry.Box;

public class BoxGeometry {
    public double X1, X2, Y1, Y2;

    public BoxGeometry(double X1, double Y1, double X2, double Y2) {
        this.X1 = X1;
        this.Y1 = Y1;
        this.X2 = X2;
        this.Y2 = Y2;
    }

    public String toString() {
        return "(" + X1 + "," + Y1 + "), (" + X2 + "," + Y2 + ")";
    }
}

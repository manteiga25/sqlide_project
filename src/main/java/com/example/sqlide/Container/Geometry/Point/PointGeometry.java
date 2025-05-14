package com.example.sqlide.Container.Geometry.Point;

public class PointGeometry {
    public double X, Y;

    public PointGeometry(double x, double y) {
            this.X = x;
            this.Y = y;
    }

    public String toString() {
        return "(" + X + "," + Y + ")";
    }
}

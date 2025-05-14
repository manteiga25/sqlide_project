package com.example.sqlide.Container.Geometry.Circle;

public class CircleGeometry {

    public double Radius, X, Y;

    public CircleGeometry(double radius, double X, double Y) {
        this.Radius = radius;
        this.X = X;
        this.Y = Y;
    }

    public String toString() {
        return "<(" + X + "," + Y + ")," + Radius + ">";
    }

}

package com.example.sqlide.Container.Geometry;

import com.example.sqlide.Container.Geometry.Box.BoxGeometry;
import com.example.sqlide.Container.Geometry.Circle.CircleGeometry;
import com.example.sqlide.Container.Geometry.Point.PointGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura dos tipos geométricos do PostgreSQL.
 *
 * <p>Os tipos {@code point}, {@code circle}, {@code box}, {@code lseg}, {@code path} e
 * {@code polygon} vêm todos como texto pelo JDBC, com sintaxes parecidas mas diferentes.
 * Todas assentam em pares {@code (x,y)}, e é isso que se extrai aqui — mais o raio, no
 * caso do círculo.</p>
 *
 * <p>As classes {@link PointGeometry}, {@link BoxGeometry} e {@link CircleGeometry} que já
 * existiam continuam a ser o formato de saída; o que faltava era quem soubesse produzi-las
 * a partir do que a base de dados devolve.</p>
 */
public final class GeometryParser {

    /** Um par de coordenadas, com o significado ainda por decidir (lon/lat ou lat/lon). */
    public record Vertex(double x, double y) {
    }

    public enum Kind {
        POINT, CIRCLE, BOX, SEGMENT, PATH, POLYGON, UNKNOWN
    }

    /**
     * Uma geometria lida de uma célula.
     *
     * @param radius raio do círculo, ou {@link Double#NaN} nos restantes tipos
     */
    public record Shape(Kind kind, List<Vertex> vertices, double radius, String raw) {

        public boolean isEmpty() {
            return vertices.isEmpty();
        }

        /** Centro da forma: a média dos vértices. */
        public Vertex centroid() {
            if (vertices.isEmpty()) return new Vertex(0, 0);
            double sumX = 0, sumY = 0;
            for (Vertex vertex : vertices) {
                sumX += vertex.x();
                sumY += vertex.y();
            }
            return new Vertex(sumX / vertices.size(), sumY / vertices.size());
        }
    }

    private static final Pattern PAIR = Pattern.compile(
            "\\(\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)\\s*\\)");

    private GeometryParser() {
    }

    /** Tipos que este leitor sabe interpretar. */
    public static boolean isGeometricType(final String sqlType) {
        if (sqlType == null) return false;
        return switch (sqlType.toUpperCase(Locale.ROOT).trim()) {
            case "POINT", "CIRCLE", "BOX", "LSEG", "PATH", "POLYGON", "LINE" -> true;
            default -> false;
        };
    }

    /**
     * Lê o valor de uma célula.
     *
     * @param declaredType tipo declarado da coluna, usado para desambiguar; pode ser null
     * @return a forma lida, ou null se o texto não tiver pares de coordenadas
     */
    public static Shape parse(final String value, final String declaredType) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return null;

        final String text = value.trim();

        final List<Vertex> vertices = new ArrayList<>();
        final Matcher matcher = PAIR.matcher(text);
        while (matcher.find()) {
            try {
                vertices.add(new Vertex(Double.parseDouble(matcher.group(1)),
                        Double.parseDouble(matcher.group(2))));
            } catch (NumberFormatException _) {
                // Par malformado: ignora-se em vez de perder a linha toda.
            }
        }

        if (vertices.isEmpty()) return null;

        final Kind kind = classify(text, declaredType, vertices.size());
        final double radius = kind == Kind.CIRCLE ? readRadius(text) : Double.NaN;

        return new Shape(kind, List.copyOf(vertices), radius, text);
    }

    private static Kind classify(final String text, final String declaredType, final int count) {
        if (declaredType != null) {
            switch (declaredType.toUpperCase(Locale.ROOT).trim()) {
                case "POINT" -> {
                    return Kind.POINT;
                }
                case "CIRCLE" -> {
                    return Kind.CIRCLE;
                }
                case "BOX" -> {
                    return Kind.BOX;
                }
                case "LSEG" -> {
                    return Kind.SEGMENT;
                }
                case "PATH" -> {
                    return Kind.PATH;
                }
                case "POLYGON" -> {
                    return Kind.POLYGON;
                }
                default -> {
                }
            }
        }

        // Sem tipo declarado, a sintaxe distingue os casos: <(x,y),r> é círculo,
        // [(...)] é segmento ou caminho aberto, ((...)) é polígono ou caixa.
        if (text.startsWith("<")) return Kind.CIRCLE;
        if (text.startsWith("[")) return count == 2 ? Kind.SEGMENT : Kind.PATH;
        if (count == 1) return Kind.POINT;
        if (count == 2) return Kind.BOX;
        return count > 2 ? Kind.POLYGON : Kind.UNKNOWN;
    }

    /** O raio de {@code <(x,y),r>} é o número depois da última vírgula. */
    private static double readRadius(final String text) {
        final int comma = text.lastIndexOf(',');
        if (comma < 0) return Double.NaN;
        final String tail = text.substring(comma + 1).replace(">", "").trim();
        try {
            return Double.parseDouble(tail);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ==== Conversão para as classes que já existiam ====

    public static PointGeometry toPoint(final Shape shape) {
        final Vertex vertex = shape.vertices().getFirst();
        return new PointGeometry(vertex.x(), vertex.y());
    }

    public static CircleGeometry toCircle(final Shape shape) {
        final Vertex centre = shape.vertices().getFirst();
        return new CircleGeometry(Double.isNaN(shape.radius()) ? 0 : shape.radius(), centre.x(), centre.y());
    }

    public static BoxGeometry toBox(final Shape shape) {
        final Vertex first = shape.vertices().getFirst();
        final Vertex second = shape.vertices().size() > 1 ? shape.vertices().get(1) : first;
        return new BoxGeometry(first.x(), first.y(), second.x(), second.y());
    }

}

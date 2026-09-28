package com.example.sqlide.DatabaseInterface;

import com.example.sqlide.Container.Geometry.GeometryParser;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.controlsfx.control.WorldMapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

/**
 * Mostra dados geométricos num mapa-múndi.
 *
 * <p>Substitui o que a "geometric engine" fazia: os tipos {@code point}, {@code circle},
 * {@code box}, {@code lseg}, {@code path} e {@code polygon} do PostgreSQL deixam de ser
 * apenas texto editável numa célula e passam a poder ser vistos onde estão.</p>
 *
 * <p>O {@link WorldMapView} do ControlsFX posiciona por latitude e longitude e não expõe a
 * sua projeção, por isso não há como desenhar um polígono à escala sem reimplementar a
 * transformação. Cada geometria é desenhada como marcadores: um por vértice, mais um no
 * centro com o glifo da forma. É o que o controlo permite fazer com honestidade.</p>
 */
public class GeoMapController {

    /** Convenção SIG: em {@code point(x,y)}, o x é a longitude. */
    private static final String SOURCE_GEOMETRY = "Geometry column";
    private static final String SOURCE_COLUMNS = "Latitude / longitude columns";

    @FXML
    private WorldMapView Map;
    @FXML
    private StackPane MapHolder;
    @FXML
    private ChoiceBox<String> SourceBox, GeometryColumnBox, LonColumnBox, LatColumnBox, LabelColumnBox;
    @FXML
    private Label TitleLabel, StatusLabel, GeometryLabel, LonLabel, LatLabel;
    @FXML
    private CheckBox SwapCheck;
    @FXML
    private Spinner<Integer> LimitSpinner;
    @FXML
    private ListView<String> ShapeList;
    @FXML
    private Slider ZoomSlider;

    private DataBase database;
    private String table;
    private List<ColumnMetadata> columns = List.of();
    private Stage dialogStage;

    /** Forma associada a cada marcador, para o desenho e o tooltip. */
    private final Map<WorldMapView.Location, GeometryParser.Shape> shapeOf = new HashMap<>();
    private final Map<WorldMapView.Location, String> roleOf = new HashMap<>();

    @FXML
    private void initialize() {
        LimitSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 20_000, 500, 50));

        SourceBox.setItems(FXCollections.observableArrayList(SOURCE_GEOMETRY, SOURCE_COLUMNS));
        SourceBox.getSelectionModel().selectFirst();
        SourceBox.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> applySourceVisibility());

        ZoomSlider.valueProperty().addListener((_, _, value) -> Map.setZoomFactor(value.doubleValue()));

        Map.setZoomFactor(1.0);
        // O SelectionMode só tem SINGLE e MULTIPLE; a seleção de países fica no mínimo.
        Map.setCountrySelectionMode(WorldMapView.SelectionMode.SINGLE);
        Map.setLocationSelectionMode(WorldMapView.SelectionMode.SINGLE);

        // Cada marcador é desenhado conforme a geometria que representa.
        Map.setLocationViewFactory(this::createMarker);

        ShapeList.setPlaceholder(new Label("Nothing plotted yet"));
        applySourceVisibility();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTable(final DataBase database, final String table, final List<ColumnMetadata> columns) {
        this.database = database;
        this.table = table;
        this.columns = columns == null ? List.of() : columns;

        TitleLabel.setText(table + "  ·  " + database.getSQLType());

        final List<String> geometric = new ArrayList<>();
        final List<String> numeric = new ArrayList<>();
        final List<String> all = new ArrayList<>();

        for (ColumnMetadata column : this.columns) {
            all.add(column.Name);
            if (GeometryParser.isGeometricType(column.Type)) geometric.add(column.Name);
            if (isNumeric(column.Type)) numeric.add(column.Name);
        }

        GeometryColumnBox.setItems(FXCollections.observableArrayList(geometric));
        if (!geometric.isEmpty()) GeometryColumnBox.getSelectionModel().selectFirst();

        LonColumnBox.setItems(FXCollections.observableArrayList(numeric));
        LatColumnBox.setItems(FXCollections.observableArrayList(numeric));
        if (numeric.size() > 1) {
            LonColumnBox.getSelectionModel().selectFirst();
            LatColumnBox.getSelectionModel().select(1);
        }

        final List<String> labels = new ArrayList<>();
        labels.add("(none)");
        labels.addAll(all);
        LabelColumnBox.setItems(FXCollections.observableArrayList(labels));
        LabelColumnBox.getSelectionModel().selectFirst();

        // Sem colunas geométricas o modo por lat/lon é o único que faz sentido.
        if (geometric.isEmpty()) {
            SourceBox.getSelectionModel().select(SOURCE_COLUMNS);
            StatusLabel.setText("No geometric columns in this table — pick two numeric columns instead.");
        }
        applySourceVisibility();
    }

    private void applySourceVisibility() {
        final boolean geometry = SOURCE_GEOMETRY.equals(SourceBox.getValue());
        setVisible(geometry, GeometryLabel, GeometryColumnBox);
        setVisible(!geometry, LonLabel, LonColumnBox, LatLabel, LatColumnBox);
        SwapCheck.setVisible(geometry);
        SwapCheck.setManaged(geometry);
    }

    private static void setVisible(final boolean visible, final Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private static boolean isNumeric(final String type) {
        if (type == null) return false;
        final String upper = type.toUpperCase(Locale.ROOT).trim();
        return upper.startsWith("INT") || upper.startsWith("DOUBLE") || upper.startsWith("REAL")
                || upper.startsWith("FLOAT") || upper.startsWith("NUMERIC") || upper.startsWith("DECIMAL")
                || upper.startsWith("BIGINT") || upper.startsWith("SMALLINT");
    }

    // ==== Desenho ====

    @FXML
    private void loadPoints() {
        final boolean geometryMode = SOURCE_GEOMETRY.equals(SourceBox.getValue());
        final String geometryColumn = GeometryColumnBox.getValue();
        final String lonColumn = LonColumnBox.getValue();
        final String latColumn = LatColumnBox.getValue();
        final String labelColumn = "(none)".equals(LabelColumnBox.getValue()) ? null : LabelColumnBox.getValue();
        final boolean swap = SwapCheck.isSelected();
        final int limit = LimitSpinner.getValue();

        if (geometryMode && geometryColumn == null) {
            StatusLabel.setText("Choose the geometry column.");
            return;
        }
        if (!geometryMode && (lonColumn == null || latColumn == null)) {
            StatusLabel.setText("Choose the longitude and latitude columns.");
            return;
        }

        StatusLabel.setText("Reading rows...");

        Thread.ofVirtual().start(() -> {
            try {
                final List<String> wanted = new ArrayList<>();
                if (geometryMode) wanted.add(geometryColumn);
                else {
                    wanted.add(lonColumn);
                    wanted.add(latColumn);
                }
                if (labelColumn != null && !wanted.contains(labelColumn)) wanted.add(labelColumn);

                final StringBuilder sql = new StringBuilder("SELECT ");
                for (int i = 0; i < wanted.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(database.Executor().quoteIdentifier(wanted.get(i)));
                }
                sql.append(" FROM ").append(database.Executor().quoteIdentifier(table))
                        .append(" LIMIT ").append(limit);

                final ArrayList<HashMap<String, String>> rows = database.Fetcher().fetchRawDataMap(sql.toString());

                final List<WorldMapView.Location> locations = new ArrayList<>();
                final Map<WorldMapView.Location, GeometryParser.Shape> shapes = new LinkedHashMap<>();
                final Map<WorldMapView.Location, String> roles = new HashMap<>();
                final List<String> summary = new ArrayList<>();

                int skipped = 0;

                if (rows != null) {
                    final String declaredType = geometryMode ? typeOf(geometryColumn) : null;

                    for (HashMap<String, String> row : rows) {
                        final String label = labelColumn == null ? "" : String.valueOf(row.get(labelColumn));

                        if (geometryMode) {
                            final GeometryParser.Shape shape =
                                    GeometryParser.parse(row.get(geometryColumn), declaredType);
                            if (shape == null || shape.isEmpty()) {
                                skipped++;
                                continue;
                            }
                            addShape(shape, label, swap, locations, shapes, roles);
                            summary.add(shape.kind() + "  " + shape.raw());
                        } else {
                            final Double lon = parse(row.get(lonColumn));
                            final Double lat = parse(row.get(latColumn));
                            if (lon == null || lat == null || !inRange(lat, lon)) {
                                skipped++;
                                continue;
                            }
                            final WorldMapView.Location location = new WorldMapView.Location(label, lat, lon);
                            locations.add(location);
                            roles.put(location, "point");
                            summary.add(String.format(Locale.US, "point  (%.5f, %.5f)  %s", lon, lat, label));
                        }
                    }
                }

                final int ignored = skipped;
                Platform.runLater(() -> {
                    shapeOf.clear();
                    shapeOf.putAll(shapes);
                    roleOf.clear();
                    roleOf.putAll(roles);

                    Map.getLocations().setAll(locations);
                    ShapeList.setItems(FXCollections.observableArrayList(summary));
                    StatusLabel.setText(locations.size() + " marker(s) from " + summary.size()
                            + " geometry value(s)"
                            + (ignored > 0 ? "; " + ignored + " row(s) skipped (empty or out of range)" : ""));
                });

            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                final String message = root.getMessage() == null ? root.toString() : root.getMessage();
                Platform.runLater(() -> {
                    StatusLabel.setText("Failed to read the rows.");
                    ShowError("Map", "Could not read the geometry column.", message);
                });
            }
        });
    }

    /**
     * Converte uma forma em marcadores.
     *
     * <p>Um ponto dá um marcador; as formas com área dão um marcador por vértice mais um no
     * centro com o glifo do tipo, que é a forma de as tornar visíveis sem uma projeção.</p>
     */
    private void addShape(final GeometryParser.Shape shape, final String label, final boolean swap,
                          final List<WorldMapView.Location> locations,
                          final Map<WorldMapView.Location, GeometryParser.Shape> shapes,
                          final Map<WorldMapView.Location, String> roles) {

        final GeometryParser.Vertex centre = shape.centroid();
        final double centreLat = swap ? centre.x() : centre.y();
        final double centreLon = swap ? centre.y() : centre.x();

        if (!inRange(centreLat, centreLon)) return;

        final WorldMapView.Location main = new WorldMapView.Location(
                label == null || label.isBlank() ? shape.kind().toString() : label, centreLat, centreLon);
        locations.add(main);
        shapes.put(main, shape);
        roles.put(main, "centre");

        // Os vértices só valem a pena quando a forma tem mais do que um.
        if (shape.kind() == GeometryParser.Kind.POINT || shape.vertices().size() < 2) return;

        for (GeometryParser.Vertex vertex : shape.vertices()) {
            final double lat = swap ? vertex.x() : vertex.y();
            final double lon = swap ? vertex.y() : vertex.x();
            if (!inRange(lat, lon)) continue;

            final WorldMapView.Location corner = new WorldMapView.Location("", lat, lon);
            locations.add(corner);
            shapes.put(corner, shape);
            roles.put(corner, "vertex");
        }
    }

    /** Marcador de um ponto do mapa, desenhado conforme o tipo de geometria. */
    private Node createMarker(final WorldMapView.Location location) {
        final GeometryParser.Shape shape = shapeOf.get(location);
        final String role = roleOf.getOrDefault(location, "point");

        final Node glyph;
        if ("vertex".equals(role)) {
            final Circle dot = new Circle(2.5, Color.web("#E5C07B"));
            dot.setStroke(Color.web("#2C2C2C"));
            dot.setStrokeWidth(0.5);
            glyph = dot;
        } else if (shape == null) {
            glyph = marker(Color.web("#3574F0"));
        } else {
            glyph = switch (shape.kind()) {
                case CIRCLE -> {
                    final Circle ring = new Circle(6);
                    ring.setFill(Color.TRANSPARENT);
                    ring.setStroke(Color.web("#98C379"));
                    ring.setStrokeWidth(2);
                    yield ring;
                }
                case BOX -> {
                    final Rectangle square = new Rectangle(9, 9);
                    square.setFill(Color.TRANSPARENT);
                    square.setStroke(Color.web("#C678DD"));
                    square.setStrokeWidth(2);
                    yield square;
                }
                case POLYGON, PATH -> {
                    final Polygon triangle = new Polygon(0, -6, 6, 5, -6, 5);
                    triangle.setFill(Color.TRANSPARENT);
                    triangle.setStroke(Color.web("#56B6C2"));
                    triangle.setStrokeWidth(2);
                    yield triangle;
                }
                case SEGMENT -> {
                    final javafx.scene.shape.Line line = new javafx.scene.shape.Line(-6, 6, 6, -6);
                    line.setStroke(Color.web("#E06C75"));
                    line.setStrokeWidth(2);
                    yield line;
                }
                default -> marker(Color.web("#3574F0"));
            };
        }

        final StackPane holder = new StackPane(glyph);
        holder.setAlignment(Pos.CENTER);

        final StringBuilder tip = new StringBuilder();
        if (location.getName() != null && !location.getName().isBlank()) {
            tip.append(location.getName()).append('\n');
        }
        tip.append(String.format(Locale.US, "lat %.5f, lon %.5f",
                location.getLatitude(), location.getLongitude()));
        if (shape != null) tip.append('\n').append(shape.kind()).append("  ").append(shape.raw());
        Tooltip.install(holder, new Tooltip(tip.toString()));

        return holder;
    }

    private static Circle marker(final Color colour) {
        final Circle dot = new Circle(4, colour);
        dot.setStroke(Color.WHITE);
        dot.setStrokeWidth(1);
        return dot;
    }

    @FXML
    private void clearMap() {
        Map.getLocations().clear();
        ShapeList.getItems().clear();
        shapeOf.clear();
        roleOf.clear();
        StatusLabel.setText("");
    }

    private String typeOf(final String column) {
        for (ColumnMetadata metadata : columns) {
            if (metadata.Name.equals(column)) return metadata.Type;
        }
        return null;
    }

    private static Double parse(final String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return null;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Fora deste intervalo o par não é uma coordenada geográfica e não vai para o mapa. */
    private static boolean inRange(final double latitude, final double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    @FXML
    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    public static void open(final Window owner, final DataBase database,
                            final String table, final List<ColumnMetadata> columns) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    GeoMapController.class.getResource("/com/example/sqlide/GeometryContainer/GeoMap.fxml"));
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("Map — " + table);
            stage.setScene(scene);
            if (owner != null) stage.initOwner(owner);
            stage.initModality(Modality.NONE);

            GeoMapController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setTable(database, table, columns);

            stage.show();
        } catch (Exception e) {
            ShowError("Error", "Could not open the map.", e.getMessage());
        }
    }

}

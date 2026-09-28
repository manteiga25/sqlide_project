package com.example.sqlide.DatabaseInterface;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import org.apache.poi.sl.draw.geom.GuideIf;

import java.awt.event.MouseEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.sqlide.misc.Dialog.ChoiceMultipleDialogStage;

public class DatabaseSchemaVisualizer {

    private final ScrollPane Container = new ScrollPane();

    private final Pane pane = new Pane();

    private ArrayList<TableMetadata> TableMetadataList;

    private final ArrayList<Table_Box> TablesBox = new ArrayList<>();

    private DataBase database;

    public void setTableMetadataList(final ArrayList<TableMetadata> TableMetadataList) {
        this.TableMetadataList = TableMetadataList;
        initialize_Box();
    }

    public void setDatabase(final DataBase database) {
        this.database = database;
    }

    public ScrollPane getContainer() {
        return Container;
    }

    public DatabaseSchemaVisualizer() {
        Container.setContent(pane);
    }

    public void addTable(final TableMetadata metadata) {
        TableMetadataList.add(metadata);
        Table_Box tableBox = new Table_Box(metadata);
        TablesBox.add(tableBox);
        pane.getChildren().add(tableBox);
        addForeign(tableBox);
    }

    public void removeTable(final TableMetadata metadata) {
        TableMetadataList.remove(metadata);
        Table_Box box = TablesBox.stream().filter(table->table.table.getName().equals(metadata.getName())).findFirst().get();
        TablesBox.remove(box);
        pane.getChildren().remove(box);
    }

    /** Espaçamento entre cartões e margem do canvas. */
    private static final double GAP_X = 40, GAP_Y = 30, MARGIN = 24, COLUMN_WIDTH = 240;

    private void initialize_Box() {
        for (TableMetadata tableMetadata : TableMetadataList) {
            Table_Box tableBox = new Table_Box(tableMetadata);
            TablesBox.add(tableBox);
            pane.getChildren().add(tableBox);
        }
        // A disposição só pode ser feita depois de o layout correr uma vez: antes disso
        // as alturas ainda são zero e não dá para saber onde acaba cada cartão.
        Platform.runLater(this::layoutTables);
        initialize_foreign();
    }

    /**
     * Arruma os cartões em colunas, empilhando cada um por baixo do anterior.
     *
     * <p>A versão anterior usava um passo fixo de 250 px em X e em Y. Como um cartão com
     * muitas colunas passa facilmente dos 250 px de altura, as tabelas da linha seguinte
     * apareciam por cima das de cima — era isso que se via no ecrã, com os títulos
     * escondidos por baixo dos cartões vizinhos.</p>
     */
    private void layoutTables() {
        if (TablesBox.isEmpty()) return;

        // Força um passo de layout para as alturas já refletirem o número de colunas.
        pane.applyCss();
        pane.layout();

        final double available = Math.max(Container.getViewportBounds().getWidth(), COLUMN_WIDTH * 2);
        final int columns = Math.max(1, (int) ((available - MARGIN) / (COLUMN_WIDTH + GAP_X)));

        // Altura já ocupada por cada coluna, para o próximo cartão entrar por baixo.
        final double[] columnBottom = new double[columns];
        Arrays.fill(columnBottom, MARGIN);

        double widest = 0;

        for (Table_Box box : TablesBox) {
            // Escolhe a coluna mais curta, para o resultado ficar equilibrado.
            int target = 0;
            for (int i = 1; i < columns; i++) if (columnBottom[i] < columnBottom[target]) target = i;

            final double x = MARGIN + target * (COLUMN_WIDTH + GAP_X);
            final double y = columnBottom[target];

            box.setLayoutX(x);
            box.setLayoutY(y);
            box.setPrefWidth(COLUMN_WIDTH);

            final double height = Math.max(box.prefHeight(COLUMN_WIDTH), box.getBoundsInParent().getHeight());
            columnBottom[target] = y + height + GAP_Y;
            widest = Math.max(widest, x + COLUMN_WIDTH);
        }

        double tallest = MARGIN;
        for (double bottom : columnBottom) tallest = Math.max(tallest, bottom);

        // O canvas cresce com o conteúdo, para o ScrollPane ter o que deslocar.
        pane.setPrefSize(widest + MARGIN, tallest + MARGIN);
    }

    /** Rearranja os cartões, mantendo as ligações. Útil depois de arrastar tudo à toa. */
    public void relayout() {
        Platform.runLater(this::layoutTables);
    }

    /**
     * Liga todas as chaves estrangeiras de uma tabela.
     *
     * <p>Antes usava-se {@code findAny()} sobre as colunas com chave estrangeira, o que
     * devolvia <em>uma</em> coluna só: uma tabela com várias referências desenhava apenas
     * a primeira ligação, e o resto do esquema aparecia sem linhas.</p>
     */
    private void addForeign(Table_Box tableBox) {
        for (final ColumnMetadata metadata : tableBox.table.getColumnMetadata()) {
            if (metadata.foreign == null || !metadata.foreign.isForeign) continue;
            connect(tableBox, metadata);
        }
    }

    private void initialize_foreign() {
        for (Table_Box tableBox : TablesBox) addForeign(tableBox);
    }

    /**
     * Desenha uma ligação entre a coluna com a chave e a coluna referenciada.
     *
     * <p>Todas as procuras são verificadas: os {@code findFirst().get()} anteriores
     * lançavam NoSuchElementException quando a tabela ou a coluna referida não existia
     * — o que acontece com uma referência a uma tabela ainda não carregada.</p>
     */
    private void connect(final Table_Box tableBox, final ColumnMetadata metadata) {
        final Optional<Table_Box> target = TablesBox.stream()
                .filter(box -> box.table.getName().equals(metadata.foreign.tableRef))
                .findFirst();
        if (target.isEmpty()) return;

        final Optional<Table_Box.ColumnBox> source = tableBox.getColumnBoxes().stream()
                .filter(box -> box.getColumn().Name.equals(metadata.Name))
                .findFirst();
        if (source.isEmpty()) return;

        final Optional<Table_Box.ColumnBox> reference = target.get().getColumnBoxes().stream()
                .filter(box -> box.getColumn().Name.equals(metadata.foreign.columnRef))
                .findFirst();
        if (reference.isEmpty()) return;

        source.get().BindColumn(reference.get(), pane, tableBox, target.get());
    }

    private class Table_Box extends TitledPane {
        public TableMetadata table;

        // Espaçamento curto: com 10 px por coluna um cartão de 8 colunas ficava
        // desnecessariamente alto e empurrava a disposição toda.
        private final VBox container = new VBox(2);

        private final List<ColumnBox> columnBoxes = new ArrayList<>();

        /** Ponto onde o rato pegou no cabeçalho, em coordenadas do próprio cartão. */
        private double dragAnchorX, dragAnchorY;

        public Table_Box(final TableMetadata table) {
            super();
            this.setCollapsible(false);
            this.setText(table.getName());
            this.table = table;
            this.setContent(container);
            this.getStyleClass().add("erd-table");
            this.getStyleClass().add("erd-accent-blue"); // ou "erd-accent-orange"
            // Arrasto pelo cabeçalho. A versão anterior punha o cartão em
            // (sceneX-120, sceneY-120): coordenadas de cena aplicadas a um layout do pane,
            // com um desvio fixo, por isso o cartão saltava para longe do rato assim que
            // a vista era deslocada.
            Platform.runLater(()->{
                final var titles = lookupAll(".title");
                if (titles.isEmpty()) return;
                final Node node = titles.iterator().next();

                node.setOnMousePressed(event -> {
                    // Guarda onde o rato pegou no cartão, para ele não saltar ao arrastar.
                    dragAnchorX = event.getX();
                    dragAnchorY = event.getY();
                    toFront();
                });

                node.setOnMouseDragged(event -> {
                    final Point2D local = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
                    setLayoutX(Math.max(0, local.getX() - dragAnchorX));
                    setLayoutY(Math.max(0, local.getY() - dragAnchorY));
                });
            });
            this.table.getColumnMetadata().addListener((ListChangeListener<? super ColumnMetadata>) (change)->{
                while (change.next()) {
                    if (change.wasAdded()) {
                        addColumn(change.getAddedSubList().get(change.getFrom()));
                    } else {
                        removeColumn(change.getRemoved().get(change.getFrom()));
                    }
                }
            });

            for (ColumnMetadata columnMetadata : table.getColumnMetadata()) addColumn(columnMetadata);

        }

        public List<ColumnBox> getColumnBoxes() {
            return columnBoxes;
        }

        private void addColumn(final ColumnMetadata metadata) {
            ColumnBox box = new ColumnBox(this, metadata);
            container.getChildren().add(box);
            columnBoxes.add(box);
        }

        private void removeColumn(final ColumnMetadata metadata) {
            // Isto fazia (ColumnBox) sobre o Stream devolvido pelo filter, o que dá
            // ClassCastException à primeira coluna removida.
            columnBoxes.stream()
                    .filter(box -> box.getColumn().Name.equals(metadata.Name))
                    .findFirst()
                    .ifPresent(box -> {
                        box.detach(pane);
                        container.getChildren().remove(box);
                        columnBoxes.remove(box);
                    });
        }


        public class ColumnBox extends HBox {

            public enum KeyState {
                NO,
                FOREIGN,
                PRIMARY
            }

            public KeyState key;

            private final ColumnMetadata column;

            final Label columnName = new Label(), keyLabel = new Label("");

            private Table_Box tableBox;

            final Circle circle1 = new Circle(), circle2 = new Circle();

            Circle currentCircle = circle1; // for left or right position

            private final Line line = new Line(), line2 = new Line();

            private static final String regex = "table:\\s*(\\w+),\\s*column:\\s*(\\w+)";

            public ColumnMetadata getColumn() {
                return column;
            }

            private void setKey() {
                keyLabel.getStyleClass().removeAll("key-pk", "key-fk");

                // column.foreign \u00E9 null nas colunas sem refer\u00EAncia: o acesso direto a
                // .isForeign lan\u00E7ava NullPointerException ao montar o cart\u00E3o.
                final boolean isForeign = column.foreign != null && column.foreign.isForeign;

                if (isForeign) {
                    key = KeyState.FOREIGN;
                    keyLabel.setText("\uD83D\uDD11");
                    keyLabel.getStyleClass().add("key-fk");
                    keyLabel.setTooltip(new Tooltip("References "
                            + column.foreign.tableRef + "." + column.foreign.columnRef));
                } else if (column.IsPrimaryKey) {
                    key = KeyState.PRIMARY;
                    keyLabel.setText("\uD83D\uDD11");
                    keyLabel.getStyleClass().add("key-pk");
                    keyLabel.setTooltip(new Tooltip("Primary key"));
                } else {
                    key = KeyState.NO;
                    keyLabel.setText("");
                    keyLabel.setTooltip(null);
                }
            }

            public ColumnBox(Table_Box tableBox, ColumnMetadata column) {
                super(5);
                this.tableBox = tableBox;
                this.column = column;
                this.getStyleClass().add("column-row");

                columnName.setText(column.Name);

                line.setStrokeWidth(2);
                line.setStroke(Color.BLUE);
                LinearGradient lg = new LinearGradient(
                        0,0, 1,0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#79d1ff")),
                        new Stop(1, Color.web("#2f80ed"))
                );
                line.setStroke(lg);
                line.getStyleClass().add("erd-connection");

                line2.setStrokeWidth(2);
                line2.setStroke(Color.BLUE);
                // line2 só serve para a linha provisória do arrasto. Era acrescentada ao
                // pane no construtor de cada coluna, deixando lá centenas de linhas de
                // comprimento zero na origem — entra agora só quando o arrasto começa.

                // Os círculos são os pontos de ligação; sem raio ficavam invisíveis e as
                // linhas nasciam todas no canto do HBox.
                circle1.setRadius(4);
                circle2.setRadius(4);
                circle1.getStyleClass().add("erd-connector");
                circle2.getStyleClass().add("erd-connector");

                keyLabel.getStyleClass().add("key-badge");
                columnName.getStyleClass().add("col-name");

                setSpacing(6);
                setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                HBox.setHgrow(columnName, javafx.scene.layout.Priority.ALWAYS);
                columnName.setMaxWidth(Double.MAX_VALUE);

                getChildren().addAll(circle1, keyLabel, columnName, circle2);

                setKey();

            /*    circle1.setOnMousePressed(event -> {
                    circle1.startFullDrag(); // <-- habilita eventos de "MouseDrag"
                });

                this.setOnMouseDragged(this::setLine);

                this.setOnMouseDragReleased(event -> {
                    System.out.println("drag terminado");
                });

                this.setOnMouseDragExited(event -> {
                    System.out.println("drag terminado");
                });

                this.setOnMouseDragOver(event -> {
                    System.out.println("drag terminado");
                }); */

                this.setOnDragDetected(_-> {
                    Dragboard db = this.startDragAndDrop(TransferMode.ANY);
                    ClipboardContent content = new ClipboardContent();
                    // Separador improvável num identificador: com um espaço, um nome de
                    // tabela ou coluna com espaço partia o split do outro lado.
                    content.putString(table.getName() + "" + column.Name);
                    db.setContent(content);
                    getStyleClass().add("dragging");
                });

                this.setOnDragDropped(e-> {
                    final Dragboard db = e.getDragboard();
                    boolean success = false;

                    final String payload = db.getString();
                    final String[] values = payload == null ? new String[0] : payload.split("", 2);

                    // Sem estas verificações um largar fora de sítio dava
                    // NoSuchElementException no findFirst().get() e a janela ficava presa.
                    if (values.length == 2 && !values[0].equals(this.tableBox.table.getName())) {
                        final Optional<Table_Box> sourceTable = TablesBox.stream()
                                .filter(box -> box.table.getName().equals(values[0])).findFirst();

                        final Optional<ColumnBox> sourceColumn = sourceTable
                                .flatMap(box -> box.getColumnBoxes().stream()
                                        .filter(c -> c.column.Name.equals(values[1])).findFirst());

                        if (sourceColumn.isPresent()) {
                            List<String> params = ChoiceMultipleDialogStage(
                                    List.of(List.of(tableBox.table.getColumnMetadata().stream().map(col->col.Name).toArray(String[]::new)),
                                            List.of(database.getDatabaseInfo().getForeignModes()),
                                            List.of(database.getDatabaseInfo().getForeignModes())),
                                    this.column.Name, "Foreign", "Foreign options",
                                    List.of("Column:", "update", "delete"));

                            // Só liga se o diálogo foi confirmado.
                            if (params != null && !params.isEmpty()) {
                                BindColumn(sourceColumn.get(), pane, tableBox, sourceTable.get());
                                success = true;
                            }
                        }
                    }

                    e.setDropCompleted(success);
                    e.consume();
                });

                this.setOnDragOver(e-> {
                    if (e.getGestureSource() != this && e.getDragboard().hasString()) {
                        e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    }
                    e.consume();
                });

                this.setOnDragDone(_ -> getStyleClass().remove("dragging"));

            }

            private void setLine(javafx.scene.input.MouseEvent mouse) {
                Point2D start = this.localToScene(getWidth() / 2, getHeight() / 2);
                Point2D startInPane = pane.sceneToLocal(start);

                Point2D point = pane.sceneToLocal(mouse.getSceneX(), mouse.getSceneY());

                line2.setStartX(startInPane.getX());
                line2.setStartY(startInPane.getY());
                line2.setEndX(point.getX());
                line2.setEndY(point.getY());
            }

            public void BindColumn(ColumnBox columnBox, Pane container, Table_Box thisTable, Table_Box otherTable) {

                container.getChildren().add(line);

                ContextMenu contextMenu = new ContextMenu();
                MenuItem item1 = new MenuItem("Remove reference");
                MenuItem item2 = new MenuItem("Edit reference");
                contextMenu.getItems().addAll(item1, item2);

                line.setOnContextMenuRequested(event->contextMenu.show(line, event.getScreenX(), event.getScreenY()));

                Runnable updater = () -> {
                    // O lado de onde a linha sai depende de qual tabela está à esquerda.
                    // Antes comparava-se p1Local.magnitude() com p2Local.magnitude(), que é
                    // a distância à origem do pane — não diz nada sobre as posições relativas,
                    // e as linhas saíam pelo lado errado, atravessando os cartões.
                    final boolean thisIsLeft =
                            thisTable.getLayoutX() + thisTable.getWidth() / 2
                                    <= otherTable.getLayoutX() + otherTable.getWidth() / 2;

                    currentCircle = thisIsLeft ? circle2 : circle1;
                    columnBox.currentCircle = thisIsLeft ? columnBox.circle1 : columnBox.circle2;

                    final Point2D start = anchorOf(this, currentCircle, container);
                    final Point2D end = anchorOf(columnBox, columnBox.currentCircle, container);

                    line.setStartX(start.getX());
                    line.setStartY(start.getY());
                    line.setEndX(end.getX());
                    line.setEndY(end.getY());
                };

                // Segue os dois círculos e as duas tabelas: mexer em qualquer um redesenha.
                circle1.boundsInParentProperty().addListener((_, _, _) -> updater.run());
                circle2.boundsInParentProperty().addListener((_, _, _) -> updater.run());
                columnBox.circle1.boundsInParentProperty().addListener((_, _, _) -> updater.run());
                columnBox.circle2.boundsInParentProperty().addListener((_, _, _) -> updater.run());

                thisTable.layoutXProperty().addListener((_, _, _) -> updater.run());
                thisTable.layoutYProperty().addListener((_, _, _) -> updater.run());
                otherTable.layoutXProperty().addListener((_, _, _) -> updater.run());
                otherTable.layoutYProperty().addListener((_, _, _) -> updater.run());

                // Ao primeiro desenho as posições ainda podem ser zero; repete depois do layout.
                updater.run();
                Platform.runLater(updater);
            }

            /** Centro do círculo de ligação, em coordenadas do pane. */
            private Point2D anchorOf(final ColumnBox box, final Circle circle, final Pane container) {
                final Point2D centre = circle.localToScene(0, 0);
                return container.sceneToLocal(centre);
            }

            /** Retira do pane o que esta coluna lá pôs, ao ser removida. */
            private void detach(final Pane container) {
                container.getChildren().removeAll(line, line2);
            }


        }

    }

}

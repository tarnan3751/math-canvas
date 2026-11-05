package mathcanvas.app.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.eval.AlgebraRow;
import mathcanvas.app.eval.CanvasFrame;
import mathcanvas.app.eval.Document;
import mathcanvas.app.eval.StatisticsSummaries;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.Arrow;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.ContourPlot;
import mathcanvas.canvas.model.FieldGrid;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.Histogram;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParametricSurface;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SpaceObject;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.canvas.model.VectorField;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.render.MathView;

/**
 * The algebra panel: every canvas object in its algebraic form, one row each in the canvas's order,
 * and a blank row for typing a new one. A row shows the object's colour, a visibility box ({@link
 * Command.SetStyle}), its form typeset ({@code MathView.setBox} on the box the worker typeset, or
 * the text when the form is over the cap), why it is undefined when there is something to say, and
 * a delete button ({@link Command.RemoveObject}, which takes the objects built on it too); a
 * slider's row has a slider control that sets its value ({@link Command.SetSlider}, a drag one undo
 * step). A double click on a row edits its text: Enter submits it ({@link Document#submitEntry}),
 * which replaces the object, and a refused text comes back to the editor with the reason under it;
 * Escape leaves the row as it was. Text typed in the blank row and submitted adds an object, the
 * same way; Escape there clears the text, and in the empty row stops the jobs as on the canvas. A
 * click selects a row, and the selection is the canvas's: selecting an object on the canvas selects
 * its row. The panel only builds nodes from the rows the worker printed and typeset ({@link
 * CanvasFrame#rows()}); a row whose object and printed form did not change keeps its nodes, so a
 * slider row being dragged is never rebuilt. A histogram's row has a spinner of its bin width; a
 * Statistics heading above the objects lists the summary of each data cell a plot reads ({@link
 * StatisticsSummaries}), read-only. Touched only on the FX thread.
 */
public final class AlgebraPanel extends VBox {

  private static final double SWATCH_RADIUS = 6;

  private final Document document;
  private final ObjectProperty<ObjectId> selection;
  private final Supplier<CanvasTheme> theme;
  private final VBox rows = new VBox(2);
  private final Map<ObjectId, RowView> views = new HashMap<>();
  private final TextField entry = new TextField();
  private final Label entryError = error();
  // Phase 7d: whether the blank row adds an object in space, while the 3D view is shown
  private boolean space;

  /**
   * A panel.
   *
   * @param document the document whose canvas it lists
   * @param selection the selected object, shared with the canvas
   * @param theme the colours the canvas draws in, for the swatches
   */
  public AlgebraPanel(
      Document document, ObjectProperty<ObjectId> selection, Supplier<CanvasTheme> theme) {
    this.document = document;
    this.selection = selection;
    this.theme = theme;
    setId("algebra");
    getStyleClass().addAll("side-panel", "algebra-panel");
    Label title = new Label(Messages.text("algebra.title"));
    title.getStyleClass().add("side-panel-title");
    rows.setId("algebra-rows");
    entry.setId("algebra-entry");
    entry.setPromptText(Messages.text("algebra.prompt"));
    entry.setOnAction(e -> submitEntry());
    entry.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          // Escape clears a row with text; in an empty row it stops the jobs, as on the canvas
          if (e.getCode() == KeyCode.ESCAPE && !entry.getText().isEmpty()) {
            entry.clear();
            showError(entryError, null);
            e.consume();
          }
        });
    VBox list = new VBox(4, rows, entry, entryError);
    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    // a row is never wider than the panel: its form is clipped, so its delete button stays in view
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    getChildren().addAll(title, scroll);
    selection.subscribe(this::select);
    document.addListener(change -> syncSliders());
    document.addListener(change -> syncCells());
  }

  /**
   * Shows the rows of a frame, in the order of its canvas's objects; an object the frame has no row
   * for is not listed.
   *
   * @param frame the frame
   */
  public void show(CanvasFrame frame) {
    List<Node> out = new ArrayList<>();
    Map<ObjectId, RowView> kept = new HashMap<>();
    // Phase 7d: the plane's rows, then the 3D view's under a heading of their own, and the plane's
    // under one too when there are both
    List<CanvasObject> ordered = new ArrayList<>();
    List<CanvasObject> inSpace = new ArrayList<>();
    for (CanvasObject o : frame.state().objects()) {
      (o instanceof SpaceObject ? inSpace : ordered).add(o);
    }
    boolean headed = !inSpace.isEmpty();
    // Phase 8c: the statistics of the data cells the plots read, above the plane's objects
    List<StatisticsSummaries.Line> statistics = StatisticsSummaries.of(frame.state());
    if (!statistics.isEmpty()) {
      out.add(heading("algebra-heading-statistics", "algebra.statistics"));
      for (StatisticsSummaries.Line line : statistics) {
        out.add(summary(line));
      }
    }
    if (headed && !ordered.isEmpty()) {
      out.add(heading("algebra-heading-plane", "algebra.plane"));
    }
    ordered.addAll(inSpace);
    for (CanvasObject o : ordered) {
      if (headed && o == inSpace.getFirst()) {
        out.add(heading("algebra-heading-space", "algebra.space"));
      }
      AlgebraRow row = frame.rows().get(o.id());
      if (row == null) {
        continue;
      }
      RowView v = views.get(o.id());
      if (v == null) {
        v = new RowView(o.id());
      }
      v.show(o, row, frame.state().geometryOf(o.id()));
      kept.put(o.id(), v);
      out.add(v);
    }
    views.clear();
    views.putAll(kept);
    if (!rows.getChildren().equals(out)) {
      rows.getChildren().setAll(out);
    }
    select(selection.get());
  }

  /** Paints the swatches again, after the canvas's colours changed. */
  void recolour() {
    for (RowView v : views.values()) {
      v.recolour();
    }
  }

  /**
   * The ids of the rows shown, in order, for the tests.
   *
   * @return the ids
   */
  public List<ObjectId> shownIds() {
    return rows.getChildren().stream()
        .filter(RowView.class::isInstance)
        .map(n -> ((RowView) n).id)
        .toList();
  }

  // Phase 7d: the 3D view's rows.

  /**
   * Makes the blank row add objects in space, while the 3D view is shown, or on the plane.
   *
   * @param on whether the 3D view is shown
   */
  void setSpace(boolean on) {
    space = on;
    entry.setPromptText(Messages.text(on ? "algebra.prompt.space" : "algebra.prompt"));
  }

  /** A heading over the rows of one view, kept between frames. */
  private Label heading(String id, String key) {
    for (Node n : rows.getChildren()) {
      if (n instanceof Label l && id.equals(l.getId())) {
        return l;
      }
    }
    Label l = new Label(Messages.text(key));
    l.setId(id);
    l.getStyleClass().add("algebra-heading");
    return l;
  }

  /**
   * A data cell's summary under the Statistics heading, read-only, kept between frames while its
   * text is the same.
   */
  private Label summary(StatisticsSummaries.Line line) {
    String id = "algebra-statistics-" + line.data();
    String text = Messages.text("algebra.statistics.line", line.data(), line.text());
    for (Node n : rows.getChildren()) {
      if (n instanceof Label l && id.equals(l.getId()) && text.equals(l.getText())) {
        return l;
      }
    }
    Label l = new Label(text);
    l.setId(id);
    l.setWrapText(true);
    l.getStyleClass().add("algebra-statistics");
    return l;
  }

  /**
   * A row's nodes, for the tests: its id is {@code algebra-<object id>}.
   *
   * @param id the object
   * @return the row, if shown
   */
  public Optional<Node> row(ObjectId id) {
    return Optional.ofNullable(views.get(id));
  }

  /**
   * The blank row's field, for the tests.
   *
   * @return the field
   */
  public TextField entry() {
    return entry;
  }

  /**
   * The message under the blank row, for the tests.
   *
   * @return the label
   */
  public Label entryError() {
    return entryError;
  }

  private void submitEntry() {
    String text = entry.getText();
    if (text.isBlank()) {
      return;
    }
    entry.clear();
    showError(entryError, null);
    document.submitEntry(
        Optional.empty(),
        text,
        space,
        message -> {
          entry.setText(text);
          entry.positionCaret(text.length());
          showError(entryError, message);
        });
  }

  private void select(ObjectId id) {
    for (RowView v : views.values()) {
      boolean on = v.id.equals(id);
      if (on != v.getStyleClass().contains("selected")) {
        if (on) {
          v.getStyleClass().add("selected");
        } else {
          v.getStyleClass().remove("selected");
        }
      }
    }
  }

  /** Shows on each row of a graph a cell drew the number of that cell, which moves with it. */
  private void syncCells() {
    for (RowView v : views.values()) {
      v.syncCell();
    }
  }

  /** Puts each slider control at its slider's value, unless the control is being dragged. */
  private void syncSliders() {
    for (RowView v : views.values()) {
      document.state().canvas().object(v.id).ifPresent(v::syncSlider);
    }
  }

  private static Label error() {
    Label l = new Label();
    l.getStyleClass().add("algebra-error");
    l.setWrapText(true);
    l.setVisible(false);
    l.setManaged(false);
    return l;
  }

  private static void showError(Label label, String message) {
    label.setText(message == null ? "" : message);
    label.setVisible(message != null);
    label.setManaged(message != null);
  }

  /**
   * The style with another visibility.
   *
   * @param style the style
   * @param visible whether the object is drawn
   * @return the style
   */
  private static Style withVisible(Style style, boolean visible) {
    return new Style(style.colour(), style.width(), style.dashed(), visible, style.labelVisible());
  }

  /**
   * The bin-width spinner's values: a step up or down goes to the next of 1, 2 and 5 times a power
   * of ten from the width shown, written to four significant digits.
   */
  private static final class WidthSteps extends SpinnerValueFactory<Double> {

    WidthSteps() {
      setConverter(
          new StringConverter<>() {
            @Override
            public String toString(Double width) {
              return width == null ? "" : StatisticsSummaries.number(width);
            }

            @Override
            public Double fromString(String text) {
              throw new UnsupportedOperationException("the widths are stepped, not typed");
            }
          });
    }

    @Override
    public void increment(int steps) {
      setValue(Histogram.steppedWidth(getValue(), steps).doubleValue());
    }

    @Override
    public void decrement(int steps) {
      setValue(Histogram.steppedWidth(getValue(), -steps).doubleValue());
    }
  }

  /** One object's row. */
  private final class RowView extends HBox {

    private final ObjectId id;
    private final Circle swatch = new Circle(SWATCH_RADIUS);
    private final CheckBox visible = new CheckBox();
    private final StackPane form = new StackPane();
    private final TextField editor = new TextField();
    private final Label undefined = error();
    private final Label refused = error();
    // the cell whose plot statement drew the object, when one did
    private final Label cell = new Label();
    private final VBox body;
    private CanvasObject object;
    private AlgebraRow row;
    private Slider slider;
    private ChoiceBox<Quadrature.Method> method;
    private ChoiceBox<Optional<ObjectId>> tail;
    private Spinner<Integer> count;
    private Spinner<Double> binWidth;
    private ChoiceBox<Integer> cells;
    private boolean syncing;

    RowView(ObjectId id) {
      this.id = id;
      setId("algebra-" + id.value());
      getStyleClass().add("algebra-row");
      swatch.getStyleClass().add("algebra-swatch");
      visible.setTooltip(new Tooltip(Messages.text("algebra.visible.tip")));
      visible.setAccessibleText(Messages.text("algebra.visible.tip"));
      visible.setOnAction(
          e ->
              document.execute(
                  new Command.SetStyle(id, withVisible(object.style(), visible.isSelected()))));
      Button delete = new Button(Messages.text("algebra.delete"));
      delete.getStyleClass().add("algebra-delete");
      delete.setTooltip(new Tooltip(Messages.text("algebra.delete.tip")));
      delete.setAccessibleText(Messages.text("algebra.delete.tip"));
      delete.setOnAction(e -> document.execute(new Command.RemoveObject(id)));
      form.setAlignment(Pos.CENTER_LEFT);
      form.setMinWidth(0);
      Rectangle clip = new Rectangle();
      clip.widthProperty().bind(form.widthProperty());
      clip.heightProperty().bind(form.heightProperty());
      form.setClip(clip);
      delete.setMinWidth(Region.USE_PREF_SIZE);
      editor.setOnAction(e -> submit());
      editor.addEventFilter(
          KeyEvent.KEY_PRESSED,
          e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
              stopEditing();
              showError(refused, null);
              e.consume();
            }
          });
      editor
          .focusedProperty()
          .subscribe(
              f -> {
                if (!f && editing()) {
                  stopEditing();
                }
              });
      body = new VBox(2, form, undefined, refused);
      body.setMinWidth(0);
      HBox.setHgrow(body, Priority.ALWAYS);
      setOnMouseClicked(
          e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
              selection.set(id);
              if (e.getClickCount() == 2 && !editing()) {
                startEditing(editable());
              }
            }
          });
      setSpacing(6);
      setAlignment(Pos.CENTER_LEFT);
      cell.getStyleClass().add("algebra-cell");
      cell.setMinWidth(Region.USE_PREF_SIZE);
      syncCell();
      getChildren().addAll(swatch, visible, body, cell, delete);
    }

    /** Names the cell that drew the object, by its number in the worksheet, or hides the name. */
    void syncCell() {
      DocumentState state = document.state();
      int number = 0;
      for (int k = 0; k < state.cells().size() && number == 0; k++) {
        if (state.plotsOf(state.cells().get(k).id()).contains(id)) {
          number = k + 1;
        }
      }
      String text = number == 0 ? "" : Messages.text("algebra.cell", number);
      if (!text.equals(cell.getText())) {
        cell.setText(text);
        cell.setTooltip(
            number == 0 ? null : new Tooltip(Messages.text("algebra.cell.tip", number)));
      }
      cell.setVisible(number != 0);
      cell.setManaged(number != 0);
    }

    void show(CanvasObject o, AlgebraRow r, Geometry g) {
      boolean sameForm = r.equals(row);
      object = o;
      row = r;
      visible.setSelected(o.style().visible());
      recolour();
      showError(undefined, r.error().orElse(null));
      if (!sameForm && !editing()) {
        form.getChildren().setAll(display(r));
      }
      if (o instanceof mathcanvas.canvas.model.Slider s) {
        if (slider == null) {
          slider = new Slider();
          slider.getStyleClass().add("algebra-slider");
          slider
              .valueProperty()
              .addListener(
                  (obs, was, now) -> {
                    if (!syncing) {
                      document.execute(
                          new Command.SetSlider(id, BigDecimal.valueOf(now.doubleValue())));
                    }
                  });
          slider
              .valueChangingProperty()
              .addListener(
                  (obs, was, now) -> {
                    if (!now) {
                      document.endMerge();
                      document.state().canvas().object(id).ifPresent(this::syncSlider);
                    }
                  });
          body.getChildren().add(1, slider);
        }
        syncSlider(s);
      } else if (slider != null) {
        body.getChildren().remove(slider);
        slider = null;
      }
      if (o instanceof RiemannSum sum) {
        if (method == null) {
          method = methodChoice();
          body.getChildren().add(1, method);
        }
        syncing = true;
        try {
          method.setValue(sum.method());
        } finally {
          syncing = false;
        }
      } else if (method != null) {
        body.getChildren().remove(method);
        method = null;
      }
      if (o instanceof Arrow a && a.head() instanceof Arrow.Bound) {
        if (tail == null) {
          tail = tailChoice();
          body.getChildren().add(1, tail);
        }
        syncing = true;
        try {
          List<Optional<ObjectId>> anchors = new ArrayList<>();
          anchors.add(Optional.empty());
          for (CanvasObject p : document.state().canvas().objects()) {
            if (p.isPoint()) {
              anchors.add(Optional.of(p.id()));
            }
          }
          if (!tail.getItems().equals(anchors)) {
            tail.getItems().setAll(anchors);
          }
          tail.setValue(a.tail());
        } finally {
          syncing = false;
        }
      } else if (tail != null) {
        body.getChildren().remove(tail);
        tail = null;
      }
      // Phase 7d: a surface's cells a side
      int n =
          o instanceof Surface surface
              ? surface.n()
              : o instanceof ParametricSurface surface ? surface.n() : 0;
      if (n > 0) {
        if (cells == null) {
          cells = cellsChoice();
          body.getChildren().add(1, cells);
        }
        syncing = true;
        try {
          cells.setValue(n);
        } finally {
          syncing = false;
        }
      } else if (cells != null) {
        body.getChildren().remove(cells);
        cells = null;
      }
      showCount(o);
      showBinWidth(o, g);
    }

    // Phase 8c: a histogram's bin width, each step one command

    /**
     * The spinner of a histogram's bin width while it has bins: its steps go to the next of 1, 2
     * and 5 times a power of ten ({@link Histogram#steppedWidth}), each one {@link
     * Command.ReplaceObject} with the width written exactly.
     */
    private void showBinWidth(CanvasObject o, Geometry g) {
      if (!(o instanceof Histogram && g instanceof Geometry.Bins bins)) {
        if (binWidth != null) {
          body.getChildren().remove(binWidth);
          binWidth = null;
        }
        return;
      }
      if (binWidth == null) {
        binWidth = new Spinner<>(new WidthSteps());
        binWidth.setId("algebra-binwidth-" + id.value());
        binWidth.getStyleClass().add("algebra-count");
        binWidth.setTooltip(new Tooltip(Messages.text("algebra.binWidth.tip")));
        binWidth.setAccessibleHelp(Messages.text("algebra.binWidth.tip"));
        binWidth
            .valueProperty()
            .addListener(
                (obs, was, now) -> {
                  if (!syncing
                      && now != null
                      && document.state().canvas().object(id).orElse(null) instanceof Histogram h) {
                    document.execute(
                        new Command.ReplaceObject(h.withBinWidth(BigDecimal.valueOf(now))));
                  }
                });
        // the arrows' clicks stay with the spinner: two quick ones are no double click on the row
        binWidth.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        body.getChildren().add(1, binWidth);
      }
      syncing = true;
      try {
        binWidth.getValueFactory().setValue(bins.width());
      } finally {
        syncing = false;
      }
    }

    // phase 7c: a field's density and a contour plot's number of levels, each change one command

    /** The spinner of a field's density or of a contour plot's count of levels, when it has one. */
    private void showCount(CanvasObject o) {
      String kind =
          switch (o) {
            case VectorField _, SlopeField _ -> "density";
            case ContourPlot c when c.levels() instanceof ContourPlot.Count -> "levels";
            default -> null;
          };
      if (count != null && (kind == null || !count.getId().startsWith("algebra-" + kind))) {
        body.getChildren().remove(count);
        count = null;
      }
      if (kind == null) {
        return;
      }
      if (count == null) {
        count =
            kind.equals("density")
                ? new Spinner<>(
                    FieldGrid.MIN_DENSITY, FieldGrid.MAX_DENSITY, FieldGrid.DEFAULT_DENSITY)
                : new Spinner<>(1, ContourPlot.MAX_LEVELS, 1);
        count.setId("algebra-" + kind + "-" + id.value());
        count.getStyleClass().add("algebra-count");
        count.setTooltip(new Tooltip(Messages.text("algebra." + kind + ".tip")));
        count.setAccessibleHelp(Messages.text("algebra." + kind + ".tip"));
        count
            .valueProperty()
            .addListener(
                (obs, was, now) -> {
                  if (syncing || now == null) {
                    return;
                  }
                  CanvasObject current = document.state().canvas().object(id).orElse(null);
                  CanvasObject changed =
                      switch (current) {
                        case VectorField v when v.density() != now -> v.withDensity(now);
                        case SlopeField s when s.density() != now -> s.withDensity(now);
                        case ContourPlot c
                            when c.levels() instanceof ContourPlot.Count n && n.n() != now ->
                            c.withLevels(new ContourPlot.Count(now));
                        case null, default -> null;
                      };
                  if (changed != null) {
                    document.execute(new Command.ReplaceObject(changed));
                  }
                });
        // the arrows' clicks stay with the spinner: two quick ones are no double click on the row
        count.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        body.getChildren().add(1, count);
      }
      int value =
          switch (o) {
            case VectorField v -> v.density();
            case SlopeField s -> s.density();
            case ContourPlot c when c.levels() instanceof ContourPlot.Count n -> n.n();
            default -> count.getValue();
          };
      syncing = true;
      try {
        count.getValueFactory().setValue(value);
      } finally {
        syncing = false;
      }
    }

    /** A surface's cells a side, changed as one command. */
    private ChoiceBox<Integer> cellsChoice() {
      ChoiceBox<Integer> box = new ChoiceBox<>();
      box.getItems().setAll(Surface.SIZES);
      box.setId("algebra-cells-" + id.value());
      box.getStyleClass().add("algebra-method");
      box.setTooltip(new Tooltip(Messages.text("algebra.cells.tip")));
      box.setAccessibleHelp(Messages.text("algebra.cells.tip"));
      box.setConverter(
          new StringConverter<>() {
            @Override
            public String toString(Integer n) {
              return n == null ? "" : Messages.text("algebra.cells", n);
            }

            @Override
            public Integer fromString(String text) {
              throw new UnsupportedOperationException("the sizes are chosen, not typed");
            }
          });
      box.valueProperty()
          .addListener(
              (obs, was, now) -> {
                if (syncing || now == null) {
                  return;
                }
                CanvasObject o = document.state().canvas().object(id).orElse(null);
                if (o instanceof Surface s && s.n() != now) {
                  document.execute(new Command.ReplaceObject(s.withN(now)));
                } else if (o instanceof ParametricSurface s && s.n() != now) {
                  document.execute(new Command.ReplaceObject(s.withN(now)));
                }
              });
      return box;
    }

    /** A bound arrow's tail, the origin or a point, changed as one command. */
    private ChoiceBox<Optional<ObjectId>> tailChoice() {
      ChoiceBox<Optional<ObjectId>> box = new ChoiceBox<>();
      box.setId("algebra-tail-" + id.value());
      box.getStyleClass().add("algebra-method");
      box.setTooltip(new Tooltip(Messages.text("algebra.tail.tip")));
      box.setAccessibleHelp(Messages.text("algebra.tail.tip"));
      box.setConverter(
          new StringConverter<>() {
            @Override
            public String toString(Optional<ObjectId> point) {
              return point == null
                  ? ""
                  : point
                      .map(
                          p ->
                              Messages.text(
                                  "algebra.tail.point",
                                  document.state().names().getOrDefault(p, "#" + p.value())))
                      .orElse(Messages.text("algebra.tail.origin"));
            }

            @Override
            public Optional<ObjectId> fromString(String text) {
              throw new UnsupportedOperationException("the tails are chosen, not typed");
            }
          });
      box.valueProperty()
          .addListener(
              (obs, was, now) -> {
                if (!syncing
                    && now != null
                    && document.state().canvas().object(id).orElse(null) instanceof Arrow a
                    && !a.tail().equals(now)) {
                  document.execute(new Command.ReplaceObject(a.withTail(now)));
                }
              });
      return box;
    }

    /** The Riemann sum's method, changed as one command. */
    private ChoiceBox<Quadrature.Method> methodChoice() {
      ChoiceBox<Quadrature.Method> box = new ChoiceBox<>();
      box.getItems().setAll(Quadrature.Method.values());
      box.setId("algebra-riemann-method-" + id.value());
      box.getStyleClass().add("algebra-method");
      box.setConverter(
          new StringConverter<>() {
            @Override
            public String toString(Quadrature.Method m) {
              return m == null ? "" : Messages.text("riemann." + m.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public Quadrature.Method fromString(String text) {
              throw new UnsupportedOperationException("the methods are chosen, not typed");
            }
          });
      box.valueProperty()
          .addListener(
              (obs, was, now) -> {
                if (!syncing
                    && now != null
                    && document.state().canvas().object(id).orElse(null) instanceof RiemannSum s
                    && s.method() != now) {
                  document.execute(new Command.ReplaceObject(s.withMethod(now)));
                }
              });
      return box;
    }

    void syncSlider(CanvasObject o) {
      if (slider == null
          || slider.isValueChanging()
          || !(o instanceof mathcanvas.canvas.model.Slider s)) {
        return;
      }
      syncing = true;
      try {
        slider.setMin(s.min().doubleValue());
        slider.setMax(s.max().doubleValue());
        slider.setBlockIncrement(s.step().doubleValue());
        slider.setValue(s.value().doubleValue());
      } finally {
        syncing = false;
      }
    }

    void recolour() {
      Rgba c = theme.get().series().get(object.style().colour());
      swatch.setFill(new Color(c.red(), c.green(), c.blue(), c.opacity()));
    }

    private Node display(AlgebraRow r) {
      if (r.box() == null) {
        Label text = new Label(Shown.capped(r.text()));
        text.getStyleClass().add("algebra-text");
        return text;
      }
      MathView view = new MathView();
      view.setBox(r.box());
      view.setMouseTransparent(true);
      view.setAccessibleRole(AccessibleRole.TEXT);
      view.setAccessibleText(r.text());
      return view;
    }

    /**
     * The text a row's editor starts with: the row's own, but for a gradient (Phase 7d), whose row
     * is words, the point it is taken at, {@code [x₀, y₀]}, which is what its row reads.
     */
    private String editable() {
      return object instanceof GradientArrow g
          ? "["
              + UnicodePrinter.INSTANCE.print(g.x())
              + ", "
              + UnicodePrinter.INSTANCE.print(g.y())
              + "]"
          : row.text();
    }

    private boolean editing() {
      return form.getChildren().contains(editor);
    }

    private void startEditing(String text) {
      editor.setText(text);
      form.getChildren().setAll(editor);
      editor.requestFocus();
      editor.positionCaret(text.length());
    }

    private void stopEditing() {
      form.getChildren().setAll(display(row));
    }

    private void submit() {
      String text = editor.getText();
      stopEditing();
      showError(refused, null);
      document.submitEntry(
          Optional.of(id),
          text,
          message -> {
            startEditing(text);
            showError(refused, message);
          });
    }
  }
}

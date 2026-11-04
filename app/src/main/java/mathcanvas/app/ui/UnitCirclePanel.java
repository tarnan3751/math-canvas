package mathcanvas.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javafx.beans.value.ObservableValue;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.eval.Document;
import mathcanvas.app.eval.TrigValues;
import mathcanvas.app.text.Messages;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.render.MathView;

/**
 * The unit circle view: a circle with a draggable angle, the point on the circle, the cosine and
 * the sine as the point's projections and the tangent as the segment on the tangent line at {@code
 * (1, 0)}, with the angle in the current angle mode and in degrees and radians, and the three
 * values. A drag snaps to the multiples of 15° within 3° (Alt drags freely); the arrow keys step
 * 15° and with Shift 1°; the field reads an angle typed in the current mode on the worker ({@link
 * Document#readAngle}). The values follow a drag from {@code double}s and settle, when it ends, to
 * the engine's ({@link Document#trigValues}): exact at a special angle, decimals at the document's
 * digits otherwise. Send to cell inserts a special angle's call into the current cell. Nothing here
 * is part of the document: the view is not saved and changes nothing but the cell a call is sent
 * to. The picture is painted on a canvas of its own from the angle, in the colours of the CSS
 * properties {@code -unit-circle-ink}, {@code -unit-circle-axis}, {@code -unit-circle-cosine},
 * {@code -unit-circle-sine} and {@code -unit-circle-tangent}, which the app's stylesheet sets for
 * each theme. Touched only on the FX thread.
 */
public final class UnitCirclePanel extends VBox {

  /** The largest width and height of the picture, in pixels; it shrinks to the room there is. */
  static final double SIZE = 240;

  /** The smallest width and height of the picture, in pixels. */
  static final double MIN_SIZE = 100;

  /** The circle's radius over the picture's size: room for a tangent of about ±1.5 beside it. */
  static final double RADIUS = 0.32;

  private static final StyleablePropertyFactory<UnitCirclePanel> CSS =
      new StyleablePropertyFactory<>(VBox.getClassCssMetaData());

  private final StyleableProperty<Color> ink =
      CSS.createStyleableColorProperty(
          this, "inkColor", "-unit-circle-ink", p -> p.ink, Color.web("#1d1d1f"));
  private final StyleableProperty<Color> axis =
      CSS.createStyleableColorProperty(
          this, "axisColor", "-unit-circle-axis", p -> p.axis, Color.web("#6e6e73"));
  private final StyleableProperty<Color> cosine =
      CSS.createStyleableColorProperty(
          this, "cosineColor", "-unit-circle-cosine", p -> p.cosine, Color.web("#1f77b4"));
  private final StyleableProperty<Color> sine =
      CSS.createStyleableColorProperty(
          this, "sineColor", "-unit-circle-sine", p -> p.sine, Color.web("#d62728"));
  private final StyleableProperty<Color> tangent =
      CSS.createStyleableColorProperty(
          this, "tangentColor", "-unit-circle-tangent", p -> p.tangent, Color.web("#2ca02c"));

  private final Document document;
  private final Canvas canvas = new Canvas(SIZE, SIZE);
  private final TextField field = new TextField();
  private final Label shown = new Label();
  private final Label refused = new Label();
  private final List<Label> texts = new ArrayList<>();
  private final List<MathView> views = new ArrayList<>();
  // the text of the value each view shows typeset
  private final List<String> typesetTexts = new ArrayList<>(List.of("", "", ""));
  private final MenuButton send = new MenuButton(Messages.text("unitcircle.send"));

  private double radians;
  private OptionalInt special = OptionalInt.of(0);
  // the angle as typed, until it is dragged or stepped
  private TrigValues.Angle typed;
  private AngleMode mode = AngleMode.RADIANS;
  private int digits = DocumentSettings.DEFAULT_DIGITS;
  // the values shown are the engine's for the current angle and digits, or asked for
  private boolean settled;
  // the values shown are the engine's, not the live ones
  private boolean engine;
  private long request;

  /**
   * A panel, at the angle 0.
   *
   * @param document the document whose worker evaluates, and whose settings it follows
   * @param insert inserts a call into the current cell
   */
  public UnitCirclePanel(Document document, Consumer<String> insert) {
    this.document = document;
    setId("unit-circle");
    getStyleClass().addAll("side-panel", "unit-circle");
    Label title = new Label(Messages.text("unitcircle.title"));
    title.getStyleClass().add("side-panel-title");

    canvas.setId("unit-circle-canvas");
    canvas.setFocusTraversable(true);
    canvas.setAccessibleRole(AccessibleRole.TEXT);
    canvas.setAccessibleHelp(Messages.text("unitCircle.accessible"));
    canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::drag);
    canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::drag);
    canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> settle());
    canvas.addEventHandler(KeyEvent.KEY_PRESSED, this::key);
    canvas.focusedProperty().subscribe(this::paint);
    StackPane plane = new StackPane(canvas);
    plane.getStyleClass().add("unit-circle-plane");
    plane.setMinSize(MIN_SIZE, MIN_SIZE);
    plane.setPrefSize(SIZE, SIZE);
    plane.setMaxSize(SIZE, SIZE);
    VBox.setVgrow(plane, Priority.ALWAYS);
    // the picture is square, as large as the room the panel has for it
    canvas.setManaged(false);
    plane
        .layoutBoundsProperty()
        .subscribe(
            b -> {
              double size = Math.max(0, Math.min(b.getWidth(), b.getHeight()));
              canvas.setWidth(size);
              canvas.setHeight(size);
              canvas.relocate((b.getWidth() - size) / 2, (b.getHeight() - size) / 2);
              paint();
            });
    Tooltip.install(plane, new Tooltip(Messages.text("unitcircle.tip")));

    field.setId("unit-circle-angle");
    field.setOnAction(e -> read());
    shown.setId("unit-circle-shown");
    shown.getStyleClass().add("unit-circle-shown");
    refused.setId("unit-circle-refused");
    refused.getStyleClass().add("algebra-error");
    refused.setWrapText(true);
    refused.managedProperty().bind(refused.visibleProperty());
    refused.setVisible(false);

    GridPane values = new GridPane();
    values.getStyleClass().add("unit-circle-values");
    String[] names = {"unitcircle.sin", "unitcircle.cos", "unitcircle.tan"};
    for (int i = 0; i < TrigValues.FUNCTIONS.size(); i++) {
      String f = TrigValues.FUNCTIONS.get(i);
      Label name = new Label(Messages.text(names[i]));
      name.getStyleClass().add("inspector-name");
      Label text = new Label();
      text.getStyleClass().add("algebra-text");
      MathView view = new MathView();
      view.setVisible(false);
      StackPane value = new StackPane(text, view);
      value.setId("unit-circle-" + f);
      StackPane.setAlignment(text, Pos.CENTER_LEFT);
      StackPane.setAlignment(view, Pos.CENTER_LEFT);
      texts.add(text);
      views.add(view);
      // sin and cos side by side, tan under them
      values.add(name, 2 * (i % 2), i / 2);
      values.add(value, 2 * (i % 2) + 1, i / 2);
      MenuItem item = new MenuItem(f);
      item.setId("unit-circle-send-" + f);
      item.setOnAction(e -> special.ifPresent(k -> insert.accept(UnitCircle.call(f, k, mode))));
      send.getItems().add(item);
    }
    send.setId("unit-circle-send");
    send.setTooltip(new Tooltip(Messages.text("unitcircle.send.tip")));
    send.setAccessibleHelp(Messages.text("unitcircle.send.tip"));

    HBox entry = new HBox(6, field, send);
    HBox.setHgrow(field, Priority.ALWAYS);
    getChildren().addAll(title, plane, entry, refused, shown, values);
    for (StyleableProperty<Color> c : List.of(ink, axis, cosine, sine, tangent)) {
      ((ObservableValue<?>) c).addListener((o, was, is) -> paint());
    }
    visibleProperty().addListener((o, was, is) -> settleIfShown());
    moveTo(0, OptionalInt.of(0), null);
  }

  /**
   * Follows the document's settings: the angle mode the angle is shown and typed in, and the digits
   * of the decimal values.
   *
   * @param settings the settings
   */
  public void show(DocumentSettings settings) {
    if (settings.angleMode() != mode) {
      mode = settings.angleMode();
      showAngle();
    }
    if (settings.numberDigits() != digits) {
      digits = settings.numberDigits();
      if (special.isEmpty()) {
        settled = false;
      }
    }
    settleIfShown();
  }

  private void drag(MouseEvent e) {
    canvas.requestFocus();
    double half = canvas.getWidth() / 2;
    double at = UnitCircle.angleAt(e.getX() - half, half - e.getY());
    OptionalInt snapped = e.isAltDown() ? OptionalInt.empty() : UnitCircle.snap(at);
    moveTo(snapped.isPresent() ? snapped.getAsInt() * Math.PI / 12 : at, snapped, null);
    e.consume();
  }

  private void key(KeyEvent e) {
    switch (e.getCode()) {
      case UP, RIGHT, DOWN, LEFT -> {
        boolean up = e.getCode() == KeyCode.UP || e.getCode() == KeyCode.RIGHT;
        double next = UnitCircle.step(radians, up, e.isShiftDown());
        moveTo(next, UnitCircle.special(next), null);
        settle();
        e.consume();
      }
      default -> {}
    }
  }

  /** Reads the typed angle on the worker; the angle moves when it arrives. */
  private void read() {
    String text = field.getText();
    if (text.isBlank()) {
      return;
    }
    document.readAngle(
        text,
        angle -> {
          // reduced and judged special exactly on the worker, never by the double
          moveTo(UnitCircle.normalize(angle.value()), angle.special(), angle);
          settle();
        },
        message -> {
          refused.setText(message);
          refused.setVisible(true);
        });
  }

  /** Moves the angle, showing the values read from {@code double}s until it settles. */
  private void moveTo(double at, OptionalInt k, TrigValues.Angle typed) {
    radians = k.isPresent() ? k.getAsInt() * Math.PI / 12 : at;
    special = k;
    refused.setVisible(false);
    this.typed = typed;
    settled = false;
    engine = false;
    request++;
    showAngle();
    double[] live = {Math.sin(radians), Math.cos(radians), UnitCircle.tan(radians)};
    for (int i = 0; i < live.length; i++) {
      double v = k.isPresent() && Math.abs(live[i]) < 1e-12 ? 0 : live[i];
      showText(i, Double.isNaN(v) ? null : UnitCircle.live(v, digits));
    }
    send.setDisable(k.isEmpty());
    paint();
  }

  private void settleIfShown() {
    if (!settled && isVisible()) {
      settle();
    }
  }

  /** Asks the worker for the engine's values of the angle; a later request supersedes it. */
  private void settle() {
    settled = true;
    long asked = ++request;
    TrigValues.Angle angle = typed != null ? typed : TrigValues.Angle.of(radians);
    document.trigValues(
        angle,
        special,
        values -> {
          if (asked != request) {
            return;
          }
          engine = true;
          for (int i = 0; i < values.size(); i++) {
            TrigValues.Value v = values.get(i);
            if (v.box() != null) {
              views.get(i).setBox(v.box());
              typesetTexts.set(i, v.text());
              views.get(i).setVisible(true);
              texts.get(i).setVisible(false);
            } else {
              showText(i, v.text());
            }
          }
        });
  }

  private void showText(int i, String text) {
    views.get(i).setVisible(false);
    texts.get(i).setVisible(true);
    texts.get(i).setText(text == null ? Messages.text("unitcircle.undefined") : text);
  }

  /** The angle in the current mode first, then in degrees and radians, and the field's prompt. */
  private void showAngle() {
    List<String> forms = new ArrayList<>();
    for (AngleMode m : List.of(mode, AngleMode.DEGREES, AngleMode.RADIANS)) {
      String number =
          special.isPresent()
              ? UnitCircle.inUnit(special.getAsInt(), m)
              : UnitCircle.decimal(radians, m);
      String form =
          switch (m) {
            case DEGREES -> Messages.text("unitcircle.degrees", number);
            case GRADIANS -> Messages.text("unitcircle.gradians", number);
            case RADIANS ->
                special.isPresent() ? number : Messages.text("unitcircle.radians", number);
          };
      if (!forms.contains(form)) {
        forms.add(form);
      }
    }
    shown.setText(
        Messages.text(
            special.isPresent() ? "unitcircle.angle" : "unitcircle.angle.about",
            String.join(special.isPresent() ? " = " : " ≈ ", forms)));
    field.setPromptText(
        switch (mode) {
          case DEGREES -> Messages.text("unitcircle.prompt.degrees");
          case RADIANS -> Messages.text("unitcircle.prompt.radians");
          case GRADIANS -> Messages.text("unitcircle.prompt.gradians");
        });
  }

  /** Paints the picture from the angle, in the theme's colours. */
  private void paint() {
    GraphicsContext g = canvas.getGraphicsContext2D();
    double size = canvas.getWidth();
    double radius = RADIUS * size;
    g.clearRect(0, 0, size, size);
    double cx = size / 2;
    double cy = size / 2;
    double cos = Math.cos(radians);
    double sin = Math.sin(radians);
    double px = cx + radius * cos;
    double py = cy - radius * sin;
    g.setLineCap(StrokeLineCap.ROUND);

    // the axes, the circle and the special angles' ticks
    g.setStroke(axis.getValue());
    g.setLineWidth(1);
    g.strokeLine(4, cy, size - 4, cy);
    g.strokeLine(cx, 4, cx, size - 4);
    g.setFill(axis.getValue());
    for (int k = 0; k < UnitCircle.SPECIAL; k++) {
      double a = k * Math.PI / 12;
      double r = k % 6 == 0 ? 3 : k % 2 == 0 ? 2.25 : 1.5;
      g.fillOval(cx + radius * Math.cos(a) - r, cy - radius * Math.sin(a) - r, 2 * r, 2 * r);
    }
    g.setStroke(ink.getValue());
    g.setLineWidth(1.5);
    g.strokeOval(cx - radius, cy - radius, 2 * radius, 2 * radius);

    // the tangent: the radius produced to the tangent line at (1, 0), and the segment on it
    double tan = UnitCircle.tan(radians);
    if (!Double.isNaN(tan)) {
      double ty = Math.max(-2, Math.min(size + 2, cy - radius * tan));
      g.setStroke(axis.getValue());
      g.setLineWidth(1);
      g.setLineDashes(3, 3);
      g.strokeLine(cx + radius, 4, cx + radius, size - 4);
      g.strokeLine(cx, cy, cx + radius, cy - radius * tan);
      g.setLineDashes();
      g.setStroke(tangent.getValue());
      g.setLineWidth(3);
      g.strokeLine(cx + radius, cy, cx + radius, ty);
    }

    // the cosine along the x axis, the sine from it up to the point
    g.setLineWidth(3);
    g.setStroke(cosine.getValue());
    g.strokeLine(cx, cy, px, cy);
    g.setStroke(sine.getValue());
    g.strokeLine(px, cy, px, py);

    // the angle's arc and the radius to the point
    g.setStroke(ink.getValue());
    g.setLineWidth(1.5);
    g.strokeArc(cx - 16, cy - 16, 32, 32, 0, Math.toDegrees(radians), ArcType.OPEN);
    g.strokeLine(cx, cy, px, py);
    g.setFill(ink.getValue());
    double r = special.isPresent() ? 5.5 : 4.5;
    g.fillOval(px - r, py - r, 2 * r, 2 * r);
    if (canvas.isFocused()) {
      g.setStroke(axis.getValue());
      g.setLineWidth(1);
      g.strokeOval(px - r - 3, py - r - 3, 2 * r + 6, 2 * r + 6);
    }

    // the names of the three segments
    g.setFont(Font.font(11));
    g.setTextBaseline(VPos.CENTER);
    g.setTextAlign(TextAlignment.CENTER);
    g.setFill(cosine.getValue());
    g.fillText("cos", (cx + px) / 2, cy + (sin >= 0 ? 10 : -10));
    g.setFill(sine.getValue());
    // inside the circle, clear of the tangent line
    g.setTextAlign(cos >= 0 ? TextAlignment.RIGHT : TextAlignment.LEFT);
    g.fillText("sin", px + (cos >= 0 ? -5 : 5), (cy + py) / 2);
    if (!Double.isNaN(tan) && Math.abs(tan) > 0.15) {
      g.setFill(tangent.getValue());
      g.setTextAlign(TextAlignment.LEFT);
      double ty = Math.max(12, Math.min(size - 12, cy - radius * tan / 2));
      g.fillText("tan", cx + radius + 5, ty);
    }
  }

  /**
   * The angle, for the tests.
   *
   * @return the angle in radians, in {@code [0, 2π)}
   */
  public double angle() {
    return radians;
  }

  /**
   * The special angle the angle is, for the tests.
   *
   * @return its multiple of 15°, or empty
   */
  public OptionalInt special() {
    return special;
  }

  /**
   * What the three values show, for the tests: the engine's text once settled, else the live text.
   *
   * @return the texts of sin, cos and tan
   */
  public List<String> valueTexts() {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < texts.size(); i++) {
      MathView v = views.get(i);
      out.add(v.isVisible() ? typesetTexts.get(i) : texts.get(i).getText());
    }
    return out;
  }

  /**
   * Whether the values shown are the engine's for the angle, for the tests.
   *
   * @return true once they have settled
   */
  public boolean showsEngineValues() {
    return engine;
  }

  /**
   * The send button, whose items insert the calls, for the tests.
   *
   * @return the button
   */
  public MenuButton sendButton() {
    return send;
  }

  /**
   * The CSS properties of the class.
   *
   * @return VBox's and the picture's colours
   */
  public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
    return CSS.getCssMetaData();
  }

  @Override
  public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
    return getClassCssMetaData();
  }
}

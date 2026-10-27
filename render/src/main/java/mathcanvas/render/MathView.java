package mathcanvas.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.font.OpenTypeFont;
import mathcanvas.render.font.Outline;
import mathcanvas.render.typeset.Box;
import mathcanvas.render.typeset.Fallback;
import mathcanvas.render.typeset.Typesetter;

/**
 * A typeset expression as a JavaFX node: the layout tree set by the {@link Typesetter} and drawn
 * from the font's outlines, every glyph in one {@code Path} (style class {@code math-glyph}), the
 * bars in another ({@code math-rule}), text the font lacks as {@code Text} nodes ({@code
 * math-glyph}), and the selected subexpression highlighted by a rectangle behind them ({@code
 * math-selection}). The colours are the looked-up colours {@code -math-ink} and {@code
 * -math-selection-fill}, which a theme sets on {@code .math-view}. A click selects the deepest
 * subexpression under it, or clears the selection. Typesetting happens on the FX thread when the
 * layout, the size or the style changes; like every node, the view is touched only there.
 */
public final class MathView extends Region {

  private static final Typesetter TYPESETTER =
      new Typesetter(OpenTypeFont.stix(), MathView::measure);

  /** The least font size, in pixels: a smaller (or undefined) size is drawn at this one. */
  public static final double MIN_FONT_SIZE = 4;

  private final ObjectProperty<Layout> layout = new SimpleObjectProperty<>(this, "layout");
  private final DoubleProperty fontSize = new SimpleDoubleProperty(this, "fontSize", 20);
  private final BooleanProperty displayStyle =
      new SimpleBooleanProperty(this, "displayStyle", true);
  private final ObjectProperty<Path> selection = new SimpleObjectProperty<>(this, "selection");

  private final Rectangle highlight = new Rectangle();
  private final javafx.scene.shape.Path glyphs = new javafx.scene.shape.Path();
  private final javafx.scene.shape.Path rules = new javafx.scene.shape.Path();
  private final Group texts = new Group();
  private final Group content = new Group(highlight, rules, glyphs, texts);
  private Box box;

  /** Creates an empty view. */
  public MathView() {
    getStyleClass().add("math-view");
    highlight.getStyleClass().add("math-selection");
    highlight.setVisible(false);
    glyphs.getStyleClass().add("math-glyph");
    glyphs.setStroke(null);
    rules.getStyleClass().add("math-rule");
    rules.setStroke(null);
    getChildren().add(content);
    layout.addListener(
        (o, was, is) -> {
          selection.set(null);
          typeset();
        });
    fontSize.addListener((o, was, is) -> typeset());
    displayStyle.addListener((o, was, is) -> typeset());
    selection.addListener((o, was, is) -> highlight());
    addEventHandler(
        MouseEvent.MOUSE_CLICKED, e -> select(hitTest(e.getX(), e.getY()).orElse(null)));
  }

  /**
   * Creates a view of a layout.
   *
   * @param layout the layout
   */
  public MathView(Layout layout) {
    this();
    setLayout(layout);
  }

  /**
   * The layout shown; null shows nothing.
   *
   * @return the property
   */
  public ObjectProperty<Layout> layoutProperty() {
    return layout;
  }

  /**
   * Returns the layout shown.
   *
   * @return the layout, or null
   */
  public Layout getLayout() {
    return layout.get();
  }

  /**
   * Shows a layout; a new layout clears the selection.
   *
   * @param value the layout, or null
   */
  public void setLayout(Layout value) {
    layout.set(value);
  }

  /**
   * The font size in pixels: one em. Defaults to 20; a size below {@link #MIN_FONT_SIZE}, or not a
   * number, is drawn at the minimum.
   *
   * @return the property
   */
  public DoubleProperty fontSizeProperty() {
    return fontSize;
  }

  /**
   * Whether the layout is set in display style, the default, rather than text style.
   *
   * @return the property
   */
  public BooleanProperty displayStyleProperty() {
    return displayStyle;
  }

  /**
   * The path of the selected subexpression in the printed tree, or null for none.
   *
   * @return the property
   */
  public ObjectProperty<Path> selectionProperty() {
    return selection;
  }

  /**
   * Returns the selected path.
   *
   * @return the path, or null
   */
  public Path getSelection() {
    return selection.get();
  }

  /**
   * Selects a subexpression by its path, or clears the selection.
   *
   * @param path the path, or null
   */
  public void select(Path path) {
    selection.set(path);
  }

  /**
   * Returns the box tree on screen, its origin at the left end of the baseline.
   *
   * @return the box tree, or empty when nothing is shown
   */
  public Optional<Box> box() {
    return Optional.ofNullable(box);
  }

  /**
   * Returns the deepest subexpression at a point.
   *
   * @param x the x in this view's coordinates
   * @param y the y in this view's coordinates
   * @return its path, or empty when there is none there
   */
  public Optional<Path> hitTest(double x, double y) {
    if (box == null) {
      return Optional.empty();
    }
    return box.hitTest(x - content.getLayoutX(), y - content.getLayoutY());
  }

  /**
   * Returns the position of the box tree's origin in this view.
   *
   * @return the origin, in this view's coordinates
   */
  public Point2D origin() {
    return new Point2D(content.getLayoutX(), content.getLayoutY());
  }

  @Override
  public String getUserAgentStylesheet() {
    return MathView.class.getResource("math-view.css").toExternalForm();
  }

  // Layout.

  @Override
  protected double computePrefWidth(double height) {
    return snappedLeftInset() + (box == null ? 0 : snapSizeX(box.width())) + snappedRightInset();
  }

  @Override
  protected double computePrefHeight(double width) {
    return snappedTopInset()
        + (box == null ? 0 : snapSizeY(box.ascent() + box.descent()))
        + snappedBottomInset();
  }

  @Override
  protected double computeMinWidth(double height) {
    return computePrefWidth(height);
  }

  @Override
  protected double computeMinHeight(double width) {
    return computePrefHeight(width);
  }

  @Override
  public double getBaselineOffset() {
    return snappedTopInset() + (box == null ? 0 : snapSizeY(box.ascent()));
  }

  @Override
  protected void layoutChildren() {
    content.setLayoutX(snappedLeftInset() - (box == null ? 0 : box.x()));
    content.setLayoutY(getBaselineOffset());
  }

  // Drawing.

  private void typeset() {
    glyphs.getElements().clear();
    rules.getElements().clear();
    texts.getChildren().clear();
    Layout l = layout.get();
    double size = fontSize.get() >= MIN_FONT_SIZE ? fontSize.get() : MIN_FONT_SIZE;
    box = l == null ? null : TYPESETTER.typeset(l, size, displayStyle.get());
    if (box != null) {
      List<PathElement> glyphElements = new ArrayList<>();
      List<PathElement> ruleElements = new ArrayList<>();
      draw(box, glyphElements, ruleElements);
      glyphs.getElements().setAll(glyphElements);
      rules.getElements().setAll(ruleElements);
    }
    highlight();
    requestLayout();
  }

  private void draw(Box b, List<PathElement> glyphElements, List<PathElement> ruleElements) {
    switch (b) {
      case Box.Group g -> {
        for (Box child : g.children()) {
          draw(child, glyphElements, ruleElements);
        }
      }
      case Box.GlyphBox g -> glyph(g, glyphElements);
      case Box.RuleBox r -> {
        ruleElements.add(new MoveTo(r.x(), r.top()));
        ruleElements.add(new LineTo(r.x() + r.width(), r.top()));
        ruleElements.add(new LineTo(r.x() + r.width(), r.bottom()));
        ruleElements.add(new LineTo(r.x(), r.bottom()));
        ruleElements.add(new ClosePath());
      }
      case Box.TextBox t -> {
        Text text = new Text(t.x(), t.baseline(), t.text());
        text.setFont(Font.font(t.size()));
        text.getStyleClass().add("math-glyph");
        texts.getChildren().add(text);
      }
    }
  }

  /** A glyph's outline, from the font's cache in font units, scaled and placed, y down. */
  private static void glyph(Box.GlyphBox g, List<PathElement> out) {
    double x = g.x();
    double y = g.baseline();
    double s = g.scale();
    for (Outline.Command command : OpenTypeFont.stix().outline(g.glyph()).commands()) {
      out.add(
          switch (command) {
            case Outline.MoveTo m -> new MoveTo(x + m.x() * s, y - m.y() * s);
            case Outline.LineTo l -> new LineTo(x + l.x() * s, y - l.y() * s);
            case Outline.QuadTo q ->
                new QuadCurveTo(x + q.cx() * s, y - q.cy() * s, x + q.x() * s, y - q.y() * s);
            case Outline.Close c -> new ClosePath();
          });
    }
  }

  private void highlight() {
    Path path = selection.get();
    Optional<Box> selected = path == null || box == null ? Optional.empty() : box.find(path);
    highlight.setVisible(selected.isPresent());
    selected.ifPresent(
        b -> {
          highlight.setX(b.x());
          highlight.setY(b.top());
          highlight.setWidth(b.width());
          highlight.setHeight(b.ascent() + b.descent());
        });
  }

  /**
   * Returns the selection rectangle's bounds in this view.
   *
   * @return the bounds, or empty when nothing is selected
   */
  public Optional<Bounds> selectionBounds() {
    return highlight.isVisible()
        ? Optional.of(content.localToParent(highlight.getBoundsInParent()))
        : Optional.empty();
  }

  private static Fallback.Metrics measure(String text, double size) {
    Text t = new Text(text);
    t.setFont(Font.font(size));
    Bounds b = t.getLayoutBounds();
    return new Fallback.Metrics(b.getWidth(), -b.getMinY(), b.getMaxY());
  }
}

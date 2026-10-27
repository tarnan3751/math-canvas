package mathcanvas.render.font;

import java.util.List;

/**
 * A glyph's outline: closed contours of straight and quadratic segments, in font units with y up,
 * the TrueType implied on-curve points made explicit. Filled with the non-zero winding rule.
 *
 * @param commands the commands, each contour a move, segments and a close
 */
public record Outline(List<Command> commands) {

  /** Copies the commands. */
  public Outline {
    commands = List.copyOf(commands);
  }

  /** A path command. */
  public sealed interface Command permits MoveTo, LineTo, QuadTo, Close {}

  /**
   * Starts a contour.
   *
   * @param x the x
   * @param y the y
   */
  public record MoveTo(double x, double y) implements Command {}

  /**
   * A straight segment.
   *
   * @param x the end's x
   * @param y the end's y
   */
  public record LineTo(double x, double y) implements Command {}

  /**
   * A quadratic segment.
   *
   * @param cx the control point's x
   * @param cy the control point's y
   * @param x the end's x
   * @param y the end's y
   */
  public record QuadTo(double cx, double cy, double x, double y) implements Command {}

  /** Closes a contour. */
  public record Close() implements Command {}

  /**
   * Returns the number of contours.
   *
   * @return the count
   */
  public int contours() {
    return (int) commands.stream().filter(c -> c instanceof MoveTo).count();
  }
}

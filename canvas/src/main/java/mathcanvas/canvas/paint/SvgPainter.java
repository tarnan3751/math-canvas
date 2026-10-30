package mathcanvas.canvas.paint;

import java.math.BigDecimal;
import mathcanvas.canvas.geom.Viewport;

/**
 * The SVG backend: each stroke, fill, point and text becomes one element of a standalone SVG
 * document the size of the viewport, in its pixel coordinates (y down, text upright), with numbers
 * written to two decimals without trailing zeros, so the same calls always give the same bytes.
 *
 * <p>A stroke is {@code <path fill="none" stroke=…>} with round caps and joins and, when dashed,
 * {@code stroke-dasharray}; a fill is {@code <path fill=…>} of closed subpaths; a point is a {@code
 * <circle>}; a text is a {@code <text>} in the platform's sans-serif font with {@code text-anchor}
 * and {@code dominant-baseline} for its anchor. Opacity below 1 is written as {@code
 * stroke-opacity} or {@code fill-opacity}. A path left empty by clipping writes nothing.
 */
public final class SvgPainter extends ScreenPainter {

  private final StringBuilder body = new StringBuilder();
  private final StringBuilder path = new StringBuilder();

  /**
   * A painter writing a drawing through a viewport.
   *
   * @param viewport the viewport, whose pixel size is the document's
   */
  public SvgPainter(Viewport viewport) {
    super(viewport);
  }

  /**
   * The document drawn so far.
   *
   * @return the SVG document, ending in a newline
   */
  public String document() {
    String w = number(viewport().width());
    String h = number(viewport().height());
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""
        + w
        + "\" height=\""
        + h
        + "\" viewBox=\"0 0 "
        + w
        + " "
        + h
        + "\">\n"
        + body
        + "</svg>\n";
  }

  @Override
  protected void beginScreenPath() {
    path.setLength(0);
  }

  @Override
  protected void moveToScreen(double x, double y) {
    path.append('M').append(number(x)).append(' ').append(number(y));
  }

  @Override
  protected void lineToScreen(double x, double y) {
    path.append('L').append(number(x)).append(' ').append(number(y));
  }

  @Override
  protected void closeScreenSubpath() {
    path.append('Z');
  }

  @Override
  protected void strokeScreenPath(Stroke stroke) {
    if (path.isEmpty()) {
      return;
    }
    body.append("<path d=\"")
        .append(path)
        .append("\" fill=\"none\" stroke=\"")
        .append(stroke.colour().hex())
        .append('"');
    opacity("stroke-opacity", stroke.colour());
    body.append(" stroke-width=\"").append(number(stroke.width())).append('"');
    if (!stroke.dash().isEmpty()) {
      body.append(" stroke-dasharray=\"");
      for (int i = 0; i < stroke.dash().size(); i++) {
        body.append(i == 0 ? "" : " ").append(number(stroke.dash().get(i)));
      }
      body.append('"');
    }
    body.append(" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n");
  }

  @Override
  protected void fillScreenPath(Rgba colour) {
    if (path.isEmpty()) {
      return;
    }
    body.append("<path d=\"").append(path).append("\" fill=\"").append(colour.hex()).append('"');
    opacity("fill-opacity", colour);
    body.append("/>\n");
  }

  @Override
  protected void pointScreen(double x, double y, double radius, Rgba colour) {
    body.append("<circle cx=\"")
        .append(number(x))
        .append("\" cy=\"")
        .append(number(y))
        .append("\" r=\"")
        .append(number(radius))
        .append("\" fill=\"")
        .append(colour.hex())
        .append('"');
    opacity("fill-opacity", colour);
    body.append("/>\n");
  }

  @Override
  protected void textScreen(String text, double x, double y, Anchor anchor, TextStyle style) {
    body.append("<text x=\"")
        .append(number(x))
        .append("\" y=\"")
        .append(number(y))
        .append("\" font-family=\"sans-serif\" font-size=\"")
        .append(number(style.size()))
        .append("\" fill=\"")
        .append(style.colour().hex())
        .append('"');
    opacity("fill-opacity", style.colour());
    body.append(
        switch (anchor.horizontal()) {
          case START -> "";
          case MIDDLE -> " text-anchor=\"middle\"";
          case END -> " text-anchor=\"end\"";
        });
    body.append(
        switch (anchor.vertical()) {
          case TOP -> " dominant-baseline=\"hanging\"";
          case MIDDLE -> " dominant-baseline=\"central\"";
          case BASELINE -> "";
          case BOTTOM -> " dominant-baseline=\"text-after-edge\"";
        });
    body.append('>').append(escape(text)).append("</text>\n");
  }

  private void opacity(String attribute, Rgba colour) {
    if (colour.opacity() < 1) {
      body.append(' ').append(attribute).append("=\"").append(number(colour.opacity())).append('"');
    }
  }

  /** Two decimals, no trailing zeros, no exponent, no negative zero. */
  static String number(double v) {
    return BigDecimal.valueOf(Math.round(v * 100), 2).stripTrailingZeros().toPlainString();
  }

  private static String escape(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}

package mathcanvas.canvas.paint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.canvas.geom.Viewport;
import org.junit.jupiter.api.Test;

class GridPainterTest {

  private static String draw(Viewport v) {
    SvgPainter p = new SvgPainter(v);
    GridPainter.paint(p, CanvasTheme.LIGHT);
    return p.document();
  }

  private static long count(String s, String regex) {
    Matcher m = Pattern.compile(regex).matcher(s);
    long n = 0;
    while (m.find()) {
      n++;
    }
    return n;
  }

  @Test
  void drawsMinorGridGridAxesAndLabels() {
    // 20 px per unit on both axes: step 5, minor step 1.
    String svg = draw(new Viewport(-10, 10, -10, 10, 400, 400, true));
    assertThat(count(svg, "<path ")).isEqualTo(3);
    // Labels at ±5 on each axis and the origin's 0; those at ±10 are on the edges, left out.
    assertThat(count(svg, "<text ")).isEqualTo(5);
    assertThat(svg).contains(">−5</text>", ">5</text>", ">0</text>").doesNotContain(">10</text>");
  }

  @Test
  void pinsLabelsWhenAnAxisIsOffScreen() {
    String svg = draw(new Viewport(100, 120, 100, 120, 400, 400, true));
    // No axis path, and no origin label; the y labels sit inside the left edge.
    assertThat(count(svg, "<path ")).isEqualTo(2);
    assertThat(svg).doesNotContain(">0</text>");
    assertThat(svg).contains("x=\"4\" y=\"200\"");
  }

  @Test
  void xLabelsFarFromTheOriginDoNotOverlap() {
    // 1e7 ± 1e-3 across 800 px: twelve-digit labels; neighbouring label centres must be further
    // apart than the widest label plus the gap.
    Viewport v = new Viewport(1e7 - 1e-3, 1e7 + 1e-3, -1, 1, 800, 400, false);
    String svg = draw(v);
    Matcher m =
        Pattern.compile(
                "<text x=\"([0-9.]+)\" y=\"[0-9.]+\"[^>]*text-anchor=\"middle\"[^>]*>([^<]*)<")
            .matcher(svg);
    double previous = Double.NaN;
    double widest = 0;
    double closest = Double.POSITIVE_INFINITY;
    int labels = 0;
    while (m.find()) {
      double x = Double.parseDouble(m.group(1));
      widest =
          Math.max(
              widest,
              mathcanvas.canvas.geom.Ticks.estimate(GridPainter.LABEL_SIZE)
                  .applyAsDouble(m.group(2)));
      if (!Double.isNaN(previous)) {
        closest = Math.min(closest, x - previous);
      }
      previous = x;
      labels++;
    }
    assertThat(labels).isGreaterThanOrEqualTo(2);
    assertThat(widest + mathcanvas.canvas.geom.Ticks.LABEL_GAP).isLessThanOrEqualTo(closest);
  }
}

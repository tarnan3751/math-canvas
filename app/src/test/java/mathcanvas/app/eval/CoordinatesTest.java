package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.print.Format;
import org.junit.jupiter.api.Test;

class CoordinatesTest {

  private static final DocumentSettings EXACT = DocumentSettings.DEFAULT;

  private static DocumentSettings with(NumberFormat format, int digits) {
    return new DocumentSettings(EXACT.precision(), EXACT.angleMode(), format, digits);
  }

  private static String row(double x, double y, DocumentSettings settings) {
    ObjectId id = new ObjectId(1);
    return new AlgebraRows()
        .of(
            new FreePoint(id, Style.DEFAULT, x, y),
            CanvasState.EMPTY,
            Map.of(id, "A"),
            new Geometry.Point(x, y),
            Optional.empty(),
            Coordinates.format(settings),
            Cancellation.NEVER)
        .text();
  }

  @Test
  void aCoordinateIsNeverExactSoTheExactFormatsWriteDecimals() {
    assertThat(Coordinates.format(EXACT)).isEqualTo(Format.decimal(10));
    assertThat(Coordinates.format(with(NumberFormat.FRACTION, 6))).isEqualTo(Format.decimal(6));
    assertThat(Coordinates.format(with(NumberFormat.DECIMAL, 4))).isEqualTo(Format.decimal(4));
    assertThat(Coordinates.format(with(NumberFormat.SCIENTIFIC, 3)))
        .isEqualTo(Format.scientific(3));
  }

  @Test
  void aRootsRowIsWrittenToTheDocumentsDigitsWithItsNoiseAsZero() {
    double root = -Math.sqrt(3);
    double noise = 1.2e-16;
    assertThat(row(root, noise, EXACT)).isEqualTo("A = ⟨-1.732050808, 0⟩");
    assertThat(row(root, noise, with(NumberFormat.DECIMAL, 4))).isEqualTo("A = ⟨-1.732, 0⟩");
    // below the point's scale only: a small coordinate of a small point is kept
    assertThat(row(1e-9, 2e-9, with(NumberFormat.DECIMAL, 4))).isNotEqualTo("A = ⟨0, 0⟩");
    assertThat(row(1e-13, -1e-14, EXACT)).isEqualTo("A = ⟨0, 0⟩");
  }

  @Test
  void theCoordinateEntrysEchoUsesTheSameFormat() {
    assertThat(Coordinates.text(Math.PI, EXACT)).isEqualTo("3.141592654");
    assertThat(Coordinates.text(Math.PI, with(NumberFormat.DECIMAL, 3))).isEqualTo("3.14");
    assertThat(Coordinates.text(3e-17, EXACT)).isEqualTo("0");
  }
}

package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import mathcanvas.canvas.geom.Viewport;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CanvasCodecProperties {

  /**
   * A loose sanity bound on a bound's move, in units in the last place of the largest coordinate.
   */
  private static final int ULPS = 16;

  /**
   * A canvas survives being written as text and read back: every kind, ids with gaps, anchors and
   * last positions with NaN, exact slider decimals, doubles over their whole range, and expressions
   * in canonical form, calls of user functions included, and the 3D view's camera; the objects'
   * text is the same when written again.
   */
  @Property(tries = 300)
  void aCanvasSurvivesWritingAndReading(@ForAll("canvases") StoredCanvas canvas) throws Exception {
    Json.Obj written = CanvasCodec.encode(canvas);
    StoredCanvas read = CanvasCodec.decode(JsonIo.parse(JsonIo.text(written)));

    assertThat(read.state()).isEqualTo(canvas.state());
    assertThat(read.names()).isEqualTo(canvas.names());
    assertThat(read.camera()).isEqualTo(canvas.camera());
    Json.Obj again = CanvasCodec.encode(read);
    assertThat(again.member("objects")).isEqualTo(written.member("objects"));
    assertThat(again.member("nextId")).isEqualTo(written.member("nextId"));
  }

  /**
   * A canvas once read back, its viewport squared as reading squares it, reads back whole as itself
   * and is written again as the same text.
   */
  @Property(tries = 300)
  void aCanvasReadBackIsWrittenAgainAsTheSameBytes(@ForAll("canvases") StoredCanvas canvas)
      throws Exception {
    StoredCanvas once = reread(canvas);
    String written = JsonIo.text(CanvasCodec.encode(once));
    StoredCanvas twice = CanvasCodec.decode(JsonIo.parse(written));

    assertThat(twice).isEqualTo(once);
    assertThat(JsonIo.text(CanvasCodec.encode(twice))).isEqualTo(written);
  }

  /**
   * A viewport reads back as it was, but that a locked one may be squared again by its constructor
   * where its spans are rounded in the last places of its coordinates (deep zooms, far centres,
   * wide or tall windows): each bound then moves by under a pixel, and from the first reading on
   * the viewport is stable, a second reading giving exactly the first; the move is also within a
   * loose {@value #ULPS} units in the last place of the largest coordinate.
   */
  @Property(tries = 1000)
  void aViewportReadsBackUnderAPixelAndIsStableFromTheFirstReading(
      @ForAll("canvases") StoredCanvas canvas) throws Exception {
    Viewport v = canvas.viewport();
    Viewport once = reread(canvas).viewport();
    Viewport twice = reread(reread(canvas)).viewport();

    assertThat(twice).isEqualTo(once);
    assertThat(once.width()).isEqualTo(v.width());
    assertThat(once.height()).isEqualTo(v.height());
    assertThat(once.aspectLocked()).isEqualTo(v.aspectLocked());
    double ulps =
        ULPS
            * Math.ulp(
                Math.max(
                    Math.max(Math.abs(v.xMin()), Math.abs(v.xMax())),
                    Math.max(Math.abs(v.yMin()), Math.abs(v.yMax()))));
    double pixelX = (v.xMax() - v.xMin()) / v.width();
    double pixelY = (v.yMax() - v.yMin()) / v.height();
    within(v.xMin(), once.xMin(), pixelX, ulps);
    within(v.xMax(), once.xMax(), pixelX, ulps);
    within(v.yMin(), once.yMin(), pixelY, ulps);
    within(v.yMax(), once.yMax(), pixelY, ulps);
    if (!v.aspectLocked()) {
      assertThat(once).isEqualTo(v);
    }
  }

  private static StoredCanvas reread(StoredCanvas c) throws Exception {
    return CanvasCodec.decode(JsonIo.parse(JsonIo.text(CanvasCodec.encode(c))));
  }

  private static void within(double was, double read, double pixel, double ulps) {
    assertThat(Math.abs(read - was)).isLessThan(pixel).isLessThanOrEqualTo(ulps);
  }

  @Provide
  Arbitrary<StoredCanvas> canvases() {
    return Arbitraries.randomValue(CanvasStates::random);
  }
}

package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Slider;
import mathcanvas.engine.expr.Interval;
import org.junit.jupiter.api.Test;

class CanvasCodecTest {

  private static final String EVERY_KIND = "canvas-every-kind.json";

  @Test
  void everyKindIsWrittenAsTheFixture() throws IOException {
    assertThat(JsonIo.text(CanvasCodec.encode(CanvasStates.everyKind())))
        .isEqualTo(Fixtures.text(EVERY_KIND));
  }

  @Test
  void theFixtureReadsAsEveryKindWithNoGeometry() throws Exception {
    StoredCanvas read = CanvasCodec.decode(Fixtures.read(EVERY_KIND));

    assertThat(read).isEqualTo(CanvasStates.everyKind());
    assertThat(read.state().geometry()).isEmpty();
  }

  @Test
  void aDomainEndWrittenAsTextReadsClosedAndAnInfiniteOneOpen() throws Exception {
    Interval half = Interval.of(CanvasStates.expr("0"), FunctionGraph.ALL.upper(), false, true);
    StoredCanvas c =
        new StoredCanvas(
            CanvasState.EMPTY.add(
                id ->
                    new FunctionGraph(
                        id,
                        CanvasStates.style(0),
                        CanvasStates.expr("x"),
                        mathcanvas.engine.expr.Symbol.of("x"),
                        half)),
            Map.of(),
            new Viewport(0, 1, 0, 1, 100, 100, false));

    FunctionGraph read =
        (FunctionGraph) CanvasCodec.decode(CanvasCodec.encode(c)).state().objects().getFirst();

    assertThat(read.domain())
        .isEqualTo(Interval.of(CanvasStates.expr("0"), FunctionGraph.ALL.upper(), true, false));
  }

  @Test
  void theDefaultIsAnEmptyCanvasAtANewDocumentsViewport() throws Exception {
    assertThat(JsonIo.text(CanvasCodec.encode(StoredCanvas.DEFAULT)))
        .isEqualTo(
            """
            {
              "viewport": {
                "xMin": -8.0,
                "xMax": 8.0,
                "yMin": -6.0,
                "yMax": 6.0,
                "width": 640.0,
                "height": 480.0,
                "aspectLocked": true
              },
              "nextId": 1,
              "objects": []
            }
            """);
  }

  private static StoredCanvas slider(BigDecimal min, BigDecimal max, BigDecimal step) {
    return new StoredCanvas(
        CanvasState.EMPTY.add(
            id -> new Slider(id, CanvasStates.style(0), "a", min, max, step, min)),
        Map.of(),
        new Viewport(0, 1, 0, 1, 100, 100, false));
  }

  @Test
  void aSliderOfHugeAndTinyBoundsIsWrittenShortAndReadBackExactly() throws Exception {
    StoredCanvas c =
        slider(new BigDecimal("-1e1001"), new BigDecimal("1e1001"), new BigDecimal("1e-1001"));
    String text = JsonIo.text(CanvasCodec.encode(c));

    assertThat(text).contains("\"min\": -1E+1001", "\"max\": 1E+1001", "\"step\": 1E-1001");
    assertThat(text.length()).isLessThan(1000);
    assertThat(CanvasCodec.decode(JsonIo.parse(text))).isEqualTo(c);

    StoredCanvas huge = slider(BigDecimal.ZERO, new BigDecimal("1e3000000"), BigDecimal.ONE);
    String hugeText = JsonIo.text(CanvasCodec.encode(huge));
    assertThat(hugeText.length()).isLessThan(1000);
    assertThat(CanvasCodec.decode(JsonIo.parse(hugeText))).isEqualTo(huge);
  }

  @Test
  void aSliderBoundWithALongMantissaReadsBack() throws Exception {
    BigDecimal max = new BigDecimal("1".repeat(1001));
    StoredCanvas c = slider(BigDecimal.ZERO, max, BigDecimal.ONE);
    String text = JsonIo.text(CanvasCodec.encode(c));

    assertThat(CanvasCodec.decode(JsonIo.parse(text))).isEqualTo(c);
  }

  // Strict reading: each case is the fixture with one thing wrong.

  private static Json.Obj fixture() throws IOException {
    return (Json.Obj) Fixtures.read(EVERY_KIND);
  }

  /** The fixture with object {@code n} (from 1) changed. */
  private static Json.Obj withObject(int n, UnaryOperator<Json.Obj> change) throws IOException {
    Json.Obj root = fixture();
    List<Json> objects = new ArrayList<>(((Json.Arr) root.member("objects").orElseThrow()).items());
    objects.set(n - 1, change.apply((Json.Obj) objects.get(n - 1)));
    return root.with("objects", new Json.Arr(objects));
  }

  private static void rejected(Json tree, String message) {
    assertThatThrownBy(() -> CanvasCodec.decode(tree))
        .isInstanceOf(FileFormatException.class)
        .hasMessageContaining(message);
  }

  private static Json.Obj anchor(Json.Obj o) {
    return (Json.Obj) o.member("anchor").orElseThrow();
  }

  private static Json.Obj approach(Json.Obj o) {
    return (Json.Obj) anchor(o).member("approach").orElseThrow();
  }

  private static Json.Str str(String s) {
    return new Json.Str(s);
  }

  @Test
  void anUnknownKindIsRefused() throws IOException {
    rejected(
        withObject(7, o -> o.with("kind", str("circle"))),
        "the kind \"circle\" is not one this release reads");
  }

  @Test
  void aMissingMemberIsRefused() throws IOException {
    rejected(withObject(2, o -> o.without("body")), "\"body\" is missing");
    rejected(fixture().without("nextId"), "\"nextId\" is missing");
    rejected(withObject(1, o -> o.without("style")), "\"style\" is missing");
  }

  @Test
  void anIllTypedMemberIsRefused() throws IOException {
    rejected(withObject(7, o -> o.with("x", str("1.5"))), "\"x\" is not a finite number");
    rejected(withObject(7, o -> o.with("x", Json.Null.INSTANCE)), "\"x\" is not a number");
    rejected(withObject(7, o -> o.with("y", new Json.Num("1e400"))), "not a finite number");
    rejected(withObject(8, o -> o.with("param", str("s"))), "\"param\" has no value \"s\"");
    rejected(withObject(9, o -> o.with("anchor", Json.Num.of(1))), "\"anchor\" is not");
    rejected(
        withObject(9, o -> o.with("anchor", anchor(o).with("approach", Json.Num.of(1)))),
        "\"approach\" is not an object or null");
    rejected(
        withObject(9, o -> o.with("anchor", anchor(o).with("approach", approach(o).without("x")))),
        "\"x\" is missing");
    rejected(
        withObject(
            9,
            o ->
                o.with(
                    "anchor",
                    anchor(o).with("approach", approach(o).with("y", Json.Null.INSTANCE)))),
        "\"y\" is not a number");
    rejected(
        withObject(
            1,
            o -> o.with("style", ((Json.Obj) o.member("style").orElseThrow()).without("dashed"))),
        "\"dashed\" is missing");
    rejected(fixture().with("objects", Json.Num.of(1)), "\"objects\" is not an array");
    rejected(withObject(2, o -> o.with("domain", new Json.Arr(List.of()))), "array of 2");
  }

  @Test
  void aParentThatDoesNotExistIsRefused() throws IOException {
    rejected(withObject(11, o -> o.with("end", Json.Num.of(19))), "missing parent #19");
  }

  @Test
  void aParentOfTheWrongKindIsRefused() throws IOException {
    rejected(withObject(8, o -> o.with("path", Json.Num.of(7))), "needs a path");
  }

  @Test
  void aCycleIsRefused() throws IOException {
    // the point on the path 2 moves onto the line 13 that goes through it
    rejected(withObject(8, o -> o.with("path", Json.Num.of(13))), "the canvas objects:");
  }

  @Test
  void anIdAtOrAboveNextIdIsRefused() throws IOException {
    rejected(fixture().with("nextId", Json.Num.of(18)), "not below nextId");
    rejected(fixture().with("nextId", Json.Num.of(0)), "\"nextId\" is not an integer from 1");
  }

  @Test
  void aSharedIdIsRefused() throws IOException {
    rejected(withObject(14, o -> o.with("id", Json.Num.of(1))), "share an id");
    rejected(withObject(14, o -> o.with("id", Json.Num.of(0))), "\"id\" is not an integer from 1");
  }

  @Test
  void textThatDoesNotParseIsRefused() throws IOException {
    rejected(withObject(2, o -> o.with("body", str("x +"))), "\"body\" does not parse");
    rejected(withObject(5, o -> o.with("equation", str("x^2 + y^2"))), "not a relation");
    rejected(withObject(5, o -> o.with("equation", str("x < y"))), "an equation");
    rejected(withObject(6, o -> o.with("inequality", str("x = y"))), "an inequality");
    rejected(withObject(2, o -> o.with("variable", str("x y"))), "not a variable name");
  }

  @Test
  void aRecordsOwnCheckIsRefused() throws IOException {
    rejected(withObject(1, o -> o.with("value", Json.Num.of(101))), "in its range");
    rejected(
        withObject(
            1,
            o ->
                o.with(
                    "style",
                    ((Json.Obj) o.member("style").orElseThrow()).with("colour", Json.Num.of(8)))),
        "\"colour\" is not an integer from 0 to 7");
    // the colour indexes the palette (CanvasState.nextColour counts by it): never outside it
    rejected(
        withObject(
            1,
            o ->
                o.with(
                    "style",
                    ((Json.Obj) o.member("style").orElseThrow()).with("colour", Json.Num.of(-1)))),
        "\"colour\" is not an integer from 0 to 7");
    rejected(
        withObject(
            1,
            o ->
                o.with(
                    "style",
                    ((Json.Obj) o.member("style").orElseThrow()).with("width", Json.Num.of(0)))),
        "a width is positive");
    rejected(withObject(8, o -> o.with("parameter", Json.Null.INSTANCE)), "finite parameter");
  }

  @Test
  void aLineHasOneForm() throws IOException {
    rejected(withObject(12, o -> o.with("point", Json.Num.of(7))), "either");
    rejected(withObject(12, o -> o.without("through")), "either");
    rejected(withObject(13, o -> o.without("slope")), "\"slope\" is missing");
  }

  @Test
  void aBadViewportIsRefused() throws IOException {
    Json.Obj v = (Json.Obj) fixture().member("viewport").orElseThrow();
    rejected(fixture().with("viewport", v.with("xMax", new Json.Num("-10.0"))), "the viewport");
    rejected(fixture().with("viewport", v.without("aspectLocked")), "\"aspectLocked\"");
    rejected(fixture().with("viewport", v.with("width", Json.Num.of(0))), "bad pixel size");
    rejected(
        fixture().with("viewport", v.with("height", new Json.Num("-480.0"))), "bad pixel size");
    rejected(
        fixture().with("viewport", v.with("height", new Json.Num("1e999"))),
        "\"height\" is not a finite number");
    rejected(fixture().with("viewport", v.without("width")), "\"width\" is missing");
  }

  @Test
  void aBadNameIsRefused() throws IOException {
    rejected(withObject(9, o -> o.with("name", str("A"))), "\"A\" is taken twice");
    rejected(withObject(9, o -> o.with("name", str("a"))), "\"a\" is taken twice");
    rejected(withObject(9, o -> o.with("name", str("sin"))), "not a variable name");
    rejected(withObject(9, o -> o.with("name", str("2B"))), "not a variable name");
    rejected(withObject(1, o -> o.with("name", str("Z"))), "a slider is named by its variable");
    rejected(withObject(9, o -> o.with("name", Json.Num.of(1))), "\"name\" is not a string");
    rejected(withObject(11, o -> o.with("name", str("S"))), "only a point has a \"name\"");
  }

  @Test
  void anUnknownMemberIsIgnored() throws Exception {
    assertThat(CanvasCodec.decode(withObject(3, o -> o.with("colourName", str("blue")))))
        .isEqualTo(CanvasStates.everyKind());
  }
}

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
import mathcanvas.canvas.model.Anchor;
import mathcanvas.canvas.model.Approach;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.space.Camera;
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

  @Test
  void aCriticalPointIsWrittenAndReadBack() throws Exception {
    Anchor where =
        new Anchor(-1, 2, -1, Double.NaN, false, java.util.Optional.of(new Approach(-1, -1, 2)));
    StoredCanvas c =
        new StoredCanvas(
            CanvasState.EMPTY
                .add(
                    id ->
                        new FunctionGraph(
                            id,
                            CanvasStates.style(0),
                            CanvasStates.expr("x^3 - 3x"),
                            mathcanvas.engine.expr.Symbol.of("x"),
                            FunctionGraph.ALL))
                .add(
                    id ->
                        new CriticalPoint(
                            id,
                            CanvasStates.style(1),
                            new ObjectId(1),
                            Feature.MAX,
                            0,
                            java.util.Optional.of(where)))
                .add(
                    id ->
                        CriticalPoint.of(
                            id, CanvasStates.style(2), new ObjectId(1), Feature.ROOT, 2)),
            Map.of(new ObjectId(2), "A", new ObjectId(3), "B"),
            new Viewport(0, 1, 0, 1, 100, 100, false));

    Json written = CanvasCodec.encode(c);
    Json.Obj max =
        (Json.Obj) ((Json.Arr) ((Json.Obj) written).member("objects").orElseThrow()).items().get(1);

    assertThat(max.member("kind")).contains(str("criticalPoint"));
    assertThat(max.member("feature")).contains(str("max"));
    assertThat(max.member("path")).contains(Json.Num.of(1));
    assertThat(CanvasCodec.decode(written)).isEqualTo(c);
    assertThat(CanvasCodec.decode(JsonIo.parse(JsonIo.text(written)))).isEqualTo(c);
    rejected(
        withCriticalPoint(written, o -> o.with("feature", str("saddle"))),
        "\"feature\" has no value \"saddle\"");
  }

  /** A written canvas with its critical point (the second object) changed. */
  private static Json withCriticalPoint(Json written, UnaryOperator<Json.Obj> change) {
    Json.Obj root = (Json.Obj) written;
    List<Json> objects = new ArrayList<>(((Json.Arr) root.member("objects").orElseThrow()).items());
    objects.set(1, change.apply((Json.Obj) objects.get(1)));
    return root.with("objects", new Json.Arr(objects));
  }

  // Strict reading: each case is the fixture with one thing wrong.

  @Test
  void theAreasAndSumsAreReadStrictly() throws Exception {
    // objects 21 and 22 are the shaded areas, 23 the Riemann sum
    rejected(withObject(21, o -> o.without("lower")), "\"lower\" is missing");
    rejected(withObject(22, o -> o.with("lower", Json.Num.of(7))), "needs a function graph");
    rejected(withObject(22, o -> o.with("lower", Json.Num.of(2))), "two graphs");
    rejected(withObject(23, o -> o.with("method", str("simpson"))), "has no value \"simpson\"");
    rejected(withObject(23, o -> o.with("n", str("1 +"))), "\"n\" does not parse");
  }

  @Test
  void theVectorsAreReadStrictly() throws Exception {
    // objects 24 to 27 are the arrows, 28 the vector sum, 29 the projection
    rejected(withObject(24, o -> o.without("head")), "either \"head\" or \"variable\"");
    rejected(withObject(27, o -> o.with("head", Json.Num.of(7))), "either");
    rejected(withObject(27, o -> o.with("variable", str("sin"))), "not a variable name");
    rejected(withObject(27, o -> o.with("variable", Json.Num.of(1))), "\"variable\" is not");
    rejected(withObject(24, o -> o.with("tail", Json.Num.of(8))), "two different points");
    rejected(withObject(24, o -> o.without("tail")), "\"tail\" is missing");
    rejected(withObject(24, o -> o.with("head", Json.Num.of(2))), "needs a point");
    rejected(withObject(28, o -> o.with("v", Json.Num.of(7))), "needs a vector");
    rejected(withObject(29, o -> o.with("u", Json.Num.of(26))), "onto another vector");
    rejected(withObject(29, o -> o.with("u", Json.Num.of(30))), "cycle");
  }

  @Test
  void theFieldsAndContoursAreReadStrictly() throws Exception {
    // objects 56 and 57 are the fields (ids 57 and 58), 58 the solution curve, 59 and 60 the
    // contour plots by count and by values
    rejected(withObject(56, o -> o.with("density", Json.Num.of(9))), "\"density\" is not");
    rejected(withObject(57, o -> o.with("density", Json.Num.of(41))), "\"density\" is not");
    rejected(withObject(56, o -> o.without("q")), "\"q\" is missing");
    rejected(withObject(58, o -> o.with("field", Json.Num.of(57))), "needs a slope field");
    rejected(withObject(58, o -> o.with("point", Json.Num.of(2))), "needs a point");
    rejected(withObject(59, o -> o.with("count", Json.Num.of(31))), "\"count\" is not");
    rejected(withObject(59, o -> o.with("levels", new Json.Arr(List.of(str("1"))))), "both");
    rejected(withObject(60, o -> o.without("levels")), "none of");
    rejected(withObject(60, o -> o.with("levels", new Json.Arr(List.of()))), "1 to 30 levels");
    rejected(
        withObject(60, o -> o.with("levels", new Json.Arr(List.of(Json.Num.of(1))))),
        "not a string");
  }

  @Test
  void theStatisticalPlotsAreReadStrictly() throws Exception {
    // objects 65 and 66 are the histograms (ids 66 and 67), 67 and 68 the bar charts, 69 and 70
    // the box plots, 71 and 72 the scatter plots by pairs and by columns
    rejected(withObject(65, o -> o.without("binWidth")), "\"binWidth\" is missing");
    rejected(withObject(66, o -> o.with("start", Json.Num.of(1))), "\"start\" is not");
    rejected(withObject(65, o -> o.with("data", str("[1,"))), "\"data\" does not parse");
    rejected(withObject(67, o -> o.with("categories", str("red"))), "not an array");
    rejected(
        withObject(67, o -> o.with("categories", new Json.Arr(List.of(Json.Num.of(1))))),
        "not a string");
    rejected(withObject(68, o -> o.without("values")), "\"values\" is missing");
    rejected(withObject(69, o -> o.without("y")), "\"y\" is missing");
    rejected(withObject(71, o -> o.without("pairs")), "none of");
    rejected(withObject(72, o -> o.with("pairs", str("m"))), "both");
    rejected(withObject(72, o -> o.without("ys")), "\"ys\" is missing");
  }

  @Test
  void residualPlotsAreReadStrictly() throws Exception {
    // objects 73 and 74 are the residual plots by columns and by pairs (ids 74 and 75)
    rejected(withObject(73, o -> o.without("fit")), "\"fit\" is missing");
    rejected(withObject(73, o -> o.with("fit", Json.Num.of(1))), "\"fit\" is not");
    rejected(withObject(74, o -> o.with("fit", str("linreg(x,"))), "\"fit\" does not parse");
    rejected(withObject(73, o -> o.without("xs")), "none of");
    rejected(withObject(73, o -> o.with("pairs", str("m"))), "both");
    rejected(withObject(73, o -> o.without("ys")), "\"ys\" is missing");
  }

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
        withObject(7, o -> o.with("kind", str("conic"))),
        "the kind \"conic\" is not one this release reads");
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
  void theConstructionsAreReadStrictly() throws IOException {
    // objects by place: ray 30, circles 32 to 34, midpoint 35, lines 36 to 41, polygons 42 and
    // 43, angle 44
    rejected(withObject(30, o -> o.without("through")), "\"through\" is missing");
    rejected(withObject(30, o -> o.with("through", Json.Num.of(7))), "two different points");
    rejected(withObject(32, o -> o.with("points", new Json.Arr(List.of()))), "has both");
    rejected(withObject(32, o -> o.with("radius", str("2"))), "has both");
    rejected(withObject(33, o -> o.without("radius")), "has none of");
    rejected(
        withObject(34, o -> o.with("points", new Json.Arr(List.of(Json.Num.of(7))))), "array of 3");
    rejected(withObject(35, o -> o.with("b", Json.Num.of(5))), "needs a point");
    rejected(
        withObject(36, o -> o.with("perpendicular", o.without("perpendicular"))),
        "\"point\" is missing");
    rejected(
        withObject(
            36,
            o ->
                o.with(
                    "perpendicular",
                    ((Json.Obj) o.member("perpendicular").orElseThrow())
                        .with("to", Json.Num.of(7)))),
        "needs a line, segment or ray");
    rejected(
        withObject(
            40,
            o ->
                o.with(
                    "tangent",
                    ((Json.Obj) o.member("tangent").orElseThrow()).with("index", Json.Num.of(2)))),
        "\"index\"");
    rejected(
        withObject(
            40,
            o ->
                o.with(
                    "tangent",
                    ((Json.Obj) o.member("tangent").orElseThrow())
                        .with("circle", Json.Num.of(31)))),
        "needs a circle");
    rejected(
        withObject(
            42, o -> o.with("vertices", new Json.Arr(List.of(Json.Num.of(7), Json.Num.of(8))))),
        "three or more");
    rejected(withObject(43, o -> o.with("vertices", new Json.Arr(List.of()))), "has both");
    rejected(withObject(44, o -> o.without("points")), "\"points\" is missing");
    rejected(withObject(45, o -> o.with("param", str("arc"))), "\"param\"");
  }

  @Test
  void theImagesMeasuresAndLociAreReadStrictly() throws IOException {
    // objects by place: reflection 47, rotation 48, translations 49 and 50, dilation 51,
    // distance 52, area 53, slope 54, locus 55
    rejected(withObject(47, o -> o.with("shape", str("point"))), "is the image of a point");
    rejected(withObject(47, o -> o.with("shape", str("conic"))), "\"shape\" has no value");
    rejected(withObject(47, o -> o.without("in")), "\"in\" is missing");
    rejected(withObject(48, o -> o.without("angle")), "\"angle\" is missing");
    rejected(withObject(49, o -> o.with("from", Json.Num.of(7))), "has both");
    rejected(withObject(49, o -> o.with("vector", Json.Num.of(7))), "needs a vector");
    rejected(withObject(50, o -> o.with("to", Json.Num.of(7))), "two different points");
    rejected(withObject(51, o -> o.with("factor", str("2 +"))), "\"factor\" does not parse");
    rejected(
        withObject(52, o -> o.with("b", Json.Num.of(33))), "needs a line, segment, ray or point");
    rejected(withObject(53, o -> o.with("of", Json.Num.of(12))), "needs a polygon or circle");
    rejected(withObject(54, o -> o.with("of", Json.Num.of(33))), "needs a line, segment or ray");
    rejected(withObject(55, o -> o.with("driver", Json.Num.of(7))), "needs a point on a path");
  }

  // Phase 7d: the 3D view's kinds.

  @Test
  void aCameraIsWrittenOnlyWhenItIsNotTheDefaultAndReadBack() throws Exception {
    StoredCanvas c = CanvasStates.everyKind();
    assertThat(JsonIo.text(CanvasCodec.encode(c))).doesNotContain("\"space\"");
    Camera turned = new Camera(0.25, -0.5, 12.5, 0.125, -1, 0.5);
    StoredCanvas seen = new StoredCanvas(c.state(), c.names(), c.viewport(), turned);
    Json.Obj encoded = CanvasCodec.encode(seen);
    assertThat(JsonIo.text(encoded))
        .contains(
            """
              "space": {
                "yaw": 0.25,
                "pitch": -0.5,
                "distance": 12.5,
                "target": [
                  0.125,
                  -1.0,
                  0.5
                ],
                "bounds": null
              }
            """);
    assertThat(CanvasCodec.decode(encoded)).isEqualTo(seen);
    // absent, as in every older file: the default camera
    assertThat(CanvasCodec.decode(fixture()).camera()).isEqualTo(Camera.DEFAULT);
    Json.Obj space = (Json.Obj) encoded.member("space").orElseThrow();
    rejected(encoded.with("space", space.with("distance", Json.Num.of(1))), "the camera");
    rejected(encoded.with("space", space.with("bounds", Json.Num.of(1))), "\"bounds\"");
    rejected(encoded.with("space", space.without("yaw")), "\"yaw\" is missing");
  }

  @Test
  void theSpaceKindsAreReadStrictly() throws IOException {
    // object 61 by place is the surface
    rejected(withObject(61, o -> o.with("n", Json.Num.of(60))), "50, 100, 150 or 200");
    rejected(withObject(61, o -> o.without("xDomain")), "\"xDomain\" is missing");
    rejected(withObject(61, o -> o.with("body", str("x +"))), "\"body\" does not parse");
    // objects 62 to 64 by place: the parametric surface, the space curve, the gradient
    rejected(withObject(62, o -> o.with("n", Json.Num.of(10))), "50, 100, 150 or 200");
    rejected(withObject(62, o -> o.without("vDomain")), "\"vDomain\" is missing");
    rejected(withObject(63, o -> o.without("z")), "\"z\" is missing");
    rejected(withObject(64, o -> o.with("surface", Json.Num.of(63))), "surface z = f(x, y)");
    rejected(withObject(64, o -> o.with("surface", Json.Num.of(99))), "missing parent");
  }

  @Test
  void aLineHasOneForm() throws IOException {
    rejected(withObject(12, o -> o.with("point", Json.Num.of(7))), "has both");
    rejected(withObject(12, o -> o.without("through")), "has none of");
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

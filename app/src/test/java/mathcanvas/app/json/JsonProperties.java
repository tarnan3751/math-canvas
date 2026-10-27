package mathcanvas.app.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

class JsonProperties {

  @Property(tries = 500)
  void aValueSurvivesWritingAndReadingAsUtf8(@ForAll("values") Json value) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (Writer out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
      JsonIo.write(value, out);
    }
    Json read =
        JsonIo.read(
            new InputStreamReader(
                new ByteArrayInputStream(bytes.toByteArray()), StandardCharsets.UTF_8));

    assertThat(read).isEqualTo(value);
    assertThat(JsonIo.text(read)).isEqualTo(bytes.toString(StandardCharsets.UTF_8));
  }

  @Provide
  Arbitrary<Json> values() {
    return value(4);
  }

  private static Arbitrary<Json> value(int depth) {
    Arbitrary<Json> leaf =
        Arbitraries.oneOf(
            strings().map(Json.Str::new),
            numbers().map(Json.Num::new),
            Arbitraries.of(true, false).map(Json.Bool::new),
            Arbitraries.just(Json.Null.INSTANCE));
    if (depth == 0) {
      return leaf;
    }
    Arbitrary<Json> child = Arbitraries.lazy(() -> value(depth - 1));
    Arbitrary<Json> array = child.list().ofMaxSize(4).map(Json.Arr::new);
    Arbitrary<Json> object =
        Arbitraries.maps(strings().filter(JsonProperties::wellFormed), child)
            .ofMaxSize(4)
            .map(m -> new Json.Obj(new LinkedHashMap<>(m)));
    return Arbitraries.frequencyOf(Tuple.of(3, leaf), Tuple.of(1, array), Tuple.of(1, object));
  }

  /**
   * Any text: controls, quotes, backslashes, non-ASCII and astral code points, and surrogate code
   * units without their pair.
   */
  private static Arbitrary<String> strings() {
    Arbitrary<String> piece =
        Arbitraries.oneOf(
            Arbitraries.integers().between(0, 0x7f).map(Character::toString),
            Arbitraries.integers().between(0x80, 0xffff).map(Character::toString),
            Arbitraries.integers().between(0x10000, 0x10ffff).map(Character::toString),
            Arbitraries.integers()
                .between(0xd800, 0xdfff)
                .map(c -> String.valueOf((char) c.intValue())));
    return piece.list().ofMaxSize(12).map(p -> String.join("", p));
  }

  private static boolean wellFormed(String s) {
    return s.codePoints()
        .noneMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
  }

  private static Arbitrary<String> numbers() {
    Arbitrary<String> digits = Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(30);
    Arbitrary<String> integer =
        digits.map(d -> d.replaceFirst("^0+(?=.)", "")).map(d -> d.isEmpty() ? "0" : d);
    return Arbitraries.of("", "-")
        .flatMap(
            sign ->
                integer.flatMap(
                    i ->
                        Arbitraries.of(List.of("", ".5", ".007", ".50", "e10", "E-3", ".25e+400"))
                            .map(tail -> sign + i + tail)));
  }
}

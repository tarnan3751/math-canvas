package mathcanvas.app.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonParseException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonTest {

  private static Json.Obj obj(Object... nameValue) {
    LinkedHashMap<String, Json> m = new LinkedHashMap<>();
    for (int k = 0; k < nameValue.length; k += 2) {
      m.put((String) nameValue[k], (Json) nameValue[k + 1]);
    }
    return new Json.Obj(m);
  }

  @Test
  void aHandWrittenFileReadsIntoTheTree() throws IOException {
    String text =
        """
        {
          "version": 1,
          "cells": [ { "input": "f(x) := x^2 + 1", "evaluated": true },
                     { "input": "π/2 \\u00b7 \\"q\\"\\n", "evaluated": false } ],
          "ratio": -0.50, "big": 1E400, "none": null
        }
        """;
    Json read = JsonIo.parse(text);

    assertThat(read)
        .isEqualTo(
            obj(
                "version", new Json.Num("1"),
                "cells",
                    new Json.Arr(
                        List.of(
                            obj(
                                "input", new Json.Str("f(x) := x^2 + 1"),
                                "evaluated", new Json.Bool(true)),
                            obj(
                                "input", new Json.Str("π/2 · \"q\"\n"),
                                "evaluated", new Json.Bool(false)))),
                "ratio", new Json.Num("-0.50"),
                "big", new Json.Num("1E400"),
                "none", Json.Null.INSTANCE));
  }

  @Test
  void writingIndentsByTwoSpacesWithLineFeeds() throws IOException {
    Json value =
        obj(
            "version",
            Json.Num.of(1),
            "cells",
            new Json.Arr(List.of(obj("input", new Json.Str("1/3")))),
            "empty",
            new Json.Arr(List.of()),
            "nothing",
            obj());

    assertThat(JsonIo.text(value))
        .isEqualTo(
            """
            {
              "version": 1,
              "cells": [
                {
                  "input": "1/3"
                }
              ],
              "empty": [],
              "nothing": {}
            }
            """);
  }

  @Test
  void numbersKeepTheirText() throws IOException {
    for (String n :
        List.of("0", "-0", "1.50", "1e400", "-12.5E-3", "123456789012345678901234567890")) {
      Json read = JsonIo.parse("[" + n + "]");
      assertThat(read).isEqualTo(new Json.Arr(List.of(new Json.Num(n))));
      assertThat(JsonIo.text(read)).contains(n);
    }
  }

  @Test
  void numberTextOutsideJsonsSyntaxIsRejected() {
    for (String n : List.of("", "01", "+1", "1.", ".5", "1e", "NaN", "Infinity", "0x10", "1 ")) {
      assertThatThrownBy(() -> new Json.Num(n)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void malformedInputIsAnError() {
    for (String bad :
        List.of(
            "",
            "   ",
            "{",
            "{\"a\": 1,}",
            "{\"a\": 1, \"a\": 2}",
            "[1] [2]",
            "{} x",
            "// c\n{}",
            "{'a': 1}",
            "[NaN]",
            "[01]")) {
      assertThatThrownBy(() -> JsonIo.parse(bad)).as(bad).isInstanceOf(JsonParseException.class);
    }
  }

  @Test
  void anUnpairedSurrogateIsEscapedAndReadsBackExactly() throws IOException {
    String text = "a\uD800b\uDC00\uD83D\uDE00\uDE00\uD83D";
    Json value = obj("\uD83D\uDE00", new Json.Str(text));
    String written = JsonIo.text(value);

    assertThat(written)
        .isEqualTo("{\n  \"\uD83D\uDE00\": \"a\\uD800b\\uDC00\uD83D\uDE00\\uDE00\\uD83D\"\n}\n");
    assertThat(JsonIo.parse(written)).isEqualTo(value);
    assertThatThrownBy(() -> obj("\uD800", Json.Null.INSTANCE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aLeadingByteOrderMarkIsSkipped() throws IOException {
    assertThat(JsonIo.parse("\uFEFF[1]")).isEqualTo(new Json.Arr(List.of(Json.Num.of(1))));
    assertThatThrownBy(() -> JsonIo.parse("\uFEFF")).isInstanceOf(JsonParseException.class);
    assertThatThrownBy(() -> JsonIo.parse("[1]\uFEFF")).isInstanceOf(JsonParseException.class);
  }

  @Test
  void jacksonsLimitsAreErrorsWithItsMessage() {
    Json deep = Json.Null.INSTANCE;
    for (int k = 0; k < 1100; k++) {
      deep = new Json.Arr(List.of(deep));
    }
    Json tooDeep = deep;
    assertThatThrownBy(() -> JsonIo.text(tooDeep))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("depth");
    assertThatThrownBy(() -> JsonIo.parse("[".repeat(1100) + "]".repeat(1100)))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("depth");
  }

  @Test
  void anObjectChangesByCopy() {
    Json.Obj o = obj("a", Json.Num.of(1), "b", Json.Num.of(2));

    assertThat(o.with("a", Json.Num.of(3)).members().sequencedKeySet()).containsExactly("a", "b");
    assertThat(o.with("c", Json.Num.of(3)).members().sequencedKeySet())
        .containsExactly("a", "b", "c");
    assertThat(o.without("a").members().sequencedKeySet()).containsExactly("b");
    assertThat(o.member("a")).contains(Json.Num.of(1));
    assertThat(o.member("z")).isEmpty();
    assertThat(o.members()).hasSize(2);
  }
}

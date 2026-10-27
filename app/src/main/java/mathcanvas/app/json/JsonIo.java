package mathcanvas.app.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Reads and writes {@link Json} values as text through Jackson Core's streaming parser and
 * generator. Reading is strict JSON: a duplicate member name, anything after the root value, and
 * every extension (comments, single quotes, {@code NaN}) are errors. Writing is indented by two
 * spaces, a member written {@code "name": value}, lines ended by LF on every platform, and the text
 * ended by a LF.
 */
public final class JsonIo {

  private static final JsonFactory FACTORY =
      new JsonFactoryBuilder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private static final DefaultIndenter INDENTER = new DefaultIndenter("  ", "\n");

  private static final DefaultPrettyPrinter PRETTY =
      new DefaultPrettyPrinter(
              Separators.createDefaultInstance()
                  .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                  .withObjectEmptySeparator("")
                  .withArrayEmptySeparator(""))
          .withObjectIndenter(INDENTER)
          .withArrayIndenter(INDENTER);

  private static final int BOM = '\uFEFF';

  private JsonIo() {}

  /**
   * Reads one JSON value, which must be all of the input.
   *
   * @param in the text
   * @return the value
   * @throws IOException if the input cannot be read or is not one JSON value; a syntax error is a
   *     {@link JsonParseException} with its location
   */
  public static Json read(Reader in) throws IOException {
    PushbackReader pushback = new PushbackReader(in);
    int first = pushback.read();
    if (first != BOM && first != -1) {
      pushback.unread(first);
    }
    try (JsonParser parser = FACTORY.createParser(pushback)) {
      JsonToken token = parser.nextToken();
      if (token == null) {
        throw new JsonParseException(parser, "no JSON value");
      }
      Json value = value(parser, token);
      if (parser.nextToken() != null) {
        throw new JsonParseException(parser, "more after the JSON value");
      }
      return value;
    }
  }

  /**
   * Reads one JSON value from a string.
   *
   * @param text the text
   * @return the value
   * @throws IOException if the text is not one JSON value
   */
  public static Json parse(String text) throws IOException {
    return read(new StringReader(text));
  }

  private static Json value(JsonParser parser, JsonToken token) throws IOException {
    return switch (token) {
      case START_OBJECT -> {
        LinkedHashMap<String, Json> members = new LinkedHashMap<>();
        for (JsonToken t = parser.nextToken(); t != JsonToken.END_OBJECT; t = parser.nextToken()) {
          String name = parser.currentName();
          members.put(name, value(parser, parser.nextToken()));
        }
        yield new Json.Obj(members);
      }
      case START_ARRAY -> {
        List<Json> items = new ArrayList<>();
        for (JsonToken t = parser.nextToken(); t != JsonToken.END_ARRAY; t = parser.nextToken()) {
          items.add(value(parser, t));
        }
        yield new Json.Arr(items);
      }
      case VALUE_STRING -> new Json.Str(parser.getText());
      case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new Json.Num(parser.getText());
      case VALUE_TRUE -> new Json.Bool(true);
      case VALUE_FALSE -> new Json.Bool(false);
      case VALUE_NULL -> Json.Null.INSTANCE;
      default -> throw new JsonParseException(parser, "unexpected " + token);
    };
  }

  /**
   * Writes a value, indented, followed by a LF.
   *
   * @param value the value
   * @param out where to write; it is flushed, not closed
   * @throws IOException if writing fails, or the value exceeds one of Jackson's limits
   */
  public static void write(Json value, Writer out) throws IOException {
    out.write(text(value));
    out.flush();
  }

  /**
   * The text of a value, as {@link #write} writes it.
   *
   * @param value the value
   * @return the text
   * @throws IOException if the value exceeds one of Jackson's limits, such as its nesting depth
   */
  public static String text(Json value) throws IOException {
    StringWriter out = new StringWriter();
    try (JsonGenerator generator = FACTORY.createGenerator(out)) {
      generator.setPrettyPrinter(new DefaultPrettyPrinter(PRETTY));
      write(generator, value);
    }
    return escapeLoneSurrogates(out.toString()) + "\n";
  }

  /**
   * Escapes every surrogate code unit that is not half of a pair. Such a unit can stand only inside
   * a string value (every structural character is ASCII, and names hold none), where its escape
   * means the same unit.
   */
  private static String escapeLoneSurrogates(String text) {
    StringBuilder out = null;
    for (int k = 0; k < text.length(); k++) {
      char c = text.charAt(k);
      boolean paired =
          Character.isHighSurrogate(c)
                  && k + 1 < text.length()
                  && Character.isLowSurrogate(text.charAt(k + 1))
              || Character.isLowSurrogate(c)
                  && k > 0
                  && Character.isHighSurrogate(text.charAt(k - 1));
      if (Character.isSurrogate(c) && !paired) {
        if (out == null) {
          out = new StringBuilder(text.length() + 16).append(text, 0, k);
        }
        out.append(String.format("\\u%04X", (int) c));
      } else if (out != null) {
        out.append(c);
      }
    }
    return out == null ? text : out.toString();
  }

  private static void write(JsonGenerator g, Json value) throws IOException {
    switch (value) {
      case Json.Obj o -> {
        g.writeStartObject();
        for (var m : o.members().entrySet()) {
          g.writeFieldName(m.getKey());
          write(g, m.getValue());
        }
        g.writeEndObject();
      }
      case Json.Arr a -> {
        g.writeStartArray();
        for (Json item : a.items()) {
          write(g, item);
        }
        g.writeEndArray();
      }
      case Json.Str s -> g.writeString(s.value());
      case Json.Num n -> g.writeNumber(n.text());
      case Json.Bool b -> g.writeBoolean(b.value());
      case Json.Null ignored -> g.writeNull();
    }
  }
}

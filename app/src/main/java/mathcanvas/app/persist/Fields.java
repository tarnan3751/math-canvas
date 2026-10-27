package mathcanvas.app.persist;

import java.util.Locale;
import mathcanvas.app.json.Json;

/** Strict readers of an object's members, for decoding the app's files. */
final class Fields {

  private Fields() {}

  static Json member(Json.Obj obj, String name) throws FileFormatException {
    return obj.member(name)
        .orElseThrow(() -> new FileFormatException("the member \"" + name + "\" is missing"));
  }

  static String string(Json.Obj obj, String name) throws FileFormatException {
    if (member(obj, name) instanceof Json.Str(String value)) {
      return value;
    }
    throw new FileFormatException("\"" + name + "\" is not a string");
  }

  static boolean bool(Json.Obj obj, String name) throws FileFormatException {
    if (member(obj, name) instanceof Json.Bool(boolean value)) {
      return value;
    }
    throw new FileFormatException("\"" + name + "\" is not true or false");
  }

  static int integer(Json.Obj obj, String name, int min, int max) throws FileFormatException {
    if (member(obj, name) instanceof Json.Num(String text) && text.matches("-?[0-9]{1,10}")) {
      long v = Long.parseLong(text);
      if (v >= min && v <= max) {
        return (int) v;
      }
    }
    throw new FileFormatException("\"" + name + "\" is not an integer from " + min + " to " + max);
  }

  /** An enum constant written as its name in lower case. */
  static <E extends Enum<E>> E constant(Json.Obj obj, String name, Class<E> type)
      throws FileFormatException {
    String s = string(obj, name);
    for (E e : type.getEnumConstants()) {
      if (text(e).value().equals(s)) {
        return e;
      }
    }
    throw new FileFormatException("\"" + name + "\" has no value \"" + s + "\"");
  }

  /** An enum constant as it is written: its name in lower case. */
  static Json.Str text(Enum<?> e) {
    return new Json.Str(e.name().toLowerCase(Locale.ROOT));
  }
}

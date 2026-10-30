package mathcanvas.app.persist;

import java.math.BigDecimal;
import java.util.List;
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

  static long longInteger(Json.Obj obj, String name, long min) throws FileFormatException {
    if (member(obj, name) instanceof Json.Num(String text) && text.matches("-?[0-9]{1,19}")) {
      try {
        long v = Long.parseLong(text);
        if (v >= min) {
          return v;
        }
      } catch (NumberFormatException e) {
        // beyond a long: reported below
      }
    }
    throw new FileFormatException("\"" + name + "\" is not an integer from " + min);
  }

  /** A finite number, as the double nearest its text. */
  static double real(Json.Obj obj, String name) throws FileFormatException {
    double v = realOrNaN(obj, name);
    if (Double.isNaN(v)) {
      throw new FileFormatException("\"" + name + "\" is not a number");
    }
    return v;
  }

  /** A finite number, or NaN where the member is {@code null}. */
  static double realOrNaN(Json.Obj obj, String name) throws FileFormatException {
    return realOrNaN(member(obj, name), name);
  }

  /** A finite number, or NaN for {@code null}. */
  static double realOrNaN(Json value, String name) throws FileFormatException {
    if (value instanceof Json.Null) {
      return Double.NaN;
    }
    if (value instanceof Json.Num(String text)) {
      double v = Double.parseDouble(text);
      if (Double.isFinite(v)) {
        return v;
      }
    }
    throw new FileFormatException("\"" + name + "\" is not a finite number");
  }

  /** A number as the exact decimal its text writes. */
  static BigDecimal decimal(Json.Obj obj, String name) throws FileFormatException {
    if (member(obj, name) instanceof Json.Num(String text)) {
      return new BigDecimal(text);
    }
    throw new FileFormatException("\"" + name + "\" is not a number");
  }

  static Json.Obj object(Json.Obj obj, String name) throws FileFormatException {
    if (member(obj, name) instanceof Json.Obj value) {
      return value;
    }
    throw new FileFormatException("\"" + name + "\" is not an object");
  }

  /** An array of a given length, or of any length when {@code length} is negative. */
  static List<Json> array(Json.Obj obj, String name, int length) throws FileFormatException {
    if (member(obj, name) instanceof Json.Arr(List<Json> items)
        && (length < 0 || items.size() == length)) {
      return items;
    }
    throw new FileFormatException(
        "\"" + name + "\" is not an array" + (length < 0 ? "" : " of " + length));
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

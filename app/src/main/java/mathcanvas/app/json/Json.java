package mathcanvas.app.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.regex.Pattern;

/**
 * An immutable JSON value: the tree the app's files are read into and written from ({@link
 * JsonIo}), so that migrations rewrite plain values and no reflection is involved. A number keeps
 * its decimal text exactly as written, never converted to a binary type; an object keeps its
 * members in order and has no two of one name.
 */
public sealed interface Json {

  /**
   * An object. A member name is well-formed text: Jackson refuses an escaped unpaired surrogate in
   * a name, so a name may not hold one (a string value may).
   *
   * @param members the members in order
   */
  record Obj(SequencedMap<String, Json> members) implements Json {

    /**
     * Copies the members.
     *
     * @throws NullPointerException if a name or a value is null
     * @throws IllegalArgumentException if a name holds an unpaired surrogate
     */
    public Obj {
      LinkedHashMap<String, Json> copy = new LinkedHashMap<>();
      for (Map.Entry<String, Json> m : members.entrySet()) {
        if (m.getKey() != null && !wellFormed(m.getKey())) {
          throw new IllegalArgumentException("a member name holds an unpaired surrogate");
        }
        copy.put(Objects.requireNonNull(m.getKey(), "name"), Objects.requireNonNull(m.getValue()));
      }
      members = Collections.unmodifiableSequencedMap(copy);
    }

    private static boolean wellFormed(String s) {
      return s.codePoints()
          .noneMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }

    /**
     * A member.
     *
     * @param name its name
     * @return its value, or empty when there is no such member
     */
    public Optional<Json> member(String name) {
      return Optional.ofNullable(members.get(name));
    }

    /**
     * Returns this object with a member set: replaced in place if it exists, else added last.
     *
     * @param name the name
     * @param value the value
     * @return the object
     */
    public Obj with(String name, Json value) {
      LinkedHashMap<String, Json> copy = new LinkedHashMap<>(members);
      copy.put(name, value);
      return new Obj(copy);
    }

    /**
     * Returns this object without a member.
     *
     * @param name the name
     * @return the object
     */
    public Obj without(String name) {
      LinkedHashMap<String, Json> copy = new LinkedHashMap<>(members);
      copy.remove(name);
      return new Obj(copy);
    }
  }

  /**
   * An array.
   *
   * @param items the items in order
   */
  record Arr(List<Json> items) implements Json {

    /**
     * Copies the items.
     *
     * @throws NullPointerException if an item is null
     */
    public Arr {
      items = List.copyOf(items);
    }
  }

  /**
   * A string.
   *
   * @param value the text
   */
  record Str(String value) implements Json {

    /**
     * Checks the text.
     *
     * @throws NullPointerException if it is null
     */
    public Str {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A number, as its text in JSON's number syntax.
   *
   * @param text the text, such as {@code 34}, {@code -0.50} or {@code 1e400}
   */
  record Num(String text) implements Json {

    private static final Pattern SYNTAX =
        Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    /**
     * Checks the text.
     *
     * @throws IllegalArgumentException if it is not a JSON number
     */
    public Num {
      if (!SYNTAX.matcher(text).matches()) {
        throw new IllegalArgumentException("not a JSON number: " + text);
      }
    }

    /**
     * The number of an integer.
     *
     * @param value the integer
     * @return the number
     */
    public static Num of(long value) {
      return new Num(Long.toString(value));
    }
  }

  /**
   * {@code true} or {@code false}.
   *
   * @param value the value
   */
  record Bool(boolean value) implements Json {}

  /** {@code null}. */
  record Null() implements Json {

    /** The one null value. */
    public static final Null INSTANCE = new Null();
  }
}

package mathcanvas.app.persist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.DocumentState.StoredCell;
import mathcanvas.app.json.Json;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.space.Camera;
import mathcanvas.engine.parse.Lexer;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Token;
import mathcanvas.engine.parse.TokenKind;

/**
 * The session file, {@code session.json}: the worksheet's cells, each its input text and whether it
 * was evaluated, and the canvas: its viewport, its objects with their ids and {@code nextId}, and
 * the names of its points ({@link CanvasCodec}). Outputs and geometry are not stored; they are
 * computed again on loading, the cells first and then every object. Version 6:
 *
 * <pre>{@code
 * {
 *   "version": 6,
 *   "cells": [
 *     { "input": "a := slider(0, 5, 0.1)", "evaluated": true },
 *     { "input": "plot(a x^2)", "evaluated": true, "plots": [2] },
 *     { "input": "", "evaluated": false }
 *   ],
 *   "canvas": {
 *     "viewport": { "xMin": -8.0, "xMax": 8.0, "yMin": -6.0, "yMax": 6.0,
 *                   "width": 640.0, "height": 480.0, "aspectLocked": true },
 *     "nextId": 3,
 *     "objects": [
 *       { "id": 1, "kind": "slider", "style": { … }, "variable": "a", … },
 *       { "id": 2, "kind": "functionGraph", "style": { … }, "body": "a*x^2", … }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>A cell's {@code plots}, the ids of the canvas objects its {@code plot} statements drew, in
 * order, is written only when it drew some and read as none when absent. Reading is strict: a
 * missing or ill-typed member is an error; members it does not know are ignored. A slider's exact
 * numbers are JSON numbers written from their decimals; an expression is its printed text. Version
 * 1 had no {@code canvas}; its migration adds an empty one at a new document's viewport.
 */
public final class SessionFile {

  /** The file's name in the data directory. */
  public static final String NAME = "session.json";

  /**
   * The versions of the file and the steps between them: version 1 to 2 adds an empty canvas, 2 to
   * 3 an {@code at} of {@code null} to every point on a path, which version 3 reads strictly
   * (version 3 also has the shaded areas and Riemann sums, which a version 2 file cannot hold), 3
   * to 4 renames the names that began with {@code _}, which since units begin a unit ({@link
   * #unitNames}), 4 to 5 the names that became keywords of programs or a builtin ({@link
   * #keywordNames}), 5 to 6 the name {@code plot}, which became a builtin ({@link #plotName}).
   */
  public static final Migrations MIGRATIONS =
      new Migrations(
          6,
          List.<UnaryOperator<Json.Obj>>of(
              v1 -> v1.with("canvas", CanvasCodec.encode(StoredCanvas.DEFAULT)),
              CanvasCodec::pointsAtNothing,
              SessionFile::unitNames,
              SessionFile::keywordNames,
              SessionFile::plotName));

  /** The names version 5 reserves that version 4 read as names (docs/language.md §19). */
  static final Set<String> KEYWORDS =
      Set.of("end", "do", "step", "break", "continue", "global", "print");

  /** The canvas members whose strings are no expressions: free text, never renamed. */
  private static final Set<String> TEXTS = Set.of("kind", "text", "categories");

  /** The head of the derivative notation before its slash: {@code d}, {@code d^2}, {@code d²}. */
  private static final Pattern DERIVATIVE_HEAD = Pattern.compile("d(\\^[0-9]+|[⁰¹²³⁴⁵⁶⁷⁸⁹]+)?$");

  /**
   * Version 3 to 4: since version 4 a {@code _} begins no name ({@code docs/language.md} §14.26),
   * so every name that began with one ({@code _kq}, {@code _1}, {@code __c}, {@code _} alone) is
   * renamed with {@code v} before it ({@code v_kq}), or with a digit after that too when the file
   * already uses that name ({@code v_kq1}), in every cell's input and in every expression and name
   * of the canvas, the same name renamed the same everywhere. The names are found as the version 3
   * lexer read them ({@link #names}): {@code C_1} and {@code x2_kq} are names of their own, {@code
   * 2_kq} was {@code 2·_kq} and becomes {@code 2v_kq}, and {@code _t²} is the name {@code _t}
   * squared, {@code v_t²}. They are compared as the lexer reads them, subscripts written with
   * {@code _} ({@code vₐ} is {@code v_a}), and a renamed one is written so ({@code _a₂} is {@code
   * v_a_2}); everything around a name is kept as it is.
   *
   * @param v3 the version 3 tree
   * @return the tree with the names renamed
   */
  static Json.Obj unitNames(Json.Obj v3) {
    List<String> texts = new ArrayList<>();
    strings(v3.member("cells").orElse(null), "input", texts);
    strings(v3.member("canvas").orElse(null), "canvas", texts);
    Set<String> used = new HashSet<>();
    for (String t : texts) {
      for (int[] n : names(t)) {
        if (t.charAt(n[0]) != '_') {
          used.add(lexed(t.substring(n[0], n[1])));
        }
      }
    }
    // keyed by the new name as the lexer reads it, so _aₖ and _a_k, one name, are renamed alike
    Map<String, String> renamed = new HashMap<>();
    for (String t : texts) {
      names(t).stream()
          .filter(n -> t.charAt(n[0]) == '_')
          .map(n -> lexed("v" + t.substring(n[0], n[1])))
          .sorted()
          .forEach(
              name -> {
                if (!renamed.containsKey(name)) {
                  String to = name;
                  for (int k = 1; used.contains(to); k++) {
                    to = name + k;
                  }
                  used.add(to);
                  renamed.put(name, to);
                }
              });
    }
    if (renamed.isEmpty()) {
      return v3;
    }
    UnaryOperator<String> rename =
        t -> {
          StringBuilder out = new StringBuilder();
          int from = 0;
          for (int[] n : names(t)) {
            if (t.charAt(n[0]) == '_') {
              out.append(t, from, n[0]);
              out.append(renamed.get(lexed("v" + t.substring(n[0], n[1]))));
              from = n[1];
            } else if (isDifferential(t, n)
                && renamed.containsKey(lexed("v" + t.substring(n[0] + 1, n[1])))) {
              out.append(t, from, n[0]).append('d');
              out.append(renamed.get(lexed("v" + t.substring(n[0] + 1, n[1]))));
              from = n[1];
            }
          }
          return out.append(t, from, t.length()).toString();
        };
    Json.Obj out = v3;
    for (String member : List.of("cells", "canvas")) {
      Optional<Json> value = v3.member(member);
      if (value.isPresent()) {
        out = out.with(member, renamed(value.get(), member, rename));
      }
    }
    return out;
  }

  /**
   * Version 4 to 5: since version 5 the words {@code end}, {@code do}, {@code step}, {@code break},
   * {@code continue} and {@code global} are keywords of programs and {@code print} a builtin
   * ({@code docs/language.md} §19), so every name that is one of them is renamed {@code v_end}, or
   * with a digit after that too when the file already uses that name ({@code v_end1}), in every
   * cell's input and every expression and name of the canvas, as {@link #unitNames} renames; the
   * names are found as {@link #unitNames} finds them, the differential of a renamed name with it
   * ({@code d/ddo} is {@code d/dv_do}, {@code ∫ global^2 dglobal} is {@code ∫ v_global^2
   * dv_global}), and everything around a name is kept.
   *
   * @param v4 the version 4 tree
   * @return the tree with the names renamed
   */
  static Json.Obj keywordNames(Json.Obj v4) {
    return renamed(v4, KEYWORDS);
  }

  /** The name version 6 reserves that version 5 read as a name (docs/language.md §12.3). */
  static final Set<String> PLOT = Set.of("plot");

  /**
   * Version 5 to 6: since version 6 {@code plot} is a builtin, drawn by the worksheet ({@code
   * docs/language.md} §12.3), so the name {@code plot} is renamed {@code v_plot} (or with a digit
   * after that when the file uses that name) in every cell's input and every expression and name of
   * the canvas, as {@link #keywordNames} renames, its differential and a derivative's head with it.
   *
   * @param v5 the version 5 tree
   * @return the tree with the name renamed
   */
  static Json.Obj plotName(Json.Obj v5) {
    return renamed(v5, PLOT);
  }

  /**
   * Renames the names that became reserved, as {@link #keywordNames} says, each {@code v_} and the
   * name, with a digit after that when the file already uses that name.
   */
  private static Json.Obj renamed(Json.Obj v4, Set<String> words) {
    List<String> texts = new ArrayList<>();
    strings(v4.member("cells").orElse(null), "input", texts);
    strings(v4.member("canvas").orElse(null), "canvas", texts);
    Set<String> used = new HashSet<>();
    Set<String> found = new TreeSet<>();
    for (String t : texts) {
      for (int[] n : names(t)) {
        String name = lexed(t.substring(n[0], n[1]));
        (words.contains(name) ? found : used).add(name);
        if (isDifferentialPlace(t, n) && words.contains(name.substring(1))) {
          found.add(name.substring(1));
        }
      }
    }
    if (found.isEmpty()) {
      return v4;
    }
    Map<String, String> renamed = new HashMap<>();
    for (String name : found) {
      String to = "v_" + name;
      for (int k = 1; used.contains(to); k++) {
        to = "v_" + name + k;
      }
      used.add(to);
      renamed.put(name, to);
    }
    UnaryOperator<String> rename =
        t -> {
          StringBuilder out = new StringBuilder();
          int from = 0;
          for (int[] n : names(t)) {
            String name = t.substring(n[0], n[1]);
            String to = renamed.get(name);
            if (to != null) {
              out.append(t, from, n[0]).append(to);
              from = n[1];
            } else if (isDifferentialPlace(t, n) && renamed.containsKey(name.substring(1))) {
              // the differential of a renamed name, in a derivative's head or an integral
              out.append(t, from, n[0]).append('d').append(renamed.get(name.substring(1)));
              from = n[1];
            }
          }
          return out.append(t, from, t.length()).toString();
        };
    Json.Obj out = v4;
    for (String member : List.of("cells", "canvas")) {
      Optional<Json> value = v4.member(member);
      if (value.isPresent()) {
        out = out.with(member, renamed(value.get(), member, rename));
      }
    }
    return out;
  }

  /**
   * Whether the name {@code n}, {@code d} and an old name ({@code d_y}), stood for the differential
   * of that name: right after the {@code /} of a derivative's head ({@code d/d_y}, {@code d²/d_y²};
   * not a division, {@code 6/d_y}), or after a space or a closing bracket in a text with an
   * integral ({@code ∫ _y^2 d_y}). A name {@code d_y} of its own in those places reads the same,
   * and is renamed too.
   */
  private static boolean isDifferential(String t, int[] n) {
    return n[1] - n[0] >= 2 && t.charAt(n[0] + 1) == '_' && isDifferentialPlace(t, n);
  }

  /**
   * Whether the name {@code n}, {@code d} and more, stands where a differential does: right after
   * the {@code /} of a derivative's head, or after a space or a closing bracket in a text with an
   * integral ({@link #isDifferential}).
   */
  private static boolean isDifferentialPlace(String t, int[] n) {
    if (n[1] - n[0] < 2 || t.charAt(n[0]) != 'd' || n[0] == 0) {
      return false;
    }
    char before = t.charAt(n[0] - 1);
    if (before == '/') {
      // a derivative's head before the slash, not a division: 6/d_y stays
      Matcher head = DERIVATIVE_HEAD.matcher(t.substring(0, n[0] - 1));
      return head.find() && (head.start() == 0 || !isNameContinue(t.codePointBefore(head.start())));
    }
    return t.lastIndexOf('∫', n[0]) >= 0 && (before == ' ' || ")]}".indexOf(before) >= 0);
  }

  /**
   * The names of a version 3 text as its lexer read them, each its start and end: a name began with
   * a letter (not {@code π}, {@code Σ}, {@code Π}, {@code ℤ}, {@code ℚ}, {@code ℝ} or {@code ℂ},
   * which are tokens) or {@code _}, and went on over letters, ASCII digits, {@code _} and subscript
   * characters; a numeral, its decimals and exponent included, was read first, so it stopped at a
   * {@code _} that began a name ({@code 2_kq}). Superscripts, {@code π} and every other character
   * end a name.
   */
  private static List<int[]> names(String t) {
    List<int[]> out = new ArrayList<>();
    int i = 0;
    while (i < t.length()) {
      int c = t.codePointAt(i);
      if (isDigit(c) || c == '.' && i + 1 < t.length() && isDigit(t.charAt(i + 1))) {
        i = numeral(t, i);
      } else if (c == '_' || isNameStart(c)) {
        int j = i + Character.charCount(c);
        while (j < t.length() && isNameContinue(t.codePointAt(j))) {
          j += Character.charCount(t.codePointAt(j));
        }
        out.add(new int[] {i, j});
        i = j;
      } else {
        i += Character.charCount(c);
      }
    }
    return out;
  }

  /** The end of the numeral at {@code i}: digits, a point and digits, an exponent. */
  private static int numeral(String t, int i) {
    int j = digits(t, i);
    if (j < t.length() && t.charAt(j) == '.' && j + 1 < t.length() && isDigit(t.charAt(j + 1))) {
      j = digits(t, j + 1);
    }
    if (j < t.length() && (t.charAt(j) == 'e' || t.charAt(j) == 'E')) {
      int k = j + 1;
      if (k < t.length() && (t.charAt(k) == '+' || t.charAt(k) == '-')) {
        k++;
      }
      if (k < t.length() && isDigit(t.charAt(k))) {
        j = digits(t, k);
      }
    }
    return j;
  }

  private static int digits(String t, int i) {
    while (i < t.length() && isDigit(t.charAt(i))) {
      i++;
    }
    return i;
  }

  private static boolean isDigit(int c) {
    return c >= '0' && c <= '9';
  }

  /**
   * A letter that began a name in version 3, as {@code Lexer.isIdentStart} has it. The migrations
   * keep their own copy of the rules of their time, so a later lexer does not change what an old
   * file's migration finds; a test checks that the copy still agrees with the lexer.
   */
  static boolean isNameStart(int c) {
    if ("πΣΠℤℚℝℂ".indexOf(c) >= 0) {
      return false;
    }
    int type = Character.getType(c);
    return type == Character.UPPERCASE_LETTER
        || type == Character.LOWERCASE_LETTER
        || type == Character.TITLECASE_LETTER
        || type == Character.OTHER_LETTER;
  }

  /** A character that went on with a name: a letter, an ASCII digit, {@code _} or a subscript. */
  static boolean isNameContinue(int c) {
    return isNameStart(c)
        || isDigit(c)
        || c == '_'
        || (c >= 0x2080 && c <= 0x2089)
        || "ₐₑₒₓₕₖₗₘₙₚₛₜᵢᵣᵤᵥⱼ".indexOf(c) >= 0;
  }

  /**
   * A name as the lexer reads it, its subscripts written with {@code _} ({@code vₐ} is {@code
   * v_a}); the text itself when it is no name.
   */
  private static String lexed(String name) {
    try {
      Token t = new Lexer(name).next();
      return t.is(TokenKind.IDENT) ? t.text() : name;
    } catch (ParseException e) {
      return name;
    }
  }

  /** The strings of a tree that {@link #unitNames} renames, under the member {@code key}. */
  private static void strings(Json value, String key, List<String> out) {
    switch (value) {
      case Json.Str(String text) when !TEXTS.contains(key) -> out.add(text);
      case Json.Arr(List<Json> items) -> items.forEach(i -> strings(i, key, out));
      case Json.Obj o -> o.members().forEach((k, v) -> strings(v, k, out));
      case null, default -> {}
    }
  }

  private static Json renamed(Json value, String key, UnaryOperator<String> rename) {
    return switch (value) {
      case Json.Str(String text) when !TEXTS.contains(key) -> new Json.Str(rename.apply(text));
      case Json.Arr(List<Json> items) ->
          new Json.Arr(items.stream().map(i -> renamed(i, key, rename)).toList());
      case Json.Obj o -> {
        LinkedHashMap<String, Json> members = new LinkedHashMap<>();
        o.members().forEach((k, v) -> members.put(k, renamed(v, k, rename)));
        yield new Json.Obj(members);
      }
      default -> value;
    };
  }

  /**
   * What the file keeps of a document: everything but outputs and geometry. Records compare by
   * value, and an object the document did not change is the same object, so comparing what is kept
   * now with what was last saved is cheap.
   *
   * @param cells the cells' texts and whether each was evaluated
   * @param objects the canvas objects in drawing order
   * @param names the names of the named points
   * @param nextId the id the next canvas object gets
   * @param viewport the canvas's viewport
   * @param camera the 3D view's camera
   */
  public record Stored(
      List<StoredCell> cells,
      List<CanvasObject> objects,
      Map<ObjectId, String> names,
      long nextId,
      Viewport viewport,
      Camera camera) {

    /**
     * Copies the lists and the names.
     *
     * @throws NullPointerException if a field is null
     */
    public Stored {
      cells = List.copyOf(cells);
      objects = List.copyOf(objects);
      names = Map.copyOf(names);
      Objects.requireNonNull(viewport, "viewport");
      Objects.requireNonNull(camera, "camera");
    }

    /**
     * What a file keeps of a document seen through the default camera.
     *
     * @param cells the cells' texts and whether each was evaluated
     * @param objects the canvas objects in drawing order
     * @param names the names of the named points
     * @param nextId the id the next canvas object gets
     * @param viewport the canvas's viewport
     */
    public Stored(
        List<StoredCell> cells,
        List<CanvasObject> objects,
        Map<ObjectId, String> names,
        long nextId,
        Viewport viewport) {
      this(cells, objects, names, nextId, viewport, Camera.DEFAULT);
    }

    /**
     * What the file keeps of a state.
     *
     * @param state the state
     * @return what is kept
     */
    public static Stored of(DocumentState state) {
      List<StoredCell> cells = new ArrayList<>();
      for (Cell c : state.cells()) {
        cells.add(new StoredCell(c.input(), c.evaluated(), state.plotSlots(c.id())));
      }
      return new Stored(
          cells,
          state.canvas().objects(),
          state.names(),
          state.canvas().nextId(),
          state.viewport(),
          state.camera());
    }

    /**
     * The document a file holds: its cells pending as {@link DocumentState#of} makes them, and its
     * canvas with no geometry, so every object is undefined until it is recomputed.
     *
     * @param settings the document settings
     * @return the state
     */
    public DocumentState state(DocumentSettings settings) {
      DocumentState out =
          DocumentState.of(cells, settings)
              .withCanvas(new CanvasState(objects, Map.of(), nextId), names)
              .withViewport(viewport)
              .withCamera(camera);
      // the cells' ids count from 1 in order (DocumentState.of)
      for (int k = 0; k < cells.size(); k++) {
        if (!cells.get(k).plots().isEmpty()) {
          out = out.withPlots(k + 1, cells.get(k).plots());
        }
      }
      return out;
    }

    private StoredCanvas canvas() {
      return new StoredCanvas(new CanvasState(objects, Map.of(), nextId), names, viewport, camera);
    }
  }

  private SessionFile() {}

  /**
   * The file's tree, at the current version.
   *
   * @param stored what is kept of the document
   * @return the tree
   */
  public static Json.Obj encode(Stored stored) {
    List<Json> cells = new ArrayList<>();
    for (StoredCell c : stored.cells()) {
      LinkedHashMap<String, Json> cell = new LinkedHashMap<>();
      cell.put("input", new Json.Str(c.input()));
      cell.put("evaluated", new Json.Bool(c.evaluated()));
      if (!c.plots().isEmpty()) {
        cell.put(
            "plots",
            new Json.Arr(c.plots().stream().<Json>map(id -> Json.Num.of(id.value())).toList()));
      }
      cells.add(new Json.Obj(cell));
    }
    LinkedHashMap<String, Json> root = new LinkedHashMap<>();
    root.put(Migrations.VERSION, Json.Num.of(MIGRATIONS.current()));
    root.put("cells", new Json.Arr(cells));
    root.put("canvas", CanvasCodec.encode(stored.canvas()));
    return new Json.Obj(root);
  }

  /**
   * What a file holds, after migrating it to the current version.
   *
   * @param root the file's root value
   * @return the cells in order and the canvas
   * @throws FileFormatException if it is not a session file this release reads
   */
  public static Stored decode(Json root) throws FileFormatException {
    Json.Obj obj = MIGRATIONS.migrate(root);
    if (!(Fields.member(obj, "cells") instanceof Json.Arr(List<Json> items))) {
      throw new FileFormatException("\"cells\" is not an array");
    }
    List<StoredCell> cells = new ArrayList<>();
    StoredCanvas canvas = CanvasCodec.decode(Fields.member(obj, "canvas"));
    Set<ObjectId> owned = new HashSet<>();
    for (Json item : items) {
      if (!(item instanceof Json.Obj cell)) {
        throw new FileFormatException("a cell is not an object");
      }
      List<ObjectId> plots = new ArrayList<>();
      if (cell.member("plots").isPresent()) {
        if (!(cell.member("plots").get() instanceof Json.Arr(List<Json> ids))) {
          throw new FileFormatException("\"plots\" is not an array");
        }
        for (Json id : ids) {
          if (!(id instanceof Json.Num(String text)) || !text.matches("[1-9][0-9]{0,17}")) {
            throw new FileFormatException("a cell's graph is not an object's id");
          }
          ObjectId graph = new ObjectId(Long.parseLong(text));
          if (graph.value() >= canvas.state().nextId() || !owned.add(graph)) {
            throw new FileFormatException(
                "a cell's graph "
                    + graph.value()
                    + " is no id of the canvas, or is another cell's");
          }
          plots.add(graph);
        }
      }
      cells.add(
          new StoredCell(Fields.string(cell, "input"), Fields.bool(cell, "evaluated"), plots));
    }
    return new Stored(
        cells,
        canvas.state().objects(),
        canvas.names(),
        canvas.state().nextId(),
        canvas.viewport(),
        canvas.camera());
  }
}

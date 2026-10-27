package mathcanvas.app.persist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.DocumentState.StoredCell;
import mathcanvas.app.json.Json;

/**
 * The session file, {@code session.json}: the worksheet's cells, each its input text and whether it
 * was evaluated. Outputs are not stored; they are computed again on loading. Version 1:
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "cells": [
 *     { "input": "f(x) := x^2 + 1", "evaluated": true },
 *     { "input": "", "evaluated": false }
 *   ]
 * }
 * }</pre>
 *
 * <p>Reading is strict: a missing or ill-typed member is an error; members it does not know are
 * ignored. An exact number, when a later version stores one, is written as a string of its printed
 * text.
 */
public final class SessionFile {

  /** The file's name in the data directory. */
  public static final String NAME = "session.json";

  /** The versions of the file and the steps between them. */
  public static final Migrations MIGRATIONS = new Migrations(1, List.of());

  private SessionFile() {}

  /**
   * The file's tree for a state, at the current version.
   *
   * @param state the state
   * @return the tree
   */
  public static Json.Obj encode(DocumentState state) {
    List<Json> cells = new ArrayList<>();
    for (Cell c : state.cells()) {
      LinkedHashMap<String, Json> cell = new LinkedHashMap<>();
      cell.put("input", new Json.Str(c.input()));
      cell.put("evaluated", new Json.Bool(c.evaluated()));
      cells.add(new Json.Obj(cell));
    }
    LinkedHashMap<String, Json> root = new LinkedHashMap<>();
    root.put(Migrations.VERSION, Json.Num.of(MIGRATIONS.current()));
    root.put("cells", new Json.Arr(cells));
    return new Json.Obj(root);
  }

  /**
   * The cells a file holds, after migrating it to the current version.
   *
   * @param root the file's root value
   * @return the cells in order
   * @throws FileFormatException if it is not a session file this release reads
   */
  public static List<StoredCell> decode(Json root) throws FileFormatException {
    Json.Obj obj = MIGRATIONS.migrate(root);
    if (!(Fields.member(obj, "cells") instanceof Json.Arr(List<Json> items))) {
      throw new FileFormatException("\"cells\" is not an array");
    }
    List<StoredCell> out = new ArrayList<>();
    for (Json item : items) {
      if (!(item instanceof Json.Obj cell)) {
        throw new FileFormatException("a cell is not an object");
      }
      out.add(new StoredCell(Fields.string(cell, "input"), Fields.bool(cell, "evaluated")));
    }
    return out;
  }
}

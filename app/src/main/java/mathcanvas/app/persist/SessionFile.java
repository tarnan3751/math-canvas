package mathcanvas.app.persist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.DocumentState.StoredCell;
import mathcanvas.app.json.Json;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ObjectId;

/**
 * The session file, {@code session.json}: the worksheet's cells, each its input text and whether it
 * was evaluated, and the canvas: its viewport, its objects with their ids and {@code nextId}, and
 * the names of its points ({@link CanvasCodec}). Outputs and geometry are not stored; they are
 * computed again on loading, the cells first and then every object. Version 2:
 *
 * <pre>{@code
 * {
 *   "version": 2,
 *   "cells": [
 *     { "input": "a := slider(0, 5, 0.1)", "evaluated": true },
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
 * <p>Reading is strict: a missing or ill-typed member is an error; members it does not know are
 * ignored. A slider's exact numbers are JSON numbers written from their decimals; an expression is
 * its printed text. Version 1 had no {@code canvas}; its migration adds an empty one at a new
 * document's viewport.
 */
public final class SessionFile {

  /** The file's name in the data directory. */
  public static final String NAME = "session.json";

  /**
   * The versions of the file and the steps between them: version 1 to 2 adds an empty canvas, 2 to
   * 3 an {@code at} of {@code null} to every point on a path, which version 3 reads strictly
   * (version 3 also has the shaded areas and Riemann sums, which a version 2 file cannot hold).
   */
  public static final Migrations MIGRATIONS =
      new Migrations(
          3,
          List.<UnaryOperator<Json.Obj>>of(
              v1 -> v1.with("canvas", CanvasCodec.encode(StoredCanvas.DEFAULT)),
              CanvasCodec::pointsAtNothing));

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
   */
  public record Stored(
      List<StoredCell> cells,
      List<CanvasObject> objects,
      Map<ObjectId, String> names,
      long nextId,
      Viewport viewport) {

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
        cells.add(new StoredCell(c.input(), c.evaluated()));
      }
      return new Stored(
          cells,
          state.canvas().objects(),
          state.names(),
          state.canvas().nextId(),
          state.viewport());
    }

    /**
     * The document a file holds: its cells pending as {@link DocumentState#of} makes them, and its
     * canvas with no geometry, so every object is undefined until it is recomputed.
     *
     * @param settings the document settings
     * @return the state
     */
    public DocumentState state(DocumentSettings settings) {
      return DocumentState.of(cells, settings)
          .withCanvas(new CanvasState(objects, Map.of(), nextId), names)
          .withViewport(viewport);
    }

    private StoredCanvas canvas() {
      return new StoredCanvas(new CanvasState(objects, Map.of(), nextId), names, viewport);
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
    for (Json item : items) {
      if (!(item instanceof Json.Obj cell)) {
        throw new FileFormatException("a cell is not an object");
      }
      cells.add(new StoredCell(Fields.string(cell, "input"), Fields.bool(cell, "evaluated")));
    }
    StoredCanvas canvas = CanvasCodec.decode(Fields.member(obj, "canvas"));
    return new Stored(
        cells,
        canvas.state().objects(),
        canvas.names(),
        canvas.state().nextId(),
        canvas.viewport());
  }
}

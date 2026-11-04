package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.DocumentState.StoredCell;
import mathcanvas.app.document.Language;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.document.Theme;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.space.Camera;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

class FilesTest {

  /** The cells both session fixtures hold. */
  private static final List<StoredCell> CELLS =
      List.of(
          new StoredCell("f(x) := x^2 + 1", true),
          new StoredCell("f(3)", true),
          new StoredCell("sin(π/6) · 2", false),
          new StoredCell("", false));

  @Test
  void theVersion1SessionFixtureReadsWithAnEmptyCanvasAtANewDocumentsViewport() throws Exception {
    SessionFile.Stored read = SessionFile.decode(Fixtures.read("session-v1.json"));

    assertThat(read.cells()).isEqualTo(CELLS);
    assertThat(read.objects()).isEmpty();
    assertThat(read.names()).isEmpty();
    assertThat(read.nextId()).isEqualTo(1);
    assertThat(read.viewport()).isEqualTo(DocumentState.DEFAULT_VIEWPORT);
  }

  @Test
  void theVersion2SessionFixtureReads() throws Exception {
    assertThat(SessionFile.decode(Fixtures.read("session-v2.json")))
        .isEqualTo(SessionFile.decode(Fixtures.read("session-v1.json")));
  }

  @Test
  void aVersion1SessionFileIsSavedAsTheVersion4Fixture() throws Exception {
    String saved =
        JsonIo.text(SessionFile.encode(SessionFile.decode(Fixtures.read("session-v1.json"))));

    assertThat(saved).isEqualTo(Fixtures.text("session-v4.json"));
    assertThat(SessionFile.MIGRATIONS.migrate(Fixtures.read("session-v1.json")))
        .isEqualTo(Fixtures.read("session-v4.json"));
  }

  @Test
  void version2And3SessionFilesAreSavedAsTheVersion4Fixture() throws Exception {
    for (String old : List.of("session-v2.json", "session-v3.json")) {
      assertThat(JsonIo.text(SessionFile.encode(SessionFile.decode(Fixtures.read(old)))))
          .as(old)
          .isEqualTo(Fixtures.text("session-v4.json"));
    }
    assertThat(SessionFile.decode(Fixtures.read("session-v3.json")))
        .isEqualTo(SessionFile.decode(Fixtures.read("session-v1.json")));
    assertThat(SessionFile.decode(Fixtures.read("session-v4.json")))
        .isEqualTo(SessionFile.decode(Fixtures.read("session-v1.json")));
  }

  @Test
  void aVersion2CanvasGainsNoExpressionOnEveryPointOnAPath() throws Exception {
    // the v2 every-kind canvas: its points on paths are at parameters of their own since v3
    for (String old : List.of("session-v2-canvas.json", "session-v3-canvas.json")) {
      String saved = JsonIo.text(SessionFile.encode(SessionFile.decode(Fixtures.read(old))));
      assertThat(saved).as(old).isEqualTo(Fixtures.text("session-v4-canvas.json"));
    }
    SessionFile.Stored read = SessionFile.decode(Fixtures.read("session-v4-canvas.json"));
    assertThat(read.objects())
        .filteredOn(o -> o instanceof PointOnPath)
        .allSatisfy(o -> assertThat(((PointOnPath) o).at()).isEmpty())
        .hasSize(2);
  }

  @Test
  void aVersion3NameBeginningWithAnUnderscoreIsRenamed() throws Exception {
    // since units, _ and a letter begin a unit: _kq is v_kq1 (the file uses v_kq), _P is v_P,
    // everywhere, and 2_kq, the product 2·_kq, is 2v_kq1; C_1, x_kq and x2_kq are untouched
    String saved =
        JsonIo.text(
            SessionFile.encode(SessionFile.decode(Fixtures.read("session-v3-underscore.json"))));

    assertThat(saved).isEqualTo(Fixtures.text("session-v4-underscore.json"));
    assertThat(SessionFile.MIGRATIONS.migrate(Fixtures.read("session-v3-underscore.json")))
        .isEqualTo(Fixtures.read("session-v4-underscore.json"));
  }

  @Test
  void theNewNamesAvoidTheNamesTheFileUsesAsTheLexerReadsThem() throws Exception {
    // vₐ is v_a, and 2v_kq uses v_kq: neither merges with a renamed name
    Json.Obj v3 =
        (Json.Obj)
            JsonIo.parse(
                "{\"cells\": [{\"input\": \"vₐ := 1\"}, {\"input\": \"_a := 5\"},"
                    + " {\"input\": \"w := 2v_kq + _kq\"}, {\"input\": \"_ + _1 + __c\"},"
                    + " {\"input\": \"_t² + _tπ + _π + _² + _a₂ + 1e5_e + π_t\"}]}");

    assertThat(JsonIo.text(SessionFile.unitNames(v3).member("cells").orElseThrow()))
        .contains("\"vₐ := 1\"")
        .contains("\"v_a1 := 5\"")
        .contains("\"w := 2v_kq + v_kq1\"")
        .contains("\"v_ + v_1 + v__c\"")
        .contains("\"v_t² + v_tπ + v_π + v_² + v_a_2 + 1e5v_e + πv_t\"");
  }

  @Test
  void theRenamingLeavesFreeTextAndNamesWithoutAnUnderscoreAlone() throws Exception {
    Json.Obj v3 =
        (Json.Obj)
            JsonIo.parse(
                "{\"cells\": [{\"input\": \"_a + _a_b + a_b + 2*_θ + 2_a + x2_a + 0.5_a # _a\"}],"
                    + " \"canvas\": {\"objects\": [{\"kind\": \"text\", \"text\": \"_a\"}]}}");
    Json.Obj v4 = SessionFile.unitNames(v3);

    assertThat(v4.member("canvas")).isEqualTo(v3.member("canvas"));
    assertThat(JsonIo.text(v4.member("cells").orElseThrow()))
        .contains("v_a + v_a_b + a_b + 2*v_θ + 2v_a + x2_a + 0.5v_a # v_a");
  }

  @Test
  void aVersion3PointOnAPathWithoutAtIsRefused() throws Exception {
    Json.Obj v3 = (Json.Obj) Fixtures.read("session-v3-canvas.json");
    Json.Obj canvas = (Json.Obj) v3.member("canvas").orElseThrow();
    Json.Arr objects = (Json.Arr) canvas.member("objects").orElseThrow();
    List<Json> items = new ArrayList<>(objects.items());
    items.set(7, ((Json.Obj) items.get(7)).without("at"));
    Json.Obj broken = v3.with("canvas", canvas.with("objects", new Json.Arr(items)));

    assertThatThrownBy(() -> SessionFile.decode(broken))
        .isInstanceOf(FileFormatException.class)
        .hasMessageContaining("at");
  }

  @Test
  void aSessionWithACanvasIsWrittenAndReadBack() throws Exception {
    StoredCanvas c = CanvasStates.everyKind();
    DocumentState s =
        DocumentState.of(CELLS, DocumentSettings.DEFAULT)
            .withCanvas(c.state(), c.names())
            .withViewport(c.viewport())
            // Phase 7d: and the 3D view's camera
            .withCamera(new Camera(1, 0.5, 9, 0, 0, 0.25));
    String text = JsonIo.text(SessionFile.encode(SessionFile.Stored.of(s)));
    SessionFile.Stored read = SessionFile.decode(JsonIo.parse(text));

    assertThat(read).isEqualTo(SessionFile.Stored.of(s));
    assertThat(read.state(DocumentSettings.DEFAULT)).isEqualTo(s);
    assertThat(read.state(DocumentSettings.DEFAULT).canvas().geometry()).isEmpty();
    assertThat(JsonIo.text(SessionFile.encode(read))).isEqualTo(text);
  }

  /**
   * What every settings fixture holds: version 1's values, and the defaults versions 2 to 5 added.
   */
  private static final SettingsFile.Stored FIXTURE =
      new SettingsFile.Stored(
          new DocumentSettings(50, AngleMode.DEGREES, NumberFormat.DECIMAL, 10),
          new Preferences(
              Theme.DARK, false, true, false, Optional.empty(), false, Language.SYSTEM));

  @Test
  void theVersion1SettingsFixtureReadsWithTheVersion2Defaults() throws Exception {
    assertThat(SettingsFile.decode(Fixtures.read("settings-v1.json"))).isEqualTo(FIXTURE);
  }

  @Test
  void theVersion2SettingsFixtureReads() throws Exception {
    assertThat(SettingsFile.decode(Fixtures.read("settings-v2.json"))).isEqualTo(FIXTURE);
  }

  @Test
  void theVersion3SettingsFixtureReads() throws Exception {
    assertThat(SettingsFile.decode(Fixtures.read("settings-v3.json"))).isEqualTo(FIXTURE);
  }

  @Test
  void theVersion4SettingsFixtureReads() throws Exception {
    assertThat(SettingsFile.decode(Fixtures.read("settings-v4.json"))).isEqualTo(FIXTURE);
  }

  @Test
  void theVersion5SettingsFixtureReads() throws Exception {
    assertThat(SettingsFile.decode(Fixtures.read("settings-v5.json"))).isEqualTo(FIXTURE);
  }

  @Test
  void version1To4SettingsFilesAreSavedAsTheVersion5Fixture() throws Exception {
    for (String old :
        List.of("settings-v1.json", "settings-v2.json", "settings-v3.json", "settings-v4.json")) {
      String saved = JsonIo.text(SettingsFile.encode(SettingsFile.decode(Fixtures.read(old))));

      assertThat(saved).isEqualTo(Fixtures.text("settings-v5.json"));
      assertThat(SettingsFile.MIGRATIONS.migrate(Fixtures.read(old)))
          .isEqualTo(Fixtures.read("settings-v5.json"));
    }
  }

  @Test
  void aSessionIsWrittenAsItsCellsAndReadBack() throws Exception {
    DocumentState s =
        new Command.Submit(1)
            .apply(new Command.EditInput(1, "a := \"π\"\n").apply(DocumentState.EMPTY));
    Json.Obj tree = SessionFile.encode(SessionFile.Stored.of(s));

    assertThat(JsonIo.text(tree))
        .isEqualTo(
            """
            {
              "version": 4,
              "cells": [
                {
                  "input": "a := \\"π\\"\\n",
                  "evaluated": true
                },
                {
                  "input": "",
                  "evaluated": false
                }
              ],
              "canvas": {
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
            }
            """);
    assertThat(SessionFile.decode(JsonIo.parse(JsonIo.text(tree))).state(s.settings()))
        .isEqualTo(s);
  }

  @Test
  void settingsAreWrittenAndReadBack() throws Exception {
    SettingsFile.Stored stored =
        new SettingsFile.Stored(
            new DocumentSettings(10_000, AngleMode.GRADIANS, NumberFormat.SCIENTIFIC, 10_000),
            new Preferences(
                Theme.LIGHT,
                true,
                false,
                true,
                Optional.of("/exports/canvas"),
                true,
                Language.FRENCH));

    assertThat(JsonIo.text(SettingsFile.encode(SettingsFile.Stored.DEFAULT)))
        .isEqualTo(
            """
            {
              "version": 5,
              "precision": 34,
              "angleMode": "radians",
              "numberFormat": "exact",
              "numberDigits": 10,
              "theme": "system",
              "showSteps": false,
              "keypadShown": true,
              "canvasShown": false,
              "exportFolder": null,
              "checkForUpdates": false,
              "language": "system"
            }
            """);
    assertThat(SettingsFile.decode(JsonIo.parse(JsonIo.text(SettingsFile.encode(stored)))))
        .isEqualTo(stored);
  }

  @Test
  void membersTheReaderDoesNotKnowAreIgnored() throws Exception {
    assertThat(
            SessionFile.decode(
                JsonIo.parse(
                    "{\"version\": 1, \"note\": 1, \"cells\": [{\"input\": \"x\","
                        + " \"evaluated\": false, \"colour\": \"red\"}]}")))
        .extracting(SessionFile.Stored::cells)
        .isEqualTo(List.of(new StoredCell("x", false)));
  }

  @Test
  void aSessionFileOfTheWrongShapeIsRejected() {
    for (String bad :
        List.of(
            "[]",
            "{\"cells\": []}",
            "{\"version\": 0, \"cells\": []}",
            "{\"version\": \"1\", \"cells\": []}",
            "{\"version\": 1.0, \"cells\": []}",
            "{\"version\": 3, \"cells\": []}",
            "{\"version\": 2, \"cells\": []}",
            "{\"version\": 2, \"cells\": [], \"canvas\": {}}",
            "{\"version\": 2, \"cells\": [], \"canvas\": []}",
            "{\"version\": 99999999999999999999, \"cells\": []}",
            "{\"version\": 1}",
            "{\"version\": 1, \"cells\": {}}",
            "{\"version\": 1, \"cells\": [1]}",
            "{\"version\": 1, \"cells\": [{\"input\": \"x\"}]}",
            "{\"version\": 1, \"cells\": [{\"input\": 1, \"evaluated\": true}]}",
            "{\"version\": 1, \"cells\": [{\"input\": \"x\", \"evaluated\": \"yes\"}]}")) {
      assertThatThrownBy(() -> SessionFile.decode(JsonIo.parse(bad)))
          .as(bad)
          .isInstanceOf(FileFormatException.class);
    }
  }

  @Test
  void aSettingsFileOfTheWrongShapeIsRejected() throws IOException {
    Json.Obj good = SettingsFile.encode(SettingsFile.Stored.DEFAULT);
    for (Json.Obj bad :
        List.of(
            good.without("precision"),
            good.with("precision", Json.Num.of(0)),
            good.with("precision", Json.Num.of(10_001)),
            good.with("precision", new Json.Num("34.0")),
            good.with("precision", new Json.Num("12345678901")),
            good.with("angleMode", new Json.Str("RADIANS")),
            good.with("numberFormat", new Json.Str("hex")),
            good.with("theme", Json.Num.of(1)),
            good.without("theme"),
            good.with("numberDigits", Json.Num.of(0)),
            good.with("numberDigits", Json.Num.of(10_001)),
            good.with("showSteps", new Json.Str("no")),
            good.without("keypadShown"),
            good.without("canvasShown"),
            good.with("canvasShown", new Json.Str("true")),
            good.with("canvasShown", Json.Num.of(1)),
            good.without("exportFolder"),
            good.with("exportFolder", Json.Num.of(1)))) {
      assertThatThrownBy(() -> SettingsFile.decode(bad))
          .as(JsonIo.text(bad))
          .isInstanceOf(FileFormatException.class);
    }
  }
}

package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.DocumentState.StoredCell;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.document.Theme;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

class FilesTest {

  @Test
  void theVersion1SessionFixtureReads() throws Exception {
    assertThat(SessionFile.decode(Fixtures.read("session-v1.json")))
        .containsExactly(
            new StoredCell("f(x) := x^2 + 1", true),
            new StoredCell("f(3)", true),
            new StoredCell("sin(π/6) · 2", false),
            new StoredCell("", false));
  }

  /**
   * What every settings fixture holds: version 1's values, and the defaults versions 2 and 3 added.
   */
  private static final SettingsFile.Stored FIXTURE =
      new SettingsFile.Stored(
          new DocumentSettings(50, AngleMode.DEGREES, NumberFormat.DECIMAL, 10),
          new Preferences(Theme.DARK, false, true, false));

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
  void version1And2SettingsFilesAreSavedAsTheVersion3Fixture() throws Exception {
    for (String old : List.of("settings-v1.json", "settings-v2.json")) {
      String saved = JsonIo.text(SettingsFile.encode(SettingsFile.decode(Fixtures.read(old))));

      assertThat(saved).isEqualTo(Fixtures.text("settings-v3.json"));
      assertThat(SettingsFile.MIGRATIONS.migrate(Fixtures.read(old)))
          .isEqualTo(Fixtures.read("settings-v3.json"));
    }
  }

  @Test
  void aSessionIsWrittenAsItsCellsAndReadBack() throws Exception {
    DocumentState s =
        new Command.Submit(1)
            .apply(new Command.EditInput(1, "a := \"π\"\n").apply(DocumentState.EMPTY));
    Json.Obj tree = SessionFile.encode(s);

    assertThat(JsonIo.text(tree))
        .isEqualTo(
            """
            {
              "version": 1,
              "cells": [
                {
                  "input": "a := \\"π\\"\\n",
                  "evaluated": true
                },
                {
                  "input": "",
                  "evaluated": false
                }
              ]
            }
            """);
    assertThat(DocumentState.of(SessionFile.decode(JsonIo.parse(JsonIo.text(tree))), s.settings()))
        .isEqualTo(s);
  }

  @Test
  void settingsAreWrittenAndReadBack() throws Exception {
    SettingsFile.Stored stored =
        new SettingsFile.Stored(
            new DocumentSettings(10_000, AngleMode.GRADIANS, NumberFormat.SCIENTIFIC, 10_000),
            new Preferences(Theme.LIGHT, true, false, true));

    assertThat(JsonIo.text(SettingsFile.encode(SettingsFile.Stored.DEFAULT)))
        .isEqualTo(
            """
            {
              "version": 3,
              "precision": 34,
              "angleMode": "radians",
              "numberFormat": "exact",
              "numberDigits": 10,
              "theme": "system",
              "showSteps": false,
              "keypadShown": true,
              "canvasShown": false
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
        .containsExactly(new StoredCell("x", false));
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
            "{\"version\": 2, \"cells\": []}",
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
            good.with("canvasShown", Json.Num.of(1)))) {
      assertThatThrownBy(() -> SettingsFile.decode(bad))
          .as(JsonIo.text(bad))
          .isInstanceOf(FileFormatException.class);
    }
  }
}

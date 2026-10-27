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

  @Test
  void theVersion1SettingsFixtureReads() throws Exception {
    assertThat(SettingsFile.decode(Fixtures.read("settings-v1.json")))
        .isEqualTo(
            new SettingsFile.Stored(
                new DocumentSettings(50, AngleMode.DEGREES, NumberFormat.DECIMAL),
                new Preferences(Theme.DARK)));
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
            new DocumentSettings(10_000, AngleMode.GRADIANS, NumberFormat.SCIENTIFIC),
            new Preferences(Theme.LIGHT));

    assertThat(JsonIo.text(SettingsFile.encode(SettingsFile.Stored.DEFAULT)))
        .isEqualTo(
            """
            {
              "version": 1,
              "precision": 34,
              "angleMode": "radians",
              "numberFormat": "exact",
              "theme": "system"
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
            good.without("theme"))) {
      assertThatThrownBy(() -> SettingsFile.decode(bad))
          .as(JsonIo.text(bad))
          .isInstanceOf(FileFormatException.class);
    }
  }
}

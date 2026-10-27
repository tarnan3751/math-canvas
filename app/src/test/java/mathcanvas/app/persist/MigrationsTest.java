package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.UnaryOperator;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import org.junit.jupiter.api.Test;

class MigrationsTest {

  /** A made-up history: version 2 renamed "text" to "input", version 3 added "evaluated". */
  private static final Migrations HISTORY =
      new Migrations(
          3,
          List.<UnaryOperator<Json.Obj>>of(
              o -> o.without("text").with("input", o.member("text").orElseThrow()),
              o -> o.with("evaluated", new Json.Bool(false))));

  @Test
  void eachStepRunsInOrderFromTheFilesVersion() throws Exception {
    Json.Obj v1 = (Json.Obj) JsonIo.parse("{\"version\": 1, \"text\": \"x\"}");
    Json.Obj v2 = (Json.Obj) JsonIo.parse("{\"version\": 2, \"input\": \"y\"}");

    assertThat(HISTORY.migrate(v1))
        .isEqualTo(JsonIo.parse("{\"version\": 3, \"input\": \"x\", \"evaluated\": false}"));
    assertThat(HISTORY.migrate(v2))
        .isEqualTo(JsonIo.parse("{\"version\": 3, \"input\": \"y\", \"evaluated\": false}"));
  }

  @Test
  void aCurrentFileIsUnchanged() throws Exception {
    Json v3 = JsonIo.parse("{\"version\": 3, \"input\": \"z\", \"evaluated\": true}");

    assertThat(HISTORY.migrate(v3)).isEqualTo(v3);
  }

  @Test
  void aNewerFileIsRejected() throws Exception {
    Json v4 = JsonIo.parse("{\"version\": 4}");

    assertThatThrownBy(() -> HISTORY.migrate(v4))
        .isInstanceOf(FileFormatException.class)
        .hasMessageContaining("newer");
  }

  @Test
  void thereIsOneStepPerEarlierVersion() {
    assertThatThrownBy(() -> new Migrations(2, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Migrations(0, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

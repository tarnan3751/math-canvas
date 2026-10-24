package mathcanvas.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VersionTest {

  @Test
  void parsesAReleaseAndAQualifiedVersion() {
    assertThat(Version.parse("1.2.3")).isEqualTo(new Version(1, 2, 3, ""));
    assertThat(Version.parse("0.1.0-SNAPSHOT")).isEqualTo(new Version(0, 1, 0, "SNAPSHOT"));
    assertThat(Version.parse("10.20.30-rc.1.x-y")).isEqualTo(new Version(10, 20, 30, "rc.1.x-y"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.1.0", "0.1.0-SNAPSHOT", "1.0.0-alpha.1", "2147483647.0.0-0.a.b-c"})
  void toStringReproducesTheInput(String text) {
    assertThat(Version.parse(text)).hasToString(text);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "1",
        "1.2",
        "1.2.3.4",
        "v1.2.3",
        " 1.2.3",
        "1.2.3 ",
        "01.2.3",
        "1.02.3",
        "1.2.03",
        "-1.2.3",
        "1.-2.3",
        "1.2.3-",
        "1.2.3-.",
        "1.2.3-a..b",
        "1.2.3-a.",
        "1.2.3-01",
        "1.2.3-rc.01",
        "1.2.3-a_b",
        "1.2.3+build",
        "1.2.3-rc+build",
        "2147483648.0.0",
        "1.2.x",
        "1.2.3-é",
        "1.2.3-a\n",
        "1.2.3-\n"
      })
  void rejectsMalformedText(String text) {
    assertThatThrownBy(() -> Version.parse(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesALongQualifier() {
    String text = "1.2.3-" + "a.".repeat(2000) + "a";
    Version version = Version.parse(text);
    assertThat(version.qualifier()).hasSize(4001);
    assertThat(version).hasToString(text);
  }

  @Test
  void rejectsMalformedComponents() {
    assertThatThrownBy(() -> new Version(-1, 0, 0, ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Version(0, 0, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Version(0, 0, 0, "a..b"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ordersByComponentsThenQualifier() {
    // The release chain is the example in the semver 2.0 specification, section 11.
    List<String> ascending =
        List.of(
            "0.1.0-SNAPSHOT",
            "0.1.0",
            "0.1.1",
            "0.2.0",
            "0.10.0",
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0-rc.2",
            "1.0.0-rc.10",
            "1.0.0",
            "2.0.0");
    for (int i = 0; i + 1 < ascending.size(); i++) {
      Version lower = Version.parse(ascending.get(i));
      Version higher = Version.parse(ascending.get(i + 1));
      assertThat(lower).isLessThan(higher);
      assertThat(higher).isGreaterThan(lower);
    }
  }

  @Test
  void currentIsTheBuildVersion() {
    Version current = Version.current();
    assertThat(Version.parse(current.toString())).isEqualTo(current);
  }
}

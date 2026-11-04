package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The build's facts reach the code through app.properties, expanded. */
class AppInfoTest {

  @Test
  void theNameAndTheRepositoryAreExpanded() {
    assertThat(AppInfo.name()).isEqualTo("math-canvas");
    assertThat(AppInfo.repository()).isEqualTo("tarnan3751/math-canvas");
  }
}

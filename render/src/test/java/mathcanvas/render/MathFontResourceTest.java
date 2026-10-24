package mathcanvas.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * The bundled font is the unmodified upstream file, with its licence beside it. The OFL's reserved
 * font name clause applies only to modified copies, so the hash is what lets the font keep its
 * name. The font is not loaded through JavaFX here: tests run without a display.
 */
class MathFontResourceTest {

  private static final String FONT = "fonts/STIXTwoMath-Regular.ttf";
  private static final String LICENCE = "fonts/OFL.txt";
  private static final String FONT_SHA_256 =
      "49b22767985171bce579307b21d98f4d35012fa39e316acc4ffcab8644dfb69b";

  @Test
  void theFontIsTheUnmodifiedUpstreamFile() throws IOException, NoSuchAlgorithmException {
    try (InputStream in = MathFontResourceTest.class.getResourceAsStream(FONT)) {
      assertThat(in).as(FONT).isNotNull();
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
      assertThat(HexFormat.of().formatHex(digest)).isEqualTo(FONT_SHA_256);
    }
  }

  @Test
  void theLicenceIsBesideTheFont() throws IOException {
    try (InputStream in = MathFontResourceTest.class.getResourceAsStream(LICENCE)) {
      assertThat(in).as(LICENCE).isNotNull();
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
          .contains("SIL OPEN FONT LICENSE Version 1.1");
    }
  }
}

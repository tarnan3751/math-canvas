package mathcanvas.app.persist;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;

/** The files of earlier versions kept in the test resources, which every later release reads. */
final class Fixtures {

  private Fixtures() {}

  static String text(String name) throws IOException {
    try (InputStream in = Fixtures.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IOException("no fixture " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  static Json read(String name) throws IOException {
    try (InputStream in = Fixtures.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IOException("no fixture " + name);
      }
      return JsonIo.read(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
  }
}

package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javafx.application.Platform;
import mathcanvas.app.update.UpdateCheck;
import mathcanvas.engine.Version;
import org.junit.jupiter.api.Test;

/**
 * The update notice: off by default, so nothing is asked and no link shown; a newer release found
 * by the check (against a stub server on the loopback interface) shows a link in the status line.
 */
class UpdateNoticeTest extends WorksheetHarness {

  @Test
  void aNewerReleaseShowsALinkInTheStatusLine() throws Exception {
    assertThat(get(() -> window.updateLink().isVisible())).isFalse();
    assertThat(get(() -> main.workspace().preferences().checkForUpdates().get())).isFalse();

    String body =
        "{\"tag_name\": \"v99.0.0\", \"html_url\":"
            + " \"https://github.com/owner/repo/releases/tag/v99.0.0\"}";
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Thread answer =
          new Thread(
              () -> {
                try (Socket s = server.accept()) {
                  BufferedReader in =
                      new BufferedReader(
                          new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                  while (!in.readLine().isEmpty()) {
                    // the request's headers
                  }
                  byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                  OutputStream out = s.getOutputStream();
                  out.write(
                      ("HTTP/1.1 200 OK\r\nContent-Length: "
                              + bytes.length
                              + "\r\nConnection: close\r\n\r\n")
                          .getBytes(StandardCharsets.UTF_8));
                  out.write(bytes);
                } catch (Exception e) {
                  // the test fails on the missing link
                }
              });
      answer.setDaemon(true);
      answer.start();
      UpdateCheck check =
          new UpdateCheck(
              URI.create("http://127.0.0.1:" + server.getLocalPort() + "/releases/latest"),
              Version.current(),
              Duration.ofSeconds(3));
      fx(() -> main.workspace().checkForUpdates(check, Platform::runLater));
      waitUntil(10, () -> window.updateLink().isVisible());
    }
    assertThat(get(() -> window.updateLink().getText()))
        .isEqualTo("math-canvas 99.0.0 is available");
    screenshot("update-notice.png");
  }
}

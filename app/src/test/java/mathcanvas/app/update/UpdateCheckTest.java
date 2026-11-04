package mathcanvas.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The update check against a stub server on the loopback interface: never the network. */
class UpdateCheckTest {

  private static final Version RUNNING = Version.parse("0.9.0");

  /** What the stub does with a connection once it has read the request. */
  @FunctionalInterface
  private interface Answer {
    void answer(Socket s) throws IOException, InterruptedException;
  }

  /** A server that answers every connection the same way and counts them. */
  private static final class Stub implements AutoCloseable {
    final ServerSocket socket;
    final List<String> requests = new ArrayList<>();
    final AtomicInteger connections = new AtomicInteger();
    private final Thread thread;

    Stub(Answer answer) throws IOException {
      socket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
      thread =
          new Thread(
              () -> {
                while (!socket.isClosed()) {
                  try (Socket s = socket.accept()) {
                    connections.incrementAndGet();
                    BufferedReader in =
                        new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    for (String line = in.readLine();
                        line != null && !line.isEmpty();
                        line = in.readLine()) {
                      synchronized (requests) {
                        requests.add(line);
                      }
                    }
                    answer.answer(s);
                  } catch (IOException | InterruptedException e) {
                    // the test is over, or the client went away
                  }
                }
              });
      thread.setDaemon(true);
      thread.start();
    }

    URI uri() {
      return URI.create(
          "http://127.0.0.1:" + socket.getLocalPort() + "/repos/owner/repo/releases/latest");
    }

    @Override
    public void close() throws IOException {
      socket.close();
      thread.interrupt();
    }
  }

  private Stub stub;

  @AfterEach
  void stop() throws IOException {
    if (stub != null) {
      stub.close();
    }
  }

  private static Answer text(String response) {
    return s -> {
      OutputStream out = s.getOutputStream();
      out.write(response.getBytes(StandardCharsets.UTF_8));
      out.flush();
    };
  }

  private static String ok(String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
        + bytes.length
        + "\r\nConnection: close\r\n\r\n"
        + body;
  }

  private Optional<UpdateCheck.Release> check(Answer answer, Duration timeout) throws IOException {
    if (stub != null) {
      stub.close();
    }
    stub = new Stub(answer);
    return new UpdateCheck(stub.uri(), RUNNING, timeout).check();
  }

  private Optional<UpdateCheck.Release> check(String response) throws IOException {
    return check(text(response), UpdateCheck.TIMEOUT);
  }

  @Test
  void aNewerReleaseIsReportedWithItsPage() throws IOException {
    Optional<UpdateCheck.Release> release =
        check(
            ok(
                "{\"tag_name\": \"v1.0.0\", \"html_url\":"
                    + " \"https://github.com/owner/repo/releases/tag/v1.0.0\", \"draft\": false}"));

    assertThat(release)
        .contains(
            new UpdateCheck.Release(
                Version.parse("1.0.0"), "https://github.com/owner/repo/releases/tag/v1.0.0"));
    assertThat(stub.requests.getFirst())
        .isEqualTo("GET /repos/owner/repo/releases/latest HTTP/1.1");
    assertThat(stub.requests).contains("Accept: application/vnd.github+json");
    assertThat(stub.connections).hasValue(1);
  }

  @Test
  void theSameOrAnOlderReleaseIsNothing() throws IOException {
    assertThat(check(ok("{\"tag_name\": \"v0.9.0\", \"html_url\": \"https://github.com/o/r\"}")))
        .isEmpty();
    assertThat(check(ok("{\"tag_name\": \"0.8.1\", \"html_url\": \"https://github.com/o/r\"}")))
        .isEmpty();
  }

  @Test
  void anErrorOrABodyOfAnotherShapeIsNothingAndIsNotResent() throws IOException {
    assertThat(check("HTTP/1.1 404 Not Found\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"))
        .isEmpty();
    assertThat(stub.connections).hasValue(1);
    assertThat(check(ok("not json"))).isEmpty();
    assertThat(check(ok("{\"tag_name\": \"latest\", \"html_url\": \"https://github.com/o/r\"}")))
        .isEmpty();
    // a page off GitHub is never offered
    assertThat(check(ok("{\"tag_name\": \"v2.0.0\", \"html_url\": \"https://example.com/r\"}")))
        .isEmpty();
  }

  @Test
  void aRedirectIsNotFollowed() throws IOException {
    assertThat(
            check(
                "HTTP/1.1 302 Found\r\nLocation: /elsewhere\r\nContent-Length: 0\r\n"
                    + "Connection: close\r\n\r\n"))
        .isEmpty();
    assertThat(stub.connections).hasValue(1);
    assertThat(stub.requests).filteredOn(l -> l.startsWith("GET ")).hasSize(1);
  }

  @Test
  void aServerThatDoesNotAnswerTimesOut() throws IOException {
    long start = System.nanoTime();
    assertThat(check(s -> Thread.sleep(10_000), Duration.ofMillis(300))).isEmpty();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }

  @Test
  void aBodyDribbledPastTheBoundIsCutOff() throws IOException {
    long start = System.nanoTime();
    Optional<UpdateCheck.Release> release =
        check(
            s -> {
              OutputStream out = s.getOutputStream();
              out.write(
                  "HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n"
                      .getBytes(StandardCharsets.UTF_8));
              for (int k = 0; k < 100; k++) {
                out.write(' ');
                out.flush();
                Thread.sleep(100);
              }
            },
            Duration.ofMillis(500));

    assertThat(release).isEmpty();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }

  @Test
  void aConnectionClosedBeforeTheAnswerIsNothingAndSentAtMostTwice() throws IOException {
    assertThat(check(s -> {}, UpdateCheck.TIMEOUT)).isEmpty();
    assertThat(stub.connections.get()).isBetween(1, 2);
  }

  @Test
  void noServerIsNothing() throws IOException {
    int port;
    try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = free.getLocalPort();
    }
    URI closed = URI.create("http://127.0.0.1:" + port + "/repos/o/r/releases/latest");
    assertThat(new UpdateCheck(closed, RUNNING, UpdateCheck.TIMEOUT).check()).isEmpty();
    assertThat(System.getProperty("jdk.httpclient.disableRetryConnect")).isEqualTo("true");
  }

  @Test
  void theStandardCheckAsksTheProjectsRepository() {
    assertThat(UpdateCheck.standard().endpoint())
        .isEqualTo(
            URI.create("https://api.github.com/repos/tarnan3751/math-canvas/releases/latest"));
  }
}

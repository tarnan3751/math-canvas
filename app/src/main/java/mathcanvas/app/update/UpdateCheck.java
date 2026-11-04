package mathcanvas.app.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import mathcanvas.app.AppInfo;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import mathcanvas.engine.Version;

/**
 * The optional update check, the only network call the app makes, and only when the person turned
 * it on: one GET of {@code https://api.github.com/repos/<owner>/<repo>/releases/latest}, whose
 * {@code tag_name} ({@code v1.2.3}) is compared with the running version. The whole exchange,
 * connecting, the headers and the body, is bounded by three seconds, the request cancelled past
 * them. The JDK's client is told not to retry a refused connection ({@code
 * jdk.httpclient.disableRetryConnect}); the one case it still resends, a connection the server
 * closed before answering, sends the GET at most twice, and an error status is never resent. No
 * redirect is followed. A newer release gives its page, which must be on {@code
 * https://github.com/}; anything else (no answer, an error status, a body of another shape, a tag
 * that is no version, an older or equal release) gives nothing, silently. It never downloads or
 * installs anything. The repository comes from the resource {@code app.properties}, which the build
 * expands ({@link AppInfo}).
 */
public final class UpdateCheck implements UpdateChecker {

  static {
    // a documented java.net.http property, read when the client's classes load; the app makes no
    // other HTTP call
    System.setProperty("jdk.httpclient.disableRetryConnect", "true");
  }

  /** How long the whole exchange may take, connecting and the body included. */
  public static final Duration TIMEOUT = Duration.ofSeconds(3);

  private static final String PAGES = "https://github.com/";

  /**
   * A release newer than the running version.
   *
   * @param version its version
   * @param page its page on GitHub
   */
  public record Release(Version version, String page) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if one is null
     */
    public Release {
      Objects.requireNonNull(version, "version");
      Objects.requireNonNull(page, "page");
    }
  }

  private final URI endpoint;
  private final Version running;
  private final Duration timeout;

  /**
   * A check against an endpoint.
   *
   * @param endpoint the URL of the latest release's JSON
   * @param running the running version
   * @param timeout how long the whole exchange may take
   */
  public UpdateCheck(URI endpoint, Version running, Duration timeout) {
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.running = Objects.requireNonNull(running, "running");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  /**
   * The check the app makes: the repository of {@code app.properties}, this build's version, the
   * three-second timeout.
   *
   * @return the check
   */
  public static UpdateCheck standard() {
    return new UpdateCheck(
        URI.create("https://api.github.com/repos/" + AppInfo.repository() + "/releases/latest"),
        Version.current(),
        TIMEOUT);
  }

  /** The URL asked, for the tests. */
  URI endpoint() {
    return endpoint;
  }

  /**
   * Asks for the latest release, blocking for at most the timeout: called off the FX thread.
   *
   * @return a newer release, or empty
   */
  @Override
  public Optional<Release> check() {
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    CompletableFuture<HttpResponse<String>> answer =
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    try {
      HttpResponse<String> response = answer.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
      return response.statusCode() == 200 ? newer(response.body(), running) : Optional.empty();
    } catch (TimeoutException | ExecutionException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } finally {
      answer.cancel(true);
      client.shutdownNow();
    }
  }

  /**
   * The release a latest-release body names, when it is newer than the running version.
   *
   * @param body the JSON GitHub answers
   * @param running the running version
   * @return the release, or empty
   */
  static Optional<Release> newer(String body, Version running) {
    try {
      if (!(JsonIo.parse(body) instanceof Json.Obj o)
          || !(o.member("tag_name").orElse(null) instanceof Json.Str tag)
          || !(o.member("html_url").orElse(null) instanceof Json.Str page)
          || !page.value().startsWith(PAGES)) {
        return Optional.empty();
      }
      String text = tag.value().startsWith("v") ? tag.value().substring(1) : tag.value();
      Version version = Version.parse(text);
      return version.compareTo(running) > 0
          ? Optional.of(new Release(version, page.value()))
          : Optional.empty();
    } catch (IOException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

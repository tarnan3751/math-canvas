package mathcanvas.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The checker finds each kind of broken link, so a clean pass on the real site means something. */
class LinkCheckTest {

  private static String page(String body) {
    return "<!DOCTYPE html>\n<html><head><title>t</title></head><body>" + body + "</body></html>\n";
  }

  @Test
  void aSoundSitePasses(@TempDir Path site) throws IOException {
    Files.writeString(
        site.resolve("a.html"), page("<h1 id=\"top\">A</h1><a href=\"b.html#x\">b</a>"));
    Files.writeString(
        site.resolve("b.html"),
        page("<h2 id=\"x\">X</h2><a href=\"#x\">x</a><a href=\"https://example.org/\">e</a>"));
    assertThat(LinkCheck.check(site)).isEmpty();
  }

  @Test
  void eachBrokenLinkIsReported(@TempDir Path site) throws IOException {
    Files.writeString(
        site.resolve("a.html"),
        page(
            "<a href=\"missing.html\">m</a><a href=\"b.html#nowhere\">n</a>"
                + "<a href=\"notes.md\">md</a><img src=\"images/none.png\" alt=\"\" />"
                + "<a href=\"../outside.html\">o</a><p id=\"d\">1</p><p id=\"d\">2</p>"));
    Files.writeString(site.resolve("b.html"), page("<h2 id=\"x\">X</h2>"));
    Files.writeString(site.resolve("notes.md"), "# notes\n");
    Files.writeString(site.resolve("outside.html"), page(""));
    assertThat(LinkCheck.check(site))
        .containsExactlyInAnyOrder(
            "a.html: the id d is repeated",
            "a.html: missing.html is not a file of the site",
            "a.html: b.html#nowhere names no anchor of its page",
            "a.html: notes.md links to Markdown, not to a page",
            "a.html: images/none.png is not a file of the site",
            "a.html: ../outside.html is not a file of the site");
  }

  @Test
  void aPageThatIsNotWellFormedFails(@TempDir Path site) throws IOException {
    Files.writeString(site.resolve("a.html"), "<!DOCTYPE html>\n<html><body><p>open</body></html>");
    assertThatThrownBy(() -> LinkCheck.check(site)).isInstanceOf(IOException.class);
  }
}

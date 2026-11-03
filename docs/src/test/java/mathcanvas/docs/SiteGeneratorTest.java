package mathcanvas.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.docs.SiteGenerator.Anchor;
import mathcanvas.docs.SiteGenerator.Page;
import mathcanvas.docs.SiteGenerator.Site;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The site built from the repository's own docs, as the site task builds it. */
class SiteGeneratorTest {

  private static final Path REPOSITORY = Path.of(System.getProperty("mathcanvas.repository"));

  @TempDir static Path out;

  private static Site site;

  @BeforeAll
  static void build() throws IOException {
    site = SiteGenerator.generate(REPOSITORY, out.resolve("site"), "1.2.3-test", null);
  }

  @Test
  void everyInternalLinkAndAnchorResolves() throws IOException {
    assertThat(LinkCheck.check(site.out())).isEmpty();
  }

  @Test
  void everyPageParses() throws IOException {
    for (Page page : site.pages()) {
      assertThat(LinkCheck.parse(site.out().resolve(page.file()))).isNotNull();
    }
  }

  @Test
  void thePagesAreTheHomeTheGuideTheLanguageEachReferenceFileAndTheScreenshots()
      throws IOException {
    List<String> files = site.pages().stream().map(Page::file).toList();
    assertThat(files)
        .startsWith("index.html", "getting-started.html", "language.html", "engine.html")
        .endsWith("screenshots.html");
    try (var references = Files.list(REPOSITORY.resolve("docs/reference"))) {
      for (Path reference : references.toList()) {
        String name = reference.getFileName().toString();
        assertThat(files).contains(name.replaceFirst("\\.md$", ".html"));
      }
    }
    assertThat(site.out().resolve("site.css")).isRegularFile();
    assertThat(site.out().resolve("search.js")).isRegularFile();
  }

  @Test
  void everySearchEntryNamesAnAnchorOfItsPage() throws IOException {
    String index = Files.readString(site.out().resolve("search-index.js"));
    Matcher entries = Pattern.compile("\"h\":\"([^\"#]+)(?:#([^\"]*))?\"").matcher(index);
    int count = 0;
    while (entries.find()) {
      Path page = site.out().resolve(entries.group(1));
      assertThat(page).isRegularFile();
      if (entries.group(2) != null) {
        assertThat(LinkCheck.ids(LinkCheck.parse(page), id -> {})).contains(entries.group(2));
      }
      count++;
    }
    int headings = site.pages().stream().mapToInt(p -> p.headings().size()).sum();
    assertThat(count).isEqualTo(headings).isGreaterThan(100);
  }

  @Test
  void theSidebarListsEveryPageAndTheCurrentPagesSections() throws IOException {
    String language = Files.readString(site.out().resolve("language.html"));
    for (Page page : site.pages()) {
      assertThat(language).contains("<a href=\"" + page.file() + "\"");
    }
    Page languagePage = site.pages().get(2);
    for (Anchor h : languagePage.headings()) {
      if (h.level() == 2 || h.level() == 3) {
        assertThat(language).contains("<a href=\"#" + h.id() + "\">");
      }
    }
  }

  @Test
  void theHomePageHoldsTheReadmesSectionsWithTheirImages() throws IOException {
    String index = Files.readString(site.out().resolve("index.html"));
    assertThat(index)
        .contains("id=\"what-it-looks-like\"", "id=\"installing\"", "id=\"macos\"")
        .contains("id=\"windows\"", "id=\"linux\"", "src=\"images/worksheet-light.png\"")
        .contains("1.2.3-test")
        .doesNotContain("id=\"building-and-running\"");
  }

  /** Every input of §17, with every value of its row, is on the getting-started page. */
  @Test
  void gettingStartedHoldsEveryWorkedExample() throws IOException {
    String page = Files.readString(site.out().resolve("getting-started.html"));
    List<String> rows = new ArrayList<>();
    boolean inside = false;
    for (String line : Files.readAllLines(REPOSITORY.resolve("docs/language.md"))) {
      if (line.startsWith("## ")) {
        inside = line.contains("Worked examples");
      } else if (inside && line.startsWith("| `")) {
        rows.add(line);
      }
    }
    assertThat(rows).hasSizeGreaterThan(60);
    Pattern code = Pattern.compile("`((?:[^`\\\\]|\\\\.)+)`");
    for (String row : rows) {
      Matcher spans = code.matcher(row);
      while (spans.find()) {
        String literal = spans.group(1).replace("\\|", "|");
        assertThat(page).contains("<code>" + SiteGenerator.escape(literal) + "</code>");
      }
    }
    int pageRows = page.split("<tr>", -1).length - 1;
    assertThat(pageRows).isGreaterThanOrEqualTo(rows.size());
  }

  @Test
  void screenshotsJoinOrReplaceTheCommittedImages(@TempDir Path dir) throws IOException {
    Path shots = Files.createDirectories(dir.resolve("shots"));
    Files.writeString(shots.resolve("worksheet-light.png"), "fresh");
    Files.writeString(shots.resolve("canvas-grid-dark.png"), "new");
    Files.writeString(shots.resolve("canvas-benchmark.txt"), "not an image");
    Site withShots = SiteGenerator.generate(REPOSITORY, dir.resolve("site"), "1", shots);
    Path images = withShots.out().resolve("images");
    assertThat(images.resolve("worksheet-light.png")).hasContent("fresh");
    assertThat(images.resolve("worksheet-dark.png")).isRegularFile();
    assertThat(images.resolve("canvas-benchmark.txt")).doesNotExist();
    assertThat(Files.readString(withShots.out().resolve("screenshots.html")))
        .contains(
            "src=\"images/canvas-grid-dark.png\"", "<figcaption>Canvas grid dark</figcaption>");
  }

  @Test
  void aSecondRunReplacesTheSite() throws IOException {
    Path stale = Files.writeString(site.out().resolve("stale.html"), "<p>old</p>");
    SiteGenerator.generate(REPOSITORY, site.out(), "1.2.3-test", null);
    assertThat(stale).doesNotExist();
  }

  @Test
  void linksBecomePagesImagesOrFilesOnGitHub() {
    Path root = REPOSITORY.toAbsolutePath().normalize();
    String github = SiteGenerator.REPOSITORY;
    assertThat(SiteGenerator.rewrite(root, "docs/language.md#31-numbers", ""))
        .isEqualTo("language.html#31-numbers");
    assertThat(SiteGenerator.rewrite(root, "reference/cli.md", "docs")).isEqualTo("cli.html");
    assertThat(SiteGenerator.rewrite(root, "../README.md#installing", "docs"))
        .isEqualTo("index.html#installing");
    assertThat(SiteGenerator.rewrite(root, "docs/images/worksheet-dark.png", ""))
        .isEqualTo("images/worksheet-dark.png");
    assertThat(SiteGenerator.rewrite(root, "CONTRIBUTING.md", ""))
        .isEqualTo(github + "/blob/main/CONTRIBUTING.md");
    assertThat(SiteGenerator.rewrite(root, "docs/reference/", ""))
        .isEqualTo(github + "/tree/main/docs/reference");
    assertThat(SiteGenerator.rewrite(root, "#local", "docs")).isEqualTo("#local");
    assertThat(SiteGenerator.rewrite(root, "https://example.org/a.md", ""))
        .isEqualTo("https://example.org/a.md");
    assertThat(SiteGenerator.rewrite(root, "../../outside.md", "docs"))
        .isEqualTo("../../outside.md");
  }
}

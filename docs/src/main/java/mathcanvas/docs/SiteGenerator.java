package mathcanvas.docs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Builds the documentation site from the repository's Markdown into one directory of static pages:
 * {@code index.html} (the README's lead paragraph, its "What it looks like" and its "Installing"
 * sections), {@code getting-started.html} (an introduction and every worked example of {@code
 * docs/language.md} §17), {@code language.html}, one page per {@code docs/reference/} file, and
 * {@code screenshots.html}; with a shared stylesheet, a sidebar of the pages and of the current
 * page's headings, and a search box over every page's headings ({@code search.js} over the
 * generated {@code search-index.js}). The Markdown is CommonMark with GitHub tables and
 * GitHub-style heading anchors; raw HTML in it is shown as text, so every page is well-formed
 * XHTML. A relative link to a converted file becomes a link to its page, one into {@code
 * docs/images/} a link to the copied image, and any other relative link a link to the file on
 * GitHub. The images are the README's committed ones, overlaid by the UI tests' screenshots when a
 * directory of them is given.
 *
 * <pre>
 * SiteGenerator --root &lt;repository&gt; --out &lt;directory&gt; --version &lt;version&gt;
 *               [--screenshots &lt;directory&gt;]
 * </pre>
 *
 * The output directory is replaced. Nothing here reads a clock, so the same sources give the same
 * site.
 */
public final class SiteGenerator {

  /** The repository on GitHub, where links to files that are not pages of the site go. */
  static final String REPOSITORY = "https://github.com/tarnan3751/math-canvas";

  private static final List<Extension> EXTENSIONS =
      List.of(TablesExtension.create(), HeadingAnchorExtension.create());

  /** The reference files in the order of the architecture; any other follows by name. */
  private static final List<String> REFERENCE_ORDER =
      List.of("engine", "render", "canvas", "app", "cli", "testing-tools");

  private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

  /** A heading of a page: its level, its text and its anchor (its id). */
  public record Anchor(int level, String text, String id) {}

  /** A page written: its file name, its title, its name in the sidebar and its headings. */
  public record Page(String file, String title, String navTitle, List<Anchor> headings) {}

  /** The site written: its directory and its pages in sidebar order. */
  public record Site(Path out, List<Page> pages) {}

  /** A page before it is written: its body, already rendered. */
  private record Body(String file, String navTitle, String html, List<Anchor> headings) {}

  private final Path root;
  private final Path out;
  private final String version;
  private final Path screenshots;
  private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

  private SiteGenerator(Path root, Path out, String version, Path screenshots) {
    this.root = root.toAbsolutePath().normalize();
    this.out = out.toAbsolutePath().normalize();
    this.version = version;
    this.screenshots = screenshots;
  }

  public static void main(String[] args) throws IOException {
    Path root = null;
    Path out = null;
    Path screenshots = null;
    String version = null;
    for (int i = 0; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--root" -> root = Path.of(args[i + 1]);
        case "--out" -> out = Path.of(args[i + 1]);
        case "--version" -> version = args[i + 1];
        case "--screenshots" -> screenshots = Path.of(args[i + 1]);
        default -> throw new IllegalArgumentException("unknown option " + args[i]);
      }
    }
    if (root == null || out == null || version == null || args.length % 2 != 0) {
      throw new IllegalArgumentException(
          "usage: SiteGenerator --root <repository> --out <directory> --version <version>"
              + " [--screenshots <directory>]");
    }
    Site site = generate(root, out, version, screenshots);
    System.out.println("site: " + site.pages().size() + " pages in " + site.out());
  }

  /**
   * Writes the site for the repository at {@code root} into {@code out}, replacing it; {@code
   * screenshots}, when not null and a directory, holds PNG files that join or replace the committed
   * images.
   */
  public static Site generate(Path root, Path out, String version, Path screenshots)
      throws IOException {
    return new SiteGenerator(root, out, version, screenshots).write();
  }

  private Site write() throws IOException {
    if (root.startsWith(out)) {
      throw new IllegalArgumentException("the output directory " + out + " holds the sources");
    }
    deleteRecursively(out);
    Files.createDirectories(out.resolve("images"));
    List<String> images = copyImages();

    List<Body> bodies = new ArrayList<>();
    bodies.add(index());
    bodies.add(gettingStarted());
    bodies.add(render("language.html", "The language", read("docs/language.md"), "docs"));
    for (Path reference : referenceFiles()) {
      String name = reference.getFileName().toString();
      String file = name.substring(0, name.length() - ".md".length()) + ".html";
      Document document = parse(Files.readString(reference));
      bodies.add(render(file, firstHeading(document), document, "docs/reference"));
    }
    bodies.add(screenshotsPage(images));

    List<Page> pages = new ArrayList<>();
    for (Body body : bodies) {
      String title =
          body.headings().stream()
              .filter(h -> h.level() == 1)
              .map(Anchor::text)
              .findFirst()
              .orElse(body.navTitle());
      pages.add(new Page(body.file(), title, body.navTitle(), body.headings()));
    }
    for (int i = 0; i < bodies.size(); i++) {
      Files.writeString(out.resolve(pages.get(i).file()), page(pages, i, bodies.get(i).html()));
    }
    Files.writeString(out.resolve("search-index.js"), searchIndex(pages));
    copyResource("site.css");
    copyResource("search.js");
    return new Site(out, List.copyOf(pages));
  }

  // Pages ----------------------------------------------------------------------------------

  /** The home page: the README's title and lead, the pages, then its two user sections. */
  private Body index() throws IOException {
    Document readme = read("README.md");
    Document lead = new Document();
    Document sections = new Document();
    Node first = readme.getFirstChild();
    if (first instanceof Heading h && h.getLevel() == 1) {
      lead.appendChild(first);
      if (readme.getFirstChild() instanceof Paragraph p) {
        lead.appendChild(p);
      }
    }
    for (String title : List.of("What it looks like", "Installing")) {
      List<Node> section = section(readme, title, 2);
      if (section.isEmpty()) {
        throw new IllegalStateException("README.md has no section \"" + title + "\"");
      }
      section.forEach(sections::appendChild);
    }
    Body top = render("index.html", "Home", lead, "");
    Body rest = render("index.html", "Home", sections, "");
    String links =
        """
        <ul class="start">
        <li><a href="getting-started.html">Getting started</a>: every worked example, with what it gives</li>
        <li><a href="language.html">The language</a>: the grammar, every function and command</li>
        <li><a href="engine.html">The reference</a>: how each part of math-canvas works</li>
        <li><a href="screenshots.html">Screenshots</a></li>
        </ul>
        """;
    List<Anchor> headings = new ArrayList<>(top.headings());
    headings.addAll(rest.headings());
    return new Body("index.html", "Home", top.html() + links + rest.html(), headings);
  }

  /** The introduction, then every subsection of the language reference's worked examples. */
  private Body gettingStarted() throws IOException {
    Document document = parse(resource("getting-started.md"));
    Document language = read("docs/language.md");
    List<Node> examples = section(language, "Worked examples", 2);
    if (examples.isEmpty()) {
      throw new IllegalStateException("docs/language.md has no section \"Worked examples\"");
    }
    examples.stream().skip(1).forEach(document::appendChild);
    return render("getting-started.html", "Getting started", document, "docs");
  }

  private Body screenshotsPage(List<String> images) {
    StringBuilder html = new StringBuilder();
    html.append("<h1 id=\"screenshots\">Screenshots</h1>\n");
    html.append("<div class=\"gallery\">\n");
    for (String image : images) {
      String caption = caption(image);
      html.append("<figure><a href=\"images/")
          .append(escape(image))
          .append("\"><img src=\"images/")
          .append(escape(image))
          .append("\" alt=\"")
          .append(escape(caption))
          .append("\" loading=\"lazy\" /></a><figcaption>")
          .append(escape(caption))
          .append("</figcaption></figure>\n");
    }
    html.append("</div>\n");
    return new Body(
        "screenshots.html",
        "Screenshots",
        html.toString(),
        List.of(new Anchor(1, "Screenshots", "screenshots")));
  }

  /** "worksheet-light.png" reads "Worksheet light". */
  private static String caption(String image) {
    String name = image.substring(0, image.lastIndexOf('.')).replace('-', ' ').replace('_', ' ');
    return name.isEmpty() ? image : Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  /**
   * Removes from {@code document} and returns the heading of the given level whose text contains
   * {@code title}, with every node after it up to the next heading of that level or higher.
   */
  private static List<Node> section(Document document, String title, int level) {
    List<Node> nodes = new ArrayList<>();
    Node node = document.getFirstChild();
    while (node != null
        && !(node instanceof Heading h && h.getLevel() == level && text(h).contains(title))) {
      node = node.getNext();
    }
    while (node != null) {
      if (!nodes.isEmpty() && node instanceof Heading h && h.getLevel() <= level) {
        break;
      }
      nodes.add(node);
      node = node.getNext();
    }
    nodes.forEach(Node::unlink);
    return nodes;
  }

  private List<Path> referenceFiles() throws IOException {
    try (Stream<Path> files = Files.list(root.resolve("docs/reference"))) {
      return files
          .filter(p -> p.getFileName().toString().endsWith(".md"))
          .sorted(
              Comparator.comparingInt(
                      (Path p) -> {
                        String name = p.getFileName().toString().replaceFirst("\\.md$", "");
                        int i = REFERENCE_ORDER.indexOf(name);
                        return i < 0 ? REFERENCE_ORDER.size() : i;
                      })
                  .thenComparing(p -> p.getFileName().toString()))
          .toList();
    }
  }

  // Rendering ------------------------------------------------------------------------------

  /**
   * Renders a document whose relative links are written from {@code sourceDir} (a path from the
   * repository root, "" for the root), collecting its headings with the anchors the heading
   * extension gives them.
   */
  private Body render(String file, String navTitle, Document document, String sourceDir) {
    List<Anchor> headings = new ArrayList<>();
    AttributeProvider provider =
        (Node node, String tag, Map<String, String> attributes) -> {
          switch (node) {
            case Heading h when attributes.containsKey("id") ->
                headings.add(new Anchor(h.getLevel(), text(h), attributes.get("id")));
            case Link link ->
                attributes.put("href", rewrite(root, link.getDestination(), sourceDir));
            case Image image ->
                attributes.put("src", rewrite(root, image.getDestination(), sourceDir));
            default -> {}
          }
        };
    HtmlRenderer renderer =
        HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .attributeProviderFactory(context -> provider)
            .build();
    String html = renderer.render(document);
    return new Body(file, navTitle, html, List.copyOf(headings));
  }

  /**
   * Where a link written in a file under {@code sourceDir} (a path from the repository root at
   * {@code root}) points on the site.
   */
  static String rewrite(Path root, String destination, String sourceDir) {
    if (destination.isEmpty()
        || destination.startsWith("#")
        || destination.startsWith("//")
        || SCHEME.matcher(destination).find()) {
      return destination;
    }
    int hash = destination.indexOf('#');
    String target = hash < 0 ? destination : destination.substring(0, hash);
    String fragment = hash < 0 ? "" : destination.substring(hash);
    Path resolved = root.resolve(sourceDir).resolve(target).normalize();
    if (!resolved.startsWith(root)) {
      return destination;
    }
    String path = root.relativize(resolved).toString().replace('\\', '/');
    if (path.equals("README.md")) {
      return "index.html" + fragment;
    }
    if (path.equals("docs/language.md")) {
      return "language.html" + fragment;
    }
    if (path.matches("docs/reference/[^/]+\\.md")) {
      return path.substring("docs/reference/".length()).replaceFirst("\\.md$", ".html") + fragment;
    }
    if (path.matches("docs/images/[^/]+")) {
      return path.substring("docs/".length());
    }
    if (path.isEmpty()) {
      return REPOSITORY;
    }
    String kind = Files.isDirectory(resolved) ? "/tree/main/" : "/blob/main/";
    return REPOSITORY + kind + path + fragment;
  }

  /** A page around its rendered body: the header with the search box, the sidebar, the footer. */
  private String page(List<Page> pages, int current, String body) {
    Page page = pages.get(current);
    StringBuilder html = new StringBuilder();
    html.append(
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        """);
    html.append("<title>")
        .append(escape(current == 0 ? "math-canvas" : page.title() + " — math-canvas"))
        .append("</title>\n");
    html.append(
        """
        <link rel="stylesheet" href="site.css" />
        <script src="search-index.js" defer="defer"></script>
        <script src="search.js" defer="defer"></script>
        </head>
        <body>
        <header class="site-header">
        <a class="brand" href="index.html">math-canvas</a>
        """);
    html.append("<span class=\"version\">").append(escape(version)).append("</span>\n");
    html.append(
        """
        <div class="search">
        <input type="search" id="search" placeholder="Search the headings" \
        aria-label="Search the headings" autocomplete="off" />
        <ul id="search-results" hidden="hidden"></ul>
        </div>
        </header>
        <div class="layout">
        <nav class="sidebar" aria-label="Pages and contents">
        <ul class="pages">
        """);
    for (int i = 0; i < pages.size(); i++) {
      Page p = pages.get(i);
      html.append(i == current ? "<li class=\"current\">" : "<li>")
          .append("<a href=\"")
          .append(p.file())
          .append("\"")
          .append(i == current ? " aria-current=\"page\"" : "")
          .append(">")
          .append(escape(p.navTitle()))
          .append("</a>");
      if (i == current) {
        html.append(contents(p.headings()));
      }
      html.append("</li>\n");
    }
    html.append("</ul>\n</nav>\n<main class=\"content\">\n");
    html.append(body);
    html.append("</main>\n</div>\n<footer class=\"site-footer\">\nmath-canvas ")
        .append(escape(version))
        .append(" · Apache License 2.0 · <a href=\"")
        .append(REPOSITORY)
        .append("\">The source on GitHub</a>\n</footer>\n</body>\n</html>\n");
    return html.toString();
  }

  /** The current page's level-2 headings, each with its level-3 headings nested below it. */
  private static String contents(List<Anchor> headings) {
    StringBuilder html = new StringBuilder();
    boolean open2 = false;
    boolean open3 = false;
    for (Anchor h : headings) {
      if (h.level() == 2) {
        if (open3) {
          html.append("</ul>");
          open3 = false;
        }
        if (open2) {
          html.append("</li>\n");
        } else {
          html.append("\n<ul class=\"toc\">\n");
        }
        html.append("<li>").append(anchor(h));
        open2 = true;
      } else if (h.level() == 3 && open2) {
        if (!open3) {
          html.append("<ul>");
          open3 = true;
        }
        html.append("<li>").append(anchor(h)).append("</li>");
      }
    }
    if (open3) {
      html.append("</ul>");
    }
    if (open2) {
      html.append("</li>\n</ul>\n");
    }
    return html.toString();
  }

  private static String anchor(Anchor h) {
    return "<a href=\"#" + escape(h.id()) + "\">" + escape(h.text()) + "</a>";
  }

  /** {@code window.SEARCH_INDEX}: every heading of every page, with its page and anchor. */
  private static String searchIndex(List<Page> pages) {
    StringBuilder js = new StringBuilder("window.SEARCH_INDEX = [\n");
    for (Page page : pages) {
      for (Anchor h : page.headings()) {
        String href = h.level() == 1 ? page.file() : page.file() + "#" + h.id();
        js.append("{\"t\":")
            .append(json(h.text()))
            .append(",\"p\":")
            .append(json(page.navTitle()))
            .append(",\"h\":")
            .append(json(href))
            .append("},\n");
      }
    }
    return js.append("];\n").toString();
  }

  // Files ----------------------------------------------------------------------------------

  /** Copies the committed images, then the screenshots over them; returns the names, sorted. */
  private List<String> copyImages() throws IOException {
    List<Path> sources = new ArrayList<>(pngs(root.resolve("docs/images")));
    if (screenshots != null) {
      sources.addAll(pngs(screenshots));
    }
    for (Path source : sources) {
      Files.copy(
          source,
          out.resolve("images").resolve(source.getFileName().toString()),
          StandardCopyOption.REPLACE_EXISTING);
    }
    return sources.stream().map(p -> p.getFileName().toString()).distinct().sorted().toList();
  }

  private static List<Path> pngs(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
    }
  }

  private Document read(String path) throws IOException {
    return parse(Files.readString(root.resolve(path)));
  }

  private Document parse(String markdown) {
    return (Document) parser.parse(markdown);
  }

  private static String resource(String name) {
    try (InputStream in = SiteGenerator.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("missing resource " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void copyResource(String name) throws IOException {
    Files.writeString(out.resolve(name), resource(name));
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    }
  }

  // Text -----------------------------------------------------------------------------------

  private static String firstHeading(Document document) {
    for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof Heading h && h.getLevel() == 1) {
        return text(h);
      }
    }
    throw new IllegalStateException("a reference file has no title");
  }

  /** The text of a node: its text and code spans, in order. */
  static String text(Node node) {
    StringBuilder text = new StringBuilder();
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      switch (child) {
        case Text t -> text.append(t.getLiteral());
        case Code c -> text.append(c.getLiteral());
        default -> text.append(text(child));
      }
    }
    return text.toString();
  }

  static String escape(String s) {
    StringBuilder out = new StringBuilder(s.length());
    for (char c : s.toCharArray()) {
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private static String json(String s) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        default -> {
          if (c < 0x20) {
            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}

package mathcanvas.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Checks a built site the way a browser would meet it, without one: every page parses (the
 * generator writes well-formed XHTML), no page repeats an id, and every internal link and anchor
 * resolves ({@code a href}, {@code img src}, {@code link href}, {@code script src}): the file
 * exists inside the site, and a fragment names an id of its page. Links with a scheme are external
 * and not followed.
 */
final class LinkCheck {

  private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
  private static final Map<String, String> REFERENCES =
      Map.of("a", "href", "img", "src", "link", "href", "script", "src");

  private final Path site;
  private final Map<Path, Set<String>> ids = new HashMap<>();
  private final List<String> problems = new ArrayList<>();

  private LinkCheck(Path site) {
    this.site = site.toAbsolutePath().normalize();
  }

  /** The problems found in the site at {@code site}, one line each; empty when there are none. */
  static List<String> check(Path site) throws IOException {
    return new LinkCheck(site).run();
  }

  /** Parses one page, or throws when it is not well-formed. */
  static Document parse(Path page) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(page.toFile());
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException(page + ": " + e.getMessage(), e);
    }
  }

  private List<String> run() throws IOException {
    List<Path> pages;
    try (Stream<Path> files = Files.walk(site)) {
      pages = files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
    }
    for (Path page : pages) {
      ids(page);
      Document document = parse(page);
      for (Map.Entry<String, String> reference : REFERENCES.entrySet()) {
        NodeList elements = document.getElementsByTagName(reference.getKey());
        for (int i = 0; i < elements.getLength(); i++) {
          Element element = (Element) elements.item(i);
          if (element.hasAttribute(reference.getValue())) {
            follow(page, element.getAttribute(reference.getValue()));
          }
        }
      }
    }
    return problems;
  }

  private void follow(Path page, String target) throws IOException {
    if (target.startsWith("//") || SCHEME.matcher(target).find()) {
      return;
    }
    String name = site.relativize(page).toString().replace('\\', '/');
    int hash = target.indexOf('#');
    String path = hash < 0 ? target : target.substring(0, hash);
    String fragment = hash < 0 ? "" : target.substring(hash + 1);
    Path file = path.isEmpty() ? page : page.getParent().resolve(path).normalize();
    if (!file.startsWith(site) || !Files.isRegularFile(file)) {
      problems.add(name + ": " + target + " is not a file of the site");
      return;
    }
    if (path.endsWith(".md")) {
      problems.add(name + ": " + target + " links to Markdown, not to a page");
    }
    if (!fragment.isEmpty()) {
      if (!file.toString().endsWith(".html")) {
        problems.add(name + ": " + target + " has a fragment but is not a page");
      } else if (!ids(file).contains(fragment)) {
        problems.add(name + ": " + target + " names no anchor of its page");
      }
    }
  }

  /** The ids of a page; a repeated id is a problem of its own. */
  private Set<String> ids(Path page) throws IOException {
    Set<String> known = ids.get(page);
    if (known == null) {
      known =
          ids(
              parse(page),
              id -> problems.add(site.relativize(page) + ": the id " + id + " is repeated"));
      ids.put(page, known);
    }
    return known;
  }

  /** Every id of a page, each repeated one also given to {@code repeated}. */
  static Set<String> ids(Document document, Consumer<String> repeated) {
    Set<String> found = new HashSet<>();
    NodeList elements = document.getElementsByTagName("*");
    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      if (element.hasAttribute("id") && !found.add(element.getAttribute("id"))) {
        repeated.accept(element.getAttribute("id"));
      }
    }
    return found;
  }
}

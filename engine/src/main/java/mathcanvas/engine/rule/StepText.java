package mathcanvas.engine.rule;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.print.UnicodePrinter;

/**
 * The texts of narration steps, from a resource bundle: a key's value is a {@link MessageFormat}
 * pattern whose arguments {@code {0}}, {@code {1}}, … are values printed, and whose named
 * placeholders {@code {before}}, {@code {after}} and {@code {list}} are the step's before and
 * after, printed, and a list already written out. A rewrite's key is {@code <rule set>.<rule>}, its
 * arguments the rule's bound variables in alphabetical order of their names (a sequence variable as
 * its combined value); a rewrite with no key is narrated by its rule's name.
 *
 * @param bundle the narration texts
 * @param printer how values are printed
 */
public record StepText(ResourceBundle bundle, Function<Expr, String> printer) {

  /**
   * The engine's bundle, {@link DefaultNarrator#BUNDLE} in the root locale, printing with the
   * Unicode printer's display.
   *
   * @return the texts
   */
  public static StepText standard() {
    return new StepText(
        ResourceBundle.getBundle(DefaultNarrator.BUNDLE, Locale.ROOT),
        UnicodePrinter.INSTANCE::display);
  }

  /**
   * Whether the bundle has a key.
   *
   * @param key the key
   * @return true if it has
   */
  public boolean has(String key) {
    return bundle.containsKey(key);
  }

  /**
   * The text of one rewrite.
   *
   * @param r the rewrite
   * @return its text, or its rule's name when the bundle has no key for it
   */
  public String of(Rewrite r) {
    return of(r, r.before(), r.after());
  }

  /**
   * The text of one rewrite, with another before and after: a step that folds later rewrites into
   * this one shows where they led.
   *
   * @param r the rewrite
   * @param before what {@code {before}} prints
   * @param after what {@code {after}} prints
   * @return its text, or its rule's name when the bundle has no key for it
   */
  public String of(Rewrite r, Expr before, Expr after) {
    String key = r.ruleSet() + "." + r.rule();
    if (!has(key)) {
      return r.rule();
    }
    Bindings b = r.bindings();
    List<Expr> args = new ArrayList<>();
    for (String n : b.names()) {
      args.add(b.get(n));
    }
    return format(key, args, before, after, "");
  }

  /**
   * The text of a key.
   *
   * @param key the key, which the bundle must have
   * @param args the values of {@code {0}}, {@code {1}}, …
   * @param before the value of {@code {before}}
   * @param after the value of {@code {after}}
   * @param list the text of {@code {list}}
   * @return the text
   */
  public String format(String key, List<Expr> args, Expr before, Expr after, String list) {
    List<Object> printed = new ArrayList<>();
    for (Expr a : args) {
      printed.add(printer.apply(a));
    }
    String pattern = bundle.getString(key);
    if (pattern.contains("{before}")) {
      pattern = pattern.replace("{before}", "{" + printed.size() + "}");
      printed.add(printer.apply(before));
    }
    if (pattern.contains("{after}")) {
      pattern = pattern.replace("{after}", "{" + printed.size() + "}");
      printed.add(printer.apply(after));
    }
    if (pattern.contains("{list}")) {
      pattern = pattern.replace("{list}", "{" + printed.size() + "}");
      printed.add(list);
    }
    return new MessageFormat(pattern, Locale.ROOT).format(printed.toArray());
  }
}

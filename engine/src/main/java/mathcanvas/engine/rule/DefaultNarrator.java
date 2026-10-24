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
 * One step per rewrite, its text from a resource bundle: the key is {@code <rule set>.<rule>}, and
 * the value a {@link MessageFormat} pattern whose arguments {@code {0}}, {@code {1}}, … are the
 * rule's bound variables in alphabetical order of their names, printed (a sequence variable as its
 * combined value). The placeholders {@code {before}} and {@code {after}} are the rewrite's before
 * and after, printed. A rule with no key is narrated by its name. A step's before and after are the
 * rewritten subexpression's.
 *
 * @param bundle the narration texts
 * @param printer how bound values are printed
 */
public record DefaultNarrator(ResourceBundle bundle, Function<Expr, String> printer)
    implements Narrator {

  /** The engine's narration bundle. */
  public static final String BUNDLE = "mathcanvas.engine.rule.narration";

  /** The engine's bundle, in the root locale, printing values with the Unicode printer. */
  public DefaultNarrator() {
    this(ResourceBundle.getBundle(BUNDLE, Locale.ROOT), UnicodePrinter.INSTANCE::display);
  }

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Step> steps = new ArrayList<>(trace.rewrites().size());
    for (Rewrite r : trace.rewrites()) {
      steps.add(new Step(text(r), r.before(), r.after(), List.of(r)));
    }
    return List.copyOf(steps);
  }

  private String text(Rewrite r) {
    String key = r.ruleSet() + "." + r.rule();
    if (!bundle.containsKey(key)) {
      return r.rule();
    }
    Bindings b = r.bindings();
    List<Object> args = new ArrayList<>();
    for (String n : b.names()) {
      args.add(printer.apply(b.get(n)));
    }
    String pattern = bundle.getString(key);
    if (pattern.contains("{before}") || pattern.contains("{after}")) {
      pattern =
          pattern
              .replace("{before}", "{" + args.size() + "}")
              .replace("{after}", "{" + (args.size() + 1) + "}");
      args.add(printer.apply(r.before()));
      args.add(printer.apply(r.after()));
    }
    return new MessageFormat(pattern, Locale.ROOT).format(args.toArray());
  }
}

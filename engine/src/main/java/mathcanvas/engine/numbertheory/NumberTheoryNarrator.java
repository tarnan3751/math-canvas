package mathcanvas.engine.numbertheory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Narrator;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.rule.Trace;

/**
 * The narrator of the number-theory steps ({@link #RULE_SETS}): one step per rewrite, each with its
 * own text {@code <rule set>.<rule>}. {@code ifactor.factors} is a run of sentences, one per method
 * that found factors ({@code ifactor.step.trial}, {@code .power}, {@code .rho}, {@code .ecm}; trial
 * division as {@code .trial-prime} when the cofactor it leaves is prime), then the factorisation,
 * then a note on primes only Baillie–PSW vouches for ({@code ifactor.step.probable}); {@code
 * ifactor.unfinished} says first which primes were found ({@code ifactor.step.found}). A list of
 * numbers reads as a list, not as a vector.
 *
 * @param texts the texts
 */
public record NumberTheoryNarrator(StepText texts) implements Narrator {

  /** The rule sets this narrator owns. */
  public static final Set<String> RULE_SETS = Set.of(NumberTheory.IFACTOR, Modular.RULE_SET);

  /** The keys of the texts this narrator adds to its rules'. */
  public static final Set<String> KEYS =
      Set.of(
          "ifactor.step.trial",
          "ifactor.step.trial-prime",
          "ifactor.step.power",
          "ifactor.step.rho",
          "ifactor.step.ecm",
          "ifactor.step.probable",
          "ifactor.step.found",
          "nt.powmod-binary");

  @Override
  public List<Step> narrate(Trace trace, Context ctx) {
    List<Step> steps = new ArrayList<>();
    for (Rewrite r : trace.rewrites()) {
      steps.add(new Step(text(r), r.before(), r.after(), List.of(r)));
    }
    return List.copyOf(steps);
  }

  private String text(Rewrite r) {
    String key = r.ruleSet() + "." + r.rule();
    SortedMap<String, Expr> values = r.values();
    List<String> sentences = new ArrayList<>();
    if (key.equals("ifactor.factors")) {
      for (String method : List.of("trial", "power", "rho", "ecm")) {
        if (method.equals("trial") && values.get("cofactor") instanceof Num cofactor) {
          sentences.add(
              texts.text(
                  "ifactor.step.trial-prime",
                  items(values.get(method)),
                  texts.printer().apply(cofactor)));
        } else if (values.containsKey(method)) {
          sentences.add(texts.text("ifactor.step." + method, items(values.get(method))));
        }
      }
    }
    if (values.containsKey("binary")) {
      sentences.add(texts.format("nt.powmod-binary", List.of(), r.before(), r.after(), "", values));
    }
    if (key.equals("ifactor.unfinished")
        && values.get("found") instanceof Vector v
        && !v.items().isEmpty()) {
      sentences.add(texts.text("ifactor.step.found", items(v)));
    }
    String main =
        texts.format(key, List.of(), r.before(), r.after(), items(values.get("left")), values);
    sentences.add(main);
    if (values.containsKey("probable")) {
      sentences.add(texts.text("ifactor.step.probable", items(values.get("probable"))));
    }
    return texts.join("step.sentences", sentences);
  }

  /** The items of a vector as a list, {@code 2, 3 and 5} written {@code 2, 3, 5}. */
  private String items(Expr list) {
    if (!(list instanceof Vector v) || v.items().isEmpty()) {
      return "";
    }
    return texts.join("step.list", v.items().stream().map(texts.printer()).toList());
  }
}

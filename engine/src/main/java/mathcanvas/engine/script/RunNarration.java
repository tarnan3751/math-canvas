package mathcanvas.engine.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;

/**
 * The narration of a traced run ({@code docs/language.md} §19.6): a step for each statement's
 * effect, its text naming the line and the statement and its after the value; a loop's first two
 * iterations and its last, each led by a step naming it, the iterations between them folded into
 * one step; each statement written as typed. At most {@link #MAX_STEPS} steps, the rest told as one
 * that counts them all; a run whose events reached their cap says where it stops.
 */
public final class RunNarration {

  /** The most steps a run's narration shows. */
  public static final int MAX_STEPS = 500;

  /** The bundle keys of the narration. */
  public static final Set<String> KEYS =
      Set.of(
          "program.assign",
          "program.evaluate",
          "program.print",
          "program.return",
          "program.condition",
          "program.iteration",
          "program.iteration-while",
          "program.folded",
          "program.folded-while",
          "program.more",
          "program.stopped");

  private RunNarration() {}

  /**
   * The steps of a run's events, each statement written as typed.
   *
   * @param events what the run recorded
   * @param texts the texts and their printer, which writes the values
   * @param typed how a statement's expression is written: as typed, the raw tree printed
   * @return the steps; none when nothing was recorded
   */
  public static List<Step> steps(
      List<RunEvent> events, StepText texts, Function<Expr, String> typed) {
    List<Step> out = new ArrayList<>();
    walk(events, texts, typed, out);
    if (out.size() > MAX_STEPS) {
      int more = out.size() - (MAX_STEPS - 1);
      Expr after = out.get(MAX_STEPS - 2).after();
      out = new ArrayList<>(out.subList(0, MAX_STEPS - 1));
      out.add(step(texts.text("program.more", String.valueOf(more)), after, after));
    }
    return List.copyOf(out);
  }

  private static void walk(
      List<RunEvent> events, StepText texts, Function<Expr, String> typed, List<Step> out) {
    for (RunEvent e : events) {
      switch (e) {
        case RunEvent.Done d -> {
          String line = String.valueOf(d.line());
          String expr = typed.apply(d.expr());
          String text =
              switch (d.kind()) {
                case ASSIGN ->
                    texts.text("program.assign", line, texts.printer().apply(d.name()), expr);
                case EVALUATE -> texts.text("program.evaluate", line, expr);
                case PRINT -> texts.text("program.print", line, expr);
                case RETURN -> texts.text("program.return", line, expr);
                case CONDITION -> texts.text("program.condition", line, expr);
              };
          out.add(step(text, d.expr(), d.value()));
        }
        case RunEvent.Loop l -> loop(l, texts, typed, out);
        case RunEvent.Stopped st -> {
          Expr after = out.isEmpty() ? Bool.TRUE : out.getLast().after();
          out.add(step(texts.text("program.stopped", String.valueOf(st.count())), after, after));
        }
      }
    }
  }

  private static void loop(
      RunEvent.Loop l, StepText texts, Function<Expr, String> typed, List<Step> out) {
    String line = String.valueOf(l.line());
    String count = String.valueOf(l.count());
    for (RunEvent.Turn t : l.turns()) {
      if (t.index() == l.count() && l.count() > 3) {
        String from = "3";
        String to = String.valueOf(l.count() - 1);
        out.add(
            l.variable() == null
                ? step(
                    texts.text("program.folded-while", line, from, to, count), Bool.TRUE, Bool.TRUE)
                : step(
                    texts.text(
                        "program.folded",
                        line,
                        from,
                        to,
                        count,
                        texts.printer().apply(l.variable())),
                    l.variable(),
                    l.folded()));
      }
      String index = String.valueOf(t.index());
      out.add(
          l.variable() == null
              ? step(
                  texts.text("program.iteration-while", line, index, count), Bool.TRUE, Bool.TRUE)
              : step(
                  texts.text(
                      "program.iteration", line, index, count, texts.printer().apply(l.variable())),
                  l.variable(),
                  t.value()));
      walk(t.events(), texts, typed, out);
    }
  }

  private static Step step(String text, Expr before, Expr after) {
    return new Step(text, before, after, List.of());
  }
}

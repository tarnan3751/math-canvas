package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.LayoutPrinter;
import org.junit.jupiter.api.Test;

class EvaluationJobTest {

  private record Reported(long id, Output output) {}

  private static List<Reported> run(Cancellation c, String... texts) {
    List<EvaluationJob.Input> inputs = new ArrayList<>();
    for (int k = 0; k < texts.length; k++) {
      inputs.add(new EvaluationJob.Input(k + 1, texts[k]));
    }
    List<Reported> out = new ArrayList<>();
    EvaluationJob.run(
        inputs,
        Environment.EMPTY,
        c,
        false,
        Format.EXACT,
        new Inspections(),
        (id, o) -> out.add(new Reported(id, o)));
    return out;
  }

  private static List<String> texts(Output o) {
    return ((Output.Done) o)
        .results().stream()
            .map(
                r ->
                    switch (r) {
                      case Result.Value v -> v.text();
                      case Result.Defined d -> d.text();
                      case Result.Printed p -> "print " + p.text();
                      case Result.Plotted p -> "plot " + p.text();
                      case Result.Function fn -> fn.text();
                      case Result.Ran _ -> "(ran)";
                      case Result.Failed f -> f.message() + " @" + f.at().column() + " " + f.line();
                    })
            .toList();
  }

  @Test
  void aProgramCellShowsItsOutputItsFunctionsAndItsValue() {
    List<Reported> r =
        run(
            Cancellation.NEVER,
            "function fact(n)\n  r := 1\n  for k in 1..n\n    r := r k\n  end\n  return r\nend",
            "for k in 1..3\n  print(fact(k))\nend\nfact(5)",
            "for k in 1..2 do print(k) end",
            "for k in 1..3 do print(k); [1] + [1, 2] end");

    assertThat(texts(r.get(0).output())).containsExactly("function fact(n)");
    assertThat(texts(r.get(1).output()))
        .containsExactly("print 1", "print 2", "print 6", "(ran)", "120");
    assertThat(texts(r.get(2).output())).containsExactly("print 1", "print 2", "(ran)");
    // what a failing statement printed comes before its error, which points into its line
    assertThat(texts(r.get(3).output()))
        .containsExactly(
            "print 1",
            "cannot add vectors of lengths 1 and 2 @28 for k in 1..3 do print(k); [1] + [1, 2] end");
    Result.Function f = (Result.Function) ((Output.Done) r.get(0).output()).results().getFirst();
    assertThat(LayoutPrinter.flatten(f.layout())).isEqualTo("function fact(n)");
    assertThat(((Output.Done) r.get(0).output()).after().isFunction("fact")).isTrue();
  }

  @Test
  void aTracedProgramNarratesItsRun() {
    List<Reported> out = new ArrayList<>();
    EvaluationJob.run(
        List.of(new EvaluationJob.Input(1, "for k in 1..2 do k end\nif true then 7 end")),
        Environment.EMPTY,
        Cancellation.NEVER,
        true,
        Format.EXACT,
        new Inspections(),
        (id, o) -> out.add(new Reported(id, o)));
    List<Result> results = ((Output.Done) out.getFirst().output()).results();
    assertThat(((Result.Ran) results.get(0)).steps())
        .extracting(Result.Step::text)
        .containsExactly(
            "Line 1: pass 1 of 2 through the loop, with k",
            "Line 1: k",
            "Line 1: pass 2 of 2 through the loop, with k",
            "Line 1: k");
    Result.Value seven = (Result.Value) results.get(1);
    assertThat(seven.traced()).isTrue();
    assertThat(seven.steps())
        .extracting(Result.Step::text)
        .containsExactly("Line 2: the condition true", "Line 2: 7");
  }

  @Test
  void cellsRunInOrderAndSeeWhatTheCellsBeforeDefined() {
    List<Reported> r = run(Cancellation.NEVER, "f(x) := x^2 + 1", "f(3)", "1/3 + 1/6; a := 2; a");

    assertThat(r).extracting(Reported::id).containsExactly(1L, 2L, 3L);
    assertThat(texts(r.get(0).output())).containsExactly("f(x) := x² + 1");
    assertThat(texts(r.get(1).output())).containsExactly("10");
    assertThat(texts(r.get(2).output())).containsExactly("", "a := 2", "2");
    assertThat(((Output.Done) r.get(2).output()).after().definition("a")).isPresent();
    Result.Value half = (Result.Value) ((Output.Done) r.get(1).output()).results().getFirst();
    assertThat(LayoutPrinter.flatten(half.layout())).isEqualTo(half.text());
    assertThat(((Result.Defined) ((Output.Done) r.get(0).output()).results().getFirst()).layout())
        .isNotNull();
    assertThat(((Output.Done) r.get(0).output()).after().definition("a")).isEmpty();
  }

  @Test
  void errorsKeepTheirPositionAndLineAndTheCellGoesOn() {
    List<Reported> r =
        run(Cancellation.NEVER, "1 +", "x := 1\n1/0 + undefinedname(", "2\nclear(3)\n4");

    assertThat(texts(r.get(0).output())).hasSize(1).first().asString().endsWith("@4 1 +");
    assertThat(texts(r.get(1).output()))
        .hasSize(1)
        .first()
        .asString()
        .endsWith(" 1/0 + undefinedname(");
    List<String> third = texts(r.get(2).output());
    assertThat(third).hasSize(3);
    assertThat(third.get(0)).isEqualTo("2");
    assertThat(third.get(1)).contains("@7 clear(3)");
    assertThat(third.get(2)).isEqualTo("4");
  }

  @Test
  void aCancelledJobReportsTheRestCancelledAndKeepsWhatFinished() {
    List<EvaluationJob.Input> inputs =
        List.of(
            new EvaluationJob.Input(1, "a := 1"),
            new EvaluationJob.Input(2, "expand((x + y + z + w + 1)^30)"),
            new EvaluationJob.Input(3, "a + 1"));
    List<Reported> out = new ArrayList<>();
    boolean[] stop = {false};
    boolean finished =
        EvaluationJob.run(
            inputs,
            Environment.EMPTY,
            () -> stop[0],
            false,
            Format.EXACT,
            new Inspections(),
            (id, o) -> {
              out.add(new Reported(id, o));
              stop[0] = true; // requested once the first cell has finished
            });

    assertThat(finished).isFalse();
    assertThat(out).extracting(Reported::id).containsExactly(1L, 2L, 3L);
    assertThat(out.get(0).output()).isInstanceOf(Output.Done.class);
    assertThat(out.get(1).output()).isEqualTo(Output.Cancelled.INSTANCE);
    assertThat(out.get(2).output()).isEqualTo(Output.Cancelled.INSTANCE);
  }

  @Test
  void aStatementStoppedMidwayLeavesNothing() {
    AtomicInteger checks = new AtomicInteger();
    // trips well inside the expansion: thousands of checks run before it ends
    Cancellation trip = () -> checks.incrementAndGet() > 200;
    long start = System.nanoTime();
    List<Reported> r = run(trip, "expand((x + y + z + w + 1)^30)");
    long millis = (System.nanoTime() - start) / 1_000_000;

    assertThat(r).extracting(Reported::output).containsExactly(Output.Cancelled.INSTANCE);
    // whole-run budget of 120 s; typically a few milliseconds, against about 4.5 s uncancelled
    assertThat(millis).isLessThan(120_000);
  }

  @Test
  void aTracedValueCarriesItsStepsPrintedOnTheWorker() {
    List<EvaluationJob.Input> inputs = List.of(new EvaluationJob.Input(1, "steps(sqrt(8))"));
    List<Reported> out = new ArrayList<>();
    EvaluationJob.run(
        inputs,
        Environment.EMPTY,
        Cancellation.NEVER,
        false,
        Format.EXACT,
        new Inspections(),
        (id, o) -> out.add(new Reported(id, o)));
    Result.Value v = (Result.Value) ((Output.Done) out.getFirst().output()).results().getFirst();

    assertThat(v.traced()).isTrue();
    assertThat(v.steps()).isNotEmpty();
    assertThat(v.steps().getLast().after()).isEqualTo("2√2");
    assertThat(v.steps()).allSatisfy(s -> assertThat(s.layout()).isNotNull());

    // with tracing on, every value is traced; without, only steps(...)
    out.clear();
    EvaluationJob.run(
        List.of(new EvaluationJob.Input(1, "sqrt(8)")),
        Environment.EMPTY,
        Cancellation.NEVER,
        true,
        Format.EXACT,
        new Inspections(),
        (id, o) -> out.add(new Reported(id, o)));
    assertThat(
            ((Result.Value) ((Output.Done) out.getFirst().output()).results().getFirst()).traced())
        .isTrue();
  }

  @Test
  void valuesAndStepsArePrintedInTheFormat() {
    List<Reported> out = new ArrayList<>();
    EvaluationJob.run(
        List.of(new EvaluationJob.Input(1, "1/2;\n7/2 x\n1/3 + 1/6\nsteps(sqrt(8))")),
        Environment.EMPTY,
        Cancellation.NEVER,
        false,
        Format.decimal(4),
        new Inspections(),
        (id, o) -> out.add(new Reported(id, o)));

    assertThat(texts(out.getFirst().output())).containsExactly("", "3.5x", "0.5", "2.828");
    List<Result> results = ((Output.Done) out.getFirst().output()).results();
    Result.Value half = (Result.Value) results.get(2);
    assertThat(LayoutPrinter.flatten(half.layout())).isEqualTo("0.5");
    assertThat(half.format()).isEqualTo(Format.decimal(4));
    assertThat(((Result.Value) results.get(3)).steps().getLast().after()).isEqualTo("2.828");
  }

  @Test
  void aValueAQuadratureGaveIsMarkedNumeric() {
    List<Reported> out = new ArrayList<>();
    Environment env =
        Environment.EMPTY.withSettings(
            new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.DECIMAL, 6).engine());
    EvaluationJob.run(
        List.of(new EvaluationJob.Input(1, "integrate(e^(-x^2), x, 0, 1)\n1/3")),
        env,
        Cancellation.NEVER,
        false,
        Format.decimal(6),
        new Inspections(),
        (id, o) -> out.add(new Reported(id, o)));

    assertThat(texts(out.getFirst().output())).containsExactly("0.746824", "0.333333");
    List<Result> results = ((Output.Done) out.getFirst().output()).results();
    assertThat(((Result.Value) results.get(0)).numeric()).isTrue();
    assertThat(((Result.Value) results.get(1)).numeric()).isFalse();
  }
}

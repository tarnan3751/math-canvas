package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.app.document.SliderSpec;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.Format;
import org.junit.jupiter.api.Test;

/** Cells with the canvas's variables: slider statements, the overlay, what a cell reads. */
class CanvasCellsTest {

  private static Slider slider(String name, String min, String max, String step, String value) {
    return new Slider(
        new ObjectId(1),
        Style.DEFAULT,
        name,
        new BigDecimal(min),
        new BigDecimal(max),
        new BigDecimal(step),
        new BigDecimal(value));
  }

  /** A slider {@code a} from 0 to 5 at 5/2, and a point {@code P} at (1.5, -2). */
  private static final CanvasBindings CANVAS =
      new CanvasBindings(
          Map.of(
              "a",
              CanvasBindings.exact(new BigDecimal("2.5")),
              "P",
              Vector.of(Num.of(Real.of(1.5)), Num.of(Real.of(-2.0)))),
          Map.of("a", slider("a", "0", "5", "0.1", "2.5")),
          Set.of("P"));

  private static List<Output.Done> run(CanvasBindings canvas, String... texts) {
    List<EvaluationJob.Input> inputs = new ArrayList<>();
    for (int k = 0; k < texts.length; k++) {
      inputs.add(new EvaluationJob.Input(k + 1, texts[k]));
    }
    return run(canvas, inputs);
  }

  private static List<Output.Done> run(CanvasBindings canvas, List<EvaluationJob.Input> inputs) {
    List<Output.Done> out = new ArrayList<>();
    EvaluationJob.run(
        inputs,
        Environment.EMPTY,
        canvas,
        Cancellation.NEVER,
        false,
        Format.EXACT,
        new Inspections(),
        (id, o) -> out.add((Output.Done) o));
    return out;
  }

  private static List<String> texts(Output.Done d) {
    return d.results().stream()
        .map(
            r ->
                switch (r) {
                  case Result.Value v -> v.text();
                  case Result.Defined x -> x.text();
                  case Result.Printed p -> "print " + p.text();
                  case Result.Function fn -> fn.text();
                  case Result.Ran _ -> "(ran)";
                  case Result.Failed f -> f.message() + " @" + f.at().column();
                })
        .toList();
  }

  @Test
  void aSliderStatementBindsItsMinimumAndListsTheSlider() {
    List<Output.Done> r = run(CanvasBindings.NONE, "a := slider(0, 5, 0.1)\n2a", "a + 1");

    assertThat(texts(r.get(0))).containsExactly("a := slider(0, 5, 0.1)", "0");
    assertThat(r.get(0).sliders())
        .containsExactly(
            new SliderSpec("a", BigDecimal.ZERO, new BigDecimal(5), new BigDecimal("0.1")));
    // the value is the canvas's, not the stored environment's
    assertThat(r.get(0).after().definition("a")).isEmpty();
    assertThat(r.get(0).inspection().entries()).isEmpty();
    // the cells after it in the run see it too
    assertThat(texts(r.get(1))).containsExactly("1");
  }

  @Test
  void aSliderThatExistsKeepsItsValueSnappedIntoTheNewRange() {
    assertThat(texts(run(CANVAS, "a := slider(0, 5, 0.1); 2a").getFirst()))
        .containsExactly("a := slider(0, 5, 0.1)", "5");
    assertThat(texts(run(CANVAS, "a := slider(0, 2, 1); a").getFirst()))
        .containsExactly("a := slider(0, 2, 1)", "2");
    assertThat(texts(run(CANVAS, "a := slider(-1, 1, 1/4); a").getFirst()))
        .containsExactly("a := slider(-1, 1, 1/4)", "1");
  }

  @Test
  void aSliderStatementChecksItsRange() {
    assertThat(texts(run(CanvasBindings.NONE, "a := slider(0, pi, 1)").getFirst()))
        .containsExactly("a slider's range and step are decimal numbers @16");
    assertThat(texts(run(CanvasBindings.NONE, "a := slider(0, 1/3, 1)").getFirst()))
        .containsExactly("a slider's range and step are decimal numbers @16");
    assertThat(texts(run(CanvasBindings.NONE, "a := slider(2, 1, 1)").getFirst()))
        .containsExactly("a slider's minimum is below its maximum @1");
    assertThat(texts(run(CanvasBindings.NONE, "a := slider(0, 1, 0)").getFirst()))
        .containsExactly("a slider's step is positive @19");
    assertThat(texts(run(CANVAS, "b := slider(0, a, 1)").getFirst()))
        .containsExactly("a slider's range cannot depend on a slider or a point @16");
    assertThat(texts(run(CANVAS, "P := slider(0, 1, 1)").getFirst()))
        .containsExactly("P is the name of a point on the canvas @1");
    assertThat(run(CANVAS, "P := slider(0, 1, 1)").getFirst().sliders()).isEmpty();
    // anywhere else slider is the engine's builtin, not available
    assertThat(texts(run(CanvasBindings.NONE, "2 slider(0, 1, 1)").getFirst()).getFirst())
        .contains("slider");
  }

  @Test
  void theCanvasVariablesAreBoundBeforeEveryCellThatDoesNotDefineThem() {
    List<Output.Done> r =
        run(CANVAS, "2a\nnth(P, 1)", "a := 3", "a\nP", "clear(a)", "a", "a := slider(0, 5, 1)\na");

    assertThat(texts(r.get(0))).containsExactly("5", "1.5");
    assertThat(texts(r.get(2))).containsExactly("3", "⟨1.5, -2.0⟩");
    assertThat(r.get(1).after().definition("a")).isPresent();
    assertThat(r.get(0).after().definition("a")).isEmpty();
    assertThat(r.get(0).after().definition("P")).isEmpty();
    // a cell's clear unbinds its own definition, and the slider is seen again
    assertThat(texts(r.get(4))).containsExactly("5/2");
    // a slider statement takes the name back from a definition
    assertThat(texts(r.get(5))).containsExactly("a := slider(0, 5, 1)", "3");
    assertThat(r.get(5).after().definition("a")).isEmpty();
  }

  @Test
  void aCellReadsWhatItsExpressionsReadThroughTheirDefinitions() {
    List<Output.Done> r =
        run(
            CANVAS,
            "b := 2a; f(x) := x + nth(P, 2)",
            "b + 1",
            "f(1)",
            "assume(x > a)",
            "c := 1; c");

    assertThat(r.get(0).reads()).isEmpty();
    assertThat(r.get(1).reads()).contains("a", "b").doesNotContain("P");
    assertThat(r.get(2).reads()).contains("f", "P").doesNotContain("a", "x");
    assertThat(r.get(3).reads()).contains("a");
    assertThat(r.get(3).assumes()).isTrue();
    assertThat(r.get(2).assumes()).isFalse();
    assertThat(r.get(4).reads()).containsExactly("c");
  }

  @Test
  void aCellWithAnEnvironmentOfItsOwnStartsFromIt() {
    Environment stored = run(CanvasBindings.NONE, "k := 7").getFirst().after();
    List<Output.Done> r =
        run(
            CanvasBindings.NONE,
            List.of(
                new EvaluationJob.Input(1, "k := 1"),
                new EvaluationJob.Input(2, "k", Optional.of(stored), true),
                new EvaluationJob.Input(3, "k")));

    assertThat(texts(r.get(1))).containsExactly("7");
    assertThat(texts(r.get(2))).containsExactly("7");
  }

  @Test
  void aSliderStatementRunAgainWithoutASubmissionMakesNoSlider() {
    EvaluationJob.Input rerun =
        new EvaluationJob.Input(1, "a := slider(0, 5, 1)\n2a", Optional.empty(), false);

    // the slider there is gives its value, and the cell stores no binding of its own
    Output.Done existing = run(CANVAS, List.of(rerun)).getFirst();
    assertThat(texts(existing)).containsExactly("a := slider(0, 5, 1)", "5");
    assertThat(existing.sliders()).isEmpty();
    assertThat(existing.after().definition("a")).isEmpty();

    // with no slider (deleted on the canvas) the name is a plain value at the minimum
    Output.Done deleted = run(CanvasBindings.NONE, List.of(rerun)).getFirst();
    assertThat(texts(deleted)).containsExactly("a := slider(0, 5, 1)", "0");
    assertThat(deleted.sliders()).isEmpty();
    assertThat(deleted.after().definition("a")).isPresent();
  }

  @Test
  void theCurvesOwnVariablesNameNoSlider() {
    for (String name : List.of("x", "y", "t", "θ")) {
      assertThat(texts(run(CanvasBindings.NONE, name + " := slider(1, 5, 1)").getFirst()))
          .containsExactly(
              name
                  + " is a variable of the canvas's curves and cannot name a slider or a point @1");
    }
  }
}

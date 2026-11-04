package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.app.help.HelpEntries;
import mathcanvas.app.help.HelpEntry;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.print.Format;
import org.junit.jupiter.api.Test;

/**
 * Every example of the help gives the result it shows, as clicking it in the Help window runs it:
 * alone, in a new cell of the worksheet, through the app's evaluation and the real engine, in the
 * exact format, in radians and an empty environment. The last result of the cell is compared with
 * the reference's, spaces ignored, so an example that needs a context it does not carry cannot
 * ship.
 */
class HelpExamplesTest {

  /** What a cell's last result shows: a value with its parameters' note, an echo, or an error. */
  private static String shown(Result r) {
    return switch (r) {
      case Result.Value v -> v.note().isEmpty() ? v.text() : v.text() + ", " + v.note();
      case Result.Defined d -> d.text();
      case Result.Failed f -> "error: " + f.message();
    };
  }

  private static String spaceless(String s) {
    return s.replaceAll("\\s+", "");
  }

  @Test
  void everyExampleGivesTheResultItShows() {
    List<String> wrong = new ArrayList<>();
    for (HelpEntry entry : HelpEntries.all()) {
      for (HelpEntry.Example example : entry.examples()) {
        List<Output> outputs = new ArrayList<>();
        EvaluationJob.run(
            List.of(new EvaluationJob.Input(1, example.input())),
            Environment.EMPTY,
            Cancellation.NEVER,
            false,
            Format.EXACT,
            new Inspections(),
            (id, o) -> outputs.add(o));
        List<Result> results = ((Output.Done) outputs.getLast()).results();
        String got = results.isEmpty() ? "" : shown(results.getLast());
        boolean failed = results.stream().anyMatch(r -> r instanceof Result.Failed);
        if (failed || !spaceless(got).equals(spaceless(example.result()))) {
          wrong.add(
              entry.name()
                  + ": "
                  + example.input()
                  + " shows "
                  + example.result()
                  + ", gives "
                  + got);
        }
      }
    }

    assertThat(wrong).as("help examples whose result is not the one shown").isEmpty();
  }
}

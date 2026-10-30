package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.layout.Fraction;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;
import org.junit.jupiter.api.Test;

class TypesetTest {

  private static Expr value(String source) {
    Session s = new Session(Environment.EMPTY);
    Statement st = Parser.parseScript(source, Names.NONE).statements().getFirst();
    return ((Outcome.Value) s.run(st)).value();
  }

  @Test
  void aSmallLayoutIsTypesetAndALargeOneIsNot() {
    Layout small = LayoutPrinter.INSTANCE.display(value("x/(2 pi)"));
    Layout large = LayoutPrinter.INSTANCE.display(value("expand((x + y + z + 1)^30)"));

    assertThat(Typeset.capped(small)).isSameAs(small);
    assertThat(Typeset.capped(large)).isNull();
    // typeset on this thread, a worker's: no FX toolkit is started
    assertThat(Typeset.box(small, Typeset.RESULT_SIZE, Cancellation.NEVER).width()).isPositive();
    assertThat(Typeset.box(null, Typeset.RESULT_SIZE, Cancellation.NEVER)).isNull();
  }

  @Test
  void anEchoIsItsHeadAndBodyJoinedByTheDefinitionSign() {
    Statement.Definition d =
        (Statement.Definition)
            Parser.parseScript("f(x) := x^2 + 1", Names.NONE).statements().getFirst();

    assertThat(LayoutPrinter.flatten(Typeset.echo(LayoutPrinter.INSTANCE, d)))
        .isEqualTo("f(x):=x² + 1");
  }

  @Test
  void aSubexpressionIsFoundByItsPath() {
    Fraction f = (Fraction) LayoutPrinter.INSTANCE.display(value("x/(2 pi)"));

    assertThat(Typeset.find(f, f.den().path())).isSameAs(f.den());
    assertThat(LayoutPrinter.flatten(Typeset.find(f, f.den().path()))).isEqualTo("2π");
    assertThat(Typeset.find(f, new Path(List.of(9, 9)))).isNull();
  }
}

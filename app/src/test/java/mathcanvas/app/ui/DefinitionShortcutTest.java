package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Num;
import org.junit.jupiter.api.Test;

class DefinitionShortcutTest {

  @Test
  void aRelationWithAFreshHeadIsOfferedAsADefinition() {
    assertThat(DefinitionShortcut.offer("f(x) = x^2 + 1", Environment.EMPTY))
        .contains(new DefinitionShortcut.Offer("f", "f(x) := x^2 + 1"));
    assertThat(DefinitionShortcut.offer("  area( w ,h )=w h ", Environment.EMPTY))
        .contains(new DefinitionShortcut.Offer("area", "area(w, h) := w h"));
    assertThat(DefinitionShortcut.offer("g(θ) = sin(θ)/2", Environment.EMPTY))
        .contains(new DefinitionShortcut.Offer("g", "g(θ) := sin(θ)/2"));
  }

  @Test
  void anythingElseIsNotOffered() {
    Environment withF = Environment.EMPTY.with("f", new Definition.Value(Num.of(1)));
    for (String text :
        List.of(
            "f(x) == x",
            "f(x) <= x",
            "f(x) := x",
            "f(x) =",
            "f() = 1",
            "f(2) = 4",
            "f(x + 1) = x",
            "x = 2",
            "sin(x) = 1/2",
            "pi(x) = 1",
            "f(x) = 1\ng(x) = 2")) {
      assertThat(DefinitionShortcut.offer(text, Environment.EMPTY)).as(text).isEmpty();
    }
    assertThat(DefinitionShortcut.offer("f(x) = x", withF)).isEmpty();
  }
}

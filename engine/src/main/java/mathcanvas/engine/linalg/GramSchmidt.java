package mathcanvas.engine.linalg;

import static mathcanvas.engine.linalg.Recorder.values;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.parse.Brackets;

/**
 * Classical Gram–Schmidt, not normalised: {@code wₖ = vₖ − Σ (⟨wⱼ, vₖ⟩/⟨wⱼ, wⱼ⟩) wⱼ} over the
 * vectors kept before it, with the Hermitian product {@code ⟨u, v⟩ = Σ conj(uᵢ) vᵢ} (the dot
 * product for real entries; symbols are real). A vector that reduces to zero lies in the span of
 * those before it and is dropped ({@code gramschmidt.dependent}); each other vector changed is a
 * step ({@code gramschmidt.project}); vectors already orthogonal are one step, {@code
 * gramschmidt.done}.
 */
final class GramSchmidt {

  /** The rule set of the steps. */
  static final String RULE_SET = "gramschmidt";

  private GramSchmidt() {}

  static Expr orthogonalize(List<List<Expr>> vectors, Entries en, Recorder steps) {
    List<List<Expr>> kept = new ArrayList<>();
    List<Expr> squares = new ArrayList<>();
    for (int k = 0; k < vectors.size(); k++) {
      List<Expr> v = vectors.get(k);
      List<Expr> w = new ArrayList<>(v);
      for (int j = 0; j < kept.size(); j++) {
        Expr c = en.div(inner(kept.get(j), v, en), squares.get(j));
        if (en.isZero(c) == Tri.TRUE) {
          continue;
        }
        for (int i = 0; i < w.size(); i++) {
          w.set(i, en.sub(w.get(i), en.mul(c, kept.get(j).get(i))));
        }
      }
      boolean zero = w.stream().allMatch(e -> en.isZero(e) == Tri.TRUE);
      List<List<Expr>> state = new ArrayList<>();
      if (!zero) {
        kept.add(w);
        squares.add(inner(w, w, en));
      }
      state.addAll(kept);
      state.addAll(vectors.subList(k + 1, vectors.size()));
      if (zero) {
        steps.step(RULE_SET, "dependent", list(state, en), values("k", Num.of(k + 1)));
      } else if (!w.equals(v)) {
        steps.step(RULE_SET, "project", list(state, en), values("k", Num.of(k + 1)));
      }
    }
    Expr value = list(kept, en);
    steps.step(RULE_SET, "done", value, values());
    return value;
  }

  private static Expr list(List<List<Expr>> vectors, Entries en) {
    return en.canon(Brackets.of(vectors.stream().map(v -> (Expr) Vector.of(v)).toList()));
  }

  /** {@code ⟨u, v⟩ = Σ conj(uᵢ) vᵢ}. */
  static Expr inner(List<Expr> u, List<Expr> v, Entries en) {
    Expr total = Num.ZERO;
    for (int i = 0; i < u.size(); i++) {
      total = en.add(total, en.mul(conj(u.get(i), en), v.get(i)));
    }
    return total;
  }

  /** The conjugate of an entry: of its numbers, through sums and products; symbols are real. */
  static Expr conj(Expr e, Entries en) {
    if (e instanceof Num n) {
      return en.canon(Num.of(n.value().conj()));
    }
    if (e instanceof Sum || e instanceof Product) {
      List<Expr> ops = new ArrayList<>();
      for (Expr op : Operands.of(e)) {
        ops.add(conj(op, en));
      }
      return en.canon(Operands.rebuild(e, ops));
    }
    return e;
  }
}

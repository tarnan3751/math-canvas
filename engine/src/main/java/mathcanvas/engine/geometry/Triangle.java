package mathcanvas.engine.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The triangle solver ({@code docs/language.md} §14.18): from three of the six parts {@code a b c A
 * B C} (sides and their opposite angles), the other three, by the law of cosines, the law of sines
 * and the angle sum, each a step of the rule set {@link #RULE_SET}.
 *
 * <ul>
 *   <li>SSS: every angle by the law of cosines;
 *   <li>SAS: the third side by the law of cosines, the angle opposite the shorter given side (an
 *       acute one) by the law of sines, the last by the angle sum;
 *   <li>ASA and AAS: the third angle by the angle sum, the other sides by the law of sines;
 *   <li>SSA, the ambiguous case: none, one or two triangles ({@code triangle.ambiguous}), the
 *       result a set of them.
 * </ul>
 *
 * <p>Data that make no triangle (a side not shorter than the other two together, angles of a half
 * turn or more) give the empty set. The values are exact where the table gives them, and otherwise
 * the inverse functions stay. The angles come in radians and go out in the session's unit.
 */
public final class Triangle {

  /** The rule set of the steps, and the narration bundle's key prefix. */
  public static final String RULE_SET = "triangle";

  /** The steps, each narrated by {@code triangle.<step>}. */
  public static final List<String> STEPS =
      List.of("law-of-cosines", "law-of-sines", "angle-sum", "ambiguous", "no-triangle");

  private static final List<String> NAMES = List.of("a", "b", "c", "A", "B", "C");

  private final Context ctx;
  private final Decide decide;
  private final List<Rewrite> steps = new ArrayList<>();
  private Expr state;

  private Triangle(Context ctx, Expr initial) {
    this.ctx = ctx;
    this.decide = new Decide(ctx);
    this.state = initial;
  }

  /**
   * Solves a triangle.
   *
   * @param given three parts by name ({@code a b c} sides, {@code A B C} the opposite angles, in
   *     radians), closed real values
   * @param ctx the context: precision, numerics (with the session's half turn), trace switch
   * @return one triangle as a list of six relations, a set of them for the ambiguous case, or the
   *     empty set; empty when a comparison cannot be decided
   * @throws IllegalArgumentException if there is no side, a side is not positive, or an angle is
   *     not strictly between 0 and a half turn
   */
  public static Optional<Rewriter.Result> solve(Map<String, Expr> given, Context ctx) {
    Expr[] parts = new Expr[6];
    for (Map.Entry<String, Expr> g : given.entrySet()) {
      parts[NAMES.indexOf(g.getKey())] = Canonical.of(g.getValue(), ctx.precision());
    }
    Triangle t = new Triangle(ctx, null);
    t.state = t.known(parts);
    Expr initial = t.state;
    try {
      Expr result = t.run(parts);
      Trace trace =
          ctx.traceEnabled() ? new Trace(initial, result, t.steps, List.of()) : Trace.NONE;
      return Optional.of(new Rewriter.Result(result, trace));
    } catch (Decide.Undecided u) {
      return Optional.empty();
    }
  }

  private Expr run(Expr[] p) {
    int sides = 0;
    int angles = 0;
    for (int i = 0; i < 3; i++) {
      if (p[i] != null) {
        sides++;
        if (decide.sign(p[i]) <= 0) {
          throw new IllegalArgumentException("`triangle`'s sides must be positive");
        }
      }
      if (p[i + 3] != null) {
        angles++;
        if (decide.sign(p[i + 3]) <= 0 || decide.compare(p[i + 3], Constant.PI) >= 0) {
          throw new IllegalArgumentException(
              "`triangle`'s angles must be between 0 and a half turn");
        }
      }
    }
    if (sides == 0) {
      throw new IllegalArgumentException("`triangle` needs a side");
    }
    if (sides == 3) {
      return sss(p);
    }
    if (sides == 1) {
      return angleSideAngle(p);
    }
    int missing = p[0] == null ? 0 : p[1] == null ? 1 : 2;
    if (p[missing + 3] != null) {
      return sas(p, missing);
    }
    return ssa(p, missing);
  }

  /** Three sides: the triangle inequality, then each angle by the law of cosines. */
  private Expr sss(Expr[] p) {
    for (int i = 0; i < 3; i++) {
      if (decide.compare(p[i], Sum.of(p[(i + 1) % 3], p[(i + 2) % 3])) >= 0) {
        return none(names(i));
      }
    }
    for (int i = 0; i < 3; i++) {
      p[i + 3] = cosineAngle(p, i);
    }
    step("law-of-cosines", p, names(3, 4, 5));
    return finish(p);
  }

  /** Two sides and the angle between them. */
  private Expr sas(Expr[] p, int missing) {
    int i = (missing + 1) % 3;
    int j = (missing + 2) % 3;
    Expr square =
        Sum.of(
            Power.of(p[i], Num.of(2)),
            Power.of(p[j], Num.of(2)),
            Product.of(Num.of(-2), p[i], p[j], Apply.of(Symbol.of("cos"), p[missing + 3])));
    p[missing] = simplify(Exprs.sqrt(square));
    step("law-of-cosines", p, names(missing));
    int shorter = decide.compare(p[i], p[j]) <= 0 ? i : j;
    int other = shorter == i ? j : i;
    p[shorter + 3] = sineAngle(p[shorter], p[missing], p[missing + 3]);
    step("law-of-sines", p, names(shorter + 3));
    p[other + 3] =
        decide.combine(Exprs.sub(Exprs.sub(Constant.PI, p[missing + 3]), p[shorter + 3]));
    step("angle-sum", p, names(other + 3));
    return finish(p);
  }

  /** One side and two angles: the third angle by the angle sum, the sides by the law of sines. */
  private Expr angleSideAngle(Expr[] p) {
    int third = p[3] == null ? 0 : p[4] == null ? 1 : 2;
    Expr rest = Exprs.sub(Exprs.sub(Constant.PI, p[(third + 1) % 3 + 3]), p[(third + 2) % 3 + 3]);
    if (decide.sign(rest) <= 0) {
      return none(names(3, 4, 5));
    }
    p[third + 3] = decide.combine(rest);
    step("angle-sum", p, names(third + 3));
    int side = p[0] != null ? 0 : p[1] != null ? 1 : 2;
    List<Integer> found = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      if (p[i] == null) {
        p[i] = sineSide(p[side], p[side + 3], p[i + 3]);
        found.add(i);
      }
    }
    step("law-of-sines", p, names(found.stream().mapToInt(Integer::intValue).toArray()));
    return finish(p);
  }

  /**
   * Two sides and an angle opposite one of them: with {@code s} the side opposite the angle and
   * {@code o} the other given side, {@code h = o·sin(angle)} decides none, one or two triangles.
   */
  private Expr ssa(Expr[] p, int missing) {
    int s = p[3] != null ? 0 : p[4] != null ? 1 : 2;
    int o = 3 - s - missing;
    Expr angle = p[s + 3];
    Expr h = Product.of(p[o], Apply.of(Symbol.of("sin"), angle));
    List<Boolean> branches = new ArrayList<>();
    boolean obtuse = decide.compare(angle, Exprs.div(Constant.PI, Num.of(2))) >= 0;
    if (obtuse) {
      if (decide.compare(p[s], p[o]) > 0) {
        branches.add(false);
      }
    } else {
      int toH = decide.compare(p[s], h);
      if (toH == 0) {
        branches.add(false);
      } else if (toH > 0) {
        branches.add(false);
        if (decide.compare(p[s], p[o]) < 0) {
          branches.add(true);
        }
      }
    }
    if (branches.isEmpty()) {
      return none(names(s, o, s + 3));
    }
    // each triangle's parts, found a law at a time, every branch at once
    List<Expr[]> triangles = new ArrayList<>();
    Expr acute = sineAngle(p[o], p[s], angle);
    for (boolean supplement : branches) {
      Expr[] t = p.clone();
      t[o + 3] = supplement ? decide.combine(Exprs.sub(Constant.PI, acute)) : acute;
      triangles.add(t);
    }
    record(
        "ambiguous",
        set(triangles),
        Bindings.EMPTY
            .with(binding("n_", Num.of(triangles.size())))
            .with(binding("p_", names(o + 3))));
    for (Expr[] t : triangles) {
      t[missing + 3] = decide.combine(Exprs.sub(Exprs.sub(Constant.PI, angle), t[o + 3]));
    }
    record("angle-sum", set(triangles), Bindings.EMPTY.with(binding("p_", names(missing + 3))));
    for (Expr[] t : triangles) {
      t[missing] = sineSide(t[s], angle, t[missing + 3]);
    }
    record("law-of-sines", set(triangles), Bindings.EMPTY.with(binding("p_", names(missing))));
    return set(triangles);
  }

  /** The set of the triangles' known parts. */
  private Expr set(List<Expr[]> triangles) {
    List<Expr> lists = new ArrayList<>();
    for (Expr[] t : triangles) {
      lists.add(known(t));
    }
    return Canonical.of(new SetExpr(lists), ctx.precision());
  }

  // The laws.

  /** The angle opposite side {@code i} from the three sides: {@code acos((b² + c² − a²)/(2bc))}. */
  private Expr cosineAngle(Expr[] p, int i) {
    Expr a = p[i];
    Expr b = p[(i + 1) % 3];
    Expr c = p[(i + 2) % 3];
    Expr cos =
        Exprs.div(
            Sum.of(
                Power.of(b, Num.of(2)), Power.of(c, Num.of(2)), Exprs.neg(Power.of(a, Num.of(2)))),
            Product.of(Num.of(2), b, c));
    return simplify(Apply.of(Symbol.of("acos"), cos));
  }

  /** The acute angle opposite {@code side}: {@code asin(side·sin(angle)/known)}. */
  private Expr sineAngle(Expr side, Expr known, Expr angle) {
    return simplify(
        Apply.of(
            Symbol.of("asin"),
            Exprs.div(Product.of(side, Apply.of(Symbol.of("sin"), angle)), known)));
  }

  /** The side opposite {@code angle}: {@code known·sin(angle)/sin(knownAngle)}. */
  private Expr sineSide(Expr known, Expr knownAngle, Expr angle) {
    return simplify(
        Exprs.div(
            Product.of(known, Apply.of(Symbol.of("sin"), angle)),
            Apply.of(Symbol.of("sin"), knownAngle)));
  }

  // States.

  private Expr none(Expr reason) {
    Expr empty = Canonical.of(new SetExpr(List.of()), ctx.precision());
    record("no-triangle", empty, Bindings.EMPTY.with(binding("p_", reason)));
    return empty;
  }

  private Expr finish(Expr[] p) {
    return list(p);
  }

  private void step(String rule, Expr[] p, Expr found) {
    record(rule, known(p), Bindings.EMPTY.with(binding("p_", found)));
  }

  private void record(String rule, Expr after, Bindings b) {
    if (ctx.traceEnabled() && !after.equals(state)) {
      steps.add(new Rewrite(state, after, RULE_SET, rule, Path.ROOT, b));
    }
    state = after;
  }

  /** The list of the parts known, in the order {@code a b c A B C}, the angles in the unit. */
  private Expr known(Expr[] p) {
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      if (p[i] != null) {
        out.add(new Relation(Relation.Op.EQ, Symbol.of(NAMES.get(i)), i < 3 ? p[i] : unit(p[i])));
      }
    }
    return Canonical.of(new ListExpr(out), ctx.precision());
  }

  private Expr list(Expr[] p) {
    return known(p);
  }

  /** An angle in radians in the session's unit: times {@code H/π}. */
  private Expr unit(Expr radians) {
    int half = ctx.numerics().halfTurn();
    if (half == 0) {
      return radians;
    }
    return decide.combine(Exprs.div(Product.of(radians, Num.of(half)), Constant.PI));
  }

  /** The parts' names as a text to narrate, {@code a}, {@code a and b}, {@code A, B and C}. */
  private static Expr names(int... indices) {
    StringBuilder text = new StringBuilder();
    for (int k = 0; k < indices.length; k++) {
      if (k > 0) {
        text.append(k == indices.length - 1 ? " and " : ", ");
      }
      text.append(NAMES.get(indices[k]));
    }
    return Symbol.of(text.toString());
  }

  private Expr simplify(Expr e) {
    return decide.simplify(e);
  }

  private static Bindings.Binding binding(String name, Expr value) {
    return new Bindings.Binding(name, value, null, null);
  }
}

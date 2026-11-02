package mathcanvas.engine.linalg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;

/** Shared helpers of the linear-algebra tests. */
final class LinalgCases {

  static final Context TRACED = Context.DEFAULT.withTrace(true);

  private LinalgCases() {}

  static Expr parse(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE));
  }

  /** Runs a call written as text, traced, and checks that its steps chain. */
  static Rewriter.Result run(String call) {
    return run((Apply) parse(call));
  }

  static Rewriter.Result run(Apply call) {
    Rewriter.Result r = Linalg.apply(call, TRACED);
    TraceChecks.assertChain(r);
    return r;
  }

  static Expr value(String name, Expr... args) {
    return Linalg.apply(new Apply(Symbol.of(name), List.of(args)), Context.DEFAULT).expr();
  }

  static List<String> rules(Rewriter.Result r) {
    return r.trace().rewrites().stream().map(w -> w.ruleSet() + "." + w.rule()).toList();
  }

  static long count(Rewriter.Result r, String set) {
    return r.trace().rewrites().stream().map(Rewrite::ruleSet).filter(set::equals).count();
  }

  /** The exact product of two matrices. */
  static List<List<Expr>> multiply(List<List<Expr>> a, List<List<Expr>> b) {
    Entries en = new Entries(Context.DEFAULT);
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> row : a) {
      List<Expr> r = new ArrayList<>();
      for (int j = 0; j < b.getFirst().size(); j++) {
        Expr total = Num.ZERO;
        for (int k = 0; k < row.size(); k++) {
          total = en.add(total, en.mul(row.get(k), b.get(k).get(j)));
        }
        r.add(total);
      }
      out.add(r);
    }
    return out;
  }

  static List<List<Expr>> identity(int n) {
    return ((Matrix) value("identity", Num.of(n))).rows();
  }

  /** A random matrix of small rationals, zero now and then. */
  static Matrix random(Random random, int rows, int columns) {
    List<List<Expr>> out = new ArrayList<>();
    for (int i = 0; i < rows; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < columns; j++) {
        row.add(
            random.nextInt(5) == 0
                ? Num.ZERO
                : Num.of(Rational.of(random.nextInt(11) - 5, 1 + random.nextInt(3))));
      }
      out.add(row);
    }
    return Matrix.of(out);
  }

  static void isIdentity(List<List<Expr>> m) {
    assertThat(m).isEqualTo(identity(m.size()));
  }
}

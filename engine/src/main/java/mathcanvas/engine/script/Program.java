package mathcanvas.engine.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Symbol;

/**
 * A block of statements as parsed ({@code docs/language.md} §19): the body of a loop, a branch of
 * an if, or a function. Immutable.
 *
 * @param statements the statements, in order
 */
public record Program(List<Stmt> statements) {

  /** The block of no statements. */
  public static final Program EMPTY = new Program(List.of());

  /**
   * Copies the statements.
   *
   * @throws NullPointerException if a statement is null
   */
  public Program {
    statements = List.copyOf(statements);
  }

  /**
   * Every expression of the block's statements and of the blocks inside them, in order, as one
   * list: what an analysis of the names a program reads walks.
   *
   * @return the list
   */
  public ListExpr expressions() {
    List<Expr> out = new ArrayList<>();
    collect(this, out);
    return new ListExpr(out);
  }

  private static void collect(Program p, List<Expr> out) {
    for (Stmt s : p.statements) {
      switch (s) {
        case Stmt.Assign a -> {
          out.add(a.name());
          out.add(a.value());
        }
        case Stmt.Evaluate e -> out.add(e.expr());
        case Stmt.Print pr -> out.add(pr.value());
        case Stmt.If i -> {
          for (Stmt.Branch b : i.branches()) {
            out.add(b.condition());
            collect(b.body(), out);
          }
          collect(i.otherwise(), out);
        }
        case Stmt.For f -> {
          out.add(f.variable());
          switch (f.source()) {
            case Stmt.Range r -> {
              out.add(r.from());
              out.add(r.to());
              if (r.step() != null) {
                out.add(r.step());
              }
            }
            case Stmt.Items items -> out.add(items.items());
          }
          collect(f.body(), out);
        }
        case Stmt.While w -> {
          out.add(w.condition());
          collect(w.body(), out);
        }
        case Stmt.Return r -> out.add(r.value());
        case Stmt.Global g -> out.addAll(g.names());
        case Stmt.Break _, Stmt.Continue _ -> {}
      }
    }
  }

  /**
   * The names the block and the blocks inside it assign with {@code :=}.
   *
   * @return the names
   */
  public Set<String> assigned() {
    Set<String> out = new TreeSet<>();
    walk(this, out, false);
    return out;
  }

  /**
   * The names the block and the blocks inside it declare {@code global}.
   *
   * @return the names
   */
  public Set<String> globals() {
    Set<String> out = new TreeSet<>();
    walk(this, out, true);
    return out;
  }

  private static void walk(Program p, Set<String> out, boolean globals) {
    for (Stmt s : p.statements) {
      switch (s) {
        case Stmt.Assign a -> {
          if (!globals) {
            out.add(a.name().name());
          }
        }
        case Stmt.Global g -> {
          if (globals) {
            g.names().forEach(n -> out.add(n.name()));
          }
        }
        case Stmt.If i -> {
          for (Stmt.Branch b : i.branches()) {
            walk(b.body(), out, globals);
          }
          walk(i.otherwise(), out, globals);
        }
        case Stmt.For f -> walk(f.body(), out, globals);
        case Stmt.While w -> walk(w.body(), out, globals);
        case Stmt.Evaluate _, Stmt.Print _, Stmt.Return _, Stmt.Break _, Stmt.Continue _ -> {}
      }
    }
  }

  /**
   * A checked copy helper for the statements that hold names.
   *
   * @param names the names
   * @return the names, copied
   */
  static List<Symbol> names(List<Symbol> names) {
    List<Symbol> out = List.copyOf(names);
    out.forEach(n -> Objects.requireNonNull(n, "name"));
    return out;
  }
}

package mathcanvas.engine.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;

/**
 * The read-ahead of the commands whose arguments the parser shapes: the names a binder command
 * binds before its variable, the unknown functions {@code dsolve}, {@code odesolve}, {@code rsolve}
 * and {@code laplace} declare, and the words {@code tvm} takes by name, for the {@link Parser}
 * reading the call.
 */
final class CommandShapes {

  private final Parser parser;

  CommandShapes(Parser parser) {
    this.parser = parser;
  }

  /**
   * The unknown functions of a {@code laplace(f, t, s)} call being read, read ahead: the names
   * applied to exactly the variable {@code (t)} in the first argument, with or without primes, that
   * are neither builtins, constants, domains, keywords, the variable, bound names nor user
   * functions. Empty when the second argument is not a name.
   */
  List<String> laplaceUnknowns() {
    int depth = 0;
    int end;
    for (int k = 0; ; k++) {
      TokenKind kind = parser.raw(k).kind();
      if (kind == TokenKind.ERROR
          || kind == TokenKind.EOF
          || kind == TokenKind.SEMI
          || kind == TokenKind.NEWLINE) {
        return List.of();
      }
      if (kind == TokenKind.LPAREN
          || kind == TokenKind.LBRACKET
          || kind == TokenKind.LBRACE
          || kind == TokenKind.LANGLE) {
        depth++;
      } else if (kind == TokenKind.RPAREN
          || kind == TokenKind.RBRACKET
          || kind == TokenKind.RBRACE
          || kind == TokenKind.RANGLE) {
        if (depth == 0) {
          return List.of();
        }
        depth--;
      } else if (kind == TokenKind.COMMA && depth == 0) {
        end = k;
        break;
      }
    }
    if (!parser.raw(end + 1).is(TokenKind.IDENT) || !parser.raw(end + 2).is(TokenKind.COMMA)) {
      return List.of();
    }
    String var = parser.raw(end + 1).text();
    List<String> out = new ArrayList<>();
    for (int k = 0; k + 3 < end; k++) {
      if (!parser.raw(k).is(TokenKind.IDENT)
          || !parser.raw(k + 1).is(TokenKind.LPAREN)
          || !parser.raw(k + 2).is(TokenKind.IDENT)
          || !parser.raw(k + 2).text().equals(var)
          || !parser.raw(k + 3).is(TokenKind.RPAREN)) {
        continue;
      }
      String name = parser.raw(k).text();
      String base = name.contains("'") ? name.substring(0, name.indexOf('\'')) : name;
      if (base.isEmpty()
          || Builtins.lookup(base).isPresent()
          || Builtins.isConstant(base)
          || Builtins.isDomain(base)
          || Builtins.isKeyword(base)
          || base.equals(var)
          || parser.bound(base)
          || parser.isUserFunction(base)
          || out.contains(base)) {
        continue;
      }
      out.add(base);
    }
    return out;
  }

  /** The commands whose second argument declares unknown functions. */
  static final Set<String> UNKNOWN_COMMANDS = Set.of("dsolve", "odesolve", "rsolve");

  /** The parts of {@code tvm}'s arguments (§14.28), literal words before {@code =} there. */
  static final Set<String> TVM_PARTS = Set.of("n", "i", "pv", "pmt", "fv", "type");

  /**
   * The unknowns of a {@code dsolve} or {@code odesolve} call being read, read ahead from its
   * second argument: {@code y(x)} or {@code [x(t), y(t), …]}, one variable throughout, the function
   * names neither builtins nor the variable. Gives the variable then the function names, or null
   * when the argument has another shape (the evaluator then reports it); sets {@link #bindAt} to 2.
   */
  List<String> declaredUnknowns() {
    bindAt = 2;
    int depth = 0;
    int start = -1;
    for (int k = 0; ; k++) {
      TokenKind kind = parser.raw(k).kind();
      if (kind == TokenKind.ERROR
          || kind == TokenKind.EOF
          || kind == TokenKind.SEMI
          || kind == TokenKind.NEWLINE) {
        return null;
      }
      if (kind == TokenKind.LPAREN
          || kind == TokenKind.LBRACKET
          || kind == TokenKind.LBRACE
          || kind == TokenKind.LANGLE) {
        depth++;
      } else if (kind == TokenKind.RPAREN
          || kind == TokenKind.RBRACKET
          || kind == TokenKind.RBRACE
          || kind == TokenKind.RANGLE) {
        if (depth == 0) {
          return null;
        }
        depth--;
      } else if (kind == TokenKind.COMMA && depth == 0) {
        start = k + 1;
        break;
      }
    }
    int k = start;
    boolean list = parser.raw(k).is(TokenKind.LBRACKET) || parser.raw(k).is(TokenKind.LANGLE);
    TokenKind close = parser.raw(k).is(TokenKind.LANGLE) ? TokenKind.RANGLE : TokenKind.RBRACKET;
    if (list) {
      k++;
    }
    List<String> out = new ArrayList<>();
    out.add(null);
    while (true) {
      Token f = parser.raw(k);
      Token v = parser.raw(k + 2);
      if (!f.is(TokenKind.IDENT)
          || !parser.raw(k + 1).is(TokenKind.LPAREN)
          || !v.is(TokenKind.IDENT)
          || !parser.raw(k + 3).is(TokenKind.RPAREN)) {
        return null;
      }
      String fn = f.text();
      String var = v.text();
      if (fn.endsWith("'")
          || var.endsWith("'")
          || Builtins.lookup(fn).isPresent()
          || Builtins.isConstant(fn)
          || Builtins.isDomain(fn)
          || Builtins.isKeyword(fn)
          || Builtins.lookup(var).isPresent()
          || Builtins.isConstant(var)
          || Builtins.isDomain(var)
          || Builtins.isKeyword(var)
          || fn.equals(var)
          || (out.getFirst() != null && !out.getFirst().equals(var))
          || out.contains(fn)) {
        return null;
      }
      out.set(0, var);
      out.add(fn);
      k += 4;
      if (list && parser.raw(k).is(TokenKind.COMMA)) {
        k++;
        continue;
      }
      break;
    }
    if (list) {
      if (!parser.raw(k).is(close)) {
        return null;
      }
      k++;
    }
    return parser.raw(k).is(TokenKind.COMMA) || parser.raw(k).is(TokenKind.RPAREN) ? out : null;
  }

  /**
   * The 1-based position of the argument naming the variable of the call being read: 2 for most
   * binder commands, 3 for {@code area(f, g, x, a, b)} and {@code volume(f, g, x, a, b)} ({@link
   * Builtin#binderFor}); set by {@link #binderNames}.
   */
  private int bindAt = 2;

  /** The position of the variable's argument in the call being read ({@link #bindAt}). */
  int bindAt() {
    return bindAt;
  }

  /** Sets the position of the variable's argument, before a call's read-ahead. */
  void bindAt(int position) {
    bindAt = position;
  }

  /** The arity message of {@code integrate}, whose iterated form takes brackets (§12.2). */
  static final String INTEGRATE_ARITY =
      "`integrate` takes 2 or 4 arguments, as in `integrate(x^2, x)` or `integrate(x^2, x, 0, 1)`,"
          + " or square brackets [x, a, b] after the function";

  /** The commands whose variable may be a list of names. */
  private static final Set<String> LIST_BINDERS =
      Set.of(
          "solve",
          // multivariable calculus
          "grad",
          "jacobian",
          "hessian",
          "divergence",
          "curl",
          "laplacian",
          "lagrange");

  /**
   * The names a binder command binds in the arguments before its variable, read ahead from the
   * variable's argument ({@link Builtin#binderFor} of the count of arguments, setting {@link
   * #bindAt}) when that is a name or, for {@code solve} and the others of {@link #LIST_BINDERS}, a
   * list of names in {@code [ ]} or {@code ⟨ ⟩}; null otherwise.
   */
  List<String> binderNames(Builtin builtin) {
    int depth = 0;
    int count = 1;
    List<Integer> starts = new ArrayList<>(List.of(0));
    for (int k = 0; ; k++) {
      Token t = parser.raw(k);
      TokenKind kind = t.kind();
      if (kind == TokenKind.ERROR
          || kind == TokenKind.EOF
          || kind == TokenKind.SEMI
          || kind == TokenKind.NEWLINE) {
        return null;
      }
      if (kind == TokenKind.LPAREN
          || kind == TokenKind.LBRACKET
          || kind == TokenKind.LBRACE
          || kind == TokenKind.LANGLE) {
        depth++;
      } else if (kind == TokenKind.RPAREN
          || kind == TokenKind.RBRACKET
          || kind == TokenKind.RBRACE
          || kind == TokenKind.RANGLE) {
        if (depth == 0) {
          break;
        }
        depth--;
      } else if (kind == TokenKind.COMMA && depth == 0) {
        count++;
        starts.add(k + 1);
      }
    }
    bindAt = builtin.binderFor(count).orElse(2);
    if (bindAt > starts.size()) {
      return null;
    }
    int second = starts.get(bindAt - 1);
    List<Token> found = new ArrayList<>();
    int end = wrappedName(second, found);
    if (end < 0 && LIST_BINDERS.contains(builtin.name())) {
      int k = second;
      int open = 0;
      while (parser.raw(k).is(TokenKind.PLUS) || parser.raw(k).is(TokenKind.LPAREN)) {
        open += parser.raw(k++).is(TokenKind.LPAREN) ? 1 : 0;
      }
      if (parser.raw(k).is(TokenKind.LBRACKET) || parser.raw(k).is(TokenKind.LANGLE)) {
        TokenKind close =
            parser.raw(k).is(TokenKind.LBRACKET) ? TokenKind.RBRACKET : TokenKind.RANGLE;
        k++;
        while (true) {
          k = wrappedName(k, found);
          if (k < 0 || !parser.raw(k).is(TokenKind.COMMA)) {
            break;
          }
          k++;
        }
        end = k >= 0 && parser.raw(k).is(close) ? k + 1 : -1;
        for (; end >= 0 && open > 0; open--, end++) {
          if (!parser.raw(end).is(TokenKind.RPAREN)) {
            end = -1;
          }
        }
      }
    }
    if (end < 0) {
      return null;
    }
    if (!parser.raw(end).is(TokenKind.COMMA) && !parser.raw(end).is(TokenKind.RPAREN)) {
      return null;
    }
    if (builtin.name().equals("diff")) {
      // diff(f, x, y, …): every later argument that is a name is a variable too
      for (int k = bindAt; k < starts.size(); k++) {
        List<Token> later = new ArrayList<>();
        int after = wrappedName(starts.get(k), later);
        if (after >= 0
            && (parser.raw(after).is(TokenKind.COMMA) || parser.raw(after).is(TokenKind.RPAREN))) {
          found.addAll(later);
        }
      }
    }
    boolean eiAllowed =
        switch (builtin.eiBinding()) {
          case YES -> true;
          case NO -> false;
          case WITH_BOUNDS -> count == 4;
        };
    List<String> out = new ArrayList<>();
    for (Token t : found) {
      out.add(Parser.boundName(t, t.text(), eiAllowed));
    }
    return out;
  }

  /**
   * Reads ahead a name at {@code k}, possibly signed with {@code +} or parenthesised, which parse
   * to the name itself; adds its token to {@code found} and returns the index after it, or -1.
   */
  private int wrappedName(int k, List<Token> found) {
    int open = 0;
    while (parser.raw(k).is(TokenKind.PLUS) || parser.raw(k).is(TokenKind.LPAREN)) {
      if (parser.raw(k).is(TokenKind.LPAREN)) {
        open++;
      }
      k++;
    }
    Token name = parser.raw(k++);
    if (!name.is(TokenKind.IDENT)
        && !name.is(TokenKind.PI)
        && !name.is(TokenKind.INFINITY)
        && !name.is(TokenKind.DOMAIN)) {
      return -1;
    }
    for (; open > 0; open--, k++) {
      if (!parser.raw(k).is(TokenKind.RPAREN)) {
        return -1;
      }
    }
    found.add(name);
    return k;
  }
}

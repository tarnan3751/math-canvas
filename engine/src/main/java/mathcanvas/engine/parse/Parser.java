package mathcanvas.engine.parse;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.builtin.Builtin;
import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.AsciiPrinter;

/**
 * The parser of {@code docs/language.md}: source text to raw expression trees, exactly as typed,
 * with a position in every error. It is a Pratt parser (Vaughan R. Pratt, "Top Down Operator
 * Precedence", POPL 1973): each token kind has a prefix action (a nud) and, where it can follow an
 * operand, a binding power and an infix action (a led), and {@code parse(rbp)} takes infix actions
 * while their binding power exceeds {@code rbp}. The powers realise the table of §4.1:
 *
 * <ul>
 *   <li>relations 20 (non-associative), {@code + -} 30, {@code * / ×} and implicit multiplication
 *       40, {@code ^} 60 (its right operand is parsed at 59, so it is right-associative and admits
 *       a sign or a prefix application), postfix {@code ! % °} and superscripts 70;
 *   <li>a sign, a prefix function name and a radical take the next unary expression, parsed at 50:
 *       it includes {@code ^} and postfix operators and stops before {@code *};
 *   <li>a big operator ({@code d/dx}, {@code lim[…]}, {@code Σ[…]}, {@code Π[…]}) takes the rest of
 *       the term, parsed at 39; an integral takes an additive integrand, parsed at 29, up to its
 *       differential; a lambda's body is an expression.
 * </ul>
 *
 * <p>A chain of {@code +} and {@code -}, or of {@code *}, {@code /} and implicit multiplication, is
 * built in a loop as one flat {@code Sum} or {@code Product} in typed order, with {@code a - b} as
 * {@code Exprs.neg(b)} and {@code a / b} as {@code Exprs.inv(b)}; parentheses nest. So recursion
 * happens only per level of nesting, and the parser rejects more than {@link #MAX_NESTING} levels,
 * counted both as open constructs and as the depth of the tree built, every {@code ×} application
 * included ("expression nested too deeply"). That keeps every later recursive walk of the tree
 * ({@code hashCode}, the canonical form, the printers) within a 1 MB thread stack, the smallest
 * default of the supported platforms.
 *
 * <p>Parsing is total: any text gives a result or a {@link ParseException}. Errors come in text
 * order: a lexical error stops the token stream where it stands and is raised when the parser
 * reaches it, and a binder's variable that is read ahead and refused is reported only once the text
 * before it has parsed.
 *
 * <p>A statement is parsed with the user-defined functions known at that point: those of the {@link
 * Names} given, which the parser consults per statement, overlaid by the script's own definitions
 * before it ({@code f(x) := …} and {@code f := x -> …} define a function, {@code f := 3} a value,
 * and a top-level {@code clear(f)} removes it). A runner that evaluates statement by statement
 * passes a {@code Names} that reflects its environment.
 *
 * <p>Positions: each statement records the position of its first token, and the parser keeps a side
 * table, identity-keyed, from each node it builds to the position of the node's first token, which
 * {@link Script#positionOf} answers. A node met at two places (a shared constant) gets none.
 */
public final class Parser {

  /** The deepest nesting accepted. */
  public static final int MAX_NESTING = 200;

  private static final String OPERAND = "expected an operand";
  private static final String PARAMETERS = "expected distinct parameter names";

  private final Lexer lexer;
  private final Names names;
  private final List<Token> tokens = new ArrayList<>();
  private ParseException lexical;
  private int pos;

  private final Map<String, Boolean> defined = new HashMap<>();
  private final IdentityHashMap<Expr, Position> positions = new IdentityHashMap<>();
  private final Deque<List<String>> scopes = new ArrayDeque<>();

  /** The unknown functions a {@code dsolve} or {@code odesolve} call around declares (§14.22). */
  private final Deque<List<String>> unknowns = new ArrayDeque<>();

  private final Deque<int[]> bars = new ArrayDeque<>();
  private final Deque<Integer> integrals = new ArrayDeque<>();
  private int brackets;
  private int limTarget = -1;
  private int nesting;

  /** Whether the next nud begins the operand of a prefix operator or of {@code ^}. */
  private boolean atPrefixOperand;

  /** A parsed subtree: its expression, its depth, and whether it is a bare integral. */
  private record P(Expr e, int depth, boolean integral) {}

  private Parser(String source, Names names) {
    this.lexer = new Lexer(source);
    this.names = names;
    bars.push(new int[1]);
  }

  /**
   * Parses the whole input as one expression; surrounding newlines are allowed.
   *
   * @param source the text
   * @param names the user-defined functions
   * @return the raw tree
   * @throws ParseException if the text is not one expression
   */
  public static Expr parseExpression(String source, Names names) {
    Parser p = new Parser(source, names);
    p.skipNewlines();
    Expr e = p.expr().e();
    p.skipNewlines();
    Token t = p.peek();
    if (!t.is(TokenKind.EOF)) {
      throw p.unexpected(t);
    }
    return e;
  }

  /**
   * Parses a script: statements ended by {@code ;}, a newline outside brackets, or the end.
   *
   * @param source the text
   * @param names the user-defined functions before the script
   * @return the statements
   * @throws ParseException at the first error
   */
  public static Script parseScript(String source, Names names) {
    Parser p = new Parser(source, names);
    List<Statement> out = new ArrayList<>();
    while (true) {
      Token t = p.peek();
      if (t.is(TokenKind.EOF)) {
        return new Script(out, p.positions);
      }
      if (t.is(TokenKind.SEMI) || t.is(TokenKind.NEWLINE)) {
        p.next();
      } else {
        out.add(p.statement());
      }
    }
  }

  // Tokens.

  private Token raw(int ahead) {
    while (tokens.size() <= pos + ahead) {
      if (lexical == null) {
        try {
          tokens.add(lexer.next());
          continue;
        } catch (ParseException e) {
          lexical = e;
        }
      }
      tokens.add(
          new Token(
              TokenKind.ERROR,
              lexical.getMessage(),
              lexical.line(),
              lexical.column(),
              lexical.offset(),
              null));
    }
    return tokens.get(pos + ahead);
  }

  private Token peek(int ahead) {
    Token t = raw(ahead);
    if (t.is(TokenKind.ERROR)) {
      throw lexical;
    }
    return t;
  }

  private Token peek() {
    return peek(0);
  }

  private Token next() {
    Token t = peek();
    pos++;
    return t;
  }

  private void skipNewlines() {
    while (peek().is(TokenKind.NEWLINE)) {
      next();
    }
  }

  private void expect(TokenKind kind, String text) {
    Token t = peek();
    if (!t.is(kind)) {
      throw t.is(TokenKind.SEMI) ? error("unexpected `;`", t) : error("expected `" + text + "`", t);
    }
    next();
  }

  private static ParseException error(String message, Token at) {
    return new ParseException(message, at);
  }

  private ParseException unexpected(Token t) {
    if (t.is(TokenKind.MINUTE) || t.is(TokenKind.SECOND)) {
      return error("`" + t.text() + "` follows a number of degrees, as in 30°15′20″", t);
    }
    return error(
        t.is(TokenKind.NEWLINE) ? "unexpected newline" : "unexpected `" + t.text() + "`", t);
  }

  // Positions.

  /**
   * Records that a node was typed starting at a token. A node already recorded at another place is
   * shared between places, and loses its position.
   */
  private void note(Expr e, Token first) {
    Position here = Position.of(first);
    if (!positions.containsKey(e)) {
      positions.put(e, here);
    } else if (!here.equals(positions.get(e))) {
      positions.put(e, null);
    }
  }

  // Nesting.

  private void enter(Token at) {
    if (++nesting > MAX_NESTING) {
      throw error("expression nested too deeply", at);
    }
  }

  private void leave() {
    nesting--;
  }

  private static P node(Expr e, int depth, Token at) {
    checkDepth(depth, at);
    return new P(e, depth, false);
  }

  private static void checkDepth(int depth, Token at) {
    if (depth > MAX_NESTING) {
      throw error("expression nested too deeply", at);
    }
  }

  private void openBracket() {
    brackets++;
    bars.push(new int[1]);
  }

  private void closeBracket() {
    brackets--;
    bars.pop();
  }

  // Statements.

  private Statement statement() {
    Statement.Definition definition = null;
    Expr expr = null;
    Token t0 = raw(0);
    Token t1 = raw(1);
    Position start = Position.of(t0);
    if (t1.is(TokenKind.DEFINE)
        && (t0.is(TokenKind.IDENT)
            || t0.is(TokenKind.PI)
            || t0.is(TokenKind.INFINITY)
            || t0.is(TokenKind.DOMAIN))) {
      definition = valueDefinition(start);
    } else if (t0.is(TokenKind.IDENT) && t1.is(TokenKind.LPAREN) && functionHeadAhead()) {
      definition = functionDefinition(start);
    } else {
      expr = expr().e();
    }
    boolean quiet = false;
    Token end = peek();
    if (end.is(TokenKind.SEMI)) {
      quiet = true;
      next();
    } else if (end.is(TokenKind.NEWLINE)) {
      next();
    } else if (!end.is(TokenKind.EOF)) {
      throw unexpected(end);
    }
    if (definition != null) {
      return new Statement.Definition(
          definition.name(), definition.params(), definition.body(), quiet, start);
    }
    if (expr instanceof Apply a
        && a.head().name().equals("clear")
        && a.args().size() == 1
        && a.args().getFirst() instanceof Symbol s) {
      defined.put(s.name(), false);
    }
    return new Statement.Expression(expr, quiet, start);
  }

  /** Whether the tokens ahead are {@code name(…) :=}. */
  private boolean functionHeadAhead() {
    int depth = 0;
    for (int k = 1; ; k++) {
      Token t = raw(k);
      switch (t.kind()) {
        case ERROR, EOF, SEMI, NEWLINE -> {
          return false;
        }
        case LPAREN -> depth++;
        case RPAREN -> {
          if (--depth == 0) {
            return raw(k + 1).is(TokenKind.DEFINE);
          }
        }
        default -> {}
      }
    }
  }

  private Statement.Definition valueDefinition(Position start) {
    Token nameToken = next();
    String name = definedName(nameToken);
    next();
    if (lambdaAhead()) {
      defined.put(name, true);
    }
    Expr body = expr().e();
    defined.put(name, body instanceof Lambda);
    return new Statement.Definition(Symbol.of(name), List.of(), body, false, start);
  }

  private Statement.Definition functionDefinition(Position start) {
    Token nameToken = next();
    String name = definedName(nameToken);
    next();
    List<String> params = new ArrayList<>();
    while (true) {
      Token p = next();
      if (!p.is(TokenKind.IDENT)) {
        throw error(PARAMETERS, p);
      }
      String param = boundName(p, p.text(), true);
      if (params.contains(param)) {
        throw error(PARAMETERS, p);
      }
      params.add(param);
      Token sep = next();
      if (sep.is(TokenKind.RPAREN)) {
        break;
      }
      if (!sep.is(TokenKind.COMMA)) {
        throw error(PARAMETERS, sep);
      }
    }
    next();
    defined.put(name, true);
    scopes.push(params);
    Expr body = expr().e();
    scopes.pop();
    return new Statement.Definition(
        Symbol.of(name), params.stream().map(Symbol::of).toList(), body, false, start);
  }

  /** The name a definition defines, refusing a reserved one. */
  private static String definedName(Token t) {
    String name = t.text();
    if (t.is(TokenKind.PI) || t.is(TokenKind.INFINITY) || Builtins.isConstant(name)) {
      throw error("`" + name + "` is a constant", t);
    }
    return boundName(t, name, true);
  }

  /**
   * A name that a binder, a parameter or a definition introduces, refusing constants (other than
   * {@code e} and {@code i} where {@code eiAllowed}), domains, builtins and keywords.
   */
  private static String boundName(Token at, String name, boolean eiAllowed) {
    if (at.is(TokenKind.PI) || at.is(TokenKind.INFINITY)) {
      throw error("`" + name + "` is a constant", at);
    }
    if (at.is(TokenKind.DOMAIN) || Builtins.isDomain(name)) {
      throw error("`" + name + "` is a domain", at);
    }
    if (!at.is(TokenKind.IDENT) && !at.is(TokenKind.DERIVATIVE)) {
      throw error("expected a name", at);
    }
    if (Builtins.isKeyword(name) || name.equals("in")) {
      throw error("`" + name + "` is reserved", at);
    }
    if (name.equals("e") || name.equals("i")) {
      if (!eiAllowed) {
        throw error("`" + name + "` cannot be the variable here", at);
      }
      return name;
    }
    if (Builtins.isConstant(name)) {
      throw error("`" + name + "` is a constant", at);
    }
    Optional<Builtin> builtin = Builtins.lookup(name);
    if (builtin.isPresent()) {
      throw error(
          "`" + name + "` is " + (builtin.get().isCallable() ? "a builtin" : "reserved"), at);
    }
    return name;
  }

  // Expressions.

  private P expr() {
    Token start = peek();
    List<String> params = lambdaParams();
    if (params != null) {
      Token arrow = tokens.get(pos - 1);
      enter(arrow);
      scopes.push(params);
      P body = expr();
      scopes.pop();
      leave();
      P lambda =
          node(
              Lambda.of(params.stream().map(Symbol::of).toList(), body.e()),
              body.depth() + 1,
              arrow);
      note(lambda.e(), start);
      return lambda;
    }
    P e = parse(0);
    if (peek().is(TokenKind.ARROW)) {
      throw error("lambda parameters must be names", start);
    }
    return e;
  }

  private boolean lambdaAhead() {
    if (raw(0).is(TokenKind.IDENT)) {
      return raw(1).is(TokenKind.ARROW);
    }
    return lambdaListEnd() > 0;
  }

  /** For {@code ( name {, name} ) ->} ahead, the index of the arrow; otherwise -1. */
  private int lambdaListEnd() {
    if (!raw(0).is(TokenKind.LPAREN)) {
      return -1;
    }
    for (int k = 1; ; k += 2) {
      if (!raw(k).is(TokenKind.IDENT)) {
        return -1;
      }
      Token sep = raw(k + 1);
      if (sep.is(TokenKind.RPAREN)) {
        return raw(k + 2).is(TokenKind.ARROW) ? k + 2 : -1;
      }
      if (!sep.is(TokenKind.COMMA)) {
        return -1;
      }
    }
  }

  /** The parameters of a lambda ahead, consumed through its arrow; null when there is none. */
  private List<String> lambdaParams() {
    List<Token> found = new ArrayList<>();
    int arrow;
    if (raw(0).is(TokenKind.IDENT) && raw(1).is(TokenKind.ARROW)) {
      found.add(raw(0));
      arrow = 1;
    } else {
      arrow = lambdaListEnd();
      if (arrow < 0) {
        return null;
      }
      for (int k = 1; k < arrow; k += 2) {
        found.add(raw(k));
      }
    }
    List<String> params = new ArrayList<>();
    for (Token t : found) {
      String name = boundName(t, t.text(), true);
      if (params.contains(name)) {
        throw error(PARAMETERS, t);
      }
      params.add(name);
    }
    pos += arrow + 1;
    return params;
  }

  private P parse(int rbp) {
    Token first = peek();
    P left = nud();
    // A parenthesised operand and a unary plus give the node inside, already recorded.
    if (!first.is(TokenKind.LPAREN) && !first.is(TokenKind.PLUS)) {
      note(left.e(), first);
    }
    while (true) {
      Token t = peek();
      if (isDifferential(t)) {
        return left;
      }
      TokenKind k = t.kind();
      if (k == TokenKind.DEFINE) {
        throw error("`:=` defines at statement level only", t);
      }
      if (left.integral() && (k == TokenKind.CARET || isPostfix(k))) {
        throw error("parenthesise the integral", t);
      }
      if (isPostfix(k)) {
        next();
        left = postfix(left, t);
      } else if (k == TokenKind.CARET) {
        if (rbp >= 60) {
          return left;
        }
        next();
        enter(t);
        atPrefixOperand = true;
        P right = parse(59);
        leave();
        left = node(Power.of(left.e(), right.e()), Math.max(left.depth(), right.depth()) + 1, t);
      } else if (k == TokenKind.STAR
          || k == TokenKind.SLASH
          || k == TokenKind.CROSS
          || startsImplicitFactor(t)) {
        if (rbp >= 40) {
          return left;
        }
        left = term(left, t, first);
      } else if (k == TokenKind.PLUS || k == TokenKind.MINUS) {
        if (rbp >= 30 || limDirection()) {
          return left;
        }
        left = additive(left, t);
      } else if (isRelation(k)) {
        if (rbp >= 20) {
          return left;
        }
        left = relation(left);
      } else {
        return left;
      }
      note(left.e(), first);
    }
  }

  private P term(P left, Token start, Token first) {
    List<Expr> factors = new ArrayList<>(List.of(left.e()));
    int depth = left.depth();
    while (true) {
      Token t = peek();
      if (isDifferential(t)) {
        break;
      }
      TokenKind k = t.kind();
      if (k == TokenKind.STAR || k == TokenKind.SLASH || k == TokenKind.CROSS) {
        next();
        P right = parse(40);
        if (k == TokenKind.STAR) {
          factors.add(right.e());
          depth = Math.max(depth, right.depth());
        } else if (k == TokenKind.SLASH) {
          Expr inverse = Exprs.inv(right.e());
          note(inverse, t);
          factors.add(inverse);
          depth = Math.max(depth, right.depth() + 1);
        } else {
          Expr acc = factors.size() == 1 ? factors.getFirst() : Product.of(factors);
          int accDepth = factors.size() == 1 ? depth : depth + 1;
          depth = Math.max(accDepth, right.depth()) + 1;
          checkDepth(depth, t);
          Expr cross = Apply.of(Symbol.of("cross"), acc, right.e());
          note(cross, first);
          factors = new ArrayList<>(List.of(cross));
        }
      } else if (startsImplicitFactor(t)) {
        if (k == TokenKind.INT || k == TokenKind.DECIMAL) {
          throw error("a number cannot follow a factor; write 2x or x*2", t);
        }
        if (k == TokenKind.LBRACKET) {
          throw error("indexing is reserved; use nth(v, 1)", t);
        }
        P right = parse(40);
        factors.add(right.e());
        depth = Math.max(depth, right.depth());
      } else {
        break;
      }
    }
    return factors.size() == 1
        ? new P(factors.getFirst(), depth, false)
        : node(Product.of(factors), depth + 1, start);
  }

  private P additive(P left, Token start) {
    List<Expr> terms = new ArrayList<>(List.of(left.e()));
    int depth = left.depth();
    while (true) {
      Token t = peek();
      if (!(t.is(TokenKind.PLUS) || t.is(TokenKind.MINUS)) || limDirection()) {
        break;
      }
      next();
      P right = parse(30);
      if (t.is(TokenKind.MINUS)) {
        Expr negated = Exprs.neg(right.e());
        note(negated, t);
        terms.add(negated);
        depth = Math.max(depth, right.depth() + 1);
      } else {
        terms.add(right.e());
        depth = Math.max(depth, right.depth());
      }
    }
    return node(Sum.of(terms), depth + 1, start);
  }

  private P relation(P left) {
    Token t = next();
    P right = parse(20);
    Token after = peek();
    if (isRelation(after.kind())) {
      throw error("chained relations are not supported", after);
    }
    Relation.Op op =
        switch (t.kind()) {
          case EQ -> Relation.Op.EQ;
          case NE -> Relation.Op.NE;
          case LT -> Relation.Op.LT;
          case LE -> Relation.Op.LE;
          case GT -> Relation.Op.GT;
          case GE -> Relation.Op.GE;
          default -> Relation.Op.IN;
        };
    return node(Relation.of(op, left.e(), right.e()), Math.max(left.depth(), right.depth()) + 1, t);
  }

  private P postfix(P left, Token t) {
    if (t.is(TokenKind.DEGREE) && left.e() instanceof Num && minutesAhead()) {
      return degreesMinutesSeconds(left, t);
    }
    Expr e =
        switch (t.kind()) {
          case BANG -> Apply.of(Symbol.of("factorial"), left.e());
          case PERCENT -> Product.of(left.e(), Exprs.inv(Num.of(100)));
          case DEGREE -> Product.of(left.e(), Constant.PI, Exprs.inv(Num.of(180)));
          default -> Power.of(left.e(), Num.of(Int.of(Numerals.integer(t.text()))));
        };
    int added = t.is(TokenKind.PERCENT) || t.is(TokenKind.DEGREE) ? 2 : 1;
    return node(e, left.depth() + added, t);
  }

  /** Whether a number and {@code ′} follow, the minutes of {@code d°m′s″}. */
  private boolean minutesAhead() {
    return (peek().is(TokenKind.INT) || peek().is(TokenKind.DECIMAL))
        && peek(1).is(TokenKind.MINUTE);
  }

  /**
   * A degrees-minutes-seconds literal {@code d°m′} or {@code d°m′s″}, whose degrees {@code left}
   * and {@code °} are read: {@code (d + m/60 + s/3600)·π/180}.
   */
  private P degreesMinutesSeconds(P left, Token degree) {
    List<Expr> parts = new ArrayList<>();
    parts.add(left.e());
    parts.add(Product.of(nud().e(), Exprs.inv(Num.of(60))));
    next();
    if ((peek().is(TokenKind.INT) || peek().is(TokenKind.DECIMAL))
        && peek(1).is(TokenKind.SECOND)) {
      parts.add(Product.of(nud().e(), Exprs.inv(Num.of(3600))));
      next();
    }
    Expr e = Product.of(Sum.of(parts), Constant.PI, Exprs.inv(Num.of(180)));
    return node(e, left.depth() + 3, degree);
  }

  private static boolean isPostfix(TokenKind k) {
    return k == TokenKind.BANG
        || k == TokenKind.PERCENT
        || k == TokenKind.DEGREE
        || k == TokenKind.SUPERSCRIPT;
  }

  private static boolean isRelation(TokenKind k) {
    return switch (k) {
      case EQ, NE, LT, LE, GT, GE, IN -> true;
      default -> false;
    };
  }

  /** Whether a token can start a unary expression (§4.2). */
  private boolean canStartUnary(Token t) {
    if (isDifferential(t)) {
      return false;
    }
    return switch (t.kind()) {
      case INT,
          DECIMAL,
          IDENT,
          PI,
          INFINITY,
          DOMAIN,
          PLUS,
          MINUS,
          SQRT,
          CBRT,
          QRT,
          LPAREN,
          LBRACKET,
          LBRACE,
          LANGLE,
          INTEGRAL,
          SIGMA,
          CAPITAL_PI,
          DERIVATIVE ->
          true;
      case BAR -> bars.peek()[0] == 0;
      default -> false;
    };
  }

  /**
   * Whether a token after an operand starts an implicit factor. Numbers and {@code [} are included,
   * to be refused with their own messages (§9.2).
   */
  private boolean startsImplicitFactor(Token t) {
    return !t.is(TokenKind.PLUS) && !t.is(TokenKind.MINUS) && canStartUnary(t);
  }

  /** Whether the token is the {@code +} or {@code -} closing a limit's target: {@code 0+]}. */
  private boolean limDirection() {
    return limTarget == brackets && peek(1).is(TokenKind.RBRACKET);
  }

  /** Whether the token is a differential ending the innermost integral's integrand (§10.2). */
  private boolean isDifferential(Token t) {
    return !integrals.isEmpty()
        && integrals.peek() == brackets
        && t.is(TokenKind.IDENT)
        && isDName(t.text());
  }

  /** Whether a name is a differential: {@code d} followed by a name, the whole not a builtin. */
  private static boolean isDName(String name) {
    return name.length() > 1
        && name.startsWith("d")
        && Lexer.isIdentStart(name.codePointAt(1))
        && Builtins.lookup(name).isEmpty();
  }

  private boolean bound(String name) {
    for (List<String> scope : scopes) {
      if (scope.contains(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isUserFunction(String name) {
    for (List<String> declared : unknowns) {
      if (declared.contains(name)) {
        return true;
      }
    }
    Boolean local = defined.get(name);
    return local != null ? local : names.isUserFunction(name);
  }

  // Prefix actions.

  private P nud() {
    boolean prefixOperand = atPrefixOperand;
    atPrefixOperand = false;
    Token t = peek();
    if (isDifferential(t)) {
      throw error(OPERAND, t);
    }
    return switch (t.kind()) {
      case INT -> {
        next();
        yield new P(Num.of(Int.of(Numerals.integer(t.text()))), 0, false);
      }
      case DECIMAL -> {
        next();
        try {
          yield new P(Num.of(new Real(Numerals.decimal(t.text()))), 0, false);
        } catch (NumberFormatException | ArithmeticException e) {
          throw error("number out of range", t);
        }
      }
      case IDENT -> name();
      case PI -> {
        next();
        yield new P(Constant.PI, 0, false);
      }
      case INFINITY -> {
        next();
        yield new P(Constant.INFINITY, 0, false);
      }
      case DOMAIN -> {
        next();
        yield new P(domain(t.text()), 0, false);
      }
      case MINUS, PLUS, SQRT, CBRT, QRT -> {
        next();
        P operand = unary(t);
        Expr e =
            switch (t.kind()) {
              case MINUS -> Exprs.neg(operand.e());
              case SQRT -> Exprs.sqrt(operand.e());
              case CBRT -> Apply.of(Symbol.of("cbrt"), operand.e());
              case QRT -> Apply.of(Symbol.of("root"), operand.e(), Num.of(4));
              default -> null;
            };
        yield e == null
            ? new P(operand.e(), operand.depth(), false)
            : node(e, operand.depth() + 1, t);
      }
      case LPAREN -> parenthesised(prefixOperand);
      case LBRACKET, LBRACE, LANGLE -> collection();
      case BAR -> {
        if (bars.peek()[0] > 0) {
          throw error(OPERAND, t);
        }
        yield absoluteValue();
      }
      case INTEGRAL -> integral();
      case SIGMA, CAPITAL_PI -> {
        next();
        yield bigOperator(t, t.is(TokenKind.SIGMA));
      }
      case DERIVATIVE -> derivative();
      case SEMI -> throw error(brackets > 0 ? "unexpected `;`" : OPERAND, t);
      default -> throw error(OPERAND, t);
    };
  }

  /** The next unary expression, the operand of a sign, a radical or a prefix name. */
  private P unary(Token operator) {
    enter(operator);
    atPrefixOperand = true;
    P result = parse(50);
    leave();
    return result;
  }

  private static Constant domain(String symbol) {
    return switch (symbol) {
      case "ℤ", "Integers" -> Constant.INTEGERS;
      case "ℚ", "Rationals" -> Constant.RATIONALS;
      case "ℝ", "Reals" -> Constant.REALS;
      default -> Constant.COMPLEXES;
    };
  }

  private P name() {
    Token t = next();
    String name = t.text();
    if (Builtins.isKeyword(name)) {
      throw error("`" + name + "` is reserved", t);
    }
    if (name.equals("mod") && !peek().is(TokenKind.LPAREN)) {
      throw error("write mod(a, b)", t);
    }
    if (name.equals("lim")) {
      return limit(t);
    }
    if (name.endsWith("'")) {
      return primed(t);
    }
    if (bound(name)) {
      return new P(Symbol.of(name), 0, false);
    }
    if (Builtins.isConstant(name)) {
      Expr constant =
          switch (name) {
            case "pi" -> Constant.PI;
            case "e" -> Constant.E;
            case "i" -> Num.of(Complex.I);
            case "inf", "infinity" -> Constant.INFINITY;
            case "true" -> Bool.TRUE;
            case "false" -> Bool.FALSE;
            default -> Constant.UNDEFINED;
          };
      return new P(constant, 0, false);
    }
    if (Builtins.isDomain(name)) {
      return new P(domain(name), 0, false);
    }
    if ((name.equals("sum") || name.equals("product")) && peek().is(TokenKind.LBRACKET)) {
      return bigOperator(t, name.equals("sum"));
    }
    Optional<Builtin> builtin = Builtins.lookup(name);
    boolean user = builtin.isEmpty() && isUserFunction(name);
    if (!user && builtin.isEmpty()) {
      return new P(Symbol.of(name), 0, false);
    }
    Token after = peek();
    if (after.is(TokenKind.LPAREN)) {
      return call(t, builtin.orElse(null));
    }
    if (after.is(TokenKind.CARET) || after.is(TokenKind.SUPERSCRIPT)) {
      String hint = "write " + name + "(x)²";
      if (Builtins.lookup("a" + name).isPresent()) {
        hint += " or a" + name + "(x)";
      }
      throw error(hint, t);
    }
    if (builtin.isPresent() && builtin.get().arities().equals(List.of(0))) {
      throw error("write " + name + "()", t);
    }
    if (builtin.isPresent() && builtin.get().prefix() && canStartUnary(after)) {
      P operand = unary(t);
      Expr e =
          name.equals("sqrt")
              ? Exprs.sqrt(operand.e())
              : Apply.of(Symbol.of(builtin.get().name()), operand.e());
      return node(e, operand.depth() + 1, t);
    }
    boolean operand =
        canStartUnary(after) && !after.is(TokenKind.PLUS) && !after.is(TokenKind.MINUS);
    throw error(name + (operand ? " needs parentheses" : " needs an argument"), t);
  }

  /**
   * The prime notation (§5): a name with primes, which must be called, {@code y'(x)}. The call's
   * head is the primed name; the base must not be a builtin.
   */
  private P primed(Token t) {
    String name = t.text();
    String base = name.substring(0, name.indexOf('\''));
    if (Builtins.lookup(base).isPresent()
        || Builtins.isConstant(base)
        || Builtins.isDomain(base)
        || Builtins.isKeyword(base)
        || base.equals("lim")) {
      throw error("the prime notation is for user functions: write diff(" + base + "(x), x)", t);
    }
    if (!peek().is(TokenKind.LPAREN)) {
      throw error(name + " needs an argument", t);
    }
    return call(t, null);
  }

  private P call(Token nameToken, Builtin builtin) {
    String name = nameToken.text();
    String head = builtin != null ? builtin.name() : name;
    Token open = next();
    enter(open);
    openBracket();
    // The variable is read ahead so that it is bound in the arguments before it; an error in it is
    // raised only when its argument is reached, after any error in those.
    List<String> bound = null;
    ParseException pending = null;
    bindAt = 2;
    if (builtin != null && builtin.binder().isPresent()) {
      try {
        bound = binderNames(builtin);
      } catch (ParseException e) {
        pending = e;
      }
    }
    // Phase 7b: dsolve and odesolve declare their unknown functions for the whole call and bind
    // the independent variable in the equations and the unknowns
    List<String> declared = null;
    if (builtin != null && UNKNOWN_COMMANDS.contains(head)) {
      List<String> found = declaredUnknowns();
      if (found != null) {
        declared = found.subList(1, found.size());
        bound = List.of(found.getFirst());
        unknowns.push(declared);
      }
    }
    // an inner call's read-ahead sets bindAt again, so this call keeps its own
    int at = bindAt;
    boolean namesOnly = head.equals("clear") || head.equals("help");
    List<Expr> args = new ArrayList<>();
    Limit.Direction direction = null;
    int depth = 0;
    if (!peek().is(TokenKind.RPAREN)) {
      for (int index = 1; ; index++) {
        if (bound != null && index == 1) {
          scopes.push(bound);
        }
        if (pending != null && index == at) {
          throw pending;
        }
        Token first = peek();
        boolean single = first.is(TokenKind.IDENT) && peek(1).is(TokenKind.RPAREN);
        if (namesOnly
            && single
            && (Builtins.lookup(first.text()).isPresent() || isUserFunction(first.text()))) {
          next();
          Symbol named = Symbol.of(first.text());
          note(named, first);
          args.add(named);
        } else if (head.equals("limit")
            && index == 4
            && single
            && (first.text().equals("left") || first.text().equals("right"))) {
          next();
          direction = first.text().equals("left") ? Limit.Direction.LEFT : Limit.Direction.RIGHT;
          args.add(Symbol.of(first.text()));
        } else {
          P arg = expr();
          args.add(arg.e());
          depth = Math.max(depth, arg.depth());
        }
        if (bound != null && (index == at || (index < at && !peek().is(TokenKind.COMMA)))) {
          scopes.pop();
        }
        if (!peek().is(TokenKind.COMMA)) {
          break;
        }
        next();
      }
    }
    expect(TokenKind.RPAREN, ")");
    if (declared != null) {
      unknowns.pop();
    }
    closeBracket();
    leave();
    if (builtin != null && !builtin.accepts(args.size())) {
      throw error(name + " takes " + builtin.arityText(), nameToken);
    }
    return node(TypedNodes.of(head, args, direction), depth + 1, open);
  }

  /** The commands whose second argument declares unknown functions (Phase 7b). */
  private static final Set<String> UNKNOWN_COMMANDS = Set.of("dsolve", "odesolve");

  /**
   * The unknowns of a {@code dsolve} or {@code odesolve} call being read, read ahead from its
   * second argument: {@code y(x)} or {@code [x(t), y(t), …]}, one variable throughout, the function
   * names neither builtins nor the variable. Gives the variable then the function names, or null
   * when the argument has another shape (the evaluator then reports it); sets {@link #bindAt} to 2.
   */
  private List<String> declaredUnknowns() {
    bindAt = 2;
    int depth = 0;
    int start = -1;
    for (int k = 0; ; k++) {
      TokenKind kind = raw(k).kind();
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
    boolean list = raw(k).is(TokenKind.LBRACKET) || raw(k).is(TokenKind.LANGLE);
    TokenKind close = raw(k).is(TokenKind.LANGLE) ? TokenKind.RANGLE : TokenKind.RBRACKET;
    if (list) {
      k++;
    }
    List<String> out = new ArrayList<>();
    out.add(null);
    while (true) {
      Token f = raw(k);
      Token v = raw(k + 2);
      if (!f.is(TokenKind.IDENT)
          || !raw(k + 1).is(TokenKind.LPAREN)
          || !v.is(TokenKind.IDENT)
          || !raw(k + 3).is(TokenKind.RPAREN)) {
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
      if (list && raw(k).is(TokenKind.COMMA)) {
        k++;
        continue;
      }
      break;
    }
    if (list) {
      if (!raw(k).is(close)) {
        return null;
      }
      k++;
    }
    return raw(k).is(TokenKind.COMMA) || raw(k).is(TokenKind.RPAREN) ? out : null;
  }

  /**
   * The 1-based position of the argument naming the variable of the call being read: 2 for most
   * binder commands, 3 for {@code area(f, g, x, a, b)} and {@code volume(f, g, x, a, b)} ({@link
   * Builtin#binderFor}); set by {@link #binderNames}.
   */
  private int bindAt = 2;

  /**
   * The names a binder command binds in the arguments before its variable, read ahead from the
   * variable's argument ({@link Builtin#binderFor} of the count of arguments, setting {@link
   * #bindAt}) when that is a name or, for {@code solve}, a list of names in {@code [ ]} or {@code ⟨
   * ⟩}; null otherwise.
   */
  private List<String> binderNames(Builtin builtin) {
    int depth = 0;
    int count = 1;
    List<Integer> starts = new ArrayList<>(List.of(0));
    for (int k = 0; ; k++) {
      Token t = raw(k);
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
    if (end < 0 && builtin.name().equals("solve")) {
      int k = second;
      int open = 0;
      while (raw(k).is(TokenKind.PLUS) || raw(k).is(TokenKind.LPAREN)) {
        open += raw(k++).is(TokenKind.LPAREN) ? 1 : 0;
      }
      if (raw(k).is(TokenKind.LBRACKET) || raw(k).is(TokenKind.LANGLE)) {
        TokenKind close = raw(k).is(TokenKind.LBRACKET) ? TokenKind.RBRACKET : TokenKind.RANGLE;
        k++;
        while (true) {
          k = wrappedName(k, found);
          if (k < 0 || !raw(k).is(TokenKind.COMMA)) {
            break;
          }
          k++;
        }
        end = k >= 0 && raw(k).is(close) ? k + 1 : -1;
        for (; end >= 0 && open > 0; open--, end++) {
          if (!raw(end).is(TokenKind.RPAREN)) {
            end = -1;
          }
        }
      }
    }
    if (end < 0) {
      return null;
    }
    if (!raw(end).is(TokenKind.COMMA) && !raw(end).is(TokenKind.RPAREN)) {
      return null;
    }
    boolean eiAllowed =
        switch (builtin.eiBinding()) {
          case YES -> true;
          case NO -> false;
          case WITH_BOUNDS -> count == 4;
        };
    List<String> out = new ArrayList<>();
    for (Token t : found) {
      out.add(boundName(t, t.text(), eiAllowed));
    }
    return out;
  }

  /**
   * Reads ahead a name at {@code k}, possibly signed with {@code +} or parenthesised, which parse
   * to the name itself; adds its token to {@code found} and returns the index after it, or -1.
   */
  private int wrappedName(int k, List<Token> found) {
    int open = 0;
    while (raw(k).is(TokenKind.PLUS) || raw(k).is(TokenKind.LPAREN)) {
      if (raw(k).is(TokenKind.LPAREN)) {
        open++;
      }
      k++;
    }
    Token name = raw(k++);
    if (!name.is(TokenKind.IDENT)
        && !name.is(TokenKind.PI)
        && !name.is(TokenKind.INFINITY)
        && !name.is(TokenKind.DOMAIN)) {
      return -1;
    }
    for (; open > 0; open--, k++) {
      if (!raw(k).is(TokenKind.RPAREN)) {
        return -1;
      }
    }
    found.add(name);
    return k;
  }

  /**
   * A parenthesised expression. The parentheses count as a level of nesting, except directly around
   * the operand of a prefix operator or of {@code ^}, where the printers write them ({@code -(-x)},
   * {@code √(√x)}, {@code 2^(2^x)}): there they add no level, so whatever the parser accepted
   * echoes to text it accepts again.
   */
  private P parenthesised(boolean free) {
    Token open = next();
    if (!free) {
      enter(open);
    }
    openBracket();
    if (peek().is(TokenKind.RPAREN)) {
      throw error(OPERAND, peek());
    }
    P inner = expr();
    if (peek().is(TokenKind.COMMA)) {
      throw error("lambda parameters must be names", open);
    }
    expect(TokenKind.RPAREN, ")");
    closeBracket();
    if (free) {
      return new P(inner.e(), inner.depth(), false);
    }
    leave();
    return node(inner.e(), inner.depth() + 1, open);
  }

  private P collection() {
    Token open = next();
    TokenKind close =
        switch (open.kind()) {
          case LBRACKET -> TokenKind.RBRACKET;
          case LBRACE -> TokenKind.RBRACE;
          default -> TokenKind.RANGLE;
        };
    enter(open);
    openBracket();
    List<Expr> items = new ArrayList<>();
    int depth = 0;
    if (!peek().is(close)) {
      while (true) {
        P item = expr();
        items.add(item.e());
        depth = Math.max(depth, item.depth());
        if (!peek().is(TokenKind.COMMA)) {
          break;
        }
        next();
      }
    }
    expect(close, close == TokenKind.RBRACKET ? "]" : close == TokenKind.RBRACE ? "}" : "⟩");
    closeBracket();
    leave();
    Expr e =
        switch (open.kind()) {
          case LBRACKET -> Brackets.of(items);
          case LBRACE -> SetExpr.of(items);
          default -> {
            if (items.isEmpty() || !Brackets.allScalar(items)) {
              throw error("a vector needs scalar elements", open);
            }
            yield Vector.of(items);
          }
        };
    return node(e, depth + 1, open);
  }

  private P absoluteValue() {
    Token open = next();
    enter(open);
    bars.peek()[0]++;
    if (peek().is(TokenKind.BAR)) {
      throw error("empty absolute value", peek());
    }
    P inner = parse(29);
    if (!peek().is(TokenKind.BAR)) {
      throw error("expected `|`", peek());
    }
    next();
    bars.peek()[0]--;
    leave();
    return node(Apply.of(Symbol.of("abs"), inner.e()), inner.depth() + 1, open);
  }

  // Notation.

  private P derivative() {
    Token t = next();
    String var = boundName(t, t.text(), false);
    if (!canStartUnary(peek())) {
      throw error("expected an expression to differentiate", peek());
    }
    enter(t);
    scopes.push(List.of(var));
    P body = parse(39);
    scopes.pop();
    leave();
    BigInteger order = t.order();
    Expr e =
        order.bitLength() < 32
            ? Derivative.of(body.e(), Symbol.of(var), order.intValue())
            : Apply.of(Symbol.of("diff"), body.e(), Symbol.of(var), Num.of(Int.of(order)));
    return node(e, body.depth() + 1, t);
  }

  private P integral() {
    Token t = next();
    enter(t);
    Expr lower = null;
    Expr upper = null;
    int depth = 0;
    if (peek().is(TokenKind.LBRACKET)) {
      next();
      openBracket();
      P a = parse(29);
      expect(TokenKind.COMMA, ",");
      P b = parse(29);
      expect(TokenKind.RBRACKET, "]");
      closeBracket();
      lower = a.e();
      upper = b.e();
      depth = Math.max(a.depth(), b.depth());
    }
    Token ahead = differentialAhead();
    boolean scoped = false;
    if (ahead != null && !ahead.text().substring(1).startsWith("d")) {
      // A variable that cannot be bound is refused at the differential, after the integrand.
      try {
        scopes.push(List.of(boundName(ahead, ahead.text().substring(1), lower != null)));
        scoped = true;
      } catch (ParseException later) {
        scoped = false;
      }
    }
    integrals.push(brackets);
    P body = parse(29);
    integrals.pop();
    if (scoped) {
      scopes.pop();
    }
    Token differential = peek();
    if (!differential.is(TokenKind.IDENT) || !isDName(differential.text())) {
      throw error("expected a differential such as dx", differential);
    }
    String var = differential.text().substring(1);
    if (var.startsWith("d")) {
      throw error(
          "use integrate(" + AsciiPrinter.INSTANCE.print(body.e()) + ", " + var + ")",
          differential);
    }
    var = boundName(differential, var, lower != null);
    next();
    leave();
    Symbol v = Symbol.of(var);
    Expr e =
        lower == null
            ? Integral.indefinite(body.e(), v)
            : Integral.definite(body.e(), v, lower, upper);
    P result = node(e, Math.max(depth, body.depth()) + 1, t);
    return new P(result.e(), result.depth(), true);
  }

  /** The differential that will end the integral being opened, read ahead; null if none. */
  private Token differentialAhead() {
    int depth = 0;
    int nested = 0;
    for (int k = 0; ; k++) {
      Token t = raw(k);
      switch (t.kind()) {
        case ERROR, EOF, SEMI, NEWLINE -> {
          return null;
        }
        case LPAREN, LBRACKET, LBRACE, LANGLE -> depth++;
        case RPAREN, RBRACKET, RBRACE, RANGLE -> {
          if (depth == 0) {
            return null;
          }
          depth--;
        }
        case INTEGRAL -> {
          if (depth == 0) {
            nested++;
          }
        }
        case IDENT -> {
          if (depth == 0 && isDName(t.text())) {
            if (nested == 0) {
              return t;
            }
            nested--;
          }
        }
        default -> {}
      }
    }
  }

  private P limit(Token lim) {
    if (!peek().is(TokenKind.LBRACKET)) {
      throw error("write lim[x → a] f or limit(f, x, a)", lim);
    }
    next();
    enter(lim);
    openBracket();
    Token v = next();
    String var = boundName(v, v.text(), true);
    if (!peek().is(TokenKind.ARROW)) {
      throw error("expected `→`", peek());
    }
    next();
    int saved = limTarget;
    limTarget = brackets;
    P point = parse(29);
    limTarget = saved;
    Limit.Direction direction = Limit.Direction.BOTH;
    if (peek().is(TokenKind.PLUS) || peek().is(TokenKind.MINUS)) {
      direction = next().is(TokenKind.PLUS) ? Limit.Direction.RIGHT : Limit.Direction.LEFT;
    }
    expect(TokenKind.RBRACKET, "]");
    closeBracket();
    scopes.push(List.of(var));
    P body = parse(39);
    scopes.pop();
    leave();
    return node(
        Limit.of(body.e(), Symbol.of(var), point.e(), direction),
        Math.max(point.depth(), body.depth()) + 1,
        lim);
  }

  private P bigOperator(Token t, boolean sum) {
    if (!peek().is(TokenKind.LBRACKET)) {
      throw error(
          sum
              ? "write Σ[k = a, b] f or sum(f, k, a, b)"
              : "write Π[k = a, b] f or product(f, k, a, b)",
          t);
    }
    next();
    enter(t);
    openBracket();
    Token v = next();
    String var = boundName(v, v.text(), true);
    if (!peek().is(TokenKind.EQ)) {
      throw error("expected `=`", peek());
    }
    next();
    P from = parse(29);
    expect(TokenKind.COMMA, ",");
    P to = parse(29);
    expect(TokenKind.RBRACKET, "]");
    closeBracket();
    scopes.push(List.of(var));
    P body = parse(39);
    scopes.pop();
    leave();
    Symbol s = Symbol.of(var);
    Expr e =
        sum
            ? BigSum.of(body.e(), s, from.e(), to.e())
            : BigProduct.of(body.e(), s, from.e(), to.e());
    return node(e, Math.max(Math.max(from.depth(), to.depth()), body.depth()) + 1, t);
  }
}

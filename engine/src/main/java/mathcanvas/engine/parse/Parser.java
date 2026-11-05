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
import mathcanvas.engine.expr.Unit;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.script.Program;
import mathcanvas.engine.script.Stmt;
import mathcanvas.engine.units.Units;

/**
 * The parser of {@code docs/language.md}: source text to raw expression trees, exactly as typed,
 * with a position in every error. It is a Pratt parser (Vaughan R. Pratt, "Top Down Operator
 * Precedence", POPL 1973): each token kind has a prefix action (a nud) and, where it can follow an
 * operand, a binding power and an infix action (a led), and {@code parse(rbp)} takes infix actions
 * while their binding power exceeds {@code rbp}. The powers realise the table of §4.1:
 *
 * <ul>
 *   <li>relations 20 (non-associative), {@code + -} 30, {@code * / × mod} and implicit
 *       multiplication 40, {@code ^} 60 (its right operand is parsed at 59, so it is
 *       right-associative and admits a sign or a prefix application), postfix {@code ! % °} and
 *       superscripts 70;
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

  private static final String OPERAND = "an expression is missing here";
  private static final String LAMBDA =
      "the parameters before `->` must be names, as in `(x, y) -> x + y`";
  private static final String DIFFERENTIATE =
      "write what to differentiate after the derivative, as in `d/dx x^2`";
  private static final String PARAMETERS =
      "the parameters must be different names, as in `f(x, y) := x + y`";

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

  /** Whether a function's body is being parsed (§19). */
  private boolean inFunction;

  /** Whether the last inline block ended at its line's end right after a {@code ;} (§19). */
  private boolean endedQuiet;

  /** The loops open in the body being parsed (§19). */
  private int loops;

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
    if (t.is(TokenKind.DOTDOT)) {
      throw unexpected(t);
    }
    if (!t.is(kind)) {
      throw error("missing `" + text + "`" + (t.is(TokenKind.SEMI) ? " before `;`" : ""), t);
    }
    next();
  }

  private static ParseException error(String message, Token at) {
    return new ParseException(message, at);
  }

  private ParseException unexpected(Token t) {
    if (t.is(TokenKind.MINUTE) || t.is(TokenKind.SECOND)) {
      return error("`" + t.text() + "` follows a number of degrees, as in `30°15′20″`", t);
    }
    if (t.is(TokenKind.DOTDOT)) {
      return error("a range `a..b` works only in a `for` loop, as in `for k in 1..3`", t);
    }
    return error(
        t.is(TokenKind.NEWLINE)
            ? "the line ends before the expression does"
            : "`" + t.text() + "` does not belong here",
        t);
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
      throw error("the expression is nested more than " + MAX_NESTING + " levels deep", at);
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
      throw error("the expression is nested more than " + MAX_NESTING + " levels deep", at);
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
    if (keyword(t0) && !t1.is(TokenKind.DEFINE)) {
      switch (t0.text()) {
        case "function" -> {
          Statement.FunctionDefinition f = function(start);
          return new Statement.FunctionDefinition(
              f.name(), f.params(), f.body(), terminate(), start);
        }
        case "if", "for", "while" -> {
          endedQuiet = false;
          Stmt control = control();
          // a one-line if's trailing ; is the statement's, which then shows nothing (§2)
          boolean quiet = endedQuiet;
          return new Statement.Block(control, terminate() || quiet, start);
        }
        case "return" -> throw error("`return` works only inside a function", t0);
        case "break", "continue" -> throw error("`" + t0.text() + "` works only inside a loop", t0);
        case "global" -> throw error("`global` works only inside a function", t0);
        default -> throw unexpected(t0);
      }
    }
    if (t0.is(TokenKind.MOD)
        && (t1.is(TokenKind.DEFINE) || t1.is(TokenKind.LPAREN) && functionHeadAhead())) {
      throw error("`mod` is built in, so it cannot be redefined", t0);
    }
    if (t1.is(TokenKind.DEFINE) && t0.is(TokenKind.UNIT)) {
      throw error("a unit is built in, so it cannot be redefined", t0);
    }
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
    boolean quiet = terminate();
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

  /** Reads a top-level statement's end: whether it was {@code ;}. */
  private boolean terminate() {
    Token end = peek();
    if (end.is(TokenKind.SEMI)) {
      next();
      return true;
    }
    if (end.is(TokenKind.NEWLINE)) {
      next();
    } else if (!end.is(TokenKind.EOF)) {
      throw unexpected(end);
    }
    return false;
  }

  // Programs (§19).

  private static boolean keyword(Token t) {
    return t.is(TokenKind.IDENT) && Builtins.isKeyword(t.text());
  }

  /** {@code function name(params) … end}, at the top level. */
  private Statement.FunctionDefinition function(Position start) {
    Token at = next();
    Token nameToken = next();
    if (!nameToken.is(TokenKind.IDENT)
        && !nameToken.is(TokenKind.PI)
        && !nameToken.is(TokenKind.INFINITY)
        && !nameToken.is(TokenKind.DOMAIN)) {
      throw error("a name is missing here", nameToken);
    }
    String name = definedName(nameToken);
    expect(TokenKind.LPAREN, "(");
    List<String> params = new ArrayList<>();
    if (peek().is(TokenKind.RPAREN)) {
      next();
    } else {
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
    }
    defined.put(name, true);
    scopes.push(params);
    inFunction = true;
    Program body;
    try {
      body = block(false);
      endOfBlock();
    } finally {
      inFunction = false;
      scopes.pop();
    }
    // a global anywhere in the body, nested blocks included, applies to the whole body
    for (String n : body.globals()) {
      if (params.contains(n)) {
        throw error(
            "`" + n + "` is a parameter of this function, so it cannot be declared global", at);
      }
    }
    return new Statement.FunctionDefinition(
        Symbol.of(name), params.stream().map(Symbol::of).toList(), body, false, start);
  }

  /** Reads the {@code end} of a block, which the end of the input leaves open. */
  private void endOfBlock() {
    endedQuiet = false;
    Token t = peek();
    if (t.is(TokenKind.EOF)) {
      throw ParseException.incomplete("expected `end`", t);
    }
    expectWord("end");
  }

  /**
   * A block's statements, up to {@code end} or {@code else} (left for the caller), separated by
   * {@code ;} or newlines; an inline block, a branch of a one-line {@code if}, ends at the end of
   * its line too.
   */
  private Program block(boolean inline) {
    enter(peek());
    List<Stmt> out = new ArrayList<>();
    while (true) {
      Token t = peek();
      if (t.is(TokenKind.SEMI) || (!inline && t.is(TokenKind.NEWLINE))) {
        next();
        continue;
      }
      if (inline && (t.is(TokenKind.NEWLINE) || t.is(TokenKind.EOF))) {
        endedQuiet = pos > 0 && tokens.get(pos - 1).is(TokenKind.SEMI);
        break;
      }
      if (isWord(t, "end") || isWord(t, "else")) {
        break;
      }
      if (t.is(TokenKind.EOF)) {
        throw ParseException.incomplete("expected `end`", t);
      }
      out.add(inner());
      Token after = peek();
      if (!after.is(TokenKind.SEMI)
          && !after.is(TokenKind.NEWLINE)
          && !after.is(TokenKind.EOF)
          && !isWord(after, "end")
          && !isWord(after, "else")) {
        throw unexpected(after);
      }
    }
    leave();
    return new Program(out);
  }

  /** One statement inside a block. */
  private Stmt inner() {
    Token t0 = raw(0);
    Token t1 = raw(1);
    Position start = Position.of(t0);
    if (keyword(t0) && !t1.is(TokenKind.DEFINE)) {
      switch (t0.text()) {
        case "if", "for", "while" -> {
          return control();
        }
        case "function" -> throw error("a function cannot be defined inside a block", t0);
        case "return" -> {
          if (!inFunction) {
            throw error("`return` works only inside a function", t0);
          }
          next();
          Token v = peek();
          if (v.is(TokenKind.SEMI)
              || v.is(TokenKind.NEWLINE)
              || v.is(TokenKind.EOF)
              || isWord(v, "end")
              || isWord(v, "else")) {
            throw error("`return` needs a value, as in `return x + 1`", v);
          }
          return new Stmt.Return(expr().e(), start);
        }
        case "break", "continue" -> {
          if (loops == 0) {
            throw error("`" + t0.text() + "` works only inside a loop", t0);
          }
          next();
          return t0.text().equals("break") ? new Stmt.Break(start) : new Stmt.Continue(start);
        }
        case "global" -> {
          if (!inFunction) {
            throw error("`global` works only inside a function", t0);
          }
          next();
          List<Symbol> names = new ArrayList<>();
          while (true) {
            Token n = next();
            if (!n.is(TokenKind.IDENT)) {
              throw error("a name is missing here", n);
            }
            names.add(Symbol.of(definedName(n)));
            if (!peek().is(TokenKind.COMMA)) {
              break;
            }
            next();
          }
          return new Stmt.Global(names, start);
        }
        default -> throw unexpected(t0);
      }
    }
    if (t1.is(TokenKind.DEFINE) && t0.is(TokenKind.UNIT)) {
      throw error("a unit is built in, so it cannot be redefined", t0);
    }
    if (t1.is(TokenKind.DEFINE)
        && (t0.is(TokenKind.IDENT)
            || t0.is(TokenKind.PI)
            || t0.is(TokenKind.INFINITY)
            || t0.is(TokenKind.DOMAIN))) {
      Token nameToken = next();
      String name = definedName(nameToken);
      next();
      return new Stmt.Assign(Symbol.of(name), expr().e(), start);
    }
    if (t0.is(TokenKind.IDENT) && t1.is(TokenKind.LPAREN) && functionHeadAhead()) {
      throw error("a function cannot be defined inside a block", t0);
    }
    Expr e = expr().e();
    if (e instanceof Apply a && a.head().name().equals("print") && a.args().size() == 1) {
      return new Stmt.Print(a.args().getFirst(), start);
    }
    return new Stmt.Evaluate(e, start);
  }

  /** {@code if}, {@code for} or {@code while}. */
  private Stmt control() {
    Token t = peek();
    return switch (t.text()) {
      case "if" -> ifStatement();
      case "for" -> forStatement();
      default -> whileStatement();
    };
  }

  /**
   * {@code if c then … [else if c then …] [else …] end}: a block form when a newline follows the
   * first {@code then}, which needs its {@code end}; otherwise a one-line form, whose branches end
   * at the end of the line and whose {@code end} is optional.
   */
  private Stmt ifStatement() {
    Token t = next();
    Position start = Position.of(t);
    enter(t);
    List<Stmt.Branch> branches = new ArrayList<>();
    Expr condition = parse(0).e();
    expectWord("then");
    boolean inline = !peek().is(TokenKind.NEWLINE) && !peek().is(TokenKind.EOF);
    branches.add(new Stmt.Branch(condition, block(inline)));
    Program otherwise = Program.EMPTY;
    while (isWord(peek(), "else")) {
      next();
      if (isWord(peek(), "if")) {
        next();
        condition = parse(0).e();
        expectWord("then");
        branches.add(new Stmt.Branch(condition, block(inline)));
        continue;
      }
      otherwise = block(inline);
      break;
    }
    if (inline) {
      if (isWord(peek(), "end")) {
        next();
        endedQuiet = false;
      }
    } else {
      endOfBlock();
    }
    leave();
    return new Stmt.If(branches, otherwise, start);
  }

  /** {@code for x in a..b [step s]} or {@code for x in items}, then {@code do} or a newline. */
  private Stmt forStatement() {
    Token t = next();
    Position start = Position.of(t);
    Token v = next();
    // the variable is bound in the body, so e and i may be loop variables there
    String name = boundName(v, v.text(), true);
    if (!peek().is(TokenKind.IN)) {
      throw error("missing `in`, as in `for k in 1..3`", peek());
    }
    next();
    Expr from = parse(29).e();
    Stmt.Source source;
    if (peek().is(TokenKind.DOTDOT)) {
      next();
      Expr to = parse(29).e();
      Expr step = null;
      if (isWord(peek(), "step")) {
        next();
        step = parse(29).e();
      }
      source = new Stmt.Range(from, to, step);
    } else {
      source = new Stmt.Items(from);
    }
    scopes.push(List.of(name));
    Program body;
    try {
      body = loopBody();
    } finally {
      scopes.pop();
    }
    return new Stmt.For(Symbol.of(name), source, body, start);
  }

  /** {@code while c}, then {@code do} or a newline. */
  private Stmt whileStatement() {
    Token t = next();
    Position start = Position.of(t);
    Expr condition = parse(0).e();
    return new Stmt.While(condition, loopBody(), start);
  }

  /** A loop's header end ({@code do} or a newline), its body and its {@code end}. */
  private Program loopBody() {
    Token h = peek();
    if (isWord(h, "do")) {
      next();
    } else if (h.is(TokenKind.EOF)) {
      throw ParseException.incomplete("expected `end`", h);
    } else if (!h.is(TokenKind.NEWLINE)) {
      throw error("missing `do` or a new line after the loop's header", h);
    }
    loops++;
    try {
      Program body = block(false);
      endOfBlock();
      return body;
    } finally {
      loops--;
    }
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
      throw error("`" + name + "` is a constant, so it cannot be used as a name here", t);
    }
    return boundName(t, name, true);
  }

  /**
   * A name that a binder, a parameter or a definition introduces, refusing constants (other than
   * {@code e} and {@code i} where {@code eiAllowed}), domains, builtins and keywords.
   */
  private static String boundName(Token at, String name, boolean eiAllowed) {
    if (at.is(TokenKind.PI) || at.is(TokenKind.INFINITY)) {
      throw error("`" + name + "` is a constant, so it cannot be used as a name here", at);
    }
    if (at.is(TokenKind.DOMAIN) || Builtins.isDomain(name)) {
      throw error("`" + name + "` is a number domain, so it cannot be used as a name here", at);
    }
    if (!at.is(TokenKind.IDENT) && !at.is(TokenKind.DERIVATIVE) && !at.is(TokenKind.PARTIAL)) {
      throw error("a name is missing here", at);
    }
    if (Builtins.isKeyword(name) || name.equals("in")) {
      throw error("`" + name + "` is a reserved word, so it cannot be used as a name here", at);
    }
    if (name.equals("e") || name.equals("i")) {
      if (!eiAllowed) {
        throw error("`" + name + "` is a constant, so it cannot be the variable here", at);
      }
      return name;
    }
    if (Builtins.isConstant(name)) {
      throw error("`" + name + "` is a constant, so it cannot be used as a name here", at);
    }
    Optional<Builtin> builtin = Builtins.lookup(name);
    if (builtin.isPresent()) {
      throw error(
          "`"
              + name
              + "` is "
              + (builtin.get().isCallable() ? "built in" : "a reserved word")
              + ", so it cannot be used as a name here",
          at);
    }
    return name;
  }

  // Expressions.

  private P expr() {
    Token start = peek();
    if (isWord(start, "if")) {
      return conditional();
    }
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
      throw error(LAMBDA, start);
    }
    return e;
  }

  /**
   * The if expression {@code if c then a else b} (§19), where an expression begins: at the lambda's
   * level, its condition an expression that stops at {@code then}, its else branch extending as far
   * right as a lambda's body. It needs its else, and takes no {@code end}.
   */
  private P conditional() {
    Token t = next();
    enter(t);
    P condition = parse(0);
    expectWord("then");
    P then = expr();
    if (!isWord(peek(), "else")) {
      throw error(
          "an `if` that gives a value needs `else`, as in `if x > 0 then 1 else 0`", peek());
    }
    next();
    P otherwise = expr();
    leave();
    P result =
        node(
            Apply.of(Symbol.of("if"), condition.e(), then.e(), otherwise.e()),
            Math.max(condition.depth(), Math.max(then.depth(), otherwise.depth())) + 1,
            t);
    note(result.e(), t);
    return result;
  }

  /** Whether a token is the keyword {@code word}. */
  private static boolean isWord(Token t, String word) {
    return t.is(TokenKind.IDENT) && t.text().equals(word);
  }

  /** Reads the keyword {@code word}, or fails with "expected `word`". */
  private void expectWord(String word) {
    Token t = peek();
    if (!isWord(t, word)) {
      throw error("missing `" + word + "`" + (t.is(TokenKind.SEMI) ? " before `;`" : ""), t);
    }
    next();
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
        throw error("a definition with `:=` must be a statement of its own, as in `a := 5`", t);
      }
      if (left.integral() && (k == TokenKind.CARET || isPostfix(k))) {
        throw error("put the integral in brackets first, as in `(∫ x dx)^2`", t);
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
          || k == TokenKind.MOD
          || k == TokenKind.BACKSLASH
          || startsImplicitFactor(t)) {
        if (rbp >= 40) {
          return left;
        }
        left = term(left, t, first);
      } else if (k == TokenKind.PLUS
          || k == TokenKind.MINUS
          || k == TokenKind.UNION
          || k == TokenKind.INTERSECT) {
        if (rbp >= 30 || limDirection()) {
          return left;
        }
        left = additive(left, t);
      } else if (isRelation(k)) {
        if (rbp >= 20) {
          return left;
        }
        left = relation(left);
      } else if (logicPower(k) > 0) {
        if (rbp >= logicPower(k)) {
          return left;
        }
        left = logical(left, t);
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
      if (k == TokenKind.STAR
          || k == TokenKind.SLASH
          || k == TokenKind.CROSS
          || k == TokenKind.MOD
          || k == TokenKind.BACKSLASH) {
        next();
        if (k == TokenKind.MOD && listAhead()) {
          throw error(
              "after a factor, `mod` is the operator: write `a mod b`, or `x·mod(a, b)`", t);
        }
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
          String head =
              switch (k) {
                case MOD -> "mod";
                case BACKSLASH -> "setdiff";
                default -> "cross";
              };
          Expr cross = Apply.of(Symbol.of(head), acc, right.e());
          note(cross, first);
          factors = new ArrayList<>(List.of(cross));
        }
      } else if (startsImplicitFactor(t)) {
        if (k == TokenKind.INT || k == TokenKind.DECIMAL) {
          throw error(
              "a number cannot come right after a factor: put `*` between them, as in `x*2`, or"
                  + " put the number first, as in `2x`",
              t);
        }
        if (k == TokenKind.LBRACKET) {
          throw error("square brackets after a value do not pick an item: write `nth(v, 1)`", t);
        }
        P right = parse(40);
        factors.add(right.e());
        depth = Math.max(depth, right.depth());
      } else {
        break;
      }
    }
    if (factors.size() == 2
        && factors.getFirst() instanceof Product magnitude
        && !(magnitude.factors().size() == 2
            && magnitude.factors().getFirst().equals(Num.MINUS_ONE))
        && isUnitPart(factors.get(1))
        && !isUnitPart(magnitude)) {
      // a product times a unit part is a quantity, printed as its magnitude's factors then the
      // units with no parentheses ((2x) _m and 3° _m as 2*x*_m and 3*pi/180*_m): one product
      List<Expr> flat = new ArrayList<>(magnitude.factors());
      flat.add(factors.get(1));
      factors = flat;
    }
    return factors.size() == 1
        ? new P(factors.getFirst(), depth, false)
        : node(Product.of(factors), depth + 1, start);
  }

  /** Whether an expression is made of units alone, as the printers read a quantity's units. */
  private static boolean isUnitPart(Expr e) {
    return switch (e) {
      case Unit _ -> true;
      case Power p -> isUnitPart(p.base());
      case Product p ->
          p.factors().stream()
              .skip(p.factors().getFirst().equals(Num.ONE) ? 1 : 0)
              .allMatch(Parser::isUnitPart);
      default -> false;
    };
  }

  private P additive(P left, Token start) {
    List<Expr> terms = new ArrayList<>(List.of(left.e()));
    int depth = left.depth();
    while (true) {
      Token t = peek();
      boolean set = t.is(TokenKind.UNION) || t.is(TokenKind.INTERSECT);
      if (!(t.is(TokenKind.PLUS) || t.is(TokenKind.MINUS) || set) || limDirection()) {
        break;
      }
      next();
      P right = parse(30);
      if (set) {
        // A ∪ B and A ∩ B fold the sum so far into their left operand, as × does a product
        Expr acc = terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
        int accDepth = terms.size() == 1 ? depth : depth + 1;
        depth = Math.max(accDepth, right.depth()) + 1;
        checkDepth(depth, t);
        Expr op =
            Apply.of(Symbol.of(t.is(TokenKind.UNION) ? "union" : "intersect"), acc, right.e());
        note(op, start);
        terms = new ArrayList<>(List.of(op));
        continue;
      }
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
    return terms.size() == 1
        ? new P(terms.getFirst(), depth, false)
        : node(Sum.of(terms), depth + 1, start);
  }

  private P relation(P left) {
    Token t = next();
    P right = parse(20);
    Token after = peek();
    if (isRelation(after.kind())) {
      throw error("relations cannot be chained: write `a < b and b < c`, not `a < b < c`", after);
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

  /**
   * The binding power of a boolean operator (§8.4): {@code and} 16, {@code xor} 15, {@code or} 14,
   * {@code implies} 12; 0 for any other token. {@code not} is a prefix operator whose operand is
   * parsed at 17.
   */
  private static int logicPower(TokenKind k) {
    return switch (k) {
      case AND -> 16;
      case XOR -> 15;
      case OR -> 14;
      case IMPLIES -> 12;
      default -> 0;
    };
  }

  /**
   * A chain of one boolean operator: {@code and}, {@code or} and {@code xor} gather their operands
   * into one application; {@code implies} is right-associative, its right side parsed below its own
   * power.
   */
  private P logical(P left, Token start) {
    TokenKind k = start.kind();
    int power = logicPower(k);
    String head =
        switch (k) {
          case AND -> "and";
          case OR -> "or";
          case XOR -> "xor";
          default -> "implies";
        };
    List<Expr> operands = new ArrayList<>(List.of(left.e()));
    int depth = left.depth();
    do {
      Token t = next();
      enter(t);
      P right = parse(k == TokenKind.IMPLIES ? power - 1 : power);
      leave();
      operands.add(right.e());
      depth = Math.max(depth, right.depth());
    } while (k != TokenKind.IMPLIES && peek().is(k));
    return node(Apply.of(Symbol.of(head), operands), depth + 1, start);
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

  /** Whether a parenthesised list {@code (a, b, …)} is ahead: a comma at its first level. */
  private boolean listAhead() {
    if (!peek().is(TokenKind.LPAREN)) {
      return false;
    }
    int depth = 0;
    for (int k = 0; ; k++) {
      Token t = raw(k);
      switch (t.kind()) {
        case LPAREN, LBRACKET, LBRACE, LANGLE -> depth++;
        case RPAREN, RBRACKET, RBRACE, RANGLE -> {
          if (--depth == 0) {
            return false;
          }
        }
        case COMMA -> {
          if (depth == 1) {
            return true;
          }
        }
        case EOF, ERROR, SEMI -> {
          return false;
        }
        default -> {}
      }
    }
  }

  /** Whether a token can start a unary expression (§4.2). */
  private boolean canStartUnary(Token t) {
    if (isDifferential(t)) {
      return false;
    }
    // a keyword ends an expression: then, else, do, end and step close the headers of §19
    if (t.is(TokenKind.IDENT) && Builtins.isKeyword(t.text())) {
      return false;
    }
    return switch (t.kind()) {
      case INT,
          DECIMAL,
          IDENT,
          UNIT,
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
          DERIVATIVE,
          PARTIAL ->
          true;
      case BAR -> bars.peek()[0] == 0;
      // the call mod(a, b); after an operand, mod is the operator
      case MOD -> t == peek() && peek(1).is(TokenKind.LPAREN);
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
          throw error("this number is too large", t);
        }
      }
      case IDENT -> name();
      case UNIT -> {
        next();
        Unit u = Units.of(t.text()).orElseThrow(() -> error("unknown unit `_" + t.text() + "`", t));
        yield new P(u, 0, false);
      }
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
      case PARTIAL -> partial();
      case NOT -> {
        next();
        enter(t);
        P operand = parse(17);
        leave();
        yield node(Apply.of(Symbol.of("not"), operand.e()), operand.depth() + 1, t);
      }
      case MOD -> {
        next();
        Token after = peek();
        if (!after.is(TokenKind.LPAREN)) {
          boolean operand =
              canStartUnary(after) && !after.is(TokenKind.PLUS) && !after.is(TokenKind.MINUS);
          throw error(operand ? needsBrackets("mod", "mod(7, 3)") : needsArgument("mod(7, 3)"), t);
        }
        yield call(t, Builtins.lookup("mod").orElseThrow());
      }
      case SEMI ->
          throw error(
              brackets > 0 ? "`;` cannot stand inside brackets or square brackets" : OPERAND, t);
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
    if (name.equals("if")) {
      throw error("put the `if` in brackets, as in `1 + (if p then 1 else 2)`", t);
    }
    if (Builtins.isKeyword(name)) {
      throw error("`" + name + "` is a reserved word and cannot stand here", t);
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
      String hint = "write `" + name + "(x)²` for the square";
      if (Builtins.lookup("a" + name).isPresent()) {
        hint += ", or `a" + name + "(x)` for the inverse";
      }
      throw error(hint, t);
    }
    if (builtin.isPresent() && builtin.get().arities().equals(List.of(0))) {
      throw error("`" + name + "` needs empty brackets: write `" + name + "()`", t);
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
    String example = builtin.map(b -> b.exampleCall(name)).orElse(name + "(x)");
    throw error(operand ? needsBrackets(name, example) : needsArgument(example), t);
  }

  /** The error of a function name followed by an operand without brackets: {@code abs x}. */
  private static String needsBrackets(String name, String example) {
    return "`" + name + "` needs brackets around what it applies to, as in `" + example + "`";
  }

  /** The error of a function name with nothing to apply to: {@code sin = 1}. */
  private static String needsArgument(String example) {
    String name = example.substring(0, example.indexOf('('));
    return "`" + name + "` needs an argument in brackets, as in `" + example + "`";
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
      throw error(
          "`" + name + "` works only for functions you define: write `diff(" + base + "(x), x)`",
          t);
    }
    if (!peek().is(TokenKind.LPAREN)) {
      throw error("`" + name + "` needs an argument in brackets, as in `" + name + "(x)`", t);
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
    // dsolve and odesolve declare their unknown functions for the whole call and bind
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
    // laplace declares the names applied to its variable in its first argument
    if (builtin != null && head.equals("laplace")) {
      List<String> found = laplaceUnknowns();
      if (!found.isEmpty()) {
        declared = found;
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
        } else if (head.equals("odesolve")
            && first.is(TokenKind.IDENT)
            && first.text().equals("steps")
            && peek(1).is(TokenKind.EQ)) {
          // odesolve's option steps = n, the builtin's name a literal word there
          next();
          next();
          P value = expr();
          args.add(Relation.of(Relation.Op.EQ, Symbol.of("steps"), value.e()));
          depth = Math.max(depth, value.depth() + 1);
        } else if (head.equals("tvm")
            && first.is(TokenKind.IDENT)
            && TVM_PARTS.contains(first.text())
            && peek(1).is(TokenKind.EQ)) {
          // tvm's parts by name, the builtins pv, pmt, fv and the constant i words there
          next();
          next();
          P value = expr();
          args.add(Relation.of(Relation.Op.EQ, Symbol.of(first.text()), value.e()));
          depth = Math.max(depth, value.depth() + 1);
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
    // integrate takes 2 or 4 arguments, or brackets [x, a, b] after the integrand
    if (head.equals("integrate")
        && args.size() != 2
        && args.size() != 4
        && !TypedNodes.isIterated(args)) {
      throw error(INTEGRATE_ARITY, nameToken);
    }
    if (builtin != null && !builtin.accepts(args.size())) {
      throw error(builtin.arityError(name), nameToken);
    }
    return node(TypedNodes.of(head, args, direction), depth + 1, open);
  }

  /**
   * The unknown functions of a {@code laplace(f, t, s)} call being read, read ahead: the names
   * applied to exactly the variable {@code (t)} in the first argument, with or without primes, that
   * are neither builtins, constants, domains, keywords, the variable, bound names nor user
   * functions. Empty when the second argument is not a name.
   */
  private List<String> laplaceUnknowns() {
    int depth = 0;
    int end;
    for (int k = 0; ; k++) {
      TokenKind kind = raw(k).kind();
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
    if (!raw(end + 1).is(TokenKind.IDENT) || !raw(end + 2).is(TokenKind.COMMA)) {
      return List.of();
    }
    String var = raw(end + 1).text();
    List<String> out = new ArrayList<>();
    for (int k = 0; k + 3 < end; k++) {
      if (!raw(k).is(TokenKind.IDENT)
          || !raw(k + 1).is(TokenKind.LPAREN)
          || !raw(k + 2).is(TokenKind.IDENT)
          || !raw(k + 2).text().equals(var)
          || !raw(k + 3).is(TokenKind.RPAREN)) {
        continue;
      }
      String name = raw(k).text();
      String base = name.contains("'") ? name.substring(0, name.indexOf('\'')) : name;
      if (base.isEmpty()
          || Builtins.lookup(base).isPresent()
          || Builtins.isConstant(base)
          || Builtins.isDomain(base)
          || Builtins.isKeyword(base)
          || base.equals(var)
          || bound(base)
          || isUserFunction(base)
          || out.contains(base)) {
        continue;
      }
      out.add(base);
    }
    return out;
  }

  /** The commands whose second argument declares unknown functions. */
  private static final Set<String> UNKNOWN_COMMANDS = Set.of("dsolve", "odesolve", "rsolve");

  /** The parts of {@code tvm}'s arguments (§14.28), literal words before {@code =} there. */
  private static final Set<String> TVM_PARTS = Set.of("n", "i", "pv", "pmt", "fv", "type");

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

  /** The arity message of {@code integrate}, whose iterated form takes brackets (§12.2). */
  private static final String INTEGRATE_ARITY =
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
    if (end < 0 && LIST_BINDERS.contains(builtin.name())) {
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
    if (builtin.name().equals("diff")) {
      // diff(f, x, y, …): every later argument that is a name is a variable too
      for (int k = bindAt; k < starts.size(); k++) {
        List<Token> later = new ArrayList<>();
        int after = wrappedName(starts.get(k), later);
        if (after >= 0 && (raw(after).is(TokenKind.COMMA) || raw(after).is(TokenKind.RPAREN))) {
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
      throw error(LAMBDA, open);
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
              throw error("a vector's entries must be single values, not lists or vectors", open);
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
      throw error("there is nothing between the bars of `| |`", peek());
    }
    P inner = parse(29);
    if (!peek().is(TokenKind.BAR)) {
      throw error("missing `|` to close the absolute value", peek());
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
      throw error(DIFFERENTIATE, peek());
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

  /**
   * The partial derivative notation (§10.1): {@code ∂/∂x f} and its mixed and higher forms take the
   * rest of the term, as {@code d/dx} does; {@code ∂f/∂x} is complete, its function a name. Either
   * is the nested {@code Derivative}s of its variables, the rightmost denominator innermost, a run
   * of one variable one node of that order.
   */
  private P partial() {
    Token t = next();
    int bar = t.text().indexOf('|');
    String function = t.text().substring(0, bar);
    List<String> vars = new ArrayList<>();
    for (String v : t.text().substring(bar + 1).split(" ")) {
      vars.add(boundName(t, v, false));
    }
    P body;
    if (function.isEmpty()) {
      if (!canStartUnary(peek())) {
        throw error(DIFFERENTIATE, peek());
      }
      enter(t);
      scopes.push(List.copyOf(vars));
      body = parse(39);
      scopes.pop();
      leave();
    } else {
      if (Builtins.isKeyword(function)
          || function.equals("in")
          || Builtins.isConstant(function)
          || Builtins.isDomain(function)
          || Builtins.lookup(function).isPresent()
          || isUserFunction(function)) {
        throw error("in `∂f/∂x`, f must be a name: write `∂/∂x " + function + "(…)`", t);
      }
      body = new P(Symbol.of(function), 0, false);
    }
    Expr e = body.e();
    for (int k = 0; k < vars.size(); ) {
      int run = k;
      while (run < vars.size() && vars.get(run).equals(vars.get(k))) {
        run++;
      }
      e = Derivative.of(e, Symbol.of(vars.get(k)), run - k);
      k = run;
    }
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
      throw error("the integral needs a differential such as `dx`, as in `∫ x^2 dx`", differential);
    }
    String var = differential.text().substring(1);
    if (var.startsWith("d")) {
      throw error(
          "write `integrate(" + AsciiPrinter.INSTANCE.print(body.e()) + ", " + var + ")`",
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
      throw error("write `lim[x → a] f` or `limit(f, x, a)`", lim);
    }
    next();
    enter(lim);
    openBracket();
    Token v = next();
    String var = boundName(v, v.text(), true);
    if (!peek().is(TokenKind.ARROW)) {
      throw error("missing `→`, as in `lim[x → 0]`", peek());
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
              ? "write `Σ[k = a, b] f` or `sum(f, k, a, b)`"
              : "write `Π[k = a, b] f` or `product(f, k, a, b)`",
          t);
    }
    next();
    enter(t);
    openBracket();
    Token v = next();
    String var = boundName(v, v.text(), true);
    if (!peek().is(TokenKind.EQ)) {
      throw error("missing `=`, as in `Σ[k = 1, n]`", peek());
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

# The math-canvas language

The reference for the language that the worksheet, the CLI and `.mc` scripts share: its
lexical structure, grammar, notation, collections, names and scope, the Phase 1 namespace, what
evaluation means, the three text printers and the parse errors. The parser and its test corpus
follow this document; a change to the syntax changes this document in the same series.

## 1. Reading this document

- A rule is stated once, with examples. `a` ⇒ `b` means "`a` parses as `b`", where `b` is
  written in **parse form**: ASCII command form, fully parenthesised where grouping matters,
  with `*` for every multiplication, `factorial(…)` for `!`, `(…)/100` for `%` and
  `(…)*pi/180` for `°`. `a` → `b` in a result column means "`a` evaluates to `b`", with `b`
  written as the Unicode printer prints it (§15).
- Parse form describes grouping and meaning. The parser produces raw nodes exactly as typed;
  bringing them to canonical form (flattening, sorting, collecting) is a separate step, defined
  with the expression tree, not here.
- "Phase N" marks a feature whose syntax is fixed now and whose evaluation arrives in that
  phase.

## 2. Source text

**Encoding.** Source is UTF-8 and is normalised to NFC before lexing. A byte-order mark (U+FEFF) at
the very start of the text is skipped; positions still count it as typed, so the first token after
it is in column 2 (§16). Every operator, constant and notation has an ASCII spelling, and the
Unicode spellings are aliases (§3.5); identifiers may use any letters (§3.2).

**Whitespace** is space (U+0020), tab and no-break space (U+00A0); a **newline** is LF, CR LF or
CR alone. Whitespace only separates tokens: no rule in this language depends on whether
whitespace is present between two tokens. It matters only where it splits what would otherwise
be one token: `xy` against `x y`, `2e-3` against `2e - 3`, `5!=3` against `5! = 3`, and the
derivative notation (§10.1), which is one token and admits none inside it. `x(x+1)` and
`x (x+1)` parse the same; so do `sin(x)` and `sin (x)`.

**Comments** run from `#` to the end of the line. (`%` is the percent operator; `//` is kept
free.)

**Statements.** A script is a sequence of statements. A statement ends at `;`, at a newline that
is not inside an open bracket (`(`, `[`, `{`, `⟨`), or at the end of the input. A statement
ended by `;` is evaluated and its result is not printed; a statement ended by a newline or by
the end of the input prints its result. Empty statements are allowed and do nothing. A `;`
inside an open bracket is an error ("unexpected `;`"). There is
no line continuation outside brackets: `x +` at the end of a line is an error ("expected an
operand"); `(x +` continues on the next line.

```
a := 5;        # defined quietly
b := a + 1     # prints  b := a + 1
b^2            # prints  36
```

## 3. Tokens

### 3.1 Numbers

| Form | Examples | Kind |
|---|---|---|
| Integer: ASCII digits | `0`, `123`, `007` | integer literal (exact) |
| Decimal: digits `.` digits, or `.` digits | `1.5`, `.5`, `2.0` | decimal literal (inexact) |
| Scientific: integer or decimal mantissa, then `e` or `E`, an optional sign, digits | `1.5e3`, `1e-3`, `2E10`, `.5e+2` | decimal literal (inexact) |

- Only ASCII digits. There is no trailing-point form: `5.` is an error ("expected a digit after
  `.`").
- **Scientific notation wins over implicit multiplication** when, and only when, the `e` or `E`
  is followed by digits, or by a sign and digits. `2e3` is the decimal 2000; `2e` ⇒ `2*e`;
  `2E` ⇒ `2*E`; `2e+x` ⇒ `2*e + x`. Note `2e-3` is the decimal 0.002, not `2e − 3` (write
  `2e - 3` with a space or `2*e - 3`), and `2ex` ⇒ `2*ex` because `ex` is one identifier (§3.2).
- Integer and decimal literals mean different things: integers are exact, decimals are inexact
  numbers that keep every digit written (§14.1).
- A numeral, or a run of superscript digits, longer than 1,000,000 characters is an error
  ("number out of range"); every digit up to that bound is kept. Every number the evaluator shows
  fits within it (§16), so printed output always reads back.

### 3.2 Identifiers

An identifier is one **start character** followed by any number of **continue characters**:

- **Start:** `_`, or a character in Unicode category Lu, Ll, Lt or Lo, except the characters
  that are tokens of their own: `π` (U+03C0), `Σ` (U+03A3), `Π` (U+03A0), `ℤ` (U+2124), `ℚ`
  (U+211A), `ℝ` (U+211D), `ℂ` (U+2102). Modifier letters (Lm) are excluded, so superscript
  letters such as `ⁿ` and `ⁱ` never join an identifier.
- **Continue:** a start character, an ASCII digit, `_`, or one of these subscript characters:
  `₀`–`₉` (U+2080–U+2089), `ₐ ₑ ₒ ₓ ₕ ₖ ₗ ₘ ₙ ₚ ₛ ₜ` (U+2090–U+209C without U+2094),
  `ᵢ ᵣ ᵤ ᵥ` (U+1D62–U+1D65) and `ⱼ` (U+2C7C).

Rules:

- **Case-sensitive, never split.** `xy` is one symbol named `xy`, always; `x y` or `x*y` is the
  product. Likewise `ex`, `xsin`, `ab2`.
- **Greek letters are letters** (`θ`, `α`, `ω`), except `π`, `Σ` and `Π`, which are always
  tokens of their own: `2πr` ⇒ `2*pi*r`, `πi` ⇒ `pi*i`.
- **Subscripts normalise to `_` plus ASCII.** The lexer replaces each maximal run of subscript
  characters with `_` followed by their ASCII equivalents: `x₁` is the identifier `x_1`, `x₁₂`
  is `x_12`, `aₙ` is `a_n`. `x₁` and `x_1` are therefore the same symbol. The Unicode printer
  writes the subscripts back (§15.3).
- Examples: `x`, `x1`, `x_1`, `x₁`, `θ`, `velocity`, `d`, `dx`, `_tmp`.

### 3.3 Reserved names

| Group | Names | May be defined (`:=`) | May be bound (§11.2) |
|---|---|---|---|
| Constants | `pi` `π`, `e`, `i`, `inf` `infinity` `∞`, `true`, `false`, `undefined` | no | `e` and `i` only, in some positions |
| Domains | `Integers` `ℤ`, `Rationals` `ℚ`, `Reals` `ℝ`, `Complexes` `ℂ` | no | no |
| Builtins | every name in §12, including the reserved later-phase names | no | no |
| Notation word | `lim` (§10.3) | no | no |
| Keywords (Phase 9) | `and`, `or`, `not`, `if`, `then`, `else`, `for`, `while`, `function`, `return` | no | no |
| Reserved infix | `mod` used as an infix operator (`a mod b`); `mod(a, b)` is the builtin | no | no |

A keyword anywhere in Phase 1 is an error ("`if` is reserved"). `a mod b` is an error with the
hint "write mod(a, b)". The word `in` is the ASCII spelling of `∈` (§8.1), not an identifier.
`lim` is not a builtin: it is a reserved notation word, used only by the limit notation
(§10.3). `left` and `right` are literal words only in the fourth argument of `limit`; everywhere
else they are ordinary identifiers.

### 3.4 Operators and punctuation

```
+  -  *  /  ^  **  !  %  °  =  !=  <  <=  >  >=  in  ->  :=
(  )  [  ]  {  }  ⟨  ⟩  |  ,  ;  #
```

plus the Unicode aliases (§3.5), `×` (cross product, §9.4), the superscripts, and the notation
tokens `∫`, `√`, `∛`, `∜`, `Σ`/`∑`, `Π`/`∏` and the derivative token (§10.1).

The lexer takes the **longest match**: `**` is power, `!=` is not-equal, `->` is the arrow, `<=`
and `>=` are relations. So `5!=3` ⇒ `5 != 3` (write `5! = 3` to compare a factorial), and
`x<-1` ⇒ `x < -1`. `==`, `=<`, `=>` and `<>` are errors with the hint to use `=`, `<=`, `>=`
or `!=`. Any other character that is not a letter, digit or listed symbol (`∂`, `∇`, `⊗`, `∧`)
is an error naming the character ("unknown operator `∂`").

### 3.5 Unicode aliases

Each row is one input spelling with its aliases; the last column is what the Unicode printer
emits.

| ASCII | Unicode input | Meaning | Unicode printer |
|---|---|---|---|
| `*` | `·` (U+00B7), `⋅` (U+22C5) | multiplication | `·` where needed, else juxtaposition |
| `/` | `÷` | division | `/` |
| `-` | `−` (U+2212) | minus | `-` (hyphen-minus) |
| `^` | `**`, superscripts (below) | power | superscripts for non-negative integer exponents, else `^` |
| `<=` `>=` `!=` | `≤` `≥` `≠` | relations | `≤` `≥` `≠` |
| `->` | `→` | lambda; approach in `lim` | `→` |
| `in` | `∈` | membership in a domain | `∈` |
| `sqrt(x)` | `√x` | square root | `√` |
| `cbrt(x)` | `∛x` | cube root | `∛` |
| `root(x, 4)` | `∜x` | fourth root | `∜` |
| `pi` | `π` | π | `π` |
| `inf`, `infinity` | `∞` | infinity | `∞` |
| `Integers` `Rationals` `Reals` `Complexes` | `ℤ` `ℚ` `ℝ` `ℂ` | domains | `ℤ` `ℚ` `ℝ` `ℂ` |
| `sum[…]` | `Σ[…]`, `∑[…]` | sum notation | `∑` |
| `product[…]` | `Π[…]`, `∏[…]` | product notation | `∏` |
| `x^2`, `x^-1` | `x²`, `x⁻¹`, `x¹⁰` | postfix power by an integer | superscripts for non-negative exponents (`x⁻¹` is input only) |
| `x*pi/180` | `x°` | degrees | written out as a multiple of `π` |
| `[a, b]` (a vector) | `⟨a, b⟩` | vector | `⟨a, b⟩` |
| `x_1` | `x₁` | subscripted identifier | `x₁` |
| `cross(u, v)` | `u × v` | cross product (§9.4) | `×` |

**Superscript powers.** A run of superscript digits `⁰`–`⁹`, optionally preceded by one `⁻`, is
a postfix power by that integer: `x²` ⇒ `x^2`, `x⁻¹` ⇒ `x^(-1)`, `x¹⁰` ⇒ `x^10`. A lone `⁻` is
an error. Superscript letters are not part of the language.

`×` is **not** an alias of `*`: it is its own operator (§9.4).

## 4. Grammar

### 4.1 Precedence

Tightest first. Every level is described in its own section below.

| Level | Construct | Operand | Associativity | Examples |
|---|---|---|---|---|
| 1 Postfix | `!`, `%`, `°`, superscript power | the primary on its left | left to right | `5!!` ⇒ `factorial(factorial(5))`; `x²!` ⇒ `factorial(x^2)` |
| 2 Power | `^` (`**`) | left: a postfix expression; right: a unary expression | right | `2^3^2` ⇒ `2^(3^2)`; `2^3!` ⇒ `2^factorial(3)`; `2^-1` ⇒ `2^(-1)` |
| 3a Prefix | unary `-` `+`; prefix function names (`sin x`); `√ ∛ ∜` | the next unary expression | right (nest) | `-2^2` ⇒ `-(2^2)`; `sin x^2` ⇒ `sin(x^2)`; `sin 2x` ⇒ `sin(2)*x` |
| 3b Big operators | `d/dx`, `d^n/dx^n`; `lim[…]`; `Σ[…]` `Π[…]` | the rest of the current multiplicative term | — | `lim[x→0] sin(x)/x` ⇒ `limit(sin(x)/x, x, 0)` |
| 3c Integral | `∫ … dx`, `∫[a, b] … dx` | an additive expression up to its differential | — | `∫ x + 1 dx` ⇒ `integrate(x + 1, x)` |
| 4 Multiplicative | `*` (`·` `⋅`), `/` (`÷`), `×`, implicit multiplication | unary expressions | left | `1/2x` ⇒ `(1/2)*x`; `a/b c` ⇒ `(a/b)*c` |
| 5 Additive | `+`, `-` | terms | left | `a - b + c` ⇒ `(a - b) + c` |
| 6 Relational | `=`, `!=`, `<`, `<=`, `>`, `>=`, `in` | additive expressions | none | `a < b < c` is an error |
| 7 Lambda | `->` | parameters; body to the right | right | `x -> x^2`; `(x, y) -> x + y` |
| 8 Definition | `:=` | statement level only | — | `f(x) := x^2 + 1` |

Parenthesised expressions `( )`, sets `{ }`, bracket constructors `[ ]`, vectors `⟨ ⟩` and
absolute-value bars `| |` are primaries.

### 4.2 Grammar summary

EBNF; `{ }` is repetition, `[ ]` is an option, quoted text is a token. Implicit multiplication
is the `factor` alternative of `term`.

```
script      = { [ statement ] terminator } ;
terminator  = ";" | newline | end-of-input ;
statement   = definition | expr ;

definition  = name ":=" expr
            | name "(" name { "," name } ")" ":=" expr ;

expr        = lambda | relation ;
lambda      = params "->" expr ;
params      = name | "(" name { "," name } ")" ;

relation    = additive [ relop additive ] ;
relop       = "=" | "!=" | "<" | "<=" | ">" | ">=" | "in" ;

additive    = term { ( "+" | "-" ) term } ;
term        = unary { ( "*" | "/" | "×" ) unary | factor } ;
factor      = unary ;          (* implicit multiplication; see §9.2 for what may start it *)

unary       = ( "-" | "+" ) unary
            | prefix-name unary              (* prefix application, §7.2 *)
            | radical unary                  (* √ ∛ ∜ *)
            | derivative term                (* §10.1 *)
            | limit-head term                (* §10.3 *)
            | sum-head term | product-head term   (* §10.4 *)
            | integral                       (* §10.2 *)
            | power ;
power       = postfix [ "^" unary ] ;
postfix     = primary { "!" | "%" | "°" | superscript-power } ;

primary     = number | name | constant | domain
            | call
            | "(" expr ")"
            | "{" [ expr { "," expr } ] "}"
            | "[" [ expr { "," expr } ] "]"
            | "⟨" expr { "," expr } "⟩"
            | "|" additive "|" ;
call        = callable-name "(" [ expr { "," expr } ] ")" ;

derivative  = "d/d" name | "d^" digits "/d" name "^" digits
            | "d" sup-digits "/d" name sup-digits ;     (* one token, §10.1 *)
limit-head  = "lim" "[" name "->" additive [ "+" | "-" ] "]" ;
sum-head    = ( "sum" | "Σ" | "∑" ) "[" name "=" additive "," additive "]" ;
product-head= ( "product" | "Π" | "∏" ) "[" name "=" additive "," additive "]" ;
integral    = "∫" [ "[" additive "," additive "]" ] additive differential ;
differential= a name "d" rest, rest itself a name not beginning with "d",
              that is not a builtin name ;   (* "ddx" is an error, §10.2 *)
```

`callable-name` is a name that is a builtin function or command (§12) or a user-defined
function in the current environment (§11.3). `prefix-name` is a name in the prefix set (§7.2).
The parser is given the set of user-defined function names before it parses a statement.
A prefix name followed by `(` is a call: the `call` alternative is tried before
`prefix-name unary`, so `sin (x)^2` ⇒ `sin(x)^2`, not `sin((x)^2)`.

## 5. Postfix operators and calls

Postfix operators bind tightest and apply left to right to the primary before them.

- `!` factorial: `5!` → `120`; `5!!` ⇒ `factorial(factorial(5))` (no double factorial); `-5!`
  ⇒ `-(factorial(5))`; `2x!` ⇒ `2*factorial(x)`.
- `%` percent: `20%` ⇒ `20/100` → `1/5`; `x%` ⇒ `x/100`; `200*10%` → `20`.
- `°` degrees: `30°` ⇒ `30*pi/180` → `π/6`, in every angle mode (§14.2); `(30 + 15)°` → `π/4`.
- Superscript power: `x²` ⇒ `x^2`; `sin(x)²` ⇒ `sin(x)^2`; `x²y` ⇒ `x^2*y`.
- **Call** `f(a, b)`: a callable name immediately followed (whitespace irrelevant) by `(`
  (§11.3). A call is a primary, so its `(` binds tightest of all, before any postfix operator:
  `sin(x)^2` ⇒ `sin(x)^2`, `f(x)!` ⇒ `factorial(f(x))`, and `sin (x)^2` is the same.
  Only a name can be called: `(x -> x^2)(3)` is `)(` multiplication and fails on evaluation
  ("a function is not a factor"); define the function first.

## 6. Power

`^` (alias `**`) is right-associative. Its left operand is a postfix expression; its right
operand is a **unary expression**, so it admits a sign and a prefix application.

- `2^3^2` ⇒ `2^(3^2)` → `512`.
- `-2^2` ⇒ `-(2^2)` → `-4`; `(-2)^2` → `4`.
- `2^-1` ⇒ `2^(-1)`; `x^-n` ⇒ `x^(-n)`; `e^-x^2` ⇒ `e^(-(x^2))`; `2^-3^2` ⇒ `2^(-(3^2))`.
- `e^sin x` ⇒ `e^sin(x)`; `e^iπ` ⇒ `e^i*pi`, the product of `e^i` and `π`: write `e^(iπ)`.
- `2^3!` ⇒ `2^factorial(3)` → `64`; `2^3²` ⇒ `2^(3^2)`; `x²^3` ⇒ `(x^2)^3`.

## 7. Prefix operators

### 7.1 Signs

Unary `-` and `+` take the next unary expression: `-2^2` ⇒ `-(2^2)`, `--x` ⇒ `-(-x)`, `-x!` ⇒
`-(factorial(x))`. After an operand, `+` and `-` are always binary: `a -b` ⇒ `a - b`,
`sin x -y` ⇒ `sin(x) - y`. A sign is accepted in operand position, for example after `*`, `/`,
`^`, `×`, a relation, a comma or an opening bracket: `2*-3` ⇒ `2*(-3)`, `x < -1`.

### 7.2 Prefix application without parentheses

The **prefix set** is: `sin cos tan cot sec csc`, `asin acos atan acot asec acsc` (and their
`arc…` aliases), `sinh cosh tanh coth sech csch`, `asinh acosh atanh acoth asech acsch`, `ln`,
`log`, `log10`, `log2`, `exp`, `sqrt`, `cbrt`. A prefix name not followed by `(` takes **exactly
the next unary expression**: a primary with its postfix operators and its `^` chain, or a
further prefix construct.

| Input | Parse |
|---|---|
| `sin x` | `sin(x)` |
| `sin x^2`, `sin x²` | `sin(x^2)` |
| `sin 2x` | `sin(2)*x` |
| `sin x cos x` | `sin(x)*cos(x)` |
| `2 sin x` | `2*sin(x)` |
| `sin -x` | `sin(-x)` |
| `sin sin x` | `sin(sin(x))` |
| `sin x!` | `sin(factorial(x))` |
| `log 10 x` | `log(10)*x` |
| `sin (x)^2` | `sin(x)^2` (a call, §5) |

Parsing is deterministic and the worksheet echoes the parsed form, which is how a user sees
`sin(2)·x` for `sin 2x` and adds parentheses.

Names outside the prefix set (`abs`, `floor`, `gcd`, `expand`, user functions) need parentheses:
`abs x` and `gcd 4` are errors ("abs needs parentheses"); followed by `+` or `-`, which are binary
there, such a name has no argument (`gcd + 1`, `abs -x`: "gcd needs an argument"). A builtin name
that is neither called nor applied (`sin = 1`, `gcd`, `f` alone for a user function) is an error
("sin needs an argument"; a command of no arguments says "write assumptions()"); the exceptions are
the name arguments of `clear` and `help`. A sign after a prefix name is its operand, so `sin + 1` ⇒
`sin(+1)`, which is `sin(1)`. Powers of function names are not notation: `sin²x` and `sin^-1 x` are
errors with the hint "write sin(x)² or asin(x)".

### 7.3 Radicals

`√`, `∛` and `∜` are prefix **operators**, not function names, and take the next unary
expression like a sign: `√2` ⇒ `sqrt(2)`, `√2x` ⇒ `sqrt(2)*x`, `√(2x)` ⇒ `sqrt(2*x)`,
`2√3` ⇒ `2*sqrt(3)`, `√2√3` ⇒ `sqrt(2)*sqrt(3)`, `√-1` ⇒ `sqrt(-1)`, `√x²` ⇒ `sqrt(x^2)`,
`∜x` ⇒ `root(x, 4)`. Because parentheses after `√` group rather than call, `√(x)^2` ⇒
`sqrt(x^2)`, while `sqrt(x)^2` ⇒ `sqrt(x)^2`.

## 8. Relations, lambdas, definitions

### 8.1 Relations

`=`, `!=` (`≠`), `<`, `<=` (`≤`), `>`, `>=` (`≥`) and `in` (`∈`) relate two additive
expressions. They are **non-associative**: `a < b < c`, `a = b = c` and `0 < x <= 1` are errors
("chained relations are not supported"); write two relations, e.g. `assume(x > 0, x <= 1)`.
A relation is a node; the parser never evaluates it (§14.4).

`in` relates an expression to a domain: `n ∈ ℤ` ⇒ `n in Integers`, `x in Reals`. In Phase 1 its
right side must be one of the four domains; any other right side parses and fails on
evaluation ("membership in a set is not supported yet").

`=` is never assignment. The worksheet accepts one convenience: a top-level `name(args) = body`
where `name` is not defined is taken as a definition and redisplayed with `:=`. That belongs to
the worksheet, not to this grammar; scripts and the CLI read `=` as a relation.

### 8.2 Lambdas

`params -> body` (alias `→`) is an anonymous function. `params` is one name or a parenthesised
list of distinct names; the body extends as far right as possible, up to the end of the
statement, a `,` or a closing bracket.

- `x -> x^2`; `(x, y) -> x + y`; `x -> y -> x + y` ⇒ `x -> (y -> x + y)`.
- `f := x -> x^2` defines `f` exactly as `f(x) := x^2` does.
- `2x -> x` and `y = x -> x` are errors ("lambda parameters must be names"): the left side
  parsed as `2*x` and as `y = x`.
- A parenthesised list is legal only before `->`: `(x, y)` alone is an error.

### 8.3 Definitions

`:=` appears only at statement level, as the statement's top operator:

- `name := expr` binds a name: `a := 5`.
- `name(p₁, …, pₙ) := expr`, n ≥ 1, distinct parameter names, defines a function:
  `f(x) := x^2 + 1`. `f() := …` is an error; use `a := …`.

`:=` anywhere else (`(a := 5)`, `solve(a := 1, a)`, `x -> a := x`) is an error ("`:=` defines
at statement level only"). Defining a constant, a domain, a builtin or a keyword is an error
(`e := 3`, `sin := 2`, `if := 1`). Redefining a user name replaces its definition. Definitions
are delayed (§14.3).

While the body of `f(params) := body` or `f := params -> body` is parsed, `f` counts as a user
function, so `f(x) := x f(x - 1)` parses `f(x - 1)` as a call. Without conditionals such a
definition can only recurse forever, so it fails at use with the cycle error (§14.3) until Phase 9
adds `if`.

## 9. Multiplication

### 9.1 Explicit

`*` (aliases `·`, `⋅`) and `/` (alias `÷`) are left-associative at one level with implicit
multiplication:

- `1/2x` ⇒ `(1/2)*x`; `a/b c` ⇒ `(a/b)*c`; `1/2π` ⇒ `(1/2)*pi`. Write `1/(2x)`.
- `x·y` ⇒ `x*y`: the same node as `x y`.
- `*` between two vectors is the dot product, between matrices the matrix product (§13.5).

### 9.2 Implicit multiplication

Two adjacent factors with no operator between them multiply, at the multiplicative level. The
second factor may start with any token that starts a unary expression, **except**:

- `+` or `-`: after an operand these are binary (`a -b` ⇒ `a - b`);
- **a number literal**: `2 3`, `x 2`, `x²2`, `5 % 3` and `π2` are errors ("a number cannot
  follow a factor; write 2x or x*2"). This guards against `2 3` meaning 6 and `5 % 3` meaning
  3/20;
- `[`: a `[` directly after an operand is reserved for indexing (§13.6), so `v[1]`, `(a)[1]`
  and `2 [1, 2]` are errors; write `2*[1, 2]` or `2⟨1, 2⟩`.

So implicit multiplication applies between a number and what follows it (`2x`, `2(x+1)`, `2π`,
`2√3`, `3i`), between brackets (`(a)(b)`, `(x+1)y`, `|x||y|`), between names separated by
whitespace (`x y`, `2 sin x`), after postfix operators (`x²y`, `20%x`) and after a closed
notation form (`∫ x dx y`). It never splits an identifier: `xy` is one symbol.

| Input | Parse |
|---|---|
| `2x` | `2*x` |
| `2(x+1)` | `2*(x + 1)` |
| `(x-1)(x+1)` | `(x - 1)*(x + 1)` |
| `x y` | `x*y` |
| `xy` | the symbol `xy` |
| `2 3` | error |
| `x(x+1)`, `x (x+1)` | `x*(x + 1)` when `x` is not a function (§11.3) |

### 9.3 Name followed by `(`

`name(` is a **call** if and only if `name` is a builtin function or command or a user-defined
function in the current environment; otherwise it is implicit multiplication. Whitespace does
not matter (§11.3).

### 9.4 Cross product `×`

`×` is its own multiplicative-level operator, left-associative with the others, and parses to
`cross(a, b)`. It evaluates to ordinary multiplication when either operand is a scalar and to
the cross product when both are 3-vectors; anything else is an evaluation error.

- `u × v` ⇒ `cross(u, v)`; `[1, 0, 0] × [0, 1, 0]` → `⟨0, 0, 1⟩`.
- `2 × 3` ⇒ `cross(2, 3)` → `6`.
- `a × b c` ⇒ `cross(a, b)*c`.

## 10. Notation

Each notation parses to the same node as its command form; the printer chooses which to show.

| Notation | Command form | Operand |
|---|---|---|
| `d/dx f` | `diff(f, x)` | rest of the term |
| `d^2/dx^2 f`, `d²/dx² f` | `diff(f, x, 2)` | rest of the term |
| `∫ f dx` | `integrate(f, x)` | additive, up to `dx` |
| `∫[a, b] f dx` | `integrate(f, x, a, b)` | additive, up to `dx` |
| `lim[x → a] f` | `limit(f, x, a)` | rest of the term |
| `lim[x → a+] f`, `lim[x → a-] f` | `limit(f, x, a, right)`, `limit(f, x, a, left)` | rest of the term |
| `Σ[k = a, b] f`, `∑[…]`, `sum[…]` | `sum(f, k, a, b)` | rest of the term |
| `Π[k = a, b] f`, `∏[…]`, `product[…]` | `product(f, k, a, b)` | rest of the term |
| `\|x\|` | `abs(x)` | inside the bars |
| `√x`, `∛x`, `∜x` | `sqrt(x)`, `cbrt(x)`, `root(x, 4)` | next unary |
| `x²`, `x⁻¹` | `x^2`, `x^(-1)` | postfix |
| `30°` | `30*pi/180` | postfix |
| `20%` | `20/100` | postfix |
| `5!` | `factorial(5)` | postfix |

**Rest of the term.** A big operator (`d/dx`, `lim`, `Σ`, `Π`) takes everything up to the end of
the multiplicative term it stands in: up to a `+`, `-`, relation, `,`, closing bracket, closing
`|` or the end of the statement. It ends the enclosing term too.

- `lim[x→0] sin(x)/x` ⇒ `limit(sin(x)/x, x, 0)` → `1`.
- `d/dx x^2 sin x` ⇒ `diff(x^2*sin(x), x)`; `d/dx x^2 + 1` ⇒ `diff(x^2, x) + 1`.
- `Σ[k=1,n] k^2 + 1` ⇒ `sum(k^2, k, 1, n) + 1`; `2 Σ[k=1,n] k x` ⇒ `2*sum(k*x, k, 1, n)`.
- `a/Σ[k=1,n] k b` ⇒ `a/sum(k*b, k, 1, n)`; `e^Σ[k=1,n] k x` ⇒ `e^sum(k*x, k, 1, n)`.
- To stop early, parenthesise: `(d/dx x^2) y`.

### 10.1 Derivatives

The derivative notation is **one token**, recognised only in these exact shapes with no
whitespace inside: `d/d` + name, `d^n/d` + name + `^n`, or `d` + superscript digits + `/d` +
name + the same superscript digits. `n` is a positive integer literal and both orders must be
equal (`d^2/dx^3` is an error). The notation is committed once its head is read (`d/d` + name,
`d^n/d` + name, or `d` + superscript digits + `/d` + name): anything after the name but the exact
shape, such as `d^2/dx²`, `d^2/dx`, `d/dx²` or `d/dx^2`, is an error ("the derivative notation is
d/dx, d^n/dx^n or dⁿ/dxⁿ"). A text that does not begin with such a head is ordinary tokens:
`d^2 + 1`.

- `d/dx f` ⇒ `diff(f, x)`; `d/dθ` ⇒ variable `θ`; `d/dx₁` ⇒ variable `x_1`.
- `d^2/dx^2 sin x` ⇒ `diff(sin(x), x, 2)`; `d²/dx² sin x` is the same.
- `d/dx (x^2 sin x)` ⇒ `diff(x^2*sin(x), x)`.
- `d` itself is an ordinary identifier and `d(f, x)` is not a command: with `d` undefined,
  `d(x+1)` ⇒ `d*(x + 1)`. `d / dx` with spaces is `d` divided by `dx`. The derivative command
  is `diff`.
- `d/dx` with nothing after it is an error ("expected an expression to differentiate").

### 10.2 Integrals

`∫` is followed by optional bounds `[a, b]` (a `[` right after `∫` is always the bounds), an
**integrand** parsed as an additive expression, and a **differential**: a name of the form `d` +
rest, with rest itself a name (it begins with a letter or `_`) and the whole name not a builtin
(`diff`, `denominator` and `dot` are not differentials; `d2` is not one either). The integration
variable is the rest: `dx` → `x`, `dθ` → `θ`, `dx₁` → `x_1`.

- `∫ x e^x dx` ⇒ `integrate(x*e^x, x)`; `∫[0,1] x^2 dx` ⇒ `integrate(x^2, x, 0, 1)`;
  `∫[0, ∞] e^-x dx` ⇒ `integrate(e^(-x), x, 0, inf)`.
- `∫ x + 1 dx` ⇒ `integrate(x + 1, x)`; `∫ x dx + 1` ⇒ `integrate(x, x) + 1`;
  `2∫ x dx` ⇒ `2*integrate(x, x)`.
- The first differential at the integral's own bracket depth ends the integrand and every
  construct opened inside it, and belongs to the innermost open integral: `∫∫ x y dx dy` ⇒
  `integrate(integrate(x*y, x), y)`; `∫ d/dx x^2 dx` ⇒ `integrate(diff(x^2, x), x)`.
- After the differential the integral is complete and acts as one factor: `∫ x dx y` ⇒
  `integrate(x, x)*y`. A `^` or postfix operator directly after the differential is an error
  ("parenthesise the integral"): write `(∫ x dx)^2`.
- **Limitation:** inside an integrand no name of the form `d` + something can appear, whether a
  variable (free or bound) or the head of a user-function call, since such a name ends the
  integrand: `∫ dist(x) dx` ends at `dist`, and `∫ ∑[dk = 1, n] dk x dx` ends at `dk`. The
  integral's own variable cannot begin with `d` in the notation either (`∫ x ddx` is an error);
  use the command form, `integrate(dist(x), x)`, `integrate(x, dx)`. `d` alone is fine.
- `∫ x` with no differential is an error ("expected a differential such as dx");
  `∫ dx/x` is not supported (write `∫ 1/x dx`).
- Inside brackets a newline is whitespace and the integrand continues. At statement level the
  statement ends at the newline, so a differential on the next line is the error "expected a
  differential such as dx".

**Numeric values.** A definite integral the exact path leaves unevaluated (no closed form found)
stays an integral, except under `numeric(…)` or a decimal or scientific number format in the
worksheet, where it is evaluated numerically when its bounds are numbers or `±∞` (`π`, `√2` and
other constants included) and its integrand holds no symbol but its variable. The value is a decimal
of `numeric`'s digits (the session precision by default) or of the format's digits, and the steps
say so: "No closed form found; the value is numeric, N digits". So `numeric(∫[0, 1] e^(-x^2) dx)` →
`0.7468241328…` and `numeric(integrate(e^(-x^2), x, -∞, ∞), 10)` → `1.772453851`, while
`∫[0, 1] e^(-x^2) dx` alone, in the exact format, is the integral itself. A value that cannot be
found to those digits leaves the integral, with the step "The numeric quadrature did not converge":
a divergent integral (`e^(x^2)/x` on `[0, 1]`), a point inside the interval where the integrand is
undefined, an integrand too singular at an end or decaying too slowly toward an infinite one for the
precision, and an oscillating or non-smooth one whose values do not settle (`sin(x^2)/x^2` on `[0,
∞]`, `e^(x^2)/(x − 0.3)` on `[0, 1]`; split the interval at a kink or a pole). A value is also
checked against a second, adaptive rule in machine precision, and left when the two disagree: the
double-exponential rule sees the integrand only at its nodes, so a feature narrower than their
spacing can be missed (`e^(-10^6 (x - 0.3)^2) + 1` on `[0, 1]` is left, not given as `1.0`); a
feature that both rules miss is the remaining limit. At most 100 digits are computed: `numeric(…,
101)` of such an integral leaves it, with a step that names the cap. In degrees and gradians an
integrand with a circular or inverse circular function of the variable is left, with its own step,
since the numeric values are taken in radians. An integral nested in a larger expression is
evaluated the same way but brings no step.

### 10.3 Limits

`lim[ name → target ]` with `->` or `→`; the target is an additive expression, optionally
followed by `+` (from the right) or `-` (from the left) immediately before `]`. Inside these
brackets the arrow means "approaches", never a lambda.

- `lim[x→0] sin(x)/x` ⇒ `limit(sin(x)/x, x, 0)`.
- `lim[x → 0+] 1/x` ⇒ `limit(1/x, x, 0, right)`; `lim[x → 0-] 1/x` ⇒ `limit(1/x, x, 0, left)`.
- `lim[x → ∞] (1 + 1/x)^x` ⇒ `limit((1 + 1/x)^x, x, inf)`; `lim[x -> -inf] e^x`.
- `lim` without `[` is an error ("write lim[x → a] f or limit(f, x, a)").

### 10.4 Sums and products

`Σ[k = a, b] f` (also `∑`, `sum`) and `Π[k = a, b] f` (also `∏`, `product`). Inside the
brackets `k = a` names the index; it is not a relation.

- `Σ[k=1,n] k^2` ⇒ `sum(k^2, k, 1, n)`; `sum[k=1,n] 1/k` ⇒ `sum(1/k, k, 1, n)`.
- `Π[k=1,n] k` ⇒ `product(k, k, 1, n)`; `Σ[i=1,n] i` ⇒ `sum(i, i, 1, n)` (`i` is the index,
  §11.2).
- `Σ`, `∑`, `Π` and `∏` not followed by `[` are errors ("write Σ[k = a, b] f or
  sum(f, k, a, b)"); `sum(` and `product(` are the command calls.

### 10.5 Absolute value

A `|` closes the innermost open bar at the current bracket depth if there is one; otherwise it
opens a new one. Brackets start a new depth.

- `|x|` ⇒ `abs(x)`; `|x - 1|^2` ⇒ `abs(x - 1)^2`; `2|x|` ⇒ `2*abs(x)`.
- `|x| |y|` and `|x||y|` ⇒ `abs(x)*abs(y)`.
- Bars do not nest without brackets: `|a|b||` reads `|a|`, `b`, then `||`, an error ("empty
  absolute value"); `|x - |y||` is an error ("expected an operand"); `||x||` is an error.
  Write `abs(a·abs(b))` or `|a·(|b|)|` ⇒ `abs(a*abs(b))`.

## 11. Names and scope

### 11.1 Constants

`pi`/`π`, `e`, `i`, `inf`/`infinity`/`∞`, `true`, `false`, `undefined` and the domains are
constants. They are symbolic until a numeric value is asked for (`2π` stays `2π`). They cannot
be defined. `undefined` is the value of an operation with no defined result (§14.1).

### 11.2 Bound variables

A **binder** introduces a variable that is local to some of its arguments: the index of a sum or
product, the variable of a limit, a derivative, an integral, `solve` and `taylor`, a lambda's
parameters and a function definition's parameters. Inside its scope a bound name means the bound
symbol, never a definition from the environment and never a constant.

`e` and `i` may be bound only where the binding cannot escape into the result:

| Binder | `e` or `i` allowed | Example |
|---|---|---|
| `Σ`, `Π`, `sum`, `product` | yes | `Σ[i=1,n] i` ⇒ `sum(i, i, 1, n)` → `n·(n + 1)/2` |
| `lim`, `limit` | yes | `lim[e → 0] (1 + e)^(1/e)` |
| `∫[a, b] … d…`, `integrate` with bounds | yes | `∫[0,1] i^2 di` → `1/3` |
| `solve` | yes | `solve(i^2 = 4, i)` → `{-2, 2}` |
| lambda and definition parameters | yes | `i -> i^2`; `f(e) := e + 1` |
| `d/di`, `diff(…, i)`, indefinite `∫ … di`, `taylor(…, i, …)` | **no**: error | `d/di i^2` |

Outside a binder, `i` is the imaginary unit and `e` is Euler's number. Other constants, domains,
builtins and keywords cannot be bound. A command's variable argument is a name, possibly
parenthesised or signed with `+` (which change nothing): `integrate(i^2, (i), 0, 1)` binds `i` as
`integrate(i^2, i, 0, 1)` does; `solve` also takes a list of names, `[x, y]` or `⟨x, y⟩`. The parser
resolves constant versus bound symbol itself, using the binder positions in §12.

### 11.3 Functions versus products

`name(` is a call if and only if `name` is a builtin function or command (§12) or a
**user-defined function** in the current environment: a name defined with `name(params) := …`
or as `name := params -> …`. Otherwise `name(…)` is implicit multiplication, whatever the
whitespace:

- `x(x+1)` ⇒ `x*(x + 1)` when `x` is not a function; `3(x+1)` ⇒ `3*(x + 1)`.
- After `f(x) := x^2`, `f(2)` and `f (2)` are calls → `4`.
- `e(x+1)` ⇒ `e*(x + 1)`: constants are not callable.
- Parameters are not functions: in `g(h, x) := h(x)`, `h(x)` ⇒ `h*x`.
- In a definition `f(params) := body`, `f` counts as a user function while the body is parsed
  (§8.3).

**Limitation (Phase 1):** there are no unknown functions. An undefined `f(x)` is `f·x`, so
`d/dx f(x)` → `f`. Unknown functions (the unknowns of differential equations in Phase 7) will
be declared inside the commands that need them.

Because the parse depends on the defined names, a statement is parsed with the names defined
before it. A stored definition keeps the parse it was given: `g(x) := f(x) + 1` written before
`f` is defined stores `f*x + 1`, and stays so until `g` is defined again. Conversely, a stored
call whose head has since been redefined as a value fails at use: after `f(t) := t^2`,
`g(x) := f(x)` (a call, since `f` was a function then) and then `f := 3`, `g(2)` is the error
"f is not a function". The corpus and the round-trip rule (§15.4)
use the empty environment, which holds the builtins only.

## 12. The Phase 1 namespace

Functions and commands share one namespace and one call syntax. A **function** evaluates its
arguments and then itself when they permit, and otherwise stays symbolic (`sin(x)` stays
`sin(x)`). A **command** declares **held** argument positions: a held argument is passed as
parsed, the command decides what of it to evaluate, and a bound variable in it is never
substituted from the environment. Arity errors are parse errors (`sin(1, 2)`: "sin takes 1
argument"). **Prefix** marks the prefix set (§7.2).

### 12.1 Functions

| Name | Arity | Prefix | Alias | Notes |
|---|---|---|---|---|
| `abs` | 1 | | `\|x\|` | |
| `sign` | 1 | | | −1, 0 or 1 |
| `floor`, `ceil`, `trunc` | 1 | | | |
| `round` | 1 | | | half away from zero |
| `mod` | 2 | | | `mod(a, b)`; infix `mod` reserved |
| `gcd`, `lcm` | 2 or more | | | integers or polynomials, §14.9 |
| `factorial` | 1 | | `n!` | |
| `binomial` | 2 | | | `binomial(n, k)` |
| `sqrt` | 1 | yes | `√` | |
| `cbrt` | 1 | yes | `∛` | |
| `root` | 2 | | `∜x` = `root(x, 4)` | `root(x, n)`, the n-th root |
| `exp` | 1 | yes | | `exp(x)` is `e^x` |
| `ln` | 1 | yes | | natural logarithm |
| `log` | 1 or 2 | yes (1) | | `log(x)` is **natural**; `log(x, b)` base `b` |
| `log10`, `log2` | 1 | yes | | |
| `sin cos tan cot sec csc` | 1 | yes | | angle mode applies (§14.2) |
| `asin acos atan acot asec acsc` | 1 | yes | `arcsin` … `arccsc` | |
| `atan2` | 2 | | | `atan2(y, x)` |
| `sinh cosh tanh coth sech csch` | 1 | yes | | |
| `asinh acosh atanh acoth asech acsch` | 1 | yes | | |
| `re`, `im`, `conj`, `arg` | 1 | | | |
| `min`, `max` | 1 or more | | | |
| `numerator`, `denominator` | 1 | | | of a number, or structural (§14.9) |
| `nth` | 2 | | | `nth(c, k)`, 1-based, of a list or vector |
| `length` | 1 | | | elements of a list, vector or set |
| `dot` | 2 | | `u * v` for vectors | dot product |
| `cross` | 2 | | `u × v` | cross product; ordinary product for scalars |

### 12.2 Commands

| Name | Arity | Held | Binds | `e`/`i` bindable | Notes |
|---|---|---|---|---|---|
| `solve` | 2 | 1, 2 | 2 | yes | `solve(rel \| {rels}, var \| [vars])`; §14.11 |
| `diff` | 2 or 3 | 1, 2 | 2 | no | `diff(f, x[, n])`; notation `d/dx` |
| `integrate` | 2 or 4 | 1, 2 | 2 | with bounds only | `integrate(f, x[, a, b])`; notation `∫` |
| `limit` | 3 or 4 | 1, 2, 4 | 2 | yes | `limit(f, x, a[, left \| right])`; notation `lim` |
| `sum` | 4 | 1, 2 | 2 | yes | `sum(f, k, a, b)`; notation `Σ` |
| `product` | 4 | 1, 2 | 2 | yes | `product(f, k, a, b)`; notation `Π` |
| `taylor` | 4 | 1, 2 | 2 | no | `taylor(f, x, a, n)`; Phase 4 |
| `expand`, `factor`, `cancel`, `simplify` | 1 | — | | | `expand`, `factor`, `cancel`: §14.9 |
| `collect` | 2 | 2 | | | `collect(e, x)`, §14.9 |
| `apart` | 2 | 2 | | | `apart(e, x)`, partial fractions in the symbol `x`, §14.9 |
| `subs` | 2 or more | all | | | `subs(e, x = v[, y = w …])` |
| `assume` | 1 or more | left sides | | | `assume(rel[, rel …])`; each relation's left side is held, its right side evaluated; returns the set of the relations recorded |
| `assumptions` | 0 | | | | the set of current assumptions |
| `numeric` | 1 or 2 | — | | | `numeric(e[, digits])` |
| `exact` | 1 | — | | | a decimal to the rational it denotes |
| `steps` | 1 | 1 | | | the value of `e`, with its steps (§14.7): each one idea of the working, standing for a run of the evaluation's rewrites, the operation's steps (`expand`, `factor`, `diff`, `solve`) first and then the simplifier's |
| `clear` | 1 | 1 | | | `clear(name)` unbinds `name`; returns the symbol |
| `help` | 1 | 1 | | | reserved; not available yet |

The bounds of `integrate`, `sum` and `product`, the point of `limit` and the orders of `diff`
and `taylor` are evaluated. `left` and `right` in `limit` are literal words in that position
only. A held argument is evaluated, when the command evaluates it, in the environment without
the command's bound variables: after `x := 3`, `solve(x^2 = 4, x)` → `{-2, 2}`, and after
`a := 2`, `solve(x^2 = a, x)` → `{-√2, √2}`. `subs` makes the substitution in the held
expression as parsed and then evaluates the result: after `a := 5`, `subs(x^2 + a, a = 2)` →
`x² + 2`. The substitution is in the held parse only: with `a := 5` and `b := a + 1`,
`subs(b, a = 2)` → `6`, because `b` as parsed contains no `a`; write `subs(a + 1, a = 2)` for
`3`.

### 12.3 Reserved later-phase names

These names are builtins now: they parse as calls and evaluate to an error ("`rref` is not
available yet"). They cannot be defined.

| Name | Phase |
|---|---|
| `det`, `rref`, `eigenvalues` | 5 |
| `grad` | 7 |
| `slider` | 3, evaluated by the canvas, not the engine |

`slider` stays unavailable to the engine and the CLI. In the app's worksheet, a statement whose
whole right side is the call, `name := slider(min, max, step)`, defines a slider on the canvas
instead of reaching the engine: the three arguments are evaluated to exact decimals (an integer, a
rational with a terminating expansion or a decimal; they may not read a slider or a point), the
name is bound to the slider's value, and the cell echoes the statement as typed. The slider and the
canvas's named points are variables of every cell that does not define the name itself, a point
`A` as the vector of its coordinates (`nth(A, 1)` is its abscissa); `docs/reference/app.md`,
**Canvas variables**, says how.

## 13. Collections

### 13.1 Sets

`{a, b, c}` is a **Set**: unordered, duplicates removed on evaluation. `{}` is the empty set.
`solve` returns sets. Sets carry no arithmetic.

### 13.2 Bracket constructors

`[e₁, …, eₙ]` builds a **Vector**, a **Matrix** or a **List**, by one rule that the constructor
applies to its elements, both when the parser builds the node and again after evaluation:

- **Vector** when every element is a scalar expression: a number, a constant other than
  `true`, `false` and the domains, a symbol, or arithmetic and function application over them.
  Symbols count as scalars; booleans and domains do not.
- **Matrix** when every element is a vector (from `[…]` or `⟨…⟩`) and all have the same length.
- **List** otherwise: ragged rows, or elements that are relations, sets, lists, matrices,
  booleans, domains or lambdas.
- `[]` is the empty List.

| Input | Node |
|---|---|
| `[1, 2, 3]`, `[x, 2y]` | Vector |
| `[[1, 2], [3, 4]]` | 2×2 Matrix |
| `[[1, 2]]` | 1×2 Matrix |
| `[[1], [2]]` | 2×1 Matrix |
| `[[1, 2], [3]]` | List of vectors |
| `[x = 1, y = 2]`, `[{1}, 2]` | List |

Because classification is re-applied after evaluation, `[a, b]` is a Vector when parsed and a
List after `a := {1, 2}`.

### 13.3 Angle-bracket vectors

`⟨a, b, c⟩` always builds a Vector. It must have at least one element and every element must be
scalar; `⟨⟩` and `⟨[1, 2], [3, 4]⟩` are errors.

### 13.4 Printing

The Unicode printer prints vectors as `⟨2x, 2y⟩`, matrices as `[[1, 2], [3, 4]]`, and lists and
sets as written, each element in its own form (`[⟨1, 2⟩, ⟨3⟩]`). The ASCII printer prints
vectors as `[2*x, 2*y]`. Both forms parse back to the same node.

### 13.5 Arithmetic

Vectors and matrices have linear-algebra arithmetic: vector sum, scalar multiple, `*` between
matrices or a matrix and a vector is the matrix product, `*` (and implicit multiplication)
between two vectors is the dot product, `×` between 3-vectors the cross product, and
`M^n` the matrix power (`M^-1` the inverse); a vector or a matrix is never an exponent. Element-wise
operations are named functions, not operators. Lists, sets, truth values and the domains carry no
arithmetic.

### 13.6 Indexing

There is no indexing syntax in Phase 1: `v[1]` is an error ("indexing is reserved; use
nth(v, 1)"). `nth(v, 1)` is the first element.

## 14. Evaluation

### 14.1 Exact by default

- Integer literals are integers; `/` between integers is an exact rational: `1/3 + 1/6` →
  `1/2`. `sqrt(8)` simplifies to `2√2`, not a decimal.
- **Decimal literals are inexact** numbers: `0.5` is not `1/2`. The parser keeps every digit of a
  decimal literal, up to 1,000,000 of them (§3.1): `0.10000000000000000000000000000001` holds
  exactly those digits. The session precision governs arithmetic on inexact numbers, not how
  literals are read. Arithmetic mixing an inexact number with exact ones is inexact: `0.5 + 1/3` →
  `0.8333…`. What folds to a decimal is arithmetic of numbers, a builtin function of a decimal
  (`exp(2.0)`, `sin(0.5)`), and a power with a decimal on either side whose other side is a
  number, `e` or `π` (`2^(0.5)`, `e^(2.0)` → `7.389…`, `π^(0.5)`, `2.0^π`). A constant, a radical
  or a function value beside a decimal in a sum or a product stays as it is: `2.0π`, `π + 0.5`,
  `1.5√2`.
- `exact(0.5)` → `1/2`; `exact(0.1)` → `1/10`. `numeric(1/3)` → `0.333…` at the session
  precision; `numeric(π, 50)` → π to 50 digits. `numeric` keeps integer exponents and a root's
  integer degree exact, and a root stays a root, never a power with an exact rational exponent:
  `numeric(x^2)` → `x²`, `numeric(root(5, 3))` → `1.7099…`, `numeric(root(x, 3))` → `root(x, 3)`,
  `numeric(root(x^2 + 1/3, 3))` → `root(x² + 0.333…, 3)`. A definite integral with no closed form
  and numeric bounds is evaluated numerically (§10.2); every other unevaluated form keeps its
  variable, bounds and point, its body made numeric.
- Constants stay symbolic until a numeric value is requested: `2π` → `2π`.
- `1/0`, `0/0`, `0·∞` and `∞ - ∞` evaluate to the constant `undefined`, which propagates
  through arithmetic: `undefined + 1` → `undefined`.
- **Precision** is a session setting (digits), used only where an inexact result arises or is
  requested.

### 14.2 Angle mode

The angle mode (radians, degrees or gradians) is a session setting. In degrees or gradians, a
trigonometric function converts its argument from the mode's unit only when the argument
evaluates to a **plain number**: an integer, rational or decimal with no constants. Inverse
trigonometric functions of plain numbers return the mode's unit.

| Input | Radians | Degrees |
|---|---|---|
| `sin(30)` | `sin(30)` | `1/2` |
| `sin(30°)` | `1/2` | `1/2` (the argument is `π/6`, not plain) |
| `sin(π)` | `0` | `0` (not plain: read as radians) |
| `sin(15 + 30°)` | `sin(15 + π/6)` | read as radians, since `15 + π/6` is not plain |
| `asin(1/2)` | `π/6` | `30` |

`30°` is `π/6` in every mode; `°` is multiplication by `π/180`.

A real argument outside an inverse function's real domain gives its principal complex value, by
counterclockwise continuity (the principal formulas, as Mathematica computes them): `asin(2.0)` is
`π/2 − 1.3169…i` and `atanh(2.0)` is `0.5493… − 1.5707…i`.

The conversion is exact, and the exact values in this table (`1/2`, `0`, `π/6`, `30`) come from
the simplifier's trigonometric rules (§14.8): in degrees `sin(30)` is first `sin(π/6)` and
`asin(1/2)` is `180·asin(1/2)/π`, which simplify to `1/2` and `30`; an angle with no exact value
keeps the conversion (`sin(1)` in degrees is `sin(π/180)`, `asin(1/3)` is `180·asin(1/3)/π`), while
`sin(30.0)` is `0.5` and `asin(0.5)` is `30.0`. The conversion is applied once, when the call is
evaluated; the printed value does not record the mode, so a value holding an inverse function of a
plain number, typed in again in degrees, is converted again.

An argument with a symbol takes the unit of the number put into it when the value is used (a
plotted value at each point, a function's body at its argument): a plain number is read in the
mode's unit. So in degrees and gradians the simplifier treats such an argument by its shape:

- built from numbers and symbols alone, it is a plain number at every plain value and is read in the
  mode's unit; the simplifier uses the mode's half turn (180 or 200): `sin(x + 180)` → `-sin(x)`,
  `sin(x + 90)` → `cos(x)`, `acos(-x)` → `180 - acos(x)`, and `sin(360n + 30)` → `1/2` for an
  integer `n` (a plain number left over is converted as the evaluator converts it);
- holding `π` (or `e`) as a term or as one factor of a term, as in `x + π` or `πk`, it is plain
  only where every such term's coefficient is 0, since a sum or product of a number and a constant
  stays (§14.1), so it is read in radians; the radian shifts apply where what they leave is read in
  radians wherever the argument was, that is where it is 0, has no plain term, or holds `π` only
  with fixed coefficients: `cos(πk)` → `(-1)^k` and `sin(x + 7π/3)` → `sin(x + π/3)`, while
  `sin(x + π)` stays, since `-sin(x)` would be read in degrees, and so does `sin(πy + x + π/2)`,
  since `cos(x + πy)` is read in degrees at `y = 0`. The inverse functions' reflections do not
  apply to such an argument: `acos(-πx)` stays, which is 90 at `x = 0`;
- otherwise, as `√x + 180`, whether it is plain at use depends on the value put in (`√4` is plain,
  `√2` is not), and the simplifier leaves the identities that carry an angle alone.

A rewritten value assumes that its symbols receive plain numbers when
it is used: given one that is not plain, such as `π`, the value `-sin(x)` is `-sin(π)` in radians,
0, where `sin(π + 180)` written out is read in radians as `sin(π + 180 radians)`.

### 14.3 Definitions

- `a := 5` binds a name; `f(x) := x^2 + 1` defines a function; later statements see them.
- Definitions are **delayed**: the right side is stored as parsed and evaluated at each use, so
  after `a := 1; b := 2a; a := 3`, `b` → `6`.
- A definition that depends on itself, directly or through others, is an error when used
  ("`a` is defined in terms of itself").
- A function's parameters shadow definitions: after `x := 3`, `f(x) := x^2` still squares its
  argument.
- `clear(a)` unbinds `a`. A symbol with no binding is itself.
- A definition statement prints an echo of what it defined: `f(x) := x^2 + 1`.

### 14.4 Relations

Relations are nodes. A relation between two numbers evaluates to `true` or `false` (`2 < 3` →
`true`, `2 ∈ ℤ` → `true`); otherwise it stays a relation (`x < 3`, `n ∈ ℤ`). An exact and an
inexact number compare by value: `1 = 1.0` → `true`. `<`, `<=`, `>` and `>=` with a complex
operand are an evaluation error ("complex numbers are not ordered").

### 14.5 Commands, hold and unevaluated forms

Commands receive their held arguments as parsed (§12.2), so `solve(x^2 = 4, x)` receives the
equation. A command that cannot complete returns its own node (`Integral`, `Limit`, `Sum`,
`Derivative`), printed with its notation and evaluable numerically later; no command returns a
string or fails silently. A command's result is not substituted again with its bound variables,
except a derivative's, whose variable is free in its result (§14.10).

### 14.6 Assumptions

`assume` records facts about symbols, which rules consult: `assume(x > 0)`, `assume(x >= 0,
x <= 1)` for a range, `assume(n ∈ ℤ)` for a domain, `assume(mod(n, 2) = 0)` for parity.
`assume` holds the left side of each relation and evaluates its right side, so after `x := 3`,
`assume(x > 0)` is about the symbol `x`, and after `a := 5`, `assume(x < a)` records `x < 5`. A
symbol on the right is evaluated too: after `x := 3`, `assume(0 < x)` is `0 < 3`, which is not an
assumption about a symbol (an error).
Symbols are real unless assumed otherwise: `assume(z ∈ ℂ)` makes `z` complex.

### 14.7 Result shapes

- `solve` for one variable returns a set of values: `{2, 3}`. For several variables it returns a
  set of lists of equations: `solve({x + y = 3, x - y = 1}, [x, y])` → `{[x = 2, y = 1]}`. A
  numeric solution is a decimal at the session precision: `solve(x^5 - x - 1 = 0, x)` →
  `{1.1673…, …}`. When no method applies, `solve` returns itself (§14.11).
- A set whose members are all constants is shown in ascending order of value, `-∞` first and `∞`
  after the real members, the non-real members last: `{-√2, √2}`, `{-i, i}`.
- A general solution (Phase 6) uses a fresh integer parameter, `k` (or `k₁`, `k₂` when taken),
  assumed in `ℤ`: `{2πk + π/6, 2πk + 5π/6}`. Until then a periodic equation returns its call
  (§14.11).
- An indefinite integral (Phase 4) returns an antiderivative without a constant: `∫ x dx` →
  `x²/2`. The worksheet may display `+ C`.
- `steps(e)` returns the value of `e`; the trace of the evaluation is attached to the output,
  and the CLI prints it before the value.

The detailed rules belong to the commands' own references.

### 14.8 Simplified forms

Every value is simplified, and the simplifier settles on one form for each family: logarithms are
natural (`ln(x)` → `log(x)`; `log10(x)`, `log2(x)` and `log(x, b)` → `log(x)/log(b)`, or a
rational when the value is one exactly: `log(8, 2)` → `3`); `exp(x)` → `e^x`; `root(x, n)` and
`cbrt(x)` of a non-negative radicand are powers (`cbrt(8)` → `2`, `root(x, 4)` → `x^(1/4)` when
`x ≥ 0`); numeric radicals are reduced and rationalised (`sqrt(8)` → `2√2`, `1/sqrt(2)` → `√2/2`).
The circular functions take exact values at multiples of `π/12` (`sin(π/6)` → `1/2`, `tan(π/2)` →
`undefined`), lose whole turns, half turns and quarter turns of their angle (`cos(x + π)` →
`-cos(x)`, `sin(x + π/2)` → `cos(x)`), and take the sign out of an argument whose leading term
is negative (`sin(y - x)` → `-sin(x - y)`, `cos(-x)` → `cos(x)`); a product of them is written with
the fewest functions (`sin(x)/cos(x)` → `tan(x)`, `1/cos(x)` → `sec(x)`), and the Pythagorean and
double-angle identities collapse (`sin(x)² + cos(x)²` → `1`, `1 + tan(x)²` → `sec(x)²`, `2 sin(x)
cos(x)` → `sin(2x)`), never the reverse. Quotients of polynomials lose their common factors and
fractions combine when that is no larger (§14.12).
The rules and their conditions are in `docs/reference/engine.md`, **Simplification**.

### 14.9 Polynomial algebra

The algebra commands work on the evaluated, canonical value of their argument, read as a
polynomial, or a quotient of polynomials, with rational coefficients. `expand`, `factor`, `collect`
and `cancel` take a relation, list, set, vector or matrix item by item (a matrix entry by entry):
`cancel((x^2 - 1)/(x - 1) = 0)` → `x + 1 = 0`.

- `expand(e)` multiplies out every product of sums and every positive integer power of a sum,
  everywhere in `e`, function arguments included: `expand((x + 1)^3)` → `x³ + 3x² + 3x + 1`,
  `expand(sin((x + 1)^2))` → `sin(x² + 2x + 1)`. A negative or fractional power of a sum stays,
  and a product with one distributes over it: `expand((x + 1)^2/(x - 1))` → `x²/(x - 1) + 2x/(x -
  1) + 1/(x - 1)`.
- `factor(e)` factors over the rationals. A sum is brought over a common denominator and its
  numerator and denominator factored; a product is factored factor by factor and its common
  factors cancelled, so a quotient comes out in lowest terms: `factor(x^4 - 1)` → `(x - 1)(x +
  1)(x² + 1)`, `factor(2x^2 + 4x + 2)` → `2(x + 1)²`, `factor((x^2 - 1)/(x^2 + 2x + 1))` → `(x -
  1)/(x + 1)`, `factor(1/x + 1/y)` → `(x + y)/(x y)`. An input already factored is returned as it
  is.
- `collect(e, x)` expands `e` and groups it by the powers of `x`, which may be any kernel:
  `collect(a sin(x)^2 + sin(x)^2 + 3, sin(x))` → `(a + 1) sin(x)² + 3`.
- `cancel(e)` brings `e` over a common denominator in lowest terms: `cancel((x^2 - 1)/(x + 1))` →
  `x - 1`. It removes a removable singularity: `cancel(x/x)` → `1`.
- `apart(e, x)` splits a quotient of polynomials in the symbol `x` into partial fractions, a
  repeated factor into its powers: `apart(1/(x^2 (x + 1)), x)` → `1/(x + 1) - 1/x + 1/x²`. With
  another symbol or kernel in it, `e` is returned as it is.
- `gcd` and `lcm` of integers, or of decimals, are the numbers' (§12.1). Otherwise they are the
  polynomial gcd and lcm, exact rationals included: `gcd(x^2 - 1, x^2 + 2x + 1)` → `x + 1`,
  `gcd(2x + 2, 4x + 4)` → `2x + 2`, `gcd(x/2, x/3)` → `x/6`. A number beside a polynomial stays
  unevaluated, since it stands for the integer gcd at each value: `gcd(x, 6)` → `gcd(x, 6)`,
  `gcd(2x, 4)` → `gcd(2x, 4)`, and `subs(gcd(x, 6), x = 4)` → `2`; where every number is an integer,
  the two identities that hold for integers and polynomials alike, signs included, evaluate:
  `gcd(x, 1)` → `1`, `lcm(x, 0)` → `0` (while `gcd(x, 0)` stays, since `gcd(-3, 0)` is 3, and
  `gcd(x, 1, 1/2)` stays, since `gcd(4, 1, 1/2)` is the gcd of contents, `1/2`).
- `numerator(e)` and `denominator(e)` of a number are its own; otherwise they read the canonical
  form, without bringing it over a common denominator: of a product, the factors with
  non-negative exponents and the bases of the others to their positive exponents
  (`numerator(2x/(y z^2))` → `2x`, `denominator(2x/(y z^2))` → `y z²`); of `b^-n`, `1` and `b^n`;
  of anything else, the value and `1` (`numerator(1/x + 1/y)` → `1/x + 1/y`).

**Kernels.** Any subexpression that is not a polynomial in the others, such as `sin(x)`, `√x`,
`x⁻¹`, `π` or `2^x`, is an independent variable: `expand((sin(x) + 1)^2)` → `2 sin(x) + sin(x)² +
1`, `factor(x^2 - π^2)` → `(x + π)(x - π)`. A decimal and a complex number such as `i` are kernels
too. The results are always identities, but the algebra does not see relations between kernels: `x`
and `√x` are unrelated, so `cancel((x - 1)/(√x - 1))` stays as it is; `x` and `x⁻¹` do not cancel
inside a polynomial; and a decimal is no rational coefficient, so `gcd(x^2 - 1.0, x - 1)` → `1` and
`factor(x^2 - 2.0)` → `x² - 2.0`. `∞`, `undefined`, a domain, a truth value and a lambda are not
kernels, nor is a relation, list, set, vector or matrix inside a sum, product or power: when the
argument of an algebra command, or one of its items, holds one, the command returns its call
unevaluated: `factor(∞ x)` → `factor(∞x)`, `cancel(∞ x)` → `cancel(∞x)`, `expand(∞ (x + 1))` →
`expand(∞·(x + 1))`. So do `numerator` and `denominator` of a vector, matrix, list, set or relation.

**Normal forms.** A gcd is the gcd of the arguments' contents (their rational factors) times the gcd
of their primitive parts, which is primitive (integer coefficients with no common divisor) with a
positive sign: `gcd(2x + 2, 4x + 4)` → `2x + 2`, `gcd(x/2, x/3)` → `x/6`. An lcm likewise has a
positive sign. Every base of a factorization is primitive with a positive sign, its rational factor
in front: `factor(2x^2 + 4x + 2)` → `2(x + 1)²`. The sign is that of the leading coefficient when
the symbols come first, so `x` stays positive in `x - π` and `x - sin(x)`. `factor` finds every
rational root and, by Kronecker's method, the factors of a part of degree up to 8; a part it cannot
split within its bounds is shown as it is, and the factorization is incomplete: `factor(x^100 - 1)`
→ `(x - 1)(x + 1)(x⁹⁸ + x⁹⁶ + … + 1)`.

**Bounds.** A polynomial of degree above 10,000 in one kernel, a product or power of more than
100,000 terms or too many coefficient digits, and a power of a coefficient past the canonical
form's folding bound are refused with an error (§16): `factor(x^10001 - 1)` is "the polynomial's
degree exceeds 10,000". `expand` leaves such a power as it is: `expand((x + y)^100000)` →
`(x + y)¹⁰⁰⁰⁰⁰`.

**Steps.** `steps(expand(e))`, `steps(factor(e))` and `steps(gcd(…))` show the command's own steps
before the simplifier's: `steps(factor(x^4 - 1))` shows the two rational roots found. A command
inside a larger expression shows no steps of its own.

### 14.10 Derivatives

`diff(f, x)`, `diff(f, x, n)` and `d/dx f` differentiate by the textbook rules: linearity, the
product, quotient, power and chain rules, and the derivative of every builtin function of §12.1 that
has one. Other symbols are constants, so `diff(x^2 y^3, y)` → `3x² y²` is the partial derivative.
The result is simplified: `d/dx (x^2 sin x)` → `x² cos(x) + 2x sin(x)`, `d/dx x^x` → `x^x·(log(x) +
1)`.

- Angles are radians: `d/dx sin x` → `cos(x)` in every angle mode.
- For a real `u`, `d/dx |u|` is `sign(u)·u′`, and `sign`, `floor`, `ceil`, `round` and `trunc`
  have derivative 0: the formulas hold away from the jumps, where there is no derivative.
- What cannot be differentiated stays a derivative: `diff(x!, x)` → `d/dx x!`, and so do
  `min`, `max`, `re`, `im`, `conj`, `mod`, `gcd`, `binomial`, integrals, limits, products and a
  held `solve` that depend on `x`. An unevaluated derivative keeps its order:
  `diff(x!, x, 2)` → `d²/dx² x!`, and `diff(x·x!, x, 2)` → `x d²/dx² x! + 2 d/dx x!`.
- A sum differentiates term by term when its bounds do not depend on `x`: `d/dx Σ[k=1,n] x^k` →
  `∑[k = 1, n] k x^(k - 1)`.
- **The variable is free in the result.** `x` is bound inside `diff(f, x)`, but the derivative is
  a function of `x`, so where `x` has a value the result takes it: after `f(x) := diff(x^3, x)`,
  `f(2)` → `12`; `subs(diff(x^2, x), x = 3)` → `6`; after `x := 3`, `diff(x^2, x)` → `6`. The
  value is put into the derivative, not into the expression before differentiating, and only for
  the variable: after `f(x, y) := diff(x^2 y, x)`, `f(2, x)` → `4x`, the `x` passed for `y` being
  another one. Where such an `x` and the variable both stay in the result, the variable keeps a
  fresh name: after `f(y) := diff(x y + x^2, x)`, `f(x)` → `x + 2x₁`.
- A number put in for the variable is read in radians, in every angle mode: in degrees, after
  `f(x) := diff(sin x, x)`, `f(30)` → `cos(30)`. As in §14.2, the printed value does not record
  this, so `cos(30)` typed in again in degrees means degrees.
- The simplifier still rewrites a derivative's value in the session's unit (§14.2): in degrees
  `diff(sin(x + 180), x)` → `-cos(x)`, which at a number put in for `x` and read in radians is
  `-cos(x radians)`, not `cos(x + 180 radians)`.
- `steps(d/dx f)` shows the differentiation's steps before the simplifier's; a derivative inside a
  larger statement shows only the simplifier's steps.

### 14.11 Solving

`solve` solves one equation in one unknown, or a system of linear equations in several, exactly
where it can and numerically where it cannot. The method and whether the answer is exact are in its
steps (`steps(solve(…))`).

- **Every complex root** of a polynomial is returned, whatever is assumed about the unknown, which
  `solve` binds: `solve(x^2 + 1 = 0, x)` → `{-i, i}`, and after `assume(x > 0)`,
  `solve(x^2 = 4, x)` → `{-2, 2}`.
- **Polynomials** are factored first; a factor of degree 2 is solved by the quadratic formula, one
  of degree 3 or 4 with rational coefficients by the cubic or quartic formula (three real roots of a
  cubic in their trigonometric form: `solve(x^3 - 3x + 1 = 0, x)` has `2 cos(2π/9)` among
  its roots), and the rest numerically. A coefficient that depends on a symbol is assumed non-zero
  where the solution divides by it (`solve(a x + b = 0, x)` → `{-b/a}`, for `a ≠ 0`); a factor of
  degree 3 or more with symbolic coefficients is not solved.
- **Denominators**: a value that makes a denominator of the equation zero is not a solution:
  `solve((x^2 - 1)/(x - 1) = 0, x)` → `{-1}`. Like factors cancel in the canonical form before
  `solve` sees them, so `(x - 1)^2/(x - 1)` is `x - 1` there.
- **Systems** must be linear in the unknowns; an unknown left free appears in the others' values,
  with no equation of its own: `solve(x + y = 3, [x, y])` → `{[x = -y + 3]}`. A system with no
  solution gives `{}`.
- **Numerically**: a polynomial's roots, real and complex, are found at the session precision, and
  none is missed. Another equation is scanned for real roots on `[-10⁶, 10⁶]`: `solve(x = cos(x),
  x)` → `{0.739085…}`. A scan can miss roots, so it answers only with the roots it found, and with
  none it returns `solve(…)`. An equation with more than 20 sign changes there, such as `sin(x) =
  1/2`, also returns itself: its general solution is Phase 6's (§14.7). The scan's grid grows
  sparser outward and can alias the periods of `sin`, `cos`, `tan`, `cot`, `sec` and `csc`, so an
  equation with one of them applied to the unknown returns itself when the scan finds more than one
  root and one of them lies beyond 10 in magnitude (`solve(tan(x) = 2x, x)`); with one root, or a
  few near the origin, it answers (`x = cos(x)`, `sin(x) = x/2`, `cos(x) = x^2`). In degrees or
  gradians an equation with an angle of the unknown is not solved numerically.
- **Names**: when `solve`'s unknown has to be renamed so as not to capture a name from outside
  (§11.2), the solutions are written with the unknown's own name unless that name also occurs in
  them from outside, where the fresh name stays: after `b := y`, `solve({x + y = b, x - y = 1},
  [x, y])` → `{[x = y/2 + 1/2, y₁ = y/2 - 1/2]}`.
- `solve` **returns itself** when the equation holds for every value (`solve(x = x, x)`), when it
  depends on a symbol whether it holds (`solve(y = 0, x)`), for an inequality (`solve(x < 2, x)`),
  and when no method applies.

### 14.12 Rational functions

Every value is simplified as a rational function too: a quotient of polynomials, in the sense of
§14.9 (rational coefficients, any other subexpression a kernel), loses the factors its numerator and
denominator share, and its numeric content is gathered into one coefficient in lowest terms.

- **Common factors cancel**: `(x^2 - 1)/(x - 1)` → `x + 1`, `x/(x^2 + x)` → `1/(x + 1)`,
  `(x + 1)^5/(x^2 - 1)` → `(x + 1)⁴/(x - 1)`, kept factored. Kernels are opaque:
  `(log(x)^2 - 1)/(log(x) - 1)` → `log(x) + 1`. The trigonometric identities act first, so
  `(sin(x)^2 - 1)/(sin(x) - 1)` → `-cos(x)²/(sin(x) - 1)`.
- **A removable singularity is removed**, as `cancel` does (§14.9): `(x^2 - 1)/(x - 1)` is `x + 1`
  also at `x = 1`. No pole is ever added, and no assumption is consulted. The value taken at a
  removed singularity need not be the limit there when a kernel is discontinuous at it:
  `a sign(a)/a` → `sign(a)`, which is 0 at `a = 0`, as `cancel` also gives.
- **Numeric content** is in lowest terms, each polynomial factor with integer coefficients and no
  common divisor: `2/(2x + 2)` → `1/(x + 1)`, `(x/2 + 1)/3` → `(x + 2)/6`, `1/(x/2 + 1)` →
  `2/(x + 2)`, `(6x + 6)/(4x - 2)` → `3(x + 1)/(2x - 1)`; `(2x + 2)/(3x - 3)` stays, its content
  `2/3` being in lowest terms. A product with no denominator is no quotient: `3(x/2 + 1)` stays.
  A factor's sign stays as written: `1/(1 - x)` → `1/(-x + 1)`.
- **Fractions combine** over a common denominator, the numerator expanded, when the result is no
  larger: `1/(x - 1) - 1/(x + 1)` → `2/(x² - 1)`, `1/(x + 1)^2 - 1/(x + 1)` → `-x/(x + 1)²`,
  `1/(sqrt(x) - 1) + 1/(sqrt(x) + 1)` → `2√x/(x - 1)`, `(x^2 - 4)/(x + 2) + 2` → `x`,
  `1/(x - 1) + 1/(1 - x)` → `0`; `1/x + 1/y` and `x + 1/(x - 1)` stay. The common denominator is
  written factored or multiplied out, whichever gives the smaller result. Every fraction of a sum
  combines, or none.
- **Expanded forms stay**: the value of `expand`, `apart` or `collect` is not combined again, so
  `apart(1/(x^2 - 1), x)` → `1/(2(x - 1)) - 1/(2(x + 1))`; `simplify(apart(1/(x^2 - 1), x))` →
  `1/(x² - 1)`. Such a value stored in a name, or inside a larger expression, is simplified
  like any other when it is used.
- **Steps** name what was cancelled and the common denominator: `steps((x^2 - 1)/(x - 1))` shows
  `Cancel the common factor x - 1: (x² - 1)/(x - 1) = x + 1`, `steps((2x^2 - 2)/(4x - 4))` that
  step and then `Cancel the common numeric factor 2: (2x + 2)/4 = (x + 1)/2`. Fractions inside
  the numerator or the denominator are cleared: `steps((x/2 + 1)/3)` shows `Multiply the numerator
  and the denominator by 2 to clear the fractions: (x/2 + 1)/3 = (x + 2)/6`, and
  `steps((2x/3 + 2)/4)` multiplies by 3 and then cancels the common numeric factor 2.

Large sums are left as they are rather than combined into coefficients of many digits or
denominators of high degree. The rules are in `docs/reference/engine.md`, **Simplification**.

## 15. Output forms

Three text printers, plus the layout tree the app typesets. How numbers are written (exact,
decimal or scientific to a number of digits, or with mixed numbers) is a printer option the
worksheet sets, not part of the language: the output below is the exact format, the only one
that reads back.

### 15.1 The printers

| Value | ASCII | Unicode | LaTeX |
|---|---|---|---|
| 2x·sin x | `2*x*sin(x)` | `2x sin(x)` | `2x\sin(x)` |
| x squared | `x^2` | `x²` | `x^{2}` |
| x to the −1 | `1/x` | `1/x` | `\frac{1}{x}` |
| x to the −2 | `1/x^2` | `1/x²` | `\frac{1}{x^{2}}` |
| e to the x | `e^x` | `e^x` | `e^{x}` |
| √2 | `sqrt(2)` | `√2` | `\sqrt{2}` |
| √(x + 1) | `sqrt(x + 1)` | `√(x + 1)` | `\sqrt{x+1}` |
| π | `pi` | `π` | `\pi` |
| one third | `1/3` | `1/3` | `\frac{1}{3}` |
| x over 2π | `x/(2*pi)` | `x/(2π)` | `\frac{x}{2\pi}` |
| −x² | `-x^2` | `-x²` | `-x^{2}` |
| (−x)² | `(-x)^2` | `(-x)²` | `(-x)^{2}` |
| x·(x + 1) | `x*(x + 1)` | `x·(x + 1)` | `x(x+1)` |
| (x − 1)(x + 1) | `(x - 1)*(x + 1)` | `(x - 1)(x + 1)` | `(x-1)(x+1)` |
| abs | `abs(x)` | `\|x\|` | `\lvert x\rvert` |
| nested abs | `abs(a*abs(b))` | `abs(a \|b\|)` | `\lvert a\lvert b\rvert\rvert` |
| vector | `[2*x, 2*y]` | `⟨2x, 2y⟩` | `\langle 2x, 2y\rangle` |
| matrix | `[[1, 2], [3, 4]]` | `[[1, 2], [3, 4]]` | `\begin{pmatrix}…\end{pmatrix}` |
| set | `{2, 3}` | `{2, 3}` | `\{2, 3\}` |
| subscript | `x_1` | `x₁` | `x_{1}` |
| decimal 2000 | `2000.0` | `2000.0` | `2000.0` |
| complex | `2 + 3*i` | `2 + 3i` | `2+3i` |
| relation | `x <= 1` | `x ≤ 1` | `x \le 1` |
| membership | `n in Integers` | `n ∈ ℤ` | `n \in \mathbb{Z}` |
| lambda | `x -> x^2` | `x → x²` | `x \mapsto x^{2}` |
| cross | `cross(u, v)` | `u × v` | `u \times v` |
| dot | `dot(u, v)` | `dot(u, v)` | `\operatorname{dot}(u, v)` |
| undefined | `undefined` | `undefined` | `\text{undefined}` |
| infinity | `inf` | `∞` | `\infty` |
| integral | `integrate(e^(x^2), x)` | `∫ e^(x²) dx` | `\int e^{x^{2}}\,dx` |
| sum | `sum(1/k^2, k, 1, n)` | `∑[k = 1, n] 1/k²` | `\sum_{k=1}^{n}\frac{1}{k^{2}}` |
| limit | `limit(f, x, 0, right)` | `lim[x → 0+] f` | `\lim_{x\to 0^{+}} f` |
| derivative | `diff(f, x, 2)` | `d²/dx² f` | `\frac{d^{2}}{dx^{2}} f` |

- **ASCII** is pure ASCII in every operator, constant and number, and parses back.
  Identifiers are printed as stored (`θ` stays `θ`; subscripts are already `_`). Every
  multiplication is `*`; notation is printed in command form.
- **Unicode** is pretty text that parses back. Minus is always the ASCII hyphen-minus `-`, so
  text copied into other tools carries no U+2212.
- **LaTeX** is for export only and need not parse.

### 15.2 Parenthesisation

A printer inserts parentheses exactly where the precedence table (§4.1) requires them, and
these specific ones:

- The base of a power is parenthesised unless it is a primary; a `^` exponent is parenthesised
  unless it is a non-negative integer literal, a name, a constant or a call (`x^(-n)`,
  `x^(1/2)`, `e^(2x)`).
- A negative integer exponent always prints as a quotient: `1/x`, `1/x²`, `x/(2π)`. Superscripts
  are only for non-negative integer exponents; `x⁻¹` is input only.
- The right operand of `/` is parenthesised when it is a product or quotient: `x/(2π)`.
- A factor that begins with `-` and is not the first factor is parenthesised: `x·(-2)`.
- A `cross` node printed as `×` is parenthesised when it is a factor other than the first, or
  the right operand of `/` or `×`; its own right operand is parenthesised when it is a product
  or quotient: `2(u × v)`, `u × (2v)`, `x/(u × v)`. (`u × 2v` would read as `cross(u, 2)*v`.)
- `dot(u, v)` prints as that call in every printer: `u·v` would read back as a product.
- The operand of a postfix `!`, `%` or `°` is parenthesised unless it is a non-negative integer
  or decimal literal, a name, a call or a bracketed form (a rational is parenthesised, since
  `1/2!` reads as `1/factorial(2)`): `(x + 1)!`, `(∫ x dx)!`, `|x|!`.
- The operand of `√`, `∛` and `∜` is parenthesised unless it is a number literal, a name or a
  constant: `√2`, `√x`, `√(x + 1)`.
- A big-operator form (`d/dx`, `lim`, `∑`, `∏`) is parenthesised when anything follows it in
  the same term or when it is the base of a power: `(∑[k = 1, n] k) y`. An integral is
  parenthesised when it is the base of a power.
- An integral whose integrand contains any name of the form `d` + something (a variable, free
  or bound, or the head of a user-function call), or whose variable is such a name, is printed
  in command form, since the notation would not parse back: `integrate(dist(x), x)`,
  `integrate(sum(dk, dk, 1, n), x)`. So is an integral whose variable makes `d` + variable a
  builtin name (`integrate(f, ot)`, since `dot` is not a differential). An integrand that
  begins with `[` is parenthesised, since a `[` after `∫` is the bounds: `∫ ([]) dx`.
- A quotient that would read as the derivative notation is written with spaces: `d / dx`,
  `d² / dx²`.
- A lambda as an operand is parenthesised.
- A symbol `left` or `right` as the fourth argument of a `limit` call that did not become a
  limit is parenthesised, `limit(x, x, 0, (left))`, so that it does not read back as a side.
- The Unicode printer prints an absolute value as `|…|` unless its argument, as printed,
  contains a `|` outside brackets; then it prints `abs(…)`.

### 15.3 Unicode juxtaposition

Between two factors L and R of a product, the Unicode printer writes:

1. `·` when R begins with a digit, `.` or `[`, or when L ends in a name or constant and R
   begins with `(`: `x·2`, `x·[1, 2]`, `x·(x + 1)`, `e·(x + 1)`, `n·(n + 1)`. A space before a
   digit would be an error (§9.2), a space before `[` the reserved indexing (§13.6), and `x(`
   could be a call; `·` keeps printed output parsing the same in every environment (§11.3).
2. nothing when L is a number literal and R begins with a letter other than `e` or `E` (and is not a
   call), or with `π`, `(`, `√`, `∛`, `∜`, `|` or `⟨`: `2x`, `2π`, `2(x + 1)`, `2√3`; and nothing
   when L ends with `)` and R begins with `(`: `(x - 1)(x + 1)`; and nothing when L ends with `π` or
   `∞` and R begins with a letter: `2πk`, `πr` (these are tokens of their own, §3.2). `e` and `i`
   are letters, so they take a space: `e k`, since `ek` would be one identifier.
3. a single space otherwise: `x y`, `2x sin(x)`, `2 sin(x)`, `(x - 1) e^x`, `2 e`. The notation of
   every unevaluated form (`∑`, `∏`, `∫`, `lim`, `d/dx`) is always set off from a preceding factor
   by a space, rules 1 and 2 notwithstanding: `2 ∑[k = 1, n] k`, `2 ∫ x dx`, `2 lim[x → 0] f`,
   `2 d/dx x²`, `π d/dx x`.

Subscripts: an identifier whose name ends in `_` followed by characters that all have subscript
forms (digits and `a e o x h k l m n p s t i r u v j`), with at least one character before the
`_`, is printed with that tail in subscripts: `x_1` → `x₁`, `a_n` → `aₙ`, `x_b` stays `x_b`.

Decimals are printed at full stored precision, always with a decimal point: as plain digits when the
decimal exponent is between -6 and 20 (`0.5`, `2000.0`), otherwise in scientific form with a point
in the mantissa and a lowercase `e` (`1.180591620717411303424e+21`, `1.5e-30`), so they read back as
decimals; display rounding is the worksheet's concern. Because the parser keeps every digit of a
decimal literal (§14.1), printing at full stored precision round-trips. Superscripts are used only
for non-negative integer exponents; every other exponent is written with `^` or as a quotient
(§15.2).

### 15.4 Round trip

For every canonical expression `e` no deeper than the nesting cap (§16), in the empty environment
(builtins only):

```
canonical(parse(printASCII(e)))   == e
canonical(parse(printUnicode(e))) == e
```

Applications of user-defined functions print as calls (`f(x)`) and read back as calls wherever
the function is defined, which is wherever such a value occurs.

The canonical expressions the grammar cannot write are the exceptions:

- intervals and piecewise expressions, which have no syntax in Phase 1: the printers write them
  as `[a, b)` (with `(`, `)`, `[` and `]` for open and closed ends) and as a call
  `piecewise(v₁, c₁, …, otherwise)`, which do not parse back;
- a symbol whose name is not an identifier or is reserved, such as a free `e` or `i` (bound in a
  sum or a lambda they print and read back as symbols);
- a list that the bracket rule reads as a vector or a matrix (`[1, 2]`), and a vector or matrix
  with an element that is not scalar.

## 16. Errors

Every parse error carries a position and a message naming what was expected. The position is a line
and a column, both 1-based and counted in Unicode code points (a tab is one column), pointing at the
first code point of the offending token, or at the end of the input for an "expected …" error there.
Line and column refer to the input text as typed, before NFC normalisation, so the position lands in
the user's text; normalisation affects only how characters are classified. A leading byte-order mark
is counted like any other code point. Parentheses directly around the operand of a sign, a radical,
a prefix function name or `^` add no level of nesting, so `-(-x)` is as deep as `--x` and any
expression the parser accepted prints to text it accepts again.

| Case | Example | Message (gist) |
|---|---|---|
| Unbalanced bracket | `(x + 1`, `x]`, `(x]` | expected `)` / unexpected `]` |
| Missing operand | `2 +`, `* 3` | expected an operand |
| Chained relation | `a < b < c` | chained relations are not supported |
| `:=` not at statement level | `(a := 5)` | `:=` defines at statement level only |
| Bad definition head | `f() := 1`, `f(x, x) := 1`, `f(2) := 1` | expected distinct parameter names |
| Defining a reserved name | `e := 3`, `sin := 1` | `e` is a constant / `sin` is a builtin |
| Reserved keyword | `if`, `a mod b` | `if` is reserved / write mod(a, b) |
| Bad number | `5.`, `1.5.2` | expected a digit after `.` / a number cannot follow a factor |
| Unknown character or operator | `∂f`, `a ⊗ b`, `x == 1` | unknown operator `∂` / use `=` |
| Number starting an implicit factor | `2 3`, `x 2`, `5 % 3` | a number cannot follow a factor; write 2x or x*2 |
| Indexing | `v[1]`, `2 [1, 2]` | indexing is reserved; use nth(v, 1) |
| Name needs parentheses | `abs x`, `gcd 4` | abs needs parentheses |
| Function name without argument | `sin = 1`, `gcd`, `assumptions` | sin needs an argument / write assumptions() |
| Power of a function name | `sin²x`, `sin^-1 x` | write sin(x)² or asin(x) |
| Wrong number of arguments | `sin(1, 2)`, `solve(x = 1)` | sin takes 1 argument |
| Bad lambda | `2x -> x`, `(x, y)` alone | lambda parameters must be names |
| Integral without differential | `∫ x` | expected a differential such as dx |
| Power or postfix after a differential | `∫ x dx^2` | parenthesise the integral |
| Derivative order | `d^2/dx^3 f`, `d^0/dx^0 f` | the orders must be equal positive integers |
| Derivative shape | `d^2/dx² f`, `d/dx² f` | the derivative notation is d/dx, d^n/dx^n or dⁿ/dxⁿ |
| Malformed notation bracket | `lim[x] f`, `Σ[k, 1, n] k` | expected `→` / expected `=` |
| `lim` without bracket | `lim(f, x, 0)` | write lim[x → a] f or limit(f, x, a) |
| `Σ`, `Π` without bracket | `Σ k`, `∏(k)` | write Σ[k = a, b] f or sum(f, k, a, b) / write Π[k = a, b] f or product(f, k, a, b) |
| `;` inside a bracket | `(a; b)` | unexpected `;` |
| `d…` variable in integral notation | `∫ x ddx` | use integrate(x, dx) |
| Bad absolute value | `\|a\|b\|\|`, `\|x - \|y\|\|`, `\|x` | empty absolute value / expected an operand / expected `\|` |
| Empty or non-scalar `⟨⟩` | `⟨⟩`, `⟨[1, 2]⟩` | a vector needs scalar elements |
| Binding a constant | `d/di i^2`, `Σ[pi=1,3] pi` | `i` cannot be the variable here / `pi` is a constant |
| Lone superscript minus | `x⁻` | expected superscript digits |
| Nesting deeper than 200 levels (every construct counts, a `×` chain included) | `((…(x)…))` | expression nested too deeply |
| Number out of range | `1e9999999999`, a numeral of more than 1,000,000 digits | number out of range |
| Invisible or combining character | a zero-width space between `a` and `b` | unknown operator U+200B |

Evaluation errors are not parse errors. Each carries the node that failed, and the parser records
where each node of a script was typed, at the first code point of its first token, so the error is
reported at that node's position. Where the script holds no position for it (a node that occurs in
several places, such as the constant `π`, or one from a definition made in an earlier input), the
error is reported at the start of the statement that failed. A mathematical failure is not an error:
it has the value `undefined` (§14.1).

| Case | Example | Message (gist) |
|---|---|---|
| Definition cycle, recursion | `a := b; b := a; a`, `f(x) := x f(x - 1); f(3)` | `a` is defined in terms of itself |
| Function used as a factor | `(x -> x^2)(3)` | a function is not a factor |
| Call of a name that is not a function | after `f := 3`, a stored `f(2)` | f is not a function |
| User function arity | `f(x) := x; f(1, 2)` | f takes 1 argument |
| Reserved later-phase name | `rref(M)`, `help(sin)` | rref is not available yet |
| `∈` with a non-domain right side | `2 ∈ {1, 2}` | membership in a set is not supported yet |
| Ordering a complex number | `i < 2`, `min(1, i)` | complex numbers are not ordered |
| `×` of anything but scalars or 3-vectors | `[1, 2] × [3, 4]` | × needs two 3-vectors |
| Vector and matrix dimensions | `[1, 2] + [1, 2, 3]`, `1 + [1, 2]`, `[1, 2]^2` | cannot add vectors of lengths 2 and 3 / cannot add a scalar and a vector / a vector has no powers |
| A vector or a matrix as an exponent | `π^[1, 2]`, `2^[[1, 2], [3, 4]]` | a vector cannot be an exponent / a matrix cannot be an exponent |
| Arithmetic on a set, a list or a truth value | `{1, 2} + 1`, `true^2` | sets carry no arithmetic / truth values carry no arithmetic |
| Arithmetic on a domain | `x + ℝ + 1`, `2^ℂ` | domains carry no arithmetic |
| A number too long to read back | a value or a definition's value holding an integer of more than 1,000,000 digits, or a fraction whose numerator or denominator has more than 315,653 (the digits of the canonical form's folding bound, 2²⁰ bits): after `a := 3^200000`, `a a a a a a a a a a a`; after `b := 1/3^200000`, `b b b b` | number too large to display |
| `dot`, `nth`, `length` arguments | `dot([1, 2], 3)`, `nth([1, 2], 3)`, `nth([1, 2], 1/2)`, `length(3)` | dot needs two vectors of one length / nth: index 3 is out of range 1 to 2 / nth needs a whole-number index / length needs a list, a vector or a set |
| A rejected assumption | `assume(x = 2)`, a contradiction, `assume(2)` | the reason the assumption is rejected / assume takes relations |
| `assume`, `clear` inside an expression | `1 + assume(x > 0)` | assume works only as a statement |
| `clear` of a non-name, `subs` without pairs | `clear(2)`, `subs(x, 2)` | clear needs a name / subs needs name = value |
| `numeric` digits | `numeric(π, 0)` | the number of digits must be an integer from 1 to 10000 |
| Too deep | definitions nested more than 200 deep, a value deeper than 200 levels | definitions nested too deeply / expression nested too deeply |
| Too large | a value of more than 1,000,000 nodes | result too large |
| Out of range | `sin(1e10000)`, `1e999999999 * 1e999999999 * 1e999999999` | number out of range at this precision |
| A rule set that does not terminate | (a bug in a rule set) | simplification did not finish |
| A polynomial past the algebra's degree bound (§14.9) | `factor(x^10001 - 1)` | the polynomial's degree exceeds 10,000 |
| A product or power past the algebra's size bounds | `cancel((x + 1)^99999/x)` | a polynomial of more than 100,000 terms / the polynomial is too large |
| A coefficient's power past the folding bound | `cancel((3x + 6)^-1000000000)` | the unit's power is too large to compute |
| Exponents or multiplicities past an integer | `factor((x + 2)^1073741823 (-x - 2)^1073741823 (x^2 + 4x + 4))` | the exponents are too large |
| Any other arithmetic failure within the algebra | (a defect) | arithmetic failed: … |
| `apart` without a variable | `apart(1/x, 2)` | apart needs a variable |
| A derivative too large to compute | `diff(x^x, x, 40)` | differentiation did not finish |
| `solve` arguments | `solve(x, x)`, `solve(x = 1, [x, x])`, `solve(x = 1, 2)` | solve needs an equation or a set of equations / solve needs a variable or a list of distinct variables |

## 17. Worked examples

### 17.1 Core examples

| Input | Parse | Result |
|---|---|---|
| `2π` | `2*pi` | `2π`; `6.283185…` with `numeric` |
| `1/3 + 1/6` | `1/3 + 1/6` | `1/2` |
| `sqrt(8)` | `sqrt(8)` | `2√2` |
| `-2^2` | `-(2^2)` | `-4` |
| `2^3^2` | `2^(3^2)` | `512` |
| `5!` | `factorial(5)` | `120` |
| `20%` | `20/100` | `1/5` |
| `sin(30°)` | `sin(30*pi/180)` | `1/2` |
| `expand((x+1)^3)` | `expand((x + 1)^3)` | `x³ + 3x² + 3x + 1` |
| `factor(x^4 - 1)` | `factor(x^4 - 1)` | `(x - 1)(x + 1)(x² + 1)` |
| `solve(x^2 - 5x + 6 = 0, x)` | `solve(x^2 - 5*x + 6 = 0, x)` | `{2, 3}` |
| `solve(sin(x) = 1/2, x)` | `solve(sin(x) = 1/2, x)` | `{2πk + π/6, 2πk + 5π/6}`, `k ∈ ℤ` (Phase 6; until then the call itself, §14.11) |
| `d/dx (x^2 sin x)` | `diff(x^2*sin(x), x)` | `x² cos(x) + 2x sin(x)` |
| `∫ x e^x dx` | `integrate(x*e^x, x)` | `(x - 1) e^x` (no `+ C`, §14.7; Phase 4) |
| `∫[0,1] x^2 dx` | `integrate(x^2, x, 0, 1)` | `1/3` (Phase 4) |
| `lim[x→0] sin(x)/x` | `limit(sin(x)/x, x, 0)` | `1` (Phase 4) |
| `taylor(e^x, x, 0, 4)` | `taylor(e^x, x, 0, 4)` | `1 + x + x²/2 + x³/6 + x⁴/24` (Phase 4; the display order of a series is Phase 4's to decide) |
| `[[1,2],[3,4]]^-1` | `[[1, 2], [3, 4]]^(-1)` | `[[-2, 1], [3/2, -1/2]]` (Phase 5) |
| `eigenvalues([[2,1],[1,2]])` | `eigenvalues([[2, 1], [1, 2]])` | `{1, 3}` (Phase 5) |
| `rref([[1,2,3],[4,5,6]])` | `rref([[1, 2, 3], [4, 5, 6]])` | `[[1, 0, -1], [0, 1, 2]]` (Phase 5) |
| `grad(x^2 + y^2)` | `grad(x^2 + y^2)` | `⟨2x, 2y⟩` (Phase 7) |
| `f(x) := x^2 + 1` | definition of `f` | echoes `f(x) := x² + 1` |
| `a := slider(0, 5, 0.1)` | definition of `a` | a slider on the canvas (app) |

The printers display a canonical sum's terms in descending total degree (the sum of the numeric
exponents of the symbols in a term; a symbolic exponent, a constant, a number or an application
counts 0), ties in canonical order, and the numeric terms (the real and imaginary parts of a complex
number included) last: `x³ + 3x² + 3x + 1`, `a - b`, `x² cos(x) + 2x sin(x)`, `2πk + π/6`,
`-2 sin(x) + 3`. The first term carries its sign in its coefficient (`-2x² + 3x - 1`); a later
term whose coefficient is a negative real, a negative imaginary or a complex number with a negative
real part is a subtraction of the negated coefficient (`a - 2i x`, `a - i·(x + 1)`,
`a - (1 - 2i) x`). A sum multiplied by a numeric coefficient whose first term would read with a
minus is led instead by its first positive term of the same degree (`-(√3 - √2)`, `2(y - x)`,
`(√6 - √2)/4`), and in a sum of rationals and numeric radicals a positive rational term leads
(`(3 - √5)/2`, `(1 + √17)/4`); only the order changes. A product's factors are displayed in groups, each in canonical order: the
numeric coefficient, then constants and symbols with their powers (a power of a constant only
with a numeric exponent: `π²` joins, `e^x` does not), then sums and their powers, then the rest
(`2πr`, `2 sin(x)`, `2x sin(x)`, `(x - 1) e^x`); a negative coefficient signs a quotient (`-1/x`,
`-x/2`), and a factor built from vectors or matrices never goes below the line (`A·B^(-1)`, `M/2`).

### 17.2 Precedence and notation

| Input | Parse |
|---|---|
| `1/2x` | `(1/2)*x` |
| `a/b c` | `(a/b)*c` |
| `1/2π` | `(1/2)*pi` |
| `sin x` | `sin(x)` |
| `sin x^2` | `sin(x^2)` |
| `sin 2x` | `sin(2)*x` |
| `sin x cos x` | `sin(x)*cos(x)` |
| `sin -x` | `sin(-x)` |
| `sin (x)^2` | `sin(x)^2` |
| `√2x` | `sqrt(2)*x` |
| `√(2x)` | `sqrt(2*x)` |
| `√(x)^2` | `sqrt(x^2)` |
| `2^3!` | `2^factorial(3)` |
| `5!!` | `factorial(factorial(5))` |
| `2^-1` | `2^(-1)` |
| `x^-n` | `x^(-n)` |
| `e^-x^2` | `e^(-(x^2))` |
| `5!=3` | `5 != 3` |
| `2e3` | the decimal `2000.0` |
| `2e` | `2*e` |
| `30°` | `30*pi/180` |
| `xy` | the symbol `xy` |
| `x·y` | `x*y` |
| `2 3` | error: a number cannot follow a factor |
| `x(x+1)` (`x` not a function) | `x*(x + 1)` |
| `f(x)` (`f` undefined) | `f*x` |
| `f(x)` (after `f(t) := t^2`) | the call `f(x)` |
| `u × v` | `cross(u, v)` |
| `d/dx x^2 + 1` | `diff(x^2, x) + 1` |
| `d/dx x^2 sin x` | `diff(x^2*sin(x), x)` |
| `lim[x→0] sin(x)/x` | `limit(sin(x)/x, x, 0)` |
| `Σ[i=1,n] i` | `sum(i, i, 1, n)` |
| `∫∫ x y dx dy` | `integrate(integrate(x*y, x), y)` |
| `\|x\|` | `abs(x)` |
| `\|a·(\|b\|)\|` | `abs(a*abs(b))` |
| `\|a\|b\|\|` | error: empty absolute value |
| `x -> x^2` | lambda |
| `(x, y) -> x + y` | lambda of two parameters |
| `a < b < c` | error: chained relations are not supported |
| `n ∈ ℤ` | `n in Integers` |
| `u × 2v` | `cross(u, 2)*v` |
| `u × (2v)` | `cross(u, 2*v)` |

### 17.3 Evaluation

| Input | Result |
|---|---|
| `0.5` | `0.5` (inexact) |
| `exact(0.5)` | `1/2` |
| `numeric(1/3)` | `0.333…` at the session precision |
| `numeric(π, 50)` | π to 50 digits |
| `sin(30)` in degree mode | `1/2` |
| `2 < 3` | `true` |
| `x < 3` | `x < 3` |
| `2 ∈ ℤ` | `true` |
| `{a, b, a}` | `{a, b}` |
| `[1, 2] * [3, 4]` | `11` |
| `[1, 0, 0] × [0, 1, 0]` | `⟨0, 0, 1⟩` |
| `x := 3; solve(x^2 = 4, x)` | `{-2, 2}` |
| `a := 5; subs(x^2 + a, a = 2)` | `x² + 2` |
| `x^-1` | `1/x` |
| `1 = 1.0` | `true` |
| `1/0` | `undefined` |
| `undefined + 1` | `undefined` |

## 18. Reserved for later

- Keywords `and`, `or`, `not`, `if`, `then`, `else`, `for`, `while`, `function`, `return`
  (Phase 9 scripting) and infix `a mod b`.
- Indexing `v[k]`.
- Chained relations as conjunctions (`0 < x < 1`).
- Membership in sets (`x ∈ {1, 2}`) and set-builder notation.
- Declared unknown functions (Phase 7), partial derivatives `∂` and `∇` (Phase 7).
- Units (Phase 8) and number bases (Phase 8).
- `help(name)`.

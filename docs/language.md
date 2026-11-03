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
+  -  *  /  ^  **  !  %  °  ′  ″  =  !=  <  <=  >  >=  in  ->  :=
(  )  [  ]  {  }  ⟨  ⟩  |  ,  ;  #
```

plus the Unicode aliases (§3.5), `×` (cross product, §9.4), the superscripts, and the notation
tokens `∫`, `√`, `∛`, `∜`, `Σ`/`∑`, `Π`/`∏` and the derivative token (§10.1). A prime, `'` or
`′`, directly after an identifier joins it, written `'`: `y′′` is the name `y''` of the prime
notation (§5); after a number and `°`, `′` is the minutes of an angle.

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
| `(30 + 15/60 + 20/3600)*pi/180` | `30°15′20″` | degrees, minutes and seconds (§5) | written out as a multiple of `π` |
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
| 1 Postfix | `!`, `%`, `°` (with `′`, `″`), superscript power | the primary on its left | left to right | `5!!` ⇒ `factorial(factorial(5))`; `x²!` ⇒ `factorial(x^2)` |
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
postfix     = primary { "!" | "%" | "°" | superscript-power }
            | number "°" number "′" [ number "″" ] ;   (* degrees, minutes, seconds, §5 *)

primary     = number | name | constant | domain
            | call
            | "(" expr ")"
            | "{" [ expr { "," expr } ] "}"
            | "[" [ expr { "," expr } ] "]"
            | "⟨" expr { "," expr } "⟩"
            | "|" additive "|" ;
call        = callable-name "(" [ expr { "," expr } ] ")"
            | name primes "(" [ expr { "," expr } ] ")" ;   (* prime notation, §5 *)
primes      = ( "'" | "′" ) { "'" | "′" } ;            (* directly after the name *)

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
- Degrees, minutes and seconds: a number, `°`, a number and `′` (U+2032), and optionally a number
  and `″` (U+2033), with spaces or nothing between the parts, is one angle: `30°15′20″` ⇒
  `(30 + 15/60 + 20/3600)*pi/180`, `30°15′` ⇒ `(30 + 15/60)*pi/180`. The literal is input only: the
  value prints as a multiple of `π`, and `dms` writes it out (§14.18). `′` or `″` anywhere else is
  an error ("`′` follows a number of degrees, as in 30°15′20″"), except that `′` directly after a
  name is a prime (below).
- **Prime notation** `f'(a)`, `f''(a)` (`′` is the same as `'`): a name with primes must be called,
  and is the derivative of that order of the function of one argument `f`, at `a`. For a
  user-defined `f`, `f(x) := x^3; f'(2)` → `12` and `f''(x)` → `6x`. A builtin cannot be primed
  (`sin'(x)`: "the prime notation is for user functions: write diff(sin(x), x)"), and a primed name
  without `(` is an error ("y' needs an argument").
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

What an integral evaluates to is §14.13.

### 10.3 Limits

`lim[ name → target ]` with `->` or `→`; the target is an additive expression, optionally
followed by `+` (from the right) or `-` (from the left) immediately before `]`. Inside these
brackets the arrow means "approaches", never a lambda.

- `lim[x→0] sin(x)/x` ⇒ `limit(sin(x)/x, x, 0)`.
- `lim[x → 0+] 1/x` ⇒ `limit(1/x, x, 0, right)`; `lim[x → 0-] 1/x` ⇒ `limit(1/x, x, 0, left)`.
- `lim[x → ∞] (1 + 1/x)^x` ⇒ `limit((1 + 1/x)^x, x, inf)`; `lim[x -> -inf] e^x`.
- `lim` without `[` is an error ("write lim[x → a] f or limit(f, x, a)").

**Evaluation.** A limit evaluates to its exact value, `∞`, `-∞`, or `undefined` when it does not
exist; when no method decides, the limit stays (`lim[x → 1/2] x!`). The variable is real, and the
facts about it do not hold inside the expression (§14.6), while the point keeps them: after
`assume(x > 0)`, `limit(sqrt(x^2)/x, x, 0, left)` → `-1`. The expression is simplified first
(§14.8), so `limit(sin(x)^2 + cos(x)^2, x, inf)` → `1`:

- `lim[x→0] sin(x)/x` → `1`; `limit((x^2 - 1)/(x - 1), x, 1)` → `2`; `lim[x → 0+] 1/x` → `∞`,
  `lim[x → 0-] 1/x` → `-∞`, `limit(1/x, x, 0)` → `undefined` (the sides differ);
  `limit(1/(x - 1)^2, x, 1)` → `∞`; `limit(abs(x)/x, x, 0)` → `undefined`.
- At infinity: `limit((3x^2 + 1)/(2x^2 - x), x, inf)` → `3/2`, `limit(x^3/e^x, x, inf)` → `0`,
  `limit(sqrt(x^2 + 1) - x, x, inf)` → `0`, `lim[x → ∞] (1 + 1/x)^x` → `e`. A limit at `∞` or
  `-∞` has no side: `limit(e^x, x, inf, left)` is an error ("a limit at ∞ or -∞ has no side").
- Oscillation has no limit: `limit(sin(x), x, inf)` and `limit(sin(1/x), x, 0)` → `undefined`;
  a bounded factor times one that tends to 0 tends to 0: `limit(x sin(1/x), x, 0)` → `0`; a step
  function lies between its bounds: `limit(floor(x)/x, x, inf)` → `1`.
- The methods, tried in order, are substitution where the expression is continuous, the sign of a
  rational function at a pole, the limit laws, the standard limits (`sin(u)/u`, `(1 − cos u)/u`,
  `(eᵘ − 1)/u`, `(1 + 1/u)ᵘ`, `(1 + u)^(1/u)`), the exponential form `uᵛ = e^(v log u)`, one side
  of a jump of `|u|`, `sign` or a step function, cancelling a common factor or combining fractions,
  multiplying by a conjugate (of a square or a cube root: `limit(cbrt(x^3 + x^2) - x, x, inf)` →
  `1/3`), logarithms whose sum is `∞ − ∞` combined into one (`limit(log(abs(x)) - log(abs(x +
  1)), x, inf)` → `0`), the leading term at infinity, a bounded factor or a step between its bounds, an
  oscillation, `x = 1/t` at infinity, L'Hôpital's rule for `0/0` and `∞/∞` (at most three times in
  a row), the series expansion of a quotient, and the two sides of a two-sided limit compared.
- A point with a parameter gives the generic answer: `limit(1/x, x, a)` → `1/a`, the steps naming
  the condition `a ≠ 0`. A parameter's facts decide what its sign does: after `assume(a > 0)`,
  `limit(x^a, x, inf)` → `∞`; after `assume(s > 1)`, `limit(s^x, x, inf)` → `∞`.
- **Never a decimal.** When no method decides, a numeric probe evaluates the expression ever closer
  to the point; it never gives the value, only a note in the steps: `steps(limit(mod(x, 1)/x, x, 0,
  right))` shows `No method finds lim[x → 0+] mod(x, 1)/x; numerically it appears to be 1.0 to 12
  digits, which proves nothing, so it stays unevaluated`.
- Angles are radians, as for a derivative (§14.10): in degrees `lim[x→0] sin(x)/x` → `1`.
- `steps(lim[x → 1] (x^2 - 1)/(x - 1))` shows the simplification, `Cancel the common factor x - 1:
  (x² - 1)/(x - 1) = x + 1`, then the derivation, one line per decision: `By substitution, x + 1 →
  2`. A method that transforms the limit says so first (`Write x^x as e^(x log(x)); …`), a one-sided
  line names its side, and L'Hôpital's rule shows each quotient of derivatives in turn.

The methods and their conditions are in `docs/reference/engine.md`, **Limits**.

### 10.4 Sums and products

`Σ[k = a, b] f` (also `∑`, `sum`) and `Π[k = a, b] f` (also `∏`, `product`). Inside the
brackets `k = a` names the index; it is not a relation.

- `Σ[k=1,n] k^2` ⇒ `sum(k^2, k, 1, n)`; `sum[k=1,n] 1/k` ⇒ `sum(1/k, k, 1, n)`.
- `Π[k=1,n] k` ⇒ `product(k, k, 1, n)`; `Σ[i=1,n] i` ⇒ `sum(i, i, 1, n)` (`i` is the index,
  §11.2).
- `Σ`, `∑`, `Π` and `∏` not followed by `[` are errors ("write Σ[k = a, b] f or
  sum(f, k, a, b)"); `sum(` and `product(` are the command calls.

**Evaluation.** A sum or product evaluates exactly, or stays. The index is bound: its facts do not
hold in the body (§14.6), and the value is free of it.

- **Integer bounds**, `b ≥ a − 1`, with at most 10,000 terms: each term is the body evaluated with
  the index's value, under the session as any statement is, so the angle mode applies (in degrees
  `sum(sin(90 k), k, 1, 3)` → `0`) and commands inside evaluate (`sum(taylor(e^x, x, 0, k), k, 0,
  2)` → `x²/2 + 2x + 3`); a statement takes at most 100,000 terms one by one in all, nested sums
  included, and past that takes none (`sum(sum(1/(j + k), j, 1, 400), k, 1, 400)` stays, its steps
  saying why), a nested sum that evaluates to arithmetic being taken once, not for each term
  (`sum(sum(1/(j k), j, 1, 300), k, 1, 400)` is `H₃₀₀·H₄₀₀`); the terms are added (or the factors multiplied) exactly:
  `Σ[k=1,4] 1/k` → `25/12`, `sum(x^k, k, 0, 3)` → `x³ + x² + x + 1`, `Π[k=1,5] k` → `120`; an
  empty sum is 0 and an empty product 1.
- **A closed form** for symbolic bounds, more terms, or an upper bound `∞`, the body simplified
  first (`sum(log(e^k), k, 1, n)` → `n·(n + 1)/2`), in these families:
  polynomial summands by Faulhaber's formula (`Σ[k=1,n] k` → `n·(n + 1)/2`,
  `Σ[k=1,n] k^2` → `n·(n + 1)(2n + 1)/6`, factored up to degree 20, expanded above); geometric
  `c·r^k` (`sum(2^k, k, 0, n)` → `2^(n + 1) - 1`, `sum(r^k, k, 0, n)` → `(r^(n + 1) - 1)/(r - 1)`
  where `r ≠ 1`, `n + 1` terms of `1` when `r = 1`); arithmetic-geometric `(αk + β)·r^k`;
  telescoping rational summands over linear factors (`sum(1/(k(k + 1)), k, 1, n)` →
  `-1/(n + 1) + 1`) and logarithms (`sum(log((k + 1)/k), k, 1, n)` → `log(n + 1)`); the binomial
  sums `Σ[k=0,n] binomial(n, k)` → `2^n` and `Σ[k=0,n] binomial(n, k) x^k` → `(x + 1)^n`.
  Products: a constant factor (`product(2, k, 1, n)` → `2^n`), consecutive integers as factorials
  (`product(k, k, 1, n)` → `n!`, `product(2k, k, 1, n)` → `2^n n!`), and geometric factors
  (`product(2^k, k, 1, n)` → `2^(n·(n + 1)/2)`).
- **Infinite sums**: the geometric series with `|r| < 1`, known from the value or from the
  assumptions on a symbol (`sum((1/2)^k, k, 0, inf)` → `2`; after `assume(r > 0, r < 1)`,
  `sum(r^k, k, 0, inf)` → `1/(-r + 1)`), the arithmetic-geometric one, the even p-series
  through `ζ(2m)` (`sum(1/k^2, k, 1, inf)` → `π²/6`, `sum(1/k^4, k, 1, inf)` → `π⁴/90`), and the
  telescoping families (`sum(1/(k(k + 1)), k, 1, inf)` → `1`). A recognised divergent sum is `∞`
  or `-∞` (`sum(1/k, k, 1, inf)`, `sum(2^k, k, 0, inf)`), and `sum((-1)^k, k, 0, inf)` →
  `undefined`; anything else stays (`sum(1/k^3, k, 1, inf)`, `sum(1/k, k, 1, n)`). Infinite
  products stay.
- Bounds that are numbers but not integers, or integers with `b < a − 1`, leave the sum, and so
  does a body that is not a number's arithmetic (a lambda, a set).
- `steps` names the family: `steps(sum(k, k, 1, n))` shows `Sum the powers of the index by
  Faulhaber's formula: ∑[k = 1, n] k = n·(n + 1)/2`; a sum that stays says why.

The families and their conditions are in `docs/reference/engine.md`, **Series and sums**.

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
| `transpose`, `inverse`, `rank`, `rref`, `trace` | 1 | | `M^-1` for `inverse` | of a matrix; §14.16 |
| `nullspace`, `colspace` | 1 | | | bases, §14.16 |
| `linsolve` | 2 | | | `linsolve(A, b)`, §14.16 |
| `gramschmidt` | 1 | | | `gramschmidt([v₁, …])`, §14.16 |
| `norm` | 1 or 2 | | | `norm(v[, 1 \| 2 \| inf])`, §14.16 |
| `normalize` | 1 | | | §14.16 |
| `identity`, `size` | 1 | | | `identity(n)`; `size(M)` → `[m, n]`; §14.16 |
| `zeros` | 2 | | | `zeros(m, n)`, §14.16 |
| `eigenvalues`, `eigenvectors`, `diagonalize` | 1 | | | §14.16 |
| `lu` | 1 | | | `[P, L, U]` with `P·A = L·U`, numeric; §14.17 |
| `qr` | 1 | | | `[Q, R]` with `A = Q·R`, `Q` orthogonal, numeric; §14.17 |
| `cholesky` | 1 | | | `L` with `A = L·Lᵀ`, numeric; §14.17 |
| `svd` | 1 | | | `[U, Σ, V]` with `A = U·Σ·Vᵀ`, the compact SVD, numeric; §14.17 |
| `lstsq` | 2 | | | `lstsq(A, b)`, the least-squares solution of `A·x = b`, numeric; §14.17 |
| `expm` | 1 | | | the matrix exponential `e^A`, numeric; §14.17 |
| `dms` | 1 | | | `[d, m, s]` of an angle in degrees; §14.18 |
| `degrees` | 3 | | | `degrees(d, m, s)`, the angle in degrees; §14.18 |
| `trianglearea`, `circumradius`, `inradius` | 3 | | | of a triangle's three sides; §14.19 |
| `polygonarea` | 1 | | | `polygonarea([[x, y], …])`, signed; §14.19 |
| `distance`, `midpoint` | 2 | | | of two points; §14.19 |
| `slope`, `lineequation` | 2 | | | of the line through two points `[x, y]`; §14.19 |
| `circleequation` | 2 | | | `circleequation([h, k], r)`; §14.19 |
| `parabola` | 2 | | | `parabola(focus, directrix)`; §14.19 |
| `ellipse`, `hyperbola` | 3 | | | `ellipse(f₁, f₂, 2a)`; §14.19 |
| `conic` | 3 | | | `conic(focus, directrix, e)`; §14.19 |

### 12.2 Commands

| Name | Arity | Held | Binds | `e`/`i` bindable | Notes |
|---|---|---|---|---|---|
| `solve` | 2 | 1, 2 | 2 | yes | `solve(rel \| {rels}, var \| [vars])`; §14.11 |
| `diff` | 2 or 3 | 1, 2 | 2 | no | `diff(f, x[, n])`; notation `d/dx` |
| `integrate` | 2 or 4 | 1, 2 | 2 | with bounds only | `integrate(f, x[, a, b])`; notation `∫`; §14.13 |
| `limit` | 3 or 4 | 1, 2, 4 | 2 | yes | `limit(f, x, a[, left \| right])`; notation `lim`; §10.3 |
| `sum` | 4 | 1, 2 | 2 | yes | `sum(f, k, a, b)`; notation `Σ`; §10.4 |
| `product` | 4 | 1, 2 | 2 | yes | `product(f, k, a, b)`; notation `Π`; §10.4 |
| `taylor` | 4 | 1, 2 | 2 | no | `taylor(f, x, a, n)`, the Taylor polynomial of degree `n` about `a`; §14.14 |
| `arclength` | 4 | 1, 2 | 2 | yes | `arclength(f, x, a, b)`, `arclength([x(t), y(t)], t, a, b)`; §14.15 |
| `area` | 4 or 5 | 1, 2 | 2 (4), 3 (5) | yes | `area(f, x, a, b)`, `area(f, g, x, a, b)`; §14.15 |
| `volume` | 4 or 5 | 1, 2 | 2 (4), 3 (5) | yes | `volume(f, x, a, b)` (discs), `volume(f, g, x, a, b)` (washers), about the x-axis; §14.15 |
| `shellvolume` | 4 | 1, 2 | 2 | yes | `shellvolume(f, x, a, b)`, shells about the y-axis; §14.15 |
| `extrema` | 2 | 1, 2 | 2 | yes | `extrema(f, x)`; §14.15 |
| `maximize`, `minimize` | 4 | 1, 2 | 2 | yes | `maximize(f, x, a, b)`; §14.15 |
| `expand`, `factor`, `cancel`, `simplify` | 1 | — | | | `expand`, `factor`, `cancel`: §14.9 |
| `collect` | 2 | 2 | | | `collect(e, x)`, §14.9 |
| `apart` | 2 | 2 | | | `apart(e, x)`, partial fractions in the symbol `x`, §14.9 |
| `det` | 1 or 2 | 2 | | | `det(M[, cofactor \| elimination])`; the method a literal word, as `limit`'s side; §14.16 |
| `charpoly` | 2 | 2 | | | `charpoly(M, x)`, the variable held as `apart`'s; §14.16 |
| `triangle` | 3 | left sides | | | `triangle(a = 3, b = 4, C = 90°)`, three parts by name; each name held, its value evaluated; §14.19 |
| `subs` | 2 or more | all | | | `subs(e, x = v[, y = w …])` |
| `assume` | 1 or more | left sides | | | `assume(rel[, rel …])`; each relation's left side is held, its right side evaluated; returns the set of the relations recorded |
| `assumptions` | 0 | | | | the set of current assumptions |
| `numeric` | 1 or 2 | — | | | `numeric(e[, digits])` |
| `exact` | 1 | — | | | a decimal to the rational it denotes |
| `steps` | 1 | 1 | | | the value of `e`, with its steps (§14.7): each one idea of the working, standing for a run of the evaluation's rewrites, the operation's steps (`expand`, `factor`, `diff`, `integrate`, `solve`) first and then the simplifier's |
| `clear` | 1 | 1 | | | `clear(name)` unbinds `name`; returns the symbol |
| `help` | 1 | 1 | | | reserved; not available yet |

The bounds of `integrate`, `sum`, `product` and the commands of §14.15, the point of `limit` and
the orders of `diff` and `taylor` are evaluated. A command's bound variable is bound in every
argument before it: in `area(f, g, x, a, b)` and `volume(f, g, x, a, b)`, in `f` and `g`.

`left` and `right` in `limit` are literal words in that position only, as `cofactor` and
`elimination` are in `det`'s second argument. A held argument is evaluated, when the command
evaluates it, in the environment without the command's bound variables: after `x := 3`, `solve(x^2 = 4, x)` → `{-2, 2}`, and after
`a := 2`, `solve(x^2 = a, x)` → `{-√2, √2}`. `subs` makes the substitution in the held
expression as parsed and then evaluates the result: after `a := 5`, `subs(x^2 + a, a = 2)` →
`x² + 2`. The substitution is in the held parse only: with `a := 5` and `b := a + 1`,
`subs(b, a = 2)` → `6`, because `b` as parsed contains no `a`; write `subs(a + 1, a = 2)` for
`3`.

A builtin cannot be redefined (§3.3). A name that a later release makes a builtin, such as `area`,
`volume` or `extrema` (§14.15), or `transpose`, `inverse`, `rank`, `trace`, `linsolve`, `nullspace`,
`colspace`, `gramschmidt`, `normalize`, `norm`, `identity`, `zeros`, `size`, `charpoly`,
`eigenvectors` and `diagonalize` (§14.16), or `dms` and `degrees` (§14.18), or `triangle`,
`trianglearea`, `polygonarea`, `circumradius`, `inradius`, `distance`, `midpoint`, `slope`,
`lineequation`, `circleequation`, `parabola`, `ellipse`, `hyperbola` and `conic` (§14.19), is no
longer free for a definition: a saved cell that defined it opens, and reports that the name is a
builtin.

### 12.3 Reserved later-phase names

These names are builtins now: they parse as calls and evaluate to an error ("`grad` is not
available yet"). They cannot be defined.

| Name | Phase |
|---|---|
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
`M^n` the matrix power (`M^-1` the inverse, `M^-k` its `k`-th power, §14.16); a vector or a matrix is never an exponent. Element-wise
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
- A general solution uses an integer parameter, `k` (or `k₁`, `k₂` when `k` is taken): each
  member stands for its values at every integer `k`, `{2πk + π/6, 2πk + 5π/6}`, and the value is
  printed with its note, `{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ`. After the statement the parameter is
  assumed in `ℤ`, as if `assume(k ∈ ℤ)` followed, so `simplify(cos(2πk))` → `1`. Two `solve` calls
  in one statement have two parameters (`k` and `k₁`), and the note lists both. When the equations
  do not mention `k` and `k` has a value where `solve` stands (a `subs` pair, a function's
  parameter), the value is put in for the parameter; when they do mention it, the parameter is `k₁`
  and the value is the input's: `subs(solve(sin(x) = k, x), k = 1/2)` →
  `{2πk₁ + π/6, 2πk₁ + 5π/6}, k₁ ∈ ℤ`. The details are in §14.18.
- An indefinite integral returns an antiderivative without a constant: `∫ x dx` → `x²/2`. The
  worksheet may display `+ C`. An integral no technique finds returns itself (§14.13).
- `steps(e)` returns the value of `e`; the trace of the evaluation is attached to the output,
  and the CLI prints it before the value.

The detailed rules belong to the commands' own references.

### 14.8 Simplified forms

Every value is simplified, and the simplifier settles on one form for each family: logarithms are
natural (`ln(x)` → `log(x)`; `log10(x)`, `log2(x)` and `log(x, b)` → `log(x)/log(b)`, or a rational
when the value is one exactly: `log(8, 2)` → `3`); `exp(x)` → `e^x`; `root(x, n)` and `cbrt(x)` of a
non-negative radicand are powers (`cbrt(8)` → `2`, `root(x, 4)` → `x^(1/4)` when `x ≥ 0`); numeric
radicals are reduced and rationalised (`sqrt(8)` → `2√2`, `1/sqrt(2)` → `√2/2`). The circular
functions take exact values at multiples of `π/12` (`sin(π/6)` → `1/2`, `tan(π/2)` → `undefined`),
lose whole turns, half turns and quarter turns of their angle (`cos(x + π)` → `-cos(x)`, `sin(x +
π/2)` → `cos(x)`), and take the sign out of an argument whose leading term is negative (`sin(y - x)`
→ `-sin(x - y)`, `cos(-x)` → `cos(x)`); a product of them is written with the fewest functions
(`sin(x)/cos(x)` → `tan(x)`, `1/cos(x)` → `sec(x)`), and the Pythagorean and double-angle identities
collapse (`sin(x)² + cos(x)²` → `1`, `1 + tan(x)²` → `sec(x)²`, `1 + sinh(x)²` → `cosh(x)²`, `2
sin(x) cos(x)` → `sin(2x)`), never the reverse. Quotients of polynomials lose their common factors
and fractions combine when that is no larger (§14.12). The rules and their conditions are in
`docs/reference/engine.md`, **Simplification**.

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
  `min`, `max`, `re`, `im`, `conj`, `mod`, `gcd`, `binomial`, limits, products and a held `solve`
  that depend on `x`. An integral that stays differentiates by the fundamental theorem of
  calculus, `d/dx ∫ x! dx` → `x!` and `d/dx ∫[0, x^2] t! dt` → `2x·(x²)!` (an integrand free of
  `x`, bounds in `x`); any other integral that depends on `x` stays a derivative. An unevaluated derivative keeps its order:
  `diff(x!, x, 2)` → `d²/dx² x!`, and `diff(x·x!, x, 2)` → `x d²/dx² x! + 2 d/dx x!`.
- A sum differentiates term by term when its bounds do not depend on `x` and it has no closed form
  (§10.4): `d/dx Σ[k=1,n] x^k/k` → `∑[k = 1, n] x^(k - 1)`; one with a closed form is that form
  differentiated: `d/dx Σ[k=1,n] x^k` differentiates `(x^(n + 1) - x)/(x - 1)`.
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
  `solve(x^2 = 4, x)` → `{-2, 2}`. A trigonometric equation is the exception: its solutions are the
  real ones (§14.18).
- **Trigonometric equations**, with every function of the unknown a circular function of a linear
  argument, have their general solution in the angle mode: `solve(sin(x) = 1/2, x)` →
  `{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ` (§14.18).
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
  none it returns `solve(…)`. An equation with more than 20 sign changes there, such as `sin(x) +
  sin(5x) = 1/2`, which the trigonometric path does not take, also returns itself. The scan's grid grows
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

### 14.13 Integrals

`integrate(f, x)`, `∫ f dx`, `integrate(f, x, a, b)` and `∫[a, b] f dx` integrate by the textbook
techniques: linearity, a table of the builtin functions and the standard forms (`1/(x² + a²)`,
`1/√(a² − x²)`, `1/(x² − a²)`, `1/√(x² ± a²)`), each with a linear argument (`∫ sin(2x + 1) dx` →
`-cos(2x + 1)/2`), the trigonometric identities for powers and products of sines and cosines and
powers of `tan`, `cot`, `sec` and `csc`, completing the square, substitution and integration by parts
(twice, and solved for the integral, for `e^x sin x`). The integrand is simplified first, and so is
the result: `∫ x e^x dx` → `x e^x - e^x`, `∫ 1/(x^2 + 2x + 5) dx` → `atan((x + 1)/2)/2`. Then a
constant factor of the whole antiderivative or of one of its terms is multiplied into a sum that
holds the variable, and a term free of the variable is dropped: `∫ x^2 e^x dx` →
`x² e^x - 2x e^x + 2 e^x`, `∫ x/(x + a) dx` → `-a log(|a + x|) + x`, `∫ sin(x)^2 dx` →
`x/2 - sin(2x)/4`.

- **Rational functions** with rational coefficients are decomposed into partial fractions over
  the rationals (§14.9, `apart`): `∫ 1/((x - 1)(x + 2)) dx` → `log(|x - 1|)/3 - log(|x + 2|)/3`.
  A power of an irreducible quadratic is lowered by Hermite's reduction, `∫ 1/(x^2 + 1)^2 dx` →
  `x/(2(x² + 1)) + atan(x)/2`, and `(αq′ + β)/q` gives a logarithm and an arctangent:
  `∫ (x + 3)/(x^2 + x + 1) dx` → `5√3 atan(2(x + 1/2) √3/3)/3 + log(x² + x + 1)/2`. No algebraic
  extension is taken: a factor of degree 3 or more is integrated only when the numerator is a
  multiple of its derivative (`∫ (3x^2 - 1)/(x^3 - x + 1) dx` → `log(|x³ - x + 1|)`); otherwise
  the integral stays (`∫ 1/(x^4 + 1) dx`).
- **Trigonometric substitutions** finish an integrand rational in `x` and the square root of one
  quadratic, after completing the square: `x = a sin θ` for `√(a² − x²)` (`∫ sqrt(1 - x^2) dx` →
  `x √(-x² + 1)/2 + asin(x)/2`), `x = a tan θ` for `√(x² + a²)` (`∫ 1/(x^2 + 1)^(3/2) dx` →
  `x/√(x² + 1)`), and `x = a sec θ` for `√(x² − a²)`, which needs the side of the root: a fact
  (`∫ sqrt(x^2 - 1) dx` stays, and after `assume(x > 1)` is
  `x √(x² - 1)/2 - log(|x + √(x² - 1)|)/2`) or the bounds of a definite integral
  (`∫[1, 2] sqrt(x^2 - 1) dx` → `√3 - log(√3 + 2)/2`). They are tried after every other technique,
  so the table's arcsines and logarithms stay as they were.

- **An antiderivative without a constant** (§14.7). A logarithm is of an absolute value for a
  real argument, so `∫ 1/x dx` → `log(|x|)`, and `log(x)` after `assume(x > 0)`; for a complex
  argument it is the logarithm itself: `∫ 2x/(x^2 + i) dx` → `log(x² + i)`, and after `assume(x ∈
  ℂ)`, `∫ 1/x dx` → `log(x)`. Inside an indefinite integral the variable keeps its facts, since the
  result is read with them; a definite integral drops them, as its variable is bound.
- **All or nothing.** When no technique finds the whole antiderivative the integral is returned as
  it is, with one step that says so: `∫ e^(x^2) dx` → `∫ e^(x²) dx`, and `∫ x + x! dx` stays whole
  rather than `x²/2 + ∫ x! dx`. A constant that the result would divide by must be known not to be
  zero: `∫ e^(a x) dx` stays until `assume(a > 0)` (or `a != 0`), then is `e^(a x)/a`; `∫ x^n dx`
  stays, since `n` may be -1.
- **The variable is free in an indefinite integral's result**, as a derivative's is (§14.10):
  after `x := 3`, `∫ x^2 dx` → `9`; after `f(x) := ∫ x^2 dx`, `f(2)` → `8/3`. A definite
  integral binds its variable, so its value has none: after `x := 3`, `∫[0, 1] x^2 dx` → `1/3`.
- **A definite integral** is `F(b) − F(a)` for an antiderivative `F` that is continuous between the
  bounds, which are evaluated with the outer facts: `∫[0, π] sin(x) dx` → `2`, `∫[0, t] x dx` →
  `t²/2`, `∫[2, 1] x dx` → `-3/2`. When the integrand or `F` has a singularity between the
  bounds, or the engine cannot rule one out, the integral is returned as it is, with a step that
  says so: `∫[0, 2] tan(x) dx` stays, and `∫[1, t] 1/x dx` stays until `assume(t > 1)`, then is
  `log(t)`. A bound may name the outer variable of the same name: after `assume(x > 1)`,
  `∫[1, x] 1/x dx` → `log(x)`.
- **An improper integral**, with an infinite bound or a singularity at a bound, is the limit of
  `F` at the upper bound from below minus its limit at the lower bound from above (both sides at
  `±∞`), through the limit provider (§10.3), when `F` is continuous strictly between the bounds:
  `∫[1, ∞] 1/x^2 dx` → `1`, `∫[0, 1] 1/sqrt(x) dx` → `2`, `∫[-∞, ∞] 1/(1 + x^2) dx` → `π`. It
  **diverges** when a limit is infinite or undefined, and is then `undefined`, with a step that
  says so: `∫[1, ∞] 1/x dx` → `undefined`. Each end must converge on its own, so there is no
  principal value: `∫[-∞, ∞] x dx` → `undefined`. A real pole of a rational integrand in the
  closed interval, between the bounds or at one, is never integrable, so the integral diverges
  without a limit being asked: `∫[-1, 1] 1/x^2 dx`, `∫[-1, 1] 1/x dx` and `∫[0, 3] x/(x - 1)^2 dx`
  → `undefined`; a pole whose place cannot be decided leaves the integral. A limit the provider
  cannot decide leaves the integral, with a step that says so: `∫[0, ∞] cos(x) e^(sin(x)) dx`
  stays; `∫[1, ∞] 1/(x (x + 1)) dx` → `log(2)`, the limit of `log|x| − log|x + 1|` at `∞`.
- **Angles are radians**, as for derivatives: `∫ cos(x) dx` → `sin(x)` in every angle mode. In
  degrees or gradians a definite integral with a circular or inverse circular function in its
  integrand or antiderivative stays, since its value (`sin(1)`, `atan(2)`) would be read back in the
  session's unit.
- `steps(∫ f dx)` shows the integration's steps, one per technique (`Integrate by parts with u = x
  and dv = e^x dx: ∫ x e^x dx = x e^x - ∫ e^x dx`), before the simplifier's; an integral inside a
  larger statement shows only the simplifier's steps.

The rules are in `docs/reference/engine.md`, **Integration**.

### 14.14 Taylor polynomials

`taylor(f, x, a, n)` with `n` a non-negative integer is the Taylor polynomial of `f` of degree `n`
about `x = a`, `Σ f⁽ᵏ⁾(a) (x − a)ᵏ/k!` for `k = 0 … n`, built from the derivatives of §14.10
evaluated exactly at `a` and simplified: `taylor(e^x, x, 0, 4)` → `x⁴/24 + x³/6 + x²/2 + x + 1`,
`taylor(log(x), x, 1, 3)` → `x - (x - 1)²/2 + (x - 1)³/3 - 1`. No series object is kept; the
value is an ordinary polynomial, shown in the printer's order (§17.1): about 0, descending powers
of `x`; about another point, the powers of `(x − a)` are factors that count no degree, so they
follow any term in `x` itself (a term `1·(x − a)` joins the sum as `x − a`), in canonical order,
the constant last, and a non-integer `a` is written over a common denominator:
`taylor(sin(x), x, π/6, 2)` → `(6x - π) √3/12 - (6x - π)²/144 + 1/2`.

- `n` symbolic leaves the call: `taylor(e^x, x, 0, n)` stays. Any other `n` is the error
  "taylor's order must be a non-negative integer"; an infinite `a` is "taylor's point must be
  finite".
- `f` must be `n` times differentiable at `a`: each derivative below the order must be smooth there
  and the last continuous, judged operator by operator as for a limit's substitution (§10.3), since
  the derivative rules ignore jumps (`|x|′` is `sign(x)`). Otherwise the error names the order of
  the derivative, 0 being `f` itself: one with no value there is undefined
  (`taylor(sin(x)/x, x, 0, 2)`: "the derivative of order 0 is undefined at x = 0"); one that the
  check cannot show continuous is said so, the check being conservative
  (`taylor(sqrt(x), x, 0, 2)`, `taylor(abs(x), x, 0, 1)` and `taylor(x^(5/2), x, 0, 2)`: "the
  derivative of order 1 could not be shown continuous at x = 0"). A derivative that stays
  unevaluated (`x!`) is an error too.
- Angles are radians, as for a derivative (§14.10): in degrees, `taylor(sin(x), x, 0, 3)` is
  `-x³/6 + x`, the polynomial of sine in radians.
- A symbolic point gives the generic polynomial: `taylor(log(x), x, a, 2)` →
  `log(a) + (-a + x)/a - (-a + x)²/(2a²)`, valid where `a ≠ 0`.
- **The variable is free in the result**, as a derivative's is (§14.10): after
  `T(x) := taylor(e^x, x, 0, 3)`, `T(1)` → `8/3`.
- `steps(taylor(cos(x), x, 0, 2))` lists the derivatives at the point and the polynomial: `The
  derivatives at x = 0 are 1 (order 0), 0 (order 1), -1 (order 2); the Taylor polynomial is
  -x²/2 + 1`.

### 14.15 Applications of integration and optimisation

The commands set a quantity up as a textbook would, give the integral to `integrate` (§14.13) and
the roots to `solve` (§14.11), and bind their variable as a definite integral does: after `x :=
3`, `area(x, x^2, x, 0, 1)` → `1/6`.

- **Arc length.** `arclength(f, x, a, b)` is `∫[a, b] √(1 + f′²) dx`, and `arclength([x(t), y(t)],
  t, a, b)` of a curve given as a pair is `∫[a, b] √(x′² + y′²) dt`: `arclength(x^(3/2), x, 0, 1)`
  → `13√13/27 - 8/27`, `arclength([cos(t), sin(t)], t, 0, pi)` → `π`. With no closed form the
  integral it set up is the value, which `numeric` evaluates: `arclength(x^3, x, 0, 1)` →
  `∫[0, 1] √(9x⁴ + 1) dx`; a parabola's has one, by a trigonometric substitution:
  `arclength(x^2, x, 0, 1)` → `√5/2 + log(√5 + 2)/4`.
- **Area** is unsigned. `area(f, x, a, b)` is the area between the graph of `f` and the x-axis,
  `area(f, g, x, a, b)` the area between two graphs, `∫[a, b] |f − g| dx`. When the difference is
  a rational function of `x` with rational coefficients, the places where it can change sign, its
  real roots and poles, are all known (the solver isolates them, §14.11); when they are exact and
  each is decidably inside `(a, b)` or not, the interval is split there and the area is the sum of
  the pieces `|∫ (f − g) dx|`: `area(x^2 - 1, x, -2, 2)` → `4`, `area(x, x^2, x, 0, 1)` → `1/6`,
  `area(2, x, 0, 1)` → `2`. A piece that ends at a pole is an improper integral (§14.13), which
  may diverge, and the area is then `undefined`, with a step that says so: `area(1/x, x, -1, 1)` →
  `undefined`. Otherwise the value is
  `∫[a, b] |f − g| dx`, with a step that says why, integrated in case the absolute value goes
  (`area(t, x, 0, 1)` → `|t|`), and which `numeric` evaluates: `area(sin(x), x, 0, 3)` →
  `∫[0, 3] |sin(x)| dx`.
- **Volume of revolution.** About the x-axis, `volume(f, x, a, b)` is `π ∫[a, b] f² dx` (discs)
  and `volume(f, g, x, a, b)` is `π ∫[a, b] |f² − g²| dx` (washers), split at the crossings as an
  area is: `volume(sqrt(x), x, 0, 4)` → `8π`, `volume(x, x^2, x, 0, 1)` → `2π/15`. **About the
  y-axis**, by shells, `shellvolume(f, x, a, b)` is `2π ∫[a, b] x |f| dx`, which needs `a ≥ 0`,
  split at the roots of `f`: `shellvolume(x^2, x, 0, 2)` → `8π`.
- **The bounds** of `arclength`, `area`, `volume` and `shellvolume` need `a ≤ b`: equal bounds give
  `0`, `a > b` is an error, and an order the engine cannot decide (`area(x^2, x, a, b)`) leaves
  the call as it is, with a step that says so. `shellvolume` with `a < 0` is an error, and with a
  sign of `a` it cannot decide stays.
- **Extrema.** `extrema(f, x)` is the set of the local minima and maxima `[x₀, f(x₀)]`. The
  critical points are the roots of `f′`, used only when they are provably all of them: `f′` a
  rational function of `x` with rational coefficients, whose real roots the solver isolates
  (exactly, or as decimals). Each is classified by the sign of `f″` there, or, where that is zero
  or cannot be decided, by the sign of `f′` either side of it; a point where `f′` keeps its sign
  is neither and is left out: `extrema(x^3 - 3x, x)` → `{[-1, 2], [1, -2]}`, `extrema(x^4, x)` →
  `{[0, 0]}`, `extrema(x^3, x)` → `{}`. The test either side stays nearer the point than the
  other critical points and the poles of `f′`, so it never steps across a pole. A derivative that
  never vanishes gives none: `extrema(x, x)` → `{}`, `extrema(1/x, x)` → `{}`. Any other
  derivative (`extrema(sin(x), x)`), a derivative identically 0 (`extrema(5, x)`, every point
  critical), a symbol beside the variable (`extrema(x^2 + a x, x)`) or a kind that cannot be
  decided leaves the call, with a step that says so.
- **Global extrema on an interval.** `maximize(f, x, a, b)` and `minimize(f, x, a, b)` are the
  point `[x₀, f(x₀)]` where `f` is largest or smallest on `[a, b]`, among the bounds and the
  critical points inside, found as for `extrema`; on a tie, the leftmost: `maximize(x^3 - 3x, x,
  -3, 3)` → `[3, 18]`, `maximize(x^2, x, -1, 1)` → `[-1, 1]`. `a > b` is an error; when `f` is not
  continuous on `[a, b]` or the engine cannot rule a discontinuity out (`maximize(1/x, x, -1,
  1)`), the critical points are not provably all known, or two values cannot be compared, the call
  stays. A function constant on the interval, `f′` identically 0, has every point critical, so its
  call stays too.
- `steps(…)` shows the command's steps: the formula of the form in use, the integrand simplified,
  the crossings and the pieces, then the integral's own steps when there is one integral; the
  critical points and their kinds; the candidates and the choice.

The commands are in `docs/reference/engine.md`, **Applications**.

### 14.16 Linear algebra

The operations are exact: entries are numbers of the tower or symbolic expressions, kept as
rational functions of their kernels in lowest terms, and a decimal entry makes what it touches
decimal (§14.1). An argument that holds a symbol where a matrix is expected may still stand for one,
so the call stays: `det(M)` → `det(M)`. A vector is a column: `[[1, 2], [3, 4]] * [1, 1]` is a
vector, and `transpose([1, 2])` → `[[1, 2]]`, the 1×2 matrix. A list of vectors of one length is a
matrix by the bracket rule (§13.2), so a basis of two or more vectors prints as the matrix whose rows
they are, a basis of one vector as a 1×n matrix, and the empty basis as `[]`.

- **Determinant.** `det(M)` of a square matrix expands by cofactors along the first row up to 3×3
  and eliminates above (Bareiss's fraction-free elimination, the value the last pivot with the sign
  of the row swaps); `det(M, cofactor)` (up to 8×8) and `det(M, elimination)` choose. `det([[1, 2],
  [3, 4]])` → `-2`, `det([[a, b], [c, d]])` → `-b c + a d`.
- **Reduced row echelon form.** `rref(M)` is Gauss–Jordan elimination as it is done by hand, so
  that the steps match a worked solution: for each column in order, the pivot is the first row at
  or below the current one whose entry is not known to be zero (swapped up when it is not the
  current row), the row is divided by it (unless it is 1), and every other row with an entry in
  the column has a multiple of it subtracted; no pivoting by size. `rref([[1, 2, 3], [4, 5, 6]])` →
  `[[1, 0, -1], [0, 1, 2]]`. A symbolic pivot is assumed not to be zero, and the step says so:
  `rref([[a, 1], [1, 1]])` → `[[1, 0], [0, 1]]`, assuming `a ≠ 0` and `(a - 1)/a ≠ 0`.
- **Rank, null space, column space.** `rank(M)` is the number of pivots; `nullspace(M)` a basis of
  the solutions of `M x = 0`, one vector per column without a pivot; `colspace(M)` the pivot columns
  of `M` itself: `nullspace([[1, 2], [2, 4]])` → `[[-2, 1]]`, `colspace([[1, 2], [2, 4]])` →
  `[[1, 2]]`.
- **Inverse.** `inverse(M)` and `M^-1`: the adjugate over the determinant for 1×1 and 2×2,
  Gauss–Jordan elimination on `[M | I]` above; `M^-k` is `(M^-1)^k`. A matrix known to be singular is
  the error "the matrix is singular"; a symbolic determinant is assumed not to be zero, and the
  step that gives the inverse says so (`det ≠ 0`).
  `[[1, 2], [3, 4]]^-1` → `[[-2, 1], [3/2, -1/2]]`.
- **Linear systems.** `linsolve(A, b)` solves `A x = b` by Gauss–Jordan elimination on `[A | b]`:
  the solution as a vector when it is unique, the general solution with parameters `t₁, t₂, …` for
  the columns without a pivot (names that do not occur in the arguments), `{}` when there is none.
  `b` may be a matrix of right sides, giving a matrix of solutions, its parameters numbered column by
  column. `linsolve([[1, 2], [2, 4]], [3, 6])` → `⟨-2t₁ + 3, t₁⟩`.
- **Gram–Schmidt.** `gramschmidt([v₁, …])` orthogonalises the vectors in order, not normalised,
  with the product `Σ conj(uᵢ) vᵢ` (the dot product for real entries; symbols are real); a vector in
  the span of those before it is dropped, with a step that says so.
- **Norms.** `norm(v)` is `√(Σ |vᵢ|²)`, `norm(v, 1)` is `Σ |vᵢ|`, `norm(v, inf)` is `max |vᵢ|`;
  `normalize(v)` is `v / norm(v)`: `norm([3, 4])` → `5`, `normalize([1, 1])` → `⟨√2/2, √2/2⟩`.
- **Shapes.** `transpose(M)`, `trace(M)` of a square matrix, `size(M)` → `[m, n]` (`size(v)` →
  `[n]`), `identity(n)` and `zeros(m, n)`.
- **Characteristic polynomial.** `charpoly(M, x)` is `det(xI − M)`, by Berkowitz's algorithm,
  which divides by nothing; `x` is held and may not occur in `M`: `charpoly([[1, 2], [3, 4]], x)` →
  `x² - 5x - 2`.
- **Eigenvalues.** `eigenvalues(M)` are the roots of the characteristic polynomial, found by
  `solve` (§14.11) factor by factor, each as often as its algebraic multiplicity, in canonical
  order; a list of numbers is a vector (§13.2): `eigenvalues([[2, 1], [1, 2]])` → `⟨1, 3⟩`,
  `eigenvalues([[2, 1], [0, 2]])` → `⟨2, 2⟩`, `eigenvalues([[1, 1], [1, 0]])` → `⟨-√5/2 + 1/2,
  √5/2 + 1/2⟩` (each root cancelled), and a cubic's three real roots by cosines. For a symbolic matrix the multiplicities
  are the generic ones: `eigenvalues([[a, b], [b, a]])` → `⟨a + |b|, a - |b|⟩`. When a root can be
  found only numerically (a quintic, say), the call stays, unless the value is asked for
  numerically (`numeric(…)` or a decimal format, as for a definite integral, §14.13): then the
  eigenvalues are decimals of those digits, and the result is marked numeric. A matrix with a
  decimal entry is inexact already, and its eigenvalues are decimals in every format, found from
  the rationals its decimals denote and then merged where they agree to the precision, so a
  repeated eigenvalue keeps its multiplicity: `eigenvalues([[2.0, 1], [0, 2.0]])` → `⟨2.0, 2.0⟩`.
- **Eigenvectors.** `eigenvectors(M)` is the list of `[λ, basis]` for each distinct eigenvalue,
  the basis of the null space of `M − λI` (a matrix of rows, by §13.2):
  `eigenvectors([[2, 1], [1, 2]])` → `[[1, [[-1, 1]]], [3, [[1, 1]]]]`. `A v = λ v` holds exactly for
  every vector reported: for a root of an irreducible rational factor the null space is computed
  in the field the factor defines and the root put in after. An eigenspace of a symbolic matrix
  assumes its pivots are not zero, and the step says so: `diagonalize([[a, 0], [1, b]])` assumes
  `a - b ≠ 0`. A basis the engine cannot find (a symbolic zero it does not see) leaves the call.
- **Diagonalization.** `diagonalize(M)` → `[P, D]` with the eigenvectors as the columns of `P` and
  the eigenvalues on the diagonal of `D`, so `P⁻¹MP = D`: `diagonalize([[2, 1], [1, 2]])` →
  `[[[-1, 1], [1, 1]], [[1, 0], [0, 3]]]`. A matrix with fewer independent eigenvectors than its
  order is not diagonalizable, and the call stays, with a step that says so.
- `steps(…)` shows the row operations (`R₂ ← R₂ − 3R₁`, `Swap R₁ and R₂`, `Divide R₂ by -3`), the
  cofactor expansion with its minors or the elimination and its pivots, the adjugate or the
  augmented matrix, and the basis or solution read off; the characteristic polynomial, its roots
  and each eigenspace.

The operations are in `docs/reference/engine.md`, **Linear algebra**.

### 14.17 Numeric linear algebra

`lu`, `qr`, `cholesky`, `svd`, `lstsq` and `expm` are numeric by nature: they compute in double
precision, under every number format (a definite integral waits for `numeric` or a decimal format,
§10.2; these do not), and the value is marked as numeric, as a quadrature's is. Each entry of the
matrix must have a real value: an integer, a rational, a decimal or a closed expression such as `π`
or `√2`, taken to 17 digits and then to a `double`. The entries of the result are decimals of at
most 16 significant digits (fewer when the precision is lower: `numeric(lu(A), 5)` gives 5), shown
as the computation gave them, so `lstsq([[1, 1], [1, 2], [1, 3]], [1, 2, 2])` →
`⟨0.6666666666666671, 0.4999999999999998⟩`, not `⟨2/3, 1/2⟩`. A negative zero is `0.0`.

- `lu(A)` → `[P, L, U]`, a list of three matrices: `P` a permutation (partial pivoting), `L` lower
  triangular with ones on its diagonal, `U` upper triangular, `P·A = L·U`:
  `lu([[4, 3], [6, 3]])` → `[[[0.0, 1.0], [1.0, 0.0]], [[1.0, 0.0], [0.6666666666666666, 1.0]],
  [[6.0, 3.0], [0.0, 1.0]]]`. `A` must be square and not numerically singular.
- `qr(A)` → `[Q, R]`, `A = Q·R` with `Q` orthogonal (m×m) and `R` upper triangular (m×n), for any
  shape.
- `cholesky(A)` → `L`, lower triangular with `A = L·Lᵀ`: `cholesky([[4, 2], [2, 3]])` →
  `[[2.0, 0.0], [1.0, 1.414213562373095]]`. `A` must be symmetric positive-definite.
- `svd(A)` → `[U, Σ, V]` with `A = U·Σ·Vᵀ`, the **compact** decomposition: for an m×n `A` and `p =
  min(m, n)`, `U` is m×p, `Σ` the p×p diagonal of the singular values, descending and
  non-negative, and `V` n×p, both with orthonormal columns.
- `lstsq(A, b)` → the vector `x` that minimises `|A·x − b|`, through the QR decomposition; `b` is a
  vector with one entry per row of `A`, which must have at least as many rows as columns and full
  numerical column rank. Several right sides at once are later scope.
- `expm(A)` → `e^A` of a square matrix (Padé approximation with scaling and squaring):
  `expm([[0, 1], [0, 0]])` → `[[1.0, 1.0], [0.0, 1.0]]`.

**Singular** means numerically singular, the standard rank tolerance: a pivot of `lu`, or a diagonal
entry of the `R` behind `lstsq`, below `max(m, n)·2⁻⁵²` times the largest entry of the matrix in
magnitude, so `lu([[10^12, 0], [0, 1]])` decomposes. The scale of a matrix does not matter
otherwise: `qr([[10^-160, 0], [0, 10^-160]])` and `cholesky` of a matrix of entries near `10²⁰⁰`
decompose as well as any other.

A matrix with an entry that holds a symbol, or an argument that is a symbol or an expression in
symbols (a name that may later stand for a matrix), leaves the call as it is, as `sin(x)` does,
with a step that says so: `lu([[a, 1], [2, 3]])` → `lu([[a, 1], [2, 3]])`; after `M := [[4, 3],
[6, 3]]`, `lu(M)` is evaluated. So does an entry the number format leaves unevaluated, which holds
its variable: under an exact format, `lu([[integrate(e^(x^2), x, 0, 1), 1], [1, 1]])` stays, and
under `numeric` or a decimal format the integral is a number first. Any other argument is an
error:

| Case | Example | Message |
|---|---|---|
| Not a matrix | `lu(5)`, `svd([1, 2])`, `lstsq([[1, 2]], 3)` | lu needs a matrix / lstsq needs a matrix and a vector |
| A complex entry | `lu([[i, 1], [1, 1]])` | lu needs a real matrix |
| An entry beyond the `double` range, or a non-zero entry below it | `lu([[10^400]])`, `lu([[10^-400]])` | lu's entries are beyond the double range |
| Not square | `lu([[1, 2, 3]])`, `expm([[1, 2]])` | lu needs a square matrix |
| Singular | `lu([[1, 2], [2, 4]])` | lu needs a matrix that is not numerically singular |
| Not symmetric positive-definite | `cholesky([[1, 2], [2, 1]])` | cholesky needs a symmetric positive-definite matrix |
| Rank-deficient, or fewer rows than columns | `lstsq([[1, 2], [2, 4], [3, 6]], [1, 2, 3])` | lstsq needs a matrix of full numerical column rank |
| Wrong length of `b` | `lstsq([[1, 1], [1, 2]], [1, 2, 3])` | lstsq needs a vector of 2 entries |
| A result beyond the `double` range | `expm([[1000]])` | expm's result is beyond the double range |

`steps(…)` shows one step: "Computed in double precision through the numeric decomposition
provider, at most 16 digits", or "lu needs a numeric matrix: the call stays". The functions are in
`docs/reference/engine.md`, **Numeric linear algebra**.

### 14.18 Trigonometric equations and angles

**General solutions.** `solve` gives a trigonometric equation's general solution when every
function of the unknown in it is one of `sin cos tan cot sec csc` of a linear argument `a·x + b`, `a`
a non-zero rational, and the arguments are multiples `n·u + β` of one of them, `u`, with `|n| ≤ 4`
and `β` a multiple of a twelfth of a half turn (`sin(2x) = sin(x)`, `cos(x + π/3) = sin(x)`; not
`sin(x) + sin(5x)`). Each solution is a family `P·k + o` in the integer parameter `k`, its period `P`
and its offset `o` in `[0, P)` where that can be decided:

| Input | Result |
|---|---|
| `solve(sin(x) = 1/2, x)` | `{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ` |
| `solve(sin(x) = 0, x)` | `{πk}, k ∈ ℤ`: two families half a period apart are one |
| `solve(sin(x) = 1/3, x)` | `{2πk + asin(1/3), 2πk + π - asin(1/3)}, k ∈ ℤ` |
| `solve(sin(2x) = 1/2, x)` | `{πk + π/12, πk + 5π/12}, k ∈ ℤ` |
| `solve(2cos(x)^2 + cos(x) - 1 = 0, x)` | `{2πk + π, 2πk + π/3, 2πk + 5π/3}, k ∈ ℤ` |
| `solve(sin(x) + cos(x) = 1, x)` | `{2πk, 2πk + π/2}, k ∈ ℤ` |
| `solve(sin(2x) = sin(x), x)` | `{πk, 2πk + π/3, 2πk + 5π/3}, k ∈ ℤ` |
| `solve(tan(x) + sec(x) = 0, x)` | `{}`: `sin(x) = -1` only where `sec(x)` is undefined |
| `solve(sin(2x)/sin(x) = 2, x)` | `{}`: `cos(x) = 1` only where `sin(x)` is 0 |
| `solve(sin(x) = 2, x)` | `{}` |
| `solve(tan(x) = a, x)` | `{πk + atan(a)}, k ∈ ℤ` |

The method, which `steps` shows: one function of `u` is a polynomial in it; several are written
through `sin(u)` and `cos(u)` and the result factored; each factor is solved as a polynomial in one
function, as `R·sin(u + φ)` (the auxiliary angle), through `sin² + cos² = 1`, or through `tan(u/2)`
(a factor of degree at most 4); each value `c` of a function gives its angles by the inverse
function and the period; and a family on which the equation is undefined is removed. The solutions
are the **real** ones: a value outside a function's range (`sin(x) = 2`) or not real
(`sin(x)^2 = -1`) has none. A value with a symbol is answered for `tan` and `cot` only;
`solve(sin(x) = a, x)` returns itself. An equation the method does not take, or cannot decide,
goes to the numeric scan as before (§14.11).

**The angle mode.** An argument built from numbers and symbols is read in the mode's unit, so the
solutions are in that unit: in degrees `solve(sin(x) = 1/2, x)` → `{360k + 30, 360k + 150}, k ∈ ℤ`,
and in gradians `{400k + 100/3, 400k + 500/3}, k ∈ ℤ`. An argument that holds `π` is read in radians
in every mode (§14.2), and so are its solutions: in degrees `solve(sin(x + π/3) = 1/2, x)` →
`{2πk + π/2, 2πk + 11π/6}, k ∈ ℤ`. Arguments of both kinds in one equation leave it to the scan.

**The parameter.** It is `k`, unless `k` is taken: the equation mentions it, it is the unknown, it
is bound or defined, it has a fact other than `k ∈ ℤ`, or an earlier `solve` of the same statement
took it; then `k₁`, `k₂`, …. So after `k := 5`,
`solve(cos(x) = 1, x)` → `{2πk₁}, k₁ ∈ ℤ`, while a second `solve` uses the `k` of the first. When `k`
has a value where `solve` stands, a `subs` pair or a function's parameter, and the equation does not
mention `k`, the value is put in, as
for a derivative's variable (§14.10): `subs(solve(sin(x) = 1/2, x), k = 0)` → `{π/6, 5π/6}`, the
principal solutions, and after `f(k) := solve(sin(x) = 1, x)`, `f(1)` → `{5π/2}`. `numeric` leaves
`k`: `numeric(solve(tan(x) = 1, x), 10)` → `{3.141592654k + 0.7853981634}, k ∈ ℤ`. A decimal
value gives decimal offsets: `solve(sin(x) = 0.5, x)` in degrees → `{360k + 30.0, 360k + 150.0}`.

**Degrees, minutes and seconds.** `dms(x)` writes an angle as `[d, m, s]`, `d` and `m` integers and
`s` a number; a negative angle carries its sign on the first component that is not zero, the rest
non-negative. A plain number is an angle in degrees in every mode, and a rational multiple of `π` an
angle in radians, converted; anything else leaves the call. `degrees(d, m, s)` is the inverse, a
number of degrees; only its first component that is not zero may be negative. The literal
`30°15′20″` (§5) is the angle itself.

| Input | Result |
|---|---|
| `dms(30.2561)` | `⟨30, 15, 21.96⟩` |
| `dms(-1/2)` | `⟨0, -30, 0⟩` |
| `dms(π/7)` | `⟨25, 42, 360/7⟩` |
| `dms(30°15′20″)` | `⟨30, 15, 20⟩` |
| `degrees(30, 15, 20)` | `2723/90` |
| `degrees(-30, 30, 0)` | `-61/2` |

### 14.19 Geometry solvers

**Triangles.** `triangle(p₁ = v₁, p₂ = v₂, p₃ = v₃)` takes three of the six parts `a b c A B C`, the
sides and their opposite angles, each name held and its value evaluated, and returns all six as a
list of relations in the order `a b c A B C`. A plain number given for an angle is in the angle
mode's unit, any other angle (`90°`, `π/2`) in radians, and the angles come back in the mode's unit.

| Given | Method |
|---|---|
| three sides (SSS) | each angle by the law of cosines |
| two sides and the angle between them (SAS) | the third side by the law of cosines, the angle opposite the shorter given side by the law of sines, the last by the angle sum |
| two angles and a side (ASA, AAS) | the third angle by the angle sum, the other sides by the law of sines |
| two sides and an angle opposite one of them (SSA) | the ambiguous case: none, one or two triangles, always a **set** of lists |

| Input | Result |
|---|---|
| `triangle(a = 3, b = 4, C = 90°)` | `[a = 3, b = 4, c = 5, A = asin(3/5), B = π/2 - asin(3/5), C = π/2]` |
| `triangle(a = 3, b = 4, c = 5)` | `[a = 3, b = 4, c = 5, A = acos(4/5), B = acos(3/5), C = π/2]` |
| `triangle(A = 30°, B = 60°, c = 2)` | `[a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]` |
| `triangle(a = 1, b = 2, A = 30°)` | `{[a = 1, b = 2, c = √3, A = π/6, B = π/2, C = π/3]}` |
| `triangle(a = 3, b = 4, A = 30°)` | a set of two triangles, with `B = asin(2/3)` and `B = π - asin(2/3)` |
| `triangle(a = 1, b = 2, c = 3)` | `{}`: no such triangle |
| `triangle(A = 30, B = 60, c = 2)` in degrees | `[a = 1, b = √3, c = 2, A = 30, B = 60, C = 90]` |

Values are exact where the special angles give them, and otherwise the inverse functions stay;
`numeric` gives decimals, and so does a decimal among the data: in degrees
`triangle(a = 3, b = 4, C = 90.0)` gives every part as a decimal. Data that make no triangle, a side not shorter than the other two together
or angles that add up to a half turn or more, give `{}`. Three angles, a side that is not positive or
an angle not strictly between 0 and a half turn is an error (§16), and a value with a symbol leaves
the call. `steps` names each law as it is used.

**Shape formulas.** `trianglearea(a, b, c)` (Heron's formula), `circumradius(a, b, c)` and
`inradius(a, b, c)` of three sides: `trianglearea(3, 4, 5)` → `6`, `circumradius(3, 4, 5)` → `5/2`,
`inradius(3, 4, 5)` → `1`; sides that make no triangle give `undefined`, and symbols the formula
(`trianglearea(a, b, c)` → `√((a + b + c)(-a + b + c)(a - b + c)(a + b - c))/4`).
`polygonarea([[x₁, y₁], …])` is the shoelace formula, signed: positive when the vertices run
counterclockwise (`polygonarea([[0, 0], [4, 0], [4, 3]])` → `6`).

**Coordinate geometry.** Points are vectors `[x, y]` (`distance` and `midpoint` take any one
dimension). `distance([1, 2], [4, 6])` → `5`; `midpoint([1, 2], [4, 6])` → `⟨5/2, 4⟩`;
`slope([1, 2], [4, 6])` → `4/3`, `undefined` for a vertical line; `lineequation([1, 2], [3, 6])` →
`y = 2x`, and `x = 1` for the vertical `lineequation([1, 2], [1, 6])`;
`circleequation([1, -2], 3)` → `(x - 1)² + (y + 2)² = 9`.

**Conics from their definitions.** Each returns its relation in `x` and `y` as `P = 0`, `P` expanded
and collected, its rational content cleared and its leading term positive. A directrix is a relation
linear in `x` and `y`.

| Input | Definition | Result |
|---|---|---|
| `parabola([0, 1], y = -1)` | as far from the focus as from the directrix | `x² - 4y = 0` |
| `conic([0, 0], x = 4, 1/2)` | the distance to the focus `e` times that to the directrix | `3x² + 4y² + 8x - 16 = 0` |
| `ellipse([-3, 0], [3, 0], 10)` | the distances to the foci add up to `2a` | `16x² + 25y² - 400 = 0` |
| `hyperbola([-5, 0], [5, 0], 6)` | the distances to the foci differ by `2a` | `16x² - 9y² - 144 = 0` |

An ellipse needs `2a` greater than the distance between the foci, a hyperbola `2a` between 0 and it,
and `conic` a positive eccentricity; the arguments must not hold `x` or `y` (§16).

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
| Minutes or seconds out of place | `15′`, `x′` | `′` follows a number of degrees, as in 30°15′20″ |
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
| Reserved later-phase name | `grad(f)`, `help(sin)` | grad is not available yet |
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
| A side at infinity | `limit(e^x, x, inf, left)` | a limit at ∞ or -∞ has no side |
| `taylor`'s order or point | `taylor(e^x, x, 0, 1/2)`, `taylor(e^x, x, inf, 2)` | taylor's order must be a non-negative integer / taylor's point must be finite |
| A Taylor polynomial that does not exist | `taylor(sin(x)/x, x, 0, 2)`, `taylor(sqrt(x), x, 0, 2)` | the derivative of order 0 is undefined at x = 0 / the derivative of order 1 could not be shown continuous at x = 0 |
| `solve` arguments | `solve(x, x)`, `solve(x = 1, [x, x])`, `solve(x = 1, 2)` | solve needs an equation or a set of equations / solve needs a variable or a list of distinct variables |
| Bounds in the wrong order (§14.15) | `area(x, x, 1, 0)`, `volume(x, x, 1, 0)`, `arclength(x, x, 1, 0)`, `maximize(x, x, 1, 0)` | area needs a ≤ b (and likewise) |
| Shells left of the axis | `shellvolume(x, x, -1, 1)` | shellvolume needs a ≥ 0 |
| Linear-algebra arguments (§14.16) | `det([1, 2])`, `rref(3)`, `norm([[1, 2]])`, `det(M, minors)`, `norm(v, 3)` | det needs a square matrix / rref needs a matrix / norm needs a vector / det's method must be cofactor or elimination / norm's order must be 1, 2 or inf |
| A singular matrix | `inverse([[1, 2], [2, 4]])`, `[[0]]^-1` | the matrix is singular |
| `linsolve`, `gramschmidt`, `normalize` shapes | `linsolve([[1, 2]], [1, 2])`, `gramschmidt([[1, 2], [3]])`, `normalize([0, 0])` | linsolve needs a matrix and a vector of matching height / gramschmidt needs vectors of one length / normalize needs a vector that is not zero |
| `charpoly`'s variable | `charpoly([[1, 2], [3, 4]], 2)`, `charpoly([[x, 1], [0, 1]], x)` | charpoly needs a variable / charpoly's variable must not occur in the matrix |
| `identity`, `zeros` sizes | `identity(0)`, `zeros(2, 1/2)`, `det(M, cofactor)` of a 9×9 | identity needs a positive integer / zeros needs positive integers / cofactor expansion is limited to 8×8 |
| `degrees` components | `degrees(30, -1, 0)`, `degrees(i, 0, 0)` | only the first component of degrees that is not zero carries a sign / degrees needs real numbers |
| `triangle`'s parts (§14.19) | `triangle(A = 30°, B = 60°, C = 90°)`, `triangle(a = -1, b = 2, c = 2)`, `triangle(a = 1, b = 2, C = 200°)`, `triangle(a = 1, a = 2, C = 20°)` | triangle needs a side / triangle's sides must be positive / triangle's angles must be between 0 and a half turn / triangle's parts must be distinct |
| Coordinate-geometry arguments (§14.19) | `slope(1, [1, 2])`, `distance([1, 2], [1, 2, 3])`, `lineequation([1, 2], [1, 2])`, `polygonarea([[0, 0], [1, 1]])` | slope needs points [x, y] / distance needs two points of one dimension / lineequation needs two distinct points / polygonarea needs at least three points [x, y] |
| Conic arguments (§14.19) | `ellipse([-3, 0], [3, 0], 6)`, `hyperbola([-3, 0], [3, 0], 6)`, `conic([0, 0], x = 4, -1)`, `parabola([0, 0], x^2 = 4)`, `parabola([x, 0], y = 1)` | ellipse needs 2a greater than the distance between the foci / hyperbola needs 2a between 0 and the distance between the foci / conic needs a positive eccentricity / parabola needs a directrix, a line such as y = -1 / parabola's arguments must not hold x or y |
| An application's other arguments | `area(x, 2, 0, 1)`, `arclength([t, t, t], t, 0, 1)` | area needs a variable / arclength needs a function or a pair [x(t), y(t)] |
| The prime notation (§5) | `sin'(x)`, `y' + 1`, `f'(1)` with `f(x, y) := x y` | the prime notation is for user functions: write diff(sin(x), x) / y' needs an argument / the prime notation needs a function of one argument: write diff |

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
| `solve(sin(x) = 1/2, x)` | `solve(sin(x) = 1/2, x)` | `{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ` (§14.18) |
| `d/dx (x^2 sin x)` | `diff(x^2*sin(x), x)` | `x² cos(x) + 2x sin(x)` |
| `∫ x e^x dx` | `integrate(x*e^x, x)` | `x e^x - e^x` (no `+ C`, §14.7) |
| `∫[0,1] x^2 dx` | `integrate(x^2, x, 0, 1)` | `1/3` |
| `lim[x→0] sin(x)/x` | `limit(sin(x)/x, x, 0)` | `1` (§10.3) |
| `taylor(e^x, x, 0, 4)` | `taylor(e^x, x, 0, 4)` | `x⁴/24 + x³/6 + x²/2 + x + 1` (§14.14) |
| `[[1,2],[3,4]]^-1` | `[[1, 2], [3, 4]]^(-1)` | `[[-2, 1], [3/2, -1/2]]` (§14.16) |
| `eigenvalues([[2,1],[1,2]])` | `eigenvalues([[2, 1], [1, 2]])` | `⟨1, 3⟩` (§14.16) |
| `rref([[1,2,3],[4,5,6]])` | `rref([[1, 2, 3], [4, 5, 6]])` | `[[1, 0, -1], [0, 1, 2]]` (§14.16) |
| `triangle(a = 3, b = 4, C = 90°)` | `triangle(a = 3, b = 4, C = 90*pi/180)` | `[a = 3, b = 4, c = 5, A = asin(3/5), B = π/2 - asin(3/5), C = π/2]` (§14.19) |
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

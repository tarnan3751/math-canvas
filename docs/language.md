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
free.) In the app's worksheet, a cell whose every line that is not empty is a **note line**, `#`
followed by a space and text that does not begin with `=>` (the corpus runner's expectation), is a
note: it is shown as text and never run (`docs/reference/app.md`, **Cells**); anywhere else such a
line is a comment.

**Statements.** A script is a sequence of statements, some of them blocks of statements over several
lines (`for … end`, §19), inside which `;` and a newline both separate statements. A statement ends at `;`, at a newline that
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
| Base 16, 2 or 8: `0x`, `0b` or `0o` (either case), then digits of the base (either case), `_` allowed between digits | `0xff`, `0XFF`, `0b1011`, `0o17`, `0xFFFF_FFFF` | integer literal (exact), §14.27 |

- A base literal begins wherever `0x`, `0b` or `0o` is followed by an ASCII letter, digit or `_`;
  every such character after the prefix belongs to it, and one that is no digit of the base is an
  error (`0b102`: "`2` is not a binary digit"; `0x1g`: "`g` is not a hexadecimal digit"), as is a
  `_` not between two digits ("a digit separator must stand between digits"); a base literal of more
  than 100,000 characters, its prefix included, is "number out of range". `0x` followed by
  anything else is `0` and the name `x`, as before: `0x + 1` ⇒ `0*x + 1`. The literal is its
  integer: `0xff` ⇒ `255`.
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

- **Start:** a character in Unicode category Lu, Ll, Lt or Lo, except the characters
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
- Examples: `x`, `x1`, `x_1`, `x₁`, `θ`, `velocity`, `d`, `dx`. A `_` starts no identifier: it
  starts a unit (§3.6), so `_tmp` is the error "unknown unit `_tmp`" and `x_m` is the identifier
  `x_m`, not `x` times a unit (write `x _m`).

### 3.3 Reserved names

| Group | Names | May be defined (`:=`) | May be bound (§11.2) |
|---|---|---|---|
| Constants | `pi` `π`, `e`, `i`, `inf` `infinity` `∞`, `true`, `false`, `undefined` | no | `e` and `i` only, in some positions |
| Domains | `Integers` `ℤ`, `Rationals` `ℚ`, `Reals` `ℝ`, `Complexes` `ℂ` | no | no |
| Builtins | every name in §12, including the reserved later-phase names | no | no |
| Notation word | `lim` (§10.3) | no | no |
| Keywords (§19) | `if`, `then`, `else`, `for`, `while`, `function`, `return`, `end`, `do`, `break`, `continue`, `global`, `step` | no | no |
| Operator words | `in` (§8.1), `and`, `or`, `xor`, `not`, `implies` (§8.4), `mod` (§9.5) | no | no |

A keyword where an operand is expected is an error ("`then` is a reserved word and cannot stand here"); at the start of a
statement `if`, `for`, `while` and `function` begin the statements of programs (§19), and `end` or
another keyword is "unexpected `end`". `if` also begins
the if expression (§19) where an expression begins; as an operator's operand it must be
parenthesised (`1 + if …`: "parenthesise the if"). A keyword ends the expression before it, so
`x then` is "unexpected `then`". The word `in` is the ASCII
spelling of `∈` (§8.1), not an identifier, and so are the boolean operators' words (§8.4). The word `mod` is the infix remainder `a mod b` (§9.5)
and, directly before `(`, the builtin `mod(a, b)`; it is never an identifier.
`lim` is not a builtin: it is a reserved notation word, used only by the limit notation
(§10.3). `left` and `right` are literal words only in the fourth argument of `limit`; everywhere
else they are ordinary identifiers.

### 3.4 Operators and punctuation

```
+  -  *  /  ^  **  !  %  °  ′  ″  =  !=  <  <=  >  >=  in  mod  \  ->  :=  ..
and  or  xor  not  implies
(  )  [  ]  {  }  ⟨  ⟩  |  ,  ;  #
```

plus units, `_` and a name (§3.6),

plus the Unicode aliases (§3.5), `×` (cross product, §9.4), `∪` and `∩` (union and intersection,
§8.5), the superscripts, and the notation tokens `∫`, `√`, `∛`, `∜`, `Σ`/`∑`, `Π`/`∏`, the
derivative token and the partial derivative token (§10.1). A prime, `'` or `′`, directly after an
identifier joins it, written `'`: `y′′` is the name `y''` of the prime notation (§5); after a number
and `°`, `′` is the minutes of an angle.

The lexer takes the **longest match**: `**` is power, `!=` is not-equal, `->` is the arrow, `<=`
and `>=` are relations. So `5!=3` ⇒ `5 != 3` (write `5! = 3` to compare a factorial), and
`x<-1` ⇒ `x < -1`. `==`, `=<`, `=>` and `<>` are errors with the hint to use `=`, `<=`, `>=`
or `!=`. Any other character that is not a letter, digit or listed symbol (`∇`, `⊗`) is an
error naming the character ("unknown operator `∇`").

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
| `and`, `or`, `xor`, `not` | `∧`, `∨`, `⊕`, `¬` | the boolean operators (§8.4) | `∧`, `∨`, `⊕`, `¬` |
| `implies` | `⟹`, `⇒` | implication (§8.4) | `⟹` |
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

### 3.6 Units

A **unit** is `_` followed by a letter and then any identifier continue characters (§3.2): `_m`,
`_km`, `_kWh`, `_degC`, `_µm`, `_kΩ`. It is one token, and the name after the underscore must be a
unit of the table (§14.26), else the error "unknown unit `_foo`"; a `_` followed by anything but a
letter is the error "expected a unit name after `_`". A unit never joins a neighbouring number or
name: `3_m` is `3 _m`, since a numeral ends at `_`, but `x_m` is one identifier (§3.2). Primes do
not join a unit. A unit is an operand like a name: after a number or a factor it multiplies
(`5 _km/_h` ⇒ `5*_km/_h`, `9.8 _m/_s^2`), and the postfix operators apply (`_m²`). A product
times a unit part is one product, as it prints: `3°_m` ⇒ `3*pi/180*_m`, `(2x) _m` ⇒ `2*x*_m`. A unit cannot
be defined (`_m := 3`: "a unit cannot be defined") or bound.

## 4. Grammar

### 4.1 Precedence

Tightest first. Every level is described in its own section below.

| Level | Construct | Operand | Associativity | Examples |
|---|---|---|---|---|
| 1 Postfix | `!`, `%`, `°` (with `′`, `″`), superscript power | the primary on its left | left to right | `5!!` ⇒ `factorial(factorial(5))`; `x²!` ⇒ `factorial(x^2)` |
| 2 Power | `^` (`**`) | left: a postfix expression; right: a unary expression | right | `2^3^2` ⇒ `2^(3^2)`; `2^3!` ⇒ `2^factorial(3)`; `2^-1` ⇒ `2^(-1)` |
| 3a Prefix | unary `-` `+`; prefix function names (`sin x`); `√ ∛ ∜` | the next unary expression | right (nest) | `-2^2` ⇒ `-(2^2)`; `sin x^2` ⇒ `sin(x^2)`; `sin 2x` ⇒ `sin(2)*x` |
| 3b Big operators | `d/dx`, `d^n/dx^n`, `∂/∂x`, `∂ⁿ/∂x∂y`; `lim[…]`; `Σ[…]` `Π[…]` | the rest of the current multiplicative term | — | `lim[x→0] sin(x)/x` ⇒ `limit(sin(x)/x, x, 0)` |
| 3c Integral | `∫ … dx`, `∫[a, b] … dx` | an additive expression up to its differential | — | `∫ x + 1 dx` ⇒ `integrate(x + 1, x)` |
| 4 Multiplicative | `*` (`·` `⋅`), `/` (`÷`), `×`, `mod`, `\`, implicit multiplication | unary expressions | left | `1/2x` ⇒ `(1/2)*x`; `a/b c` ⇒ `(a/b)*c`; `a mod b c` ⇒ `mod(a, b)*c` |
| 5 Additive | `+`, `-`, `∪`, `∩` | terms | left | `a - b + c` ⇒ `(a - b) + c`; `A ∪ B ∩ C` ⇒ `intersect(union(A, B), C)` |
| 6 Relational | `=`, `!=`, `<`, `<=`, `>`, `>=`, `in` | additive expressions | none | `a < b < c` is an error |
| 6a Negation | `not` (`¬`) | a relational expression | right (nest) | `not x = 1` ⇒ `not(x = 1)` |
| 6b Conjunction | `and` (`∧`) | negations | left | `p and q and r` ⇒ `and(p, q, r)` |
| 6c Exclusive or | `xor` (`⊕`) | conjunctions | left | `p xor q and r` ⇒ `xor(p, and(q, r))` |
| 6d Disjunction | `or` (`∨`) | exclusive ors | left | `p or q and r` ⇒ `or(p, and(q, r))` |
| 6e Implication | `implies` (`⟹`, `⇒`) | disjunctions | right | `p implies q implies r` ⇒ `implies(p, implies(q, r))` |
| 7 Lambda | `->` | parameters; body to the right | right | `x -> x^2`; `(x, y) -> x + y` |
| 7 If expression | `if … then … else …` (§19) | a condition; two branches, the else branch to the right | right | `if n = 0 then 1 else n f(n - 1)` ⇒ `if(n = 0, 1, n*f(n - 1))` |
| 8 Definition | `:=` | statement level only | — | `f(x) := x^2 + 1` |

Parenthesised expressions `( )`, sets `{ }`, bracket constructors `[ ]`, vectors `⟨ ⟩` and
absolute-value bars `| |` are primaries.

### 4.2 Grammar summary

EBNF; `{ }` is repetition, `[ ]` is an option, quoted text is a token. Implicit multiplication
is the `factor` alternative of `term`.

```
script      = { [ statement ] terminator } ;
terminator  = ";" | newline | end-of-input ;
statement   = definition | function | control | expr ;     (* function, control: §19 *)

definition  = name ":=" expr
            | name "(" name { "," name } ")" ":=" expr ;

expr        = lambda | conditional | implication ;
conditional = "if" implication "then" expr "else" expr ;      (* §19 *)
implication = disjunction [ ( "implies" | "⟹" | "⇒" ) implication ] ;
disjunction = exclusive { ( "or" | "∨" ) exclusive } ;
exclusive   = conjunction { ( "xor" | "⊕" ) conjunction } ;
conjunction = negation { ( "and" | "∧" ) negation } ;
negation    = ( "not" | "¬" ) negation | relation ;
lambda      = params "->" expr ;
params      = name | "(" name { "," name } ")" ;

relation    = additive [ relop additive ] ;
relop       = "=" | "!=" | "<" | "<=" | ">" | ">=" | "in" ;

additive    = term { ( "+" | "-" | "∪" | "∩" ) term } ;
term        = unary { ( "*" | "/" | "×" | "mod" | "\" ) unary | factor } ;
factor      = unary ;          (* implicit multiplication; see §9.2 for what may start it *)

unary       = ( "-" | "+" ) unary
            | prefix-name unary              (* prefix application, §7.2 *)
            | radical unary                  (* √ ∛ ∜ *)
            | derivative term                (* §10.1 *)
            | partial-head term              (* §10.1 *)
            | limit-head term                (* §10.3 *)
            | sum-head term | product-head term   (* §10.4 *)
            | integral                       (* §10.2 *)
            | power ;
power       = postfix [ "^" unary ] ;
postfix     = primary { "!" | "%" | "°" | superscript-power }
            | number "°" number "′" [ number "″" ] ;   (* degrees, minutes, seconds, §5 *)

primary     = number | name | unit | constant | domain
            | call
            | "(" expr ")"
            | "{" [ expr { "," expr } ] "}"
            | "[" [ expr { "," expr } ] "]"
            | "⟨" expr { "," expr } "⟩"
            | "|" additive "|"
            | partial-name ;                 (* ∂f/∂x, §10.1 *)
call        = callable-name "(" [ expr { "," expr } ] ")"
            | name primes "(" [ expr { "," expr } ] ")" ;   (* prime notation, §5 *)
primes      = ( "'" | "′" ) { "'" | "′" } ;            (* directly after the name *)

derivative  = "d/d" name | "d^" digits "/d" name "^" digits
            | "d" sup-digits "/d" name sup-digits ;     (* one token, §10.1 *)
partial-head= "∂" [ order ] "/" denominator { denominator } ;   (* one token, §10.1 *)
partial-name= "∂" [ order ] name "/" denominator { denominator } ;   (* one token *)
denominator = "∂" name [ order ] ;
order       = "^" digits | sup-digits ;
limit-head  = "lim" "[" name "->" additive [ "+" | "-" ] "]" ;
sum-head    = ( "sum" | "Σ" | "∑" ) "[" name "=" additive "," additive "]" ;
product-head= ( "product" | "Π" | "∏" ) "[" name "=" additive "," additive "]" ;
integral    = "∫" [ "[" additive "," additive "]" ] additive differential ;
differential= a name "d" rest, rest itself a name not beginning with "d",
              that is not a builtin name ;   (* "ddx" is an error, §10.2 *)
```

The statements of programs (§19):

```
function    = "function" name "(" [ name { "," name } ] ")" block "end" ;
control     = if | for | while ;
if          = "if" implication "then" block { "else" "if" implication "then" block }
              [ "else" block ] "end"                  (* a newline after the first then *)
            | "if" implication "then" line-block { "else" "if" implication "then" line-block }
              [ "else" line-block ] [ "end" ] ;       (* on one line *)
for         = "for" name "in" ( additive ".." additive [ "step" additive ] | additive )
              ( "do" | newline ) block "end" ;
while       = "while" implication ( "do" | newline ) block "end" ;
block       = { [ inner ] ( ";" | newline ) } ;       (* up to else or end *)
line-block  = { [ inner ] ";" } ;                     (* up to else, end or the line's end *)
inner       = name ":=" expr | control | "return" expr | "break" | "continue"
            | "global" name { "," name } | expr ;
```

`unit` is the unit token (§3.6).
`callable-name` is a name that is a builtin function or command (§12) or a user-defined
function in the current environment (§11.3), or an unknown function that the `dsolve` call around
it declares (§14.22). `prefix-name` is a name in the prefix set (§7.2).
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
  user-defined `f`, `f(x) := x^3; f'(2)` → `12` and `f''(x)` → `6x`; inside `dsolve` (§14.22) the
  unknown `y'(x)` is the derivative of the unknown function, and `diff(y(x), x)` evaluates to it. A
  builtin cannot be primed (`sin'(x)`: "the prime notation is for user functions: write
  diff(sin(x), x)"), and a primed name without `(` is an error ("y' needs an argument").
- Superscript power: `x²` ⇒ `x^2`; `sin(x)²` ⇒ `sin(x)^2`; `x²y` ⇒ `x^2*y`.
- **Call** `f(a, b)`: a callable name immediately followed (whitespace irrelevant) by `(`
  (§11.3). A call is a primary, so its `(` binds tightest of all, before any postfix operator:
  `sin(x)^2` ⇒ `sin(x)^2`, `f(x)!` ⇒ `factorial(f(x))`, and `sin (x)^2` is the same.
  Only a name can be called: `(x -> x^2)(3)` is `)(` multiplication and fails on evaluation
  ("a function cannot be multiplied: give it a name and call it, as in `f(3)`"); define the function
  first.

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

`in` relates an expression to a domain or a set: `n ∈ ℤ` ⇒ `n in Integers`, `x in Reals`, `2 ∈ {1,
2}`. Its right side must be a domain, a set, or an expression that may be one (a name, a set
operation); a number, a truth value or a boolean expression, a relation or a list, vector or matrix
there fails on evaluation ("∈ needs a set or a domain on its right"). §14.4 says when membership is
decided.

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
function, so `f(x) := x f(x - 1)` parses `f(x - 1)` as a call. A recursion ends only through a
condition, so `f(x) := if x = 0 then 1 else x f(x - 1)` recurses (§19), while a definition with no
`if` can only recurse forever and fails at use with the cycle error (§14.3).

### 8.4 Boolean operators

`and` (`∧`), `or` (`∨`), `xor` (`⊕`), `not` (`¬`) and `implies` (`⟹`, also typed `⇒`) combine truth
values: `true`, `false`, relations, names that stand for truth values, `isprime`, a function you
defined whose value is a truth value, and other boolean expressions; another built-in function's
value (`sin(x)`, `x mod 2`) is not a truth value, and an undeclared `f(x)` reads as the product
`f·x` (§15.4), which is not one either. They bind more loosely than relations and more tightly
than a lambda's arrow, in the order of §4.1: `not` > `and` > `xor` > `or` > `implies`. `and`, `or`
and `xor` gather a chain of themselves into one node of several operands; `implies` is
right-associative. So `x > 0 and x < 1` ⇒ `and(x > 0, x < 1)`, `not p or q` ⇒ `or(not(p), q)` and `p
∧ q ⟹ r` ⇒ `implies(and(p, q), r)`. `→` stays the lambda's arrow (§8.2): `p → q` is a function of
`p`. The words are operators, not names: `and(p, q)` is an error, and none of them can be defined or
bound. After an operand a `not` is an error (`2 not p`: unexpected `not`), but where an operand
begins it is the operator, so `x = not p` ⇒ `x = not(p)`.

### 8.5 Set operators

`A ∪ B` (union) and `A ∩ B` (intersection) are the builtins `union(A, B)` and `intersect(A, B)`
written infix, at the additive level, left-associative with `+` and `-`; `A \ B` (difference) is
`setdiff(A, B)`, at the multiplicative level as `mod` is (§9.5). So `A ∪ B ∩ C` ⇒
`intersect(union(A, B), C)`, `A ∪ B \ C` ⇒ `union(A, setdiff(B, C))` and `x ∈ A ∪ B` ⇒ `x in
union(A, B)`. The operators parse whatever their operands are; on evaluation the operands must be
sets (§14.24). There is no ASCII spelling but the calls.

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
`2√3`, `3i`, `3 _m`), between factors and units (`x _m`, `_kg _m`, §3.6), between brackets
(`(a)(b)`, `(x+1)y`, `|x||y|`), between names separated by whitespace (`x y`, `2 sin x`), after
postfix operators (`x²y`, `20%x`) and after a closed notation form (`∫ x dx y`). It never splits an
identifier: `xy` is one symbol.

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

### 9.5 Remainder `mod`

`a mod b` is the builtin `mod(a, b)` written infix (§12.1): an operator word at the multiplicative
level, left-associative with `*`, `/` and `×`, its right operand a unary expression, exactly as
`/`'s is.

- `17 mod 5` ⇒ `mod(17, 5)` → `2`; `-7 mod 3` ⇒ `mod(-7, 3)` → `2` (the sign follows the
  divisor).
- `2x mod 3` ⇒ `mod(2*x, 3)`; `a mod b c` ⇒ `mod(a, b)*c`; `a + b mod c` ⇒ `a + mod(b, c)`;
  `a mod b mod c` ⇒ `mod(mod(a, b), c)`; `2^10 mod 7` ⇒ `mod(2^10, 7)` → `2`.
- `x mod 2 = 0` is the relation `mod(x, 2) = 0`.
- After an operand, `mod` is always the operator, whatever follows: `x mod (5)` ⇒ `mod(x, 5)`, and
  `x mod(5, 3)` is an error, since `(5, 3)` is not an operand. Where `mod` begins an operand and
  `(` follows it, it is the call: `mod(17, 5)`, `2·mod(17, 5)`.

## 10. Notation

Each notation parses to the same node as its command form; the printer chooses which to show.

| Notation | Command form | Operand |
|---|---|---|
| `d/dx f` | `diff(f, x)` | rest of the term |
| `d^2/dx^2 f`, `d²/dx² f` | `diff(f, x, 2)` | rest of the term |
| `∂/∂x f`, `∂²/∂x² f` | `diff(f, x)`, `diff(f, x, 2)` | rest of the term |
| `∂²/∂x∂y f` | `diff(f, y, x)` | rest of the term |
| `∂f/∂x`, `∂²f/∂x∂y` | `diff(f, x)`, `diff(f, y, x)` | none: `f` is a name |
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

**Rest of the term.** A big operator (`d/dx`, `∂/∂x`, `lim`, `Σ`, `Π`) takes everything up to the end of
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

**Partial derivatives.** `∂` begins one token, and nothing else uses it. Its shapes, with no
whitespace inside: `∂`, an optional order (`^` + digits or superscript digits), an optional name
`f`, `/`, then one or more denominators `∂` + name, each with an optional order. The
denominators' orders must add up to the head's (1 when it has none): `∂³/∂x²∂y`; otherwise the
error is "the order must be the sum of the denominator's orders", and any other shape is "the
partial derivative notation is ∂/∂x, ∂ⁿ/∂xⁿ, ∂ⁿ/∂x∂y or ∂f/∂x". The rightmost denominator is
differentiated first, as in `∂²f/∂x∂y = ∂/∂x (∂f/∂y)`; a run of one variable is one derivative of
that order.

- `∂/∂x x^2 y` ⇒ `diff(x^2*y, x)`; `∂²/∂x² f` ⇒ `diff(f, x, 2)`.
- `∂²/∂x∂y f` ⇒ `diff(diff(f, y), x)`; `∂³/∂x²∂y f` ⇒ `diff(diff(f, y), x, 2)`.
- `∂f/∂x` ⇒ `diff(f, x)`, a complete operand: `∂f/∂x y` ⇒ `diff(f, x)*y`. `f` must be a name
  that is not a constant, a builtin or a function: write `∂/∂x f(x, y)` to differentiate a call.
- `∂²/∂x f` is an error ("the order must be …"); so is `∂/∂x` with nothing after it ("expected an
  expression to differentiate"). `∂` alone, or `∂x`, is the shape error, and `∂sin/∂x` is "∂f/∂x
  needs a name for f; write ∂/∂x sin(…)". `∂/∂i i^2` is an error, as `d/di` is (§11.2).

Both notations parse to the same derivative nodes as `diff`; which is printed is the printer's
choice (§15.1): `∂` when the expression differentiated has two or more free symbols, `d` otherwise,
and a derivative of a derivative in another variable as one mixed head, `∂²/∂y∂x f`.

### 10.2 Integrals

`∫` is followed by optional bounds `[a, b]` (a `[` right after `∫` is always the bounds), an
**integrand** parsed as an additive expression, and a **differential**: a name of the form `d` +
rest, with rest itself a name (it begins with a letter) and the whole name not a builtin
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
say so: "No exact value was found, so the value is approximate, to N digits". So `numeric(∫[0, 1] e^(-x^2) dx)` →
`0.7468241328…` and `numeric(integrate(e^(-x^2), x, -∞, ∞), 10)` → `1.772453851`, while
`∫[0, 1] e^(-x^2) dx` alone, in the exact format, is the integral itself. A value that cannot be
found to those digits leaves the integral, with the step "No approximate value can be found":
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

What an integral evaluates to is §14.13. `∫[c, d] ∫[a, b] f dx dy` and the command form
`integrate(f, [x, a, b], [y, c, d])` are the same nested integrals; iterated integrals are §14.20.

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
  right))` shows `No method finds lim[x → 0+] (x mod 1)/x. Approximate values suggest 1.0, to
  12 digits, but that is not a proof, so the limit is left as it is`.
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
- Bars do not nest without brackets: `|a|b||` reads `|a|`, `b`, then `||`, an error ("there is
  nothing between the bars of `| |`"); `|x - |y||` is an error ("an expression is missing here"); `||x||` is an error.
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
| a `for` loop's variable (§19) | yes | `for i in 1..3 do s := s + i end` |
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
substituted from the environment. Arity errors are parse errors (`sin(1, 2)`: "`sin` takes 1
argument; `help(sin)` shows examples"). **Prefix** marks the prefix set (§7.2).

### 12.0 Getting started

Work out an expression, define a function, plot it, make a slider and see the steps. Click an
example to run it in a new cell, or open the welcome worksheet to try each step with a note before
it.

| Input | Result |
|---|---|
| `sqrt(8) + 1/3` | `2√2 + 1/3` |
| `f(x) := x^2 - 2x` | `f(x) := x² - 2x` |
| `plot(x^2 - 2x)` | `y = x² - 2x` |
| `a := slider(-3, 3, 1)` | `a := slider(-3, 3, 1)` |
| `steps(solve(x^2 - 2x = 0, x))` | `{0, 2}` |

The in-app Help lists these first, as the entry Getting started (`docs/reference/app.md`, **Help**);
each runs alone in a new cell, as every example does (§12.4).

### 12.1 Functions

| Name | Arity | Prefix | Alias | Notes |
|---|---|---|---|---|
| `abs` | 1 | | `\|x\|` | |
| `sign` | 1 | | | −1, 0 or 1 |
| `floor`, `ceil`, `trunc` | 1 | | | |
| `round` | 1 | | | half away from zero |
| `mod` | 2 | | `a mod b` | `mod(a, b)`; §9.5 |
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
| `erf`, `erfc` | 1 | | | the error function and `1 − erf(x)`; exact at 0 and ±∞, odd (§14.25) |
| `gamma` | 1 | | | `Γ(x)`, exact at the integers and half-integers, `undefined` at its poles (§14.25) |
| `beta` | 2 | | | `B(a, b) = Γ(a)Γ(b)/Γ(a + b)` (§14.25) |
| `sinh cosh tanh coth sech csch` | 1 | yes | | |
| `asinh acosh atanh acoth asech acsch` | 1 | yes | | |
| `re`, `im`, `conj`, `arg` | 1 | | | |
| `min`, `max` | 1 or more | | | |
| `numerator`, `denominator` | 1 | | | of a number, or structural (§14.9) |
| `nth` | 2 | | | `nth(c, k)`, 1-based, of a list or vector |
| `length` | 1 | | | elements of a list, vector or set |
| `print` | 1 | | | `print(e)` is `e`; as a statement in a block it prints `e` (§19.2) |
| `append` | 2 | | | `append(c, x)`: the list, vector or matrix `c` with `x` after its last item, by the bracket rule (`append([1, 2], 3)` → `⟨1, 2, 3⟩`, `append([], x)` → `⟨x⟩`) |
| `setnth` | 3 | | | `setnth(c, k, x)`: `c` with its `k`-th item (1-based) replaced by `x`, by the bracket rule |
| `dot` | 2 | | `u * v` for vectors | dot product |
| `cross` | 2 | | `u × v` | cross product; ordinary product for scalars |
| `transpose`, `inverse`, `rank`, `rref`, `trace` | 1 | | `M^-1` for `inverse` | of a matrix; §14.16 |
| `nullspace`, `colspace` | 1 | | | bases, §14.16 |
| `linsolve` | 2 | | | `linsolve(A, b)`, §14.16 |
| `gramschmidt` | 1 | | | `gramschmidt([v₁, …])`, §14.16 |
| `norm` | 1 or 2 | | | `norm(v[, 1 \| 2 \| inf])`, §14.16 |
| `normalize` | 1 | | | §14.16 |
| `identity`, `size` | 1 | | | `identity(n)`; `size(M)` → `[m, n]`, `size` of a list or a set its count; §14.16, §14.24 |
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
| `isprime` | 1 | | | `true` or `false`; §14.23 |
| `nextprime`, `prevprime` | 1 | | | the nearest prime above or below; §14.23 |
| `primes` | 2 | | | `primes(a, b)`, the primes in `[a, b]`; §14.23 |
| `ifactor` | 1 | | | the prime factorisation, shown as a product; §14.23 |
| `divisors` | 1 | | | the positive divisors, ascending; §14.23 |
| `numdivisors`, `sigma`, `totient`, `mobius` | 1 | | | the divisor functions; §14.23 |
| `powmod` | 3 | | | `powmod(a, k, m)`, `a^k` modulo `m`; §14.23 |
| `invmod` | 2 | | | `invmod(a, m)`, the inverse of `a` modulo `m`; §14.23 |
| `crt` | 2 | | | `crt([r₁, …], [m₁, …])`, the Chinese remainder theorem; §14.23 |
| `union`, `intersect`, `setdiff` | 2 | | `A ∪ B`, `A ∩ B`, `A \ B` | §8.5, §14.24 |
| `symdiff`, `cartesian` | 2 | | | the symmetric difference, the Cartesian product; §14.24 |
| `subsets` | 1 | | | the power set; §14.24 |
| `choose` | 2 | | | an alias of `binomial` |
| `permutations` | 2 | | | `permutations(n, k)`, the arrangements of `k` of `n`; §14.23 |
| `multinomial` | 2 | | | `multinomial(n, [k₁, …])`; §14.23 |
| `fibonacci`, `lucas`, `catalan`, `bell`, `partitions` | 1 | | | the sequences; §14.23 |
| `stirling2` | 2 | | | `stirling2(n, k)`, Stirling numbers of the second kind; §14.23 |
| `units`, `magnitude` | 1 | | | the unit part and the magnitude of a quantity; §14.26 |
| `hex`, `bin`, `oct` | 1 | | | the integer shown in base 16, 2, 8; §14.27 |
| `base` | 2 | | | `base(n, b)`, the integer shown in base `b` from 2 to 36; §14.27 |
| `digits` | 2 | | | `digits(n, b)`, the digits of `n ≥ 0` in base `b`; §14.27 |
| `bitand`, `bitor`, `bitxor` | 2 | | | on integers; §14.27 |
| `bitnot`, `bitcount` | 1 | | | `-a - 1`; the ones of `n ≥ 0`; §14.27 |
| `shiftleft`, `shiftright`, `bit` | 2 | | | `shiftleft(a, n)`, arithmetic `shiftright(a, n)`, `bit(a, i)`; §14.27 |
| `pv`, `fv`, `pmt`, `nper` | 3 to 5 | | | `pv(r, n, pmt[, fv[, type]])` and likewise; §14.28 |
| `rate` | 3 to 6 | | | `rate(n, pmt, pv[, fv[, type[, guess]]])`, numeric; §14.28 |
| `npv` | 2 | | | `npv(r, [c₁, …])`; §14.28 |
| `irr` | 1 or 2 | | | `irr([c₀, …][, guess])`, numeric; §14.28 |
| `effective`, `nominal` | 2 | | | `effective(nominal, m)`, `nominal(effective, m)`; §14.28 |
| `amortization` | 3 | | | `amortization(r, n, pv)`, the schedule; §14.28 |
| `mean`, `median`, `mode` | 1 or 2 | | | `mean(data[, counts])`; `mode` a value, or the set of the tied values; §14.25 |
| `variance`, `stdev` | 1 or 2 | | | the sample's, dividing by `n − 1`; §14.25 |
| `pvariance`, `pstdev` | 1 or 2 | | | the population's, dividing by `n`; §14.25 |
| `range`, `quartiles`, `iqr` | 1 or 2 | | | `quartiles` → `[Q1, Q2, Q3]`, the medians of the halves (TI); §14.25 |
| `skewness`, `kurtosis` | 1 or 2 | | | sample-adjusted, the kurtosis in excess of 3 (Excel's `SKEW`, `KURT`); §14.25 |
| `summary`, `frequencies` | 1 or 2 | | | `[n = …, mean = …, …]`; the matrix of values and counts; §14.25 |
| `percentile` | 2 or 3 | | | `percentile(data[, counts], p)`, `p` in `[0, 100]`, interpolated (R's type 7); §14.25 |
| `zscore` | 2 or 3 | | | `zscore(x, data)`, `zscore(x, μ, σ)`; §14.25 |
| `sort`, `cumsum` | 1 | | | ascending; running sums; §14.25 |
| `cov`, `cor` | 1 or 2 | | | `cov(xs, ys)` or `cov(M)` of a two-column matrix; sample covariance, Pearson's r; §14.25 |
| `normalpdf`, `invnorm` | 1 to 3 | | | `normalpdf(x[, μ[, σ]])`, `invnorm(p[, μ[, σ]])`; `μ`, `σ` default to 0, 1; §14.25 |
| `normalcdf` | 2 to 4 | | | `normalcdf(a, b[, μ[, σ]])`, `P(a < X ≤ b)`, `-∞` and `∞` as bounds; §14.25 |
| `tpdf`, `invt`; `chi2pdf`, `invchi2` | 2 | | | `tpdf(x, df)`, `invt(p, df)`; §14.25 |
| `tcdf`, `chi2cdf` | 3 | | | `tcdf(a, b, df)`; §14.25 |
| `fpdf`, `invf`; `fcdf` | 3; 4 | | | `fpdf(x, d1, d2)`, `fcdf(a, b, d1, d2)`; §14.25 |
| `binompdf`, `binomcdf` | 3 | | | `binompdf(n, p, k)`, `binomcdf(n, p, k)` = `P(X ≤ k)`; §14.25 |
| `poissonpdf`, `poissoncdf`, `geometpdf`, `geometcdf` | 2 | | | `poissonpdf(λ, k)`, `geometpdf(p, k)` (trials, `k ≥ 1`); §14.25 |
| `hypergeompdf`, `hypergeomcdf` | 4 | | | `hypergeompdf(N, K, n, k)`; §14.25 |
| `exppdf`; `expcdf` | 2; 3 | | | `exppdf(x, λ)`, `expcdf(a, b, λ)`; §14.25 |
| `unifpdf`; `unifcdf` | 3; 4 | | | `unifpdf(x, a, b)`, `unifcdf(lo, hi, a, b)`; §14.25 |
| `linreg`, `quadreg`, `cubicreg`, `quartreg` | 1 or 2 | | | `linreg(xs, ys)` or `linreg(M)`, least squares → `[y = …, r² = …(, r = …)]`; §14.25 |
| `polyreg` | 3 | | | `polyreg(xs, ys, n)`, degree 1 to 10; §14.25 |
| `expreg`, `lnreg`, `powreg` | 1 or 2 | | | `y = a·bˣ`, `y = a + b log x`, `y = a xᵇ` by their linear forms, numeric; §14.25 |
| `residuals` | 3 | | | `residuals(xs, ys, fit)`; §14.25 |
| `predict` | 2 | | | `predict(fit, x)`; §14.25 |
| `chi2gof` | 2 | | | `chi2gof(observed, expected)`, counts; §14.25 |
| `chi2test` | 1 | | | `chi2test(M)`, independence in a table of counts; §14.25 |
| `anova` | 2 or more | | | `anova(group₁, group₂, …)`, one-way; §14.25 |
| `zinterval` | 3 or 4 | | | `zinterval(data, σ, C)`, `zinterval(x̄, σ, n, C)` → `[lower, upper]`; §14.25 |
| `tinterval` | 2 or 4 | | | `tinterval(data, C)`, `tinterval(x̄, s, n, C)`; §14.25 |
| `propinterval`; `propinterval2` | 3; 5 | | | `propinterval(x, n, C)`, `propinterval2(x₁, n₁, x₂, n₂, C)`; §14.25 |

### 12.2 Commands

| Name | Arity | Held | Binds | `e`/`i` bindable | Notes |
|---|---|---|---|---|---|
| `solve` | 2 | 1, 2 | 2 | yes | `solve(rel \| {rels}, var \| [vars])`; §14.11 |
| `diff` | 2 or more | 1, 2 | 2, and every later name | no | `diff(f, x[, n])` with `n` anything but a name, its value a positive integer; `diff(f, x, y, …)` with names; notation `d/dx`, `∂/∂x`; §14.10 |
| `integrate` | 2 or 4, or brackets | 1, 2 | 2 | with bounds only | `integrate(f, x[, a, b])`, `integrate(f, [x, a, b], [y, c, d], …)`; notation `∫`; §14.13, §14.20 |
| `limit` | 3 or 4 | 1, 2, 4 | 2 | yes | `limit(f, x, a[, left \| right])`; notation `lim`; §10.3 |
| `sum` | 4 | 1, 2 | 2 | yes | `sum(f, k, a, b)`; notation `Σ`; §10.4 |
| `product` | 4 | 1, 2 | 2 | yes | `product(f, k, a, b)`; notation `Π`; §10.4 |
| `taylor` | 4 | 1, 2 | 2 | no | `taylor(f, x, a, n)`, the Taylor polynomial of degree `n` about `a`; §14.14 |
| `diophantine` | 2 | 1, 2 | 2 | yes | `diophantine(a x + b y = c, [x, y])`, the integer solutions; §14.23 |
| `truthtable` | 2 | 1, 2 | 2 | no | `truthtable(e, [p, q, …])`, the truth table of `e` in the names; §14.24 |
| `dsolve` | 2 or more | all | the unknowns' variable, in 1 and 2 | no | `dsolve(eq \| [eqs], y(x) \| [x(t), y(t), …][, y(a) = b, y'(a) = c …])`; the second argument declares the unknown functions for the whole call; §14.22 |
| `rsolve` | 2 or more | all | the index, in 1 | no | `rsolve(recurrence, a(n)[, a(k) = v …])`; the second argument declares the unknown function as `dsolve`'s does; §14.24 |
| `odesolve` | 5 or more | all | the unknowns' variable, in 1 and 2 | no | `odesolve(eq \| [eqs], y(x) \| [x(t), …], conditions…, a, b[, steps = n][, at = x₀])`, numeric; `steps` and `at` literal words there; §14.22 |
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
| `grad`, `hessian`, `laplacian` | 2 | 1, 2 | 2 | no | `grad(f, [x, y, …])`, a list of names bound as `solve`'s; §14.20 |
| `jacobian`, `divergence`, `curl` | 2 | 1, 2 | 2 | no | `jacobian([f₁, …], [x, …])`; §14.20 |
| `lagrange` | 3 | 1, 2, 3 | 3 | no | `lagrange(f, [g₁ = c₁, …], [x, y, …])`, the critical points; §14.20 |
| `lineintegral` | 5 | 1, 2, 3 | 3, and `x, y, z` in the field | yes | `lineintegral(f, [x(t), y(t)], t, a, b)`; §14.20 |
| `laplace`, `ilaplace` | 3 | 1, 2, 3 | 2 | no | `laplace(f, t, s)`, `ilaplace(F, s, t)`; §14.21 |
| `surfaceintegral` | 4 | all | the brackets' names, and `x, y, z` in the field | no | `surfaceintegral(f, [x(u, v), y(u, v), z(u, v)], [u, a, b], [v, c, d])`; §14.20 |
| `triangle` | 3 | left sides | | | `triangle(a = 3, b = 4, C = 90°)`, three parts by name; each name held, its value evaluated; §14.19 |
| `convert` | 2 | — | | | `convert(q, unit)`, the quantity in other units; §14.26 |
| `tvm` | 4 or 5 | left sides | | | `tvm(n = 10, i = 5 %, pv = -1000, pmt = 0)`, four of `n i pv pmt fv` and `type` by name, the names literal words there; §14.28 |
| `subs` | 2 or more | all | | | `subs(e, x = v[, y = w …])` |
| `ztest` | 3 to 5 | all | | | `ztest(data, σ, μ₀[, alternative])`, `ztest(x̄, σ, n, μ₀[, alternative])`; §14.25 |
| `ttest` | 2 to 5 | all | | | `ttest(data, μ₀[, alternative])`, `ttest(x̄, s, n, μ₀[, alternative])`; §14.25 |
| `ttest2` | 2 to 4 | all | | | `ttest2(data₁, data₂[, pooled][, alternative])`, Welch's unless `pooled`; §14.25 |
| `pairedttest` | 1 to 3 | all | | | `pairedttest(data₁, data₂[, alternative])` or `pairedttest(M[, alternative])`; §14.25 |
| `proptest` | 3 or 4 | all | | | `proptest(x, n, p₀[, alternative])`; §14.25 |
| `proptest2` | 4 or 5 | all | | | `proptest2(x₁, n₁, x₂, n₂[, alternative])`; §14.25 |
| `tinterval2` | 3 or 4 | all | | | `tinterval2(data₁, data₂, C[, pooled])`; §14.25 |
| `assume` | 1 or more | left sides | | | `assume(rel[, rel …])`; each relation's left side is held, its right side evaluated; returns the set of the relations recorded |
| `assumptions` | 0 | | | | the set of current assumptions |
| `numeric` | 1 or 2 | — | | | `numeric(e[, digits])` |
| `exact` | 1 | — | | | a decimal to the rational it denotes |
| `steps` | 1 | 1 | | | the value of `e`, with its steps (§14.7): each one idea of the working, standing for a run of the evaluation's rewrites, the operation's steps (`expand`, `factor`, `diff`, `integrate`, `solve`) first and then the simplifier's |
| `clear` | 1 | 1 | | | `clear(name)` unbinds `name`; returns the symbol |
| `help` | 1 | 1 | | | `help(name)`, the name held; its value is the name, and the app opens its entry in the Help window, the CLI prints it (`docs/reference/app.md`, **Help**); anything but a name is "help needs a name". It takes a function's or a command's name: the operators, `mod` included (an infix word, so `help(mod)` does not parse), are reached from the Help window's list and search |

The bounds of `integrate`, `sum`, `product` and the commands of §14.15, the point of `limit`, the
order of `taylor` and a third argument of `diff` that is not a name (its order, §14.10) are
evaluated. A command's bound variable is bound in every
argument before it: in `area(f, g, x, a, b)` and `volume(f, g, x, a, b)`, in `f` and `g`.

`left` and `right` in `limit` are literal words in that position only, as `cofactor` and
`elimination` are in `det`'s second argument, and the alternatives `two`, `less` and `greater` and
the word `pooled` are as the last arguments of a test (§14.25). A held argument is evaluated, when the command
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
`lineequation`, `circleequation`, `parabola`, `ellipse`, `hyperbola` and `conic` (§14.19), or
`jacobian`, `hessian`, `divergence`, `curl`, `laplacian`, `lagrange`, `lineintegral` and
`surfaceintegral` (§14.20), or `laplace` and `ilaplace` (§14.21), or `dsolve` and `odesolve`
(§14.22), or `isprime`, `nextprime`, `prevprime`, `primes`, `ifactor`, `divisors`, `numdivisors`,
`sigma`, `totient`, `mobius`, `powmod`, `invmod`, `crt`, `diophantine`, `choose`, `permutations`,
`multinomial`, `fibonacci`, `lucas`, `catalan`, `bell`, `stirling2` and `partitions` (§14.23), or
`union`, `intersect`, `setdiff`, `symdiff`, `subsets`, `cartesian`, `truthtable` and `rsolve`
(§14.24), or `convert`, `units` and `magnitude` (§14.26), or `hex`, `bin`, `oct`, `base`, `digits`
and the bitwise functions (§14.27), or the finance functions and `tvm` (§14.28), or `erf`, `erfc`,
`gamma` and `beta` (§12.1), or `mean`, `median`, `mode`, `variance`, `pvariance`, `stdev`, `pstdev`,
`range`, `quartiles`, `iqr`, `percentile`, `zscore`, `sort`, `cumsum`, `summary`, `skewness`,
`kurtosis`, `cov`, `cor`, `frequencies`, the distributions' names, the regressions', the tests' and
the intervals' (§14.25), is no longer free for a definition: a saved cell that defined it opens, and
reports that the name is a builtin.

### 12.3 Reserved later-phase names

These names are builtins now: they parse as calls and evaluate to an error ("`slider` works only
as a cell of its own in the app, as in `a := slider(0, 5, 0.5)`", and `plot`'s below). They cannot
be defined.

| Name | Phase |
|---|---|
| `slider` | 3, evaluated by the canvas, not the engine |
| `plot` | 10, drawn on the canvas by the worksheet, not the engine |

`slider` stays unavailable to the engine and the CLI. In the app's worksheet, a statement whose
whole right side is the call, `name := slider(min, max, step)`, defines a slider on the canvas
instead of reaching the engine: the three arguments are evaluated to exact decimals (an integer, a
rational with a terminating expansion or a decimal; they may not read a slider or a point), the
name is bound to the slider's value, and the cell echoes the statement as typed. The slider and the
canvas's named points are variables of every cell that does not define the name itself, a point
`A` as the vector of its coordinates (`nth(A, 1)` is its abscissa); `docs/reference/app.md`,
**Canvas variables**, says how.

`plot` is unavailable to the engine and the CLI too, which refuse it with "`plot` works only as a cell of its own in the app, as in `plot(x^2)`". In the app's worksheet, a statement that is the call, `plot(e₁, e₂, …)`,
draws a graph on the canvas for each argument instead of reaching the engine: an expression `e` is
the function graph `y = e` in `x`, an equation `y = e` with `e` free of `y` is the same, and any
other equation in `x` and `y` is an implicit curve (`plot(x^2 + y^2 = 4)`). An argument that is no
such equation (an inequality, a list, a vector, an equation true everywhere such as `plot(y)`, one
in neither `x` nor `y`, or one with an undefined side such as `plot(1/0)`) is the statement's error,
and the statement draws nothing. Each graph is stored as typed, never evaluated, so it follows the
names it reads: `plot(f(x) + a)` moves when `f` is defined again or the slider `a` is dragged. The
cell shows each graph's equation with those names worked out and `x` and `y` left free, as the
canvas draws them whatever a cell defined them as, `plot(f(x))` showing `y = x² - 2x` when `f(x) :=
x^2 - 2x`; a statement ended by `;` shows nothing and still draws. The graphs belong to the cell
that drew them: only a submission of the cell draws (a run for another reason, such as a slider's
drag, only shows the equations), and the next submission replaces them, the first graph of the cell
by the first argument and so on, each keeping its colour, and an argument whose graph was deleted on
the canvas drawn anew in its own place, so the others keep theirs; a cell that no longer plots,
cleared or deleted, removes its graphs. Two cells with the same argument draw two graphs. A graph
deleted on the canvas stays deleted until its cell is submitted again; `docs/reference/app.md`,
**Plot statements**, says how.

### 12.4 Examples

An example for each name and operator that no other table of inputs and results shows, each
evaluated alone in a new cell: the exact format, radians, nothing defined. The in-app help takes
its examples from this table and from the other tables of inputs and results, never from the
prose, whose examples may need their context (`docs/reference/app.md`, **Help**), and a test
evaluates every one of them and compares the result with the one shown.

| Names | Input | Result |
|---|---|---|
| `=` `!=` | `2 + 2 = 4`, `1 != 2` | `true`, `true` |
| `<` `<=` `>` `>=` | `2 < 3`, `3 <= 3`, `5 > 3`, `2 >= 3` | `true`, `true`, `true`, `false` |
| `in` | `2 in Integers` | `true` |
| `->` | `f := x -> x^2` | `f := x → x²` |
| `:=` | `f(x) := x^2 + 1` | `f(x) := x² + 1` |
| `%` `°` `′` `″` | `20%`, `sin(30°)`, `30°15′36″` | `1/5`, `1/2`, `1513π/9000` |
| `**` `+` `*` `·` `⋅` `÷` | `2**3`, `+3`, `2*3`, `2·3`, `2⋅3`, `6 ÷ 4` | `8`, `3`, `6`, `6`, `6`, `3/2` |
| `√` `∛` `∜` | `√16`, `∛27`, `∜16` | `4`, `3`, `2` |
| `d/dx` `d^n/dx^n` | `d/dx x^3`, `d^2/dx^2 x^3` | `3x²`, `6x` |
| `∂/∂x` `∂ⁿ/∂x∂y` | `∂/∂x x^2 y`, `∂²/∂x∂y x^2 y` | `2x y`, `2x` |
| `Σ` `Π` | `Σ[k=1, 10] k`, `Π[k=1, 5] k` | `55`, `120` |
| `∫` | `∫[0, 1] x^2 dx` | `1/3` |
| `×` `\` | `[1, 0, 0] × [0, 1, 0]`, `{1, 2, 3} \ {2}` | `⟨0, 0, 1⟩`, `{1, 3}` |
| `¬` `∧` `⊕` `∨` `⟹` `⇒` | `¬true`, `true ∧ false`, `true ⊕ true`, `true ∨ false`, `false ⟹ true`, `false ⇒ true` | `false`, `false`, `false`, `true`, `true`, `true` |
| `!` `^` `-` `/` | `5!`, `2^10`, `-2^2`, `6/4` | `120`, `1024`, `-4`, `3/2` |
| `lim` | `lim[x→0] sin(x)/x` | `1` |
| `if` | `if 2 < 3 then 1 else 2` | `1` |
| `print` | `print(2 + 3)` | `5` |
| `append` `setnth` | `append([1, 2], 3)`, `setnth([1, 2, 3], 2, 0)` | `⟨1, 2, 3⟩`, `⟨1, 0, 3⟩` |
| `mod` | `17 mod 5`, `mod(17, 5)` | `2`, `2` |
| `∪` `∩` | `{1, 2} ∪ {2, 3}`, `{1, 2} ∩ {2, 3}` | `{1, 2, 3}`, `{2}` |
| `not` `and` `xor` `or` `implies` | `not true`, `true and false`, `true xor true`, `true or false`, `false implies true` | `false`, `false`, `false`, `true`, `true` |
| `abs` `sign` | `abs(-3)`, `sign(-2)` | `3`, `-1` |
| `floor` `ceil` `trunc` `round` | `floor(5/2)`, `ceil(5/2)`, `trunc(-5/2)`, `round(5/2)` | `2`, `3`, `-2`, `3` |
| `binomial` `log10` `log2` | `binomial(5, 2)`, `log10(1000)`, `log2(8)` | `10`, `3`, `3` |
| `cot` `sec` `csc` | `cot(pi/4)`, `sec(0)`, `csc(pi/2)` | `1`, `1`, `1` |
| `asin` `atan` `acot` `asec` `acsc` `atan2` | `asin(1)`, `atan(1)`, `acot(1)`, `asec(2)`, `acsc(2)`, `atan2(1, 0)` | `π/2`, `π/4`, `π/4`, `π/3`, `π/6`, `π/2` |
| `sinh` `cosh` `tanh` `sech` | `sinh(0)`, `cosh(0)`, `tanh(0)`, `sech(0)` | `0`, `1`, `0`, `1` |
| `coth` `csch` | `diff(coth(x), x)` | `-csch(x)²` |
| `asinh` `acosh` `atanh` | `asinh(0)`, `acosh(1)`, `atanh(0)` | `0`, `0`, `0` |
| `acoth` | `diff(acoth(x), x)` | `1/(-x² + 1)` |
| `asech` | `diff(asech(x), x)` | `-1/(x² √(1/x - 1) √(1/x + 1))` |
| `acsch` | `diff(acsch(x), x)` | `-1/(√(x² + 1) \|x\|)` |
| `re` `im` `conj` `arg` | `re(3 + 4i)`, `im(3 + 4i)`, `conj(3 + 4i)`, `arg(-1)` | `3`, `4`, `3 - 4i`, `π` |
| `min` `max` | `min(3, 1, 2)`, `max(3, 1, 2)` | `1`, `3` |
| `gcd` `lcm` | `gcd(12, 18)`, `lcm(4, 6)` | `6`, `12` |
| `cbrt` `root` `exp` `ln` `log` | `cbrt(27)`, `root(16, 4)`, `exp(0)`, `ln(e^2)`, `log(8, 2)` | `3`, `2`, `1`, `2`, `3` |
| `cos` `tan` `acos` | `cos(pi/3)`, `tan(pi/4)`, `acos(1/2)` | `1/2`, `1`, `π/3` |
| `erf` `erfc` `gamma` `beta` | `erf(0)`, `erfc(0)`, `gamma(5)`, `beta(2, 3)` | `0`, `1`, `24`, `1/12` |
| `numerator` `denominator` | `numerator(6/4)`, `denominator(6/4)` | `3`, `2` |
| `cross` `transpose` | `cross([1, 0, 0], [0, 1, 0])`, `transpose([[1, 2], [3, 4]])` | `⟨0, 0, 1⟩`, `[[1, 3], [2, 4]]` |
| `nullspace` `colspace` `linsolve` | `nullspace([[1, 2], [2, 4]])`, `colspace([[1, 2], [2, 4]])`, `linsolve([[2, 1], [1, 3]], [3, 5])` | `[[-2, 1]]`, `[[1, 2]]`, `⟨4/5, 7/5⟩` |
| `norm` `normalize` | `norm([3, 4])`, `normalize([3, 4])` | `5`, `⟨3/5, 4/5⟩` |
| `eigenvectors` `diagonalize` | `eigenvectors([[2, 0], [0, 3]])`, `diagonalize([[2, 0], [0, 3]])` | `[[2, [[1, 0]]], [3, [[0, 1]]]]`, `[[[1, 0], [0, 1]], [[2, 0], [0, 3]]]` |
| `det` `charpoly` | `det([[1, 2], [3, 4]])`, `charpoly([[1, 2], [3, 4]], x)` | `-2`, `x² - 5x - 2` |
| `lu` | `lu([[2, 1], [4, 3]])` | `[[[0.0, 1.0], [1.0, 0.0]], [[1.0, 0.0], [0.5, 1.0]], [[4.0, 3.0], [0.0, -0.5]]]` |
| `qr` `cholesky` | `qr([[2, 0], [0, 3]])`, `cholesky([[4, 2], [2, 2]])` | `[[[-1.0, 0.0], [0.0, -1.0]], [[-2.0, 0.0], [0.0, -3.0]]]`, `[[2.0, 0.0], [1.0, 1.0]]` |
| `svd` `lstsq` `expm` | `svd([[2, 0], [0, 1]])`, `lstsq([[1], [1]], [1, 3])`, `expm([[0, 0], [0, 0]])` | `[[[-1.0, 0.0], [0.0, 1.0]], [[2.0, 0.0], [0.0, 1.0]], [[-1.0, 0.0], [0.0, 1.0]]]`, `⟨2.0⟩`, `[[1.0, 0.0], [0.0, 1.0]]` |
| `trianglearea` `circumradius` `inradius` `polygonarea` | `trianglearea(3, 4, 5)`, `circumradius(3, 4, 5)`, `inradius(3, 4, 5)`, `polygonarea([[0, 0], [4, 0], [0, 3]])` | `6`, `5/2`, `1`, `6` |
| `distance` `midpoint` `slope` | `distance([0, 0], [3, 4])`, `midpoint([0, 0], [4, 2])`, `slope([0, 0], [2, 4])` | `5`, `⟨2, 1⟩`, `2` |
| `lineequation` `circleequation` | `lineequation([0, 1], [1, 3])`, `circleequation([1, 2], 3)` | `y = 2x + 1`, `(x - 1)² + (y - 2)² = 9` |
| `pv` `fv` `pmt` `nper` | `pv(0, 10, -100)`, `fv(0, 10, -100)`, `pmt(0, 10, 1000)`, `nper(0, -100, 1000)` | `1000`, `1000`, `-100`, `10` |
| `rate` `npv` `irr` | `rate(10, 0, -100, 200)`, `npv(0, [100, 100])`, `irr([-100, 110])` | `0.07177346253629316421300632502334202`, `200`, `0.1` |
| `effective` `nominal` | `effective(0.12, 12)`, `nominal(0.12, 12)` | `0.126825030131969720661201`, `0.113865515214995689516260830321928` |
| `amortization` | `amortization(0, 2, 100)` | `[[1, -50, 0, -50, 50], [2, -50, 0, -50, 0]]` |
| `mean` `median` `stdev` `quartiles` | `mean([1, 2, 4])`, `median([3, 1, 2])`, `stdev([1, 2, 4])`, `quartiles([1, 2, 3, 4, 5, 6, 7])` | `7/3`, `2`, `√21/3`, `⟨2, 4, 6⟩` |
| `normalpdf` `invnorm` `normalcdf` | `normalpdf(0)`, `invnorm(1/2)`, `normalcdf(-inf, 0)` | `√2/(2√π)`, `0.0`, `0.5` |
| `tpdf` `invt` `invf` | `tpdf(0, 1)`, `invt(1/2, 3)`, `invf(0.5, 2, 2)` | `1/π`, `0.0`, `1.0` |
| `binompdf` `binomcdf` `poissoncdf` `expcdf` | `binompdf(10, 1/2, 3)`, `binomcdf(10, 1/2, 3)`, `poissoncdf(2, 3)`, `expcdf(0, 1, 2)` | `15/128`, `11/64`, `19/(3 e²)`, `-1/e² + 1` |
| `linreg` `tinterval` | `linreg([1, 2, 3], [2, 4, 6])`, `tinterval([1, 2, 3, 4], 0.95)` | `[y = 2x, r² = 1, r = 1]`, `⟨0.4457397432394785, 4.554260256760522⟩` |
| `sum` `product` | `sum(k, k, 1, 10)`, `product(k, k, 1, 5)` | `55`, `120` |
| `arclength` `area` `volume` `shellvolume` | `arclength(x, x, 0, 1)`, `area(x^2, x, 0, 1)`, `volume(x, x, 0, 1)`, `shellvolume(x, x, 0, 1)` | `√2`, `1/3`, `π/3`, `2π/3` |
| `extrema` `maximize` | `extrema(x^3 - 3x, x)`, `maximize(x - x^2, x, 0, 1)` | `{[-1, 2], [1, -2]}`, `[1/2, 1/4]` |
| `cancel` `simplify` `collect` `apart` | `cancel((x^2 - 1)/(x - 1))`, `simplify(sin(x)^2 + cos(x)^2)`, `collect(a x + b x, x)`, `apart(1/(x^2 - 1), x)` | `x + 1`, `1`, `x·(a + b)`, `1/(2(x - 1)) - 1/(2(x + 1))` |
| `hessian` `laplacian` | `hessian(x^2 y, [x, y])`, `laplacian(x^2 + y^2, [x, y])` | `[[2y, 2x], [2x, 0]]`, `4` |
| `jacobian` `divergence` `curl` | `jacobian([x y, x + y], [x, y])`, `divergence([x, y], [x, y])`, `curl([-y, x, 0], [x, y, z])` | `[[y, x], [1, 1]]`, `2`, `⟨0, 0, 2⟩` |
| `lagrange` | `lagrange(x + y, [x^2 + y^2 = 2], [x, y])` | `{[x = -1, y = -1, λ = -1/2], [x = 1, y = 1, λ = 1/2]}` |
| `lineintegral` `surfaceintegral` | `lineintegral(1, [t, 0], t, 0, 1)`, `surfaceintegral(1, [u, v, 0], [u, 0, 1], [v, 0, 2])` | `1`, `2` |
| `laplace` `ilaplace` | `laplace(1, t, s)`, `ilaplace(1/s, s, t)` | `1/s`, `1` |
| `subs` | `subs(x^2 + 1, x = 2)` | `5` |
| `numeric` | `numeric(pi, 5)` | `3.1416` |
| `nth` `length` `dot` | `nth([5, 6, 7], 2)`, `length([5, 6, 7])`, `dot([1, 2], [3, 4])` | `6`, `3`, `11` |
| `inverse` `rank` `trace` | `inverse([[1, 2], [3, 4]])`, `rank([[1, 2], [2, 4]])`, `trace([[1, 2], [3, 4]])` | `[[-2, 1], [3/2, -1/2]]`, `1`, `5` |
| `gramschmidt` `identity` `zeros` | `gramschmidt([[3, 4], [1, 0]])`, `identity(2)`, `zeros(2, 3)` | `[[3, 4], [16/25, -12/25]]`, `[[1, 0], [0, 1]]`, `[[0, 0, 0], [0, 0, 0]]` |
| `union` `intersect` `setdiff` | `union({1, 2}, {2, 3})`, `intersect({1, 2}, {2, 3})`, `setdiff({1, 2}, {2, 3})` | `{1, 2, 3}`, `{2}`, `{1}` |
| `units` `magnitude` `convert` | `units(5 _km/_h)`, `magnitude(5 _km/_h)`, `convert(1 _km, _m)` | `_km/_h`, `5`, `1000 _m` |
| `hex` `bin` `oct` `base` `digits` | `hex(255)`, `bin(5)`, `oct(8)`, `base(255, 16)`, `digits(255, 16)` | `0xff`, `0b101`, `0o10`, `0xff`, `⟨15, 15⟩` |
| `bitand` `bitor` `bitxor` `bitnot` `bitcount` | `bitand(12, 10)`, `bitor(12, 10)`, `bitxor(12, 10)`, `bitnot(5)`, `bitcount(7)` | `8`, `14`, `6`, `-6`, `3` |
| `shiftleft` `shiftright` `bit` | `shiftleft(1, 4)`, `shiftright(16, 2)`, `bit(5, 0)` | `16`, `4`, `1` |
| `mode` `range` `iqr` | `mode([1, 2, 2, 3])`, `range([3, 1, 4])`, `iqr([1, 2, 3, 4, 5, 6, 7])` | `2`, `3`, `4` |
| `variance` `pvariance` `pstdev` | `variance([1, 2, 4])`, `pvariance([1, 2, 4])`, `pstdev([1, 2, 3, 4])` | `7/3`, `14/9`, `√5/2` |
| `skewness` `kurtosis` | `skewness([1, 2, 4])`, `kurtosis([1, 2, 3, 10])` | `10√21/49`, `807/250` |
| `summary` | `summary([1, 2, 3, 4])` | `[n = 4, mean = 5/2, median = 5/2, min = 1, Q1 = 3/2, Q3 = 7/2, max = 4, stdev = √15/3]` |
| `frequencies` `percentile` `zscore` | `frequencies([1, 1, 2])`, `percentile([1, 2, 3, 4], 50)`, `zscore(4, [1, 2, 3, 4])` | `[[1, 2], [2, 1]]`, `5/2`, `3√15/10` |
| `sort` `cumsum` `cov` `cor` | `sort([3, 1, 2])`, `cumsum([1, 2, 3])`, `cov([1, 2, 3], [2, 4, 7])`, `cor([1, 2, 3], [2, 4, 6])` | `⟨1, 2, 3⟩`, `⟨1, 3, 6⟩`, `5/2`, `1` |
| `chi2pdf` `invchi2` `chi2cdf` | `chi2pdf(1, 2)`, `invchi2(0.95, 2)`, `chi2cdf(0, 2, 2)` | `1/(2√e)`, `5.991464547107982`, `0.6321205588285578` |
| `tcdf` `fpdf` `fcdf` | `tcdf(-inf, 0, 5)`, `fpdf(1, 2, 2)`, `fcdf(0, 1, 2, 2)` | `0.5`, `1/4`, `0.5` |
| `poissonpdf` `geometpdf` `geometcdf` | `poissonpdf(2, 3)`, `geometpdf(1/2, 2)`, `geometcdf(1/2, 2)` | `4/(3 e²)`, `1/4`, `3/4` |
| `hypergeompdf` `hypergeomcdf` | `hypergeompdf(10, 4, 3, 1)`, `hypergeomcdf(10, 4, 3, 1)` | `1/2`, `2/3` |
| `exppdf` `unifpdf` `unifcdf` | `exppdf(1, 2)`, `unifpdf(1, 0, 4)`, `unifcdf(0, 1, 0, 4)` | `2/e²`, `1/4`, `1/4` |
| `quadreg` `polyreg` | `quadreg([0, 1, 2, 3], [1, 2, 5, 10])`, `polyreg([0, 1, 2, 3], [1, 2, 5, 10], 2)` | `[y = x² + 1, r² = 1]`, `[y = x² + 1, r² = 1]` |
| `cubicreg` `quartreg` | `cubicreg([0, 1, 2, 3], [0, 1, 8, 27])`, `quartreg([0, 1, 2, 3, 4], [0, 1, 16, 81, 256])` | `[y = x³, r² = 1]`, `[y = x⁴, r² = 1]` |
| `expreg` `powreg` | `expreg([0, 1, 2, 3], [3, 6, 12, 24])`, `powreg([1, 2, 3], [2, 8, 18])` | `[y = 3.0·2.0^x, r² = 1.0]`, `[y = 2.0x^(2.0), r² = 1.0]` |
| `lnreg` | `lnreg([1, 2, 4, 8], [1, 2, 3, 4])` | `[y = 1.442695040888963407359924681001892 log(x) + 1.0, r² = 1.0]` |
| `residuals` `predict` | `residuals([1, 2, 3], [2, 4, 7], y = 2x)`, `predict(y = 2x + 1, 3)` | `⟨0, 0, 1⟩`, `7` |
| `chi2gof` `chi2test` | `chi2gof([10, 20], [15, 15])`, `chi2test([[10, 20], [20, 10]])` | `[χ² = 10/3, df = 1, p = 0.06788915486182878]`, `[χ² = 20/3, df = 1, p = 0.009823274507519242]` |
| `anova` | `anova([1, 2, 3], [4, 5, 6])` | `[F = 27/2, df1 = 1, df2 = 4, p = 0.02131164112875672]` |
| `ztest` `ttest` | `ztest([1, 2, 3, 4], 1, 2)`, `ttest([1, 2, 3, 4], 2)` | `[z = 1, p = 0.317310507862914]`, `[t = √15/5, df = 3, p = 0.4950253460597108]` |
| `ttest2` `pairedttest` | `ttest2([1, 2, 3, 4], [2, 3, 4, 5])`, `pairedttest([1, 2, 3, 4], [2, 3, 5, 5])` | `[t = -√30/5, df = 6, p = 0.3153335962012298]`, `[t = -5, df = 3, p = 0.01539243807330232]` |
| `proptest` `proptest2` | `proptest(55, 100, 1/2)`, `proptest2(40, 100, 50, 100)` | `[z = 1, p = 0.317310507862914]`, `[z = -10√22/33, p = 0.155218489684684]` |
| `propinterval` `propinterval2` | `propinterval(40, 100, 0.95)`, `propinterval2(40, 100, 50, 100, 0.95)` | `⟨0.3039817664728938, 0.4960182335271063⟩`, `⟨-0.2371974789178038, 0.03719747891780381⟩` |
| `zinterval` | `zinterval([1, 2, 3, 4], 1, 0.95)` | `⟨1.520018007729973, 3.479981992270028⟩` |
| `tinterval2` | `tinterval2([1, 2, 3, 4], [2, 3, 4, 5], 0.95)` | `⟨-3.233714695164706, 1.233714695164706⟩` |
| `minimize` | `minimize(x^2 - 2x, x, 0, 3)` | `[1, -1]` |
| `tvm` | `tvm(n = 10, i = 5%, pv = -1000, pmt = 0)` | `[n = 10, i = 1/20, pv = -1000, pmt = 0, fv = 16679880978201/10240000000]` |
| `assume` `assumptions` | `assume(x > 0)`, `assumptions()` | `{x > 0}`, `{}` |
| `steps` | `steps(expand((x + 1)^2))` | `x² + 2x + 1`, with its step |
| `clear` `help` | `clear(x)`, `help(sin)` | `x`, `sin` |
| `slider` | `a := slider(0, 5, 0.1)` | `a := slider(0, 5, 0.1)`, a slider on the canvas in the app (§12.3) |
| `plot` | `plot(x^2 - 2x)`, `plot(x^2 + y^2 = 4)` | `y = x² - 2x`, `x² + y² = 4` |

## 13. Collections

### 13.1 Sets

`{a, b, c}` is a **Set**: unordered, duplicates removed on evaluation. `{}` is the empty set.
`solve` returns sets. Sets cannot be added, multiplied or raised to powers; they have their own operations, `∪`, `∩` and `\`
(§8.5) and the functions of §14.24.

### 13.2 Bracket constructors

`[e₁, …, eₙ]` builds a **Vector**, a **Matrix** or a **List**, by one rule that the constructor
applies to its elements, both when the parser builds the node and again after evaluation:

- **Vector** when every element is a scalar expression: a number, a constant other than
  `true`, `false` and the domains, a symbol, or arithmetic and function application over them.
  Symbols count as scalars; booleans and domains do not.
- **Matrix** when every element is a vector (from `[…]` or `⟨…⟩`) and all have the same length.
- **List** otherwise: ragged rows, or elements that are relations, sets, set operations, lists,
  matrices, booleans, domains or lambdas.
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
  `numeric(root(x^2 + 1/3, 3))` → `root(x² + 0.333…, 3)`. `e` to a power that stays symbolic
  stays the exponential function: `numeric(e^(2x + 1))` → `e^(2.0x + 1.0)`. A definite integral with no closed form
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
  ("`a` is defined in terms of itself"), unless it is a function that recurses through an if
  expression (§19): a call of a function is allowed again when a function whose body holds an
  `if` has been called since its last entry, its own included, at most 1,000 calls deep.
- A function's parameters shadow definitions: after `x := 3`, `f(x) := x^2` still squares its
  argument.
- `clear(a)` unbinds `a`. A symbol with no binding is itself.
- A definition statement prints an echo of what it defined: `f(x) := x^2 + 1`.

### 14.4 Relations

Relations are nodes. A relation between two numbers evaluates to `true` or `false` (`2 < 3` →
`true`, `2 ∈ ℤ` → `true`); otherwise it stays a relation (`x < 3`, `n ∈ ℤ`). An exact and an
inexact number compare by value: `1 = 1.0` → `true`. `<`, `<=`, `>` and `>=` with a complex
operand are an evaluation error ("complex numbers are not ordered").

Two truth values are equal or not (`true = false` → `false`); they are not ordered, `<`, `<=`, `>`
and `>=` with one is an error ("truth values are not ordered").

Membership in a set is decided when the items decide it: `2 ∈ {1, 2}` → `true`, `5 ∈ {1, 2}` →
`false`, `x ∈ {x, 2}` → `true`, while `x ∈ {1, 2}` stays, since `x` may be 1. Two items are the
same when they are equal as written or are numbers of equal value (`2.0 ∈ {1, 2}` → `true`), and
distinct when both are numbers, truth values, or lists, vectors or matrices of them, that differ.

### 14.5 Commands, hold and unevaluated forms

Commands receive their held arguments as parsed (§12.2), so `solve(x^2 = 4, x)` receives the
equation. A relation there is not decided, even between two numbers, so a call that stays
shows it as typed: `solve(0 = 0, x)` → `solve(0 = 0, x)`. A command that cannot complete returns its own node (`Integral`, `Limit`, `Sum`,
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
A sign is deduced from the facts through sums, products and powers, and a range's ends count:
after `assume(y >= -1, y <= 1)`, `1 - y²` is non-negative, so `abs(1 - y^2)` → `-y² + 1`.

### 14.7 Result shapes

- `solve` for one variable returns a set of values: `{2, 3}`. For several variables it returns a
  set of lists of equations: `solve({x + y = 3, x - y = 1}, [x, y])` → `{[x = 2, y = 1]}`. A
  numeric solution is a decimal at the session precision: `solve(x^5 - x - 1 = 0, x)` →
  `{1.1673…, …}`. When no method applies, `solve` returns itself (§14.11).
- A set whose members are all constants is shown in ascending order of value, `-∞` first and `∞`
  after the real members, the non-real members last: `{-√2, √2}`, `{-i, i}`.
- A general solution (of `solve`, §14.18, or `diophantine`, §14.23) uses an integer parameter,
  `k` (or `k₁`, `k₂` when `k` is taken): each
  member stands for its values at every integer `k`, `{2πk + π/6, 2πk + 5π/6}`, and the value is
  printed with its note, `{2πk + π/6, 2πk + 5π/6}, k ∈ ℤ`. After the statement the parameter is
  assumed in `ℤ`, as if `assume(k ∈ ℤ)` followed, so `simplify(cos(2πk))` → `1`. Two `solve` calls
  in one statement have two parameters (`k` and `k₁`), and the note lists both. When the equations
  do not mention `k` and `k` has a value where `solve` stands (a `subs` pair, a function's
  parameter), the value is put in for the parameter; when they do mention it, the parameter is `k₁`
  and the value is the input's: `subs(solve(sin(x) = k, x), k = 1/2)` →
  `{2πk₁ + π/6, 2πk₁ + 5π/6}, k₁ ∈ ℤ`. The details are in §14.18.
- `dsolve` returns `y(x) = …` with constants of integration `C₁`, `C₂`, … (fresh as `k` is: the
  first `C_n` not mentioned, defined or holding other facts), printed with the note `C₁ ∈ ℝ` and
  assumed real after the statement, as `k` is assumed an integer (§14.22). As for `k`, a value the
  name has where `dsolve` stands is put in: `subs(dsolve(y'(x) = y(x), y(x)), C_1 = 0)` →
  `y(x) = 0`.
- `rsolve` returns `a(n) = …` with constants `C₁`, `C₂`, … noted and assumed as `dsolve`'s are
  (§14.24).
- An indefinite integral returns an antiderivative without a constant: `∫ x dx` → `x²/2`. The
  worksheet may display `+ C`. An integral no technique finds returns itself (§14.13).
- `steps(e)` returns the value of `e`; the trace of the evaluation is attached to the output,
  and the CLI prints it before the value.

The detailed rules belong to the commands' own references.

### 14.8 Simplified forms

Every value is simplified, and the simplifier settles on one form for each family: logarithms are
natural (`ln(x)` → `log(x)`; `log10(x)`, `log2(x)` and `log(x, b)` → `log(x)/log(b)`, or a rational
when the value is one exactly: `log(8, 2)` → `3`), and a logarithm of a perfect power is a multiple
of one of its root (`log(8)` → `3 log(2)`, so `log(2) - log(4)` → `-log(2)`); a negation of a sum of
negative terms is distributed (`-(-a - d)` → `a + d`), and an integer multiple of a fraction joins it when its numerator is a sum of constants
(`(1 + sqrt(5))/2 - 1` → `(√5 - 1)/2`, while `(x + 1)/2 - 1` stays); `exp(x)` → `e^x`; `root(x, n)` and `cbrt(x)` of a
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
- **Successive and mixed partial derivatives.** `diff(f, x, y, …)` differentiates in `x`, then in
  `y`, and so on: `diff(x^2 y^3, x, y)` → `6x y²` is `∂²f/∂y∂x`, and parses to the same derivatives
  as `∂²/∂y∂x (x^2 y^3)`, so a definition echoes with `∂`. A third argument that is a name is
  always a variable of differentiation, bound as the second argument is, so no value reaches it:
  after `n := 2`, `diff(x^3, x, n)` → `0`; after `p(x, y) := diff(x^3 y^2, x, y)`, `p(1, 2)` → `12`;
  `subs(diff(x^3 y^2, x, y), y = 2)` → `12x²`. Anything else there is the order, which must
  evaluate to a positive integer: `diff(x^3, x, 2)` and `diff(x^3, x, 1 + 1)` → `6x`; an order that
  is not one (`1/2`, `2.0`) leaves the call, printed as written. Every variable is free in the
  result, as the first is: after `x := 2`, `diff(x^2 y^3, x, y)` → `12y²`. The chain is taken in
  one run of the rules.
- **From 0.8.0.** A cell that gave the order by a name (`n := 2; diff(x^3, x, n)`) now
  differentiates in `n` and gives `0`, and `diff(f, x, e)` is a parse error (`e` cannot be the
  variable); write the order as a number, `diff(x^3, x, 2)`.
- **The chain rule in several variables.** The derivative of a call of a defined function of two
  or more parameters, with an argument that depends on the variable and is not a name, is taken by
  the chain rule: after `f(u, v) := u^2 v`, `u(x, y) := x y` and `v(x, y) := x + y`,
  `steps(diff(f(u(x, y), v(x, y)), x))` says the derivative of `f(u, v)` is `fᵤ uₓ + fᵥ vₓ` with
  `fᵤ = 2u v` and `fᵥ = u²`, then differentiates the arguments, `uₓ = y` and `vₓ = 1`. The value
  is the plain derivative's. A parameter named as the variable is renamed in the steps (`x₁`).
- **Partial derivatives** say in their first step which symbols are held constant:
  `steps(diff(x^2 y^3, x))` begins "Differentiate with respect to x, treating y as constant".
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
- **Linear systems**: an unknown left free appears in the others' values, with no equation of its
  own: `solve(x + y = 3, [x, y])` → `{[x = -y + 3]}`. A system with no solution gives `{}`.
- **Polynomial systems** (every equation polynomial in the unknowns) are solved by elimination: an
  unknown that an equation holds linearly is put into the others (with a branch where its
  coefficient is zero when that may happen), an equation in one unknown is solved as above, and an
  equation that factors splits into one branch per factor; every candidate is substituted into the original equations and kept only when they
  hold. `solve({x y = 6, x + y = 5}, [x, y])` → `{[x = 2, y = 3], [x = 3, y = 2]}`,
  `solve({x^2 + y^2 = 1, y = x}, [x, y])` → `{[x = -√2/2, y = -√2/2], [x = √2/2, y = √2/2]}`.
  Every complex solution is returned, as for one polynomial. A system that leaves an unknown free
  (`{x^2 + y = 3, 2x^2 + 2y = 6}`), or that no step reduces (two quadratics that neither factor, nor
  hold an unknown linearly or in a single power), returns itself; any other system that is not
  linear returns itself too.
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
  `x √(x² - 1)/2 - log(x + √(x² - 1))/2`) or the bounds of a definite integral
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
- **Equal bounds** give `0`, whatever the integrand: `∫[1, 1] 1/x^2 dx` → `0`, `integrate(e^(x^2),
  x, 0, 0)` → `0`.
- **A definite integral** is `F(b) − F(a)` for an antiderivative `F` that is continuous between the
  bounds, which are evaluated with the outer facts: `∫[0, π] sin(x) dx` → `2`, `∫[0, t] x dx` →
  `t²/2`, `∫[2, 1] x dx` → `-3/2`. When the integrand or `F` has a singularity between the
  bounds, or the engine cannot rule one out, the integral is returned as it is, with a step that
  says so: `∫[0, 2] tan(x) dx` stays, and `∫[1, t] 1/x dx` stays until `assume(t > 1)`, then is
  `log(t)`. A bound may name the outer variable of the same name: after `assume(x > 1)`,
  `∫[1, x] 1/x dx` → `log(x)`. A bound must be known to be real: `∫[0, log(2)] e^x dx` → `1`, while
  `∫[0, sqrt(y)] 1 dx` stays, with a step that says so, until `assume(y >= 0)`.
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
  "`taylor`'s order must be a whole number, 0 or more, as in `taylor(e^x, x, 0, 3)`"; an infinite
  `a` is "`taylor`'s point must be finite".
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

`steps(…)` shows one step: "Approximate value, shown to at most 16 digits", or "lu needs a matrix
of numbers, so it is left as it is". The functions are in
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

### 14.20 Multivariable calculus

Partial derivatives are `diff` with every other symbol a constant, successive variables
(`diff(f, x, y)`) and the `∂` notation (§10.1, §14.10). The vector calculus commands take a
function or a field and a list of distinct variables, `[x, y, …]` or `⟨x, y, …⟩`, bound in the
function as `solve`'s unknowns are; each variable is free in the result, as a derivative's is
(after `x := 1`, `grad(x^2 y, [x, y])` → `⟨2y, 1⟩`). A field is a list or vector of scalar
expressions. Every partial derivative is taken by the rules of §14.10 and the result is
simplified; `steps(…)` lists the partial derivatives taken.

| Command | Result | Example |
|---|---|---|
| `grad(f, [x₁, …, xₙ])` | the vector `⟨∂f/∂x₁, …, ∂f/∂xₙ⟩` | `grad(x^2 + y^2, [x, y])` → `⟨2x, 2y⟩` |
| `jacobian([f₁, …, fₘ], [x₁, …, xₙ])` | the m×n matrix whose row i holds the partials of `fᵢ` | `jacobian([x y, x + y^2], [x, y])` → `[[y, x], [1, 2y]]` |
| `hessian(f, [x₁, …, xₙ])` | the n×n matrix of `∂²f/∂xⱼ∂xᵢ` | `hessian(x^3 y^2, [x, y])` → `[[6x y², 6x² y], [6x² y, 2x³]]` |
| `divergence([F₁, …, Fₙ], [x₁, …, xₙ])` | `∂F₁/∂x₁ + … + ∂Fₙ/∂xₙ` | `divergence([x^2, x y, z], [x, y, z])` → `3x + 1` |
| `curl([P, Q, R], [x, y, z])` | `⟨∂R/∂y − ∂Q/∂z, ∂P/∂z − ∂R/∂x, ∂Q/∂x − ∂P/∂y⟩` | `curl([-y, x, 0], [x, y, z])` → `⟨0, 0, 2⟩` |
| `curl([P, Q], [x, y])` | the scalar `∂Q/∂x − ∂P/∂y` | `curl([-y, x], [x, y])` → `2` |
| `laplacian(f, [x₁, …, xₙ])` | `∂²f/∂x₁² + … + ∂²f/∂xₙ²` | `laplacian(x^2 + y^2 + z^2, [x, y, z])` → `6` |

A function where a field is needed, a field where a function is needed, a field and variables of
different lengths, a curl of other than 2 or 3 components, and variables that are not distinct
names are errors (§16).

**Lagrange multipliers.** `lagrange(f, [g₁ = c₁, …], [x, y, …])` sets `∂f/∂x = λ₁ ∂g₁/∂x + …` for
each variable, keeps the constraints, and solves the system with `solve` in the variables and the
multipliers; the result is the set of critical points, each a list `[x = …, y = …, λ = …]`, those
with a coordinate that is not real dropped (decided numerically for a constant such as `√(2 − √5)`). The multiplier is `λ` for one constraint and `λ₁, λ₂,
…` for several, renamed as `k` is (§14.7) when the name is taken: after `λ := 2` the multiplier is
`λ₁`. Which point is a maximum or a minimum is left to the reader (compare `f` at the points).

- `lagrange(x^2 + y^2, [x + y = 1], [x, y])` → `{[x = 1/2, y = 1/2, λ = 1]}`.
- `lagrange(x y, [x^2 + y^2 = 1], [x, y])` → the four points `(±√2/2, ±√2/2)`, `λ = ±1/2`.
- `lagrange(x + y + z, [x^2 + y^2 + z^2 = 3], [x, y, z])` →
  `{[x = -1, y = -1, z = -1, λ = -1/2], [x = 1, y = 1, z = 1, λ = 1/2]}`.
- When `solve` leaves the system (it is not polynomial, or elimination does not reduce it), the call
  stays: `lagrange(y sin(x), [x + y = 1], [x, y])`. `steps(lagrange(…))` shows the system, then the
  points.

**Multiple integrals.** `integrate(f, [x, a, b], [y, c, d], …)` is the iterated integral, the first
bracket innermost: it parses to the nested integrals `∫[c, d] ∫[a, b] f dx dy`, which print with
the notation. A bracket is a name and two bounds; an inner level's bounds may hold the variables of
the levels outside it, never the other way round. Each level is a definite integral of §14.13,
evaluated inside out, so the inner level's value is the outer level's integrand; an outer level with
constant bounds lends its range to the levels inside it as facts (`integrate(sqrt(y^2), [x, 0, 1],
[y, 0, 2])` → `2`, as `y ≥ 0` there). The brackets are read in that order and nothing else: in
`integrate(x y, [x, 0, 1], [y, 0, x])` the second bracket's bound `x` is not the inner variable but
a free `x` from outside, and the value is `x²/4`.

- `integrate(x^2 + y, [x, 0, y], [y, 0, 1])` → `5/12`;
  `integrate(x y z, [x, 0, 1], [y, 0, 2], [z, 0, 3])` → `9/2`;
  `integrate(r, [r, 0, 2], [θ, 0, 2π])` → `4π` (a disc in polar coordinates, `r` the Jacobian).
- `steps(…)` shows one step per inner level, "Integrate the inner integral first: ∫[0, x] x y dy =
  x³/2", then the outermost level's own steps.
- A level with no closed form stays, and so does every level outside it:
  `∫[0,1] ∫[0,1] e^(-x^2 y) dx dy` is the integral itself, and so is the hemisphere
  `integrate(sqrt(1 - x^2 - y^2), [x, -sqrt(1 - y^2), sqrt(1 - y^2)], [y, -1, 1])`, whose inner
  bounds are its integrand's singular ends. The outer range does bound an inner root: with `-1 ≤ y
  ≤ 1`, `1 - y²` is non-negative (§14.6), so `integrate(1, [x, 0, sqrt(1 - y^2)], [y, -1, 1])` →
  `π/2`.

**Numeric values.** Under `numeric(…)` or a decimal or scientific format, an iterated integral that
stays is evaluated by nested quadrature when every bound is a number or holds only the variables
of the levels outside it, and the innermost integrand holds only the levels' variables: adaptive
Gauss–Kronrod in double precision, each inner integral a function of the outer variables, run twice
at two tolerances. The value is given to the leading digits the two runs agree on, at most 12 and
at most the digits asked for, and the step names how many: `numeric(∫[0,1] ∫[0,1] e^(-x^2 y) dx
dy, 10)` → `0.8615277068`, and the hemisphere above under `numeric(…, 20)` → `2.09439510239`, its step saying that the value has the digits two runs agree on,
at most 12. The value is marked numeric as a single integral's is. Runs that agree on fewer than
8 digits, an infinite bound, a bound with a symbol of no outer level, a pole or a jump, an integrand
that cannot be compiled (§10.2's limits), and an integral that needs more than the sample budget
(`|x − y|^(1/3)|y − z|^(1/3)|x − z|^(1/3)` over the unit cube at 12 digits) leave the integral, with
the step "No approximate value can be found". A single integral keeps the double-exponential
rule of §10.2.

**Line and surface integrals.** The field is written in `x` and `y`, or `x`, `y` and `z`, which are
bound in it (after `x := 5`, `lineintegral(x, [t, 0], t, 0, 1)` → `1/2`); the path or the surface
gives them as functions of its parameters. A parameter's name in the field is a symbol from outside,
not the parameter: `lineintegral(t, [cos(t), sin(t)], t, 0, 2π)` → `2πt`. A parameter with constant
bounds has its range as facts while the integrand is simplified, so the polar disc
`surfaceintegral(1, [r cos(θ), r sin(θ), 0], [r, 0, 1], [θ, 0, 2π])` → `π` (`|n| = |r| = r`). Each reduces to an ordinary or an iterated integral in
the parameters, shown as the first step, and that integral is evaluated as §14.13 and the numeric
rule above say.

- `lineintegral(f, [x(t), y(t)], t, a, b)` (or three components) of a scalar field is `∫ f ds`, the
  integral of `f(r(t)) |r′(t)|` over the interval from the smaller bound to the larger, since a
  length does not depend on the direction: `lineintegral(1, [cos(t), sin(t)], t, 0, 2π)` and
  `lineintegral(1, [cos(t), sin(t)], t, 2π, 0)` → `2π`, `lineintegral(1, [cos(t), sin(t), t], t,
  0, 2π)` → `2π √2`.
- Of a field `[P, Q]` (or `[P, Q, R]`, as many components as the path) it is the work `∫ F·dr`, the
  integral of `F(r(t))·r′(t)` from `a` to `b` as given, so reversing the bounds negates it:
  `lineintegral([-y, x], [cos(t), sin(t)], t, 0, 2π)` → `2π`, and `-2π` from `2π` to `0`; a
  gradient field's is the potential's difference, `lineintegral([2x y, x^2], [t, t^2], t, 0, 1)` →
  `1`.
- `surfaceintegral(f, [x(u, v), y(u, v), z(u, v)], [u, a, b], [v, c, d])`, the first bracket the
  inner integral (its bounds may hold `v`), uses the normal `n = r_u × r_v`: of a scalar field it is
  `∫∫ f dS`, the iterated integral of `f(r) |n|`, and of a field `[P, Q, R]` the flux `∫∫ F·n dS`,
  of `F(r)·n`, oriented by `n` and the brackets as given; a scalar field's integral takes each
  bracket with constant bounds from its smaller bound to its larger. The flux of `[x, y, z]` out of the unit sphere is
  `surfaceintegral([x, y, z], [sin(u) cos(v), sin(u) sin(v), cos(u)], [u, 0, π], [v, 0, 2π])` →
  `4π`; `surfaceintegral(z, [u, v, 1 - u - v], [u, 0, 1 - v], [v, 0, 1])` → `√3/6`.
- The length `|r′|` and `|n|` are the roots of sums of squares, factored first so that they simplify
  where they can; a root left (`√(4u² + 4v² + 1)`) or an absolute value whose sign the facts do not
  settle (`|sin u|` on the sphere: the range `0 ≤ u ≤ π` does not tell the sign of `sin u`) leaves
  the integral, for the numeric rule: `numeric(surfaceintegral(1, [sin(u) cos(v), sin(u)
  sin(v), cos(u)], [u, 0, π], [v, 0, 2π]), 10)` → `12.56637061`, the sphere's area `4π`.
- A path of other than 2 or 3 components, a field of other than as many, a field that uses `z` on a
  plane path, a surface of other than 3 and brackets that are not a name and two bounds are errors
  (§16).

### 14.21 Laplace transforms

`laplace(f, t, s)` is the Laplace transform of `f` in `t`, a function of `s`, and `ilaplace(F, s,
t)` the inverse; `t` (`s`) is bound in the first argument, and the result's variable is free in the
result, as a derivative's is (after `s := 2`, `laplace(e^t, t, s)` → `1`). Both work by table, as a
textbook does; no region of convergence is stated (the transforms hold for `s` beyond the growth of
`f`), and angles are radians, in every angle mode.

| `f(t)` | `laplace(f, t, s)` |
|---|---|
| `c`, `tⁿ` | `c/s`, `n!/sⁿ⁺¹` |
| `e^(at + b)` | `e^b/(s − a)` |
| `sin(bt)`, `cos(bt)` | `b/(s² + b²)`, `s/(s² + b²)` (with a phase `bt + c`, by the sum formulas) |
| `sinh(bt)`, `cosh(bt)` | `b/(s² − b²)`, `s/(s² − b²)` |

- **Linearity**: a sum term by term, a factor free of `t` taken out: `laplace(cos(t) + 2t, t, s)` →
  `s/(s² + 1) + 2/s²`.
- **The first shifting theorem**: `e^(at)·g` moves `g`'s transform to `s − a`:
  `laplace(e^(2t) sin(t), t, s)` → `1/((s - 2)² + 1)`, `laplace(t^2 e^(3t), t, s)` → `2/(s - 3)³`.
- **Multiplication by `tⁿ`** is `(−1)ⁿ` times the n-th derivative of the transform, for `n` up to
  10: `laplace(t sin(t), t, s)` → `2s/(s² + 1)²`; `laplace(t^12 sin(t), t, s)` stays.
- **A derivative of an unknown function** `y(t)` transforms to `sⁿ laplace(y(t), t, s) − sⁿ⁻¹ y(0) −
  … − y⁽ⁿ⁻¹⁾(0)`, the initial values written as in an initial condition, `y(0)`, `y'(0)`, …; the
  transform of `y(t)` stays in the result. Inside `laplace`'s first argument a name applied to
  exactly its variable, with or without primes, that is no builtin and no user function is an
  unknown function, as in `dsolve` (§14.22): `laplace(y''(t) + y(t), t, s)` → `s² laplace(y(t), t,
  s) - s y(0) + laplace(y(t), t, s) - y′(0)`; a name the session defines as a value is still that
  value times `t` (after `a := 3`, `laplace(a(t), t, s)` → `3/s²`, while `laplace(a'(t), t, s)` is
  the error "a is not a function"). So `laplace(f(t), t, s)` with an undefined `f` is the transform
  of an unknown function, which stays: `laplace(f(t), t, s)` → `laplace(f(t), t, s)`, not `f/s²`.
- **The inverse** splits a rational function of `s` into partial fractions (`apart`, §14.9), then
  inverts each term by the table read backwards: `c/(s − a)ⁿ` → `c tⁿ⁻¹ e^(at)/(n − 1)!`, and `(As +
  B)/(s² + ps + q)` with `q − p²/4` a positive constant `β²` → `e^(−pt/2)(A cos(βt) + ((B −
  Ap/2)/β) sin(βt))` (`cosh` and `sinh` when it is negative), and its square `(As + B)/(s² + ps +
  q)²`, with `a = −p/2`, → `e^(at)(A t sin(βt)/(2β) + (B + Aa)(sin(βt) − βt cos(βt))/(2β³))`
  (`t sinh(γt)/(2γ)` and `(γt cosh(γt) − sinh(γt))/(2γ³)` when `q − p²/4 = −γ²`), so `y'' + y = sin
  t` with `y(0) = 0` and `y'(0) = 1`, whose transform is `(s² + 2)/(s² + 1)²`, inverts to `sin(t) +
  (-t cos(t) + sin(t))/2`:
  `ilaplace(1/((s - 1)(s + 2)), s, t)` → `e^t/3 - e^(-2t)/3`, `ilaplace((s + 1)/(s^2 + 2s + 5), s,
  t)` → `e^(-t) cos(2t)`.
- **All or nothing**: when no rule takes some part, the call stays whole, with a step that says so:
  `laplace(e^(t^2), t, s)`, `ilaplace(e^(-s)/s, s, t)`. `steps(…)` shows one step per rule.
- A transform that converges for no `s` is `undefined`: `laplace(e^(s t), t, s)`, whose table entry
  `1/(s − s)` divides by zero.

### 14.22 Differential equations

**Writing an equation.** `dsolve(equation, y(x))` solves an ordinary differential equation for the
unknown function `y` of `x`. The second argument declares the unknown: inside the call `y` is a
function (§9.3), so `y(x)` is its application and not the product `y·x`, and `x` is bound in the
equation as a derivative's variable is. Derivatives are written in the prime notation, `y'(x)`,
`y''(x)` (§5), or as `diff(y(x), x)`, `diff(y(x), x, 2)`, which evaluate to the same. A system is a
list of equations and a list of unknowns in one variable: `dsolve([x'(t) = y(t), y'(t) = -x(t)],
[x(t), y(t)])`. Initial conditions follow as relations `y(a) = b`, `y'(a) = c`, with `a` and `b`
free of the unknowns and of the variable: `dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1, y'(0) = 0)`.

**The solution.** One solution is a relation `y(x) = …`; several branches are a set of them; a
system's solution is a list `[x(t) = …, y(t) = …]`. The constants of integration are `C₁`, `C₂`, …
(§14.7), each standing for any real value, and the value carries the note `C₁ ∈ ℝ`. When `y` cannot
be solved for, the solution is implicit, `F(x, y(x)) = C₁`; an `atan` of `y` alone is inverted by
the tangent first (`dsolve(y'(x) = 1 + y(x)^2, y(x))` → `y(x) = tan(C₁ + x)`), as a local solution
the substitution checks. Initial conditions solve for the
constants, and every solution returned meets every condition; conditions that no solution meets
give `{}` (a linear system for the constants with no solution, two conditions that ask an implicit
solution for two values of `F`). One condition `y(a) = b` on a first-order equation always has a
local solution, so it never gives `{}`: a condition at an equilibrium that separating the variables
divided away, where `h(b) = 0`, gives the constant solution `y(x) = b`
(`dsolve(y'(x) = y(x)^2, y(x), y(0) = 0)` → `y(x) = 0`), and one whose constant cannot be found
leaves the call. When no method applies, or a solution found does not satisfy the equation when
substituted back, the call stays.

**The methods**, tried in this order, the first that applies giving the solution:

| Class | Form | Method |
|---|---|---|
| Linear, first order | `y' + P(x) y = Q(x)` | the integrating factor `μ = e^∫P dx`: `y = (∫μQ dx + C₁)/μ` |
| Separable | `y' = g(x) h(y)` | `∫ dy/h(y) = ∫ g(x) dx + C₁`, then `y` where `solve` finds it; a sum of logarithms is exponentiated, its sign and `e^C₁` absorbed into the constant |
| Exact | `M + N y' = 0` with `∂M/∂y = ∂N/∂x` | a potential `Φ` with `Φₓ = M` and `Φ_y = N`: `Φ(x, y) = C₁` |
| Bernoulli | `y' = a(x) y + b(x) yⁿ`, `n ≠ 0, 1` | `v = y^(1−n)` is linear; for an odd `n` both branches `y = ±v^(1/(1−n))`, a condition choosing by its sign; for an `n` that is not an integer `y^(1−n)` is a root of `y`, so `y = v^(1/(1−n))` holds only where `v ≥ 0`: the explicit general solution when the check confirms it, else the implicit `μ y^(1−n) − ∫ μQ dx = C₁` (`dsolve(y'(x) = y(x) + sqrt(y(x)), y(x))` → `e^(-x/2) + e^(-x/2) √(y(x)) = C₁`); a condition `y(a) = b` with `b > 0` gives the branch through it, checked near `a`, a local solution (`dsolve(y'(x) = y(x) + sqrt(y(x)), y(x), y(0) = 4)` → `y(x) = (-e^(-x/2) + 3)² e^x`), and an implicit solution only with a constant known to be real |
| Constant coefficients, any order | `Σ aⱼ y⁽ʲ⁾ = g(x)` | the roots of the characteristic polynomial: `xᵏ e^(rx)` for a root `r` of multiplicity above `k`, `xᵏ e^(αx) cos(βx)` and `xᵏ e^(αx) sin(βx)` for a pair `α ± βi`; for a forcing term made of polynomials, exponentials, sines and cosines, a particular solution by undetermined coefficients, multiplied by `xˢ` when the term's `α + βi` is a root of multiplicity `s` |
| Linear systems | `X' = A X + b(t)`, `A` constant | the eigenvalues and generalised eigenvectors of `A`, the real and imaginary parts for a complex pair; `b` by variation of parameters |

Equations of higher order with coefficients that are not constant (`x² y'' + y = 0`), and
equations of no class here (`y' = sin(x y)`), stay. Every solution is substituted into the
equations and returned only if the residual simplifies to 0, or else is zero to the working
precision at fixed points. The constants are real; a separable equation's solutions that dividing
by `h(y)` loses (`y = 0` for `y' = y²`) are not listed in the general solution, but a condition at
one gives it. `dsolve` works in radians in every angle mode, as `diff` and `integrate` do.

| Input | Result |
|---|---|
| `dsolve(y'(x) = y(x), y(x))` | `y(x) = C₁ e^x, C₁ ∈ ℝ` |
| `dsolve(y'(x) + y(x)/x = x^2, y(x))` | `y(x) = (x⁴ + 4C₁)/(4x), C₁ ∈ ℝ` |
| `dsolve(y'(x) = x/y(x), y(x), y(0) = 2)` | `y(x) = √(x² + 4)` |
| `dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x))` | `x² y(x) + y(x)³ = C₁, C₁ ∈ ℝ` |
| `dsolve(y''(x) + 4y(x) = sin(2x), y(x))` | `y(x) = C₁ cos(2x) + C₂ sin(2x) - x cos(2x)/4, C₁ ∈ ℝ, C₂ ∈ ℝ` |
| `dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1, y'(0) = 0)` | `y(x) = cos(x)` |
| `dsolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0)` | `[x(t) = cos(t), y(t) = -sin(t)]` |

**Numeric solutions.** `odesolve(equation, y(x), y(x₀) = y₀, …, a, b)` integrates an initial value
problem numerically on `[a, b]`, in radians in every angle mode, by the Dormand–Prince 5(4) method with adaptive steps (relative
tolerance 10⁻¹⁰, absolute 10⁻¹²), for any equation that solves for its highest derivative: `y'' =
-sin(y)` as well as the classes above. A system is written as for `dsolve`. The conditions give
each unknown and each of its derivatives below the highest at one point `x₀` in `[a, b]`, and the
integration runs both ways from it. The value is a matrix of rows `[x, y(x)]` (`[t, x(t), y(t),
…]` for a system) at `n + 1` evenly spaced points, `n` = 100 unless `steps = n` is given, or with
`at = x₁` the value at one point (a vector of the unknowns' values for a system). Every number is a
decimal of at most 16 digits, a `double`'s, and the value is marked numeric (§14.13, the badge).
The equations may hold no symbol but the variable and the unknowns. A solution that leaves the
`double` range on the interval is an error.

| Input | Result |
|---|---|
| `odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 4)` | `[[0.0, 1.0], [0.25, 1.284025416719398], [0.5, 1.648721270726389], [0.75, 2.117000016659303], [1.0, 2.718281828512877]]` |
| `odesolve(y''(x) = -y(x), y(x), y(0) = 1, y'(0) = 0, 0, 10, at = pi)` | `-0.9999999999620286` |
| `odesolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0, 0, 2, at = 2)` | `⟨-0.4161468365393486, -0.9092974268055933⟩` |

`steps(dsolve(…))` shows the class and its working: the standard form, the integrating factor and
the integration for a linear equation; the separation and the two integrals for a separable one;
the exactness test and the potential; the characteristic equation, the homogeneous basis and the
particular solution; the system's matrix and its eigenvalues; then the constants the initial
conditions fix. The operation is in `docs/reference/engine.md`, **Ordinary differential
equations**.

### 14.23 Number theory

The functions of this section take integers. A number that is not an integer is an error
("isprime takes an integer"); a symbol leaves the call (`isprime(n)` stays).

**Primes.** `isprime(n)` is `true` or `false`; numbers below 2, negative ones included, are not
prime. Below 3.3·10²⁴ the answer is a proof; above it, it is the Baillie–PSW test's, which no
known composite passes. `nextprime(n)` is the least prime above `n` (2 for every `n < 2`),
`prevprime(n)` the greatest below it (`undefined` for `n ≤ 2`). `primes(a, b)` lists the primes in
`[a, b]`, ascending, by the bracket rule (§13.2), so it is a vector, or `[]` when there are none;
an interval of more than 10⁶ integers is an error.

**Factorisation.** `ifactor(n)` is `n`'s prime factorisation, shown as a product of prime powers in
ascending order with the sign in front: `ifactor(360)` → `2³ · 3² · 5`, `ifactor(-12)` → `-2² · 3`.
A prime, 0 and ±1 are themselves. The product is a form for display only: used in anything
(arithmetic, a relation, a function's argument, `numeric`), it is the integer, so `ifactor(360) + 1`
→ `361`; it is kept as the value of a statement and as an item of a list, vector or matrix, while a
set's items are their integers, so that `{ifactor(12), 12}` → `{12}`. It reads back as the integer.
The factors are found by trial division, a perfect-power test, Pollard's rho method and the
elliptic-curve method, each within a cap of work; a number whose composite part is not split within
the caps (a product of two primes of 30 digits, say) leaves the call, and `steps` says what was
found and what is left. `steps(ifactor(n))` names the methods that found factors.

**Divisor functions.** `divisors(n)` lists the positive divisors of `|n|`, ascending (at most
100,000 of them, else an error); `numdivisors(n)` counts them, `sigma(n)` adds them,
`totient(n)` counts the integers from 1 to `|n|` prime to `n`, and `mobius(n)` is 0 when a square
divides `n` and otherwise `(-1)^k` for `k` prime factors. They take `|n|` for a negative `n`, and
are `undefined` at 0; when `n` cannot be factored, the call stays.

| Input | Result |
|---|---|
| `isprime(97)`, `isprime(1)`, `isprime(561)` | `true`, `false`, `false` |
| `nextprime(100)`, `prevprime(100)`, `prevprime(2)` | `101`, `97`, `undefined` |
| `primes(10, 30)` | `⟨11, 13, 17, 19, 23, 29⟩` |
| `ifactor(360)`, `ifactor(-12)`, `ifactor(2^64 + 1)` | `2³ · 3² · 5`, `-2² · 3`, `274177 · 67280421310721` |
| `divisors(12)` | `⟨1, 2, 3, 4, 6, 12⟩` |
| `numdivisors(360)`, `sigma(12)`, `totient(36)`, `mobius(30)` | `24`, `28`, `12`, `-1` |

**Modular arithmetic.** `a mod b` is the remainder (§9.5). `powmod(a, k, m)` is `a^k` modulo `m`, in
`[0, m)`, by square and multiply: `powmod(3, 100, 7)` → `4`. A negative `k` raises the inverse of
`a` (`powmod(2, -1, 7)` → `4`), and is `undefined` when there is none; `powmod(0, 0, m)` is
`undefined` for `m > 1`, as `0^0` is, while modulo 1 every value is 0, so `powmod(0, 0, 1)` → `0`;
the test is on `a` itself, not on its remainder, so `powmod(5, 0, 5)` → `1`. `invmod(a, m)` is the
inverse of `a` modulo `m`, in `[0, m)`, by the extended Euclidean algorithm: `invmod(17, 43)` →
`38`; it is `undefined` when `gcd(a, m) ≠ 1`. The modulus is a positive integer (else an error);
modulo 1 every value is 0.

**The Chinese remainder theorem.** `crt([r₁, …, rₙ], [m₁, …, mₙ])` is the least non-negative
`x` with `x ≡ rᵢ (mod mᵢ)` for every `i`, which is unique modulo the lcm of the moduli. The
congruences are merged two at a time, so the moduli need not be coprime (`crt([1, 3], [4, 6])` →
`9`); when two congruences disagree there is no solution and the value is `undefined`
(`crt([1, 2], [4, 6])`). The lists have one length, at least one congruence, and positive moduli.

**Linear Diophantine equations.** `diophantine(a x + b y = c, [x, y])`, a command that binds its
unknowns as `solve` does, gives the integer solutions of a linear equation with rational
coefficients (they are cleared to integers). There are solutions exactly when `g = gcd(a, b)`
divides `c`; they are `x = x₀ + (|b|/g)k`, `y = y₀ − sign(b)(a/g)k` for every integer `k`, with
`x₀` in `[0, |b|/g)`, written with the parameter `k` as `solve`'s general solutions are (§14.7,
§14.18): `diophantine(3x + 5y = 7, [x, y])` → `{[x = 5k + 4, y = -3k - 1]}, k ∈ ℤ`. With no
solution the value is `{}`; an equation that holds for every pair (`0 = 0`) or has a symbol for a
coefficient leaves the call, and an equation that is not linear in the unknowns is an error.

**Combinatorics.** `choose(n, k)` is `binomial(n, k)` (§12.1). `permutations(n, k)` is the
falling factorial `n(n − 1)…(n − k + 1)`, the arrangements of `k` things of `n`;
`multinomial(n, [k₁, …, kₘ])` is `n!/(k₁!…kₘ!)`; `fibonacci(n)` and `lucas(n)` are the Fibonacci
and Lucas numbers (`F(0) = 0`, `F(1) = 1`, `L(0) = 2`, `L(1) = 1`, extended to negative `n` by
`F(−n) = (−1)^(n+1) F(n)` and `L(−n) = (−1)^n L(n)`); `catalan(n)`, `bell(n)`, `stirling2(n, k)`
(the ways to split `n` things into `k` non-empty blocks) and `partitions(n)` (the ways to write `n`
as a sum of positive integers) are the counts of those names. A count of no structure is 0: a
negative argument of the counts, `k < 0` or `k > n ≥ 0`, parts that do not add up to `n`. A value
too large to keep exactly, and `bell` and `stirling2` of `n` above 2000 or `partitions` above
10,000, leave the call.

`steps` shows the working: `powmod`'s exponent in binary and each square and multiply, from its
lowest bit; `invmod`'s extended Euclidean algorithm as a table of remainders, quotients and
coefficients, and the Bézout identity it gives; `crt`'s merges; `diophantine`'s particular
solution and then the general one.

| Input | Result |
|---|---|
| `17 mod 5`, `-7 mod 3` | `2`, `2` |
| `powmod(3, 100, 7)`, `powmod(2, -1, 7)` | `4`, `4` |
| `invmod(17, 43)`, `invmod(6, 9)` | `38`, `undefined` |
| `crt([2, 3, 2], [3, 5, 7])`, `crt([1, 2], [4, 6])` | `23`, `undefined` |
| `diophantine(3x + 5y = 7, [x, y])` | `{[x = 5k + 4, y = -3k - 1]}, k ∈ ℤ` |
| `diophantine(4x + 6y = 7, [x, y])` | `{}` |
| `choose(5, 2)`, `permutations(5, 2)`, `multinomial(4, [2, 1, 1])` | `10`, `20`, `12` |
| `fibonacci(10)`, `fibonacci(-8)`, `lucas(10)` | `55`, `-21`, `123` |
| `catalan(5)`, `bell(5)`, `stirling2(5, 2)`, `partitions(100)` | `42`, `52`, `15`, `190569292` |

### 14.24 Sets, logic and recurrences

**Logic.** A boolean expression (§8.4) is simplified by the identities that hold for every
assignment of truth values: a negation moves inward (`not not p` → `p`, `not (p and q)` → `¬p ∨
¬q`, `not (p implies q)` → `¬q ∧ p`, `not (x = 1)` → `x ≠ 1`, and between real sides `not (x < 1)`
→ `x ≥ 1`); `true` and `false` are absorbed or decide the operator (`p and true` → `p`, `true xor
p` → `¬p`, `false implies p` → `true`); a repeated operand counts once (twice cancels in `xor`), an
operand beside its negation decides `and` and `or` (`p or not p` → `true`), and absorption removes
the operand a neighbour decides (`p and (p or q)` → `p`). An implication otherwise stays as it is.
The operands are kept in canonical order, `q and p` → `p ∧ q`; `steps` names each identity. An
operand that cannot be a truth value (a number, arithmetic, a collection, a set operation) is an
error.

**Truth tables.** `truthtable(e, [p, q, …])` is a matrix: its first row is the names and `e`, and
each further row an assignment of truth values to the names, all `true` first and the last name
changing fastest, with `e`'s value under it. The names are bound in `e`, as a binder's are; at most
10 of them, distinct. `e` must reduce to `true` or `false` under every assignment: a relation
between numbers decides itself, but one on another name does not (`truthtable(p and x > 0, [p])`
is an error naming `x > 0`). A matrix of truth values does not read back as a matrix (§15.4).

| Input | Result |
|---|---|
| `truthtable(p implies q, [p, q])` | `[[p, q, p ⟹ q], [true, true, true], [true, false, false], [false, true, true], [false, false, true]]` |
| `truthtable(not p, [p])` | `[[p, ¬p], [true, false], [false, true]]` |

**Recurrences.** `rsolve(recurrence, a(n), conditions…)` solves a linear recurrence for the unknown
sequence `a` of the index `n`. The second argument declares `a` for the whole call, as `dsolve`'s
does (§14.22): `a(n + 1)` is the sequence at `n + 1`, and `n` is bound. The recurrence relates
`a(n + s)` for integer shifts `s`, at least two of them, and must be linear in them; its order is
the spread of the shifts, at most 8. Conditions `a(k) = v` at integer indices `k` fix the
constants.

- **Constant coefficients**, any order: the roots of the characteristic polynomial (`Σ cⱼ rʲ` for
  `Σ cⱼ a(n + j)`), found by `solve`, give `nᵏ rⁿ` for a root `r` of multiplicity above `k`, and
  `nᵏ ρⁿ cos(θn)`, `nᵏ ρⁿ sin(θn)` for a pair `ρe^(±iθ)`, the angle in radians in every angle mode.
  A forcing term made of polynomials in `n` times exponentials `bⁿ` gets a particular solution by
  undetermined coefficients, multiplied by `nˢ` when `b` is a root of multiplicity `s`.
- **First order with variable coefficients**, `a(n + 1) = p(n) a(n) + q(n)`: `a(n) = P(n)(C₁ +
  Σ q(j)/P(j + 1))` with `P(n) = Π p(k)`, the product and the sum from the least condition's index
  (or 0) to `n − 1`, when both close (§10.4).

Without conditions the solution carries the constants `C₁`, `C₂`, … with the note `C₁ ∈ ℝ`, as
`dsolve`'s does (§14.7); conditions that no solution meets give `{}`. Every solution is substituted
into the recurrence at its first six indices and returned only if the residual is 0, exactly or to
the working precision; otherwise, and when no class applies (`a(n + 1) = a(n)²`, `a(2n)`, numeric
roots), the call stays. The recurrence must hold at least two shifts (`rsolve(a(n) = 3, a(n))` is an
error), and the conditions' indices must be integers. `steps` shows the characteristic equation, the
homogeneous solution, the particular solution or the product, and the constants the conditions fix.

| Input | Result |
|---|---|
| `rsolve(a(n + 1) = 2 a(n) + 1, a(n), a(0) = 1)` | `a(n) = 2·2^n - 1` |
| `rsolve(a(n + 1) = 2 a(n) + 1, a(n))` | `a(n) = C₁·2^n - 1, C₁ ∈ ℝ` |
| `rsolve(a(n + 2) = -a(n), a(n))` | `a(n) = C₁ cos(πn/2) + C₂ sin(πn/2), C₁ ∈ ℝ, C₂ ∈ ℝ` |
| `rsolve(a(n + 2) - 4 a(n + 1) + 4 a(n) = 0, a(n))` | `a(n) = C₂ n·2^n + C₁·2^n, C₁ ∈ ℝ, C₂ ∈ ℝ` |
| `rsolve(a(n + 1) = (n + 1) a(n), a(n), a(0) = 1)` | `a(n) = n!` |
| `rsolve(a(n + 1) = a(n)^2, a(n))` | stays |

**Sets.** `A ∪ B` (`union(A, B)`), `A ∩ B` (`intersect(A, B)`), `A \ B` (`setdiff(A, B)`) and
`symdiff(A, B)` (the items in exactly one) of two sets are sets, in canonical order. Two items are
the same when they are equal as written or are numbers of equal value, and distinct when both are
numbers, truth values, or lists, vectors or matrices of them, that differ (§14.4); an item that
could be either, such as `x` beside `1`, leaves an intersection, a difference or a symmetric
difference as it is, while a union keeps both: `{x} ∪ {1}` → `{1, x}`, `{x} ∩ {1}` stays.
`subsets(A)` is the set of all subsets of `A` (at most 16 items), `cartesian(A, B)` the set of the
pairs `[a, b]`, by the bracket rule (§13.2), and `size(A)` the number of items, of a set or a list.
An argument that is a value but not a set is an error ("union takes sets"); a name leaves the call.

| Input | Result |
|---|---|
| `{1, 2} ∪ {2, 3}`, `{1, 2, 3} ∩ {2, 3, 4}`, `{1, 2, 3} \ {2}` | `{1, 2, 3}`, `{2, 3}`, `{1, 3}` |
| `symdiff({1, 2}, {2, 3})` | `{1, 3}` |
| `subsets({1, 2})`, `cartesian({1, 2}, {a})` | `{{}, {1}, {1, 2}, {2}}`, `{⟨1, a⟩, ⟨2, a⟩}` |
| `2 ∈ {1, 2}`, `5 ∈ {1, 2}`, `x ∈ {1, 2}` | `true`, `false`, `x ∈ {1, 2}` |
| `size({1, 2, 3})`, `{1} ∩ {}` | `3`, `{}` |

### 14.25 Statistics

**Data.** A sample is a vector or a list, `[1, 2, 4]`; its entries are numbers or exact real
constants (`[π, 1, √2]`). A frequency table is a second list of counts, non-negative integers, one
per value: `mean([1, 2], [3, 1])` is the mean of `1, 1, 1, 2`, `5/4`. Paired data (`cov`, `cor`) is
two lists of one length or a matrix of two columns, `[[x₁, y₁], …]`. An argument or entry that holds
a symbol may still stand for data, so the call stays: `mean([1, a])` → `mean(⟨1, a⟩)`; a complex
entry is an error.

**Exactness.** On exact data every value is exact: `mean([1, 2, 4])` → `7/3`, `stdev([1, 2, 4])` →
`√21/3`, `mean([π, 1])` → `(π + 1)/2`. A decimal entry makes the value a decimal at the session
precision, computed from the rationals the decimals denote and rounded once: `mean([0.1, 0.2])` →
`0.15`. The counts of `summary` and `frequencies` stay integers, and `sort` and `mode` give the
entries as they were written. Exact constants are ordered by their values at the working precision,
and two of equal value are taken as equal: `median([π, 3, 4])` → `π`.

**The functions.** `variance` and `stdev` are the sample's (dividing by `n − 1`), `pvariance` and
`pstdev` the population's (dividing by `n`). `quartiles` are the medians of the lower and upper
halves, the median left out of both for an odd count (the TI convention): `quartiles([1, 2, 3, 4, 5,
6, 7])` → `⟨2, 4, 6⟩`; `iqr` is `Q3 − Q1`. `percentile(data, p)` interpolates linearly between the
order statistics at `h = (n − 1)p/100`, counted from 0 (R's type 7), so `percentile(d, 25)` may
differ from `Q1`. `mode` is the most frequent value, or the set of the values tied for it (all of
them when they are all distinct). `skewness` and `kurtosis` are adjusted for the sample, the
kurtosis in excess of 3 (Excel's `SKEW` and `KURT`). `zscore(x, data)` is `(x − mean)/stdev`, and
`zscore(x, μ, σ)` is `(x − μ)/σ` for any `x`. `cov` is the sample covariance and `cor` Pearson's
`r`. `frequencies` is the matrix of the distinct values, ascending, and their counts. `summary` is
the list `[n = …, mean = …, median = …, min = …, Q1 = …, Q3 = …, max = …, stdev = …]`; its labels
`mean`, `median`, `min`, `max` and `stdev` are builtin names, so the printed list does not parse back
(a round-trip exception, §15.4); `nth(summary(d), 2)` is the mean's relation.

| Function | Needs at least |
|---|---|
| `mean`, `median`, `mode`, `pvariance`, `pstdev`, `range`, `percentile`, `sort`, `cumsum`, `frequencies` | 1 value |
| `variance`, `stdev`, `quartiles`, `iqr`, `summary`, `zscore(x, data)`, `cov`, `cor` | 2 values |
| `skewness` | 3 values |
| `kurtosis` | 4 values |

**Distributions.** The names are the TI calculators': for each law a density (`…pdf`, the
probability of a value for a discrete law), a cumulative function (`…cdf`) and, for the continuous
laws but the exponential and uniform, a quantile (`inv…`, the lower tail).

| Law | Density | Cumulative | Quantile |
|---|---|---|---|
| Normal | `normalpdf(x[, μ[, σ]])` | `normalcdf(a, b[, μ[, σ]])` | `invnorm(p[, μ[, σ]])` |
| Student's t | `tpdf(x, df)` | `tcdf(a, b, df)` | `invt(p, df)` |
| χ² | `chi2pdf(x, df)` | `chi2cdf(a, b, df)` | `invchi2(p, df)` |
| F | `fpdf(x, d1, d2)` | `fcdf(a, b, d1, d2)` | `invf(p, d1, d2)` |
| Exponential | `exppdf(x, λ)` | `expcdf(a, b, λ)` | |
| Uniform on `[lo, hi]` | `unifpdf(x, lo, hi)` | `unifcdf(a, b, lo, hi)` | |
| Binomial | `binompdf(n, p, k)` | `binomcdf(n, p, k)`, `P(X ≤ k)` | |
| Poisson | `poissonpdf(λ, k)` | `poissoncdf(λ, k)` | |
| Geometric (trials, `k ≥ 1`) | `geometpdf(p, k)` | `geometcdf(p, k)` | |
| Hypergeometric | `hypergeompdf(N, K, n, k)` | `hypergeomcdf(N, K, n, k)` | |

A continuous cumulative function takes an interval, `P(a < X ≤ b)`, with `-∞` and `∞` as bounds; `a
> b` gives the negative, `F(b) − F(a)`. `μ` and `σ` default to 0 and 1.

**What is exact.** Every density and the discrete, exponential and uniform cumulative functions have
closed forms, which exact arguments give: `binompdf(10, 1/2, 3)` → `15/128`, `binomcdf(10, 1/2, 3)`
→ `11/64`, `poissoncdf(2, 3)` → `19/(3 e²)`, `tpdf(0, 1)` → `1/π`, `expcdf(0, 1, 2)` → `-1/e² + 1`;
the t, χ² and F densities through `gamma` and `beta`, which are exact at integer and half-integer
degrees of freedom. The normal and t densities have their closed form at a symbolic point, so
`normalpdf(x)` → `√2 e^(-x²/2)/(2√π)` plots; the other densities, whose formulas hold only on their
support, and every other function of a symbol, stay (`chi2pdf(x, 3)`); a plot compiles them (and the
cumulative functions, `normalcdf(-∞, x)`) to `double` all the same. With a decimal argument, a
closed form whose `gamma` and `beta` values are exact is evaluated at the precision
(`normalpdf(1.0)` → `0.2419707245191433497978301929355607`); one that keeps a `gamma` or `beta` with
no exact value (a degree of freedom that is not a half-integer: `chi2pdf(2.0, 3.5)`), and such a
call under `numeric(…)`, is computed by the provider, as below: `0.2001384184560116`, marked
numeric.
A discrete law with more than 2000 trials (or draws, or terms of a Poisson sum) stays, and
`numeric(…)` (or a decimal format) evaluates it through the provider, marked numeric with its step:
`numeric(binompdf(3000, 1/2, 1500), 5)` → `0.014566`. A rational `λ`'s Poisson sum is one rational,
`poissoncdf(7/3, 2)` → `109/(18 e^(7/3))`.

**What is numeric.** The cumulative functions and quantiles of the normal, t, χ² and F
distributions, and a discrete law with a decimal parameter, are computed in double precision (by
the `Distribute` provider, `docs/reference/engine.md` **Statistics**) under every number format, as
the decompositions are (§14.17): at most 16 digits, the value marked numeric, and `steps(…)` shows
"Approximate value, shown to at most 16 digits".
`normalcdf(-1.96, 1.96)` → `0.9500042097035593`, `invt(0.975, 10)` → `2.228138851986274`,
`invnorm(0)` → `-∞`, `normalcdf(-∞, ∞)` → `1.0`. A quantile is found on the tail that holds its
digits (`1 − p` formed exactly above the median: `invf(1 - 10^(-12), 3, 7)` → `7966.551038258161`)
and checked; one the provider cannot find to its accuracy (a probability below the `double` normal
range, `invt(1e-320, 3)`) stays, and `steps(…)` says "No approximate value reaches the needed
accuracy, so the function is left as it is".

The parameters must be in their domains: `σ > 0`, `df > 0`, `0 ≤ p ≤ 1` (`0 < p ≤ 1` for the
geometric law), `λ > 0`, `lo < hi`, `n`, `N`, `K` integers with `0 ≤ K ≤ N` and `0 ≤ n ≤ N`, `k` an
integer, and a quantile's `p` in `[0, 1]`.

**Regression.** `linreg(xs, ys)` fits `y = a x + b` by least squares, `quadreg`, `cubicreg` and
`quartreg` the polynomials of degree 2 to 4, and `polyreg(xs, ys, n)` degree `n`; the data are two
lists or a matrix of two columns. The fit solves the normal equations `XᵀX β = Xᵀy` exactly through
the linear algebra (§14.16), so it is exact on exact data, and decimal data are fitted from the
rationals they denote and rounded once. The value is the list of relations `[y = …, r² = …]`,
`r² = 1 − SSres/SStot`, with `r = …` (the sign of the slope times `√r²`) for the line:
`linreg([1, 2, 3], [1, 2, 2])` → `[y = x/2 + 2/3, r² = 3/4, r = √3/2]`. The fitted equation comes
first, so `nth(fit, 1)` is `y = x/2 + 2/3`, which a plot draws. `expreg` fits `y = a·bˣ`, `lnreg` `y
= a + b log(x)` and `powreg` `y = a xᵇ` by least squares of their linear forms (`log(y) = log(a) + x
log(b)`, …), numerically at the precision and marked numeric, with the linear form's `r²` (the
TI and Excel convention); they need positive `y`, positive `x`, or both. `residuals(xs, ys, fit)` is
the vector of `yᵢ − ŷᵢ`, and `predict(fit, x)` the fitted value at `x`; `fit` is the list a
regression gives or a relation `y = …` in `x`. Constant `y` gives `r² = undefined`. `steps(linreg(…))`
shows the normal equations, the row reduction that solves them, and the fitted equation with
`r²`. The regressions match the NIST Statistical Reference Datasets' certified values (the corpus).

**Hypothesis tests.** Each test's value is a list of relations: the statistic, exact where the data
are (computed from the rationals decimal data denote, and then a decimal), the degrees of freedom,
exact (Welch's a rational), and the `p`-value, numeric in double precision as the distributions'
cumulative functions are, which marks the value numeric. No decision is made at a significance
level: the reader compares `p` with theirs.

| Test | Hypotheses | Value |
|---|---|---|
| `ztest(data, σ, μ₀)`, `ztest(x̄, σ, n, μ₀)` | `μ = μ₀` | `[z = (x̄ − μ₀)/(σ/√n), p = …]` |
| `ttest(data, μ₀)`, `ttest(x̄, s, n, μ₀)` | `μ = μ₀` | `[t = (x̄ − μ₀)/(s/√n), df = n − 1, p = …]` |
| `ttest2(data₁, data₂)` | `μ₁ − μ₂ = 0` | Welch's `t` and degrees of freedom; with `pooled`, the pooled variance and `n₁ + n₂ − 2` |
| `pairedttest(data₁, data₂)`, `pairedttest(M)` | `μ_d = 0` | the one-sample `t` of the differences |
| `proptest(x, n, p₀)` | `p = p₀` | `[z = (p̂ − p₀)/√(p₀(1 − p₀)/n), p = …]` |
| `proptest2(x₁, n₁, x₂, n₂)` | `p₁ − p₂ = 0` | `z` with the pooled proportion |
| `chi2gof(observed, expected)` | the counts follow the expected | `[χ² = Σ (O − E)²/E, df = k − 1, p = …]`, the upper tail; the totals agree within a relative `1e-3`, so rounded counts such as `[33.33, 33.33, 33.33]` against 100 pass |
| `chi2test(M)` | rows and columns independent | `χ²` against `row total × column total / grand total`, `df = (r − 1)(c − 1)` |
| `anova(g₁, g₂, …)` | the means equal | `[F = (SSB/(k − 1))/(SSW/(N − k)), df1 = k − 1, df2 = N − k, p = …]` |

The `z` and `t` tests take an **alternative** as their last argument, the word `two` (the default,
`p` of both tails), `less` or `greater`; `ttest2` takes the word `pooled` before it. The words are read
as written, even when the names are defined: after `two := 3`, `ttest(d, 5, two)` is the two-sided
test. `ttest([4, 5, 6, 7, 8], 5)` → `[t = √2, df = 4, p = 0.2301996410804988]`; `steps(…)` shows the
hypotheses `[μ = 5, μ ≠ 5]`, the data's `n`, `x̄` and `s`, the statistic's formula with the numbers
in, the degrees of freedom and the `p`-value.

**Confidence intervals.** An interval is `[lower, upper]`, the estimate plus or minus the critical
value times its standard error, at the level `C`, a number strictly between 0 and 1 (`0.95`, `95/100`;
`95` is an error): `zinterval` with the normal's `z* = invnorm((1 + C)/2)` and `σ/√n`, `tinterval`
with Student's `t*` at `n − 1` degrees of freedom and `s/√n`, `tinterval2` for the difference of
two means (Welch's, or `pooled` as its last word, read as written), `propinterval` the Wald interval
`p̂ ± z*√(p̂(1 − p̂)/n)` and `propinterval2` for `p̂₁ − p̂₂`. The ends are decimals of at most 16
digits, marked numeric: `tinterval([4, 5, 6, 7, 8], 0.95)` → `⟨4.036756838522443,
7.963243161477557⟩`. `steps(…)` shows the critical value, then the interval with the estimate and
the standard error.

**Special functions.** `erf(x)`, `erfc(x) = 1 − erf(x)`, `gamma(x) = Γ(x)` and `beta(a, b) =
Γ(a)Γ(b)/Γ(a + b)` are exact where they have exact values: `gamma(5)` → `24`, `gamma(1/2)` → `√π`,
`gamma(-1/2)` → `-2√π`, `gamma(0)` → `undefined` (a pole, as at every negative integer),
`beta(1/2, 1/2)` → `π`, `erf(0)` → `0`, `erf(∞)` → `1`, `erf(-x)` → `-erf(x)`, `erfc(-x)` →
`2 - erfc(x)`. Any other exact argument leaves the call (`gamma(1/3)`, `erf(1/2)`). A decimal argument
is computed in double precision, so the value has at most 16 digits whatever the precision:
`erf(1.0)` → `0.8427007929497151`, `numeric(gamma(1/3))` → `2.678938534707748`. `diff` knows `erf`
and `erfc` (`d/dx erf(x) = 2e^(−x²)/√π`), not `gamma` or `beta`. The engine's rules are in
`docs/reference/engine.md`, **Simplification**.

### 14.26 Units

**Quantities.** A quantity is a magnitude times units: `3 _m`, `5 _km/_h`, `9.8 _m/_s^2`,
`x _kg`. A unit (§3.6) is a value of its own, and every operation treats it as a positive real
constant: `_m·_m` is `_m²`, `3 _m + 2 _m` is `5 _m`, `_m/_m` is 1, `sqrt(4 _m^2)` is `2 _m`,
`diff(3 _m x^2, x)` is `6x _m`, and `expand((x + 1) _m)` is `x _m + _m`. Units of different names
are different constants until a conversion relates them (below).

**The table.** Every unit has a dimension, a product of powers of the seven SI base dimensions
(mass, length, time, electric current, temperature, amount of substance, luminous intensity), and
an exact factor to the coherent SI unit of its dimension. The factors come from the SI Brochure
(9th edition, 2019) and NIST SP 811 (Appendix B), with the international foot (0.3048 m), and are
exact rationals except the degree's, which is π/180, so conversions round-trip exactly.

| Group | Units (factor to the coherent SI unit) |
|---|---|
| SI base | `_kg` (the gram `_g` is 1/1000 kg), `_m`, `_s`, `_A`, `_K`, `_mol`, `_cd` |
| SI derived | `_N`, `_J`, `_W`, `_Pa`, `_Hz`, `_C`, `_V`, `_ohm` (`_Ω`), `_F`, `_T`, `_Wb`, `_H` |
| Accepted | `_L` (1/1000 m³), `_t` (1000 kg), `_eV` (1.602176634·10⁻¹⁹ J), `_bar` (100000 Pa), `_atm` (101325 Pa), `_cal` (4.184 J), `_Wh` (3600 J), `_min` (60 s), `_h` (3600 s), `_d` (86400 s), `_yr` (365.25 d), `_deg` (π/180), `_rad` (1), `_sr` (1) |
| Imperial and US | `_in` (127/5000 m), `_ft` (381/1250 m), `_yd` (1143/1250 m), `_mi` (201168/125 m), `_nmi` (1852 m), `_oz` (1/16 lb), `_lb` (45359237/10⁸ kg), `_st` (14 lb), `_ton` (the short ton, 2000 lb), `_floz` (1/128 gal), `_pt` (1/8 gal), `_qt` (1/4 gal), `_gal` (the US gallon, 231 in³), `_mph` (1397/3125 m/s), `_acre` (43560 ft²), `_psi` (the pound-force per square inch), `_hp` (the mechanical horsepower, 550 ft·lbf/s) |
| Temperature | `_degC`, `_degF` (on an offset scale, below) |

The SI prefixes `y` (10⁻²⁴), `z`, `a`, `f`, `p`, `n`, `u` or `µ` (10⁻⁶), `m`, `c`, `d`, `da`, `h`,
`k`, `M`, `G`, `T`, `P`, `E`, `Z`, `Y` (10²⁴) apply to the SI base and derived units and to `_L`,
`_t`, `_eV`, `_bar`, `_cal`, `_Wh` and `_rad`: `_km`, `_µs`, `_kWh`, `_hPa`, `_MeV`. A name of the
table wins over a prefixed reading, so `_ft` is the foot and `_pt` the pint. `_µm`, `_μm` and `_um`
are one unit, as are `_kΩ` and `_kohm`.

**Dimensions.** Arithmetic checks dimensions as it evaluates. The terms of a sum must have one
dimension ("cannot add m and s", "cannot add a number and m"), so must the sides of a relation and
the arguments of `min` and `max` ("cannot compare m and s"), an exact 0 having every dimension
(`solve(x _m + 2 _cm = 0, x)` is `{-1/50}`, `max(0, 3 _m)` is `3 _m`); the items of a list, a
vector or a matrix are checked each (`[3 _m] + [2 _s]` is "cannot add m and s"); a quantity is not
a member of a number domain (`3 _m in Reals` is that error); an exponent cannot have units, and a
quantity's exponent must be a rational number (`sqrt(2 _m)` is `√2 √_m`, `_m^x` is an error).
A builtin function needs numbers: `sin(3 _m)` is the error "sin needs a number, not a quantity in
m", except `abs`, `sign`, `sqrt`, `cbrt`, `root`, `min` and `max`, which take quantities
(`abs(-3 _m)` is `3 _m`, `max(3 _m, 200 _cm)` is `3 _m`). A dimensionless argument is the number it
stands for (`sin(_km/_m)` is `sin(1000)`), and an argument in radians or degrees is evaluated in
radians whatever the angle mode: `sin(30 _deg)` is `1/2`, and `sin(2 _rad)` in degrees is `sin(2)`.
Commands treat units as constants: `solve(x _m = 3 _cm, x)` is `{3/100}`, `integrate(3 _m, x)` is
`3x _m`; `numeric` and `exact` act on the magnitude.

**Combining units.** The simplifier relates units of one dimension (the rule set `units`, first in
the standard list):

- a sum of quantities of one dimension becomes one quantity in the units of its first term in
  canonical order, with exact factors: `3 _m + 2 _cm` and `2 _cm + 3 _m` are `151/50 _m`, `x _m +
  2 _cm` is `(x + 1/50) _m`, `5 _km/_h + 1 _m/_s` is `43/18 _m/_s`; under a decimal it is a decimal,
  `3.0 _m + 2 _cm` is `3.02 _m`; a dimensionless sum is a number, `1 + 30 _deg` is `π/6 + 1`;
- two units of one dimension in a product merge into the first: `_km _m` is `1000 _m²`, `_km/_m`
  is 1000; derived units are kept as written, so `3 _N · 2 _m` is `6 _N _m` (`convert` writes it in
  `_J`);
- units whose dimensions cancel are their factor: `3 _h _Hz` is 10800, `_J/(_N _m)` is 1; a single
  `_rad` or `_deg` stays, `30 _deg` is `30 _deg`.

`expand`, `apart` and `collect` keep their expanded forms: `expand((x + 1) _m)` is `x _m + _m`.

**Conversion.** `convert(q, unit)` writes the quantity `q` in the units `unit`, a product of
powers of units of `q`'s dimension, with exact factors: `convert(5 _km/_h, _m/_s)` is
`25/18 _m/_s`, `convert(6 _N _m, _J)` is `6 _J`, `convert(1 _kWh, _MJ)` is `18/5 _MJ`,
`convert(3 _m + 2 _cm, _cm)` is `302 _cm`, and a number converts to a dimensionless unit,
`convert(π, _deg)` is `180 _deg`. The target cannot be a quantity ("convert needs a unit such as
_m/_s"), hold two units of one dimension ("convert's target must not hold two units of one
dimension"), or have another dimension ("cannot convert m to s", "cannot convert km/h to s").
`steps` shows the definitions it used, as one step, with the quotient before it is reduced when it
is not in lowest terms: `1 _km = 1000 _m. 1 _h = 3600 _s. So 5 _km/_h = 5000/3600 _m/_s = 25/18
_m/_s`. `units(q)` is
the unit part of `q` (1 for a number) and `magnitude(q)` the rest: `units(5 _km/_h)` is `_km/_h`,
`magnitude(3 _m + 2 _cm)` is `151/50`.

**Temperature.** `_K` is absolute; `_degC` and `_degF` are on offset scales (0 °C is 273.15 K, 0 °F
is 459.67 °R). In arithmetic and in compound units they are differences, a degree Celsius one kelvin
and a degree Fahrenheit 5/9 of one: `5 _degC + 3 _degC` is `8 _degC`, `20 _degC + 10 _degF` is
`230/9 _degC`. A relation between two bare temperatures (a magnitude times one temperature unit)
compares them on the absolute scale: `30 _degC > 300 _K` is `true`, `20 _degC = 68 _degF` is `true`;
and `convert` of a bare temperature to a single unit of temperature is on the absolute scale:
`convert(20 _degC, _degF)` is `68 _degF`, `convert(10 _degC, _K)` is `5663/20 _K`, while
`convert(5 _degC/_s, _K/_s)` is a difference, `5 _K/_s`. A quantity with the exact magnitude 0 is
the number 0, as `0 x` is, so it keeps no unit and no scale, a known limitation: `0 _degC` is `0`,
and `convert(0 _degC, _degF)` is the error "a temperature of 0 is the number 0, which keeps no
unit: write 0.0 _degC or 273.15 _K"; `convert(0.0 _degC, _degF)` is `32.0 _degF`.

**Printing.** A quantity prints as its magnitude, a space and its units, numerator units first,
then `/` and the denominator units, parenthesised when there are several: `25/18 _m/_s`,
`(x + 3) _m`, `6 _N _m`, `_J/(_kg _K)`; a unit part with no unit above the line divides the
magnitude, `5/_s`, `1/_s²`, and a magnitude of -1 is a sign, `-_m`, `-_m/_s`. Units stand in the table's order: the SI derived units, the base units in the
order `kg m s A K mol cd`, the accepted units, the imperial and US units, the temperatures, the
coherent unit before its prefixed forms. The Unicode printer writes `_µm` and `_kΩ`, the ASCII
printer `_um` and `_kohm`; both read back.

### 14.27 Bases and bitwise operations

**Literals.** An integer may be written in base 16, 2 or 8 (§3.1): `0xff`, `0b1011`, `0o17`, the
prefix in either case and the digits in either case, with `_` between digits as in Java
(`0xFFFF_FFFF`). The literal is the integer: `0xff + 1` is `256`, and `0xff` alone prints as `255`.

**Showing an integer in a base.** `hex(n)`, `bin(n)`, `oct(n)` and `base(n, b)`, `b` from 2 to 36,
show the integer `n` in base 16, 2, 8 or `b`: `hex(255)` is `0xff`, `bin(11)` is `0b1011`,
`oct(15)` is `0o17`, `base(1295, 36)` is `zz₍₃₆₎` (written `base(1295, 36)` by the ASCII printer),
`hex(-255)` is `-0xff`, and `base(n, 10)` is `n`. The digits are lower case. The base is only how
the value is shown: it does not propagate, so `hex(255) + 1` is `256` and `sin(hex(1))` is
`sin(1)`; a list, a vector or a matrix keeps its items' bases (`[hex(1), hex(255)]` is `⟨0x1,
0xff⟩`), a set does not. A value shown in a base compares equal to its integer (`hex(255) = 255` is
`true`). `digits(n, b)` is the vector of the digits of `n ≥ 0` in base `b`, the most significant
first: `digits(11, 2)` is `⟨1, 0, 1, 1⟩`. A symbolic argument leaves the call as it is; a number
that is not an integer is the error "hex needs an integer", a base outside 2 to 36 "the base must
be an integer from 2 to 36".

**Bitwise operations** act on integers as two's-complement numbers of unbounded width, as Java's
`BigInteger` does: `bitand(a, b)`, `bitor(a, b)`, `bitxor(a, b)`, `bitnot(a)` (which is `-a - 1`),
`shiftleft(a, n)`, `shiftright(a, n)` (arithmetic: `shiftright(-16, 2)` is `-4`), `bit(a, i)` (the
bit of weight `2^i`, 0 or 1; `bit(-1, 1000)` is 1) and `bitcount(n)` (the ones of `n ≥ 0`):
`bitand(12, 10)` is 8, `hex(bitand(0xf0, 0x3c))` is `0x30`. A shift or an index runs from 0 to
2²⁰; an argument that is no integer is the error "bitand needs integers"; a symbolic argument
leaves the call. They are compiled for plotting on integers within 2⁵³, and are undefined
elsewhere.

### 14.28 Finance

The time value of money, in Excel's conventions: money paid out is negative, money received
positive, and `type` 0 (the default) pays at the end of each period, 1 at the beginning. A rate is
per period, as a number (`5 %` is `1/20`). With `g = (1 + r)ⁿ`, the equation that ties the present
value, the payment and the future value is `pv·g + pmt·(1 + r·type)(g − 1)/r + fv = 0`, and
`pv + pmt·n + fv = 0` at `r = 0`. Exact arguments give an exact value, a rational or a radical or
logarithm where the formula needs one; a decimal argument gives a decimal. A rate of −1 or less is
`undefined`; a symbolic argument leaves the call as it is.

| Call | Is | Example |
|---|---|---|
| `pv(r, n, pmt[, fv[, type]])` | the present value | `pv(1/10, 2, 0, 121)` → `-100` |
| `fv(r, n, pmt[, pv[, type]])` | the future value | `fv(1/10, 2, 0, -100)` → `121` |
| `pmt(r, n, pv[, fv[, type]])` | the payment per period | `pmt(1/100, 2, 100)` → `-10201/201`; `pmt(0.05/12, 360, 200000)` → `-1073.64…` |
| `nper(r, pmt, pv[, fv[, type]])` | the number of periods, a logarithm | `nper(1/10, 0, -100, 121)` → `2` |
| `rate(n, pmt, pv[, fv[, type[, guess]]])` | the rate, numeric (Newton's method from `guess`, 0.1 by default) | `rate(2, 0, -100, 121)` → `0.1` |
| `npv(r, [c₁, …, cₙ])` | the net present value, the first flow at period 1 | `npv(1/10, [-100, 60, 60])` → `5000/1331` |
| `irr([c₀, …, cₙ][, guess])` | the internal rate of return, the first flow at period 0, numeric | `irr([-100, 60, 60])` → `0.1306…` |
| `effective(nominal, m)` | the effective rate of a nominal rate compounded `m` times | `effective(1/10, 2)` → `41/400` |
| `nominal(effective, m)` | the nominal rate | `nominal(41/400, 2)` → `1/10` |
| `amortization(r, n, pv)` | the schedule: rows of the period, payment, interest, principal and balance | `amortization(1/10, 2, 100)` → `[[1, -1210/21, -10, -1000/21, 1100/21], [2, -1210/21, -110/21, -1100/21, 0]]` |

`rate` and `irr` are numeric, their value marked so, and `steps` says how they were found; one that
does not converge, after damped Newton steps from the guess, is the error "rate did not converge
from the guess 0.1; give another guess", and `irr` needs flows of both signs. A rate whose equation
holds at 0 exactly is 0 (`rate(4, -25, 100)` and `irr([-100, 50, 50])` are `0`), and so is one found
below the session's last digit.

**The solver.** `tvm(n = 10, i = 5 %, pv = -1000, pmt = 0)` takes four of `n`, `i`, `pv`, `pmt` and
`fv`, by name, and an optional `type`, and solves for the fifth through the functions above: it
returns the list `[n = 10, i = 1/20, pv = -1000, pmt = 0, fv = 16679880978201/10240000000]`. Inside
`tvm` the names `pv`, `pmt`, `fv` and `i` before `=` are its parts, not the functions or the
imaginary unit. Exactly four of the five must be given, each a real number, and a name at most once
("tvm needs four of n, i, pv, pmt, fv", "tvm's parts are n, i, pv, pmt, fv and type", "tvm's parts
must be distinct"); solving for `i` is numeric, as `rate` is. The list prints `i` as a name, which
reads back as the imaginary unit outside `tvm` (§15.4).

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
| boolean | `not p and (q or r) implies s` | `¬p ∧ (q ∨ r) ⟹ s` | `(\lnot p \land (q \lor r)) \implies s` |
| membership | `n in Integers` | `n ∈ ℤ` | `n \in \mathbb{Z}` |
| lambda | `x -> x^2` | `x → x²` | `x \mapsto x^{2}` |
| cross | `cross(u, v)` | `u × v` | `u \times v` |
| union, intersection | `union(A, B)`, `intersect(A, B)` | `A ∪ B`, `A ∩ B` | `A \cup B`, `A \cap B` |
| difference | `setdiff(A, B)` | `A \ B` | `A \setminus B` |
| remainder | `a mod b` | `a mod b` | `a \bmod b` |
| integer in a base | `0xff`, `base(1295, 36)` | `0xff`, `zz₍₃₆₎` | `\texttt{0xff}`, `\mathrm{zz}_{36}` |
| quantity | `25/18*_m/_s` | `25/18 _m/_s` | `\frac{25}{18}\,\mathrm{m}/\mathrm{s}` |
| dot | `dot(u, v)` | `dot(u, v)` | `\operatorname{dot}(u, v)` |
| undefined | `undefined` | `undefined` | `\text{undefined}` |
| infinity | `inf` | `∞` | `\infty` |
| integral | `integrate(e^(x^2), x)` | `∫ e^(x²) dx` | `\int e^{x^{2}}\,dx` |
| sum | `sum(1/k^2, k, 1, n)` | `∑[k = 1, n] 1/k²` | `\sum_{k=1}^{n}\frac{1}{k^{2}}` |
| limit | `limit(f, x, 0, right)` | `lim[x → 0+] f` | `\lim_{x\to 0^{+}} f` |
| derivative | `diff(f, x, 2)` | `d²/dx² f` | `\frac{d^{2}}{dx^{2}} f` |
| partial derivative | `diff(diff((x y)!, x), y)` | `∂²/∂y∂x (x y)!` | `\frac{\partial^{2}}{\partial y\partial x} (x y)!` |

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
- A boolean operand at its operator's level or looser is parenthesised, `(p ∨ q) ∧ r`, but for
  the right side of `⟹`; the operand of `¬` is parenthesised unless it is a name, a constant or
  another `¬`, so a relation under it reads `¬(x < 1)`. A boolean expression as an operand of a
  relation is parenthesised, `x = (¬p)`. The ASCII printer writes the words, the Unicode printer the
  signs.
- `A ∪ B` and `A ∩ B` are at the additive level: a right operand at that level or looser is
  parenthesised, and so is the other of the two on the left, `(A ∪ B) ∩ C`. `A \ B` is
  parenthesised as a `mod` node is (below). The ASCII printer writes the calls.
- A `mod` node is printed infix, `a mod b`, by every printer. It is parenthesised when it is a
  factor of a product (the first included), an operand of `/`, `×` or a sign, or the base of a
  power; its left operand when it is a sum or begins with an open big operator; its right operand
  unless it is a unary expression not beginning with `-`: `(a mod b) c`, `x/(a mod b)`, `-(a mod
  b)`, `(a + 1) mod b`, `a mod (2b)`, `a mod (-b)`.
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
   L `0` takes a space before an R that begins with `x`, `b` or `o` (either case) and a letter, a
   digit or `_`, which would read as a base literal (§3.1): `0 x2`, `0 b1`.
3. a single space otherwise, and always before a unit (`3 _m`, `x _m`, `_kg _m`; `x_m` would be
   one name): `x y`, `2x sin(x)`, `2 sin(x)`, `(x - 1) e^x`, `2 e`. The notation of
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
  sum or a lambda they print and read back as symbols), or the labels `mean`, `median`, `min`, `max`
  and `stdev` of `summary` (§14.25), which are builtin names;
- a list that the bracket rule reads as a vector or a matrix (`[1, 2]`), and a vector or matrix
  with an element that is not scalar;
- an integer shown in a base (§14.27), which reads back as its integer (`0xff` is `255`), or in a
  base other than 16, 2 and 8 does not parse in Unicode (`zz₍₃₆₎`) and is the call `base(1295, 36)`
  in ASCII, which evaluates to it;
- `ifactor`'s product (§14.23), such as `2³ · 3² · 5`, which reads back as the integer it
  shows, `360`;
- an application of an unknown function, such as `y(x) = C₁ e^x` from `dsolve` (§14.22) or `a(n) =
  2·2^n - 1` from `rsolve` (§14.24): it reads back as the call only inside a `dsolve` or `rsolve`
  that declares the function, and elsewhere as a product.

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
| Unbalanced bracket | `(x + 1`, `x]`, `(x]` | missing `)` / `]` does not belong here |
| Missing operand | `2 +`, `* 3` | an expression is missing here |
| Chained relation | `a < b < c` | relations cannot be chained: write `a < b and b < c`, not `a < b < c` |
| `:=` not at statement level | `(a := 5)` | a definition with `:=` must be a statement of its own, as in `a := 5` |
| Bad definition head | `f() := 1`, `f(x, x) := 1`, `f(2) := 1` | the parameters must be different names, as in `f(x, y) := x + y` |
| Defining a reserved name | `e := 3`, `sin := 1` | `e` is a constant, so it cannot be used as a name here / `sin` is built in, so it cannot be used as a name here |
| Reserved keyword | `(then)`, `x + while` | `then` is a reserved word and cannot stand here |
| An if as an operand, an if without else (§19) | `1 + if p then 1 else 2`, `x := if p then 1` | put the `if` in brackets, as in `1 + (if p then 1 else 2)` / an `if` that gives a value needs `else`, as in `if x > 0 then 1 else 0` |
| A block not closed, a loop header (§19) | `for k in 1..3`, `for k in 1..3)`, `for k = 1..3 do k end` | missing `end` / missing `do` or a new line after the loop's header / missing `in`, as in `for k in 1..3` |
| A statement out of place (§19) | `return 1`, `break`, `global a` at the top level; `function` or `g(x) := …` in a block; `end` alone | `return` works only inside a function / `break` works only inside a loop / `global` works only inside a function / a function cannot be defined inside a block / `end` does not belong here |
| A function's parts (§19) | `function f(x) return end`, `function f(x) global x end` | `return` needs a value, as in `return x + 1` / `x` is a parameter of this function, so it cannot be declared global |
| A range outside a for loop | `1..3` | a range `a..b` works only in a `for` loop, as in `for k in 1..3` |
| `mod` not before `(` where an operand is expected | `mod 3`, `x mod(5, 3)` | `mod` needs brackets around what it applies to, as in `mod(7, 3)` / `,` does not belong here |
| Bad number | `5.`, `1.5.2` | a digit must follow the decimal point, as in `5.0` / a number cannot come right after a factor |
| Unknown character or operator | `∇f`, `a ⊗ b`, `x == 1` | unknown symbol `∇` / write `=` for equals, not `==` |
| Number starting an implicit factor | `2 3`, `x 2`, `5 % 3` | a number cannot come right after a factor: put `*` between them, as in `x*2`, or put the number first, as in `2x` |
| Indexing | `v[1]`, `2 [1, 2]` | square brackets after a value do not pick an item: write `nth(v, 1)` |
| Name needs parentheses | `abs x`, `gcd 4` | `abs` needs brackets around what it applies to, as in `abs(x)` |
| Function name without argument | `sin = 1`, `gcd`, `assumptions` | `sin` needs an argument in brackets, as in `sin(x)` / `assumptions` needs empty brackets: write `assumptions()` |
| Power of a function name | `sin²x`, `sin^-1 x` | write `sin(x)²` for the square, or `asin(x)` for the inverse |
| Wrong number of arguments | `sin(1, 2)`, `solve(x = 1)` | `sin` takes 1 argument; `help(sin)` shows examples |
| Bad lambda | `2x -> x`, `(x, y)` alone | the parameters before `->` must be names, as in `(x, y) -> x + y` |
| Integral without differential | `∫ x` | the integral needs a differential such as `dx`, as in `∫ x^2 dx` |
| Power or postfix after a differential | `∫ x dx^2` | put the integral in brackets first, as in `(∫ x dx)^2` |
| Derivative order | `d^2/dx^3 f`, `d^0/dx^0 f` | a derivative's two orders must be the same positive whole number, as in `d^2/dx^2` |
| Derivative shape | `d^2/dx² f`, `d/dx² f` | write a derivative as `d/dx`, `d^2/dx^2` or `d²/dx²` |
| Malformed notation bracket | `lim[x] f`, `Σ[k, 1, n] k` | missing `→`, as in `lim[x → 0]` / missing `=`, as in `Σ[k = 1, n]` |
| `lim` without bracket | `lim(f, x, 0)` | write `lim[x → a] f` or `limit(f, x, a)` |
| `Σ`, `Π` without bracket | `Σ k`, `∏(k)` | write `Σ[k = a, b] f` or `sum(f, k, a, b)` / write `Π[k = a, b] f` or `product(f, k, a, b)` |
| `;` inside a bracket | `(a; b)` | `;` cannot stand inside brackets or square brackets |
| `d…` variable in integral notation | `∫ x ddx` | write `integrate(x, dx)` |
| Bad absolute value | `\|a\|b\|\|`, `\|x - \|y\|\|`, `\|x` | there is nothing between the bars of `\| \|` / an expression is missing here / missing `\|` to close the absolute value |
| Empty or non-scalar `⟨⟩` | `⟨⟩`, `⟨[1, 2]⟩` | a vector's entries must be single values, not lists or vectors |
| Binding a constant | `d/di i^2`, `Σ[pi=1,3] pi` | `i` is a constant, so it cannot be the variable here / `pi` is a constant, so it cannot be used as a name here |
| Lone superscript minus | `x⁻` | a superscript minus needs superscript digits after it, as in `x⁻¹` |
| A base literal (§3.1) | `0b102`, `0x1g`, `0x_ff` | `2` is not a binary digit / `g` is not a hexadecimal digit / a digit separator `_` must stand between two digits |
| A unit (§3.6) | `_tmp`, `_ 2`, `_m := 3` | unknown unit `_tmp` / a unit name must follow `_`, as in `3 _m` / a unit is built in, so it cannot be redefined |
| Minutes or seconds out of place | `15′`, `x′` | `′` follows a number of degrees, as in `30°15′20″` |
| Nesting deeper than 200 levels (every construct counts, a `×` chain included) | `((…(x)…))` | the expression is nested more than 200 levels deep |
| Number out of range | `1e9999999999`, a numeral of more than 1,000,000 digits | this number is too large |
| Invisible or combining character | a zero-width space between `a` and `b` | unknown invisible character U+200B: delete it |

Evaluation errors are not parse errors. Each carries the node that failed, and the parser records
where each node of a script was typed, at the first code point of its first token, so the error is
reported at that node's position. Where the script holds no position for it (a node that occurs in
several places, such as the constant `π`, or one from a definition made in an earlier input), the
error is reported at the start of the statement that failed. A mathematical failure is not an error:
it has the value `undefined` (§14.1).

| Case | Example | Message (gist) |
|---|---|---|
| Definition cycle, recursion with no condition | `a := b; b := a; a`, `f(x) := x f(x - 1); f(3)` | `a` is defined in terms of itself / `f` is defined in terms of itself: a function that calls itself needs an `if` that stops it, as in `f(n) := if n = 0 then 1 else n f(n - 1)` |
| A condition that is no truth value (§19) | `if x > 0 then 1 else 0`, `if 3 then 1 else 0` | the app cannot tell whether `x > 0` is true: use `assume` first, as in `assume(x > 0)` / the condition `3` is neither true nor false |
| A recursion too deep (§19) | `f(n) := if n = 0 then 0 else f(n - 1); f(1000)` | the function called itself more than 1000 levels deep: check that it stops / the function called itself too deeply for the memory set aside: check that it stops |
| A program that runs too long (§19) | `while true do end` | the program ran more than 1000000 steps |
| A local read before it is assigned (§19) | `function f() y := r; r := 1 end; f()` | in `f`, line 1: `r` is used before it is given a value |
| A range or a loop's items (§19) | `for k in 1..2.5 do k end`, `for k in 1..5 step 0 do k end`, `for k in x do k end` | a range needs integers / the step of a range must not be 0 / for iterates a list, a vector, a set or a range a..b |
| A call's missing value (§19) | `function f(x) y := x end; f(3) + 1` | `f` gives no value here: add a `return` |
| An error inside a function (§19) | `function f(x)` … `return [1] + [1, 2]` on its line 3, `f(1)` | in `f`, line 3: cannot add vectors of lengths 1 and 2 |
| Function used as a factor | `(x -> x^2)(3)` | a function cannot be multiplied: give it a name and call it, as in `f(3)` |
| Call of a name that is not a function | after `f := 3`, a stored `f(2)` | `f` is not a function: define it first, as in `f(x) := x^2` |
| User function arity | `f(x) := x; f(1, 2)` | `f` takes 1 argument |
| Reserved later-phase name | `slider(0, 1, 2)`, `plot(x^2, sin(x))` | `slider` works only as a cell of its own in the app, as in `a := slider(0, 5, 0.5)` / `plot` works only as a cell of its own in the app, as in `plot(x^2)` |
| A field in `z` on a plane path (§14.20) | `lineintegral(z, [t, t], t, 0, 1)` | the field uses z on a plane path |
| Line and surface integral arguments (§14.20) | `lineintegral(1, [t], t, 0, 1)`, `lineintegral([x, y, z], [t, t], t, 0, 1)`, `surfaceintegral(1, [u, v], [u, 0, 1], [v, 0, 1])`, `surfaceintegral(1, [u, v, 0], u, [v, 0, 1])` | `lineintegral` needs a path [x(t), y(t)] or [x(t), y(t), z(t)] / `lineintegral` needs a field of as many components as the path / `surfaceintegral` needs a surface [x(u, v), y(u, v), z(u, v)] / `surfaceintegral` needs square brackets [u, a, b] and [v, c, d] |
| Laplace variables (§14.21) | `laplace(t, t, t)`, `laplace(t, t, 2)` | `laplace` needs two different variables / `laplace` needs two variables |
| `lagrange` arguments (§14.20) | `lagrange(x, [x], [x])`, `lagrange(x, [x = 1], [x, x])` | `lagrange` needs constraints g = c in a list / `lagrange` needs a list of distinct variables |
| Vector calculus arguments (§14.20) | `grad([x], [x])`, `jacobian(x, [x])`, `divergence([x, y], [x])`, `curl([x], [x])`, `grad(x, [x, x])` | `grad` needs a scalar function / `jacobian` needs a list of functions / `divergence` needs a field and variables of the same length / `curl` needs a field of 2 or 3 components and as many variables / `grad` needs a list of distinct variables |
| `∈` with a right side that is not a set | `2 ∈ 5`, `2 ∈ [1, 2]` | `∈` needs a set or a number domain on its right, as in `2 ∈ {1, 2}` |
| A boolean operator of something that is not a truth value | `1 and p`, `not [1, 2]` | `and` works on values that are true or false / `not` works on values that are true or false |
| Ordering truth values, arithmetic on them | `true < false`, `(p and q) + 1` | true and false cannot be compared with < or > / true and false cannot be added, multiplied or raised to powers |
| A truth table's names, or an expression they do not decide | `truthtable(p, [p, p])`, `truthtable(p and x > 0, [p])` | `truthtable` takes an expression and a list of distinct names [p, q, …] / `truthtable` cannot tell whether `x > 0` is true from the names given |
| A recurrence with one shift, a condition off the integers | `rsolve(a(n) = 3, a(n))`, `rsolve(a(n + 1) = a(n), a(n), a(1/2) = 1)` | `rsolve` needs a recurrence: a(n) with at least two shifts, as a(n + 1) and a(n) / `rsolve`'s conditions are a(k) = v at integers k |
| A set operation of something else | `{1} ∪ 2`, `subsets([1, 2])` | `union` takes sets / `subsets` takes a set |
| Too many subsets or pairs | `subsets(A)` of 17 items, `cartesian(A, B)` of more than 100,000 pairs | `subsets` takes a set of at most 16 items / `cartesian` forms at most 100000 pairs |
| Ordering a complex number | `i < 2`, `min(1, i)` | complex numbers cannot be compared with < or > |
| `×` of anything but scalars or 3-vectors | `[1, 2] × [3, 4]` | × needs two 3-vectors |
| Vector and matrix dimensions | `[1, 2] + [1, 2, 3]`, `1 + [1, 2]`, `[1, 2]^2` | cannot add vectors of lengths 2 and 3 / cannot add a scalar and a vector / a vector has no powers |
| A vector or a matrix as an exponent | `π^[1, 2]`, `2^[[1, 2], [3, 4]]` | a vector cannot be an exponent / a matrix cannot be an exponent |
| Arithmetic on a set, a list or a truth value | `{1, 2} + 1`, `true^2` | sets cannot be added, multiplied or raised to powers / true and false cannot be added, multiplied or raised to powers |
| Arithmetic on a domain | `x + ℝ + 1`, `2^ℂ` | number domains cannot be added, multiplied or raised to powers |
| A number too long to read back | a value or a definition's value holding an integer of more than 1,000,000 digits, or a fraction whose numerator or denominator has more than 315,653 (the digits of the canonical form's folding bound, 2²⁰ bits): after `a := 3^200000`, `a a a a a a a a a a a`; after `b := 1/3^200000`, `b b b b` | the number is too large to show |
| `append`, `setnth` arguments (§12.1) | `append({1}, 2)`, `setnth([1, 2], 3, 0)`, `setnth([1, 2], 1/2, 0)` | `append` needs a list, a vector or a matrix / `setnth`: index 3 is out of range 1 to 2 / `setnth` needs a whole-number index |
| `dot`, `nth`, `length` arguments | `dot([1, 2], 3)`, `nth([1, 2], 3)`, `nth([1, 2], 1/2)`, `length(3)` | `dot` needs two vectors of the same length / `nth`: index 3 is out of range 1 to 2 / `nth` needs a whole-number index / `length` needs a list, a vector or a set |
| A rejected assumption | `assume(x = 2)`, a contradiction, `assume(2)` | the reason the assumption is rejected (`assume` cannot take an equation such as `x = 2`: define the value with `:=` instead) / `assume` takes relations, as in `assume(x > 0)` |
| `assume`, `clear` inside an expression | `1 + assume(x > 0)` | `assume` works only as a statement of its own, not inside an expression |
| `clear` of a non-name, `subs` without pairs | `clear(2)`, `subs(x, 2)` | `clear` needs a name / `subs` needs pairs name = value, as in `subs(x^2, x = 3)` |
| `numeric` digits | `numeric(π, 0)` | the number of digits must be a whole number from 1 to 10000 |
| Too deep | definitions nested more than 200 deep, a value deeper than 200 levels | the definitions refer to each other more than 200 levels deep / the expression is nested more than 200 levels deep |
| Too large | a value of more than 1,000,000 nodes | the result is too large |
| Out of range | `sin(1e10000)`, `1e999999999 * 1e999999999 * 1e999999999` | a number grew too large or too small for this precision |
| A number-theory function of a number that is not an integer | `isprime(1/2)`, `primes(1, 2.5)` | `isprime` takes an integer / `primes` takes integers |
| A modulus that is not positive, congruence lists that do not match | `powmod(2, 3, 0)`, `crt([1, 2], [3])` | `powmod`'s modulus must be a positive integer / `crt` takes a list of residues and a list of moduli of the same length |
| `diophantine` of a nonlinear equation | `diophantine(x^2 + y = 1, [x, y])` | `diophantine` takes a linear equation a x + b y = c |
| Too wide or too many | `primes(0, 10^7)`, `divisors(210^20)` | `primes` takes an interval of at most 1000000 integers / `divisors` lists at most 100000 divisors |
| A rule set that does not terminate | (a bug in a rule set) | simplification did not finish, which is a bug in the app: report it on the project's GitHub issues page with the code basic/… |
| A polynomial past the algebra's degree bound (§14.9) | `factor(x^10001 - 1)` | the polynomial's degree exceeds 10,000 |
| A product or power past the algebra's size bounds | `cancel((x + 1)^99999/x)` | a polynomial of more than 100,000 terms / the polynomial is too large |
| A coefficient's power past the folding bound | `cancel((3x + 6)^-1000000000)` | a power of a coefficient is too large to compute |
| Exponents or multiplicities past an integer | `factor((x + 2)^1073741823 (-x - 2)^1073741823 (x^2 + 4x + 4))` | the exponents are too large |
| Any other arithmetic failure within the algebra | (a defect) | arithmetic failed: … |
| `apart` without a variable | `apart(1/x, 2)` | `apart` needs a variable |
| A derivative too large to compute | `diff(x^x, x, 40)` | differentiation did not finish, which is a bug in the app: report it on the project's GitHub issues page with the code … |
| A side at infinity | `limit(e^x, x, inf, left)` | a limit at ∞ or -∞ has no side |
| `taylor`'s order or point | `taylor(e^x, x, 0, 1/2)`, `taylor(e^x, x, inf, 2)` | `taylor`'s order must be a whole number, 0 or more, as in `taylor(e^x, x, 0, 3)` / `taylor`'s point must be finite |
| A Taylor polynomial that does not exist | `taylor(sin(x)/x, x, 0, 2)`, `taylor(sqrt(x), x, 0, 2)` | the derivative of order 0 is undefined at x = 0 / the derivative of order 1 could not be shown continuous at x = 0 |
| `solve` arguments | `solve(x, x)`, `solve(x = 1, [x, x])`, `solve(x = 1, 2)` | `solve` needs an equation or a set of equations / `solve` needs a variable or a list of distinct variables |
| Bounds in the wrong order (§14.15) | `area(x, x, 1, 0)`, `volume(x, x, 1, 0)`, `arclength(x, x, 1, 0)`, `maximize(x, x, 1, 0)` | `area` needs a ≤ b (and likewise) |
| Shells left of the axis | `shellvolume(x, x, -1, 1)` | `shellvolume` needs a ≥ 0 |
| Linear-algebra arguments (§14.16) | `det([1, 2])`, `rref(3)`, `norm([[1, 2]])`, `det(M, minors)`, `norm(v, 3)` | `det` needs a square matrix / `rref` needs a matrix / `norm` needs a vector / `det`'s method must be cofactor or elimination / `norm`'s order must be 1, 2 or inf |
| A singular matrix | `inverse([[1, 2], [2, 4]])`, `[[0]]^-1` | the matrix is singular |
| `linsolve`, `gramschmidt`, `normalize` shapes | `linsolve([[1, 2]], [1, 2])`, `gramschmidt([[1, 2], [3]])`, `normalize([0, 0])` | `linsolve` needs a matrix and a vector of matching height / `gramschmidt` needs vectors of the same length / `normalize` needs a vector that is not zero |
| `charpoly`'s variable | `charpoly([[1, 2], [3, 4]], 2)`, `charpoly([[x, 1], [0, 1]], x)` | `charpoly` needs a variable / `charpoly`'s variable must not occur in the matrix |
| `identity`, `zeros` sizes | `identity(0)`, `zeros(2, 1/2)`, `det(M, cofactor)` of a 9×9 | `identity` needs a positive integer / `zeros` needs positive integers / cofactor expansion works up to 8×8: use `det(M, elimination)` |
| `degrees` components | `degrees(30, -1, 0)`, `degrees(i, 0, 0)` | only the first part of `degrees` that is not zero can be negative, as in `degrees(-30, 15, 0)` / `degrees` needs real numbers |
| `triangle`'s parts (§14.19) | `triangle(A = 30°, B = 60°, C = 90°)`, `triangle(a = -1, b = 2, c = 2)`, `triangle(a = 1, b = 2, C = 200°)`, `triangle(a = 1, a = 2, C = 20°)` | `triangle` needs a side / `triangle`'s sides must be positive / `triangle`'s angles must be between 0 and a half turn / `triangle`'s parts must be distinct |
| Coordinate-geometry arguments (§14.19) | `slope(1, [1, 2])`, `distance([1, 2], [1, 2, 3])`, `lineequation([1, 2], [1, 2])`, `polygonarea([[0, 0], [1, 1]])` | `slope` needs points [x, y] / `distance` needs two points of one dimension / `lineequation` needs two distinct points / `polygonarea` needs at least three points [x, y] |
| Conic arguments (§14.19) | `ellipse([-3, 0], [3, 0], 6)`, `hyperbola([-3, 0], [3, 0], 6)`, `conic([0, 0], x = 4, -1)`, `parabola([0, 0], x^2 = 4)`, `parabola([x, 0], y = 1)` | `ellipse` needs 2a greater than the distance between the foci / `hyperbola` needs 2a between 0 and the distance between the foci / `conic` needs a positive eccentricity / `parabola` needs a directrix, a line such as y = -1 / `parabola`'s arguments must not hold x or y |
| An application's other arguments | `area(x, 2, 0, 1)`, `arclength([t, t, t], t, 0, 1)` | `area` needs a variable / `arclength` needs a function or a pair [x(t), y(t)] |
| The prime notation (§5) | `sin'(x)`, `y' + 1`, `f'(1)` with `f(x, y) := x y` | the prime notation is for user functions: write diff(sin(x), x) / y' needs an argument / the prime notation needs a function of one argument: write diff |
| Bases (§14.27) | `hex(1/2)`, `base(10, 37)`, `digits(-1, 2)` | `hex` needs an integer / the base must be an integer from 2 to 36 / `digits` needs a non-negative integer |
| `tvm`'s parts (§14.28) | `tvm(n = 10, i = 5 %, pv = -1000, x = 0)`, `tvm(n = 1, i = 1, pv = 1, pmt = 1, fv = 1)` | `tvm`'s parts are n, i, pv, pmt, fv and type / `tvm` needs four of n, i, pv, pmt, fv |
| Finance (§14.28) | `fv(1/10, 2, 0, -100, 2)`, `irr([100, 60])`, `amortization(1/10, 0, 100)`, `effective(1/10, 1/2)` | `fv`'s type must be 0 (end) or 1 (beginning) / `irr` needs cash flows of both signs / `amortization`'s periods must be an integer from 1 to 1000 / `effective`'s m must be a positive integer |
| Bitwise (§14.27) | `bitand(1.5, 2)`, `bitcount(-1)`, `shiftleft(1, -1)` | `bitand` needs integers / `bitcount` needs a non-negative integer / `shiftleft`'s shift must be an integer from 0 to 1048576 |
| `convert`'s target (§14.26) | `convert(3 _m, _s)`, `convert(3 _m, 2 _km)`, `convert(3 _m^2, _km _m)`, `convert(0 _degC, _degF)` | cannot convert m to s / `convert` needs a unit such as _m/_s / `convert`'s target must not hold two units of one dimension / a temperature of 0 is the number 0, which keeps no unit: write 0.0 _degC or 273.15 _K |
| Dimensions (§14.26) | `3 _m + 2 _s`, `3 _m + 2`, `3 _m < 2 _s`, `sin(3 _m)`, `2^_m`, `_m^x`, `3 _m in Reals` | cannot add m and s / cannot add a number and m / cannot compare m and s / `sin` needs a number, not a quantity in m / an exponent cannot have units / a quantity's exponent must be a rational number / a quantity is not a member of a number domain |
| `odesolve`'s arguments (§14.22) | `odesolve(y''(t) = y(t), y(t), y(0) = 1, 0, 1)`, `odesolve(y'(t) = a y(t), y(t), y(0) = 1, 0, 1)`, `odesolve(y'(t) = y(t), y(t), y(3) = 1, 0, 1)`, `odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 0)` | `odesolve` needs the initial conditions y(a), y'(a), once each / `odesolve` needs equations in t and the unknowns only, with real values / `odesolve` needs the initial point in [a, b] / `odesolve`'s steps must be an integer from 1 to 100000 |
| A numeric solution that blows up | `odesolve(y'(t) = y(t)^2, y(t), y(0) = 1, 0, 2)` | `odesolve`'s solution is not finite on the interval: it blows up or is undefined |
| `dsolve`'s arguments (§14.22) | `dsolve(y'(x) = 1, 2)`, `dsolve(x = 1, y(x))`, `dsolve(y'(x) = y(x), y(x), y(x) = 1)` | `dsolve` needs an unknown function y(x), or a list of them in one variable / `dsolve` needs an equation or a list of equations / an initial condition is y(a) = b or y'(a) = b, with a and b free of the unknowns |
| Distribution parameters (§14.25) | `normalpdf(0, 0, 0)`, `tpdf(1, 0)`, `binompdf(10, 2, 3)`, `binompdf(2.5, 1/2, 1)`, `binompdf(10, 1/2, 1/2)`, `geometpdf(0, 1)`, `hypergeompdf(5, 6, 1, 0)`, `invnorm(2)`, `unifpdf(1, 2, 2)`, `normalcdf(i, 1)` | `normalpdf` needs σ > 0 / `tpdf` needs df > 0 / `binompdf` needs 0 ≤ p ≤ 1 / `binompdf` needs a non-negative integer n / `binompdf` needs an integer k / `geometpdf` needs 0 < p ≤ 1 / `hypergeompdf` needs integers 0 ≤ K ≤ N and 0 ≤ n ≤ N / `invnorm` needs 0 ≤ p ≤ 1 / `unifpdf` needs a < b / `normalcdf` needs a real bound |
| A distribution's value beyond the `double` range, above or, not zero, below | `gamma(200.0)`, `gamma(-200.5)`, `numeric(beta(6000, 6000))` | `gamma`'s value is too large, or too close to zero, to approximate / `beta`'s value is too large, or too close to zero, to approximate |
| A discrete law past the provider's `int` range | `numeric(binompdf(10^12, 1/2, 3))` | `binompdf` can give an approximate value only for n ≤ 2147483647 |
| A quantile's tail beyond the `double` range | `invnorm(10^(-400))`, `invt(1 - 10^(-400), 3)` | `invnorm`'s tail probability is too small to approximate / `invt`'s tail probability is too small to approximate |
| Test arguments (§14.25) | `ttest([1, 1, 1], 0)`, `ttest(6, 2, 1, 5)`, `proptest(120, 100, 1/2)`, `chi2gof([20, 30], [0, 50])`, `chi2gof([10, 20], [15, 20])`, `ttest([4, 5, 6], 5, sideways)`, `ztest([4, 5, 6], 2, 5, pooled)`, `chi2test([1, 2])`, `anova([1], [2])` | `ttest` needs s > 0 / `ttest` needs at least 2 values / `proptest` needs an integer x with 0 ≤ x ≤ n / `chi2gof` needs positive expected counts / `chi2gof` needs expected counts that sum to the observed total: 35 against 30 / `ttest`'s alternative must be two, less or greater / `ztest` has no `pooled` option: `pooled` belongs to `ttest2` and `tinterval2` / `chi2test` needs a matrix of at least 2 rows and 2 columns / `anova` needs more values than groups |
| Interval arguments (§14.25) | `zinterval(6, 2, 25, 95)`, `propinterval(120, 100, 0.95)`, `tinterval([4], 0.95)`, `zinterval(6, 0, 25, 0.95)`, `tinterval([3, 3, 3], 0.95)` | `zinterval` needs a confidence level between 0 and 1 / `propinterval` needs an integer x with 0 ≤ x ≤ n / `tinterval` needs at least 2 values / `zinterval` needs σ > 0 / `tinterval` needs s > 0 |
| Regression arguments (§14.25) | `linreg([1, 1, 1], [1, 2, 3])`, `quadreg([1, 2], [1, 2])`, `polyreg([1, 2], [1, 2], 11)`, `expreg([1, 2], [1, -2])`, `lnreg([0, 2], [1, 2])`, `predict(3, 4)` | `linreg` needs at least 2 distinct x values / `quadreg` needs at least 3 distinct x values / `polyreg`'s degree must be an integer from 1 to 10 / `expreg` needs positive y values / `lnreg` needs positive x values / `predict` needs a fit: an equation y = … or the list `linreg` gives, as in `predict(linreg(xs, ys), 3)` |
| Statistics data (§14.25) | `mean([])`, `variance([1])`, `mean(5)`, `mean([1, i])`, `mean([1, 2], [1])`, `mean([1, 2], [1, -1])`, `cov([1, 2], [1, 2, 3])`, `percentile([1, 2], 150)`, `zscore(1, 2, 0)` | `mean` needs at least 1 value / `variance` needs at least 2 values / `mean` needs data: a list or vector of real numbers / `mean` needs real data / `mean` needs one count per value / `mean` needs counts that are non-negative integers / `cov` needs two lists of the same length or a matrix of two columns / `percentile` needs 0 ≤ p ≤ 100 / `zscore` needs σ > 0 |

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
| `grad(x^2 + y^2, [x, y])` | `grad(x^2 + y^2, [x, y])` | `⟨2x, 2y⟩` (§14.20) |
| `dsolve(y'(x) = x y(x), y(x), y(0) = 2)` | `dsolve(y'(x) = x*y(x), y(x), y(0) = 2)` | `y(x) = 2 e^(x²/2)` (§14.22) |
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
| `p and q or r` | `or(and(p, q), r)` |
| `not x < 1` | `not(x < 1)` |
| `p ⟹ q ⟹ r` | `implies(p, implies(q, r))` |
| `a mod b c` | `mod(a, b)*c` |
| `2x mod 3` | `mod(2*x, 3)` |
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

- Indexing `v[k]`.
- Chained relations as conjunctions (`0 < x < 1`).
- Set-builder notation.
- Declared unknown functions (Phase 7) and `∇`.
- Units arrived in Phase 8 (§3.6, §14.26). Since then `_` starts no identifier: a session saved
  before then (version 3) is migrated as it opens, every name that began with an underscore
  renamed with `v` before it in its cells and its canvas (`_tmp` is `v_tmp`, `_1` is `v_1`, or
  `v_tmp1` when the session already uses `v_tmp`); such a name typed now is "unknown unit `_tmp`"
  or "expected a unit name after `_`".
- Base literals arrived in Phase 8 (§3.1, §14.27): a saved cell with `0x`, `0b` or `0o` before a
  letter or a digit, once zero times a name (`0xa`), now reads it as a literal (`10`) or reports the
  digit that does not fit.

**Migration notes.** A release that turns a reserved form into syntax can change what old text
means; each such change is listed here.

- Phase 8: `and`, `or` and `not`, reserved for Phase 9, are the boolean operators (§8.4), and
  `xor` and `implies`, which were free names, are operator words too: a definition of `xor` or
  `implies` is now an error, and a name so written must be renamed.
- Phase 9: `end`, `do`, `break`, `continue`, `global` and `step` are keywords (§19), as `if`,
  `then`, `else`, `for`, `while`, `function` and `return` were already, and `print` is a builtin, so
  a name so written must be renamed; a session saved before then (version 4) is migrated as it
  opens, each such name renamed with `v_` before it (`end` is `v_end`, or `v_end1` when the session
  already uses `v_end`) in its cells and its canvas. `1..n`, an error before, is a range in a `for`
  loop.
- Phase 10: `plot` is a builtin (§12.3), drawn by the worksheet and refused by the engine and the
  CLI, so a name so written must be renamed; a session saved before then (version 5) is migrated as
  it opens, `plot` renamed `v_plot` (or `v_plot1`) in its cells and its canvas, as the Phase 9
  keywords are.
- Phase 8: `mod` after an operand is the infix remainder (§9.5). `x mod(5, 3)`, which read as
  the product `x·mod(5, 3)`, is now an error; write `x·mod(5, 3)` or `x*mod(5, 3)`.

## 19. Programs

### 19.1 The if expression

`if c then a else b` is a value: the condition `c` is evaluated, and simplified as a statement's
value is, so the rule sets decide it (`if sin(π/6) = 1/2 then 1 else 0` → `1`); a relation `a > b`,
`a ≥ b`, `a < b`, `a ≤ b`, `a = b` or `a ≠ b` that stays is decided by the assumptions when they
fix the sign of `a − b` by the same structural deduction `abs` uses (§14.6: after `assume(y > 0)`, `if y > 0 then 1 else 0` → `1`, while `if y
> 1 then …` stays undecided); then only the branch it chooses is evaluated. It parses where an expression begins, at the lambda's
level (§4.1): the condition is an expression that stops at `then`, the else branch extends as far
right as a lambda's body, and an `if` in the then branch takes the first `else`. It needs its
`else` ("an if used as a value needs else") and takes no `end`; as an operator's operand it is
parenthesised (`(if p then 1 else 2) + 1`). Its parse form is `if(c, a, b)`. A condition that does
not evaluate to `true` or `false` is the error "the app cannot tell whether `x > 0` is true: use `assume` first, as in
`assume(x > 0)`", so a
function defined with an `if` evaluates only on arguments that decide it.

| Input | Result |
|---|---|
| `if 2 < 3 then a else b` | `a` |
| `f(n) := if n = 0 then 1 else n f(n - 1); f(10)` | `3628800` |
| `fib(n) := if n < 2 then n else fib(n - 1) + fib(n - 2); fib(15)` | `610` |
| `if x > 0 then 1 else 0` | error: the app cannot tell whether x > 0 is true |

**Recursion.** A function may call itself, directly or through other functions (§8.3, §14.3), when
the recursion can end: through an if expression, or through a function defined with `function`
(§19.4), whose body may end it with a condition, a loop or `return`. A call is allowed again when
such a function has been called since the name's last entry, its own call included. At most 1,000
calls are open at once ("recursion deeper than 1000 calls"), and each call is a step of the run
(§19.5), so a recursion that branches widely ends at the budget; a recursion within the call limit
on a thread whose stack cannot hold it is "recursion too deep for the stack". A function defined
with `:=` that recurses with no `if` on its way stays the cycle error.

### 19.2 Statements and blocks

A program is ordinary statements and the control statements `if`, `for` and `while` and the
function definition `function`, each holding a **block**: statements separated by `;` or newlines,
closed by `end`. Every statement of a script runs and shows its result as before (§2); a block's own
statements show nothing. Nothing tells a program from a one-line expression but its first word: a
statement that begins with `if`, `for`, `while` or `function` is one of these, anything else is
parsed as before, and a script may mix them.

```
function fact(n)
  r := 1
  for k in 1..n
    r := r k
  end
  return r
end
function collatz(n)
  count := 0
  while n ≠ 1
    if n mod 2 = 0 then n := n/2 else n := 3n + 1 end
    count := count + 1
  end
  return count
end
for p in primes(1, 20) do print(p^2) end
```

- **`if c then … end`.** When a newline follows the first `then`, each branch is a block, ended by
  `else` or `end`, and the `end` is needed. `else if c then` on one line continues the same `if`,
  closed by one `end`; to nest an `if` in an `else`, begin it on a new line. When a statement
  follows `then` on its line, the `if` is on one line: each branch runs to `else`, `end` or the end
  of the line (so `if p then a := 1; b := 2` sets both when `p`), and the `end` may be left out; a
`;` that ends the line ends the statement, which then shows nothing (`if p then 3 else 4;`). A
  statement that begins with `if` is always this statement; where a value is expected, `if` is the
  if expression (§19.1).
- **`for x in a..b [step s] … end`.** (`..` anywhere else, `[1..3]` or `sin(1..3)`, is "a range
  a..b is written only in a for loop".) `a`, `b` and `s` are evaluated once and must be integers ("a
  range needs integers"); the step is 1 unless given, must not be 0, and counts down when negative;
  a range past its end, `5..1`, runs nothing. `for x in e … end` takes the items of a list or a
  vector in order, of a set in canonical order, or the rows of a matrix; anything else is "for
  iterates a list, a vector, a set or a range a..b". `..` is written only in a `for` header.
- **`while c … end`.** The condition is evaluated before each turn.
- The header of a loop ends at `do` or at a newline; a body that begins on the header's line needs
  the `do`, and every loop ends with `end`.
- **`break`** leaves the innermost loop and **`continue`** begins its next turn; both are parse
  errors outside a loop.
- **Conditions.** A condition is evaluated and simplified as a statement's value is, and must be
  `true` or `false`: "the app cannot tell whether … is true" otherwise.

**Output.** `print(e)` as a statement in a block or a function's body appends the value of `e` to
the output of the statement at the top level that ran it, in order, a function called from inside
an expression included; the worksheet shows the output above the statement's value and the CLI
prints each value on a line of its own. A statement ended by `;` hides its value, not its output,
and a statement that fails keeps what it printed before. Anywhere else `print(e)` is `e`: `print(2 +
3)` at the top level is `5`, its value.

**Values.** A block's value is the value of the last statement it ran, when that statement is an
expression or an `if` whose branch has one; an assignment, a loop, `print` and `global` have none. A top-level
`if` shows its value as an expression does; a loop, or an `if` with none, shows nothing.

### 19.3 Assignment and scope

- **At the top level**, outside every block, `name := e` is a definition as ever, delayed (§14.3).
- **In a block**, `name := e` evaluates `e` now and binds `name` to its value, which is taken as it
  is at each later use, never evaluated again. A block at the top level runs in the document: its
  assignments define the document's names for every later statement. So after `n := 1; b := n + 1;
  for k in 1..2 do n := n + 1 end`, `b` (delayed) is 4, while `for k in 1..1 do c := n + 1 end`
  binds `c` to a number.
- **A loop variable** is local to its loop, and bound in its body as a binder's is (§11.2), so `i`
  and `e` may be loop variables there and are the constants again after the loop: assigning it inside sets it for the rest of that turn,
  and after the loop the name means what it meant before.
- **A function's body** has a scope of its own: its parameters, and every name it assigns anywhere,
  are local to the call, decided before it runs (as in Python); reading such a local before it is
  assigned is the error "`r` is used before it is given a value". Every other name is the document's,
  read as each statement begins. `global a, b` makes those names the document's in the whole
  body, so that assigning them defines them there (creating them if need be); a parameter cannot be
  global. A function sees neither its caller's locals nor its loop variables.
- An assignment in a block of a lambda (`g := x -> x^2`) does not make `g` callable: functions are
  defined at the top level only, with `:=` or `function`.
- A list is a value: `append(L, x)` and `setnth(L, k, x)` (§12.1) give a new one, which a block
  assigns, `L := setnth(L, j, t)`, as it would any value (there is no `L[k] := …`).

### 19.4 Functions

`function f(p, …) … end` defines `f` in the document, as `f(p, …) := …` does: one namespace, the
later definition replacing the earlier, and `f` callable in its own body and every statement after
it. It may have no parameters (`function main() … end`, called `main()`); `f() := …` stays an error.
It echoes its head, `function fact(n)`. A call evaluates its arguments, runs the body, and has the
value of its `return`, else its body's value (§19.2). A call that ends with no value is a statement
with no value when it is the whole statement, and the error "`f` gives no value here: add a `return`" inside an expression.
`return` needs a value, and is a parse error outside a function's body; `function`, and `f(x) := …`,
inside a block are "functions are defined at the top level only". A function defined with
`function` runs only on what its conditions decide: `diff`, the prime notation and plotting take
functions defined with `:=`.

An error inside a function's body is reported at the call, its message led by the function and the
line of the statement that failed, counted in the text that defined it: "in f, line 3: …".

### 19.5 Limits

Each statement at the top level may take at most 1,000,000 steps, a step being a statement run, a
loop's turn or a call of a function defined either way ("the program ran more than 1000000
steps"), so `fib(30)` with `fib(n) := if n < 2 then n else fib(n - 1) + fib(n - 2)` ends there; calls of functions, defined either way, are
at most 1,000 deep (§19.1). Every step checks the request to stop, so a long run stops promptly.

### 19.6 Steps

Under `steps` (`steps(f(5))`, the worksheet's Show steps, the CLI's `--steps`) a statement's program
narrates its run: a step for each statement of its outermost block, which is the top-level block
or, for a statement that calls a function, the body of each call it makes itself, with the line it
is on (counted in the text it was typed in) and its value after it: `Line 3: r := k r` = `6`, `Line
4: the condition n mod 2 = 0` = `true`, `Line 7: return count` = `3`. A function called from those
statements is not narrated: its value is in the step of the statement that called it. A loop's
first two iterations and its last are narrated, each led by `Line 2: pass 1 of 5 through the loop, with
k` = `1`, and the iterations between them are one step, `Line 2: passes 3 to 4 of 5 repeat the same
lines; the last one has k` = `4`. Each statement is written as typed (`Line 3: s := s +
k`), its value after it. A run's narration has at most 500 steps and the stop step, the rest told as one step that
counts them all; a run whose kept events pass 5,000 (only the iterations shown count) says where its
narration stops, "No more steps are shown after these N". The steps of the statement's
value follow it.

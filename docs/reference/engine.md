# Engine

How the symbolic engine in `engine/` works, one section per area as it lands. Read `CLAUDE.md`
first; this file assumes its **Architecture rules**. The engine is pure Java: no JavaFX, no
clock, no randomness, no I/O.

## Numbers

Package `mathcanvas.engine.number`. The expression tree wraps these values in nodes; this
package knows nothing of expressions, symbols (`π`, `e` and `∞` are `Constant` nodes, not
numbers; **Expressions**), parsing or printing (`toString` is for debugging only).

### The tower

`Numeric` is a sealed interface over four records; `RealNumeric` is the sealed sub-interface of
the three ordered kinds.

| Kind | Holds | Normal form |
|---|---|---|
| `Int` | `BigInteger` | any integer |
| `Rational` | numerator and denominator, `BigInteger` | lowest terms, denominator positive and never 1 |
| `Real` | `BigDecimal` | trailing zeros stripped, so `1.0` and `1.00` are one value |
| `Complex` | two `RealNumeric` parts | imaginary part never `Int 0`; both parts exact or both `Real` |

Every value has one representation, so the records' own `equals` and `hashCode` are value
equality within a kind. A rational with denominator 1 **is** an `Int`, and a complex number with
imaginary part `Int 0` **is** its real part. The canonical constructors validate and
normalise: `new Real` strips trailing zeros, `new Rational` reduces to lowest terms with a
positive denominator and throws only when the reduced denominator is 1 (`new Rational(4, 2)`),
and `new Complex` throws on an `Int 0` imaginary part or on one exact and one `Real` part. The
factories also demote (`Rational.of(4, 2)` is `Int 2`, `Complex.of(x, Int 0)` is `x`). A
`Real 0.0` imaginary part stays, because it is inexact. A complex number is exact or inexact as
a whole: `Complex.of(re, im, mc)` promotes an exact part beside a `Real` one (**Precision**),
so `1 + 2.0i` is `1.0 + 2.0i`.

Constants: `Int.ZERO`, `ONE`, `TWO`, `MINUS_ONE`, `Complex.I`.

### Promotion and contagion

`Int ⊂ Rational ⊂ Real`, each `⊂ Complex`. A binary operation promotes the lower operand,
operates, and demotes the result when it is exactly representable lower (`1/2 + 1/2` is `Int 1`,
`i·i` is `Int -1`). **Inexactness is contagious and never demoted**: any operation with a `Real`
operand returns a `Real`, or a `Complex` with `Real` parts, even when the value is an integer
(`2.5 · 2` is `Real 5.0`). Nothing turns a `Real` into a `Rational` implicitly.

The predicates `isZero`, `isOne` and `isInteger` test the value (`Real 1.0` is one); `isExact`
is false when any part is a `Real`. A rule needs both: `x·1.0` must not become `x`.

### Precision

Operations that can be inexact take a `java.math.MathContext`; exact kinds ignore it, and the
context lives on the operation, never on the number. `add`, `subtract`, `multiply`, `divide`,
`pow` and `sqrt` take one. `negate`, `abs`, `compareTo`, `compareValue`, `signum`, `floor`,
`ceil`, `round`, `truncate`, `re`, `im` and `conj` do not: they are exact on every kind.

A complex result whose one part is exact and the other a `Real` has its exact part promoted, by
`Complex.of(re, im, mc)`: an `Int` exactly, a `Rational` rounded to the context (under an
unlimited context a non-terminating one throws). So `(1 + 2i) + 0.5` is `1.5 + 2.0i`, and
`sqrt(-2.25)` and `(-4)^0.5` are `Complex(Real 0, Real …)`. The constructor and the two-argument
`Complex.of` have no context and throw on a mixed pair.

A `Real` result is the exact result rounded **once** to the context. When no `Rational` is
involved this is `BigDecimal`'s own operation under the context. With a `Rational` operand
`p/q`, the operation is carried out exactly on fractions and the one final division rounds. So
`0.1 + 0.2` is exactly `0.3` at every precision, and `1/3` is never rounded before it is added.
Past a magnitude gap of 10,000 digits between the terms of a mixed sum, the numerator is rounded
to odd at `p + digits(q) + 3` digits instead of formed exactly. It still rounds as the exact sum
would (Boldo and Melquiond, 2008). A zero term is never added: the sum is the other term, so a
`Real 0` part does not write its partner out to a distant scale. Each part of a complex product
or quotient is formed exactly
(`pr − qs`, `ps + qr`, and over `r² + s²`) and rounded once, so it stays right under
cancellation; it is exact when its operands are. When `r²` and `s²` differ by more than 10,000
digits, the exact norm would be that long, so it is bracketed instead: truncated at
`p + digits(q) + 3` digits, it lies between the truncation and one unit above it. The quotient is
computed against both ends, and since rounding is monotone, equal results are the exact quotient
rounded once. They differ only when the quotient lies within the bracket's width of a rounding
boundary (an exact value just below a power of ten, rounded UP: `(4/15)i/(10^-5100 + 0.2666…7i)`
at one digit is `1`, not `2`); then the truncation precision doubles, up to `4p + 400` digits,
and at that cap the norm is formed exactly. So every part of a complex product or quotient is
rounded once from the exact value; the far-apart divisor costs one division more.

`MathContext.UNLIMITED` is accepted. Addition, subtraction and multiplication are then exact,
and every result that would be inexact (a division, root or conversion whose decimal expansion
does not terminate, an inexact `pow`, `argument` or elementary function) throws
`ArithmeticException` with the message "an inexact result needs a finite precision".
`IllegalArgumentException` is kept for invalid arguments (a root degree below 1, a NaN `double`).

`double` appears only in `RealNumeric.toDouble()` (for the plotting evaluators; within one ulp,
correctly rounded except for a `Rational`) and `Real.of(double)` (through the shortest decimal
representation, so `0.1` is `0.1`; NaN and infinities throw).

### Operations

- **Arithmetic** on `Numeric`: `add`, `subtract`, `multiply`, `divide` (division by any zero,
  exact or `Real`, throws `ArithmeticException`), `negate`.
- **Integer powers** `pow(int, mc)`: exact for exact kinds; `0^0` is 1, and `0^-n` throws.
  An inexact power (`Real`, or `Complex` with `Real` parts) is the exact power rounded once
  whenever it has at most 100,000 digits, so `pow(x, 2)` is `x·x`; it is formed on the parts'
  exact decimals (a negative complex power as `(c - di)/(c² + d²)`, one division per part), never
  through reduced fractions, whose gcds made `z^-1160` take seconds. Beyond 100,000 digits it is
  binary powering at `p + digits(n) + 5` digits rounded at the end, which is faithful (within one
  ulp). `BigDecimal.pow(int, MathContext)` is not used: its documented error is two ulps.
- **General powers** `pow(Numeric, mc)`: the principal value `exp(y·Log x)`, with the
  logarithm's argument in `(-π, π]`. The result is exact only when both operands are exact and
  the value is a Gaussian rational:
  - an `Int` exponent (outside the `int` range only for the bases 0, 1 and -1);
  - `p/q` on a non-negative exact base whose numerator and denominator are perfect `q`-th powers
    (`8^(1/3)` is `2`, `(8/27)^(2/3)` is `4/9`);
  - `p/2` on a negative exact base whose absolute value is a rational square, giving
    `|x|^(p/2)·i^p` (`(-4)^(1/2)` is `2i`, `(-4)^(3/2)` is `-8i`).

  Everything else is inexact. A negative base with any other non-integer exponent gives the
  principal complex value: `(-8)^(1/3)` is `1 + 1.7320…i`, not `-2`. A `Real` exponent with an
  integer value is an integer power made inexact, so `(-8)^2.0` is `Real 64`; an exact base keeps
  its exact power, rounded once, up to 100,000 digits, and beyond is rounded at `p + digits(n) + 5`
  digits first, so `(1/3)^1e9` is a faithful `Real` at once. `0^y` is 0 when `Re y > 0` and 1
  when `y = 0`; otherwise it throws.
- **Square root** `sqrt(mc)`, the principal root. It is exact for a rational square, and for a
  Gaussian rational square through the half-angle formulas (`sqrt(3+4i)` is `2+i`). A negative
  real gives `Complex(0, √|x|)`, its zero of the root's exactness (`sqrt(-4)` is `2i`,
  `sqrt(-2.25)` is `Complex(Real 0, Real 1.5)`). A `Real` root is
  `BigDecimal.sqrt(mc)`: correctly rounded under the half-way modes, and within one ulp under the
  directed ones (the JDK's specification). The root of a non-square `Rational`, an inexact
  complex root and an inexact complex `modulus` are faithful (within one ulp), not correctly
  rounded.
- **Real root** `RealNumeric.root(n, mc)`, for `n ≥ 1`: exact for an exact perfect `n`-th power
  (`root(8/27, 3)` is `2/3`). A `Real` that is an exact decimal `n`-th power has its exact root,
  rounded once (`root(1e-49, 7)` is `1E-7` in every rounding mode, and `root(8.0, 3)` is `2.0`
  even under `UNLIMITED`). Otherwise the root is a `Real` computed as `exp(log(x)/n)` through the
  elementary functions' agreement loop: faithful, at a cost independent of `n`. The perfect-power
  test makes no search: a 60-digit `exp(log(x)/n)` seeds Newton's method on integers at doubling
  precision, and the one candidate and its two neighbours are checked. A negative value has the
  negative real root for odd `n` (`root(-8, 3)` is `Int -2`), and throws `ArithmeticException`
  for even `n`. This is what the language's `cbrt` and `root(x, n)` call; callers wanting the
  complex value use `pow`.
- **Parts** `re`, `im` (`Int 0` for a real number), `conj`; `modulus(mc)` (exact when a
  rational square: `|3+4i|` is `5`; otherwise faithful, since the squares are summed a few digits
  beyond the context before the square root); `argument(mc)` in `(-π, π]`. The argument of a
  non-negative real is 0 (`Int 0` when exact; that of zero included), and of a negative real `π`.
- **Order** on `RealNumeric`. `compareTo` is the total order used for canonical ordering: by
  value, exactly across kinds, and at equal value an exact number before a `Real` (`1 < 1.0`),
  so it is consistent with `equals`. `compareValue` is the mathematical comparison used by
  relations: by value alone, so `1` and `1.0` compare equal. `Complex` is not ordered.
- **Rounding** on `RealNumeric`: `floor`, `ceil`, `round` (halves away from zero: `round(-2.5)`
  is `-3`), `truncate`. A `Real` stays a `Real`.
- **Integers** (`Int`): `gcd` (`gcd(0, 0)` is 0), `lcm`, `mod` (the sign follows the divisor, as
  in `Math.floorMod`: `mod(-7, 3)` is 2), `factorial` (binary splitting), `binomial` (the falling
  factorial over `k!` by binary splitting, so defined for negative `n`, where it is
  `(-1)^k C(k - n - 1, k)`; 0 for `k < 0`), `isqrt`. Primality is in the number-theory package (**Number theory**). `Int` and `Rational` have `numerator` and `denominator`, and
  `RealNumeric` has `isPerfectSquare` (false for a `Real`).

### Elementary functions

`Elementary` gives `exp`, `log`, `sin`, `cos`, `tan`, `atan`, `asin`, `acos`, `sinh`, `cosh`,
`tanh`, `pi` and `e` on any `Numeric`, in `BigDecimal` end to end (no Hipparchus, no `double`).
They serve explicit numeric requests only, never the simplifier. An exact argument is converted,
and every result is inexact (`exp(0)` is `Real 1`): exact special values are the rules' job.

The real kernels (`BigDecimalMath`) follow Brent and Zimmermann, *Modern Computer Arithmetic*,
chapter 4, and Muller, *Elementary Functions*, chapters 6 and 11:

- `exp` scales its argument by `2^-k` (exact in decimal), sums the Taylor series, then squares
  `k` times;
- `log` reduces by powers of 10 and 2 and sums `2 atanh((x-1)/(x+1))`;
- `sin` and `cos` reduce modulo `π/2` with `π` carried to the argument's integer digits beyond
  the working precision;
- `atan` uses the reciprocal identity and two halvings;
- `π` comes from Machin's formula.

Complex values use the textbook formulas through the tower's own arithmetic.

**Accuracy.** Real results are faithfully rounded: the error is below one ulp at the context's
precision `p`, and the context's rounding mode is honoured only within that bound. Each part of a
complex result is within one ulp of itself, except a part smaller than `10^-p` times the larger
part, which is within one ulp of `10^-p` times the larger part. The bound comes from Ziv's
strategy: the function is evaluated at `p + 10` digits and at a rising working precision, and the
result is accepted when two evaluations agree to a thousandth of the bound. The agreement test is
a heuristic: strong evidence, not a proof. **The working precision is capped at `4p + 400`
digits**. An evaluation that has not converged there throws `ArithmeticException`, for instance
`sin` at 34 digits of `π` given to 1000 digits.

The reduction of `sin` and `cos` modulo `π/2` adds the angle's integer digits on top of the
working precision. **An angle whose decimal exponent exceeds the cap `4p + 400` is refused** with
`ArithmeticException` ("argument too large to reduce at this precision"), so `sin(1e10000)` fails
at once rather than computing `π` to ten thousand digits. This covers:

- the real argument of `sin`, `cos` and `tan`;
- the real part of a complex argument of `sin` and `cos`, and `2·re` for `tan`;
- the imaginary part of a complex argument of `exp`, `sinh` and `cosh`, and `2·im` for `tanh`;
- the angle of `exp(y log x)` in a general power.

Likewise `exp` checks its argument first: beyond `|x| > 4.95e9` the result leaves `BigDecimal`'s
exponent range, and it throws at once.

**Domains and branch cuts.** A real argument outside the real domain gives the principal complex
value. Without signed zeros the value on a cut must be fixed by convention, and the engine uses
counterclockwise continuity (Kahan, 1987; *Common Lisp the Language*, 2nd ed., §12.5.3), as
Mathematica does:

- `log` is cut along the negative real axis and continuous with quadrant II there, so
  `log(-1)` is `πi` and `arg` lies in `(-π, π]`.
- `asin` and `acos` are cut along the real axis outside `[-1, 1]`, continuous with quadrant II
  on the left and quadrant IV on the right. So **`asin(2)` is `π/2 - 1.3169578969…i`**, and
  `acos(2)` is `1.3169578969…i`. C99's `casin(2 + 0i)` gives `π/2 + 1.3169…i` instead: the C
  library takes the upper side for a `+0` imaginary part.
- `atan` is cut along the imaginary axis outside `[-i, i]`, continuous with quadrant III below
  and quadrant I above, so `atan(2i)` is `π/2 + 0.5493…i`.

`log 0`, `atan(±i)`, a pole of `tan` or `tanh`, and a result outside `BigDecimal`'s exponent
range (`exp(1e10)`) throw `ArithmeticException`.

**Cancellation.** Each function has an overload taking a `Cancellation` (**Cancellation** under
**Evaluation**). The agreement loop checks it at every iteration, and the series kernels
(`BigDecimalMath`, a record of the token) at every term and every squaring of `exp`, so a request
stops an evaluation at thousands of digits within a term: one iteration of the loop at 10,000
digits takes tens of seconds (`numeric(pi, 10000)` took 60 s), one term milliseconds at most.
`Numeric.pow`, `RealNumeric.root` and `Numeric.argument` have overloads that pass it to their
inexact paths, and `Canonical.of(e, mc, cancellation)` to the numeric power fold. The overloads
without a token run under `Cancellation.NEVER`.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/number/`. There is a unit test per
kind (`IntTest`, `RationalTest`, `RealTest`, `ComplexTest`), plus `PowersTest` and
`ElementaryTest`, the latter against published digits at 50 and 100 places. The jqwik properties
use the shared generators in `NumberArbitraries`: small values dominate, so that ties are common;
values run to 100 digits; and neighbour pairs give the same value built another way, the same
value in another kind, and adjacent values. The properties are:

- `ArithmeticProperties`: the ring axioms, single rounding (also past the magnitude gap and at
  ties), and agreement with `double`;
- `ComplexProperties`: each part of a complex product and quotient against the exact value
  rounded once, cancelling pairs included; quotients by far-apart divisors within a faithful bound
  in every rounding mode; `z.pow(2) == z·z`; faithful roots of rationals; and Pascal's rule
  for `binomial`;
- `ComplexQuotientBoundaryProperties` (20,000 tries, a few seconds): quotients by far-apart
  divisors whose exact value lies within `10⁻³⁰` of a power of ten, in every rounding mode, each
  part equal to the exact numerator over the exact norm divided once as decimals;
- `OrderProperties`: the order's consistency, transitivity and tie-break;
- `DemotionProperties`: normal form over random operation sequences, and `root(x^n, n) = x`;
- `ElementaryProperties`: every function at 34 digits against 54 digits within one ulp, against
  `java.lang.Math`, and the classical identities within their conditioning bounds.

## Expressions

Package `mathcanvas.engine.expr`: the expression tree, the total order and the canonical form.
Parsing and printing are not here; `toString` gives a debug form such as `Sum[Num 1, Symbol x]`.

### Node kinds

`Expr` is a sealed interface over one immutable record per kind. Code over expressions uses an
exhaustive pattern-matching `switch` with no `default`.

| Kind | Fields | Notes |
|---|---|---|
| `Num` | `Numeric value` | a number of the tower; `Num(Int 1)` and `Num(Real 1.0)` differ |
| `Based` | `Int value`, `int base` | an integer shown in base 2 to 36 but 10 (**Bases**); a display of its integer, not `equals` to it |
| `Constant` | `Kind kind` | `PI`, `E`, `INFINITY`, `UNDEFINED`, and the domains `INTEGERS`, `RATIONALS`, `REALS`, `COMPLEXES` |
| `Bool` | `boolean value` | a decided relation |
| `Symbol` | `String name` | a variable or a function head |
| `Unit` | `String name`, `int rank` | a unit of measurement (**Units**): the normalised ASCII name (`km`, `um`, `kohm`) and its place in the order, both given by the unit table |
| `Sum`, `Product` | `List<Expr>` | n-ary, at least two operands |
| `Power` | `base`, `exponent` | |
| `Apply` | `Symbol head`, `List<Expr> args` | functions and commands share one namespace |
| `Lambda` | `List<Symbol> params`, `body` | parameters distinct; equality is structural, so `x -> x` ≠ `y -> y` |
| `Relation` | `Op op`, `left`, `right` | `EQ`, `NE`, `LT`, `LE`, `GT`, `GE`, `IN` |
| `ListExpr`, `Vector`, `SetExpr` | `List<Expr> items` | a vector has at least one item |
| `Matrix` | `List<List<Expr>> rows` | rectangular, non-empty |
| `Interval` | `lower`, `upper`, two closedness flags | |
| `Piecewise` | `List<Piece> pieces`, `otherwise` | at least one `Piece(value, condition)` |
| `Derivative` | `expr`, `Symbol var`, `int order` | order at least 1 |
| `Integral` | `integrand`, `Symbol var`, `lower`, `upper` | both bounds null (indefinite) or both present |
| `Limit` | `expr`, `Symbol var`, `point`, `Direction` | `BOTH`, `LEFT`, `RIGHT` |
| `BigSum`, `BigProduct` | `body`, `Symbol var`, `from`, `to` | Σ and Π |

The imaginary unit is `Num(Complex.I)`, not a constant; negative infinity is
`Product(-1, ∞)`. There is no subtraction, division or unary-minus node: `a - b` is
`Sum(a, Product(-1, b))`, `a / b` is `Product(a, Power(b, -1))`, `-x` is `Product(-1, x)`. `Exprs`
builds these shapes (`neg`, `sub`, `div`, `inv`, `sqrt` as `Power(e, 1/2)`). `Integral` is built
with `Integral.indefinite` or `Integral.definite` and tested with `isDefinite()`.

**Raw nodes.** The records are plain. A factory validates its arguments and builds the node as
given; nothing is flattened, sorted or collected on construction, so the parser can produce
exactly what was typed (`Sum.of(x)` is an error, not `x`). Whether a node is canonical is not
stored on it; callers canonicalise at their boundaries.

**Equality.** Records compare structurally, and on canonical forms that is mathematical equality
for a large class of expressions. There is no intern table (`PLAN.md` §11): `hashCode` is
recomputed on each call, in time proportional to the tree. The canonical form therefore never
hashes; code that keys a hash table on large trees should hash at its own level.

### The order

`ExprOrder.INSTANCE` is a total order consistent with `equals`, used by the canonical form and,
later, by commutative matching. It follows Cohen's order (*Computer Algebra and Symbolic
Computation: Mathematical Methods*, §3.1), so like terms sit together:
`x < 2x < x² < y < x·y`. The first applicable step decides:

1. **Numbers** come before everything. Reals compare by the tower's `compareTo` (exact before
   inexact on a tie); a complex number comes after every real, and complex numbers compare by
   real part, then imaginary part. An integer in a base sorts as its integer, after the integer
   itself, then by base.
2. If either side is a **product**, the operand lists compare from the last operand backwards,
   and a list that runs out first comes first; a non-product `v` counts as `[v]`.
3. Otherwise, if either side is a **power**, bases compare, then exponents; a non-power `v`
   counts as `v^1` and comes before the power it ties with (`x < x^1`).
4. Otherwise, if either side is a **sum**, as for products.
5. Otherwise by kind. Constants come first, in `Kind` order. Symbols and applications sort
   together by name, a symbol before an application of the same name, and two applications by
   their arguments from the left, fewer first (`f < f(x) < f(x, y) < g`). Then units, by their
   rank and then their name, so a quantity's units follow its other factors. Then the other kinds
   in the order derivative, integral (indefinite first), limit, Σ, Π, lambda, relation,
   interval, piecewise, vector, matrix, list, set, truth value, each compared field by field.

Each mixed step compares a strict part of one side with the whole of the other, which keeps the
order transitive; the properties check it on raw and canonical trees. It uses no hash and no
identity, so it is the same on every run.

### The canonical form

`Canonical.of(e)` and `Canonical.of(e, mc)` return the canonical form: Cohen's automatic
simplification (§3.2). It is the one transformation in the engine that is not a named rule:
structural, untraced, and limited to the list below. Distribution, expansion, radicals,
trigonometry, logarithms and the evaluation of `Apply` are rule sets or the evaluator's work,
and this list does not grow.

- **Bottom-up.** Operands are canonicalised first. Nested sums and products are flattened with
  an explicit stack, so a long left-nested raw chain does not recurse once per operand.
- **Numbers.** The numbers of a sum or product fold through the tower in sorted order, so the
  result does not depend on the operand order; a `Real` result is rounded to `mc`, which is
  `Canonical.DEFAULT_CONTEXT` (`MathContext.DECIMAL128`, 34 digits) in the overload without one.
- **Sum.** Like terms combine by their non-numeric part: a term `Product(c, f…)` with a number
  `c` is `c` times `f…`, any other term is 1 times itself (`2x + 3x` is `5x`, `x + x` is `2x`,
  `x - x` disappears). The number goes first; an exact zero is dropped, except a zero
  coefficient on a non-commuting part, which stays as the zero of that shape (`A - A` is `0·A`);
  one term is unwrapped, none is `0`; the terms are sorted by the order.
- **Product.** Factors with equal bases combine by adding exponents (`x·x` is `x²`, `x·x⁻¹` is
  1, `xᵃ·xᵇ` is `x^(a+b)`); a number and a power of that number stay apart (`2·2ˣ`). The
  coefficient goes first and `-1` is kept as one (`-x`); an exact 1 is dropped; an exact zero
  coefficient makes the product `0`; one factor is unwrapped, none is `1`. **Vectors, matrices,
  and sums, products and powers built from them, do not commute**: they follow the sorted scalar
  factors in their typed order and never combine (`B·A` stays `B·A`, `A·A` stays, `B·(1 + A)`
  stays). With non-commuting factors an exact zero coefficient does not make the product `0`:
  the commuting factors are dropped and `0` times the non-commuting ones stays, the zero vector
  or matrix of their shape (`0·x·A` is `0·A`), after the infinity rule below.
- **Power.** `x¹ = x`; `x⁰ = 1`, also for `x = 0`, except for a non-commuting `x` (a vector or a
  matrix, or arithmetic built from them), whose `x⁰` stays as the identity of its shape, as `0·A`
  stays as its zero; `1ˣ = 1` (for a number `x`, when it is
  exact: `1^i` is 1, `1^2.5` is `1.0`); `(xᵃ)ⁿ = x^(a·n)` and
  `(x·y)ⁿ = xⁿ·yⁿ` only for an exact integer `n` (the second only when every factor commutes).
  A power of two numbers folds when the result is exact or an operand is already inexact:
  `4^(1/2)` is 2, `(-4)^(1/2)` is `2i`, `2.0^(1/2)` is a `Real`, while `2^(1/2)` and
  `(-8)^(1/3)` stay. So does a power of `e` or `π` beside an inexact number, at the context's
  precision: `e` to a decimal is `exp` of it (`e^(2.0)` is `exp(2.0)` to the last digit), and `π`
  to a decimal or a decimal to `e` or `π` is computed with `π` or `e` carried ten digits beyond the
  context, so it rounds once (`π^(0.5)`, `2.0^π`); a power past the tower's range, and any power
  under an unlimited context, stays. Exact operands are probed with `MathContext.UNLIMITED`, under which the tower
  either answers exactly or throws, and a throw leaves the power. An exact integer power folds
  only while the base's bit length (for a rational, the numerator's plus the denominator's)
  times `|n|` is at most `Canonical.MAX_FOLD_BITS` (2²⁰), except the inverse `n⁻¹`, which folds
  whatever the size of `n`, since forming `1/n` costs nothing, and
  an exact `p/q` power only when `q` does not exceed the base's bit length (a larger `q` cannot
  give an exact root); a `Real` base has no limit. A zero base follows the tower: `0ⁿ` is 0 when
  `Re n > 0`, and undefined for any other non-zero number `n` (`0⁻¹`, `0ⁱ`); `0ˣ` with a
  symbolic `x` stays.
- **Infinity and undefined**, the exceptions to the identities above. An `UNDEFINED` operand
  makes a sum, product or power `UNDEFINED`. A cancellation to an exact zero over a part built
  by sums, products and powers from `∞` or `UNDEFINED` is `UNDEFINED`: `∞ - ∞`, `∞·∞⁻¹`,
  `0·∞`, `0·(1 + ∞)`, `0·∞·A`. So are `∞⁰` and `1^∞`. Otherwise infinity is carried, not evaluated
  (`∞ + ∞` is `2∞`, `∞ + 1` stays), and an application is opaque (`f(∞) - f(∞)` is 0).
- **Exactness.** Every identity tests exactness as well as value, as the tower requires: `x +
  0.0`, `1.0·x`, `x^1.0` and `x^0.0` stay, and `2x - 2.0x` is `0.0·x`.
- **Relation.** Both sides are canonicalised; terms never move across. A relation between two
  numbers becomes a `Bool` by value (`1 = 1.0` is true), except an ordering with a complex
  number, which stays for the evaluator to reject. A number against `∞` or `-∞` is decided the
  same way. Membership (`IN`) is never decided here.
- **Set.** Items are canonicalised, sorted and deduplicated by the order, so `{1, 1.0}` keeps both.
- **Boolean operators** (Phase 8a). `and`, `or` and `xor` are associative and commutative: nested
  applications of the same operator are flattened and the operands sorted by the order; with one
  operand the application is that operand, with none its identity (`true`, `false`, `false`). A
  repeated operand stays, for the `logic` rule set to remove with its step. Two truth values are
  equal or not (`true = false` is `false`).
- **Integers in a base** (`Based`): replaced by their integer under every parent but a list, a
  vector or a matrix, and kept at the root, so the base never propagates (`hex(255) + 1` is 256,
  `{hex(1), 1}` is `{1}`) while a value or a vector of them keeps it.
- **Everything else** (lists, vectors, matrices, intervals, piecewise expressions, lambdas,
  applications, the unevaluated forms) keeps its structure with its operands canonicalised.
  Nothing is type-checked: a `Bool` inside a sum is carried as it is.

**Display-only values** are kept at the top of a statement and in lists, vectors and matrices and
are their plain value in every other use, a set's items included (so equal items are one); the
canonical form keeps them as they are. `ifactor`'s product is the application `factored(sign, p₁,
e₁, …)` (**Number theory**): the printers write it as `2³ · 3² · 5`, and the evaluator puts its
integer (`NumberTheory.plain`, which also looks into the items of lists, sets, vectors and matrices)
into the operands of arithmetic, the sides of a relation, the arguments of a builtin function, and
the argument of `numeric` and `exact`.

The result is idempotent: `Canonical.of(Canonical.of(e))` equals `Canonical.of(e)`. It is one
pass over the tree, not an iteration of the whole to a fixpoint; within a sum or a product the
grouping step reruns while a combination yields a new sum or product (a coefficient of 1 on a
sum, `(x·y)^(1/2)·(x·y)^(1/2)`) or leaves two terms with one non-numeric part (`0·M + 0·M`) or
two factors with one base (`(x⁻¹)^(1/2)·(x⁻¹)^(1/2)·x`, whose first group collapses to `x⁻¹`).
It ends because each rerun merges groups or removes a level of nesting.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/expr/`: `ExprTest` (validation and the
debug forms), `ExprsTest`, `ExprOrderTest` (Cohen's examples and the mixed-kind rules, and a fixed
list sorted to a literal sequence), `CanonicalTest` (each rule) and `CanonicalPerformanceTest` (a
sum of 10,000 symbols, flat and left-nested, and products of 1,000 powers, against a generous bound;
each takes milliseconds). `ExprArbitraries` is the shared generator of raw expressions, for later
packages too: symbols from `a b x y`, numbers from a small pool, depth up to 4, with twins such as
`e - e`, `e·e⁻¹` and `(eᵃ)ⁿ` frequent; `exactExprs()` has exact numbers and arithmetic only,
`exprs()` every kind, and `elementaryExprs()` adds to `exactExprs()` the builtin functions, numeric
radicals and rule-shaped twins the simplifier works on, and `trigExprs()` adds to that the circular,
hyperbolic and inverse functions, rational multiples of `π`, shifts by them and by `πk`, and twins
for the trigonometric rules (`c·sin²e + c·cos²e`, products of powers of the six functions of one
argument, `f(-e)`, `asin(sin e)`, inverse functions of special values); `rationalExprs()` builds
quotients and sums of quotients of polynomials in `x` and `y` (linear factors `x - r` with `r` in
`RATIONAL_ROOTS`, `x² + 1`, `x + y`, and the kernel factors `log(x) - 1` and `sin(x) + 2`, some
multiplied out), with shared factors (`p·q/(p·r)`) and related denominators, and sums over powers of
degree 40 to 69 too large to combine, for the rational rule set, and `rationalExprsWithContents()`
gives half the factors a rational content from `{1/2, 1/3, 2, 3, 6}`, multiplied out (`x/2 - 1/2`).
It draws from jqwik's random source directly, because jqwik's combinators were orders of magnitude
slower on these trees, so failures are reported unshrunk. The properties are:

- `ExprOrderProperties`: consistency with `equals`, antisymmetry and transitivity, on raw and
  canonical trees;
- `CanonicalProperties`: exact soundness at random points through the test-only `TestEvaluator`
  (symbols, `π` and `e` take random exact values; a point where either side is undefined or not
  exact is skipped, since the tower recognises exact powers only in its documented shapes and a
  combined exponent can hide one; the statistics require most samples to be compared), idempotence,
  independence from operand order, and the shape of every node (at least two operands, none of
  the same kind, at most one number and it first, operands sorted, no `x^1` or `x^0`).

## Patterns and rules

Packages `mathcanvas.engine.match` (patterns, the matcher, bindings, the operand index scheme)
and `mathcanvas.engine.rule` (rules, rule sets, the rewriter, the trace, narration). Every
simplification rule, derivative rule and integration-table rule is written against them; the
shape is Rubi's (*pattern → replacement, if condition*), so that rule set could be carried later
(`PLAN.md` §14).

### Operands

`Operands` fixes one index scheme for the operands of every node, used by the matcher, by
substitution, by the rewriter's traversal and by trace paths:

| Kind | Operands |
|---|---|
| `Num`, `Constant`, `Bool`, `Symbol` | none |
| `Sum`, `Product` | the terms, the factors |
| `Power` | base, exponent |
| `Apply` | the arguments (the head is not an operand) |
| `Relation`, `Interval` | left and right side; lower and upper end |
| `ListExpr`, `Vector`, `SetExpr` | the items |
| `Matrix` | the entries, row by row |
| `Piecewise` | each piece's value then condition, then `otherwise` |
| `Lambda` | the parameters, then the body |
| `Derivative` | expression, variable |
| `Integral` | integrand, variable, then the bounds when definite |
| `Limit` | expression, variable, point |
| `BigSum`, `BigProduct` | body, index, first and last value |

The **bound positions** are a lambda's parameters and the variable (index 1) of a derivative,
integral, limit, Σ or Π. `Operands.rebuild` replaces the operands of a node raw, keeping its kind
and data; a bound position takes only a symbol.

### Patterns

A pattern is written in the language: `Pattern.parse("a_ x_^n_")` reads the text with the
parser in the empty environment, where the variables are ordinary identifiers, and
`Pattern.of(e)` compiles a tree built by hand. A name followed by `(` is a call only for a builtin
(**Parsing**), so `f(x_)` parses as `f·x_` and a variable head such as `h_(u_)` is built as a tree
for `Pattern.of`; `sqrt(x_^2)` parses to `(x_²)^(1/2)`. A parse error is the parser's
`ParseException`. A symbol whose name ends in `_` is a **pattern
variable** (`x_`), in `__` a **sequence variable** matching one or more operands (`rest__`), in
`___` one matching zero or more (`rest___`). A name of underscores alone is rejected. Everything
else matches by equality, so `2` and `2.0` differ. The expression is **canonicalised first**, so
the literal parts are in the form subjects are in (`x_ - 1` is `Sum(-1, x_)`, `x_/2` is
`(1/2)·x_`), and "left to right" means the order of the canonical pattern.

The compiled tree is the sealed `PatternNode`: `Literal`, `Var`, `Seq`, `SumP` and `ProductP`
(the operands, and at most one sequence variable), `PowerP`, `ApplyP` (the head a literal symbol,
or a variable that binds the head symbol, so `h_(u_)` matches `sin(x)` with `h_ = sin`), and
`Structural` for every other kind (the same kind and data, operands positionally). A sequence
variable may stand only as a direct operand of a sum or product (one there) or as an argument; a
sequence variable may not occur twice. A plain variable may repeat: every occurrence must match
an equal expression.

**Defaults.** `pattern.optional("a_", 1)` gives a variable a default, bound when the variable has
no operand to match. The variable must occur once, as a direct operand of a sum or product
pattern or as the exponent of a power pattern. So `a_·x_` with `a_` defaulting to 1 matches `x`,
`x_^n_` with `n_` defaulting to 1 matches `x`, and Rubi's `(a_. + b_. x_)^m_.` matches `x`.

**Conditions.** `where("n_", e -> …)` tests one variable's value (a sequence variable's combined
value); `where(List.of("a_", "b_"), (bindings, assumptions) -> …)` tests several; `where((bindings,
assumptions) -> …)` tests the complete match. The first two run as soon as their variables are
bound, which prunes the search; the third when the match is complete. Conditions see the
assumptions of the match (**Assumptions**).

### Matching

`Matcher.match(pattern, subject[, assumptions])` returns the first match's `Bindings`;
`Matcher.all` enumerates every match lazily, in the same deterministic order. Subjects are
canonical.

- **Sums and products** match commutatively; associativity enters only through sequence variables. A
  pattern variable binds exactly one term or factor, and the sequence variable binds the rest. The
  subject's terms or factors are the candidates; a subject of another kind counts as its only
  operand, which is how `a_·x_` matches `x` and `x_ + rest___` matches `y`. Each pattern operand,
  left to right, takes a distinct candidate, trying the candidates in canonical order and then, for
  an optional variable, its default. The sequence variable takes what is left; with no sequence
  variable nothing may be left, so `x_ + y_` does not match `a + b + c`, and `(a_. + b_. x_)^m_.`
  does not match `(1 + y + 2x)`. `all` therefore lists `x_ + y_` against `a + b` as `{x_ = a, y_ =
  b}` then `{x_ = b, y_ = a}`.
- A sequence variable binds the **combination** of its operands: their sum or product, the single
  operand alone, or `0` or `1` when `___` takes none. An ordered subset of a canonical operand list
  is canonical, so the combination is built directly. `Bindings.sequence(name)` gives the operands.
- **Factors that do not commute** (vectors, matrices, and sums, products and powers built from
  them) keep their order. With one such factor it is a candidate like the others; with two or
  more they are not candidates, and only the sequence variable may take them, together and in
  order, or the match fails.
- **Powers**: base and exponent; a power pattern whose exponent has a default matches the
  subject's base and exponent first, then the whole subject with the default exponent.
- **Arguments** and the operands of every other kind match positionally; a sequence variable among
  arguments tries its shortest length first and binds a `ListExpr` of the arguments.

**Flat binding is a later extension.** A named variable that takes a non-empty sub-sum or
sub-product (Mathematica's `Flat` and `OneIdentity`, so that `a_ + b_ x_` matches `1 + y + 2x`
with `a_ = 1 + y`) is not supported. Rubi-shaped integration rules need it; it will be added as an
explicit option per variable when the integration tables require it, leaving the one-operand
semantics above the default.

**Cost.** Before each step the count of candidates left is checked against the operands still
required, and a pattern without a sequence variable is rejected at once when the subject has more
operands than it. A literal operand, or a variable already bound, has one possible candidate and is
located by binary search in the sorted candidates; every other operand is tried only on candidates
of a kind it can match (a power pattern on powers, `f(…)` on applications of `f` with a fitting
arity). A product of twenty factors against a three-operand pattern matches in well under a
millisecond, and a thousand-term sum against `a_ + rest___` in a few milliseconds
(`MatcherPerformanceTest` times 301 matches of each as one run). The leftover operands of a sequence
variable are counted before anything is built, and its combination is built only when a binding is
produced. The weak spot is several **unconstrained** variables over a long subject when the match
then fails: `x_ + y_ + rest___` against a sum of a thousand terms with a condition that rejects
every match tries about 10⁶ assignments, each building a 998-term `rest___`, and takes about two
seconds. Rules keep such patterns guarded by a literal, a kind or a condition on the first variable.

### Bindings and templates

`Bindings` is an immutable map from variable name to value, sorted by name, so bindings with the
same values are equal whatever the order of binding. `substituteInto(template)` instantiates a
template: each symbol ending in `_` is replaced by its value. A sequence variable that is a direct
term of a sum, factor of a product or argument of an application **splices** its operands in when
they came from the same kind (terms into a sum, factors into a product, arguments into an argument
list), and anywhere else stands for its combination, so `2·rest___` with `rest___` taken from a sum
is `2·(b + c)`, not `2·b·c`. A sum or product left with one operand is that operand, with none `0`
or `1`. The result is raw; the rewriter canonicalises it. An unbound variable, or a non-symbol in a
bound position or a head, throws `IllegalArgumentException`.

### Rules and rule sets

`Rule(name, pattern, replacement)`: the name is kebab-case and unique within its rule set, and the
trace and the narration bundle use it. The replacement is a `Template` (every variable it uses must
be the pattern's) or a `Computed` function of the `Match` (bindings, subject, context) returning
the right-hand side, or empty when the rule does not apply after all; it computes only the
right-hand side. A `Valued` replacement (`Rule.valued(name, pattern, names, function)`) is a
computed one that also returns named values for its narration (`Valued.Result(expr, values)`), such
as the gcd a cancellation divides out, which is no pattern binding: the names are declared with the
rule, each a lowercase word (`[a-z][a-z0-9]*`, so never a pattern variable, which ends in `_`) other
than `before`, `after` and `list`, and a value for a name the rule did not declare is an
`IllegalStateException` at the rewrite. `RuleSet(name, rules)` is ordered by priority.

### The rewriter

`Rewriter.rewrite(e, ruleSet, context)` returns the result and its trace. `Context` holds the
assumptions, the precision (`MathContext`, also used to canonicalise), whether to trace, the
rewrite cap, the providers of the heavyweight operations (**Providers**), and the `Numerics`: the
numeric value of a closed expression at a precision (`value(e, mc)`, empty where it has none) and
the session's half turn in its angle unit (`halfTurn()`: 180, 200, or 0 in radians, with
`radians()` true at 0), for the operations that need values (the numeric fallback of **Solving**)
or the angle unit (the `trig` rules), and the builtin functions of numbers evaluated as evaluation
does, in radians (`exact(e, mc)`: `sinh(0)` is 0, `floor(5/2)` is 2; by default nothing is), for the
operations that put a number into an expression (`taylor` and **Limits**), and the `Cancellation`, the request to stop (**Cancellation** under
**Evaluation**). `Context.DEFAULT` is no assumptions, 34 digits, no trace, 10,000 rewrites,
`Providers.defaults()`, `Numerics.NONE`, under which every value is empty, and
`Cancellation.NEVER`. The four-argument constructor takes the default providers, it and the
five-argument one take `Numerics.NONE`, and they and the six-argument one take
`Cancellation.NEVER`; every `with…` method keeps the token, and `withCancellation` replaces it. The
evaluator supplies its own numerics and the statement's token (**Evaluation**).

1. The input is canonicalised (untraced).
2. At each node, the operands are visited first, in index order, except the bound positions; the
   children of unevaluated forms and collections are visited like any other operand.
3. If an operand changed, the node is rebuilt and canonicalised, and its operands are visited
   again until nothing changes.
4. The rules are tried in order, each on its **first match only**: the replacement is instantiated
   or computed and canonicalised. An empty computed replacement, or a result equal to the node,
   means the rule does not apply there, and the next rule is tried. Otherwise the result replaces
   the node, the rewrite is counted, and the visit **restarts at that node** (step 2).
5. The node is done when no rule applies; the whole call ends at the fixpoint.

The cancellation is checked at every node visit (each pass of step 2) and before each rule is tried
in step 4, and the canonical form is taken with it (`Canonical.of(e, mc, cancellation)`), so a
request stops the rewrite within one rule attempt. The matcher's own backtracking does not check
it: one commutative match is the residual, milliseconds on the largest inputs of the performance
tests.

The rewrite after the cap's last allowed one throws `RewriteLimitException`, naming the rule set,
the rule and its path: a rule set that can loop is a bug.

**The rule index.** Only the rules whose pattern can match a node are tried there, in their order.
Per call, and lazily, the rules are grouped by the kind of node and, for an application, the head
their pattern's root can match; a head variable matches any application, a root that is a
variable or a power pattern with a default exponent is in every group, and a sum or product
pattern is in its own kind's group and in another's only when it can take such a node as its only
operand (every operand but at most one can take nothing, and that one can match the node), so
`d___ * sin(u_)^2 + rest__` is tried on sums alone and `h_(u_)^n_ * rest___` also on powers and
applications. The grouping skips only rules that would fail at the root, so the result and the
trace are those of trying every rule (`RewriterIndexProperties` compares the two on random
expressions with every rule set, and with random subsets of them joined by edge rules whose roots
take every branch of `canMatch`: a variable, literal applications, numbers and constants, a
variable head, a power with a literal or a defaulted exponent, a sum and a product with a
defaulted operand whose remaining operand is a variable or a power, and the structural shapes; a
narrowed branch fails it). With the standard list, simplifying a value of 900,000 nodes that no
rule changes went from about 1.3
seconds to 0.16.

**Binders.** Inside the body of a binder (a lambda's body, the expression of a derivative or
limit, an integrand, the body of a Σ or Π) the facts about the symbols it binds are dropped
(`Assumptions.without`), because there the symbol is the binder's own: under `x ≥ 0`,
`∫₋₁¹ √(x²) dx`, `Σ[x = -3, 3] √(x²)` and `x -> √(x²)` keep their `√(x²)`. The bounds of a Σ, Π
or definite integral and a limit's point are outside that scope and keep the outer facts. Taking
the range of a definite integral or sum as a fact about its variable inside the body is a later
refinement.

### The trace

With tracing on, each rewrite is a `Rewrite(before, after, ruleSet, rule, path, bindings, values)`:
`before` and `after` are the subexpressions at `path`, a `Path` of operand indices from the root, and
`values` are a valued rule's named values, canonical at the context's precision, kept apart from the
bindings (empty for every other rule; a six-argument constructor gives none).
A path addresses the tree as it stood when the rule fired: operands are rewritten before their
parent is canonicalised, so the ancestors are rebuilt raw around them, and each canonicalisation of
a rebuilt node is recorded as a `Trace.Normalisation` between the rewrites. The trace stores only
these events and the initial expression; `roots()` replays them once to give the whole expression
after each rewrite, sharing every unchanged subtree, and `Canonical.of` of a root is its display
form. With tracing off the result carries `Trace.NONE` and nothing is allocated per rewrite.
`Trace.concat(first, second)` joins the traces of two operations run one after the other (an
operation and the simplifier after it): the second must start from the first's result, and a
normalisation at the root between them keeps the replay exact.

Every rewrite changes its node, with one exception: a **terminal note**, the last record of a
trace or of its operation's part of it (only the numeric path's `numeric.*` records may follow, taking
over the integral the exact path left), may leave its node as it is, so an operation that cannot finish says why (`integrate.none`,
`integrate.angle-mode`, `integrate.singular`, `integrate.improper`, the other operations' `none`
steps, `limit.numeric`, `numeric.digits`, `decomposition.symbolic`, `dsolve.unsolved`,
`dsolve.unverified` and `dsolve.unmet`). No other record is
a no-change rewrite; the tests' `TraceChecks.assertChain` accepts exactly that.

### Narration

`Narrator.narrate(trace, context)` turns a trace into `Step(text, before, after, rewrites)`.
`DefaultNarrator` gives one step per rewrite, its text from the bundle
`mathcanvas.engine.rule.narration` (in the engine's resources, root locale): the key is
`<rule set>.<rule>`, the value a `MessageFormat` pattern whose arguments `{0}`, `{1}`, … are the
rule's bound variables **in alphabetical order of their names**, printed with the Unicode
printer's display form (**Printing**). The placeholders `{before}` and `{after}` are the rewrite's
before and after, printed the same way (`Expand (x + 1)² by the binomial theorem`), and `{name}`
is the value of that name from a valued rule (`Cancel the common factor {gcd}: {before} = {after}`);
a text that names a placeholder with no value is an `IllegalArgumentException` naming it. A rule
with no key is narrated by its name. Another bundle or printer can be passed to the record's constructor.
`StepText` holds the bundle and the printer and formats every narrator's texts, a rewrite's or a
grouped step's, and joins their pieces through the keys `step.list` (the items of a list) and
`step.sentences` (sentences on one line), so another locale can change the separators too; `Replay`
replays a trace once and gives the whole expression before and after each rewrite, at each
boundary between rewrites, and the subexpression at a path.

Each curated operation has a narrator beside its rules, which groups its rewrites into steps of
one idea each. `eval.Narration.steps(result, context)` splits a statement's trace into the maximal
runs of rewrites one narrator owns, by the rewrite's rule set, narrates each run with its owner
(`DefaultNarrator` for a run nobody owns, such as the `provider` steps) and concatenates the
steps. Every step stands for a contiguous run of the trace's rewrites, none is dropped, and a
step's before and after are the whole expression, canonical, at the boundaries around its run: the
rewriter's own tree after the rewrites before it and the normalisations that follow them
(`Replay.boundary`; after the last step, the result). The boundaries are the rewriter's normalised
trees rather than the raw tree canonicalised whole because the two can settle on different
canonical shapes of one value (`2^(3/2)/20` and `√2/10`), and each step must start exactly where
the one before it ended. A normalisation is no step: the step before it shows it. The grouped
steps' keys are `<operation>.step.<name>`:

| Narrator | Owns | Groups |
|---|---|---|
| `simplify.SimplifyNarrator` | `basic`, `rational`, `exp-log`, `radical`, `trig` | a rule that opens an idea (`IDEAS`: `log-of-product` with the `log-of-power` and `log-of-reciprocal` on its pieces; `log10-to-base` and `log2-to-base` with `log-base-exact`; `reduce-radicand-power` with `split-integer-exponent` and `extract-perfect-power`) and the rewrites that continue it at or below its place are one step, the first rule's text with the final `{after}`; one rule on separate subexpressions in a row is one step, `simplify.step.many`: the first's text, then the others listed, each `simplify.step.equation`; any other rewrite is its own step. A `rational` content step with the value `gcd` = `p/q` keeps its rule's text when `q = 1` (`Cancel the common numeric factor 2`), and otherwise says the numerator and the denominator are multiplied by `q`, `rational.step.clear-fractions`, then that `p` cancels when `p > 1`, `rational.step.clear-fractions-and-cancel` (`(2x/3 + 2)/4`: multiply by 3, then cancel 2) |
| `algebra.AlgebraNarrator` | `expand`, `factor` | the `distribute` rewrites of one product and the products nested in it are one step, `expand.step.multiply-out` (a single one keeps its own text); the `rational-root` steps in a row are one step, `factor.step.rational-roots` listing the roots, with the root of the linear quotient the last one left (the search records no step for it), so a single root found also takes this text when the quotient is linear; any other step is its own. No step says a factorization is incomplete: that shows only in the result |
| `calculus.DiffNarrator` | `diff` | the first step of a partial derivative opens with `diff.step.partial`, the symbols held constant; a `sum-rule` and the linearity rewrites after it (`sum-rule`, `constant-multiple`, `constant`, `variable`) are one step, `diff.step.linearity` ("constants fall away") when a `constant` rewrite among them dropped a constant term and `diff.step.term-by-term` otherwise; a `chain-rule` takes the rewrites below its place, the inner derivative's, as one step, `diff.step.chain` naming the outer and the inner derivative, then the inner steps' texts on the same line, for one level only: when the inner derivative takes a chain rule itself, the outer chain rule is a step of its own with its rule's text and the inner one is narrated as a step; any other rule is its own step, the `variable` rewrites right after it and below it folded in |
| `calculus.IntegrateNarrator` | `integrate` | a `sum-rule` step and the linearity steps right after it (`sum-rule`, `constant-multiple`, `constant`) are one step, `integrate.step.term-by-term`; every other step, a technique or the terminal `none` or `angle-mode`, is its own step with its rule's text, which names the integral it rewrote and what replaced it (the values `integral` and `result`) |
| `solve.SolveNarrator` | `solve` | one step per state of the equations as the solver records them (`collect`, `isolate`, `divide`; `factor`; `roots`, whose solutions the solver has already simplified), except that a quadratic's `discriminant` and `formula` are one method and one step, `solve.step.quadratic-formula`, naming the discriminant |
| `calculus.SeriesNarrator` | `taylor`, `sum`, `product` | one step per rewrite; a Taylor polynomial's lists the derivatives at the point, `taylor.step.derivative` each, inside `taylor.polynomial`; a generic sum's conditions follow its text (`sum.step.where`) |
| `calculus.LimitNarrator` | `limit` | one step per rewrite: a rule's derivation, one line per inner decision (**Limits**, **Narration**) |
| `linalg.LinalgNarrator` | `rref`, `det`, `inverse`, `rank`, `linsolve`, `nullspace`, `colspace`, `gramschmidt`, `eigen`, `diagonalize` | one step per step of the operation, each with its rule's text; an `add` row operation whose multiple has a negative numeric coefficient reads `R₂ ← R₂ + 3R₁` or `R₃ ← R₃ + R₂/x` (`rref.step.plus`); a step with `conditions` is followed by `linalg.step.assuming` (one condition without the set's braces), a generic `{}` by `linalg.step.unless` |
| `units.UnitsNarrator` | `convert` | a conversion is one step, its rewrites' texts (`1 {unit} = {definition}`, `So {quantity} = {result}`) joined by `step.sentences` |
| `calculus.ApplicationsNarrator` | `applications` | one step per step of the command, each with its text `applications.<command>.<step>` |

`steps(factor(x^4 - 1))` is the one step `1, -1 are rational roots, so x⁴ - 1 = (x - 1)(x + 1)(x² +
1)`; `steps(diff(x^2 sin(x), x))` is the product rule, the derivative of sin, the power rule;
`steps(solve(x^2 - 5x + 6 = 0, x))` is `Factor the left side`, then `The solutions are {2, 3}`;
`steps(∫ x e^x dx)` is integration by parts, then the integral of `e^x`.

In `eval/`, `NarrationTest` checks the texts per operation and that the steps replay,
`NarrationProperties` that over the rule sets' generators (elementary expressions for simplify and
diff, polynomials for expand, factor and solve) the steps never throw, cover every rewrite in order,
chain from one to the next and end at the result, and `NarrationBundleTest` that the bundle has a
key for every rule, event and grouped step and no other, and that every text formats with sample
values, its placeholders filled and its apostrophes escaped.

### Tests

In `engine/src/test/java/mathcanvas/engine/match/`: `MatcherTest` (every construct, the order of
`all`, pruning, compile errors), `BindingsTest` (substitution and splicing), `OperandsTest`,
`MatcherPerformanceTest` (the budgets above) and `PatternParseTest` (the fixed cases written as
text). `MatcherProperties`, whose subjects are asserted to be canonical fixpoints: an instance of a
linear, unambiguous pattern rebinds its values; argument sequences rebind their arguments; `all` has
no duplicates and starts with `match`; combined sequences are canonical; matching random patterns
(the generator's symbols turned into variables) never throws. In `rule/`: `RewriterTest` (order,
restart, bound positions, binder scope, normalisation, the cap, assumptions, paths),
`TraceProperties` (the trace replays to the result, with and without tracing), `NarratorTest`
(the bundle's text, the fallback, the Unicode printer) and `ValuedReplacementTest` (a valued rule's
values recorded canonical and filled into its text, an unknown placeholder, the rejected names, an
undeclared value).

`engine/src/test/java/mathcanvas/engine/testing/` holds the tooling every rule set's tests use:

- `NumericEvaluator.eval(e, point, mc)` evaluates over the number tower, exact where the tower is
  exact and otherwise at `mc`, through the elementary functions in radians (and `erf`, `erfc`,
  `gamma`, `beta` and the compiled densities and cumulative functions in `double` through Hipparchus
  directly), and is empty where the
  value is undefined or outside bounds that keep every evaluation fast, since such a point is no
  useful sample: a power of an exact base other than 0 and 1 (whose powers do not grow; -1 stays
  bounded, as its rational powers cost a reduction of `π·q`) formed with more than 4,000 bits (the
  base's digits times its exact exponent's numerator), and of a non-zero inexact base, or of an
  exact one under an inexact exponent, whose result's decimal exponent would pass 10⁴ (the base's
  decimal exponent, plus one, times the exponent's numerator, or an inexact exponent's modulus
  rounded up: `16^(5.125^20)`), since an inexact power is rounded to the precision whatever its
  exponent; a root of a degree past the `int` range; `exp` or a power of `e` with an argument of
  modulus above 10⁴; a logarithm of a value
  whose decimal exponent is beyond ±10⁴; a circular or hyperbolic function of an argument of
  modulus above 10⁶, a circular function of an argument whose imaginary part, or a hyperbolic one
  of an argument whose real part, exceeds 50 in modulus (the value would pass `e^50`, and `cos²z +
  sin²z` would cancel more digits than any recheck carries); and any inexact value, operand or
  result, whose decimal exponent is beyond ±10⁴. `NumericEvaluator.beyondBounds(e, point, mc)`
  says whether an evaluation, away from cuts, stopped at one of these bounds rather than at an
  undefined value (a division by zero, a logarithm of zero, a pole within rounding); every operand
  of a node is evaluated before the node is judged, so an undefined operand makes it undefined
  whatever its order beside one past a bound, and a zero base, exact or inexact, is never past a
  bound, so `0.0^-20000` is a pole. It knows `sign` of a real value (inexact for an
  inexact value, so the sign of noise claims no exact value) besides the functions the rule sets
  use, every circular and hyperbolic function and inverse (by their principal formulas: `tan`,
  `cot`, `sec` and `csc` through `sin` and `cos`, `coth`, `sech` and `csch` through `sinh` and
  `cosh`, `acot`, `asec`, `acsc`, `acoth`, `asech` and `acsch` of the reciprocal, `acosh` in Kahan's
  form), `atan2` of reals and the step functions of a real value, and evaluates a power of `e`
  through `exp`. `evalAwayFromCuts` is also empty where the rounding noise of an inexact value
  decides the result rather than the expression: a logarithm, a square root or a non-integer power
  within rounding of the negative real axis; `asin`, `acos`, `atanh` (and `asec`, `acsc` of the
  reciprocal) within rounding of the real axis outside [-1, 1], and `atan`, `asinh` (and `acot`)
  of the imaginary axis outside [-i, i]; `sign` of an inexact value within rounding of zero; a pole
  within rounding: a circular or hyperbolic function whose denominator is an inexact value within
  rounding of zero, or such a value to a negative power or as the argument of a reciprocal inverse
  (`tan(3π/2)` and `-cot(π)` are each one over a different rounding noise); `sin` or `cos` whose
  inexact value is within rounding of zero (`cos(acos(10^-158))`); and a root or a non-integer
  power of an inexact value within rounding of zero, which magnifies the noise. Within rounding of
  zero is a modulus at most `10^(20 - p)` at the precision `p`.
- `Soundness.points(symbols, assumptions)` draws points within the assumptions: integers in their
  window and parity, rationals within their bounds for real symbols, Gaussian rationals for complex
  ones; a non-strict bound is itself drawn about one time in four, so a rule wrong only at `x = 0`
  under `x ≥ 0` is caught (`SoundnessTest`). `Soundness.check(before, after, points)` asserts
  agreement where both are defined, within `1e-20` relative for exact expressions and `1e-12` when
  either holds an inexact number, with an absolute floor of `1e-25`, and returns how many points it
  compared. The rule sets' properties recheck a disagreeing point at 100 digits and skip it only on
  a cut, a pole or a zero within rounding, or when it is ill-conditioned (either side disagrees with
  itself between 34 and 100 digits), with ill-conditioned skips capped at 5% of the points compared
  per property run (**Simplification**, **Tests**).
- `TerminationProbe.run(ruleSet, exprs, context)` fails on the first expression that exceeds the
  cap and reports the most rewrites taken.
- `RandomAssumptions` draws facts about each symbol: none, a sign, an integer domain, a parity or
  the complex domain; `withRanges` gives a third of the symbols a closed range instead, its ends
  from `-2 … 0` and `1/2 … 2`, so that some ranges lie within `[-π/2, π/2]` and some do not.

`PLAN.md`'s acceptance pairs are carried by the rule sets themselves (**Simplification**).

## Assumptions

Package `mathcanvas.engine.assume`. `Assumptions` is an immutable context of facts about symbols,
consulted by rule conditions through the rewriter's `Context`.

### Facts

`with(relation)` records a fact; the relation is canonicalised first and must have a symbol on the
left:

| Relation | Fact |
|---|---|
| `x > c`, `x >= c`, `x < c`, `x <= c`, `c` a real number | a lower or upper bound; implies real |
| `x != 0` | non-zero |
| `x ∈ ℤ`, `ℚ`, `ℝ`, `ℂ` | the domain |
| `mod(x, 2) = 0` or `= 1` | even or odd; implies integer |

Anything else throws `IllegalArgumentException` with the reason: an equation, `x != 2`, a number
on the left, a bound that is not a real number (`π`, `y`, `i`), another domain. A range is two
relations. `facts(x)` returns the combined `Facts(domain, lower, upper, parity, nonZero)`,
`relations()` the relations in force as a canonical set, for `assumptions()`, and `without(x)` the
assumptions with every fact about `x` removed. The record's constructor replays its relations and
throws `IllegalArgumentException` unless they give exactly its facts, so no instance bypasses the
checks of `with`.

**A symbol is real unless it is assumed otherwise.** `Facts.NONE` has the domain ℝ. Facts combine
by tightening: the narrower domain, the higher lower bound and the lower upper bound (the strict
one on a tie). `x ∈ ℂ` is the one fact that widens: it is accepted only while nothing recorded
about `x` says it is real (no domain of ℝ, ℚ or ℤ, no bound, no parity; `x != 0` may stand), and a
bound or a parity on a complex symbol is a contradiction until a domain of ℝ, ℚ or ℤ narrows it
back, which drops the `x ∈ ℂ`.

A fact that contradicts the recorded ones throws `IllegalArgumentException` naming the new relation
and those recorded about the symbol, printed in Unicode (`x > 0 contradicts [x < 0]`): bounds that
cross or meet with a strict end, a point bound at 0 with `x != 0`, opposite parities, and an integer
symbol whose window, tightened to integers and to its parity, holds no value (`x ∈ ℤ`, `0 < x < 1`).

### Queries

`isReal`, `isInteger`, `isRational`, `isPositive`, `isNegative`, `isNonNegative`,
`isNonPositive`, `isZero`, `isNonZero`, `isEven` and `isOdd` answer a `Tri` (`TRUE`, `FALSE`,
`UNKNOWN`, with `and`, `or`, `not`) for any expression. They are a query, a hand-written structural
inference, not a rewrite. It tracks whether the value is real, rational, an integer, zero, the
signs a real value may take, and its parity, and propagates them:

- **Numbers** by value: a decimal is rational, and an integer when its value is (`2.0`); a complex
  number is real only when its imaginary part is zero.
- **Constants**: `π` and `e` are positive and irrational; `∞` is not real; the rest are unknown.
- **Units** are positive and real (a unit is a positive constant, **Units**), neither known
  rational nor known irrational.
- **Symbols** from their facts: real unless assumed complex, the signs their bounds allow.
- **Sums**: real, rational or integer when every term is, and not when exactly one term is not.
  The sign is added abstractly: positive and non-negative terms make a positive sum, so `x² + 1` is
  positive for a real `x`. The parity of integers adds.
- **Products**: closed likewise; one non-real factor among non-zero reals makes it non-real. The
  sign multiplies; zero when a factor is zero, non-zero when every factor is. Even when an integer
  factor is even.
- **Powers**: an even integer exponent of a real base is non-negative (positive when the base is
  non-zero), an odd one keeps the base's sign; an integer to a non-negative integer power is an
  integer, a rational to an integer power rational; a non-negative base to a real exponent is
  non-negative, a positive one positive (so `e^x` is positive for a real `x`); a non-zero base gives
  a non-zero power.
- **`abs`** is non-negative, **`exp`** positive for a real argument and never zero, **`sqrt`**
  non-negative for a non-negative argument, each only when the argument is arithmetic over
  numbers, symbols, `π` and `e`: `abs(∞)` and `exp(-∞)` are unknown.
- **`sin`, `cos`, `tan`, `cot`, `sec`, `csc`, `sinh`, `tanh`** and **`atan`** of a real argument
  are real (where defined), and **`cosh`** of one is positive, under the same condition on the
  argument; so `sin(x)²` is non-negative for a real `x`, and `|sin x|²` and `√(sin²x)` resolve.
- An expression whose sign set is empty, undefined wherever the facts hold (`1/x` under `x ≥ 0`
  and `x ≤ 0`), answers unknown to every sign and zero query, and so does every sum, product,
  power or `abs` containing it (`y/x`, `1/x + 1`).
- Everything else is unknown.

### Tests

`AssumptionsTest` in `engine/src/test/java/mathcanvas/engine/assume/` covers every accepted and
rejected form, the composition and the contradictions, and each inference rule with known and
unknown answers. `InferenceProperties` (in `testing/`) checks every known answer against the values
of random expressions at points within random assumptions, over `exactExprs()` and over
`trigExprs()`.

## Parsing

Packages `mathcanvas.engine.builtin` and `mathcanvas.engine.parse`: source text to raw
expression trees. `docs/language.md` is the specification; this section says how the code
realises it.

### The builtin registry

`Builtins` holds every name of `docs/language.md` §12 as a `Builtin`: its `Kind` (`FUNCTION`,
`COMMAND`, `RESERVED` for a later-phase name, `NOTATION` for `lim`), the argument counts it
accepts (a list plus a variadic flag, so `limit` takes 3 or 4 and `gcd` 2 or more), whether
it is in the prefix set, the positions a command holds (or all of them), the position of its
bound variable, with an override per argument count (`binderFor(count)`: `area` and `volume` bind
their second argument with four arguments and their third with five), and whether `e` and `i` may
be that variable (`NO`, `YES`, `WITH_BOUNDS`).
`lookup` resolves the `arc…` aliases to their names (`arcsin` is `asin`). The reserved
constants, domains and Phase 9 keywords are answered here too. The parser and the evaluator use
this one registry.

### The lexer

`Lexer` is a pull lexer. The text is normalised to NFC one segment at a time, a segment being a
starter and the combining marks (and Hangul medial and final jamo) after it, so `=` followed by
U+0338 is `≠` and `e` followed by U+0301 is `é`. Each normalised code point keeps the line, column
(in code points) and UTF-16 offset of the text as typed, so a `Token`, and every error, points into
the user's own text. A byte-order mark at the very start is skipped, but counted in the column and
the offset, as typed. A numeral, a run of superscript digits or a derivative order longer than
`Lexer.MAX_DIGITS` (1,000,000) characters is "number out of range"; every digit up to that bound is
kept. `Numerals` converts a numeral of more than a thousand digits by splitting it in halves,
converting each the same way and joining them with a power of ten computed once per length, so the
work is `BigInteger`'s Karatsuba and Toom-Cook multiplications rather than its quadratic
`BigInteger(String)`: a million digits read in about a second instead of more than a minute. A
decimal literal is its digits so converted with the scale `BigDecimal(String)` would give.

Tokens follow §3: longest match, the Unicode aliases, identifiers with subscript runs normalised
to `_` and ASCII, superscript powers as one token carrying their integer, `in` as the
membership token, `mod` as the remainder operator's token, and `and`, `or`, `xor`, `not` and
`implies` as the boolean operators' tokens, as are `∧ ∨ ⊕ ¬ ⟹ ⇒`. Reserved words stay identifiers, so a message names the word as typed. The
lexer counts open brackets and emits a newline token only outside them. The derivative notation is
one token carrying its variable and order. Primes (`'` or `′`) directly after an identifier join it
as `'`, so `y′′` is the identifier `y''` (the prime notation). A `_` starts no identifier: `_` and a
letter begin a `UNIT` token, its text the name after the underscore read as an identifier's
characters, which the parser resolves through `units.Units` ("unknown unit `_foo`"); a base
literal (`0x`, `0b` or `0o` before an ASCII letter, digit or `_`) is read as an `INT` token whose
text is its integer in decimal, every letter or digit outside the base an error at it, and one
longer than `Lexer.MAX_BASED_LENGTH` (100,000 characters) "number out of range", since its
conversion to decimal grows with the square of its length; a `_` before anything else is "expected
a unit name after `_`". The derivative token commits once its head
(`d/d` + name, `d^n/d` + name, or `dⁿ/d` + name) has been read; after that, anything but the exact
shape is the shape error.
The partial derivative notation is one token too, `PARTIAL`, begun by every `∂`: its text is the
function name of `∂f/∂x` (empty for `∂/∂x`), `|`, and the variables in the order they are
differentiated, rightmost denominator first, each repeated by its order (`∂²/∂x∂y` is `|y x`); its
order is the total, which must equal the head's. The parser builds the nested `Derivative`s, a run
of one variable one node of that order.

### The parser

`Parser.parseExpression(text, names)` reads one expression; `Parser.parseScript(text, names)`
reads statements (`Statement.Definition`, `Statement.Expression`, each marked quiet when ended
by `;`, each with the `Position` of its first token as `start()`: line, column and UTF-16
offset). `Names` says which names are user-defined functions, and is consulted for every
statement. Within a script, the script's own definitions overlay it for the statements after
them: `f(x) := …` and `f := x -> …` define a function, `f := 3` a value, and a top-level
`clear(f)` removes it.

It is a Pratt parser (Pratt, *Top Down Operator Precedence*, 1973). Binding powers realise the
table of §4.1:

| Construct | Power | Operand parsed at |
|---|---|---|
| `implies` (`⟹ ⇒`) | 12, right-associative | 11 |
| `or` (`∨`) | 14, n-ary | 14 |
| `xor` (`⊕`) | 15, n-ary | 15 |
| `and` (`∧`) | 16, n-ary | 16 |
| `not` (`¬`) | prefix | 17: a relation, stopping before `and` |
| relations | 20, non-associative | 20 |
| `+ - ∪ ∩` | 30 | 30 |
| `* / ×`, `mod`, `\`, implicit multiplication | 40 | 40 |
| sign, prefix function name, radical | prefix | 50: takes `^` and postfix, stops before `*` |
| `^` | 60 | 59: right-associative, admits a sign |
| `! % °`, superscript | 70 | |
| `d°m′s″` | 70 | a number, `°`, a number and `′`, optionally a number and `″` |
| `d/dx`, `lim[…]`, `Σ[…]`, `Π[…]` | prefix | 39: the rest of the term |
| `∫` | prefix | 29: an additive integrand, to its differential |

`×`, `mod` and `\` fold the term so far into their left operand, as `cross(a, b)`, `mod(a, b)` and
`setdiff(a, b)`, and `∪` and `∩` the sum so far, as `union(a, b)` and `intersect(a, b)`. The
token `mod` where an operand begins is the call `mod(…)` when `(` follows, else "mod needs
parentheses"; after a factor it is the operator, and a parenthesised list after it, `x mod(5, 3)`,
is refused with the hint to write `x·mod(a, b)` (§9.5). `mod := …` is "`mod` is a builtin".

The tree is raw: what was typed, in the order typed.

**Positions.** The `Script` carries a side table from each node the parser built to the position of
the node's first token, keyed by identity: `positionOf(node)`. Nodes are not interned, so equal
nodes typed in two places are two objects with two positions. Parentheses and a unary `+` give the
node inside, which keeps its own position; a `-` or `/` operand's `Exprs.neg` or `Exprs.inv` is at
its operator. A node met at two places, which happens only for the shared constants (`π`, `e`,
`true`, the domains), has no position. The table serves an evaluation error, whose node is the raw
node by identity (**The evaluator**).

- **Arithmetic.** A chain of `+`/`-` or of `*`/`/`/implicit factors is one flat `Sum` or
  `Product`, with `a - b` as `Exprs.neg(b)` and `a / b` as `Exprs.inv(b)`; parentheses nest.
  `-2` is `Product(-1, 2)`, `20%` is `20·100⁻¹`, `30°` is `30·π·180⁻¹`, `30°15′20″` is
  `(30 + 15·60⁻¹ + 20·3600⁻¹)·π·180⁻¹` (the tokens `MINUTE` and `SECOND`, read only after a
  number and `°`; anywhere else "`′` follows a number of degrees"), `√x` and `sqrt(x)` are
  `Power(x, 1/2)`, `∛x` is `cbrt(x)`, `∜x` is `root(x, 4)`, `|x|` is `abs(x)`, `5!` is
  `factorial(5)`. Unary `+` builds no node.
- **Calls.** `name(` is a call only for a builtin or a user-defined function that no binder in
  scope shadows. A builtin's arity is checked here. `diff`, `integrate`, `limit`, `sum` and
  `product`, like their notation, build `Derivative`, `Integral`, `Limit`, `BigSum` and
  `BigProduct` when the arguments fit the fields (a symbol variable, a positive integer order,
  `left` or `right` as `limit`'s fourth argument). Otherwise they build `Apply`. `TypedNodes`
  holds this construction, so the evaluator builds the same node once a command's arguments,
  evaluated, come to fit (`TypedNodes.of(Apply)`).
- **Binders.** A binder's variable is known before its scope is parsed, so a bound `e` or `i` is a
  symbol there. For the notation the variable comes first. For `∫` the parser reads ahead to the
  differential that will end the integral, counting nested integrals at the same bracket depth.
  For a command (`solve diff integrate limit sum product taylor` and the applications) it reads
  ahead to the argument at the binder position for the call's argument count (the second, or the
  third for `area(f, g, x, a, b)` and `volume(f, g, x, a, b)`), a name possibly signed with `+` or
  parenthesised, or a list of names for `solve`. That name is bound in that argument and every
  argument before it.
- **Unknown functions** (Phase 7b). For `dsolve` the parser reads ahead to the second argument, `y(x)`
  or `[x(t), y(t), …]` (names that are not builtins, one variable): the function names are callable
  for the whole call, and the variable is bound in the first two arguments. Another shape declares
  nothing, and the evaluator reports it.
- **The prime notation.** A primed identifier must be followed by `(` and is a call whose head is the
  primed name (`y'(x)` is `Apply(y', x)`); a builtin, constant, domain or keyword cannot be primed.
- **Brackets.** `[…]` is classified by `Brackets.of`: a Vector when every element is scalar, a
  Matrix when every element is a vector of one length, a List otherwise. The evaluator applies
  the same rule after evaluation. `Brackets.isScalar` counts numbers, non-domain constants,
  symbols, and arithmetic, applications and the unevaluated forms over them.
- **Constants.** Outside a binder, `e`, `π`, `∞` and `undefined` are `Constant`s, `i` is
  `Num(Complex.I)`, `true` and `false` are `Bool`s, and the domains are `Constant`s.

**Totality and depth.** Any text gives a result or a `ParseException`, and errors come in text
order. A lexical error stops the token stream where it stands and is raised only when the parser
reaches it. A binder's variable that is read ahead and refused (`diff(…, i)`, `∫ … di`) is reported
only once the text before it has parsed. Recursion happens only per level of nesting. The parser
rejects more than `Parser.MAX_NESTING` (200) levels, counted both as open constructs and as the
depth of the tree built, every `×` application included ("expression nested too deeply").
Parentheses directly around the operand of a prefix operator or of `^` count no level, since the
printers write them (`-(-x)`, `√(√x)`, `2^(2^x)`): a tree accepted at the cap echoes to text
accepted again. That keeps every recursive walk of a parsed tree (`hashCode`, `Canonical.of`, the
printers) within a 1 MB thread stack, the smallest default of the supported platforms (Linux and
Windows x64), while a 10,000-term chain is one flat node. The tests run with `-Xss1m`
(`build-logic`'s `mathcanvas.java-conventions`), so they fail here on a stack that would be too
small there. Worker threads that the app creates (Phase 2) must have a stack of at least 1 MB.

## Printing

Package `mathcanvas.engine.print`: `AsciiPrinter`, `UnicodePrinter` and `LatexPrinter`, each a
`Printer` singleton, and `LayoutPrinter`, which prints to the layout tree `render` typesets
(**The layout tree**).

**Faithful printing.** `Printer.print(e)` prints a tree as it holds its operands, so the raw tree
of a parse echoes as typed. Each subtree prints to a piece carrying its precedence and the facts
its parent needs. The parent parenthesises exactly where the child would otherwise read back
differently: §15.2, plus a nested sum or product, which keeps what the user grouped.

- **Numbers.** A number prints as the raw shape it reads back as, so a negative, rational or
  complex number parenthesises like that expression (`x·(-2)`, `x^(1/3)`, `(1 + 2i) x`). A
  decimal always has a point: plain digits when its decimal exponent is in [-6, 20] (`2000.0`),
  otherwise scientific with a lowercase `e` (`1.180591620717411303424e+21`). `Real.toString`
  writes the same form.
- **Products.** A factor `x⁻¹` after the first prints as `/x`.
- **Big operators.** A big operator followed by anything in its term is parenthesised.
- **Integrals.** An integrand that begins with `[` is parenthesised, since a `[` after `∫` is the
  bounds. An integral whose notation would not read back prints in command form.
- **The derivative notation.** A quotient that would read as the derivative notation is spaced:
  `d / dx`.
- **`limit` calls.** A `left` or `right` symbol as the fourth argument of a `limit` call is
  parenthesised, so it does not read back as the side of a `Limit`.
- **Juxtaposition.** A number before a call takes a space (`2 sin(x)`), otherwise §15.3.

The ASCII and Unicode printers share one implementation with a style flag, which the layout
printer shares too (**The layout tree**); LaTeX has its own. `mod(a, b)` is printed infix, `a mod b`
(LaTeX `a \bmod b`), at the multiplicative level with the parentheses of §15.2; in the layout tree
the word is an upright operator. The Unicode printer writes `union`, `intersect` and `setdiff` as
`A ∪ B`, `A ∩ B` (additive, the other of the two parenthesised on the left) and `A \ B`
(multiplicative); the ASCII printer writes the calls; LaTeX `\cup`, `\cap`, `\setminus`. The
boolean operators are written infix at their levels (§15.2), the words in ASCII, `∧ ∨ ⊕ ¬ ⟹` in
Unicode, `\land \lor \oplus \lnot \implies` in LaTeX. `OperatorRoundTripProperties` reads random
trees of the boolean and set operators back from both text printers.

**Display.** `Display.of(e)` turns a canonical value into the raw tree whose faithful printing is
the conventional notation, and `Printer.display(e)` is `print(Display.of(e))`:

- **Sums** are in descending total degree: the sum of the numeric exponents of the symbols in a
  term, a symbolic exponent, a constant, a number or an application counting 0. Ties are in
  canonical order, and the numeric terms come last: `x³ + 3x² + 3x + 1`,
  `x² cos(x) + 2x sin(x)`, `2πk + π/6`, `-2 sin(x) + 3`. The first term carries its sign in its
  coefficient (`-2x² + 3x - 1`); a later term with a negative coefficient becomes a subtraction of
  the negated coefficient, a coefficient being negative when it is a negative real, a negative
  imaginary or a complex number with a negative real part (`a - 2i x`, `a - (1 - 2i) x`). A sum
  that is a factor of a product with a numeric coefficient and whose first term reads with a minus
  is led instead by its first positive term of the same degree (`-(√3 - √2)`, `2(y - x)`,
  `(√6 - √2)/4`; `-(-x² + 1)` keeps its degree order), and in a sum of rationals and rational
  multiples of numeric radicals a positive rational term leads (`(3 - √5)/2`, `(1 + √17)/4`); a
  sum on its own is untouched (`-x + y`).
  Only the order changes, so the round trip holds. A complex number is split into its real and imaginary terms, its real part left out when it is
  zero (`2i`, `2.0i`).
- **Products** become quotients: a rational coefficient's denominator and every commuting power with
  a negative numeric exponent go below the line (`x/(2π)`, `x²/2`, `1/√x`; a number's power only
  with an integer exponent, so `2^(-1/2)` stays a power, since its root would fold first when read
  back), and a negative coefficient signs the quotient (`-1/x`, `-x/2`). Factors are in groups, each
  in canonical order: the coefficient; constants and symbols with their powers (a power of a
  constant only with a numeric exponent); sums and their powers; the rest (`2πr`, `2x sin(x)`,
  `(x - 1) e^x`).
- **Vectors and matrices.** A factor built from them never goes below the line. It follows the
  commuting numerator in its order, keeps its negative powers (`A·B^(-1)`), and the denominator
  divides the whole product (`M/2`, `2M/3`).
- **Sets of constants** (every member `±∞`, or arithmetic over numbers, `π`, `e` and `exp`, `log`,
  `sin`, `cos`, `tan`, `asin`, `acos`, `atan`) are in ascending order of value: `-∞` first, then the
  real members, then `∞`, then the non-real members by real and then imaginary part (`{-√2, √2}`,
  `{-∞, 2, ∞, i}`; `ConstantOrder`). The values are inexact, at 30 digits: every number is made a
  `Real` first, an integer power is taken by squaring with rounding, a half-integer one through the
  square root, and any other through `exp(y log x)`, so a value costs a few operations per node.
  Each member's value is taken once and the set sorted on those keys; only the members tied at 30
  digits are valued again, once each, at 60 (`{π, 3.141592653589793238462643383279503}`), and equal
  there keep their canonical order. A set of 1,000 numeric radicals orders in a few milliseconds; a
  member with another fractional power or an elementary function costs about a tenth of a
  millisecond more. A value whose imaginary part is below `10^-25` of it counts as real. A set with
  a member that has no such value keeps its canonical order: another function or a symbol (`{√2,
  -√2, x}`), a power whose exponent exceeds 10⁶ in magnitude (`{2, 3^1000000000}`), or a value
  outside `BigDecimal`'s range.

  `Display.of(e, cancellation)` and `Printer.display(e, cancellation)` check a `Cancellation` at
  every member valued, throwing `CancelledException`; `Display.of(e)` and `display(e)` stop only
  at a thread interrupt, like every engine loop. `Printer.print(e, cancellation)` checks it at
  every node printed, and `display(e, cancellation)` prints with it, so a request stops a huge
  print too; the ASCII and Unicode printers and `LayoutPrinter.print(e, cancellation)` check, the
  LaTeX printer prints without stopping.

**Echoes and carets.** `Printer.echo(definition)` prints a definition statement as the CLI and
the worksheet echo it: the head and the body as parsed, faithfully, joined by ` := `
(`f(x) := x² + 1`, `g := t → t²`). `Caret.under(source, column)` is the line under an error's
source line that puts a `^` under a 1-based code-point column, copying each tab of the source
before the column so the caret lines up (`docs/reference/cli.md`, **Errors**).

**A package cycle.** `Printer.echo` takes a `parse.Statement`, and the parser prints an expression
through `AsciiPrinter` in one of its messages, so `print` and `parse` depend on each other inside
the module, which JPMS allows. Neither package's static initialisation reads the other's: the
printers' `INSTANCE` fields need nothing from `parse`, and the parser touches a printer only while
it reports an error; keep it so. The decimal number formats evaluate through
`eval.NumericForm.numeric`, so `print` depends on `eval` as well; that is a call at print time,
never a static field.

**Number formats.** `Printer.withFormat(format)` returns a printer that writes numbers in a
`Format(kind, digits)`; `format()` says which, `Format.EXACT` by default. The ASCII, Unicode and
LaTeX printers and `LayoutPrinter.withFormat` honour it in `print` and `display` alike, and the
format changes how numbers are written, never the value printed:

| Kind | Writes |
|---|---|
| `EXACT` | numbers as they are, rationals as quotients: the printers' default |
| `DECIMAL` | rationals and decimals as decimals of `digits` significant digits (half-even, trailing zeros dropped, written as `Real` writes them: `0.3333`, `3.5`, `1.235`, and a result that rounds to a whole number with a point, `7/2` at one digit as `4.0`); integers as integers |
| `SCIENTIFIC` | the same in scientific notation (`3.333e-1`, `3.5e+0`); an integer only when it has more than `digits` digits (`1e+21`, but `2`); in LaTeX with `\times 10^{…}`; a scientific literal is set off from a following name by a space (`3.5e+0 x`) |
| `FRACTION` | a rational whose magnitude exceeds 1, printed as a number on its own (a value, a sum term, an item, a matrix entry, a complex part), as a mixed number: `3 1/2`, `-3 1/2`, `x + 3 1/2`, LaTeX `3\frac{1}{2}` |

Under `DECIMAL` and `SCIENTIFIC`, `display` prepares the value before `Display.of`: each rational
becomes a decimal, so a coefficient is written as one (`7x/2` is `3.5x`), and a subexpression made
only of numbers becomes the decimal of its numeric form at `digits` (`3√2/4` is `1.061`); constants,
symbols and calls stay (`2π`, `sin(1)`), and so do integer exponents and the `±1/2` of a root (`x²`,
`1/x`, `√x/3` is `0.3333√x`). A rational exponent is a decimal (`x^(1.5)`), never scientific and
never a mixed number (`x^(3/2)` under `FRACTION`); a coefficient `Display` writes over the line
keeps its quotient under `FRACTION` (`7x/2`). A complex number formats each part (`0.5 + 0.75i`).
`print` formats the held tree's numbers as they stand, so a typed `1/3`, a quotient of integers,
stays `1/3`. In the layout a mixed number is a row of its whole part, a linear space and a
fraction, which flattens to `3 1/2`. Only `EXACT` output reads back: a mixed number does not parse
(a number cannot start an implicit factor), and a decimal is not the value it rounds.

**Literal bases.** A power whose base is a number literal with an exponent marker parenthesises
it in every format, `(1.0e+21)^x`, since `1.0e+21^x` reads back as `1.0e+(21^x)`.

**Round trip.** For a canonical `e`, `Canonical.of(parse(p.display(e)))` equals `e` for the ASCII
and Unicode printers in the `EXACT` format, in an environment where every function applied is
defined. The exceptions are listed in `docs/language.md` §15.4. For a raw tree, `parse(p.print(e))`
is `e` with each number in its typed shape.

### The layout tree

`LayoutPrinter.print(e)` lays out a tree as it holds its operands and `display(e)` lays out
`Display.of(e)`, like the text printers; `display(e, cancellation)` stops at a request while the
display shape is built, as `Printer.display(e, cancellation)` does; `withFormat` gives one writing
numbers in another format (**Number formats**). The result is a `Layout` (package
`mathcanvas.engine.print.layout`), a sealed hierarchy of records:

| Node | Holds |
|---|---|
| `Row` | parts side by side; a grouping only, its items spaced as if in the enclosing row |
| `Glyph` | one character, its `Style` (upright or italic) and its spacing class |
| `Text` | a run set as one atom: a number, a function name, a word, a name of several letters |
| `UnitText` | a unit (**Units**), upright: what is set (`km`, `°C`) and its linear form (`_km`), which `flatten` writes |
| `Space` | a space of the text: `LINEAR` (no width when typeset) or `THIN` (before a differential) |
| `Fraction` | numerator and denominator |
| `Scripts` | a base with a subscript, a superscript or both |
| `Radical` | a radicand and its index (`3`, `4`) or none |
| `Delimited` | a body between stretchy fences: `( )`, `[ ]`, `{ }`, `\| \|`, `⟨ ⟩`, an interval's mixed pair |
| `Matrix` | rows of cells between brackets |
| `BigOp` | `∫`, `∑`, `∏`, `lim`, `d/dx` or `∂/∂x` with its bounds and body; `limits` when the bounds go below and above in display style (`∑`, `∏`, `lim`), not as scripts (`∫`) |
| `Piecewise` | rows of a value and its condition, and the value otherwise |

The spacing class is TeX's (`Cls`: ordinary, operator, binary, relation, open, close, punctuation,
inner). A glyph and a text carry their own; a fraction, a fenced body, a matrix and a piecewise
expression are inner, a big operator an operator, a scripted base keeps its base's class.

**One walk.** The layout printer is the Unicode printer's walk with a second sink: the walk builds
the text as before, decides every parenthesis and separator on it, and the sink records the same
structure as nodes. The text printers' sink builds nothing, so their output does not depend on it.
Where the two-dimensional form makes a parenthesis of the text unnecessary, the node drops it and
records it in a flag: a quotient's numerator and denominator (`(x + 1)/2`, `x/(2π)`, and the spaced
`d / dx`), a `^` exponent (`e^(x²)`) and a radicand (`√(x + 1)`). A script records its linear form
(`^`, `^( )`, Unicode superscript or subscript characters, appended as the side of a limit point
`0+`, or `_`). So `LayoutPrinter.flatten(layout)` rebuilds the text exactly: it equals
`UnicodePrinter`'s output for the same tree, in both modes, and the properties check that.

**What the nodes are.** A sum is a row of terms with binary signs; a subtraction is a row of the
sign and the operand carrying the negated term's path. A product's divided factor makes a fraction
whose numerator is every part before it, as the parser reads `a/b/c`; a minus sign at the head of
a numerator stands before the bar (`-x/2` reads as −(x/2)), and the parentheses the printer put
around such a numerator come off it, while an interval's own brackets stay (`-(a, e]/12`). A power is a superscript, a square
root, `cbrt` and `root(x, 4)` are radicals, `abs` is a pair of bars where the Unicode printer writes
bars, a call is its head and its parenthesised arguments. A one-letter Latin or lower-case Greek
name is italic, an upper-case Greek letter and a name of several letters upright, `e`, `i`, `π` and
the differential `d` italic, `∞` and the number sets upright. A name `base_tail` is its base with
the tail as a subscript. A builtin's name, or a name of several letters, heading a call is an
upright operator. The layout keeps the text's characters (`-`, `·`); the typesetter chooses the
glyphs.

**Paths.** Every node but a space carries the `rule.Path` (operand indices as `Operands` defines
them) of the subexpression it prints in the printed tree, or none: an operator sign, a call's head,
the numerator `2x` of `2x/3`, a big operator's bounds as a group. Parentheses carry their content's
path. The parts of a number's synthetic display tree (`-3`, `1/2`, `1 + 2i`) carry the number's
path. A node's path extends the path of the nearest node above it that has one. Under `display`
the paths refer to the display tree; mapping them back to the canonical value is left for the
worksheet.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/builtin/`, `…/parse/` and `…/print/`:

- `BuiltinsTest`;
- `LexerTest`: tokens, longest match, the derivative shapes, newlines, NFC and positions;
- `ParserTest`: the raw shapes, statements, the environment, error order, and 10,000-term chains.
  It runs every nesting construct at the cap and one level past it, and prints (the layout tree
  included, flattened against the Unicode text), canonicalises and hashes the deepest trees;
- `PrinterTest`: every row of §15.1 in all three printers, and display order, parentheses,
  juxtaposition and integrals;
- `PrintCancellationTest`: a 10,000-term sum stops at any node printed, as text and as a layout;
- `DisplayCancellationTest`: a thousand-member set of constants stops at every member, in the
  Unicode printer and the layout printer;
- `EchoAndCaretTest`: definition echoes and caret lines;
- `LayoutPrinterTest`: the layout tree of each construct with its paths;
- `ModuloPrintTest`: the infix `mod` in the three printers with its parentheses, and random trees
  of `mod` among sums, products, quotients, powers and signs read back from both text printers;
- `FormatTest`: each number format in the ASCII, Unicode, LaTeX and layout printers, displayed and
  held, the mixed number's layout, and the digits' bounds;
- `LanguageCorpusTest`, which runs `src/test/resources/mathcanvas/engine/parse/language.corpus`.
  That file holds every example of `docs/language.md`, with its parse form or its error message
  and position, a `# §N` line before each section. Its header gives the format, and its parse form
  comes from the test-only `ParseFormPrinter`, written independently of the printers.

The properties are:

- `RoundTripProperties`: the round trip on canonical trees from `ExprArbitraries` and the faithful
  round trip on raw ones, for both printers. The raw trees are compared with `TypedShape`, the
  tree with each number in its typed shape. Each canonical sample is also checked to be a fixed
  point of `Canonical.of`, since the round trip is stated for canonical values;
- `LayoutProperties`: the layout tree flattens to the Unicode printer's text, for raw trees and
  displayed canonical values from `ExprArbitraries`, in the `EXACT` format and in a random one, and
  every path in it names a subexpression of the printed tree below the path of the nearest node
  above it;
- `ParserFuzzProperties`: random strings over the alphabet, random token sequences and mutations of
  the corpus inputs. Each must parse, or fail with a `ParseException`, within a second, and
  whatever parses must echo back as the same tree (up to `TypedShape`) through both printers.

## Evaluation

Packages `mathcanvas.engine.eval` (the environment, the evaluator, the session) and
`mathcanvas.engine.simplify` (the standard simplifier). `docs/language.md` §11–§14 is the
specification; this section says how the code realises it.

### The environment

`Settings(precision, angleMode, quadratureDigits)` holds the session settings: the precision in
digits, 1 to 10,000 (`Settings.DEFAULT` is 34, radians), from which `mathContext()` derives a
half-even `MathContext`, the `AngleMode` (`RADIANS`, `DEGREES`, `GRADIANS`) with its factors
`toRadians()` (`π/180`, `π/200`) and `fromRadians()`, and the quadrature digits, 0 (the default,
and what the two-argument constructor gives) or 1 to 10,000, to which a definite integral with no
closed form is evaluated numerically (**Numeric integration**; the app's decimal formats set them).

A `Definition` is a `Value(body)` (`a := body`) or a `Function(params, body)` (`f(x) := body`); both
hold the right side as parsed, since definitions are delayed. `Environment(definitions, assumptions,
settings)` is immutable: the definitions are a sorted map by name, so nothing hashes an expression.
`with(name, definition)` binds or rebinds (a `Value` whose body is a lambda is stored as the
`Function` it denotes, so `f := x -> x^2` is `f(x) := x^2`), `without(name)` unbinds,
`withAssumptions` and `withSettings` replace, `isFunction(name)` answers the parser, and `names()`
is the parser's `Names` view.

### The evaluator

`Evaluator.evaluate(raw, env, trace)` returns `Result(value, trace)`. The value is canonical.
Mathematical failure is a value (`undefined`); the errors of §16 throw `EvaluationException`, which
carries the raw node that failed by identity (`node()`), which the script's `positionOf` places in
the text (**Parsing**).

Evaluation is bottom-up, and every intermediate value is canonical (`Canonical.of` at the session
precision):

- **A symbol** is looked up in order: a name that a binder around it binds is itself; a parameter
  of the current **frame** is its value, returned as it is and never evaluated again; a name bound
  to a value definition is the evaluation of the definition's body, with no frame and no binder.
  Those values are memoised per call, by name, since the environment is fixed during a statement,
  so `a2 := a1 + a1`, `a3 := a2 + a2`, … cost one evaluation each. A stack of the names being
  expanded turns a cycle into "a is defined in terms of itself". A symbol bound to a function is "a
  function is not a factor"; any other symbol is itself.
- **A sum, product or power** evaluates its operands, is rebuilt and canonicalised, and then its
  vector and matrix arithmetic is carried out (**Vectors and matrices**). A lambda operand is "a
  function is not a factor"; a set, list, truth-value or domain operand is "sets carry no
  arithmetic" ("lists …", "truth values …", "domains …").
- **An application** of a builtin function evaluates its arguments, then the function
  (**Functions on numbers**), or a linear-algebra function's operation (**Linear algebra**); one with
  an `undefined` argument is `undefined`. A command is described under **Commands**. A reserved
  later-phase name is "grad is not available yet". A user
  function checks its arity ("f takes 1 argument"), evaluates its arguments, and evaluates its raw
  body in a frame of its own that maps its parameters to those values, with its name on the
  expansion stack, so recursion is the cycle error. Scoping is static: the frame holds only that
  call's parameters, so a nested call sees its own, and no binder of the caller reaches the body.
  An error inside the body carries the body's raw node. Any other head is "f is not a function".
- **A relation** evaluates and canonicalises both sides; the canonical form decides relations
  between numbers. A membership with a number on the left and a domain on the right is decided by
  value (`2.0 ∈ ℤ`, and a decimal is in ℚ); a right side that is not a domain is "membership in a
  set is not supported yet"; an ordering with a complex number on a side is "complex numbers are
  not ordered".
- **Brackets** are classified again by `Brackets.of` after their elements are evaluated, so `[a, b]`
  is a list once `a` is a set. A set is deduplicated by the canonical form.
- **A lambda** is a value as it stands: its body is held, and its free names are resolved when it is
  applied. Built inside a frame, it takes the frame's values into its body (capture-avoiding), so
  `f(t) := (x -> x + t); f(2)` is `x → x + 2`.
- **An unevaluated form** (derivative, integral, limit, Σ, Π) evaluates its body in its variable's
  scope (**Binders**) and its bounds, point or order as they are.

**Frames.** A value is never evaluated twice: a parameter's value is taken from the frame as it
is, so in degrees `f(t) := t; f(asin(1/2))` is `180·asin(1/2)/π`, converted once. `subs(e, x = v,
…)` evaluates each `v`, then the held `e` in a frame of the names inside the current one (so the
substitution is simultaneous, and the binders around the `subs` do not bind its names in `e`).

Then `Simplifier.simplify` runs the standard list once on the value (`Simplifier.forExpandedForms()`
when the statement is a call of `expand`, `apart` or `collect`: a command that produces an expanded
form is not undone by the automatic simplification of its own value), in a `Context` built per
statement from the environment: its assumptions, the session's `MathContext`, the trace switch, a
rewrite cap (**Limits**) and the session's numerics, whose half turn the `trig` rules read. A `RewriteLimitException` becomes the error "simplification did not
finish". When the statement is an algebra command (`expand`, `factor`, `gcd`, `lcm`) evaluated with
the trace on, the command's own trace comes first, joined to the simplifier's with `Trace.concat`; a
command inside a larger expression runs untraced. An `AlgebraLimitException` is an evaluation error
at the command, with its message. Evaluation and simplification run once each, in that order: a
builtin whose argument becomes a number only by a rule is not evaluated again. The rule sets carry
the numeric cases their own rewrites create (`abs` and `sign` of numbers, `log(1)`, `log(0)` and
`log(0, b)` as `undefined`, exact logarithms, numeric roots, an even root of a negative number as
`undefined`), so `abs(-3y)` still ends as `3|y|`, and `sin(π/6)`, which evaluation leaves for the
trigonometric set, ends as `1/2`; a function no rule set covers (`floor(x)`) stays as it is.

**Binders.** While a binder's scope is evaluated its variable is a bound name: a bound symbol is
neither looked up nor taken from a frame, so after `x := 3`, `solve(x^2 = 4, x)` receives the
symbol, and the nodes keep their identity. A static check, made once before the scope is evaluated,
decides whether the variable `v` could capture a name from outside the scope. It does when (i) a
value of the current frames holds a symbol named `v` (the names of each frame value are collected
once, by identity), or (ii) a definition the scope reaches holds one: the scope's symbols and heads,
the names in the raw bodies of the definitions they name (without a function's own parameters), and
theirs, transitively, through the environment, memoised per statement; a cycle among the definitions
is harmless here and remains the evaluation-time error. Lambda bodies inside the scope count. A name
bound to a value definition is left out of (ii), since it is expanded and never free (its own reach
stays in), so `k := 7; b := k^2; sum(k b, k, 1, 3)` is `∑[k = 1, 3] 49k`, unless it occurs in the
body of a lambda (in the scope or in a body reached), which is held and keeps the name free: `k :=
7; f(t) := (x -> x + k); sum(f(1), k, 1, 3)` is `∑[k₁ = 1, 3] (x → k + x)`. The check is
conservative: it looks at every occurrence of `v`, free or bound. When it finds one, the binder is
renamed (`Substitution.renameBinder`) to `base_k`, the base being `v` without a `_digits` suffix,
for the least `k ≥ 1` whose name occurs in neither the binder, the names of the enclosing binders,
the environment's definitions, the frame values nor the definitions the scope reaches, so it cannot
capture either (a variable `k_1` becomes `k_2`); nothing is evaluated twice. With `b := k^2`, `sum(k
b, k, 1, 3)` is `∑[k₁ = 1, 3] k² k₁`; with `f(t) := t + k`, `sum(f(1), k, 1, 3)` is `∑[k₁ = 1, 3]
(k + 1)`; `f(t) := sum(t k, k, 1, 3); f(k)` is `∑[k₁ = 1, 3] k k₁`; and nested binders of one name
over `a := k` become `k₁`, `k₂`, … in turn. The binders are a lambda, the unevaluated forms and the
commands with a bound variable, whose scope is the arguments before the variable
(`Builtin.binderFor`).

**Limits.** At most `Evaluator.MAX_DEPTH` (1,000) nested evaluation steps, counting descents into
operands and expansions together, and `MAX_EXPANSIONS` (200) definitions and calls being expanded at
once ("expression nested too deeply", "definitions nested too deeply"); and a value is at most
`Parser.MAX_NESTING` (200) levels deep ("expression nested too deeply"), the bound every later walk
of a tree relies on. A definition's memoised value and the statement's value have at most
`MAX_NODES` (1,000,000) nodes, a shared subtree counted at each occurrence as every walk will
("result too large"), so `h_k := [h_{k−1}, h_{k−1}]` up to `h30` is an error, not an
`OutOfMemoryError`; each is counted once, and a call's value is not counted, so passing a large
value through calls costs nothing. The same cap applies **while** a vector or matrix sum, product,
scalar multiple or power is built (**Vectors and matrices**), so a chain of symbolic matrix products
is refused at the first product that passes it rather than after exhausting the heap. The simplifier
may make `Evaluator.REWRITE_CAP` (10,000) plus `REWRITE_CAP_PER_NODE` (20) per node of the
statement's value rewrites, so a long sum of radicals and logarithms simplifies while a rule set
that loops still stops quickly on small input. An algebra command's own rewriting (`expand`,
`factor`, `gcd`, `lcm` and the others) has the same cap, counted over the nodes of the call's
evaluated arguments. The same walk over the statement's value and each memoised definition value
refuses an exact integer of more than `Lexer.MAX_DIGITS` digits, or a rational whose numerator or
denominator has more than `Canonical.MAX_FOLD_DIGITS` (315,653, the digits of the fold limit), a
complex number's parts included ("number too large to display"), so every value shown reads back in
reasonable time: reading a fraction back costs the gcd of its terms, quadratic in their length, a
few seconds at that size, while an integer of a million digits reads in about a second. The test is
a bit length below three times the digits or a comparison with the power of ten. The canonical form
folds powers only up to `Canonical.MAX_FOLD_BITS`, but products of numbers are unbounded, which is
why the check is on the value. Intermediate values are not checked, so `a·a/a` with a large `a` is
fine. An `ArithmeticException` from the tower or the canonical form (`1e999999999` cubed) is "number
out of range at this precision". The tests run these limits on the 1 MB test stack.

### Functions on numbers

`Functions` evaluates a builtin function whose arguments are all numbers; with any other argument
the call stays, except for `gcd`, `lcm`, `numerator` and `denominator`, which take polynomials and
structural fractions (**Commands**: so `gcd(1/2, 2)`, with a rational argument, is the algebra's
`1/2`). When every argument is exact, the call evaluates only to an exact result:

- the tower's exact operations: `abs` (an exact complex modulus as `(re² + im²)^(1/2)`), `sign`,
  `floor`, `ceil`, `round`, `trunc` and `mod` (`a − b·floor(a/b)`) on reals, `gcd` and `lcm` on
  integers, `factorial` and `binomial` on integers while the result is under
  `Canonical.MAX_FOLD_BITS`, `min` and `max` on reals, `re`, `im`, `conj`, `arg` of a real (`0` or
  `π`), `numerator` and `denominator` of a rational, `cbrt` and `root(x, n)` when the root is exact;
- the values at 0 and 1 that need no rule: `exp(0)`, `log(1)` (and `ln`, `log10`, `log2`),
  `log(x, x)`, `sin`, `tan`, `sinh`, `tanh`, `asin`, `atan`, `asinh`, `atanh` of 0, `cos`, `sec`,
  `cosh`, `sech` of 0, `acos(1)`, `acosh(1)`, `erf(0)`, `erfc(0)`, and `atan2` on the axes.

Everything else stays (`sin(1)`, `sin(π/6)`, `exp(2)`, `log(8, 2)`): exact values are the rule
sets'. A domain error is `undefined`: `mod(1, 0)`, `factorial(-1)`, `root(-4, 2)`, `log(0)`,
`cot(0)`, `atan2(0, 0)`, `log(x, 1)`.

When any argument is inexact, the call evaluates through `Elementary` at the precision. `mod` is `a
− b·floor(a/b)` computed exactly on the decimals' values and rounded once, so `mod(1e100, 3.0)` is
`1.0`. `gcd`, `lcm`, `binomial` and `factorial` of integer-valued decimals are their exact values
made inexact (`binomial(5.0, 2)` is `10.0`); a decimal that is not an integer, a `factorial` beyond
the fold limit (`factorial(1e6)`) and `numerator`/`denominator` of a decimal (`numerator(0.5)`)
stay. `erf`, `erfc`, `gamma` and `beta` of reals are computed in `double` through the `Distribute`
provider, at most 16 digits (fewer at a lower precision), `undefined` at a pole of `Γ` (**Simplification**,
`special`). The functions the tower lacks are composed at 10 guard digits and rounded once: `cot`, `sec`,
`csc` and their hyperbolic forms as quotients, the inverse reciprocals through `atan`, `acos` and
`asin` of `1/x`, `log(x, b)`, `log10`, `log2` and `atan2`. The six inverse hyperbolic functions are
logarithmic formulas (`asinh x = ln(x + √(x² + 1))`, `acosh` in Kahan's form and `i·acos(x)` on
`[-1, 1)`, `atanh x` and `acoth x` from `½ ln |(1 + x)/(1 − x)|`, `asech x = ln((1 + √(1 − x²))/x)`
on `(0, 1]`, `acsch x = sign(x)·ln((1 + √(1 + x²))/|x|)`) whose differences with 1 are formed
exactly on the decimal argument, evaluated at rising working precisions until two evaluations agree
to a hundredth of an ulp, as the tower's own functions are, so they are faithful near the points of
cancellation (`acoth(1 + 10^-30)`). Two zero evaluations are accepted only where the value is zero
by the argument (`asinh(0)`, `atanh(0)`, `acosh(1)`, `asech(1)`); elsewhere a zero is a cancellation
not yet resolved and the precision keeps rising, so `acosh(1 + 10^-140)` is `1.414…e-70`. Past the
precision's reach the leading term is the value: `asinh` and `atanh` of a tiny `x` are `x`, `acoth`
and `acsch` of a huge one `1/x`. A complex argument takes the reciprocal forms through `atanh`,
`acosh` and `asinh`. A zero argument of a logarithm or a reciprocal is `undefined`; any other
failure of the tower (an argument too large to reduce, a result out of range) is the error "number
out of range at this precision".

**Outside the real domain.** A real argument outside an inverse function's real domain gives a
complex value, by the convention of **Numbers** (**Domains and branch cuts**): the value of the
principal formula, with the principal `log` and `sqrt`, at the real point, which is also
Mathematica's value. `asin z = −i·log(iz + √(1 − z²))`, `acos z = π/2 − asin z`, `acosh z = log(z +
√(z − 1)·√(z + 1))`, `atanh z = ½(log(1 + z) − log(1 − z))`, `acoth z = atanh(1/z)`, `asech z =
acosh(1/z)`, `acsch z = asinh(1/z)`. So `asin(2.0)` is `π/2 − 1.3169…i`, `acos(2.0)` is `1.3169…i`,
`log(−1.0)` is `πi`; `acosh(0.5)` is `1.0471…i` and `acosh(−2.0)` is `1.3169… + πi`; `asech(2.0)` is
`1.0471…i`; `atanh(x)` has the imaginary part `−π/2` for `x > 1` and `+π/2` for `x < −1`
(`atanh(2.0)` is `0.5493… − 1.5707…i`), and `acoth(x)` likewise by the sign of `x` inside `(−1, 1)`
(`acoth(0.5)` is `0.5493… − 1.5707…i`, `acoth(−0.5)` is `−0.5493… + 1.5707…i`). A property checks
each inverse hyperbolic function at random real points outside its domain against its formula
through the tower's complex `log` and `sqrt`.

**Angle mode.** In degrees or gradians a trigonometric function converts a plain-number argument
(an integer, rational or decimal) to radians, exactly: `sin(30)` becomes `sin(π/6)`, which is not
plain and is never converted again. An inverse function of a plain number answers in the mode's
unit: `asin(1/2)` becomes `180·asin(1/2)/π`, and `asin(0.5)` is `30.0`. The conversion is applied
once, during evaluation; `numeric` and the rules then work in radians. A value holding an inverse
function of a plain number, typed in again in degrees, is converted again: the printed form does
not record the mode.

**Degrees, minutes and seconds** (`AngleFunctions`, `docs/language.md` §14.18). `dms(x)` of a real
number (degrees in every mode) or of a rational multiple of `π` (radians, times `180/π`): `d` the
floor of `|x|`, `m` the floor of the remainder times 60, `s` the rest times 60, exact for exact input
and a decimal for a decimal, as the bracket constructor's vector `⟨d, m, s⟩`, the sign of `x` on the
first component that is not zero; any other argument leaves the call. `degrees(d, m, s)` of three
real numbers is `±(|d| + |m|/60 + |s|/3600)` with the sign of the first that is not zero; a negative
component after it is the error "only the first component of degrees that is not zero carries a
sign", a complex one "degrees needs real numbers", and a non-number leaves the call.

### numeric and exact

`numeric(e[, digits])` checks `digits` (an integer from 1 to 10,000), evaluates `e` at that
precision, then runs `NumericForm` over the value with 10 guard digits and rounds each decimal of
the result to `digits`. The pass makes every exact number inexact, replaces `π` and `e` by their
values, canonicalises the arithmetic (which folds it) and evaluates every builtin function over
numbers through the inexact path, in radians. Symbols, `∞`, `undefined`, truth values and lambda
bodies stay, and so do integer exponents, a `root`'s integer degree (the inexact `root` takes an
integer one, so `numeric(root(5, 3))` is `1.7099…`, not `root(5.0, 3.0)`) and the variables,
bounds, points and orders of the unevaluated forms, whose bodies are made numeric:
`numeric(x^2 + 1/3)` is `x² + 0.333…` and `numeric(sum(k/3, k, 1, n))` is `∑[k = 1, n] 0.333…k`.
A definite integral the exact path leaves, with numeric bounds, is evaluated by quadrature at the
digits instead (**Numeric integration**). A command's held arguments stay. The cost
grows quickly with the digits: `numeric(π, 10000)` takes about twenty seconds, which the app's
worker must be able to cancel.

`exact(e)` turns every decimal in the value into the rational its digits denote (`0.1` is `1/10`,
`1.5e3` is `1500`), complex parts included; a decimal exponent beyond 100,000 either way is "number
out of range at this precision".

### Vectors and matrices

`LinearAlgebra` works on canonical sums, products and powers, whose vectors and matrices the
canonical form keeps in typed order; entries may be symbolic.

- A **sum** adds its vectors, or its matrices, element by element; lengths and shapes must agree,
  and a scalar added to a vector or a matrix is an error.
- A **product** multiplies its scalar factors into the entries and folds its vectors and matrices
  left to right: vector times vector is the dot product, matrix times matrix needs matching inner
  dimensions, matrix times vector is a vector, vector times matrix is the row vector's product. The
  `0·M` the canonical form keeps becomes the zero vector or matrix.
- A **power** of a square matrix to a positive integer is multiplied out by squaring, for numeric
  entries while the result stays under the fold limit and for symbolic entries up to the 16th
  power; `M^0`, which the canonical form keeps for a non-commuting base, is the identity matrix;
  `M^-k` is the `k`-th power of `Linalg.inverse(M)` (**Linear algebra**) under the statement's
  assumptions, a singular matrix the error "the matrix is singular"; other exponents stay. A power of a vector or of a non-square matrix is an
  error, and so is a vector or a matrix as an exponent ("a vector cannot be an exponent", "a
  matrix cannot be an exponent"); a set or list exponent never reaches this check, since the
  evaluator refuses it as an operand first ("sets carry no arithmetic").
- `cross` multiplies two scalars and crosses two 3-vectors ("× needs two 3-vectors" otherwise);
  `dot` of two vectors of one length is their dot product, of two symbols it stays; `nth(c, k)`
  takes the `k`-th item of a list or vector or row of a matrix, checked; `length` counts the items
  of a list, vector or set, or a matrix's rows.

The operands of a node are reduced first, since the canonical form can build new arithmetic below
the top (`(M^a)^n` as `M^(a·n)`).

**Size.** Each vector or matrix built by a sum, a product, a scalar multiple or a squaring of a
power keeps a running count of the nodes of the entries built so far (per occurrence, as
`MAX_NODES` counts), and is refused with "result too large" as soon as the count passes
`MAX_NODES` (**Limits**), before its remaining entries are built: building one entry costs at most
its operands, which are under the cap, so the memory used stays near the cap. Only the actual
count decides, so a result that fits is never refused; an intermediate product of a chain or a
power that passes the cap is refused even when the final value, after cancellation, would fit.
With a symbolic 6×6 `M`, `M*M*M*M*M*M*M*M` is refused in about 0.7 s (it ran 15 s to the same
error, or out of memory, when only the finished value was checked) and `M^16` in about 0.5 s,
while `M^12` still evaluates.

### Commands

| Command | Evaluation |
|---|---|
| `solve` | the equations evaluated in the scope of the unknowns, then solved (**Solving**) |
| `integrate`, `∫` | the scope and the bounds as below, then an `Integral` is integrated (**Integration**) under the facts without the bound names, with the default providers and the session's numerics; an indefinite integral's variable is free in its result as a derivative's is (a value the written name has is put in, a fresh name renamed back), a definite integral binds it, and its value is folded as evaluation leaves a value (functions of decimals evaluated, `log(15.625)` at the bounds a decimal, so evaluating the value again changes nothing), the trace carried to it by a normalisation at the root; an `integrate` call whose variable is not a name stays; a limit of the cap is "integration did not finish (rule set …, rule …)"; then, under the quadrature digits, a definite integral that stays is evaluated numerically (**Numeric integration**) |
| `sum`, `product`, `Σ`, `Π` | the scope and the bounds as for those; for integer bounds with at most 10,000 terms the terms themselves, each the body as written in a frame of the index under the session's settings, added in pairs (**Series and sums**, **Term by term**); otherwise a `BigSum` or `BigProduct` through `Calculus.sum` (**Series and sums**) under the assumptions without those of the enclosing binders' names and the session's numerics in radians; a call that makes no node stays; the cap is "the sum did not finish (rule set …, rule …)"; the value is free of the index, and at the top of a statement its trace comes first |
| `limit`, `lim` | the scope, the point and the side as for those, then a `Limit` node through `Limits.limit` (**Limits**) under the assumptions without those of the enclosing binders' names, the session's numerics in radians and a cap of `REWRITE_CAP + REWRITE_CAP_PER_NODE` per node of the limit; a `limit` call that makes no node stays; a side at `±∞` is the error "a limit at ∞ or -∞ has no side", the algebra's limits errors, the cap "the limit did not finish (rule set …, rule …)"; the value is free of the variable, and at the top of a statement its trace comes first |
| `taylor` | the scope, the point and the order as for those; an order with a symbol in it leaves the call, any other that is not a non-negative integer is "taylor's order must be a non-negative integer", an infinite point "taylor's point must be finite"; otherwise `Calculus.taylor` (**Series and sums**) under the assumptions without those of the enclosing binders' names and the session's numerics in radians, a `TaylorException` an error with its message, the cap as for `diff`; the variable free in the result, as a derivative's (below) |
| `arclength area volume shellvolume extrema maximize minimize` | the scope evaluated with the variable bound, the bounds evaluated; a variable a bound names is renamed to a fresh name first, as for a definite integral; then `Applications.of` (**Applications**) under the facts without the bound names and the variable, with the default providers, the session's numerics and the cap for the call's size; a wrong argument is the command's error, a limit of the cap "… did not finish (rule set …, rule …)"; at the top of a statement its trace; then an integral the command leaves is evaluated numerically under the quadrature digits, as `integrate`'s is |
| `dsolve` | the second argument read as written, `y(x)` or a list of such in one variable (else "dsolve needs an unknown function y(x), …"); the equations evaluated with the unknown functions declared (an application of one evaluates its arguments and stays) and the variable bound, the initial conditions with the functions declared; then `Dsolve.solve` (**Ordinary differential equations**) under the assumptions without facts about the functions and their primed names, the session's numerics in radians and the cap for the call's size; the constants of integration below; its trace kept when the call is the statement; an `IllegalArgumentException`, the algebra's limits and the cap ("dsolve did not finish (rule set …, rule …)") are errors at the call |
| `odesolve` | the trailing relations `steps = n` and `at = x₀` read as written (once each, not both), the rest as for `dsolve` with the last two arguments the interval's ends, evaluated; `steps` an integer from 1 to `NumericOde.MAX_STEPS`; then `NumericOde.solve` (**Numeric ODEs**) under the assumptions without facts about the functions, the session's numerics in radians and its settings (compiled in radians); its `IllegalArgumentException` is the error at the call; the value marks the statement numeric, and at the top of a statement its trace is the one step `numeric.ode` (`digits_` 16) |
| `f'(a)`, the prime notation | an unknown function a `dsolve` around declares stays applied; otherwise `f` must be a user function of one argument (else "f is not a function", or "the prime notation needs a function of one argument"), and the value is that of `Derivative(f(p), p, n)` in a frame mapping its parameter `p` to the evaluated `a`, `n` the number of primes: the body differentiated and the value put in, as for `f(x) := diff(…, x)` |
| `diff`, `d/dx` | the scope, the order and the typed node as for those (`diff(x^2, x, 1 + 1)` is the `Derivative` of order 2), then a `Derivative` is differentiated (**Differentiation**), its variable free in the result (below); a `diff` call whose order is not a positive integer stays; a differentiation that exceeds its cap is "differentiation did not finish (rule set …, rule …)" |
| `expand factor cancel collect(e, x)` | the argument evaluated (`collect`'s variable held), a relation, list, set, vector or matrix item by item; the call when the value or an item is not a polynomial in scalar kernels (`Polynomial.isScalar`: `∞`, `undefined`, a domain, a truth value, a lambda, a collection or a matrix inside a sum, product or power) or the algebra refuses it; otherwise `Algebra.expand`, `factor`, `cancel`, `collect` (**Algebra**); an `expand` or `factor` at the top of a statement keeps its trace |
| `apart(e, x)` | `x` held and a symbol ("apart needs a variable"), `e` evaluated; the call when `e` is not scalar or `Algebra.apart` refuses it (a relation or collection included) |
| `gcd lcm` (functions) | on numbers the tower's, as above, when every argument is an integer or any is a decimal; the call itself when an exact number stands beside a non-number (`gcd(x, 6)`, `gcd(2x, 4)`, `gcd(x, 0)`: the integer gcd at each integer, not the polynomial gcd), except, when every number is an integer, `gcd(u, ±1)`, which is 1, and `lcm(u, 0)`, which is 0, identities for both with their signs (`gcd(x, 1, 1/2)` stays: `gcd(4, 1, 1/2)` is the gcd of contents, `1/2`); otherwise `Algebra.gcd`/`lcm` (exact rationals included: `gcd(1/2, 1/3)` is `1/6`), the call when refused; at the top of a statement the provider's trace |
| `numerator denominator` (functions) | on a number the tower's; otherwise structural on the canonical value: of a product, the factors with non-negative exponents (and a rational coefficient's numerator) and the bases of the others to their positive exponents (and its denominator); of `b^-n`, `1` and `b^n`; of anything else, the value and `1`; the call for a value that is not scalar (a vector, matrix, list, set or relation) |
| `simplify steps` | the value of the argument; `steps` at the top of a statement turns the trace on |
| `subs(e, x = v, …)` | the values evaluated, substituted together into the held `e`, then evaluated; a pair that is not `name = value` is "subs needs name = value" |
| `numeric exact` | as above |
| `assumptions()` | the relations in force, as a set |
| `assume clear` | statements only (**The session**); in an expression, "assume works only as a statement" |
| `det(M[, method])` | `M` evaluated, the method held (a word, as `limit`'s side); then `Linalg.apply` (**Linear algebra**) under the statement's assumptions and precision, its trace kept when the call is the statement (or the argument of a `numeric` that is); a wrong argument is the operation's error at the call; the eigen-theory functions run the same way with the quadrature digits as the numeric rule's, and a numeric outcome marks the statement as a quadrature does |
| `charpoly(M, x)` | as `det`, the variable held |
| `help`, and `grad slider` | "help is not available yet", "grad is not available yet" |

**The derivative's variable is free in its result.** `diff(f, x)` binds `x` in `f`, but its value is
a function of `x` (`docs/language.md` §14.10). After `Calculus.differentiate` (under the assumptions
without those of the enclosing binders' names, with a cap of `REWRITE_CAP + REWRITE_CAP_PER_NODE`
per node of the derivative), when the name as written has a value where the derivative stands (a
parameter of the current frame, or a value definition, and no enclosing binder of the name), the
value is substituted for the variable under its **current** name, fresh or written: `f(x) :=
diff(x^3, x); f(2)` is `12`, `subs(diff(x^2, x), x = 3)` is `6`, after `x := 3`, `diff(x^2, x)` is
`6`, and `f(x, y) := diff(x^2 y, x); f(2, x)` is `4x`, since the `x` that came in through `y` is not
the variable. Then a fresh `v_k` the capture check gave the variable is renamed back to the name as
written, unless that name occurs free in the result, where the fresh name stays: `f(y) := diff(x^2 +
y, x); f(x)` is `2x`, `f(y) := diff(x y + x^2, x); f(x)` is `x + 2x₁`. The result is then
**folded**: evaluated once more with every symbol taken as itself and functions taking their
arguments in radians, which evaluates the functions the rules introduced on numbers (`log(2.0)`)
without looking a name up again or converting an angle a second time. A derivative left in the
result is not differentiated again. When the statement is the derivative itself (or `steps` of it),
its trace precedes the simplifier's (`Trace.concat`), provided the result is the one the trace ends
with; a derivative nested in a larger statement brings no steps in Phase 1.

**solve.** The unknowns are bound while the equations are evaluated, as for every binder; the
capture check may rename one (**Binders**), and in a set of solutions a renamed unknown is renamed
back to its written name, unless that name occurs free in the solutions, where the fresh name stays
everywhere (set members, left sides, free unknowns): `m(t) := solve({x + y = 2, x - y = 0}, [x, y]);
m(x)` is `{[x = 1, y = 1]}`, but `k(t) := solve(x + y = t, [x, y]); k(x)` is `{[x₁ = x - y]}`. An
unevaluated call keeps the fresh name. The solver runs under the assumptions
without the unknowns' facts, the statement's precision and a cap of `10,000 + 20 × nodes` of the
equations, with `SessionNumerics`, the `numeric` pass (**numeric and exact**) as the context's
numeric values, radians only in radian mode. The solver's argument errors and the algebra's limits
are evaluation errors at the call; the solutions are not evaluated again. When `solve` is the whole
statement, or the argument of `steps`, its trace comes first in the statement's trace, joined to
the simplifier's by `Trace.concat`; nested in a larger expression its steps are not shown.

**A general solution's parameter.** The evaluator names the integer parameter a periodic
equation's solutions may use (**Solving**, **Trigonometric equations**): `k`, else `k_1`, `k_2`, …
for the first not taken, a name being taken when the held equations or their values mention it,
it is an unknown, it is bound by an enclosing binder, it has a definition, it has facts other than
exactly `k ∈ ℤ` (so a later statement's `solve` uses an earlier one's `k`), or an earlier `solve` of
the same statement took it (two parameters, both in the note). A name the held equations do not
mention that has a value in the current frame (a `subs` pair, a function's parameter) is not taken:
when the solutions hold it, that value is put in, as a derivative's variable's is, so
`subs(solve(sin(x) = 1/2, x), k = 0)` is `{π/6, 5π/6}`; when they mention it, the frame value is
the input's and the parameter is `k_1`. The solver
runs with `k ∈ ℤ` added to its facts when `k` has none. A parameter left in the value is collected,
and `Evaluator.Result.parameters` holds the canonical set of the relations `k ∈ ℤ` of those that
occur in the statement's value; the statement's simplification runs with them assumed.

**A constant of integration** (Phase 7b) is named as the parameter is: `C_1`, `C_2`, … for the first
not taken, a name being taken when the held call or its value mentions it, it is an unknown, it is
bound, it has a definition, it has facts other than exactly `C ∈ ℝ`, or this statement took it.
A name the held call does not mention that has a value in the current frame (a `subs` pair, a
function's parameter) is not taken, and its value is put in for it in the result, as a parameter's
is: `subs(dsolve(y'(x) = y(x), y(x)), C_1 = 0)` is `y(x) = 0`. `Dsolve` draws them in order as a
class needs them and assumes each real; those left in the value join `Evaluator.Result.parameters`
as `C_1 ∈ ℝ`, with which the statement's simplification runs and which the session records as it
does `k ∈ ℤ`.

### The session

`Session` holds the current `Environment`, the one mutable object of evaluation; it is not
thread-safe. `run(Statement)` returns an `Outcome`: a definition binds its name and gives
`Defined(echo)`; a top-level `assume(rel, …)` records each relation with its left side held and its
right side evaluated, through `Assumptions.with` (whose rejection becomes the error, with its
message), and gives the set of the relations recorded; a top-level `clear(name)` unbinds the name
(the assumptions stay) and gives the symbol; any other expression is evaluated and gives
`Value(value, trace, quiet, numeric, parameters)`, `parameters` the set of relations `k ∈ ℤ` of a
general solution's integer parameters and `C ∈ ℝ` of `dsolve`'s constants in the value (empty for
any other), each of which the
session then records in its assumptions when the parameter has no fact yet, as if `assume(k ∈ ℤ)`
followed the statement. An evaluation error gives `Failed(error)` and leaves the environment
as it was. Nothing else leaves `run`: any other `RuntimeException`, a defect, becomes
`Failed("internal error: <class>: <message>")`, so no caller can crash; the tests run a list of
probes and require that none gives one. An `OutOfMemoryError` becomes the failure "result too
large": a backstop for a path the size limits miss, not the mechanism, since the limits refuse a
value before it is built (the statement's state is unreachable once the error has unwound, so the
session goes on). `run(Script)` runs every statement in order, past failures.
`run(Statement, Cancellation)` and `run(Script, Cancellation)` run under a request to stop
(**Cancellation**); the methods without one run under `Cancellation.NEVER`. `setPrecision` and
`setAngleMode` change the settings, which the language has no syntax for; `setTracing(true)`
evaluates every expression statement with its trace, as `steps(…)` does one (the CLI's `--steps`);
`names()` is what the next statement is parsed with.

### Cancellation

The app evaluates on a worker and stops a statement on request (`PLAN.md` §5.5). The engine's
side is cooperative: `mathcanvas.engine.Cancellation` is a token whose `requested()` the caller
sets (a volatile read), with `Cancellation.NEVER`, and whose `check()` throws
`CancelledException` when it is requested **or the current thread is interrupted**, so
`Future.cancel(true)` works without a token; the interrupt flag is read, never cleared. Both live in
the root package, which imports nothing from the engine, so `number` can check it.
`CancelledException` extends `RuntimeException` directly and carries no stack trace, so no catch
of an engine failure (`ArithmeticException`, `IllegalArgumentException`, the limits) takes it.
The token is explicit state: `rule.Context` carries it, and the numeric layer takes it as a
parameter; there is no thread-local and no clock.

It is checked:

- by the evaluator at every evaluation step (`State.eval`) and in its canonical forms, and passed
  in every context it builds (the simplifier, the algebra commands, `solve`, the derivative) and
  in the solver's numeric values; its vector and matrix arithmetic checks at every entry of a
  product, every entry added or scaled and every squaring of a matrix power;
- by the rewriter at every node visit and before every rule (**The rewriter**);
- in the numeric layer at every iteration of an agreement loop, the evaluator's inverse hyperbolic
  one included, every series term and every node of the `numeric` pass (**Elementary functions**);
- in the algebra, through the context the providers receive: every operand converted and every
  term multiplied in `Polynomial.of` (so an expansion), every step of a remainder sequence and of
  the contents in `Gcd`, every step of Yun's algorithm, every candidate root, Kronecker degree and
  combination, and every bivariate subset in `Factoring`, every node of `RationalFunction.of`, and
  every factor, every step of a factor's expansion and every step of the extended Euclidean
  algorithm in `PartialFractions`;
- in the solver: every Sturm-sequence step and bisection, every iteration of Brent's method, every
  Durand–Kerner sweep and Newton polish step, every point of the scan, every Bareiss row;
- in `Display.of(e, cancellation)` at every member of a set of constants valued (**Printing**);
- in numeric integration at every level, node and sample of the double-exponential rule and at
  every bisection of Gauss–Kronrod (**Numeric integration**).

A check costs a volatile read and the interrupt flag's read, 2 to 5 ns, against hundreds of
nanoseconds of work at every check point. The stopping time is that of the work between two
checks: measured from the request to the return at every check point of `SessionCancellationTest`,
it was 20 to 150 µs on each statement there (a symbolic 6×6 matrix to the 12th, 38 µs; `apart` of
a denominator of degree 60, 21 µs; the largest, an occasional 0.7 ms under load), and 0.55 ms for
a thread interrupt. Exactly two residuals stay without a check: the matcher's backtracking within
one rule attempt, and a numeric fold of the canonical form reached from inside a rule (the rules
canonicalise without the token).

**The session.** `Session.run(statement, cancellation)` checks the token first, so a statement
cancelled before it starts does nothing; a `CancelledException` from anywhere in the statement
gives `Outcome.Cancelled()` and restores the environment taken before it (the session assigns it
only after a statement completes, so the restore is a guard), and the `RuntimeException` catch of
**The session** never takes it. `run(script, cancellation)` keeps one outcome per statement: the
statements before the cancelled one keep their effects, and it and every later one give
`Cancelled`. The engine never cancels on its own; the CLI, which never cancels, reports the outcome
as `statement cancelled` (`docs/reference/cli.md`, **Errors**).

### The simplifier

`Simplifier.standard()` is the list of rule sets every value is simplified with
(**Simplification**). `Simplifier.simplify(e, context)` runs the list as one combined rule set
through the `Rewriter`, so the rules of every set share one fixpoint and one cap; a rule name must
be unique across the list, and each rewrite in the trace names the set its rule came from.
`Simplifier.forExpandedForms()` is the list without the sets that would undo an expanded form, for
the value of a top-level `expand`, `apart` or `collect`.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/eval/` and `…/simplify/`, most through
the test helper `Run`, which runs a script in a new session:

- `EnvironmentTest`, `SimplifierTest`, `SubstitutionTest`, `SessionTest`;
- `EvaluatorTest`: definitions, cycles, calls, relations, membership, brackets, binders and capture,
  commands, the errors and the node they carry;
- `AlgebraCommandsTest`: the algebra commands' values, refusals, limits and traces;
- `FunctionsTest`: every function on exact, inexact and symbolic arguments, undefined values, the
  angle modes;
- `NumericExactTest`, `LinearAlgebraTest`, `EvaluationExamplesTest` (§17.3 and §14);
- `SessionCancellationTest`: one statement per checked loop cancelled at check 1, every third
  power and the last, each giving `Outcome.Cancelled` with the environment unchanged and the time
  from the request to the return within the whole-run budget; a statement cancelled before it
  starts, a script cancelled in its third statement, and a thread interrupt on `numeric(pi,
  10000)`; `CancellationTest` (in the root package) the token and the context's withers;
- `MatrixSizeTest`: a chain of symbolic 6×6 products refused while it is built, a 4×4 chain that
  fits displayed exactly as before, `M^16` refused within its last squaring, and the session's
  backstop for `OutOfMemoryError`;
- `EvaluationDepthTest`: the deepest parse, the limits, a 10,000-term sum, 30 and 120 nested
  binders of one name (milliseconds), and 150 identity calls on a value of 900,000 nodes
  (about half a second), a 10,000-term sum of radicals and logarithms that takes more
  rewrites than the base cap, on the 1 MB stack.

The properties are:

- `EvaluationProperties`: evaluation is deterministic and idempotent on values, over every kind of
  expression from `ExprArbitraries`, with its heads defined as functions; a value passed through
  an identity function is the value, in every angle mode; and evaluation agrees with substituting
  the raw bodies of the definitions directly, over binder-free expressions; and a simplified value
  of `ExprArbitraries.elementaryExprs()` evaluates to itself;
- `NumericProperties`: the evaluation of builtin functions is sound at random points
  (`Soundness.check`), a disagreeing point rechecked as the rule sets' properties recheck it
  (`testing.Recheck`, **Simplification**, **Tests**: `asin(tan(atan(1)))` is `π/2` at 100 digits
  though one ulp of 1 off at 34), under the same 5% cap on ill-conditioned skips; `numeric` agrees
  with `NumericEvaluator` within `10^-15` where the value is well conditioned (the evaluator agrees
  with itself at 44, 60 and 80 digits); the six inverse hyperbolic functions are within one ulp of
  their logarithmic formulas at 150 digits or more, at full-precision arguments `±(1 ± d·10^-k)` and
  `±d·10^±k` for `k` up to 200, and outside their real domains within one ulp of their principal
  formulas through the tower's complex `log` and `sqrt`; `exact` undoes `numeric` on rationals with
  terminating expansions;
- `CancellationProperties`: random scripts cancelled at a random check keep the uncancelled
  outcomes before the stop, cancel every statement from it on, and leave the environment as it was
  before the stopped statement;
- `MatrixSizeProperties`: random symbolic matrices up to 4×4 to powers up to 8 or in chains of up
  to four products evaluate or are refused as too large, and nothing else escapes (about a second;
  the refusals themselves are `MatrixSizeTest`'s);
- `LinearAlgebraProperties`: sums, multiples, dot and cross products, matrix products and powers
  against element-wise loops on the tower's numbers.

## Simplification

Package `mathcanvas.engine.simplify`: the rule sets of the standard simplifier, in `RuleSets`
(`BASIC`, `RATIONAL`, `EXPLOG`, `RADICAL`, `TRIG`), and `Simplifier`, which runs them (**Evaluation**). Every
rule is a named `Rule` through the matcher (**Patterns and rules**), its pattern written in the
language, its condition a query of the assumptions (**Assumptions**) that makes it sound over the
principal values: symbols are real unless assumed complex, and a rule valid only for positive or
non-negative values asks `isPositive` or `isNonNegative` of the bound expression. Each rule has a
narration line keyed `<set>.<rule>` in the bundle (**Narration**); the computed rules' lines use
`{before}` and `{after}`, and the valued ones' also their values (`{gcd}`, `{lcm}`). The canonical form cancels `x·x⁻¹` to 1 for any `x`, so a rule that builds
a quotient checks its denominator's known zeros first (`log-to-base-one` comes before
`log-change-of-base`, or `log(1, log e)` would become `log(1)/log(1)` and then 1). A rule that
builds an exponent (`power-of-power-nonnegative`, `power-of-even-power`, `power-of-product`,
`exp-of-log`) takes only values that commute: arithmetic with a vector or matrix is the evaluator's.

### The standard list

`Simplifier.standard()` is `UNITS`, `BASIC`, `RATIONAL`, `EXPLOG`, `RADICAL`, `TRIG`, `LOGIC`,
`SPECIAL`, run as one combined rule set, so a rule of a later set can enable one of an earlier set,
and rule names are unique across the list. The order matters only where two left sides overlap, and
there every order gives the same result: `rational`'s left sides are products with a negative power
of a polynomial, or a fractional coefficient (`3(x/2 + 1)` stays as written), and sums of such
quotients, which the innermost-first rewriter reaches only after the other sets have settled their
operands, so `trig`'s collapses act on trig kernels first (`(sin(x)² - 1)/(sin(x) - 1)` is
`-cos(x)²/(sin(x) - 1)`, not `sin(x) + 1`); the other sets meet on numeric powers (`basic`'s powers
of powers and products want a power or product base, `radical`'s rules a number) and on `abs` and
`log` arguments, which `basic` and `exp-log` treat in one direction; `trig`'s left sides are
circular, hyperbolic and inverse functions and the sums and products of them, and it comes after the
others so that their traces are unchanged; `logic`'s left sides are the boolean operators, which no
other set touches, so it comes after them; `special`'s left sides are `gamma`, `beta`, `erf` and
`erfc` calls, which no other set rewrites, and it comes last for the same reason. The forms the list
settles on: natural logarithms only (`ln`, `log10`, `log2` and `log(a, b)` become `log`), `exp(x)`
is `e^x`, `root` and `cbrt` of a non-negative radicand are powers, a numeric radical is a rational
times `n^(p/q)` with `0 < p/q < 1` and `n` a positive integer that is not a perfect power and has no
`q`-th power dividing `n^p` (so the form is unique within the 1,024-bit and prime-table bounds of
the rules below: `4^(1/3)` and `2^(2/3)` are both `2^(2/3)`), a circular function has its exact
value at a multiple of `π/12`, an angle with no whole or quarter turn in it and an argument that
does not look negative, a product of circular functions of one argument has the fewest functions, a
quotient of polynomials in kernels has no common factor and its numeric content in lowest terms, and
absolute values, signs and logarithms are pushed into the factors of products and the bases of
powers as far as the facts allow.

### basic

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `undefined-argument` | `h_(args___)` → `undefined` | `h_` a builtin function (not a command), an argument `undefined` | `abs(undefined)` → `undefined` |
| `abs-of-abs` | `abs(abs(x_))` → `abs(x_)` | | `abs(abs(log x))` → `abs(log x)` |
| `abs-of-nonnegative` | `abs(x_)` → `x_` | `x_ ≥ 0` | `abs(x²)` → `x²` |
| `abs-of-nonpositive` | `abs(x_)` → `-x_` | `x_ ≤ 0` | `abs(-3)` → `3` |
| `abs-of-closed-constant` | `abs(c_)` → `c_` or its negation, a sum's terms negated (computed) | `c_` closed (no symbol, `∞` or `undefined`, not a number), its value from the context's numerics real and of one sign at 20 and at 40 digits, clear of 0 by `10⁻¹⁵` | `abs(cos(1))` → `cos(1)`; `abs(2√2 − 3)` → `3 − 2√2` |
| `abs-of-complex-number` | `abs(z_)` → `√(re² + im²)` (computed) | `z_` a non-real number | `abs(1 + i)` → `√2` |
| `abs-of-product` | `abs(x_ * rest__)` → `abs(x_) * abs(rest__)` | | `abs(-3y)` → `3\|y\|` |
| `abs-of-power` | `abs(x_^n_)` → `abs(x_)^n_` | `n_` real | `abs(x³)` → `\|x\|³` |
| `abs-to-even-power` | `abs(x_)^n_` → `x_^n_` | `n_` even, `x_` real | `\|x\|²` → `x²` |
| `sign-of-positive`, `sign-of-negative`, `sign-of-zero` | `sign(x_)` → `1`, `-1`, `0` | `x_ > 0`, `< 0`, `= 0` | `sign(x² + 1)` → `1` |
| `sign-of-product` | `sign(x_ * rest__)` → `sign(x_) * sign(rest__)` | `x_` and `rest__` real | `sign(-2y)` → `-sign(y)` |
| `sign-times-abs` | `sign(x_) * abs(x_) * rest___` → `x_ * rest___` | `x_` real | `3 sign(x)\|x\|` → `3x` |
| `power-of-power-nonnegative` | `(x_^a_)^b_` → `x_^(a_ * b_)` | `x_ ≥ 0`, `a_` real | `√(x²)` → `x` under `x ≥ 0` |
| `power-of-even-power` | `(x_^n_)^b_` → `abs(x_)^(n_ * b_)` | `x_` real, `n_` even | `√(x²)` → `\|x\|`; `(x⁴)^(1/2)` → `x²` |
| `power-of-product` | `(x_ * rest__)^a_` → `x_^a_ * rest__^a_` | `x_ ≥ 0` | `√(4y)` → `2√y` |
| `minus-one-to-even-power`, `minus-one-to-odd-power` | `(-1)^n_` → `1`, `-1` | `n_` even, odd | `(-1)^n` → `1` for an even `n` |
| `zero-to-positive-power` | `0^x_` → `0` | `x_ > 0` | `0^y` → `0` under `y > 0` |

A positive (or non-negative) factor leaves the argument of a product unchanged, so one such factor
is enough for `power-of-product`, whatever the rest: `√(4y)` is `2√y` for every `y`, while `√(xy)`
splits only under `x ≥ 0` or `y ≥ 0`.

### rational

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `cancel-common-factor` | `n__ * d_^k_` → the product with the first common factor cancelled (valued: `gcd`) | `d_` not a number, `k_` a negative integer; a numerator factor `N^m` and a denominator factor `D^k` whose provider gcd has a non-constant primitive part `g` | `(x² - 1)/(x - 1)` → `x + 1`; `x/(x² + x)` → `1/(x + 1)`; `(x + 1)⁵/(x² - 1)` → `(x + 1)⁴/(x - 1)` |
| `cancel-common-content` | `n_ * rest__` → the product with primitive polynomial factors and one coefficient (valued: `gcd`) | a quotient whose numeric content is not in lowest terms (below) | `2/(2x + 2)` → `1/(x + 1)`; `(x/2 + 1)/3` → `(x + 2)/6`; `(6x + 6)/(4x - 2)` → `3(x + 1)/(2x - 1)` |
| `cancel-denominator-content` | `d_^k_` → the same, for a negative power on its own (valued: `gcd`) | `d_` not a number, `k_` a negative integer, the content as below | `1/(x/2 + 1)` → `2/(x + 2)` |
| `combine-fractions` | `c___ * d_^k_ + e___ * f_^j_ + rest___` → one fraction (valued: `lcm`) | `k_`, `j_` negative integers; every fraction term combines and the result is no larger | `1/(x - 1) - 1/(x + 1)` → `2/(x² - 1)`; `1/(x + 1)² - 1/(x + 1)` → `-x/(x + 1)²`; `(x² - 4)/(x + 2) + 2` → `x` |

Numerators and denominators are polynomials over ℚ in the algebra's kernels (**Polynomials and
kernels**), so `log(x)` and `sin(x)` are opaque variables (`(log(x)² - 1)/(log(x) - 1)` is
`log(x) + 1`) and every identity holds wherever the kernels are defined. Every gcd comes from the
gcd provider (`Providers.attempt`, **Providers**), normalised; when no provider answers, a node is
no polynomial in scalar kernels (`(x - ∞)/(x² - ∞)`), or the algebra's bounds are passed
(`(x^20000 - 1)/(x - 1)`), the rule declines. No assumption is consulted: polynomial cancellation is
unconditional, and like `cancel` (**Rational functions and partial fractions**) it removes a
removable singularity, `(x² - 1)/(x - 1)` being `x + 1` also at 1, but never adds a pole. The value
taken at a removed singularity need not be the limit there when a kernel is discontinuous at it:
`a·sign(a)/a` is `sign(a)`, 0 at `a = 0`, as `cancel` also gives it. A whole cancellation can grow
the expression (`(x^10 - 1)/(x - 1)` is the ten-term sum), as `cancel` does.

`cancel-common-factor` cancels the primitive part of the gcd only, so the contents stay where they
are and a factored numerator or denominator stays factored. `cancel-common-content` then works on
the whole quotient: with the coefficient `P/Q`, the numerator's content `P·Π content(Nᵢ)^mᵢ = a/b`
and the denominator's `Q·Π content(Dⱼ)^|kⱼ| = c/d` in lowest terms, it fires when `b ≠ 1`, `d ≠ 1`
or `gcd(a, c) ≠ 1`, which is `cancel`'s criterion once the primitive parts are coprime, and makes
every polynomial factor primitive with the whole content as one coefficient; its value `gcd` is
`gcd(a, c)/lcm(b, d)`, the integer factor cancelled over the multiplier that clears the fractions.
So `(2x + 2)/(3x - 3)` and `1/(2x + 2)` stay, as `cancel` gives them. Only a quotient takes part, a
product with a negative power of a polynomial or a fractional coefficient, so `3(x/2 + 1)` stays as
`cancel` leaves it; `cancel-denominator-content` is the same function on the other node shape, a
negative power on its own, which is no product. A gcd from the provider that does not divide its
arguments is a provider bug, not a decline: the exact division by it throws. A factor's sign is kept (`1/(1 - x)` stays), since a sign normal form for denominators is out of
scope. The rewriter visits a product's factors first, so a negative power inside a product is made
primitive by `cancel-denominator-content` before the product is seen: `(x - 2)²/(2(x - 1)(x +
1/2))`, whose whole content is already 1, becomes `(x - 2)²/((x - 1)(2x + 1))`, no larger.

`combine-fractions` writes the sum over a common denominator with the numerator expanded. It builds
the lcm of the terms' denominators expanded, with the primitive part of its gcd with the numerator
cancelled. When the distinct denominator bases are pairwise coprime (the primitive parts of their
gcds are 1), it also builds their product to the highest powers, kept factored, which is then
their lcm, with a base that divides the numerator cancelled whole and a part of one left to
`cancel-common-factor`; of the two canonical results it takes the smaller by `RationalRules.size`,
the factored one on a tie. So `1/(x - 1) - 1/(x + 1)` is `2/(x² - 1)`, `1/(x + 1)² - 1/(x + 1)` is
`-x/(x + 1)²`, and `1/(√x - 1) + 1/(√x + 1)` is `2√x/(x - 1)`, the factored `(√x - 1)(√x + 1)`
being larger than its canonical expansion `x - 1`. The value `lcm` is the chosen form's common
denominator before any cancelling. The result replaces the sum only when it is no larger by `RationalRules.size`, which counts nodes but a
numeral as its digits, so that `1/x + 1/y` and `x + 1/(x - 1)` stay and a sum of twenty pairs
`c/(x - k) - c/(x + k)` with 20-digit `c` is not traded for coefficients of hundreds of digits.
Every fraction term is combined or none (combining by groups of terms that share denominator kernels
is deferred). A sum whose denominators have a total degree above 200, or whose terms' denominator
term counts multiply past `Polynomial.MAX_TERMS`, is declined before anything is built. The value
of a top-level `expand`, `apart` or `collect` is simplified without this set (**Evaluation**), so
`apart(1/(x² - 1), x)` stays split and `simplify(apart(…))` combines it again. No standard rule may
distribute a denominator over a sum: it would cycle with `combine-fractions`.

### exp-log

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `exp-to-power` | `exp(x_)` → `e^x_` | | `exp(2x)` → `e^(2x)` |
| `ln-to-log`, `log10-to-base`, `log2-to-base` | `ln(x_)` → `log(x_)`; `log10(x_)` → `log(x_, 10)`; `log2(x_)` → `log(x_, 2)` | | |
| `log-to-base-one` | `log(a_, 1)` → `undefined` | | `log(x, 1)` → `undefined` |
| `log-base-exact` | `log(a_, b_)` → `p/q` (computed) | `a_`, `b_` positive rationals, `b_ ≠ 1`, `a_ = b_^(p/q)` | `log(8, 2)` → `3`; `log10(1000)` → `3`; `log(1/4, 2)` → `-2` |
| `log-change-of-base` | `log(a_, b_)` → `log(a_) / log(b_)` | | `log(3, 2)` → `log(3)/log(2)` |
| `log-of-zero-to-base`, `log-of-zero` | `log(0, b_)` → `undefined`; `log(0)` → `undefined` | | |
| `log-of-one`, `log-of-e` | `log(1)` → `0`; `log(e)` → `1` | | |
| `sinh-of-zero`, `cosh-of-zero`, `tanh-of-zero`, `sech-of-zero`, `csch-of-zero`, `coth-of-zero` | `sinh(0)` → `0`; `cosh(0)` → `1`; `tanh(0)` → `0`; `sech(0)` → `1`; `csch(0)`, `coth(0)` → `undefined` | | |
| `log-of-exp` | `log(e^x_)` → `x_` | `x_` real | `log(e^(2y))` → `2y` |
| `exp-of-log` | `e^(c___ * log(x_) + r___)` → `x_^c___ * e^r___` | `x_ > 0` | `e^(2 log x + y)` → `x² e^y` under `x > 0` |
| `log-of-power` | `log(x_^n_)` → `n_ * log(x_)` | `x_ > 0`, `n_` real | `log(√2)` → `log(2)/2` |
| `log-of-even-power` | `log(x_^n_)` → `n_ * log(abs(x_))` | `x_` real, `n_` even, `n_ ≠ 0` or `x_ ≠ 0` (at `n = x = 0` the left side is 0) | `log(x²)` → `2 log\|x\|` |
| `log-of-product` | `log(x_ * rest__)` → `log(x_) + log(rest__)` | `x_ > 0` | `log(ab)` → `log a + log b` under `a > 0`; `log(2x)` → `log 2 + log x` |
| `log-of-reciprocal` | `log(1/q)` → `-log(q)` (computed) | `q > 1` an integer | `log(1/2)` → `-log 2`; `log(3/2)` stays |
| `log-of-negative` | `log(x_)` → `log(-x_) + iπ` | `x_ < 0` | `log(-2)` → `log 2 + iπ` |

`log(x/y)` splits through `log-of-product` and then `log-of-power` on `y⁻¹`. Like
`power-of-product`, `log-of-product` needs one positive factor. The set expands logarithms of
products and powers and collapses `exp`; it never recombines `log a + log b` (a later
`logcombine`-style command's job), so the two directions cannot alternate. `log-base-exact` writes
`b = r^k` with `r > 1` not a perfect power (`k` up to `b`'s bit length, by exact root tests), and
`a = r^m` with `r = N/D` in lowest terms forces `num(a) = N^m` and `den(a) = D^m` (the parts
swapped for `m < 0`, which comparing `a` with 1 decides): `|m|` is the least `m` with `N^m` at
least the larger part, found by bisection on bit lengths, and two exact powers confirm it, so
`p/q = m/k`. It does not fire on a numerator or denominator beyond 4,096 bits, which bounds `|m|`
by 4,096 and keeps every power small; `log(1000, 1001/1000)` declines in milliseconds.

### radical

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `negative-radicand` | `n_^e_` → `(-1)^e_ * (-n_)^e_` | `n_` a negative rational other than `-1`, `e_` a non-integer rational | `√-8` → `2i√2` |
| `split-integer-exponent` | `n^e` → `n^⌊e⌋ · n^(e - ⌊e⌋)` (computed) | `n` a non-zero rational, `e > 1` | `2^(3/2)` → `2√2` |
| `rationalise-denominator` | the same | `e < 0` | `1/√2` → `√2/2`; `3/√5` → `3√5/5` |
| `split-fraction-radicand` | `(N/D)^e` → `N^e · D^(1 - e) / D` (computed) | `N/D > 0` not an integer, `0 < e < 1` | `√(8/9)` → `2√2/3`; `√(2/3)` → `√6/3` |
| `reduce-radicand-power` | `n^e` → `m^(k·e)` (computed) | `n = m^k` an integer with `k > 1` maximal, `0 < e < 1` | `4^(1/3)` → `2^(2/3)`; `√8` → `2^(3/2)`, then `2√2`; `4^(2/3)` → `2·2^(1/3)` |
| `extract-perfect-power` | `n^(p/q)` → `a · (n^p / a^q)^(1/q)` (computed) | `n` a positive integer, `0 < p/q < 1`, `a > 1` the largest found with `a^q \| n^p` | `√12` → `2√3`; `54^(1/3)` → `3·2^(1/3)` |
| `combine-numeric-radicals` | `a_^e_ * b_^e_ * rest___` → `(a_ * b_)^e_ * rest___` | `a_`, `b_` positive rationals, `e_` a non-integer rational | `√2·√3` → `√6`; `∛2·∛3` → `∛6` |
| `denest-square-root` | `(a_ + b_ * c_^(1/2))^(1/2)` → `√((a + d)/2) ± √((a - d)/2)` (computed; `b_` defaults to 1) | `a_ > 0` and `b_` rational, `c_` a positive integer, `a² - b²c = d²` a rational square | `√(5 + 2√6)` → `√2 + √3` |
| `extract-radicand-content` | `s_^e_` → `d^(±1) · (s_/d^n)^e_` (valued: `factor`, `d^n`) | `s_` a sum, a polynomial in its kernels whose rational content has the `n`-th power factor `d^n > 1` (the largest `n`-th powers found in its numerator over those in its denominator), `e_ = ±1/n`, `n ≥ 2` | `√(4x + 4)` → `2√(x + 1)`; `√(12x + 12)` → `2√(3x + 3)`; `1/√(4x + 4)` → `1/(2√(x + 1))` |
| `root-of-odd-power` | `root(x_^n_, n_)` → `x_` | `x_` real, `n_` an odd positive integer | `root(x³, 3)` → `x` |
| `root-of-nonnegative` | `root(x_, n_)` → `x_^(1/n_)` | `x_ ≥ 0`, `n_` a positive integer, `x_` holding no decimal | `root(x, 4)` → `x^(1/4)` under `x ≥ 0`; `root(x² + 0.5, 3)` stays |
| `root-of-negative` | `root(x_, n_)` → `-root(-x_, n_)` | `x_ < 0`, `n_` odd | `root(-16, 3)` → `-2·2^(1/3)` |
| `root-of-negative-even` | `root(x_, n_)` → `undefined` | `x_ < 0`, `n_` even | `root(-16, 4)` → `undefined` |
| `cbrt-of-cube`, `cbrt-of-nonnegative`, `cbrt-of-negative` | the same for `cbrt` | | `cbrt(-16)` → `-2·2^(1/3)` |

An even root of an even power needs no rule of its own: `root(x², 2)` is `(x²)^(1/2)` by
`root-of-nonnegative`, then `|x|`. `extract-perfect-power` divides by the primes below 10,000 and
then tests the cofactor for being a perfect `q`-th power exactly (`root(q, UNLIMITED)`); a `q`-th
power of a larger prime inside a composite cofactor is not found, which leaves a sound but
unreduced radical. It does not fire when `n^p` exceeds 10,000 bits.

`extract-radicand-content` reads a sum radicand as a polynomial over ℚ in its kernels
(**Polynomials and kernels**; a decimal is a kernel, so it never counts toward the content), takes
its rational content `a/b`, and the largest `n`-th powers found in `a` and in `b` the same way as
`extract-perfect-power` (within 10,000 bits). It is sound for every value of the kernels and needs
no assumption: `d^n > 0`, so `(d^n·t)^(1/n) = d·t^(1/n)` on the principal branch for every complex
`t`, negative radicands and odd roots included (`√(-4x - 4)` → `2√(-x - 1)`). A product radicand
is `power-of-product`'s (`√(4(x + 1))` → `2√(x + 1)`); exponents other than `±1/n`
(`(4x + 4)^(3/2)`) are left as they are. `2^(2/3)` stays as it is
rather than becoming `4^(1/3)`: the rule fires only when it takes something out, and
`reduce-radicand-power` (on radicands of at most 1,024 bits) takes `4^(1/3)` back to `2^(2/3)`.

### trig

Angles are in radians: evaluation has applied the angle mode before the rules run (`sin(30)` in
degrees reaches them as `sin(π/6)`, `asin(1/2)` as `asin(1/2)·180/π`, **Evaluation**). The
identities hold over the complex numbers with the engine's principal branches, so a rule asks the
facts only where the table says.

In degrees and gradians (the context's `Numerics.halfTurn()`, 180 or 200, **The rewriter**) an
argument's unit depends on its shape (`docs/language.md` §14.2). A **closed** argument, one with no
symbol, is read in radians, and so are the evaluator's own conversions, so every rule applies to it
as in radians. An argument with a symbol takes the unit of the value put into it: a plain number
there is read in the session's unit. An argument with a symbol falls in one of three classes:

- **plain-typed**: built from real numbers and symbols by sums, products and integer powers, so
  that it is a plain number at every plain value of its symbols, exact or decimal, and read in the
  session's unit. The rules that carry an angle constant (the shifts, the quarter turns,
  `acos-of-negative`, `asec-of-negative`, `atan-of-reciprocal`) do not apply to it, and versions in
  the session's unit, with the half turn `H` in place of `π`, do. When such a rule leaves an
  argument with no symbol, a plain number meant in the unit, it writes the evaluator's conversion
  `rest·π/H`, so the radian rules read it as meant (`sin(360n + 30)` → `sin(π/6)` → `1/2`).
- **constant-typed**: each top-level term plain-typed or `π` or `e` once beside plain-typed factors,
  at least one such. Under every plain value a term with the constant is a rational or a decimal
  times it, or 0, and §14.1 folds neither into `π` or `e` in a sum or product, so the argument is
  plain only where every such term's coefficient is 0; it is read in radians, and the radian shifts
  and quarter turns apply under the condition below. The inverse functions' reflections
  (`acos-of-negative`, `asec-of-negative`, `atan-of-reciprocal`) do not: the argument is a plain 0
  where its coefficient vanishes, and the inverse function of it answers in the unit (`acos(-πx)` is
  90 in degrees at `x = 0`, where `π - acos(πx)` is `π - 90`).
- **ambiguous**: anything else, as `√x + 180`, whose plainness at use depends on the value put in;
  no rule that carries an angle constant applies.

In both cases where the radian shifts and quarter turns apply (a closed or a constant-typed
argument), they apply only when the argument they leave is read in radians wherever the argument
they came from is: it is 0; it is closed and not a plain number; it has no plain-typed term, so
that it is 0 where its constant terms vanish; or it holds a constant term and each has a fixed
non-zero rational coefficient (`π/3`), so that it is never plain. Otherwise it is plain where a
symbolic coefficient of `π` is 0, while the argument it came from still held the removed shift.
So `cos(πk)` → `(-1)^k`, `sin(πk + πy)` → `(-1)^k sin(πy)` and `sin(x + 7π/3)` → `sin(x + π/3)`
in degrees, while `sin(x + π)`, `cos(x + 2πk)`, `sin(πy + x + π/2)` and the closed
`sin(1.0 + π)` stay. A decimal multiple of a half turn (`360.0n`) is no whole one. In radians the versions in
the unit never apply.

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `special-angle` | `h_(x_)` → the exact value (computed) | `h_` circular, `x_` `0` or `qπ` with `q` an exact rational | `sin(π/6)` → `1/2`; `sin(π/12)` → `(√6 - √2)/4`; `tan(π/2)` → `undefined`; `sin(7π/5)` → `-sin(2π/5)` |
| `inverse-special-value` | `h_(x_)` → the angle (computed) | `h_` an inverse circular function, `x_` or `-x_` in its table | `asin(1/2)` → `π/6`; `acos(-√2/2)` → `3π/4`; `atan(√3 - 2)` → `-π/12` |
| `shift-by-multiple-of-pi` | `h_(π·k___ + u___)` → `(-1)^k h_(u___)`, `h_(u___)` for `tan`, `cot` (computed) | `h_` circular, `k___` an integer | `sin(x + π)` → `-sin(x)`; `cos(πn)` → `(-1)^n`; `cos(x + 2πk)` → `cos(x)` |
| `shift-by-half-turns` | `h_(x_)`, `x_` a sum with a term `k·H` → as `shift-by-multiple-of-pi` (computed) | degrees or gradians, `x_` plain-typed with a symbol, `k` an integer | in degrees `sin(x + 180)` → `-sin(x)`, `cos(x + 360n)` → `cos(x)` for an integer `n` |
| `reduce-rational-shift` | `h_(q_·π + u__)` → `h_(q'π + u__)` (computed) | `q_` a non-integer rational outside `(-1, 1]` (`(-1/2, 1/2]` for `tan`, `cot`) | `sin(x + 7π/3)` → `sin(x + π/3)` |
| `reduce-half-turn-shift` | `h_(x_)`, `x_` a sum with a number `q·H` → `q'·H` in its place (computed) | degrees or gradians, `x_` plain-typed with a symbol, `q` as in `reduce-rational-shift` | in degrees `sin(x + 420)` → `sin(x + 60)` |
| `sin-plus-half-pi`, `sin-minus-half-pi`, `cos-plus-half-pi`, `cos-minus-half-pi`, `tan-plus-half-pi`, `cot-plus-half-pi`, `sec-plus-half-pi`, `sec-minus-half-pi`, `csc-plus-half-pi`, `csc-minus-half-pi` | `sin(u__ + π/2)` → `cos(u__)`; `sin(u__ - π/2)` → `-cos(u__)`; `cos(u__ ± π/2)` → `∓sin(u__)`; `tan(u__ + π/2)` → `-cot(u__)`; `cot(u__ + π/2)` → `-tan(u__)`; `sec(u__ ± π/2)` → `∓csc(u__)`; `csc(u__ ± π/2)` → `±sec(u__)` | | `sin(π/2 - x)` → `cos(-x)` → `cos(x)` |
| `quarter-turn-in-unit` | `h_(u ± H/2)` → as the quarter turns above (computed) | degrees or gradians, the argument plain-typed with a symbol | in degrees `sin(x + 90)` → `cos(x)`; in gradians `cos(x - 100)` → `sin(x)` |
| `odd-function` | `h_(u_)` → `-h_(-u_)` (computed) | `h_` one of `sin tan cot csc sinh tanh asin atan acot acsc asinh atanh`, `u_` looks negative; for `acot`, `u_ ≠ 0` (`acot(0)` is `π/2`) | `sin(-2x)` → `-sin(2x)`; `sin(y - x)` → `-sin(x - y)` |
| `even-function` | `h_(u_)` → `h_(-u_)` (computed) | `h_` one of `cos sec cosh`, `u_` looks negative | `cos(π/3 - x)` → `cos(x - π/3)` |
| `acos-of-negative`, `asec-of-negative` | `acos(u_)` → `π - acos(-u_)`; the same for `asec` | `u_` a negative number or a product with a negative coefficient | `acos(-x)` → `π - acos(x)`; `acos(1 - x)` stays |
| `acos-of-negative-in-unit`, `asec-of-negative-in-unit` | `acos(u_)` → `H - acos(-u_)`; the same for `asec` (computed) | as above, in degrees or gradians, `u_` plain-typed with a symbol | in degrees `acos(-x)` → `180 - acos(x)` |
| `sin-of-asin`, `cos-of-acos`, `tan-of-atan` | `sin(asin(u_))` → `u_`, … | | `sin(asin(x))` → `x` |
| `sin-of-acos`, `cos-of-asin` | `sin(acos(u_))` → `√(1 - u_²)`, … | | `cos(asin(x))` → `√(1 - x²)` |
| `sin-of-atan`, `cos-of-atan` | `sin(atan(u_))` → `u_/√(1 + u_²)`; `cos(atan(u_))` → `1/√(1 + u_²)` | `u_` real | |
| `asin-of-sin`, `atan-of-tan` | `asin(sin(u_))` → `u_`; `atan(tan(u_))` → `u_` | `u_` known within `[-π/2, π/2]`, `(-π/2, π/2)` | `asin(sin(x))` → `x` under `-1 ≤ x ≤ 1` |
| `atan-of-reciprocal` | `atan(1/u_)` → `π/2 - atan(u_)` | `u_ > 0` | |
| `atan-of-reciprocal-in-unit` | `atan(1/u_)` → `H/2 - atan(u_)` (computed) | `u_ > 0`, in degrees or gradians, `u_` plain-typed with a symbol | in degrees `atan(1/x)` → `90 - atan(x)` for `x > 0` |
| `combine-trig-factors` | the circular factors of a product in normal form (computed) | the group's measure drops | `sin(x)/cos(x)` → `tan(x)`; `1/cos(x)²` → `sec(x)²`; `tan(x) cos(x)` → `sin(x)`; `sin(x)/cos(x)²` → `tan(x) sec(x)`; `sin(x) cos(y)` stays |
| `sine-double-angle` | `c_·sin(u_)·cos(u_)·rest___` → `c_/2·sin(2u_)·rest___` | `c_` a rational with an even numerator | `2 sin(x) cos(x)` → `sin(2x)`; `sin(x) cos(x)` stays |
| `tanh-of-quotient` | `sinh(u_)^n_·cosh(u_)^m_·rest___` → `tanh(u_)^n_·rest___` | `n_` an integer, `m_ = -n_` | `sinh(x)/cosh(x)` → `tanh(x)` |
| `pythagorean` | `d·sin(u)² + d·cos(u)² + rest` → `d + rest` (computed, as every sum rule) | | `3 sin(x)² + 3 cos(x)²` → `3` |
| `one-minus-sin-squared`, `one-minus-cos-squared` | `d·sin(u)² - d + rest` → `-d·cos(u)² + rest`; the same with `cos` | | `1 - sin(x)²` → `cos(x)²`; `a - a sin(x)²` → `a cos(x)²` |
| `one-plus-tan-squared`, `one-plus-cot-squared` | `d·tan(u)² + d + rest` → `d·sec(u)² + rest`; `cot`, `csc` | | `1 + tan(x)²` → `sec(x)²` |
| `cosine-double-angle` | `d·cos(u)² - d·sin(u)² + rest` → `d·cos(2u) + rest` | | `cos(x)² - sin(x)²` → `cos(2x)` |
| `cosine-double-angle-from-cos`, `cosine-double-angle-from-sin` | `d·cos(u)² - d/2 + rest` → `d/2·cos(2u) + rest`; `d·sin(u)² - d/2 + rest` → `-d/2·cos(2u) + rest` | | `2cos(x)² - 1` → `cos(2x)`; `1 - 2sin(x)²` → `cos(2x)` |
| `hyperbolic-pythagorean` | `d·cosh(u)² - d·sinh(u)² + rest` → `d + rest` | | `cosh(x)² - sinh(x)²` → `1` |
| `one-plus-sinh-squared`, `cosh-squared-minus-one` | `d·sinh(u)² + d + rest` → `d·cosh(u)² + rest`; `d·cosh(u)² - d + rest` → `d·sinh(u)² + rest` | | `sinh(x)² + 1` → `cosh(x)²` |
| `one-minus-tanh-squared`, `coth-squared-minus-one` | `d·tanh(u)² - d + rest` → `-d·sech(u)² + rest`; `d·coth(u)² - d + rest` → `d·csch(u)² + rest` | | `1 - tanh(x)²` → `sech(x)²` |

**Exact values.** `special-angle` reduces `qπ` exactly (integer arithmetic on `q`'s numerator
and denominator) by the period and the symmetries of the function to `rπ` with `r` in
`[0, 1/2]`: for `sin` and `csc` a half turn changes the sign and `π - t` keeps it, for `cos` and
`sec` `2π - t` keeps the sign and `π - t` changes it, and `tan` and `cot` reduce modulo `π` with
`π - t` changing the sign. When `12r` is an integer the value comes from the table, in the radical
set's fixed form; elsewhere the call is `±f(rπ)` with the same function, so a non-special angle
stays (`sin(1)`, `sin(π/5)`) or is reduced (`cos(3π/5)` → `-cos(2π/5)`). Poles give `undefined`.

| `r` | 0 | 1/12 | 1/6 | 1/4 | 1/3 | 5/12 | 1/2 |
|---|---|---|---|---|---|---|---|
| `sin` | 0 | (√6 − √2)/4 | 1/2 | √2/2 | √3/2 | (√6 + √2)/4 | 1 |
| `cos` | 1 | (√6 + √2)/4 | √3/2 | √2/2 | 1/2 | (√6 − √2)/4 | 0 |
| `tan` | 0 | 2 − √3 | √3/3 | 1 | √3 | 2 + √3 | pole |
| `cot` | pole | 2 + √3 | √3 | 1 | √3/3 | 2 − √3 | 0 |
| `sec` | 1 | √6 − √2 | 2√3/3 | √2 | 2 | √6 + √2 | pole |
| `csc` | pole | √6 + √2 | 2 | √2 | 2√3/3 | √6 − √2 | 1 |

`inverse-special-value` reads the table backwards: `asin` from the `sin` row, `acos(v)` as
`π/2 - asin(v)`, `atan` and `acot` from their rows (`acot(0)` is `π/2`, as `acot(x) = atan(1/x)`
gives), `asec` and `acsc` from theirs (`asec(x) = acos(1/x)`); for `-v` the odd functions negate
the angle and `acos`, `asec` take `π` minus it. The lookup compares canonical forms with a rational
coefficient of a sum distributed over its terms, so `(√6 − √2)/4`, `√6/4 − √2/4`, `(√2 − √6)/4` and
`-(√6 − √2)/4` all meet the table (`asin((√2 − √6)/4)` is `-π/12`, `acos` of it `7π/12`). A
negation that is 0 is not looked up: `acot(x - (x + 1) + 1)`, a zero not written as 0, stays, since
`acot(0)` is `π/2` and not `-acot(0)`.

**Shifts and parity.** A whole multiple of `π`, numeric or an integer by the facts, leaves the
angle with the sign `(-1)^k` (none for `tan`, `cot`), resolved when the parity of `k` is known; a
quarter turn changes the function; any other rational shift is only reduced into the period's
centred range, so `sin(x - π/3)` and `sin(x + 2π/3)` stay. An argument **looks negative** when it
is a negative number, a product with a negative coefficient, or a sum whose display-leading term
is one: the first term that is not a number, in descending total degree and then canonical order,
the order the printer shows (`TrigTables.looksNegative` reimplements `Display`'s order and must
change with it). So `sin(y - x)` becomes `-sin(x - y)` and `sin(x - 1)` stays, `sin(a - b) +
sin(b - a)` cancels, and `sin(2 - √3)` displays as `-sin(√3 - 2)`. The negation is termwise,
since the canonical form does not distribute `-1` over a sum. `acos` and `asec` flip only on a
negative number or coefficient, so `acos(1 - x)` does not become `π - acos(x - 1)`.

**Products.** `combine-trig-factors` groups a product's circular factors with integer exponents by
argument and counts their net exponents `A` of `sin` and `B` of `cos` (`tan` is `sin·cos⁻¹`, `sec`
is `cos⁻¹`, …). The normal form of `(A, B)` is `sin^A cos^B` when neither is negative, `tan^k` or
`cot^k` for the `k` pairs of opposite signs and then the rest as `sin`, `cos`, `sec` or `csc`, and
`csc^-A sec^-B` when both are negative. A group is rewritten only when its node count drops, or
stays with fewer negative exponents (`1/cos(x)²` → `sec(x)²`). The normal form never adds a pole:
it uses `tan`, `sec` or `cos⁻¹` only when `B < 0`, which needs an input factor with a pole where
`cos` vanishes; it can remove one, as the canonical form's `x·x⁻¹` does (`tan(x)·cos(x)` →
`sin(x)`, `sec(x)·cos(x)` → `1`).

**Sums.** Each sum rule's pattern is anchored on one squared function with its coefficient,
`d___ * sin(u_)^2 + rest__`, with the condition that `rest__` holds the partner term, built
canonically (`d·cos(u)²`, `-d`, `d`, `-d/2`, …): an anchored pattern stays linear in the number of
terms, where a free coefficient variable would try every pair of a long sum. The replacement is
computed: it replaces every such pair of the sum at once by its one term, so a sum of a thousand
pairs takes one rewrite per rule rather than a thousand, each of which would revisit every term.

### logic

`LogicRules` (Phase 8a, `docs/language.md` §8.4 and §14.24): the identities of the boolean
operators, each valid for every assignment of truth values. `and`, `or` and `xor` are flat and
sorted in canonical form, so their rules match the whole application (`and(p_, args__)`) and look
through its operands in Java; the rest are templates.

| Rule | Rewrite |
|---|---|
| `not-true`, `not-false` | `¬true` → `false`, `¬false` → `true` |
| `double-negation` | `¬¬p` → `p` |
| `de-morgan-and`, `de-morgan-or` | `¬(p ∧ q ∧ …)` → `¬p ∨ ¬q ∨ …`, and dually |
| `not-implies` | `¬(p ⟹ q)` → `p ∧ ¬q` |
| `not-xor` | `¬(p ⊕ q ⊕ …)` → `¬p ⊕ q ⊕ …`, the first operand in canonical order |
| `not-equal`, `not-unequal` | `¬(a = b)` → `a ≠ b`, `¬(a ≠ b)` → `a = b` |
| `not-order` | `¬(a < b)` → `a ≥ b` (and the other three), when both sides are real by the facts |
| `and-false`, `or-true` | an operand `false` (`true`) decides the operator |
| `and-true`, `or-false`, `xor-false` | an identity operand is dropped |
| `and-idempotent`, `or-idempotent` | a repeated operand counts once |
| `xor-self` | an operand twice cancels |
| `xor-true` | `true ⊕ q ⊕ …` → `¬(q ⊕ …)` |
| `and-complement`, `or-complement` | an operand beside its negation decides the operator |
| `absorption-and`, `absorption-or` | in `p ∧ (p ∨ q) ∧ …` the operand `(p ∨ q)` goes, and dually |
| `implies-true-premise`, `implies-false-premise`, `implies-true-conclusion`, `implies-false-conclusion`, `implies-self` | `true ⟹ q` → `q`, `false ⟹ q` → `true`, `p ⟹ true` → `true`, `p ⟹ false` → `¬p`, `p ⟹ p` → `true` |

It terminates: every rule but the four negation rules removes nodes, and those move a `¬` onto
strictly smaller operands (De Morgan's laws, `not-implies`, `not-xor`) or remove it (`not-equal`,
`not-order`); no rule creates a `⟹` or pulls a `¬` outward, `xor-true`'s `¬` included, which only
`not-xor` and `double-negation` then move down. `LogicProperties` checks soundness: random formulas
in three or four names over every operator and the constants keep their truth table, as does every
rewrite of the trace, and simplify within the cap.

### special

The gamma function, the beta function and the error functions (Phase 8b; `docs/language.md` §12.1).
Each rule removes a `gamma` or a `beta`, or takes a sign out of an argument that looks negative (as
`trig`'s parity rules decide it), so the set cannot loop.

| Rule | Rewrites | When |
|---|---|---|
| `gamma-integer` | `Γ(n)` → `(n − 1)!` | `n` a positive integer up to 10,000 |
| `gamma-pole` | `Γ(n)` → `undefined` | `n` an integer at or below 0 |
| `gamma-half-integer` | `Γ(n + 1/2)` → `(2n)!/(4ⁿ n!)·√π`; `Γ(1/2 − n)` → `(−4)ⁿ n!/(2n)!·√π` | `n` up to 10,000 |
| `beta-gamma` | `B(a, b)` → `Γ(a)Γ(b)/Γ(a + b)` | `a` and `b` positive integers or half-integers with `a + b ≤ 10000`, so every value is exact (`beta(6000, 6000)` stays) |
| `erf-odd` | `erf(u)` → `−erf(−u)` | `u` looks negative |
| `erfc-odd` | `erfc(u)` → `2 − erfc(−u)` | `u` looks negative |
| `erf-infinity`, `erfc-infinity` | `erf(∞)` → 1, `erfc(∞)` → 0 | |

`erf(0)` and `erfc(0)` are values at 0 of the evaluator (**Functions on numbers**), as `sin(0)` is.
A decimal argument goes through the `Distribute` provider's `VALUE` kind in `double` (**Statistics**),
at most 16 digits, fewer at a lower precision, unmarked as `sin(0.5)` is; a pole of a decimal
argument is `undefined`, and a value beyond the `double` range an error. An exact argument no rule
answers leaves the call (`gamma(1/3)`, `erf(1/3)`), which `numeric` evaluates the same way.

`SpecialFunctionsTest` checks the values, the poles, the symmetries, the decimals and the
derivatives; `SpecialFunctionsProperties` checks `Γ` at every integer and half-integer of `(−40,
40)` and `B` at positive halves against Hipparchus to `1e-13`, the error functions at rationals,
and the derivatives against central difference quotients.

### Computed rules

A rule is computed when its right side needs arithmetic a template cannot express:
`abs-of-complex-number` (the parts of a number), `split-integer-exponent` and
`rationalise-denominator` (the floor of the exponent), `split-fraction-radicand` (numerator and
denominator), `reduce-radicand-power` (the perfect-power decomposition), `extract-perfect-power`
(trial division), `denest-square-root` (the square root
`d`), `log-base-exact` (the exponent search), `log-of-reciprocal` (the denominator),
`special-angle` and `reduce-rational-shift` (the reduction of the angle's rational multiple),
`inverse-special-value` (the table read backwards), `shift-by-multiple-of-pi` (the sign of `k`),
`odd-function` and `even-function` (the termwise negation), `combine-trig-factors` (the net
exponents and the measure) and the sum rules of `trig` (every pair at once). The four rules of
`rational` are valued (the gcd, the contents and the common denominator come from the algebra).

### Termination

The rewriter is innermost-first, so when a rule fires every proper subterm is already a fixpoint,
and a subterm a rule copies (an exponent split over a product's factors) brings no new work. With
the precedence `exp`, `ln`, `log10`, `log2`, two-argument `log`, `cbrt`, `root` above `log` above
`abs` above `sign` above power above product above sum above the atoms, every rule but three is
decreasing in the recursive path ordering: the name rules remove a stronger head, the `abs`,
`sign` and `log` rules move the operator into strictly smaller arguments or remove it, and
`power-of-power-nonnegative` and `power-of-product` move a power to smaller bases. The three:

- `power-of-even-power` and `log-of-even-power` replace a power by the `abs` of its strictly
  smaller base; the rules that move an `abs` again (`abs-of-product`, `abs-of-power`) move it only
  inward, so a repeat at one position acts on a strictly smaller term;
- `exp-of-log` removes one `log` from an exponent of `e`, and no rule puts one there.

No rule undoes another: nothing recombines `log a + log b`, `|x|·|y|` or `x^a·y^a`, the canonical
form merges `e^a·e^b` (the direction `exp-of-log` wants), and it never rebuilds a power of a power,
a power of a product or a log of a product. A numeric power passes through `negative-radicand`,
the exponent splits, `split-fraction-radicand`, `reduce-radicand-power` (which strictly lowers the
base) and `extract-perfect-power` to the fixed form;
`combine-numeric-radicals` strictly lowers the number of numeric radical factors of a product and
`denest-square-root` the depth of nested radicals. `extract-radicand-content` leaves a sum radicand
whose content has no `n`-th power factor, so it does not fire there again; nothing gathers its
`d · t^(1/n)` back into one radical (`combine-numeric-radicals` takes numeric bases only,
`power-of-product` acts on product bases, and the canonical form keeps a number times a power), and
`rational`'s content rules act on quotients, never on the base of a fractional power, while
`combine-fractions` turns a sum into a product, which the rule does not take. A rule of another
set that raises a radicand's content again is finite by that set's own argument.

`rational` terminates by a lexicographic measure: the number of fraction terms of sums, which
`combine-fractions` lowers and the other rules never raise (a product with a negative power stays a
product under the canonical form, and no standard rule distributes a denominator over a sum); then
the degree of the denominators in the kernels, which `cancel-common-factor` lowers; then whether
some quotient's numeric content is not in lowest terms, which `cancel-common-content` and
`cancel-denominator-content` clear in one step without touching the degrees or the primitive
parts. `rational` produces only polynomials in the kernels it was given and their negative powers,
which no other set's left side wants beyond the canonical form's own arithmetic.

In `trig`, every rule removes something or flips a sign once: the exact values replace an
application by a number or by a reduced application that is their own fixpoint; the shift rules
remove a multiple of `π` from an argument, except `reduce-rational-shift`, which moves it into the
centred range once (its negation stays there, as `q` is not an integer); a parity rule turns an
argument that looks negative into one that does not, so two parity rewrites at one position need a
removal between them; the inverse compositions remove a level of application and
`atan-of-reciprocal` a reciprocal; `combine-trig-factors` strictly lowers its measure, toward a
normal form it never leaves, and nothing writes `tan`, `cot`, `sec` or `csc` back as `sin` and
`cos`; `sine-double-angle` and `tanh-of-quotient` lower the number of function factors, and the sum
rules the number of terms. The identities are collapsed only (no expansion, half angle or power
reduction exists), so nothing undoes them. `trig` produces no `abs`, `sign`, `log`, `exp`, `root` or
`cbrt`, and no other set produces a circular or hyperbolic function, so the sets meet only where
`basic`'s power and `abs` rules act on what `trig` leaves (`√(1 - sin²x)` → `√(cos²x)` →
`|cos(x)|`), after which `trig` has nothing to do. This is an argument, not a mechanised proof; the
termination properties are its evidence (**Tests**), and the rewriter's cap stays the guard.

### Tests

In `engine/src/test/java/mathcanvas/engine/simplify/`:

- `BasicRulesTest`, `ExpLogRulesTest`, `RadicalRulesTest`, `TrigRulesTest`: through `RuleCases`, a
  positive case (the rule fires, the set reaches a stated value) and a negative case (it does not
  fire) for every rule, which `covered()` checks, plus the examples above; `RuleCases.halfTurn`
  runs the cases that follow in degrees or gradians;
- `AngleModeProperties` (in `eval/`): in degrees and gradians, an expression with symbols (shifted
  by twelfths of a half turn, by quarter and half turns, by an integer symbol times whole turns,
  alone and under `asin(sin …)` and `atan(tan …)`, by `π` and `πk`, and the inverse reflections),
  evaluated, given random values in its value (a decimal, integers for the integer symbols) and
  evaluated again, has the value the expression has with them written in (both made numeric; a
  pole within rounding is skipped); and at every twelfth of a half turn over two
  turns either way each circular function's exact value in the unit is its decimal value (`undefined`
  at a pole), as are the inverse functions' at plain values;
- `RuleSetsTest`: the order of the list (and of the list for expanded forms), names unique across
  it, a narration line for every rule whose arguments are the rule's variables and whose named
  placeholders are reserved ones or values its rule declares, and a narrated trace;
- `AcceptancePairsTest`: `PLAN.md`'s pairs (`√(x²)` is `|x|`, and `x` under `x ≥ 0`; `log(ab)`
  splits only under positivity; `asin(sin x)` is `x` only within `[-π/2, π/2]`), the textbook
  radicals, logarithms and trigonometry, and the angle-mode table of `docs/language.md` §14.2
  (`sin(30°)` is `1/2`; in degrees `sin(30)` is `1/2` and `asin(1/2)` is `30`), through the list
  and through a session;
- `SpecialAnglesTest`: every circular function at every multiple of `π/12` in two turns either way
  and far out, and every inverse function at its table's values and their negatives, against the
  numeric evaluator to 30 digits, with `undefined` exactly at the poles;
- `RationalRulesTest`, besides every rule's cases and the examples above: the declines (a gcd of
  1, a refused kernel, the degree bound, the size condition, all-or-nothing, a sum of high degree
  declined at once), a registry whose only gcd provider declines, the gcd going through the
  registry, the narration with its values, and `(2x² - 2)/(4x - 4)` taking a factor step and a
  content step and stopping (`TerminationProbe` with the standard list);
- `SimplifierPerformanceTest`: a sum of 1,000 terms of radicals, logarithms and absolute values,
  and one of 1,000 groups of Pythagorean pairs, special angles, shifts and quotients, under a
  generous bound (each takes well under a second); for `rational`, a sum of twenty fractions over
  distinct linear denominators and a degree-50 quotient with a common factor of degree 25 (about
  0.05 seconds);
- `RationalProperties` (5,000 tries; idempotence 2,000, termination 200 and 500) over `rationalExprs()` and `rationalExprsWithContents()`:
  soundness for the set alone and in the standard list, at random points and at a factor's roots
  (`RATIONAL_ROOTS`); the simplified form has a value wherever the input has an exact one (no pole
  is added); a simplified quotient is in lowest terms (the gcd of `P·Π Nᵢ` and `Q·Π Dⱼ` is 1 in the
  algebra's normal form); idempotence; and termination;
- `BasicProperties`, `ExpLogProperties`, `RadicalProperties` (5,000 tries each) and
  `StandardProperties` (10,000) over `ExprArbitraries.elementaryExprs()` under `RandomAssumptions`,
  and `TrigProperties` (5,000 for `trig` alone and for the standard list) over `trigExprs()` under
  `RandomAssumptions.withRanges`: each simplification agrees with its input at six points within the
  facts (`Soundness.check`, point by point), and is defined wherever its input has an exact value,
  a point where the simplification's evaluation stops only at a bound of `NumericEvaluator`, both
  exactly and with the point made inexact,
  (`(2·a·b·y)^((1/2)^x)` at `a = 0`, `x = -16` is `0^65536 = 0`, while its rewrite holds
  `2^65536`) being skipped as an ill-conditioned one, under the same cap;
  the termination probe (200 and 500 tries of 20 expressions) asserts fewer than 200 rewrites per
  expression. `RadicalProperties` also compares `extract-radicand-content` on sum radicands
  `(k·(c₁x + c₂y + c₀))^(±1/n)` with `k = (d/d')^n·r` (2,000 tries) at random points and at points
  on the radicand's zero and on either side of it, where the value is complex, and runs the
  termination probe over the standard list on such radicands of elementary expressions. A point that disagrees at 34 digits is checked again at 100 digits, and skipped when
  either side is empty there away from cuts (`NumericEvaluator.evalAwayFromCuts`): the true value
  lies on a cut, a pole or a zero and the rounding noise picks the result (`(-8/81)^(1/3)` cubed,
  then a logarithm; `root(log(log e, (2^b)^(1/3)), 4)`, which is 0 computed near `10^-9`); the jump
  of `sign` at 0 is treated alike. A point that still disagrees at 100 digits is
  **ill-conditioned**, and skipped, when either side disagrees with itself between 34 and 100
  digits: its digits are lost to cancellation beyond what the recheck recovers (`10^120·(sin²2 +
  cos²2 - 1)`), so it is no evidence; a wrong rule at a well-conditioned point still disagrees with
  a self-consistent input and fails. So is a point where either side holds a discontinuous function at its jump
  that accounts for the disagreement: `sign` of an argument within the tolerance of 0, `floor`,
  `ceil` or `trunc` of one within it of an integer, `round` of one within it of a half-integer, or
  a power whose base and exponent are both within it of 0 (or the exponent without a value), and
  where taking that node's value as either of its one-sided values or its value at the jump (the
  function a quarter below the jump, at it and a quarter above, since `sign(0)` is neither side's
  value; 0 and 1 for `0^0`) reconciles the two sides at 100 digits. A rule that makes
  such an argument exact takes the value on one side where the computed residue takes the other
  (`((1 + √2)^(1/2)·b)^atan(tan π)` at `b = 0`, whose `atan(tan π)` is near `10^-100` at any
  precision; `log(e^sign(cos(asin(cos²y + sin²y))))` at `y = -16`, whose `cos` is near
  `10^-50`; `sign(log((cosh²x - sinh²x)^-3))` at `x = 99/4`, whose difference cancels to just
  below 1, against the simplified 0). `mod` has no value in `NumericEvaluator`, and `abs` is
  continuous. `RecheckProperties` checks that `sign(log(u^n))`, with `u` identically 1 by an
  identity, never disagrees at quarter points up to 50. A sum that cancels past 34 digits
  (`NumericEvaluator.cancellations`: an inexact value smaller than its largest term by more than
  `10^(digits − 5)`, or an inexact 0) is evaluated again, before the sides are compared at 100
  digits, at the digits that resolve it (100, plus its largest term's decimal exponent, plus the
  floor's 25 and a guard), and its value put in its place; the comparison, the jump check and each
  side's check against itself (resolved again at 50 more digits) are made on the resolved sides.
  So `e^(S − S)`, with `S` holding a term near `10^2066` and `log 3375`, agrees with its
  simplification `e^(…)/3375`; `(T + 1) − T` against `(T + 2) − T` disagrees at any `T`; and an
  identity that is an inexact 0 beside terms of size 1 (`z·(sin²x + cos²x − 1)`) stays noise near
  0 at any precision and excuses nothing. A point is ill-conditioned when a sum would need more
  than 3,000 digits (a resolution near 10,000 took minutes for one point); terms are not bounded by
  their size. A call that loses its argument's digits near a
  point is resolved alike (`NumericEvaluator.nearUnitCalls`: `log`, `ln`, `log10`, `log2` and
  `acosh` of an argument within `10^(5 − digits)` of 1, `asin` and `acos` within it of ±1, an
  exact argument included unless it is exactly the point, since `acosh(1 + y²/10^60)` at 34 digits
  is off in its fourth digit): the argument is evaluated at 135 digits, doubling up to 1,080, until its deviation from the
  point stands out of the rounding, and the call is evaluated at 100 digits plus the deviation's
  decimal digits and the guard. So `(|b^(1 + 2a)|·log(e^(e^(−6y))))^(1/3)` at `y = 198/5`, whose
  argument is `1 + 10^(−103)`, agrees with its simplification `(e^(−6y)·|b^(1 + 2a)|)^(1/3)`; an
  argument that is the point within rounding at 1,080 digits, an identity such as
  `log(sin²x + cos²x)`, is left alone and excuses nothing. **Each property run asserts that its ill-conditioned skips are
  at most 5% of the points it compared** (`SoundnessChecks.begin` and `end`, around every property,
  through `testing.Recheck`, which holds the recheck and the tally), so a rule cannot pass by
  skipping; each run publishes its count, the points compared and the rate through jqwik's reporter,
  and the cap is exact: `skipped · 20 ≤ compared`. `SoundnessChecksTest` checks both sides of the
  policy. A point whose values overflow the comparison is skipped. The tolerances are those of
  `Soundness`.

## Differentiation

Package `mathcanvas.engine.calculus`: the rule set `diff` (`RuleSets.DIFF`) and its entry point
`Calculus.differentiate(e, x, n, context)`. Every rule is a named `Rule` through the matcher
(**Patterns and rules**) whose pattern is a `Derivative` node, written `diff(u_, x_)` in the
language. The rewriter works innermost-first, so linearity, the product, quotient and power rules
and the chain rule drive the whole computation one `Derivative` at a time, and a `Derivative` no
rule matches stays: the unevaluated form. Each rule has a narration line keyed `diff.<rule>`.

### The entry point

`Calculus.differentiate(e, x, n, context)` rewrites `Derivative(e, x, n)` with `diff`, then runs
the standard simplifier once on the result (**Simplification**), both under the context's
assumptions, precision and cap; with tracing on, the trace is the differentiation's rewrites then
the simplifier's (`Trace.concat`). The result may hold `Derivative` nodes where nothing applied.
The evaluator calls it for `diff` and `d/dx` (**Evaluation**, **Commands**).

### Rules

In priority order; `D(·)` is `diff(·, x_)`, the derivative of order 1 in the variable `x_`.

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `higher-order-constant` | `d_` → `0` | `d_` a derivative of order `n ≥ 2` whose expression is a constant (as for `constant`) | `diff(x⁴, x, 2147483647)` → `0` |
| `higher-order` | `d_`, that is `D_n(u)`, → `D_{n−1}(D(u))` (computed) | `d_` a derivative of order `n ≥ 2`, and `u` not stuck | `diff(x³, x, 2)` → `6x` |
| `merge-orders` | `D(d_)` → `D_{k+1}(u)` (computed) | `d_` is `D_k(u)` in the same variable, and `u` stuck | `D(D(x!))` → `d²/dx² x!` |
| `undefined` | `D(undefined)` → `undefined` | | |
| `constant` | `D(c_)` → `0` | `c_` free of `x_`, commuting, of an arithmetic kind (not a relation, truth value, collection, interval or lambda), with no `undefined` | `D(a² + π)` → `0` |
| `fundamental-theorem` | `D(i_)` → `f` for `i_ = ∫ f dx`; `f(b)·D(b) − f(a)·D(a)` for `i_ = ∫[a, b] f dt` (computed) | `i_` an integral: indefinite in `x_`, or definite in another variable with an integrand free of `x_` and real, finite bounds not both free of it | `D(∫[0, x²] t! dt)` → `2x·(x²)!` |
| `vector`, `matrix` | `D(v_)` → the derivative of each entry (computed) | `v_` a vector, a matrix | `D([x, x²])` → `⟨1, 2x⟩` |
| `piecewise` | `D(p_)` → each piece's derivative, conditions kept (computed) | `p_` piecewise | |
| `variable` | `diff(x_, x_)` → `1` | | |
| `sum-rule` | `D(u_ + rest__)` → the sum of the terms' derivatives (computed, every term at once) | | `D(x² + x)` → `2x + 1` |
| `constant-multiple` | `D(c_ * u__)` → `c_ * D(u__)` | `c_` free of `x_`, commuting | `D(3 sin x)` → `3 cos x` |
| `quotient-rule` | `D(u__ * v_^(-1))` → `(D(u__)·v_ − u__·D(v_))/v_²` | `v_` depends on `x_`, commuting | `D(sin(x)/x)` → `(x cos(x) − sin(x))/x²` |
| `product-rule` | `D(u_ * v__)` → `D(u_)·v__ + u_·D(v__)` | | `D(x² sin x)` → `x² cos(x) + 2x sin(x)` |
| `exp-rule` | `D(e^v_)` → `e^v_·D(v_)` | `v_` commuting | `D(e^(2x))` → `2e^(2x)` |
| `power-rule` | `D(u_^n_)` → `n_·u_^(n_ − 1)·D(u_)` | `n_` free of `x_`, `u_` commuting | `D(√x)` → `1/(2√x)` |
| `exponential-rule` | `D(a_^v_)` → `a_^v_·log(a_)·D(v_)` | `a_` free of `x_`, not 0, commuting | `D(2^x)` → `2^x log(2)` |
| `general-power-rule` | `D(u_^v_)` → `u_^v_·(D(v_)·log(u_) + v_·D(u_)/u_)` | `u_` depends on `x_`, both commuting | `D(x^x)` → `x^x·(log(x) + 1)` |
| `exp`, `ln`, …, `trunc` | `D(f(x_))` → `f′(x_)` from the table (computed) | the table's | `D(sin x)` → `cos x` |
| `chain-rule` | `D(h_(u_))` → `f′(u_)·D(u_)` from the table (computed) | `h_` in the table, `u_` not `x_` | `D(sin(x²))` → `2x cos(x²)` |
| `unknown-function` | `D(h_(u_))` → `h_′(u_)·D(u_)`, the head primed (computed) | `h_` not a builtin (with its primes removed): an unknown function of one argument, which only `dsolve` declares or the prime notation writes | `D(y(x))` → `y′(x)`, `D(y′(x²))` → `2x y′′(x²)` |
| `log-base-constant` | `D(log(u_, b_))` → `D(u_)/(u_·log(b_))` | `b_` free of `x_` | `D(log(x, 2))` → `1/(x log(2))` |
| `log-base` | `D(log(u_, b_))` → `D(u_)/(u_ log b_) − log(u_)·D(b_)/(b_·log(b_)²)` | | |
| `root` | `D(root(u_, n_))` → `root(u_, n_)^(1 − n_)·D(u_)/n_` | `n_` free of `x_` | `D(root(x, 4))` → `1/(4(∜x)³)` |
| `atan2` | `D(atan2(u_, v_))` → `(v_·D(u_) − u_·D(v_))/(u_² + v_²)` | | |
| `big-sum` | `D(sum(f_, k_, a_, b_))` → `sum(D(f_), k_, a_, b_)` | `a_`, `b_` free of `x_`, `k_` not `x_` | `D(Σ[k=1,n] x^k)` → `Σ[k=1,n] k x^(k−1)` |

A rule that builds a quotient or a product with a vector or matrix asks that its values commute;
the derivative of a product of two non-commuting factors stays.

### The derivative table

| Function | Derivative in `u` |
|---|---|
| `exp` | `exp(u)` |
| `ln`, `log` | `1/u` |
| `log10`, `log2` | `1/(u log 10)`, `1/(u log 2)` |
| `cbrt` | `1/(3 cbrt(u)²)` |
| `sin`, `cos`, `tan`, `cot`, `sec`, `csc` | `cos u`, `−sin u`, `sec²u`, `−csc²u`, `sec u tan u`, `−csc u cot u` |
| `asin`, `acos`, `atan`, `acot` | `1/√(1 − u²)`, `−1/√(1 − u²)`, `1/(1 + u²)`, `−1/(1 + u²)` |
| `asec`, `acsc` | `±1/(\|u\|√(u² − 1))` for a real `u`; `±1/(u²√(1 − 1/u²))` otherwise |
| `sinh`, `cosh`, `tanh`, `coth`, `sech`, `csch` | `cosh u`, `sinh u`, `sech²u`, `−csch²u`, `−sech u tanh u`, `−csch u coth u` |
| `asinh`, `acosh` | `1/√(u² + 1)`, `1/(√(u − 1)√(u + 1))` |
| `atanh`, `acoth` | `1/(1 − u²)` |
| `erf`, `erfc` | `2e^(−u²)/√π`, `−2e^(−u²)/√π` |
| `asech` | `−1/(u²√(1/u − 1)√(1/u + 1))` |
| `acsch` | `−1/(\|u\|√(u² + 1))` for a real `u`; `−1/(u²√(1 + 1/u²))` otherwise |
| `abs` | `sign(u)`, for a real `u` |
| `sign`, `floor`, `ceil`, `round`, `trunc` | `0`, for a real `u` |

The formulas hold for the principal values (**Numbers**, counterclockwise continuity) wherever the
function is differentiable, also where a real argument gives a complex value: `acosh` and `asech`
are in their principal forms because the textbook `1/√(u² − 1)` and `−1/(u√(1 − u²))` have the
wrong sign for `u < −1`, and `|u|` is used only for a real argument. `abs` and the step functions
need a real argument (an assumptions query) and ignore their jumps, where there is no derivative.
`factorial`, `binomial`, `gcd`, `lcm`, `mod`, `min`, `max`, `re`, `im`, `conj`, `arg`, `gamma`,
`beta` and the other builtins have no rule.

### Free of x, and what stays

`constant` and the conditions ask whether a value is free of `x`, a query over the tree. A binder
whose variable is `x` hides it in its body (a lambda, Σ, Π, a limit, a definite integral), while
its bounds and point are outside that scope. A derivative's variable is free in its value, so a
derivative is free of `x` when its expression is; an indefinite integral in `x` never is; a command
left as a call with a variable of its own (a held `solve`) is free only when `x` occurs nowhere in
it. Everything without a rule stays a `Derivative` when it depends on `x`: the builtins without a
table entry, integrals but those of `fundamental-theorem`, limits, Π, Σ with a bound in `x`, a derivative
in another variable whose own derivative stays, a held command. Other symbols are constants, so
`diff(f, y)` is the partial derivative.

### Higher orders

`higher-order` and `merge-orders` share one predicate, **stuck(u)**: no rule of the set applies to
`D(u)`, by the rewriter's own test (a match whose replacement exists and differs from the node),
under the facts of the place where that derivative is rewritten, inside a derivative's body,
without the facts about `x`. `higher-order` unrolls `D_n(u)` only when `u` is not stuck, and
`merge-orders` combines `D(D_k(u))` into `D_{k+1}(u)` only when `u` is stuck, so a derivative that
stays keeps its order: `diff(x!, x, 2)` is `d²/dx² x!`, and `diff(x·x!, x, 2)` is
`x d²/dx² x! + 2 d/dx x!`.

### Mixed and successive derivatives

A derivative of a derivative in another variable (`∂²/∂x∂y f`, `diff(f, y, x)`) is evaluated as one
node: the evaluator evaluates the inner derivatives' scopes without differentiating them (the
evaluator's `deferred` node, by identity) and hands the whole chain to `Calculus.differentiate`, so
the rules finish the innermost derivative first and the statement has one trace. Each variable of
the chain is free in the result, as the outer one is. `diff(f, x, y, …)` with every argument after
`f` a name is built as that chain when it is parsed (`TypedNodes`), the later names bound in `f` as
the variable is (the parser's read-ahead), so a frame value, `subs` or a value definition never
reaches a variable. Any other third argument is the order, evaluated: one that comes to a positive
integer gives the `Derivative` of that order (`TypedNodes`, as the evaluator rebuilds the call), and
any other (`1/2`, `2.0`) leaves the call, printed as written. The printers write `∂` when the
outermost derivative's expression has two or more free symbols (`PrintForm.partial`: a derivative's
variable counts, a binder's does not), and a chain of derivatives in different variables as one
mixed head, outermost variable first: `∂²/∂y∂x (x y)!`, LaTeX
`\frac{\partial^{2}}{\partial y\partial x}`; the layout tree's `BigOp` holds the `∂` fraction.
ASCII keeps the nested `diff` calls.

### The chain rule in several variables

`calculus.ChainRule.of(f, params, body, args, x, context)` differentiates a call of a defined
function of several parameters, `f(a₁, …, aₙ)`, as `Σ f_{pₖ}(a)·∂aₖ/∂x`: each partial `f_{pₖ}` is
the body's derivative by the `diff` rules, the arguments are put in by the matcher
(`IntegrateRules.replace`), each `∂aₖ/∂x` is the rules' again, and the sum is simplified, so the
value is the plain derivative's (`ChainRuleTest` checks it at random points). The evaluator takes
this path for `diff(f(…), x)` and `d/dx f(…)` of order 1 when `f` is a defined function of two or
more parameters, the arguments are as many, one of them is not a name and depends on `x`, and the
capture check passes; the arguments are evaluated in the scope of `x`, the body with each
parameter itself, renamed fresh when an argument holds it. The steps, in the rule set `chain`, are
`chain.partials` from the derivative of the composite to the partials at the arguments times the
inner derivatives still to take (values `function`, `formula`, `partials`) and `chain.inner`
(value `inner`), then the simplifier's. `DiffNarrator` begins a partial derivative's first step with
`diff.step.partial`, "Differentiate with respect to x, holding y constant", naming the free symbols
of the innermost expression other than its variable.

### Termination

Every rule rewrites a `Derivative` node: `higher-order-constant` removes one, and every rule but
`higher-order` and `merge-orders` replaces a derivative of order 1 with an expression whose
`Derivative` nodes are of order 1 over strict subterms of its expression (a term, the rest of a sum
or product, a base, an exponent, an argument, a Σ body), or with none; instantiating a template
keeps those subterms canonical, and the canonical form can only merge equal derivatives. For the
derivatives with no derivative inside, the multiset of the sizes of their expressions therefore
decreases with each rewrite, which ends (Dershowitz–Manna). Innermost-first, a derivative inside
another is finished first, and to the outer one a stuck inner derivative is an atom, so induction on
the nesting covers nested derivatives. `higher-order` fires only when the new inner `D(u)` is not
stuck, so a rule then rewrites it; `merge-orders` fires only when it is stuck, removing a level of
nesting; by the shared predicate the two never undo each other, and induction on the order ends the
unrolling. `DerivativeProperties` probes it: under 200 rewrites per generated derivative measured,
1,000 asserted.

### Tests

In `engine/src/test/java/mathcanvas/engine/calculus/`:

- `DiffRulesTest`: through `DiffCases`, a positive and a negative case for every rule (the
  piecewise one on a built tree), which `covered()` checks, and a narration line for every rule
  whose arguments are the rule's variables;
- `FreeTest`: binders, derivatives, indefinite integrals and held commands;
- `CalculusTest`: the PLAN examples, textbook derivatives, the simplifier under assumptions,
  higher orders and stuck orders, the forms that stay, partial derivatives, the trace's order and
  replay, and its narration;
- `CalculusPerformanceTest`: a 500-term sum, a product of 30 factors and the fourth derivative of
  `x^x` under a generous bound (each well under a second);
- `DerivativeProperties`: over `DiffArbitraries` (sums, products, powers with numeric exponents and
  symbolic ones kept small where the points are: a leaf, a multiple of `x` or a bounded function,
  exponentials, every table function, logarithms to a base, roots, `atan2`, depth up to 4, in `x`
  and a parameter `a`) under random facts (for `x` none, a sign or the complex domain),
  the derivative agrees with the difference quotient (`DifferenceQuotient`) at three points
  (5,000 tries), the second derivative with the quotient of the first (1,000), and every table
  function on simple inner functions (3,000); the termination probe (200 tries of 20 derivatives
  of order 1 or 2). A point is compared only within the domain of the function differentiated
  (`DifferenceQuotient.defined`): a derivative can have a value where the function has none, from
  a removed singularity, and that value need not be the limit (**rational**). Each requires a share
  of its tries to compare a point (measured about 85% each).

`DifferenceQuotient` compares at `x₀` the derivative with the Richardson value `(4q(h/2) − q(h))/3`
of the central quotients at `h = 10⁻¹⁰`, at 34 digits, within `10⁻⁶` relative plus `10⁻¹⁸` times the
function's size. It skips a point outside the facts of `x`, where the function is undefined, where
it is not smooth (the two central quotients, or the forward and backward ones, disagree: a pole, a
kink, a branch cut), where a step function jumps between `x₀ ± 10h`, where the argument of a
logarithm or of a non-integer power crosses the negative real axis between `x₀ ± 10h` (counted as on
a cut: the function is smooth on either side, so neither pair need see the jump), and where the
central quotient at `10h` differs from the one at `h` by more than `10⁻³` relative (a function
smooth at the scale of `h` agrees to `O(h²)`; one that oscillates or winds faster than `h`, which no
precision repairs, does not). A disagreement is checked again at 60, 120, 240 and 480 digits away
from the branch cuts, and fails only when it persists and its values no longer move between the last
two precisions. Its precondition is the generator's bound: callers must keep evaluated exponents
below 10⁴ in magnitude at the point; beyond that the finite difference carries no information
(`(−1)^w` for a huge `w` aliases to the same value at every step) and the checker may fail on a
correct derivative, so it throws `IllegalArgumentException` on such a function, and the properties
skip such a point first.

## Integration

Package `mathcanvas.engine.calculus`: the rule set `integrate` (`RuleSets.INTEGRATE`), its default
provider `IntegrateProvider` of the operation `Integrate` (**Providers**), and the entry point
`Calculus.integrate(f, x, lower, upper, context)`. Every rule is a named `Rule` through the matcher
whose pattern is an `Integral` node, written `integrate(f_, x_)` in the language
(`integrate(f_, x_, a_, b_)` for a definite one). The rewriter works innermost-first, so linearity
splits an integral into integrals of its terms and each is finished by the table, an identity,
completing the square, a substitution or integration by parts. Each rule has a narration line keyed
`integrate.<rule>`.

### The entry point

`Calculus.integrate(f, x, lower, upper, context)` builds the `Integral` node (indefinite when both
bounds are null), runs the standard simplifier on it, which simplifies the integrand and the bounds
(`2 sin(x) cos(x)` is `sin(2x)` before any rule sees it), then the providers of `Integrate` through
`attempt`, then the simplifier on the answer and a final pass of two rules of the set `integrate`
on the result's top level (`IntegrateResult`): `distribute` multiplies a constant factor into a sum,
the factor of the whole result or of one of its terms, again until none is left, so towers flatten
(`x² e^x − 2(x e^x − e^x)` → `x² e^x − 2x e^x + 2e^x`); for an antiderivative the factor is free of
`x` and the sum holds `x`, for a definite value the factor is a number (`−(−cot 1 − 1)` → `cot 1 +
1`). Then, for an antiderivative only, `constant-term` drops a term free of `x`, since an
antiderivative is one up to a constant (`x − log|x + 1| + 1` → `x − log|x + 1|`). The rules match
the result wrapped in an application whose head no expression can name, so nothing inside a
function application or a power changes, and their steps are recorded on the result. An integrand
with a domain, truth value, collection, relation or `∞` in it is no scalar expression: no provider
is asked, and the integral stays with `integrate.none`. **Facts.** Inside an indefinite integral the
variable keeps its facts, since it is free in the result, which is read in the same context
(`assume(x > 0)` makes `∫ 1/x dx` `log(x)`, `assume(x ∈ ℂ)` keeps it `log(x)` rather than
`log|x|`); a definite integral drops the facts of its variable, which is bound, and the evaluator
first renames that variable to a fresh name when a bound names it, so `assume(x > 1); ∫[1, x] 1/x
dx` keeps `x > 1` for the bound and is `log(x)`. When every provider declines, the
integral as it came is the result, canonical but its integrand not simplified (so the numeric path
and the statement's simplifier see it as written), with the one note that says why. With tracing on, the trace is the three parts' (`Trace.concat`):
the simplifier's rewrites; the provider's steps, each a rewrite of the whole expression in the rule
set `integrate`; the simplifier's again; and the final pass's. A provider that answers without steps is one step in the
rule set `provider` named after it, and when every provider declines without a step the trace has
the step `integrate.none`. The evaluator calls it for `integrate` and `∫` (**Evaluation**,
**Commands**).

A definite integral whose bounds are equal (once simplified) is `0` before any provider is asked,
with the step `integrate.equal-bounds`.

`IntegrateProvider` rewrites the integral with `integrate` and answers only when no `Integral` in
`x` is left in the result (all or nothing: `∫ x + x! dx` stays whole). It records each rewrite as a
step from the whole expression before to the whole expression after, with the rewritten integral
and its replacement as the named values `integral` and `result` (`Steps.record` with values), so
the texts read `Apply the power rule: ∫ x² dx = x³/3`. When an integral stays it records the
terminal step `none` on the integral as it came, which names the techniques of the set, or
a note that says why: `angle-mode` (below); for a definite integral whose antiderivative was found,
`improper` when a bound is infinite or the integrand, simplified at a bound, has no value there
(`∫[0, 1] 1/x dx`), and `singular` otherwise (the antiderivative is not continuous between the
bounds, or that could not be decided: `∫[−1, 1] 1/x² dx`); and declines. A call's rules share a
`IntegrateRules.Search`, which remembers every integral they try, by integrand, variable, depth and
facts, so a trial is never made twice (`RuleSets.INTEGRATE` is the shared search, which remembers
nothing). `IntegrateNarrator` makes one step of a `sum-rule` rewrite and
the linearity rewrites after it (`integrate.step.term-by-term`), and one step of every other
rewrite.

### Rules

In priority order; `∫ ·` is `integrate(·, x_)`. The table's rules take a **linear argument** `u`
of `x`: a polynomial of degree 1 in `x` whose other kernels are free of `x`, its slope `s` free of
`x` and known not to be zero (`IntegrateRules.slope`); the formula divides by `s`. A logarithm is of
an absolute value when its argument is real (an assumptions query), of the argument itself
otherwise; the simplifier then removes an `abs` the facts allow (`log|x|` is `log(x)` under
`x > 0`).

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `undefined` | `∫ undefined` → `undefined` | | |
| `constant` | `∫ c_` → `c_·x_` | `c_` free of `x_`, of an arithmetic kind, with no `undefined` (as `diff`'s `constant`) | `∫ a² + π dx` → `(a² + π) x` |
| `sum-rule` | `∫ u_ + rest__` → the sum of the terms' integrals (computed, every term at once) | | |
| `constant-multiple` | `∫ c_·u__` → `c_·∫ u__` | `c_` free of `x_`, commuting | |
| `power` | `∫ u_^n_` → `u^(n+1)/((n+1)s)` | `n_` free of `x_` (default 1), `n + 1` known non-zero | `∫ √(2x + 1) dx` → `(2x + 1)^(3/2)/3` |
| `reciprocal` | `∫ u_^-1` → `log\|u\|/s` | | `∫ 1/(3x − 2) dx` → `log\|3x − 2\|/3` |
| `exp`, `exponential` | `∫ e^u_` → `e^u/s`; `∫ b_^u_` → `b^u/(s log b)` | `b_` free of `x_`, positive, not 1 | `∫ 2^x dx` → `2^x/log(2)` |
| `sin`, `cos`, `tan`, `cot`, `sec`, `csc` | `−cos u/s`, `sin u/s`, `−log\|cos u\|/s`, `log\|sin u\|/s`, `log\|sec u + tan u\|/s`, `−log\|csc u + cot u\|/s` | | |
| `sec-squared`, `csc-squared`, `sec-tan`, `csc-cot` | `∫ sec(u_)²` → `tan u/s`; `−cot u/s`; `∫ sec(u_) tan(u_)` → `sec u/s`; `−csc u/s` | | |
| `sinh`, `cosh`, `tanh`, `coth`, `sech`, `csch` | `cosh u/s`, `sinh u/s`, `log(cosh u)/s`, `log\|sinh u\|/s`, `atan(sinh u)/s`, `log\|tanh(u/2)\|/s` | | |
| `sech-squared`, `csch-squared`, `sech-tanh`, `csch-coth` | `tanh u/s`, `−coth u/s`, `−sech u/s`, `−csch u/s` | | |
| `square-reciprocal` | `∫ (k_·u_² + c_)^-1`: `sign(c)·atan(u√(k/c))/(s√(kc))` when `k`, `c` have one sign, `(log\|u − r\| − log\|u + r\|)/(2skr)` with `r = √(−c/k)` when opposite | `k_` (default 1), `c_` free of `x_`, `c_ ≠ 0`, their signs known | `∫ 1/(x² − 4) dx` → `log\|x − 2\|/4 − log\|x + 2\|/4` |
| `square-root-reciprocal` | `∫ (k_·u_² + c_)^(−1/2)`: `log(√k u + √(ku² + c))/(s√k)` when `k > 0` (in `\|·\|` when `c < 0`), `asin(u√(−k/c))/(s√−k)` when `k < 0 < c` | as above | `∫ 1/√(1 − x²) dx` → `asin(x)` |
| `odd-power` | `∫ sin(u)^m cos(u)^n` → `∫ sin u·(1 − cos²u)^((m−1)/2)·cos^n u`, or the same with the roles swapped (computed) | `m`, `n` non-negative integers, the odd one (the smaller when both are) at least 3 | `∫ sin³x dx` → `−cos x + cos³x/3` |
| `half-angle` | `∫ sin(u)^m cos(u)^n` → `∫ ((1 − cos 2u)/2)^(m/2)·((1 + cos 2u)/2)^(n/2)` (computed) | both even, `m + n ≥ 2` | `∫ sin²x dx` → `x/2 − sin 2x/4` |
| `product-to-sum` | `∫ sin(v)cos(w)`, `sin(v)sin(w)`, `cos(v)cos(w)` → the sum forms (computed) | `v ≠ w`, both linear | `∫ sin 3x cos 2x dx` → `−cos x/2 − cos 5x/10` |
| `tan-power`, `cot-power` | `∫ tan(u_)^n_` → `∫ tan^(n−2) u·sec²u − tan^(n−2) u`; `cot` with `csc` | `n_` an integer, at least 2 | `∫ tan²x dx` → `tan x − x` |
| `sec-reduction`, `csc-reduction` | `∫ sec(u_)^n_` → `sec^(n−2)u tan u/(s(n−1)) + (n−2)/(n−1)·∫ sec^(n−2)u`; `csc` likewise with `−csc^(n−2)u cot u` | `n_` an integer, at least 3 | `∫ sec³x dx` → `log\|sec x + tan x\|/2 + sec x tan x/2` |
| `complete-the-square` | `∫ q_^n_` → `∫ (A(x + B/(2A))² + C − B²/(4A))^n` (computed) | `n` −1 or −1/2, `q` written out as `Ax² + Bx + C` term by term, `A` known non-zero, `B` present | `∫ 1/(x² + 2x + 5) dx` → `atan((x + 1)/2)/2` |
| `partial-fractions` | `∫ R` → `∫ apart(R)` (computed) | `R` a rational function of `x` alone with rational coefficients, not a polynomial, whose decomposition is a sum other than `R` | `∫ 1/((x − 1)(x + 2)) dx` → `log\|x − 1\|/3 − log\|x + 2\|/3` |
| `substitution` | `∫ f` → `H(g)` (valued: `substitution`, `transformed`, `antiderivative`) | below | `∫ x cos(x²) dx` → `sin(x²)/2` |
| `by-parts-twice` | `∫ e^v·sin(w)`, `e^v·cos(w)` → `(R₁ − R₂)/(1 − k)` (valued: `first`, `second`, `factor`) | below | `∫ e^x sin x dx` → `−e^x cos x/2 + e^x sin x/2` |
| `by-parts` | `∫ u·dv` → `u·v − W`, `W` the residual `∫ v·u′` found whole (valued: `u`, `dv`, `residual`) | below | `∫ x e^x dx` → `x e^x − e^x` |
| `expand` | `∫ f` → `∫ expand(f)` (computed) | `f` a product or a positive integer power of a sum, its expansion a sum | `∫ (x² + 1)² dx` |
| `hermite` | `∫ A/q^k` → `−t/((k − 1)q^(k−1)) + ∫ (s + t′/(k − 1))/q^(k−1)` with `A = s·q + t·q′` (computed, `PartialFractions.hermite`) | proper, the denominator one base proven irreducible of degree at least 2, `k ≥ 2` | `∫ 1/(x² + 1)² dx` → `x/(2(x² + 1)) + atan(x)/2` |
| `log-part` | `∫ (αq′ + β)/q` → `α log\|q\| + β ∫ 1/q` (`log(q)` when `q` has no real root and a positive leading coefficient; computed) | `q` an irreducible quadratic, `α`, `β` not zero | `∫ (x + 3)/(x² + x + 1) dx` |
| `trig-substitution` | `∫ f` → `G(θ(x))` (valued: `substitution`, `transformed`, `antiderivative`) | below | `∫ √(1 − x²) dx` → `x√(1 − x²)/2 + asin(x)/2` |
| `definite` | `∫[a_, b_] f_` → `F(b) − F(a)` (valued: `antiderivative`) | below | `∫[0, π] sin x dx` → `2` |
| `limits`, `diverges` | `∫[a_, b_] f_` → `lim_{x→b⁻} F − lim_{x→a⁺} F`, or `undefined` (valued: `antiderivative`, `lower`, `upper`) | below | `∫[1, ∞] 1/x² dx` → `1`; `∫[1, ∞] 1/x dx` → `undefined` |
| `pole` | `∫[a_, b_] R` → `undefined` (valued: `poles`) | below; before `limits` | `∫[−1, 1] 1/x² dx` → `undefined` |

**Substitution.** The candidates `g` are, in the order of a walk of the integrand and at most 8:
the arguments of applications and the applications, the bases and the exponents of powers and the
powers of a constant base, each depending on `x` and not `x`, a linear one only as the base of a
power. For a candidate that is not linear, `f/g′` (or its `cancel`) with `g` written as a fresh
`u` (`u`, `u₁`, … not in the integrand), and `b^(k w)` as `u^k` for `g = b^w`, must be free of `x`;
every factor of `g′` free of `x` must be known non-zero (so `∫ e^(a x) dx` stays until
`assume(a != 0)`). A linear base `g = ax + b` is substituted through `x = (u − b)/a` instead
(`∫ x/(x + 1) dx`). The integral in `u` is tried with the rule set one depth lower, without a trace,
`u` real when `g` holds no non-real constant and every symbol in it is real under the facts (a
symbol is real unless assumed complex; a part with no symbol is real when the facts say so or the
numerics give it a real value at 20 and at 40 digits, so `log 2` and `√2` are real and `asin(2)`
and `(−1)^(1/3)` are not; where such a `g` is not real for a real `x`, a logarithm or
root of a negative value, the integrand is not real either), and assumed complex otherwise, so the
real form wins on the real domain (`∫ 1/(x log x) dx` → `log|log x|`, `∫ 1/(√x(√x − 1)) dx` →
`2 log|√x − 1|`) and the table takes `log(u)` for a complex `g` (`∫ 2x/(x² + i) dx` → `log(x² +
i)`, `∫ x/(x + i) dx` → `x − i log(x + i)`, `∫ 2x/(x² + a) dx` with `a ∈ ℂ`), and the rule fires
only when it is found whole: the result is its
antiderivative with `g` put back for `u`. One step, narrated with the substitution, the integral in
`u` and its antiderivative.

**Rational functions.** `partial-fractions` runs before `substitution`, which would otherwise
hide the decomposition behind a linear change of variable; linearity and the table finish the
linear terms `c/(x − r)^j`. `hermite` and `log-part` come after `expand` and finish the terms over
an irreducible quadratic; a base of degree 3 or more is finished only when the numerator is a
multiple of its derivative, which `substitution` takes as a logarithm. No algebraic extension is
taken (no `RootSum`), so `∫ 1/(x⁴ + 1) dx` stays.

**Trigonometric substitution.** `TrigSubstitution`, after every other technique, at depth 1 or more:
the integrand, with each `q^(n/2)` of the one quadratic `q` (rational coefficients) written as
`r^n` for a fresh `r`, must be a rational function of `x` and `r` over ℚ. With `q = A(x + h)² + C`
and `u = x + h`, `a² = |C/A|`: `u = a sin θ` (`√q = √(−A)·a cos θ`) for `A < 0 < C`, `u = a tan θ`
(`√q = √A·a sec θ`) for `A, C > 0`, and `u = a sec θ` for `A > 0 > C` only when the bounds of `x`
in the facts put `u ≥ a` (`√q = √A·a tan θ`) or `u ≤ −a` (`−√A·a tan θ`), an even power of
`tan θ` then written as one of `sec² θ − 1`. The integral in `θ` (a fresh `θ`) is tried one depth
lower; its antiderivative has `sin nθ` and `cos nθ` expanded by the rules `sine-of-multiple` and
`cosine-of-multiple`, then each trigonometric function of `θ`, and `θ`, replaced by its value in
`u` and `√q` (`cos θ = √q/(√(−A)·a)`, `θ = asin(u/a)`, `atan(u/a)` or `asec(u/a)`), and the rule
fires when no `θ` is left. One step, narrated with the substitution, the integral in `θ` and its
antiderivative.

**By parts.** Two classes only, each with `u` by LIATE: a product of polynomials in `x` (`u`) times
one exponential with a constant positive base, sine, cosine, hyperbolic sine or cosine of a linear
argument, or an exponential times a sine or cosine (`dv`); or one logarithm, power of a logarithm or
inverse function (`u`) times powers of `x` and polynomials (`dv`, 1 when there are none). `v = ∫ dv`
must be found whole, without a logarithm or inverse function when `u` is one, and so must the
residual `∫ v·u′`, both at the same depth, and the rule's result is `u·v` less the residual's
antiderivative from that trial (one step, naming the residual). A polynomial factor that is a
positive power of a sum declines, so `expand` writes it out first (`∫ (x² + 1)^12 e^x dx` is a sum
of `x^k e^x` integrals). `by-parts-twice` takes
`c·e^v·T(w)`, `T` a sine or cosine: one round with `u = T` gives `R₁ − J₁`, a second on `J₁` gives
`J₁ = R₂ − J₂`, and when `J₂ = k·f` with `k` free of `x` and `1 − k` known non-zero, `I = (R₁ −
R₂)/(1 − k)`.

### Iterated integrals

`integrate(f, [x, a, b], [y, c, d], …)` is built by `TypedNodes` as the nested `Integral` nodes,
the first bracket innermost, so it is the notation's `∫[c, d] ∫[a, b] f dx dy`: the binders nest,
an inner level's bounds may hold the outer variables, and the evaluator evaluates the outer level's
integrand (the inner integral) before it integrates. The parser accepts `integrate` with any number
of brackets after the integrand (its registry entry is 2 or more); any other count but 2 and 4 is
the parser's "integrate takes 2 or 4 arguments, or an integrand followed by brackets".
When the iterated integral is the statement, the evaluator records each inner level's integral as
it was integrated and its value (`levels`, by identity of the typed node), and the trace begins
with one `integrate.iterated` step per inner level, innermost first, from the whole integral with
that level as integrated to the same with its value (values `integral` and `result`), then the
outermost level's own trace; a level whose value is still an integral leaves the outer trace
alone. While a definite integral's integrand is evaluated, the evaluator holds its variable's range
(`ranges`: `min ≤ x ≤ max` when its bounds evaluate to constants and the facts are of an accepted
form), and an inner level integrates under the facts of the enclosing levels' variables it sees, so
`integrate(sqrt(y^2), [x, 0, 1], [y, 0, 2])` is `2`. The brackets are read in order only: a bound of
an outer bracket naming an inner variable is a free symbol from outside.

### Definite integrals

`definite` finds an antiderivative `F` whole with the set, and failing that with the facts that put
`x` between numeric bounds (or on the half-line to an infinite one), which the secant substitution
reads; when `F` is continuous between the bounds, it gives `F(b) − F(a)` (the order of the bounds is
free: `∫[2, 1] x dx` is `-3/2`). The bounds must be real and finite. The check (`Definite`) reads
the kernels of `f` and of `F`: the base of a negative integer power must not vanish on the closed
interval; the base of a fractional power must be positive there, or non-negative for a positive
exponent, a zero allowed at an end (`∫[0, 1] √x dx` is `2/3`, `∫[0, 1] 1/√x dx` stays); the argument
of a logarithm positive (of `log|g|`, `g` not zero), that of `coth`, `csch` and `acot` not zero, `1
− g²` non-negative under `asin` and `acos` and `g² − 1` under `asec` and `acsc`; a linear argument
of `tan`, `sec`, `cot` and `csc` must not cross a pole, and a sine or cosine of a linear argument
that must not vanish is checked the same way; `sin`, `cos`, `atan`, `exp`, `sinh`, `cosh`, `tanh`,
`sech`, `asinh` and `abs` are continuous; any other function of `x` leaves the integral. Each
condition holds by the assumptions (with the interval as facts about `x` when both bounds are
numbers), or, for a polynomial in `x` alone, when every real root the solver finds lies outside the
interval (the signs of `r − a` and `r − b` agree, decided exactly, by the assumptions or numerically
clear of a tie at 40 digits) and the value at the midpoint has the sign; `p + √q` with `q − p²` a
constant is positive when that constant is, and not zero when it is not (`log(x + √(x² + 1))`), and
`sec v + tan v` and `csc v + cot v` are not zero wherever their terms are defined; a pole of `tan`
or `sec` is an integer value of `(g − π/2)/π` within the range of `g` between the bounds (its values
at the bounds and at the real roots of `g′` between them, for a polynomial `g`), and a zero of `cot`
or `csc` likewise of `g/π`. Anything else, or any doubt, leaves the integral to the rules below, and
then unevaluated with the note `integrate.singular` (`∫[0, 2] tan(x) dx`, `∫[1, t] 1/x dx` with
nothing known of `t`), or `integrate.improper` for an infinite bound or a bound where the integrand
has no value.

**Improper integrals.** `limits` and `diverges` (`Definite.ends`) take bounds that are real or
`±∞`, in order, and an antiderivative `F` found as for `definite` and continuous strictly between
the bounds (`Definite.continuousInside`: the same check with a root at an end allowed for every
kernel, an infinite end on the inner side of every root, and the sign taken at a point inside, one
from a finite end beside an infinite one). At a finite end where `F` is continuous on the closed
interval its value is taken; otherwise the one-sided limit from inside (both sides at `±∞`), through
`Limits.limit` and so the limit provider. Two finite limits give `limits` and their difference; an
infinite or `undefined` one gives `diverges` and `undefined`, so each end converges on its own and
there is no principal value; a `Limit` the provider leaves keeps the integral, with the note
`integrate.improper` (`∫[0, ∞] e^(sin x) cos x dx`, whose antiderivative `e^(sin x)` has no limit
the provider decides at `∞`).
`pole`, after `definite` and before any limit is asked, takes a rational integrand over ℚ whose
real poles in lowest terms `RealRoots` finds completely and exactly, each placed against the bounds
by the sign decision (an infinite bound beyond every pole), and gives `undefined` when one lies in
the closed interval, between the bounds or at one, since such a pole is never integrable
(`∫[0, 3] x/(x − 1)² dx`, whose antiderivative's limits are `∞ − ∞`); the step names the poles. A
pole that cannot be placed leaves the integral to the other rules. The limits name their bounds in
the steps (`its limit at 1 is −1 and at ∞ is 0`).

**Angle mode.** Integration is in radians in every mode, as differentiation is. In degrees or
gradians a definite integral with a circular or inverse circular function in `f` or in `F` stays,
with the terminal step `integrate.angle-mode`: its value, `sin(1)` or `atan(2)`, would be read back
in the session's unit. An indefinite integral is given as in radians, like a derivative.

### Termination

The set exists at each depth from 0 to 3 (`IntegrateRules.at(d)`; `INTEGRATE` is `at(3)`), all named
`integrate`. `substitution` and `trig-substitution` are the only rules that lower the depth: each
tries its integral with `at(d − 1)` and declines at depth 0, so substitutions nest at most three
deep, and its result holds no integral. `partial-fractions` never fires on its own result, whose
terms are already in Hermite's form; `hermite` lowers the power of its base by one; `log-part`
leaves the reciprocal of its base, which completing the square finishes; and `pole` leaves no
integral. `by-parts` keeps the depth, since its residual is smaller in a measure of its own:
lexicographically, the number of logarithms and inverse functions (with the power of a logarithm),
which the second class lowers, then the degree of the polynomial factor, which the first lowers;
`by-parts-twice` leaves no integral, and neither does `by-parts`, whose result takes the residual's
antiderivative from its trial. The search's memo makes each distinct trial once, so a chain of
rounds (`x^n e^x`) costs one trial per round, not a tree of them. The other rules each have a
measure: linearity rewrites to strict subterms, the table leaves no integral, `odd-power` and
`half-angle` lower the trigonometric exponents (half angles halve them, and their output's `cos(2u)`
terms come with lower exponents), the power and reduction rules lower `n`, `complete-the-square`
fires only on a quadratic written out term by term and its output is not one, and `expand` fires
only on products and positive powers, whose expansion it never applies to again;
`complete-the-square` acts only on a bare power and `expand` never on one, so the two cannot
alternate. This is an argument, not a mechanised proof; the termination property is its evidence and
the rewriter's cap stays the guard.

### The fundamental theorem

`diff`'s rule `fundamental-theorem` differentiates an integral that stays: `d/dx ∫ f dx` is `f`
for an indefinite integral in `x`, and `d/dx ∫[a, b] f dt`, for a definite one in another variable
whose integrand is free of `x` and whose bounds are not both free of it and are real and finite
(`d/dx ∫[x, ∞] t! dt` stays), is `f(b)·b′ − f(a)·a′`:
`d/dx ∫[0, x²] t! dt` → `2x·(x²)!`. Nothing else is differentiated under the integral sign.

### Tests

In `engine/src/test/java/mathcanvas/engine/calculus/`:

- `IntegrateRulesTest`: through `IntegrateCases`, a positive and a negative case for every rule,
  which `covered()` checks, each through `Calculus.integrate` to the simplified value, a narration
  line for every rule whose placeholders are the rule's variables and values, and the same rules at
  every depth;
- `IntegrateResultTest`: the final pass, distributing through nested constant factors and dropping a
  constant term at the top level only, its steps, and a definite value distributing numbers only;
- `IntegrationTest`: textbook integrals, the integrand simplified first, the step `none`, all or
  nothing, logarithms of absolute values and complex arguments, complex inner functions, the facts
  of an indefinite integral's variable, constants that may be zero, definite integrals and the
  singularities that keep them with the notes that say why, improper integrals by their limits and
  divergent ones, the secant substitution by the facts and by the bounds, the angle mode, the depth,
  the steps' texts and the trace's replay;
- `IntegrationPerformanceTest`: integrands with no elementary antiderivative and large ones, and
  `x^20 e^x`, `x^8 e^x sin x`, `x^30 sin x` and `(x² + 1)^12 e^x`, each group under one whole-run
  budget of 30 s;
- `IntegrationProperties`: over `IntegrateArbitraries` (polynomials; polynomials times `e`, `sin`
  or `cos` of a linear argument; `c·g′·h(g)` with `g` from `x²`, `x³ + 1`, `sin x`, `cos x`, `e^x`,
  `log x`, `x² + 1` and `h` from the table; `x^n e^x`, `x^n log x`, `x^n sin x`, `x^n cos x`,
  `atan x`, `asin x`, `log²x`, `e^(ax) sin(bx)`; `sin^m cos^n`, `sin(ax) cos(bx)` and the powers of
  `tan`, `sec`, `cot`, `csc`; reciprocals and reciprocal square roots of quadratics of known sign,
  the opposite-sign and `k < 0` forms among them;
  sums of these), each with an interval where it is smooth: every integrand is integrated whole,
  `DifferenceQuotient` of the antiderivative agrees with it at three points of the interval, and
  `d/dx ∫ f dx − f` expands and simplifies to 0 on the polynomial, exponential, polynomial times
  sine or cosine, substitution and by-parts families (the share elsewhere is recorded); `g′/(g + c i)`
  and `g′·(g + c i)^-2`, `c` a non-zero rational, differentiate back as complex values at real
  points; a definite integral between
  random rational bounds inside the interval is evaluated in at least 90% of tries and agrees with
  adaptive Simpson quadrature (`testing.AdaptiveSimpson`, over the compiled integrand) within
  `10⁻¹⁰·max(|I|, ∫|f|)`; and the rule set terminates on samples with integrands that have no
  elementary antiderivative;
- `RationalIntegrationProperties`: over proper rational functions whose denominators are products
  of distinct and repeated linear factors and an irreducible quadratic, up to its square, every one
  is integrated whole, `d/dx ∫ f dx − f`, with `sign(u)/|u|` read as `1/u`, cancels to 0, and the
  difference quotient agrees away from the poles; a definite integral between the poles is evaluated
  in at least 95% of tries and agrees with adaptive Simpson within `10⁻¹⁰`; over the three root
  forms, shifted, times a power or as a reciprocal (the secant one with its side as a fact), the
  antiderivative's difference quotient agrees with the integrand, at least 90% integrated; and the
  rule set terminates on both.

In `…/eval/`, `ImproperIntegralProperties`: convergent improper integrals (a polynomial times a
decaying exponential on `[0, ∞)`, reciprocals of positive quadratics and of a product of two on the
line, powers of `x` integrable at 0 or at `∞`) agree at 20 digits with the double-exponential rule
(`NumericIntegral`) on the same integral, and the divergent powers are `undefined`.

In `…/provider/`, `IntegrateSeamTest` (a provider that integrates polynomials only, raw: routed to,
falling through, the step `none` when every provider declines, the two providers' results equal); in
`…/eval/`, `IntegrationEvaluationTest` (the notation, the free and the bound variable, the outer
facts of the bounds, poles, the angle mode, what stays, the steps). The corpus is
`engine/src/test/corpus/integration.mc`.

## Limits

Package `mathcanvas.engine.calculus`: the language's `limit` and `lim`. `Limits.limit(f, x, a,
side, ctx)` takes the evaluated expression, the variable, the evaluated point (finite, `∞` or `-∞`)
and the side (`BOTH`, `LEFT`, `RIGHT`), and returns a `Rewriter.Result`: the limit, a value free of
`x`, `∞`, `-∞` or `undefined` (the limit does not exist: the sides differ, the function oscillates,
a pole of odd order is approached from both sides), or the `Limit` node when no method decides. A
side at an infinite point is the `IllegalArgumentException` "a limit at ∞ or -∞ has no side"; an
`undefined` expression or point gives `undefined`, and a body or point that is not a number's
arithmetic (a collection, a lambda, a truth value, a domain) leaves the node. The variable is real:
the facts about it are those of the place where the limit stands, which hold for the point, never
inside the expression. The `Limit` node is first simplified by the standard list, which drops the
facts about `x` in the body as the rewriter does in every binder, so `sin(x)² + cos(x)²` is 1 and a
rational common factor is cancelled before any method; its steps come first in the trace. Inside
the methods every simplification and algebra call on an expression in `x` (the quotient of
L'Hôpital's rule, cancelling, the conjugate's product, the standard limits' matching) runs under the
assumptions without `x` (`LimitMethods.in`); the facts of the point apply only to values free of
`x`.

### The operation

The limit goes to the provider operation `Limit(expr, var, point, direction, precision)`
(**Providers**) through `attempt`; its result is normalised by `Canonical.of` at the precision.
The default provider, `LimitProvider`, rewrites the `Limit` node with the rule set `limit`
(`RuleSets.LIMIT`) and records each rewrite as a step with its values (`Steps.record` with values);
it declines when the node stays. When every provider declines the node is returned, and with the
trace on the numeric probe runs: its step is `limit.numeric` with the values `estimate` and
`digits` when it settles, `limit.none` otherwise, a note from the node to itself (the only rewrite of
the trace that changes nothing). A provider that
answers without steps is one step in the rule set `provider` named after it. The numeric probe never
gives a value: an exact query never returns a decimal (`PLAN.md` §11, 2026-10-30).

### The methods

Every rule of `limit` has the pattern `l_` for a `Limit` node and rewrites it to its value by one
method (`LimitMethods`), in this order, or does not apply; its value `derivation` is the list of
lines the narration shows (**Narration**), the inner decisions first and the method's conclusion
last.

| Rule | Decides | When |
|---|---|---|
| `substitution` | `f(a)` | `a` finite and `f` continuous there by the lax certificate (**Series and sums**, **The continuity certificate**); a condition on a parameter makes the answer generic, named in the line (`1/a`, where `a ≠ 0`) |
| `pole` | `±∞`, or `undefined` from both sides at a pole of odd order | `f` a rational function in `x` alone, `a` rational, the denominator zero at `a`: its order `m` is that of the first derivative not zero there, the sign that of the numerator over that derivative, times `(-1)^m` from the left |
| `limit-laws` | the limits of the operands combined | a sum, product, power or function of one argument whose operands' limits are decided and combine determinately: no `∞ − ∞`, `0·∞`, `0^0`, `1^∞`, `∞^0`; a power of a base tending to 0 or a function at a jump uses the side the base takes the limit from (below); a function at `±∞` its limit there (`exp`, `log`, `atan`, `asec`, `acsc`, `acoth`, `acsch`, `tanh`, …), and the side it takes it from where known; a constant times one factor that takes its value from one side, the side the constant's sign gives; `asin` and `acos` at `±1` and `acosh` at 1 approached from inside the real domain their value there, by continuity on the closed domain (`lim[x → 1−] asin x = π/2`); `tan`, `cot`, `sec` and `csc` at a pole as a quotient of `sin` and `cos` at the next level; a power with a symbolic exponent free of `x` by the exponent's sign (after `assume(a > 0)`, `x^a → ∞` at `∞`) |
| `sine-over-argument`, `one-minus-cosine`, `exponential-minus-one`, `compound-interest`, `root-of-one-plus` | 1, 0, 1, `e`, `e` (times the constant factors) | the expression matches `sin(u)/u`, `(1 − cos(u))/u`, `(eᵘ − 1)/u` with `u → 0`, `(1 + 1/u)ᵘ` with `u → ∞`, `(1 + u)^(1/u)` with `u → 0` |
| `exponential-form` | `e^L` for `L` the limit of `v·log(u)` (`∞` and `-∞` giving `∞` and 0) | `u^v` with both in `x` and `u` tending to a positive value, to `∞`, or to 0 from above |
| `one-sided` | the limit of `f` with its jumps replaced by their forms on the side | a one-sided limit at a jump of `|u|`, `sign`, `floor`, `ceil`, `round` or `trunc` (`|u|` → `±u`, `floor(u)` → `n` or `n − 1`, …), the side of `u` decided |
| `cancel` | the limit of `Algebra.cancel(f)`, a cancelled form simplified | the reciprocal functions and `tan`, `tanh` written as quotients of `sin` and `cos` (or `sinh`, `cosh`) first; the numerator and denominator have a common factor (`Gcd` through the provider), or a sum's fractions combine (`combine`: `sec(x) − tan(x)` is `(1 − sin(x))/cos(x)`) |
| `conjugate` | the limit after multiplying above and below by the conjugate | a factor `S` or `S⁻¹`, `S` a sum of two terms one of which has a root of `x`; the conjugate keeps the root's sign positive, `S·S̄` expanded; for a cube root `a − b` is `(a³ − b³)/(a² + ab + b²)` (`∛(x³ + x²) − x → 1/3` at `∞`) |
| `combine-logs` | the limit of the sum with its logarithms combined into one | a sum whose terms `cᵢ·log(·)` in `x` (`cᵢ` exact numbers) have limits among them `∞` and `-∞`: `Σ cᵢ log|uᵢ|` becomes `log|Π uᵢ^cᵢ|` and, where every argument is plain, `Σ cᵢ log uᵢ` becomes `log Π uᵢ^cᵢ`; a plain `log uᵢ` only where `uᵢ` is positive near the point (its limit positive or `∞`, or 0 from above), so `log uᵢ = log|uᵢ|` there; the quotient cancelled (`Algebra.cancel`) so the side of a limit 0 is known; the other terms kept |
| `leading-term` | the limit of the leading term | `a = ±∞` (`-∞` by `x → -x`) and the growth of `f` found (`Growth`: `c·e^(Σ λ_q x^q)·x^r·log(x)^s`, decided by the leading `λ`, then `r`, then `s`; `|u|` first written `±u` by the sign of `u` far out, so `|x| − x` cancels, and `∛u` by its argument's; leading terms that cancel, or an order not decided, decline) |
| `squeeze` | 0 | a product of bounded factors (`sin`, `cos`, `atan`, `tanh`, `sign`, `acot` of a real argument, and their positive powers) and a part that tends to 0; or, at the next level, a product of one step function `s(u)` and a rest of constant sign near the point, between the products with the step's bounds (`u − 1 < floor(u) ≤ u`, …) when those have one limit (`floor(x)/x → 1` at `∞`) |
| `oscillation` | `undefined` | `c·sin(u)` or `c·cos(u)`, `c` not zero, with `u → ±∞` and no step function in `u` (a step-valued argument may take only the values where the function repeats: `cos(2π·floor(x))` is 1) |
| `reciprocal-point` | the limit of `f(±1/t)` as `t → 0⁺` | `a = ±∞`, `t` a fresh name |
| `lhopital` | the limit of `f′/g′` | `f/g`, a quotient by negative integer powers, or a product `0·∞` written as one (the infinite factors over the reciprocal of the others first), with both limits 0 or both infinite; the derivatives through `Calculus.differentiate`; neither function with `|u|`, `sign` or a step function (their jumps may gather at the point), and the derivative of `g` not zero and without a circular function of an argument that grows without bound (its zeros may gather) |
| `series` | `c/d`, 0, or the pole's sign | `a` finite and `f = N/D` with both smooth at `a`: the first terms `c(x − a)^p` and `d(x − a)^q` of their expansions that are not zero, up to order 6 (`TaylorRules.Derivatives`) |
| `two-sided` | the common value, or `undefined` when the sides differ or one has no limit | a two-sided limit at a finite point whose two one-sided limits are decided |

A rule's method decides the limits it needs with the same methods in the same order
(`LimitMethods.decide`, one memo per rule's application): the limits of strictly smaller expressions
(an operand, a factor, a numerator) at the same level, and the limit of an expression the method made
(a cancelled or conjugated quotient, `f′/g′`, the one-sided form, `v·log(u)`, `f(1/t)`, the two
sides, a `tan` as a quotient) at the next level. `limit` exists at the levels 0 to 4
(`LimitRules.at`): the methods that decide by another level are absent at level 4, and `lhopital`
from level 3, so it applies at most three times along a chain. A quotient is read from a product by
its negative integer powers and with the reciprocal functions as quotients (`csc(u) = 1/sin(u)`,
`cot(u) = cos(u)/sin(u)`, and the hyperbolic ones), as the simplifier writes `1/sin(2x)` as
`csc(2x)`. A finite value carries the side it is
taken from when known (from the laws, a series' leading term, or the first derivative not zero at
the point), which `1/u`, `log(u)` and the step functions read. A method whose algebra refuses its
input (`AlgebraLimitException`, an `ArithmeticException`, a kernel refused) does not apply.

**Termination.** Every rule is terminal: it replaces the `Limit` node by a value with no `Limit`
in it, or does not apply, so the rewriter stops after one rewrite per node. A method's own work is
finite: the limits it needs are of strictly smaller expressions at the same level, of which there
are finitely many, or at a higher level, of which there are five; a limit already being decided is
not decided again inside itself (it does not apply there). `LimitPerformanceTest` probes the cases
that would loop without the levels (`√(x² + 1)/x`, under which L'Hôpital's rule cycles) within the
whole-run budget (**Timing budgets**).

**The numeric probe** (`Limits.probe`) evaluates `f` through the context's numerics at `a ± 10^-k`,
or at `±10^k` for an infinite point, for `k = D + 2 … D + 11` from each side asked for, with `D =
min(precision, 12)` and `3D + 20` working digits, and accepts when the last five values agree to `D`
digits with the last one and the two sides agree; the estimate is the last value rounded to `D`
digits. A point with a symbol, a value that is not real or not defined, gives no estimate.

### Narration

`LimitNarrator` (owner `limit`) makes one step of each rewrite. A rule's step is its derivation:
each line in order, a rule's line with the text `limit.<rule>` and the lines that are no method's
own (`substitution-where`, `pole-sides`, `sides-differ`, `combine`, `squeeze-between`,
`exponential-value`) with `limit.step.<name>`, joined by `limit.step.then`, the first letter a
capital when the text starts with a word; a set of conditions prints as the list of its members
(`for a ≠ 0`). A method that transforms the limit says so first, then its inner decisions in order
(`Write x^x as e^(x log(x)); …; so x^x → 1`), and a one-sided line names its side (`From the
right, |x|/x is 1`). The lines of a part's routine decision (`substitution`, `limit-laws`, `pole` of
a part) are left out of the whole's derivation, and each application of L'Hôpital's rule is a line of
its own with the limits of the numerator and the denominator and their derivatives;
`steps(lim[x → 1] (x^2 - 1)/(x - 1))` is the simplifier's `Cancel the common factor x - 1: (x² -
1)/(x - 1) = x + 1`, then `By substitution, x + 1 → 2`. The events have their texts: `limit.none`,
`limit.numeric` (with the estimate and its digits).

### Tests

In `engine/src/test/java/mathcanvas/engine/calculus/`, through `LimitCases` (the test numerics, the
trace on): `LimitRulesTest` decides a case at the top with each rule, and checks its value, its rule,
its narration, its trace as a chain of real rewrites (`TraceChecks.assertChain`; a note's is the node
to itself), and that every rule of the set is covered, `cancel` on the rule set itself (the
simplification before the methods cancels a rational body); the facts about `x` not holding in the
expression (`assume(x > 0)`, `sqrt(x^2)/x` from the left is −1); `LimitPerformanceTest` (the hard
cases in one run, about two seconds); `LimitProperties`: generated continuous functions at random
rational points and from random sides equal their values (40 tries); rational functions with integer
roots at a pole of random order (60 tries) follow the sign of their factors and the side, and the
values `10⁻⁸` from the pole have that sign; rational functions at `±∞` (60 tries) follow their
degrees and leading coefficients, and the values at `±10⁹` agree; quotients `g₁·h/(g₂·k)` of two
different functions vanishing at the point (`u`, `sin u`, `eᵘ − 1`, `log(1 + u)`, `tan u`, …) and
smooth `h`, `k` with `k` positive (40 tries), under no facts, `x > 0` or `x < 0`, have the limit
`h(a)/k(a)` within `10⁻³⁰`, which the values `10⁻⁷` away approach; a point beyond the test
evaluator's bounds is skipped. In `…/provider/`, `LimitSeamTest`: a trivial provider routed to, the rest
falling through, and the node with `limit.none` when every provider declines. In `…/eval/`,
`LimitEvaluationTest`: the notation, the bound variable, the point's assumptions, the side at `∞`, the
simplification first, the steps and the probe's note. The corpus `limits.mc`.

## Series and sums

Package `mathcanvas.engine.calculus`, beside differentiation.

### Taylor polynomials

`Calculus.taylor(f, x, a, n, context)` rewrites `taylor(f, x, a, n)` with the rule set `taylor`
(`RuleSets.TAYLOR`), then runs the standard simplifier once on the result; with tracing on, the
trace is the rule's rewrite then the simplifier's (`Trace.concat`). The set has one valued rule,
`polynomial`, whose pattern is a `taylor` call with a symbol variable, a finite point free of it
and an integer order `n ≥ 0`. It differentiates `f` `n` times through `Calculus.differentiate`
(under the context without the facts about `x`, untraced), evaluates each derivative at `a` by
substitution (`Subst`, which refuses a derivative or an indefinite integral in `x`) and the
simplifier, and builds `Σ f⁽ᵏ⁾(a)·(1/k!)·(x − a)ᵏ`, canonical. Its values are `derivatives`, the
list of `f⁽ᵏ⁾(a)`, and `point`, the equation `x = a`; `SeriesNarrator` (owner `series`) writes them
as one step, `taylor.step.derivative` per derivative inside `taylor.polynomial`.

The derivative rules ignore jumps, so the polynomial is built only where `f` is `n` times
differentiable: each `f⁽ᵏ⁾` for `k < n` must pass the strict continuity certificate at `a`, and
`f⁽ⁿ⁾` the lax one (**The continuity certificate**). A derivative left unevaluated, one whose value
at `a` is not finite (it holds `∞` or `undefined`), or one that fails its certificate throws
`TaylorException` with the order, 0 being `f` itself: "the derivative of order k is undefined at x =
a" for a value that is not finite, "the derivative of order k could not be computed" for one left
unevaluated, and "the derivative of order k could not be shown continuous at x = a" when `f⁽ᵏ⁻¹⁾`
has a value but its certificate fails, since the certificate is conservative (`x^(5/2)` at 0 is
refused though it is twice differentiable there). The evaluator turns it into an evaluation error
(**Commands**). Symbolic angles are radians, as for a derivative.

### Sums and products

**Term by term.** For integer bounds with `b ≥ a − 1` and at most `Calculus.MAX_TERMS` (10,000)
terms the evaluator takes the terms itself: the body as written evaluated in a frame of the index at
each value, under the session's settings, so the angle mode applies and held commands inside
(`taylor`, `limit`, `diff`, a nested sum) evaluate; the values are added or multiplied in pairs, so
exact sums of many fractions stay of balanced size. The step is `sum.terms` or `product.factors`,
from the node to the value. A body free of the index is one term repeated. A statement takes at
most 100,000 terms one by one in all (`Evaluator.TERM_BUDGET`), checked before a sum's terms are
taken against its count times the terms of the integer-bounded sums written in its body. A body
with such sums in it that evaluated to arithmetic over numbers and symbols alone gives its terms by
substituting each value in the evaluated body, through the evaluator's folding, so the nested sum is
not taken again for each term; there the estimate is the count times the evaluated body's terms
(`sum(sum(1/(j k), j, 1, 300), k, 1, 400)` is `H₃₀₀·H₄₀₀`). A nested
sum that would pass the budget, or a term that is not a number's arithmetic (a lambda), gives up
every term-by-term evaluation around it, so no value is half evaluated: the node goes to
`Calculus.sum`, and when it stays its note is `budget` or `not-arithmetic`.

`Calculus.sum(node, context)` first simplifies the node with the standard list, which drops the
facts about the index in the body (`sum(log(e^k), k, 1, n)` is a polynomial sum), then rewrites a
`BigSum` with the rule set `sum` (`RuleSets.SUM`) or a `BigProduct` with `product`
(`RuleSets.PRODUCT`), then runs the standard simplifier once when a rule evaluated it
(`Trace.concat`). When none did, the node stays, and with tracing on the trace ends in a note from
the node to itself that says why: `too-many-terms` (integer bounds with more than 10,000 terms and
no closed form), `bounds` (numeric bounds that are not integers, or integers with `b < a − 1`),
`none` otherwise; the evaluator gives the notes `budget` and `not-arithmetic` of a term-by-term
evaluation it gave up. The evaluator calls it for `sum`, `product`, `Σ` and `Π` (**Commands**).

Every rule is valued, its pattern any `Σ` or `Π` node, and terminal (the node becomes an
expression without it). A node takes part only when each bound is an integer or has a symbol in it
(the upper also `∞`), integer bounds are at most an empty range apart, and the body is arithmetic
(no lambda, collection, truth value, relation, piecewise expression or domain). In order:

| Rule | Value | When |
|---|---|---|
| `sum.binomial` | `(1 + x)^n` | `Σ[k=0,n] binomial(n, k)·x^k`, `x` free of `k` (1 when absent) |
| `sum.polynomial` | `Σ c_p (F_p(b) − F_p(a − 1))`, `F_p(n) = Σ_{k=1}^n k^p` by Faulhaber's formula from the exact Bernoulli numbers (`B₁ = +1/2` in it); factored by the factor algebra when it is a polynomial in symbols of degree at most 20, else expanded; `±∞` for an infinite upper bound, by the leading coefficient's sign | the body a polynomial in `k` of degree at most 200 with coefficients free of `k`, a sum of at most `SumRules.MAX_MERGED` (64) terms |
| `sum.geometric` | `c(r^(b+1) − r^a)/(r − 1)`, with the condition `r ≠ 1` (value `conditions`) for a symbolic `r`, `c(b − a + 1)` when `r = 1`; at `∞`, `c·r^a/(1 − r)` when `|r| < 1`, `±∞` when `r ≥ 1`, `undefined` when `r ≤ −1` | the body `c·Π uᵢ^(αᵢk + βᵢ)`, bases and coefficients free of `k`: `r = Π uᵢ^αᵢ` (value `ratio`) |
| `sum.arithmetic-geometric` | `α·(T(b) − T(a − 1))` plus the geometric sum of `β`, `T(n) = r(1 − (n + 1)rⁿ + n r^(n+1))/(1 − r)²`; at `∞` with `|r| < 1`, `r/(1 − r)² − T(a − 1)` | `(αk + β)·c·r^k`, `r` decided not 1 |
| `sum.telescoping` | the boundary terms: for each group of terms `c_j·g(k + α₀ + d_j)` with `Σ c_j = 0`, `Σ_j c_j (Σ_{i=1}^{d_j} g(b + α₀ + i) − Σ_{i=0}^{d_j−1} g(a + α₀ + i))`, the first part dropped at `∞` (for logarithms, `±∞` there unless `Σ c_j d_j = 0`) | a rational body in `k` alone whose partial fractions are over linear bases with no polynomial part (`g(u) = u^−m`, grouped by `m` and `α mod 1`), no pole in the range: those of each of its terms, added, at any length, or, where a term has a base of higher degree, those of the terms merged into one rational function, at most 64 terms with a merged denominator of degree at most 64; or a sum of rational multiples of logarithms of rational functions of `k` that factor into monic linear factors over ℚ positive on the range (`g = log`); shifts at most 100 |
| `sum.p-series` | `c(ζ(2m) − Σ_{k<a} k^(−2m))`, `ζ(2m) = (−1)^(m+1) B_2m (2π)^2m/(2(2m)!)`; `±∞` for `q ≥ −1` | `Σ[k=a,∞] c·k^q`, `a ≥ 1` an integer, `q = −2m` with `2m ≤ 200`, or `q ≥ −1` |
| `product.constant`, `product.factorial`, `product.geometric` | each factor's product: `c^(b − a + 1)`, `((b + m)!/(a + m − 1)!)^e`, `u^(α(a + b)(b − a + 1)/2 + β(b − a + 1))`, multiplied; the rule is named by the kind present (a linear factor makes it `factorial`, else an exponential `geometric`) | factors free of `k`, `(k + m)^e` with integers `m` and `e` and `a + m ≥ 1` not decided false, and `u^(αk + β)` |

`SeriesNarrator` writes each rewrite with its rule's text, `sum.<rule>` or `product.<rule>`, and a
generic answer's conditions after it (`sum.step.where`).

### The continuity certificate

`Continuity.at(f, x, a, ctx, strict)` decides, operator by operator, that `f` is continuous at
`x = a` on the real line, and returns the conditions it assumed, or empty when it cannot. Each node
is classed as failing, continuous, or continuous and real-valued near the point (needed by the
functions continuous only for a real argument): an expression free of `x` when it is finite (real
when the assumptions say so), `x` itself, sums and products of their operands, `b^k` for an integer
`k ≥ 0`, `b^k` for a negative `k` where `b(a) ≠ 0`, a power with another numeric exponent and `√`
where a real base is positive (real), or not zero (continuous, the principal value), and a base that
may be complex where it is positive; `c^u` for a positive constant `c` or `e`; `u^v` with both in
`x` where `u` is real and positive; `sin`, `cos`, `exp`, `sinh`, `cosh`, `tanh`, `sech`, `atan`,
`acot`, `asinh` of a real argument; `tan` and `sec` where `cos(u(a)) ≠ 0`, `cot` and `csc` where
`sin(u(a)) ≠ 0`; `coth`, `csch`, `acsch` and `sign` where `u(a) ≠ 0`; the logarithms where a real
argument is positive (real) or not zero (continuous: `log|u| + iπ` along the real line); `asin`,
`acos`, `atanh` on `(−1, 1)`, `acosh` above 1, `asec`, `acsc`, `acoth` outside `[−1, 1]`, `asech`
on `(0, 1)`; `floor`, `ceil`, `trunc` where `u(a)` is decided not to be an integer, `round` not a
half-integer; `abs` and `cbrt` of a real argument. Anything else fails.

The **strict** certificate, for Taylor polynomials, also asks for smoothness: `|u|` and `∛u` where
`u(a) ≠ 0`, and no root of zero. The **lax** one, for limits, accepts a power with a positive
numeric exponent of a base that is zero at the point, whose value there is 0 from either side.

The values at the point are decided by `Signs`: a number by its value, an expression with symbols by
the assumptions and by the range its symbols' bounds give (`Signs.Range`: sums, numeric multiples,
products and positive integer powers of non-negative ranges, each end open or closed as the values
take it, so `s − 1` is positive after `assume(s > 1)` and `1 − r²` after `assume(0 < r, r < 1)`; a
product's lower end of 0 is open only when every factor's lower end of 0 is, so `r·s` may be 0
after `assume(r >= 0, s > 0)`), a closed constant (`√2 − 1`, `cos(1/2)`) by the simplifier, which proves a zero,
or by its value through the context's numerics at 40 and 80 digits, which proves a sign only when
both are beyond `10⁻²⁵` with the same sign. A value that depends on a parameter and is not decided
becomes a condition (`a ≠ 0`, `a > 0`): the answer is the generic one. Undecided otherwise, the
certificate fails, so a context without numerics (`Numerics.NONE`) decides only exact values.

### Tests

In `engine/src/test/java/mathcanvas/engine/calculus/`, `TaylorProperties` (25 tries each, about a
second together) over sums, products and compositions of functions smooth on the real line
(`TestNumerics` gives the test evaluator's numeric values to the context), at rational points in
`[−1, 1]` and degrees 0 to 3: the polynomial is within 1.5 times the Lagrange bound (the largest
`|f⁽ⁿ⁺¹⁾|` on a nine-point grid) of `f` at a point `h ≤ 9/200` away, and its derivatives of orders
0 to `n` at the point are `f`'s within `10⁻³⁰` relative. `SumsTest`: each rule of `sum` and `product`
with its value and step, its trace a chain (`TraceChecks.assertChain`), the notes (each from the node
to itself), the body simplified first, `Σ k^200` and summands too long to merge (65 powers, 65
quadratic bases, 1000 linear ones) within the budget, a 101-term summand telescoping term by term,
and that every rule is covered; `SumProperties`: summands of
every family (polynomials of degree 3, `c·r^k`, `(αk + β)r^k`, `1/((k + p)(k + q))`,
`log((k + 2)/(k + 1))`, `binomial(n, k)·r^k`, partial fractions written term by term whose
coefficients cancel; 70 tries) and products (constants, `c(k + m)`,
`(k + m)²`, `r^(αk + β)`; 40 tries) in a symbolic upper bound equal the term-by-term value at a
random integer bound within `10⁻³⁰`, the latter evaluated term by term by the evaluator. In
`…/eval/`, `TaylorEvaluationTest`: the display order, a symbolic order, the free variable, every
error and the narrated step; `SumEvaluationTest`: term by term, each term under the session (the
angle mode, held commands inside), the statement's budget (a nested sum evaluated to arithmetic
taken by substitution), the step, and `Σ[k=1,10000] 1/k` and long nested bodies (1000 × 200
terms, and 400 terms to a symbolic bound) within the budget. The corpus
`series.mc`.

## Applications

Package `mathcanvas.engine.calculus`, class `Applications`: the commands of `docs/language.md`
§14.15, `Applications.of(call, context)` on a call whose scope and bounds the evaluator has
evaluated, its variable bound. Each sets its quantity up and calls `Calculus.integrate` for every
integral, `Calculus.differentiate` for every derivative and `Solver.solve` for every root set;
nothing here integrates, differentiates or solves by its own means, and no tree is rewritten here.
The steps are rewrites of the whole expression at the root, in the rule set `applications`, named
`<command>.<step>` and chained (each one's `after` the next one's `before`); when a single integral
finishes the command, its own trace follows (`Trace.concat`).

- **Bounds.** `arclength`, `area`, `volume`, `shellvolume`, `maximize` and `minimize` decide the
  order of `a` and `b` by the sign decision of definite integrals (`Definite.sign` of `b − a`: an
  exact sign, the assumptions, then numeric values at 20 and 40 digits that agree): equal bounds
  give the step `empty` and 0 (for the optimisers the one candidate), `a > b` is the error "<command>
  needs a ≤ b", an undecided order the step `none` and the call.
- **Arc length, discs.** The formula of the form in use (`formula`: `∫ √(1 + f′²)` for a graph,
  `∫ π f²` for discs; `curve`: `∫ √(x′² + y′²)` for a pair), the integrand simplified when that
  changes it (`integrand`), then `Calculus.integrate`.
- **Unsigned integrals.** `area` (of `h = f − g`), washers (of `h = f² − g²`, times π) and shells
  (of `f` with the weight `x`, times 2π, after the check that `a ≥ 0`, where `|x f| = x |f|`): the
  zeros and poles of `h` by `RealRoots.of`, those strictly inside `(a, b)` by the sign decision,
  sorted by it; each piece `|∫ weight·h|` between consecutive ends, integrated without its trace, so
  a piece ending at a pole is an improper integral, which may diverge; the steps `formula`
  (`washers` for washers, `∫ |weight·h|`), `split` (the values `roots`, the zeros and poles inside,
  and `pieces`) or `whole` (none inside), or `diverges` and `undefined` at once when a piece
  diverges, and otherwise `value`, the simplified sum, when that changes it; then the final pass of
  a definite integral's value (`IntegrateResult`: `distribute`, a number multiplied into a sum, its
  own step), which for π or 2π times the sum runs on the sum within the `value` step, since it
  distributes numbers only. When the zeros and poles are not provably complete and exact, or a place
  or an order is undecided, the step `crossings` gives the integral of the absolute value, which
  goes to `Calculus.integrate` (it finishes when the absolute value simplifies away, `∫[0, 1] |t|
  dx` → `|t|`) and then to the evaluator's numeric path.
- **Zeros and poles.** `RealRoots.of(g, x, ctx, exact)` uses the solver's sets only when `g`,
  canonical, is built of `x`, integers and rationals by sums, products and integer powers. A
  constant has neither; otherwise its numerator and denominator in lowest terms (`Algebra.cancel`)
  are polynomials with rational coefficients, so the solver's real roots of each (radicals, or
  Sturm-isolated decimals) are all of them: the zeros and the poles. Non-real roots
  (`Definite.real`) are dropped; a root whose reality is undecided, or a set that is not finite,
  refuses; with `exact`, so does a decimal.
- **extrema.** `f′ ≡ 0` (every point critical) is the step `none` and the call. The critical
  points are the zeros of `f′` (decimals allowed), sorted; none is the step `no-critical` and the
  empty set; otherwise the step `critical` (values `derivative` and `points`). Each is classified
  by the sign of `f″` at it, and when that is 0 or undecided by the first-derivative test: the
  sign of `f′` at `x₀ ± δ`, `δ` the largest `2^−j` below half the distance to the neighbouring
  zeros and poles of `f′` (by their values at 40 digits; 1 for a lone point), so none lies
  between and the sign is the one next to the point. Different signs make a minimum or a maximum,
  equal signs neither; a zero or undecided sign, an undecided `f″` or an undecided order of the
  zeros and poles, the step `none` and the call. The step `classify` (values `minima`, `maxima`,
  `neither`) gives the set of the extrema `[x₀, f(x₀)]`, in the order of `x₀`.
- **maximize, minimize.** `f` continuous on `[a, b]` (`Definite.continuous`, the bounds real and
  finite: `Definite.bound`), the critical points inside `(a, b)` as for `extrema`; the step
  `candidates` lists `[x₀, f(x₀)]` for `a`, the critical points and `b` in order; the values are
  compared pairwise by the sign decision, a tie keeping the earlier, and the step `value` gives the
  best. A discontinuity that cannot be ruled out, an incomplete root set, `f′ ≡ 0` or an undecided
  comparison: the step `none` and the call.

`Applications.STEPS` lists every step. `ApplicationsNarrator` makes one narrated step of each,
with its text `applications.<command>.<step>`; `none` is the terminal note.

### Tests

In `engine/src/test/java/mathcanvas/engine/eval/`:

- `ApplicationsEvaluationTest`: each command's textbook values, the integral left when there is
  no closed form or the crossings are not known, the bounds (equal, reversed, undecided), shells
  left of the axis, the bound variable (a value of the name, a definition's parameter), the
  classification by `f″` and by the first-derivative test, the calls that stay, ties, the
  five-argument forms, poles (an area split at one, the first-derivative test beside one),
  constant differences and derivatives, and the steps of every traced value, chained
  (`TraceChecks.assertChain`);
- `ApplicationsProperties`: over generated polynomials and rational bounds, the arc length and the
  disc volume against adaptive Simpson quadrature of their integrands within `10⁻¹⁰`; a line's
  and a circle arc's lengths against their closed forms; the area between polynomials with planted
  rational crossings against the sum of the unsigned pieces' quadratures; the extrema of
  antiderivatives of `c ∏ (x − rᵢ)` with planted simple and double roots against the sign of the
  derivative either side, and again with an odd-order pole of `f′` planted within 1 of the roots;
  and `maximize`/`minimize` of those against a dense scan of the interval.

In `engine/src/test/java/mathcanvas/engine/builtin/`, `BuiltinsTest` checks the binder per argument
count. The corpus is `engine/src/test/corpus/applications.mc`.

## Algebra

Package `mathcanvas.engine.algebra`: sparse polynomials over ℚ, the algorithms on them, and
`Algebra`, the entry point the language's `expand`, `factor`, `collect`, `gcd` and `lcm` call.

### Polynomials and kernels

`Polynomial(variables, terms)` is immutable. The variables are **kernels**, sorted by `ExprOrder`
with no repeats, each occurring in some term; the terms are `Term(Monomial, coefficient)` in
descending **graded lexicographic** order (total degree, then the exponents from the first variable
on), with distinct monomials and exact non-zero `Int` or `Rational` coefficients. Two polynomials
are equal exactly when they are equal records. `Monomial` holds one non-negative `int` exponent per
variable; `Monomial.ORDER` is the term order. `coefficients()` views the terms as a sorted map.

`Polynomial.of(e)` canonicalises `e` and collects every kernel it meets:

- `Int` and `Rational` numbers are coefficients; sums and products convert operand by operand, a
  sum's terms merged in one pass; a power of a non-numeric base with an exact integer exponent
  `n ≥ 0` is the converted base to the `n`;
- `b⁻ⁿ` is the kernel `b⁻¹` to the `n`; `b^(p/q)` is the kernel `b^(1/q)` to the `p`, or `b^(-1/q)`
  to the `-p`, so `x^(3/2) + x^(1/2)` is a cubic in `x^(1/2)`; each for `n` or `|p|` up to
  `Integer.MAX_VALUE`, past which the power is a kernel itself (`Algebra.factor`'s power case and
  the rational functions of `cancel` and `apart` take the same range);
- the other **scalar** expressions are kernels: symbols, units, `π` and `e`, applications (whatever
  their arguments), decimal and complex numbers (`i` too: `i²` folds back to `-1` in the canonical
  form), a power of a number that the canonical form left unfolded (`3^100000000`, never computed),
  other powers (`x^y`, `2^x`), and the unevaluated forms (derivatives, integrals, limits, Σ, Π,
  piecewise expressions);
- nothing else is: `∞`, `undefined`, the domains, truth values, relations, lists, sets, intervals,
  lambdas, vectors and matrices, alone or in a sum, product or power, and a factor that does not
  commute, are refused with `IllegalArgumentException`.

`Polynomial.of(e, variables)` is empty when `e` has a kernel outside the list. `toExpr()` is the
canonical form of the sum of the terms, so `(x^(1/2))²` folds back to `x`.

**Kernels are independent.** Every kernel stands for a number wherever the expression is defined,
and putting numbers (real or complex) for the kernels is a ring homomorphism from the polynomials
over ℚ, so every identity the polynomial arithmetic proves (an expansion, a gcd dividing, a
factorization multiplying back) holds for the expressions at every point where they are defined.
That is why only scalar kernels are accepted: `∞ - ∞` or a relation is no number, and a vector or
matrix does not commute. The converse does not: `x` and `x^(1/2)`, or `x` and
`sin(x)`, are separate variables, so coprimality and irreducibility are over the kernels
(`cancel((x - 1)/(√x - 1))` does not cancel).

Arithmetic: `add`, `subtract`, `negate`, `multiply` (by a polynomial or a rational), `pow` (the
binomial theorem for two terms, repeated squaring otherwise), `derivative(v)`. Views: `degree(v)`,
`coefficient(v, k)` and `leadingCoefficient(v)` (polynomials in the other variables),
`totalDegree()`, `maxDegree()` (the highest degree in any one variable), `leadingCoefficient()` and
`constantTerm()`, and for one variable `degree()` and `coefficient(k)`.

**The algebra's bounds.** Each is checked before the work it bounds, and past it the operation
throws `AlgebraLimitException`, which the evaluator reports as an evaluation error:

- **terms**: a product or power of more than `Polynomial.MAX_TERMS` (100,000) terms ("a polynomial
  of more than 100,000 terms"); a power `pⁿ` checks the fewer of the multinomial count
  `C(n + t - 1, t - 1)` for `t` terms and the number of monomials of total degree at most `n·deg p`
  in all the variables;
- **total bits**: a product or power estimated to hold more than `Polynomial.MAX_TOTAL_BITS` (2²⁸
  bits, 32 MB) of coefficients ("the polynomial is too large"). A product's estimate is its terms
  (the fewer of the pairs of terms and the monomials in the box of its degrees) times the sum of
  the factors' largest coefficient bit lengths (numerator and denominator), checked at every
  multiplication, the squarings of a power included; a two-term power's is `(n + 1)·n·(1 + bits(a)
  + bits(b))`, since its central coefficient has about `n` bits. So `expand((x + 1)^99999)` and
  `(x + y)^99999` are refused at once, while `(x + 1)^3000` takes about 50 ms;
- **degree**: `Polynomial.MAX_DEGREE` (10,000), the highest degree in any one kernel that `gcd`,
  `lcm`, `factor`, `apart` and `cancel` (and the divisions and Yun's algorithm) accept ("the
  polynomial's degree exceeds 10,000");
- **coefficient power**: every coefficient power goes through `Q.pow`, which raises a rational to
  the `n` only while its numerator's and denominator's bit lengths times `n` stay within the
  canonical form's folding bound, `Canonical.MAX_FOLD_BITS` (±1 and `n ≤ 1` exempt; "the unit's
  power is too large to compute"); a binomial power checks its two coefficients the same way before
  building its terms.

### expand and collect

`Algebra.expand(e, ctx)` runs the rule set `ExpandRules.RULES` (`expand`) through the rewriter, so
its trace, paths and narration are the rule engine's. Its rules, each computed, replace the matched
node by its polynomial's sum of terms:

| Rule | Pattern |
|---|---|
| `binomial-theorem` | `(a_ + b_)^n_`, `n_` a positive integer |
| `expand-power` | `s_^n_`, `s_` a sum, `n_` a positive integer |
| `distribute` | `s_·rest__`, `s_` a sum |

The expansion is **deep**: the rewriter visits every operand, so `sin((x + 1)²)` expands to
`sin(x² + 2x + 1)`, as SymPy's `expand` does (Mathematica's `Expand` stops at function arguments). A
negative or fractional power of a sum is a kernel and stays (`(x + 1)⁻²`), and a product with one
distributes over it (`(x + 1)²/(x - 1)` is `x²/(x - 1) + 2x/(x - 1) + 1/(x - 1)`). A node whose
expansion would pass `MAX_TERMS`, or that holds a refused node (`∞·(x + 1)`), is left as it is. The
rule set terminates: an expanded node is a sum of products of kernels, which no rule matches, unless
the canonical form turns a kernel's power back into a sum (`(u^(1/2))²`), which the next rewrite
expands.

`Algebra.collect(e, v, ctx)` expands `e` and groups it by the powers of the kernel `v` (a symbol or
any kernel, `sin(x)`): `Σ cₖ·vᵏ`, each coefficient a canonical expression in the other kernels.
A negative or fractional power of `v` is a kernel of its own and stays in a coefficient. The terms
are grouped in one pass by the exponents of `v` that occur, so `collect(x^1073741823, x)` takes a
millisecond. When `v` does not occur, the expansion is returned.

### Division, gcd and square-free decomposition

`Division.divide(a, b)` divides with remainder over ℚ in one variable (`a = q·b + r`, `deg r < deg
b`), the remainder kept in one sorted map so that a step costs the divisor's terms; `pseudoDivide(a,
b, v)` gives `lc(b)^(m-n+1)·a = q·b + r` with `lc(b)` the leading coefficient in `v` (Knuth, vol. 2,
§4.6.1, Algorithms D and R); `exactQuotient(a, b)` divides in any number of variables by the
division algorithm with one divisor in the graded lexicographic order, empty when `b` does not
divide `a` (one polynomial is a Gröbner basis of its ideal, so the remainder is zero exactly then).

`Gcd.gcd(a, b)` returns the **normal form**: the gcd of the inputs' rational contents times the gcd
of their primitive parts, which is primitive over ℤ with a positive sign. **The sign** of a
polynomial (`Gcd.sign`) is that of its leading coefficient under the **symbols-first** order: the
total degree in the symbol kernels, then their exponents lexicographically in variable order, then
the total degree in the other kernels, then their exponents lexicographically. A symbol outranks
every other kernel, so `x - π`, `x + sin(x)` and `x² - sin(x)²` keep `x` positive
(`factor(x² - π²)` is `(x - π)(x + π)`, `factor(sin(x)² - x²)` is `-(x - sin(x))(x + sin(x))`),
and `π - 1` keeps `π` positive. The order is an admissible monomial order, so the sign is
multiplicative, `sign(pq) = sign(p)·sign(q)`, and a product of normal forms is a normal form; the
terms stay stored in the graded lexicographic order, and the sign scans them. The
rational content of `p` is the positive `c` for which `p/c` has coprime integer coefficients, the
gcd of the numerators over the lcm of the denominators. So `gcd(2x + 2, 4x + 4)` is `2x + 2`,
`gcd(x/2, x/3)` is `x/6`, an integer gcd is the integer gcd, and `gcd(0, p)` is `±p` with a positive
sign. `Gcd.lcm` is `(a/gcd)·b` with a positive sign, dividing first so that no intermediate passes
the degree bound (`lcm(x^10000 - 1, x² - 1)` answers); `content(p, v)`,
`rationalContent(p)`, `primitivePart(p)` and `normalise(g, a, b)` are public.

- One variable: the primitive PRS over ℤ (Knuth, Algorithm E) on a dense `BigInteger` form. The
  pseudo-remainder multiplies the dividend by `lc^(m-n+1)` once (not at all for a monic divisor)
  and divides each step's coefficient exactly, so a step costs the divisor's degree:
  `gcd(x^10000 - 1, x² - 1)` takes milliseconds.
- Several: a variable only one input has is removed by taking the other's content in it; then the
  inputs are univariate in the variable of least degree (the first such in the variable order)
  over ℤ of the others, and the gcd is the gcd of the contents, recursively, times the primitive
  part of the last subresultant (Collins 1967; Brown and Traub 1971; Cohen, *A Course in
  Computational Algebraic Number Theory*, Algorithm 3.3.1). The cost of the subresultant PRS grows
  quickly with the degree in several variables: trivariate inputs of total degree about 20 with
  30-digit coefficients take up to seconds.

`SquareFree.of(p)` is Yun's algorithm (SYMSAC 1976): `p = unit·∏ partsᵢ^(i+1)`, each part
primitive, square-free and coprime to the others (a part may be 1). In several variables the
content in the first variable is decomposed recursively and Yun's algorithm with the partial
derivative splits the primitive part.

### Factoring

`Factoring.factor(p, steps)` factors over ℚ into a `Factorization(unit, factors, complete)` in
normal form: non-constant bases, primitive with a positive sign, distinct, sorted by
the order of their expressions, the unit carrying the rational factor (0 factors as unit 0 with no
factors). `complete` is false when a bound stopped the search and a base may still split.

1. The rational content and sign (`pull-content`), then the largest monomial dividing every term
   (`pull-power`).
2. Yun's decomposition (`square-free-split`).
3. Each square-free part:
   - in several variables, its content in each variable is split off (`split-content`), and a part
     of degree 1 in some variable is irreducible;
   - in one variable, the rational roots `p/q` with `p | a₀` and `q | aₙ`, cut by the Cauchy bound
     and the tests `(q−p) | f(1)` and `(q+p) | f(−1)`, each checked exactly as `qⁿf(p/q) = 0`
     (`rational-root`). A part of degree 2 or 3 without a root is irreducible. A part of degree 4
     to `KRONECKER_MAX_DEGREE` (8) goes to **Kronecker's method** (Knuth, §4.6.2): for each degree
     `d ≤ n/2`, the `d+1` integers nearest 0 with the smallest non-zero `|f(a)|`, every choice of a
     divisor of each `f(a)` (the first positive), interpolated by divided differences and tried as
     a divisor when it has integer coefficients and degree `d` (`kronecker-split`). At most
     `KRONECKER_CAP` (200,000) interpolations in one `factor` call, its parts and the bivariate
     images counted together: about 0.3 seconds. A linear part is not searched (its root's factor
     is itself);
   - a homogeneous part in two variables is factored through `f(x, 1)`, each factor `g` becoming
     `y^deg(g)·g(x/y)` (`homogeneous-split`);
   - any other part in two variables goes through **Kronecker's substitution** `y → x^D`,
     `D = deg_x f + 1`, while the image has degree at most 64: the image is factored, and every
     sub-multiset of its factors, at most 4,096, is mapped back and tried (`bivariate-split`). A
     factor `h` has `deg_x h < D`, so its image is such a product and maps back to `h`: when none
     divides and the image's factorization is complete, the part is irreducible. **In practice the
     image's parts above degree 8 are split only by their rational roots**, so many ordinary
     products of two bivariate factors (`(x² + y + 1)(x + y² + 2)`, say) come back unsplit and
     incomplete; the homogeneous route and the content splits cover the textbook cases.
4. A part past these bounds (degree above 8 after its roots, three or more variables with no split,
   a coefficient the short factorisation cannot factor, a cap reached) stays as it is and the
   factorization is incomplete. No step is recorded for it, since nothing changes. `x^100 - 1`
   returns at once as `(x - 1)(x + 1)(x⁹⁸ + x⁹⁶ + … + 1)`, and `x^10000 - 1` in a few tens of
   milliseconds.

The divisors come from `numbertheory.Factorizer` within its short budget (**Number theory**):
trial division to 10⁴, the primality test, and Brent's rho for 20,000 iterations, with no elliptic
curves; a coefficient not factored within it leaves the divisors unknown.

The quadratic, cubic and quartic formulas are not here: `factor` is over ℚ, and the solving brief
applies the formulas.

### Rational functions and partial fractions

`RationalFunction(numerator, denominator)` is in lowest terms, the denominator primitive with a
positive sign, so the numerator carries the rational factor; the factories
`of(num, den)` and `of(e)` return that normal form (the constructor checks the denominator only).
`of(e)` brings `e` over a common denominator, negative powers going below the line (`b^(-p/q)` is
`1/(b^(1/q))^p`). `Algebra.cancel(e, ctx)` is its canonical expression: `(x² - 1)/(x + 1)` is
`x - 1`. Cancelling removes removable singularities (`x/x` is 1), as Mathematica's `Cancel` and
SymPy's `cancel` do; the algebra does not consult the assumptions, since its results are
identities.

`PartialFractions.of(f, x, factorization)` decomposes a rational function in the one variable `x`:
the polynomial part by division; the numerator over each coprime power `Dᵢ` of the factored
denominator as `r·u⁻¹·(D/Dᵢ)⁻¹ mod Dᵢ`, the inverse by the extended Euclidean algorithm; and a
repeated factor's numerator expanded by repeated division into `Σ cⱼ/pᵢʲ` with `deg cⱼ < deg pᵢ`
(Bronstein, *Symbolic Integration I*, ch. 2). An incomplete factorization gives a coarser, still
valid decomposition. `Algebra.apart(e, x, ctx)` factors the denominator through the provider and
returns the sum; an expression with another kernel is returned as it is. At the degree bound,
`cancel((x^10000 - 1)/(x - 1))` and `apart(1/(x^10000 - 1), x)` take about 0.1 seconds.

### The entry point

`Algebra` maps the language's commands onto the algorithms; the evaluator calls it (**Evaluation**,
**Commands**), so from the language `expand`, `factor`, `collect`, `cancel`, `apart`, and
`gcd`/`lcm` on anything but integers, run here, in the statement's `Context` with the default
providers, and a refused argument leaves the call unevaluated:

- `expand` and `collect`, above;
- `factor(e, ctx)` factors **by structure**:
  - a sum is brought over a common denominator and its numerator and denominator are factored
    through the `factor` provider;
  - a product is factored factor by factor, a power `bⁿ` as `b` with multiplicity `n` (a negative
    `n` on the denominator side); the factorizations are merged (units multiplied, equal bases'
    multiplicities added), and the bases common to the numerator and denominator sides are
    cancelled. When either side holds an incomplete base, each pair of their bases is split by its
    gcd through the `gcd` provider first, so a factor hidden in an unsplit base cancels too
    (`factor((x⁴ + 1)/((x⁴ + 1)(x⁹ + 2)(x⁹ + 3)))` with the denominator expanded is
    `1/(x¹⁸ + 5x⁹ + 6)`, the cofactor itself unsplit);
  - any other power factors its base; a kernel or a number is returned as it is;
  - relations, lists, sets, vectors and matrices are factored item by item.

  So an input that is already factored is returned as it is, with no step (`factor((x + 1)(x +
  2))`, `factor(2x/(x + 1)²)`), and a product of two degree-8 parts is never expanded past
  Kronecker's bound.
- `gcd(args, ctx)` and `lcm(args, ctx)` fold the arguments pairwise through the `gcd` provider;
- `cancel` and `apart`, above.

A refused argument (a node that is no scalar kernel) leaves `expand`, `collect` and `cancel`
returning the expression as it is, and `gcd`, `lcm`, `factor` and `apart` returning empty: the
language returns the call unevaluated. Past the bounds they throw `AlgebraLimitException`, the only
exception they throw: an `ArithmeticException` from within becomes one, "the exponents are too
large" for an overflow and "arithmetic failed: …" with its message otherwise. An `OutOfMemoryError`
is not caught, and the bounds keep it out of reach: every product and power is checked against the
term and total-bit bounds before it is built, the dense univariate forms have at most 10,001
coefficients, Kronecker's substitution images at most 65, and every coefficient power is within the
folding bound (**The algebra's bounds**), so `cancel((3x + 6)^-1000000000)` and `apart(1/(3x +
6)^1000000000, x)` refuse at once. A multiplicity is kept within an `int`, raised or merged; past it
the limit is "the exponents are too large", never a refusal. In `factor`, when `Factorization.pow`
refuses, the power case falls back to the canonical power of the factored base, with no step, its
factors opaque kernels: `factor((3x + 6)^1000000000)` is `3^1000000000 (x + 2)^1000000000` in
milliseconds, the unit's power unfolded.

**The factor trace** is a chain of rewrites, each changing its node, each `before` the node at its
path in the tree as it stood, starting from the input. A sum's steps are at its path: a
`common-denominator` step when its common-denominator form differs from it, then the provider's own
steps, in the rule set `factor`, each with its before and after the whole quotient: `pull-content`,
`pull-power`, `square-free-split`, `split-content`, `rational-root`, `kronecker-split`,
`homogeneous-split`, `bivariate-split`. A product's factors record their steps at their own paths;
the product, rebuilt raw around them, then takes one `cancel` step when bases cancel (`factor((x² -
1)/(x - 1))` is `rational-root` at the numerator, then `cancel`), and otherwise a normalisation to
its canonical form. A rebuilt power is a normalisation too. A step that would change nothing is not
recorded: a linear part is not searched for a root, an unsplit part records nothing. A provider that
recorded no steps but changed the expression (another provider) is one rewrite in the rule set
`provider`, named after it, from its input to its normalised result; so is a final normalisation the
default's steps did not show. These are labelled steps of an algorithm, not matcher rules: their
bindings are the values the narration prints (`r_` the root, `f_` the factor found, `c_` the
content, `m_` the monomial, `v_` a variable), and the narration texts also use `{before}`, the
display of the step's before (**Narration**). The gcd and lcm folds record one provider rewrite per
pairwise call.

The narration keys are `expand.<rule>`, `factor.<step>` and `provider.<name>` in the engine's
bundle.

### Tests

In `engine/src/test/java/mathcanvas/engine/algebra/`: `PolynomialTest`, `ExpandTest`, `GcdTest`
(division, gcds, contents, Yun), `FactorTest`, `RationalFunctionTest` (cancellation, factoring of
quotients, partial fractions, refusals and the degree bound), and `AlgebraPerformanceTest`:
`expand((x + y + z)^20)`, `collect((x + y + z + w)^30, x)`, `expand((a + b + c + d + e)^20·(x +
1))`, the gcd of two degree-50 polynomials with a common factor of degree 25, `factor(x^100 - 1)`,
Kronecker's method near its cap, and `gcd`, `factor`, `cancel` and `apart` at the degree bound, each
well under a second against the whole-run budget (**Timing budgets**). `TraceChecks.assertChain`
checks that every rewrite of a trace changes its node, that its before is the node at its path in
the tree as it stood (the previous root with the normalisations before it), and that its after sits
at its path in the root after it. `PolynomialArbitraries` generates polynomials in `x y z` or in
given kernels (small integer coefficients most often, simple rationals, 30-digit integers) and
products of generated factors, some squared. The properties are:

- `PolynomialProperties`: the ring laws, powers, the round trip through `toExpr`, the product rule,
  and conversion soundness at random points;
- `ExpandProperties`: soundness, idempotence, a result that conversion leaves unchanged, and
  termination. The soundness properties draw exact expressions with numeric exponents, `π` and `e`
  turned into symbols: as kernels they take exact values, so an expansion's cancellation is
  compared exactly. A fractional power is still inexact (`(y⁻¹)^(4/3)`), and at a zero of a factor
  the expanded terms cancel all but their rounding, so a point that disagrees at 34 digits is
  rechecked as the rule sets' properties recheck it (`testing.Recheck`, **Simplification**,
  **Tests**), ill-conditioned skips capped at 5%;
- `GcdProperties`: division with remainder, pseudo-division, exact quotients, the gcd dividing both
  inputs with coprime cofactors and its normal form (three variables with factors of degree 2, two
  with degree 3), agreement of the univariate and multivariate paths, the sign's multiplicativity
  over symbols and other kernels, and Yun's output. A gcd whose subresultant remainders pass the
  algebra's bounds (`AlgebraLimitException`) is refused for every caller, so such a try has nothing
  to check and is counted under its own label, as in `RationalFunctionProperties` and the rational
  set's lowest-terms property; in these two classes such skips are capped as the ill-conditioned
  ones are, at one in 20 of the tries checked (`BoundsTally`), so a gcd that started refusing
  ordinary inputs fails;
- `FactorProperties`: factorizations multiply back into normal-form, square-free bases; products of
  known irreducibles are recovered; the factored expression of a polynomial or quotient has the
  input's value, and its trace is a chain starting from the input;
- `RationalFunctionProperties`: cancellation keeps the value in lowest terms, and partial fractions
  sum back.

**Timing budgets.** Every timing assertion in the engine's tests is a whole-run budget of 30 s:
typical runs take milliseconds to a few seconds, and the budget fails only a regression of an order
of magnitude, which is what it guards. It bounds a whole run, never a single call: CI runners vary
by tens of milliseconds from one call to the next, a Windows runner once took 118 ms over a 50 ms
bound, and a loaded Windows runner once took over 5 s for a collect that takes 0.1 to 0.8 s
locally. So `FactorProperties`' power of a linear base sums the time of its timed calls (each after
a warm-up call) over its 300 tries, the refusals at the size bound (`PolynomialTest`,
`RationalFunctionTest`) and `FactorTest`'s huge powers are timed as whole tests, and
`MatcherPerformanceTest` times 301 matches as one run, and `CompiledPerformanceTest` a million
evaluations of a compiled 30-node expression. Each budget has the typical local time in a
comment beside it, so a reader sees the margin. The `*PerformanceTest` classes run in the
engine's `perfTest` task, not in `test`: the full gate runs them after the build, and CI does not
(`docs/reference/testing-tools.md`, **Tests**), since a loaded machine or the slowest runner fails
their budgets without a regression (`AlgebraPerformanceTest` on Windows, `SolvePerformanceTest`
under a load average of 16). "At once" in this section means milliseconds on a
developer machine.

## Linear algebra

Package `mathcanvas.engine.linalg`: the linear-algebra builtins of `docs/language.md` §14.16 on the
canonical `Matrix` and `Vector` nodes. `PLAN.md` §5's `Matrix<T>` over a field is met by `Expr`
entries over the tower's fields (ℚ, `Real`, `Complex`) and the rational functions of kernels.

**The entry point.** `Linalg.apply(call, ctx)` takes a call whose arguments are evaluated (`det`'s
method as written) and returns a `Rewriter.Result`; `Linalg.FUNCTIONS` names the builtins, which
the evaluator sends there before `Functions` (functions, but for `det`, a command holding its
method). An argument that holds a symbol where a matrix, a vector or a size is expected leaves the
call as it is, with no trace; an argument of the wrong kind is an `IllegalArgumentException` with
the language's message, which the evaluator reports at the call, as it does an
`AlgebraLimitException`. `Linalg.inverse(M, ctx)` is the inverse without steps, for `M^-k`
(**Vectors and matrices**). Every symbolic entry built is refused past `MAX_NODES` (**Limits**), and
every matrix the operations build (each row operation's, an inverse, a solution, `P`) is counted
entry by entry against `MAX_NODES` and refused, "result too large", as soon as the count passes it;
`identity` and `zeros` refuse more than 1,000,000 entries before building them. The count bounds
the values, not the work inside `Algebra.cancel`: entries whose polynomials swell there, such as
those of the inverse of a matrix of Cardano's radicals, can take the statement's time and memory
while every matrix stays under the cap, which the cancellation and the session's backstop for an
`OutOfMemoryError` bound.

**Entries.** `Entries` is the field arithmetic: two numbers on the tower's fast path, anything else
canonicalised and then `Algebra.cancel`led, so a symbolic entry is a rational function of its
kernels in lowest terms; no `double` anywhere. An entry is zero when its cancelled form is the
number 0 (which folds `(√2)² − 2`), the assumptions say so (`Tri`), or the simplifier takes it to
0 (`|b|² − b²` for a real `b`; past its cap it decides nothing); a value hidden among
independent kernels (`√2·√3 − √6`) is not seen, as in **Linear systems**. A division by an entry
that is not a number and not known to be non-zero records the condition `e ≠ 0`.

**Row reduction** (`RowReduction`) is textbook Gauss–Jordan, deterministic so that step counts
match work done by hand: for each column in order, the pivot is the first row at or below the
current one whose entry is not known to be zero (`swap`, values `i`, `j` the rows as `R_k`, printed
`Rₖ`), the row is divided by it unless it is 1 (`scale`, values `i`, `k` the pivot and `conditions`
for a symbolic one), and every other row whose entry in the column is not known to be zero has
`k` times the pivot row subtracted (`add`, one step per row); no pivoting by size. An exact zero of
the pivot row leaves the entry it would change as it is, so a decimal matrix keeps its exact zeros
and ones. It reduces in place, pivoting only in the first `columns` columns (the left block of an
augmented matrix), and returns the rows, the pivot columns and the conditions. `rref`, `rank`
(`rank.count`), `nullspace` (`nullspace.basis`: per free column `f` the vector with 1 at `f`, `−R[r][f]`
at each pivot column, 0 elsewhere), `colspace` (`colspace.pivots`, the pivot columns of the matrix
itself), `inverse` and `linsolve` run it and keep its steps; `rref.done` is the last step only when
the reduction changed nothing.

**Determinants** (`Determinants`): 1×1 and 2×2 in one step, `det.formula`. Cofactors (the default
up to 3×3, on request up to 8×8, beyond "cofactor expansion is limited to 8×8"): `det.cofactor`,
the expansion along the first row with the minors as `det` calls, then `det.minors`, the value,
each minor expanded the same way. Elimination (the default above 3×3): Bareiss's fraction-free
elimination, each row below the pivot `(p·row − e·pivot row)/p_prev` with the division exact
(`det.eliminate`, one step per row, values the pivot `p`, the previous `q`, and `conditions` for a
symbolic pivot), `det.swap` for a row swap, the value the last pivot, negated for an odd number of
swaps (`det.pivots`); a column with no pivot gives 0 (`det.zero-column`).

**Inverses** (`Inverses`): 1×1 and 2×2 by the adjugate over the determinant (`inverse.adjugate`,
values `d` and `conditions`); larger by Gauss–Jordan on `[A | I]` (`inverse.augment`, the row
operations, `inverse.read`, which records `det ≠ 0` from the determinant when a pivot was
symbolic, not the pivots' own conditions). A determinant known to be zero, or a
column of `A` without a pivot, is "the matrix is singular".

**linsolve** (`Subspaces.linsolve`): `b` a vector of `A`'s height or a matrix of right sides with as
many rows, else "linsolve needs a matrix and a vector of matching height"; `linsolve.augment`, the
row operations, then a row beyond the rank whose right side is not zero gives `{}`
(`linsolve.inconsistent`; one that may vanish gives the generic `{}` with the value `unless`, the
equations `c = 0` under which it would not hold), and
otherwise back-substitution with the parameters `t_1, t_2, …` (skipping a name that occurs in the
arguments, numbered column by column of `b`) for the free columns (`linsolve.unique` or
`linsolve.general`, the value `free`).

**Gram–Schmidt** (`GramSchmidt`): the rows of the argument (a matrix by the bracket rule; a list of
vectors of different lengths is "gramschmidt needs vectors of one length"), each reduced by its
projections on the vectors kept before it under `⟨u, v⟩ = Σ conj(uᵢ) vᵢ`, `conj` taken of the
numbers through sums and products (symbols are real); `gramschmidt.project` for each vector
changed, `gramschmidt.dependent` for one that reduces to zero and is dropped, `gramschmidt.done`
when nothing changed.

**Norms** (`Norms`): the Euclidean norm `(Σ |vᵢ|²)^(1/2)`, `|vᵢ|²` taken exactly for a number and as
`abs(vᵢ)²` otherwise (the simplifier settles it for a real symbol), `Σ |vᵢ|` and `max |vᵢ|`;
`normalize` divides by the Euclidean norm. **Shapes**: `transpose` (a vector is a column, so its
transpose is the 1×n matrix), `trace`, `size`, `identity`, `zeros`, without steps.

**The characteristic polynomial** (`CharPoly`) is Berkowitz's division-free algorithm: with `A =
[[a, R], [C, A₁]]`, the coefficients of `det(xI − A)` are the lower-triangular Toeplitz matrix of
`1, −a, −R·C, −R·A₁·C, …` times those of `A₁`, so they are exact for any entries.
`charpoly(M, x)` is one step, `eigen.charpoly` (values `x` and `m`, the matrix `xI − M`); `x` must
be a name ("charpoly needs a variable") that does not occur in `M`.

**Eigen-theory** (`Eigen`). The polynomial in a fresh `λ` (`λ₁`, … when `M` holds `λ`), cancelled,
is the step `eigen.charpoly` to the equation `p(λ) = 0`. Its roots come from `Solver.solve`, so
through the `Solve` providers, factor by factor: for a matrix of rationals `p` is split square-free
(`SquareFree`, the multiplicities) and each part factored over ℚ (`Factoring`); for a symbolic one
each square-free part is solved as it is, its multiplicity the generic one; for one with a decimal
entry `p` with each decimal replaced by the rational it denotes is solved as a rational matrix's,
its roots then put at the precision `p` (`NumericForm.numeric`) and those within `10^-(p−5)` of one
another, relative to the larger or 1, merged with their multiplicities added. Each root is
cancelled (`Entries.cancel`). The distinct roots are put in the order of the canonical
set of them, and the multiplicities must add up to the order, or the call stays. When a factor's
roots are inexact while the matrix is exact, the solver found them only numerically: with the
numeric rule off (no quadrature digits) the call stays (`eigen.unsolved`, from the equation back to
the call); with it on, the roots are found at those digits, `eigen.unsolved` is followed by
`numeric.eigen` (rule set `numeric`, value `digits`) from the call to the value, and
`Linalg.Outcome.numeric` is set, which the evaluator turns into the statement's numeric mark. With
exact roots, `eigen.roots` gives the list with the multiplicities. The eigenvectors of a root of an
irreducible rational factor `q` of degree 2 or more, when every entry of `A` is an exact rational,
are the null space of `A − xI` over `ℚ[x]/(q)`
(`Modular`: entries reduced modulo `q`, pivots inverted by the extended Euclidean algorithm, zero
tests exact), each entry with `x` replaced by the root and cancelled, so `A v = λ v` holds exactly however the
root is written (radicals, the cubic's cosines) and a numeric root is put into an exact basis. Any
other root's are the null space of `A − λI` by `RowReduction`; for a decimal matrix at `p + 10`
digits, a number of magnitude below `10^-(p−5)` of the largest entry of `A − λI` taken as zero, the
basis rounded to `p` digits. An empty null space is a zero test that missed, and the call stays.
`eigen.nullspace` records each eigenspace (value `lambda`, and `conditions`, those its symbolic
pivots assume). `diagonalize` puts the basis vectors as the columns of `P` and their eigenvalues on
the diagonal of `D` (`diagonalize.assemble`, with every eigenspace's conditions), or, with
fewer vectors than the order, goes back to the call (`diagonalize.not-diagonalizable`, values `k`
and `n`).

**The trace.** Every step is a `Rewrite` at the root in the rule set of its operation (`rref`,
`det`, `inverse`, `rank`, `linsolve`, `nullspace`, `colspace`, `gramschmidt`, `eigen`,
`diagonalize`; `numeric.eigen` in `numeric`), with named values,
from the call to the matrices it passes through and the value (`Recorder`); a step that would
change nothing is not recorded, so the steps chain (`TraceChecks.assertChain`). `Linalg.STEPS` lists
them, and `LinalgNarrator` (**Narration**) narrates them.

### Tests

In `engine/src/test/java/mathcanvas/engine/linalg/`: `RowReductionTest` (five reference matrices
whose row-operation counts were derived by hand from the algorithm as stated, the order of the
operations, a symbolic pivot's condition, decimals), `DeterminantsTest` (the methods, their
agreement, swaps, a missing pivot, the cofactor cap), `InversesTest`, `SubspacesTest` (rank, the
bases, `linsolve`'s three outcomes and its parameters), `GramSchmidtTest` (a dependent vector, the
Hermitian product, the norms), and `LinalgPerformanceTest` (a 10×10 determinant with `x` on the
diagonal and a 20×20 rational inverse, each well under a second, against a 30-second whole-run
budget). `LinalgProperties` (seeded): `A·A⁻¹ = I = A⁻¹·A` exactly for random rational matrices up to
6×6, a singular one the error (300 tries); the reduced form idempotent, the rank its pivot count,
the rank of the transpose, rank plus nullity the number of columns (300); every `linsolve`
solution substituting back exactly, parameters and all, and `{}` only when `[A | b]` has the larger
rank (300); Gram–Schmidt pairwise orthogonal, as many vectors as the rank, the span kept (300);
cofactors and elimination agreeing up to 6×6, and `det(A)·det(A⁻¹) = 1` (200). `EigenTest` (the
polynomial, multiplicities, eigenvectors of a defective and a scalar matrix, the field `ℚ[x]/(x² −
2)`, diagonalization, the numeric rule, a symbolic matrix, irrational and complex entries, the
eigenspaces' conditions, decimal multiplicities) and `EigenProperties` (seeded, 100 tries
each): `A v = λ v` exactly for every pair of a `P D P⁻¹` with rational eigenvalues up to 4×4, whose
multiplicities add up to the order; for random integer 2×2 and 3×3 matrices exactly where the
residual cancels and otherwise within `10^-30` at 40 digits; Cayley–Hamilton, the polynomial of a
random rational matrix up to 5×5 at the matrix exactly zero; `A P = P D` with `P` of full rank for
every diagonalization; each property requires most of its tries to answer (all for `P D P⁻¹`, 95%
of the random pairs, 90% of the diagonalizations), so a call that stays cannot pass for success.
`LinalgPerformanceTest` also takes the eigenvectors of a 6×6 `P D P⁻¹`. In
`…/eval/`,
`LinalgEvaluationTest` (the values, `M^-k` and the assumptions, the calls that stay, the errors, the
held method word, the narration, the numeric rule and its mark in every format). The corpus `linalg.mc` runs the language's cases through the CLI.

## Providers

Package `mathcanvas.engine.provider`: the backend interface (`PLAN.md` §5.2, extension hook 3).
Each heavyweight operation is invoked through a provider, so another implementation (Rings,
Hipparchus, an external process) can be registered for one operation without touching its callers.

- `Operation<O>` is a sealed interface over the typed operations, each a record of its inputs:
  `Gcd(Polynomial a, Polynomial b)` → `Polynomial`, `Factor(Polynomial p)` → `Factorization`,
  `Solve(List<Expr> equations, List<Symbol> vars, MathContext precision)` → `SetExpr`,
  `Integrate(Expr integrand, Symbol var, Expr lower, Expr upper, MathContext precision)` → `Expr`
  (the bounds null together for an indefinite integral), `Limit(Expr expr, Symbol var, Expr
  point, Direction direction, MathContext precision)` → `Expr` (**Limits**), `Decompose(Kind
  kind, double[][] a, double[] b)` → `Decomposition(Status status, List<double[][]> factors)`
  (**Numeric linear algebra**) and `OdeSolve(Rhs rhs, double t0, double[] y0, double[] times,
  double relativeTolerance, double absoluteTolerance)` → `OdeSolution(Status status, double[][]
  states, String message)` (**Numeric ODEs**) and `Distribute(Law law, Kind kind, double[] parameters,
  double[] points)` → `DistributionValue(Status status, double value)` (**Statistics**). Later
  operations are declared as they land. Each has
  `normalise(result)`: the gcd's normal form
  (`Gcd.normalise`, so a provider's `-3g` becomes `g`'s normal form), `Factorization.normalised()`,
  the canonical form of a set of solutions at the precision (each solution canonical, the set
  sorted and deduplicated), the factors copied with every `-0.0` made `0.0` and a non-finite one
  made the status `NOT_FINITE` (the states of an ODE likewise, and a distribution's value, with a
  probability clamped into `[0, 1]` and a density to 0 or above), the canonical form of an antiderivative, a value or a limit at the
  precision; and `outputType()`, the result's class, with which a provider's answer is checked.
- `Provider<I, O>` has a kebab-case `name()` and `apply(input, ctx, steps)`, which returns a correct
  result in any equivalent form, or empty to decline. `steps` is a `Steps` sink (`record(rule,
  before, after, bindings)`, and `record(…, values)` with values named for the narration, which a
  sink that keeps none drops) for the
  algorithm's named steps, `Steps.NONE` when tracing is off; a provider that cannot narrate ignores
  it. The limit provider's steps carry each rule's `derivation` as a value.
- `Providers` is an immutable registry: per operation, providers in the order they are tried.
  `defaults()` holds the engine's own (`DefaultProviders`, each named `default`); `register(op,
  provider[, index])` and `unregister(op, name)` return a new registry; `names(op)` lists them.
  `attempt(operation, ctx, steps)` tries them in order, normalises the first result, and returns it
  with the name of the provider that answered, or empty when every provider declines (the default
  solver declines when no method applies); `run` is `attempt` for an operation that must have an
  answer, and throws `IllegalStateException` when none answers (the default gcd and factor
  providers always answer). An entry holds the provider as a `Call`
  typed when it was registered, so running it takes no generic cast: the answer is checked with
  `outputType().cast`, and a provider registered through a raw type that answers with the wrong
  class throws `ClassCastException` there.

A provider receives the statement's `Cancellation` in the context and checks it in its long loops
(`ctx.cancellation().check()`); the default gcd and factor providers pass it to `Gcd` and
`Factoring` (**Cancellation** under **Evaluation**).

The registry is a component of the rule `Context`, so a test swaps providers with `withProviders`
and no static state is involved. Callers in `Algebra` record the provider's own steps, and a
`Rewrite` in the rule set `provider`, named after the provider that answered, when it recorded none
but changed the expression (**The entry point**). Calls inside an algorithm (the contents and gcds
within the gcd, Yun's gcds, the gcd that keeps a `RationalFunction` in lowest terms) call the
default code directly, so a provider never re-enters the registry.

**A package cycle.** `rule.Context` holds `provider.Providers`, a provider takes a `rule.Context`,
`Providers.defaults()` names `algebra.DefaultProviders`, which registers `solve.SolveProvider`,
`calculus.IntegrateProvider`, `calculus.LimitProvider`, `linalg.numeric.HipparchusDecompose`,
`ode.numeric.HipparchusOde` and `stat.numeric.HipparchusDistributions`,
and `algebra`, `solve` and `calculus`
call the registry or the algebra: the five packages depend on each other inside the module, which JPMS allows
(`linalg.numeric`, `ode.numeric` and `stat.numeric` depend on `provider` and `rule` only). The default
integration provider solves through `Solver.solve`, so through the `Solve` providers, when it
checks a definite integral's bounds; no provider calls its own operation's registry.
**Static initialisation** stays acyclic: `Context.DEFAULT` builds `Providers.defaults()`, whose
`DefaultProviders.REGISTRY` needs only `Providers.EMPTY` and the providers' records (which hold no
static state; `IntegrateProvider` reads `RuleSets.INTEGRATE` only when applied), and no algebra,
provider, solve or calculus static field reads `Context.DEFAULT`; keep it so.

`ProviderSeamTest` (in `…/provider/`) is the Phase 1 acceptance: a deliberately trivial `gcd`
provider, which answers `-7` whenever an input is a non-zero constant, is registered ahead of the
default; the engine routes to it (a counter and the trace's provider event show it) and falls
through to the default otherwise; and on random inputs both providers' results normalise to the
same canonical forms. `IntegrateSeamTest` does the same for `integrate` with a provider that
integrates polynomials only, term by term and raw. `SolveSeamTest` does the same for `solve` with a provider that answers linear
equations only (`-b/a`, raw): routed to, falling through to the default for the rest, the call left
unevaluated with the step `solve.unsolved` when every provider declines, and both providers' results
equal once normalised and simplified, as a statement's value is.

## Solving

Package `mathcanvas.engine.solve`: the language's `solve`. `Solver.solve(equations, variables,
ctx)` takes an equation, a truth value or a set of them, evaluated and canonical, and an unknown or
a list or vector of distinct unknowns, and returns a `Rewriter.Result`: a set of solutions (values
for one unknown, one list of equations `x = v` for several), or the call unevaluated. A first
argument of another kind is the `IllegalArgumentException` "solve needs an equation or a set of
equations", unknowns that are not distinct names "solve needs a variable or a list of distinct
variables"; past the algebra's bounds it throws `AlgebraLimitException`. The context's assumptions
must not hold facts about the unknowns: the evaluator drops them, since the unknowns are bound.

### The operation

A `false` among the equations gives `{}`; `true`s are dropped, and none left is every value, the
call unevaluated (`solve.all-values`). An inequality leaves the call (`solve.not-an-equation`).
Otherwise the equations go to the provider operation `Solve` (**Providers**) through `attempt`, with
the integer parameter a general solution uses (`Solve.PARAMETER`, `k`, unless the caller names
another). The
default provider, `SolveProvider`, runs the methods below; when none applies it records the step
that says why, from the state reached to the unevaluated call, and declines. When every provider
declines the call is returned, with a step `solve.unsolved` if no reason was recorded; a provider
that answers without steps is one step in the rule set `provider` named after it. The unevaluated
call is `solve(e, x)` for one equation and one unknown, `solve({…}, ⟨x, y⟩)` otherwise.

One equation in one unknown goes to `Univariate`; several equations, or several unknowns, to
`LinearSystems` when every equation is linear in the unknowns, to `PolynomialSystems` when every
equation is polynomial in them (**Polynomial systems**), and otherwise the call stays
(`solve.not-linear-system`).

### The trace

Every step is a `Rewrite` at the root in the rule set `solve`, each replacing the whole state: the
equation (or the set), the forms a method passes through, a list of solved equations `[x = r, …]`,
and last the set of solutions, the step `solve.roots`. A step that would change nothing is not
recorded, so the steps are a chain from the equations to the result and replay to it. Bindings name
what the narration prints: `a_` a coefficient divided by, `d_` a denominator or the discriminant,
`conditions_` the set of conditions `e ≠ 0` a step assumes (always present on `divide`,
`discriminant`, `factor`, `eliminate`, `back-substitute` and `free-variables`, empty when there is
none), `solutions_` the set, `methods_` the methods used when several factors were solved,
`digits_` the precision of a numeric answer, `interval_` the interval scanned, `form_` the cubic's
form, `n_` a degree or a count, `free_` the free unknowns, `i_`, `j_` and `p_` rows and a pivot. The
narration keys are `solve.<step>` in the engine's bundle. The language shows the steps when `solve`
is the whole statement or the argument of `steps`, followed by the simplifier's (`Trace.concat`).

### One equation

**Preprocessing.** `E = lhs − rhs`, canonical. The bases of the negative integer powers that contain
the unknown, through the sums and products of either side (not inside a function's argument), are
its denominators; `D` is their lcm. When `D` is not constant both sides are multiplied by it and
cancelled (`clear-denominators`, `d_ = D`), and the numerator `N` of the result is divided by its
gcd with `D` until they are coprime (`exclude`), which removes exactly the roots of `N` that are
zeros of `D`: `(x² − 1)/(x − 1) = 0` is `{-1}`, `x/(x − 1) = 1/(x − 1)` is `{}`. The canonical form
cancels like bases before `solve` sees them, so `(x − 1)²/(x − 1) = 0` is the equation `x − 1 = 0`
and gives `{1}`, as `x/x = 1` is `1 = 1`. A refused node (`∞`, a set) leaves the call
(`solve.not-polynomial`).

**Free of the unknown.** When `N` has no unknown, the assumptions decide: zero is every value
(`solve.all-values`, the call stays), non-zero is `{}`, unknown leaves the call
(`solve.depends-on-parameters`). A leading coefficient the assumptions know is zero is dropped.

**Not a polynomial.** When a kernel other than the unknown contains it (`sin(x)`, `√x`, `2^x`), the
trigonometric path (**Trigonometric equations**) where it applies, and otherwise, or where it cannot
decide, the numeric scan below.

**Linear** (degree 1): on the current equation's sides, `collect` (the unknown's terms gathered on
the left: `a·x + L₀ = R₀`), `isolate` (`a·x = R₀ − L₀`), `divide` (`x = (R₀ − L₀)/a`, with
`conditions_ = {a ≠ 0}` unless `a` is a number or known non-zero), then `roots`.

**Polynomial** (degree 2 and up, coefficients free of the unknown): `N` is factored over ℚ (the
algebra's `Factoring`, called directly as calls inside an algorithm are). A single base of
multiplicity 1 is solved as `N` itself, so the method's steps show the equation's own coefficients;
otherwise the step `factor` (`(x − 3)(x − 2) = 0`, with `conditions_` for a base free of the unknown
that is not a number) and each base solved by its degree, the step `roots` naming the methods. Each
base, by degree:

- 1: `−c₀/c₁`.
- 2, any coefficients free of the unknown: `discriminant` (the completed square `(x + b/(2a))² =
  Δ/(4a²)`, `d_ = Δ` simplified, `conditions_ = {a ≠ 0}` for a symbolic `a`), `formula` (`[x = (−b −
  √Δ)/(2a), x = (−b + √Δ)/(2a)]`), `roots`. Every complex root is returned, whatever the unknown's
  domain: `x² + 1 = 0` is `{-i, i}`.
- 3, rational coefficients: `cubic` (`form_`), on the depressed `t³ + pt + q` with `x = t − b/(3a)`
  and the discriminant `−(4p³ + 27q²)`, never zero for a square-free base:
  - `binomial` when `p = 0`: the real cube root of `−q` times `1`, `ω`, `ω̄`;
  - `trigonometric` when the discriminant is positive (three real roots, the casus irreducibilis):
    `t_k = 2√(−p/3)·cos(acos((3q/(2p))·√(−3/p))/3 − 2πk/3)`, `k = 0, 1, 2`;
  - `radical` when it is negative: `C + D` and `−(C + D)/2 ± (√3/2)(C − D)i`, `C` and `D` the real
    cube roots of `−q/2 ± √(q²/4 + p³/27)`, each written as `±A^(1/3)` of a positive `A` with its
    sign decided exactly, so that every expression is real where the root is.
- 4, rational coefficients: `quartic`, Ferrari's method on the depressed `y⁴ + py² + qy + r`: when
  `q = 0` the biquadratic `y² = −p/2 ± √((p² − 4r)/4)`, whose square roots the radical set denests
  (`x⁴ − 10x² + 1 = 0` is `{-(√2 + √3), -(√3 - √2), -√2 + √3, √2 + √3}`); otherwise a root `m` of
  the resolvent `8m³ + 8pm² + (2p² − 8r)m − q² = 0`, a rational one when it has one (a positive one
  first), else its largest real root from the cubic formula, which is positive since the resolvent
  is `−q² < 0` at 0, and the two quadratics `y² ∓ √(2m)·y + p/2 + m ± q/(2√(2m)) = 0`.
- 3 and 4 with coefficients that are closed but not rational (`π`, a decimal), and 5 and up: the
  numeric fallback. 3 and up with a symbol among the coefficients leaves the call
  (`solve.symbolic-degree`).

The roots are simplified with the standard list and the set is canonical. `x³ − 3x + 1 = 0` is
`{-2 cos(π/9), 2 cos(4π/9), 2 cos(2π/9)}`: the trigonometric rule set takes `acos(-1/2)` to `2π/3`,
and the angles' shifts and symmetries to the first quadrant.

### Linear systems

Each equation, over a common denominator free of the unknowns and of total degree at most 1 in
them, is a row of the augmented matrix: the coefficients in the order the unknowns were given, then
the right side, each a polynomial in the other kernels (`augment`). Fraction-free Gaussian
elimination (Bareiss 1968): for each column a pivot row, a non-zero number first, then an entry the
assumptions know is non-zero, then any entry not known to be zero (`swap-rows` when it is not the
current row); each row below becomes `(p·row − entry·pivot row)/p_prev`, the division exact
(`eliminate`, one step per row, `conditions_ = {p ≠ 0}` for a symbolic pivot). An entry is zero when
its polynomial is, when its canonical form folds to 0 (`(√2)² − 2`), or when the assumptions say so;
a value hidden among independent kernels (`√2·√3 − √6`) is not seen. A row left with no coefficients
and a right side not zero gives `{}` (`roots`, with `conditions_` naming the right side when it
depends on a parameter: the generic answer). Back-substitution from the last pivot, each value
cancelled, gives `[x = …, …]` (`back-substitute`, or `free-variables` when a column had no pivot:
its unknown stays free in the others' values and has no equation, `free_` naming it), then the
values simplified (`roots`): `solve(x + y = 3, [x, y])` is `{[x = -y + 3]}`. With one unknown the
result is its values.

### Polynomial systems

`PolynomialSystems` (the step `eliminate-polynomial`, `solutions_` the set): each equation's
numerator over a denominator free of the unknowns, which may hold them only as its variables, is a
polynomial in them, and the system is reduced branch by branch. In each branch, an equation that is
zero is dropped, one free of the unknowns left ends the branch when it is a constant (or known not
to be zero) and leaves the call when it depends on a parameter (`depends-on-parameters`); then, in
this order: an unknown that an equation holds with degree 1 and a constant coefficient known not to
be zero is that equation solved for it, put into the others; an equation in one unknown alone is
solved by `Univariate` (its own trace dropped), one branch per root with the root put in; an unknown
that an equation holds in one term `c·uᵏ` (`c` a constant) while the others hold only powers of
`uᵏ` has `uᵏ = −d/c` put into the others (`x² − 2λ² = 0` with `x² + λ² = 3`); an equation whose
polynomial factors over ℚ (the algebra's `Factoring`, multivariate) is one branch per factor holding
an unknown; last, an unknown that an equation holds with degree 1 and a coefficient `c` in the other
unknowns splits into the branch `c = 0` with the rest of the equation, and the branch `c ≠ 0` with
the unknown put in, the other equations' numerators taken (a denominator's zeros are no solutions)
and every factor `c` divided out of them (`1 = 2λx` gives `x = 1/(2λ)`). Each step removes an
unknown, removes one from the other equations, or replaces a polynomial by polynomials of smaller
degree or fewer unknowns, so the reduction ends; past `MAX_BRANCHES` (64) branches, when no step
applies, or when a branch leaves an unknown free, the method declines (`not-polynomial-system`). The values are
put back from the last unknown eliminated, each simplified and also expanded with a denominator
`a + b√r` rationalised (up to three times, for nested roots), the expanded form taken when it
clears the roots from the denominator or is no larger (`-(-(√73 − 9)/2 − 9)` is `√73/2 + 9/2`,
`(-i√3 − 1)²/4` is `i√3/2 − 1/2`). A coefficient the simplifier cannot fold counts as non-zero when
it is a constant numerically clear of 0 (`3·2^(2/3)`). Every candidate is substituted into the
original equations: kept when each difference simplifies to 0 (or the assumptions say it is), or,
free of symbols, is within `10^-(p−5)` of 0 at `p + 10` digits through the context's numerics;
dropped when it is a constant that is not, or `undefined` (a `c ≠ 0` branch whose candidate makes
`c` zero after all); a residual with a symbol that does not simplify to 0, or a constant with no
numeric value, leaves the call. Two quadratics with no linear unknown, no single power and no factor are not reduced
(resultants are a later item). `PolynomialSystemProperties` plants an integer solution in random systems of a linear
and a quadratic equation (always solved) or two quadratics, and checks that the planted point is
found and every solution satisfies each equation exactly.

### Trigonometric equations

`TrigEquations`: the general solution of `E = 0` (`E` the sides subtracted, canonical) when every
kernel holding the unknown `x` is a circular function of an argument `a·x + b`, `a` a non-zero
rational and `b` free of `x`, and `x` occurs nowhere else; the session's numerics are needed. The
path returns nothing when it does not apply or cannot decide, and the scan runs as before; its
steps are recorded only when it succeeds.

1. **The unit.** In radians the half turn `H` is `π`. In degrees or gradians, offsets `b` that are
   all plain-typed (numbers and symbols by sums, products and integer powers) are read in the unit,
   `H` 180 or 200; offsets that are all closed and hold `π` are read in radians
   (`docs/language.md` §14.2), `H = π`; a mixture does not apply.
2. **The base.** `g` is the gcd of the `|a|` (of the numerators over the lcm of the denominators);
   the base argument is `u = a_j·x + b_j` for an argument with `|a_j| = g`, its sign kept, else
   `g·x`. Each argument must be `n·u + β` with `n` an integer, `|n| ≤ MAX_MULTIPLE` (4), and `β/H` a
   multiple of `1/12`, so that the expansions below have the table's exact values.
3. **One function of `u`** (every kernel `f(u)` with one `f`): `E` is a polynomial in it. Otherwise
   the rule set `solve-trig`, one computed rule `to-sin-cos` through the matcher (`h_(v_)`, `h_`
   circular, `v_` one of the arguments, not `sin(u)` or `cos(u)`), writes each kernel through
   `sin(u)` and `cos(u)`: the multiple angle from `cos(nu) + i·sin(nu) = (cos u + i·sin u)ⁿ`, the
   angle addition with the exact `cos β` and `sin β`, `tan` and `cot` as quotients, `sec` and `csc`
   as reciprocals (`trig-to-sin-cos`, `u_` the base, recorded only when the rewriting changed the
   equation). Every replacement holds only `sin(u)`,
   `cos(u)` and numbers, which the rule does not take, so the set terminates.
4. **Factors.** `E` as a rational function; its numerator is factored over ℚ (`trig-factor` when
   more than one factor holds a function) and each factor with a function solved:
   - in one function: its roots by the polynomial path's methods (`Univariate.allRoots`), each a
     basic equation `f(u) = c`;
   - `a·sin u + b·cos u + d` with closed coefficients: `R·sin(u + φ) = −d`, `R = √(a² + b²)`,
     `φ = asin(b/R)` for `a > 0` and `π − asin(b/R)` for `a < 0`, in the unit
     (`trig-auxiliary-angle`, `r_` the amplitude);
   - only even powers of `cos u` (of `sin u`): `cos² = 1 − sin²`, a polynomial in the other
     (`trig-pythagorean`);
   - else, of total degree at most `MAX_HALF_ANGLE_DEGREE` (4): `sin u = 2t/(1 + t²)`, `cos u =
     (1 − t²)/(1 + t²)`, the numerator times `(1 + t²)^deg` a polynomial in `t = tan(u/2)`
     (`trig-half-angle`), its roots each `tan(u/2) = t`, and `cos u = −1` when the factor vanishes
     at `sin u = 0, cos u = −1`, where `t` has no value;
   - else the path does not apply.

   The basic equations whose value is closed and not real are dropped (the solutions are the real
   ones); the rest is the step `trig-basic`.
5. **Inverse functions** (`trig-general`, `k_` the parameter). `sec` and `csc` as `cos` and `sin` of
   `1/c` (none for `c = 0`), `cot` as `tan` of `1/c` (`H/2 + H·k` for `c = 0`). `sin`: `α = asin c`,
   families `α` and `H − α` of period `2H`; `cos`: `±acos c`, period `2H`; `tan`: `atan c`, period
   `H`; each inverse value is `h(c)·H/π`, simplified, so the table gives `π/6` or `30`. `|c| > 1` for
   `sin` and `cos` gives none; at the ends `c = ±1` (exact or decimal, `1.0` included) the two
   families are one; the ends are decided exactly, the rest numerically at
   `p + 10` digits, and a value too close to call is undecided (the path does not apply). A symbolic
   `c` is taken by `tan` only (and so `cot`), real by the facts. A family of `m·u + φ` is one of `u`
   with offset `(α − φ)/m` and period `P/m`.
6. **Merging.** Offsets are reduced into `[0, P)` by a whole number of periods where the quotient is
   exact or numerically clear of a whole number. A family whose period is a multiple of another's
   and whose offset is one of the other's values is dropped; two of one period whose offsets differ
   by half of it are one of half the period (`sin x = 0`: `0` and `π` are `πk`). Differences are
   taken expanded (`Algebra.expand`), since the canonical form does not distribute a sign over a
   sum.
7. **Exclusion** (`trig-exclude`). The denominators are the bases of the negative powers holding
   `x` in the equation as `solve` received it, taken before any rewriting (whose canonical form
   cancels `2 sin u cos u·sin(u)⁻¹`, so `sin(2x)/sin(x) = 2` would otherwise answer where it is
   undefined), written through `sin u` and `cos u` like the equation; the rational function's own;
   and the cosines or sines the rewriting divided by (`tan`, `sec`, `cot`, `csc`; in the
   one-function case none: a value of the function is finite). Each family is checked at its residues modulo the full turn
   `2H`, at most 8, at the angle in radians: a denominator that simplifies to 0 removes that residue
   (the others stay as families of period `2H`); one numerically clear of 0, or non-zero by the
   facts, keeps it; otherwise the path does not apply.
8. **Back to `x`**: `x = (θ − b*)/g + (P/|g|)·k`, the offset reduced into `[0, P/|g|)`, each value
   simplified under `k ∈ ℤ`, then `roots`. The inverse of a decimal value is evaluated
   (`Numerics.exact`), and an offset holding a decimal is a decimal at the precision.

The path runs once through, over finitely many factors, roots, families and residues, and calls
nothing of its own again: it terminates. Its narration keys are `solve.trig-*`. In degrees,
`solve(sin(x) = 1/2, x)` is `{360k + 30, 360k + 150}`, and `solve(sin(x + π/3) = 1/2, x)`, whose
argument holds `π`, `{2πk + π/2, 2πk + 11π/6}`.

### The numeric fallback

The precision is the context's, `p` digits; the work is at `p + 10`, and the results are `Real`
numbers, or `Complex` numbers with `Real` parts, rounded to `p` digits, so they show they are
inexact. The step is `numeric` (`digits_`).

**Polynomials** (`NumericRoots`), up to degree 100 (`solve.numeric-degree` beyond). Coefficients
that are not rational are evaluated at `p + 20` digits through the context's numerics
(`solve.no-numerics` without them) and taken as the rationals their decimals denote. With real
coefficients the real roots are isolated exactly by Sturm's theorem (`RealRoots`: the sequence `S,
S', −rem(S, S'), …` over ℤ, each remainder a positive multiple so the signs hold, counted at dyadic
points by bisection of Cauchy's bound `(−B, B)`), so none is missed and a root beyond 10⁶ is found;
each interval's root by Brent's method (`Brent`, Brent 1973) with the polynomial evaluated exactly,
then two Newton steps. When they do not account for the degree, Durand–Kerner (`DurandKerner`,
from `R·(0.4 + 0.9i)ᵏ` at `min(p, 30) + 10` digits, at most 500 sweeps, retried once at twice the
digits and 2,000 sweeps) gives every root; the `n − r` with the largest imaginary parts are the
non-real ones, those in the upper half-plane are polished by Newton's method at a doubling precision
to `p + 10` digits and taken with their conjugates. Complex coefficients go to Durand–Kerner and
Newton alone. Roots that did not settle, or ran together, are `solve.numeric-failed`. A part of a
complex root below `10^-p` of the other is zero (`±1.657…i`).

**Other equations** (`Scan`): the values of `E` at `0` and `±10^(−6 + j/40)`, `j = 0 … 480`, a
geometric grid of 40 points per decade over `[−10⁶, 10⁶]`, at 20 digits through the context's
numerics; a value that is not real breaks the neighbourhood. More than 20 sign changes leaves the
call (`solve.too-many-roots`: a periodic equation the trigonometric path does not take, such as
`sin(x) + sin(5x) = 1/2`), and so does
none found (`solve.no-root-found`, `interval_`), since a scan proves nothing. Each sign change is
refined by Brent's method at `p + 10` digits and kept where the value has fallen below `10^-(p−5)`
of the values at the bracket's ends, which rules out poles and jumps (`tan(x)`, `sign(x)`). The scan
misses a pair of roots closer than the grid, a root where the function only touches zero, and roots
outside the interval; the step names the interval (`interval_`). The grid grows sparser outward
and aliases the periods of `sin`, `cos`, `tan`, `cot`, `sec` and `csc`, so with one of them applied
to an expression of the unknown, more than one root found with one of them beyond 10 in magnitude
(`Scan.ALIAS_REACH`) leaves the call with its own step, `solve.aliased` (`x_` the unknown:
`tan(x) = 2x`), while a single root or a few near the origin answer (`x = cos(x)`, `sin(x) = x/2`,
`cos(x) = x²`). In degrees or gradians
(`Numerics.radians()` false) an equation with a trigonometric function of the unknown leaves the
call (`solve.angle-mode`), since the scan's values are in radians. `x = cos(x)` is
`{0.7390851332151606416553120876738734}`.

### Tests

In `engine/src/test/java/mathcanvas/engine/solve/`, through `SolveCases`, which checks every trace
for its chain and a narration line per step: `LinearSolveTest` (the steps, symbolic and zero
coefficients, equations free of the unknown, denominators, refused arguments), `PolynomialSolveTest`
(factoring, the quadratic formula, the three cubic forms, the quartic, symbolic degrees),
`SystemSolveTest` (a 3×3 system with a parameter, inconsistent and free cases),
`NumericSolveTest` (`x⁵ − x − 1` against the same roots at 100 digits, mixed exact and numeric
sets, complex coefficients, the scan and its reasons, the degree bound) and
`SolvePerformanceTest` (degree 100, a 6×6 symbolic system and the scan, each well under its
ten-second bound). In `…/eval/`, `SolveEvaluationTest` (held arguments, the renamed unknown, the
session's numerics and angle mode, the errors, the steps), `TrigEquationTest` (each step of the
trigonometric path, merging, exclusion, the angle modes, the parameter's note, freshness and frame
value, a frame value of an input `k`, two parameters in one statement, decimal values, what goes
to the scan) and `AngleFunctionsTest` (`dms`, `degrees` and the literal); in `…/rule/`,
`TraceConcatTest`. The properties:

- `SolveProperties` (5,000 tries): products of linear factors, quadratics with roots `u ± v√m` and
  complex quadratics, now and then a cubic, to degree 6; every solution makes the equation hold,
  exactly (expanded and simplified to 0) when it has square roots only, and otherwise through
  `Soundness.check` on the positive and negative terms of the polynomial at the solution; a complete
  factorization gives as many solutions as distinct roots;
- `SystemProperties` (2,000): random systems of 1 to 4 unknowns and 1 to 5 equations, half of them
  consistent by construction, dependent rows and a parameter now and then; every solution satisfies
  every equation exactly (cancelled and simplified to 0), and `{}` is an inconsistent system by an
  independent rank count over ℚ;
- `NumericSolveProperties` (500): random integer polynomials of degree 5 to 9; the solutions hold
  through `Soundness.check`, their number is the number of distinct roots, and at 34 digits they
  are within `10^-32` of the roots found at 60 digits;
- `TrigEquationProperties` (300): random equations, in radians and in degrees, of one circular
  function of a linear argument equal to a value, quadratics in one, and sums of `sin x`, `cos x`
  and `sin 2x` or `cos 2x`; every family makes the equation hold at `k = −2 … 2` (through the
  compiled evaluator, within `10⁻⁷`), and a fine scan of a window, sign changes and touching minima,
  finds no root that the families miss.

## Multivariable calculus

Package `mathcanvas.engine.calculus`: the commands of `docs/language.md` §14.20 beside the
derivatives of several variables (**Differentiation**, **Mixed and successive derivatives**, **The
chain rule in several variables**).

**The vector calculus commands.** `Multivariable.of(call, context)` takes `grad`, `jacobian`,
`hessian`, `divergence`, `curl` or `laplacian` with the function or field evaluated in the scope of
the variables and builds the textbook definition, every partial derivative by
`Calculus.differentiate` (so by the rule set `diff`), then simplifies the result. The variables are
a list or vector of distinct names, bound as `solve`'s (the parser's `LIST_BINDERS`); the evaluator
drops their facts, and puts the value of a variable's written name into the result, as for a
derivative. A function or field of the wrong shape is an `IllegalArgumentException` with the
language's message. The trace is one step in the rule set `multivariable`, `<command>.partials`, from
the call to the vector, matrix or scalar built, with the value `partials` listing every partial
derivative taken once (`∂/∂x f = …`), then the simplifier's. `MultivariableProperties` checks
`grad`, `jacobian` and `hessian` against central difference quotients at random points over smooth
functions of `x, y, z`, and on random polynomial fields of degree 3 that `div(curl F)`, `curl(grad
f)` and `laplacian f − div(grad f)` expand to 0.

**Line and surface integrals.** `VectorIntegrals.line` and `VectorIntegrals.surface` reduce
`lineintegral` and `surfaceintegral` to an integral in the parameters and hand it to
`Calculus.integrate`; nothing integrates by its own means. The field's coordinates `x, y, z`
(`COORDINATES`) are replaced by the path's or the surface's components through placeholders, by the
matcher (`IntegrateRules.replace`), so a component holding another coordinate is not replaced
twice; every derivative is the rule set `diff`'s. A scalar field is multiplied by `|r′|` or `|n|`,
the root of the sum of squares after the algebra's `factor` (`cos²u sin²u + sin⁴u` is `sin²u`), a
vector field dotted with `r′` or `n = r_u × r_v`. A surface integral integrates the inner bracket
first; when the inner integral stays, the iterated integral is the result whole (so the numeric
path's nested quadrature can take it), not the outer level integrated around it. A parameter the field mentions is renamed to a fresh name in the path or surface and the bounds
before the reduction (the evaluator, as `integrate`'s capture check does), so the field's symbol
stays free; a field in `z` on a plane path is "the field uses z on a plane path". A scalar field
integrates each parameter with constant bounds from the smaller bound to the larger (when the order of
the bounds is decided, `Definite.sign`), a vector field over the bounds as given. A parameter with
constant bounds has the facts `min ≤ t ≤ max` while the integrand is simplified (`ranged`), so `|n|
= √(r²)` is `r` on `[0, 1]`; `|sin u|` on `[0, π]` stays, the facts not deciding the sign of `sin u`.
The steps, in the
rule set `vector-integrals`, are `lineintegral.parametrise` (value `derivative`, `r′`) or
`surfaceintegral.normal` (value `normal`) from the call to the reduced integral, then
`surfaceintegral.inner` (the inner level's value), then the integral's own trace. The evaluator
evaluates the field with `x, y, z` bound, the path or surface with its parameters bound and the
inner bracket's bounds with the outer parameter bound; `Substitution` keeps those bindings for
`subs` and calls (`Substitution.field`), with no renaming of a parameter against a value that
mentions it. `VectorIntegralsTest` checks the textbook values, the flux `4π` through the unit
sphere, and over random polynomial potentials and paths that a gradient field's line integral is
the potential's difference.

**Lagrange multipliers.** `Lagrange.of(call, context, taken)` builds `∂f/∂x = Σ λᵢ ∂(gᵢ − cᵢ)/∂x` for
each variable by the rule set `diff`, adds the constraints, and hands the set to `Solver.solve` in
the variables and the multipliers (so to `PolynomialSystems` for the usual nonlinear system,
**Solving**); the points with a coordinate that is not real are dropped (below). The multipliers
are `λ` (one constraint) or `λ_1, λ_2, …`, the first names that occur nowhere in the call and that
`taken` refuses (the evaluator refuses a name with a value, a fact or a binder where the call
stands). The steps, in the rule set `lagrange`, are `system` (the call to the set of equations) and
`solve` (to the points), or `unsolved` (back to the call, which stays). `LagrangeTest` checks on
random quadratic objectives with a linear or an elliptic constraint that every point satisfies the
constraint and the gradient condition, exactly or within `10⁻²⁰` at 40 digits. A point is dropped when a coordinate holds a complex number or, free of
symbols, has a numeric value at the working precision whose imaginary part is beyond `10^-(p−5)` of
its size (`√(−√5 + 2)` in `lagrange(x + y + z, [x^2 + y^2 = 1, z = x^2], [x, y, z])`).

## Laplace transforms

`calculus.Laplace`: the rule sets `laplace` (`Laplace.LAPLACE`), on calls `laplace(f, t, s)`, and
`ilaplace` (`Laplace.ILAPLACE`), on calls `ilaplace(F, s, t)`, every rule computed on the call
matched by a predicate pattern and replacing it with transforms of smaller expressions (or none),
so the rewriter, innermost first, finishes the inner calls a rule makes. `Laplace.of(call,
context)` runs the set to its fixpoint, then the standard simplifier; when the call is left as it
came, or a transform is left in the result other than `laplace(y(t), t, s)` of an unknown function,
the result is the call, canonical, with the terminal note `laplace.none` or `ilaplace.none` (all or
nothing). The steps are the rules' rewrites, narrated by `DefaultNarrator`.

- `laplace`: `linear` (a sum term by term), `constant-multiple` (the factors free of `t` out of a
  product), `table` (a constant `c/s`, `t` and `tⁿ` as `n!/sⁿ⁺¹`, `e^v`, `sin`, `cos`, `sinh`,
  `cosh` of `v`, with `v = at + b` linear in `t`, found by differentiating once; the phase `b`
  through the sum formulas), `shift` (`e^(at + b)·g`, `a ≠ 0`, to `e^b laplace(g, t, s − a)`),
  `multiply-t` (`tⁿ·g`, `n` at most `MAX_T_POWER` (10), to `(−1)ⁿ dⁿ/dσⁿ` of `g`'s transform finished
  in a fresh `σ`, one derivative at a time, each simplified, then `σ` put back to `s`), `derivative` (`dⁿy/dtⁿ` of an applied unknown `y(t)` to `sⁿ laplace(y(t), t, s) − Σ
  s^(n−1−k) y⁽ᵏ⁾(0)`, the initial values `Laplace.initialValue(y, k)`: the applied unknown
  `y(0)` and the primed heads `Apply(Symbol("y'"), 0)`, … as the differential equations write
  them).
- `ilaplace`: `linear`, `constant-multiple`, `partial-fractions` (`Algebra.apart` in `s`, when that
  gives a sum), `table` (the expression as a rational function in `s` with
  coefficients free of it, the numerator of lower degree: `c/(lead·(s − a)ⁿ)`, the denominator
  checked against the power by expansion, to `c tⁿ⁻¹ e^(at)/(n − 1)!`; a quadratic `s² + ps + q`
  whose `q − p²/4` the assumptions know positive or negative to the cosine and sine, or the
  hyperbolic ones, shifted by `e^(−pt/2)`).

The evaluator binds the transform's variable as a command's (position 2), holds the result's
variable, which must be another name, and puts that name's value into the result afterwards, the
trace carried to the folded value by a normalisation at the root. A transform that converges for
no `s` comes out `undefined` (`laplace(e^(s t), t, s)`, the table's `1/(s − s)`). `LaplaceTest` checks the table,
the inverse, the notes and the derivative rule on a constructed tree, and over sums of random table
forms that the transform at `s = 5` is Gauss–Kronrod's `∫₀⁴⁰ f(t) e^(−5t) dt` within `1e-8` and that
`ilaplace` gives `f` back, compared at points.

## Ordinary differential equations

Package `mathcanvas.engine.ode`: the language's `dsolve` (`docs/language.md` §14.22).
`Dsolve.solve(call, problem, constants, ctx)` takes the evaluated call, a `Problem` (the equations,
the unknown functions, the variable, the initial conditions `y⁽ʲ⁾(a) = b`), a supplier of fresh
constants of integration and the context, and returns a `Rewriter.Result`: one relation `y(x) = …`,
a set of them, a list of them per solution of a system, `{}`, or the call.

**Symbols.** The work is on symbols: `y(x)` becomes the symbol `y`, `y′(x)` the symbol `y'`, and a
derivative of an unknown in the variable the primed symbol of its order; the names cannot occur
otherwise inside the call, where the declared names are functions. An unknown applied to anything
but the variable leaves the call. Each equation is `lhs − rhs`, canonical. `Tools` holds the
operations, all under the context with its trace off: the canonical form, the standard simplifier,
`expand`, `Calculus.differentiate` and `integrate` (an antiderivative that keeps an `Integral` is
none), substitution, `Solver.solve` for one unknown or several (a general solution's integer
parameter put to 0, a decimal answer to an exact equation refused, the unknowns' facts dropped), and
the split of an exact value into real and imaginary parts through its complex numbers.

**The classes**, in order, the first that gives a general solution winning. For one equation of
first order, with `f` the unique value of `y′` that `solve` gives:

| Class | Detection | Method and steps |
|---|---|---|
| linear | `∂f/∂y` free of `y` and `f = (∂f/∂y) y + f(x, 0)` | `P = −∂f/∂y`, `μ = e^∫P` with each `c log\|v\|` of the exponent written `v^c` and every `\|u\|` dropped (a constant multiple of an integrating factor is one); `y = (∫μQ + C)/μ`; `f` free of `y` is a plain integral (`integrate`). Steps `linear`, `integrating-factor`, `integrate`, `solution` |
| separable | the factors of `f`, or of `factor(f)`, each free of `x` or of `y` (`e^(a(x) + b(y))` split) | `H = ∫ dy/h`, `G = ∫ g dx`; a sum of rational multiples of logarithms `Σ cᵢ log\|uᵢ\|` is exponentiated to `Π uᵢ^cᵢ = C e^G`, which absorbs the sign and `e^C`; `y` by `solve`, each value a branch, else implicit. Steps `separate`, `integrate`, `exponentiate`, `implicit` |
| exact | the equation `M + N y′` with `N = ∂/∂y′` free of `y′`, and `∂M/∂y − ∂N/∂x` simplifying to 0 | `Φ = ∫M dx + h(y)`, `h′ = N − ∂_y∫M dx` free of `x`; `Φ = C` solved for `y`, else implicit. Steps `exact-test`, `potential` |
| Bernoulli | the expanded `f` a sum of terms `a(x) y` and `b(x) yⁿ`, `n` rational, not 0 or 1 | `v = y^(1−n)` is linear (the linear method, its states written in `y^(1−n)`), `y = v^(1/(1−n))`, and `−v^(1/(1−n))` too when `1 − n` is an even integer; the potential `μ y^(1−n) − ∫μQ dx = C`. Steps `bernoulli`, then the linear ones |

Then, for one equation of any order up to `Dsolve.MAX_ORDER` (8), **constant coefficients**
(`ConstantCoefficients`): every `∂E/∂y⁽ʲ⁾` free of `x` and of the unknowns and exact, and `E` equal
to `Σ aⱼ y⁽ʲ⁾ − g`. The characteristic polynomial in `r` (or `r_1`, … if `r` occurs) is solved; each
exact root's multiplicity is the order of the first derivative of the polynomial not zero there; a
real root `ρ` of multiplicity `m` gives `xᵏ e^(ρx)`, `k < m`, a pair `α ± βi` gives `xᵏ e^(αx)
cos(βx)` and `xᵏ e^(αx) sin(βx)` once (its conjugate matched by simplification, `β` made positive
when it reads negative); fewer than `n` basis functions leaves the call. A forcing term `g` is
expanded and each term classified as `c xᵐ e^(αx)` times `cos(βx)`, `sin(βx)` or neither (a sine or
cosine of `βx` exactly, at most one); the families by `(α, β)` take the trial `xˢ e^(αx) Σ_{k≤M}
(Aₖ xᵏ cos βx + Bₖ xᵏ sin βx)`, `M` the family's highest power and `s` the multiplicity of `α + βi`
as a root (resonance); the residual `L[y_p] − g`, expanded, gives one linear equation per product
of the factors in `x`, solved by `solve`; an unknown left free is 0. Steps `characteristic`,
`basis` (with the roots), `particular`.

**Systems** (`Systems`): as many equations as unknowns, first order, solved for the derivatives by
`solve`, each right side `Σ Aᵢₖ xₖ + bᵢ` with `A` constant and exact. The eigenvalues come from
`Linalg`'s `eigenvalues`, each with its multiplicity `m`; a basis `w` of the null space of `(A −
λI)^m` (`nullspace`) gives the solutions `e^(λt) Σ_{j<m} tʲ/j! (A − λI)ʲ w`, so a defective matrix
needs no other path; for a pair `α ± βi`, one member's solutions are written `e^(αt)(cos βt + i sin
βt)` times the polynomial vector, and their real and imaginary parts are the two real solutions. `n`
solutions are required. A forcing `b(t)` is met by variation of parameters, `X_p = Φ ∫ Φ⁻¹ b dt`,
with `Φ(t)⁻¹ = Φ(0)⁻¹ Φ(−t) Φ(0)⁻¹` (since `Φ(t) = e^(At) Φ(0)`), so only a constant matrix is
inverted (`Linalg.inverse`). Steps `system` (with `A`), `eigen` (with the eigenvalues), `variation`.

**Constants and conditions.** A class draws its constants in order from a pool the supplier fills
once, so a class that fails after drawing `C_1` leaves it to the next; each is assumed real in the
context. A separable class records its `h(y)`, and Bernoulli with `n > 0` records `y`: with one
condition `y(a) = b` on a first-order equation and `h(b)` simplifying to 0, the solution is the
constant `y = b`, the equilibrium the division lost. For a first-order solution with one condition
`y(a) = b`, the class's potential `Φ = C` (separable, exact, Bernoulli) gives `C = Φ(a, b)`
directly, and the explicit branches through `(a, b)` are kept, or the implicit solution `Φ = Φ(a,
b)` when none is shown to pass through it. Otherwise each condition is `y⁽ʲ⁾` of the solution at
`a` (the builtin functions of numbers evaluated, then simplified), and `solve` finds the constants:
each answer is a solution, none is `{}`; when `solve` leaves them for a branch, that branch is
dropped, and the call stays if no branch gave an answer. An implicit solution takes `Φ(aᵢ, bᵢ)` at
every condition, all of order 0, and has none (`{}`) when two are shown to differ; when that cannot
be decided the call stays. One regular condition on a first-order equation never gives `{}`: when
no solution is found the call stays. Last, every solution is checked against every condition
(`y⁽ʲ⁾(a) − b`, or `Φ(a, b)` less the right side), which also drops a spurious branch of `solve`.
These comparisons are decided as the check below decides a residual: 0 when it simplifies to 0 or
is within the tolerance at the fixed points (so a decimal condition's rounding is 0), non-zero when
it is beyond it at a point where it is defined, and otherwise undecided. A solution shown to miss a
condition is dropped; one undecided, or none left, leaves the call with the terminal note `unmet`.
Step `conditions`.

**The check.** Every solution is substituted back before it is returned: the symbols of `y` and its
derivatives replaced by the solution and its derivatives (an implicit `Φ = C` puts `y′ = −Φₓ/Φ_y`),
and the residual must simplify to 0, expanded if need be. Otherwise it is evaluated, through the
context's numerics at its precision, in radians (the evaluator gives `dsolve` radian numerics), at
eight fixed rational points for `x` of both signs, every other symbol (the constants, a parameter,
`y` of an implicit solution) at its own fixed rational value per point, also of both signs; it
vanishes when, at five points or more where it and each of its terms are defined, it is within
`10^-(p − 10)` of 0 relative to its largest term. The engine has no randomness, so the points are
fixed. A solution that fails is dropped; when every one fails the call stays with the terminal note
`unverified`.

**Trace.** Each step is a `Rewrite` at the root in the rule set `dsolve`, replacing the whole state:
the call, the class's equations and forms (in the applied form, `y(x)`), the general solution, and
the result (`solution` or `conditions`). A class's steps are kept only when it succeeds, so the steps
chain from the call to the result; when nothing applies there is one terminal note, `unsolved`, from
the call to itself. Bindings name what the narration prints: `p_`, `q_` the linear form's `P` and
`Q`, `mu_` the integrating factor, `g_`, `h_` the separated factors, `d_`, `m_`, `n_` the exactness
test, `n_` Bernoulli's power, `roots_` the characteristic roots, `yp_` the particular solution (by
undetermined coefficients in `particular`, by variation of parameters in `variation`), `a_`
the system's matrix, `values_` its eigenvalues. The keys are `dsolve.<step>`; the default narrator
gives one step per rewrite.

### Tests

In `engine/src/test/java/mathcanvas/engine/eval/`: `DsolveTest` (each class, branches, implicit
solutions, initial conditions with one, several and no solutions, equilibria a separation lost,
two conditions on an implicit solution, Bernoulli's two branches, a constant's value from a frame,
systems with real, complex, repeated and defective eigenvalues and with forcing, the unsolved and unverified calls, the trace
chained by `TraceChecks.assertChain`), `DsolveProperties` (every class with random rational
coefficients, each returned solution substituted back by the test itself with a residual of 0) and
the prime notation in the parser and evaluator tests. The golden corpus is `odes.mc`.

## Numeric ODEs

Package `mathcanvas.engine.ode` (`NumericOde`) and `mathcanvas.engine.ode.numeric`
(`HipparchusOde`): the language's `odesolve` (`docs/language.md` §14.22).

**The problem.** `NumericOde.solve(problem, a, b, steps, at, settings, ctx)` takes the equations in
the symbols `dsolve` uses (an unknown applied to anything but the variable is the error "odesolve
needs the unknowns applied to …"), one per unknown, each unknown's highest order the highest symbol
that occurs. `solve` finds the highest derivatives together, and must give one solution holding
every one of them and none on a right side ("odesolve needs equations that solve for the highest
derivatives"). The state is each unknown and its derivatives below the highest, in order; each
right side is compiled once to a `double` evaluator of the variable and the state (**Compilation**,
in radians whatever the session's angle mode, as `dsolve`, `diff` and `integrate` work; the
conditions, the ends and the point too); one that holds another symbol or no real value is "odesolve needs
equations in x and the unknowns only, with real values". The conditions must give every state
component once, all at one point; the point, the values and the ends are closed real values,
compiled and evaluated; `a < b` and the point in `[a, b]`. The derivative of a state component is
the next one, or the compiled right side for the highest; the provider is asked for the states at
the grid `a + k(b − a)/n`, `k = 0 … n`, the last exactly `b`, or at the one point `at`, with relative
tolerance `1e-10` and absolute `1e-12`.

**The provider.** `OdeSolve` holds the right side as a functional interface, `Rhs.derivatives(t, y,
dy)`, the initial time and state and the times; its arrays are copied in and compared by identity, a
transient input, as `Decompose`'s. `OdeSolution` is `OK` with the state at each time, in order,
`NOT_FINITE`, or `FAILED` with the reason. `HipparchusOde`, the default, runs Hipparchus's
`DormandPrince54Integrator` (Dormand and Prince's embedded 5(4) pair, adaptive steps, no lower bound
on the step and the integration's span as its upper bound, at most `MAX_EVALUATIONS` evaluations of
the right side), once forward from `t₀` to the last time after it and once backward to the first
before it, and reads each time's state from the step that contains it through the step
interpolator's dense output (`ODEStateInterpolator.getInterpolatedState`), so the grid never forces
the steps; a time equal to `t₀` is the initial state. A step handler checks the cancellation at
every step. Hipparchus's `NAN_APPEARING_DURING_INTEGRATION` and any state beyond the `double` range
are `NOT_FINITE` ("odesolve's solution is not finite on the interval: it blows up or is undefined");
its exceeded count and minimal step are `FAILED` ("odesolve did not finish: …").

**The value.** A matrix of rows `[t, y₁(t), …]` (the unknowns, not their derivatives) for a grid,
the first unknown's value or a vector of the unknowns' for one point, each entry a `Real` rounded
to the session's digits but at most `NumericOde.DIGITS` (16). The statement is marked numeric.
Hipparchus ODE (`hipparchus-ode` 4.0.3) is the automatic module `hipparchus.ode`, named by the
build's transform as Core is (`docs/reference/testing-tools.md`, **Jars without a module name**); of
it only `org.hipparchus.ode`, its `nonstiff` and `sampling` packages and its `LocalizedODEFormats`
are used.

### Tests

`NumericOdeTest` (in `…/eval/`: the grid and the point, orders above one, systems, a condition
inside the interval, the numeric mark, the errors), `NumericOdeProperties` (the acceptance suite:
`y' = y`, `y' = −2ty`, the harmonic oscillator as a system, `y' = −50(y − cos t)` and the logistic
equation against their closed forms on `[0, 5]` within `10⁻⁸` relative, at `y(0) = 1` and at random
initial values) and `NumericOdePerformanceTest` (a stiff-ish equation on a grid of 10,000 intervals
against the whole-run budget, in `perfTest`). The corpus `numeric-odes.mc` prints its values to 8
digits through `numeric(…, 8)`, so they read the same on every platform.

## Geometry solvers

Package `mathcanvas.engine.geometry`: the triangle solver and the shape, coordinate-geometry and
conic formulas of `docs/language.md` §14.19. The evaluator calls them with a context of the
session's facts, precision and numerics (`SessionNumerics`, which carries the half turn), and turns
an `IllegalArgumentException` into the evaluation error with its message. `Decide` makes their
decisions on closed real values: a difference that simplifies to 0 is 0, otherwise the sign of its
value at the precision plus 10 digits when that is clear of `10^-(p + 5)`, and otherwise none
(`Decide.Undecided`), which leaves the call.

### The triangle solver

`Triangle.solve(given, ctx)` takes three parts by name (`a b c` sides, `A B C` the opposite angles
in radians, closed real values; the evaluator has held each name, evaluated its value, and read a
plain number given for an angle in the session's unit) and returns a list of the six relations
`[a = …, b = …, c = …, A = …, B = …, C = …]`, a set of such lists for the ambiguous case, or `{}`;
the angles go out times `H/π` in degrees or gradians. With no side, a side that is not positive or an
angle not in `(0, π)` it throws; an undecidable comparison gives nothing and the call stays.

- **SSS**: the triangle inequality, strict (a degenerate triangle is none); each angle
  `acos((b² + c² − a²)/(2bc))` (`law-of-cosines`).
- **SAS**: the third side `√(a² + b² − 2ab·cos C)` (`law-of-cosines`), the angle opposite the
  shorter given side `asin(s·sin C/c)`, acute, so the principal value is it (`law-of-sines`), the
  last by `π` minus the others (`angle-sum`).
- **ASA, AAS**: the third angle by the angle sum, none when it is not positive (`angle-sum`); the
  other sides `s·sin X/sin S` (`law-of-sines`).
- **SSA**, the angle `α` opposite the side `s` and the other given side `o`: with `h = o·sin α`, for
  `α ≥ π/2` one triangle when `s > o`, else none; for `α < π/2` none when `s < h`, one (right) when
  `s = h`, two when `h < s < o` (the angle opposite `o` acute, `asin(o·sin α/s)`, and its
  supplement), one when `s ≥ o`; the third angle by the sum and the third side by the law of sines.
  The result is always a set: the step `ambiguous` (`n_` the number of triangles, `p_` the angle
  found by the law of sines) gives each triangle's angle opposite `o`, then `angle-sum` the third
  angle and `law-of-sines` the third side, every triangle at once; none is `no-triangle`.
- No triangle: `no-triangle`, to `{}`.

The steps are `Rewrite`s at the root in the rule set `triangle`, each replacing the list of the parts
known so far, so they chain from the given parts to the result; the `DefaultNarrator` narrates them
by `triangle.<step>` (`Triangle.STEPS`), `p_` the parts found, written out (`A, B and C`).
A decimal among the data makes the evaluator give every part as a decimal at the precision. Angle sums and differences are taken
expanded (`Decide.combine`), so `π − π/6 − (π − asin(2/3))` is `asin(2/3) − π/6`.

### Shapes, coordinate geometry and conics

`Shapes.apply(name, args)` on evaluated arguments. Points are vectors or lists of scalars (`[x, y]`
for all but `distance` and `midpoint`, which take any one dimension); `polygonarea` takes a matrix of
two columns or a list of points.

| Function | Value |
|---|---|
| `trianglearea(a, b, c)` | `√((a + b + c)(−a + b + c)(a − b + c)(a + b − c))/4`, simplified |
| `circumradius(a, b, c)` | `abc/(4·area)`; `undefined` for a degenerate triangle |
| `inradius(a, b, c)` | `area/((a + b + c)/2)` |
| `polygonarea(points)` | `½·Σ(xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ)`, signed |
| `distance(p, q)` | `√Σ(qᵢ − pᵢ)²` |
| `midpoint(p, q)` | `(p + q)/2` |
| `slope(p, q)` | `(q_y − p_y)/(q_x − p_x)`, cancelled; `undefined` when the run simplifies to 0 |
| `lineequation(p, q)` | `y = m·x + b`, or `x = p_x` when the run is 0 |
| `circleequation(c, r)` | `(x − h)² + (y − k)² = r²` |
| `conic(F, L, e)`, `parabola(F, L)` | `|PF|²·(α² + β²) − e²·(αx + βy + γ)² = 0` for `L` the line `αx + βy + γ = 0` (`e = 1` for `parabola`) |
| `ellipse(F₁, F₂, L)`, `hyperbola(F₁, F₂, L)` | `4L²·|PF₁|² − (L² + |PF₁|² − |PF₂|²)² = 0` |

The last row is the definition with its square roots squared away: from `d₁ ± d₂ = L` and `d₁² −
d₂² = u`, `d₁ ∓ d₂ = u/L` and `2d₁ = L + u/L` up to the sign, whose square is the same for the sum
(ellipse) and the difference (hyperbola); `u` is linear, so the relation has degree 2. A conic's `P`
is expanded (`Algebra.expand`), simplified, its terms with an inexact zero coefficient dropped
(`0.0x²` from a decimal eccentricity of 1), and divided by the gcd of its rational coefficients'
numerators over the lcm of their denominators, negated when its leading coefficient is negative. With
closed sides that fail the triangle inequality (or are not positive) the triangle formulas are
`undefined`; with symbols they are the formulas. The checks of the errors (`docs/language.md` §16)
run only on closed values.

### Tests

In `engine/src/test/java/mathcanvas/engine/eval/`: `GeometryTest` (each triangle case, the
ambiguous case, no triangle, the angle modes, the steps, the errors; the shape, coordinate and conic
values and errors) and `GeometryProperties`: random triangles of each case, in radians and in
degrees, satisfy the law of sines, the law of cosines and the angle sum (300 tries); the ambiguous
case gives as many triangles as a double-precision count (200); `distance`, `midpoint`, `slope` and
`lineequation` agree with direct computation (200); and points of each conic's relation, found by
solving it for `y` at random `x`, have the defining property: equal distances to the focus and the
directrix times `e`, the sum or the difference of the focal distances `2a` (200). The corpus
`geometry.mc` has every example of §14.19 with the triangle steps.

## Compilation

Package `mathcanvas.engine.compile`: an evaluated value compiled to a `double` function of its
arguments, which the canvas samples millions of times a second (`PLAN.md` §5.5). It is the one place
`double` arithmetic appears in the engine outside explicit numeric requests.

### The entry point

`Compiled.compile(e, arguments, settings[, cancellation])` takes a canonical value as evaluation
gives it in `settings`' angle mode (the precision is ignored) and the symbols it is a function of,
distinct and in order; `eval(double... args)` gives the value at a point, and passing one array on
every call allocates nothing. A `Compiled` is immutable and safe to evaluate on any number of
threads at once. The engine keeps no cache: a caller memoises by the value, the arguments and the
angle mode. A symbol that is not an argument is refused, so the caller substitutes a slider's value
first, or makes it an argument.

Compilation is two linear passes, checking the cancellation at every node; evaluation never checks
it, since it is the sampler's hot loop. The first pass records, per node by identity, whether it
depends on an argument and whether it is **plain**: a real number once the arguments are decimals
(an argument, a real number, or arithmetic and functions over plain parts that hold an argument; a
constant part that is not a number, such as `√2`, `π` or `sin(π/7)`, stays symbolic in evaluation
and is not plain). The second builds a tree of final node classes, one per operation (a sum of two,
of more, a product, a negation, a reciprocal, a square, an integer power, `e^u`, …), each evaluating
its operands and then itself. Each maximal constant part is folded to the nearest double: numbers
and `π` and `e` directly, `undefined` as NaN, anything else through `numeric` at 34 digits (in
radians: the value has been evaluated already), since `numeric` carries only 10 guard digits through
a cancellation (`sec(2^15·10^15 + π/3)` is off by `6e-8` at 17 digits). A constant part `numeric`
cannot fold, beyond the tower's range (`e^(10^30)`), is compiled as nodes and evaluated once in
`double`, so it is ±∞ or NaN like any overflow (`e^(10^30) + x` is +∞, as `10^400·x` is).

**The form.** The tree was measured against a stack of instructions (a postfix `int[]` interpreted
in a `switch` loop) on one 30-node expression, a million evaluations each: 39–41 ns per evaluation
for the tree and 59–65 ns for the stack, 47–57 and 62–68 ns with the call sites' profiles polluted
by other shapes, as many plotted expressions make them; one generic unary node calling an enum of
functions was 56–68 ns, and the expression written in Java 12.6 ns. `CompiledPerformanceTest`
reports the time on the machine it runs on (about 30 ns).

**Refusals.** `UnsupportedException` names what cannot be compiled by a `kind()` and a `subject()`,
from which the app words its message:

- `FUNCTION`: a user function, a command, a builtin with no `double` form (`numerator`,
  `denominator`, `nth`, `length`, `dot`, `cross`), `root` with a degree that is not a constant
  positive integer (on any other degree `numeric` leaves the call), and a constant call that stays
  on real numbers;
- `FORM`: a quantity (a unit, "quantity"), a relation, truth value, lambda, collection,
  interval, piecewise expression (it has no syntax yet, and `numeric` selects no piece),
  unevaluated form, `∞` (but as a bound of a cumulative function) or domain, and a constant call
  `numeric` leaves on a part beyond the tower's range, named as written (`sin(e^(10^30))`);
- `FREE_SYMBOL`: a symbol that is not an argument;
- `COMPLEX`: a complex number, or a constant part whose value is complex (`x + √(-2)`), named as
  written.

### Semantics

The value agrees with the engine's at the same point wherever that value is real and within the
`double` range, within the tolerance below. Everything else follows the table. NaN propagates
through every node (`x^0` of NaN included), and the sampler treats every non-finite value as a gap.

| Construct | Value in `double` |
|---|---|
| Undefined or non-real values | NaN: `1/0`, `0/0`, `log 0`, `√(-1)`, `asin 2` |
| Complex intermediates | NaN, even where the engine's value is real: `√x·√y` at `x = y = -1` is NaN (complex plotting is out of scope) |
| Overflow and underflow | a finite value beyond the range is ±∞ (`exp 800`, `171!`); a tiny one is 0, and a division by it is then NaN |
| Sum, product | IEEE arithmetic in the canonical order |
| `b⁻¹`, `b^(-1/2)` | NaN at `b = 0` |
| `uⁿ`, integer `n` | `u·u` for 2, `Math.pow` otherwise, and beyond the `int` range `\|u\|ⁿ` with the sign the parity of `n` gives (`(-1)^(2^60 + 1)` is −1), ±1 for `\|u\| = 1` whatever the size of `n`; `0⁻ⁿ` is NaN |
| `u^y`, other `y` | `Math.pow`: NaN for `u < 0` (the principal value is complex, so `x^(1/3)` at −8 is NaN; `cbrt` and `root` are real); `0^y` is 0 for `y > 0`, 1 for `y = 0`, NaN below |
| `e^u`, `exp`, `sqrt`, `cbrt` | `Math.exp`, `Math.sqrt` (NaN below 0), `Math.cbrt` |
| `root(x, n)`, constant `n ≥ 1` | the real root: `-\|x\|^(1/n)` for odd `n` and `x < 0` (`root(-8, 3)` is −2), NaN for even `n` and `x < 0` |
| `abs`, `floor`, `ceil` | `Math` |
| `sign`, `trunc`, `round` | −1, 0 or 1; toward zero; halves away from zero (`round(-2.5)` is −3) |
| `mod(a, b)` | `a − b·⌊a/b⌋` with the sign of `b`, from the exact remainder and one rounded correction; NaN at `b = 0` |
| `min`, `max` | `Math.min`, `Math.max` over every argument |
| `gcd`, `lcm` | Euclid on integer values (`lcm` with a 0 is 0); NaN for any other value |
| `factorial` | integers 0 to 170 from a table; +∞ above; NaN for negative and non-integer values (`gamma` is its own function) |
| `binomial(n, k)` | integer values: 0 for `k < 0` and `k > n ≥ 0`, `(-1)^k binomial(k − n − 1, k)` for `n < 0`, a product loop, dividing before multiplying so it stays in range while the value does, of at most about 1,100 steps before it overflows; NaN for other values |
| `ln`, `log(x)` | NaN at 0 and below |
| `log(x, b)`, `log10`, `log2` | `ln x/ln b`; NaN at `x = 0`, `b = 0`, `b = 1` and for negative arguments |
| `sin`, `cos`, `tan` | `Math` in radians |
| `cot`, `sec`, `csc` | `1/tan`, `1/cos`, `1/sin`; NaN at 0 for `cot` and `csc` |
| `asin`, `acos`, `atan` | `Math`; NaN outside [−1, 1] |
| `acot` | `atan(1/x)`, `π/2` at 0, in (−π/2, π/2] |
| `asec`, `acsc` | `acos(1/x)`, `asin(1/x)`, by `atan(√(x² − 1))` near ±1; NaN inside (−1, 1) |
| `atan2(y, x)` | NaN at the origin; `π` on the negative real axis (there is no −0) |
| `bitand`, `bitor`, `bitxor`, `bitnot`, `shiftleft`, `shiftright`, `bit`, `bitcount` | on integers within 2⁵³ as `long`s; NaN for any other value, a negative shift or index, and a result past 2⁵³ (**Bases**) |
| `pv`, `fv`, `pmt`, `nper`, `effective`, `nominal` | the closed forms in `double`; NaN for a rate of −1 or less, a type other than 0 or 1, or a non-positive `nper` ratio (**Finance**) |
| `sinh`, `cosh`, `tanh`, `sech` | `Math`, `1/cosh` |
| `coth`, `csch` | `1/tanh`, `1/sinh`; NaN at 0 |
| `asinh`, `acosh`, `atanh` | fdlibm's logarithmic forms (Java has none); `acosh` NaN below 1, `atanh` NaN outside (−1, 1) |
| `acoth`, `asech`, `acsch` | `½ log1p(2/(\|x\| − 1))`, real outside [−1, 1]; `acosh(1/x)`, real on (0, 1]; `asinh(1/x)`, NaN at 0; below `2⁻²⁸` in magnitude `±(ln 2 − ln\|x\|)`, so that `1/x` never overflows |
| `re`, `conj`, `im`, `arg` | `x`, `x`, 0, and 0 or `π` (in radians in every mode, as evaluation does) |
| `erf`, `erfc`, `gamma`, `beta` | Hipparchus's `Erf` and `Gamma` through `stat.numeric.DistributionMath`, the `Distribute` provider's own functions; `gamma` NaN at 0 and the negative integers; `beta(a, b)` as `e^(log B)` for positive arguments, the gamma ratio otherwise, NaN at a pole of `a` or `b` and 0 where only `a + b` is one |
| the densities `normalpdf`, `tpdf`, `chi2pdf`, `fpdf`, `exppdf`, `unifpdf` | `DistributionMath.density`: 0 off the support and at ±∞; the χ² and F densities at 0 finite only from 2 degrees of freedom, NaN below; NaN for a parameter outside its domain or NaN (evaluation gives the normal and t densities in closed form, which compile as arithmetic) |
| the cumulative functions `normalcdf`, `tcdf`, `chi2cdf`, `fcdf`, `expcdf`, `unifcdf` | `DistributionMath.probability` of the interval, its tails computed directly, negative for bounds in the wrong order; a bound may be `±∞` (`normalcdf(-∞, x)`), the one place an infinity compiles |

The elementary functions are `java.lang.Math`'s, within one ulp, not `StrictMath`'s, for speed: a
value may differ in its last bit between platforms.

**Angle mode.** In degrees and gradians a circular function of plain arguments converts them from
the mode's unit, and an inverse one, `atan2` included, answers in it, as `docs/language.md` §14.2
specifies and evaluation does at a decimal point; anything else is in radians. So `sin(sin x)`
converts both, while `sin(x + π/7)` and `sin(√2·x)` are read in radians, as `sin(30.0√2)` is. A
converted angle is reduced exactly, modulo the turn, to within an eighth of a turn of a quarter turn
before it is multiplied by `π/180` (or `π/200`), so a large angle loses nothing (`sin(10^10°)`) and
a multiple of the quarter turn has the exact value: `sin 180` is 0 and `tan 90` is NaN, as the exact
evaluation says (`tan(90)` is `undefined`), where the engine at the decimal `90.0` gives `6.1e26`.

### Tests

In `engine/src/test/java/mathcanvas/engine/compile/`: `CompiledTest` turns the table into cases at
each domain edge, the inverse hyperbolic and reciprocal functions checked against the engine at 40
digits near their points of cancellation, the angle modes, the refusals of each kind, the argument
order, a wrong count of values and a cancelled compilation; `CompiledPerformanceTest` (**Timing
budgets**). `CompiledProperties` (300 tries each) draws expressions from `ExprArbitraries`'
elementary and trigonometric generators, renamed to `x` (and `y`), evaluates them in the mode,
compiles them and checks eight points each, in radians in one and two variables and in degrees or
gradians. Points are full-mantissa doubles in [−10, 10] most often, then small integers (in degrees,
multiples of 15 and 45), halves, 0 and ±1, values near 1 and near 0, and up to ±1,000. In degrees a
value holding a circular function of numbers is skipped, since evaluating it again in the mode would
convert a plain constant argument twice. A fourth property (300 tries) wraps a moderate expression,
a shift plus a multiple of a bounded function of `x` (`sin`, `atan`, `tanh`, `x/(1 + x²)`, or
`x/100`), so that the points stay inside the functions' domains and the `double` range, in a
statistics function, `erf`, `erfc`, `gamma`, `beta`, a density or a cumulative function with
constant parameters in their domain (1 to 50 degrees of freedom) and `±∞` among the bounds, and
checks it the same way; the oracle needs no new case for them: they are real functions of a real
argument, the engine has no complex special function (a non-real argument leaves the call, whose
compiled NaN agrees), and a real value reached through a non-real child (`erf(root(−2, 2)²)`) is
counted as `NON_REAL_PATH`, as for the elementary functions; a node's children are compiled on their
own only for a sum's terms, since the bound `−∞` does not compile alone. Since the engine's
`numeric` and the compiled node both call `DistributionMath`, the property also checks each compiled
call against `testing.NumericEvaluator`, which computes these functions in `double` through
Hipparchus's `Erf`, `Gamma`, `Beta` and densities directly (the t and F tails at the smaller of the
incomplete beta function's argument and its complement, each computed directly; an interval in the
upper half as the difference of upper tails; the χ² density in closed form), at the arguments' own
compiled values, so that an ill-conditioned argument is no difference (`1e-9` relative or `1e-12`
absolute where both are finite; an argument with no real value is not compared). `CompiledTest`
checks each statistics function at a point, its support, a pole and an upper tail.

The oracle, `CompileOracle`, evaluates the value in the mode with each argument bound to the decimal
its double denotes and takes `numeric` at 17 digits: a real value within the `double` range must
agree within `1e-9` relative or `1e-12` absolute, and an undefined, non-real or symbolic one needs
NaN. Decimals rather than exact rationals: the mode converts only plain numbers, so nested functions
convert only at decimal points (`sin(sin 30)` is `sin(1/2)`, `sin(sin 30.0)` converts both); and
`numeric` turns the degree of `root(x, 3)` into `3.0`, on which the call stays, while evaluation
takes the root of a decimal `x` at once; a `root` the evaluation leaves (its argument holds a
constant that is not a number, `root(sin(4)·x, 2)`) is written as the real root it denotes,
`sign(u)·|u|^(1/n)` or `u^(1/n)`, before `numeric`, for the same reason. Where the whole value
disagrees, every node is checked locally: its compiled value against the engine's operation on the
decimals of its children's compiled values at 34 digits, with the same tolerance (a sum against the
magnitudes of its terms, which it rounds in turn) and the same NaN rule, a NaN operand needing a NaN
node; a circular function of plain arguments in degrees or gradians takes its argument exactly, as
the exact evaluator does, so `tan(90)` needs NaN. A local disagreement fails and names the node.
When every node agrees, the evaluator did each operation right and the point is ill-conditioned: it
is skipped and counted with `testing.Recheck`, capped at one in 20 of the points compared. A point
at an exact quarter turn is skipped without a cap. A point whose whole the engine finds out of range
must compile to ±∞, or to NaN from an operand that is not finite, or else every node must agree
locally, and the point is ill-conditioned (`csc(285π − e^(−855))` is `1e371`, where π rounds to give
`−3.4e14`). A point is out of range when the engine's value of the whole, or of a node whose
children's compiled values are finite, is beyond the `double` range, or when a child's compiled
value is infinite; a node that is infinite where the engine's value is within the range fails,
except a sum or product whose finite terms or factors pass the range on the way (their magnitudes
add, or those above 1 multiply, past it): its compiled ±∞ or NaN is an overflow in between, not an
error (`27·12^(1/3)·cosh x·(2cosh²x − 2)·x²/sinh x` at 270 in degrees forms `cosh(270)³` before the
division), and the point is skipped as an ill-conditioned one, under that cap. A node whose compiled value is NaN while the
engine's value of it is real, through a part the engine finds not real at the point (a real
function outside its real domain: `root(−2·sign(…), 2)` is `i√2`, its fourth power the real 4, and
`4^(−1/3)` is 0.63), is a legitimate difference of a plotting evaluator over the reals, which
rightly has no value there: the point is counted under its own label, `NON_REAL_PATH`, and not
asserted. Such
points are counted under their own label and capped at one in 10 of the points compared.

**Bounds.** Before anything large is evaluated, the oracle judges the point on the compiled values
of the value's parts there, from the inside out, constant parts included, as
`testing.NumericEvaluator` bounds its own: a point where `exp`, a power of `e` or a hyperbolic
function (formed through `exp`) takes an argument of modulus above 10⁴, another power an exponent of
magnitude above 10⁴ (or above 1 on a base beyond the `double` range), a negative base to a
non-integer power a complex value of modulus above 10⁴, or a logarithm a value whose decimal
exponent is beyond ±10⁴ (in a double, an infinite one) is not evaluated, since the engine would take
seconds to minutes at 34 digits (`10^(-1.2e8)` took a minute, `cosh(2·10⁷)` 37 s, `(x^x)^(3x/2)` at
853 3 s, `cot(x^(5/2))` at −779 15 s). Each compiled value that bounds a point is first checked
against the engine's value of its part, which its own bounds keep cheap: the part must be out of
range or agree, or else every node of the part must agree locally (the value is then large by the
part's conditioning, as `csc(2πx)` is at −8.5 where π rounds, or by an overflow below it), so a
wrongly large compiled value fails instead of bounding the point away. Such points are counted under
their own label and capped at one in 10 of the points compared, so what is compared does not depend
on the machine's speed. An integer exponent beyond the `int` range is above the bound, so the powers
that keep their parity are covered by `CompiledTest`'s cases, not here.

## Numeric integration

Two quadratures of our own (`PLAN.md` §11, 2026-10-30: no Hipparchus in Phase 4): Gauss–Kronrod in
`double` over compiled functions for the canvas, and the double-exponential rule at a requested
precision for the numeric value of a definite integral the exact path leaves.

### Gauss–Kronrod in double

`compile.Quadrature.integrate(f, a, b[, tolerance], cancellation)` integrates a `Compiled` function
of one argument and gives an `Estimate(value, error, converged)`. It is QUADPACK's QAG with the
15-point rule (Piessens et al., 1983): each interval's value is the Kronrod sum, its error
QUADPACK's estimate `resasc·min(1, (200|K − G|/resasc)^1.5)`, at least 50 ulps of the interval's
integral of `|f|`; the interval of the largest error (the leftmost on a tie, so the result is
deterministic) is bisected until the sum of the errors is below the tolerance (default `1e-10`)
times the larger of `|value|` and a thousandth of the integral of `|f|`, which lets an integral that
cancels to 0 converge, or 200 intervals are reached, where the estimate comes back with `converged`
false. The cancellation is checked at every bisection. A sample that is not finite, or a bound that
is not, makes the value NaN; `a > b` gives the negated integral and `a = b` 0.
`riemann(f, a, b, n, method)` is the Riemann sum over `n` equal pieces, `LEFT`, `RIGHT`,
`MIDPOINT` or `TRAPEZOID`, and `riemannHeights` the heights it samples (`n + 1` values at the ends of
the pieces for the trapezoids), piece `i` spanning `a + i(b − a)/n` to `a + (i + 1)(b − a)/n`; a
sample that is not finite makes the sum NaN. Finite samples whose integral is beyond the `double`
range give the infinite value, an error that is not finite and `converged` false.

### The double-exponential rule

`number.Quadrature.integrate(f, a, b, digits, cancellation)` integrates an `Integrand`, a function
from a `Real` and the context to evaluate it at to a `Real` (null where it is undefined or not real;
an `ArithmeticException` is a value beyond the tower's range), between `Bound`s that are finite
reals or `±∞`, and gives a `Value(value, error, level)` or a `None(reason)`. It is Takahasi and
Mori's rule (1974): tanh-sinh, `x = c ± h·δ(t)` with the distance from the end `δ = 2q/(1 + q)`,
`q = e^(−2u)`, `u = (π/2) sinh t`, on a finite interval; exp-sinh, `x = a ± e^u`, toward one
infinite end; sinh-sinh, `x = sinh u`, on the whole line; `BigDecimal` throughout at `digits + 10`
working digits, the abscissas and weights from the exponential kernel. A node near a finite end is
the end plus or minus its distance, exactly, and is sampled at the digits that carries, so an
endpoint singularity is seen at its true distance (`1/√(1 − x²)` on `[−1, 1]` is π to 34 digits).

Level `k` adds the odd multiples of `2^−k` in `t` to the nodes before it (level 0 every integer),
walking out from `t = 0` on each side: toward a finite end until two terms in a row are below
`10^−wp` of the sum of the absolute terms, or `u` passes `3wp + 20`, the reach; toward an infinite
end to the reach, or to a sample beyond the tower's range once the terms have vanished, so a bump
beyond a stretch of vanished terms is still reached (`e^(−(x−30)²)` beside `e^(−x²)`). The **error
estimate** is the difference of a level from the one before. A value is accepted from level 6 on
(level 7 toward an infinite end) when that difference has been below `10^−(digits+2)` of the larger
of the value and `10^−6` of the integral of `|f|` on three levels in a row, and when the caller's
`Check` agrees (the cross-check, below); it is rounded to `digits`. A refused value makes the levels
go on; one refused still at the cap is `DISAGREE`. The level cap, `maxLevel(digits)`, is 5 plus the
bit length of the working digits (10 at 10 digits, 11 at 34, 12 at 100, 15 at 1,000): measured,
smooth integrands are accepted at levels 6 and 7 at 10 digits, 7 to 9 at 34 and 8 to 11 at 100
(`e^(−x²)` on `[0, 1]` at 6, 7 and 9, on the line at 7, 9 and 11; `1/√x` on `(0, 1]` at 6, 7 and
8). A run to the cap is about 16,000 samples at 34 digits: `e^(x²)/(x − 0.3)` on `[0, 1]` took 5 s
in a warm JVM at a load average of 12 to 20, `1/(x − 0.3)` 0.3 s, `e^(−x²)` on `[0, 1]` 0.13 s.

There is **no value** (`None`) when:

- a sample inside the interval is undefined or not real (`UNDEFINED`): `1/x` on `[−1, 1]`;
- a side reaches the reach with its terms not vanished, and the integrand's size times its span from
  the end (its distance from the end, or from 0 toward an infinite end), carried from the side's
  last two nodes to the span at the reach as a power of the span, is above `10^−(digits+2)` of the
  integral of `|f|` (`TAIL`): a divergent integral (`1/x` on `(0, 1]`, `1/x` on `[1, ∞)`, `1/(1 −
  x)` on `[0, 1)`), and also a convergent one whose singularity is too strong (`x^(−0.9)` on `(0,
  1]`) or whose tail decays too slowly (`x^(−1.5)` toward ∞) for the precision; without this test
  the truncated sum of `1/x` would settle near `wp·ln 10`;
- the levels do not agree by the cap (`UNSETTLED`): oscillating integrands on an infinite range
  (`sin(x²)/x²` on `[0, ∞)`, `cos(x)/(1 + x²)` on `[0, ∞)`), a pole inside the interval that no node
  hits (`1/(x − 0.3)` on `[0, 1]`), and a kink or jump inside it (`|x − 0.3|`, `sign(x − 0.3)`), whose
  convergence is too slow for the digits; splitting the interval at the point helps;
- the levels agree only on values the cross-check refuses (`DISAGREE`).

**The limit.** The levels see the integrand only at their nodes, so a feature narrower than the
finest spacing reached can be missed, and the levels then agree on the value without it: before the
minimum level of 6, three agreements and the cross-check, `e^(−10⁶(x − 0.3)²) + 1` on `[0, 1]` at
10 digits gave `1.0` against `1.001772453851`, and `e^(−(x−30)²) + e^(−x²)` on the line gave
`1.772453851` against `3.544907702` (review-9). The rule alone cannot rule this out; the guard is
the cross-check, a rule that adapts to the local error, under which those integrals, `e^(−10⁵(x −
0.3)²) + 1` on `[0, 1]` and `e^(−10⁴(x − 5)²)` on `[0, ∞)` give the true value or no value at 10
digits (they give none today; the wider `e^(−10⁴(x − 0.3)²) + 1` gets its true value, which the
levels see from the minimum on). A feature that both rules miss, narrower than Gauss–Kronrod's
bisection finds within its cap, is the remaining limit. The cancellation is checked at every level,
every node and every sample, and the sampler checks it at every node of its numeric pass.

### The numeric path of an integral

`Settings.quadratureDigits` (0 by default) are the digits a definite integral the exact path leaves
is evaluated to; the app sets them to the format's digits under the decimal and scientific formats
(`docs/reference/app.md`). The evaluator's state carries them, and `numeric(e, d)` sets them to `d`
for its argument. Where they are set, the value of an `integrate` call or an `∫` node goes through
`eval.NumericIntegral`: a definite `Integral` whose bounds are `±∞` or constants with a real numeric
value, and whose integrand holds no symbol but its variable, is integrated by the double-exponential
rule, each sample the `numeric` form of the integrand with the variable replaced by the node, at the
sample's digits; a sample that is a complex number or `undefined` is undefined, and one the tower
leaves unevaluated (`e^(−10^13)` stays a power) is beyond its range. The value is a `Real` of the
digits; anything else is returned as it is. In degrees and gradians an integrand with a circular or
inverse circular function of the variable is returned with a `numeric.angle-mode` step, since the
numeric pass is in radians (the solver's rule, **The numeric fallback**). The digits are capped at
`NumericIntegral.MAX_DIGITS` (100): beyond, the integral is returned with a `numeric.digits` step
naming the cap (a 300-digit run took 522 s under load, and the decimal format allows 10,000).

**The cross-check.** Each value the levels agree on is checked against Gauss–Kronrod in `double`
over the integrand compiled in radians (**Gauss–Kronrod in double**), which bisects where the local
error is and so sees a narrow feature: on a finite interval directly, toward an infinite end after
the change of variable `x = a ± t/(1 − t)` on `[0, 1]` (`x = t/(1 − t²)` on `[−1, 1]` for the
line), times `dx/dt`, whose ends the 15-point nodes never reach. A value stands when the double rule
converged and agrees within `1e-8` of the value or `1e-10` of the integral of `|f|`; an integrand
that does not compile, or a double rule that does not converge, gives no check, and the levels'
value stands alone.

**The steps** are computed `Rewrite`s at the root in the rule set `numeric`: `numeric.quadrature`
from the integral to its value (`digits_`), "No closed form found; the value is numeric, N digits";
`numeric.none`, `numeric.angle-mode` and `numeric.digits` (`digits_`, the cap) on the integral,
which they leave as it is. They are
recorded when the integral is the statement, or the argument of `steps` or of a `numeric` that is,
after the exact path's own steps and before the simplifier's (`Trace.concat`); at the top of
`numeric` a normalisation at the root then carries the trace to the numeric form of the value. An
integral nested in a larger expression is evaluated the same way and brings no step, as every
nested command. Whatever the trace, a statement in which a quadrature gave a value has `numeric` set
on its `Evaluator.Result` and its `Outcome.Value` (from `NumericIntegral.Outcome.numeric`, set
only when the quadrature gave the value, never for a closed form the exact path gave, and kept by a
value derived from it, such as its `round`; a numeric decomposition sets it too, **Numeric linear
algebra**), which the worksheet marks (`docs/reference/app.md`),
so a number no closed form gave is never shown unmarked.

### The numeric path of an iterated integral

An integral whose integrand is a definite integral, down to an innermost integrand free of
integrals, goes through `NumericIntegral.iterated` instead of the double-exponential rule (whose
nested runs were measured: two levels at 3 digits took 139 s and ran past two million samples, at
the rule's minimum of level 6 on every inner integral). Every bound must hold only the variables of
the levels outside it, and the innermost integrand only the levels' variables. The innermost
integrand and each level's bounds are compiled over all the variables (`Compiled`, in radians; in
degrees and gradians an angle of a variable leaves the integral with `numeric.angle-mode`), and
the integral is nested adaptive Gauss–Kronrod in `double`
(`compile.Quadrature.integrate(DoubleUnaryOperator, …)`, each inner level a function of the outer
variables), its innermost level at the tolerance `1e-12` and each level around it ten times looser
(its samples carry the inner level's error), and again with every tolerance 100 times looser; NaN
when a level does not converge or a bound or a sample is not finite. Both runs share a count of
the innermost integrand's samples, `SAMPLE_BUDGET` (20,000,000, measured at about 5 s of CPU on the
development machine for `|x − y|^(1/3)|y − z|^(1/3)|x − z|^(1/3)` over the unit cube, which ran 3
min 24 s without it); past it the integral is left. The value is given to the leading digits the
two runs agree on (`agreeing`: of their difference relative to the finer, all of them when they are
equal or both within `1e-15` of 0), at most `MAX_CUBATURE_DIGITS` (12) and at most the digits asked
for; fewer than `MIN_CUBATURE_DIGITS` (8) leaves the integral. The step is `numeric.cubature`
(`digits_`), or `numeric.cubature-capped` when more digits were asked than are given, and the
statement is marked numeric. Anything else (an infinite bound, a bound with a symbol of no outer
level, an innermost integrand with another symbol, a piece that does not compile, runs that do not
agree, the budget spent) leaves the integral with `numeric.none`, recorded when traced.
`CubatureTest` pins the values, the refusals and the budget, and is the Phase 7 acceptance
criterion: over random polynomial, exponential and trigonometric integrands on rectangles and
triangles, an iterated integral the exact path finishes (the value not numeric; a stayed one counts
as such) agrees, at 20 digits, with a nested Gauss–Kronrod run (inner `1e-12`, outer `1e-10`) within
`1e-8`.

### Tests

In `engine/src/test/java/mathcanvas/engine/`: `compile/QuadratureTest` (known integrals to `1e-10`
with the error estimate covering the difference, an integral that cancels, reversed, equal and
infinite bounds, NaN samples, an integral beyond the `double` range never converged, the interval
cap, cancellation, determinism, the Riemann sums of a line, their heights and their orders of
convergence); `number/QuadratureTest` (`x³`, `eˣ` at 34 and
80 digits, `sin` over a half turn, `1/√x` on `(0, 1]`, `1/√(1 − x²)` on `[−1, 1]`, `e^(−x²)` on the
line and on `[0, 1]`, `1/(1 + x²)` on the line and both half-lines, `e^(−x)` on `[0, ∞)`, against
values written out from independent computations; the refusals, a cross-check that refuses every
value giving `DISAGREE` and seeing the value oriented as given, a spike of width 0.007 the true
value at 10 digits, the level cap, determinism, and a counting cancellation); `compile/QuadratureProperties` (100 tries: the shared generator's elementary
expressions in `x` on random intervals in `[−3, 3]`, Gauss–Kronrod against the double-exponential
rule at 20 digits through `numeric(integrate(…))`, within `1e-8` of the value or `1e-10` of the
integral of `|f|`; an integrand that does not compile or that either rule gives no value for is
skipped, at most one skip per comparison); `eval/NumericIntegralTest` (the path under `numeric` and
under the quadrature digits, the exact formats' node, the steps and their narration, a nested
integral, a divergent one, a parameter, a symbolic bound, degrees; three narrow-feature integrals
at 10 digits, `e^(−10⁵(x − 0.3)²) + 1` on `[0, 1]`, the two gaussians on the line and `e^(−10⁴(x −
5)²)` on `[0, ∞)`, each the true value or the integral itself (review-9's `10⁶` spike pins the same
failure but takes a minute to refuse, its samples at `exp(−490,000)`), and the cap at 101 digits
with 100 still running the rule). The corpus
`numeric-integration.mc` runs the language's cases through the CLI.

## Numeric linear algebra

The numeric decompositions of `docs/language.md` §14.17 (`lu`, `qr`, `cholesky`, `svd`, `lstsq`,
`expm`): builtin functions, numeric by nature, computed in `double` through the provider operation
`Decompose` by Hipparchus (`org.hipparchus:hipparchus-core`), the engine's only numeric library.
Nothing symbolic goes through it, and its eigen-decomposition is not used (numeric eigenvalues are
the exact path's `NumericRoots`).

### The provider

`provider.Decompose(Kind kind, double[][] a, double[] b)` is the operation: `Kind` is `LU`, `QR`,
`CHOLESKY`, `SVD`, `LEAST_SQUARES` or `EXP`, `a` the matrix by rows (rectangular, non-empty), `b`
the right side of `LEAST_SQUARES` only, one entry per row; the arrays are copied in, so the record
compares them by identity, a transient input and never a key. The result,
`provider.Decomposition(Status status, List<double[][]> factors)`, holds the factors in the
builtin's order (`[P, L, U]`, `[Q, R]`, `[L]`, `[U, Σ, V]`, `[x]` as an n×1 matrix, `[e^A]`) when
the status is `OK`, and none otherwise: `NOT_SQUARE`, `SINGULAR`, `NOT_SPD`, `RANK_DEFICIENT` (also
fewer rows than columns for least squares), `NOT_FINITE`. A provider's answer is normalised by
copying the factors, making every `-0.0` `0.0`, and turning a factor with a NaN or an infinity into
`NOT_FINITE`, so no caller sees a non-finite entry.

`linalg.numeric.HipparchusDecompose` is the default and only provider (`default`), registered by
`DefaultProviders`. It imports `org.hipparchus.linear` and, to catch the one failure Hipparchus
reports by throwing (Cholesky's), `org.hipparchus.exception.MathIllegalArgumentException`; it always
answers, so a singular or indefinite input has a status, never a matrix.

**Scaling.** Every decomposition but the exponential runs on the matrix times `2^-e`, `e` the
exponent of its largest entry in magnitude (`Math.getExponent`, or for a subnormal, to which it
answers −1023, its true exponent), which brings that entry into `[1, 2)`; Cholesky's on the matrix times a power of four, into `[1, 4)`, so that the root of the scale is
a power of two too. Scaling by a power of two is exact in `double` (`Math.scalb`), and it keeps
Householder's unscaled sums of squares clear of underflow and overflow (unscaled, `qr` of
`10⁻¹⁶⁰·I` gave a `Q` of `−1.000011·I` and `lstsq` was off by `1.7·10⁻⁵` relative) and makes
Cholesky's absolute positivity test relative to the matrix (unscaled, a well-conditioned matrix of
entries near `10⁻²⁰⁰` was "not positive-definite"). The factors that carry the scale are unscaled
exactly: `U` of LU, `R` of QR and `Σ` of the SVD times `2^e`, Cholesky's `L` times the root of its
scale, and least squares' `x` times `2^(f − e)`, `b` scaled on its own by `2^-f`; `P`, LU's unit `L`,
`Q`, `U` and `V` do not depend on the scale. A zero matrix is not scaled.

**Tolerances.** LU is `LUDecomposition` with partial pivoting and the singularity threshold
`n·ε·max|aᵢⱼ|` of the scaled matrix, `ε = 2⁻⁵²` (`EPSILON`), the standard numerical rank
tolerance (Hipparchus's own default, `10⁻¹¹`, is absolute); `getL()` null means singular, and a zero
matrix is singular. QR is `QRDecomposition`, `Q` m×m and `R` m×n. Cholesky keeps Hipparchus's
thresholds (symmetry relative to the entries, `10⁻¹⁵`; each pivot positive beyond `10⁻¹⁰` of the
scaled matrix). The SVD is Hipparchus's compact one, `U` m×p, `Σ` p×p, `V` n×p for `p = min(m,
n)`, the singular values descending. Least squares is the QR solver with the threshold
`max(m, n)·ε·max|aᵢⱼ|` on `R`'s diagonal (`isNonSingular()` false is `RANK_DEFICIENT`). The
exponential is `MatrixUtils.matrixExponential` (Higham 2005, Padé with scaling and squaring) on the
matrix as given, since `e^A` does not scale. The cancellation is checked before and after the call;
a 200×200 SVD takes about 0.15 s.

**The module.** Hipparchus ships no module descriptor and no `Automatic-Module-Name`; the build
names its automatic module `hipparchus.core` (`docs/reference/testing-tools.md`, **Jars without a
module name**), `engine` requires it (not transitively, as no exported signature names a Hipparchus
type) and exports `mathcanvas.engine.linalg.numeric`, and jlink's merged module carries it into the
image.

### The builtins

`eval.NumericDecompositions` evaluates a call whose arguments are evaluated. Each entry of the
matrix goes through the session's numerics (`SessionNumerics.value`) at `INPUT_DIGITS = 17` digits
to a `RealNumeric` and then `toDouble()`; an entry that holds a symbol, or an argument that is not a
collection (vector, list, matrix) and holds one, leaves the call, canonical, with the terminal note
`decomposition.symbolic` (`f_` the name), "lu needs a numeric matrix: the call stays"; so does an
entry the number format leaves unevaluated, such as a definite integral without a closed form under
an exact format, which holds its variable. Any other
argument that is not a matrix is "lu needs a matrix" ("lstsq needs a matrix and a vector"), an entry
without a real value "lu needs a real matrix", one beyond the `double` range, or a non-zero one
that rounds to `0.0`, "lu's entries are beyond the double range", `b` of the wrong length "lstsq needs a vector of N entries", and each
failed status its message (`docs/language.md` §14.17). The operation runs through
`Providers.defaults()`; each factor becomes a `Matrix` of `Real` entries, `BigDecimal.valueOf` of
the `double` (its shortest decimal) rounded to `min(precision, DIGITS = 16)` digits, half even, so
nothing beyond what the computation gave is shown and a result under `numeric(…, 5)` has 5 digits;
`lu`, `qr` and `svd` give a `List` of matrices, `lstsq` a `Vector`, and the value is canonical.

**The step and the mark.** A value is recorded as one `Rewrite` at the root in the rule set
`numeric`, `numeric.decomposition` (`digits_ = 16`), from the canonical call to the value,
"Computed in double precision through the numeric decomposition provider, at most 16 digits", when
the call is the statement or the argument of `steps` or of a `numeric` that is (as an integral's
`numeric.quadrature`, **The numeric path of an integral**), and nested calls bring no step. Whatever
the trace, a value sets the evaluator's numeric flag, so `Evaluator.Result.numeric` and
`Outcome.Value.numeric` mark the statement and the worksheet shows its badge
(`docs/reference/app.md`); a call that stays sets nothing. These are functions, not commands, so
they evaluate under every number format, and `Functions.apply` never sees them; the plotting
compiler refuses them as functions it does not compile.

### Tests

In `engine/src/test/java/mathcanvas/engine/`: `linalg/numeric/HipparchusDecomposeProperties` (200
tries each over random `double` matrices with entries in `[−10, 10]` up to 8×8: `P·A = L·U` with
`L` unit lower and `U` upper triangular and `P` a permutation, `Q·R = A` with `QᵀQ = I` and `R`
upper, `L·Lᵀ = A` for `B·Bᵀ + I`, `U·Σ·Vᵀ = A` with orthonormal columns and the singular values
descending and non-negative, `expm(A)·expm(−A) = I` for entries in `[−1, 1]`, each to `1e-10`
relative to the matrix, and least squares meeting the normal equations `Aᵀ(A·x − b) = 0`, each
asserting the status `OK`; and the same at every scale, matrices times `2^k` for `k` in `[−500,
500]`: `Q·R`, `U·Σ·Vᵀ`, `P·A = L·U` and `L·Lᵀ` to `1e-10` of the matrix with `Q` and `U`
orthonormal, and least squares' `x` the same as at scale 1);
`linalg/numeric/HipparchusDecomposeTest` (least squares against the normal equations solved exactly
over ℚ by the engine's `solve`, the exponential of a diagonal and of a nilpotent matrix, every
failing status and that it carries no factor, the relative singularity test (`diag(10¹², 1)`
decomposes, a pivot of `2⁻⁵²` does not), `qr` and `lstsq` at `10⁻¹⁶⁰`, and `qr`, `cholesky`, `lstsq`
and the SVD at `10^±200`, `-0.0` and NaN in
`normalise`, the operands' checks and copies); `provider/DecomposeSeamTest` (a provider of 1×1
exponentials ahead of the default: routed to, normalised, falling through, agreeing);
`eval/NumericDecompositionsTest` (each builtin's shape and value, the digits, closed and degree-mode
entries, the call left with its note, every error, the step with `TraceChecks.assertChain` and its
narration, the numeric mark nested and at the top); `linalg/numeric/NumericLinalgPerformanceTest`
(a 200×200 SVD against the whole-run budget, in `perfTest`). The corpus `numeric-linalg.mc` runs
the language's cases through the CLI, and `StepsTest` in `app` shows the badge.

## Number theory

Package `mathcanvas.engine.numbertheory`: exact integer algorithms, all deterministic (no random
base or curve is drawn) and all checking the cancellation in every loop that can run long.

**Primality** (`Primes`). `isPrime(n)` is false below 2. Numbers up to `SMALL_BOUND` (10⁴) are
looked up in a table of the primes, made by the sieve of Eratosthenes; larger ones are divided by
the primes below 1,000 and then given the strong probable-prime test. Below `DETERMINISTIC_BOUND`
(3,317,044,064,679,887,385,961,981, the least composite that passes the strong test to every base
from 2 to 41, Sorenson and Webster 2015) the test runs to the thirteen bases 2 … 41 and its answer
is a proof (`isProven`); at or above it the test is Baillie–PSW: the strong test to base 2 and the
strong Lucas test with Selfridge's parameters (`D` the first of 5, −7, 9, −11, … with Jacobi symbol
−1, `P = 1`, `Q = (1 − D)/4`; a perfect square is composite), which no known composite passes.
`next(n)` and `previous(n)` step over the odd numbers; `between(a, b)` sieves the window `[a, b]`
(at most `MAX_WINDOW`, 10⁶, integers) with the primes up to `√b`, or tests each number when `√b` is
past 10⁷.

**Factorisation** (`Factorizer.factor(n, budget, cancellation)`) returns a `Factorization`: the
sign, the primes with their exponents, the composite cofactors left `unfinished` with theirs, and
the `Event`s that found each factor (`trial`, `power`, `rho`, `ecm`), for the narration. It
divides by the primes up to 10⁴ (a cofactor left below 10⁸ is then prime), and takes each cofactor
in turn: prime by the test above; a perfect power `r^k` (`k` from the largest possible down); else
Brent's variant of Pollard's rho on `x² + c` for `c` = 1, 3, 5, … within `Budget.rhoIterations`
per cofactor, with the products of differences batched 256 at a time between gcds, on two 64-bit
limbs in Montgomery form below 2¹²⁶ and on `BigInteger` above; else Lenstra's elliptic-curve
method on Montgomery curves `By² = x³ + Ax² + x` in `x` and `z` only, with Suyama's
parametrisation for σ = 6, 7, 8, …: 25 curves at B1 = 2,000 and then 90 at B1 = 11,000, each a
ladder by the product of the prime powers up to B1 and a prime continuation to 50·B1 in steps of
210. A factor found, prime or not, goes back on the list. The curves' modular multiplications are
counted for the whole call against `Budget.ecmWork`; past it the cofactor is left unfinished.
`Budget.FULL` (rho 2²⁰ iterations, `ECM_WORK` = 6,000,000 multiplications) is the budget of the
language's functions: a 30-digit product of two 15-digit primes factors in about a second on an idle
machine, and a 60-digit product of two 30-digit primes is given up after about ten. `Budget.SHORT`
(rho 20,000 iterations, no curves) is the polynomial algebra's: `divisors(n, cap, cancellation)`
gives the positive divisors of `|n|` ascending, or nothing when `n` is not factored within it or
has more than `cap` divisors.

**The functions** (`NumberTheory.apply(call, ctx)`, from the evaluator for the names in
`NumberTheory.FUNCTIONS`, `docs/language.md` §14.23) take integer arguments: another number is an
`IllegalArgumentException` ("isprime takes an integer"), the evaluator's error at the call; a
non-number leaves the call. `isprime`, `nextprime`, `prevprime` and `primes` are `Primes`'s;
`primes` gives a vector by the bracket rule. `ifactor` factors within `Budget.FULL`: 0 and ±1 are
themselves, a complete factorisation is `factored(sign, p₁, e₁, …)` (`NumberTheory.factored`,
which gives the plain integer for ±p), an incomplete one leaves the call. `divisors`,
`numdivisors`, `sigma`, `totient` and `mobius` take `|n|`, are `undefined` at 0, stay when the
factorisation is incomplete, and are computed from the prime powers (`divisors` lists at most
`MAX_DIVISORS`, 100,000).

**Steps.** At the top of a statement `ifactor` records one rewrite from the call in the rule set
`ifactor`: `factors`, with the factors each method found as named values (`trial`, `power`, `rho`,
`ecm`), a prime cofactor that trial division left (`cofactor`, narrated by
`ifactor.step.trial-prime` as "leaves 5, which is prime" when it is a proven prime) and the primes
above the deterministic bound (`probable`); `prime` for a prime; or the note `unfinished`, which
leaves the call, with the primes `found` (left out when none was found) and the composite parts
`left`. The values are built only when the statement is traced; the untraced path returns before
any of them. `NumberTheoryNarrator` writes `factors` as a run of sentences, one per method
(`ifactor.step.*`), the factorisation, and the Baillie–PSW caveat, with the numbers as lists.

**Modular arithmetic** (`Modular`, through `NumberTheory.apply`). `powmod(a, k, m)` and
`invmod(a, m)` take integers and a positive modulus ("powmod's modulus must be a positive
integer"); modulo 1 the value is 0. `powmod` with `k < 0` raises the inverse to `−k`, `undefined`
when `gcd(a, m) ≠ 1`, and `powmod(0, 0, m)` is `undefined`; otherwise it squares and multiplies
from the exponent's lowest bit. `invmod` runs the extended Euclidean algorithm on `m` and `a mod m`,
`undefined` when the last remainder is not 1. `crt(rs, ms)` takes two lists or vectors of integers
of one length, at least one congruence and positive moduli; it reduces each residue and merges the
first two congruences while there are two: with `g = gcd(m₁, m₂)`, they disagree when `g ∤ r₂ −
r₁` (the value is `undefined`), else `x = r₁ + m₁t` with `t ≡ ((r₂ − r₁)/g)·(m₁/g)⁻¹ (mod m₂/g)`,
modulo `lcm(m₁, m₂)`. A list with an item that is not a number leaves the call; a number, set,
matrix or truth value for a list is an error.

**Steps** (rule set `nt`, narrated by `NumberTheoryNarrator`), each a rewrite at the root replacing
the whole state, so they chain from the call to the value: `powmod`'s states are `r ·
powmod(b, e, m)` (the invariant `aᵏ ≡ r·bᵉ`), one step per bit, `powmod-square` for a 0 bit and
`powmod-multiply` for a 1, the first prefixed by the exponent in binary (`nt.powmod-binary`), the
run ending at the number or as soon as `r` is 0; `powmod-inverse` first for a negative exponent;
an exponent of more than `Modular.NARRATED_BITS` (64) bits is the one step `powmod-long`.
`invmod-euclid` (to `t mod m`, with the table of remainders, quotients and coefficients of `a` as a
matrix) and `invmod-reduce`; `crt-merge` per merge (to the call on one congruence fewer) and
`crt-done`; `powmod-none`, `invmod-none` and `crt-conflict` to `undefined`.

**Combinatorics** (`Combinatorics`, through `NumberTheory.apply`; `choose` is the registry's
alias of `binomial`). `permutations` is the falling factorial; `multinomial(n, ks)` a product of
binomials, `C(k₁, k₁)·C(k₁ + k₂, k₂)…`; `fibonacci` and `lucas` by fast doubling (`F(2j) =
F(j)(2F(j + 1) − F(j))`, `F(2j + 1) = F(j)² + F(j + 1)²`, `L(m) = 2F(m + 1) − F(m)`); `catalan` as
`C(2n, n)/(n + 1)`; `bell` by the Bell triangle; `stirling2` by the inclusion–exclusion sum
`(1/k!) Σ (−1)^j C(k, j)(k − j)^n`; `partitions` by Euler's pentagonal recurrence. Counts of no
structure are 0. The call stays past `Canonical.MAX_FOLD_BITS` (estimated before computing: `k`
times the bits of `|n| + k` for `permutations`, `n` times the bits of `n` for `multinomial`, `|n| ≤
MAX_FIBONACCI` (1,500,000), `n ≤ 2²⁰/2` for `catalan`) and past `MAX_BELL` (2000, for `bell` and
`stirling2`) and `MAX_PARTITIONS` (10,000).

**Linear Diophantine equations** (`Diophantine.solve(call, equation, x, y, k, ctx)`, from the
evaluator's command `diophantine`, which evaluates the equation in the scope of its two unknowns,
renames them as `solve` does when they would capture a name, and chooses the integer parameter as
`solve` does, collecting it for the note `k ∈ ℤ`). A decided equation is `{}` when false and leaves
the call when true. `lhs − rhs` must be a polynomial of total degree 1 in the unknowns with rational
coefficients: a nonlinear one in the unknowns alone is an error, any other kernel (a symbolic
coefficient) leaves the call. The coefficients are cleared to integers `a x + b y = c`. With `a = b
= 0` the call stays for `c = 0`, else `{}`; `g = gcd(a, b) ∤ c` gives `{}` (`diophantine-none`);
one zero coefficient fixes that unknown (`diophantine-one`, the other `k`); otherwise `x₀ = (c/g)·
(a/g)⁻¹ mod |b|/g`, `y₀ = (c/g − (a/g)x₀)/(b/g)`, the step `diophantine-particular` to `{[x = x₀, y =
y₀]}` and `diophantine-general` to `{[x = x₀ + (|b|/g)k, y = y₀ − sign(b)(a/g)k]}`.

### Tests

`PrimesTest` (small numbers, Mersenne primes, Carmichael numbers and the strong pseudoprimes to the
first bases up to the bound, the least strong Lucas pseudoprime, the Jacobi symbol, the primes
around and between numbers), `FactorizerTest` (trial division, perfect powers, rho, the curves on
two 15-digit primes, an unfinished cofactor, the short budget, cancellation),
`FactorizerProperties` (factorisations of random 1–40-digit numbers multiply back with prime
factors and are complete to 100 bits; primality against a sieve below 10⁶; products of two primes
composite), `NumberTheoryPerformanceTest` (the 30-digit and 60-digit cases above), and
`eval/NumberTheoryFunctionsTest` (each function's values and edge cases, the calls left, the errors,
the display-only product in every use, and the steps with `TraceChecks.assertChain`, the unfinished
note narrated), `eval/ModularFunctionsTest` (`powmod`, `invmod`, `crt` and `diophantine`: values,
edge cases, errors, the parameter, every step's text) and `ModularProperties` (`powmod` and
`invmod` against `BigInteger` for random operands of up to 80 bits, `crt`'s value satisfying every
congruence below the lcm or the congruences disagreeing, `diophantine`'s solutions substituted at
seven values of `k`, none exactly when the gcd does not divide; every traced run a chain),
`eval/CombinatoricsFunctionsTest` (the counts, their conventions at negative and out-of-range
arguments, the caps, and identities between them). The
corpus `number-theory.mc` runs the language's cases through the CLI.

## Discrete mathematics

Package `mathcanvas.engine.discrete`: finite sets and recurrences (`docs/language.md` §8.5,
§14.24). The boolean
operators (§8.4) are evaluated by the evaluator, which checks that each operand can be a truth value
(a truth value, a name, a relation, an application that is not a set operation; else "and takes
truth values") and leaves the identities to the rule set `logic` (**Simplification**); it refuses
a boolean expression as an operand of arithmetic and an ordering of truth values.

**Truth tables** (the evaluator's command `truthtable`). The second argument, as written, is a list
of distinct names (at most `MAX_TRUTH_TABLE_NAMES`, 10); the header row is the names and the first
argument evaluated with them bound; each assignment, every name `true` first and the last changing
fastest, evaluates the held expression in a frame of the truth values (inside the current frame, as
`subs` does) and simplifies it with the standard list, which must give a `Bool`, else the error
names what is left. The value is a `Matrix` of the rows, which the final simplification of the
statement leaves as it is but for the header's expression.

**Sets** (`Sets`). `Sets.apply(name, args, cancellation)` evaluates `union`, `intersect`,
`setdiff`, `symdiff`, `subsets` and `cartesian` of evaluated arguments, giving null when the call
stays. An argument that is a value but not a set (`Sets.isValue`: a number, a truth value, a
domain, a relation, a list, vector or matrix) is an `IllegalArgumentException`; any other
non-set leaves the call. `Sets.same(a, b)` decides whether two items are one element: `TRUE` when
they are equal trees or numbers of equal value, `FALSE` when both are closed values (numbers, truth
values, or lists, vectors and matrices of them, compared item by item) that differ, else `UNKNOWN`.
`member(x, s)` is `TRUE` when some item is the same, `FALSE` when every item is distinct. A union
adds the items of the second set not already members of the first (an unknown one is added, so
`{x} ∪ {1}` keeps both); an intersection or difference with an unknown member stays; the symmetric
difference is the union of the two differences. `subsets` lists the `2ⁿ` subsets (`n ≤
MAX_SUBSETS`, 16), `cartesian` the pairs by the bracket rule (at most `MAX_PAIRS`, 100,000). Every
result is a canonical set.

The evaluator calls `Sets.apply` for those names, decides `x ∈ S` for a set `S` by `member` (the
relation stays when it is unknown), refuses `∈` with a right side `Sets.isValue` refuses but a
domain, and refuses a set or a set operation as an operand of arithmetic ("sets carry no
arithmetic"). `size` of a list or a set is its item count (`linalg.Linalg`). `Brackets.isScalar` is
false for a set operation, so a bracket holding one is a list.

**Recurrences** (`Rsolve.solve(call, problem, constants, ctx)`, from the evaluator's command
`rsolve`, which reads its arguments as `dsolve`'s, one recurrence in one unknown, and gives it fresh
constants `C_1, C_2, …` collected for the note; the numerics are in radians). Built from
`ode.Tools`. The applications `a(n + s)` of the unknown must have integer shifts (another argument
is unsupported); with fewer than two shifts, or a condition `a(k) = v` whose `k` is not an integer,
it is an `IllegalArgumentException`. `n` is shifted so the least shift is 0, each `a(n + j)` becomes
a symbol `yⱼ`, and the equation must be linear in them, `Σ cⱼ yⱼ = f`, of order `d ≤ MAX_ORDER` (8),
with `c₀` and `c_d` not zero and nothing inexact.

- Constant coefficients: the characteristic polynomial `Σ cⱼ rʲ` (step `characteristic`, shown in
  `r`, or `λ` when the call mentions `r`) is solved by `Tools.solveFor` (exact roots only, none 0);
  each root's multiplicity is the count of derivatives vanishing at it; a real root gives `nᵏ rⁿ`, a
  pair `α ± βi` (taken from `β > 0`) gives `nᵏ ρⁿ cos(θn)` and `nᵏ ρⁿ sin(θn)` with `ρ = √(α² + β²)`
  and `θ = atan(β/α)` (plus π for `α < 0`, π/2 for `α = 0`), simplified. The basis must have `d`
  members (step `basis`). The forcing `f`, expanded, is split into families `poly(n) bⁿ` (a factor
  `w^(un + v)` gives `w^u` to the base; any other factor in `n` is unsupported); each family's trial
  is `nˢ (A₀ + … + A_D nᴰ) bⁿ`, `s` the multiplicity of `b`, and the coefficients solve the
  recurrence at `d + Σ (s + D + 1)` consecutive indices from 0, which determine them (an exponential
  polynomial vanishing at that many consecutive integers is 0) (step `particular`).
- First order with variable coefficients: `p = −c₀/c₁`, `q = f/c₁`, `P(n) = Π_{k=n₀}^{n−1} p(k)` and
  `Σ_{j=n₀}^{n−1} q(j)/P(j + 1)` by `Calculus.sum`, `n₀` the least condition's index or 0; a product
  or sum left unevaluated is unsupported (step `product`).

The conditions solve for the constants (`Tools.solveSystem`, step `conditions`); no solution is `{}`.
The check substitutes the solution for every application in the recurrence at the `CHECKED` (6)
indices from `n₀` and evaluates the builtins of numbers: the residual must be 0, or within the
precision less ten digits of 0 relative to its largest term with the constants at fixed rationals;
then each condition is checked. An unsupported form leaves the call with the note `unsolved`, a
failed check with `unverified`, an unmet condition with `unmet`.

### Tests

`eval/RsolveTest` (each class, the constants and their note, what stays, the errors, the steps
with `TraceChecks.assertChain`), `discrete/RsolveProperties` (random recurrences of order 1 to 3
with small integer coefficients, a constant, polynomial, exponential or no forcing and initial
values, every solution returned agreeing with the iteration at the first twelve indices),
`eval/TruthTableTest` (an implication's table, the shape, the names bound, the errors),
`eval/LogicOperatorsTest` (the identities, relations as operands, the errors, the printers, a
step), `simplify/LogicProperties`, `print/OperatorRoundTripProperties`, `eval/SetOperationsTest` (the operators and functions, empty sets, membership, undecided items,
the errors, the three printers) and `SetProperties` (commutativity, distributivity, De Morgan's laws
for the difference, the symmetric difference, inclusion and exclusion, the sizes of the power set
and the Cartesian product, at random sets of small integers). The corpora `collections.mc` (sets) and
`discrete.mc` (logic, truth tables, recurrences) run the language's cases.

## Units

Package `mathcanvas.engine.units`: the unit table, the resolution of a written unit, and
dimensions (`docs/language.md` §3.6, §14.26).

### The table and the node

`UnitTable.ENTRIES` lists every unit with its ASCII name, its Unicode spelling (`Ω` for `ohm`), the
spelling the layout shows (`°C` for `degC`, `°` for `deg`), its `Dimension`, its exact factor to the
coherent SI unit of that dimension (a canonical `Num`, or `π/180` for the degree), an offset for the
affine temperatures (`degC` 5463/20, `degF` 45967/100, in their own scale), whether the SI prefixes
apply, and which prefix makes the coherent unit (3 for the gram, whose coherent unit is the
kilogram). The sources and every factor are in the class comment and `UnitTableTest`.

`Dimension` is seven exact exponents over `kg m s A K mol cd`, rational so that `√(2 m)` has one;
`times` adds, `pow` scales, `isNone` is a number's.

`Units.of(written)` resolves a name as typed: a spelling of the table, else the longest SI prefix
(`da` before `d`; micro as `u`, `µ` or `μ`) and a unit that takes prefixes, else empty. A name of
the table wins, so `ft` and `pt` are never a femtotonne and a picotonne. The node is
`expr.Unit(name, rank)` with the normalised ASCII name (`um`, `kohm`) and the rank: the table index,
then the coherent unit before the other prefixes, then the prefix's exponent. The table's order is
the SI derived units, the base units in their order, the accepted units, the imperial and US units,
the temperatures, so the order of a product's units reads `6 _N _m`, `_J/(_kg _K)`, `_km/_h`.
`Units.info(unit)` gives the entry and the prefix: the factor, the dimension, the offset and the
spellings. `Units.all()` lists every unit and every prefixed form.

### Units as constants

Every operation treats a unit as a positive real constant: the canonical form collects equal units
like symbols and keeps unlike ones apart, `Inference` knows a unit is positive and real,
`Polynomial.of` takes one as a kernel, `Free` finds it free of every variable, `NumericForm` and
`Substitution` leave it as it is, and the compiler refuses it as a `FORM` ("quantity"). The
identities this gives are true for any positive values of the units, which is what makes the
identities between units (`_km = 1000 _m`), applied only by the conversions, an addition rather
than an exception.

### Dimensions

`Quantities` splits a canonical term into its unit factors (units and powers of units), its unit
part and its magnitude, and gives an expression's `dimension`: a product adds, a power scales by an
exact rational exponent, a sum's terms must agree (an exact 0, `isZero`, agreeing with any
dimension, so `x _m + 2 _cm = 0` and `max(0, 3 _m)` fit), `abs`, `sqrt`, `cbrt`, `root`, `min` and `max`
carry their arguments' dimension and `sign` has none, any other application, a relation and a
collection have none, and an unevaluated form has its body's. A misfit throws `DimensionException`
with its kind (`ADD`, `COMPARE`, `EXPONENT_UNITS`, `EXPONENT`, `FUNCTION`) and the terms involved.
`factor(part)` is a unit part's exact factor, `number(e)` a dimensionless expression with every unit
replaced by its factor, `inBase(e, absolute)` a quantity's value in coherent SI units (on the
absolute scale for a bare temperature when asked), and `isBareTemperature` says which.

The evaluator checks, and never rewrites: after the arithmetic of a sum, product or power, the
value's dimension when it holds a unit (one walk of its arithmetic, and none when it holds none),
each item's for a list, a vector or a matrix (`[3 _m] + [2 _s]` is refused); for a relation, the
sides' dimensions, an exact 0 fitting either, deciding two numbers times units by their values in
coherent units, two bare temperatures absolutely; for a builtin function other than those that
take quantities, each argument with units must be dimensionless and is replaced by its number, an
argument with radians or degrees evaluating the function in radians; `min` and `max` of quantities
pick by their values in coherent units, or stay; a quantity on the left of `in` is an error. Every
misfit is an evaluation error at the node, naming a sum's units by its first term with units.

Known limits: a quantity with the exact magnitude 0 is the number 0 in canonical form, so `0 _degC`
loses its scale (`convert` says to write `0.0 _degC` or `273.15 _K`); `factor` and the other
algebra commands see units of one dimension as different constants, so a sum in mixed units is
factored before the `units` rules write it in one unit (`factor(x^2 _m - _cm)` is `(x² - 1/100)
_m`, not `(x - 1/10)(x + 1/10) _m`; convert first); and two angle units in a product merge into
the earlier in the table, the degree (`_rad _deg` is `180/π _deg²`).

### The `units` rule set

First in the standard list, so a sum of quantities meets one unit before `rational` combines
fractions (`1/_s + 1/_ms` is `1001/_s`); not in the list for expanded forms, whose sums of
quantities it would collect again.

| Rule | Pattern → replacement | Example |
|---|---|---|
| `add-quantities` | a sum with two or more terms with units, or with units and dimensionless, every term of one dimension → `(Σ magnitude · factor/target factor) · target`, the target the unit part of the first term with units (none when dimensionless); computed | `3 _m + 2 _cm` → `151/50 _m`; `1 + 30 _deg` → `π/6 + 1` |
| `merge-units` | in a product, a unit with the dimension of an earlier one → its factor over the earlier's times the earlier; valued (`unit`, `definition`) | `_km _m` → `1000 _m²` |
| `cancel-dimensionless` | a product of two or more different units whose dimensions cancel → its number; computed | `3 _h _Hz` → `10800` |

Termination: each rule lowers the number of different units of a sum or a product, and nothing in
the standard list makes a unit. A rule whose dimensions do not fit declines, so the set never
throws; the evaluator reports the misfit.

### Conversion

`Convert.of(q, target, ctx)` checks the target (a product of powers of units, no two of one
dimension, `q`'s dimension), brings a sum to one term with the `units` set, untraced, and rewrites
`convert(q, target, q)` with the rule set `convert`, whose third argument keeps the quantity as
given for the narration:

| Rule | Does | Values |
|---|---|---|
| `affine` | a bare temperature to one unit of temperature, on the absolute scale, in one step | `quantity`, `result` |
| `to-base` | the first unit of `q` that is not a coherent SI base unit, written in base units (`_km` → `1000 _m`, `_N` → `_kg _m/_s²`) | `unit`, `definition` |
| `to-target` | `q` in base units, the target in base units: the magnitude over the target's factor | `quantity`, `result`; `num`, `den`, `target` |
| `to-defined-target` | the same with a target that is not in base units, its definition narrated | `unit`, `definition`, `quantity`, `result`; `num`, `den`, `target` |

`num` and `den` are the quotient before it is reduced, the given magnitude times the source units'
factors over the target's, given only when every factor is rational, the target has a unit above
the line and the quotient is not in lowest terms. `UnitsNarrator` owns the set and makes a
conversion one step, its rewrites' texts joined as sentences, a last rewrite with `num` told by its
key with `-quotient` appended (`UnitsNarrator.KEYS`): `So 5 _km/_h = 5000/3600 _m/_s = 25/18
_m/_s`. The messages print units as the Unicode printer does (`cannot convert km/h to s`). `units` and `magnitude` take the unit part and the magnitude of `Quantities.single(q)`,
a sum of quantities written in one unit.

### Printing

`Display` writes a product with units as its magnitude times its unit part, each displayed as a
product of its own, so the magnitude's denominator stays with it: `25/18 _m/_s`, not
`25 _m/(18 _s)`. The text printers write a unit with its underscore, the Unicode printer in its
Unicode spelling (`_µm`, `_kΩ`) and the ASCII printer in its name (`_um`, `_kohm`); a quantity is
the magnitude, a space (`*` in ASCII) and the unit part, a unit part with no unit above the line
dividing the magnitude (`5/_s`, `1/_s²`), a magnitude of -1 a sign (`-_m`, `-_m/_s`, unparenthesised
since `(-_m)/_s` is the same). LaTeX writes a unit upright (`\mathrm{km}`, `\mathrm{k\Omega}`,
`{}^{\circ}\mathrm{C}`) and a quantity on the line, a thin space after the magnitude:
`\frac{25}{18}\,\mathrm{m}/\mathrm{s}`, `\mathrm{J}/(\mathrm{kg}\,\mathrm{K})`. The layout sets a unit as a `UnitText`, a thin space after the magnitude
(none before `°`), and the unit part's division on the line with an ordinary solidus (`5 km/h`,
`J/(kg K)`), never as a stacked fraction; the magnitude's own fraction stays stacked.

### Tests

`units/UnitTableTest` (resolution, every factor exact, the dimensions, the order, every unit reading
back from its name and its Unicode spelling); `eval/UnitsTest` (quantities as values, their printing
and round trip, the token's errors); `eval/ConvertTest` (`convert` by each rule, the temperatures,
`units` and `magnitude`, the step, the errors); `eval/UnitsArithmeticTest` (sums, products,
temperatures, comparisons, functions, commands and every dimension error); `eval/UnitsProperties`
(the `units` rules sound when the base units take random positive values, through the test
evaluator, which values a unit as its factor times the base units' values; a sum of one dimension
left with one unit part; a sum of two dimensions refused, unless one is 0). The corpus `units.mc` runs the language's
cases through the CLI.

## Bases

Package `mathcanvas.engine.bases` (`docs/language.md` §3.1, §14.27). `Bases.apply` gives `hex`,
`bin`, `oct` and `base(n, b)` as a `Based(value, base)` (`base(n, 10)` is the integer) and
`digits(n, b)` as a vector, most significant digit first; a symbolic argument leaves the call, a
number that is no integer or a base outside 2 to 36 is a `BaseException`, which the evaluator
reports at the call. `Bases.text` is the literal: `0xff`, `-0b101`, `0o17`, or a base's bare digits.

A `Based` is a display of its integer: the canonical form keeps it only at the root and as an item
of a list, a vector or a matrix (**The canonical form**), and the evaluator unwraps it in the
arguments of every builtin function, so it reaches arithmetic and functions as the integer. The
text printers write `0xff`, `0b1011`, `0o17` (a sign before the prefix), which read back as the
integer (a `0` before a name that would join it into one, `x2`, takes a space: `0 x2`); another base is `zz₍₃₆₎` in Unicode and the layout, and the call `base(1295, 36)` in ASCII,
which evaluates to the value; LaTeX writes `\texttt{0xff}` or `\mathrm{zz}_{36}`. `NumericForm`
and the compiler take it as its integer.

`Bitwise.apply` computes `bitand`, `bitor`, `bitxor`, `bitnot`, `shiftleft`, `shiftright`, `bit`
and `bitcount` with `BigInteger`, a shift or an index from 0 to `MAX_SHIFT` (2²⁰, the canonical
form's folding bound). The compiler's `Node.Bits` takes integers within 2⁵³ as `long`s and gives
NaN for anything else or a result past 2⁵³.

### Tests

`eval/BasesTest` (the literals and their errors, each function, the base not propagating, the
digits, the errors); `eval/BitwiseTest` and `eval/BitwiseProperties` (each function against
`BigInteger` on random integers, negatives included, and the compiled forms within 2⁵³);
`eval/BasesProperties` (the digits reconstruct `n` in every base, the literals
read as their integers, the printers' text reads back, the base does not propagate). The corpus
`bases.mc` runs the language's cases through the CLI.

## Finance

Package `mathcanvas.engine.finance` (`docs/language.md` §14.28), in Excel's conventions: money
paid out negative, `type` 0 at the end of each period and 1 at the beginning.

`Tvm.apply` returns for `pv`, `fv`, `pmt`, `nper`, `effective` and `nominal` the expression the
function stands for, which the evaluator then evaluates: with `g = (1 + r)ⁿ` and
`A = (1 + r·type)(g − 1)/r`, `fv = −(pv·g + pmt·A)`, `pv = −(fv + pmt·A)/g`,
`pmt = −(pv·g + fv)/A`, at `r = 0` the linear forms, and `nper = log(x, 1 + r)` with
`x = (pmt(1 + r·type) − fv·r)/(pmt(1 + r·type) + pv·r)`, undefined when `x` is not positive; the
evaluation gives exact values from exact data (`log-base-exact` makes `nper(1/10, 0, -100, 121)`
2) and decimals from decimals. A rate of −1 or less is `undefined`; an argument that is not a real
number leaves the call.

`Cashflows.npv` is `Σ cₖ/(1 + r)ᵏ` from `k = 1`, and `Cashflows.amortization` the schedule of
period, payment, interest (`−balance·r`), principal (payment − interest) and balance, in the
tower's arithmetic, so an exact loan ends at an exact 0; at most 1000 periods.

`Newton.rate` solves the equation divided by `g`, `pv + pmt(1 + r·type)(1 − q)/r + fv·q = 0` with
`q = (1 + r)⁻ⁿ`, whose steps do not run off towards −1 as the undivided one's do, and
`Newton.irr` the root of `Σ cₖ (1 + r)⁻ᵏ` from `k = 0`, both by Newton's method with the analytic
derivative in decimals ten digits beyond the session's, each step halved while it would leave the
rates above −1 or not reduce the residual, at most 100 steps, until a step is below the session's
last digit; an exact guess starts as a decimal at the working precision, and the value is rounded
to the session's precision. A root at 0 is found first, the equation at `r = 0` (`pv + pmt·n + fv`,
or the flows' sum) checked exactly, and a value below the session's last digit is 0. The evaluator
marks the value numeric and records the step `numeric.rate` or `numeric.irr` (`digits_` the
digits), `tvm` solving for `i` the step `numeric.rate` for its list. No
convergence, and `irr` without flows of both signs, is a `FinanceException` reported at the call.

`tvm` is a command whose arguments are held, the parser reading `n i pv pmt fv type` before `=`
inside it as literal names (the builtins `pv`, `pmt`, `fv` and the constant `i` included, as
`odesolve` reads `steps`); the evaluator evaluates each part's value, finds the missing one with
the matching function (`nper` for `n`, `rate` for `i`) and returns the list of the five relations.

The compiler's `Node.Finance` gives the closed forms in `double`, NaN for a rate of −1 or less, a
type other than 0 or 1, or a non-positive `nper` ratio.

### Tests

`eval/FinanceTest` (every function exact and decimal, the rates marked numeric, the schedule, what
is undefined or stays, the errors, the compiled forms against the exact ones);
`eval/TvmTest` (each part solved for, the names inside the call, the errors);
`eval/FinanceProperties` (`pv` and `fv` inverse, `pmt` satisfying the equation exactly, `nper`
recovering the periods, `rate` and `irr` recovering the rate that made the data to 1e-10). The
corpus `finance.mc` runs the language's cases through the CLI.

## Statistics

Packages `mathcanvas.engine.stat` and `mathcanvas.engine.stat.numeric` (`docs/language.md` §14.25).

### Data and the descriptive statistics

`Statistics` is the entry point: the evaluator hands it a call of one of `Statistics.FUNCTIONS`
with its arguments evaluated, and it answers the value, its steps and whether it is numeric, or the
call itself when it stays; a wrong argument is an `IllegalArgumentException` with the language's
message, which the evaluator reports at the call. `Data` reads the arguments: a sample from a
vector or a list (a list of anything but scalars is wrong), with an optional list of counts,
non-negative integers; paired data from a two-column matrix or two samples of one length. An
argument or entry that holds a symbol leaves the call. Each entry keeps its exact value (a decimal
as the rational it denotes, so a sample with a decimal is computed exactly and the value rounded
once at the precision through the context's numerics), the entry as written, its count, and a
key, the number it is ordered by: the entry itself when it is a number, else its value at the
precision plus 10 digits through the numerics (a complex or undefined value is an error), so
entries of equal value tie.

`Descriptive` computes on expressions through `Arith`, which takes every node it builds to
canonical form: numbers fold exactly, constants and radicals stay, and the statement's simplifier
tidies the value (`√(7/3)` → `√21/3`). The moments are sums over the entries times their counts; the
order statistics walk the entries sorted by key with their counts, so a count of a million costs
nothing. The quartiles are the medians of the first and last `⌊n/2⌋` values; a percentile is R's
type 7; skewness and kurtosis are Excel's sample-adjusted forms. The minimum count each function
needs is in `Descriptive.LEAST`.

### The distributions

`Distributions` decides each call's exact case before the provider is asked. A parameter or a bound
that holds a symbol leaves the call, but for the point of the normal and t densities with exact
parameters (their closed form holds on all of ℝ); a parameter is checked against its law's domain
by its value. Exact arguments give the closed forms through `Arith`: the binomial, Poisson,
geometric and hypergeometric probabilities and cumulative sums (the hypergeometric in integers, a
rational `λ`'s Poisson sum `Σ λʲ/j!` by Horner's scheme in integers, reduced once),
every density (the t, χ² and F through `gamma` and `beta`, which the `special` rules resolve, the
χ² and F densities at 0 finite only from 2 degrees of freedom, `undefined` below), and the
exponential and uniform cumulative functions. A decimal argument evaluates the closed form at the
precision, simplified first so that the gamma values are exact. A discrete law past
`MAX_TERMS` = 2000 trials, draws or Poisson terms stays; under `numeric(…)` or a decimal format
(the evaluator's quadrature digits set) `Statistics.apply` is called with `force` and asks the
provider, with the step and the mark, and `Statistics.numeric`, which `Functions.numeric` calls
for a call over numbers that evaluation left, asks it too; a discrete law with a decimal parameter
asks it at once. The provider's binomial and hypergeometric laws take `int` counts: a larger `n` or
`N` is an error naming the range. The normal, t, χ² and F cumulative functions and quantiles always
ask it, their arguments taken to `double` from their exact values (`±∞` as the infinities), the value a
decimal of at most 16 digits; `invnorm` and `invt` at `p = 0` and `1` are `∓∞`, `invchi2` and
`invf` at 0 are 0, before the provider. A provider value marks the statement numeric, with the step
`numeric.distribution` (its binding `digits_`); a provider that did not converge leaves the call,
with the step `numeric.distribution-none`.

### Regression

`Regression` fits a polynomial of degree `n` by the normal equations: the sums `Σ xᵏ` for `k` up to
`2n` and `Σ xᵏ y` up to `n`, the matrix with the columns `xⁿ, …, x, 1`, and `linsolve` of
`Linalg.apply`, whose steps the narration splices in. A solution that is not a vector of constants
(the matrix singular: too few distinct `x`) is an error. On decimal data the normal equations are
shown in decimals, solved silently and exactly from the rationals, and the coefficients rounded
once. `r² = 1 − SSres/SStot` from the fitted values; the line's `r` is the slope's sign times `√r²`.
The linearised fits (`expreg`, `lnreg`, `powreg`) are least squares of their linear forms in
decimals at the precision plus 10 guard digits, rounded once; they mark the value numeric.
`residuals` and `predict` put each `x` into the fitted right side (`x` is the variable of a fit),
folding a closed value with a decimal to a decimal. A regression records its steps in the rule set
`reg` through `Record` (`reg.normal`, the spliced `linsolve.*` and `rref.*`, `reg.fit`; `reg.exact`
for decimal data; `reg.linearise`), which `StatisticsNarrator` narrates one step each.

### Hypothesis tests

`Tests` computes each statistic and its degrees of freedom exactly with the descriptive moments and
`Arith`, simplified silently before they are shown (decimal data's as decimals), and the
`p`-value through the `Distribute` provider: both tails as twice the upper tail of `|statistic|`,
`less` the lower and `greater` the upper, χ² and F the upper. The `z` and `t` tests are commands
that hold every argument: the evaluator reads a trailing `two`, `less` or `greater`, and `pooled`
before it, as written, evaluates the rest, and calls `Statistics.test`; `chi2gof`, `chi2test` and
`anova` are functions. A test's steps, in the rule set `test`, form a chain: the hypotheses as the
list `[μ = μ₀, μ ≠ μ₀]`, the data's summary (the summary-form call for `ztest` and `ttest`, the
matrix of `n`, `x̄`, `s` for `ttest2`, the differences for `pairedttest`, the expected counts for
`chi2test`), the statistic, the degrees of freedom and the `p`-value, each a list of the relations
so far.

### The provider

`Distribute(Law law, Kind kind, double[] parameters, double[] points)` → `DistributionValue(Status
status, double value)` is the numeric seam of the statistics. `Law` names the distributions with
their parameters (`NORMAL(μ, σ)`, `STUDENT_T(ν)`, `CHI_SQUARED(k)`, `F(d1, d2)`, `EXPONENTIAL(λ)`,
`UNIFORM(a, b)`, `BINOMIAL(n, p)`, `POISSON(λ)`, `GEOMETRIC(p)`, `HYPERGEOMETRIC(N, K, n)`) and the
special functions the distributions are built on (`ERF`, `ERFC`, `GAMMA`, `BETA(b)`). `Kind` is
`DENSITY` at `[x]` (a discrete law's probability of `x`), `PROBABILITY` of `[a, b]` with `a ≤ b`,
either infinite (`P(a < X ≤ b)`, or `P(a ≤ X ≤ b)` for a discrete law), `INVERSE` at `[p]` (the
least `x` with `P(X ≤ x) ≥ p`, continuous laws only), `UPPER_INVERSE` at `[p]` (the `x` with `P(X >
x) = p`, the quantile of an upper tail) and `VALUE` at `[x]`, the special functions' only kind. The record checks the arity, that the parameters are finite and in the law's domain, and
the points; the status is `OK`, `NOT_FINITE` (beyond the `double` range either way or not a
number: a special function's non-zero value that underflows to 0 is beyond it too) or
`NO_CONVERGENCE`, and `normalise` turns `-0.0` into `0.0`, a non-finite value into `NOT_FINITE`,
and clamps a probability into `[0, 1]` and a density to 0 or above.

The default provider, `stat.numeric.HipparchusDistributions`, always answers, through
`DistributionMath`, the `double` functions the compiled evaluators share (**Compilation**), over
Hipparchus Core's `distribution` and `special` packages:

- **Densities** are Hipparchus's, but for χ², whose closed form `x^(k/2 − 1) e^(−x/2)/(2^(k/2)
  Γ(k/2))` is taken in logarithms (Hipparchus's gave `0.0` for `chi2pdf(1e-20, 1)` against `3.99e9`);
  the χ² and F densities at 0 are finite only from 2 degrees of freedom (NaN below), the exponential
  and uniform in closed form; the geometric law counts trials `1, 2, …` where Hipparchus counts
  failures. A quantile's tail below `Double.MIN_NORMAL` has lost digits of its own and is no
  convergence for every law (`invnorm(1e-320)` stays).
- **Probabilities** come from the tail that keeps their digits: `P(X ≤ x)` and `P(X > x)` each
  directly (the normal through `erfc`, Student's t through `½ I_{ν/(ν+t²)}(ν/2, ½)`, χ² through the
  regularised gamma functions `P` and `Q`, F through the regularised beta function), and an interval
  whose lower end is above the median as the difference of the upper tails, so `P(Z > 6)` keeps its
  digits. A discrete interval is the difference of two cumulative probabilities. The incomplete beta
  function is our own evaluation of Hipparchus's continued fraction, handed the argument and its
  complement each computed directly (`ν/(ν + t²)` and `t²/(ν + t²)`; `d1 x/(d1 x + d2)` and
  `d2/(d1 x + d2)`), its prefactor's logarithms taken from the smaller of the two; where one
  parameter is at most 1 and the other at least `10⁴` times it (t past `2·10⁴` degrees of freedom)
  the fraction is run on the small parameter's side up to twice Hipparchus's crossover, where the
  other side's has lost digits: `P(T > 1.96)` at `ν = 10⁵` is right to `1e-14`, where `ν/(ν + t²)`
  alone left `1e-12`. What remains: in the region of about `ν ≥ 10⁵` with `t ≥ 2.5`, where neither
  side's fraction holds every digit, t's upper tail is off by up to about `1e-11` (`2e-11` at `ν =
  10⁶`, `t = 4`), and a quantile there by less (`invt(0.995, 250000)` is `8.6e-13` off); χ² at
  thousands of degrees of freedom is off by more, through Hipparchus's incomplete gamma function,
  and a quantile there that fails its check stays (`invchi2(0.999, 9000)`).
- **Quantiles** are asked of the tail that holds their digits, formed exactly: `Distributions`
  hands the provider `q` itself (`INVERSE`) for `q ≤ 1/2` and `1 − q`, subtracted in the number
  tower before any `double` (`UPPER_INVERSE`), above; the intervals hand it `(1 − C)/2` alike. `1 −
  q` taken in `double` would carry `q`'s rounding amplified by `1/(1 − q)` (`invf(1 − 10^(−12), 3,
  7)` was off by `6e-6`, `invnorm(1 − 10^(−300))` had no value). The normal's is `∓√2·erfc⁻¹(2·tail)`
  by the side, with `erfc⁻¹` refined by Newton's method on `erfc` (Hipparchus's `erfcInv(y)` is
  `erfInv(1 − y)`, which loses the digits of a small `y`: `invnorm(1e-17)` had no value). Those of
  t, χ² and F are a Brent search on that side to two units in the last place of `x`, bracketed by
  doubling steps from 0 or 1 (Hipparchus's own search is to an absolute accuracy: `invt(1e-20, 3)`
  was `−8.66e9` against `−4795275.72`). The continued fractions run to a relative `1e-15`. Every
  quantile is checked against its own tail, `|P − tail| ≤ 1e-12·min(tail, 1 − tail)` (the normal's
  standard quantile before `μ + σz` rounds it), and one that fails is no convergence, the call left
  with its note. `invt(0.975, 10)` is `2.228138851986274` against mpmath's `2.2281388519862747` at
  the exact tail `0.025`: the incomplete beta function holds about 15 digits.
- **Special functions**: `Erf.erf`, `Erf.erfc`, `Gamma.gamma` (NaN at the poles, the integers 0 and
  below), and `B(a, b)` as `e^(log B)` for positive arguments, the gamma ratio otherwise (NaN where
  `a` or `b` is a pole, 0 where only `a + b` is).

### Tests

`eval/DescriptiveStatisticsTest` checks the exact moments, the order statistics by their
conventions, `summary`, counts, decimal and constant data, the call left for a symbol and the
errors; `eval/DescriptiveStatisticsProperties` compares the moments, the order statistics and the
paired statistics with a plain `double` computation on random integer data with ties (relative
`1e-12`), and checks that random rational data gives values with no decimal. `eval/DistributionsTest` checks
the exact cases, the closed densities, the numeric values with their mark and step (`TraceChecks.assertChain`),
the bound past which a discrete law stays, the calls left for a symbol and the errors;
`eval/DistributionsProperties` sums the binomial probabilities to exactly 1, checks the Poisson
cumulative function against its terms, the whole normal line, each quantile against its cumulative
function (`1e-10` for the normal on `[−5, 5]`, `1e-9` relative for t, `1e-12` in probability for χ²
and F), and the closed densities against Hipparchus's (`1e-12`). `eval/RegressionTest` checks the exact fits, the decimal one (Anscombe's first set), the linearised
fits, residuals and predictions, the narration with the spliced row reduction (`TraceChecks.assertChain`)
and the errors; `eval/RegressionProperties` compares the line with its closed form and polynomials
with Hipparchus's QR solve on random data (`1e-9`), and checks that the residuals meet the normal
equations exactly, `Σ rᵢ xᵢʲ = 0`. `eval/HypothesisTestsTest` checks every test's value, the alternatives and
`pooled`, the words read when defined, the narrations (`TraceChecks.assertChain`) and the errors;
`eval/HypothesisTestsProperties` compares the one-sample and Welch `t` tests and `chi2gof` with their
formulas in `double` (`1e-12`) and their `p`-values with Hipparchus's distributions (`1e-9`).
`compile/DensityQuadratureProperties` integrates
each density, compiled, over random intervals against the provider's probability (relative `1e-8`)
and the normal density over ±40σ to 1. The corpus `statistics.mc` holds the published tables'
values. `DistributeSeamTest` registers a provider that answers the standard exponential's probabilities
raw (a negative zero, a value above 1) ahead of the default: it is routed to and normalised, and the
rest falls through. `HipparchusDistributionsTest` checks the provider against the NIST/SEMATECH
e-Handbook tables (§1.3.6.7), quantiles in the tails against mpmath at 50 digits (relative
`1e-13`), the upper tail of `P(Z > 6)`, the two tails of t summing to 1, χ² with 2 degrees of
freedom as an exponential, the discrete laws' counting (the geometric law at `p = 1` included), and
the special functions, a special function's underflow reported as beyond the range;
`QuantileTailProperties` inverts the normal, t, χ² and F tails `t ∈ [1e-15, 1e-3]`, lower and upper,
each built from `t` itself and checked against it, `P(X ≤ x) = t` or `P(X > x) = t` to `1e-12`
relative, t also at up to `2·10⁵` degrees of freedom, where only t past `10⁴` may declare no
convergence instead.

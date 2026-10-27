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
  whenever it has at most 100,000 digits, so `pow(x, 2)` is `x·x`. Beyond that it is binary
  powering at `p + digits(n) + 5` digits rounded at the end, which is faithful (within one
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
  `(-1)^k C(k - n - 1, k)`; 0 for `k < 0`), `isqrt`,
  `isProbablePrime(certainty)`. `Int` and `Rational` have `numerator` and `denominator`, and
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
| `Constant` | `Kind kind` | `PI`, `E`, `INFINITY`, `UNDEFINED`, and the domains `INTEGERS`, `RATIONALS`, `REALS`, `COMPLEXES` |
| `Bool` | `boolean value` | a decided relation |
| `Symbol` | `String name` | a variable or a function head |
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
   real part, then imaginary part.
2. If either side is a **product**, the operand lists compare from the last operand backwards,
   and a list that runs out first comes first; a non-product `v` counts as `[v]`.
3. Otherwise, if either side is a **power**, bases compare, then exponents; a non-power `v`
   counts as `v^1` and comes before the power it ties with (`x < x^1`).
4. Otherwise, if either side is a **sum**, as for products.
5. Otherwise by kind. Constants come first, in `Kind` order. Symbols and applications sort
   together by name, a symbol before an application of the same name, and two applications by
   their arguments from the left, fewer first (`f < f(x) < f(x, y) < g`). Then the other kinds
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
  `(-8)^(1/3)` stay. Exact operands are probed with `MathContext.UNLIMITED`, under which the tower
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
- **Everything else** (lists, vectors, matrices, intervals, piecewise expressions, lambdas,
  applications, the unevaluated forms) keeps its structure with its operands canonicalised.
  Nothing is type-checked: a `Bool` inside a sum is carried as it is.

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
whether angles are in radians (`radians()`), for the operations that need values (the numeric
fallback of **Solving**), and the `Cancellation`, the request to stop (**Cancellation** under
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
| `calculus.DiffNarrator` | `diff` | a `sum-rule` and the linearity rewrites after it (`sum-rule`, `constant-multiple`, `constant`, `variable`) are one step, `diff.step.linearity`; a `chain-rule` takes the rewrites below its place, the inner derivative's, as one step, `diff.step.chain` naming the outer and the inner derivative, then the inner steps' texts on the same line, for one level only: when the inner derivative takes a chain rule itself, the outer chain rule is a step of its own with its rule's text and the inner one is narrated as a step; any other rule is its own step, the `variable` rewrites right after it and below it folded in |
| `solve.SolveNarrator` | `solve` | one step per state of the equations as the solver records them (`collect`, `isolate`, `divide`; `factor`; `roots`, whose solutions the solver has already simplified), except that a quadratic's `discriminant` and `formula` are one method and one step, `solve.step.quadratic-formula`, naming the discriminant |

`steps(factor(x^4 - 1))` is the one step `1, -1 are rational roots, so x⁴ - 1 = (x - 1)(x + 1)(x² +
1)`; `steps(diff(x^2 sin(x), x))` is the product rule, the derivative of sin, the power rule;
`steps(solve(x^2 - 5x + 6 = 0, x))` is `Factor the left side`, then `The solutions are {2, 3}`.

In `eval/`, `NarrationTest` checks the texts per operation and that the steps replay,
`NarrationProperties` that over the rule sets' generators (elementary expressions for simplify and
diff, polynomials for expand, factor and solve) the steps never throw, cover every rewrite in order,
chain from one to the next and end at the result, and `NarrationBundleTest` that the bundle has a
key for every rule, event and grouped step and no other.

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
  exact and otherwise at `mc`, through the elementary functions in radians, and is empty where the
  value is undefined or outside bounds that keep every evaluation fast, since such a point is no
  useful sample: a power of an exact base formed with more than 4,000 bits (the base's digits times
  its exponent's numerator), and of an inexact base whose result's decimal exponent would pass 10⁴
  (the base's decimal exponent, plus one, times the exponent's numerator), since an inexact power
  is rounded to the precision whatever its exponent; `exp` or a power of `e` with an argument of
  modulus above 10⁴; a logarithm of a value
  whose decimal exponent is beyond ±10⁴; a circular or hyperbolic function of an argument of
  modulus above 10⁶, a circular function of an argument whose imaginary part, or a hyperbolic one
  of an argument whose real part, exceeds 50 in modulus (the value would pass `e^50`, and `cos²z +
  sin²z` would cancel more digits than any recheck carries); and any inexact value, operand or
  result, whose decimal exponent is beyond ±10⁴. It knows `sign` of a real value (inexact for an
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
accepts (a list plus a variadic flag, so `integrate` takes 2 or 4 and `gcd` 2 or more), whether
it is in the prefix set, the positions a command holds (or all of them), the position of its
bound variable, and whether `e` and `i` may be that variable (`NO`, `YES`, `WITH_BOUNDS`).
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
to `_` and ASCII, superscript powers as one token carrying their integer, and `in` as the
membership token. Reserved words stay identifiers, so a message names the word as typed. The
lexer counts open brackets and emits a newline token only outside them. The derivative notation is
one token carrying its variable and order. It commits once its head (`d/d` + name, `d^n/d` + name,
or `dⁿ/d` + name) has been read; after that, anything but the exact shape is the shape error.

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
| relations | 20, non-associative | 20 |
| `+ -` | 30 | 30 |
| `* / ×`, implicit multiplication | 40 | 40 |
| sign, prefix function name, radical | prefix | 50: takes `^` and postfix, stops before `*` |
| `^` | 60 | 59: right-associative, admits a sign |
| `! % °`, superscript | 70 | |
| `d/dx`, `lim[…]`, `Σ[…]`, `Π[…]` | prefix | 39: the rest of the term |
| `∫` | prefix | 29: an additive integrand, to its differential |

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
  `-2` is `Product(-1, 2)`, `20%` is `20·100⁻¹`, `30°` is `30·π·180⁻¹`, `√x` and `sqrt(x)` are
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
  For a command (`solve diff integrate limit sum product taylor`) it reads ahead to the second
  argument, a name possibly signed with `+` or parenthesised, or a list of names for `solve`. That
  name is bound in the first two arguments.
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
printer shares too (**The layout tree**); LaTeX has its own.

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
  every member valued, throwing `CancelledException`; `Display.of(e)` and `display(e)` never stop.

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
| `Space` | a space of the text: `LINEAR` (no width when typeset) or `THIN` (before a differential) |
| `Fraction` | numerator and denominator |
| `Scripts` | a base with a subscript, a superscript or both |
| `Radical` | a radicand and its index (`3`, `4`) or none |
| `Delimited` | a body between stretchy fences: `( )`, `[ ]`, `{ }`, `\| \|`, `⟨ ⟩`, an interval's mixed pair |
| `Matrix` | rows of cells between brackets |
| `BigOp` | `∫`, `∑`, `∏`, `lim` or `d/dx` with its bounds and body; `limits` when the bounds go below and above in display style (`∑`, `∏`, `lim`), not as scripts (`∫`) |
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
a numerator stands before the bar (`-x/2` reads as −(x/2)). A power is a superscript, a square
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
- `DisplayCancellationTest`: a thousand-member set of constants stops at every member, in the
  Unicode printer and the layout printer;
- `EchoAndCaretTest`: definition echoes and caret lines;
- `LayoutPrinterTest`: the layout tree of each construct with its paths;
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

`Settings(precision, angleMode)` holds the session settings: the precision in digits, 1 to
10,000 (`Settings.DEFAULT` is 34, radians), from which `mathContext()` derives a half-even
`MathContext`, and the `AngleMode` (`RADIANS`, `DEGREES`, `GRADIANS`) with its factors
`toRadians()` (`π/180`, `π/200`) and `fromRadians()`.

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
  (**Functions on numbers**); one with an `undefined` argument is `undefined`. A command is
  described under **Commands**. A reserved later-phase name is "rref is not available yet". A user
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
statement from the environment: its assumptions, the session's `MathContext`, the trace switch and a
rewrite cap (**Limits**). A `RewriteLimitException` becomes the error "simplification did not
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
commands with a bound variable, whose scope is their first two arguments.

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
  `cosh`, `sech` of 0, `acos(1)`, `acosh(1)`, and `atan2` on the axes.

Everything else stays (`sin(1)`, `sin(π/6)`, `exp(2)`, `log(8, 2)`): exact values are the rule
sets'. A domain error is `undefined`: `mod(1, 0)`, `factorial(-1)`, `root(-4, 2)`, `log(0)`,
`cot(0)`, `atan2(0, 0)`, `log(x, 1)`.

When any argument is inexact, the call evaluates through `Elementary` at the precision. `mod` is `a
− b·floor(a/b)` computed exactly on the decimals' values and rounded once, so `mod(1e100, 3.0)` is
`1.0`. `gcd`, `lcm`, `binomial` and `factorial` of integer-valued decimals are their exact values
made inexact (`binomial(5.0, 2)` is `10.0`); a decimal that is not an integer, a `factorial` beyond
the fold limit (`factorial(1e6)`) and `numerator`/`denominator` of a decimal (`numerator(0.5)`)
stay. The functions the tower lacks are composed at 10 guard digits and rounded once: `cot`, `sec`,
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

### numeric and exact

`numeric(e[, digits])` checks `digits` (an integer from 1 to 10,000), evaluates `e` at that
precision, then runs `NumericForm` over the value with 10 guard digits and rounds each decimal of
the result to `digits`. The pass makes every exact number inexact, replaces `π` and `e` by their
values, canonicalises the arithmetic (which folds it) and evaluates every builtin function over
numbers through the inexact path, in radians. Symbols, `∞`, `undefined`, truth values and lambda
bodies stay, and so do integer exponents and the variables, bounds, points and orders of the
unevaluated forms, whose bodies are made numeric: `numeric(x^2 + 1/3)` is `x² + 0.333…` and
`numeric(sum(k/3, k, 1, n))` is `∑[k = 1, n] 0.333…k`. A command's held arguments stay. The cost
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
  `M^-1` and other exponents stay for Phase 5. A power of a vector or of a non-square matrix is an
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
| `integrate limit sum product taylor` | the scope evaluated with the variables bound, the bounds, point and order evaluated, `left`/`right` kept; the call is built again through `TypedNodes`, so arguments that come to fit give the typed node; the node or application is returned (their commands are Phase 4's) |
| `diff`, `d/dx` | the scope, the order and the typed node as for those (`n := 2; diff(x^2, x, n)` is the `Derivative`), then a `Derivative` is differentiated (**Differentiation**), its variable free in the result (below); a `diff` call whose order is not a positive integer stays; a differentiation that exceeds its cap is "differentiation did not finish (rule set …, rule …)" |
| `expand factor cancel collect(e, x)` | the argument evaluated (`collect`'s variable held), a relation, list, set, vector or matrix item by item; the call when the value or an item is not a polynomial in scalar kernels (`Polynomial.isScalar`: `∞`, `undefined`, a domain, a truth value, a lambda, a collection or a matrix inside a sum, product or power) or the algebra refuses it; otherwise `Algebra.expand`, `factor`, `cancel`, `collect` (**Algebra**); an `expand` or `factor` at the top of a statement keeps its trace |
| `apart(e, x)` | `x` held and a symbol ("apart needs a variable"), `e` evaluated; the call when `e` is not scalar or `Algebra.apart` refuses it (a relation or collection included) |
| `gcd lcm` (functions) | on numbers the tower's, as above, when every argument is an integer or any is a decimal; otherwise `Algebra.gcd`/`lcm` (exact rationals included: `gcd(1/2, 1/3)` is `1/6`), the call when refused; at the top of a statement the provider's trace |
| `numerator denominator` (functions) | on a number the tower's; otherwise structural on the canonical value: of a product, the factors with non-negative exponents (and a rational coefficient's numerator) and the bases of the others to their positive exponents (and its denominator); of `b^-n`, `1` and `b^n`; of anything else, the value and `1`; the call for a value that is not scalar (a vector, matrix, list, set or relation) |
| `simplify steps` | the value of the argument; `steps` at the top of a statement turns the trace on |
| `subs(e, x = v, …)` | the values evaluated, substituted together into the held `e`, then evaluated; a pair that is not `name = value` is "subs needs name = value" |
| `numeric exact` | as above |
| `assumptions()` | the relations in force, as a set |
| `assume clear` | statements only (**The session**); in an expression, "assume works only as a statement" |
| `help`, and `det rref eigenvalues grad slider` | "help is not available yet", "rref is not available yet" |

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

### The session

`Session` holds the current `Environment`, the one mutable object of evaluation; it is not
thread-safe. `run(Statement)` returns an `Outcome`: a definition binds its name and gives
`Defined(echo)`; a top-level `assume(rel, …)` records each relation with its left side held and its
right side evaluated, through `Assumptions.with` (whose rejection becomes the error, with its
message), and gives the set of the relations recorded; a top-level `clear(name)` unbinds the name
(the assumptions stay) and gives the symbol; any other expression is evaluated and gives
`Value(value, trace, quiet)`. An evaluation error gives `Failed(error)` and leaves the environment
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
- in `Display.of(e, cancellation)` at every member of a set of constants valued (**Printing**).

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
- `MatrixSizeProperties`: random symbolic matrices up to 5×5 to random powers or in chains of
  products evaluate or are refused as too large, and nothing else escapes;
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

`Simplifier.standard()` is `BASIC`, `RATIONAL`, `EXPLOG`, `RADICAL`, `TRIG`, run as one combined
rule set, so a rule of a later set can enable one of an earlier set, and rule names are unique
across the list. The order matters only where two left sides overlap, and there every order gives
the same result: `rational`'s left sides are products with a negative power of a polynomial, or a
fractional coefficient (`3(x/2 + 1)` stays as written), and sums of such quotients, which the
innermost-first rewriter reaches only after the other sets have settled their operands, so `trig`'s
collapses act on trig kernels first (`(sin(x)² - 1)/(sin(x) - 1)` is `-cos(x)²/(sin(x) - 1)`, not
`sin(x) + 1`); the other sets meet on numeric powers (`basic`'s powers of powers and products want a
power or product base, `radical`'s rules a number) and on `abs` and `log` arguments, which `basic`
and `exp-log` treat in one direction; `trig`'s left sides are circular, hyperbolic and inverse
functions and the sums and products of them, and it comes last so that the other sets' traces are
unchanged. The forms the list settles on: natural logarithms only (`ln`, `log10`, `log2` and
`log(a, b)` become `log`), `exp(x)` is `e^x`, `root` and `cbrt` of a non-negative radicand are
powers, a numeric radical is a rational times `n^(p/q)` with `0 < p/q < 1` and `n` a positive
integer that is not a perfect power and has no `q`-th power dividing `n^p` (so the form is unique
within the 1,024-bit and prime-table bounds of the rules below: `4^(1/3)` and `2^(2/3)` are both
`2^(2/3)`), a circular function has its exact value at a multiple of `π/12`, an angle with no whole
or quarter turn in it and an argument that does not look negative, a product of circular functions
of one argument has the fewest functions, a quotient of polynomials in kernels has no common factor
and its numeric content in lowest terms, and absolute values, signs and logarithms are pushed into
the factors of products and the bases of powers as far as the facts allow.

### basic

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `undefined-argument` | `h_(args___)` → `undefined` | `h_` a builtin function (not a command), an argument `undefined` | `abs(undefined)` → `undefined` |
| `abs-of-abs` | `abs(abs(x_))` → `abs(x_)` | | `abs(abs(log x))` → `abs(log x)` |
| `abs-of-nonnegative` | `abs(x_)` → `x_` | `x_ ≥ 0` | `abs(x²)` → `x²` |
| `abs-of-nonpositive` | `abs(x_)` → `-x_` | `x_ ≤ 0` | `abs(-3)` → `3` |
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
| `root-of-odd-power` | `root(x_^n_, n_)` → `x_` | `x_` real, `n_` an odd positive integer | `root(x³, 3)` → `x` |
| `root-of-nonnegative` | `root(x_, n_)` → `x_^(1/n_)` | `x_ ≥ 0`, `n_` a positive integer | `root(x, 4)` → `x^(1/4)` under `x ≥ 0` |
| `root-of-negative` | `root(x_, n_)` → `-root(-x_, n_)` | `x_ < 0`, `n_` odd | `root(-16, 3)` → `-2·2^(1/3)` |
| `root-of-negative-even` | `root(x_, n_)` → `undefined` | `x_ < 0`, `n_` even | `root(-16, 4)` → `undefined` |
| `cbrt-of-cube`, `cbrt-of-nonnegative`, `cbrt-of-negative` | the same for `cbrt` | | `cbrt(-16)` → `-2·2^(1/3)` |

An even root of an even power needs no rule of its own: `root(x², 2)` is `(x²)^(1/2)` by
`root-of-nonnegative`, then `|x|`. `extract-perfect-power` divides by the primes below 10,000 and
then tests the cofactor for being a perfect `q`-th power exactly (`root(q, UNLIMITED)`); a `q`-th
power of a larger prime inside a composite cofactor is not found, which leaves a sound but
unreduced radical. It does not fire when `n^p` exceeds 10,000 bits. `2^(2/3)` stays as it is
rather than becoming `4^(1/3)`: the rule fires only when it takes something out, and
`reduce-radicand-power` (on radicands of at most 1,024 bits) takes `4^(1/3)` back to `2^(2/3)`.

### trig

Angles are in radians: evaluation has applied the angle mode before the rules run (`sin(30)` in
degrees reaches them as `sin(π/6)`, `asin(1/2)` as `asin(1/2)·180/π`, **Evaluation**). The
identities hold over the complex numbers with the engine's principal branches, so a rule asks the
facts only where the table says.

| Rule | Pattern → replacement | Condition | Example |
|---|---|---|---|
| `special-angle` | `h_(x_)` → the exact value (computed) | `h_` circular, `x_` `0` or `qπ` with `q` an exact rational | `sin(π/6)` → `1/2`; `sin(π/12)` → `(√6 - √2)/4`; `tan(π/2)` → `undefined`; `sin(7π/5)` → `-sin(2π/5)` |
| `inverse-special-value` | `h_(x_)` → the angle (computed) | `h_` an inverse circular function, `x_` or `-x_` in its table | `asin(1/2)` → `π/6`; `acos(-√2/2)` → `3π/4`; `atan(√3 - 2)` → `-π/12` |
| `shift-by-multiple-of-pi` | `h_(π·k___ + u___)` → `(-1)^k h_(u___)`, `h_(u___)` for `tan`, `cot` (computed) | `h_` circular, `k___` an integer | `sin(x + π)` → `-sin(x)`; `cos(πn)` → `(-1)^n`; `cos(x + 2πk)` → `cos(x)` |
| `reduce-rational-shift` | `h_(q_·π + u__)` → `h_(q'π + u__)` (computed) | `q_` a non-integer rational outside `(-1, 1]` (`(-1/2, 1/2]` for `tan`, `cot`) | `sin(x + 7π/3)` → `sin(x + π/3)` |
| `sin-plus-half-pi`, `sin-minus-half-pi`, `cos-plus-half-pi`, `cos-minus-half-pi`, `tan-plus-half-pi`, `cot-plus-half-pi`, `sec-plus-half-pi`, `sec-minus-half-pi`, `csc-plus-half-pi`, `csc-minus-half-pi` | `sin(u__ + π/2)` → `cos(u__)`; `sin(u__ - π/2)` → `-cos(u__)`; `cos(u__ ± π/2)` → `∓sin(u__)`; `tan(u__ + π/2)` → `-cot(u__)`; `cot(u__ + π/2)` → `-tan(u__)`; `sec(u__ ± π/2)` → `∓csc(u__)`; `csc(u__ ± π/2)` → `±sec(u__)` | | `sin(π/2 - x)` → `cos(-x)` → `cos(x)` |
| `odd-function` | `h_(u_)` → `-h_(-u_)` (computed) | `h_` one of `sin tan cot csc sinh tanh asin atan acot acsc asinh atanh`, `u_` looks negative; for `acot`, `u_ ≠ 0` (`acot(0)` is `π/2`) | `sin(-2x)` → `-sin(2x)`; `sin(y - x)` → `-sin(x - y)` |
| `even-function` | `h_(u_)` → `h_(-u_)` (computed) | `h_` one of `cos sec cosh`, `u_` looks negative | `cos(π/3 - x)` → `cos(x - π/3)` |
| `acos-of-negative`, `asec-of-negative` | `acos(u_)` → `π - acos(-u_)`; the same for `asec` | `u_` a negative number or a product with a negative coefficient | `acos(-x)` → `π - acos(x)`; `acos(1 - x)` stays |
| `sin-of-asin`, `cos-of-acos`, `tan-of-atan` | `sin(asin(u_))` → `u_`, … | | `sin(asin(x))` → `x` |
| `sin-of-acos`, `cos-of-asin` | `sin(acos(u_))` → `√(1 - u_²)`, … | | `cos(asin(x))` → `√(1 - x²)` |
| `sin-of-atan`, `cos-of-atan` | `sin(atan(u_))` → `u_/√(1 + u_²)`; `cos(atan(u_))` → `1/√(1 + u_²)` | `u_` real | |
| `asin-of-sin`, `atan-of-tan` | `asin(sin(u_))` → `u_`; `atan(tan(u_))` → `u_` | `u_` known within `[-π/2, π/2]`, `(-π/2, π/2)` | `asin(sin(x))` → `x` under `-1 ≤ x ≤ 1` |
| `atan-of-reciprocal` | `atan(1/u_)` → `π/2 - atan(u_)` | `u_ > 0` | |
| `combine-trig-factors` | the circular factors of a product in normal form (computed) | the group's measure drops | `sin(x)/cos(x)` → `tan(x)`; `1/cos(x)²` → `sec(x)²`; `tan(x) cos(x)` → `sin(x)`; `sin(x)/cos(x)²` → `tan(x) sec(x)`; `sin(x) cos(y)` stays |
| `sine-double-angle` | `c_·sin(u_)·cos(u_)·rest___` → `c_/2·sin(2u_)·rest___` | `c_` a rational with an even numerator | `2 sin(x) cos(x)` → `sin(2x)`; `sin(x) cos(x)` stays |
| `tanh-of-quotient` | `sinh(u_)^n_·cosh(u_)^m_·rest___` → `tanh(u_)^n_·rest___` | `n_` an integer, `m_ = -n_` | `sinh(x)/cosh(x)` → `tanh(x)` |
| `pythagorean` | `d·sin(u)² + d·cos(u)² + rest` → `d + rest` (computed, as every sum rule) | | `3 sin(x)² + 3 cos(x)²` → `3` |
| `one-minus-sin-squared`, `one-minus-cos-squared` | `d·sin(u)² - d + rest` → `-d·cos(u)² + rest`; the same with `cos` | | `1 - sin(x)²` → `cos(x)²`; `a - a sin(x)²` → `a cos(x)²` |
| `one-plus-tan-squared`, `one-plus-cot-squared` | `d·tan(u)² + d + rest` → `d·sec(u)² + rest`; `cot`, `csc` | | `1 + tan(x)²` → `sec(x)²` |
| `cosine-double-angle` | `d·cos(u)² - d·sin(u)² + rest` → `d·cos(2u) + rest` | | `cos(x)² - sin(x)²` → `cos(2x)` |
| `cosine-double-angle-from-cos`, `cosine-double-angle-from-sin` | `d·cos(u)² - d/2 + rest` → `d/2·cos(2u) + rest`; `d·sin(u)² - d/2 + rest` → `-d/2·cos(2u) + rest` | | `2cos(x)² - 1` → `cos(2x)`; `1 - 2sin(x)²` → `cos(2x)` |
| `hyperbolic-pythagorean` | `d·cosh(u)² - d·sinh(u)² + rest` → `d + rest` | | `cosh(x)² - sinh(x)²` → `1` |

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
`denest-square-root` the depth of nested radicals.

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
  fire) for every rule, which `covered()` checks, plus the examples above;
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
  facts (`Soundness.check`, point by point), and is defined wherever its input has an exact value;
  the termination probe (200 and 500 tries of 20 expressions) asserts fewer than 200 rewrites per
  expression. A point that disagrees at 34 digits is checked again at 100 digits, and skipped when
  either side is empty there away from cuts (`NumericEvaluator.evalAwayFromCuts`): the true value
  lies on a cut, a pole or a zero and the rounding noise picks the result (`(-8/81)^(1/3)` cubed,
  then a logarithm; `root(log(log e, (2^b)^(1/3)), 4)`, which is 0 computed near `10^-9`); the jump
  of `sign` at 0 is treated alike. A point that still disagrees at 100 digits is
  **ill-conditioned**, and skipped, when either side disagrees with itself between 34 and 100
  digits: its digits are lost to cancellation beyond what the recheck recovers (`10^120·(sin²2 +
  cos²2 - 1)`), so it is no evidence; a wrong rule at a well-conditioned point still disagrees with
  a self-consistent input and fails. **Each property run asserts that its ill-conditioned skips are
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
| `asech` | `−1/(u²√(1/u − 1)√(1/u + 1))` |
| `acsch` | `−1/(\|u\|√(u² + 1))` for a real `u`; `−1/(u²√(1 + 1/u²))` otherwise |
| `abs` | `sign(u)`, for a real `u` |
| `sign`, `floor`, `ceil`, `round`, `trunc` | `0`, for a real `u` |

The formulas hold for the principal values (**Numbers**, counterclockwise continuity) wherever the
function is differentiable, also where a real argument gives a complex value: `acosh` and `asech`
are in their principal forms because the textbook `1/√(u² − 1)` and `−1/(u√(1 − u²))` have the
wrong sign for `u < −1`, and `|u|` is used only for a real argument. `abs` and the step functions
need a real argument (an assumptions query) and ignore their jumps, where there is no derivative.
`factorial`, `binomial`, `gcd`, `lcm`, `mod`, `min`, `max`, `re`, `im`, `conj`, `arg` and the
other builtins have no rule.

### Free of x, and what stays

`constant` and the conditions ask whether a value is free of `x`, a query over the tree. A binder
whose variable is `x` hides it in its body (a lambda, Σ, Π, a limit, a definite integral), while
its bounds and point are outside that scope. A derivative's variable is free in its value, so a
derivative is free of `x` when its expression is; an indefinite integral in `x` never is; a command
left as a call with a variable of its own (a held `solve`) is free only when `x` occurs nowhere in
it. Everything without a rule stays a `Derivative` when it depends on `x`: the builtins without a
table entry, integrals (the Leibniz rule is Phase 4), limits, Π, Σ with a bound in `x`, a derivative
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
- the other **scalar** expressions are kernels: symbols, `π` and `e`, applications (whatever their
  arguments), decimal and complex numbers (`i` too: `i²` folds back to `-1` in the canonical form),
  a power of a number that the canonical form left unfolded (`3^100000000`, never computed), other
  powers (`x^y`, `2^x`), and the unevaluated forms (derivatives, integrals, limits, Σ, Π,
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
   a coefficient that trial division to 10⁵ cannot factor, a cap reached) stays as it is and the
   factorization is incomplete. No step is recorded for it, since nothing changes. `x^100 - 1`
   returns at once as `(x - 1)(x + 1)(x⁹⁸ + x⁹⁶ + … + 1)`, and `x^10000 - 1` in a few tens of
   milliseconds.

The divisors come from trial division to 10⁵; a cofactor left over is taken as prime when it is
below 10¹⁰ or a probable prime (certainty 50).

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
`MatcherPerformanceTest` times 301 matches as one run. Each budget has the typical local time in a
comment beside it, so a reader sees the margin. "At once" in this section means milliseconds on a
developer machine.

## Providers

Package `mathcanvas.engine.provider`: the backend interface (`PLAN.md` §5.2, extension hook 3).
Each heavyweight operation is invoked through a provider, so another implementation (Rings,
Hipparchus, an external process) can be registered for one operation without touching its callers.

- `Operation<O>` is a sealed interface over the typed operations, each a record of its inputs:
  `Gcd(Polynomial a, Polynomial b)` → `Polynomial`, `Factor(Polynomial p)` → `Factorization` and
  `Solve(List<Expr> equations, List<Symbol> vars, MathContext precision)` → `SetExpr`. Later
  operations (integrate, limit, decompositions) are declared as they land. Each has
  `normalise(result)`: the gcd's normal form (`Gcd.normalise`, so a provider's `-3g` becomes `g`'s
  normal form), `Factorization.normalised()`, the canonical form of a set of solutions at the
  precision (each solution canonical, the set sorted and deduplicated); and `outputType()`, the
  result's class, with which a provider's answer is checked.
- `Provider<I, O>` has a kebab-case `name()` and `apply(input, ctx, steps)`, which returns a correct
  result in any equivalent form, or empty to decline. `steps` is a `Steps` sink for the
  algorithm's named steps, `Steps.NONE` when tracing is off; a provider that cannot narrate ignores
  it.
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
`Providers.defaults()` names `algebra.DefaultProviders`, which registers `solve.SolveProvider`, and
`algebra` and `solve` call the registry or the algebra: the four packages depend on each other
inside the module, which JPMS allows. **Static initialisation** stays acyclic: `Context.DEFAULT`
builds `Providers.defaults()`, whose `DefaultProviders.REGISTRY` needs only `Providers.EMPTY` and
the providers' records, and no algebra, provider or solve static field reads `Context.DEFAULT`;
keep it so.

`ProviderSeamTest` (in `…/provider/`) is the Phase 1 acceptance: a deliberately trivial `gcd`
provider, which answers `-7` whenever an input is a non-zero constant, is registered ahead of the
default; the engine routes to it (a counter and the trace's provider event show it) and falls
through to the default otherwise; and on random inputs both providers' results normalise to the
same canonical forms. `SolveSeamTest` does the same for `solve` with a provider that answers linear
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
Otherwise the equations go to the provider operation `Solve` (**Providers**) through `attempt`. The
default provider, `SolveProvider`, runs the methods below; when none applies it records the step
that says why, from the state reached to the unevaluated call, and declines. When every provider
declines the call is returned, with a step `solve.unsolved` if no reason was recorded; a provider
that answers without steps is one step in the rule set `provider` named after it. The unevaluated
call is `solve(e, x)` for one equation and one unknown, `solve({…}, ⟨x, y⟩)` otherwise.

One equation in one unknown goes to `Univariate`; several equations, or several unknowns, to
`LinearSystems` when every equation is linear in the unknowns, and otherwise the call stays
(`solve.not-linear-system`). Polynomial systems are a later phase.

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
numeric scan below.

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
call (`solve.too-many-roots`: periodic equations have general solutions, Phase 6's), and so does
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
session's numerics and angle mode, the errors, the steps); in `…/rule/`, `TraceConcatTest`. The
properties:

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
  are within `10^-32` of the roots found at 60 digits.

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
| `Complex` | two `RealNumeric` parts | imaginary part never `Int 0` |

Every value has one representation, so the records' own `equals` and `hashCode` are value
equality within a kind. A rational with denominator 1 **is** an `Int`, and a complex number with
imaginary part `Int 0` **is** its real part. The canonical constructors validate and
normalise: `new Real` strips trailing zeros, `new Rational` reduces to lowest terms with a
positive denominator and throws only when the reduced denominator is 1 (`new Rational(4, 2)`),
and `new Complex` throws on an `Int 0` imaginary part. The factories also demote
(`Rational.of(4, 2)` is `Int 2`, `Complex.of(x, Int 0)` is `x`). A `Real 0.0` imaginary part
stays, because it is inexact. Complex parts may mix kinds (`1 + 2.0i`).

Constants: `Int.ZERO`, `ONE`, `TWO`, `MINUS_ONE`, `Complex.I`.

### Promotion and contagion

`Int ⊂ Rational ⊂ Real`, each `⊂ Complex`. A binary operation promotes the lower operand,
operates, and demotes the result when it is exactly representable lower (`1/2 + 1/2` is `Int 1`,
`i·i` is `Int -1`). **Inexactness is contagious and never demoted**: any operation with a `Real`
operand returns a `Real`, or a `Complex` with a `Real` part, even when the value is an integer
(`2.5 · 2` is `Real 5.0`). Nothing turns a `Real` into a `Rational` implicitly.

The predicates `isZero`, `isOne` and `isInteger` test the value (`Real 1.0` is one); `isExact`
is false when any part is a `Real`. A rule needs both: `x·1.0` must not become `x`.

### Precision

Operations that can be inexact take a `java.math.MathContext`; exact kinds ignore it, and the
context lives on the operation, never on the number. `add`, `subtract`, `multiply`, `divide`,
`pow` and `sqrt` take one. `negate`, `abs`, `compareTo`, `compareValue`, `signum`, `floor`,
`ceil`, `round`, `truncate`, `re`, `im` and `conj` do not: they are exact on every kind.

A `Real` result is the exact result rounded **once** to the context. When no `Rational` is
involved this is `BigDecimal`'s own operation under the context. With a `Rational` operand
`p/q`, the operation is carried out exactly on fractions and the one final division rounds. So
`0.1 + 0.2` is exactly `0.3` at every precision, and `1/3` is never rounded before it is added.
Past a magnitude gap of 10,000 digits between the terms of a mixed sum, the numerator is rounded
to odd at `p + digits(q) + 3` digits instead of formed exactly. It still rounds as the exact sum
would (Boldo and Melquiond, 2008). Each part of a complex product or quotient is formed exactly
(`pr − qs`, `ps + qr`, and over `r² + s²`) and rounded once, so it stays right under
cancellation; it is exact when its operands are. When `r²` and `s²` differ by more than 10,000
digits, the divisor's norm is rounded to odd too, and the quotient is then faithful (within one
ulp, plus a margin of `10^-2` ulp) rather than rounded once. A rounded divisor can move a quotient
that sits that close to a rounding boundary across it: in UP mode at one digit,
`(1 + i)/(10^5005 + i)` gives `1E-5005`, not `2E-5005`. Among the four operations this far-apart
divisor is the one place a `Real` result is faithful rather than rounded once from the exact
value.

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
  An inexact power (`Real`, or `Complex` with a `Real` part) is the exact power rounded once
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
  integer value is an integer power made inexact, so `(-8)^2.0` is `Real 64`. `0^y` is 0 when
  `Re y > 0` and 1 when `y = 0`; otherwise it throws.
- **Square root** `sqrt(mc)`, the principal root. It is exact for a rational square, and for a
  Gaussian rational square through the half-angle formulas (`sqrt(3+4i)` is `2+i`). A negative
  real gives `Complex(Int 0, √|x|)` (`sqrt(-4)` is `2i`). A `Real` root is
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
  rounded once, cancelling pairs included; quotients by far-apart divisors within their faithful
  bound in every rounding mode; `z.pow(2) == z·z`; faithful roots of rationals; and Pascal's rule
  for `binomial`;
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
- **Power.** `x¹ = x`; `x⁰ = 1`, also for `x = 0`; `1ˣ = 1` (for a number `x`, when it is
  exact: `1^i` is 1, `1^2.5` is `1.0`); `(xᵃ)ⁿ = x^(a·n)` and
  `(x·y)ⁿ = xⁿ·yⁿ` only for an exact integer `n` (the second only when every factor commutes).
  A power of two numbers folds when the result is exact or an operand is already inexact:
  `4^(1/2)` is 2, `(-4)^(1/2)` is `2i`, `2.0^(1/2)` is a `Real`, while `2^(1/2)` and
  `(-8)^(1/3)` stay. Exact operands are probed with `MathContext.UNLIMITED`, under which the tower
  either answers exactly or throws, and a throw leaves the power. An exact integer power folds
  only while the base's bit length (for a rational, the numerator's plus the denominator's)
  times `|n|` is at most `Canonical.MAX_FOLD_BITS` (2²⁰), and
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
pass, not an iteration to a fixpoint; a combination that yields a new sum or product (a
coefficient of 1 on a sum, `(x·y)^(1/2)·(x·y)^(1/2)`) is flattened and the step rerun, which
ends because each rerun removes a level of nesting.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/expr/`: `ExprTest` (validation and the
debug forms), `ExprsTest`, `ExprOrderTest` (Cohen's examples and the mixed-kind rules, and a fixed
list sorted to a literal sequence), `CanonicalTest` (each rule) and `CanonicalPerformanceTest`
(a sum of 10,000 symbols, flat and left-nested, and products of 1,000 powers, against a generous
bound; each takes milliseconds). `ExprArbitraries` is the shared generator of raw expressions,
for later packages too: symbols from `a b x y`, numbers from a small pool, depth up to 4, with
twins such as `e - e`, `e·e⁻¹` and `(eᵃ)ⁿ` frequent; `exactExprs()` has exact numbers and
arithmetic only, `exprs()` every kind. It draws from jqwik's random source directly, because
jqwik's combinators were orders of magnitude slower on these trees, so failures are reported
unshrunk. The properties are:

- `ExprOrderProperties`: consistency with `equals`, antisymmetry and transitivity, on raw and
  canonical trees;
- `CanonicalProperties`: exact soundness at random points through the test-only `TestEvaluator`
  (symbols, `π` and `e` take random exact values; a point where either side is undefined or not
  exact is skipped, since the tower recognises exact powers only in its documented shapes and a
  combined exponent can hide one; the statistics require most samples to be compared), idempotence,
  independence from operand order, and the shape of every node (at least two operands, none of
  the same kind, at most one number and it first, operands sorted, no `x^1` or `x^0`).

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
  integer value is an integer power made inexact, so `(-8)^2.0` is `Real 64`; an exact base keeps
  its exact power, rounded once, up to 100,000 digits, and beyond is rounded at `p + digits(n) + 5`
  digits first, so `(1/3)^1e9` is a faithful `Real` at once. `0^y` is 0 when `Re y > 0` and 1
  when `y = 0`; otherwise it throws.
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
pass over the tree, not an iteration of the whole to a fixpoint; within a sum or a product the
grouping step reruns while a combination yields a new sum or product (a coefficient of 1 on a
sum, `(x·y)^(1/2)·(x·y)^(1/2)`) or leaves two terms with one non-numeric part (`0·M + 0·M`) or
two factors with one base (`(x⁻¹)^(1/2)·(x⁻¹)^(1/2)·x`, whose first group collapses to `x⁻¹`).
It ends because each rerun merges groups or removes a level of nesting.

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

A pattern is written in the language and compiled from an expression by `Pattern.of(e)`
(`Pattern.parse` follows with the parser). A symbol whose name ends in `_` is a **pattern
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

- **Sums and products** match commutatively; associativity enters only through sequence
  variables. A pattern variable binds exactly one term or factor, and the sequence variable binds
  the rest. The subject's terms or factors are the candidates; a subject of another kind counts as its only operand, which is how `a_·x_`
  matches `x` and `x_ + rest___` matches `y`. Each pattern operand, left to right, takes a
  distinct candidate, trying the candidates in canonical order and then, for an optional variable,
  its default. The sequence variable takes what is left; with no sequence variable nothing may be
  left, so `x_ + y_` does not match `a + b + c`, and `(a_. + b_. x_)^m_.` does not match
  `(1 + y + 2x)`. `all` therefore lists `x_ + y_` against `a + b` as `{x_ = a, y_ = b}` then
  `{x_ = b, y_ = a}`.
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
required, and a pattern without a sequence variable is rejected at once when the subject has
more operands than it. A literal operand, or a variable already bound, has one possible
candidate and is located by binary search in the sorted candidates; every other operand is tried
only on candidates of a kind it can match (a power pattern on powers, `f(…)` on applications of
`f` with a fitting arity). A product of twenty factors against a three-operand pattern matches in
well under a millisecond, and a thousand-term sum against `a_ + rest___` in under five
milliseconds (`MatcherPerformanceTest`). The leftover operands of a sequence variable are counted
before anything is built, and its combination is built only when a binding is produced. The weak
spot is several **unconstrained** variables over a long subject when the match then fails: `x_ +
y_ + rest___` against a sum of a thousand terms with a condition that rejects every match tries
about 10⁶ assignments, each building a 998-term `rest___`, and takes about two seconds. Rules keep
such patterns guarded by a literal, a kind or a condition on the first variable.

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
right-hand side. `RuleSet(name, rules)` is ordered by priority.

### The rewriter

`Rewriter.rewrite(e, ruleSet, context)` returns the result and its trace. `Context` holds the
assumptions, the precision (`MathContext`, also used to canonicalise), whether to trace, and the
rewrite cap; `Context.DEFAULT` is no assumptions, 34 digits, no trace, 10,000 rewrites.

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

The rewrite after the cap's last allowed one throws `RewriteLimitException`, naming the rule set,
the rule and its path: a rule set that can loop is a bug.

**Binders.** Inside the body of a binder (a lambda's body, the expression of a derivative or
limit, an integrand, the body of a Σ or Π) the facts about the symbols it binds are dropped
(`Assumptions.without`), because there the symbol is the binder's own: under `x ≥ 0`,
`∫₋₁¹ √(x²) dx`, `Σ[x = -3, 3] √(x²)` and `x -> √(x²)` keep their `√(x²)`. The bounds of a Σ, Π
or definite integral and a limit's point are outside that scope and keep the outer facts. Taking
the range of a definite integral or sum as a fact about its variable inside the body is a later
refinement.

### The trace

With tracing on, each rewrite is a `Rewrite(before, after, ruleSet, rule, path, bindings)`:
`before` and `after` are the subexpressions at `path`, a `Path` of operand indices from the root.
A path addresses the tree as it stood when the rule fired: operands are rewritten before their
parent is canonicalised, so the ancestors are rebuilt raw around them, and each canonicalisation of
a rebuilt node is recorded as a `Trace.Normalisation` between the rewrites. The trace stores only
these events and the initial expression; `roots()` replays them once to give the whole expression
after each rewrite, sharing every unchanged subtree, and `Canonical.of` of a root is its display
form. With tracing off the result carries `Trace.NONE` and nothing is allocated per rewrite.

### Narration

`Narrator.narrate(trace, context)` turns a trace into `Step(text, before, after, rewrites)`. Each
curated operation brings its own narrator, which groups its rewrites. `DefaultNarrator` gives one
step per rewrite, its text from the bundle `mathcanvas.engine.rule.narration` (in the engine's
resources, root locale): the key is `<rule set>.<rule>`, the value a `MessageFormat` pattern whose
arguments `{0}`, `{1}`, … are the rule's bound variables **in alphabetical order of their names**,
printed. A rule with no key is narrated by its name. Until the printers land the default narrator
prints values in their debug form.

### Tests

In `engine/src/test/java/mathcanvas/engine/match/`: `MatcherTest` (every construct, the order of
`all`, pruning, compile errors), `BindingsTest` (substitution and splicing), `OperandsTest`, and
`MatcherPerformanceTest` (the budgets above). `MatcherProperties`: an instance of a linear,
unambiguous pattern rebinds its values; argument sequences rebind their arguments; `all` has no
duplicates and starts with `match`; combined sequences are canonical; matching random patterns
(the generator's symbols turned into variables) never throws. In `rule/`: `RewriterTest` (order,
restart, bound positions, binder scope, normalisation, the cap, assumptions, paths),
`TraceProperties` (the trace replays to the result, with and without tracing) and `NarratorTest`.

`engine/src/test/java/mathcanvas/engine/testing/` holds the tooling every rule set's tests use:

- `NumericEvaluator.eval(e, point, mc)` evaluates over the number tower, exact where the tower is
  exact and otherwise at `mc`, through the elementary functions in radians, and is empty where the
  value is undefined or a power would be formed with more than 4,000 bits (a base's digits times
  its exponent's numerator), which is no useful sample point.
- `Soundness.points(symbols, assumptions)` draws points within the assumptions: integers in their
  window and parity, rationals within their bounds for real symbols, Gaussian rationals for complex
  ones. `Soundness.check(before, after, points)` asserts agreement where both are defined, within
  `1e-20` relative for exact expressions and `1e-12` when either holds an inexact number, with an
  absolute floor of `1e-25`, and returns how many points it compared.
- `TerminationProbe.run(ruleSet, exprs, context)` fails on the first expression that exceeds the
  cap and reports the most rewrites taken.

`FixtureRules` encodes `PLAN.md`'s acceptance pairs until the radical and exp-log rule sets carry
them (`FixturePairsTest`), and `FixtureProperties` checks it for soundness and termination under
random assumptions.

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
and those recorded about the symbol: bounds that cross or meet with a strict end, a point bound at
0 with `x != 0`, opposite parities, and an integer symbol whose window, tightened to integers and
to its parity, holds no value (`x ∈ ℤ`, `0 < x < 1`).

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
- An expression whose sign set is empty, undefined wherever the facts hold (`1/x` under `x ≥ 0`
  and `x ≤ 0`), answers unknown to every sign and zero query, and so does every sum, product,
  power or `abs` containing it (`y/x`, `1/x + 1`).
- Everything else is unknown.

### Tests

`AssumptionsTest` in `engine/src/test/java/mathcanvas/engine/assume/` covers every accepted and
rejected form, the composition and the contradictions, and each inference rule with known and
unknown answers. `InferenceProperties` (in `testing/`) checks every known answer against the values
of random expressions at points within random assumptions.

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
printed with the Unicode printer's display form (**Printing**). A rule with no key is narrated by
its name. Another bundle or printer can be passed to the record's constructor.

### Tests

In `engine/src/test/java/mathcanvas/engine/match/`: `MatcherTest` (every construct, the order of
`all`, pruning, compile errors), `BindingsTest` (substitution and splicing), `OperandsTest`,
`MatcherPerformanceTest` (the budgets above) and `PatternParseTest` (the fixed cases written as
text). `MatcherProperties`, whose subjects are asserted to be canonical fixpoints: an instance of
a linear, unambiguous pattern rebinds its values; argument sequences rebind their arguments; `all` has no
duplicates and starts with `match`; combined sequences are canonical; matching random patterns
(the generator's symbols turned into variables) never throws. In `rule/`: `RewriterTest` (order,
restart, bound positions, binder scope, normalisation, the cap, assumptions, paths),
`TraceProperties` (the trace replays to the result, with and without tracing) and `NarratorTest`
(the bundle's text, the fallback, the Unicode printer).

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

`FixtureRules` encodes `PLAN.md`'s acceptance pairs, written in the language, until the radical
and exp-log rule sets carry them (`FixturePairsTest`, from trees and from text), and `FixtureProperties` checks it for soundness and termination under
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
and those recorded about the symbol, printed in Unicode (`x > 0 contradicts [x < 0]`): bounds that cross or meet with a strict end, a point bound at
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
`Lexer.MAX_DIGITS` (100,000) characters is "number out of range"; every digit up to that bound is
kept.

Tokens follow §3: longest match, the Unicode aliases, identifiers with subscript runs normalised
to `_` and ASCII, superscript powers as one token carrying their integer, and `in` as the
membership token. Reserved words stay identifiers, so a message names the word as typed. The
lexer counts open brackets and emits a newline token only outside them. The derivative notation is
one token carrying its variable and order. It commits once its head (`d/d` + name, `d^n/d` + name,
or `dⁿ/d` + name) has been read; after that, anything but the exact shape is the shape error.

### The parser

`Parser.parseExpression(text, names)` reads one expression; `Parser.parseScript(text, names)`
reads statements (`Statement.Definition`, `Statement.Expression`, each marked quiet when ended
by `;`, each with the `Position` of its first token as `start()`: line, column and UTF-16 offset). `Names` says which names are user-defined functions, and is consulted for every
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
`Printer` singleton.

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

The ASCII and Unicode printers share one implementation with a style flag; LaTeX has its own.

**Display.** `Display.of(e)` turns a canonical value into the raw tree whose faithful printing is
the conventional notation, and `Printer.display(e)` is `print(Display.of(e))`:

- **Sums** are in descending total degree: the sum of the numeric exponents of the symbols in a
  term, a symbolic exponent, a constant, a number or an application counting 0. Ties are in
  canonical order, and the numeric terms come last: `x³ + 3x² + 3x + 1`,
  `x² cos(x) + 2x sin(x)`, `2πk + π/6`, `-2 sin(x) + 3`. The first term carries its sign in its
  coefficient (`-2x² + 3x - 1`); a later term with a negative coefficient becomes a subtraction. A
  complex number is split into its real and imaginary terms, its real part left out when it is
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

**Round trip.** For a canonical `e`, `Canonical.of(parse(p.display(e)))` equals `e` for the ASCII
and Unicode printers, in an environment where every function applied is defined. The exceptions
are listed in `docs/language.md` §15.4. For a raw tree, `parse(p.print(e))` is `e` with each
number in its typed shape.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/builtin/`, `…/parse/` and `…/print/`:

- `BuiltinsTest`;
- `LexerTest`: tokens, longest match, the derivative shapes, newlines, NFC and positions;
- `ParserTest`: the raw shapes, statements, the environment, error order, and 10,000-term chains.
  It runs every nesting construct at the cap and one level past it, and prints, canonicalises and
  hashes the deepest trees;
- `PrinterTest`: every row of §15.1 in all three printers, and display order, parentheses,
  juxtaposition and integrals;
- `LanguageCorpusTest`, which runs `src/test/resources/mathcanvas/engine/parse/language.corpus`.
  That file holds every example of `docs/language.md`, with its parse form or its error message
  and position, a `# §N` line before each section. Its header gives the format, and its parse form
  comes from the test-only `ParseFormPrinter`, written independently of the printers.

The properties are:

- `RoundTripProperties`: the round trip on canonical trees from `ExprArbitraries` and the faithful
  round trip on raw ones, for both printers. The raw trees are compared with `TypedShape`, the
  tree with each number in its typed shape. Each canonical sample is also checked to be a fixed
  point of `Canonical.of`, since the round trip is stated for canonical values;
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
hold the right side as parsed, since definitions are delayed. `Environment(definitions,
assumptions, settings)` is immutable: the definitions are a sorted map by name, so nothing hashes
an expression. `with(name, definition)` binds or rebinds (a `Value` whose body is a lambda is stored
as the `Function` it denotes, so `f := x -> x^2` is `f(x) := x^2`), `without(name)` unbinds,
`withAssumptions` and `withSettings` replace, `isFunction(name)` answers the parser, and `names()` is
the parser's `Names` view.

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
  function is not a factor"; a set, list or truth-value operand is "sets carry no arithmetic"
  ("lists …", "truth values …").
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

Then `Simplifier.simplify` runs the standard list once on the value, in a `Context` built per
statement from the environment: its assumptions, the session's `MathContext`, the trace switch and
a cap of 10,000 rewrites. A `RewriteLimitException` becomes the error "simplification did not
finish". Evaluation and simplification run once each, in that order: a builtin whose argument
becomes a number only by a rule stays unevaluated. This is to be revisited when the first rule sets
land.

**Binders.** While a binder's scope is evaluated its variable is a bound name: a bound symbol is
neither looked up nor taken from a frame, so after `x := 3`, `solve(x^2 = 4, x)` receives the
symbol, and the nodes keep their identity. A static check, made once before the scope is
evaluated, decides whether the variable `v` could capture a name from outside the scope. It does
when
(i) a value of the current frames holds a symbol named `v` (the names of each frame value are
collected once, by identity), or (ii) a definition the scope reaches holds one: the scope's
symbols and heads, the names in the raw bodies of the definitions they name (without a function's
own parameters), and theirs, transitively, through the environment, memoised per statement; a
cycle among the definitions is harmless here and remains the evaluation-time error. Lambda bodies
inside the scope count. A name bound to a value definition is left out of (ii), since it is
expanded and never free (its own reach stays in), so `k := 7; b := k^2; sum(k b, k, 1, 3)` is
`∑[k = 1, 3] 49k`, unless it occurs in the body of a lambda (in the scope or in a body reached),
which is held and keeps the name free: `k := 7; f(t) := (x -> x + k); sum(f(1), k, 1, 3)` is
`∑[k₁ = 1, 3] (x → k + x)`. The check is conservative: it looks at every occurrence of `v`, free or bound.
When it finds one, the binder is renamed (`Substitution.renameBinder`) to `base_k`, the base being
`v` without a `_digits` suffix, for the least `k ≥ 1` whose name occurs in neither the binder, the
names of the enclosing binders, the environment's definitions, the frame values nor the
definitions the scope reaches, so it cannot capture either (a variable `k_1` becomes `k_2`);
nothing is evaluated twice. With `b := k^2`, `sum(k b, k, 1, 3)` is
`∑[k₁ = 1, 3] k² k₁`; with `f(t) := t + k`, `sum(f(1), k, 1, 3)` is `∑[k₁ = 1, 3] (k + 1)`;
`f(t) := sum(t k, k, 1, 3); f(k)` is `∑[k₁ = 1, 3] k k₁`; and nested binders of one name over
`a := k` become `k₁`, `k₂`, … in turn. The binders are a lambda, the unevaluated forms and the
commands with a bound variable, whose scope is their first two arguments.

**Limits.** At most `Evaluator.MAX_DEPTH` (1,000) nested evaluation steps, counting descents into
operands and expansions together, and `MAX_EXPANSIONS` (200) definitions and calls being expanded
at once ("expression nested too deeply", "definitions nested too deeply"); and a value is at most
`Parser.MAX_NESTING` (200) levels deep ("expression nested too deeply"), the bound every later walk
of a tree relies on. A definition's memoised value and the statement's value have at most
`MAX_NODES` (1,000,000) nodes, a shared subtree counted at each occurrence as every walk will
("result too large"), so `h_k := [h_{k−1}, h_{k−1}]` up to `h30` is an error, not an
`OutOfMemoryError`; each is counted once, and a call's value is not counted, so passing a large
value through calls costs nothing. An `ArithmeticException` from the tower or the canonical form
(`1e999999999` cubed) is "number out of range at this precision". The tests run these limits on
the 1 MB test stack.

### Functions on numbers

`Functions` evaluates a builtin function whose arguments are all numbers; with any other argument
the call stays. When every argument is exact, the call evaluates only to an exact result:

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
`asin` of `1/x`, `log(x, b)`, `log10`, `log2` and `atan2`. The six inverse hyperbolic functions are logarithmic formulas (`asinh x = ln(x + √(x² + 1))`,
`acosh` in Kahan's form and `i·acos(x)` on `[-1, 1)`, `atanh x` and `acoth x` from
`½ ln |(1 + x)/(1 − x)|`, `asech x = ln((1 + √(1 − x²))/x)` on `(0, 1]`,
`acsch x = sign(x)·ln((1 + √(1 + x²))/|x|)`) whose differences with 1 are formed exactly on the
decimal argument, evaluated at rising working precisions until two evaluations agree to a hundredth
of an ulp, as the tower's own functions are, so they are faithful near the points of cancellation
(`acoth(1 + 10^-30)`). Two zero evaluations are accepted only where the value is zero by the
argument (`asinh(0)`, `atanh(0)`, `acosh(1)`, `asech(1)`); elsewhere a zero is a cancellation not
yet resolved and the precision keeps rising, so `acosh(1 + 10^-140)` is `1.414…e-70`. Past the
precision's reach the leading term is the value: `asinh` and `atanh` of a tiny `x` are `x`, `acoth`
and `acsch` of a huge one `1/x`. A complex argument takes the reciprocal forms through `atanh`,
`acosh` and `asinh`. A zero argument of a logarithm or a reciprocal is `undefined`; any other
failure of the tower (an argument too large to reduce, a result out of range) is the error "number
out of range at this precision".

**Outside the real domain.** A real argument outside an inverse function's real domain gives a
complex value, by counterclockwise continuity (`PLAN.md` §11): the value of the principal formula,
with the principal `log` and `sqrt`, at the real point, which is also Mathematica's value.
`asin z = −i·log(iz + √(1 − z²))`, `acos z = π/2 − asin z`, `acosh z = log(z + √(z − 1)·√(z + 1))`,
`atanh z = ½(log(1 + z) − log(1 − z))`, `acoth z = atanh(1/z)`, `asech z = acosh(1/z)`,
`acsch z = asinh(1/z)`. So `asin(2.0)` is `π/2 − 1.3169…i`, `acos(2.0)` is `1.3169…i`, `log(−1.0)`
is `πi`; `acosh(0.5)` is `1.0471…i` and `acosh(−2.0)` is `1.3169… + πi`; `asech(2.0)` is
`1.0471…i`; `atanh(x)` has the imaginary part `−π/2` for `x > 1` and `+π/2` for `x < −1`
(`atanh(2.0)` is `0.5493… − 1.5707…i`), and `acoth(x)` likewise by the sign of `x` inside
`(−1, 1)` (`acoth(0.5)` is `0.5493… − 1.5707…i`, `acoth(−0.5)` is `−0.5493… + 1.5707…i`). A
property checks each inverse hyperbolic function at random real points outside its domain against
its formula through the tower's complex `log` and `sqrt`.

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
  error.
- `cross` multiplies two scalars and crosses two 3-vectors ("× needs two 3-vectors" otherwise);
  `dot` of two vectors of one length is their dot product, of two symbols it stays; `nth(c, k)` takes
  the `k`-th item of a list or vector or row of a matrix, checked; `length` counts the items of a
  list, vector or set, or a matrix's rows.

The operands of a node are reduced first, since the canonical form can build new arithmetic below
the top (`(M^a)^n` as `M^(a·n)`).

### Commands

| Command | Evaluation |
|---|---|
| `solve diff integrate limit sum product taylor` | the scope evaluated with the variables bound, the bounds, point and order evaluated, `left`/`right` kept; the call is built again through `TypedNodes`, so arguments that come to fit give the typed node (`n := 2; diff(x^2, x, n)` is the `Derivative`); the node or application is returned until the command's brief lands |
| `expand factor` | the argument evaluated; the application is returned |
| `collect` | the first argument evaluated, the variable held |
| `simplify steps` | the value of the argument; `steps` at the top of a statement turns the trace on |
| `subs(e, x = v, …)` | the values evaluated, substituted together into the held `e`, then evaluated; a pair that is not `name = value` is "subs needs name = value" |
| `numeric exact` | as above |
| `assumptions()` | the relations in force, as a set |
| `assume clear` | statements only (**The session**); in an expression, "assume works only as a statement" |
| `help`, and `det rref eigenvalues grad slider` | "help is not available yet", "rref is not available yet" |

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
probes and require that none gives one. `run(Script)` runs every statement in order, past failures. `setPrecision` and
`setAngleMode` change the settings, which the language has no syntax for; `setTracing(true)`
evaluates every expression statement with its trace, as `steps(…)` does one (the CLI's `--steps`);
`names()` is what the next statement is parsed with.

### The simplifier

`Simplifier.standard()` is the list of rule sets every value is simplified with, empty until the
rule sets land, so the value is the canonical form. `Simplifier.simplify(e, context)` runs the list
as one combined rule set through the `Rewriter`, so the rules of every set share one fixpoint and
one cap; a rule name must be unique across the list, and each rewrite in the trace names the set its
rule came from.

### Tests

The tests are in `engine/src/test/java/mathcanvas/engine/eval/` and `…/simplify/`, most through
the test helper `Run`, which runs a script in a new session:

- `EnvironmentTest`, `SimplifierTest`, `SubstitutionTest`, `SessionTest`;
- `EvaluatorTest`: definitions, cycles, calls, relations, membership, brackets, binders and capture,
  commands, the errors and the node they carry;
- `FunctionsTest`: every function on exact, inexact and symbolic arguments, undefined values, the
  angle modes;
- `NumericExactTest`, `LinearAlgebraTest`, `EvaluationExamplesTest` (§17.3 and §14);
- `EvaluationDepthTest`: the deepest parse, the limits, a 10,000-term sum, 30 and 120 nested
  binders of one name (each under a second), and 150 identity calls on a value of 900,000 nodes
  (under 200 ms more than the value alone), on the 1 MB stack.

The properties are:

- `EvaluationProperties`: evaluation is deterministic and idempotent on values, over every kind of
  expression from `ExprArbitraries`, with its heads defined as functions; a value passed through
  an identity function is the value, in every angle mode; and evaluation agrees with substituting
  the raw bodies of the definitions directly, over binder-free expressions;
- `NumericProperties`: the evaluation of builtin functions is sound at random points
  (`Soundness.check`); `numeric` agrees with `NumericEvaluator` within `10^-15` where the value is
  well conditioned (the evaluator agrees with itself at 44, 60 and 80 digits); the six inverse
  hyperbolic functions are within one ulp of their logarithmic formulas at 150 digits or more, at
  full-precision arguments `±(1 ± d·10^-k)` and `±d·10^±k` for `k` up to 200, and outside their
  real domains within one ulp of their principal formulas through the tower's complex `log` and
  `sqrt`; `exact` undoes `numeric` on rationals with terminating expansions;
- `LinearAlgebraProperties`: sums, multiples, dot and cross products, matrix products and powers
  against element-wise loops on the tower's numbers.

# Engine

How the symbolic engine in `engine/` works, one section per area as it lands. Read `CLAUDE.md`
first; this file assumes its **Architecture rules**. The engine is pure Java: no JavaFX, no
clock, no randomness, no I/O.

## Numbers

Package `mathcanvas.engine.number`. The expression tree wraps these values in nodes; this
package knows nothing of expressions, symbols (`π`, `e` and `∞` are symbols, not numbers),
parsing or printing (`toString` is for debugging only).

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

# Polynomial algebra in the language (docs/language.md §14.9): expand, factor, collect, cancel,
# apart, gcd and lcm of polynomials, and the structural numerator and denominator.
# A golden corpus file: docs/reference/cli.md describes the format.

# expand
expand((x + 1)^3)
# => x³ + 3x² + 3x + 1
expand((x + y + z)^2)
# => x² + 2x y + y² + 2x z + 2y z + z²
expand((a + b)^2 (a - b))
# => a³ + a² b - a b² - b³
expand((x/2 + 1/3)^2)
# => x²/4 + x/3 + 1/9
expand(sin((x + 1)^2))
# => sin(x² + 2x + 1)
expand((sin(x) + 1)^2)
# => 2 sin(x) + sin(x)² + 1
expand((x + 1)^2/(x - 1))
# => x²/(x - 1) + 2x/(x - 1) + 1/(x - 1)
a := 2; expand((x + a)^2)
# => x² + 4x + 4
clear(a)
expand((x + y)^100000)
# => (x + y)¹⁰⁰⁰⁰⁰
expand({(x + 1)^2, 2})
# => {2, x² + 2x + 1}
expand([[(x + 1)^2, 1], [2, 3]])
# => [[x² + 2x + 1, 1], [2, 3]]
expand(∞ (x + 1))
# => expand(∞·(x + 1))

# factor
factor(x^4 - 1)
# => (x - 1)(x + 1)(x² + 1)
factor(x^2 - 5x + 6)
# => (x - 3)(x - 2)
factor(6x^2 - x - 2)
# => (2x + 1)(3x - 2)
factor(x^3 - x)
# => x·(x - 1)(x + 1)
factor(2x^2 + 4x + 2)
# => 2(x + 1)²
factor(x^2/2 - 1/2)
# => (x - 1)(x + 1)/2
factor(1 - x)
# => -(x - 1)
factor(x^2 + 1)
# => x² + 1
factor(x^4 + 4)
# => (x² - 2x + 2)(x² + 2x + 2)
factor(x^6 - 1)
# => (x - 1)(x + 1)(x² + x + 1)(x² - x + 1)
factor(x^4 - 10x^2 + 1)
# => x⁴ - 10x² + 1
factor(x^2 - y^2)
# => (x + y)(x - y)
factor(x^3 + y^3)
# => (x + y)(x² - x y + y²)
factor(x y + x + y + 1)
# => (x + 1)(y + 1)
factor(x^2 - π^2)
# => (x + π)(x - π)
factor(sin(x)^2 - 1)
# => (sin(x) - 1)(sin(x) + 1)
factor((x^2 - 1)/(x^2 + 2x + 1))
# => (x - 1)/(x + 1)
factor(1/x + 1/y)
# => (x + y)/(x y)
factor((x + 1)(x + 2))
# => (x + 1)(x + 2)
factor(x^2 - 1 = 0)
# => (x - 1)(x + 1) = 0
factor([x^2 - 1, 2x + 2])
# => ⟨(x - 1)(x + 1), 2(x + 1)⟩
# The incomplete case: x² + x + 1 divides x²⁰ + x + 1, but the part is past Kronecker's degree
# bound and has no rational root, so it is shown as it is.
factor(x^20 + x + 1)
# => x²⁰ + x + 1
factor(∞ x)
# => factor(∞x)
factor(x^10001 - 1)
# !! the polynomial's degree exceeds 10,000
steps(factor(x^4 - 1))
# steps:
#   step 1: 1 is a root, so x - 1 is a factor of x⁴ - 1
#   = (x - 1)(x³ + x² + x + 1)
#   step 2: -1 is a root, so x + 1 is a factor of (x - 1)(x³ + x² + x + 1)
#   = (x - 1)(x + 1)(x² + 1)
# => (x - 1)(x + 1)(x² + 1)

# collect
collect(a x + b x + x^2 + c, x)
# => x² + c + x·(a + b)
collect((x + y)^2, x)
# => x² + 2x y + y²
collect(a sin(x)^2 + sin(x)^2 + 3, sin(x))
# => (a + 1) sin(x)² + 3
collect(∞ x, x)
# => collect(∞x, x)

# cancel
cancel((x^2 - 1)/(x + 1))
# => x - 1
cancel((x^2 - y^2)/(2x - 2y))
# => x/2 + y/2
cancel(x/x)
# => 1
cancel(1/x + 1/y)
# => (x + y)/(x y)
cancel((x - 1)/(√x - 1))
# => (x - 1)/(√x - 1)
cancel((x^2 - 1)/(x - 1) = 0)
# => x + 1 = 0
cancel(∞ x)
# => cancel(∞x)
cancel((3x + 6)^-1000000000)
# !! the unit's power is too large to compute

# apart
apart(1/(x^2 - 1), x)
# => 1/(2(x - 1)) - 1/(2(x + 1))
apart((x^3 + 1)/(x^2 - 3x + 2), x)
# => x + 9/(x - 2) - 2/(x - 1) + 3
apart(1/(x^2 (x + 1)), x)
# => 1/(x + 1) - 1/x + 1/x²
apart((2x + 3)/(x + 1)^3, x)
# => 1/(x + 1)³ + 2/(x + 1)²
apart(1/(x (x^2 + 1)), x)
# => -x/(x² + 1) + 1/x
apart(1/(x y), x)
# => 1/(x y)
apart(1/x, 2)
# !! apart needs a variable

# gcd and lcm
gcd(x^2 - 1, x^2 + 2x + 1)
# => x + 1
gcd(2x + 2, 4x + 4)
# => 2x + 2
gcd(x/2, x/3)
# => x/6
gcd(x^2 - y^2, x^2 + 2x y + y^2)
# => x + y
gcd(12, 18)
# => 6
lcm(x^2 - 1, x + 1)
# => x² - 1
lcm(4, 6)
# => 12
lcm(x, y)
# => x y

# numerator and denominator
numerator(2x/(y z^2))
# => 2x
denominator(2x/(y z^2))
# => y z²
numerator(x/2)
# => x
denominator(x/2)
# => 2
numerator(1/x^2)
# => 1
denominator(1/x^2)
# => x²
numerator(1/x + 1/y)
# => 1/x + 1/y
denominator(1/x + 1/y)
# => 1
numerator(3/4)
# => 3
numerator({1, 2})
# => numerator({1, 2})

# decimals are kernels
gcd(x^2 - 1.0, x - 1)
# => 1
factor(x^2 - 2.0)
# => x² - 2.0

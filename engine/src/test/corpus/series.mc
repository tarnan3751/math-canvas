# Taylor polynomials, sums and products (docs/language.md §14.14 and §10.4;
# docs/reference/engine.md, Series and sums).
# A golden corpus file: docs/reference/cli.md describes the format.

# Taylor polynomials, in the printer's order
taylor(e^x, x, 0, 4)
# => x⁴/24 + x³/6 + x²/2 + x + 1
taylor(sin(x), x, 0, 7)
# => -x⁷/5040 + x⁵/120 - x³/6 + x
taylor(cos(x), x, 0, 6)
# => -x⁶/720 + x⁴/24 - x²/2 + 1
taylor(1/(1 - x), x, 0, 5)
# => x⁵ + x⁴ + x³ + x² + x + 1
taylor(log(x), x, 1, 3)
# => x - (x - 1)²/2 + (x - 1)³/3 - 1
taylor(e^x, x, 1, 2)
# => e + e·(x - 1) + e·(x - 1)²/2
taylor(sqrt(x), x, 4, 2)
# => (x - 4)/4 - (x - 4)²/64 + 2
taylor(atan(x), x, 0, 5)
# => x⁵/5 - x³/3 + x
taylor(x^3 - 2x, x, 0, 0)
# => 0
taylor(e^x, x, 0, n)
# => taylor(e^x, x, 0, n)
steps(taylor(cos(x), x, 0, 4))
# steps:
#   step 1: The derivatives at x = 0 are 1 (order 0), 0 (order 1), -1 (order 2), 0 (order 3), 1 (order 4); the Taylor polynomial is x⁴/24 - x²/2 + 1
#   = x⁴/24 - x²/2 + 1
# => x⁴/24 - x²/2 + 1
T(x) := taylor(e^x, x, 0, 3)
# => T(x) := taylor(e^x, x, 0, 3)
T(1)
# => 8/3
taylor(sqrt(x), x, 0, 2)
# !! the derivative of order 1 could not be shown continuous at x = 0
taylor(abs(x), x, 0, 1)
# !! the derivative of order 1 could not be shown continuous at x = 0
taylor(x^(5/2), x, 0, 2)
# !! the derivative of order 1 could not be shown continuous at x = 0
taylor(sin(x)/x, x, 0, 2)
# !! the derivative of order 0 is undefined at x = 0
taylor(e^x, x, 0, 1/2)
# !! taylor's order must be a non-negative integer

# Sums with integer bounds, term by term
Σ[k=1,4] 1/k
# => 25/12
sum(k^2, k, 1, 10)
# => 385
sum(x^k, k, 0, 3)
# => x³ + x² + x + 1
sum(factorial(k), k, 0, 4)
# => 34
sum(taylor(e^x, x, 0, k), k, 0, 2)
# => x²/2 + 2x + 3
sum(sum(j, j, 1, k), k, 1, 3)
# => 10
sum(k, k, 1, 0)
# => 0
steps(sum(k^2, k, 1, 3))
# steps:
#   step 1: Add the terms: ∑[k = 1, 3] k² = 14
#   = 14
# => 14

# Closed forms
Σ[k=1,n] k
# => n·(n + 1)/2
sum(k^2, k, 1, n)
# => n·(n + 1)(2n + 1)/6
sum(k^3, k, 1, n)
# => n² (n + 1)²/4
sum(k, k, a, b)
# => -(a + b)(a - b - 1)/2
sum(2^k, k, 0, n)
# => 2^(n + 1) - 1
sum(r^k, k, 0, n)
# => (r^(n + 1) - 1)/(r - 1)
sum(k*2^k, k, 0, n)
# => 2(n·2^(n + 1) - (n + 1)·2^n + 1)
sum(1/(k(k + 1)), k, 1, n)
# => -1/(n + 1) + 1
sum(log((k + 1)/k), k, 1, n)
# => log(n + 1)
sum(binomial(n, k), k, 0, n)
# => 2^n
sum(binomial(n, k) x^k, k, 0, n)
# => (x + 1)^n
sum(k, k, 1, 1000000)
# => 500000500000
sum(log(e^k), k, 1, n)
# => n·(n + 1)/2
sum(k^30, k, 1, n)
# => n³¹/31 + n³⁰/2 + 5n²⁹/2 - 203n²⁷/6 + 1131n²⁵/2 - 16965n²³/2 + 216775n²¹/2 - 2304485n¹⁹/2 + 19959975n¹⁷/2 - 137514723n¹⁵/2 + 731482225n¹³/2 - 31795091601n¹¹/22 + 8053785025n⁹/2 - 102818379585n⁷/14 + 15626519181n⁵/2 - 23749461029n³/6 + 8615841276005n/14322
steps(sum(k, k, 1, n))
# steps:
#   step 1: Sum the powers of the index by Faulhaber's formula: ∑[k = 1, n] k = n·(n + 1)/2
#   = n·(n + 1)/2
# => n·(n + 1)/2
steps(sum(r^k, k, 0, n))
# steps:
#   step 1: Sum the geometric series of ratio r: ∑[k = 0, n] r^k = (r^(n + 1) - 1)/(r - 1), for r ≠ 1
#   = (r^(n + 1) - 1)/(r - 1)
# => (r^(n + 1) - 1)/(r - 1)

# Infinite sums
sum((1/2)^k, k, 0, inf)
# => 2
sum(k*(1/2)^k, k, 1, inf)
# => 2
sum(1/(k(k + 1)), k, 1, inf)
# => 1
sum(1/(k^2 - 1), k, 2, inf)
# => 3/4
sum(1/k^2, k, 1, inf)
# => π²/6
sum(1/k^4, k, 1, inf)
# => π⁴/90
sum(1/k^6, k, 1, inf)
# => π⁶/945
sum(1/k, k, 1, inf)
# => ∞
sum(2^k, k, 0, inf)
# => ∞
sum((-1)^k, k, 0, inf)
# => undefined
steps(sum((1/2)^k, k, 0, inf))
# steps:
#   step 1: Sum the geometric series of ratio 1/2: ∑[k = 0, ∞] (1/2)^k = 2
#   = 2
# => 2

# Products
product(k, k, 1, n)
# => n!
Π[k=1,n] 2k
# => 2^n n!
product(2, k, 1, n)
# => 2^n
product(2^k, k, 1, n)
# => 2^(n·(n + 1)/2)
product(k, k, 1, 5)
# => 120

# What stays, and why
sum(1/k, k, 1, n)
# => ∑[k = 1, n] 1/k
sum(1/k^3, k, 1, inf)
# => ∑[k = 1, ∞] 1/k³
steps(sum(1/k, k, 1, n))
# steps:
#   step 1: No closed form is known for ∑[k = 1, n] 1/k; it stays
#   = ∑[k = 1, n] 1/k
# => ∑[k = 1, n] 1/k
steps(sum(1/k, k, 1, 20000))
# steps:
#   step 1: ∑[k = 1, 20000] 1/k has more than 10,000 terms to add one by one and no closed form is known; it stays
#   = ∑[k = 1, 20000] 1/k
# => ∑[k = 1, 20000] 1/k
steps(sum(k, k, 1, 1/2))
# steps:
#   step 1: The bounds of ∑[k = 1, 1/2] k are not integers with the upper at least the lower minus one; it stays
#   = ∑[k = 1, 1/2] k
# => ∑[k = 1, 1/2] k

# Ratios known from the facts
assume(r > 0, r < 1, s > 1); sum(k r^k, k, 1, inf)
# => r/(-r + 1)²
sum(r^(2k), k, 0, inf)
# => 1/(-r² + 1)
sum(s^k, k, 0, inf)
# => ∞

# The standard simplifier: the basic, exp-log, radical and trig rule sets
# (docs/reference/engine.md, Simplification; docs/language.md §14.8).
# Facts are recorded with assume and hold for the rest of the file, so each fact is about a
# symbol of its own: p, q > 0; u >= 0; w <= 0; n even; m odd; z complex.
# A golden corpus file: docs/reference/cli.md describes the format.

assume(p > 0, q > 0, u >= 0, w <= 0, mod(n, 2) = 0, mod(m, 2) = 1, z ∈ ℂ)

# PLAN §6: assumptions change results where they should
sqrt(8)
# => 2√2
sqrt(x^2)
# => |x|
sqrt(u^2)
# => u
log(a b)
# => log(a b)
log(p q)
# => log(p) + log(q)
log(p b)
# => log(b) + log(p)
sqrt(z^2)
# => √(z²)

# Simplified forms (docs/language.md §14.8)
ln(x)
# => log(x)
log10(x)
# => log(x)/log(10)
log2(x)
# => log(x)/log(2)
log(x, 3)
# => log(x)/log(3)
log(8, 2)
# => 3
exp(x)
# => e^x
cbrt(8)
# => 2
root(u, 4)
# => u^(1/4)
1/sqrt(2)
# => √2/2

# basic
abs(undefined)
# => undefined
abs(abs(log(x)))
# => |log(x)|
abs(x^2)
# => x²
abs(-3)
# => 3
abs(w)
# => -w
abs(1 + i)
# => √2
abs(3 + 4i)
# => 5
abs(-3y)
# => 3|y|
abs(x^3)
# => |x|³
abs(x)^2
# => x²
abs(z)^2
# => |z|²
sign(x^2 + 1)
# => 1
sign(-pi)
# => -1
sign(-2y)
# => -sign(y)
3 sign(x) abs(x)
# => 3x
sqrt(sqrt(u))
# => u^(1/4)
(x^4)^(1/2)
# => x²
(x^2)^(1/3)
# => |x|^(2/3)
sqrt(4y)
# => 2√y
sqrt(x y)
# => √(x y)
sqrt(u y)
# => √u √y
(-1)^n
# => 1
(-1)^m
# => -1
0^p
# => 0

# exp-log
exp(2x)
# => e^(2x)
log(1)
# => 0
log(e)
# => 1
log(e^(2y))
# => 2y
e^(2 log(p) + y)
# => p² e^y
e^log(x)
# => e^log(x)
log(p^3)
# => 3 log(p)
log(sqrt(2))
# => log(2)/2
log(x^2)
# => 2 log(|x|)
log(x/y)
# => log(x/y)
log(p/q)
# => log(p) - log(q)
log(2x)
# => log(2) + log(x)
log(1/2)
# => -log(2)
log(3/2)
# => log(3/2)
log(-2)
# => i π + log(2)
log(0)
# => undefined
log(0, 2)
# => undefined
log(x^n)
# => log(x^n)
log(x, 1)
# => undefined
log10(1000)
# => 3
log(1/4, 2)
# => -2
log(4, 8)
# => 2/3
log(3, 2)
# => log(3)/log(2)

# radical
sqrt(-8)
# => 2i √2
(-16)^(1/3)
# => 2(-1)^(1/3)·2^(1/3)
2^(3/2)
# => 2√2
3/sqrt(5)
# => 3√5/5
sqrt(8/9)
# => 2√2/3
sqrt(2/3)
# => √6/3
4^(1/3)
# => 2^(2/3)
4^(1/3) - 2^(2/3)
# => 0
sqrt(12)
# => 2√3
12^(1/2)
# => 2√3
54^(1/3)
# => 3·2^(1/3)
4^(2/3)
# => 2·2^(1/3)
2^(2/3)
# => 2^(2/3)
sqrt(2) sqrt(3)
# => √6
cbrt(2) cbrt(3)
# => 6^(1/3)
cbrt(2) cbrt(4)
# => 2
sqrt(5 + 2 sqrt(6))
# => √2 + √3
sqrt(3 - 2 sqrt(2))
# => √2 - 1
sqrt(2 + sqrt(3))
# => √2/2 + √6/2
sqrt(1 + sqrt(2))
# => √(√2 + 1)
root(x^3, 3)
# => x
root(x^2, 2)
# => |x|
root(-16, 4)
# => undefined
root(-16, 3)
# => -2·2^(1/3)
cbrt(-16)
# => -2·2^(1/3)
cbrt(x^3)
# => x
cbrt(-p)
# => -p^(1/3)

# Steps
steps(sqrt(8/9))
# steps:
#   step 1: Take the denominator out of the radicand: √(8/9) = √8/3
#   = √8/3
#   step 2: Write the radicand as a power of a smaller base: √8 = 2^(3/2)
#   = 2^(3/2)/3
#   step 3: Take the whole part of the exponent out: 2^(3/2) = 2√2
#   = 2√2/3
# => 2√2/3
steps(sqrt(x^2) + abs(-3y))
# steps:
#   step 1: The absolute value of a product is the product of the absolute values: |-3y| = |-3| |y|
#   = |-3| |y| + √(x²)
#   step 2: -3 ≤ 0, so |-3| = 3
#   = 3|y| + √(x²)
#   step 3: 2 is even, so the even power of x is that of |x|: √(x²) = |x|
#   = |x| + 3|y|
# => |x| + 3|y|
steps(log10(1000) + log(p^2 q))
# steps:
#   step 1: p² > 0, so the logarithm of the product splits: log(p² q) = log(p²) + log(q)
#   = log(p²) + log(q) + log10(1000)
#   step 2: p > 0 and 2 is real, so log(p²) = 2 log(p)
#   = 2 log(p) + log(q) + log10(1000)
#   step 3: log10 is the logarithm to base 10: log10(1000) = log(1000, 10)
#   = log(1000, 10) + 2 log(p) + log(q)
#   step 4: 1000 is a rational power of 10: log(1000, 10) = 3
#   = 2 log(p) + log(q) + 3
# => 2 log(p) + log(q) + 3

# Trigonometry (the trig rule set). The angle-mode rows of docs/language.md §14.2 in degrees are
# AcceptancePairsTest's: this file runs in radians.
assume(t >= -1, t <= 1, k ∈ ℤ)
sin(30°)
# => 1/2
sin(π/6)
# => 1/2
cos(π/4)
# => √2/2
tan(π/3)
# => √3
sin(π/12)
# => (-√2 + √6)/4
cos(5π/4)
# => -√2/2
sec(π/6)
# => 2√3/3
tan(π/2)
# => undefined
cot(0)
# => undefined
sin(7π/5)
# => -sin(2π/5)
sin(1)
# => sin(1)
asin(1/2)
# => π/6
acos(-√2/2)
# => 3π/4
atan(√3 - 2)
# => -π/12
sin(x + π)
# => -sin(x)
cos(x + 2π k)
# => cos(x)
cos(π k)
# => (-1)^k
sin(π/2 - x)
# => cos(x)
sin(x - π/3)
# => sin(x - π/3)
sin(y - x)
# => -sin(x - y)
cos(-x)
# => cos(x)
sin(x)^2 + cos(x)^2
# => 1
3 sin(x)^2 + 3 cos(x)^2 + y
# => y + 3
1 - sin(x)^2
# => cos(x)²
1 + tan(x)^2
# => sec(x)²
sin(x)/cos(x)
# => tan(x)
1/cos(x)^2
# => sec(x)²
sin(x) cos(y)
# => cos(y) sin(x)
2 sin(x) cos(x)
# => sin(2x)
cos(x)^2 - sin(x)^2
# => cos(2x)
sqrt(1 - sin(x)^2)
# => |cos(x)|
asin(sin(x))
# => asin(sin(x))
asin(sin(t))
# => t
sin(acos(x))
# => √(-x² + 1)
cosh(x)^2 - sinh(x)^2
# => 1
steps(sin(π/6) + sin(x)^2 + cos(x)^2)
# steps:
#   step 1: Reduce the angle and take the exact value: sin(π/6) = 1/2
#   = cos(x)² + sin(x)² + 1/2
#   step 2: sin²(x) + cos²(x) = 1: cos(x)² + sin(x)² + 1/2 = 3/2
#   = 3/2
# => 3/2
steps(sin(π - x)/cos(x))
# steps:
#   step 1: Write the trigonometric factors with the fewest functions: 1/cos(x) = sec(x)
#   = sec(x) sin(-x + π)
#   step 2: Remove the whole multiple of π from the angle: sin(-x + π) = -sin(-x)
#   = -sec(x) sin(-x)
#   step 3: An odd function of a negative: sin(-x) = -sin(x)
#   = sec(x) sin(x)
#   step 4: Write the trigonometric factors with the fewest functions: sec(x) sin(x) = tan(x)
#   = tan(x)
# => tan(x)

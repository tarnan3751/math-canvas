# Laplace transforms by table: laplace and ilaplace
# (docs/reference/engine.md, Laplace transforms; docs/language.md §14.21).
# A golden corpus file: docs/reference/cli.md describes the format.

# The table
laplace(1, t, s)
# => 1/s
laplace(t^3, t, s)
# => 6/s⁴
laplace(e^(2t), t, s)
# => 1/(s - 2)
laplace(sin(3t), t, s)
# => 3/(s² + 9)
laplace(cos(ω t), t, s)
# => s/(s² + ω²)
laplace(cosh(2t), t, s)
# => s/(s² - 4)
laplace(sin(2t + 1), t, s)
# => (s sin(1) + 2 cos(1))/(s² + 4)

# Linearity, the shift and multiplication by t
laplace(cos(t) + 2t, t, s)
# => s/(s² + 1) + 2/s²
laplace(e^(2t) sin(t), t, s)
# => 1/((s - 2)² + 1)
laplace(t^2 e^(3t), t, s)
# => 2/(s - 3)³
laplace(t sin(t), t, s)
# => 2s/(s² + 1)²
steps(laplace(e^(2t) sin(t), t, s))
# steps:
#   step 1: By the first shifting theorem, e^(at) moves the transform to s − a: laplace(e^(2t) sin(t), t, s) = laplace(sin(t), t, s - 2)
#   = laplace(sin(t), t, s - 2)
#   step 2: From the table of transforms: laplace(sin(t), t, s - 2) = 1/((s - 2)² + 1)
#   = 1/((s - 2)² + 1)
# => 1/((s - 2)² + 1)

# The inverse: partial fractions, then the table read backwards
ilaplace(1/(s - 2), s, t)
# => e^(2t)
ilaplace(1/(s + 1)^4, s, t)
# => t³ e^(-t)/6
ilaplace(s/(s^2 + 4), s, t)
# => cos(2t)
ilaplace((s + 1)/(s^2 + 2s + 5), s, t)
# => e^(-t) cos(2t)
ilaplace(1/(s^2 - 2), s, t)
# => √2 sinh(t √2)/2
steps(ilaplace(1/((s - 1)(s + 2)), s, t))
# steps:
#   step 1: Split into partial fractions: ilaplace(1/((s - 1)(s + 2)), s, t) = ilaplace(1/(3(s - 1)) - 1/(3(s + 2)), s, t)
#   = ilaplace(1/(3(s - 1)) - 1/(3(s + 2)), s, t)
#   step 2: Invert term by term: ilaplace(1/(3(s - 1)) - 1/(3(s + 2)), s, t) = ilaplace(1/(3(s - 1)), s, t) + ilaplace(-1/(3(s + 2)), s, t)
#   = ilaplace(1/(3(s - 1)), s, t) + ilaplace(-1/(3(s + 2)), s, t)
#   step 3: Take the constant factor out of the inverse transform: ilaplace(1/(3(s - 1)), s, t) = ilaplace(1/(s - 1), s, t)/3
#   = ilaplace(1/(s - 1), s, t)/3 + ilaplace(-1/(3(s + 2)), s, t)
#   step 4: From the table of transforms: ilaplace(1/(s - 1), s, t) = e^t
#   = e^t/3 + ilaplace(-1/(3(s + 2)), s, t)
#   step 5: Take the constant factor out of the inverse transform: ilaplace(-1/(3(s + 2)), s, t) = -ilaplace(1/(s + 2), s, t)/3
#   = e^t/3 - ilaplace(1/(s + 2), s, t)/3
#   step 6: From the table of transforms: ilaplace(1/(s + 2), s, t) = e^(-2t)
#   = e^t/3 - e^(-2t)/3
# => e^t/3 - e^(-2t)/3
ilaplace(laplace(t^2 e^(-t) + 3 cos(2t), t, s), s, t)
# => t² e^(-t) + 3 cos(2t)

# The result's variable is free in the result
s := 2
laplace(e^t, t, s)
# => 1
clear(s)
# => s

# What no rule takes stays whole
laplace(e^(t^2), t, s)
# => laplace(e^(t²), t, s)
steps(ilaplace(e^(-s)/s, s, t))
# steps:
#   step 1: ilaplace(e^(-s)/s, s, t) is not in the table of transforms, so it is left as it is
#   = ilaplace(e^(-s)/s, s, t)
# => ilaplace(e^(-s)/s, s, t)
laplace(t, t, t)
# !! `laplace` needs two different variables

# Review-28: tⁿ up to 10, no split of an irreducible quadratic, a transform that never converges
laplace(t^12 sin(t), t, s)
# => laplace(t¹² sin(t), t, s)
steps(ilaplace(1/(s^2 + 2s + 5), s, t))
# steps:
#   step 1: From the table of transforms: ilaplace(1/(s² + 2s + 5), s, t) = e^(-t) sin(2t)/2
#   = e^(-t) sin(2t)/2
# => e^(-t) sin(2t)/2
laplace(e^(s t), t, s)
# => undefined

# An unknown function applied to the variable, its derivatives by the prime notation
laplace(y''(t) + y(t), t, s)
# => s² laplace(y(t), t, s) - s y(0) + laplace(y(t), t, s) - y′(0)
laplace(y'(t) - 2y(t), t, s)
# => s laplace(y(t), t, s) - 2 laplace(y(t), t, s) - y(0)
# A name the session defines as a value is still that value times t
a := 3
laplace(a(t), t, s)
# => 3/s²
laplace(a'(t), t, s)
# !! `a` is not a function: define it first, as in `a(x) := x^2`
laplace(f(t), t, s)
# => laplace(f(t), t, s)
# y'' + y = sin t, y(0) = 0, y'(0) = 1: the transform gives (s² + 1) Y - 1 = 1/(s² + 1), so
# Y = (s² + 2)/(s² + 1)², whose inverse is the solution dsolve gives
laplace(sin(t), t, s)
# => 1/(s² + 1)
ilaplace((s^2 + 2)/(s^2 + 1)^2, s, t)
# => sin(t) + (-t cos(t) + sin(t))/2
dsolve(y''(t) + y(t) = sin(t), y(t), y(0) = 0, y'(0) = 1)
# => y(t) = -t cos(t)/2 + 3 sin(t)/2
# Repeated quadratics
ilaplace(1/(s^2 + 1)^2, s, t)
# => (-t cos(t) + sin(t))/2
ilaplace(s/((s + 1)^2 + 4)^2, s, t)
# => (t sin(2t)/4 - (-2t cos(2t) + sin(2t))/16) e^(-t)
ilaplace(1/(s^2 - 2)^2, s, t)
# => (t √2 cosh(t √2) - sinh(t √2)) √2/8

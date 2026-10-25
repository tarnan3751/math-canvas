# Differentiation: the diff command and the d/dx notation
# (docs/reference/engine.md, Differentiation; docs/language.md §14.10).
# A golden corpus file: docs/reference/cli.md describes the format.

# PLAN §6
d/dx (x^2 sin x)
# => x² cos(x) + 2x sin(x)

# Polynomials and powers
# d/dx takes the rest of its term only
d/dx x^3 + 2x + 1
# => 3x² + 2x + 1
diff(x^3 + 2x + 1, x)
# => 3x² + 2
diff(5x^4 - 3x^2 + 7, x)
# => 20x³ - 6x
diff(sqrt(x), x)
# => 1/(2√x)
diff(1/x^2, x)
# => -2/x³
diff(x^x, x)
# => x^x·(log(x) + 1)
diff(2^x, x)
# => 2^x log(2)
diff(a x^2 + b x + c, x)
# => 2a x + b

# The chain rule
diff(sin(x^2), x)
# => 2x cos(x²)
diff(e^(3x), x)
# => 3 e^(3x)
diff(exp(x^2), x)
# => 2x e^(x²)
diff(ln(x^2 + 1), x)
# => 2x/(x² + 1)
diff(sqrt(1 - x^2), x)
# => -x/√(-x² + 1)
diff(cos(2x + 1), x)
# => -2 sin(2x + 1)
diff(log10(x), x)
# => 1/(x log(10))

# Quotients
diff(sin(x)/x, x)
# => (x cos(x) - sin(x))/x²
diff(x/(x + 1), x)
# => 1/(x + 1)²
diff(tan(x), x)
# => sec(x)²

# Inverse trigonometric and hyperbolic functions
diff(asin(x), x)
# => 1/√(-x² + 1)
diff(acos(x), x)
# => -1/√(-x² + 1)
diff(atan(x), x)
# => 1/(x² + 1)
diff(atan(x^2), x)
# => 2x/(x⁴ + 1)
diff(asinh(x), x)
# => 1/√(x² + 1)

# Absolute value, and what stays
diff(abs(x), x)
# => sign(x)
diff(factorial(x), x)
# => d/dx x!
diff(factorial(x), x, 2)
# => d²/dx² x!
diff(x factorial(x), x, 2)
# => x d²/dx² x! + 2 d/dx x!

# Higher orders, partial derivatives and sums
d^2/dx^2 sin x
# => -sin(x)
diff(x^5, x, 3)
# => 60x²
diff(x^4, x, 2147483647)
# => 0
diff(x^2 y^3, y)
# => 3x² y²
d/dx Σ[k=1,n] x^k
# => ∑[k = 1, n] k x^(k - 1)

# The variable is free in the result
f(x) := diff(x^3, x)
# => f(x) := d/dx x³
f(2)
# => 12
subs(diff(x^2, x), x = 3)
# => 6
g(y) := diff(t^2 + y, t)
# => g(y) := d/dt (t² + y)
g(t)
# => 2t
h(x, y) := diff(x^2 y, x)
# => h(x, y) := d/dx x² y
h(2, x)
# => 4x
subs(diff(x^2 y, x), x = 2, y = x)
# => 4x

# Steps
steps(d/dx (x^2 sin x))
# steps:
#   step 1: Apply the product rule: d/dx x² sin(x) = x² d/dx sin(x) + sin(x) d/dx x²
#   = x² d/dx sin(x) + sin(x) d/dx x²
#   step 2: The derivative of sin is cos: d/dx sin(x) = cos(x)
#   = x² cos(x) + sin(x) d/dx x²
#   step 3: Apply the power rule: d/dx x² = 2x
#   = x² cos(x) + 2x sin(x)
# => x² cos(x) + 2x sin(x)
steps(diff(3x^2 + 2x + 1, x))
# steps:
#   step 1: Differentiate term by term; constants fall away
#   = 3 d/dx x² + 2
#   step 2: Apply the power rule: d/dx x² = 2x
#   = 6x + 2
# => 6x + 2
steps(diff(sin(x)/x, x))
# steps:
#   step 1: Apply the quotient rule: d/dx sin(x)/x = (x d/dx sin(x) - sin(x) d/dx x)/x²
#   = (x d/dx sin(x) - sin(x) d/dx x)/x²
#   step 2: The derivative of sin is cos: d/dx sin(x) = cos(x)
#   = (x cos(x) - sin(x) d/dx x)/x²
#   step 3: The derivative of x with respect to x is 1
#   = (x cos(x) - sin(x))/x²
# => (x cos(x) - sin(x))/x²
steps(d/dx sin(x^2))
# steps:
#   step 1: Apply the chain rule to sin(x²): the outer derivative is cos(x²), the inner is 2x. Apply the power rule: d/dx x² = 2x
#   = 2x cos(x²)
# => 2x cos(x²)

# A derivative too large to finish
diff(x^x, x, 40)
# !! differentiation did not finish

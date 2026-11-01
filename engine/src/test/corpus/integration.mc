# Integration: the integrate command and the ∫ notation
# (docs/reference/engine.md, Integration; docs/language.md §14.13).
# A golden corpus file: docs/reference/cli.md describes the format.
#
# An indefinite integral is an antiderivative without a constant; an integral no technique
# finds stays as it is, with the step that says so.

# PLAN §6
∫ x e^x dx
# => x e^x - e^x
∫[0,1] x^2 dx
# => 1/3

# The table: powers, reciprocals, exponentials
∫ x^3 + 2x + 1 dx
# => x⁴/4 + x² + x
∫ sqrt(x) dx
# => 2x^(3/2)/3
∫ 1/x^2 dx
# => -1/x
∫ 1/x dx
# => log(|x|)
∫ 1/(2x + 3) dx
# => log(|2x + 3|)/2
∫ 3 e^(2x) dx
# => 3 e^(2x)/2
∫ e^(-x) dx
# => -e^(-x)
∫ 2^x dx
# => 2^x/log(2)
∫ (3x + 2)^10 dx
# => (3x + 2)¹¹/33

# The table: circular and hyperbolic functions of a linear argument
∫ sin(2x + 1) dx
# => -cos(2x + 1)/2
∫ cos(x/2) dx
# => 2 sin(x/2)
∫ tan(x) dx
# => -log(|cos(x)|)
∫ cot(x) dx
# => log(|sin(x)|)
∫ sec(x) dx
# => log(|sec(x) + tan(x)|)
∫ csc(x) dx
# => -log(|cot(x) + csc(x)|)
∫ sec(x)^2 dx
# => tan(x)
∫ sec(x) tan(x) dx
# => sec(x)
∫ cosh(3x) dx
# => sinh(3x)/3
∫ tanh(x) dx
# => log(cosh(x))
∫ sech(x) dx
# => atan(sinh(x))
∫ sech(x)^2 dx
# => tanh(x)

# The table: inverse circular forms and logarithms of quadratics
∫ 1/(1 + x^2) dx
# => atan(x)
∫ 1/(4 + 9x^2) dx
# => atan(3x/2)/6
∫ 1/sqrt(1 - x^2) dx
# => asin(x)
∫ 1/(x^2 - 4) dx
# => log(|x - 2|)/4 - log(|x + 2|)/4
∫ 1/(4 - x^2) dx
# => -log(|x - 2|)/4 + log(|x + 2|)/4
∫ 1/sqrt(x^2 + 1) dx
# => log(x + √(x² + 1))
∫ 1/sqrt(x^2 - 1) dx
# => log(|x + √(x² - 1)|)

# Linearity, and a constant that may be zero
∫ a x^2 + b dx
# => a x³/3 + b x
∫ e^(a x) dx
# => ∫ e^(a x) dx
assume(a > 0)
∫ e^(a x) dx
# => e^(a x)/a
clear(a)

# Substitution
∫ 2x cos(x^2) dx
# => sin(x²)
∫ x e^(x^2) dx
# => e^(x²)/2
∫ x/(x^2 + 1) dx
# => log(x² + 1)/2
∫ log(x)/x dx
# => log(x)²/2
∫ e^x/(1 + e^(2x)) dx
# => atan(e^x)
∫ tan(x) sec(x)^2 dx
# => sec(x)²/2
∫ x^2 sqrt(x^3 + 1) dx
# => 2(x³ + 1)^(3/2)/9
∫ x sqrt(x + 1) dx
# => -2(x + 1)^(3/2)/3 + 2(x + 1)^(5/2)/5
∫ x/(x + 1) dx
# => x - log(|x + 1|)
∫ 1/(x log(x)) dx
# => log(|log(x)|)
∫ 1/(sqrt(x) (sqrt(x) - 1)) dx
# => 2 log(|√x - 1|)

# By parts
∫ x^2 e^x dx
# => x² e^x - 2x e^x + 2 e^x
∫ x sin(x) dx
# => -x cos(x) + sin(x)
∫ x log(x) dx
# => -x²/4 + x² log(x)/2
∫ log(x) dx
# => -x + x log(x)
∫ atan(x) dx
# => x atan(x) - log(x² + 1)/2
∫ asin(x) dx
# => x asin(x) + √(-x² + 1)
∫ e^x sin(x) dx
# => -e^x cos(x)/2 + e^x sin(x)/2

# Trigonometric identities
∫ sin(x)^2 dx
# => x/2 - sin(2x)/4
∫ sin(x)^3 dx
# => -cos(x) + cos(x)³/3
∫ sin(x)^2 cos(x)^3 dx
# => sin(x)³/3 - sin(x)⁵/5
∫ sin(3x) cos(2x) dx
# => -cos(x)/2 - cos(5x)/10
∫ tan(x)^2 dx
# => -x + tan(x)
∫ tan(x)^3 dx
# => log(|cos(x)|) + sec(x)²/2
∫ sec(x)^3 dx
# => log(|sec(x) + tan(x)|)/2 + sec(x) tan(x)/2

# Completing the square
∫ 1/(x^2 + 2x + 5) dx
# => atan((x + 1)/2)/2
∫ 1/sqrt(x^2 + 2x + 2) dx
# => log(x + √((x + 1)² + 1) + 1)
∫ 1/sqrt(3 + 2x - x^2) dx
# => asin((x - 1)/2)

# Definite integrals
∫[0, π] sin(x) dx
# => 2
∫[1, e] log(x) dx
# => 1
∫[0, 1] x e^x dx
# => 1
∫[0, π/2] sin(x)^2 dx
# => π/4
∫[0, 1] 1/(1 + x^2) dx
# => π/4
∫[1, 2] 1/x dx
# => log(2)
∫[0, 1] sqrt(x) dx
# => 2/3
∫[0, 1] tan(x) dx
# => -log(cos(1))
∫[0, t] x dx
# => t²/2
∫[2, 1] x dx
# => -3/2

# Across a singularity: a pole of a rational integrand in the interval makes it diverge;
# another singularity, or one whose place cannot be decided, leaves the integral
∫[-1, 1] 1/x dx
# => undefined
∫[0, 2] tan(x) dx
# => ∫[0, 2] tan(x) dx
∫[1, t] 1/x dx
# => ∫[1, t] 1/x dx
assume(t > 1)
∫[1, t] 1/x dx
# => log(t)
clear(t)

# A definite integral that diverges or is refused says why
steps(∫[-1, 1] 1/x^2 dx)
# steps:
#   step 1: The integrand has a pole at {0} in the interval, where it is not integrable, so the integral diverges: ∫[-1, 1] 1/x² dx = undefined
#   = undefined
# => undefined
steps(∫[0, 1] 1/x dx)
# steps:
#   step 1: The integrand has a pole at {0} in the interval, where it is not integrable, so the integral diverges: ∫[0, 1] 1/x dx = undefined
#   = undefined
# => undefined

# A complex inner function is substituted by a complex variable
∫ 2x/(x^2 + i) dx
# => log(x² + i)
∫ x/(x + i) dx
# => x - i log(x + i)
∫ 2x/(x^2 + asin(2)) dx
# => log(x² + asin(2))
∫ 2x/(x^2 + log(2)) dx
# => log(|x² + log(2)|)

# What no technique integrates stays
∫ e^(x^2) dx
# => ∫ e^(x²) dx
∫ sin(x)/x dx
# => ∫ sin(x)/x dx
∫ x! dx
# => ∫ x! dx
∫ x + x! dx
# => ∫ x + x! dx
∫ x^n dx
# => ∫ x^n dx

# Steps: one example per technique
steps(∫ x^2 + 3 sin(x) dx)
# steps:
#   step 1: Integrate term by term
#   = 3 ∫ sin(x) dx + ∫ x² dx
#   step 2: The integral of sin is -cos: ∫ sin(x) dx = -cos(x)
#   = -3 cos(x) + ∫ x² dx
#   step 3: Apply the power rule: ∫ x² dx = x³/3
#   = x³/3 - 3 cos(x)
# => x³/3 - 3 cos(x)
steps(∫ x cos(x^2) dx)
# steps:
#   step 1: Substitute u = x²: the integral becomes ∫ cos(u)/2 du = sin(u)/2, so ∫ x cos(x²) dx = sin(x²)/2
#   = sin(x²)/2
# => sin(x²)/2
steps(∫ x e^x dx)
# steps:
#   step 1: Integrate by parts with u = x and dv = e^x dx, then integrate ∫ e^x dx: ∫ x e^x dx = x e^x - e^x
#   = x e^x - e^x
# => x e^x - e^x
steps(∫ e^x sin(x) dx)
# steps:
#   step 1: Integrate by parts twice: the integral comes back -1 times itself, so solve for it: ∫ e^x sin(x) dx = (e^x sin(x) - e^x cos(x))/2
#   = (e^x sin(x) - e^x cos(x))/2
#   step 2: Multiply the constant 1/2 into the sum: (e^x sin(x) - e^x cos(x))/2 = -e^x cos(x)/2 + e^x sin(x)/2
#   = -e^x cos(x)/2 + e^x sin(x)/2
# => -e^x cos(x)/2 + e^x sin(x)/2
steps(∫ sin(x)^2 dx)
# steps:
#   step 1: Lower the even powers with sin² = (1 - cos 2u)/2 and cos² = (1 + cos 2u)/2: ∫ sin(x)² dx = ∫ (1 - cos(2x))/2 dx
#   = ∫ (1 - cos(2x))/2 dx
#   step 2: Take the constant factor 1/2 out of the integral: ∫ (1 - cos(2x))/2 dx = ∫ -cos(2x) + 1 dx/2
#   = ∫ -cos(2x) + 1 dx/2
#   step 3: Integrate term by term
#   = (x - ∫ cos(2x) dx)/2
#   step 4: The integral of cos is sin: ∫ cos(2x) dx = sin(2x)/2
#   = (x - sin(2x)/2)/2
#   step 5: Multiply the numerator and the denominator by 2 to clear the fractions: (x - sin(2x)/2)/2 = (2x - sin(2x))/4
#   = (2x - sin(2x))/4
#   step 6: Multiply the constant 1/4 into the sum: (2x - sin(2x))/4 = x/2 - sin(2x)/4
#   = x/2 - sin(2x)/4
# => x/2 - sin(2x)/4
steps(∫ 1/(x^2 + 2x + 5) dx)
# steps:
#   step 1: Complete the square: ∫ 1/(x² + 2x + 5) dx = ∫ 1/((x + 1)² + 4) dx
#   = ∫ 1/((x + 1)² + 4) dx
#   step 2: The reciprocal of a constant times a square plus a constant integrates to an arctangent or logarithms: ∫ 1/((x + 1)² + 4) dx = atan((x + 1)/2)/2
#   = atan((x + 1)/2)/2
# => atan((x + 1)/2)/2
steps(∫[0, 1] x^2 dx)
# steps:
#   step 1: An antiderivative is x³/3; evaluate it at the bounds: ∫[0, 1] x² dx = 1/3
#   = 1/3
# => 1/3
steps(∫ e^(x^2) dx)
# steps:
#   step 1: No antiderivative found by the table, linearity, the trigonometric identities, completing the square, substitution or integration by parts: ∫ e^(x²) dx stays
#   = ∫ e^(x²) dx
# => ∫ e^(x²) dx
steps(d/dx ∫ x! dx)
# steps:
#   step 1: By the fundamental theorem of calculus: d/dx ∫ x! dx = x!
#   = x!
# => x!

# The variable is free in an indefinite integral's result, bound in a definite one's
f(x) := ∫ x^2 dx
f(2)
# => 8/3
x := 3
∫ x^2 dx
# => 9
∫[0, 1] x^2 dx
# => 1/3
clear(x)

# The fundamental theorem: an integral that stays differentiates to its integrand
d/dx ∫ x! dx
# => x!
d/dx ∫[0, x^2] t! dt
# => 2x·(x²)!

# The facts about the variable apply to the result: log|x| is log(x) for a positive x
assume(x > 0)
∫ 1/x dx
# => log(x)
assume(x > 1)
∫[1, x] 1/x dx
# => log(x)

# Ordinary differential equations: dsolve, its classes, initial conditions and systems, and the
# prime notation (docs/language.md §5, §14.22; docs/reference/engine.md, Ordinary differential
# equations). A golden corpus file: docs/reference/cli.md describes the format.

# The prime notation on a user function
f(x) := x^3 - 2x
f'(2)
# => 10
f''(x)
# => 6x
g(t) := sin(t)^2
g'(π/4)
# => 1

# Linear, first order: the integrating factor
dsolve(y'(x) = y(x), y(x))
# => y(x) = C₁ e^x, C₁ ∈ ℝ
dsolve(diff(y(x), x) = y(x), y(x))
# => y(x) = C₁ e^x, C₁ ∈ ℝ
dsolve(y'(x) + 2y(x) = 4, y(x))
# => y(x) = (C₁ + 2 e^(2x)) e^(-2x), C₁ ∈ ℝ
dsolve(y'(x) + y(x)/x = x^2, y(x))
# => y(x) = (x⁴ + 4C₁)/(4x), C₁ ∈ ℝ
dsolve(y'(x) - 2y(x) = e^(2x), y(x))
# => y(x) = (C₁ + x) e^(2x), C₁ ∈ ℝ
dsolve(y'(x) + tan(x) y(x) = cos(x), y(x))
# => y(x) = (C₁ + x) cos(x), C₁ ∈ ℝ
dsolve(y'(x) = cos(x), y(x), y(0) = 1)
# => y(x) = sin(x) + 1
dsolve(y'(x) = x y(x), y(x), y(0) = 2)
# => y(x) = 2 e^(x²/2)
steps(dsolve(y'(x) + y(x)/x = x^2, y(x)))
# steps:
#   step 1: The equation is linear, y′ + P y = Q, with P = 1/x and Q = x²: y′(x) + y(x)/x = x²
#   = y′(x) + y(x)/x = x²
#   step 2: Multiply by the integrating factor μ = e^∫P dx = x, which makes the left side the derivative of a product: d/dx x y(x) = x³
#   = d/dx x y(x) = x³
#   step 3: Integrate both sides, with a constant of integration: x y(x) = x⁴/4 + C₁
#   = x y(x) = x⁴/4 + C₁
#   step 4: The solution: y(x) = (x⁴ + 4C₁)/(4x)
#   = y(x) = (x⁴ + 4C₁)/(4x)
# => y(x) = (x⁴ + 4C₁)/(4x), C₁ ∈ ℝ

# Separable: the separated integrals, the logarithms exponentiated, the branches
dsolve(y'(x) = y(x)^2, y(x))
# => y(x) = -1/(C₁ + x), C₁ ∈ ℝ
dsolve(y'(x) = x/y(x), y(x))
# => {y(x) = √(x² + 2C₁), y(x) = -√(x² + 2C₁)}, C₁ ∈ ℝ
dsolve(y'(x) = x/y(x), y(x), y(0) = 2)
# => y(x) = √(x² + 4)
dsolve(y'(x) = x/y(x), y(x), y(0) = -3)
# => y(x) = -√(x² + 9)
dsolve(y'(x) = y(x)(1 - y(x)), y(x))
# => y(x) = -1/(C₁ e^(-x) - 1), C₁ ∈ ℝ
dsolve(y'(x) = y(x)(1 - y(x)), y(x), y(0) = 1/2)
# => y(x) = -1/(-e^(-x) - 1)
dsolve(y'(x) = e^(x + y(x)), y(x))
# => -e^x - e^(-y(x)) = C₁, C₁ ∈ ℝ
dsolve(y'(x) = 1 + y(x)^2, y(x))
# => x - atan(y(x)) = C₁, C₁ ∈ ℝ
steps(dsolve(y'(x) = y(x)^2, y(x)))
# steps:
#   step 1: The equation is separable, y′ = g(x) h(y) with g = 1 and h = y(x)²; divide by h: y′(x)/y(x)² = 1
#   = y′(x)/y(x)² = 1
#   step 2: Integrate both sides, with a constant of integration: -1/y(x) = C₁ + x
#   = -1/y(x) = C₁ + x
#   step 3: The solution: y(x) = -1/(C₁ + x)
#   = y(x) = -1/(C₁ + x)
# => y(x) = -1/(C₁ + x), C₁ ∈ ℝ

# an equilibrium the separation divided away
dsolve(y'(x) = y(x)(1 - y(x)), y(x), y(0) = 0)
# => y(x) = 0
dsolve(y'(x) = 2x (y(x) - 1)^2, y(x), y(0) = 1)
# => y(x) = 1

# Exact equations: the potential, implicit when y does not solve
dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x))
# => x² y(x) + y(x)³ = C₁, C₁ ∈ ℝ
dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(1) = 1)
# => x² y(x) + y(x)³ = 2
dsolve(y(x) + (x + 2y(x)) y'(x) = 0, y(x))
# => {y(x) = (-x + √(x² + 4C₁))/2, y(x) = (-x - √(x² + 4C₁))/2}, C₁ ∈ ℝ

# two conditions on an implicit solution: one potential, or none
dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(1) = 1, y(-1) = 1)
# => x² y(x) + y(x)³ = 2
dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(0) = 1, y(1) = 5)
# => {}
# the real root r of r³ + r = 1 by Cardano: the potentials agree, numerically
r := cbrt(1/2 + sqrt(31/108)) + cbrt(1/2 - sqrt(31/108));
dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(0) = 1, y(1) = r)
# => x² y(x) + y(x)³ = 1
clear(r)
# decimal conditions, met to the working precision
dsolve(y'(x) = y(x), y(x), y(0.5) = 2)
# => y(x) = 1.213061319425266847207599069982361 e^x
dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1.5, y'(0) = 0)
# => y(x) = 1.5 cos(x)
dsolve(y'(x) = 0.5 y(x), y(x), y(0) = 1)
# => y(x) = 1.0 e^(0.5x)

# Bernoulli: y^(1 - n) is linear
dsolve(y'(x) + y(x) = x y(x)^2, y(x))
# => y(x) = e^(-x)/(C₁ + x e^(-x) + e^(-x)), C₁ ∈ ℝ
dsolve(y'(x) + y(x) = x y(x)^3, y(x))
# => {y(x) = e^(-x)/√(C₁ + x e^(-2x) + e^(-2x)/2), y(x) = -e^(-x)/√(C₁ + x e^(-2x) + e^(-2x)/2)}, C₁ ∈ ℝ
dsolve(y'(x) + y(x) = x y(x)^3, y(x), y(0) = -1)
# => y(x) = -e^(-x)/√(x e^(-2x) + e^(-2x)/2 + 1/2)
dsolve(y'(x) + y(x) = x y(x)^3, y(x), y(0) = 0)
# => y(x) = 0
dsolve(y'(x) = y(x) - y(x)^3, y(x))
# => √(y(x) - 1) √(y(x) + 1) e^x/y(x) = C₁, C₁ ∈ ℝ

# Constant coefficients: the characteristic roots, real, repeated and complex
dsolve(y''(x) - 3y'(x) + 2y(x) = 0, y(x))
# => y(x) = C₁ e^x + C₂ e^(2x), C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y''(x) + 2y'(x) + y(x) = 0, y(x))
# => y(x) = C₂ x e^(-x) + C₁ e^(-x), C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y''(x) + 2y'(x) + 5y(x) = 0, y(x))
# => y(x) = C₁ e^(-x) cos(2x) + C₂ e^(-x) sin(2x), C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y'''(x) - y(x) = 0, y(x))
# => y(x) = C₁ e^x + C₂ e^(-x/2) cos(x √3/2) + C₃ e^(-x/2) sin(x √3/2), C₁ ∈ ℝ, C₂ ∈ ℝ, C₃ ∈ ℝ
dsolve(y''''(x) - y(x) = 0, y(x))
# => y(x) = C₁ e^(-x) + C₂ e^x + C₃ cos(x) + C₄ sin(x), C₁ ∈ ℝ, C₂ ∈ ℝ, C₃ ∈ ℝ, C₄ ∈ ℝ
dsolve(y''(x) = 6x, y(x))
# => y(x) = x³ + C₂ x + C₁, C₁ ∈ ℝ, C₂ ∈ ℝ
# undetermined coefficients, with resonance
dsolve(y''(x) - 3y'(x) + 2y(x) = e^x, y(x))
# => y(x) = C₁ e^x + C₂ e^(2x) - x e^x, C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y''(x) + 4y(x) = sin(2x), y(x))
# => y(x) = C₁ cos(2x) + C₂ sin(2x) - x cos(2x)/4, C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y''(x) - 2y'(x) + y(x) = x e^x, y(x))
# => y(x) = x³ e^x/6 + C₂ x e^x + C₁ e^x, C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y''(x) - y(x) = e^x + cos(x), y(x))
# => y(x) = C₁ e^(-x) + C₂ e^x + x e^x/2 - cos(x)/2, C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve(y''(x) + y(x) = x^2, y(x))
# => y(x) = x² + C₁ cos(x) + C₂ sin(x) - 2, C₁ ∈ ℝ, C₂ ∈ ℝ
# initial conditions
dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1, y'(0) = 0)
# => y(x) = cos(x)
dsolve(y''(x) - y(x) = 0, y(x), y(0) = 2, y'(0) = 0)
# => y(x) = e^x + e^(-x)
dsolve(y''(x) + y(x) = 0, y(x), y(0) = 0, y(pi) = 1)
# => {}
dsolve(y''(x) + y(x) = 0, y(x), y(0) = 0)
# => y(x) = C₂ sin(x), C₂ ∈ ℝ
steps(dsolve(y''(x) + 4y(x) = sin(2x), y(x)))
# steps:
#   step 1: The characteristic equation: r² + 4 = 0
#   = r² + 4 = 0
#   step 2: The roots {-2i, 2i} give the general solution of the homogeneous equation: y(x) = C₁ cos(2x) + C₂ sin(2x)
#   = y(x) = C₁ cos(2x) + C₂ sin(2x)
#   step 3: Undetermined coefficients give the particular solution -x cos(2x)/4: y(x) = C₁ cos(2x) + C₂ sin(2x) - x cos(2x)/4
#   = y(x) = C₁ cos(2x) + C₂ sin(2x) - x cos(2x)/4
# => y(x) = C₁ cos(2x) + C₂ sin(2x) - x cos(2x)/4, C₁ ∈ ℝ, C₂ ∈ ℝ

# Linear systems: eigenvalues, a complex pair, a defective matrix, a forcing term
dsolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)])
# => [x(t) = -C₂ cos(t) + C₁ sin(t), y(t) = C₁ cos(t) + C₂ sin(t)], C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0)
# => [x(t) = cos(t), y(t) = -sin(t)]
dsolve([x'(t) = x(t) + 2y(t), y'(t) = 2x(t) + y(t)], [x(t), y(t)])
# => [x(t) = -C₁ e^(-t) + C₂ e^(3t), y(t) = C₁ e^(-t) + C₂ e^(3t)], C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve([x'(t) = x(t) + y(t), y'(t) = y(t)], [x(t), y(t)])
# => [x(t) = C₂ t e^t + C₁ e^t, y(t) = C₂ e^t], C₁ ∈ ℝ, C₂ ∈ ℝ
dsolve([x'(t) = x(t) - 2y(t), y'(t) = 2x(t) + y(t)], [x(t), y(t)], x(0) = 1, y(0) = 0)
# => [x(t) = e^t cos(2t), y(t) = e^t sin(2t)]
dsolve([x'(t) = x(t) + 2y(t) + 1, y'(t) = 2x(t) + y(t)], [x(t), y(t)])
# => [x(t) = -C₁ e^(-t) + C₂ e^(3t) + 1/3, y(t) = C₁ e^(-t) + C₂ e^(3t) - 2/3], C₁ ∈ ℝ, C₂ ∈ ℝ
steps(dsolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0))
# steps:
#   step 1: The system is linear with constant coefficients, x′ = A x + b with A = [[0, 1], [-1, 0]]: [x′(t) = y(t), y′(t) = -x(t)]
#   = [x′(t) = y(t), y′(t) = -x(t)]
#   step 2: The eigenvalues ⟨-i, i⟩ and their generalised eigenvectors give the general solution: [x(t) = -C₂ cos(t) + C₁ sin(t), y(t) = C₁ cos(t) + C₂ sin(t)]
#   = [x(t) = -C₂ cos(t) + C₁ sin(t), y(t) = C₁ cos(t) + C₂ sin(t)]
#   step 3: The initial conditions fix the constants: [x(t) = cos(t), y(t) = -sin(t)]
#   = [x(t) = cos(t), y(t) = -sin(t)]
# => [x(t) = cos(t), y(t) = -sin(t)]

# What stays: no class applies, or the integral has no closed form
dsolve(y'(x) = sin(x y(x)), y(x))
# => dsolve(y′(x) = sin(x y(x)), y(x))
dsolve(x^2 y''(x) + y(x) = 0, y(x))
# => dsolve(x² y′′(x) + y(x) = 0, y(x))
dsolve(y'(x) = e^(x^2) y(x)^2 + y(x), y(x))
# => dsolve(y′(x) = y(x) + e^(x²) y(x)², y(x))
steps(dsolve(y'(x) = sin(x y(x)), y(x)))
# steps:
#   step 1: No method applies to dsolve(y′(x) = sin(x y(x)), y(x))
#   = dsolve(y′(x) = sin(x y(x)), y(x))
# => dsolve(y′(x) = sin(x y(x)), y(x))

# The constants are fresh and real
dsolve(y'(x) = C_1, y(x))
# => y(x) = C₁ x + C₂, C₂ ∈ ℝ
subs(dsolve(y'(x) = y(x), y(x)), C_1 = 0)
# => y(x) = 0
f(C_1) := dsolve(y'(x) = y(x), y(x))
f(3)
# => y(x) = 3 e^x
dsolve(y'(x) = 0, y(x)); assumptions()
# => {C₁ ∈ ℝ, C₂ ∈ ℝ, C₃ ∈ ℝ, C₄ ∈ ℝ}

# Errors
sin'(x)
# !! the prime notation is for user functions
y'(x)
# !! y is not a function
dsolve(y'(x) = 1, 2)
# !! dsolve needs an unknown function
dsolve(x = 1, y(x))
# => dsolve(x = 1, y(x))
dsolve(y(x), y(x))
# !! dsolve needs an equation
dsolve(y'(x) = y(x), y(x), y(x) = 1)
# !! an initial condition is

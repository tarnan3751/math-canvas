# Multivariable calculus: partial and mixed derivatives, the ∂ notation
# (docs/reference/engine.md, Differentiation and Multivariable calculus; docs/language.md §10.1,
# §14.10, §14.20).
# A golden corpus file: docs/reference/cli.md describes the format.

# Partial derivatives: every other symbol is a constant
diff(x^2 y^3, y)
# => 3x² y²
∂/∂x x^2 y + y^2
# => 2x y + y²
∂²/∂x² sin(x y)
# => -y² sin(x y)
∂f/∂x
# => 0
∂f/∂x y
# => 0

# Successive variables, the rightmost denominator first
diff(x^2 y^3, x, y)
# => 6x y²
diff(x^2 y^3, x, y, y)
# => 12x y
∂²/∂x∂y (x^2 y^3)
# => 6x y²
∂³/∂x²∂y x^3 y^2
# => 12x y
expand(diff(e^(x y), x, y) - diff(e^(x y), y, x))
# => 0

# The order is a literal integer only; a name is a variable, whatever its value
n := 2
diff(x^3, x, n)
# => 0
clear(n)
# => n
p(x, y) := diff(x^3 y^2, x, y)
# => p(x, y) := ∂²/∂y∂x x³ y²
p(1, 2)
# => 12
q(y) := diff(x^3 y^2, x, y)
q(2)
# => 12x²
subs(diff(x^3 y^2, x, y), y = 2)
# => 12x²

# Every variable is free in the result
x := 2
diff(x^2 y^3, x, y)
# => 12y²
clear(x)
# => x

# What stays is printed with ∂, one mixed head
diff((x y)!, x, y)
# => ∂²/∂y∂x (x y)!
diff((x y)!, x)
# => ∂/∂x (x y)!
diff(x!, x)
# => d/dx x!

# Errors
∂²/∂x f
# !! the order must be the sum of the denominator's orders
∂sin/∂x
# !! ∂f/∂x needs a name for f; write ∂/∂x sin(…)

# The chain rule in several variables, through defined functions
f(u, v) := u^2 v
u(x, y) := x y
v(x, y) := x + y
steps(diff(f(u(x, y), v(x, y)), x))
# steps:
#   step 1: By the chain rule in several variables, the derivative of f(u, v) is fᵤ uₓ + fᵥ vₓ, where [fᵤ = 2u v, fᵥ = u²]: ∂/∂x x² y² (x + y) = x² y² ∂/∂x (x + y) + 2x y·(x + y) ∂/∂x x y
#   = x² y² ∂/∂x (x + y) + 2x y·(x + y) ∂/∂x x y
#   step 2: Differentiate the arguments, [uₓ = y, vₓ = 1]: x² y² + 2x y² (x + y)
#   = x² y² + 2x y² (x + y)
# => x² y² + 2x y² (x + y)
expand(diff(f(u(x, y), v(x, y)), y) - diff(x^2 y^2 (x + y), y))
# => 0
clear(f)
# => f
clear(u)
# => u
clear(v)
# => v

# A partial derivative's steps say what is held constant
steps(diff(x^2 y^3, x))
# steps:
#   step 1: Differentiate with respect to x, holding y constant. Take the constant factor y³ out of the derivative: ∂/∂x x² y³ = y³ d/dx x²
#   = y³ d/dx x²
#   step 2: Apply the power rule: d/dx x² = 2x
#   = 2x y³
# => 2x y³

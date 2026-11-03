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

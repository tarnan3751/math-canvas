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

# Vector calculus
grad(x^2 + y^2, [x, y])
# => ⟨2x, 2y⟩
grad(x^2 y + sin(z), ⟨x, y, z⟩)
# => ⟨2x y, x², cos(z)⟩
jacobian([x y, x + y^2], [x, y])
# => [[y, x], [1, 2y]]
jacobian([r cos(θ), r sin(θ)], [r, θ])
# => [[cos(θ), -r sin(θ)], [sin(θ), r cos(θ)]]
det(jacobian([r cos(θ), r sin(θ)], [r, θ]))
# => r
hessian(x^3 y^2, [x, y])
# => [[6x y², 6x² y], [6x² y, 2x³]]
divergence([x^2, x y, z], [x, y, z])
# => 3x + 1
curl([-y, x, 0], [x, y, z])
# => ⟨0, 0, 2⟩
curl([-y, x], [x, y])
# => 2
curl([y z, x z, x y], [x, y, z])
# => ⟨0, 0, 0⟩
laplacian(x^2 + y^2 + z^2, [x, y, z])
# => 6
laplacian(log(x^2 + y^2), [x, y])
# => 0
x := 1
grad(x^2 y, [x, y])
# => ⟨2y, 1⟩
clear(x)
# => x
steps(grad(x^2 y, [x, y]))
# steps:
#   step 1: The gradient is the vector of the partial derivatives, [∂/∂x x² y = 2x y, ∂/∂y x² y = x²]: ⟨2x y, x²⟩
#   = ⟨2x y, x²⟩
# => ⟨2x y, x²⟩
grad([x], [x])
# !! grad needs a scalar function
jacobian(x, [x])
# !! jacobian needs a list of functions
divergence([x, y], [x])
# !! divergence needs a field and variables of one length
curl([x], [x])
# !! curl needs a field of 2 or 3 components and as many variables
grad(x, [x, x])
# !! grad needs a list of distinct variables

# Lagrange multipliers
lagrange(x^2 + y^2, [x + y = 1], [x, y])
# => {[x = 1/2, y = 1/2, λ = 1]}
lagrange(x y, [x^2 + y^2 = 1], [x, y])
# => {[x = -√2/2, y = -√2/2, λ = 1/2], [x = -√2/2, y = √2/2, λ = -1/2], [x = √2/2, y = -√2/2, λ = -1/2], [x = √2/2, y = √2/2, λ = 1/2]}
lagrange(x + y + z, [x^2 + y^2 + z^2 = 3], [x, y, z])
# => {[x = -1, y = -1, z = -1, λ = -1/2], [x = 1, y = 1, z = 1, λ = 1/2]}
lagrange(x^2 y, [x^2 + y^2 = 3], [x, y])
# => {[x = 0, y = √3, λ = 0], [x = 0, y = -√3, λ = 0], [x = √2, y = -1, λ = -1], [x = √2, y = 1, λ = 1], [x = -√2, y = -1, λ = -1], [x = -√2, y = 1, λ = 1]}
lagrange(x y z, [x + y + z = 3, x - y = 0], [x, y, z])
# => {[x = 0, y = 0, z = 3, λ₁ = 0, λ₂ = 0], [x = 1, y = 1, z = 1, λ₁ = 1, λ₂ = 0]}
steps(lagrange(x^2 + y^2, [x + y = 1], [x, y]))
# steps:
#   step 1: Set the gradient of the function to the multipliers times the constraints' gradients, and keep the constraints: {2x = λ, 2y = λ, x + y = 1}
#   = {2x = λ, 2y = λ, x + y = 1}
#   step 2: Solve the system; the real solutions are the critical points: {[x = 1/2, y = 1/2, λ = 1]}
#   = {[x = 1/2, y = 1/2, λ = 1]}
# => {[x = 1/2, y = 1/2, λ = 1]}
λ := 2
lagrange(λ x y, [x + y = 2], [x, y])
# => {[x = 1, y = 1, λ₁ = 2]}
clear(λ)
# => λ
lagrange(y sin(x), [x + y = 1], [x, y])
# => lagrange(y sin(x), [x + y = 1], ⟨x, y⟩)
lagrange(x, [x], [x])
# !! lagrange needs constraints g = c in a list

# Review-26: a linear equation in one unknown with a constant coefficient, and real points only
solve({x^3 = 2, x y = 1}, [x, y])
# => {[x = 2^(1/3), y = 2^(2/3)/2], [x = (-i √3/2 - 1/2)·2^(1/3), y = -2^(2/3)/4 + i·2^(2/3) √3/4], [x = (i √3/2 - 1/2)·2^(1/3), y = -2^(2/3)/4 - i·2^(2/3) √3/4]}
solve({1 = a (4x^3 + 2x), x^4 + x^2 = 1}, [x, a])
# => {[x = √(-√5/2 - 1/2), a = -1/(-2√(-√5/2 - 1/2) - 4(-√5/2 - 1/2)^(3/2))], [x = -√(-√5/2 - 1/2), a = -1/(2√(-√5/2 - 1/2) + 4(-√5/2 - 1/2)^(3/2))], [x = √(√5/2 - 1/2), a = -1/(-2√(√5/2 - 1/2) - 4(√5/2 - 1/2)^(3/2))], [x = -√(√5/2 - 1/2), a = -1/(2√(√5/2 - 1/2) + 4(√5/2 - 1/2)^(3/2))]}
lagrange(x + y, [x^3 = 2, y = 0], [x, y])
# => {[x = 2^(1/3), y = 0, λ₁ = 2^(1/3)/6, λ₂ = 1]}
lagrange(x + y + z, [x^2 + y^2 = 1, z = x^2], [x, y, z])
# => {[x = √5/4 + √(√5 + 2)/4 - √(√5 + 2) √5/4 - 1/4, y = -√5/4 + √(√5 + 2)/4 - √(√5 + 2) √5/4 + 1/4, z = -3√(√5 + 2)/4 + √(√5 + 2) √5/4 + 1/2, λ₁ = -√(√5 + 2)/2 + 1/2, λ₂ = 1], [x = √5/4 - √(√5 + 2)/4 + √(√5 + 2) √5/4 - 1/4, y = -√5/4 - √(√5 + 2)/4 + √(√5 + 2) √5/4 + 1/4, z = 3√(√5 + 2)/4 - √(√5 + 2) √5/4 + 1/2, λ₁ = √(√5 + 2)/2 + 1/2, λ₂ = 1]}
solve({x^2 = y, y^2 = x}, [x, y])
# => {[x = 0, y = 0], [x = 1, y = 1], [x = -i √3/2 - 1/2, y = i √3/2 - 1/2], [x = i √3/2 - 1/2, y = -i √3/2 - 1/2]}

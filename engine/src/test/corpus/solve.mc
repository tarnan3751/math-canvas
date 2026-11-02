# Solving equations (docs/language.md §12.2, §14.7; docs/reference/engine.md, Solving).
# A golden corpus file: docs/reference/cli.md describes the format.

# Linear equations, with the steps a student writes
solve(2x + 3 = 7, x)
# => {2}
steps(solve(2x + 3 = x + 7, x))
# steps:
#   step 1: Collect the terms in the unknown
#   = x + 3 = 7
#   step 2: Move the constant terms to the right side
#   = x = 4
#   step 3: The solutions are {4}
#   = {4}
# => {4}
solve(x/2 + 1/3 = 1, x)
# => {4/3}
solve(a x + b = 0, x)
# => {-b/a}
solve(x = x + 1, x)
# => {}
solve(x = x, x)
# => solve(x = x, x)

# Rational equations: the denominator's zeros are excluded
solve(1/x = 2, x)
# => {1/2}
solve(1/x = 0, x)
# => {}
solve((x^2 - 1)/(x - 1) = 0, x)
# => {-1}
solve(x/(x - 1) = 1/(x - 1), x)
# => {}

# Polynomials: factoring first, then the formulas
solve(x^2 - 5x + 6 = 0, x)
# => {2, 3}
steps(solve(x^2 - 5x + 6 = 0, x))
# steps:
#   step 1: Factor the left side
#   = (x - 3)(x - 2) = 0
#   step 2: The solutions are {2, 3}
#   = {2, 3}
# => {2, 3}
steps(solve(x^2 - 2 = 0, x))
# steps:
#   step 1: The discriminant is 8; apply the quadratic formula
#   = [x = -√8/2, x = √8/2]
#   step 2: The solutions are {-√2, √2}
#   = {-√2, √2}
# => {-√2, √2}
x := 3; solve(x^2 = 4, x)
# => {-2, 2}
a := 2; solve(x^2 = a, x)
# => {-√2, √2}
clear(x); clear(a)
solve(x^2 - 2 = 0, x)
# => {-√2, √2}
steps(solve(x^2 + x - 1 = 0, x))
# steps:
#   step 1: The discriminant is 5; apply the quadratic formula
#   = [x = (-√5 - 1)/2, x = (√5 - 1)/2]
#   step 2: The solutions are {(-√5 - 1)/2, (√5 - 1)/2}
#   = {(-√5 - 1)/2, (√5 - 1)/2}
# => {(-√5 - 1)/2, (√5 - 1)/2}
solve(x^2 + 1 = 0, x)
# => {-i, i}
solve(x^2 + x + 1 = 0, x)
# => {(-i √3 - 1)/2, (i √3 - 1)/2}
solve(x^2 - (a + b) x + a b = 0, x)
# => {a, b}
solve(a x^2 + b x + c = 0, x)
# => {(-b + √(b² - 4a c))/(2a), (-b - √(b² - 4a c))/(2a)}
solve(x^4 - 1 = 0, x)
# => {-1, 1, -i, i}
solve((x - 1)^2 (x + 2) = 0, x)
# => {-2, 1}
solve(i^2 = 4, i)
# => {-2, 2}

# The cubic: three real roots in the trigonometric form; the binomial form; one real root
solve(x^3 - 3x + 1 = 0, x)
# => {-2 cos(π/9), 2 cos(4π/9), 2 cos(2π/9)}
solve(x^3 - 2 = 0, x)
# => {2^(1/3), (-i √3/2 - 1/2)·2^(1/3), (i √3/2 - 1/2)·2^(1/3)}
solve(x^3 + 3x + 1 = 0, x)
# => {(√5/2 - 1/2)^(1/3) - (√5/2 + 1/2)^(1/3), -i·((√5/2 - 1/2)^(1/3) + (√5/2 + 1/2)^(1/3)) √3/2 - ((√5/2 - 1/2)^(1/3) - (√5/2 + 1/2)^(1/3))/2, i·((√5/2 - 1/2)^(1/3) + (√5/2 + 1/2)^(1/3)) √3/2 - ((√5/2 - 1/2)^(1/3) - (√5/2 + 1/2)^(1/3))/2}

# The quartic: a biquadratic whose roots denest; Ferrari's method
solve(x^4 - 10x^2 + 1 = 0, x)
# => {-(√2 + √3), -(√3 - √2), -√2 + √3, √2 + √3}
solve(x^4 - 2 = 0, x)
# => {-2^(1/4), 2^(1/4), -i·2^(1/4), i·2^(1/4)}

# Linear systems
solve({x + y = 3, x - y = 1}, [x, y])
# => {[x = 2, y = 1]}
solve({x + y + z = 6, x - y + 2z = 5, 2x + a y - z = 1}, [x, y, z])
# => {[x = (7a - 14)/(a - 8), y = -14/(a - 8), z = (-a - 20)/(a - 8)]}
solve({x + y = 1, x + y = 2}, [x, y])
# => {}
solve(x + y = 3, [x, y])
# => {[x = -y + 3]}
solve({x + 1 = 2, 2x = 2}, x)
# => {1}
solve({x y = 1, x + y = 2}, [x, y])
# => solve({x y = 1, x + y = 2}, ⟨x, y⟩)

# The numeric fallback
numeric(solve(x^5 - x - 1 = 0, x), 10)
# => {1.167303978, -0.7648844336 - 0.352471546i, -0.7648844336 + 0.352471546i, 0.1812324445 - 1.083954101i, 0.1812324445 + 1.083954101i}
solve(x^5 - x - 1 = 0, x)
# => {1.167303978261418684256045899854842, -0.7648844336005847260298231877085417 - 0.3524715460317262493179470914025811i, -0.7648844336005847260298231877085417 + 0.3524715460317262493179470914025811i, 0.1812324444698753839018002377811206 - 1.083954101317710668430344492980767i, 0.1812324444698753839018002377811206 + 1.083954101317710668430344492980767i}
numeric(solve(x = cos(x), x), 15)
# => {0.739085133215161}
numeric(solve(e^x = 3x, x), 15)
# => {0.619061286735945, 1.51213455165784}
solve(sin(x) + sin(5x) = 1/2, x)
# => solve(sin(x) + sin(5x) = 1/2, x)
solve(e^x = 0, x)
# => solve(e^x = 0, x)

# Not yet solved, and errors
solve(x < 2, x)
# => solve(x < 2, x)
solve(x, x)
# !! solve needs an equation or a set of equations
solve(x = 1, [x, x])
# !! solve needs a variable or a list of distinct variables

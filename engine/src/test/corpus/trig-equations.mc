# Trigonometric equations with general solutions, and degrees, minutes and seconds
# (docs/language.md §14.7, §14.11, §14.18; docs/reference/engine.md, Solving). A golden corpus
# file: docs/reference/cli.md describes the format. This file runs in radians; the answers in
# degrees and gradians are TrigEquationTest's.

# One function of the unknown: the inverse function and the period
solve(sin(x) = 1/2, x)
# => {2πk + π/6, 2πk + 5π/6}, k ∈ ℤ
steps(solve(sin(x) = 1/2, x))
# steps:
#   step 1: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk + π/6, x = 2πk + 5π/6]
#   step 2: The solutions are {2πk + π/6, 2πk + 5π/6}
#   = {2πk + π/6, 2πk + 5π/6}
# => {2πk + π/6, 2πk + 5π/6}, k ∈ ℤ
solve(sin(x) = -1/2, x)
# => {2πk + 7π/6, 2πk + 11π/6}, k ∈ ℤ
solve(cos(x) = 1/2, x)
# => {2πk + π/3, 2πk + 5π/3}, k ∈ ℤ
solve(tan(x) = 1, x)
# => {πk + π/4}, k ∈ ℤ
solve(cot(x) = √3, x)
# => {πk + π/6}, k ∈ ℤ
solve(sec(x) = 2, x)
# => {2πk + π/3, 2πk + 5π/3}, k ∈ ℤ
solve(csc(x) = -2, x)
# => {2πk + 7π/6, 2πk + 11π/6}, k ∈ ℤ
solve(sin(x) = 1/3, x)
# => {2πk + asin(1/3), 2πk + π - asin(1/3)}, k ∈ ℤ
solve(tan(x) = -2, x)
# => {πk + π - atan(2)}, k ∈ ℤ

# Families half a period apart merge; values out of range have no real solution
solve(sin(x) = 0, x)
# => {πk}, k ∈ ℤ
solve(cos(x) = 0, x)
# => {πk + π/2}, k ∈ ℤ
solve(cos(x) = -1, x)
# => {2πk + π}, k ∈ ℤ
solve(sin(x) = 2, x)
# => {}
solve(sec(x) = 1/2, x)
# => {}
solve(sin(x)^2 = -1, x)
# => {}

# Linear arguments
solve(sin(2x) = 1/2, x)
# => {πk + π/12, πk + 5π/12}, k ∈ ℤ
solve(cos(x/2 + π/3) = 0, x)
# => {2πk + π/3}, k ∈ ℤ
solve(tan(3x - π/4) = 1, x)
# => {πk/3 + π/6}, k ∈ ℤ
solve(sin(x + 1) = 1, x)
# => {2πk + π/2 - 1}, k ∈ ℤ

# A polynomial in one function: each root back-substituted
solve(sin(x)^2 - sin(x) = 0, x)
# => {πk, 2πk + π/2}, k ∈ ℤ
steps(solve(2cos(x)^2 + cos(x) - 1 = 0, x))
# steps:
#   step 1: Factor the left side as a polynomial in the functions
#   = (cos(x) + 1)(2 cos(x) - 1) = 0
#   step 2: Solve for the function of the angle
#   = [cos(x) = -1, cos(x) = 1/2]
#   step 3: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk + π, x = 2πk + π/3, x = 2πk + 5π/3]
#   step 4: The solutions are {2πk + π, 2πk + π/3, 2πk + 5π/3}
#   = {2πk + π, 2πk + π/3, 2πk + 5π/3}
# => {2πk + π, 2πk + π/3, 2πk + 5π/3}, k ∈ ℤ
solve(tan(x)^2 = 3, x)
# => {πk + π/3, πk + 2π/3}, k ∈ ℤ
solve(4sin(x)^2 = 3, x)
# => {πk + π/3, πk + 2π/3}, k ∈ ℤ

# Several functions: written through sin and cos, then factored
steps(solve(sin(x) + cos(x) = 1, x))
# steps:
#   step 1: Combine the sine and the cosine into one sine of a shifted angle, of amplitude √2
#   = √2 sin(x + π/4) = 1
#   step 2: Solve for the function of the angle
#   = sin(x + π/4) = √2/2
#   step 3: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk, x = 2πk + π/2]
#   step 4: The solutions are {2πk, 2πk + π/2}
#   = {2πk, 2πk + π/2}
# => {2πk, 2πk + π/2}, k ∈ ℤ
solve(√3 sin(x) + cos(x) = 1, x)
# => {2πk, 2πk + 2π/3}, k ∈ ℤ
steps(solve(sin(2x) = sin(x), x))
# steps:
#   step 1: Write every function through sin(x) and cos(x)
#   = -sin(x) + 2 cos(x) sin(x) = 0
#   step 2: Factor the left side as a polynomial in the functions
#   = (2 cos(x) - 1) sin(x) = 0
#   step 3: Solve for the function of the angle
#   = [cos(x) = 1/2, sin(x) = 0]
#   step 4: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk + π/3, x = 2πk + 5π/3, x = πk]
#   step 5: The solutions are {πk, 2πk + π/3, 2πk + 5π/3}
#   = {πk, 2πk + π/3, 2πk + 5π/3}
# => {πk, 2πk + π/3, 2πk + 5π/3}, k ∈ ℤ
solve(cos(2x) = cos(x), x)
# => {2πk, 2πk + 2π/3, 2πk + 4π/3}, k ∈ ℤ
steps(solve(cos(2x) = sin(x), x))
# steps:
#   step 1: Write every function through sin(x) and cos(x)
#   = cos(x)² - sin(x) - sin(x)² = 0
#   step 2: Write the equation in one function through sin² + cos² = 1
#   = -sin(x) - 2 sin(x)² + 1 = 0
#   step 3: Solve for the function of the angle
#   = [sin(x) = -1, sin(x) = 1/2]
#   step 4: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk + 3π/2, x = 2πk + π/6, x = 2πk + 5π/6]
#   step 5: The solutions are {2πk + π/6, 2πk + 5π/6, 2πk + 3π/2}
#   = {2πk + π/6, 2πk + 5π/6, 2πk + 3π/2}
# => {2πk + π/6, 2πk + 5π/6, 2πk + 3π/2}, k ∈ ℤ
solve(sin(x) = cos(x), x)
# => {πk + π/4}, k ∈ ℤ
solve(sin(x)^2 - 3sin(x)cos(x) + 2cos(x)^2 = 0, x)
# => {πk + π/4, πk + asin(2√5/5)}, k ∈ ℤ
steps(solve(sin(x)cos(x) + sin(x) + cos(x) = 1, x))
# steps:
#   step 1: Substitute t = tan(x/2), and check the half turn, where t has no value, on its own
#   = 4 tan(x/2) - 2 tan(x/2)² - 2 tan(x/2)⁴ = 0
#   step 2: Solve for the function of the angle
#   = [tan(x/2) = 0, tan(x/2) = 1]
#   step 3: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk, x = 2πk + π/2]
#   step 4: The solutions are {2πk, 2πk + π/2}
#   = {2πk, 2πk + π/2}
# => {2πk, 2πk + π/2}, k ∈ ℤ

# Where the equation is undefined there is no solution, even when the rewriting cancels the
# denominator
steps(solve(tan(x) + sec(x) = 0, x))
# steps:
#   step 1: Write every function through sin(x) and cos(x)
#   = sin(x) + 1 = 0
#   step 2: Solve for the function of the angle
#   = sin(x) = -1
#   step 3: Solve each equation by the inverse function and the period, for every integer k
#   = [x = 2πk + 3π/2]
#   step 4: Remove the solutions where the equation is undefined
#   = []
#   step 5: The solutions are {}
#   = {}
# => {}
solve(sin(2x)/sin(x) = 2, x)
# => {}
solve(sin(4x)/sin(2x) = 2, x)
# => {}
solve(sin(2x)/sin(x) = 1, x)
# => {2πk + π/3, 2πk + 5π/3}, k ∈ ℤ
solve(cot(2x) = tan(x), x)
# => {πk + π/6, πk + 5π/6}, k ∈ ℤ
solve(tan(x) cos(x) = 1, x)
# => {}
solve(tan(x) = sin(x), x)
# => {πk}, k ∈ ℤ

# The parameter: noted, assumed an integer afterwards, fresh, and put in from a frame
simplify(cos(2π k))
# => 1
subs(solve(sin(x) = 1/2, x), k = 0)
# => {π/6, 5π/6}
subs(solve(sin(x) = 1/2, x), k = 1)
# => {13π/6, 17π/6}
numeric(solve(tan(x) = 1, x), 10)
# => {3.141592654k + 0.7853981634}, k ∈ ℤ
n := 1
solve(tan(x) = n, x)
# => {πk + π/4}, k ∈ ℤ
assumptions()
# => {k ∈ ℤ}
k := 5
solve(cos(x) = 1, x)
# => {2πk₁}, k₁ ∈ ℤ

# An input k: the parameter is k₁, and a frame value is the input's
subs(solve(sin(x) = k, x), k = 1/2)
# => {2πk₁ + π/6, 2πk₁ + 5π/6}, k₁ ∈ ℤ
f(k) := solve(sin(x) = k, x)
f(1/2)
# => {2πk₁ + π/6, 2πk₁ + 5π/6}, k₁ ∈ ℤ
# two solves in one statement: two parameters
[solve(sin(x) = 0, x), solve(cos(x) = 0, x)]
# => [{πk₁}, {πk₂ + π/2}], k₁ ∈ ℤ, k₂ ∈ ℤ

# Decimal values
solve(sin(x) = 1.0, x)
# => {2πk₁ + 1.570796326794896619231321691639751}, k₁ ∈ ℤ
solve(sin(x) = 0.5, x)
# => {2πk₁ + 0.5235987755982988730771072305465838, 2πk₁ + 2.617993877991494365385536152732919}, k₁ ∈ ℤ
solve(tan(x) = 1.0, x)
# => {πk₁ + 0.7853981633974483096156608458198757}, k₁ ∈ ℤ

# A symbolic value: answered for tan and cot only
solve(tan(x) = a, x)
# => {πk₁ + atan(a)}, k₁ ∈ ℤ
solve(sin(x) = a, x)
# => solve(sin(x) = a, x)

# What the trigonometric path does not take goes to the numeric scan
solve(sin(x) = x/2, x)
# => {-1.895494267033980947144035738093602, 0.0, 1.895494267033980947144035738093602}
solve(sin(x^2) = 0, x)
# => solve(sin(x²) = 0, x)
solve(sin(x) + sin(5x) = 1/2, x)
# => solve(sin(x) + sin(5x) = 1/2, x)

# Degrees, minutes and seconds
dms(30.2561)
# => ⟨30, 15, 21.96⟩
dms(61/2)
# => ⟨30, 30, 0⟩
dms(-1/2)
# => ⟨0, -30, 0⟩
dms(π/7)
# => ⟨25, 42, 360/7⟩
dms(30°15′20″)
# => ⟨30, 15, 20⟩
degrees(30, 15, 20)
# => 2723/90
degrees(-30, 30, 0)
# => -61/2
30°15′20″
# => 2723π/16200
sin(30°0′)
# => 1/2
degrees(30, -1, 0)
# !! only the first component of degrees that is not zero carries a sign

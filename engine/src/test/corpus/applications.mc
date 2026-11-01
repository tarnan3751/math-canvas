# The applications of integration and the optimisation helpers (docs/language.md §14.15):
# arclength, area, volume, shellvolume, extrema, maximize and minimize. Each sets its quantity
# up and hands the integral to integrate and the roots to solve.
# A golden corpus file: docs/reference/cli.md describes the format.

# Arc length of a graph and of a parametric curve
arclength(x^(3/2), x, 0, 1)
# => 13√13/27 - 8/27
arclength(cosh(x), x, 0, 1)
# => sinh(1)
arclength(2x + 1, x, 0, 3)
# => 3√5
arclength([cos(t), sin(t)], t, 0, pi)
# => π
arclength([3 cos(t), 3 sin(t)], t, 0, 2 pi)
# => 6π
arclength([t, 2t], t, 0, 1)
# => √5
assume(a > 0)
arclength(a x, x, 0, 1)
# => √(a² + 1)
clear(a)
# No closed form: the integral it set up, which numeric evaluates
arclength(x^3, x, 0, 1)
# => ∫[0, 1] √(9x⁴ + 1) dx
numeric(arclength(x^3, x, 0, 1), 20)
# => 1.5478656546836101448
# A parabola's, by the tangent substitution
arclength(x^2, x, 0, 1)
# => √5/2 + log(√5 + 2)/4
numeric(arclength(sin(x), x, 0, pi), 20)
# => 3.8201977890277120179
steps(arclength(x^(3/2), x, 0, 1))
# steps:
#   step 1: The arc length of a graph is the integral of √(1 + f′²): ∫[0, 1] √(9x/4 + 1) dx
#   = ∫[0, 1] √(9x/4 + 1) dx
#   step 2: Simplify the integrand: ∫[0, 1] √(9x + 4)/2 dx
#   = ∫[0, 1] √(9x + 4)/2 dx
#   step 3: An antiderivative is (9x + 4)^(3/2)/27; evaluate it at the bounds: ∫[0, 1] √(9x + 4)/2 dx = 13^(3/2)/27 - 8/27
#   = 13^(3/2)/27 - 8/27
#   step 4: Take the whole part of the exponent out: 13^(3/2) = 13√13
#   = 13√13/27 - 8/27
# => 13√13/27 - 8/27
steps(arclength([t, 2t], t, 0, 1))
# steps:
#   step 1: The arc length of a curve given as a pair is the integral of √(x′² + y′²): ∫[0, 1] √5 dt
#   = ∫[0, 1] √5 dt
#   step 2: An antiderivative is t √5; evaluate it at the bounds: ∫[0, 1] √5 dt = √5
#   = √5
# => √5

# Area: unsigned, split at the crossings when they are known exactly
area(x^2 - 1, x, -2, 2)
# => 4
area(x^3 - 2x, x, -2, 2)
# => 4
area(x, x^2, x, 0, 1)
# => 1/6
area(x^2, 2x, x, 0, 2)
# => 4/3
area(x^2, 2x, x, 0, 3)
# => 8/3
area(1/x - 1, x, 1/2, 2)
# => 1/2
area(sqrt(2) - x, x, 0, 2)
# => ∫[0, 2] |-x + √2| dx
area(x, x, 2, 2)
# => 0
area(2, x, 0, 1)
# => 2
# A pole is a sign change too: each piece ending at it is improper, and here each diverges
area(1/x, x, -1, 1)
# => undefined
steps(area(1/x, x, -1, 1))
# steps:
#   step 1: The area between the curves is the integral of the absolute value of their difference: ∫[-1, 1] |1/x| dx
#   = ∫[-1, 1] |1/x| dx
#   step 2: The difference can change sign only where it vanishes or is undefined, at {0}, and the integral of a piece there diverges, so the area is undefined
#   = undefined
# => undefined
steps(area(x^2 - 1, x, -2, 2))
# steps:
#   step 1: The area between the curves is the integral of the absolute value of their difference: ∫[-2, 2] |x² - 1| dx
#   = ∫[-2, 2] |x² - 1| dx
#   step 2: The difference can change sign only where it vanishes or is undefined, at {-1, 1}, so the area is the sum of the unsigned pieces [|4/3|, |-4/3|, |4/3|]
#   = |-4/3| + 2|4/3|
#   step 3: Take the absolute values and add: 4
#   = 4
# => 4
# A number is distributed over a sum, as for a definite integral's value
steps(area(x^2 - 2, x, -2, 2))
# steps:
#   step 1: The area between the curves is the integral of the absolute value of their difference: ∫[-2, 2] |x² - 2| dx
#   = ∫[-2, 2] |x² - 2| dx
#   step 2: The difference can change sign only where it vanishes or is undefined, at {-√2, √2}, so the area is the sum of the unsigned pieces [|4√2/3 - 4/3|, |-8√2/3|, |4√2/3 - 4/3|]
#   = |-8√2/3| + 2|4√2/3 - 4/3|
#   step 3: Take the absolute values and add: 8√2/3 + 2(4√2/3 - 4/3)
#   = 8√2/3 + 2(4√2/3 - 4/3)
#   step 4: Multiply the constant 2 into the sum: 8√2/3 + 2(4√2/3 - 4/3) = 16√2/3 - 8/3
#   = 16√2/3 - 8/3
# => 16√2/3 - 8/3
volume(x^2 - 2, 1, x, -2, 2)
# => π·(-16√3/5 + 164/15)
# Crossings not known exactly: the integral of the absolute value
area(sin(x), x, 0, 3)
# => ∫[0, 3] |sin(x)| dx
numeric(area(sin(x), x, 0, 3), 15)
# => 1.98999249660045
area(x^5 - x - 1, x, 0, 2)
# => ∫[0, 2] |x⁵ - x - 1| dx
steps(area(sin(x), x, 0, 3))
# steps:
#   step 1: The crossings are not all known exactly, so the area is the integral of the absolute value: ∫[0, 3] |sin(x)| dx
#   = ∫[0, 3] |sin(x)| dx
#   step 2: No antiderivative found by the table, linearity, the trigonometric identities, completing the square, substitution or integration by parts: ∫[0, 3] |sin(x)| dx stays
#   = ∫[0, 3] |sin(x)| dx
# => ∫[0, 3] |sin(x)| dx
# The integral of the absolute value finishes when the absolute value goes
area(t, x, 0, 1)
# => |t|
# An order of the bounds that cannot be decided leaves the call
area(x^2, x, a, b)
# => area(x², x, a, b)
steps(area(x^2, x, a, b))
# steps:
#   step 1: The order of the bounds cannot be decided, so area(x², x, a, b) stays
#   = area(x², x, a, b)
# => area(x², x, a, b)

# Volumes of revolution about the x-axis (discs, washers) and the y-axis (shells)
volume(sqrt(x), x, 0, 4)
# => 8π
volume(sqrt(1 - x^2), x, -1, 1)
# => 4π/3
volume(x, x, 0, 3)
# => 9π
assume(h > 0)
volume(r, x, 0, h)
# => πh r²
clear(h)
volume(x, x^2, x, 0, 1)
# => 2π/15
volume(2x, x^2, x, 0, 2)
# => 64π/15
volume(x, -x, x, 0, 1)
# => 0
steps(volume(sqrt(x), x, 0, 4))
# steps:
#   step 1: By discs, the volume of revolution about the x-axis is π times the integral of the radius squared: ∫[0, 4] πx dx
#   = ∫[0, 4] πx dx
#   step 2: An antiderivative is πx²/2; evaluate it at the bounds: ∫[0, 4] πx dx = 8π
#   = 8π
# => 8π
steps(volume(x, x^2, x, 0, 1))
# steps:
#   step 1: By washers, the volume of revolution about the x-axis is π times the integral of the difference of the radii squared: ∫[0, 1] π |-x⁴ + x²| dx
#   = ∫[0, 1] π |-x⁴ + x²| dx
#   step 2: The difference of the squares does not change sign between the bounds, so the volume is the absolute value of the integral: π |2/15|
#   = π |2/15|
#   step 3: Take the absolute values and add: 2π/15
#   = 2π/15
# => 2π/15
shellvolume(x^2, x, 0, 2)
# => 8π
shellvolume(sqrt(4 - x^2), x, 0, 2)
# => ∫[0, 2] 2π |x| √(|-x² + 4|) dx
shellvolume(x - x^2, x, 0, 2)
# => 3π
shellvolume(1, x, 0, 1)
# => π
steps(shellvolume(x^2, x, 0, 2))
# steps:
#   step 1: By shells, the volume of revolution about the y-axis is 2π times the integral of x times the height: ∫[0, 2] 2π |x³| dx
#   = ∫[0, 2] 2π |x³| dx
#   step 2: The height does not change sign between the bounds, so the volume is the absolute value of the integral: 2π |4|
#   = 2π |4|
#   step 3: Take the absolute values and add: 8π
#   = 8π
# => 8π

# The variable is bound
x := 3
area(x, x^2, x, 0, 1)
# => 1/6
arclength(x^(3/2), x, 0, 1)
# => 13√13/27 - 8/27
clear(x)
f(y) := area(x y, x, 0, 1)
f(2)
# => 1

# Extrema: critical points classified by the second derivative or the first-derivative test
extrema(x^3 - 3x, x)
# => {[-1, 2], [1, -2]}
extrema(x^4, x)
# => {[0, 0]}
extrema(x^3, x)
# => {}
extrema(x^4 - 2x^2, x)
# => {[-1, -1], [0, 0], [1, -1]}
extrema(x + 1/x, x)
# => {[-1, -2], [1, 2]}
extrema(1/x, x)
# => {}
extrema(x^5 - 5x, x)
# => {[-1, 4], [1, -4]}
extrema(x^5 - x - 1, x)
# => {[-5^(3/4)/5, 4·5^(3/4)/25 - 1], [5^(3/4)/5, -4·5^(3/4)/25 - 1]}
steps(extrema(x^3 - 3x, x))
# steps:
#   step 1: The derivative 3x² - 3 vanishes at {-1, 1}
#   = {-1, 1}
#   step 2: By the second derivative, or the sign of the derivative on either side: the minima are {[1, -2]}, the maxima {[-1, 2]}; {} are neither
#   = {[-1, 2], [1, -2]}
# => {[-1, 2], [1, -2]}
# The test either side never steps across a pole of f′ = (x − 1)³/(x − 1/2)
extrema((x - 1/2)^3/3 - 3(x - 1/2)^2/4 + 3(x - 1/2)/4 - log(x - 1/2)/8, x)
# => {[1, log(2)/8 + 11/48]}
# A derivative that never vanishes: no extrema
extrema(x, x)
# => {}
steps(extrema(x, x))
# steps:
#   step 1: The derivative 1 never vanishes, so there are no extrema: {}
#   = {}
# => {}
# Not provably complete: the call stays
extrema(sin(x), x)
# => extrema(sin(x), x)
extrema(x^2 + a x, x)
# => extrema(a x + x², x)
# A derivative identically 0: every point is critical
extrema(5, x)
# => extrema(5, x)
steps(extrema(sin(x), x))
# steps:
#   step 1: The critical points cannot all be found exactly, or every point is one, or their kinds cannot be decided, so extrema(sin(x), x) stays
#   = extrema(sin(x), x)
# => extrema(sin(x), x)

# Global extrema on a closed interval: the ends and the critical points inside
maximize(x^3 - 3x, x, -3, 3)
# => [3, 18]
minimize(x^3 - 3x, x, -3, 3)
# => [-3, -18]
maximize(x^3 - 3x, x, -3/2, 3/2)
# => [-1, 2]
minimize(x^2 - 2x, x, 0, 3)
# => [1, -1]
maximize(x + 1/x, x, 1/2, 3)
# => [3, 10/3]
minimize(x + 1/x, x, 1/2, 3)
# => [1, 2]
# A tie goes to the leftmost point
maximize(x^2, x, -1, 1)
# => [-1, 1]
minimize(x^3, x, 2, 2)
# => [2, 8]
steps(maximize(x^3 - 3x, x, -3, 3))
# steps:
#   step 1: The candidates are the ends of the interval and the critical points inside it: [[-3, -18], [-1, 2], [1, -2], [3, 18]]
#   = [[-3, -18], [-1, 2], [1, -2], [3, 18]]
#   step 2: The largest value is at [3, 18]
#   = [3, 18]
# => [3, 18]
# Not continuous on the interval, or not provably complete: the call stays
maximize(1/x, x, -1, 1)
# => maximize(1/x, x, -1, 1)
minimize(sin(x), x, 0, 3)
# => minimize(sin(x), x, 0, 3)
steps(maximize(1/x, x, -1, 1))
# steps:
#   step 1: The function is not continuous on the interval, or its critical points or their values cannot be compared, so maximize(1/x, x, -1, 1) stays
#   = maximize(1/x, x, -1, 1)
# => maximize(1/x, x, -1, 1)

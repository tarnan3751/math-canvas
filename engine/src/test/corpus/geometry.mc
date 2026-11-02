# The geometry solvers: the triangle solver, shape formulas, coordinate geometry and conics
# (docs/language.md §14.19; docs/reference/engine.md, Geometry solvers). A golden corpus file:
# docs/reference/cli.md describes the format. This file runs in radians; the angles in degrees
# are GeometryTest's.

# The triangle solver: every case
triangle(a = 3, b = 4, c = 5)
# => [a = 3, b = 4, c = 5, A = acos(4/5), B = acos(3/5), C = π/2]
steps(triangle(a = 3, b = 4, c = 5))
# steps:
#   step 1: Find A, B and C by the law of cosines
#   = [a = 3, b = 4, c = 5, A = acos(4/5), B = acos(3/5), C = π/2]
# => [a = 3, b = 4, c = 5, A = acos(4/5), B = acos(3/5), C = π/2]
triangle(a = 1, b = 1, c = 1)
# => [a = 1, b = 1, c = 1, A = π/3, B = π/3, C = π/3]
triangle(a = 3, b = 4, C = 90°)
# => [a = 3, b = 4, c = 5, A = asin(3/5), B = π/2 - asin(3/5), C = π/2]
steps(triangle(a = 3, b = 4, C = 90°))
# steps:
#   step 1: Find c by the law of cosines
#   = [a = 3, b = 4, c = 5, C = π/2]
#   step 2: Find A by the law of sines
#   = [a = 3, b = 4, c = 5, A = asin(3/5), C = π/2]
#   step 3: Find B from the angle sum, a half turn
#   = [a = 3, b = 4, c = 5, A = asin(3/5), B = π/2 - asin(3/5), C = π/2]
# => [a = 3, b = 4, c = 5, A = asin(3/5), B = π/2 - asin(3/5), C = π/2]
triangle(A = 30°, B = 60°, c = 2)
# => [a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]
steps(triangle(A = 30°, B = 60°, c = 2))
# steps:
#   step 1: Find C from the angle sum, a half turn
#   = [c = 2, A = π/6, B = π/3, C = π/2]
#   step 2: Find a and b by the law of sines
#   = [a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]
# => [a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]
triangle(A = 30°, C = 90°, a = 1)
# => [a = 1, b = √3, c = 2, A = π/6, B = π/3, C = π/2]
triangle(b = 2, A = π/4, C = π/4)
# => [a = √2, b = 2, c = √2, A = π/4, B = π/2, C = π/4]

# The ambiguous case: none, one or two triangles
triangle(a = 1, b = 4, A = 30°)
# => {}
triangle(a = 1, b = 2, A = 30°)
# => {[a = 1, b = 2, c = √3, A = π/6, B = π/2, C = π/3]}
steps(triangle(a = 3, b = 4, A = 30°))
# steps:
#   step 1: Two sides and the angle opposite one of them give 2 triangles: find B by the law of sines
#   = {[a = 3, b = 4, A = π/6, B = asin(2/3)], [a = 3, b = 4, A = π/6, B = π - asin(2/3)]}
#   step 2: Find C from the angle sum, a half turn
#   = {[a = 3, b = 4, A = π/6, B = asin(2/3), C = 5π/6 - asin(2/3)], [a = 3, b = 4, A = π/6, B = π - asin(2/3), C = -π/6 + asin(2/3)]}
#   step 3: Find c by the law of sines
#   = {[a = 3, b = 4, c = -6 sin(π/6 - asin(2/3)), A = π/6, B = π - asin(2/3), C = -π/6 + asin(2/3)], [a = 3, b = 4, c = 6 sin(5π/6 - asin(2/3)), A = π/6, B = asin(2/3), C = 5π/6 - asin(2/3)]}
# => {[a = 3, b = 4, c = -6 sin(π/6 - asin(2/3)), A = π/6, B = π - asin(2/3), C = -π/6 + asin(2/3)], [a = 3, b = 4, c = 6 sin(5π/6 - asin(2/3)), A = π/6, B = asin(2/3), C = 5π/6 - asin(2/3)]}
steps(triangle(a = 1, b = 4, A = 30°))
# steps:
#   step 1: No triangle has these parts
#   = {}
# => {}
triangle(a = 5, b = 4, A = 30°)
# => {[a = 5, b = 4, c = 10 sin(5π/6 - asin(2/5)), A = π/6, B = asin(2/5), C = 5π/6 - asin(2/5)]}
triangle(a = 5, b = 4, A = 120°)
# => {[a = 5, b = 4, c = 10√3 sin(π/3 - asin(2√3/5))/3, A = 2π/3, B = asin(2√3/5), C = π/3 - asin(2√3/5)]}
triangle(a = 4, b = 5, A = 120°)
# => {}

# No such triangle, a symbol, decimals, and errors
steps(triangle(a = 1, b = 2, c = 3))
# steps:
#   step 1: No triangle has these parts
#   = {}
# => {}
triangle(A = 100°, B = 90°, c = 2)
# => {}
triangle(a = 3, b = x, C = 90°)
# => triangle(a = 3, b = x, C = π/2)
numeric(triangle(a = 3, b = 4, c = 5), 10)
# => [a = 3.0, b = 4.0, c = 5.0, A = 0.6435011088, B = 0.927295218, C = 1.570796327]
triangle(a = 3.0, b = 4, C = 90°)
# => [a = 3.0, b = 4.0, c = 5.0, A = 0.6435011087932843868028092287173226, B = 0.9272952180016122324285124629224288, C = 1.570796326794896619231321691639751]
triangle(A = 30°, B = 60°, C = 90°)
# !! triangle needs a side
triangle(a = -1, b = 2, c = 2)
# !! triangle's sides must be positive
triangle(a = 1, b = 2, C = 200°)
# !! triangle's angles must be between 0 and a half turn
triangle(a = 1, a = 2, C = 20°)
# !! triangle's parts must be distinct

# Shape formulas
trianglearea(3, 4, 5)
# => 6
trianglearea(1, 2, 3)
# => 0
trianglearea(1, 1, 3)
# => undefined
trianglearea(a, b, c)
# => √((a + b + c)(-a + b + c)(a - b + c)(a + b - c))/4
circumradius(3, 4, 5)
# => 5/2
inradius(3, 4, 5)
# => 1
polygonarea([[0, 0], [4, 0], [4, 3]])
# => 6
polygonarea([[0, 0], [0, 3], [4, 0]])
# => -6
polygonarea([[0, 0], [2, 0], [2, 2], [0, 2]])
# => 4
polygonarea([[0, 0], [1, 1]])
# !! polygonarea needs at least three points [x, y]

# Coordinate geometry
distance([1, 2], [4, 6])
# => 5
distance([0, 0, 0], [1, 2, 2])
# => 3
distance([a, b], [c, d])
# => √((-a + c)² + (-b + d)²)
midpoint([1, 2], [4, 6])
# => ⟨5/2, 4⟩
slope([1, 2], [4, 6])
# => 4/3
slope([1, 2], [1, 6])
# => undefined
lineequation([1, 2], [3, 6])
# => y = 2x
lineequation([0, 1], [2, 0])
# => y = -x/2 + 1
lineequation([1, 2], [1, 6])
# => x = 1
circleequation([1, -2], 3)
# => (x - 1)² + (y + 2)² = 9
lineequation([1, 2], [1, 2])
# !! lineequation needs two distinct points
distance([1, 2], [1, 2, 3])
# !! distance needs two points of one dimension

# Conics from their definitions
parabola([0, 1], y = -1)
# => x² - 4y = 0
parabola([2, 3], x = -1)
# => y² - 6x - 6y + 12 = 0
conic([0, 0], x = 4, 1/2)
# => 3x² + 4y² + 8x - 16 = 0
conic([0, 0], x + y = 2, 2)
# => x² + 4x y + y² - 8x - 8y + 8 = 0
conic([0, 0], x = 4, 1.0)
# => y² + 8.0x - 16.0 = 0
ellipse([-3, 0], [3, 0], 10)
# => 16x² + 25y² - 400 = 0
ellipse([0, 0], [2, 2], 4)
# => 3x² - 2x y + 3y² - 4x - 4y - 4 = 0
hyperbola([-5, 0], [5, 0], 6)
# => 16x² - 9y² - 144 = 0
parabola([p, 0], x = -p)
# => 4p x - y² = 0
ellipse([-3, 0], [3, 0], 6)
# !! ellipse needs 2a greater than the distance between the foci
hyperbola([-3, 0], [3, 0], 6)
# !! hyperbola needs 2a between 0 and the distance between the foci
parabola([0, 0], x^2 = 4)
# !! parabola needs a directrix, a line such as y = -1

# Exact linear algebra and eigen-theory (docs/language.md §14.16).
# A golden corpus file: docs/reference/cli.md describes the format.

# Determinants: cofactors up to 3×3, Bareiss elimination above, either on request
det([[7]])
# => 7
det([[1, 2], [3, 4]])
# => -2
det([[a, b], [c, d]])
# => -b c + a d
steps(det([[1, 2], [3, 4]]))
# steps:
#   step 1: For a matrix of order 1 or 2, det [a] = a and det [[a, b], [c, d]] = ad − bc: det([[1, 2], [3, 4]]) = -2
#   = -2
# => -2
steps(det([[1, 2, 3], [4, 5, 6], [7, 8, 10]]))
# steps:
#   step 1: Expand along the first row by cofactors: det([[1, 2, 3], [4, 5, 6], [7, 8, 10]]) = 3 det([[4, 5], [7, 8]]) - 2 det([[4, 6], [7, 10]]) + det([[5, 6], [8, 10]])
#   = 3 det([[4, 5], [7, 8]]) - 2 det([[4, 6], [7, 10]]) + det([[5, 6], [8, 10]])
#   step 2: Work out the minors and add: -3
#   = -3
# => -3
steps(det([[1, 2, 3], [4, 5, 6], [7, 8, 10]], elimination))
# steps:
#   step 1: Clear the entry of R₂ below the pivot 1, dividing by the previous pivot 1: [[1, 2, 3], [0, -3, -6], [7, 8, 10]]
#   = [[1, 2, 3], [0, -3, -6], [7, 8, 10]]
#   step 2: Clear the entry of R₃ below the pivot 1, dividing by the previous pivot 1: [[1, 2, 3], [0, -3, -6], [0, -6, -11]]
#   = [[1, 2, 3], [0, -3, -6], [0, -6, -11]]
#   step 3: Clear the entry of R₃ below the pivot -3, dividing by the previous pivot 1: [[1, 2, 3], [0, -3, -6], [0, 0, -3]]
#   = [[1, 2, 3], [0, -3, -6], [0, 0, -3]]
#   step 4: The determinant is the last pivot -3 times (−1)^0, for the row swaps: -3
#   = -3
# => -3
det([[2, 0, 1, 3], [1, 1, 0, 2], [0, 3, 1, 1], [1, 0, 2, 1]])
# => -1
det([[2, 0, 1, 3], [1, 1, 0, 2], [0, 3, 1, 1], [1, 0, 2, 1]], cofactor)
# => -1
steps(det([[0, 1, 0], [1, 0, 0], [0, 0, 1]], elimination))
# steps:
#   step 1: Swap R₁ and R₂, which changes the sign of the determinant: [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
#   = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
#   step 2: The determinant is the last pivot 1 times (−1)^1, for the row swaps: -1
#   = -1
# => -1
steps(det([[0, 1, 2], [0, 3, 4], [0, 5, 6]], elimination))
# steps:
#   step 1: Column 1 has no pivot, so the determinant is 0
#   = 0
# => 0
det([[1, 2, 3], [4, 5, 6], [7, 8, 9]])
# => 0
det([[x, 1, 0], [1, x, 1], [0, 1, x]])
# => x³ - 2x
det(M)
# => det(M)
det(5)
# !! det needs a square matrix
det([[1, 2, 3]])
# !! det needs a square matrix
det([[1, 2], [3, 4]], minors)
# !! det's method must be cofactor or elimination

# Reduced row echelon form, every row operation recorded
steps(rref([[1, 2, 3], [4, 5, 6]]))
# steps:
#   step 1: R₂ ← R₂ − 4R₁: [[1, 2, 3], [0, -3, -6]]
#   = [[1, 2, 3], [0, -3, -6]]
#   step 2: Divide R₂ by -3 to make the pivot 1: [[1, 2, 3], [0, 1, 2]]
#   = [[1, 2, 3], [0, 1, 2]]
#   step 3: R₁ ← R₁ − 2R₂: [[1, 0, -1], [0, 1, 2]]
#   = [[1, 0, -1], [0, 1, 2]]
# => [[1, 0, -1], [0, 1, 2]]
steps(rref([[0, 1, 2], [1, 1, 1], [2, 3, 4]]))
# steps:
#   step 1: Swap R₁ and R₂: [[1, 1, 1], [0, 1, 2], [2, 3, 4]]
#   = [[1, 1, 1], [0, 1, 2], [2, 3, 4]]
#   step 2: R₃ ← R₃ − 2R₁: [[1, 1, 1], [0, 1, 2], [0, 1, 2]]
#   = [[1, 1, 1], [0, 1, 2], [0, 1, 2]]
#   step 3: R₁ ← R₁ − R₂: [[1, 0, -1], [0, 1, 2], [0, 1, 2]]
#   = [[1, 0, -1], [0, 1, 2], [0, 1, 2]]
#   step 4: R₃ ← R₃ − R₂: [[1, 0, -1], [0, 1, 2], [0, 0, 0]]
#   = [[1, 0, -1], [0, 1, 2], [0, 0, 0]]
# => [[1, 0, -1], [0, 1, 2], [0, 0, 0]]
steps(rref([[1, 2], [-1, 0]]))
# steps:
#   step 1: R₂ ← R₂ + R₁: [[1, 2], [0, 2]]
#   = [[1, 2], [0, 2]]
#   step 2: Divide R₂ by 2 to make the pivot 1: [[1, 2], [0, 1]]
#   = [[1, 2], [0, 1]]
#   step 3: R₁ ← R₁ − 2R₂: [[1, 0], [0, 1]]
#   = [[1, 0], [0, 1]]
# => [[1, 0], [0, 1]]
steps(rref([[a, 1], [1, 1]]))
# steps:
#   step 1: Divide R₁ by a to make the pivot 1: [[1, 1/a], [1, 1]]. This assumes a ≠ 0
#   = [[1, 1/a], [1, 1]]
#   step 2: R₂ ← R₂ − R₁: [[1, 1/a], [0, (a - 1)/a]]
#   = [[1, 1/a], [0, (a - 1)/a]]
#   step 3: Divide R₂ by (a - 1)/a to make the pivot 1: [[1, 1/a], [0, 1]]. This assumes (a - 1)/a ≠ 0
#   = [[1, 1/a], [0, 1]]
#   step 4: R₁ ← R₁ − R₂/a: [[1, 0], [0, 1]]
#   = [[1, 0], [0, 1]]
# => [[1, 0], [0, 1]]
steps(rref([[1, 0], [0, 1]]))
# steps:
#   step 1: The matrix is in reduced row echelon form: [[1, 0], [0, 1]]
#   = [[1, 0], [0, 1]]
# => [[1, 0], [0, 1]]
rref([[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]])
# => [[1, 2, 0, 1], [0, 0, 1, -1], [0, 0, 0, 0]]
rref([[2.0, 1, 1], [1, 1, 3]])
# => [[1, 0, -2.0], [0, 1, 5.0]]
rref(5)
# !! rref needs a matrix

# Rank, the null space and the column space
steps(rank([[1, 2], [2, 4]]))
# steps:
#   step 1: R₂ ← R₂ − 2R₁: [[1, 2], [0, 0]]
#   = [[1, 2], [0, 0]]
#   step 2: The rank is the number of pivots: 1
#   = 1
# => 1
rank([[0, 0], [0, 0]])
# => 0
rank([[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]])
# => 2
steps(nullspace([[1, 2], [2, 4]]))
# steps:
#   step 1: R₂ ← R₂ − 2R₁: [[1, 2], [0, 0]]
#   = [[1, 2], [0, 0]]
#   step 2: Each column without a pivot gives a basis vector of the null space: [[-2, 1]]
#   = [[-2, 1]]
# => [[-2, 1]]
nullspace([[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]])
# => [[-2, 1, 0, 0], [-1, 0, 1, 1]]
nullspace([[1, 2], [3, 4]])
# => []
steps(colspace([[1, 2, 1, 0], [2, 4, 0, 2], [3, 6, 1, 2]]))
# steps:
#   step 1: R₂ ← R₂ − 2R₁: [[1, 2, 1, 0], [0, 0, -2, 2], [3, 6, 1, 2]]
#   = [[1, 2, 1, 0], [0, 0, -2, 2], [3, 6, 1, 2]]
#   step 2: R₃ ← R₃ − 3R₁: [[1, 2, 1, 0], [0, 0, -2, 2], [0, 0, -2, 2]]
#   = [[1, 2, 1, 0], [0, 0, -2, 2], [0, 0, -2, 2]]
#   step 3: Divide R₂ by -2 to make the pivot 1: [[1, 2, 1, 0], [0, 0, 1, -1], [0, 0, -2, 2]]
#   = [[1, 2, 1, 0], [0, 0, 1, -1], [0, 0, -2, 2]]
#   step 4: R₁ ← R₁ − R₂: [[1, 2, 0, 1], [0, 0, 1, -1], [0, 0, -2, 2]]
#   = [[1, 2, 0, 1], [0, 0, 1, -1], [0, 0, -2, 2]]
#   step 5: R₃ ← R₃ + 2R₂: [[1, 2, 0, 1], [0, 0, 1, -1], [0, 0, 0, 0]]
#   = [[1, 2, 0, 1], [0, 0, 1, -1], [0, 0, 0, 0]]
#   step 6: The pivot columns {1, 3} of the matrix are a basis of its column space: [[1, 2, 3], [1, 0, 1]]
#   = [[1, 2, 3], [1, 0, 1]]
# => [[1, 2, 3], [1, 0, 1]]

# Inverses: the adjugate for 2×2, Gauss–Jordan on [A | I] above
steps(inverse([[1, 2], [3, 4]]))
# steps:
#   step 1: The inverse is the adjugate divided by the determinant -2: [[-2, 1], [3/2, -1/2]]
#   = [[-2, 1], [3/2, -1/2]]
# => [[-2, 1], [3/2, -1/2]]
steps(inverse([[a, b], [c, d]]))
# steps:
#   step 1: The inverse is the adjugate divided by the determinant -b c + a d: [[d/(-b c + a d), -b/(-b c + a d)], [-c/(-b c + a d), a/(-b c + a d)]]. This assumes -b c + a d ≠ 0
#   = [[d/(-b c + a d), -b/(-b c + a d)], [-c/(-b c + a d), a/(-b c + a d)]]
# => [[d/(-b c + a d), -b/(-b c + a d)], [-c/(-b c + a d), a/(-b c + a d)]]
steps(inverse([[2, 0, 1], [1, 1, 0], [0, 3, 1]]))
# steps:
#   step 1: Write the matrix beside the identity: [[2, 0, 1, 1, 0, 0], [1, 1, 0, 0, 1, 0], [0, 3, 1, 0, 0, 1]]
#   = [[2, 0, 1, 1, 0, 0], [1, 1, 0, 0, 1, 0], [0, 3, 1, 0, 0, 1]]
#   step 2: Divide R₁ by 2 to make the pivot 1: [[1, 0, 1/2, 1/2, 0, 0], [1, 1, 0, 0, 1, 0], [0, 3, 1, 0, 0, 1]]
#   = [[1, 0, 1/2, 1/2, 0, 0], [1, 1, 0, 0, 1, 0], [0, 3, 1, 0, 0, 1]]
#   step 3: R₂ ← R₂ − R₁: [[1, 0, 1/2, 1/2, 0, 0], [0, 1, -1/2, -1/2, 1, 0], [0, 3, 1, 0, 0, 1]]
#   = [[1, 0, 1/2, 1/2, 0, 0], [0, 1, -1/2, -1/2, 1, 0], [0, 3, 1, 0, 0, 1]]
#   step 4: R₃ ← R₃ − 3R₂: [[1, 0, 1/2, 1/2, 0, 0], [0, 1, -1/2, -1/2, 1, 0], [0, 0, 5/2, 3/2, -3, 1]]
#   = [[1, 0, 1/2, 1/2, 0, 0], [0, 1, -1/2, -1/2, 1, 0], [0, 0, 5/2, 3/2, -3, 1]]
#   step 5: Divide R₃ by 5/2 to make the pivot 1: [[1, 0, 1/2, 1/2, 0, 0], [0, 1, -1/2, -1/2, 1, 0], [0, 0, 1, 3/5, -6/5, 2/5]]
#   = [[1, 0, 1/2, 1/2, 0, 0], [0, 1, -1/2, -1/2, 1, 0], [0, 0, 1, 3/5, -6/5, 2/5]]
#   step 6: R₁ ← R₁ − R₃/2: [[1, 0, 0, 1/5, 3/5, -1/5], [0, 1, -1/2, -1/2, 1, 0], [0, 0, 1, 3/5, -6/5, 2/5]]
#   = [[1, 0, 0, 1/5, 3/5, -1/5], [0, 1, -1/2, -1/2, 1, 0], [0, 0, 1, 3/5, -6/5, 2/5]]
#   step 7: R₂ ← R₂ + R₃/2: [[1, 0, 0, 1/5, 3/5, -1/5], [0, 1, 0, -1/5, 2/5, 1/5], [0, 0, 1, 3/5, -6/5, 2/5]]
#   = [[1, 0, 0, 1/5, 3/5, -1/5], [0, 1, 0, -1/5, 2/5, 1/5], [0, 0, 1, 3/5, -6/5, 2/5]]
#   step 8: The left half is now the identity, so the right half is the inverse: [[1/5, 3/5, -1/5], [-1/5, 2/5, 1/5], [3/5, -6/5, 2/5]]
#   = [[1/5, 3/5, -1/5], [-1/5, 2/5, 1/5], [3/5, -6/5, 2/5]]
# => [[1/5, 3/5, -1/5], [-1/5, 2/5, 1/5], [3/5, -6/5, 2/5]]
[[2, 0, 1], [1, 1, 0], [0, 3, 1]]^-1
# => [[1/5, 3/5, -1/5], [-1/5, 2/5, 1/5], [3/5, -6/5, 2/5]]
[[1, 2], [3, 4]]^-2
# => [[11/2, -5/2], [-15/4, 7/4]]
A := [[1, 2], [3, 4]]
A * A^-1
# => [[1, 0], [0, 1]]
inverse([[1, 2], [2, 4]])
# !! the matrix is singular
inverse([[1, 2, 3]])
# !! inverse needs a square matrix

# Linear systems in matrix form
linsolve([[1, 2], [3, 4]], [5, 6])
# => ⟨-4, 9/2⟩
steps(linsolve([[1, 2], [2, 4]], [3, 6]))
# steps:
#   step 1: Write the augmented matrix [A | b]: [[1, 2, 3], [2, 4, 6]]
#   = [[1, 2, 3], [2, 4, 6]]
#   step 2: R₂ ← R₂ − 2R₁: [[1, 2, 3], [0, 0, 0]]
#   = [[1, 2, 3], [0, 0, 0]]
#   step 3: The columns without a pivot are free, with the parameters {t₁}: ⟨-2t₁ + 3, t₁⟩
#   = ⟨-2t₁ + 3, t₁⟩
# => ⟨-2t₁ + 3, t₁⟩
steps(linsolve([[1, 2], [2, 4]], [3, 7]))
# steps:
#   step 1: Write the augmented matrix [A | b]: [[1, 2, 3], [2, 4, 7]]
#   = [[1, 2, 3], [2, 4, 7]]
#   step 2: R₂ ← R₂ − 2R₁: [[1, 2, 3], [0, 0, 1]]
#   = [[1, 2, 3], [0, 0, 1]]
#   step 3: A row reads 0 = c with c not zero, so there is no solution: {}
#   = {}
# => {}
linsolve([[1, 2], [3, 4]], [[1, 0], [0, 1]])
# => [[-2, 1], [3/2, -1/2]]
linsolve([[1, 1, 1], [1, 2, 3]], [6, 14])
# => ⟨t₁ - 2, -2t₁ + 8, t₁⟩
linsolve([[1, 2], [3, 4]], [1, 2, 3])
# !! linsolve needs a matrix and a vector of matching height

# Gram–Schmidt, not normalised, and the norms
steps(gramschmidt([[1, 1, 0], [1, 0, 1], [2, 1, 1]]))
# steps:
#   step 1: Subtract from vector 2 its projections on the vectors before it: [[1, 1, 0], [1/2, -1/2, 1], [2, 1, 1]]
#   = [[1, 1, 0], [1/2, -1/2, 1], [2, 1, 1]]
#   step 2: Vector 3 lies in the span of the vectors before it and is dropped: [[1, 1, 0], [1/2, -1/2, 1]]
#   = [[1, 1, 0], [1/2, -1/2, 1]]
# => [[1, 1, 0], [1/2, -1/2, 1]]
gramschmidt([[1, 0], [0, 1]])
# => [[1, 0], [0, 1]]
gramschmidt([[1, i], [1, -i]])
# => [[1, i], [1, -i]]
gramschmidt([[1, 2], [3]])
# !! gramschmidt needs vectors of one length
norm([3, 4])
# => 5
norm([x, y])
# => √(x² + y²)
norm([1, -2, 2], 1)
# => 5
norm([1, -5, 3], inf)
# => 5
normalize([1, 1])
# => ⟨√2/2, √2/2⟩
normalize([0, 0])
# !! normalize needs a vector that is not zero
norm([1, 2], 3)
# !! norm's order must be 1, 2 or inf

# Shapes
transpose([[1, 2, 3], [4, 5, 6]])
# => [[1, 4], [2, 5], [3, 6]]
transpose([1, 2])
# => [[1, 2]]
trace([[1, 2], [3, 4]])
# => 5
trace([[a, b], [c, d]])
# => a + d
size([[1, 2, 3], [4, 5, 6]])
# => ⟨2, 3⟩
size([1, 2, 3])
# => ⟨3⟩
identity(3)
# => [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
zeros(2, 3)
# => [[0, 0, 0], [0, 0, 0]]
identity(0)
# !! identity needs a positive integer

# A symbolic right side: no solution unless it vanishes, and a negative symbolic multiple
steps(linsolve([[1, 2], [2, 4]], [a, b]))
# steps:
#   step 1: Write the augmented matrix [A | b]: [[1, 2, a], [2, 4, b]]
#   = [[1, 2, a], [2, 4, b]]
#   step 2: R₂ ← R₂ − 2R₁: [[1, 2, a], [0, 0, -2a + b]]
#   = [[1, 2, a], [0, 0, -2a + b]]
#   step 3: A row reads 0 = c with c not zero, so there is no solution: {}. This holds unless -2a + b = 0
#   = {}
# => {}
steps(rref([[1, -1/x], [0, 1]]))
# steps:
#   step 1: R₁ ← R₁ + R₂/x: [[1, 0], [0, 1]]
#   = [[1, 0], [0, 1]]
# => [[1, 0], [0, 1]]
# A symbolic inverse above 2×2 assumes its determinant is not zero
steps(inverse([[x, 1, 0], [0, y, 1], [0, 1, 1]]))
# steps:
#   step 1: Write the matrix beside the identity: [[x, 1, 0, 1, 0, 0], [0, y, 1, 0, 1, 0], [0, 1, 1, 0, 0, 1]]
#   = [[x, 1, 0, 1, 0, 0], [0, y, 1, 0, 1, 0], [0, 1, 1, 0, 0, 1]]
#   step 2: Divide R₁ by x to make the pivot 1: [[1, 1/x, 0, 1/x, 0, 0], [0, y, 1, 0, 1, 0], [0, 1, 1, 0, 0, 1]]. This assumes x ≠ 0
#   = [[1, 1/x, 0, 1/x, 0, 0], [0, y, 1, 0, 1, 0], [0, 1, 1, 0, 0, 1]]
#   step 3: Divide R₂ by y to make the pivot 1: [[1, 1/x, 0, 1/x, 0, 0], [0, 1, 1/y, 0, 1/y, 0], [0, 1, 1, 0, 0, 1]]. This assumes y ≠ 0
#   = [[1, 1/x, 0, 1/x, 0, 0], [0, 1, 1/y, 0, 1/y, 0], [0, 1, 1, 0, 0, 1]]
#   step 4: R₁ ← R₁ − R₂/x: [[1, 0, -1/(x y), 1/x, -1/(x y), 0], [0, 1, 1/y, 0, 1/y, 0], [0, 1, 1, 0, 0, 1]]
#   = [[1, 0, -1/(x y), 1/x, -1/(x y), 0], [0, 1, 1/y, 0, 1/y, 0], [0, 1, 1, 0, 0, 1]]
#   step 5: R₃ ← R₃ − R₂: [[1, 0, -1/(x y), 1/x, -1/(x y), 0], [0, 1, 1/y, 0, 1/y, 0], [0, 0, (y - 1)/y, 0, -1/y, 1]]
#   = [[1, 0, -1/(x y), 1/x, -1/(x y), 0], [0, 1, 1/y, 0, 1/y, 0], [0, 0, (y - 1)/y, 0, -1/y, 1]]
#   step 6: Divide R₃ by (y - 1)/y to make the pivot 1: [[1, 0, -1/(x y), 1/x, -1/(x y), 0], [0, 1, 1/y, 0, 1/y, 0], [0, 0, 1, 0, -1/(y - 1), y/(y - 1)]]. This assumes (y - 1)/y ≠ 0
#   = [[1, 0, -1/(x y), 1/x, -1/(x y), 0], [0, 1, 1/y, 0, 1/y, 0], [0, 0, 1, 0, -1/(y - 1), y/(y - 1)]]
#   step 7: R₁ ← R₁ + R₃/(x y): [[1, 0, 0, 1/x, -1/(x y - x), 1/(x y - x)], [0, 1, 1/y, 0, 1/y, 0], [0, 0, 1, 0, -1/(y - 1), y/(y - 1)]]
#   = [[1, 0, 0, 1/x, -1/(x y - x), 1/(x y - x)], [0, 1, 1/y, 0, 1/y, 0], [0, 0, 1, 0, -1/(y - 1), y/(y - 1)]]
#   step 8: R₂ ← R₂ − R₃/y: [[1, 0, 0, 1/x, -1/(x y - x), 1/(x y - x)], [0, 1, 0, 0, 1/(y - 1), -1/(y - 1)], [0, 0, 1, 0, -1/(y - 1), y/(y - 1)]]
#   = [[1, 0, 0, 1/x, -1/(x y - x), 1/(x y - x)], [0, 1, 0, 0, 1/(y - 1), -1/(y - 1)], [0, 0, 1, 0, -1/(y - 1), y/(y - 1)]]
#   step 9: The left half is now the identity, so the right half is the inverse: [[1/x, -1/(x y - x), 1/(x y - x)], [0, 1/(y - 1), -1/(y - 1)], [0, -1/(y - 1), y/(y - 1)]]. This assumes x y - x ≠ 0
#   = [[1/x, -1/(x y - x), 1/(x y - x)], [0, 1/(y - 1), -1/(y - 1)], [0, -1/(y - 1), y/(y - 1)]]
# => [[1/x, -1/(x y - x), 1/(x y - x)], [0, 1/(y - 1), -1/(y - 1)], [0, -1/(y - 1), y/(y - 1)]]


# The characteristic polynomial, by Berkowitz's division-free algorithm
charpoly([[1, 2], [3, 4]], x)
# => x² - 5x - 2
steps(charpoly([[2, 1, 0], [1, 2, 1], [0, 1, 2]], t))
# steps:
#   step 1: The characteristic polynomial is det(tI − A) = det([[t - 2, -1, 0], [-1, t - 2, -1], [0, -1, t - 2]]): t³ - 6t² + 10t - 4
#   = t³ - 6t² + 10t - 4
# => t³ - 6t² + 10t - 4
charpoly([[a, b], [c, d]], x)
# => -b c + a d - a x - d x + x²
charpoly([[x, 1], [0, 1]], x)
# !! charpoly's variable must not occur in the matrix

# Eigenvalues, each as often as its multiplicity; eigenvectors; diagonalization
steps(eigenvalues([[2, 1], [1, 2]]))
# steps:
#   step 1: The characteristic polynomial is det(λI − A) = det([[λ - 2, -1], [-1, λ - 2]]): λ² - 4λ + 3 = 0
#   = λ² - 4λ + 3 = 0
#   step 2: Its roots, each as often as its multiplicity, are the eigenvalues: ⟨1, 3⟩
#   = ⟨1, 3⟩
# => ⟨1, 3⟩
eigenvalues([[2, 1], [0, 2]])
# => ⟨2, 2⟩
eigenvalues([[0, -1], [1, 0]])
# => ⟨-i, i⟩
eigenvalues([[1, 1], [1, 0]])
# => ⟨-√5/2 + 1/2, √5/2 + 1/2⟩
eigenvalues([[2, 0, 0], [0, 3, 4], [0, 4, 9]])
# => ⟨1, 2, 11⟩
eigenvalues([[0, 1, 0], [0, 0, 1], [1, 3, 0]])
# => ⟨2 cos(π/9), -2 cos(2π/9), -2 cos(4π/9)⟩
steps(eigenvectors([[2, 1], [1, 2]]))
# steps:
#   step 1: The characteristic polynomial is det(λI − A) = det([[λ - 2, -1], [-1, λ - 2]]): λ² - 4λ + 3 = 0
#   = λ² - 4λ + 3 = 0
#   step 2: Its roots, each as often as its multiplicity, are the eigenvalues: ⟨1, 3⟩
#   = ⟨1, 3⟩
#   step 3: For λ = 1, the eigenvectors are the null space of A − λI: [[1, [[-1, 1]]]]
#   = [[1, [[-1, 1]]]]
#   step 4: For λ = 3, the eigenvectors are the null space of A − λI: [[1, [[-1, 1]]], [3, [[1, 1]]]]
#   = [[1, [[-1, 1]]], [3, [[1, 1]]]]
# => [[1, [[-1, 1]]], [3, [[1, 1]]]]
eigenvectors([[2, 1], [0, 2]])
# => [[2, [[1, 0]]]]
eigenvectors([[3, 0], [0, 3]])
# => [[3, [[1, 0], [0, 1]]]]
eigenvectors([[1, 1], [1, 0]])
# => [[-√5/2 + 1/2, [[-√5/2 + 1/2, 1]]], [√5/2 + 1/2, [[√5/2 + 1/2, 1]]]]
eigenvectors([[0, -1], [1, 0]])
# => [[-i, [[-i, 1]]], [i, [[i, 1]]]]
diagonalize([[2, 1], [1, 2]])
# => [[[-1, 1], [1, 1]], [[1, 0], [0, 3]]]
steps(diagonalize([[2, 1], [0, 2]]))
# steps:
#   step 1: The characteristic polynomial is det(λI − A) = det([[λ - 2, -1], [0, λ - 2]]): λ² - 4λ + 4 = 0
#   = λ² - 4λ + 4 = 0
#   step 2: Its roots, each as often as its multiplicity, are the eigenvalues: ⟨2, 2⟩
#   = ⟨2, 2⟩
#   step 3: For λ = 2, the eigenvectors are the null space of A − λI: [[2, [[1, 0]]]]
#   = [[2, [[1, 0]]]]
#   step 4: The number of independent eigenvectors, 1, is less than 2, so the matrix is not diagonalizable and diagonalize([[2, 1], [0, 2]]) is left as it is
#   = diagonalize([[2, 1], [0, 2]])
# => diagonalize([[2, 1], [0, 2]])
eigenvalues([[a, b], [b, a]])
# => ⟨a + |b|, a - |b|⟩
eigenvectors([[a, b], [b, a]])
# => [[a + |b|, [[b/|b|, 1]]], [a - |b|, [[-b/|b|, 1]]]]
eigenvalues([[2.0, 1], [1, 2]])
# => ⟨1.0, 3.0⟩
eigenvalues(M)
# => eigenvalues(M)
eigenvalues([[1, 2, 3]])
# !! eigenvalues needs a square matrix

# Irrational and complex entries; the conditions of a symbolic eigenspace; decimal multiplicities
eigenvectors([[0, √2], [√2, 0]])
# => [[√2, [[1, 1]]], [-√2, [[-1, 1]]]]
diagonalize([[0, i], [i, 0]])
# => [[[-1, 1], [1, 1]], [[-i, 0], [0, i]]]
steps(diagonalize([[a, 0], [1, b]]))
# steps:
#   step 1: The characteristic polynomial is det(λI − A) = det([[-a + λ, 0], [-1, -b + λ]]): a b - a λ - b λ + λ² = 0
#   = a b - a λ - b λ + λ² = 0
#   step 2: Its roots, each as often as its multiplicity, are the eigenvalues: ⟨a, b⟩
#   = ⟨a, b⟩
#   step 3: For λ = a, the eigenvectors are the null space of A − λI: [[a, [[a - b, 1]]]]
#   = [[a, [[a - b, 1]]]]
#   step 4: For λ = b, the eigenvectors are the null space of A − λI: [[a, [[a - b, 1]]], [b, [[0, 1]]]]. This assumes a - b ≠ 0
#   = [[a, [[a - b, 1]]], [b, [[0, 1]]]]
#   step 5: P has the eigenvectors as its columns and D the eigenvalues on its diagonal, so P⁻¹AP = D: [[[a - b, 0], [1, 1]], [[a, 0], [0, b]]]. This assumes a - b ≠ 0
#   = [[[a - b, 0], [1, 1]], [[a, 0], [0, b]]]
# => [[[a - b, 0], [1, 1]], [[a, 0], [0, b]]]
eigenvalues([[1.0, 0], [0, 1.0]])
# => ⟨1.0, 1.0⟩
eigenvalues([[2.0, 1], [0, 2.0]])
# => ⟨2.0, 2.0⟩
diagonalize([[2.0, 1], [0, 2.0]])
# => diagonalize([[2.0, 1], [0, 2.0]])

# A quintic the solver finds only numerically: the call stays, and numeric(…) gives decimals
eigenvalues([[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]])
# => eigenvalues([[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]])
steps(numeric(eigenvalues([[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]]), 10))
# steps:
#   step 1: The characteristic polynomial is det(λI − A) = det([[λ, -1, 0, 0, 0], [0, λ, -1, 0, 0], [0, 0, λ, -1, 0], [0, 0, 0, λ, -1], [-1, -1, 0, 0, λ]]): λ⁵ - λ - 1 = 0
#   = λ⁵ - λ - 1 = 0
#   step 2: The roots of the characteristic polynomial λ⁵ - λ - 1 cannot be found exactly, so eigenvalues([[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]]) is left as it is
#   = eigenvalues([[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [0, 0, 0, 1, 0], [0, 0, 0, 0, 1], [1, 1, 0, 0, 0]])
#   step 3: The roots cannot be found exactly, so the eigenvalues are approximate, to 10 digits: ⟨1.167303978, -0.7648844336 - 0.352471546i, -0.7648844336 + 0.352471546i, 0.1812324445 - 1.083954101i, 0.1812324445 + 1.083954101i⟩
#   = ⟨1.167303978, -0.7648844336 - 0.352471546i, -0.7648844336 + 0.352471546i, 0.1812324445 - 1.083954101i, 0.1812324445 + 1.083954101i⟩
# => ⟨1.167303978, -0.7648844336 - 0.352471546i, -0.7648844336 + 0.352471546i, 0.1812324445 - 1.083954101i, 0.1812324445 + 1.083954101i⟩

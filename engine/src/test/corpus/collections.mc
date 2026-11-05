# Sets, lists, vectors and matrices (docs/language.md §13).
# A golden corpus file: docs/reference/cli.md describes the format.

# The bracket constructor
[1, 2, 3]
# => ⟨1, 2, 3⟩
⟨1, 2⟩
# => ⟨1, 2⟩
[[1, 2], [3, 4]]
# => [[1, 2], [3, 4]]
[[1, 2], [3]]
# => [⟨1, 2⟩, ⟨3⟩]
[x = 1, y = 2]
# => [x = 1, y = 2]
[]
# => []
{a, b, a}
# => {a, b}
{}
# => {}
⟨⟩
# !! a vector's entries must be single values, not lists or vectors
v[1]
# !! brackets after a value do not pick an item: write `nth(v, 1)`

# Vector arithmetic, the dot and cross products
[1, 2] + [3, 4]
# => ⟨4, 6⟩
[1, 2] - [1, 2]
# => ⟨0, 0⟩
[x, 2y] + [y, x]
# => ⟨x + y, x + 2y⟩
2·[1, 2]
# => ⟨2, 4⟩
[1, 2] * [3, 4]
# => 11
[1, 2] · [3, 4]
# => 11
dot([1, 2], [3, 4])
# => 11
[a, b] * [c, d]
# => a c + b d
[1, 0, 0] × [0, 1, 0]
# => ⟨0, 0, 1⟩
cross([1, 2, 3], [4, 5, 6])
# => ⟨-3, 6, -3⟩

# Matrix products and powers
[[1, 2], [3, 4]] * [[5, 6], [7, 8]]
# => [[19, 22], [43, 50]]
[[1, 2], [3, 4]] * [1, 1]
# => ⟨3, 7⟩
[[1, 2], [3, 4]]^2
# => [[7, 10], [15, 22]]
[[1, 2], [3, 4]]^0
# => [[1, 0], [0, 1]]
[[1, 2], [3, 4]]^-1
# => [[-2, 1], [3/2, -1/2]]
[[1, 2], [3, 4]]^-2
# => [[11/2, -5/2], [-15/4, 7/4]]
[[1, 2], [2, 4]]^-1
# !! the matrix is singular

# Items and lengths
nth([5, 6, 7], 2)
# => 6
nth([[1, 2], [3, 4]], 2)
# => ⟨3, 4⟩
length({1, 2, 2})
# => 2

# Errors of shape and kind
[1, 2] + [1, 2, 3]
# !! cannot add vectors of lengths 2 and 3
1 + [1, 2]
# !! cannot add a scalar and a vector
[1, 2]^2
# !! a vector has no powers
[1, 2] × [3, 4]
# !! × needs two 3-vectors
[[1, 2], [3, 4]] * [[1, 2, 3]]
# !! cannot multiply a 2×2 matrix by a 1×3 matrix
dot([1, 2], 3)
# !! `dot` needs two vectors of the same length
nth([1, 2], 3)
# !! `nth`: index 3 is out of range 1 to 2
length(3)
# !! `length` needs a list, a vector or a set
{1, 2} + 1
# !! sets cannot be added, multiplied or raised to powers
true^2
# !! true and false cannot be added, multiplied or raised to powers

# Set operators and functions (§8.5, §14.24)
{1, 2} ∪ {2, 3}
# => {1, 2, 3}
{1, 2, 3} ∩ {2, 3, 4}
# => {2, 3}
{1, 2, 3} \ {2}
# => {1, 3}
symdiff({1, 2}, {2, 3})
# => {1, 3}
{1, 2} ∪ {3} ∩ {1, 3}
# => {1, 3}
subsets({1, 2})
# => {{}, {1}, {1, 2}, {2}}
cartesian({1, 2}, {a})
# => {⟨1, a⟩, ⟨2, a⟩}
size({1, 2, 3})
# => 3
{ifactor(12), 12}
# => {12}
size({ifactor(12), 12})
# => 1
{1} ∩ {}
# => {}
2 ∈ {1, 2}
# => true
5 ∈ {1, 2}
# => false
x ∈ {1, 2}
# => x ∈ {1, 2}
{x} ∩ {1}
# => {x} ∩ {1}
(A ∪ B) ∩ C
# => (A ∪ B) ∩ C
2 ∈ 5
# !! `∈` needs a set or a number domain on its right, as in `2 ∈ {1, 2}`
{1} ∪ 2
# !! `union` takes sets

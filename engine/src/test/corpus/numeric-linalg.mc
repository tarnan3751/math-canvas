# Numeric linear algebra: lu, qr, cholesky, svd, lstsq and expm through the numeric
# decomposition provider (docs/reference/engine.md, Numeric linear algebra; docs/language.md
# §14.17). A golden corpus file: docs/reference/cli.md describes the format.
#
# The entries are decimals computed in double precision, at most 16 digits, shown as the
# decomposition gave them.

# LU with partial pivoting: P·A = L·U
lu([[4, 3], [6, 3]])
# => [[[0.0, 1.0], [1.0, 0.0]], [[1.0, 0.0], [0.6666666666666666, 1.0]], [[6.0, 3.0], [0.0, 1.0]]]
lu([[2, 1, 1], [4, -6, 0], [-2, 7, 2]])
# => [[[0.0, 1.0, 0.0], [1.0, 0.0, 0.0], [0.0, 0.0, 1.0]], [[1.0, 0.0, 0.0], [0.5, 1.0, 0.0], [-0.5, 1.0, 1.0]], [[4.0, -6.0, 0.0], [0.0, 4.0, 1.0], [0.0, 0.0, 1.0]]]

# QR: A = Q·R, Q orthogonal
qr([[1, 2], [3, 4]])
# => [[[-0.3162277660168382, 0.9486832980505142], [-0.9486832980505138, -0.3162277660168382]], [[-3.16227766016838, -4.427188724235732], [0.0, 0.632455532033676]]]
qr([[1, 2], [3, 4], [5, 6]])
# => [[[-0.1690308509457032, 0.8970852271450604, 0.4082482904638636], [-0.50709255283711, 0.2760262237369421, -0.8164965809277258], [-0.8451542547285165, -0.3450327796711773, 0.4082482904638627]], [[-5.916079783099616, -7.437357441610944], [0.0, 0.828078671210824], [0.0, 0.0]]]

# Cholesky: A = L·Lᵀ
cholesky([[4, 2], [2, 3]])
# => [[2.0, 0.0], [1.0, 1.414213562373095]]
cholesky([[4, 12, -16], [12, 37, -43], [-16, -43, 98]])
# => [[2.0, 0.0, 0.0], [6.0, 1.0, 0.0], [-8.0, 5.0, 3.0]]

# The compact SVD: A = U·Σ·Vᵀ, the singular values descending
svd([[3, 0], [0, 4]])
# => [[[0.0, -1.0], [1.0, 0.0]], [[4.0, 0.0], [0.0, 3.0]], [[0.0, -1.0], [1.0, 0.0]]]
svd([[1, 2], [3, 4]])
# => [[[0.4045535848337569, 0.9145142956773045], [0.9145142956773045, -0.4045535848337569]], [[5.464985704219043, 0.0], [0.0, 0.3659661906262574]], [[0.5760484367663208, -0.8174155604703632], [0.8174155604703632, 0.5760484367663208]]]
svd([[1, 0, 1], [0, 1, 1]])
# => [[[0.7071067811865477, -0.7071067811865474], [0.7071067811865474, 0.7071067811865477]], [[1.732050807568877, 0.0], [0.0, 1.0]], [[0.408248290463863, -0.7071067811865475], [0.408248290463863, 0.7071067811865478], [0.816496580927726, 2.220446049250313e-16]]]

# Least squares: the line through (1, 1), (2, 2), (3, 2)
lstsq([[1, 1], [1, 2], [1, 3]], [1, 2, 2])
# => ⟨0.6666666666666671, 0.4999999999999998⟩
lstsq([[2, 1], [1, 3]], [3, 5])
# => ⟨0.8000000000000006, 1.4⟩

# The matrix exponential
expm([[0, 1], [0, 0]])
# => [[1.0, 1.0], [0.0, 1.0]]
expm([[1, 0], [0, 2]])
# => [[2.718281828459046, 0.0], [0.0, 7.389056098930651]]
expm([[0, -1], [1, 0]])
# => [[0.5403023058681397, -0.8414709848078965], [0.8414709848078964, 0.5403023058681397]]

# Closed entries are numbers; decimals and rationals too
lu([[1/3, π], [√2, 1]])
# => [[[0.0, 1.0], [1.0, 0.0]], [[1.0, 0.0], [0.2357022603955158, 1.0]], [[1.414213562373095, 1.0], [0.0, 2.905890393194277]]]
cholesky([[2.25, 0], [0, e]])
# => [[1.5, 0.0], [0.0, 1.648721270700128]]

# The scale does not matter: the singularity test is relative to the largest entry, and tiny or
# huge matrices decompose as well as others
lu([[10^12, 0], [0, 1]])
# => [[[1.0, 0.0], [0.0, 1.0]], [[1.0, 0.0], [0.0, 1.0]], [[1000000000000.0, 0.0], [0.0, 1.0]]]
qr([[10^-160, 0], [0, 10^-160]])
# => [[[-1.0, 0.0], [0.0, -1.0]], [[-1.0e-160, 0.0], [0.0, -1.0e-160]]]
cholesky([[4·10^200, 2·10^200], [2·10^200, 3·10^200]])
# => [[2.0e+100, 0.0], [1.0e+100, 1.414213562373095e+100]]

# The digits: at most 16, fewer under numeric
numeric(lu([[4, 3], [6, 3]]), 5)
# => [[[0.0, 1.0], [1.0, 0.0]], [[1.0, 0.0], [0.66667, 1.0]], [[6.0, 3.0], [0.0, 1.0]]]
nth(svd([[1, 2], [3, 4]]), 2)
# => [[5.464985704219043, 0.0], [0.0, 0.3659661906262574]]

# The step
steps(cholesky([[4, 2], [2, 3]]))
# steps:
#   step 1: Computed in double precision through the numeric decomposition provider, at most 16 digits
#   = [[2.0, 0.0], [1.0, 1.414213562373095]]
# => [[2.0, 0.0], [1.0, 1.414213562373095]]

# A symbolic matrix leaves the call, with a note
lu([[a, 1], [2, 3]])
# => lu([[a, 1], [2, 3]])
steps(svd(M))
# steps:
#   step 1: svd needs a numeric matrix: the call stays
#   = svd(M)
# => svd(M)
# An entry the format leaves unevaluated holds the integral's variable: the call stays
lu([[integrate(e^(x^2), x, 0, 1), 1], [1, 1]])
# => lu([[∫[0, 1] e^(x²) dx, 1], [1, 1]])
M := [[4, 3], [6, 3]]
# => M := [[4, 3], [6, 3]]
nth(lu(M), 3)
# => [[6.0, 3.0], [0.0, 1.0]]

# The errors
lu(5)
# !! lu needs a matrix
svd([1, 2])
# !! svd needs a matrix
lstsq([[1, 2]], 3)
# !! lstsq needs a matrix and a vector
lu([[1, 2], [2, 4]])
# !! lu needs a matrix that is not numerically singular
lu([[0, 0], [0, 0]])
# !! lu needs a matrix that is not numerically singular
lu([[1, 2, 3]])
# !! lu needs a square matrix
expm([[1, 2]])
# !! expm needs a square matrix
cholesky([[1, 2], [2, 1]])
# !! cholesky needs a symmetric positive-definite matrix
lstsq([[1, 2], [2, 4], [3, 6]], [1, 2, 3])
# !! lstsq needs a matrix of full numerical column rank
lstsq([[1, 1], [1, 2]], [1, 2, 3])
# !! lstsq needs a vector of 2 entries
expm([[1000]])
# !! expm's result is beyond the double range
lu([[i, 1], [1, 1]])
# !! lu needs a real matrix
lu([[10^400]])
# !! lu's entries are beyond the double range
lu([[10^-400]])
# !! lu's entries are beyond the double range

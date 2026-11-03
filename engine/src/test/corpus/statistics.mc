# Statistics: descriptive statistics, the probability distributions against published tables,
# regression, hypothesis tests and confidence intervals (docs/language.md §14.25;
# docs/reference/engine.md, Statistics). A golden corpus file: docs/reference/cli.md describes the
# format.

# Descriptive statistics, exact on exact data
mean([1, 2, 4])
# => 7/3
variance([1, 2, 4])
# => 7/3
pvariance([1, 2, 4])
# => 14/9
stdev([1, 2, 4])
# => √21/3
median([1, 2, 3, 4])
# => 5/2
mode([1, 1, 2, 2, 3])
# => {1, 2}
skewness([1, 2, 4])
# => 10√21/49
kurtosis([1, 2, 3, 4])
# => -6/5
cov([1, 2, 3], [2, 4, 7])
# => 5/2
cor([[1, 2], [2, 4], [3, 7]])
# => 5√57/38
zscore(4, [1, 2, 4])
# => 5√21/21
sort([3, 1.5, 2])
# => ⟨1.5, 2, 3⟩
cumsum([1, 2, 3])
# => ⟨1, 3, 6⟩
frequencies([2, 1, 2])
# => [[1, 1], [2, 2]]

# Quartiles by the median of halves without the median (TI), percentiles by R's type 7
quartiles([1, 2, 3, 4, 5, 6, 7])
# => ⟨2, 4, 6⟩
quartiles([1, 2, 3, 4, 5, 6, 7, 8])
# => ⟨5/2, 9/2, 13/2⟩
iqr([1, 2, 3, 4, 5, 6, 7])
# => 4
percentile([1, 2, 3, 4], 50)
# => 5/2
percentile([1, 2, 3, 4, 5, 6, 7], 25)
# => 5/2
summary([1, 2, 4])
# => [n = 3, mean = 7/3, median = 2, min = 1, Q1 = 1, Q3 = 4, max = 4, stdev = √21/3]

# Counts, decimal data and exact constants
mean([1, 2], [3, 1])
# => 5/4
median([1, 2], [3, 1])
# => 1
mean([0.1, 0.2])
# => 0.15
numeric(stdev([1.0, 2, 4]), 10)
# => 1.527525232
mean([π, 1])
# => (π + 1)/2
median([π, 3, 4])
# => π

# The distributions, exact where rational
binompdf(10, 1/2, 3)
# => 15/128
binomcdf(10, 1/2, 3)
# => 11/64
poissoncdf(2, 3)
# => 19/(3 e²)
geometpdf(1/4, 3)
# => 9/64
hypergeompdf(10, 4, 3, 2)
# => 3/10
normalpdf(x)
# => √2 e^(-x²/2)/(2√π)
tpdf(0, 1)
# => 1/π
chi2pdf(2, 2)
# => 1/(2 e)
expcdf(0, 1, 2)
# => -1/e² + 1
unifcdf(-1, 1, 0, 4)
# => 1/4
binompdf(3000, 1/2, 1500)
# => binompdf(3000, 1/2, 1500)
numeric(binompdf(3000, 1/2, 1500), 5)
# => 0.014566
steps(normalcdf(-1, 1))
# steps:
#   step 1: Computed in double precision through the distribution provider, at most 16 digits
#   = 0.6826894921370861
# => 0.6826894921370861

# The standard normal against its table (NIST/SEMATECH e-Handbook of Statistical Methods,
# §1.3.6.7.1), to the table's digits
numeric(normalcdf(-1.96, 1.96), 4)
# => 0.95
numeric(normalcdf(-inf, 1.96), 4)
# => 0.975
numeric(normalcdf(-inf, 1), 4)
# => 0.8413
numeric(normalcdf(-inf, 2.33), 4)
# => 0.9901
numeric(invnorm(0.975), 4)
# => 1.96
numeric(invnorm(0.95), 4)
# => 1.645

# Student's t: upper critical values (e-Handbook §1.3.6.7.2)
numeric(invt(0.975, 10), 4)
# => 2.228
numeric(invt(0.95, 5), 4)
# => 2.015
numeric(invt(0.995, 20), 4)
# => 2.845
numeric(invt(0.975, 1), 5)
# => 12.706

# χ²: upper and lower critical values (e-Handbook §1.3.6.7.4)
numeric(invchi2(0.95, 3), 4)
# => 7.815
numeric(invchi2(0.95, 1), 4)
# => 3.841
numeric(invchi2(0.99, 10), 5)
# => 23.209
numeric(invchi2(0.05, 10), 4)
# => 3.94

# F: upper critical values (e-Handbook §1.3.6.7.3)
numeric(invf(0.95, 5, 10), 4)
# => 3.326
numeric(invf(0.95, 2, 20), 4)
# => 3.493
numeric(invf(0.99, 10, 10), 4)
# => 4.849

# The special functions
gamma(5)
# => 24
gamma(1/2)
# => √π
erf(1.0)
# => 0.8427007929497151

# Regression: exact normal equations on exact data
linreg([1, 2, 3], [1, 2, 2])
# => [y = x/2 + 2/3, r² = 3/4, r = √3/2]
quadreg([0, 1, 2, 3], [1, 2, 5, 10])
# => [y = x² + 1, r² = 1]
polyreg([0, 1, 2], [1, 3, 7], 2)
# => [y = x² + x + 1, r² = 1]
residuals([1, 2, 3], [1, 2, 2], linreg([1, 2, 3], [1, 2, 2]))
# => ⟨-1/6, 1/3, -1/6⟩
predict(linreg([1, 2, 3], [1, 2, 2]), 4)
# => 8/3
numeric(expreg([0, 1, 2, 3], [2, 6, 18, 54]), 10)
# => [y = 2.0·3.0^x, r² = 1.0]

# Anscombe's first set (F. J. Anscombe, "Graphs in Statistical Analysis", The American
# Statistician 27(1), 1973): y = 3.00 + 0.500x, r² = 0.667, r = 0.816
numeric(linreg([10, 8, 13, 9, 11, 14, 6, 4, 12, 7, 5], [8.04, 6.95, 7.58, 8.81, 8.33, 9.96, 7.24, 4.26, 10.84, 4.82, 5.68]), 4)
# => [y = 0.5001x + 3.0, r² = 0.6665, r = 0.8164]

# NIST Statistical Reference Datasets, linear least squares (itl.nist.gov/div898/strd): the
# certified values to their 15 digits. Norris, a line through 36 points:
# B0 = -0.262323073774029, B1 = 1.00211681802045, R² = 0.999993745883712
norrisx := [0.2, 337.4, 118.2, 884.6, 10.1, 226.5, 666.3, 996.3, 448.6, 777.0, 558.2, 0.4, 0.6, 775.5, 666.9, 338.0, 447.5, 11.6, 556.0, 228.1, 995.8, 887.6, 120.2, 0.3, 0.3, 556.8, 339.1, 887.2, 999.0, 779.0, 11.1, 118.3, 229.2, 669.1, 448.9, 0.5]
norrisy := [0.1, 338.8, 118.1, 888.0, 9.2, 228.1, 668.5, 998.5, 449.1, 778.9, 559.2, 0.3, 0.1, 778.1, 668.8, 339.3, 448.9, 10.8, 557.7, 228.3, 998.0, 888.8, 119.6, 0.3, 0.6, 557.6, 339.3, 888.0, 998.5, 778.9, 10.2, 117.6, 228.9, 668.4, 449.2, 0.2]
numeric(linreg(norrisx, norrisy), 15)
# => [y = 1.00211681802045x - 0.262323073774029, r² = 0.999993745883712, r = 0.999996872936967]
# Pontius, a quadratic through 40 points: B0 = 0.673565789473684E-03, B1 = 0.732059160401003E-06,
# B2 = -0.316081871345029E-14, R² = 0.999999900178537
pontiusx := [150000, 300000, 450000, 600000, 750000, 900000, 1050000, 1200000, 1350000, 1500000, 1650000, 1800000, 1950000, 2100000, 2250000, 2400000, 2550000, 2700000, 2850000, 3000000, 150000, 300000, 450000, 600000, 750000, 900000, 1050000, 1200000, 1350000, 1500000, 1650000, 1800000, 1950000, 2100000, 2250000, 2400000, 2550000, 2700000, 2850000, 3000000]
pontiusy := [.11019, .21956, .32949, .43899, .54803, .65694, .76562, .87487, .98292, 1.09146, 1.20001, 1.30822, 1.41599, 1.52399, 1.63194, 1.73947, 1.84646, 1.95392, 2.06128, 2.16844, .11052, .22018, .32939, .43886, .54798, .65739, .76596, .87474, .98300, 1.09150, 1.20004, 1.30818, 1.41613, 1.52408, 1.63159, 1.73965, 1.84696, 1.95445, 2.06177, 2.16829]
numeric(quadreg(pontiusx, pontiusy), 15)
# => [y = -3.16081871345029e-15x² + 7.32059160401003e-7x + 0.000673565789473684, r² = 0.999999900178537]

# A symbol leaves the call
mean([1, a])
# => mean(⟨1, a⟩)

# Errors
mean([])
# !! mean needs at least 1 value
variance([1])
# !! variance needs at least 2 values
mean(5)
# !! mean needs data: a list or vector of real numbers
mean([1, i])
# !! mean needs real data
mean([1, 2], [1, -1])
# !! mean needs counts that are non-negative integers
percentile([1, 2], 150)
# !! percentile needs 0 ≤ p ≤ 100
cov([1, 2], [1, 2, 3])
# !! cov needs two lists of the same length or a matrix of two columns
normalpdf(0, 0, 0)
# !! normalpdf needs σ > 0
binompdf(10, 2, 3)
# !! binompdf needs 0 ≤ p ≤ 1
binompdf(10, 1/2, 1/2)
# !! binompdf needs an integer k
invnorm(2)
# !! invnorm needs 0 ≤ p ≤ 1
linreg([1, 1, 1], [1, 2, 3])
# !! linreg needs at least 2 distinct x values
expreg([1, 2], [1, -2])
# !! expreg needs positive y values

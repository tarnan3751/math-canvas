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

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

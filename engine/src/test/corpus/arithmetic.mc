# The number tower through the language (docs/language.md §14.1, §14.4, §17.3).
# A golden corpus file: docs/reference/cli.md describes the format.

# Exact rationals and integers
1/3 + 1/6
# => 1/2
7/14
# => 1/2
(2/3)^2
# => 4/9
2^-1
# => 1/2
2^3^2
# => 512
-2^2
# => -4
2^100
# => 1267650600228229401496703205376
5!
# => 120
0!
# => 1
10!
# => 3628800
20%
# => 1/5
30°
# => π/6
2π
# => 2π

# Inexact decimals
0.1 + 0.2
# => 0.3
0.5 + 1/3
# => 0.8333333333333333333333333333333333
1e3
# => 1000.0
2e-3
# => 0.002
exact(0.5)
# => 1/2
exact(0.1)
# => 1/10
numeric(1/3)
# => 0.3333333333333333333333333333333333
numeric(π, 50)
# => 3.1415926535897932384626433832795028841971693993751
numeric(2π)
# => 6.283185307179586476925286766559006
numeric(π, 0)
# !! the number of digits must be an integer from 1 to 10000

# Relations between numbers
2 < 3
# => true
3 ≥ 4
# => false
1 ≠ 2
# => true
1 = 1.0
# => true
2 ∈ ℤ
# => true
1/2 ∈ ℤ
# => false
0.5 ∈ ℚ
# => true
x < 3
# => x < 3
i < 2
# !! complex numbers are not ordered

# Undefined values
1/0
# => undefined
0/0
# => undefined
undefined + 1
# => undefined
∞ - ∞
# => undefined
0 ∞
# => undefined
factorial(-1)
# => undefined

# Powers and roots
0^0
# => 1
4^(1/2)
# => 2
8^(1/3)
# => 2
sqrt(4)
# => 2
(-1)^(1/2)
# => i
(-8)^(1/3)
# => 2(-1)^(1/3)
i^2
# => -1
(1 + 2i)(3 - i)
# => 5 + 5i
sqrt(8)
# => 2√2

# Functions on numbers
abs(-3)
# => 3
floor(7/2)
# => 3
gcd(12, 18)
# => 6
mod(7, 3)
# => 1
binomial(5, 2)
# => 10

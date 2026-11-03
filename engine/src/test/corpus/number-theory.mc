# Number theory (docs/language.md §9.5 and §14.23).
# A golden corpus file: docs/reference/cli.md describes the format.

# Primes
isprime(97)
# => true
isprime(1)
# => false
isprime(0)
# => false
isprime(-7)
# => false
isprime(561)
# => false
isprime(3215031751)
# => false
isprime(2^61 - 1)
# => true
isprime(n)
# => isprime(n)
isprime(1/2)
# !! isprime takes an integer
nextprime(100)
# => 101
nextprime(-5)
# => 2
prevprime(100)
# => 97
prevprime(2)
# => undefined
primes(10, 30)
# => ⟨11, 13, 17, 19, 23, 29⟩
primes(14, 16)
# => []

# The factorisation, a product for display only
ifactor(360)
# => 2³ · 3² · 5
ifactor(-12)
# => -2² · 3
ifactor(1)
# => 1
ifactor(0)
# => 0
ifactor(97)
# => 97
ifactor(2^64 + 1)
# => 274177 · 67280421310721
ifactor(1000000016000000063)
# => 1000000007 · 1000000009
ifactor(200000000000027 * 700000000000051)
# => 200000000000027 · 700000000000051
ifactor(241191224237888947190361313462598123228390025041668594610227)
# => ifactor(241191224237888947190361313462598123228390025041668594610227)
ifactor(360) + 1
# => 361
[ifactor(12), ifactor(18)]
# => ⟨2² · 3, 2 · 3²⟩
steps(ifactor(360))
# steps:
#   step 1: Trial division by the primes up to 10⁴ finds 2, 3 and leaves 5, which is prime. ifactor(360) = 2³ · 3² · 5
#   = 2³ · 3² · 5
# => 2³ · 3² · 5

# The divisor functions
divisors(12)
# => ⟨1, 2, 3, 4, 6, 12⟩
divisors(0)
# => undefined
numdivisors(360)
# => 24
sigma(12)
# => 28
totient(36)
# => 12
mobius(30)
# => -1
mobius(12)
# => 0

# Modular arithmetic
powmod(3, 100, 7)
# => 4
steps(powmod(3, 100, 7))
# steps:
#   step 1: The exponent 100 is 1100100₂ in binary: square and multiply from its lowest bit. The bit is 0: square the base, 3² ≡ 2 (mod 7), and halve the exponent: powmod(2, 50, 7)
#   = powmod(2, 50, 7)
#   step 2: The bit is 0: square the base, 2² ≡ 4 (mod 7), and halve the exponent: powmod(4, 25, 7)
#   = powmod(4, 25, 7)
#   step 3: The bit is 1: multiply the result by 4, ≡ 4 (mod 7): 4 powmod(4, 24, 7)
#   = 4 powmod(4, 24, 7)
#   step 4: The bit is 0: square the base, 4² ≡ 2 (mod 7), and halve the exponent: 4 powmod(2, 12, 7)
#   = 4 powmod(2, 12, 7)
#   step 5: The bit is 0: square the base, 2² ≡ 4 (mod 7), and halve the exponent: 4 powmod(4, 6, 7)
#   = 4 powmod(4, 6, 7)
#   step 6: The bit is 0: square the base, 4² ≡ 2 (mod 7), and halve the exponent: 4 powmod(2, 3, 7)
#   = 4 powmod(2, 3, 7)
#   step 7: The bit is 1: multiply the result by 2, ≡ 1 (mod 7): powmod(2, 2, 7)
#   = powmod(2, 2, 7)
#   step 8: The bit is 0: square the base, 2² ≡ 4 (mod 7), and halve the exponent: powmod(4, 1, 7)
#   = powmod(4, 1, 7)
#   step 9: The bit is 1: multiply the result by 4, ≡ 4 (mod 7): 4
#   = 4
# => 4
powmod(2, -1, 7)
# => 4
powmod(2, -1, 4)
# => undefined
powmod(0, 0, 5)
# => undefined
powmod(0, 0, 1)
# => 0
powmod(5, 0, 5)
# => 1
powmod(2, 3, 0)
# !! powmod's modulus must be a positive integer
invmod(17, 43)
# => 38
invmod(6, 9)
# => undefined
steps(invmod(17, 43))
# steps:
#   step 1: The extended Euclidean algorithm on 43 and 17, rows of remainder, quotient and coefficient of 17: [[17, 2, 1], [9, 1, -2], [8, 1, 3], [1, 8, -5]]; so -5·17 ≡ 1 (mod 43), and 17⁻¹ ≡ -5: -5 mod 43
#   = -5 mod 43
#   step 2: Reduce modulo 43: 38
#   = 38
# => 38

# The Chinese remainder theorem
crt([2, 3, 2], [3, 5, 7])
# => 23
crt([1, 3], [4, 6])
# => 9
crt([1, 2], [4, 6])
# => undefined
crt([1, 2], [3])
# !! crt takes a list of residues and one of moduli, as long

# Linear Diophantine equations
diophantine(3x + 5y = 7, [x, y])
# => {[x = 5k + 4, y = -3k - 1]}, k ∈ ℤ
diophantine(4x + 6y = 7, [x, y])
# => {}
diophantine(x/2 + y/3 = 1, [x, y])
# => {[x = 2k, y = -3k + 3]}, k ∈ ℤ
diophantine(3y = 6, [x, y])
# => {[x = k, y = 2]}, k ∈ ℤ
diophantine(x^2 + y = 1, [x, y])
# !! diophantine takes a linear equation a x + b y = c

# Combinatorics
choose(5, 2)
# => 10
choose(n, 2)
# => binomial(n, 2)
permutations(5, 2)
# => 20
permutations(5, 6)
# => 0
multinomial(4, [2, 1, 1])
# => 12
multinomial(4, [2, 1])
# => 0
fibonacci(10)
# => 55
fibonacci(-8)
# => -21
lucas(10)
# => 123
catalan(5)
# => 42
catalan(-1)
# => 0
bell(5)
# => 52
stirling2(5, 2)
# => 15
partitions(100)
# => 190569292
bell(2001)
# => bell(2001)

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

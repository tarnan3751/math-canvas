# Logic, truth tables and recurrences (docs/language.md §8.4 and §14.24).
# A golden corpus file: docs/reference/cli.md describes the format.

# The boolean operators and the rule set logic
true and false
# => false
p and true
# => p
not (p and q)
# => ¬p ∨ ¬q
not (p or q)
# => ¬p ∧ ¬q
p or not p
# => true
p and (p or q)
# => p
true xor p
# => ¬p
false implies p
# => true
p ⟹ q ⟹ r
# => p ⟹ q ⟹ r
1 < 2 and p
# => p
not (x < 1)
# => x ≥ 1
x > 0 and x < 1
# => x < 1 ∧ x > 0
steps(not (p and q))
# steps:
#   step 1: De Morgan’s law: the negation of a conjunction is the disjunction of the negations: ¬(p ∧ q) = ¬p ∨ ¬q
#   = ¬p ∨ ¬q
# => ¬p ∨ ¬q
1 and p
# !! and takes truth values
sin(x) and p
# !! and takes truth values
2 ∈ (p and q)
# !! ∈ needs a set or a domain on its right
true < false
# !! truth values are not ordered

# Truth tables
truthtable(p implies q, [p, q])
# => [[p, q, p ⟹ q], [true, true, true], [true, false, false], [false, true, true], [false, false, true]]
truthtable(1 < 2 and p, [p])
# => [[p, p], [true, true], [false, false]]
truthtable(p and x > 0, [p])
# !! truthtable: the names do not decide x > 0

# Recurrences
rsolve(a(n + 1) = 2 a(n) + 1, a(n), a(0) = 1)
# => a(n) = 2·2^n - 1
rsolve(a(n + 1) = 2 a(n) + 1, a(n))
# => a(n) = C₁·2^n - 1, C₁ ∈ ℝ
rsolve(a(n) = a(n - 1) + a(n - 2), a(n), a(0) = 0, a(1) = 1)
# => a(n) = (√5 + 1)^n·(1/2)^n √5/5 - (-√5 + 1)^n·(1/2)^n √5/5
rsolve(a(n + 2) = -a(n), a(n))
# => a(n) = C₁ cos(πn/2) + C₂ sin(πn/2), C₁ ∈ ℝ, C₂ ∈ ℝ
rsolve(a(n + 2) - 4 a(n + 1) + 4 a(n) = 0, a(n))
# => a(n) = C₂ n·2^n + C₁·2^n, C₁ ∈ ℝ, C₂ ∈ ℝ
rsolve(a(n + 1) = 2 a(n) + 2^n, a(n))
# => a(n) = C₁·2^n + n·2^n/2, C₁ ∈ ℝ
rsolve(a(n + 1) = (n + 1) a(n), a(n), a(0) = 1)
# => a(n) = n!
rsolve(a(n + 1) = a(n) + n, a(n), a(0) = 0)
# => a(n) = n²/2 - n/2
rsolve(a(n + 2) = a(n + 1) + 2a(n), a(n), a(0) = 1, a(1) = 1, a(2) = 7)
# => {}
rsolve(a(n + 1) = a(n)^2, a(n))
# => rsolve(a(n + 1) = a(n)², a(n))
rsolve(a(2n) = a(n), a(n))
# => rsolve(a(2n) = a(n), a(n))
rsolve(a(n + 1) = 2a(n) + C_1, a(n), a(0) = 1)
# => a(n) = -C₁ + (C₁ + 1)·2^n
rsolve(a(n) = 3, a(n))
# !! rsolve needs a recurrence: a(n) with at least two shifts, as a(n + 1) and a(n)

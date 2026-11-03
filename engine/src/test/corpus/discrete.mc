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

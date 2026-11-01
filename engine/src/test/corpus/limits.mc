# Limits: the limit command and the lim notation (docs/language.md §10.3;
# docs/reference/engine.md, Limits).
# A golden corpus file: docs/reference/cli.md describes the format.

# PLAN §6
lim[x→0] sin(x)/x
# => 1

# Substitution where the expression is continuous
limit(x^2 + 1, x, 2)
# => 5
limit(sqrt(x + 5), x, 4)
# => 3
limit(e^x cos(x), x, 0)
# => 1
limit(log(x), x, e)
# => 1
limit(1/x, x, a)
# => 1/a
steps(limit(x^3 - 2x, x, 2))
# steps:
#   step 1: By substitution, x³ - 2x → 4
#   = 4
# => 4

# Poles and one-sided limits
lim[x → 0+] 1/x
# => ∞
lim[x → 0-] 1/x
# => -∞
limit(1/x, x, 0)
# => undefined
limit(1/(x - 1)^2, x, 1)
# => ∞
limit((2x + 3)/(x - 1), x, 1, right)
# => ∞
limit(abs(x)/x, x, 0, right)
# => 1
limit(abs(x)/x, x, 0, left)
# => -1
limit(abs(x)/x, x, 0)
# => undefined
limit(floor(x), x, 2, left)
# => 1
limit(floor(x), x, 2)
# => undefined
limit(sign(x), x, 0, right)
# => 1
limit(tan(x), x, pi/2, left)
# => ∞
limit(log(x), x, 0, right)
# => -∞
lim[x → 1-] asin(x)
# => π/2
limit(acos(x), x, -1, right)
# => π
limit(acosh(x), x, 1, right)
# => 0
limit(asin(x), x, 1)
# => lim[x → 1] asin(x)
limit(e^(1/x), x, 0, left)
# => 0
limit(e^(1/x), x, 0)
# => undefined
steps(limit(1/x, x, 0))
# steps:
#   step 1: The denominator of 1/x vanishes to order 1 at the point and its numerator does not, so 1/x → -∞ from the left and ∞ from the right; the limit is undefined
#   = undefined
# => undefined
steps(limit(abs(x)/x, x, 0))
# steps:
#   step 1: From the left, |x|/x is -1; from the right, |x|/x is 1; from the left the limit is -1 and from the right 1; they differ, so the limit is undefined
#   = undefined
# => undefined

# Algebraic forms: cancelling, combining, conjugates and the standard limits
limit((x^2 - 1)/(x - 1), x, 1)
# => 2
limit((x^3 - 8)/(x - 2), x, 2)
# => 12
limit(1/x - 1/(x^2 + x), x, 0)
# => 1
limit((sqrt(x + 4) - 2)/x, x, 0)
# => 1/4
limit((1 - cos(x))/x, x, 0)
# => 0
limit((e^x - 1)/x, x, 0)
# => 1
lim[x → ∞] (1 + 1/x)^x
# => e
limit((1 + x)^(1/x), x, 0)
# => e
steps(lim[x → 1] (x^2 - 1)/(x - 1))
# steps:
#   step 1: Cancel the common factor x - 1: (x² - 1)/(x - 1) = x + 1
#   = lim[x → 1] (x + 1)
#   step 2: By substitution, x + 1 → 2
#   = 2
# => 2
steps(limit((sqrt(x + 4) - 2)/x, x, 0))
# steps:
#   step 1: Multiply above and below by the conjugate √(x + 4) + 2, giving 1/(√(x + 4) + 2); by substitution, 1/(√(x + 4) + 2) → 1/4
#   = 1/4
# => 1/4
steps(limit(sin(x)/x, x, 0))
# steps:
#   step 1: By the standard limit sin(u)/u → 1 as u → 0, sin(x)/x → 1
#   = 1
# => 1

# At infinity
limit((3x^2 + 1)/(2x^2 - x), x, inf)
# => 3/2
limit(x^3/e^x, x, inf)
# => 0
limit(log(x)/sqrt(x), x, inf)
# => 0
limit(-x^3 + x, x, -inf)
# => ∞
limit(atan(x), x, inf)
# => π/2
limit(asec(x), x, -inf)
# => π/2
limit(acsc(x), x, inf)
# => 0
limit(1/(asec(x) - pi/2), x, inf)
# => -∞
assume(c != 0); limit(1/(c asec(x) - c pi/2), x, inf)
# => lim[x → ∞] 2/(-πc + 2c asec(x))
limit(log(abs(x)) - log(abs(x + 1)), x, inf)
# => 0
steps(limit(log(x) - log(x + 1), x, inf))
# steps:
#   step 1: Combine the logarithms of log(x) - log(x + 1) into log(x/(x + 1)); as x → ∞, x/(x + 1) behaves like its leading term 1, so x/(x + 1) → 1; by the limit laws, log(x/(x + 1)) → 0
#   = 0
# => 0
limit(sqrt(x^2 + 1) - x, x, inf)
# => 0
limit(x - sqrt(x^2 - 3x), x, inf)
# => 3/2
limit(log(log(x))/log(x), x, inf)
# => 0
limit(cbrt(x^3 + x^2) - x, x, inf)
# => 1/3
limit(abs(x)/x, x, -inf)
# => -1
limit((sqrt(x^2) + x)/x, x, -inf)
# => 0
limit(sin(x)^2 + cos(x)^2, x, inf)
# => 1
lim[x -> -inf] e^x
# => 0
steps(limit((3x^2 + 1)/(2x^2 - x), x, inf))
# steps:
#   step 1: As x → ∞, (3x² + 1)/(2x² - x) behaves like its leading term 3/2, so (3x² + 1)/(2x² - x) → 3/2
#   = 3/2
# => 3/2

# Bounded factors and oscillation
limit(x sin(1/x), x, 0)
# => 0
limit(sin(x)/x, x, inf)
# => 0
limit(sin(x), x, inf)
# => undefined
limit(sin(1/x), x, 0)
# => undefined
limit(floor(x)/x, x, inf)
# => 1
limit(floor(1/x) x, x, 0, right)
# => 1
limit(cos(2 pi abs(floor(x))), x, inf)
# => lim[x → ∞] cos(2π |floor(x)|)
steps(limit(x sin(1/x), x, 0))
# steps:
#   step 1: sin(1/x) is bounded and x → 0, so x sin(1/x) → 0
#   = 0
# => 0
steps(limit(floor(x)/x, x, inf))
# steps:
#   step 1: As x → ∞, (x - 1)/x behaves like its leading term 1, so (x - 1)/x → 1; floor(x)/x lies between (x - 1)/x and 1, which both tend to 1, so floor(x)/x → 1
#   = 1
# => 1

# Powers in the exponential form
limit(x^x, x, 0, right)
# => 1
limit((1 + 2/x)^x, x, inf)
# => e²
limit(x^(1/x), x, inf)
# => 1
limit(cos(x)^(1/x^2), x, 0)
# => 1/√e
steps(limit(x^x, x, 0, right))
# steps:
#   step 1: Write x^x as e^(x log(x)); log(x) → -∞ and 1/x → ∞; by L'Hôpital's rule take the quotient of their derivatives, 1/x and -1/x²; by substitution, -x → 0; so x^x → 1
#   = 1
# => 1

# L'Hôpital's rule
limit((x - sin(x))/x^3, x, 0)
# => 1/6
limit(sin(3x)/sin(2x), x, 0)
# => 3/2
limit(x/tan(x), x, 0)
# => 1
limit(x log(x), x, 0, right)
# => 0
limit((e^x - 1 - x)/x^2, x, 0)
# => 1/2
limit(1/x - 1/sin(x), x, 0)
# => 0
limit(sec(x) - tan(x), x, pi/2)
# => 0
limit((tan(x) - x)/x^3, x, 0)
# => 1/3
steps(limit((x - sin(x))/x^3, x, 0))
# steps:
#   step 1: x - sin(x) → 0 and x³ → 0; by L'Hôpital's rule take the quotient of their derivatives, -cos(x) + 1 and 3x²; (1 - cos(x))/3 → 0 and x² → 0; by L'Hôpital's rule take the quotient of their derivatives, sin(x)/3 and 2x; by the standard limit sin(u)/u → 1 as u → 0, sin(x)/(6x) → 1/6
#   = 1/6
# => 1/6

# Series expansion
limit(sin(x) abs(x + 3)/x, x, 0)
# => 3
limit((sin(x) - x + x^3/6)/x^5, x, 0)
# => 1/120
steps(limit(sin(x) abs(x + 3)/x, x, 0))
# steps:
#   step 1: The leading terms of the expansions about the point are 3x for |x + 3| sin(x) and x for x, so the quotient → 3
#   = 3
# => 3

# What no method decides stays; the numeric probe only notes an estimate
limit(x!, x, 1/2)
# => lim[x → 1/2] x!
steps(limit(mod(x, 1)/x, x, 0, right))
# steps:
#   step 1: No method finds lim[x → 0+] mod(x, 1)/x; numerically it appears to be 1.0 to 12 digits, which proves nothing, so it stays unevaluated
#   = lim[x → 0+] mod(x, 1)/x
# => lim[x → 0+] mod(x, 1)/x

# Bound variable and errors
x := 3
# => x := 3
limit(x^2, x, 2)
# => 4
clear(x)
# => x
limit(e^x, x, inf, left)
# !! a limit at ∞ or -∞ has no side

# The facts about the variable hold at the point, never in the expression
assume(a > 0, s > 1); limit(x^a, x, inf)
# => ∞
limit(s^x, x, inf)
# => ∞
assume(x > 0); limit(sqrt(x^2)/x, x, 0, left)
# => -1
limit(sqrt(x^2)/x, x, 0, right)
# => 1
assume(r >= 0, t > 0); limit(x^(r t), x, inf)
# => lim[x → ∞] x^(r t)

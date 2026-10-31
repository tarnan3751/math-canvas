# The numeric path of a definite integral (docs/language.md §10.2): under numeric(…) a definite
# integral with no closed form is evaluated by the double-exponential rule, with its step.
# A golden corpus file: docs/reference/cli.md describes the format.

# No closed form: the value is numeric
numeric(integrate(e^(-x^2), x, 0, 1))
# => 0.746824132812427025399467436131853
numeric(∫[0, 1] e^(-x^2) dx, 50)
# => 0.74682413281242702539946743613185300535449968681261
numeric(integrate(x^x, x, 0, 1), 20)
# => 0.78343051071213440706
numeric(integrate(sin(x^2), x, 0, √π), 15)
# => 0.894831469484145

# Infinite bounds
numeric(integrate(e^(-x^2), x, -∞, ∞))
# => 1.772453850905516027298167483341145
numeric(integrate(e^(-x^2), x, 0, ∞), 20)
# => 0.88622692545275801365

# Endpoint singularities
numeric(integrate(e^x/sqrt(x), x, 0, 1), 20)
# => 2.9253034918143632176
numeric(integrate(1/sqrt(1 - x^4), x, 0, 1), 20)
# => 1.3110287771460599052

# The step, at the top of numeric
steps(numeric(integrate(e^(-x^2), x, 0, 1), 10))
# steps:
#   step 1: No closed form found; the value is numeric, 10 digits
#   = 0.7468241328
# => 0.7468241328

# Nested in a larger expression: numeric, without the step
numeric(1 + integrate(e^(-x^2), x, 0, 1), 10)
# => 1.746824133

# No value: divergent, or a sample undefined inside, or no agreement; the node stays
steps(numeric(integrate(e^(x^2)/x, x, 0, 1), 10))
# steps:
#   step 1: The numeric quadrature did not converge; the integral is left unevaluated
#   = ∫[0, 1] 2.718281828^(x²)/x dx
# => ∫[0, 1] 2.718281828^(x²)/x dx
numeric(integrate(e^(x^2)/(x - 1/2), x, 0, 1), 10)
# => ∫[0, 1] 2.718281828^(x²)/(x - 0.5) dx
numeric(integrate(e^(x^2)/(x - 0.3), x, 0, 1), 10)
# => ∫[0, 1] 2.718281828^(x²)/(x - 0.3) dx
numeric(integrate(sin(x^2)/x^2, x, 0, ∞), 10)
# => ∫[0, ∞] sin(x²)/x² dx

# A spike narrower than the coarse levels' nodes: the double rule's cross-check refuses the value
# the levels agree on without it, so the node stays rather than a wrong number
numeric(integrate(e^(-10^6 (x - 0.3)^2) + 1, x, 0, 1), 10)
# => ∫[0, 1] 2.718281828^(-1000000.0(x - 0.3)²) + 1.0 dx

# More than 100 digits: the node stays, with its step
steps(numeric(integrate(e^(-x^2), x, 0, 1), 101))
# steps:
#   step 1: Integrals are evaluated numerically to at most 100 digits; the integral is left unevaluated
#   = ∫[0, 1] 2.7182818284590452353602874713526624977572470936999595749669676277240766303535475945713821785251664274^(-1.0x²) dx
# => ∫[0, 1] 2.7182818284590452353602874713526624977572470936999595749669676277240766303535475945713821785251664274^(-1.0x²) dx

# A parameter or a symbolic bound: the node stays
numeric(integrate(e^(-a x^2), x, 0, 1), 10)
# => ∫[0, 1] 2.718281828^(-1.0a x²) dx
numeric(integrate(e^(-x^2), x, 0, b), 10)
# => ∫[0, b] 2.718281828^(-1.0x²) dx

# Without numeric (and without a decimal number format), the node is the answer
integrate(e^(-x^2), x, 0, 1)
# => ∫[0, 1] e^(-x²) dx

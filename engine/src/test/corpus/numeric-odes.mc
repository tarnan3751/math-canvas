# Numeric ODEs: odesolve by Dormand–Prince 5(4) through the Hipparchus provider (docs/language.md
# §14.22; docs/reference/engine.md, Numeric ODEs). A golden corpus file: docs/reference/cli.md
# describes the format. The values are shown to 8 digits by numeric(…, 8), well inside the
# tolerances, so they read the same on every platform.

# One point, against e^1, e^-4, cos(π) and the logistic curve
numeric(odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, at = 1), 8)
# => 2.7182818
numeric(odesolve(y'(t) = -2t y(t), y(t), y(0) = 1, 0, 5, at = 2), 8)
# => 0.018315639
numeric(odesolve(y''(x) = -y(x), y(x), y(0) = 1, y'(0) = 0, 0, 10, at = pi), 8)
# => -1.0
numeric(odesolve(y'(t) = y(t)(1 - y(t)), y(t), y(2) = 1/2, 0, 4, at = 0), 8)
# => 0.11920292
numeric(odesolve(y'(t) = -50(y(t) - cos(t)), y(t), y(0) = 0, 0, 5, at = 5), 8)
# => 0.26437795

# A grid: the rows [t, y(t)], and [t, x(t), y(t)] for a system
numeric(odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 4), 8)
# => [[0.0, 1.0], [0.25, 1.2840254], [0.5, 1.6487213], [0.75, 2.117], [1.0, 2.7182818]]
numeric(odesolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0, 0, 2, steps = 2), 8)
# => [[0.0, 1.0, 0.0], [1.0, 0.54030231, -0.84147098], [2.0, -0.41614684, -0.90929743]]
numeric(odesolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0, 0, 2, at = 1), 8)
# => ⟨0.54030231, -0.84147098⟩
# a forcing term and a condition that is not at the start
numeric(odesolve(y'(x) + y(x) = sin(x), y(x), y(1) = 0, 0, 2, steps = 2), 8)
# => [[0.0, -0.90933067], [1.0, 0.0], [2.0, 0.60732525]]

# Errors
odesolve(y'(t) = y(t)^2, y(t), y(0) = 1, 0, 2, at = 2)
# !! `odesolve`'s solution is not finite on the interval: it blows up or is undefined
odesolve(y'(t) = a y(t), y(t), y(0) = 1, 0, 1)
# !! `odesolve` needs equations in t and the unknowns only, with real values
odesolve(y''(t) = y(t), y(t), y(0) = 1, 0, 1)
# !! `odesolve` needs the initial conditions y(a), y'(a), once each
odesolve(y'(t) = y(t), y(t), y(3) = 1, 0, 1)
# !! `odesolve` needs the initial point in [a, b]
odesolve(y'(t) = y(t), y(t), y(0) = 1, 0, 1, steps = 0)
# !! `odesolve`'s steps must be an integer from 1 to 100000
odesolve(y'(t) = y(t), y(t), 0, 1)
# !! `odesolve` takes 5 or more arguments; `help(odesolve)` shows examples

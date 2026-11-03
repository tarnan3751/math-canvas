# The time value of money (docs/language.md §14.28; docs/reference/engine.md, Finance). A golden
# corpus file: docs/reference/cli.md describes the format.

# Closed forms, exact
fv(1/10, 2, 0, -100)
# => 121
pv(1/10, 2, 0, 121)
# => -100
pmt(1/100, 2, 100)
# => -10201/201
nper(1/10, 0, -100, 121)
# => 2
nper(5/100, -100, 1000)
# => log(2)/log(21/20)
fv(0, 10, -5, -100)
# => 150
fv(1/10, 1, -100, 0, 1)
# => 110
effective(1/10, 2)
# => 41/400
nominal(41/400, 2)
# => 1/10
npv(1/10, [-100, 60, 60])
# => 5000/1331

# Decimals: the textbook mortgage payment
pmt(0.05/12, 360, 200000)
# => -1073.643246024277969656985158225072
fv(0.1, 2, 0, -100)
# => 121.0

# The rates, numeric
rate(2, 0, -100, 121)
# => 0.1
steps(rate(2, 0, -100, 121))
# steps:
#   step 1: Solve the time-value-of-money equation for the rate by Newton's method, 34 digits: 0.1
#   = 0.1
# => 0.1
irr([-100, 60, 60])
# => 0.1306623862918074852584262744907492
rate(4, -25, 100)
# => 0
irr([-100, 50, 50])
# => 0

# The schedule
amortization(1/10, 2, 100)
# => [[1, -1210/21, -10, -1000/21, 1100/21], [2, -1210/21, -110/21, -1100/21, 0]]

# Undefined, unevaluated, errors
fv(-1, 2, 0, -100)
# => undefined
nper(1/10, 0, 100, 121)
# => undefined
fv(r, 2, 0, -100)
# => fv(r, 2, 0, -100)
fv(1/10, 2, 0, -100, 2)
# !! fv's type must be 0 (end) or 1 (beginning)
irr([100, 60])
# !! irr needs cash flows of both signs
amortization(1/10, 0, 100)
# !! amortization's periods must be an integer from 1 to 1000

# The solver
tvm(n = 10, i = 5 %, pv = -1000, pmt = 0)
# => [n = 10, i = 1/20, pv = -1000, pmt = 0, fv = 16679880978201/10240000000]
tvm(i = 1/10, pv = -100, pmt = 0, fv = 121)
# => [n = 2, i = 1/10, pv = -100, pmt = 0, fv = 121]
tvm(n = 2, i = 1/100, pv = 100, fv = 0)
# => [n = 2, i = 1/100, pv = 100, pmt = -10201/201, fv = 0]
tvm(n = 2, pv = -100, pmt = 0, fv = 121)
# => [n = 2, i = 0.1, pv = -100, pmt = 0, fv = 121]
tvm(n = 10, i = 5 %, pv = -1000, x = 0)
# !! tvm's parts are n, i, pv, pmt, fv and type

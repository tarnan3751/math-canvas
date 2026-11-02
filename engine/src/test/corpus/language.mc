# The language sketch of PLAN §6, one statement per row (docs/language.md §17.1).
# Rows the engine cannot answer yet are kept as "# later:" lines; the brief that lands the
# answer removes the "# later: " prefix from the statement and its expectation.
# A golden corpus file: docs/reference/cli.md describes the format.

2π
# => 2π
numeric(2π)
# => 6.283185307179586476925286766559006
1/3 + 1/6
# => 1/2
sqrt(8)
# => 2√2
-2^2
# => -4
2^3^2
# => 512
5!
# => 120
20%
# => 1/5
sin(30°)
# => 1/2
expand((x+1)^3)
# => x³ + 3x² + 3x + 1
factor(x^4 - 1)
# => (x - 1)(x + 1)(x² + 1)
solve(x^2 - 5x + 6 = 0, x)
# => {2, 3}
# later: solve(sin(x) = 1/2, x)
# later: # => {2πk + π/6, 2πk + 5π/6}
d/dx (x^2 sin x)
# => x² cos(x) + 2x sin(x)
∫ x e^x dx
# => x e^x - e^x
∫[0,1] x^2 dx
# => 1/3
lim[x→0] sin(x)/x
# => 1
taylor(e^x, x, 0, 4)
# => x⁴/24 + x³/6 + x²/2 + x + 1
[[1,2],[3,4]]^-1
# => [[-2, 1], [3/2, -1/2]]
eigenvalues([[2,1],[1,2]])
# => ⟨1, 3⟩
rref([[1,2,3],[4,5,6]])
# => [[1, 0, -1], [0, 1, 2]]
# later: grad(x^2 + y^2)
# later: # => ⟨2x, 2y⟩
f(x) := x^2 + 1
# => f(x) := x² + 1
# The slider row is the canvas's (Phase 3); the CLI echoes the definition.
a := slider(0, 5, 0.1)
# => a := slider(0, 5, 0.1)

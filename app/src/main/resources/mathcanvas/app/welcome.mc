# Type an expression and press Enter. Try changing a number.
sqrt(8) + 1/3

# Define a function with :=
f(x) := x^2 - 2x

# Plot it. Drag the canvas to pan, scroll to zoom.
plot(f(x))

# A slider makes a number you can drag. Try it.
a := slider(-3, 3, 1)
plot(f(x) + a)

# Show steps explains how an answer was found.
steps(solve(f(x) = 0, x))

# Click Help, or press F1, to see every function with examples you can run.

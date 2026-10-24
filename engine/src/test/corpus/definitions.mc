# Definitions (docs/language.md §14.3, §16).
# A golden corpus file: docs/reference/cli.md describes the format.

# Binding a name and defining a function; later statements see them
a := 5
# => a := 5
f(x) := x^2 + 1
# => f(x) := x² + 1
f(a)
# => 26
f(t)
# => t² + 1

# Definitions are delayed: the right side is evaluated at each use
a := 1; b := 2a; a := 3
# => a := 3
b
# => 6

# A function's parameters shadow definitions
x := 3
# => x := 3
g(x) := x^2
# => g(x) := x²
g(4)
# => 16
x
# => 3

# clear unbinds; a symbol with no binding is itself
clear(a)
# => a
b
# => 2a

# A lambda defines a function; a value's name before ( multiplies
sq := t -> t^2
# => sq := t → t²
sq(5)
# => 25
c := 3;
c(2)
# => 6
y(t) := t + 1;
clear(y)
# => y
y(2)
# => 2y

# A definition statement ended by ; echoes nothing
q := 7;
q
# => 7

# A definition in terms of itself is an error when used
a := b; b := a
# => b := a
a
# !! a is defined in terms of itself
h(n) := n h(n - 1)
# => h(n) := n h(n - 1)
h(3)
# !! h is defined in terms of itself

# Calls are checked against the definition
k(x) := x;
k(1, 2)
# !! k takes 1 argument

# Reserved names cannot be defined, and parameters must be distinct names
sin := 1
# !! `sin` is a builtin
e := 3
# !! `e` is a constant
pi := 3
# !! `pi` is a constant
f(x, x) := 1
# !! expected distinct parameter names
f(2) := 1
# !! expected distinct parameter names

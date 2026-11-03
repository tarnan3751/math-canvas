# Programs (docs/language.md §19).
# A golden corpus file: docs/reference/cli.md describes the format.

# A function with a loop, defined over several lines, echoes its head
function fact(n)
  r := 1
  for k in 1..n
    r := r k
  end
  return r
end
# => function fact(n)
fact(5)
# => 120
fact(0)
# => 1

# A while loop and a one-line if with end
function collatz(n)
  count := 0
  while n ≠ 1
    if n mod 2 = 0 then n := n/2 else n := 3n + 1 end
    count := count + 1
  end
  return count
end
# => function collatz(n)
collatz(27)
# => 111

# print shows each value on a line; a loop has no value of its own
for p in primes(1, 20) do print(p^2) end
# => 4
# => 9
# => 25
# => 49
# => 121
# => 169
# => 289
# => 361

# A top-level block writes the document; a loop variable is local to its loop
s := 0
# => s := 0
for k in 1..10
  s := s + k
end
s
# => 55
k
# => k
for i in 1..3 do s := s + i end; i
# => i
s
# => 61

# Ranges with steps, empty ranges, items of lists, sets and matrices
t := 0; for k in 10..1 step -3 do t := t + k end; t
# => 22
t := 0; for k in 5..1 do t := t + k end; t
# => 0
for v in {3, 1, 2} do print(v) end
# => 1
# => 2
# => 3
for r in [[1, 2], [3, 4]] do print(r) end
# => ⟨1, 2⟩
# => ⟨3, 4⟩

# An if as a statement has the value of its branch; else if chains under one end
x := 5
# => x := 5
if x > 3 then
  big
else if x > 1 then
  middle
else
  small
end
# => big

# Recursion through an if expression, and through a function's body
fib(n) := if n < 2 then n else fib(n - 1) + fib(n - 2)
# => fib(n) := if n < 2 then n else fib(n - 1) + fib(n - 2)
fib(15)
# => 610
function power(b, n)
  if n = 0 then return 1 end
  return b power(b, n - 1)
end
# => function power(b, n)
power(2, 10)
# => 1024

# A function's locals; global writes the document
count := 0
# => count := 0
function tick()
  global count
  count := count + 1
end
# => function tick()
tick(); tick(); count
# => 2
function shadow(y)
  count := y
  return count
end
# => function shadow(y)
shadow(9)
# => 9
count
# => 2

# break, continue, and lists built with append and setnth
L := []
# => L := []
for k in 1..10
  if k mod 3 = 0 then continue end
  if k > 8 then break end
  L := append(L, k)
end
L
# => ⟨1, 2, 4, 5, 7, 8⟩
function swapfirst(M)
  return setnth(setnth(M, 1, nth(M, 2)), 2, nth(M, 1))
end
# => function swapfirst(M)
swapfirst([3, 1, 2])
# => ⟨1, 3, 2⟩

# The steps of a call narrate its body
function double(n)
  m := 2n
  return m
end
# => function double(n)
steps(double(4))
# steps:
#   step 1: Line 2: m := 2n
#   = 8
#   step 2: Line 3: return m
#   = 8
# => 8

# The errors
if w > 0 then 1 end
# !! the condition did not decide: w > 0
for k in 1..2.5 do k end
# !! a range needs integers
for k in z do k end
# !! for iterates a list, a vector, a set or a range a..b
function bad(x)
  y := 1
  return [1] + [1, 2]
end
# => function bad(x)
bad(1)
# !! in bad, line 3: cannot add vectors of lengths 1 and 2
function none(x)
  y := x
end
# => function none(x)
none(1) + 1
# !! none has no value
function early()
  u := v
  v := 1
end
# => function early()
early()
# !! in early, line 2: `v` is read before it is assigned

# Integers in other bases (docs/language.md §3.1, §14.27; docs/reference/engine.md, Bases). A
# golden corpus file: docs/reference/cli.md describes the format.

# Literals are integers
0xff
# => 255
0XFF + 1
# => 256
0b1011
# => 11
0o17
# => 15
0xFFFF_FFFF
# => 4294967295
0xff x
# => 255x

# An integer shown in a base
hex(255)
# => 0xff
bin(11)
# => 0b1011
oct(15)
# => 0o17
hex(-255)
# => -0xff
base(1295, 36)
# => zz₍₃₆₎
base(255, 10)
# => 255
[hex(1), hex(255)]
# => ⟨0x1, 0xff⟩
hex(x)
# => hex(x)

# The base does not propagate
hex(255) + 1
# => 256
2 hex(3)
# => 6
hex(255) = 255
# => true
{hex(1), 1}
# => {1}
bin(hex(5))
# => 0b101

# Digits
digits(11, 2)
# => ⟨1, 0, 1, 1⟩
digits(255, 16)
# => ⟨15, 15⟩

# Bitwise operations
bitand(12, 10)
# => 8
bitor(12, 10)
# => 14
bitxor(12, 10)
# => 6
bitnot(5)
# => -6
bitand(-1, 0xff)
# => 255
shiftleft(1, 10)
# => 1024
shiftright(-16, 2)
# => -4
bitcount(255)
# => 8
bit(5, 0)
# => 1
hex(bitand(0xf0, 0x3c))
# => 0x30

# Errors
0b102
# !! `2` is not a binary digit
0x1g
# !! `g` is not a hexadecimal digit
hex(1/2)
# !! `hex` needs an integer
base(10, 37)
# !! the base must be an integer from 2 to 36
digits(-1, 2)
# !! `digits` needs a non-negative integer
bitand(1.5, 2)
# !! `bitand` needs integers
bitcount(-1)
# !! `bitcount` needs a non-negative integer

# Units and quantities (docs/language.md §3.6, §14.26; docs/reference/engine.md, Units). A golden
# corpus file: docs/reference/cli.md describes the format.

# Quantities as values; units as positive constants
3 _m
# => 3 _m
5 _km/_h
# => 5 _km/_h
9.8 _m/_s^2
# => 9.8 _m/_s²
_J/(_kg _K)
# => _J/(_kg _K)
3 _m * 2 _m
# => 6 _m²
sqrt(4 _m^2)
# => 2 _m
abs(-3 _m)
# => 3 _m
diff(3 _m x^2, x)
# => 6x _m

# Sums of one dimension meet in the first term's unit
3 _m + 2 _cm
# => 151/50 _m
2 _cm + 3 _m
# => 151/50 _m
x _m + 2 _cm
# => (x + 1/50) _m
5 _km/_h + 1 _m/_s
# => 43/18 _m/_s
3.0 _m + 2 _cm
# => 3.02 _m
steps(3 _m + 2 _cm)
# steps:
#   step 1: Add the quantities in one unit: 3 _m + 2 _cm = 151/50 _m
#   = 151/50 _m
# => 151/50 _m

# Products: units of one dimension merge, derived units stay, dimensionless ones cancel
3 _N * 2 _m
# => 6 _N _m
_km _m
# => 1000 _m²
_km/_m
# => 1000
3 _h _Hz
# => 10800
1 + 30 _deg
# => π/6 + 1

# Temperatures: differences in arithmetic, absolute in comparisons
5 _degC + 3 _degC
# => 8 _degC
20 _degC + 10 _degF
# => 230/9 _degC
30 _degC > 300 _K
# => true

# Comparisons, functions, commands
1 _km > 900 _m
# => true
max(3 _m, 200 _cm)
# => 3 _m
sin(30 _deg)
# => 1/2
sin(_km/_m)
# => sin(1000)
solve(x _m = 3 _cm, x)
# => {3/100}
numeric(5 _km/_h)
# => 5.0 _km/_h
solve(x _m + 2 _cm = 0, x)
# => {-1/50}
max(0, 3 _m)
# => 3 _m
-_m/_s
# => -_m/_s
1/_s^2
# => 1/_s²

# Errors
_tmp
# !! unknown unit `_tmp`
3 _m + 2 _s
# !! cannot add m and s
3 _m + 2
# !! cannot add a number and m
3 _m < 2 _s
# !! cannot compare m and s
sin(3 _m)
# !! sin needs a number, not a quantity in m
2^_m
# !! an exponent cannot have units
[3 _m] + [2 _s]
# !! cannot add m and s
3 _m in Reals
# !! a quantity is not a member of a number domain

# Conversion (convert, units, magnitude)
convert(5 _km/_h, _m/_s)
# => 25/18 _m/_s
steps(convert(5 _km/_h, _m/_s))
# steps:
#   step 1: 1 _km = 1000 _m. 1 _h = 3600 _s. So 5 _km/_h = 5000/3600 _m/_s = 25/18 _m/_s
#   = 25/18 _m/_s
# => 25/18 _m/_s
convert(6 _N _m, _J)
# => 6 _J
steps(convert(6 _N _m, _J))
# steps:
#   step 1: 1 _N = _kg _m/_s². 1 _J = _kg _m²/_s². So 6 _N _m = 6 _J
#   = 6 _J
# => 6 _J
convert(1 _mi, _km)
# => 25146/15625 _km
convert(1 _kWh, _MJ)
# => 18/5 _MJ
convert(3 _m + 2 _cm, _cm)
# => 302 _cm
convert(π, _deg)
# => 180 _deg
convert(0 _degC, _degF)
# !! a temperature of 0 is the number 0, which keeps no unit: write 0.0 _degC or 273.15 _K
convert(0.0 _degC, _degF)
# => 32.0 _degF
convert(20 _degC, _degF)
# => 68 _degF
convert(x _degC, _degF)
# => (9x/5 + 32) _degF
convert(5 _degC/_s, _K/_s)
# => 5 _K/_s
units(5 _km/_h)
# => _km/_h
magnitude(3 _m + 2 _cm)
# => 151/50
convert(3 _m, _s)
# !! cannot convert m to s
convert(3 _m, 2 _km)
# !! convert needs a unit such as _m/_s

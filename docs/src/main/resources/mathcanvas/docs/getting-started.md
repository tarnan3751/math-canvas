# Getting started

This page takes you from an installed math-canvas to every worked example of the language
reference, each with the result it gives.

## Install and open math-canvas

Install math-canvas for your platform as [Installing](../README.md#installing) says, and open
it. The window is a worksheet of cells beside a coordinate plane: type a statement into a cell
and press Enter, and the result appears under it, typeset. The keypad types the symbols that are
not on a keyboard (`π`, `√`, `∫`, `°`), and each of them also has an ASCII spelling (`pi`,
`sqrt(…)`, `integrate(…)`, `*pi/180`) that [the language](language.md#35-unicode-aliases) lists.

## Or use the command line

The command-line runner evaluates the same statements and prints the results in Unicode. It is
not in the installers; from a clone of the repository, run

```
./gradlew :cli:run --args="-e 'factor(x^4 - 1)'"
```

which prints `(x - 1)(x + 1)(x² + 1)`. [The CLI reference](reference/cli.md) describes its
options and its scripts.

## The worked examples

The examples below are those of [the language reference](language.md#17-worked-examples). The
first table gives each input, the form it parses to and its result; type the input and compare.
The second shows how the parser groups what you type, and the third what evaluation does with a
few inputs worth knowing.

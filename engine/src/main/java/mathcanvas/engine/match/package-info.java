/**
 * Patterns and the matcher: rules locate their subexpressions through this package.
 *
 * <p>A {@link mathcanvas.engine.match.Pattern} is compiled from an expression in the language,
 * where symbols ending in {@code _}, {@code __} and {@code ___} are pattern and sequence variables;
 * {@link mathcanvas.engine.match.Matcher} matches it against canonical expressions, commutatively
 * inside sums and products, with defaults and conditions that may query the assumptions; {@link
 * mathcanvas.engine.match.Bindings} holds a match and instantiates templates; {@link
 * mathcanvas.engine.match.Operands} is the index scheme of a node's operands, and {@link
 * mathcanvas.engine.match.Occurrence} the occurrence queries and fresh names over it. {@code
 * docs/reference/engine.md}, section <b>Patterns and rules</b>, is the reference.
 */
package mathcanvas.engine.match;

/**
 * Programs ({@code docs/language.md} §19): the statements of a block as parsed, {@link
 * mathcanvas.engine.script.Program} and {@link mathcanvas.engine.script.Stmt}, which sit beside the
 * expression tree rather than in it, and the {@link mathcanvas.engine.script.Interpreter} that runs
 * them, every expression through the evaluator, under one {@link
 * mathcanvas.engine.script.Execution} per top-level statement: its step budget, its calls, its
 * request to stop. {@code docs/reference/engine.md}, section <b>Scripting</b>, is the reference.
 */
package mathcanvas.engine.script;

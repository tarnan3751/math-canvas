package mathcanvas.engine.rule;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Bindings;

/**
 * What a computed replacement is given: the match and where it was found.
 *
 * @param bindings the pattern variables' values
 * @param subject the subexpression matched
 * @param context the context of the rewrite
 */
public record Match(Bindings bindings, Expr subject, Context context) {}

package mathcanvas.engine.expr;

/**
 * An expression: an immutable tree of records with structural equality. Code over expressions uses
 * an exhaustive pattern-matching {@code switch} over this sealed interface, with no {@code default}
 * branch, so a new kind is a compile error wherever it is not handled.
 */
public sealed interface Expr
    permits Num,
        Based,
        Constant,
        Bool,
        Symbol,
        Unit,
        Sum,
        Product,
        Power,
        Apply,
        Lambda,
        Relation,
        ListExpr,
        Vector,
        Matrix,
        SetExpr,
        Interval,
        Piecewise,
        Derivative,
        Integral,
        Limit,
        BigSum,
        BigProduct {}

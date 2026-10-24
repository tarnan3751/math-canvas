/**
 * The backend provider interface: each heavyweight operation is a typed {@link
 * mathcanvas.engine.provider.Operation} run through the {@link
 * mathcanvas.engine.provider.Providers} registry that the rule {@link
 * mathcanvas.engine.rule.Context} carries, so a second implementation can be registered for one
 * operation without touching its callers. {@code docs/reference/engine.md}, section
 * <b>Providers</b>, is the reference.
 */
package mathcanvas.engine.provider;

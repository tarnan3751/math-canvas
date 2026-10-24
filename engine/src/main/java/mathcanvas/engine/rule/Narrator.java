package mathcanvas.engine.rule;

import java.util.List;

/**
 * Turns a trace into human steps. Each curated operation has its own narrator, which groups its
 * rewrites; {@link DefaultNarrator} gives one step per rewrite.
 */
public interface Narrator {

  /**
   * Returns the steps of {@code trace}.
   *
   * @param trace the trace
   * @param ctx the context the operation ran under
   * @return the steps, in order
   */
  List<Step> narrate(Trace trace, Context ctx);
}

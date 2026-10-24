package mathcanvas.engine.provider;

import java.util.Optional;
import mathcanvas.engine.rule.Context;

/**
 * One implementation of an operation.
 *
 * @param <I> the operation, with its inputs
 * @param <O> the type of its result
 */
public interface Provider<I extends Operation<O>, O> {

  /**
   * Returns the provider's name, in kebab-case, unique among the providers of its operation; the
   * trace names the provider that answered.
   *
   * @return the name
   */
  String name();

  /**
   * Applies the operation.
   *
   * @param input the operation and its inputs
   * @param ctx the context: assumptions, precision, whether to trace
   * @param steps where to record the algorithm's steps; {@link Steps#NONE} when tracing is off
   * @return a correct result in any equivalent form, or empty when this provider does not answer
   *     for these inputs, and the next one is tried
   */
  Optional<O> apply(I input, Context ctx, Steps steps);
}

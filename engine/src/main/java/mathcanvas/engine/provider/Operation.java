package mathcanvas.engine.provider;

/**
 * A heavyweight operation with its inputs: the key a provider is registered under and the value it
 * is applied to. The operations are declared here as their briefs land.
 *
 * @param <O> the type of the result
 */
public sealed interface Operation<O> permits Gcd {

  /**
   * Returns the class of the result, with which a provider's answer is checked.
   *
   * @return the result's class
   */
  Class<O> outputType();

  /**
   * Returns a provider's result in the engine's normal form, so that callers never see which
   * provider ran.
   *
   * @param result a correct result for this operation's inputs, in any of its equivalent forms
   * @return the normal form
   */
  O normalise(O result);
}

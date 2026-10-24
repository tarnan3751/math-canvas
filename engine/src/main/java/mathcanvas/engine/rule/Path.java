package mathcanvas.engine.rule;

import java.util.List;

/**
 * Where a subexpression is: the operand indices ({@link mathcanvas.engine.match.Operands}) from the
 * root down.
 *
 * @param indices the indices, empty for the root
 */
public record Path(List<Integer> indices) {

  /** The root. */
  public static final Path ROOT = new Path(List.of());

  /** Copies the indices. */
  public Path {
    indices = List.copyOf(indices);
  }

  /**
   * Returns the path of the first {@code depth} entries of {@code stack}.
   *
   * @param stack the indices
   * @param depth how many are used
   * @return the path
   */
  static Path of(int[] stack, int depth) {
    Integer[] boxed = new Integer[depth];
    for (int i = 0; i < depth; i++) {
      boxed[i] = stack[i];
    }
    return new Path(List.of(boxed));
  }
}

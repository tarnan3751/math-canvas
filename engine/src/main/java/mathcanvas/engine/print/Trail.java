package mathcanvas.engine.print;

/**
 * Where the printer is in the tree it prints: the operand index ({@link
 * mathcanvas.engine.match.Operands}) of each subtree from the root down, linked to its parent. A
 * frozen trail stands for a number's synthetic display tree ({@link PrintForm#typed}), every part
 * of which is the number itself.
 *
 * @param parent the parent's trail, null for the root
 * @param index the operand index in the parent
 * @param frozen whether children share this trail
 */
record Trail(Trail parent, int index, boolean frozen) {

  /** The root. */
  static final Trail ROOT = new Trail(null, 0, false);

  /** The trail of operand {@code i}, or this one when frozen. */
  Trail child(int i) {
    return frozen ? this : new Trail(this, i, false);
  }

  /** This trail, frozen. */
  Trail freeze() {
    return new Trail(parent, index, true);
  }
}

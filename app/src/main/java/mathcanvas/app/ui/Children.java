package mathcanvas.app.ui;

import java.util.HashSet;
import java.util.List;
import javafx.scene.Node;

/**
 * Brings a parent's children to a new order by removing the nodes that left and inserting the nodes
 * that came or moved, so that the others stay in the scene: a node taken out of the scene and put
 * back, as {@code setAll} does with every child, has the style of its whole subtree worked out
 * again. FX thread only.
 */
final class Children {

  private Children() {}

  /**
   * Makes a child list equal to an order.
   *
   * @param children the parent's children
   * @param order the nodes in their new order, each once
   */
  static void reorder(List<Node> children, List<Node> order) {
    if (children.equals(order)) {
      return;
    }
    children.retainAll(new HashSet<>(order));
    for (int k = 0; k < order.size(); k++) {
      Node n = order.get(k);
      if (k >= children.size() || children.get(k) != n) {
        children.remove(n);
        children.add(k, n);
      }
    }
  }
}

package mathcanvas.canvas.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The axes box's fit and its map onto the cube. */
class Box3Test {

  @Test
  void theBoxFitsTheUnionOfTheBoundsAndWidensAnEmptyAxis() {
    Box3 b = Box3.fit(List.of(new double[] {-1, 2, 0, 1, 3, 3}, new double[] {0, 5, -4, 0, 3, 3}));
    assertThat(b).isEqualTo(new Box3(-1, 5, -4, 1, 2, 4));
    assertThat(Box3.fit(List.of())).isEqualTo(Box3.DEFAULT);
  }

  @Test
  void eachAxisMapsOntoTheCubeAndBack() {
    Box3 b = new Box3(-1, 5, -4, 1, 0, 100);
    assertThat(b.toCube(0, -1)).isEqualTo(-1);
    assertThat(b.toCube(1, 1)).isEqualTo(1);
    assertThat(b.toCube(2, 50)).isEqualTo(0);
    for (int a = 0; a < 3; a++) {
      assertThat(b.toWorld(a, b.toCube(a, 0.375))).isCloseTo(0.375, within(1e-12));
    }
  }

  @Test
  void anEmptyOrUnboundedBoxIsRefused() {
    assertThatThrownBy(() -> new Box3(0, 0, 0, 1, 0, 1)).hasMessageContaining("not empty");
    assertThatThrownBy(() -> new Box3(0, 1, 0, Double.POSITIVE_INFINITY, 0, 1))
        .hasMessageContaining("finite");
  }
}

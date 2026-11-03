package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@code append} and {@code setnth} ({@code docs/language.md} §12.1, §19). */
class ListUpdateTest {

  @Test
  void append() {
    assertThat(shown("append([1, 2], 3)")).isEqualTo("⟨1, 2, 3⟩");
    assertThat(shown("append([], x)")).isEqualTo("⟨x⟩");
    assertThat(shown("append([{1}], 2)")).isEqualTo("[{1}, 2]");
    assertThat(shown("append([[1, 2]], [3, 4])")).isEqualTo("[[1, 2], [3, 4]]");
    assertThat(shown("append(L, 3)")).isEqualTo("append(L, 3)");
    assertThat(error("append({1}, 2)")).isEqualTo("append needs a list, a vector or a matrix");
    assertThat(error("append(1, 2)")).isEqualTo("append needs a list, a vector or a matrix");
  }

  @Test
  void setnth() {
    assertThat(shown("setnth([1, 2, 3], 2, x)")).isEqualTo("⟨1, x, 3⟩");
    assertThat(shown("setnth([[1, 2], [3, 4]], 1, [0, 0])")).isEqualTo("[[0, 0], [3, 4]]");
    assertThat(shown("setnth([1, 2], k, 0)")).isEqualTo("setnth(⟨1, 2⟩, k, 0)");
    assertThat(error("setnth([1, 2], 3, 0)")).isEqualTo("setnth: index 3 is out of range 1 to 2");
    assertThat(error("setnth([1, 2], 1/2, 0)")).isEqualTo("setnth needs a whole-number index");
    assertThat(error("setnth({1, 2}, 1, 0)"))
        .isEqualTo("setnth needs a list, a vector or a matrix");
  }

  @Test
  void inAProgram() {
    assertThat(shown("L := []; for k in 1..4 do L := append(L, k^2) end; L"))
        .isEqualTo("⟨1, 4, 9, 16⟩");
    assertThat(
            shown(
                "L := [3, 1]; if true then t := nth(L, 1); L := setnth(L, 1, nth(L, 2));"
                    + " L := setnth(L, 2, t) end; L"))
        .isEqualTo("⟨1, 3⟩");
  }
}

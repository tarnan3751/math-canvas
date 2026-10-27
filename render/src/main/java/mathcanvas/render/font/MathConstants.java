package mathcanvas.render.font;

/** The values of the MATH table's constants. */
public final class MathConstants {

  private final int[] values;

  private MathConstants(int[] values) {
    this.values = values;
  }

  /**
   * Returns a constant.
   *
   * @param constant the constant
   * @return its value, in font units or a percentage
   */
  public int get(MathConstant constant) {
    return values[constant.ordinal()];
  }

  /**
   * Reads the MathConstants table: two int16 percentages, two uint16 heights, the MathValueRecords
   * (a value and a device-table offset, which is ignored), then one int16 percentage.
   */
  static MathConstants read(Bytes b, int at) {
    MathConstant[] all = MathConstant.values();
    int[] values = new int[all.length];
    values[0] = b.s16(at);
    values[1] = b.s16(at + 2);
    values[2] = b.u16(at + 4);
    values[3] = b.u16(at + 6);
    int record = at + 8;
    for (int k = 4; k < all.length - 1; k++) {
      values[k] = b.s16(record);
      record += 4;
    }
    values[all.length - 1] = b.s16(record);
    return new MathConstants(values);
  }
}

package mathcanvas.render.font;

/** Big-endian reads from the font file, as the OpenType data types. */
final class Bytes {

  private final byte[] data;

  Bytes(byte[] data) {
    this.data = data;
  }

  int length() {
    return data.length;
  }

  int u8(int at) {
    return data[at] & 0xFF;
  }

  int s8(int at) {
    return data[at];
  }

  int u16(int at) {
    return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
  }

  int s16(int at) {
    return (short) u16(at);
  }

  long u32(int at) {
    return ((long) u16(at) << 16) | u16(at + 2);
  }

  /** An offset32 as an index into the file, which is far below 2 GB. */
  int offset32(int at) {
    long value = u32(at);
    if (value > data.length) {
      throw new IllegalArgumentException("an offset beyond the file: " + value);
    }
    return (int) value;
  }

  /** A 2.14 fixed-point number. */
  double f2dot14(int at) {
    return s16(at) / 16384.0;
  }

  String tag(int at) {
    return new String(
        new char[] {(char) u8(at), (char) u8(at + 1), (char) u8(at + 2), (char) u8(at + 3)});
  }
}

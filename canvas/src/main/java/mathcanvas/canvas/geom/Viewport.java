package mathcanvas.canvas.geom;

/**
 * The visible window of the plane: a world rectangle shown in a pixel rectangle, y up in the world
 * and down on the screen. Immutable; pan, zoom and resize return new viewports.
 *
 * <p>With {@code aspectLocked} a world unit is as many pixels across as up: the constructor widens
 * the axis with fewer units per pixel about its centre until both match (to a relative 1e-12, so a
 * locked viewport is kept as it is rather than nudged by rounding on every operation).
 *
 * @param xMin the world x at the left edge
 * @param xMax the world x at the right edge, above {@code xMin}
 * @param yMin the world y at the bottom edge
 * @param yMax the world y at the top edge, above {@code yMin}
 * @param width the width in pixels, positive
 * @param height the height in pixels, positive
 * @param aspectLocked whether units are square
 */
public record Viewport(
    double xMin,
    double xMax,
    double yMin,
    double yMax,
    double width,
    double height,
    boolean aspectLocked) {

  /** The widest span on either axis that zoom and resize produce. */
  public static final double MAX_SPAN = 1e15;

  /**
   * The narrowest span relative to the window's largest coordinate that zoom produces; below it
   * neighbouring pixels share a double.
   */
  public static final double MIN_RELATIVE_SPAN = 1e-12;

  /** The narrowest span that zoom produces near the origin. */
  public static final double MIN_SPAN = 1e-100;

  private static final double SQUARE_TOLERANCE = 1e-12;

  /**
   * Checks the window and squares a locked one.
   *
   * @throws IllegalArgumentException if a bound, a span or a size is not finite, a span is not
   *     positive, or a size is not positive
   */
  public Viewport {
    if (!(Double.isFinite(xMin) && Double.isFinite(xMax) && xMin < xMax)
        || !Double.isFinite(xMax - xMin)) {
      throw new IllegalArgumentException("bad x range: " + xMin + " .. " + xMax);
    }
    if (!(Double.isFinite(yMin) && Double.isFinite(yMax) && yMin < yMax)
        || !Double.isFinite(yMax - yMin)) {
      throw new IllegalArgumentException("bad y range: " + yMin + " .. " + yMax);
    }
    if (!(Double.isFinite(width) && width > 0 && Double.isFinite(height) && height > 0)) {
      throw new IllegalArgumentException("bad pixel size: " + width + " x " + height);
    }
    if (aspectLocked) {
      double ux = (xMax - xMin) / width;
      double uy = (yMax - yMin) / height;
      double u = Math.max(ux, uy);
      if (Math.abs(ux - uy) > SQUARE_TOLERANCE * u) {
        if (ux < uy) {
          double c = xMin / 2 + xMax / 2;
          xMin = c - u * width / 2;
          xMax = c + u * width / 2;
        } else {
          double c = yMin / 2 + yMax / 2;
          yMin = c - u * height / 2;
          yMax = c + u * height / 2;
        }
      }
    }
  }

  /**
   * A viewport centred on a world point.
   *
   * @param centreX the world x at the centre
   * @param centreY the world y at the centre
   * @param unitsPerPixel world units per pixel on both axes
   * @param width the width in pixels
   * @param height the height in pixels
   * @param aspectLocked whether units are square
   * @return the viewport
   */
  public static Viewport centred(
      double centreX,
      double centreY,
      double unitsPerPixel,
      double width,
      double height,
      boolean aspectLocked) {
    double hw = unitsPerPixel * width / 2;
    double hh = unitsPerPixel * height / 2;
    return new Viewport(
        centreX - hw, centreX + hw, centreY - hh, centreY + hh, width, height, aspectLocked);
  }

  /**
   * Pixels per world unit along x.
   *
   * @return {@code width / (xMax - xMin)}
   */
  public double pixelsPerUnitX() {
    return width / (xMax - xMin);
  }

  /**
   * Pixels per world unit along y.
   *
   * @return {@code height / (yMax - yMin)}
   */
  public double pixelsPerUnitY() {
    return height / (yMax - yMin);
  }

  /**
   * The screen x of a world x.
   *
   * @param x a world x
   * @return pixels from the left edge
   */
  public double toScreenX(double x) {
    return (x - xMin) * pixelsPerUnitX();
  }

  /**
   * The screen y of a world y.
   *
   * @param y a world y
   * @return pixels from the top edge
   */
  public double toScreenY(double y) {
    return (yMax - y) * pixelsPerUnitY();
  }

  /**
   * The world x of a screen x.
   *
   * @param sx pixels from the left edge
   * @return the world x
   */
  public double toWorldX(double sx) {
    return xMin + sx * (xMax - xMin) / width;
  }

  /**
   * The world y of a screen y.
   *
   * @param sy pixels from the top edge
   * @return the world y
   */
  public double toWorldY(double sy) {
    return yMax - sy * (yMax - yMin) / height;
  }

  /**
   * Moves the content by a pixel offset, as a drag does: the world point under a pixel ends up
   * under the pixel {@code (dx, dy)} away.
   *
   * @param dx pixels to the right
   * @param dy pixels down
   * @return the moved viewport
   */
  public Viewport pan(double dx, double dy) {
    double wx = dx * (xMax - xMin) / width;
    double wy = dy * (yMax - yMin) / height;
    return new Viewport(xMin - wx, xMax - wx, yMin + wy, yMax + wy, width, height, aspectLocked);
  }

  /**
   * Zooms about a screen point, which keeps showing the same world point. Returns this viewport
   * unchanged when either span would leave the supported range ({@link #MAX_SPAN}, {@link
   * #MIN_RELATIVE_SPAN}, {@link #MIN_SPAN}).
   *
   * @param factor above 1 zooms in, below 1 out; positive and finite
   * @param sx the fixed point's screen x
   * @param sy the fixed point's screen y
   * @return the zoomed viewport
   * @throws IllegalArgumentException if the factor is not positive and finite
   */
  public Viewport zoom(double factor, double sx, double sy) {
    if (!(Double.isFinite(factor) && factor > 0)) {
      throw new IllegalArgumentException("bad zoom factor: " + factor);
    }
    double wx = toWorldX(sx);
    double wy = toWorldY(sy);
    double nxMin = wx - (wx - xMin) / factor;
    double nxMax = wx + (xMax - wx) / factor;
    double nyMin = wy - (wy - yMin) / factor;
    double nyMax = wy + (yMax - wy) / factor;
    if (!supported(nxMin, nxMax) || !supported(nyMin, nyMax)) {
      return this;
    }
    return new Viewport(nxMin, nxMax, nyMin, nyMax, width, height, aspectLocked);
  }

  /**
   * The same centre and scale in a new pixel size: a larger view shows more of the plane.
   *
   * @param newWidth the width in pixels, positive
   * @param newHeight the height in pixels, positive
   * @return the resized viewport, or this one when the size is unchanged
   */
  public Viewport resize(double newWidth, double newHeight) {
    if (newWidth == width && newHeight == height) {
      return this;
    }
    double cx = xMin / 2 + xMax / 2;
    double cy = yMin / 2 + yMax / 2;
    double hw = (xMax - xMin) / width * newWidth / 2;
    double hh = (yMax - yMin) / height * newHeight / 2;
    return new Viewport(cx - hw, cx + hw, cy - hh, cy + hh, newWidth, newHeight, aspectLocked);
  }

  /**
   * This window with the aspect lock set or cleared; locking squares the units.
   *
   * @param locked whether units are square
   * @return the viewport
   */
  public Viewport withAspectLocked(boolean locked) {
    return locked == aspectLocked
        ? this
        : new Viewport(xMin, xMax, yMin, yMax, width, height, locked);
  }

  private static boolean supported(double min, double max) {
    double span = max - min;
    return Double.isFinite(min)
        && Double.isFinite(max)
        && span <= MAX_SPAN
        && span >= MIN_SPAN
        && span >= MIN_RELATIVE_SPAN * Math.max(Math.abs(min), Math.abs(max));
  }
}

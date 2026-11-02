package mathcanvas.engine.linalg.numeric;

import java.util.Random;
import mathcanvas.engine.provider.Decompose;
import mathcanvas.engine.provider.Decompose.Kind;
import mathcanvas.engine.provider.Decomposition;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;

/**
 * Dense {@code double} matrix helpers for the tests, written out here, independent of Hipparchus.
 */
final class Mat {

  private Mat() {}

  /** Runs the operation through the default registry, normalised. */
  static Decomposition run(Kind kind, double[][] a, double[] b) {
    return Providers.defaults().run(new Decompose(kind, a, b), Context.DEFAULT, Steps.NONE).value();
  }

  static Decomposition run(Kind kind, double[][] a) {
    return run(kind, a, null);
  }

  static double[][] random(Random random, int rows, int columns) {
    double[][] m = new double[rows][columns];
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < columns; j++) {
        m[i][j] = random.nextDouble() * 20 - 10;
      }
    }
    return m;
  }

  static double[][] times(double[][] a, double[][] b) {
    double[][] c = new double[a.length][b[0].length];
    for (int i = 0; i < a.length; i++) {
      for (int k = 0; k < b.length; k++) {
        for (int j = 0; j < b[0].length; j++) {
          c[i][j] += a[i][k] * b[k][j];
        }
      }
    }
    return c;
  }

  static double[][] transpose(double[][] a) {
    double[][] t = new double[a[0].length][a.length];
    for (int i = 0; i < a.length; i++) {
      for (int j = 0; j < a[0].length; j++) {
        t[j][i] = a[i][j];
      }
    }
    return t;
  }

  static double[][] identity(int n) {
    double[][] m = new double[n][n];
    for (int i = 0; i < n; i++) {
      m[i][i] = 1;
    }
    return m;
  }

  static double[][] negate(double[][] a) {
    double[][] m = new double[a.length][a[0].length];
    for (int i = 0; i < a.length; i++) {
      for (int j = 0; j < a[0].length; j++) {
        m[i][j] = -a[i][j];
      }
    }
    return m;
  }

  /** The largest entry in magnitude. */
  static double norm(double[][] a) {
    double max = 0;
    for (double[] row : a) {
      for (double x : row) {
        max = Math.max(max, Math.abs(x));
      }
    }
    return max;
  }

  /** The largest difference of two matrices of one shape, entry by entry. */
  static double distance(double[][] a, double[][] b) {
    if (a.length != b.length || a[0].length != b[0].length) {
      throw new AssertionError("shapes differ");
    }
    double max = 0;
    for (int i = 0; i < a.length; i++) {
      for (int j = 0; j < a[0].length; j++) {
        max = Math.max(max, Math.abs(a[i][j] - b[i][j]));
      }
    }
    return max;
  }
}

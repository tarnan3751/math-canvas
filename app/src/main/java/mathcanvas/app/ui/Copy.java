package mathcanvas.app.ui;

import java.util.Objects;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Path;

/**
 * What the Copy action of a typeset output puts on the clipboard: the selected subexpression, or
 * the whole output when nothing is selected, as Unicode text (the plain-text form) and as LaTeX
 * (under the type {@code application/x-latex}).
 *
 * @param unicode the Unicode form
 * @param latex the LaTeX form
 */
public record Copy(String unicode, String latex) {

  /** The clipboard type of the LaTeX form. */
  public static final DataFormat LATEX =
      Objects.requireNonNullElseGet(
          DataFormat.lookupMimeType("application/x-latex"),
          () -> new DataFormat("application/x-latex"));

  /**
   * The copy of a value's subexpression.
   *
   * @param shown the display tree the output typesets
   * @param path the selection, a path into that tree, or null for the whole
   * @return the copy
   */
  public static Copy of(Expr shown, Path path) {
    Expr e = shown;
    if (path != null) {
      for (int index : path.indices()) {
        e = Operands.of(e).get(index);
      }
    }
    return new Copy(UnicodePrinter.INSTANCE.print(e), LatexPrinter.INSTANCE.print(e));
  }

  /**
   * The copy of a definition's echo.
   *
   * @param definition the definition
   * @return the copy
   */
  public static Copy of(Statement.Definition definition) {
    Expr head =
        definition.params().isEmpty()
            ? definition.name()
            : Apply.of(definition.name(), definition.params());
    return new Copy(
        UnicodePrinter.INSTANCE.echo(definition),
        LatexPrinter.INSTANCE.print(head)
            + " := "
            + LatexPrinter.INSTANCE.print(definition.body()));
  }

  /** Puts both forms on the system clipboard; on the FX thread. */
  public void toClipboard() {
    ClipboardContent content = new ClipboardContent();
    content.putString(unicode);
    content.put(LATEX, latex);
    Clipboard.getSystemClipboard().setContent(content);
  }
}

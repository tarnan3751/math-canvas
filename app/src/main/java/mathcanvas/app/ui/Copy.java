package mathcanvas.app.ui;

import java.util.Objects;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import mathcanvas.app.document.Result;
import mathcanvas.app.eval.Typeset;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.LayoutPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Path;

/**
 * What the Copy action of a typeset output puts on the clipboard: the selected subexpression, or
 * the whole output when nothing is selected, as Unicode text (the plain-text form) and as LaTeX
 * (under the type {@code application/x-latex}).
 *
 * @param unicode the Unicode form
 * @param latex the LaTeX form, or null when there is none to give
 */
public record Copy(String unicode, String latex) {

  /** The clipboard type of the LaTeX form. */
  public static final DataFormat LATEX =
      Objects.requireNonNullElseGet(
          DataFormat.lookupMimeType("application/x-latex"),
          () -> new DataFormat("application/x-latex"));

  /**
   * The copy of a value's subexpression, or of the whole value. In the exact format the selection
   * is a subtree of the display tree; in another format, whose printed tree is the value prepared
   * for the format, a selection is copied as the text of its layout only, and the whole value as
   * its text and its LaTeX in the format.
   *
   * @param value the value
   * @param path the selection, or null for the whole
   * @return the copy
   */
  public static Copy of(Result.Value value, Path path) {
    if (value.format().equals(Format.EXACT)) {
      return of(value.shown(), path);
    }
    if (path == null) {
      return new Copy(
          value.text(), LatexPrinter.INSTANCE.withFormat(value.format()).display(value.value()));
    }
    Layout node = Typeset.find(value.layout(), path);
    return new Copy(node == null ? value.text() : LayoutPrinter.flatten(node), null);
  }

  /**
   * The copy of a printed value's subexpression, or of the whole value ({@code docs/language.md}
   * §19.2), as a value's is.
   *
   * @param printed the printed value
   * @param path the selection, or null for the whole
   * @return the copy
   */
  public static Copy of(Result.Printed printed, Path path) {
    if (printed.format().equals(Format.EXACT)) {
      return of(printed.shown(), path);
    }
    if (path == null) {
      return new Copy(
          printed.text(),
          LatexPrinter.INSTANCE.withFormat(printed.format()).display(printed.shown()));
    }
    Layout node = Typeset.find(printed.layout(), path);
    return new Copy(node == null ? printed.text() : LayoutPrinter.flatten(node), null);
  }

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
    if (latex != null) {
      content.put(LATEX, latex);
    }
    Clipboard.getSystemClipboard().setContent(content);
  }
}

package mathcanvas.app.ui;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import mathcanvas.app.eval.CanvasFrame;
import mathcanvas.app.eval.Document;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.view.CanvasView;

/**
 * The canvas side of the window: a small toolbar over the coordinate plane ({@link CanvasView}). It
 * follows the document: the view draws the content of the document's canvas frame, the view's
 * viewport (a pan, a zoom, a resize, the aspect lock) goes to the document through {@link
 * Document#setViewport}, which keeps it in the state without an undo step (the session file does
 * not hold it yet), and a viewport the document changes comes back to the view. The colours the
 * view's CSS gives it are sent to the document ({@link Document#setCanvasTheme}) whenever the theme
 * changes, so the content is built in them. Hovering gives the trace readout ({@link Readout}).
 * Escape on the canvas stops the running jobs, as it does in a cell. Touched only on the FX thread.
 */
public final class CanvasPane extends BorderPane {

  private final Document document;
  private final CanvasView view = new CanvasView();
  private final ReadOnlyStringWrapper readout = new ReadOnlyStringWrapper(this, "readout");
  private CanvasFrame frame = CanvasFrame.EMPTY;
  private CanvasTheme sentTheme;

  /**
   * A pane following a document.
   *
   * @param document the document
   */
  public CanvasPane(Document document) {
    this.document = document;
    setId("canvas-pane");
    getStyleClass().add("canvas-pane");
    view.setId("canvas");
    view.setFocusTraversable(true);
    view.setViewport(document.state().viewport());

    ToggleButton aspect = new ToggleButton(Messages.text("canvas.aspect"));
    aspect.setId("canvas-aspect");
    aspect.setTooltip(new Tooltip(Messages.text("canvas.aspect.tip")));
    aspect.setSelected(view.getViewport().aspectLocked());
    aspect
        .selectedProperty()
        .addListener((o, was, now) -> view.setViewport(view.getViewport().withAspectLocked(now)));
    view.viewportProperty()
        .addListener(
            (o, was, now) -> {
              aspect.setSelected(now.aspectLocked());
              document.setViewport(now);
              showReadout();
            });
    ToolBar toolbar = new ToolBar(aspect);
    toolbar.getStyleClass().add("canvas-toolbar");

    document.addListener(
        change -> {
          if (!change.after().viewport().equals(view.getViewport())) {
            view.setViewport(change.after().viewport());
          }
        });
    document.canvasFrameProperty().subscribe(this::show);
    view.hoveredHitProperty().subscribe(h -> showReadout());
    view.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> view.requestFocus());
    addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            document.stop();
            e.consume();
          }
        });
    sceneProperty()
        .subscribe(
            scene -> {
              if (scene != null) {
                sendTheme();
              } else {
                readout.set(null); // hidden: nothing is hovered
              }
            });

    setTop(toolbar);
    setCenter(view);
  }

  /**
   * Shows a frame: its content on the plane. The document's frames come here; the tests show frames
   * of their own.
   *
   * @param frame the frame
   */
  public void show(CanvasFrame frame) {
    this.frame = frame;
    view.setContent(frame.content());
  }

  /**
   * The frame shown.
   *
   * @return the frame
   */
  public CanvasFrame frame() {
    return frame;
  }

  /**
   * Sends the document the colours the view's CSS now gives it, if they changed: called when the
   * pane enters a scene and whenever the scene's theme changes.
   */
  void sendTheme() {
    view.applyCss();
    CanvasTheme theme = view.theme();
    if (!theme.equals(sentTheme)) {
      sentTheme = theme;
      document.setCanvasTheme(theme);
    }
  }

  private void showReadout() {
    Hit h = view.getHoveredHit();
    readout.set(
        h == null
            ? null
            : Readout.text(h, view.getViewport(), document.state().settings().format()));
  }

  /**
   * The trace readout: the coordinates of the hovered point, or null when nothing is hovered.
   *
   * @return the property
   */
  public ReadOnlyStringProperty readoutProperty() {
    return readout.getReadOnlyProperty();
  }

  /**
   * The coordinate plane, for the tests.
   *
   * @return the view
   */
  public CanvasView view() {
    return view;
  }
}

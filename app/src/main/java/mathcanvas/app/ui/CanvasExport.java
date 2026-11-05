package mathcanvas.app.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import mathcanvas.app.eval.Document;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.space.SpaceScene;
import mathcanvas.canvas.view.CanvasContent;
import mathcanvas.canvas.view.CanvasView;
import mathcanvas.canvas.view.Export;
import mathcanvas.canvas.view.PlotContent;
import mathcanvas.canvas.view.SpaceView;

/**
 * File → Export: the canvas as a PNG image or an SVG document, the grid and the objects as the view
 * shows them, without the overlay ({@link Export}).
 *
 * <p>A PNG is the view's pixel size times a scale (1 to 4, those that fit {@link
 * Export#MAX_IMAGE_SIDE}; when none does, the status line says so), in the view's theme. An SVG is
 * the view's pixel size, in the light theme unless the dialog's "current theme" box is ticked; the
 * objects are drawn again in the light colours from the canvas the view shows. Both are drawn on
 * the FX thread with the view's label measure (the 3D view's, its scene at rest through its camera,
 * while it is shown), a user's request and bounded work, the same drawing as one frame; the file is
 * encoded and written on an export thread of its own, and the outcome, a failure of the encoding or
 * of the write included, is told to the status line. The file chooser opens in the folder of the
 * last export, a preference kept in {@code settings.json}, and a name chosen that does not end in
 * the kind's extension gets it, asking before it replaces a file. The questions go through {@link
 * Dialogs}, which the UI tests answer.
 */
public final class CanvasExport {

  /** The scales an image is offered at. */
  static final List<Integer> SCALES = List.of(1, 2, 3, 4);

  private static final Executor WRITER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "mathcanvas-export");
            t.setDaemon(true);
            return t;
          });

  private final Window owner;
  private final Document document;
  private final CanvasView view;
  private SpaceView space;
  private BooleanSupplier spaceShown;
  private final PreferenceProperties preferences;
  private final Consumer<Notice> notice;
  private Dialogs dialogs;

  /**
   * The export of a window's canvas.
   *
   * @param owner the window the dialogs belong to
   * @param document the document
   * @param view the canvas view
   * @param preferences the preferences, whose export folder it reads and sets
   * @param notice tells the status line the outcome
   */
  public CanvasExport(
      Window owner,
      Document document,
      CanvasView view,
      PreferenceProperties preferences,
      Consumer<Notice> notice) {
    this.owner = owner;
    this.document = Objects.requireNonNull(document, "document");
    this.view = Objects.requireNonNull(view, "view");
    this.preferences = Objects.requireNonNull(preferences, "preferences");
    this.notice = Objects.requireNonNull(notice, "notice");
    this.dialogs = windowDialogs();
  }

  /**
   * What File → Export asks: the scale of an image, whether an SVG takes the current theme, and the
   * file. The window's are JavaFX dialogs and a file chooser; the UI tests answer for them.
   */
  public interface Dialogs {

    /**
     * Asks the scale of an image.
     *
     * @param offered the scales offered, as their labels, the first the default
     * @return the index of the one chosen, or empty when cancelled
     */
    Optional<Integer> scale(List<String> offered);

    /**
     * Asks whether an SVG is drawn in the current theme rather than the light one.
     *
     * @return the answer, or empty when cancelled
     */
    Optional<Boolean> currentTheme();

    /**
     * Asks the file to write.
     *
     * @param title the dialog's title
     * @param filter the description of the files of the kind
     * @param extension the kind's extension, such as {@code png}
     * @param folder where to open, when there is a folder to open in
     * @return the file, or empty when cancelled
     */
    Optional<Path> file(String title, String filter, String extension, Optional<File> folder);

    /**
     * Asks whether to replace a file that exists: the name chosen did not end in the kind's
     * extension, so the chooser asked nothing about the file with the extension added.
     *
     * @param file the file with its extension
     * @return true to replace it
     */
    boolean confirmReplace(Path file);
  }

  /**
   * Answers File → Export's questions instead of the dialogs: for the tests.
   *
   * @param dialogs the answers
   */
  public void setDialogs(Dialogs dialogs) {
    this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
  }

  /**
   * Asks the scale and the file, then exports a PNG. FX thread. When not even the view's own size
   * fits an image, says so in the status line instead.
   *
   * @return completed as {@link #png} completes, or with null when nothing was exported
   */
  public CompletableFuture<Path> choosePng() {
    Viewport v = shownViewport();
    List<String> labels = new ArrayList<>();
    List<Integer> scales = new ArrayList<>();
    for (int s : SCALES) {
      if (Export.fits(v, s)) {
        labels.add(scaleLabel(v, s));
        scales.add(s);
      }
    }
    if (scales.isEmpty()) {
      notice.accept(new Notice(Messages.text("export.tooLarge", Export.MAX_IMAGE_SIDE), ""));
      return CompletableFuture.completedFuture(null);
    }
    Optional<Integer> chosen = dialogs.scale(labels);
    if (chosen.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    int scale = scales.get(chosen.get());
    return dialogs
        .file(
            Messages.text("export.png.title"), Messages.text("export.png.filter"), "png", folder())
        .map(f -> exportTo(f, "png", file -> png(file, scale)))
        .orElse(CompletableFuture.completedFuture(null));
  }

  /**
   * Asks the theme and the file, then exports an SVG. FX thread.
   *
   * @return completed as {@link #svg} completes, or with null when nothing was exported
   */
  public CompletableFuture<Path> chooseSvg() {
    Optional<Boolean> current = dialogs.currentTheme();
    if (current.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return dialogs
        .file(
            Messages.text("export.svg.title"), Messages.text("export.svg.filter"), "svg", folder())
        .map(f -> exportTo(f, "svg", file -> svg(file, current.get())))
        .orElse(CompletableFuture.completedFuture(null));
  }

  /**
   * Exports to a chosen file, its extension added when its name does not end in it; a file the
   * extension's adding makes, which exists, is replaced only when the user agrees, since the
   * chooser asked about the name chosen and not about it.
   */
  private CompletableFuture<Path> exportTo(
      Path chosen, String extension, Function<Path, CompletableFuture<Path>> export) {
    Path file = withExtension(chosen, extension);
    if (!file.equals(chosen) && Files.exists(file) && !dialogs.confirmReplace(file)) {
      return CompletableFuture.completedFuture(null);
    }
    return export.apply(file);
  }

  /** A file name ending in the kind's extension: {@code plot} and {@code plot.v2} get it added. */
  static Path withExtension(Path file, String extension) {
    String name = file.getFileName().toString();
    return name.endsWith("." + extension) ? file : file.resolveSibling(name + "." + extension);
  }

  private static String scaleLabel(Viewport v, int scale) {
    return Messages.text(
        "export.png.scaleOption",
        scale,
        (int) Math.ceil(v.width() * scale),
        (int) Math.ceil(v.height() * scale));
  }

  /** The window's dialogs: a choice of scale, a check box for the theme, a file chooser. */
  private Dialogs windowDialogs() {
    return new Dialogs() {
      @Override
      public Optional<Integer> scale(List<String> offered) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(offered.getFirst(), offered);
        dialog.initOwner(owner);
        dialog.setTitle(Messages.text("export.png.title"));
        dialog.setHeaderText(Messages.text("export.png.title"));
        dialog.setContentText(Messages.text("export.png.scale"));
        DialogStyle.apply(dialog.getDialogPane(), owner);
        return dialog.showAndWait().map(offered::indexOf);
      }

      @Override
      public Optional<Boolean> currentTheme() {
        CheckBox current = new CheckBox(Messages.text("export.svg.currentTheme"));
        current.setId("export-current-theme");
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(Messages.text("export.svg.title"));
        dialog.setHeaderText(Messages.text("export.svg.title"));
        dialog.getDialogPane().setContent(current);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyle.apply(dialog.getDialogPane(), owner);
        dialog.setResultConverter(b -> b == ButtonType.OK ? current.isSelected() : null);
        return dialog.showAndWait();
      }

      @Override
      public Optional<Path> file(
          String title, String filter, String extension, Optional<File> folder) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser
            .getExtensionFilters()
            .add(new FileChooser.ExtensionFilter(filter, "*." + extension));
        chooser.setInitialFileName("canvas." + extension);
        folder.ifPresent(chooser::setInitialDirectory);
        return Optional.ofNullable(chooser.showSaveDialog(owner)).map(File::toPath);
      }

      @Override
      public boolean confirmReplace(Path file) {
        Alert alert =
            new Alert(
                Alert.AlertType.CONFIRMATION,
                Messages.text("export.replace", file.getFileName()),
                ButtonType.OK,
                ButtonType.CANCEL);
        alert.initOwner(owner);
        alert.setHeaderText(null);
        DialogStyle.apply(alert.getDialogPane(), owner);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
      }
    };
  }

  /** The folder of the last export, when it still is one. */
  private Optional<File> folder() {
    try {
      return preferences
          .exportFolder()
          .get()
          .map(Path::of)
          .filter(Files::isDirectory)
          .map(Path::toFile);
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
  }

  /**
   * Exports the canvas as a PNG image, the view's pixel size times a scale, in the view's theme. FX
   * thread.
   *
   * @param file the file to write
   * @param scale the scale, one that {@link Export#fits}
   * @return completed, on the FX thread, once the file is written and the outcome told, with the
   *     file, or exceptionally when it could not be written
   */
  public CompletableFuture<Path> png(Path file, int scale) {
    WritableImage image =
        spaceShown()
            ? Export.image(
                space.sceneContentProperty().get(),
                space.getCamera(),
                view.theme(),
                shownViewport().width(),
                shownViewport().height(),
                space::labelWidth,
                scale)
            : Export.image(
                view.getViewport(), view.theme(), view.getContent(), view::labelWidth, scale);
    int width = (int) image.getWidth();
    int height = (int) image.getHeight();
    int[] argb = new int[width * height];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
    return write(
        file,
        () -> {
          BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
          out.setRGB(0, 0, width, height, argb, 0, width);
          if (!ImageIO.write(out, "png", file.toFile())) {
            throw new IOException("no PNG writer");
          }
        });
  }

  /**
   * Exports the canvas as an SVG document the view's pixel size. FX thread.
   *
   * @param file the file to write
   * @param currentTheme whether to draw in the view's theme rather than the light one
   * @return completed, on the FX thread, once the file is written and the outcome told, with the
   *     file, or exceptionally when it could not be written
   */
  public CompletableFuture<Path> svg(Path file, boolean currentTheme) {
    CanvasTheme theme = currentTheme ? view.theme() : CanvasTheme.LIGHT;
    if (spaceShown()) {
      SpaceScene scene =
          theme.equals(view.theme())
              ? space.sceneContentProperty().get()
              : SpaceScene.build(document.canvasFrameProperty().get().state(), theme);
      String text =
          Export.svg(
              scene,
              space.getCamera(),
              theme,
              shownViewport().width(),
              shownViewport().height(),
              space::labelWidth);
      return write(file, () -> Files.writeString(file, text, StandardCharsets.UTF_8));
    }
    PlotContent content =
        theme.equals(view.theme())
            ? view.getContent()
            : CanvasContent.build(
                    document.canvasFrameProperty().get().state(), document.state().names(), theme)
                .content();
    String text = Export.svg(view.getViewport(), theme, content, view::labelWidth);
    return write(file, () -> Files.writeString(file, text, StandardCharsets.UTF_8));
  }

  // Phase 7d: the 3D view, exported while it is shown.

  /**
   * Exports the 3D view while it is shown, in the plane's place.
   *
   * @param view the 3D view
   * @param shown whether it is shown
   */
  public void setSpace(SpaceView view, BooleanSupplier shown) {
    this.space = Objects.requireNonNull(view, "view");
    this.spaceShown = Objects.requireNonNull(shown, "shown");
  }

  private boolean spaceShown() {
    return space != null && spaceShown.getAsBoolean();
  }

  /** The viewport of the view shown: the plane's, or the 3D view's pixels. */
  private Viewport shownViewport() {
    return spaceShown()
        ? Export.pixelViewport(
            Math.max(1, Math.round(space.getWidth())), Math.max(1, Math.round(space.getHeight())))
        : view.getViewport();
  }

  /** Something that writes a file. */
  @FunctionalInterface
  private interface Writing {
    void run() throws IOException;
  }

  private CompletableFuture<Path> write(Path file, Writing writing) {
    CompletableFuture<Path> done = new CompletableFuture<>();
    WRITER.execute(
        () -> {
          Exception failed = null;
          try {
            writing.run();
          } catch (IOException | RuntimeException e) {
            failed = e;
          }
          Exception error = failed;
          Platform.runLater(
              () -> {
                if (error == null) {
                  Path parent = file.toAbsolutePath().getParent();
                  if (parent != null) {
                    preferences.exportFolder().set(Optional.of(parent.toString()));
                  }
                  notice.accept(new Notice(Messages.text("export.done", file.getFileName()), ""));
                  done.complete(file);
                } else {
                  notice.accept(
                      new Notice(
                          Messages.text("export.failed", file.getFileName()),
                          String.valueOf(error.getMessage())));
                  done.completeExceptionally(error);
                }
              });
        });
    return done;
  }
}

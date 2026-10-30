package mathcanvas.app;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.DocumentState.StoredCell;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.eval.CanvasJob;
import mathcanvas.app.eval.Document;
import mathcanvas.app.json.Json;
import mathcanvas.app.persist.SessionFile;
import mathcanvas.app.persist.SettingsFile;
import mathcanvas.app.persist.Store;
import mathcanvas.app.text.Messages;
import mathcanvas.app.ui.Notice;
import mathcanvas.app.ui.PreferenceProperties;
import mathcanvas.canvas.sample.Samplers;

/**
 * What the window works on: the document loaded from the data directory, its evaluation worker, the
 * preferences, the store that saves both, and a notice for the status line. Confined to the FX
 * thread, like the document.
 *
 * <p>Loading reads {@code settings.json}, then {@code session.json}; a missing file gives the
 * defaults, an unreadable one is set aside by the store and named in the notice. Every change of
 * the document or the preferences that alters a file's content asks the store to save it
 * (debounced); {@link #close} flushes, on the FX thread, at exit only.
 */
final class Workspace implements AutoCloseable {

  /** The evaluation thread's stack: the parser's nesting cap needs more than 1 MB. */
  private static final long WORKER_STACK = 2L << 20;

  private final Document document;
  private final PreferenceProperties preferences;
  private final ReadOnlyObjectWrapper<Notice> notice = new ReadOnlyObjectWrapper<>();
  private final ExecutorService worker;
  private final Store store;
  private Json savedSession;
  private Json savedSettings;

  private Workspace(
      DocumentState state,
      Preferences preferences,
      Store store,
      List<Notice> notices,
      Consumer<Runnable> ui) {
    this.worker =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(null, r, "mathcanvas-eval", WORKER_STACK);
              t.setDaemon(true);
              return t;
            });
    this.document =
        new Document(
            state,
            worker,
            ui::accept,
            System::nanoTime,
            CanvasJob.Sampling.of(new Samplers()),
            CanvasJob.Content.STANDARD);
    this.preferences = new PreferenceProperties(preferences);
    document.setTracing(preferences.showSteps());
    this.store = store;
    if (!notices.isEmpty()) {
      notice.set(
          new Notice(
              String.join(" ", notices.stream().map(Notice::text).toList()),
              String.join("\n", notices.stream().map(Notice::detail).toList()).strip()));
    }
    if (store != null) {
      savedSession = SessionFile.encode(state);
      savedSettings = settingsTree();
    }
    document.addListener(change -> save());
    this.preferences.addListener(o -> save());
    this.preferences.showSteps().addListener((o, was, now) -> document.setTracing(now));
  }

  /**
   * Opens the data directory and loads what it holds.
   *
   * @param directory the data directory
   * @param ui runs a task on the FX thread ({@code Platform::runLater})
   * @return the workspace; its pending cells start evaluating at once
   */
  static Workspace open(Path directory, Consumer<Runnable> ui) {
    List<Notice> notices = new ArrayList<>();
    Workspace[] self = new Workspace[1];
    Store store;
    try {
      store =
          Store.open(
              directory,
              Clock.systemUTC(),
              Store.SAVE_DELAY_MILLIS,
              e ->
                  ui.accept(
                      () ->
                          self[0].notice.set(
                              new Notice(
                                  Messages.text("status.saveFailed"),
                                  String.valueOf(e.getMessage())))));
    } catch (IOException e) {
      store = null;
      notices.add(
          new Notice(
              Messages.text("status.noDataDirectory", directory), String.valueOf(e.getMessage())));
    }
    SettingsFile.Stored settings = SettingsFile.Stored.DEFAULT;
    List<StoredCell> cells = List.of();
    if (store != null) {
      settings =
          loaded(
              store.load(SettingsFile.NAME, SettingsFile::decode),
              SettingsFile.NAME,
              SettingsFile.Stored.DEFAULT,
              notices);
      cells =
          loaded(
              store.load(SessionFile.NAME, SessionFile::decode),
              SessionFile.NAME,
              List.of(),
              notices);
    }
    DocumentState state = DocumentState.of(cells, settings.settings());
    self[0] = new Workspace(state, settings.preferences(), store, notices, ui);
    self[0].document.start();
    return self[0];
  }

  private static <T> T loaded(Store.Loaded<T> loaded, String name, T otherwise, List<Notice> out) {
    return switch (loaded) {
      case Store.Loaded.Read<T> r -> r.value();
      case Store.Loaded.Absent<T> _ -> otherwise;
      case Store.Loaded.SetAside<T> s -> {
        out.add(
            new Notice(
                Messages.text("status.setAside", name, s.aside().getFileName()), s.reason()));
        yield otherwise;
      }
      case Store.Loaded.Unreadable<T> u -> {
        out.add(new Notice(Messages.text("status.unreadable", name), u.reason()));
        yield otherwise;
      }
    };
  }

  Document document() {
    return document;
  }

  PreferenceProperties preferences() {
    return preferences;
  }

  ReadOnlyObjectProperty<Notice> noticeProperty() {
    return notice.getReadOnlyProperty();
  }

  private Json settingsTree() {
    return SettingsFile.encode(
        new SettingsFile.Stored(document.state().settings(), preferences.get()));
  }

  private void save() {
    if (store == null) {
      return;
    }
    Json session = SessionFile.encode(document.state());
    if (!session.equals(savedSession)) {
      savedSession = session;
      store.saveLater(SessionFile.NAME, session);
    }
    Json settings = settingsTree();
    if (!settings.equals(savedSettings)) {
      savedSettings = settings;
      store.saveLater(SettingsFile.NAME, settings);
    }
  }

  /** Stops the evaluation and writes what is unsaved; on the FX thread, at exit. */
  @Override
  public void close() {
    document.stop();
    worker.shutdownNow();
    if (store != null) {
      store.close();
    }
  }
}

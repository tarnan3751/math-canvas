package mathcanvas.app;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import mathcanvas.app.document.DocumentState;
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
import mathcanvas.app.update.UpdateCheck;
import mathcanvas.app.update.UpdateChecker;
import mathcanvas.canvas.sample.Samplers;

/**
 * What the window works on: the document loaded from the data directory, its evaluation worker, the
 * preferences, the store that saves both, and a notice for the status line. Confined to the FX
 * thread, like the document.
 *
 * <p>Loading reads {@code settings.json}, then {@code session.json}; a missing file gives the
 * defaults, an unreadable one is set aside by the store and named in the notice. The session's
 * canvas expressions are parsed here, on the FX thread at start-up (bounded by the parser's nesting
 * cap); its cells are evaluated and its objects recomputed on the worker, the objects undefined
 * until then. Every change of the document or the preferences that alters a file's content asks the
 * store to save it (debounced); {@link #close} flushes, on the FX thread, at exit only. When the
 * person turned the update check on, it runs once at the start, off the FX thread.
 */
final class Workspace implements AutoCloseable {

  /** The evaluation thread's stack: the parser's nesting cap needs more than 1 MB. */
  private static final long WORKER_STACK = 2L << 20;

  private final Document document;
  private final PreferenceProperties preferences;
  private final ReadOnlyObjectWrapper<Notice> notice = new ReadOnlyObjectWrapper<>();
  private final ReadOnlyObjectWrapper<UpdateCheck.Release> update = new ReadOnlyObjectWrapper<>();
  private Thread updateThread;
  private final ExecutorService worker;
  private final Store store;
  private SessionFile.Stored savedSession;
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
      savedSession = SessionFile.Stored.of(state);
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
   * @param updates the update check, run once on a thread of its own when the person turned it on,
   *     and never otherwise
   * @return the workspace; its pending cells start evaluating at once
   */
  static Workspace open(Path directory, Consumer<Runnable> ui, UpdateChecker updates) {
    // the start-up notices are worded once the settings say in which language
    List<Supplier<Notice>> notices = new ArrayList<>();
    Workspace[] self = new Workspace[1];
    Store store;
    try {
      store =
          Store.open(
              directory,
              Clock.systemUTC(),
              Store.SAVE_DELAY_MILLIS,
              e -> ui.accept(() -> self[0].saveFailed(e)));
    } catch (IOException e) {
      store = null;
      notices.add(
          () ->
              new Notice(
                  Messages.text("status.noDataDirectory", directory),
                  String.valueOf(e.getMessage())));
    }
    SettingsFile.Stored settings = SettingsFile.Stored.DEFAULT;
    SessionFile.Stored session = null;
    if (store != null) {
      settings =
          loaded(
              store.load(SettingsFile.NAME, SettingsFile::decode),
              SettingsFile.NAME,
              SettingsFile.Stored.DEFAULT,
              notices);
      session =
          loaded(
              store.load(SessionFile.NAME, SessionFile::decode), SessionFile.NAME, null, notices);
    }
    // the texts of the person's language from here on, before any is read: the notices, the window
    Messages.setLocale(settings.preferences().language().locale());
    DocumentState state =
        session == null
            ? DocumentState.of(List.of(), settings.settings())
            : session.state(settings.settings());
    self[0] =
        new Workspace(
            state, settings.preferences(), store, notices.stream().map(Supplier::get).toList(), ui);
    self[0].document.start();
    if (settings.preferences().checkForUpdates()) {
      self[0].checkForUpdates(updates, ui);
    }
    return self[0];
  }

  private static <T> T loaded(
      Store.Loaded<T> loaded, String name, T otherwise, List<Supplier<Notice>> out) {
    return switch (loaded) {
      case Store.Loaded.Read<T> r -> r.value();
      case Store.Loaded.Absent<T> _ -> otherwise;
      case Store.Loaded.SetAside<T> s -> {
        out.add(
            () ->
                new Notice(
                    Messages.text("status.setAside", name, s.aside().getFileName()), s.reason()));
        yield otherwise;
      }
      case Store.Loaded.Unreadable<T> u -> {
        out.add(() -> new Notice(Messages.text("status.unreadable", name), u.reason()));
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

  ReadOnlyObjectProperty<UpdateCheck.Release> updateProperty() {
    return update.getReadOnlyProperty();
  }

  /**
   * Runs the update check once, on a daemon thread of its own, and puts a newer release it finds in
   * {@link #updateProperty()} on the FX thread.
   */
  void checkForUpdates(UpdateChecker check, Consumer<Runnable> ui) {
    Thread t =
        new Thread(
            () -> check.check().ifPresent(r -> ui.accept(() -> update.set(r))),
            "mathcanvas-update");
    t.setDaemon(true);
    updateThread = t;
    t.start();
  }

  /** The update check's thread, once started; for the tests. */
  Thread updateThread() {
    return updateThread;
  }

  private Json settingsTree() {
    return SettingsFile.encode(
        new SettingsFile.Stored(document.state().settings(), preferences.get()));
  }

  private void save() {
    if (store == null) {
      return;
    }
    SessionFile.Stored session = SessionFile.Stored.of(document.state());
    if (!session.equals(savedSession)) {
      savedSession = session;
      // printing the canvas's expressions is engine work: done on the store's thread
      store.saveLater(SessionFile.NAME, () -> SessionFile.encode(session));
    }
    Json settings = settingsTree();
    if (!settings.equals(savedSettings)) {
      savedSettings = settings;
      store.saveLater(SettingsFile.NAME, settings);
    }
  }

  /**
   * A save failed: says so, and forgets what was last saved, so the next change saves both files
   * again rather than taking them for saved.
   */
  private void saveFailed(IOException e) {
    notice.set(new Notice(Messages.text("status.saveFailed"), String.valueOf(e.getMessage())));
    savedSession = null;
    savedSettings = null;
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

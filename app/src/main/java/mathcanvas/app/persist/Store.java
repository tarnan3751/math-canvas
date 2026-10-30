package mathcanvas.app.persist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;

/**
 * The app's files in its data directory. Reading a file that is missing gives nothing; a file that
 * cannot be read (not UTF-8, not JSON, not a shape or version this release reads) is renamed aside
 * as {@code <name>.unreadable-<UTC time>.json} and the app starts without it, so it is never
 * silently overwritten. If even the rename fails, that file is never written for the rest of the
 * run.
 *
 * <p>Saving is debounced: {@link #saveLater} keeps the newest tree per file, or what makes it, and
 * writes them on the store's own daemon thread once no save was asked for during the delay, so a
 * tree that is costly to make (the session's printed expressions) is made there, once; {@link
 * #flush} writes at once and waits. A file is written whole to {@code <name>.tmp} and moved over
 * the old one, as UTF-8 with LF line ends, so a crash leaves the old file or the new one, never
 * half of one.
 */
public final class Store implements AutoCloseable {

  /** The pause after the last change before the files are written: one second. */
  public static final long SAVE_DELAY_MILLIS = 1000;

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private static final long FLUSH_SECONDS = 5;

  /**
   * What loading a file gave.
   *
   * @param <T> what the file holds
   */
  public sealed interface Loaded<T> {

    /**
     * The file does not exist.
     *
     * @param <T> what the file holds
     */
    record Absent<T>() implements Loaded<T> {}

    /**
     * The file was read.
     *
     * @param <T> what the file holds
     * @param value what it holds
     */
    record Read<T>(T value) implements Loaded<T> {}

    /**
     * The file could not be read and was renamed aside.
     *
     * @param <T> what the file holds
     * @param aside where it is now
     * @param reason why it could not be read
     */
    record SetAside<T>(Path aside, String reason) implements Loaded<T> {}

    /**
     * The file could not be read and could not be renamed either, so it will not be written.
     *
     * @param <T> what the file holds
     * @param reason why it could not be read
     */
    record Unreadable<T>(String reason) implements Loaded<T> {}
  }

  /**
   * Turns a file's tree into what it holds.
   *
   * @param <T> what the file holds
   */
  @FunctionalInterface
  public interface Decoder<T> {

    /**
     * Decodes a tree.
     *
     * @param root the file's root value
     * @return what it holds
     * @throws FileFormatException if it is not a file this release reads
     */
    T decode(Json root) throws FileFormatException;
  }

  private final Path directory;
  private final Clock clock;
  private final long delayMillis;
  private final Consumer<IOException> onError;
  private final ScheduledExecutorService io;
  private final Map<String, Supplier<Json>> pending = new LinkedHashMap<>();
  private final Set<String> blocked = new HashSet<>();
  private ScheduledFuture<?> scheduled;

  /**
   * Opens the store, creating the directory if it does not exist.
   *
   * @param directory the data directory
   * @param clock the clock the names of set-aside files are stamped from
   * @param delayMillis the pause before a debounced save
   * @param onError told of a write that failed, or of a tree that could not be made, on the store's
   *     thread
   * @return the store
   * @throws IOException if the directory cannot be created
   */
  public static Store open(
      Path directory, Clock clock, long delayMillis, Consumer<IOException> onError)
      throws IOException {
    Files.createDirectories(directory);
    return new Store(directory, clock, delayMillis, onError);
  }

  private Store(Path directory, Clock clock, long delayMillis, Consumer<IOException> onError) {
    this.directory = directory;
    this.clock = Objects.requireNonNull(clock, "clock");
    this.delayMillis = delayMillis;
    this.onError = Objects.requireNonNull(onError, "onError");
    this.io =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "mathcanvas-io");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * The data directory.
   *
   * @return the directory
   */
  public Path directory() {
    return directory;
  }

  /**
   * Loads a file, setting it aside if it cannot be read.
   *
   * @param <T> what the file holds
   * @param name the file's name in the directory
   * @param decoder what turns its tree into a value
   * @return what loading gave
   */
  public <T> Loaded<T> load(String name, Decoder<T> decoder) {
    Path file = directory.resolve(name);
    String reason;
    try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return new Loaded.Read<>(decoder.decode(JsonIo.read(in)));
    } catch (NoSuchFileException e) {
      return new Loaded.Absent<>();
    } catch (IOException | FileFormatException e) {
      reason = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
    }
    try {
      return new Loaded.SetAside<>(setAside(file), reason);
    } catch (IOException e) {
      synchronized (this) {
        blocked.add(name);
      }
      return new Loaded.Unreadable<>(reason);
    }
  }

  private Path setAside(Path file) throws IOException {
    String name = file.getFileName().toString();
    String base = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    String stem = base + ".unreadable-" + STAMP.format(clock.instant());
    for (int k = 1; ; k++) {
      Path aside = file.resolveSibling(stem + (k == 1 ? "" : "-" + k) + ".json");
      try {
        Files.move(file, aside);
        return aside;
      } catch (FileAlreadyExistsException e) {
        // that second's name is taken: try the next suffix
      }
    }
  }

  /**
   * Saves a file after the pause, unless another save of any file comes first, which restarts it.
   *
   * @param name the file's name in the directory
   * @param value its tree
   */
  public void saveLater(String name, Json value) {
    saveLater(name, () -> value);
  }

  /**
   * Saves a file after the pause, its tree made on the store's thread when it is written, unless
   * another save of any file comes first, which restarts it.
   *
   * @param name the file's name in the directory
   * @param value makes its tree; called once, on the store's thread, and only if it is written
   */
  public synchronized void saveLater(String name, Supplier<Json> value) {
    if (blocked.contains(name)) {
      return;
    }
    pending.put(name, value);
    if (scheduled != null) {
      scheduled.cancel(false);
    }
    scheduled = io.schedule(this::writePending, delayMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Writes every pending save now and waits for it, at most five seconds.
   *
   * @throws InterruptedException if interrupted while waiting
   */
  public void flush() throws InterruptedException {
    synchronized (this) {
      if (scheduled != null) {
        scheduled.cancel(false);
        scheduled = null;
      }
    }
    try {
      io.submit(this::writePending).get(FLUSH_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      onError.accept(new IOException("saving did not finish", e));
    }
  }

  /**
   * Flushes, then stops the store's thread. Interrupted while flushing, it keeps the interrupt and
   * stops anyway.
   */
  @Override
  public void close() {
    try {
      flush();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      io.shutdown();
    }
  }

  private void writePending() {
    Map<String, Supplier<Json>> now;
    synchronized (this) {
      now = new LinkedHashMap<>(pending);
      pending.clear();
    }
    for (Map.Entry<String, Supplier<Json>> e : now.entrySet()) {
      try {
        write(e.getKey(), e.getValue().get());
      } catch (IOException x) {
        onError.accept(x);
      } catch (RuntimeException x) {
        // a tree that could not be made: reported like a failed write, and the next file written
        onError.accept(new IOException(e.getKey() + " could not be made: " + x, x));
      }
    }
  }

  private void write(String name, Json value) throws IOException {
    Path file = directory.resolve(name);
    Path tmp = directory.resolve(name + ".tmp");
    try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
      JsonIo.write(value, out);
    }
    try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
    try {
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}

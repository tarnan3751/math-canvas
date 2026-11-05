package mathcanvas.app.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import mathcanvas.app.json.Json;
import mathcanvas.app.json.JsonIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-27T10:15:30Z"), ZoneOffset.UTC);

  @TempDir Path dir;

  private final List<IOException> errors = new ArrayList<>();

  private Store open(long delayMillis) throws IOException {
    return Store.open(dir.resolve("data"), CLOCK, delayMillis, errors::add);
  }

  private static List<String> names(Path d) throws IOException {
    try (Stream<Path> files = Files.list(d)) {
      return files.map(p -> p.getFileName().toString()).sorted().toList();
    }
  }

  @Test
  void openingCreatesTheDirectoryAndAMissingFileIsAbsent() throws IOException {
    try (Store store = open(0)) {
      assertThat(store.directory()).isDirectory();
      assertThat(store.load(SessionFile.NAME, SessionFile::decode))
          .isInstanceOf(Store.Loaded.Absent.class);
    }
  }

  @Test
  void aSavedFileLoadsBack() throws Exception {
    Json tree = SettingsFile.encode(SettingsFile.Stored.DEFAULT);
    try (Store store = open(10_000)) {
      store.saveLater(SettingsFile.NAME, tree);
      assertThat(store.directory().resolve(SettingsFile.NAME)).doesNotExist();
      store.flush();

      assertThat(names(store.directory())).containsExactly(SettingsFile.NAME);
      assertThat(Files.readString(store.directory().resolve(SettingsFile.NAME)))
          .isEqualTo(JsonIo.text(tree));
      assertThat(store.load(SettingsFile.NAME, SettingsFile::decode))
          .isEqualTo(new Store.Loaded.Read<>(SettingsFile.Stored.DEFAULT));
    }
    assertThat(errors).isEmpty();
  }

  @Test
  void theNewestSaveWins() throws Exception {
    try (Store store = open(0)) {
      store.saveLater("a.json", Json.Num.of(1));
      store.saveLater("a.json", Json.Num.of(2));
      store.flush();

      assertThat(Files.readString(store.directory().resolve("a.json"))).isEqualTo("2\n");
    }
  }

  @Test
  void aTreeMadeLaterIsMadeOnceOnTheStoresThreadAndOnlyIfWritten() throws Exception {
    List<String> made = new CopyOnWriteArrayList<>();
    try (Store store = open(60_000)) {
      store.saveLater(
          "a.json",
          () -> {
            made.add("superseded");
            return Json.Num.of(1);
          });
      store.saveLater(
          "a.json",
          () -> {
            made.add(Thread.currentThread().getName());
            return Json.Num.of(2);
          });
      store.flush();

      assertThat(Files.readString(store.directory().resolve("a.json"))).isEqualTo("2\n");
    }
    assertThat(made).containsExactly("mathcanvas-io");
  }

  @Test
  void aTreeThatCannotBeMadeIsReportedAndTheOtherFilesAreStillWritten() throws Exception {
    try (Store store = open(60_000)) {
      store.saveLater(
          "a.json",
          () -> {
            throw new IllegalStateException("no tree");
          });
      store.saveLater("b.json", Json.Num.of(2));
      store.flush();

      assertThat(store.directory().resolve("a.json")).doesNotExist();
      assertThat(Files.readString(store.directory().resolve("b.json"))).isEqualTo("2\n");
    }
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst())
        .hasMessageContaining("a.json")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void aVersion1SessionFileLoadsAndIsSavedAsVersion4() throws Exception {
    Path data = Files.createDirectories(dir.resolve("data"));
    Files.writeString(data.resolve(SessionFile.NAME), Fixtures.text("session-v1.json"));

    try (Store store = open(0)) {
      SessionFile.Stored stored =
          ((Store.Loaded.Read<SessionFile.Stored>)
                  store.load(SessionFile.NAME, SessionFile::decode))
              .value();
      store.saveLater(SessionFile.NAME, () -> SessionFile.encode(stored));
      store.flush();
    }
    assertThat(Files.readString(data.resolve(SessionFile.NAME)))
        .isEqualTo(Fixtures.text("session-v6.json"));
  }

  @Test
  void aVersion1SettingsFileLoadsAndIsSavedAsVersion5() throws Exception {
    Path data = Files.createDirectories(dir.resolve("data"));
    Files.writeString(data.resolve(SettingsFile.NAME), Fixtures.text("settings-v1.json"));

    try (Store store = open(0)) {
      SettingsFile.Stored stored =
          ((Store.Loaded.Read<SettingsFile.Stored>)
                  store.load(SettingsFile.NAME, SettingsFile::decode))
              .value();
      store.saveLater(SettingsFile.NAME, SettingsFile.encode(stored));
      store.flush();
    }
    assertThat(Files.readString(data.resolve(SettingsFile.NAME)))
        .isEqualTo(Fixtures.text("settings-v5.json"));
  }

  @Test
  void anUnreadableFileIsSetAsideAndNeverOverwritten() throws Exception {
    Path data = Files.createDirectories(dir.resolve("data"));
    Files.writeString(data.resolve(SessionFile.NAME), "{ not json", StandardCharsets.UTF_8);
    Files.writeString(data.resolve(SettingsFile.NAME), "{\"version\": 7}");
    Files.writeString(data.resolve("session.unreadable-20261027T101530Z.json"), "older");

    try (Store store = open(0)) {
      Store.Loaded<?> session = store.load(SessionFile.NAME, SessionFile::decode);
      Store.Loaded<?> settings = store.load(SettingsFile.NAME, SettingsFile::decode);

      assertThat(session)
          .isEqualTo(
              new Store.Loaded.SetAside<>(
                  data.resolve("session.unreadable-20261027T101530Z-2.json"),
                  ((Store.Loaded.SetAside<?>) session).reason()));
      assertThat(((Store.Loaded.SetAside<?>) settings).aside())
          .isEqualTo(data.resolve("settings.unreadable-20261027T101530Z.json"));
      assertThat(((Store.Loaded.SetAside<?>) settings).reason()).contains("newer");
      assertThat(Files.readString(data.resolve("session.unreadable-20261027T101530Z-2.json")))
          .isEqualTo("{ not json");
      assertThat(names(data))
          .containsExactly(
              "session.unreadable-20261027T101530Z-2.json",
              "session.unreadable-20261027T101530Z.json",
              "settings.unreadable-20261027T101530Z.json");
    }
  }

  @Test
  void textThatIsNotUtf8IsUnreadable() throws Exception {
    Path data = Files.createDirectories(dir.resolve("data"));
    Files.write(data.resolve(SessionFile.NAME), new byte[] {'"', (byte) 0xff, '"'});

    try (Store store = open(0)) {
      assertThat(store.load(SessionFile.NAME, SessionFile::decode))
          .isInstanceOf(Store.Loaded.SetAside.class);
    }
  }

  @Test
  void aFileThatCannotEvenBeSetAsideIsNeverWritten() throws Exception {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    Path data = Files.createDirectories(dir.resolve("data"));
    Path session = data.resolve(SessionFile.NAME);
    Files.writeString(session, "garbage");
    Set<PosixFilePermission> writable = Files.getPosixFilePermissions(data);
    Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("r-xr-xr-x"));
    try (Store store = open(0)) {
      assertThat(store.load(SessionFile.NAME, SessionFile::decode))
          .isInstanceOf(Store.Loaded.Unreadable.class);
      Files.setPosixFilePermissions(data, writable);
      store.saveLater(SessionFile.NAME, Json.Num.of(1));
      store.flush();

      assertThat(Files.readString(session)).isEqualTo("garbage");
    } finally {
      Files.setPosixFilePermissions(data, writable);
    }
    assertThat(errors).isEmpty();
  }

  @Test
  void aWriteThatFailsIsReported() throws Exception {
    try (Store store = open(0)) {
      Files.createDirectories(store.directory().resolve("a.json.tmp"));
      store.saveLater("a.json", Json.Num.of(1));
      store.flush();
    }
    assertThat(errors).hasSize(1);
  }
}

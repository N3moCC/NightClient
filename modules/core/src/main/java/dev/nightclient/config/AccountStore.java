package dev.nightclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AccountStore implements AutoCloseable {
  private static final int MAX_BYTES = 128 * 1024;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private final Path directory;
  private final Path accountsFile;
  private final ScheduledExecutorService writer;
  private final Object ioLock = new Object();
  private ScheduledFuture<?> pending;
  private volatile String latest;
  private volatile String status = "saved";
  private boolean closed;

  private static final class Document {
    int schemaVersion = 1;
    List<SavedAccount> accounts = new ArrayList<SavedAccount>();
  }

  public AccountStore(Path configDirectory) {
    directory = configDirectory.resolve("nightclient");
    accountsFile = directory.resolve("accounts.json");
    writer = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "NightClient accounts");
      t.setDaemon(true);
      return t;
    });
  }

  public Path file() {
    return accountsFile;
  }

  public List<SavedAccount> load() {
    if (!Files.isRegularFile(accountsFile)) return new ArrayList<SavedAccount>();
    try {
      return decode(read(accountsFile));
    } catch (Exception error) {
      status = "recovered";
      try {
        Files.move(accountsFile,
            directory.resolve("accounts.broken-" + System.currentTimeMillis() + ".json"));
      } catch (IOException ignored) {
        status = "read-error";
      }
      return new ArrayList<SavedAccount>();
    }
  }

  public List<SavedAccount> decode(String json) {
    if (json == null || json.length() > MAX_BYTES
        || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IllegalArgumentException("Accounts file is too large");
    JsonElement tree;
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      reader.setLenient(false);
      tree = gson.getAdapter(JsonElement.class).read(reader);
      if (reader.peek() != JsonToken.END_DOCUMENT)
        throw new IllegalArgumentException("Trailing accounts content");
    } catch (IOException | RuntimeException ex) {
      throw new IllegalArgumentException("Invalid accounts JSON", ex);
    }
    if (!tree.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object");
    JsonObject object = tree.getAsJsonObject();
    if (object.has("schemaVersion")) {
      JsonElement version = object.get("schemaVersion");
      if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
          || version.getAsBigDecimal().compareTo(BigDecimal.ONE) != 0)
        throw new IllegalArgumentException("Unsupported accounts schema version");
    }
    Document doc;
    try {
      doc = gson.fromJson(object, Document.class);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid accounts payload", ex);
    }
    List<SavedAccount> result = new ArrayList<SavedAccount>();
    if (doc == null || doc.accounts == null) return result;
    Set<String> seenIds = new HashSet<String>();
    Set<String> seenNames = new HashSet<String>();
    for (SavedAccount entry : doc.accounts) {
      if (entry == null || result.size() >= SavedAccount.MAX_ACCOUNTS) continue;
      if (!entry.normalize()) continue;
      String lowerName = entry.name.toLowerCase(Locale.ROOT);
      if (!seenNames.add(lowerName)) continue;
      while (!seenIds.add(entry.id)) entry.id = SavedAccount.newId();
      result.add(entry);
    }
    return result;
  }

  public String encode(List<SavedAccount> accounts) {
    Document doc = new Document();
    doc.schemaVersion = 1;
    if (accounts != null) {
      Set<String> seenIds = new HashSet<String>();
      Set<String> seenNames = new HashSet<String>();
      for (SavedAccount entry : accounts) {
        if (entry == null || doc.accounts.size() >= SavedAccount.MAX_ACCOUNTS) continue;
        SavedAccount copy = entry.copy();
        if (!copy.normalize()) continue;
        String lowerName = copy.name.toLowerCase(Locale.ROOT);
        if (!seenNames.add(lowerName)) continue;
        while (!seenIds.add(copy.id)) copy.id = SavedAccount.newId();
        doc.accounts.add(copy);
      }
    }
    return gson.toJson(doc);
  }

  public synchronized void save(List<SavedAccount> accounts) {
    if (closed) return;
    latest = encode(accounts);
    status = "pending";
    if (pending != null) pending.cancel(false);
    final String snapshot = latest;
    pending = writer.schedule(() -> saveSnapshot(snapshot), 250, TimeUnit.MILLISECONDS);
  }

  public synchronized void saveNow(List<SavedAccount> accounts) {
    if (closed) return;
    latest = encode(accounts);
    if (pending != null) pending.cancel(false);
    saveSnapshot(latest);
  }

  private void saveSnapshot(String snapshot) {
    synchronized (ioLock) {
      if (!snapshot.equals(latest)) return;
      try {
        writeAtomic(accountsFile, snapshot);
        if (snapshot.equals(latest)) status = "saved";
      } catch (IOException error) {
        status = "write-error";
      }
    }
  }

  public String status() {
    return status;
  }

  private static String read(Path file) throws IOException {
    if (Files.size(file) > MAX_BYTES) throw new IOException("Accounts file is too large");
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  private static void writeAtomic(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Path temp = Files.createTempFile(file.getParent(), ".nightclient-accounts-", ".tmp");
    try {
      Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
      try {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (pending != null) pending.cancel(false);
    final String snapshot = latest;
    if (snapshot != null) writer.execute(() -> saveSnapshot(snapshot));
    writer.shutdown();
    try {
      if (!writer.awaitTermination(3, TimeUnit.SECONDS)) writer.shutdownNow();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      writer.shutdownNow();
    }
  }
}

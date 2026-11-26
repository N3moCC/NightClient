package dev.nightclient.renderer.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class DiskRegionStore {
  private static final int MAGIC = 0x54564D31;
  private static final int FORMAT_VERSION = 1;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path rootDir;

  public DiskRegionStore(Path configDir) {
    this.rootDir = configDir == null ? null : configDir.resolve("nightclient").resolve("map-cache");
  }

  public Path rootDir() {
    return rootDir;
  }

  public boolean isAvailable() {
    return rootDir != null;
  }

  public static String sanitizeDirSegment(String raw) {
    if (raw == null || raw.trim().isEmpty()) return "default";
    String s = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
        out.append(c);
      } else if (c == ':' || c == '/' || c == '\\' || Character.isWhitespace(c)) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '_') out.append('_');
      }
    }
    while (out.length() > 0 && (out.charAt(0) == '.' || out.charAt(0) == '_')) out.deleteCharAt(0);
    while (out.length() > 0
        && (out.charAt(out.length() - 1) == '.' || out.charAt(out.length() - 1) == '_')) {
      out.deleteCharAt(out.length() - 1);
    }
    String result = out.toString();
    if (result.isEmpty()) return "default";
    if (result.length() > 96) {
      int h = fnv1a(raw);
      result = result.substring(0, 87) + "_" + String.format(Locale.ROOT, "%08x", h);
    }
    return result;
  }

  private static int fnv1a(String s) {
    int h = 0x811c9dc5;
    for (int i = 0; i < s.length(); i++) {
      h ^= s.charAt(i);
      h *= 0x01000193;
    }
    return h;
  }

  public Path serverDirectory(String serverKey) {
    if (rootDir == null) return null;
    return rootDir.resolve(sanitizeDirSegment(serverKey));
  }

  public Path dimensionDirectory(String serverKey, String subWorldDir, String dimension) {
    if (rootDir == null) return null;
    return serverDirectory(serverKey)
        .resolve(sanitizeDirSegment(subWorldDir))
        .resolve(sanitizeDirSegment(dimension));
  }

  public Path regionFile(String serverKey, String subWorldDir, String dimension, int rx, int rz) {
    Path dir = dimensionDirectory(serverKey, subWorldDir, dimension);
    return dir == null ? null : dir.resolve("r." + rx + "." + rz + ".tvmap");
  }

  public void saveRegion(
      String serverKey, String subWorldDir, String dimension, MapRegionTile tile)
      throws IOException {
    if (rootDir == null || tile == null || !tile.hasAnyPopulatedChunk()) return;
    Path file = regionFile(serverKey, subWorldDir, dimension, tile.regionX, tile.regionZ);
    if (file == null) return;
    Files.createDirectories(file.getParent());

    long[] maskCopy = new long[16];
    int[] rgbCopy;
    short[] heightCopy;
    byte[] lightCopy;
    int populatedCount = 0;
    long snapshotRevision;
    synchronized (tile) {
      System.arraycopy(tile.populatedMask, 0, maskCopy, 0, 16);
      for (int i = 0; i < 16; i++) populatedCount += Long.bitCount(maskCopy[i]);
      if (populatedCount == 0) return;
      rgbCopy = new int[populatedCount * 256];
      heightCopy = new short[populatedCount * 256];
      lightCopy = new byte[populatedCount * 256];
      int outChunk = 0;
      for (int cz = 0; cz < MapRegionTile.CHUNKS_PER_SIDE; cz++) {
        for (int cx = 0; cx < MapRegionTile.CHUNKS_PER_SIDE; cx++) {
          int bit = (cz << 5) | cx;
          if ((maskCopy[bit >>> 6] & (1L << (bit & 63))) == 0L) continue;
          int bx0 = cx << 4;
          int bz0 = cz << 4;
          int dstBase = outChunk << 8;
          for (int lz = 0; lz < 16; lz++) {
            int srcRow = (bz0 + lz) * MapRegionTile.SIZE + bx0;
            int dstRow = dstBase + (lz << 4);
            System.arraycopy(tile.baseRgb, srcRow, rgbCopy, dstRow, 16);
            System.arraycopy(tile.heights, srcRow, heightCopy, dstRow, 16);
            System.arraycopy(tile.blockLight, srcRow, lightCopy, dstRow, 16);
          }
          outChunk++;
        }
      }
      snapshotRevision = tile.revision();
    }

    Path tmp = Files.createTempFile(
        file.getParent(), ".r." + tile.regionX + "." + tile.regionZ + "-", ".tvmap.tmp");
    Deflater deflater = new Deflater(Deflater.BEST_SPEED);
    try {
      try (DataOutputStream header = new DataOutputStream(
               new BufferedOutputStream(Files.newOutputStream(tmp), 8192))) {
        header.writeInt(MAGIC);
        header.writeInt(FORMAT_VERSION);
        header.writeInt(tile.regionX);
        header.writeInt(tile.regionZ);
        for (int i = 0; i < 16; i++) header.writeLong(maskCopy[i]);
        header.flush();
        try (DataOutputStream body = new DataOutputStream(
                 new BufferedOutputStream(new DeflaterOutputStream(header, deflater), 16384))) {
          int total = populatedCount * 256;
          for (int i = 0; i < total; i++) {
            int c = rgbCopy[i];
            body.writeByte((c >>> 16) & 0xFF);
            body.writeByte((c >>> 8) & 0xFF);
            body.writeByte(c & 0xFF);
            body.writeShort(heightCopy[i]);
            body.writeByte(lightCopy[i]);
          }
          body.flush();
        }
      }
      try {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException nonAtomic) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
      tile.clearDiskDirtyIfRevision(snapshotRevision);
    } finally {
      deflater.end();
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
      }
    }
  }

  public MapRegionTile loadRegion(String serverKey, String subWorldDir, String dimension, int rx,
      int rz, boolean terrainShading, boolean useBlockLight) {
    return loadRegion(serverKey, subWorldDir, dimension, rx, rz, terrainShading, useBlockLight,
        MapColorShading.RELIEF_CLASSIC, 3.0f);
  }

  public MapRegionTile loadRegion(String serverKey, String subWorldDir, String dimension, int rx,
      int rz, boolean terrainShading, boolean useBlockLight, int reliefStyle,
      float reliefStrength) {
    if (rootDir == null) return null;
    Path file = regionFile(serverKey, subWorldDir, dimension, rx, rz);
    if (file == null || !Files.isRegularFile(file)) return null;
    try (DataInputStream header = new DataInputStream(
             new BufferedInputStream(Files.newInputStream(file), 8192))) {
      int magic = header.readInt();
      int ver = header.readInt();
      int fileRx = header.readInt();
      int fileRz = header.readInt();
      if (magic != MAGIC || ver != FORMAT_VERSION || fileRx != rx || fileRz != rz) return null;
      MapRegionTile tile = new MapRegionTile(rx, rz);
      for (int i = 0; i < 16; i++) tile.populatedMask[i] = header.readLong();
      try (DataInputStream body = new DataInputStream(
               new BufferedInputStream(new InflaterInputStream(header), 16384))) {
        for (int cz = 0; cz < MapRegionTile.CHUNKS_PER_SIDE; cz++) {
          for (int cx = 0; cx < MapRegionTile.CHUNKS_PER_SIDE; cx++) {
            int bit = (cz << 5) | cx;
            if ((tile.populatedMask[bit >>> 6] & (1L << (bit & 63))) == 0L) continue;
            int bx0 = cx << 4;
            int bz0 = cz << 4;
            for (int lz = 0; lz < 16; lz++) {
              int dstRow = (bz0 + lz) * MapRegionTile.SIZE + bx0;
              for (int lx = 0; lx < 16; lx++) {
                int r = body.readUnsignedByte();
                int g = body.readUnsignedByte();
                int b = body.readUnsignedByte();
                short h = body.readShort();
                byte light = body.readByte();
                tile.baseRgb[dstRow + lx] = (r << 16) | (g << 8) | b;
                tile.heights[dstRow + lx] = h;
                tile.blockLight[dstRow + lx] = light;
              }
            }
          }
        }
      }
      tile.rebuildShading(terrainShading, useBlockLight, reliefStyle, reliefStrength);
      tile.clearDiskDirty();
      return tile;
    } catch (Exception ignored) {
      return null;
    }
  }

  public Set<Long> discoverStoredRegions(String serverKey, String subWorldDir, String dimension) {
    if (rootDir == null) return Collections.emptySet();
    Path dir = dimensionDirectory(serverKey, subWorldDir, dimension);
    if (dir == null || !Files.isDirectory(dir)) return Collections.emptySet();
    Set<Long> keys = new HashSet<Long>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "r.*.*.tvmap")) {
      for (Path p : stream) {
        String name = p.getFileName().toString();
        String middle = name.substring(2, name.length() - 6);
        int dot = middle.indexOf('.');
        if (dot <= 0 || dot >= middle.length() - 1) continue;
        try {
          int rx = Integer.parseInt(middle.substring(0, dot));
          int rz = Integer.parseInt(middle.substring(dot + 1));
          keys.add(MapRegionTile.regionKey(rx, rz));
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (IOException ignored) {
    }
    return keys;
  }

  public int deleteDimensionCache(String serverKey, String subWorldDir, String dimension) {
    if (rootDir == null) return 0;
    Path dir = dimensionDirectory(serverKey, subWorldDir, dimension);
    if (dir == null || !Files.isDirectory(dir)) return 0;
    int deleted = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path p : stream) {
        String name = p.getFileName().toString();
        if (Files.isRegularFile(p) && (name.endsWith(".tvmap") || name.endsWith(".tvmap.tmp"))) {
          if (Files.deleteIfExists(p)) deleted++;
        }
      }
    } catch (IOException ignored) {
    }
    return deleted;
  }

  public int deleteAllCache() {
    if (rootDir == null || !Files.isDirectory(rootDir)) return 0;
    return deleteRecursiveSafe(rootDir, false);
  }

  private static int deleteRecursiveSafe(Path path, boolean deleteSelf) {
    int deleted = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
      for (Path child : stream) {
        if (Files.isDirectory(child)) {
          deleted += deleteRecursiveSafe(child, true);
        } else if (Files.isRegularFile(child)) {
          String name = child.getFileName().toString();
          if (name.endsWith(".tvmap") || name.endsWith(".tvmap.tmp")
              || "worlds-index.json".equals(name) || name.endsWith("worlds-index.json.tmp")
              || (name.startsWith(".worlds-index-") && name.endsWith(".tmp"))) {
            if (Files.deleteIfExists(child)) deleted++;
          }
        }
      }
    } catch (IOException ignored) {
    }
    if (deleteSelf) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException ignored) {
      }
    }
    return deleted;
  }

  public static final class ServerWorldIndex {
    public int version = 1;
    public Map<String, SubWorldRecord> worlds = new LinkedHashMap<String, SubWorldRecord>();
  }

  public static final class SubWorldRecord {
    public String worldId = "";
    public String dirName = "";
    public String label = "";
    public String seedHex = "";
    public String spawnKey = "";
    public String dimension = "minecraft:overworld";
    public long lastSeenMs = 0L;
    public List<String> terrainSamples = new ArrayList<String>();
  }

  public ServerWorldIndex loadServerIndex(String serverKey) {
    if (rootDir == null) return new ServerWorldIndex();
    Path indexPath = serverDirectory(serverKey).resolve("worlds-index.json");
    if (!Files.isRegularFile(indexPath)) return new ServerWorldIndex();
    try {
      String json = new String(Files.readAllBytes(indexPath), StandardCharsets.UTF_8);
      ServerWorldIndex parsed = GSON.fromJson(json, ServerWorldIndex.class);
      if (parsed == null) return new ServerWorldIndex();
      if (parsed.worlds == null) parsed.worlds = new LinkedHashMap<String, SubWorldRecord>();
      return parsed;
    } catch (Exception ignored) {
      return new ServerWorldIndex();
    }
  }

  public void saveServerIndex(String serverKey, ServerWorldIndex index) {
    if (rootDir == null || index == null) return;
    Path dir = serverDirectory(serverKey);
    Path indexPath = dir.resolve("worlds-index.json");
    Path tmp = null;
    try {
      Files.createDirectories(dir);
      tmp = Files.createTempFile(dir, ".worlds-index-", ".tmp");
      byte[] bytes = GSON.toJson(index).getBytes(StandardCharsets.UTF_8);
      Files.write(tmp, bytes);
      try {
        Files.move(tmp, indexPath, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException nonAtomic) {
        Files.move(tmp, indexPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ignored) {
    } finally {
      if (tmp != null) {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignored2) {
        }
      }
    }
  }
}

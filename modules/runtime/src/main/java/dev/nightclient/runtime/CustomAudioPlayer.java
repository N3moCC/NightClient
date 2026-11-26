package dev.nightclient.runtime;

import dev.nightclient.runtime.mp3.Sound;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public final class CustomAudioPlayer {
  private static final long MAX_FILE_BYTES = 25L * 1024L * 1024L;
  private static final int MAX_DURATION_SECONDS = 15;
  private static final int OUTPUT_SAMPLE_RATE = 44100;
  private static final int MAX_CONCURRENT_VOICES = 4;
  private static final int CACHE_LIMIT = 8;

  public static final class DecodedClip {
    public final short[] samples;
    public final int sampleRate;
    public final int channels;
    public final int frameCount;

    DecodedClip(short[] samples, int sampleRate, int channels) {
      this.samples = samples;
      this.sampleRate = sampleRate;
      this.channels = channels;
      this.frameCount = channels > 0 ? (samples.length / channels) : 0;
    }
  }

  private static final class CacheEntry {
    final long lastModified;
    final long size;
    final DecodedClip clip;

    CacheEntry(long lastModified, long size, DecodedClip clip) {
      this.lastModified = lastModified;
      this.size = size;
      this.clip = clip;
    }
  }

  private final Map<String, CacheEntry> cache = new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
      return size() > CACHE_LIMIT;
    }
  };
  private final AtomicInteger activeVoices = new AtomicInteger();
  private final ExecutorService executor;

  public CustomAudioPlayer() {
    this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
      private final AtomicInteger n = new AtomicInteger(1);

      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "nightclient-audio-" + n.getAndIncrement());
        t.setDaemon(true);
        return t;
      }
    });
  }

  public static Path resolvePath(Path soundsDir, String configured) {
    if (configured == null) return null;
    String trimmed = configured.trim();
    if (trimmed.isEmpty()) return null;
    try {
      Path p = Paths.get(trimmed);
      if (p.isAbsolute()) return p;
      if (soundsDir != null) return soundsDir.resolve(trimmed).normalize();
      return p;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static String displayFileName(String configured) {
    if (configured == null || configured.trim().isEmpty()) return "";
    String s = configured.trim().replace('\\', '/');
    int slash = s.lastIndexOf('/');
    return slash >= 0 ? s.substring(slash + 1) : s;
  }

  public String importAudioFile(Path soundsDir, String sourcePath, String slotPrefix)
      throws IOException {
    if (sourcePath == null || sourcePath.trim().isEmpty()) {
      throw new IOException("Empty path");
    }
    Path src = Paths.get(sourcePath.trim());
    if (!Files.isRegularFile(src)) {
      throw new IOException("File not found: " + src);
    }
    long size = Files.size(src);
    if (size <= 0 || size > MAX_FILE_BYTES) {
      throw new IOException("File size out of range (max 25 MB)");
    }

    DecodedClip decoded = decodeFile(src);
    if (decoded == null || decoded.frameCount <= 0) {
      throw new IOException("Unsupported or empty audio file");
    }

    String rawName = src.getFileName() != null ? src.getFileName().toString() : "sound.mp3";
    String ext = ".mp3";
    int dot = rawName.lastIndexOf('.');
    String stem = dot > 0 ? rawName.substring(0, dot) : rawName;
    if (dot > 0 && dot < rawName.length() - 1) {
      String candidateExt = rawName.substring(dot).toLowerCase(Locale.ROOT);
      if (".mp3".equals(candidateExt) || ".wav".equals(candidateExt)
          || ".aiff".equals(candidateExt) || ".au".equals(candidateExt)) {
        ext = candidateExt;
      }
    }
    String cleanStem = stem.replaceAll("[^A-Za-z0-9._-]", "_");
    while (cleanStem.startsWith(".") || cleanStem.startsWith("_")) {
      cleanStem = cleanStem.substring(1);
    }
    if (cleanStem.isEmpty()) cleanStem = slotPrefix;
    if (cleanStem.length() > 40) cleanStem = cleanStem.substring(0, 40);

    Files.createDirectories(soundsDir);
    String targetName = cleanStem + ext;
    Path dst = soundsDir.resolve(targetName).normalize();
    if (!dst.startsWith(soundsDir.normalize())) {
      targetName = slotPrefix + ext;
      dst = soundsDir.resolve(targetName);
    }
    if (!src.toAbsolutePath().normalize().equals(dst.toAbsolutePath().normalize())) {
      Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }
    long mtime = Files.getLastModifiedTime(dst).toMillis();
    long dstSize = Files.size(dst);
    synchronized (cache) {
      cache.put(dst.toAbsolutePath().normalize().toString(), new CacheEntry(mtime, dstSize, decoded));
    }
    return targetName;
  }

  public DecodedClip loadClip(Path soundsDir, String configured) {
    Path path = resolvePath(soundsDir, configured);
    if (path == null || !Files.isRegularFile(path)) return null;
    try {
      String key = path.toAbsolutePath().normalize().toString();
      long mtime = Files.getLastModifiedTime(path).toMillis();
      long size = Files.size(path);
      if (size <= 0 || size > MAX_FILE_BYTES) return null;
      synchronized (cache) {
        CacheEntry existing = cache.get(key);
        if (existing != null && existing.lastModified == mtime && existing.size == size) {
          return existing.clip;
        }
      }
      DecodedClip decoded = decodeFile(path);
      if (decoded == null || decoded.frameCount <= 0) return null;
      synchronized (cache) {
        cache.put(key, new CacheEntry(mtime, size, decoded));
      }
      return decoded;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public boolean playAsync(final Path soundsDir, final String configured, final float volume,
      final float pitch) {
    if (configured == null || configured.trim().isEmpty() || volume <= 0.002f) return false;
    if (executor.isShutdown()) return false;
    if (activeVoices.get() >= MAX_CONCURRENT_VOICES) return false;
    final Path path = resolvePath(soundsDir, configured);
    if (path == null || !Files.isRegularFile(path)) return false;

    activeVoices.incrementAndGet();
    try {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          try {
            DecodedClip clip = loadClip(soundsDir, configured);
            if (clip != null && clip.frameCount > 0) {
              playClipBlocking(clip, volume, pitch);
            }
          } catch (Throwable ignored) {
          } finally {
            activeVoices.decrementAndGet();
          }
        }
      });
      return true;
    } catch (Throwable ignored) {
      activeVoices.decrementAndGet();
      return false;
    }
  }

  public void shutdown() {
    executor.shutdownNow();
    synchronized (cache) {
      cache.clear();
    }
  }

  public static DecodedClip decodeFile(Path path) throws IOException {
    byte[] raw = Files.readAllBytes(path);
    return decodeBytes(raw, path.getFileName() != null ? path.getFileName().toString() : "");
  }

  public static DecodedClip decodeBytes(byte[] raw, String fileNameHint) throws IOException {
    if (raw == null || raw.length < 4) {
      throw new IOException("Audio file is empty");
    }
    String lower = fileNameHint == null ? "" : fileNameHint.toLowerCase(Locale.ROOT);
    boolean isRiff = raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F';
    boolean isAiff = raw[0] == 'F' && raw[1] == 'O' && raw[2] == 'R' && raw[3] == 'M';
    if (isRiff || isAiff || lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".au")) {
      try {
        return decodeJavaSound(raw);
      } catch (Throwable wavErr) {
        if (lower.endsWith(".mp3")) {
          return decodeMp3(raw);
        }
        throw new IOException("Failed to decode WAV audio: " + wavErr.getMessage(), wavErr);
      }
    }
    return decodeMp3(raw);
  }

  private static DecodedClip decodeMp3(byte[] raw) throws IOException {
    int offset = skipId3v2Header(raw);
    if (offset >= raw.length) {
      throw new IOException("No MPEG audio frames after ID3 tag");
    }
    try (Sound sound = new Sound(new ByteArrayInputStream(raw, offset, raw.length - offset))) {
      int sampleRate = Math.max(8000, sound.getSamplingFrequency());
      int channels = sound.isStereo() ? 2 : 1;
      int maxBytes = sampleRate * channels * 2 * MAX_DURATION_SECONDS;
      ByteArrayOutputStream pcm = new ByteArrayOutputStream(Math.min(maxBytes, 262144));
      byte[] buf = new byte[8192];
      int total = 0;
      int n;
      while (total < maxBytes && (n = sound.read(buf, 0, Math.min(buf.length, maxBytes - total))) > 0) {
        pcm.write(buf, 0, n);
        total += n;
      }
      byte[] bytes = pcm.toByteArray();
      return fromLittleEndian16(bytes, sampleRate, channels);
    }
  }

  static int skipId3v2Header(byte[] raw) {
    int offset = 0;
    while (offset + 10 <= raw.length && raw[offset] == 'I' && raw[offset + 1] == 'D'
        && raw[offset + 2] == '3') {
      int flags = raw[offset + 5] & 0xFF;
      int tagSize = ((raw[offset + 6] & 0x7F) << 21)
          | ((raw[offset + 7] & 0x7F) << 14)
          | ((raw[offset + 8] & 0x7F) << 7)
          | (raw[offset + 9] & 0x7F);
      int next = offset + 10 + tagSize + ((flags & 0x10) != 0 ? 10 : 0);
      if (next <= offset || next >= raw.length) break;
      offset = next;
    }
    return offset;
  }

  private static DecodedClip decodeJavaSound(byte[] raw) throws Exception {
    try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(raw))) {
      AudioFormat base = rawStream.getFormat();
      int sampleRate = Math.max(8000, Math.round(base.getSampleRate() > 0 ? base.getSampleRate() : 44100f));
      int channels = Math.max(1, Math.min(2, base.getChannels()));
      AudioFormat target = new AudioFormat(
          AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
      try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(target, rawStream)) {
        int maxBytes = sampleRate * channels * 2 * MAX_DURATION_SECONDS;
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 131072));
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while (total < maxBytes && (n = pcmStream.read(buf, 0, Math.min(buf.length, maxBytes - total))) > 0) {
          out.write(buf, 0, n);
          total += n;
        }
        return fromLittleEndian16(out.toByteArray(), sampleRate, channels);
      }
    }
  }

  private static DecodedClip fromLittleEndian16(byte[] bytes, int sampleRate, int channels) {
    int frameBytes = channels * 2;
    int usableBytes = (bytes.length / frameBytes) * frameBytes;
    short[] samples = new short[usableBytes / 2];
    for (int i = 0; i < samples.length; i++) {
      int lo = bytes[i * 2] & 0xFF;
      int hi = bytes[i * 2 + 1];
      samples[i] = (short) ((hi << 8) | lo);
    }
    return new DecodedClip(samples, sampleRate, channels);
  }

  private static void playClipBlocking(DecodedClip clip, float volume, float pitch) {
    float gain = Math.max(0.0f, Math.min(2.0f, volume));
    float rateScale = Math.max(0.5f, Math.min(2.0f, pitch));
    AudioFormat outFmt = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED, OUTPUT_SAMPLE_RATE, 16, 2, 4, OUTPUT_SAMPLE_RATE, false);
    DataLine.Info info = new DataLine.Info(SourceDataLine.class, outFmt);
    SourceDataLine line = null;
    try {
      line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(outFmt, 16384);
      line.start();

      double step = ((double) clip.sampleRate / (double) OUTPUT_SAMPLE_RATE) * rateScale;
      double pos = 0.0;
      int maxFrame = clip.frameCount - 1;
      int ch = clip.channels;
      short[] src = clip.samples;
      byte[] chunk = new byte[4096];

      while (pos < maxFrame && !Thread.currentThread().isInterrupted()) {
        int outBytes = 0;
        while (outBytes < chunk.length && pos < maxFrame) {
          int idx = (int) pos;
          double frac = pos - idx;
          int next = Math.min(maxFrame, idx + 1);
          double l, r;
          if (ch == 1) {
            double s = src[idx] + (src[next] - src[idx]) * frac;
            l = s;
            r = s;
          } else {
            int i0 = idx * 2;
            int i1 = next * 2;
            l = src[i0] + (src[i1] - src[i0]) * frac;
            r = src[i0 + 1] + (src[i1 + 1] - src[i0 + 1]) * frac;
          }
          int sl = clampPcm(Math.round(l * gain));
          int sr = clampPcm(Math.round(r * gain));
          chunk[outBytes++] = (byte) (sl & 0xFF);
          chunk[outBytes++] = (byte) ((sl >> 8) & 0xFF);
          chunk[outBytes++] = (byte) (sr & 0xFF);
          chunk[outBytes++] = (byte) ((sr >> 8) & 0xFF);
          pos += step;
        }
        if (outBytes > 0) {
          line.write(chunk, 0, outBytes);
        }
      }
      line.drain();
    } catch (Throwable ignored) {
    } finally {
      if (line != null) {
        try {
          line.stop();
        } catch (Throwable ignored) {
        }
        try {
          line.close();
        } catch (Throwable ignored) {
        }
      }
    }
  }

  private static int clampPcm(long v) {
    if (v > 32767L) return 32767;
    if (v < -32768L) return -32768;
    return (int) v;
  }
}

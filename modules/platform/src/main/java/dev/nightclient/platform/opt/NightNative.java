package dev.nightclient.platform.opt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class NightNative {
  private NightNative() {}

  public static final int EXPECTED_ABI_VERSION = 1;

  public static final int ENT_FLAG_CULL_FACE = 1 << 0;
  public static final int ENT_FLAG_CUTOUT_01 = 1 << 1;
  public static final int ENT_FLAG_CUTOUT_05 = 1 << 2;
  public static final int ENT_FLAG_NO_CARDINAL_LIGHT = 1 << 3;
  public static final int ENT_FLAG_PER_FACE_LIGHT = 1 << 4;
  public static final int ENT_FLAG_EMISSIVE = 1 << 5;
  public static final int ENT_FLAG_NO_OVERLAY = 1 << 6;
  public static final int ENT_FLAG_BLEND = 1 << 7;

  public static final int TXT_FLAG_SEE_THROUGH = 1 << 0;
  public static final int TXT_FLAG_POLYGON_OFFSET = 1 << 1;
  public static final int TXT_FLAG_NO_TEX = 1 << 2;
  public static final int TXT_FLAG_INTENSITY = 1 << 3;

  public static final String[] GL_FUNCTION_NAMES = {
      "glGetError",
      "glGetIntegerv",
      "glGetIntegeri_v",
      "glGetInteger64i_v",
      "glGetBooleanv",
      "glIsEnabled",
      "glEnable",
      "glDisable",
      "glDepthMask",
      "glDepthFunc",
      "glColorMask",
      "glBlendFuncSeparate",
      "glPolygonOffset",
      "glViewport",
      "glBindFramebuffer",
      "glGenVertexArrays",
      "glDeleteVertexArrays",
      "glBindVertexArray",
      "glGenBuffers",
      "glDeleteBuffers",
      "glBindBuffer",
      "glBindBufferBase",
      "glBindBufferRange",
      "glBufferData",
      "glBufferSubData",
      "glCopyBufferSubData",
      "glMapBufferRange",
      "glUnmapBuffer",
      "glEnableVertexAttribArray",
      "glVertexAttribPointer",
      "glVertexAttribIPointer",
      "glVertexAttribDivisor",
      "glCreateShader",
      "glShaderSource",
      "glCompileShader",
      "glGetShaderiv",
      "glGetShaderInfoLog",
      "glDeleteShader",
      "glCreateProgram",
      "glAttachShader",
      "glLinkProgram",
      "glGetProgramiv",
      "glGetProgramInfoLog",
      "glDeleteProgram",
      "glUseProgram",
      "glGetUniformLocation",
      "glGetUniformBlockIndex",
      "glUniformBlockBinding",
      "glUniform1i",
      "glUniform1iv",
      "glUniformMatrix4fv",
      "glActiveTexture",
      "glBindTexture",
      "glTexSubImage2D",
      "glPixelStorei",
      "glGenSamplers",
      "glDeleteSamplers",
      "glBindSampler",
      "glSamplerParameteri",
      "glFenceSync",
      "glClientWaitSync",
      "glDeleteSync",
      "glDrawElementsInstancedBaseVertex",
      "glDrawElementsInstancedBaseVertexBaseInstance",
      "glMultiDrawElementsIndirect",
      "glBufferStorage",
  };

  private static volatile boolean loadAttempted;
  private static volatile boolean loaded;
  private static volatile String loadStatus = "uninitialized";
  private static volatile int glCaps;
  private static volatile long meshGeneration = 1L;

  public static long meshGeneration() {
    return meshGeneration;
  }

  public static boolean isLoaded() {
    if (!loadAttempted) {
      ensureLoaded();
    }
    return loaded;
  }

  public static boolean isGlReady() {
    return loaded && (glCaps & 1) != 0;
  }

  public static boolean isPboReady() {
    return loaded && (glCaps & 2) != 0;
  }

  public static boolean isGpuInstancerReady() {
    return loaded && (glCaps & 4) != 0;
  }

  public static String statusLabel() {
    if (!loadAttempted) ensureLoaded();
    return loadStatus;
  }

  public static synchronized boolean ensureLoaded() {
    if (loadAttempted) return loaded;
    loadAttempted = true;
    try {
      String enabledProp = System.getProperty("nightclient.native.enabled");
      if ("false".equalsIgnoreCase(enabledProp) || "0".equals(enabledProp)) {
        loadStatus = "disabled-by-property";
        return false;
      }

      String platformDir = detectPlatformDir();
      String libFileName = detectLibFileName();
      if (platformDir == null || libFileName == null) {
        loadStatus = "unsupported-platform:" + System.getProperty("os.name") + "/" + System.getProperty("os.arch");
        return false;
      }

      String explicit = System.getProperty("nightclient.native.path");
      if (explicit != null && !explicit.trim().isEmpty()) {
        File f = new File(explicit.trim());
        if (f.isDirectory()) {
          f = new File(f, libFileName);
        }
        if (f.isFile() && tryLoadFile(f.getAbsolutePath(), "explicit:" + f.getName())) {
          return true;
        }
      }

      Path devRelease = Paths.get("native", "nightclient-native", "target", "release", libFileName);
      if (Files.isRegularFile(devRelease)
          && tryLoadFile(devRelease.toAbsolutePath().toString(), "dev-release")) {
        return true;
      }
      Path walk = Paths.get("").toAbsolutePath();
      for (int i = 0; i < 6 && walk != null; i++) {
        Path cand = walk.resolve("native").resolve("nightclient-native").resolve("target")
            .resolve("release").resolve(libFileName);
        if (Files.isRegularFile(cand) && tryLoadFile(cand.toString(), "repo-release")) {
          return true;
        }
        walk = walk.getParent();
      }

      Path cfgPlatform = Paths.get("config", "nightclient", "natives", platformDir, libFileName);
      if (Files.isRegularFile(cfgPlatform)
          && tryLoadFile(cfgPlatform.toAbsolutePath().toString(), "config:" + platformDir)) {
        return true;
      }
      Path cfgFlat = Paths.get("config", "nightclient", "natives", libFileName);
      if (Files.isRegularFile(cfgFlat)
          && tryLoadFile(cfgFlat.toAbsolutePath().toString(), "config")) {
        return true;
      }

      String resourcePath = "/assets/nightclient/natives/" + platformDir + "/" + libFileName;
      try (InputStream in = NightNative.class.getResourceAsStream(resourcePath)) {
        if (in != null) {
          java.io.ByteArrayOutputStream rawOut = new java.io.ByteArrayOutputStream();
          byte[] buf = new byte[16384];
          int r;
          while ((r = in.read(buf)) != -1) {
            rawOut.write(buf, 0, r);
          }
          byte[] libBytes = rawOut.toByteArray();
          String hashPrefix = shortSha256(libBytes);
          Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "nightclient-native", platformDir);
          Files.createDirectories(cacheDir);
          Path cachedFile = cacheDir.resolve("nc-" + hashPrefix + "-" + libFileName);
          if (!Files.isRegularFile(cachedFile) || Files.size(cachedFile) != libBytes.length) {
            Path tmp = Files.createTempFile(cacheDir, "nc-stage-", ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp.toFile())) {
              out.write(libBytes);
            }
            tmp.toFile().setExecutable(true, false);
            try {
              Files.move(tmp, cachedFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                  java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable moveErr) {
              try {
                Files.move(tmp, cachedFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              } catch (Throwable secondErr) {
                Files.deleteIfExists(tmp);
              }
            }
          }
          if (Files.isRegularFile(cachedFile)
              && tryLoadFile(cachedFile.toAbsolutePath().toString(), "bundled:" + platformDir)) {
            return true;
          }
        }
      }

      loadStatus = "not-found:" + platformDir;
      return false;
    } catch (Throwable t) {
      loadStatus = "load-error:" + t.getClass().getSimpleName();
      loaded = false;
      return false;
    }
  }

  private static String shortSha256(byte[] data) {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
      StringBuilder sb = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        sb.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 0xFF));
      }
      return sb.toString();
    } catch (Throwable ignored) {
      return Integer.toHexString(java.util.Arrays.hashCode(data));
    }
  }

  private static boolean tryLoadFile(String absolutePath, String tag) {
    try {
      System.load(absolutePath);
      int abi = nAbiVersion();
      if (abi != EXPECTED_ABI_VERSION) {
        loadStatus = "abi-mismatch:" + abi;
        return false;
      }
      loaded = true;
      loadStatus = "loaded(" + tag + ",abi=" + abi + ")";
      System.out.println("[NightClient] nightclient-native loaded: " + loadStatus);
      return true;
    } catch (Throwable t) {
      loadStatus = "dlopen-failed:" + t.getMessage();
      return false;
    }
  }

  private static String detectPlatformDir() {
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
    String osTag;
    if (os.contains("linux")) osTag = "linux";
    else if (os.contains("win")) osTag = "windows";
    else if (os.contains("mac") || os.contains("darwin")) osTag = "macos";
    else return null;

    String archTag;
    if ("amd64".equals(arch) || "x86_64".equals(arch)) archTag = "x64";
    else if ("aarch64".equals(arch) || "arm64".equals(arch)) archTag = "arm64";
    else return null;

    return osTag + "-" + archTag;
  }

  private static String detectLibFileName() {
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("linux")) return "libnightclient_native.so";
    if (os.contains("win")) return "nightclient_native.dll";
    if (os.contains("mac") || os.contains("darwin")) return "libnightclient_native.dylib";
    return null;
  }

  public static int initGl(long fnPtrsAddr, int fnCount) {
    if (!isLoaded()) return 0;
    try {
      int caps = nInitGl(fnPtrsAddr, fnCount);
      glCaps = caps;
      System.out.println(
          "[NightClient] nightclient-native GL caps="
              + caps
              + " (pbo="
              + ((caps & 2) != 0)
              + ", gpuInstancer="
              + ((caps & 4) != 0)
              + ")");
      return caps;
    } catch (Throwable t) {
      glCaps = 0;
      return 0;
    }
  }

  public static void resetGpuMeshes() {
    meshGeneration++;
    if (!isGpuInstancerReady()) return;
    try {
      nResetGpuMeshes();
    } catch (Throwable ignored) {
    }
  }

  public static void shutdownGl() {
    if (!isGlReady()) return;
    glCaps = 0;
    try {
      nShutdownGl();
    } catch (Throwable ignored) {
    }
  }

  public static int registerEntityMesh(long vertsAddr, int vertCount) {
    if (!isGpuInstancerReady()) return 0;
    return nRegisterEntityMesh(vertsAddr, vertCount);
  }

  public static int registerTextMesh(long vertsAddr, int vertCount) {
    if (!isGpuInstancerReady()) return 0;
    return nRegisterTextMesh(vertsAddr, vertCount);
  }

  public static void flushFrame(
      long paramsAddr, long entitiesAddr, int entityCount, long textsAddr, int textCount) {
    if (!isGpuInstancerReady()) return;
    nFlushFrame(paramsAddr, entitiesAddr, entityCount, textsAddr, textCount);
  }

  public static boolean uploadTexture2dPbo(
      int glTexId,
      int mipLevel,
      int dstX,
      int dstY,
      int width,
      int height,
      long srcPtr,
      int srcStridePx,
      int srcX,
      int srcY,
      int components) {
    if (!isPboReady()) return false;
    return nUploadTexture2dPbo(
        glTexId, mipLevel, dstX, dstY, width, height, srcPtr, srcStridePx, srcX, srcY, components);
  }

  public static int zlibInflate(long srcAddr, int srcLen, long dstAddr, int dstLen) {
    if (!isLoaded()) return -1;
    return nZlibInflate(srcAddr, srcLen, dstAddr, dstLen);
  }

  public static int zlibDeflate(long srcAddr, int srcLen, long dstAddr, int dstCap, int level) {
    if (!isLoaded()) return -1;
    return nZlibDeflate(srcAddr, srcLen, dstAddr, dstCap, level);
  }

  public static int zlibCompressBound(int srcLen) {
    if (!isLoaded()) return srcLen + (srcLen >> 12) + 64;
    return nZlibCompressBound(srcLen);
  }

  public static int frustumCullSpheres(
      long planesAddr, long spheresAddr, int count, long outVisAddr) {
    if (!isLoaded()) return -1;
    return nFrustumCullSpheres(planesAddr, spheresAddr, count, outVisAddr);
  }

  public static void readStats(long outLongsAddr, int len) {
    if (!isLoaded()) return;
    nReadStats(outLongsAddr, len);
  }

  public static void resetStats() {
    if (!isLoaded()) return;
    nResetStats();
  }

  public static long directBufferAddress(ByteBuffer buf) {
    if (buf == null || !buf.isDirect()) return 0L;
    return DirectAddrHolder.address(buf);
  }

  private static final class DirectAddrHolder {
    private static final java.lang.reflect.Field ADDRESS_FIELD;

    static {
      java.lang.reflect.Field f = null;
      try {
        f = java.nio.Buffer.class.getDeclaredField("address");
        f.setAccessible(true);
      } catch (Throwable ignored) {
      }
      ADDRESS_FIELD = f;
    }

    static long address(ByteBuffer buf) {
      if (ADDRESS_FIELD == null) return 0L;
      try {
        return ADDRESS_FIELD.getLong(buf);
      } catch (Throwable ignored) {
        return 0L;
      }
    }
  }

  public static long[] readStatsSnapshot() {
    long[] out = new long[10];
    if (!isLoaded()) return out;
    ByteBuffer bb = ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
    long addr = directBufferAddress(bb);
    if (addr == 0L) return out;
    nReadStats(addr, 10);
    for (int i = 0; i < 10; i++) {
      out[i] = bb.getLong(i * 8);
    }
    return out;
  }

  private static native int nAbiVersion();

  private static native int nInitGl(long fnPtrsAddr, int fnCount);

  private static native void nResetGpuMeshes();

  private static native void nShutdownGl();

  private static native int nRegisterEntityMesh(long vertsAddr, int vertCount);

  private static native int nRegisterTextMesh(long vertsAddr, int vertCount);

  private static native void nFlushFrame(
      long paramsAddr, long entitiesAddr, int entityCount, long textsAddr, int textCount);

  private static native boolean nUploadTexture2dPbo(
      int glTexId,
      int mipLevel,
      int dstX,
      int dstY,
      int width,
      int height,
      long srcPtr,
      int srcStridePx,
      int srcX,
      int srcY,
      int components);

  private static native int nZlibInflate(long srcAddr, int srcLen, long dstAddr, int dstLen);

  private static native int nZlibDeflate(
      long srcAddr, int srcLen, long dstAddr, int dstCap, int level);

  private static native int nZlibCompressBound(int srcLen);

  private static native int nFrustumCullSpheres(
      long planesAddr, long spheresAddr, int count, long outVisAddr);

  private static native void nReadStats(long outLongsAddr, int len);

  private static native void nResetStats();
}

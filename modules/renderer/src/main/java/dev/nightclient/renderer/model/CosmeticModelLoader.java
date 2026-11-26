package dev.nightclient.renderer.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class CosmeticModelLoader {
  private static final int GLB_MAGIC = 0x46546C67;
  private static final int CHUNK_JSON = 0x4E4F534A;
  private static final int CHUNK_BIN = 0x004E4942;
  private static final int MAX_VERTICES = 1_500_000;
  private static final int MAX_INDICES = 4_500_000;
  private static final int MAX_TEXTURE_DIM = 2048;
  private static final int MAX_PREVIEW_TRIS = 320_000;

  private CosmeticModelLoader() {}

  public static CosmeticModelMesh loadResource(String resourcePath, boolean armorModel)
      throws IOException {
    InputStream in = CosmeticModelLoader.class.getResourceAsStream(resourcePath);
    if (in == null) throw new IOException("Missing 3D model resource: " + resourcePath);
    byte[] raw = readAllBytes(in);
    String lower = resourcePath.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".obj")) {
      return loadObj(raw, Collections.<String, byte[]>emptyMap(), armorModel);
    }
    return loadGlb(raw, armorModel);
  }

  public static CosmeticModelMesh loadFile(Path file, boolean armorModel) throws IOException {
    if (file == null || !Files.isRegularFile(file)) {
      throw new IOException("Model file not found: " + file);
    }
    long size = Files.size(file);
    if (size <= 0 || size > 128L * 1024L * 1024L) {
      throw new IOException("Model file size out of supported range: " + size);
    }
    byte[] raw = Files.readAllBytes(file);
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".glb") || isGlbMagic(raw)) {
      return loadGlb(raw, armorModel);
    }
    if (name.endsWith(".obj")) {
      return loadObjFromFile(file, raw, armorModel);
    }
    throw new IOException("Unsupported 3D model format (expected .glb or .obj): " + name);
  }

  private static boolean isGlbMagic(byte[] raw) {
    if (raw == null || raw.length < 12) return false;
    int magic = ByteBuffer.wrap(raw, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    return magic == GLB_MAGIC;
  }

  private static final class MaterialSpec {
    float r = 0.85f, g = 0.85f, b = 0.88f, a = 1.0f;
    float metallic = 0.25f;
    float roughness = 0.55f;
    float er = 0f, eg = 0f, eb = 0f;
    float emissiveStrength = 0f;
    float tintMask = 0f;
    int blendMode = CosmeticModelMesh.BLEND_OPAQUE;
    boolean doubleSided = false;
    int flags = 0;
    int textureIndex = -1;
    int mrTextureIndex = -1;
    int normalTextureIndex = -1;
    float normalScale = 1.0f;
    int occlusionTextureIndex = -1;
    boolean hasExplicitMetallic = false;
    boolean hasExplicitRoughness = false;
  }

  private static final class BucketKey {
    final int boneId;
    final int blendMode;
    final boolean doubleSided;

    BucketKey(int boneId, int blendMode, boolean doubleSided) {
      this.boneId = boneId;
      this.blendMode = blendMode;
      this.doubleSided = doubleSided;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BucketKey)) return false;
      BucketKey other = (BucketKey) o;
      return boneId == other.boneId && blendMode == other.blendMode
          && doubleSided == other.doubleSided;
    }

    @Override
    public int hashCode() {
      return (boneId * 31 + blendMode) * 31 + (doubleSided ? 1 : 0);
    }
  }

  private static final class RawMeshPart {
    final int boneId;
    final MaterialSpec material;
    final float[] pos;
    final float[] nrm;
    final float[] tan;
    final float[] uv;
    final int[] col;
    final int[] idx;

    RawMeshPart(int boneId, MaterialSpec material, float[] pos, float[] nrm, float[] tan,
        float[] uv, int[] col, int[] idx) {
      this.boneId = boneId;
      this.material = material;
      this.pos = pos;
      this.nrm = nrm;
      this.tan = tan;
      this.uv = uv;
      this.col = col;
      this.idx = idx;
    }
  }

  private static final class DecodedImg {
    final int[] pixels;
    final int width;
    final int height;
    int atlasX, atlasY;
    int atlasW, atlasH;

    DecodedImg(int[] pixels, int width, int height) {
      this.pixels = pixels;
      this.width = width;
      this.height = height;
      this.atlasW = width;
      this.atlasH = height;
    }
  }

  public static CosmeticModelMesh loadGlb(byte[] raw, boolean armorModel) throws IOException {
    if (raw == null || raw.length < 28) throw new IOException("GLB file too small");
    ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
    int magic = header.getInt(0);
    int version = header.getInt(4);
    int length = header.getInt(8);
    if (magic != GLB_MAGIC || version != 2) {
      throw new IOException("Invalid GLB header (version=" + version + ")");
    }
    if (length > raw.length) length = raw.length;

    String jsonText = null;
    byte[] binChunk = new byte[0];
    int cursor = 12;
    while (cursor + 8 <= length) {
      int chunkLen = header.getInt(cursor);
      int chunkType = header.getInt(cursor + 4);
      int dataStart = cursor + 8;
      if (chunkLen < 0 || chunkLen > 128 * 1024 * 1024 || (long) dataStart + chunkLen > length)
        break;
      if (chunkType == CHUNK_JSON) {
        jsonText = new String(raw, dataStart, chunkLen, StandardCharsets.UTF_8);
      } else if (chunkType == CHUNK_BIN) {
        binChunk = Arrays.copyOfRange(raw, dataStart, dataStart + chunkLen);
      }
      long nextCursor = (long) dataStart + (((long) chunkLen + 3L) & ~3L);
      if (nextCursor > length || nextCursor <= cursor) break;
      cursor = (int) nextCursor;
    }
    if (jsonText == null) throw new IOException("GLB missing JSON chunk");

    JsonObject root;
    try {
      root = new JsonParser().parse(jsonText).getAsJsonObject();
    } catch (Exception e) {
      throw new IOException("Corrupt GLB JSON chunk", e);
    }

    boolean calibrated = false;
    if (root.has("asset") && root.get("asset").isJsonObject()) {
      JsonObject asset = root.getAsJsonObject("asset");
      if (asset.has("extras") && asset.get("extras").isJsonObject()) {
        JsonObject ex = asset.getAsJsonObject("extras");
        if (ex.has("nightclient_calibrated")) {
          calibrated = ex.get("nightclient_calibrated").getAsBoolean();
        }
      }
    }

    JsonArray bufferViews = root.has("bufferViews") ? root.getAsJsonArray("bufferViews") : new JsonArray();
    JsonArray accessors = root.has("accessors") ? root.getAsJsonArray("accessors") : new JsonArray();
    JsonArray meshes = root.has("meshes") ? root.getAsJsonArray("meshes") : new JsonArray();
    JsonArray nodes = root.has("nodes") ? root.getAsJsonArray("nodes") : new JsonArray();

    List<DecodedImg> images = decodeGlbImages(root, bufferViews, binChunk);
    int[] textureToImage = parseGlbTextures(root, images.size());
    List<MaterialSpec> materials = parseGlbMaterials(root, textureToImage);

    List<RawMeshPart> parts = new ArrayList<RawMeshPart>();
    int[] counters = new int[2];
    float[] identity = mat4Identity();

    List<Integer> rootNodes = new ArrayList<Integer>();
    if (root.has("scenes") && root.getAsJsonArray("scenes").size() > 0) {
      int sceneIdx = root.has("scene") ? root.get("scene").getAsInt() : 0;
      JsonArray scenes = root.getAsJsonArray("scenes");
      if (sceneIdx >= 0 && sceneIdx < scenes.size()) {
        JsonObject scene = scenes.get(sceneIdx).getAsJsonObject();
        if (scene.has("nodes")) {
          for (JsonElement el : scene.getAsJsonArray("nodes")) {
            rootNodes.add(el.getAsInt());
          }
        }
      }
    }
    if (rootNodes.isEmpty()) {
      for (int i = 0; i < nodes.size(); i++) rootNodes.add(i);
    }

    for (int nodeIdx : rootNodes) {
      traverseGlbNode(nodeIdx, nodes, meshes, accessors, bufferViews, binChunk, materials,
          identity, -1, armorModel, parts, counters, 0);
    }

    if (parts.isEmpty()) throw new IOException("GLB contains no renderable triangle geometry");
    return assembleMesh(parts, images, armorModel, calibrated);
  }

  private static List<DecodedImg> decodeGlbImages(JsonObject root, JsonArray bufferViews,
      byte[] binChunk) {
    List<DecodedImg> out = new ArrayList<DecodedImg>();
    if (!root.has("images")) return out;
    JsonArray imgs = root.getAsJsonArray("images");
    for (int i = 0; i < imgs.size(); i++) {
      DecodedImg decoded = null;
      try {
        JsonObject img = imgs.get(i).getAsJsonObject();
        byte[] imgBytes = null;
        if (img.has("bufferView")) {
          int bvIdx = img.get("bufferView").getAsInt();
          if (bvIdx >= 0 && bvIdx < bufferViews.size()) {
            JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
            int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
            int len = bv.get("byteLength").getAsInt();
            if (off >= 0 && len > 0 && off + len <= binChunk.length) {
              imgBytes = Arrays.copyOfRange(binChunk, off, off + len);
            }
          }
        } else if (img.has("uri")) {
          String uri = img.get("uri").getAsString();
          int comma = uri.indexOf(',');
          if (uri.startsWith("data:") && comma > 0) {
            imgBytes = Base64.getDecoder().decode(uri.substring(comma + 1));
          }
        }
        if (imgBytes != null) decoded = decodeImageBytes(imgBytes);
      } catch (Throwable ignored) {
      }
      out.add(decoded);
    }
    return out;
  }

  private static int[] parseGlbTextures(JsonObject root, int imageCount) {
    if (!root.has("textures")) return new int[0];
    JsonArray texs = root.getAsJsonArray("textures");
    int[] map = new int[texs.size()];
    Arrays.fill(map, -1);
    for (int i = 0; i < texs.size(); i++) {
      JsonObject t = texs.get(i).getAsJsonObject();
      if (t.has("source")) {
        int src = t.get("source").getAsInt();
        if (src >= 0 && src < imageCount) map[i] = src;
      }
    }
    return map;
  }

  private static List<MaterialSpec> parseGlbMaterials(JsonObject root, int[] textureToImage) {
    List<MaterialSpec> out = new ArrayList<MaterialSpec>();
    if (!root.has("materials")) return out;
    JsonArray mats = root.getAsJsonArray("materials");
    for (int i = 0; i < mats.size(); i++) {
      JsonObject m = mats.get(i).getAsJsonObject();
      MaterialSpec spec = new MaterialSpec();
      if (m.has("pbrMetallicRoughness") && m.get("pbrMetallicRoughness").isJsonObject()) {
        JsonObject pbr = m.getAsJsonObject("pbrMetallicRoughness");
        if (pbr.has("baseColorFactor")) {
          JsonArray f = pbr.getAsJsonArray("baseColorFactor");
          if (f.size() >= 3) {
            spec.r = clamp01(f.get(0).getAsFloat());
            spec.g = clamp01(f.get(1).getAsFloat());
            spec.b = clamp01(f.get(2).getAsFloat());
            spec.a = f.size() >= 4 ? clamp01(f.get(3).getAsFloat()) : 1f;
          }
        } else if (pbr.has("baseColorTexture")) {
          spec.r = 1f;
          spec.g = 1f;
          spec.b = 1f;
          spec.a = 1f;
        }
        if (pbr.has("metallicFactor")) {
          spec.metallic = clamp01(pbr.get("metallicFactor").getAsFloat());
          spec.hasExplicitMetallic = true;
        }
        if (pbr.has("roughnessFactor")) {
          spec.roughness = clamp01(pbr.get("roughnessFactor").getAsFloat());
          spec.hasExplicitRoughness = true;
        }
        if (pbr.has("baseColorTexture") && pbr.get("baseColorTexture").isJsonObject()) {
          int texIdx = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
          if (texIdx >= 0 && texIdx < textureToImage.length) {
            spec.textureIndex = textureToImage[texIdx];
          }
        }
        if (pbr.has("metallicRoughnessTexture") && pbr.get("metallicRoughnessTexture").isJsonObject()) {
          int texIdx = pbr.getAsJsonObject("metallicRoughnessTexture").get("index").getAsInt();
          if (texIdx >= 0 && texIdx < textureToImage.length) {
            spec.mrTextureIndex = textureToImage[texIdx];
            if (!spec.hasExplicitMetallic) spec.metallic = 1.0f;
            if (!spec.hasExplicitRoughness) spec.roughness = 1.0f;
          }
        }
      }
      if (m.has("normalTexture") && m.get("normalTexture").isJsonObject()) {
        JsonObject nt = m.getAsJsonObject("normalTexture");
        int texIdx = nt.get("index").getAsInt();
        if (texIdx >= 0 && texIdx < textureToImage.length) {
          spec.normalTextureIndex = textureToImage[texIdx];
        }
        if (nt.has("scale")) {
          spec.normalScale = Math.max(0f, Math.min(2f, nt.get("scale").getAsFloat()));
        }
      }
      if (m.has("occlusionTexture") && m.get("occlusionTexture").isJsonObject()) {
        int texIdx = m.getAsJsonObject("occlusionTexture").get("index").getAsInt();
        if (texIdx >= 0 && texIdx < textureToImage.length) {
          spec.occlusionTextureIndex = textureToImage[texIdx];
        }
      }
      if (m.has("emissiveFactor")) {
        JsonArray ef = m.getAsJsonArray("emissiveFactor");
        if (ef.size() >= 3) {
          spec.er = clamp01(ef.get(0).getAsFloat());
          spec.eg = clamp01(ef.get(1).getAsFloat());
          spec.eb = clamp01(ef.get(2).getAsFloat());
          if (spec.er > 0.01f || spec.eg > 0.01f || spec.eb > 0.01f) {
            spec.emissiveStrength = 1.0f;
          }
        }
      }
      if (m.has("doubleSided")) spec.doubleSided = m.get("doubleSided").getAsBoolean();
      if (m.has("alphaMode") && "BLEND".equalsIgnoreCase(m.get("alphaMode").getAsString())) {
        spec.blendMode = CosmeticModelMesh.BLEND_TRANSLUCENT;
      }
      if (m.has("extras") && m.get("extras").isJsonObject()) {
        JsonObject ex = m.getAsJsonObject("extras");
        if (ex.has("nightclient") && ex.get("nightclient").isJsonObject()) {
          JsonObject tv = ex.getAsJsonObject("nightclient");
          if (tv.has("emissiveStrength")) {
            spec.emissiveStrength = Math.max(0f, Math.min(4f, tv.get("emissiveStrength").getAsFloat()));
          }
          if (tv.has("tintMask")) {
            spec.tintMask = clamp01(tv.get("tintMask").getAsFloat());
          }
          if (tv.has("additive") && tv.get("additive").getAsBoolean()) {
            spec.blendMode = CosmeticModelMesh.BLEND_ADDITIVE;
          }
          if (tv.has("flags")) {
            spec.flags = tv.get("flags").getAsInt() & 0x7F;
          }
        }
      }
      out.add(spec);
    }
    return out;
  }

  private static void traverseGlbNode(int nodeIdx, JsonArray nodes, JsonArray meshes,
      JsonArray accessors, JsonArray bufferViews, byte[] binChunk, List<MaterialSpec> materials,
      float[] parentMat, int inheritedBone, boolean armorModel, List<RawMeshPart> outParts,
      int[] counters, int depth) throws IOException {
    if (depth > 32 || nodeIdx < 0 || nodeIdx >= nodes.size()) return;
    JsonObject node = nodes.get(nodeIdx).getAsJsonObject();
    String nodeName = node.has("name") ? node.get("name").getAsString() : "";
    int bone = inheritedBone;
    if (armorModel) {
      int classified = classifyArmorBone(nodeName);
      if (classified >= 0) bone = classified;
    } else {
      bone = CosmeticModelMesh.BONE_WEAPON;
    }

    float[] localMat = readNodeMatrix(node);
    float[] worldMat = mat4Mul(parentMat, localMat);

    if (node.has("mesh")) {
      int meshIdx = node.get("mesh").getAsInt();
      if (meshIdx >= 0 && meshIdx < meshes.size()) {
        JsonObject mesh = meshes.get(meshIdx).getAsJsonObject();
        int meshBone = bone;
        if (armorModel && meshBone < 0 && mesh.has("name")) {
          meshBone = classifyArmorBone(mesh.get("name").getAsString());
        }
        if (meshBone < 0) meshBone = armorModel ? CosmeticModelMesh.BONE_BODY : CosmeticModelMesh.BONE_WEAPON;
        if (mesh.has("primitives")) {
          JsonArray prims = mesh.getAsJsonArray("primitives");
          for (int pi = 0; pi < prims.size(); pi++) {
            readGlbPrimitive(prims.get(pi).getAsJsonObject(), accessors, bufferViews, binChunk,
                materials, worldMat, meshBone, outParts, counters);
          }
        }
      }
    }

    if (node.has("children")) {
      for (JsonElement child : node.getAsJsonArray("children")) {
        traverseGlbNode(child.getAsInt(), nodes, meshes, accessors, bufferViews, binChunk,
            materials, worldMat, bone, armorModel, outParts, counters, depth + 1);
      }
    }
  }

  private static void readGlbPrimitive(JsonObject prim, JsonArray accessors, JsonArray bufferViews,
      byte[] binChunk, List<MaterialSpec> materials, float[] worldMat, int boneId,
      List<RawMeshPart> outParts, int[] counters) throws IOException {
    int mode = prim.has("mode") ? prim.get("mode").getAsInt() : 4;
    if (mode != 4 && mode != 5 && mode != 6) return;
    if (!prim.has("attributes")) return;
    JsonObject attrs = prim.getAsJsonObject("attributes");
    if (!attrs.has("POSITION")) return;

    float[] pos = readFloatAccessor(attrs.get("POSITION").getAsInt(), 3, accessors, bufferViews, binChunk);
    if (pos == null || pos.length < 9) return;
    int vcount = pos.length / 3;
    if (counters[0] + vcount > MAX_VERTICES) {
      throw new IOException("Model exceeds maximum vertex limit (" + MAX_VERTICES + ")");
    }

    float[] nrm = attrs.has("NORMAL")
        ? readFloatAccessor(attrs.get("NORMAL").getAsInt(), 3, accessors, bufferViews, binChunk)
        : null;
    float[] tan = attrs.has("TANGENT")
        ? readFloatAccessor(attrs.get("TANGENT").getAsInt(), 4, accessors, bufferViews, binChunk)
        : null;
    float[] uv = attrs.has("TEXCOORD_0")
        ? readFloatAccessor(attrs.get("TEXCOORD_0").getAsInt(), 2, accessors, bufferViews, binChunk)
        : null;
    int[] col = attrs.has("COLOR_0")
        ? readColorAccessor(attrs.get("COLOR_0").getAsInt(), vcount, accessors, bufferViews, binChunk)
        : null;

    int[] rawIdx;
    if (prim.has("indices")) {
      rawIdx = readIndexAccessor(prim.get("indices").getAsInt(), accessors, bufferViews, binChunk);
    } else {
      rawIdx = new int[vcount];
      for (int i = 0; i < vcount; i++) rawIdx[i] = i;
    }
    int[] triIdx = triangulateMode(rawIdx, mode, vcount);
    if (triIdx.length < 3) return;
    if (counters[1] + triIdx.length > MAX_INDICES) {
      throw new IOException("Model exceeds maximum index limit (" + MAX_INDICES + ")");
    }

    transformPositions(pos, worldMat);
    if (matrixDeterminant3x3(worldMat) < 0f) {
      for (int i = 0; i + 2 < triIdx.length; i += 3) {
        int tmp = triIdx[i + 1];
        triIdx[i + 1] = triIdx[i + 2];
        triIdx[i + 2] = tmp;
      }
    }
    if (nrm != null && nrm.length == vcount * 3) {
      transformNormals(nrm, worldMat);
    } else {
      nrm = computeSmoothNormals(pos, triIdx, vcount);
    }
    if (tan != null && tan.length == vcount * 4) {
      transformTangents(tan, worldMat);
    }
    if (uv == null || uv.length != vcount * 2) {
      uv = new float[vcount * 2];
      Arrays.fill(uv, 0.5f);
    }

    MaterialSpec mat = new MaterialSpec();
    if (prim.has("material")) {
      int mi = prim.get("material").getAsInt();
      if (mi >= 0 && mi < materials.size()) mat = materials.get(mi);
    }

    counters[0] += vcount;
    counters[1] += triIdx.length;
    outParts.add(new RawMeshPart(boneId, mat, pos, nrm, tan, uv, col, triIdx));
  }

  private static int[] triangulateMode(int[] raw, int mode, int vcount) {
    if (raw == null || raw.length < 3) return new int[0];
    if (mode == 4) {
      int tris = raw.length / 3;
      int[] out = new int[tris * 3];
      int n = 0;
      for (int i = 0; i < tris * 3; i += 3) {
        int a = raw[i], b = raw[i + 1], c = raw[i + 2];
        if (a >= 0 && a < vcount && b >= 0 && b < vcount && c >= 0 && c < vcount) {
          out[n++] = a;
          out[n++] = b;
          out[n++] = c;
        }
      }
      return n == out.length ? out : Arrays.copyOf(out, n);
    }
    if (mode == 5) {
      int tris = raw.length - 2;
      int[] out = new int[tris * 3];
      int n = 0;
      for (int i = 0; i < tris; i++) {
        int a = raw[i], b = raw[i + 1], c = raw[i + 2];
        if (a < 0 || a >= vcount || b < 0 || b >= vcount || c < 0 || c >= vcount) continue;
        if ((i & 1) == 0) {
          out[n++] = a; out[n++] = b; out[n++] = c;
        } else {
          out[n++] = a; out[n++] = c; out[n++] = b;
        }
      }
      return n == out.length ? out : Arrays.copyOf(out, n);
    }
    int tris = raw.length - 2;
    int[] out = new int[tris * 3];
    int n = 0;
    int hub = raw[0];
    for (int i = 1; i <= tris; i++) {
      int b = raw[i], c = raw[i + 1];
      if (hub >= 0 && hub < vcount && b >= 0 && b < vcount && c >= 0 && c < vcount) {
        out[n++] = hub; out[n++] = b; out[n++] = c;
      }
    }
    return n == out.length ? out : Arrays.copyOf(out, n);
  }

  private static float[] readFloatAccessor(int accIdx, int comps, JsonArray accessors,
      JsonArray bufferViews, byte[] bin) {
    if (accIdx < 0 || accIdx >= accessors.size()) return null;
    JsonObject acc = accessors.get(accIdx).getAsJsonObject();
    if (!acc.has("bufferView")) return null;
    int bvIdx = acc.get("bufferView").getAsInt();
    if (bvIdx < 0 || bvIdx >= bufferViews.size()) return null;
    JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();

    int count = acc.get("count").getAsInt();
    if (count <= 0 || count > MAX_VERTICES) return null;
    int compType = acc.get("componentType").getAsInt();
    int accOff = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
    int bvOff = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
    if (accOff < 0 || bvOff < 0) return null;
    int elemBytes = componentSize(compType);
    int defaultStride = elemBytes * comps;
    int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : defaultStride;
    if (stride < defaultStride) stride = defaultStride;
    boolean normalized = acc.has("normalized") && acc.get("normalized").getAsBoolean();

    long baseL = (long) bvOff + (long) accOff;
    if (baseL < 0L || baseL + (long) (count - 1) * stride + defaultStride > bin.length) {
      return null;
    }
    int base = (int) baseL;

    ByteBuffer bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
    float[] out = new float[count * comps];
    for (int i = 0; i < count; i++) {
      int row = base + i * stride;
      for (int c = 0; c < comps; c++) {
        out[i * comps + c] = readScalarAsFloat(bb, row + c * elemBytes, compType, normalized);
      }
    }
    return out;
  }

  private static int[] readColorAccessor(int accIdx, int vcount, JsonArray accessors,
      JsonArray bufferViews, byte[] bin) {
    if (accIdx < 0 || accIdx >= accessors.size()) return null;
    JsonObject acc = accessors.get(accIdx).getAsJsonObject();
    String type = acc.get("type").getAsString();
    int comps = "VEC4".equals(type) ? 4 : ("VEC3".equals(type) ? 3 : 0);
    if (comps == 0) return null;
    float[] f = readFloatAccessor(accIdx, comps, accessors, bufferViews, bin);
    if (f == null || f.length < vcount * comps) return null;
    int[] out = new int[vcount];
    for (int i = 0; i < vcount; i++) {
      int r = CosmeticModelMesh.clampByte(Math.round(f[i * comps] * 255f));
      int g = CosmeticModelMesh.clampByte(Math.round(f[i * comps + 1] * 255f));
      int b = CosmeticModelMesh.clampByte(Math.round(f[i * comps + 2] * 255f));
      int a = comps == 4 ? CosmeticModelMesh.clampByte(Math.round(f[i * comps + 3] * 255f)) : 255;
      out[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    return out;
  }

  private static int[] readIndexAccessor(int accIdx, JsonArray accessors, JsonArray bufferViews,
      byte[] bin) {
    if (accIdx < 0 || accIdx >= accessors.size()) return new int[0];
    JsonObject acc = accessors.get(accIdx).getAsJsonObject();
    if (!acc.has("bufferView")) return new int[0];
    int bvIdx = acc.get("bufferView").getAsInt();
    if (bvIdx < 0 || bvIdx >= bufferViews.size()) return new int[0];
    JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();

    int count = acc.get("count").getAsInt();
    if (count <= 0 || count > MAX_INDICES) return new int[0];
    int compType = acc.get("componentType").getAsInt();
    int accOff = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
    int bvOff = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
    if (accOff < 0 || bvOff < 0) return new int[0];
    int elemBytes = componentSize(compType);
    int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : elemBytes;
    if (stride < elemBytes) stride = elemBytes;
    long baseL = (long) bvOff + (long) accOff;
    if (baseL < 0L || baseL + (long) (count - 1) * stride + elemBytes > bin.length) {
      return new int[0];
    }
    int base = (int) baseL;

    ByteBuffer bb = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
    int[] out = new int[count];
    for (int i = 0; i < count; i++) {
      int pos = base + i * stride;
      if (compType == 5123) out[i] = bb.getShort(pos) & 0xFFFF;
      else if (compType == 5125) out[i] = bb.getInt(pos);
      else if (compType == 5121) out[i] = bb.get(pos) & 0xFF;
      else if (compType == 5122) out[i] = bb.getShort(pos);
      else out[i] = 0;
    }
    return out;
  }

  private static int componentSize(int compType) {
    switch (compType) {
      case 5120:
      case 5121:
        return 1;
      case 5122:
      case 5123:
        return 2;
      case 5125:
      case 5126:
        return 4;
      default:
        return 4;
    }
  }

  private static float readScalarAsFloat(ByteBuffer bb, int pos, int compType, boolean normalized) {
    switch (compType) {
      case 5126:
        return bb.getFloat(pos);
      case 5121: {
        int u = bb.get(pos) & 0xFF;
        return normalized ? u / 255f : u;
      }
      case 5123: {
        int u = bb.getShort(pos) & 0xFFFF;
        return normalized ? u / 65535f : u;
      }
      case 5120: {
        int s = bb.get(pos);
        return normalized ? Math.max(-1f, s / 127f) : s;
      }
      case 5122: {
        int s = bb.getShort(pos);
        return normalized ? Math.max(-1f, s / 32767f) : s;
      }
      case 5125:
        return bb.getInt(pos);
      default:
        return 0f;
    }
  }

  private static float[] readNodeMatrix(JsonObject node) {
    if (node.has("matrix")) {
      JsonArray m = node.getAsJsonArray("matrix");
      if (m.size() == 16) {
        float[] out = new float[16];
        for (int i = 0; i < 16; i++) out[i] = m.get(i).getAsFloat();
        return out;
      }
    }
    float tx = 0f, ty = 0f, tz = 0f;
    float qx = 0f, qy = 0f, qz = 0f, qw = 1f;
    float sx = 1f, sy = 1f, sz = 1f;
    if (node.has("translation")) {
      JsonArray t = node.getAsJsonArray("translation");
      if (t.size() >= 3) {
        tx = t.get(0).getAsFloat();
        ty = t.get(1).getAsFloat();
        tz = t.get(2).getAsFloat();
      }
    }
    if (node.has("rotation")) {
      JsonArray r = node.getAsJsonArray("rotation");
      if (r.size() >= 4) {
        qx = r.get(0).getAsFloat();
        qy = r.get(1).getAsFloat();
        qz = r.get(2).getAsFloat();
        qw = r.get(3).getAsFloat();
      }
    }
    if (node.has("scale")) {
      JsonArray s = node.getAsJsonArray("scale");
      if (s.size() >= 3) {
        sx = s.get(0).getAsFloat();
        sy = s.get(1).getAsFloat();
        sz = s.get(2).getAsFloat();
      }
    }
    return mat4FromTrs(tx, ty, tz, qx, qy, qz, qw, sx, sy, sz);
  }

  private static CosmeticModelMesh loadObjFromFile(Path objFile, byte[] raw, boolean armorModel)
      throws IOException {
    Path parent = objFile.getParent();
    Map<String, byte[]> sidecars = new HashMap<String, byte[]>();
    if (parent != null) {
      Path normParent = parent.toAbsolutePath().normalize();
      String text = new String(raw, StandardCharsets.UTF_8);
      for (String line : text.split("\n")) {
        String trimmed = line.trim();
        if (trimmed.startsWith("mtllib ")) {
          String mtlName = trimmed.substring(7).trim();
          Path mtlPath = normParent.resolve(mtlName).normalize();
          if (mtlPath.startsWith(normParent) && Files.isRegularFile(mtlPath)
              && Files.size(mtlPath) < 8 * 1024 * 1024) {
            byte[] mtlBytes = Files.readAllBytes(mtlPath);
            sidecars.put(mtlName, mtlBytes);
            String mtlText = new String(mtlBytes, StandardCharsets.UTF_8);
            for (String mline : mtlText.split("\n")) {
              String mt = mline.trim();
              if (mt.startsWith("map_Kd ")) {
                String texName = mt.substring(7).trim();
                Path texPath = normParent.resolve(texName).normalize();
                if (texPath.startsWith(normParent) && Files.isRegularFile(texPath)
                    && Files.size(texPath) < 32 * 1024 * 1024) {
                  sidecars.put(texName, Files.readAllBytes(texPath));
                }
              }
            }
          }
        }
      }
    }
    return loadObj(raw, sidecars, armorModel);
  }

  public static CosmeticModelMesh loadObj(byte[] raw, Map<String, byte[]> sidecars,
      boolean armorModel) throws IOException {
    List<Float> vPos = new ArrayList<Float>(1024);
    List<Float> vNrm = new ArrayList<Float>(1024);
    List<Float> vUv = new ArrayList<Float>(1024);
    Map<String, MaterialSpec> mtlMap = new HashMap<String, MaterialSpec>();
    List<DecodedImg> images = new ArrayList<DecodedImg>();

    final class GroupAccum {
      final int boneId;
      final String matName;
      final List<Float> pos = new ArrayList<Float>();
      final List<Float> nrm = new ArrayList<Float>();
      final List<Float> uv = new ArrayList<Float>();
      final List<Integer> idx = new ArrayList<Integer>();
      final Map<String, Integer> dedup = new HashMap<String, Integer>();
      boolean hasNormals = true;

      GroupAccum(int boneId, String matName) {
        this.boneId = boneId;
        this.matName = matName;
      }
    }

    Map<String, GroupAccum> groups = new LinkedHashMap<String, GroupAccum>();
    int curBone = armorModel ? CosmeticModelMesh.BONE_BODY : CosmeticModelMesh.BONE_WEAPON;
    String curMat = "";
    GroupAccum active = null;
    int totalObjVerts = 0;
    int totalObjIndices = 0;

    BufferedReader br = new BufferedReader(
        new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8));
    String line;
    while ((line = br.readLine()) != null) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      if (s.startsWith("v ")) {
        String[] tok = splitWs(s);
        if (tok.length >= 4) {
          vPos.add(parseFloat(tok[1], 0f));
          vPos.add(parseFloat(tok[2], 0f));
          vPos.add(parseFloat(tok[3], 0f));
        }
      } else if (s.startsWith("vn ")) {
        String[] tok = splitWs(s);
        if (tok.length >= 4) {
          vNrm.add(parseFloat(tok[1], 0f));
          vNrm.add(parseFloat(tok[2], 1f));
          vNrm.add(parseFloat(tok[3], 0f));
        }
      } else if (s.startsWith("vt ")) {
        String[] tok = splitWs(s);
        if (tok.length >= 3) {
          vUv.add(parseFloat(tok[1], 0.5f));
          vUv.add(1f - parseFloat(tok[2], 0.5f));
        }
      } else if (s.startsWith("o ") || s.startsWith("g ")) {
        if (armorModel) {
          int b = classifyArmorBone(s.substring(2).trim());
          curBone = b >= 0 ? b : CosmeticModelMesh.BONE_BODY;
        }
        active = null;
      } else if (s.startsWith("mtllib ")) {
        String name = s.substring(7).trim();
        byte[] mtlBytes = sidecars == null ? null : sidecars.get(name);
        if (mtlBytes != null) parseMtl(mtlBytes, sidecars, mtlMap, images);
      } else if (s.startsWith("usemtl ")) {
        curMat = s.substring(7).trim();
        active = null;
      } else if (s.startsWith("f ")) {
        String[] tok = splitWs(s);
        if (tok.length < 4) continue;
        if (active == null) {
          String gkey = curBone + ":" + curMat;
          active = groups.get(gkey);
          if (active == null) {
            active = new GroupAccum(curBone, curMat);
            groups.put(gkey, active);
          }
        }
        int nv = vPos.size() / 3;
        int nt = vUv.size() / 2;
        int nn = vNrm.size() / 3;
        if (nv <= 0) continue;
        int[] poly = new int[tok.length - 1];
        boolean validFace = true;
        for (int i = 1; i < tok.length; i++) {
          String[] parts = tok[i].split("/", -1);
          int vi = resolveObjIndex(parts[0], nv);
          if (vi < 0 || vi >= nv) {
            validFace = false;
            break;
          }
          int ti = parts.length > 1 && !parts[1].isEmpty() ? resolveObjIndex(parts[1], nt) : -1;
          int ni = parts.length > 2 && !parts[2].isEmpty() ? resolveObjIndex(parts[2], nn) : -1;
          String resolvedKey = vi + "/" + ti + "/" + ni;
          Integer existing = active.dedup.get(resolvedKey);
          if (existing != null) {
            poly[i - 1] = existing.intValue();
            continue;
          }
          if (++totalObjVerts > MAX_VERTICES) {
            throw new IOException("OBJ model exceeds maximum vertex limit (" + MAX_VERTICES + ")");
          }
          int idx = active.pos.size() / 3;
          active.pos.add(vPos.get(vi * 3));
          active.pos.add(vPos.get(vi * 3 + 1));
          active.pos.add(vPos.get(vi * 3 + 2));
          if (ti >= 0 && ti < nt) {
            active.uv.add(vUv.get(ti * 2));
            active.uv.add(vUv.get(ti * 2 + 1));
          } else {
            active.uv.add(0.5f);
            active.uv.add(0.5f);
          }
          if (ni >= 0 && ni < nn) {
            active.nrm.add(vNrm.get(ni * 3));
            active.nrm.add(vNrm.get(ni * 3 + 1));
            active.nrm.add(vNrm.get(ni * 3 + 2));
          } else {
            active.hasNormals = false;
            active.nrm.add(0f);
            active.nrm.add(1f);
            active.nrm.add(0f);
          }
          active.dedup.put(resolvedKey, Integer.valueOf(idx));
          poly[i - 1] = idx;
        }
        if (!validFace) continue;
        int addedIndices = Math.max(0, (poly.length - 2) * 3);
        totalObjIndices += addedIndices;
        if (totalObjIndices > MAX_INDICES) {
          throw new IOException("OBJ model exceeds maximum index limit (" + MAX_INDICES + ")");
        }
        for (int i = 1; i + 1 < poly.length; i++) {
          active.idx.add(poly[0]);
          active.idx.add(poly[i]);
          active.idx.add(poly[i + 1]);
        }
      }
    }

    List<RawMeshPart> parts = new ArrayList<RawMeshPart>();
    for (GroupAccum g : groups.values()) {
      if (g.idx.size() < 3) continue;
      int vc = g.pos.size() / 3;
      float[] pos = toFloatArray(g.pos);
      float[] uv = toFloatArray(g.uv);
      int[] idx = toIntArray(g.idx);
      float[] nrm = g.hasNormals ? toFloatArray(g.nrm) : computeSmoothNormals(pos, idx, vc);
      MaterialSpec mat = mtlMap.containsKey(g.matName) ? mtlMap.get(g.matName) : new MaterialSpec();
      parts.add(new RawMeshPart(g.boneId, mat, pos, nrm, null, uv, null, idx));
    }
    if (parts.isEmpty()) throw new IOException("OBJ contains no renderable faces");
    return assembleMesh(parts, images, armorModel, false);
  }

  private static void parseMtl(byte[] raw, Map<String, byte[]> sidecars,
      Map<String, MaterialSpec> mtlMap, List<DecodedImg> images) {
    String text = new String(raw, StandardCharsets.UTF_8);
    MaterialSpec cur = null;
    for (String line : text.split("\n")) {
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      if (s.startsWith("newmtl ")) {
        cur = new MaterialSpec();
        mtlMap.put(s.substring(7).trim(), cur);
      } else if (cur != null) {
        if (s.startsWith("Kd ")) {
          String[] tok = splitWs(s);
          if (tok.length >= 4) {
            cur.r = clamp01(parseFloat(tok[1], 0.85f));
            cur.g = clamp01(parseFloat(tok[2], 0.85f));
            cur.b = clamp01(parseFloat(tok[3], 0.88f));
          }
        } else if (s.startsWith("Ke ")) {
          String[] tok = splitWs(s);
          if (tok.length >= 4) {
            cur.er = clamp01(parseFloat(tok[1], 0f));
            cur.eg = clamp01(parseFloat(tok[2], 0f));
            cur.eb = clamp01(parseFloat(tok[3], 0f));
            if (cur.er > 0.01f || cur.eg > 0.01f || cur.eb > 0.01f) cur.emissiveStrength = 1.2f;
          }
        } else if (s.startsWith("Ks ")) {
          String[] tok = splitWs(s);
          if (tok.length >= 4) {
            float ks = (parseFloat(tok[1], 0f) + parseFloat(tok[2], 0f) + parseFloat(tok[3], 0f)) / 3f;
            cur.metallic = clamp01(ks);
          }
        } else if (s.startsWith("Ns ")) {
          String[] tok = splitWs(s);
          if (tok.length >= 2) {
            float ns = Math.max(1f, parseFloat(tok[1], 32f));
            cur.roughness = clamp01(1f - (float) Math.sqrt(Math.min(1f, ns / 256f)));
          }
        } else if (s.startsWith("d ")) {
          String[] tok = splitWs(s);
          if (tok.length >= 2) {
            cur.a = clamp01(parseFloat(tok[1], 1f));
            if (cur.a < 0.99f) cur.blendMode = CosmeticModelMesh.BLEND_TRANSLUCENT;
          }
        } else if (s.startsWith("map_Kd ")) {
          String texName = s.substring(7).trim();
          byte[] tb = sidecars == null ? null : sidecars.get(texName);
          if (tb != null) {
            DecodedImg img = decodeImageBytes(tb);
            if (img != null) {
              cur.textureIndex = images.size();
              images.add(img);
            }
          }
        }
      }
    }
  }

  private static int resolveObjIndex(String token, int size) {
    if (size <= 0) return -1;
    try {
      int v = Integer.parseInt(token.trim());
      if (v > 0) return v <= size ? v - 1 : -1;
      if (v < 0) return size + v >= 0 ? size + v : -1;
    } catch (NumberFormatException ignored) {
    }
    return -1;
  }

  private static CosmeticModelMesh assembleMesh(List<RawMeshPart> parts, List<DecodedImg> images,
      boolean armorModel, boolean calibrated) {
    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (RawMeshPart p : parts) {
      int vc = p.pos.length / 3;
      MaterialSpec m = p.material;
      DecodedImg normImg = (m.normalTextureIndex >= 0 && m.normalTextureIndex < images.size())
          ? images.get(m.normalTextureIndex)
          : null;
      if (normImg != null && p.tan != null && p.tan.length == vc * 4
          && normImg.pixels != null && normImg.width > 0 && normImg.height > 0) {
        float nScale = m.normalScale;
        for (int vi = 0; vi < vc; vi++) {
          float rawU = wrap01(p.uv[vi * 2]);
          float rawV = wrap01(p.uv[vi * 2 + 1]);
          int nxTex = Math.max(0, Math.min(normImg.width - 1, (int) (rawU * (normImg.width - 1))));
          int nyTex = Math.max(0, Math.min(normImg.height - 1, (int) (rawV * (normImg.height - 1))));
          int np = normImg.pixels[nyTex * normImg.width + nxTex];
          float mapX = ((((np >>> 16) & 255) / 255f) * 2f - 1f) * nScale;
          float mapY = ((((np >>> 8) & 255) / 255f) * 2f - 1f) * nScale;
          float mapZ = Math.max(0.05f, ((np & 255) / 255f) * 2f - 1f);

          float nx = p.nrm[vi * 3], ny = p.nrm[vi * 3 + 1], nz = p.nrm[vi * 3 + 2];
          float tx = p.tan[vi * 4], ty = p.tan[vi * 4 + 1], tz = p.tan[vi * 4 + 2], tw = p.tan[vi * 4 + 3];
          float bx = (ny * tz - nz * ty) * tw;
          float by = (nz * tx - nx * tz) * tw;
          float bz = (nx * ty - ny * tx) * tw;

          float wx = tx * mapX + bx * mapY + nx * mapZ;
          float wy = ty * mapX + by * mapY + ny * mapZ;
          float wz = tz * mapX + bz * mapY + nz * mapZ;
          float len = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
          if (len > 1e-6f) {
            p.nrm[vi * 3] = wx / len;
            p.nrm[vi * 3 + 1] = wy / len;
            p.nrm[vi * 3 + 2] = wz / len;
          }
        }
      }
      for (int i = 0; i < p.pos.length; i += 3) {
        float x = p.pos[i], y = p.pos[i + 1], z = p.pos[i + 2];
        if (x < minX) minX = x; if (x > maxX) maxX = x;
        if (y < minY) minY = y; if (y > maxY) maxY = y;
        if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
      }
    }

    if (!calibrated) {
      float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
      float maxSpan = Math.max(dx, Math.max(dy, dz));
      if (maxSpan > 1e-5f) {
        if (!armorModel) {
          boolean swapXY = dx > dy * 1.25f && dx >= dz;
          boolean swapZY = !swapXY && dz > dy * 1.25f && dz >= dx;
          if (swapXY || swapZY) {
            for (RawMeshPart p : parts) {
              for (int i = 0; i < p.pos.length; i += 3) {
                float x = p.pos[i], y = p.pos[i + 1], z = p.pos[i + 2];
                float nx = p.nrm[i], ny = p.nrm[i + 1], nz = p.nrm[i + 2];
                if (swapXY) {
                  p.pos[i] = -y; p.pos[i + 1] = x;
                  p.nrm[i] = -ny; p.nrm[i + 1] = nx;
                } else {
                  p.pos[i + 1] = z; p.pos[i + 2] = -y;
                  p.nrm[i + 1] = nz; p.nrm[i + 2] = -ny;
                }
              }
            }
            minX = minY = minZ = Float.POSITIVE_INFINITY;
            maxX = maxY = maxZ = Float.NEGATIVE_INFINITY;
            for (RawMeshPart p : parts) {
              for (int i = 0; i < p.pos.length; i += 3) {
                float x = p.pos[i], y = p.pos[i + 1], z = p.pos[i + 2];
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
              }
            }
            dx = maxX - minX; dy = maxY - minY; dz = maxZ - minZ;
            maxSpan = Math.max(dx, Math.max(dy, dz));
          }
          float scale = 1.40f / maxSpan;
          float cx = (minX + maxX) * 0.5f;
          float cy = minY + dy * 0.18f;
          float cz = (minZ + maxZ) * 0.5f;
          for (RawMeshPart p : parts) {
            for (int i = 0; i < p.pos.length; i += 3) {
              p.pos[i] = (p.pos[i] - cx) * scale;
              p.pos[i + 1] = (p.pos[i + 1] - cy) * scale;
              p.pos[i + 2] = (p.pos[i + 2] - cz) * scale;
            }
          }
          minX = (minX - cx) * scale; maxX = (maxX - cx) * scale;
          minY = (minY - cy) * scale; maxY = (maxY - cy) * scale;
          minZ = (minZ - cz) * scale; maxZ = (maxZ - cz) * scale;
        } else if (maxSpan > 4.0f || maxSpan < 0.25f) {
          float scale = 1.85f / maxSpan;
          for (RawMeshPart p : parts) {
            for (int i = 0; i < p.pos.length; i++) p.pos[i] *= scale;
          }
          minX *= scale; maxX *= scale;
          minY *= scale; maxY *= scale;
          minZ *= scale; maxZ *= scale;
        }
      }
    }

    int[] atlasPixels;
    int atlasW, atlasH;
    boolean[] usedByBaseColor = new boolean[images.size()];
    for (RawMeshPart p : parts) {
      int ti = p.material.textureIndex;
      if (ti >= 0 && ti < images.size() && images.get(ti) != null) {
        usedByBaseColor[ti] = true;
      }
    }
    List<DecodedImg> usedImages = new ArrayList<DecodedImg>();
    for (int i = 0; i < images.size(); i++) {
      if (usedByBaseColor[i]) usedImages.add(images.get(i));
    }
    if (usedImages.isEmpty()) {
      atlasW = 1;
      atlasH = 1;
      atlasPixels = new int[] {0xffffffff};
    } else if (usedImages.size() == 1) {
      DecodedImg single = usedImages.get(0);
      single.atlasX = 0;
      single.atlasY = 0;
      single.atlasW = single.width;
      single.atlasH = single.height;
      atlasW = single.width;
      atlasH = single.height;
      atlasPixels = single.pixels;
    } else {
      float scale = 1.0f;
      int packedW = 1, packedH = 1;
      boolean packed = false;
      for (int attempt = 0; attempt < 6; attempt++) {
        int curX = 0, curY = 0, shelfH = 0, maxAtlasX = 1;
        boolean fits = true;
        for (DecodedImg img : usedImages) {
          int sw = Math.max(1, Math.round(img.width * scale));
          int sh = Math.max(1, Math.round(img.height * scale));
          if (curX > 0 && curX + sw > MAX_TEXTURE_DIM) {
            curX = 0;
            curY += shelfH;
            shelfH = 0;
          }
          if (sw > MAX_TEXTURE_DIM || curY + sh > MAX_TEXTURE_DIM) {
            fits = false;
            break;
          }
          img.atlasX = curX;
          img.atlasY = curY;
          img.atlasW = sw;
          img.atlasH = sh;
          curX += sw;
          if (curX > maxAtlasX) maxAtlasX = curX;
          if (sh > shelfH) shelfH = sh;
        }
        if (fits) {
          packedW = Math.min(MAX_TEXTURE_DIM, Math.max(1, maxAtlasX));
          packedH = Math.min(MAX_TEXTURE_DIM, Math.max(1, curY + shelfH));
          packed = true;
          break;
        }
        scale *= 0.65f;
      }
      if (!packed) {
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(usedImages.size())));
        int rows = Math.max(1, (usedImages.size() + cols - 1) / cols);
        int cellW = Math.max(1, MAX_TEXTURE_DIM / cols);
        int cellH = Math.max(1, MAX_TEXTURE_DIM / rows);
        packedW = Math.min(MAX_TEXTURE_DIM, cols * cellW);
        packedH = Math.min(MAX_TEXTURE_DIM, rows * cellH);
        for (int idx = 0; idx < usedImages.size(); idx++) {
          DecodedImg img = usedImages.get(idx);
          img.atlasX = (idx % cols) * cellW;
          img.atlasY = (idx / cols) * cellH;
          img.atlasW = Math.min(cellW, Math.max(1, packedW - img.atlasX));
          img.atlasH = Math.min(cellH, Math.max(1, packedH - img.atlasY));
        }
      }
      atlasW = packedW;
      atlasH = packedH;
      atlasPixels = new int[atlasW * atlasH];
      Arrays.fill(atlasPixels, 0xffffffff);
      for (DecodedImg img : usedImages) {
        if (img.atlasX < 0 || img.atlasY < 0 || img.atlasX >= atlasW || img.atlasY >= atlasH) {
          img.atlasX = 0;
          img.atlasY = 0;
          img.atlasW = 1;
          img.atlasH = 1;
          continue;
        }
        int dstW = Math.max(1, Math.min(img.atlasW, atlasW - img.atlasX));
        int dstH = Math.max(1, Math.min(img.atlasH, atlasH - img.atlasY));
        img.atlasW = dstW;
        img.atlasH = dstH;
        if (dstW == img.width && dstH == img.height) {
          for (int y = 0; y < dstH; y++) {
            System.arraycopy(
                img.pixels, y * img.width, atlasPixels, (img.atlasY + y) * atlasW + img.atlasX, dstW);
          }
        } else {
          for (int y = 0; y < dstH; y++) {
            int sy = Math.min(img.height - 1, (int) ((long) y * img.height / dstH));
            int dstRow = (img.atlasY + y) * atlasW + img.atlasX;
            int srcRow = sy * img.width;
            for (int x = 0; x < dstW; x++) {
              int sx = Math.min(img.width - 1, (int) ((long) x * img.width / dstW));
              atlasPixels[dstRow + x] = img.pixels[srcRow + sx];
            }
          }
        }
      }
    }

    Map<BucketKey, List<RawMeshPart>> buckets = new LinkedHashMap<BucketKey, List<RawMeshPart>>();
    for (RawMeshPart part : parts) {
      BucketKey key = new BucketKey(part.boneId, part.material.blendMode, part.material.doubleSided);
      List<RawMeshPart> list = buckets.get(key);
      if (list == null) {
        list = new ArrayList<RawMeshPart>();
        buckets.put(key, list);
      }
      list.add(part);
    }
    List<BucketKey> orderedKeys = new ArrayList<BucketKey>(buckets.keySet());
    Collections.sort(orderedKeys, (a, b) -> {
      if (a.blendMode != b.blendMode) return Integer.compare(a.blendMode, b.blendMode);
      if (a.boneId != b.boneId) return Integer.compare(a.boneId, b.boneId);
      return Boolean.compare(a.doubleSided, b.doubleSided);
    });

    int totalVerts = 0;
    int totalIndices = 0;
    for (BucketKey key : orderedKeys) {
      for (RawMeshPart p : buckets.get(key)) {
        totalVerts += p.pos.length / 3;
        totalIndices += p.idx.length;
      }
    }

    byte[] vertexBytes = new byte[totalVerts * CosmeticModelMesh.VERTEX_STRIDE];
    ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.nativeOrder());
    int[] allIndices = new int[totalIndices];
    List<CosmeticModelMesh.SubMesh> subMeshes = new ArrayList<CosmeticModelMesh.SubMesh>(orderedKeys.size());

    int vCursor = 0;
    int iCursor = 0;
    for (BucketKey key : orderedKeys) {
      int subStart = iCursor;
      for (RawMeshPart p : buckets.get(key)) {
        int vc = p.pos.length / 3;
        MaterialSpec m = p.material;
        DecodedImg img = (m.textureIndex >= 0 && m.textureIndex < images.size())
            ? images.get(m.textureIndex)
            : null;
        DecodedImg mrImg = (m.mrTextureIndex >= 0 && m.mrTextureIndex < images.size())
            ? images.get(m.mrTextureIndex)
            : null;
        DecodedImg occImg = (m.occlusionTextureIndex >= 0 && m.occlusionTextureIndex < images.size())
            ? images.get(m.occlusionTextureIndex)
            : null;
        float uOff = img != null ? img.atlasX / (float) atlasW : 0f;
        float vOff = img != null ? img.atlasY / (float) atlasH : 0f;
        float uScale = img != null ? img.atlasW / (float) atlasW : 1f;
        float vScale = img != null ? img.atlasH / (float) atlasH : 1f;

        short defaultUv1x = CosmeticModelMesh.packByteAnd7Bit(m.metallic, m.roughness);
        short uv1y = CosmeticModelMesh.packByteAnd7Bit(Math.min(1f, m.emissiveStrength * 0.25f), m.tintMask);
        short uv2x = CosmeticModelMesh.packByteAnd7Bit(m.er, m.eg);
        short uv2y = CosmeticModelMesh.packByteAndFlags7(m.eb, m.flags);

        for (int vi = 0; vi < vc; vi++) {
          float px = p.pos[vi * 3];
          float py = p.pos[vi * 3 + 1];
          float pz = p.pos[vi * 3 + 2];
          float nx = p.nrm[vi * 3];
          float ny = p.nrm[vi * 3 + 1];
          float nz = p.nrm[vi * 3 + 2];
          float rawU = wrap01(p.uv[vi * 2]);
          float rawV = wrap01(p.uv[vi * 2 + 1]);
          float u = uOff + rawU * uScale;
          float v = vOff + rawV * vScale;

          short uv1x = defaultUv1x;
          if (mrImg != null && mrImg.pixels != null && mrImg.width > 0 && mrImg.height > 0) {
            int mx = Math.max(0, Math.min(mrImg.width - 1, (int) (rawU * (mrImg.width - 1))));
            int my = Math.max(0, Math.min(mrImg.height - 1, (int) (rawV * (mrImg.height - 1))));
            int mrPixel = mrImg.pixels[my * mrImg.width + mx];
            float rSample = ((mrPixel >>> 8) & 255) / 255f;
            float mSample = (mrPixel & 255) / 255f;
            uv1x = CosmeticModelMesh.packByteAnd7Bit(m.metallic * mSample, m.roughness * rSample);
          }

          float cr = m.r, cg = m.g, cb = m.b, ca = m.a;
          if (occImg != null && occImg.pixels != null && occImg.width > 0 && occImg.height > 0) {
            int ox = Math.max(0, Math.min(occImg.width - 1, (int) (rawU * (occImg.width - 1))));
            int oy = Math.max(0, Math.min(occImg.height - 1, (int) (rawV * (occImg.height - 1))));
            int occPixel = occImg.pixels[oy * occImg.width + ox];
            float ao = 0.35f + 0.65f * (((occPixel >>> 16) & 255) / 255f);
            cr *= ao;
            cg *= ao;
            cb *= ao;
          }
          if (p.col != null && vi < p.col.length) {
            int argb = p.col[vi];
            ca *= ((argb >>> 24) & 255) / 255f;
            cr *= ((argb >>> 16) & 255) / 255f;
            cg *= ((argb >>> 8) & 255) / 255f;
            cb *= (argb & 255) / 255f;
          }

          vb.putFloat(px).putFloat(py).putFloat(pz);
          vb.put((byte) CosmeticModelMesh.clampByte(Math.round(cr * 255f)));
          vb.put((byte) CosmeticModelMesh.clampByte(Math.round(cg * 255f)));
          vb.put((byte) CosmeticModelMesh.clampByte(Math.round(cb * 255f)));
          vb.put((byte) CosmeticModelMesh.clampByte(Math.round(ca * 255f)));
          vb.putFloat(u).putFloat(v);
          vb.putShort(uv1x).putShort(uv1y);
          vb.putShort(uv2x).putShort(uv2y);
          vb.put(CosmeticModelMesh.packSignedNormal(nx));
          vb.put(CosmeticModelMesh.packSignedNormal(ny));
          vb.put(CosmeticModelMesh.packSignedNormal(nz));
          vb.put((byte) 0);
        }

        for (int ii = 0; ii < p.idx.length; ii++) {
          allIndices[iCursor++] = vCursor + p.idx[ii];
        }
        vCursor += vc;
      }
      if (iCursor > subStart) {
        subMeshes.add(new CosmeticModelMesh.SubMesh(
            key.boneId, subStart, iCursor - subStart, key.blendMode, key.doubleSided));
      }
    }

    CosmeticModelMesh.PreviewMesh preview = buildPreviewMesh(parts, images, atlasPixels, atlasW, atlasH);

    return new CosmeticModelMesh(
        vertexBytes,
        allIndices,
        totalVerts,
        subMeshes.toArray(new CosmeticModelMesh.SubMesh[subMeshes.size()]),
        atlasPixels,
        atlasW,
        atlasH,
        minX, minY, minZ,
        maxX, maxY, maxZ,
        preview);
  }

  private static CosmeticModelMesh.PreviewMesh buildPreviewMesh(List<RawMeshPart> parts,
      List<DecodedImg> images, int[] atlasPixels, int atlasW, int atlasH) {
    int totalTris = 0;
    float gxMin = Float.POSITIVE_INFINITY, gyMin = Float.POSITIVE_INFINITY, gzMin = Float.POSITIVE_INFINITY;
    float gxMax = Float.NEGATIVE_INFINITY, gyMax = Float.NEGATIVE_INFINITY, gzMax = Float.NEGATIVE_INFINITY;
    for (RawMeshPart p : parts) {
      totalTris += p.idx.length / 3;
      for (int i = 0; i < p.pos.length; i += 3) {
        float x = p.pos[i], y = p.pos[i + 1], z = p.pos[i + 2];
        if (x < gxMin) gxMin = x; if (x > gxMax) gxMax = x;
        if (y < gyMin) gyMin = y; if (y > gyMax) gyMax = y;
        if (z < gzMin) gzMin = z; if (z > gzMax) gzMax = z;
      }
    }

    boolean cluster = totalTris > MAX_PREVIEW_TRIS;
    float span = Math.max(1e-4f, Math.max(gxMax - gxMin, Math.max(gyMax - gyMin, gzMax - gzMin)));
    float gridCells = cluster
        ? Math.max(24f, Math.min(220f, (float) Math.sqrt((double) MAX_PREVIEW_TRIS / Math.max(1, totalTris)) * 180f))
        : 1f;
    float cellSize = span / gridCells;
    float invCell = cluster ? 1f / cellSize : 1f;

    boolean hasTex = atlasW > 1 || atlasH > 1;
    int cap = Math.min(totalTris, MAX_PREVIEW_TRIS);
    float[] pPos = new float[cap * 9];
    float[] pNrm = new float[cap * 9];
    float[] pUv = hasTex ? new float[cap * 6] : null;
    int[] pCol = new int[cap];
    byte[] pBone = new byte[cap];
    int[] pMat = new int[cap];
    int count = 0;

    for (RawMeshPart part : parts) {
      int tris = part.idx.length / 3;
      MaterialSpec m = part.material;
      DecodedImg img = (m.textureIndex >= 0 && m.textureIndex < images.size())
          ? images.get(m.textureIndex)
          : null;
      DecodedImg mrImg = (m.mrTextureIndex >= 0 && m.mrTextureIndex < images.size())
          ? images.get(m.mrTextureIndex)
          : null;
      DecodedImg occImg = (m.occlusionTextureIndex >= 0 && m.occlusionTextureIndex < images.size())
          ? images.get(m.occlusionTextureIndex)
          : null;
      float uOff = img != null ? img.atlasX / (float) atlasW : 0f;
      float vOff = img != null ? img.atlasY / (float) atlasH : 0f;
      float uScale = img != null ? img.atlasW / (float) atlasW : 1f;
      float vScale = img != null ? img.atlasH / (float) atlasH : 1f;
      int baseArgb =
          (CosmeticModelMesh.clampByte(Math.round(m.a * 255f)) << 24)
              | (CosmeticModelMesh.clampByte(Math.round(m.r * 255f)) << 16)
              | (CosmeticModelMesh.clampByte(Math.round(m.g * 255f)) << 8)
              | CosmeticModelMesh.clampByte(Math.round(m.b * 255f));
      int defaultMatBits =
          CosmeticModelMesh.clampByte(Math.round(m.metallic * 255f))
              | (CosmeticModelMesh.clampByte(Math.round(Math.min(1f, m.emissiveStrength * 0.4f) * 255f)) << 8)
              | (CosmeticModelMesh.clampByte(Math.round(m.tintMask * 255f)) << 16)
              | ((m.blendMode & 3) << 24);

      for (int t = 0; t < tris && count < cap; t++) {
        int i0 = part.idx[t * 3];
        int i1 = part.idx[t * 3 + 1];
        int i2 = part.idx[t * 3 + 2];
        float x0 = part.pos[i0 * 3], y0 = part.pos[i0 * 3 + 1], z0 = part.pos[i0 * 3 + 2];
        float x1 = part.pos[i1 * 3], y1 = part.pos[i1 * 3 + 1], z1 = part.pos[i1 * 3 + 2];
        float x2 = part.pos[i2 * 3], y2 = part.pos[i2 * 3 + 1], z2 = part.pos[i2 * 3 + 2];
        if (cluster) {
          int cx0 = Math.round((x0 - gxMin) * invCell), cy0 = Math.round((y0 - gyMin) * invCell), cz0 = Math.round((z0 - gzMin) * invCell);
          int cx1 = Math.round((x1 - gxMin) * invCell), cy1 = Math.round((y1 - gyMin) * invCell), cz1 = Math.round((z1 - gzMin) * invCell);
          int cx2 = Math.round((x2 - gxMin) * invCell), cy2 = Math.round((y2 - gyMin) * invCell), cz2 = Math.round((z2 - gzMin) * invCell);
          if ((cx0 == cx1 && cy0 == cy1 && cz0 == cz1)
              || (cx1 == cx2 && cy1 == cy2 && cz1 == cz2)
              || (cx2 == cx0 && cy2 == cy0 && cz2 == cz0)) {
            continue;
          }
          x0 = gxMin + cx0 * cellSize; y0 = gyMin + cy0 * cellSize; z0 = gzMin + cz0 * cellSize;
          x1 = gxMin + cx1 * cellSize; y1 = gyMin + cy1 * cellSize; z1 = gzMin + cz1 * cellSize;
          x2 = gxMin + cx2 * cellSize; y2 = gyMin + cy2 * cellSize; z2 = gzMin + cz2 * cellSize;
        }
        int o9 = count * 9;
        pPos[o9] = x0; pPos[o9 + 1] = y0; pPos[o9 + 2] = z0;
        pPos[o9 + 3] = x1; pPos[o9 + 4] = y1; pPos[o9 + 5] = z1;
        pPos[o9 + 6] = x2; pPos[o9 + 7] = y2; pPos[o9 + 8] = z2;

        pNrm[o9] = part.nrm[i0 * 3]; pNrm[o9 + 1] = part.nrm[i0 * 3 + 1]; pNrm[o9 + 2] = part.nrm[i0 * 3 + 2];
        pNrm[o9 + 3] = part.nrm[i1 * 3]; pNrm[o9 + 4] = part.nrm[i1 * 3 + 1]; pNrm[o9 + 5] = part.nrm[i1 * 3 + 2];
        pNrm[o9 + 6] = part.nrm[i2 * 3]; pNrm[o9 + 7] = part.nrm[i2 * 3 + 1]; pNrm[o9 + 8] = part.nrm[i2 * 3 + 2];

        float u0 = wrap01(part.uv[i0 * 2]), v0 = wrap01(part.uv[i0 * 2 + 1]);
        float u1 = wrap01(part.uv[i1 * 2]), v1 = wrap01(part.uv[i1 * 2 + 1]);
        float u2 = wrap01(part.uv[i2 * 2]), v2 = wrap01(part.uv[i2 * 2 + 1]);
        if (pUv != null) {
          int o6 = count * 6;
          if (img != null) {
            pUv[o6] = uOff + u0 * uScale; pUv[o6 + 1] = vOff + v0 * vScale;
            pUv[o6 + 2] = uOff + u1 * uScale; pUv[o6 + 3] = vOff + v1 * vScale;
            pUv[o6 + 4] = uOff + u2 * uScale; pUv[o6 + 5] = vOff + v2 * vScale;
          } else {
            pUv[o6] = -1f;
          }
        }

        int argb = baseArgb;
        int matBits = defaultMatBits;
        float u = wrap01((u0 + u1 + u2) / 3f);
        float v = wrap01((v0 + v1 + v2) / 3f);
        if (part.col != null && i0 < part.col.length && i1 < part.col.length && i2 < part.col.length) {
          int c0 = part.col[i0], c1 = part.col[i1], c2 = part.col[i2];
          int va = (((c0 >>> 24) & 255) + ((c1 >>> 24) & 255) + ((c2 >>> 24) & 255)) / 3;
          int vr = (((c0 >>> 16) & 255) + ((c1 >>> 16) & 255) + ((c2 >>> 16) & 255)) / 3;
          int vg = (((c0 >>> 8) & 255) + ((c1 >>> 8) & 255) + ((c2 >>> 8) & 255)) / 3;
          int vb = ((c0 & 255) + (c1 & 255) + (c2 & 255)) / 3;
          argb = multiplyArgb(argb, (va << 24) | (vr << 16) | (vg << 8) | vb);
        }
        if (pUv == null && img != null && atlasPixels.length == atlasW * atlasH) {
          int tx = Math.max(0, Math.min(atlasW - 1, img.atlasX + (int) (u * (img.width - 1))));
          int ty = Math.max(0, Math.min(atlasH - 1, img.atlasY + (int) (v * (img.height - 1))));
          argb = multiplyArgb(argb, atlasPixels[ty * atlasW + tx]);
        }
        if (occImg != null && occImg.pixels != null && occImg.width > 0 && occImg.height > 0) {
          int ox = Math.max(0, Math.min(occImg.width - 1, (int) (u * (occImg.width - 1))));
          int oy = Math.max(0, Math.min(occImg.height - 1, (int) (v * (occImg.height - 1))));
          int occVal = Math.round(89f + 166f * (((occImg.pixels[oy * occImg.width + ox] >>> 16) & 255) / 255f));
          argb = multiplyArgb(argb, (255 << 24) | (occVal << 16) | (occVal << 8) | occVal);
        }
        if (mrImg != null && mrImg.pixels != null && mrImg.width > 0 && mrImg.height > 0) {
          int mx = Math.max(0, Math.min(mrImg.width - 1, (int) (u * (mrImg.width - 1))));
          int my = Math.max(0, Math.min(mrImg.height - 1, (int) (v * (mrImg.height - 1))));
          int mSample = mrImg.pixels[my * mrImg.width + mx] & 255;
          int mByte = CosmeticModelMesh.clampByte(Math.round(m.metallic * mSample));
          matBits = (defaultMatBits & ~0xFF) | mByte;
        }
        pCol[count] = argb;
        pBone[count] = (byte) part.boneId;
        pMat[count] = matBits;
        count++;
      }
    }

    return new CosmeticModelMesh.PreviewMesh(
        count,
        Arrays.copyOf(pPos, count * 9),
        Arrays.copyOf(pNrm, count * 9),
        Arrays.copyOf(pCol, count),
        Arrays.copyOf(pBone, count),
        Arrays.copyOf(pMat, count),
        pUv != null ? Arrays.copyOf(pUv, count * 6) : null,
        hasTex ? atlasPixels : null,
        hasTex ? atlasW : 0,
        hasTex ? atlasH : 0);
  }

  public static int classifyArmorBone(String rawName) {
    if (rawName == null || rawName.isEmpty()) return -1;
    String n = rawName.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    if (n.contains("head") || n.contains("helm") || n.contains("visor") || n.contains("crown")
        || n.contains("mask") || n.contains("kabuto") || n.contains("hat")) {
      return CosmeticModelMesh.BONE_HEAD;
    }
    boolean right = n.contains("right") || n.endsWith("_r") || n.contains("_r_") || n.startsWith("r_");
    boolean left = n.contains("left") || n.endsWith("_l") || n.contains("_l_") || n.startsWith("l_");
    if (n.contains("boot") || n.contains("foot") || n.contains("feet") || n.contains("shin")
        || n.contains("greave") || n.contains("sabaton") || n.contains("suneate")) {
      return left && !right ? CosmeticModelMesh.BONE_LEFT_BOOT : CosmeticModelMesh.BONE_RIGHT_BOOT;
    }
    if (n.contains("leg") || n.contains("thigh") || n.contains("knee") || n.contains("cuisse")
        || n.contains("haidate")) {
      return left && !right ? CosmeticModelMesh.BONE_LEFT_LEG : CosmeticModelMesh.BONE_RIGHT_LEG;
    }
    if (n.contains("arm") || n.contains("shoulder") || n.contains("pauldron") || n.contains("sleeve")
        || n.contains("gauntlet") || n.contains("hand") || n.contains("sode") || n.contains("kote")) {
      return left && !right ? CosmeticModelMesh.BONE_LEFT_ARM : CosmeticModelMesh.BONE_RIGHT_ARM;
    }
    if (n.contains("body") || n.contains("chest") || n.contains("torso") || n.contains("cuirass")
        || n.contains("back") || n.contains("waist") || n.contains("belt")) {
      return CosmeticModelMesh.BONE_BODY;
    }
    return -1;
  }

  private static float[] computeSmoothNormals(float[] pos, int[] idx, int vcount) {
    float[] nrm = new float[vcount * 3];
    for (int t = 0; t + 2 < idx.length; t += 3) {
      int i0 = idx[t], i1 = idx[t + 1], i2 = idx[t + 2];
      float x0 = pos[i0 * 3], y0 = pos[i0 * 3 + 1], z0 = pos[i0 * 3 + 2];
      float ax = pos[i1 * 3] - x0, ay = pos[i1 * 3 + 1] - y0, az = pos[i1 * 3 + 2] - z0;
      float bx = pos[i2 * 3] - x0, by = pos[i2 * 3 + 1] - y0, bz = pos[i2 * 3 + 2] - z0;
      float cx = ay * bz - az * by;
      float cy = az * bx - ax * bz;
      float cz = ax * by - ay * bx;
      nrm[i0 * 3] += cx; nrm[i0 * 3 + 1] += cy; nrm[i0 * 3 + 2] += cz;
      nrm[i1 * 3] += cx; nrm[i1 * 3 + 1] += cy; nrm[i1 * 3 + 2] += cz;
      nrm[i2 * 3] += cx; nrm[i2 * 3 + 1] += cy; nrm[i2 * 3 + 2] += cz;
    }
    for (int i = 0; i < vcount; i++) {
      float x = nrm[i * 3], y = nrm[i * 3 + 1], z = nrm[i * 3 + 2];
      float l = (float) Math.sqrt(x * x + y * y + z * z);
      if (l > 1e-7f) {
        nrm[i * 3] = x / l; nrm[i * 3 + 1] = y / l; nrm[i * 3 + 2] = z / l;
      } else {
        nrm[i * 3] = 0f; nrm[i * 3 + 1] = 1f; nrm[i * 3 + 2] = 0f;
      }
    }
    return nrm;
  }

  private static float[] mat4Identity() {
    return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
  }

  private static float[] mat4FromTrs(float tx, float ty, float tz, float qx, float qy, float qz,
      float qw, float sx, float sy, float sz) {
    float x2 = qx + qx, y2 = qy + qy, z2 = qz + qz;
    float xx = qx * x2, xy = qx * y2, xz = qx * z2;
    float yy = qy * y2, yz = qy * z2, zz = qz * z2;
    float wx = qw * x2, wy = qw * y2, wz = qw * z2;
    return new float[] {
        (1f - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0f,
        (xy - wz) * sy, (1f - (xx + zz)) * sy, (yz + wx) * sy, 0f,
        (xz + wy) * sz, (yz - wx) * sz, (1f - (xx + yy)) * sz, 0f,
        tx, ty, tz, 1f
    };
  }

  private static float[] mat4Mul(float[] a, float[] b) {
    float[] r = new float[16];
    for (int col = 0; col < 4; col++) {
      float b0 = b[col * 4], b1 = b[col * 4 + 1], b2 = b[col * 4 + 2], b3 = b[col * 4 + 3];
      r[col * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3;
      r[col * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3;
      r[col * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3;
      r[col * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
    }
    return r;
  }

  private static void transformPositions(float[] pos, float[] m) {
    if (isIdentity(m)) return;
    for (int i = 0; i < pos.length; i += 3) {
      float x = pos[i], y = pos[i + 1], z = pos[i + 2];
      pos[i] = m[0] * x + m[4] * y + m[8] * z + m[12];
      pos[i + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
      pos[i + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }
  }

  private static float matrixDeterminant3x3(float[] m) {
    float a00 = m[0], a01 = m[4], a02 = m[8];
    float a10 = m[1], a11 = m[5], a12 = m[9];
    float a20 = m[2], a21 = m[6], a22 = m[10];
    return a00 * (a11 * a22 - a12 * a21)
        - a01 * (a10 * a22 - a12 * a20)
        + a02 * (a10 * a21 - a11 * a20);
  }

  private static void transformNormals(float[] nrm, float[] m) {
    if (isIdentity(m)) return;
    float a00 = m[0], a01 = m[4], a02 = m[8];
    float a10 = m[1], a11 = m[5], a12 = m[9];
    float a20 = m[2], a21 = m[6], a22 = m[10];
    float c00 = a11 * a22 - a12 * a21;
    float c01 = -(a10 * a22 - a12 * a20);
    float c02 = a10 * a21 - a11 * a20;
    float c10 = -(a01 * a22 - a02 * a21);
    float c11 = a00 * a22 - a02 * a20;
    float c12 = -(a00 * a21 - a01 * a20);
    float c20 = a01 * a12 - a02 * a11;
    float c21 = -(a00 * a12 - a02 * a10);
    float c22 = a00 * a11 - a01 * a10;
    float det = a00 * c00 + a01 * c01 + a02 * c02;
    float sign = det < 0f ? -1f : 1f;
    for (int i = 0; i < nrm.length; i += 3) {
      float x = nrm[i], y = nrm[i + 1], z = nrm[i + 2];
      float nx = (c00 * x + c01 * y + c02 * z) * sign;
      float ny = (c10 * x + c11 * y + c12 * z) * sign;
      float nz = (c20 * x + c21 * y + c22 * z) * sign;
      float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (l > 1e-7f) {
        nrm[i] = nx / l; nrm[i + 1] = ny / l; nrm[i + 2] = nz / l;
      }
    }
  }

  private static void transformTangents(float[] tan, float[] m) {
    if (isIdentity(m)) return;
    float a00 = m[0], a01 = m[4], a02 = m[8];
    float a10 = m[1], a11 = m[5], a12 = m[9];
    float a20 = m[2], a21 = m[6], a22 = m[10];
    float det = a00 * (a11 * a22 - a12 * a21)
        - a01 * (a10 * a22 - a12 * a20)
        + a02 * (a10 * a21 - a11 * a20);
    float handednessFlip = det < 0f ? -1f : 1f;
    for (int i = 0; i < tan.length; i += 4) {
      float x = tan[i], y = tan[i + 1], z = tan[i + 2];
      float tx = a00 * x + a01 * y + a02 * z;
      float ty = a10 * x + a11 * y + a12 * z;
      float tz = a20 * x + a21 * y + a22 * z;
      float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
      if (l > 1e-7f) {
        tan[i] = tx / l;
        tan[i + 1] = ty / l;
        tan[i + 2] = tz / l;
      }
      tan[i + 3] *= handednessFlip;
    }
  }

  private static boolean isIdentity(float[] m) {
    return m[0] == 1f && m[5] == 1f && m[10] == 1f && m[15] == 1f
        && m[1] == 0f && m[2] == 0f && m[3] == 0f
        && m[4] == 0f && m[6] == 0f && m[7] == 0f
        && m[8] == 0f && m[9] == 0f && m[11] == 0f
        && m[12] == 0f && m[13] == 0f && m[14] == 0f;
  }

  private static DecodedImg decodeImageBytes(byte[] bytes) {
    try {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
      if (src == null) return null;
      int w = src.getWidth(), h = src.getHeight();
      if (w <= 0 || h <= 0) return null;
      if (w > MAX_TEXTURE_DIM || h > MAX_TEXTURE_DIM) {
        float scale = Math.min(MAX_TEXTURE_DIM / (float) w, MAX_TEXTURE_DIM / (float) h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        src = scaled;
        w = nw;
        h = nh;
      }
      int[] px = new int[w * h];
      src.getRGB(0, 0, w, h, px, 0, w);
      return new DecodedImg(px, w, h);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static int multiplyArgb(int a, int b) {
    int aa = ((a >>> 24) * (b >>> 24)) / 255;
    int rr = (((a >>> 16) & 255) * ((b >>> 16) & 255)) / 255;
    int gg = (((a >>> 8) & 255) * ((b >>> 8) & 255)) / 255;
    int bb = ((a & 255) * (b & 255)) / 255;
    return (aa << 24) | (rr << 16) | (gg << 8) | bb;
  }

  private static float wrap01(float v) {
    if (!Float.isFinite(v)) return 0.5f;
    if (v >= 0f && v <= 1f) return v;
    float r = v - (float) Math.floor(v);
    if (r < 0f) r += 1f;
    return (v > 0f && r == 0f) ? 1f : r;
  }

  private static float clamp01(float v) {
    return Float.isFinite(v) ? (v < 0f ? 0f : (v > 1f ? 1f : v)) : 0f;
  }

  private static String[] splitWs(String line) {
    return line.trim().split("\\s+");
  }

  private static float parseFloat(String s, float fallback) {
    try {
      float v = Float.parseFloat(s);
      return Float.isFinite(v) ? v : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static float[] toFloatArray(List<Float> list) {
    float[] out = new float[list.size()];
    for (int i = 0; i < out.length; i++) out[i] = list.get(i).floatValue();
    return out;
  }

  private static int[] toIntArray(List<Integer> list) {
    int[] out = new int[list.size()];
    for (int i = 0; i < out.length; i++) out[i] = list.get(i).intValue();
    return out;
  }

  private static byte[] readAllBytes(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
    byte[] buf = new byte[8192];
    int n;
    try {
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
      }
    }
    return out.toByteArray();
  }
}

package dev.nightclient.platform;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class MapTextureBackend {
    private static final int CACHE_LIMIT = 1024;
    private static final int HARD_CACHE_LIMIT = 2048;
    private static final int ICON_ATLAS_SIZE = 1024;
    private static final int ICON_ATLAS_PAD = 1;
    private static final int MAX_ICON_ATLAS_PAGES = 4;
    private static long frameSerial = 1L;
    private static final List<Integer> RETIRE_QUEUE = new ArrayList<Integer>();
    private static IntBuffer uploadBuffer;

    public static final class IconSlot {
        public final int texId;
        public final float u0, v0, u1, v1;

        IconSlot(int texId, float u0, float v0, float u1, float v1) {
            this.texId = texId;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }

    private static final class AtlasPage {
        final int texId;
        int cursorX = ICON_ATLAS_PAD;
        int cursorY = ICON_ATLAS_PAD;
        int shelfHeight;

        AtlasPage(int texId) {
            this.texId = texId;
        }
    }

    private static final class AtlasEntry {
        final IconSlot slot;
        final int width;
        final int height;
        final long revision;

        AtlasEntry(IconSlot slot, int width, int height, long revision) {
            this.slot = slot;
            this.width = width;
            this.height = height;
            this.revision = revision;
        }
    }

    private static final class Entry {
        int texId;
        int width;
        int height;
        long revision = Long.MIN_VALUE;
        long lastUsedFrame = 0L;
    }

    private static final Map<String, MapTextureBackend.Entry> CACHE = new LinkedHashMap<String, MapTextureBackend.Entry>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MapTextureBackend.Entry> eldest) {
            if (size() <= HARD_CACHE_LIMIT) return false;
            MapTextureBackend.Entry e = eldest.getValue();
            if (e != null && e.lastUsedFrame >= frameSerial) return false;
            if (e != null && e.texId != 0) {
                RETIRE_QUEUE.add(Integer.valueOf(e.texId));
            }
            return true;
        }
    };

    private static final List<AtlasPage> ICON_ATLAS_PAGES = new ArrayList<AtlasPage>(2);
    private static final Map<String, AtlasEntry> ICON_SLOTS = new HashMap<String, AtlasEntry>(512);

    private MapTextureBackend() {}

    public static void flushRetired() {
        frameSerial++;
        if (!RETIRE_QUEUE.isEmpty()) {
            for (int i = 0; i < RETIRE_QUEUE.size(); i++) {
                int id = RETIRE_QUEUE.get(i).intValue();
                if (id != 0) {
                    try { GL11.glDeleteTextures(id); } catch (Throwable ignored) {}
                }
            }
            RETIRE_QUEUE.clear();
        }
        trimCache(CACHE, CACHE_LIMIT);
    }

    private static void trimCache(Map<String, Entry> cache, int limit) {
        if (cache.size() <= limit) return;
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (cache.size() > limit && it.hasNext()) {
            Entry e = it.next().getValue();
            if (e != null && e.lastUsedFrame >= frameSerial - 1L) break;
            if (e != null && e.texId != 0) {
                try { GL11.glDeleteTextures(e.texId); } catch (Throwable ignored) {}
            }
            it.remove();
        }
    }

    public static void clearMaps() {
        clearMap(CACHE);
    }

    private static void clearIconAtlas() {
        for (int i = 0; i < ICON_ATLAS_PAGES.size(); i++) {
            AtlasPage page = ICON_ATLAS_PAGES.get(i);
            if (page != null && page.texId != 0) {
                try { GL11.glDeleteTextures(page.texId); } catch (Throwable ignored) {}
            }
        }
        ICON_ATLAS_PAGES.clear();
        ICON_SLOTS.clear();
    }

    public static void clear() {
        clearMaps();
        clearIconAtlas();
        for (int i = 0; i < RETIRE_QUEUE.size(); i++) {
            int id = RETIRE_QUEUE.get(i).intValue();
            if (id != 0) {
                try { GL11.glDeleteTextures(id); } catch (Throwable ignored) {}
            }
        }
        RETIRE_QUEUE.clear();
    }

    private static void clearMap(Map<String, Entry> cache) {
        for (Entry e : cache.values()) {
            if (e != null && e.texId != 0) {
                try { GL11.glDeleteTextures(e.texId); } catch (Throwable ignored) {}
            }
        }
        cache.clear();
    }

    public static void close() {
        clear();
        uploadBuffer = null;
    }

    public static int getOrUpload(String key, long revision, int[] pixels, int width, int height) {
        return getOrUploadInternal(CACHE, key == null ? "map" : key, revision, pixels, width, height);
    }

    public static IconSlot getOrPackIcon(String key, long revision, int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) return null;
        if (width + ICON_ATLAS_PAD * 2 > ICON_ATLAS_SIZE || height + ICON_ATLAS_PAD * 2 > ICON_ATLAS_SIZE) {
            int fallback = getOrUploadInternal(CACHE, key == null ? "icon" : key, revision, pixels, width, height);
            return fallback == 0 ? null : new IconSlot(fallback, 0f, 0f, 1f, 1f);
        }
        String safeKey = key == null ? "icon" : key;
        AtlasEntry existing = ICON_SLOTS.get(safeKey);
        if (existing != null && existing.revision == revision && existing.width == width && existing.height == height) {
            return existing.slot;
        }
        AtlasPage page = null;
        int slotX = 0, slotY = 0;
        for (int i = 0; i < ICON_ATLAS_PAGES.size(); i++) {
            AtlasPage candidate = ICON_ATLAS_PAGES.get(i);
            if (candidate.cursorX + width + ICON_ATLAS_PAD <= ICON_ATLAS_SIZE
                && candidate.cursorY + height + ICON_ATLAS_PAD <= ICON_ATLAS_SIZE) {
                page = candidate;
                slotX = candidate.cursorX;
                slotY = candidate.cursorY;
                break;
            }
            int nextY = candidate.cursorY + candidate.shelfHeight + ICON_ATLAS_PAD;
            if (ICON_ATLAS_PAD + width + ICON_ATLAS_PAD <= ICON_ATLAS_SIZE
                && nextY + height + ICON_ATLAS_PAD <= ICON_ATLAS_SIZE) {
                candidate.cursorX = ICON_ATLAS_PAD;
                candidate.cursorY = nextY;
                candidate.shelfHeight = 0;
                page = candidate;
                slotX = candidate.cursorX;
                slotY = candidate.cursorY;
                break;
            }
        }
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LegacyUiRenderer.PixelUnpackState unpack = new LegacyUiRenderer.PixelUnpackState();
        try {
            if (page == null) {
                if (ICON_ATLAS_PAGES.size() >= MAX_ICON_ATLAS_PAGES) clearIconAtlas();
                page = createAtlasPage();
                if (page == null) return null;
                slotX = page.cursorX;
                slotY = page.cursorY;
            }
            page.cursorX = slotX + width + ICON_ATLAS_PAD;
            if (height > page.shelfHeight) page.shelfHeight = height;
            int count = width * height;
            if (uploadBuffer == null || uploadBuffer.capacity() < count) {
                uploadBuffer = BufferUtils.createIntBuffer(count);
            }
            uploadBuffer.clear();
            for (int i = 0; i < count; i++) {
                int c = pixels[i];
                uploadBuffer.put((c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >>> 16) & 0xFF));
            }
            uploadBuffer.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, page.texId);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, slotX, slotY, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, uploadBuffer);
            float inv = 1.0f / (float) ICON_ATLAS_SIZE;
            IconSlot slot = new IconSlot(page.texId, slotX * inv, slotY * inv, (slotX + width) * inv, (slotY + height) * inv);
            ICON_SLOTS.put(safeKey, new AtlasEntry(slot, width, height, revision));
            return slot;
        } finally {
            unpack.restore();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    private static AtlasPage createAtlasPage() {
        int texId = GL11.glGenTextures();
        if (texId == 0) return null;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ICON_ATLAS_SIZE, ICON_ATLAS_SIZE, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        int stripH = 64;
        int stripCount = ICON_ATLAS_SIZE * stripH;
        if (uploadBuffer == null || uploadBuffer.capacity() < stripCount) {
            uploadBuffer = BufferUtils.createIntBuffer(stripCount);
        }
        uploadBuffer.clear();
        for (int i = 0; i < stripCount; i++) uploadBuffer.put(0x00FFFFFF);
        uploadBuffer.flip();
        for (int y = 0; y < ICON_ATLAS_SIZE; y += stripH) {
            uploadBuffer.rewind();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, y, ICON_ATLAS_SIZE, stripH,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, uploadBuffer);
        }
        AtlasPage page = new AtlasPage(texId);
        ICON_ATLAS_PAGES.add(page);
        return page;
    }

    private static int getOrUploadInternal(Map<String, Entry> cache, String safeKey, long revision, int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) return 0;
        Entry entry = cache.get(safeKey);
        if (entry != null && entry.texId != 0
            && entry.revision == revision && entry.width == width && entry.height == height) {
            entry.lastUsedFrame = frameSerial;
            return entry.texId;
        }
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        LegacyUiRenderer.PixelUnpackState unpack = new LegacyUiRenderer.PixelUnpackState();
        try {
            boolean allocated = false;
            if (entry == null) {
                entry = new Entry();
                entry.lastUsedFrame = frameSerial;
                cache.put(safeKey, entry);
            } else {
                entry.lastUsedFrame = frameSerial;
            }
            if (entry.texId == 0) {
                entry.texId = GL11.glGenTextures();
                if (entry.texId == 0) return 0;
                allocated = true;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, entry.texId);
            if (allocated) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
            int count = width * height;
            if (uploadBuffer == null || uploadBuffer.capacity() < count) {
                uploadBuffer = BufferUtils.createIntBuffer(count);
            }
            uploadBuffer.clear();
            for (int i = 0; i < count; i++) {
                int c = pixels[i];
                uploadBuffer.put((c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >>> 16) & 0xFF));
            }
            uploadBuffer.flip();
            if (allocated || entry.width != width || entry.height != height) {
                entry.width = width;
                entry.height = height;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, uploadBuffer);
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, uploadBuffer);
            }
            entry.revision = revision;
            return entry.texId;
        } finally {
            unpack.restore();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }
}

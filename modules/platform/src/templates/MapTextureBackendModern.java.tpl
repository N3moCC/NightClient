package dev.nightclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.@@IDENTIFIER@@;

public final class MapTextureBackend {
    private static final int CACHE_LIMIT = 1024;
    private static final int HARD_CACHE_LIMIT = 2048;
    private static final int ICON_ATLAS_SIZE = 1024;
    private static final int ICON_ATLAS_PAD = 1;
    private static final int MAX_ICON_ATLAS_PAGES = 4;
    private static int sequence = 1;
    private static long frameSerial = 1L;
    private static final List<@@IDENTIFIER@@> RETIRE_QUEUE = new ArrayList<@@IDENTIFIER@@>();

    public static final class IconSlot {
        public final DynamicTexture texture;
        public final float u0, v0, u1, v1;

        IconSlot(DynamicTexture texture, float u0, float v0, float u1, float v1) {
            this.texture = texture;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }

    private static final class AtlasPage {
        final DynamicTexture texture;
        final @@IDENTIFIER@@ id;
        int cursorX = ICON_ATLAS_PAD;
        int cursorY = ICON_ATLAS_PAD;
        int shelfHeight;
        boolean dirty;
        long verifiedFrame = -1L;

        AtlasPage(DynamicTexture texture, @@IDENTIFIER@@ id) {
            this.texture = texture;
            this.id = id;
        }
    }

    private static final class AtlasEntry {
        final AtlasPage page;
        final IconSlot slot;
        final int width;
        final int height;
        final long revision;

        AtlasEntry(AtlasPage page, IconSlot slot, int width, int height, long revision) {
            this.page = page;
            this.slot = slot;
            this.width = width;
            this.height = height;
            this.revision = revision;
        }
    }

    private static final class Entry {
        DynamicTexture texture;
        @@IDENTIFIER@@ id;
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
            if (e != null && e.id != null) {
                RETIRE_QUEUE.add(e.id);
            }
            return true;
        }
    };

    private static final List<AtlasPage> ICON_ATLAS_PAGES = new ArrayList<AtlasPage>(2);
    private static final Map<String, AtlasEntry> ICON_SLOTS = new HashMap<String, AtlasEntry>(512);

    private MapTextureBackend() {}

    public static void flushRetired() {
        frameSerial++;
        Minecraft mc = Minecraft.getInstance();
        if (!RETIRE_QUEUE.isEmpty()) {
            if (mc != null && mc.getTextureManager() != null) {
                for (int i = 0; i < RETIRE_QUEUE.size(); i++) {
                    try { mc.getTextureManager().release(RETIRE_QUEUE.get(i)); } catch (Throwable ignored) {}
                }
            }
            RETIRE_QUEUE.clear();
        }
        trimCache(CACHE, CACHE_LIMIT, mc);
    }

    private static void trimCache(Map<String, Entry> cache, int limit, Minecraft mc) {
        if (cache.size() <= limit) return;
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (cache.size() > limit && it.hasNext()) {
            Entry e = it.next().getValue();
            if (e != null && e.lastUsedFrame >= frameSerial - 1L) break;
            if (e != null && e.id != null && mc != null && mc.getTextureManager() != null) {
                try { mc.getTextureManager().release(e.id); } catch (Throwable ignored) {}
            }
            it.remove();
        }
    }

    public static void clearMaps() {
        for (Entry e : CACHE.values()) {
            if (e != null && e.id != null) RETIRE_QUEUE.add(e.id);
        }
        CACHE.clear();
    }

    private static void clearIconAtlas() {
        for (int i = 0; i < ICON_ATLAS_PAGES.size(); i++) {
            AtlasPage page = ICON_ATLAS_PAGES.get(i);
            if (page != null && page.id != null) RETIRE_QUEUE.add(page.id);
        }
        ICON_ATLAS_PAGES.clear();
        ICON_SLOTS.clear();
    }

    public static void clear() {
        clearMaps();
        clearIconAtlas();
    }

    public static void close() {
        clear();
        flushRetired();
    }

    public static DynamicTexture getOrUpload(String key, long revision, int[] pixels, int width, int height) {
        return getOrUploadInternal(CACHE, "nightclient:map_tile_", key == null ? "map" : key, revision, pixels, width, height);
    }

    public static IconSlot getOrPackIcon(String key, long revision, int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) return null;
        if (width + ICON_ATLAS_PAD * 2 > ICON_ATLAS_SIZE || height + ICON_ATLAS_PAD * 2 > ICON_ATLAS_SIZE) {
            DynamicTexture fallback = getOrUploadInternal(CACHE, "nightclient:svg_icon_", key == null ? "icon" : key, revision, pixels, width, height);
            return fallback == null ? null : new IconSlot(fallback, 0f, 0f, 1f, 1f);
        }
        String safeKey = key == null ? "icon" : key;
        AtlasEntry existing = ICON_SLOTS.get(safeKey);
        if (existing != null && existing.revision == revision && existing.width == width && existing.height == height) {
            if (isPageAlive(existing.page)) return existing.slot;
            clearIconAtlas();
        }
        AtlasPage page = null;
        int slotX = 0, slotY = 0;
        for (int i = 0; i < ICON_ATLAS_PAGES.size(); i++) {
            AtlasPage candidate = ICON_ATLAS_PAGES.get(i);
            if (!isPageAlive(candidate)) {
                clearIconAtlas();
                break;
            }
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
        if (page == null) {
            if (ICON_ATLAS_PAGES.size() >= MAX_ICON_ATLAS_PAGES) clearIconAtlas();
            page = createAtlasPage();
            slotX = page.cursorX;
            slotY = page.cursorY;
        }
        page.cursorX = slotX + width + ICON_ATLAS_PAD;
        if (height > page.shelfHeight) page.shelfHeight = height;
        NativeImage image = page.texture.getPixels();
        if (image != null && image.getWidth() == ICON_ATLAS_SIZE && image.getHeight() == ICON_ATLAS_SIZE) {
            for (int y = 0; y < height; y++) {
                int row = y * width;
                int dy = slotY + y;
                for (int x = 0; x < width; x++) {
                    image.setPixel(slotX + x, dy, pixels[row + x]);
                }
            }
            page.dirty = true;
        }
        float inv = 1.0f / (float) ICON_ATLAS_SIZE;
        IconSlot slot = new IconSlot(page.texture, slotX * inv, slotY * inv, (slotX + width) * inv, (slotY + height) * inv);
        ICON_SLOTS.put(safeKey, new AtlasEntry(page, slot, width, height, revision));
        return slot;
    }

    public static void flushIconAtlas() {
        for (int i = 0; i < ICON_ATLAS_PAGES.size(); i++) {
            AtlasPage page = ICON_ATLAS_PAGES.get(i);
            if (page.dirty) {
                page.dirty = false;
                try { page.texture.upload(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean isPageAlive(AtlasPage page) {
        if (page == null || page.texture == null || page.id == null) return false;
        if (page.verifiedFrame == frameSerial) return true;
        try {
            if (Minecraft.getInstance().getTextureManager().getTexture(page.id) == page.texture) {
                page.verifiedFrame = frameSerial;
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static AtlasPage createAtlasPage() {
        String name = "nightclient:svg_icon_atlas_" + (sequence++);
        DynamicTexture texture = new DynamicTexture(name, ICON_ATLAS_SIZE, ICON_ATLAS_SIZE, false);
        NativeImage image = texture.getPixels();
        if (image != null) {
            for (int y = 0; y < ICON_ATLAS_SIZE; y++) {
                for (int x = 0; x < ICON_ATLAS_SIZE; x++) {
                    image.setPixel(x, y, 0x00FFFFFF);
                }
            }
        }
        @@IDENTIFIER@@ id = GameBridge.identifier(name);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        AtlasPage page = new AtlasPage(texture, id);
        page.verifiedFrame = frameSerial;
        page.dirty = true;
        ICON_ATLAS_PAGES.add(page);
        return page;
    }

    private static DynamicTexture getOrUploadInternal(Map<String, Entry> cache, String prefix, String safeKey, long revision, int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) return null;
        Entry entry = cache.get(safeKey);
        if (entry != null && entry.texture != null && entry.id != null) {
            try {
                if (Minecraft.getInstance().getTextureManager().getTexture(entry.id) == entry.texture
                    && entry.revision == revision && entry.width == width && entry.height == height) {
                    entry.lastUsedFrame = frameSerial;
                    return entry.texture;
                }
            } catch (Throwable ignored) {
                entry.texture = null;
            }
        }
        if (entry == null) {
            entry = new Entry();
            entry.lastUsedFrame = frameSerial;
            cache.put(safeKey, entry);
        } else {
            entry.lastUsedFrame = frameSerial;
        }
        boolean alive = false;
        if (entry.texture != null && entry.id != null && entry.width == width && entry.height == height) {
            try {
                alive = Minecraft.getInstance().getTextureManager().getTexture(entry.id) == entry.texture;
            } catch (Throwable ignored) {}
        }
        if (!alive) {
            if (entry.id != null) {
                RETIRE_QUEUE.add(entry.id);
            }
            String name = prefix + (sequence++);
            entry.texture = new DynamicTexture(name, width, height, false);
            entry.id = GameBridge.identifier(name);
            entry.width = width;
            entry.height = height;
            Minecraft.getInstance().getTextureManager().register(entry.id, entry.texture);
        }
        NativeImage image = entry.texture.getPixels();
        if (image == null || image.getWidth() != width || image.getHeight() != height) return entry.texture;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, pixels[row + x]);
            }
        }
        entry.texture.upload();
        entry.revision = revision;
        return entry.texture;
    }
}

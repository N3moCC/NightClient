package dev.nightclient.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FeaturePolicy {
  public static final String CLIENT_ID = "nightclient";
  public static final String CHANNEL = "liteapi:feature-control";

  public static final List<String> REGISTERED_FEATURES = Collections.unmodifiableList(Arrays.asList(
      "saturation",
      "hitColor",
      "noRender",
      "lowFire",
      "enchantGlint",
      "wavyCapes",
      "tooltips",
      "optimization",
      "motionBlur",
      "tabAnimation",
      "crosshair",
      "zoom",
      "freelook",
      "aspectRatio",
      "swing",
      "viewmodel",
      "protect",
      "hwhelper",
      "hwhelper.aucprice",
      "hwhelper.answap",
      "hwhelper.betterSell",
      "hwhelper.events",
      "hwhelper.timers",
      "hwhelper.zones",
      "hwhelper.compassWaypoint",
      "safeCtLeave",
      "itemScroller",
      "tapeMouse",
      "soundControl",
      "effectAlert",
      "tweaksy",
      "tweaksy.mainLeftFix",
      "tweaksy.commandFix",
      "tweaksy.cursorFix",
      "tweaksy.fullBright",
      "tweaksy.ownNametag",
      "tweaksy.invisibleArmorNametag",
      "accountSwitcher",
      "atmosphere",
      "customSky",
      "worldMap",
      "worldMap.players",
      "worldMap.invisibles",
      "worldMap.items",
      "worldMap.mobs",
      "waypoints",
      "playerHealth",
      "playerHealth.invisibles",
      "totemCounter",
      "totemCounter.invisibles",
      "playerPing",
      "playerPing.invisibles",
      "damageSplash",
      "explosionRing",
      "hitbox",
      "blockOverlay",
      "itemPhysics",
      "target",
      "hud",
      "tnt",
      "armor",
      "potions",
      "inventory",
      "food",
      "minimap",
      "minimap.players",
      "minimap.invisibles",
      "minimap.items",
      "minimap.mobs",
      "minimap.explosives",
      "betterChat",
      "scoreboard",
      "keystrokes",
      "coordinates",
      "ping",
      "fps"));

  private static final Map<String, String> ALIASES = createAliases();

  private static volatile Set<String> blocked = Collections.emptySet();
  private static volatile long revision = 0L;

  private FeaturePolicy() {}

  private static Map<String, String> createAliases() {
    Map<String, String> m = new HashMap<String, String>();
    m.put("cooldowns", "hud");
    m.put("fullbright", "tweaksy.fullbright");
    m.put("hwhelper.esp", "hwhelper.zones");
    m.put("hitboxes", "hitbox");
    m.put("tnttimer", "tnt");
    m.put("targethud", "target");
    m.put("damageindicator", "damagesplash");
    m.put("tweaks", "tweaksy");
    m.put("holyworldhelper", "hwhelper");
    m.put("answap", "hwhelper.answap");
    m.put("aucprice", "hwhelper.aucprice");
    m.put("bettersell", "hwhelper.bettersell");
    return Collections.unmodifiableMap(m);
  }

  private static String normalize(String id) {
    if (id == null) return "";
    String lower = id.trim().toLowerCase(Locale.ROOT);
    if (lower.isEmpty()) return "";
    String alias = ALIASES.get(lower);
    return alias != null ? alias : lower;
  }

  public static long revision() {
    return revision;
  }

  public static boolean hasBlocked() {
    return !blocked.isEmpty();
  }

  public static Set<String> blocked() {
    return blocked;
  }

  public static synchronized boolean setBlocked(Collection<String> features) {
    Set<String> next;
    if (features == null || features.isEmpty()) {
      next = Collections.emptySet();
    } else {
      Set<String> copy = new HashSet<String>(features.size() * 2);
      for (String raw : features) {
        String norm = normalize(raw);
        if (!norm.isEmpty()) copy.add(norm);
      }
      next = copy.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(copy);
    }
    if (next.equals(blocked)) return false;
    blocked = next;
    revision++;
    return true;
  }

  public static boolean clear() {
    return setBlocked(Collections.<String>emptyList());
  }

  public static boolean isBlocked(String featureId) {
    Set<String> set = blocked;
    if (set.isEmpty() || featureId == null || featureId.isEmpty()) return false;
    String norm = normalize(featureId);
    if (norm.isEmpty()) return false;
    if (set.contains(norm)) return true;
    int dot = norm.indexOf('.');
    if (dot > 0 && set.contains(norm.substring(0, dot))) return true;
    return false;
  }

  public static boolean isWidgetBlocked(String widgetId) {
    if (blocked.isEmpty() || widgetId == null || widgetId.isEmpty()) return false;
    if ("cooldowns".equals(widgetId)) return isBlocked("hud");
    return isBlocked(widgetId);
  }

  public static boolean isControlBlocked(String bindPath) {
    if (blocked.isEmpty() || bindPath == null || bindPath.isEmpty()) return false;
    String feature = featureForBind(bindPath);
    return feature != null && isBlocked(feature);
  }

  public static String featureForBind(String bindPath) {
    if (bindPath == null || bindPath.isEmpty()) return null;
    int dot = bindPath.indexOf('.');
    String root = dot > 0 ? bindPath.substring(0, dot) : bindPath;
    if ("hwhelper".equals(root)) {
      if (bindPath.startsWith("hwhelper.aucprice")) return "hwhelper.aucprice";
      if (bindPath.startsWith("hwhelper.answap")) return "hwhelper.answap";
      if (bindPath.startsWith("hwhelper.betterSell")) return "hwhelper.betterSell";
      if (bindPath.startsWith("hwhelper.events")) return "hwhelper.events";
      if (bindPath.startsWith("hwhelper.timers")) return "hwhelper.timers";
      if (bindPath.startsWith("hwhelper.esp")) return "hwhelper.zones";
      if (bindPath.startsWith("hwhelper.compassWaypoint")) return "hwhelper.compassWaypoint";
      return "hwhelper";
    }
    if ("tweaksy".equals(root)) {
      if (bindPath.startsWith("tweaksy.mainLeftFix")) return "tweaksy.mainLeftFix";
      if (bindPath.startsWith("tweaksy.commandFix")) return "tweaksy.commandFix";
      if (bindPath.startsWith("tweaksy.cursorFix")) return "tweaksy.cursorFix";
      if (bindPath.startsWith("tweaksy.fullBright")) return "tweaksy.fullBright";
      if (bindPath.startsWith("tweaksy.ownNametag")) return "tweaksy.ownNametag";
      if (bindPath.startsWith("tweaksy.invisibleArmorNametag")) {
        return "tweaksy.invisibleArmorNametag";
      }
      return "tweaksy";
    }
    if ("minimap".equals(root)) {
      if ("minimap.showInvisiblePlayers".equals(bindPath)) return "minimap.invisibles";
      if ("minimap.showPlayers".equals(bindPath) || "minimap.showPlayerHeads".equals(bindPath)
          || "minimap.showPlayerNames".equals(bindPath) || "minimap.playerScale".equals(bindPath)) {
        return "minimap.players";
      }
      if ("minimap.showItems".equals(bindPath) || "minimap.itemStyle".equals(bindPath)
          || "minimap.itemBackground".equals(bindPath)
          || "minimap.onlyValuableItems".equals(bindPath)
          || "minimap.itemScale".equals(bindPath)) {
        return "minimap.items";
      }
      if ("minimap.mobIconStyle".equals(bindPath) || "minimap.mobScale".equals(bindPath)
          || "minimap.showHostileMobs".equals(bindPath)
          || "minimap.showPassiveMobs".equals(bindPath)
          || "minimap.showNeutralMobs".equals(bindPath)
          || "minimap.showOtherEntities".equals(bindPath)
          || "minimap.showMobNames".equals(bindPath)) {
        return "minimap.mobs";
      }
      if ("minimap.showExplosives".equals(bindPath)) return "minimap.explosives";
      return "minimap";
    }
    if ("worldMap".equals(root)) {
      if ("worldMap.showInvisiblePlayers".equals(bindPath)) return "worldMap.invisibles";
      if ("worldMap.showPlayers".equals(bindPath)) return "worldMap.players";
      if ("worldMap.showItems".equals(bindPath)) return "worldMap.items";
      if ("worldMap.showMobs".equals(bindPath) || "worldMap.showOtherEntities".equals(bindPath)) {
        return "worldMap.mobs";
      }
      return "worldMap";
    }
    if ("playerHealth.showInvisible".equals(bindPath)) return "playerHealth.invisibles";
    if ("totemCounter.showInvisible".equals(bindPath)) return "totemCounter.invisibles";
    if ("playerPing.showInvisible".equals(bindPath)) return "playerPing.invisibles";
    if ("noRender.fire".equals(bindPath) || "noRender.hideFire".equals(bindPath)
        || "noRender.fireOffset".equals(bindPath)) {
      return isBlocked("lowFire") ? "lowFire" : "noRender";
    }
    if ("menu".equals(root) || "theme".equals(root) || "skin".equals(root)
        || "cosmetics".equals(root) || "language".equals(root) || "interfaceState".equals(root)) {
      return null;
    }
    return root;
  }

  public static boolean isActionBlocked(String action) {
    if (blocked.isEmpty() || action == null || action.isEmpty()) return false;
    if ("page:waypoints".equals(action) || "waypoints-menu-open".equals(action)
        || "waypoint-create".equals(action)) {
      return isBlocked("waypoints");
    }
    if ("page:events".equals(action) || "hw-events-refresh".equals(action)) {
      return isBlocked("hwhelper.events");
    }
    if ("world-map-open".equals(action)) return isBlocked("worldMap");
    if ("map-settings-open".equals(action)) {
      return isBlocked("minimap") && isBlocked("worldMap");
    }
    if ("editor-crosshair".equals(action)) return isBlocked("crosshair");
    if (action.startsWith("bind-module:")) {
      return isBlocked(action.substring("bind-module:".length()));
    }
    if (action.startsWith("editor-target:")) {
      return isWidgetBlocked(action.substring("editor-target:".length()));
    }
    if (action.startsWith("bind-key:")) {
      String key = action.substring("bind-key:".length());
      if ("zoom".equals(key)) return isBlocked("zoom");
      if ("freelook".equals(key)) return isBlocked("freelook");
      if ("waypoint".equals(key) || "waypointsMenu".equals(key)) return isBlocked("waypoints");
      if ("worldMap".equals(key)) return isBlocked("worldMap");
      if ("mapEnlarge".equals(key)) return isBlocked("minimap");
      if ("mapSettings".equals(key)) return isBlocked("minimap") && isBlocked("worldMap");
      if ("tapeMouseKey".equals(key)) return isBlocked("tapeMouse");
    }
    return false;
  }
}

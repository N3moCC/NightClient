package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.config.FeaturePolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiteApiManagerTest {
  @BeforeEach
  void resetBefore() {
    FeaturePolicy.clear();
  }

  @AfterEach
  void resetAfter() {
    FeaturePolicy.clear();
  }

  @Test
  void joinSendsExactProtocolEnvelopeAndAppliesBlocklist() {
    List<String> channels = new ArrayList<String>();
    List<String> payloads = new ArrayList<String>();
    AtomicInteger policyChanges = new AtomicInteger();

    LiteApiManager mgr = new LiteApiManager((channel, body) -> {
      channels.add(channel);
      payloads.add(new String(body, StandardCharsets.UTF_8));
      return true;
    }, policyChanges::incrementAndGet);

    mgr.onServerJoin(true, 1000L);
    assertEquals(1, channels.size());
    assertEquals("liteapi:feature-control", channels.get(0));

    JsonObject req = new JsonParser().parse(payloads.get(0)).getAsJsonObject();
    assertTrue(req.has("id") && !req.get("id").getAsString().isEmpty());
    assertEquals("checkFeatures", req.get("method").getAsString());
    JsonObject payload = req.getAsJsonObject("payload");
    assertEquals("nightclient", payload.get("client").getAsString());
    JsonArray features = payload.getAsJsonArray("features");
    assertEquals(FeaturePolicy.REGISTERED_FEATURES.size(), features.size());

    String id = req.get("id").getAsString();
    String response = "{\"id\":\"" + id + "\",\"ok\":true,\"payload\":{\"blocklist\":[\"tapeMouse\",\"minimap.invisibles\",\"fullbright\"]}}";
    mgr.onIncomingPayload(response.getBytes(StandardCharsets.UTF_8), 1100L);

    assertEquals(1, policyChanges.get());
    assertTrue(FeaturePolicy.isBlocked("tapeMouse"));
    assertTrue(FeaturePolicy.isBlocked("minimap.invisibles"));
    assertFalse(FeaturePolicy.isBlocked("minimap"));
    assertTrue(FeaturePolicy.isBlocked("tweaksy.fullBright"));
    assertTrue(FeaturePolicy.isControlBlocked("minimap.showInvisiblePlayers"));
    assertFalse(FeaturePolicy.isControlBlocked("minimap.showPlayers"));
    assertTrue(FeaturePolicy.isActionBlocked("bind-key:tapeMouseKey"));

    mgr.onDisconnect();
    assertFalse(FeaturePolicy.hasBlocked());
    assertFalse(FeaturePolicy.isBlocked("tapeMouse"));
    assertEquals(2, policyChanges.get());
  }

  @Test
  void enforcesRateLimitAcrossRapidSubserverSwitchesAndRateLimitedError() {
    List<String> payloads = new ArrayList<String>();
    LiteApiManager mgr = new LiteApiManager((ch, body) -> {
      payloads.add(new String(body, StandardCharsets.UTF_8));
      return true;
    }, null);

    mgr.onServerJoin(true, 10_000L);
    assertEquals(1, payloads.size());

    mgr.onServerJoin(true, 13_000L);
    assertEquals(1, payloads.size(), "Must not exceed 1 req / 10s rate limit");

    mgr.tick(20_000L);
    assertEquals(1, payloads.size());

    mgr.tick(20_500L);
    assertEquals(2, payloads.size(), "Deferred request fires after interval elapses");

    String rateLimited = "{\"id\":\"x\",\"ok\":false,\"error\":\"RATE_LIMITED\",\"message\":\"Slow down\"}";
    mgr.onIncomingPayload(rateLimited.getBytes(StandardCharsets.UTF_8), 21_000L);
    mgr.tick(31_000L);
    assertEquals(2, payloads.size());
    mgr.tick(32_000L);
    assertEquals(3, payloads.size());
  }

  @Test
  void pushEventUpdatesBlocklistAndSingleplayerClearsIt() {
    LiteApiManager mgr = new LiteApiManager((ch, body) -> true, null);
    mgr.onServerJoin(true, 1_000L);

    String push = "{\"event\":\"features.updated\",\"payload\":{\"blocklist\":[\"hwhelper\",\"waypoints\"]}}";
    mgr.onIncomingPayload(push.getBytes(StandardCharsets.UTF_8), 2_000L);

    assertTrue(FeaturePolicy.isBlocked("hwhelper"));
    assertTrue(FeaturePolicy.isBlocked("hwhelper.events"));
    assertTrue(FeaturePolicy.isBlocked("hwhelper.zones"));
    assertTrue(FeaturePolicy.isBlocked("waypoints"));
    assertTrue(FeaturePolicy.isActionBlocked("page:waypoints"));
    assertTrue(FeaturePolicy.isActionBlocked("page:events"));

    mgr.onServerJoin(false, 5_000L);
    assertFalse(FeaturePolicy.hasBlocked());
    assertFalse(FeaturePolicy.isBlocked("hwhelper"));
    assertFalse(FeaturePolicy.isBlocked("waypoints"));
  }
}

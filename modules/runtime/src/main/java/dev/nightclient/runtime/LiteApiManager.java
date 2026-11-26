package dev.nightclient.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightclient.config.FeaturePolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LiteApiManager {
  public static final long MIN_REQUEST_INTERVAL_MS = 10_500L;
  public static final long REQUEST_TIMEOUT_MS = 5_000L;
  public static final long RATE_LIMIT_RETRY_MS = 11_000L;

  public interface Sender {
    boolean send(String channel, byte[] payload);
  }

  private final Sender sender;
  private final Runnable onPolicyChanged;
  private boolean inMultiplayer;
  private long lastRequestSentMs;
  private String pendingRequestId;
  private long pendingRequestSentMs;
  private long nextRetryAtMs;

  public LiteApiManager(Sender sender, Runnable onPolicyChanged) {
    this.sender = sender;
    this.onPolicyChanged = onPolicyChanged;
  }

  public synchronized void onServerJoin(boolean remoteServer, long nowMs) {
    pendingRequestId = null;
    pendingRequestSentMs = 0L;
    nextRetryAtMs = 0L;
    if (!remoteServer) {
      inMultiplayer = false;
      if (FeaturePolicy.clear() && onPolicyChanged != null) {
        onPolicyChanged.run();
      }
      return;
    }
    inMultiplayer = true;
    long elapsed = nowMs - lastRequestSentMs;
    if (lastRequestSentMs == 0L || elapsed >= MIN_REQUEST_INTERVAL_MS) {
      sendCheckFeaturesLocked(nowMs);
    } else {
      nextRetryAtMs = lastRequestSentMs + MIN_REQUEST_INTERVAL_MS;
    }
  }

  public synchronized void onDisconnect() {
    inMultiplayer = false;
    pendingRequestId = null;
    pendingRequestSentMs = 0L;
    nextRetryAtMs = 0L;
    if (FeaturePolicy.clear() && onPolicyChanged != null) {
      onPolicyChanged.run();
    }
  }

  public synchronized void tick(long nowMs) {
    if (!inMultiplayer) return;
    if (pendingRequestId != null && nowMs - pendingRequestSentMs >= REQUEST_TIMEOUT_MS) {
      pendingRequestId = null;
      pendingRequestSentMs = 0L;
    }
    if (nextRetryAtMs > 0L && nowMs >= nextRetryAtMs) {
      long elapsed = nowMs - lastRequestSentMs;
      if (lastRequestSentMs == 0L || elapsed >= MIN_REQUEST_INTERVAL_MS) {
        nextRetryAtMs = 0L;
        sendCheckFeaturesLocked(nowMs);
      } else {
        nextRetryAtMs = lastRequestSentMs + MIN_REQUEST_INTERVAL_MS;
      }
    }
  }

  public synchronized void onIncomingPayload(byte[] data, long nowMs) {
    if (data == null || data.length == 0) return;
    String json = new String(data, StandardCharsets.UTF_8).trim();
    if (json.isEmpty()) return;
    JsonElement rootElem;
    try {
      rootElem = new JsonParser().parse(json);
    } catch (Throwable ignored) {
      return;
    }
    if (rootElem == null || !rootElem.isJsonObject()) return;
    JsonObject root = rootElem.getAsJsonObject();

    if (root.has("event") && root.get("event").isJsonPrimitive()) {
      if (root.has("payload") && root.get("payload").isJsonObject()) {
        applyBlocklistFromPayload(root.getAsJsonObject("payload"));
      }
      return;
    }

    if (!root.has("ok") || !root.get("ok").isJsonPrimitive()) return;
    String id = root.has("id") && root.get("id").isJsonPrimitive()
        ? root.get("id").getAsString()
        : null;
    if (pendingRequestId != null && id != null && pendingRequestId.equals(id)) {
      pendingRequestId = null;
      pendingRequestSentMs = 0L;
    }

    boolean ok = root.get("ok").getAsBoolean();
    if (ok) {
      if (root.has("payload") && root.get("payload").isJsonObject()) {
        applyBlocklistFromPayload(root.getAsJsonObject("payload"));
      }
      return;
    }

    String error = root.has("error") && root.get("error").isJsonPrimitive()
        ? root.get("error").getAsString()
        : "";
    if ("RATE_LIMITED".equals(error) && inMultiplayer) {
      nextRetryAtMs = Math.max(nextRetryAtMs, nowMs + RATE_LIMIT_RETRY_MS);
    }
  }

  private void applyBlocklistFromPayload(JsonObject payload) {
    if (payload == null || !payload.has("blocklist") || !payload.get("blocklist").isJsonArray()) {
      return;
    }
    JsonArray arr = payload.getAsJsonArray("blocklist");
    List<String> list = new ArrayList<String>(arr.size());
    for (int i = 0; i < arr.size(); i++) {
      JsonElement el = arr.get(i);
      if (el != null && el.isJsonPrimitive()) {
        list.add(el.getAsString());
      }
    }
    if (FeaturePolicy.setBlocked(list) && onPolicyChanged != null) {
      onPolicyChanged.run();
    }
  }

  private void sendCheckFeaturesLocked(long nowMs) {
    if (sender == null) return;
    String id = UUID.randomUUID().toString();
    byte[] body = encodeCheckFeaturesRequest(id, FeaturePolicy.CLIENT_ID, FeaturePolicy.REGISTERED_FEATURES);
    if (sender.send(FeaturePolicy.CHANNEL, body)) {
      lastRequestSentMs = nowMs;
      pendingRequestId = id;
      pendingRequestSentMs = nowMs;
    }
  }

  public static byte[] encodeCheckFeaturesRequest(String id, String clientId, List<String> features) {
    JsonArray arr = new JsonArray();
    if (features != null) {
      for (int i = 0; i < features.size(); i++) {
        String f = features.get(i);
        if (f != null && !f.isEmpty()) arr.add(f);
      }
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("client", clientId == null ? FeaturePolicy.CLIENT_ID : clientId);
    payload.add("features", arr);

    JsonObject envelope = new JsonObject();
    envelope.addProperty("id", id == null ? UUID.randomUUID().toString() : id);
    envelope.addProperty("method", "checkFeatures");
    envelope.add("payload", payload);
    return envelope.toString().getBytes(StandardCharsets.UTF_8);
  }
}

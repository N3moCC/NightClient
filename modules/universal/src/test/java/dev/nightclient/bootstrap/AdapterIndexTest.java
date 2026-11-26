package dev.nightclient.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AdapterIndexTest {
  static final String ID = "mc1_20";

  static String indexText() {
    return indexText(ID);
  }

  static String indexText(String id) {
    StringBuilder text = new StringBuilder("schemaVersion=1\nmodVersion=0.2.0\nversions=");
    text.append(String.join(",", AdapterIndex.supportedVersions().keySet())).append('\n');
    for (Map.Entry<String, Integer> version : AdapterIndex.supportedVersions().entrySet()) {
      String prefix = "version." + version.getKey() + ".";
      text.append(prefix).append("adapter=").append(id).append('\n');
      text.append(prefix)
          .append("entrypoint=dev.nightclient.adapter.")
          .append(id)
          .append(".platform.NightClientMod\n");
      text.append(prefix)
          .append("mixinPackage=dev.nightclient.universal.mixin.")
          .append(id)
          .append('\n');
      text.append(prefix).append("mixins=FrameMixin,InputMixin\n");
      text.append(prefix)
          .append("compatibilityLevel=JAVA_")
          .append(version.getValue())
          .append('\n');
    }
    return text.toString();
  }

  static AdapterIndex read(String text) throws IOException {
    return AdapterIndex.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
  }

  @Test
  void everyStableReleaseHasItsExactJavaFloorAndOnlyItsSelectedMixinNames() throws Exception {
    AdapterIndex index = read(indexText());
    assertEquals(24, AdapterIndex.supportedVersions().size());
    for (Map.Entry<String, Integer> entry : AdapterIndex.supportedVersions().entrySet()) {
      AdapterIndex.Adapter adapter = index.select(entry.getKey(), entry.getValue());
      assertEquals(entry.getKey(), adapter.minecraft);
      assertEquals("JAVA_" + entry.getValue(), adapter.compatibilityLevel);
      assertEquals(ID + ".FrameMixin", adapter.mixins.get(0));
      assertThrows(UnsupportedOperationException.class, () -> adapter.mixins.add("OtherMixin"));
      assertThrows(
          IllegalStateException.class, () -> index.select(entry.getKey(), entry.getValue() - 1));
    }
  }

  @Test
  void snapshotsLegacyVersionsFuturePatchesAndFriendlyVersionAliasesAreRejected() throws Exception {
    AdapterIndex index = read(indexText());
    for (String version : new String[] {"1.16.5", "1.19.4", "1.21.12", "26.3.1", "26.4",
             "26.3-snapshot-1", "26.3-pre-1", "26.3-rc-1", "Minecraft 26.3", "26.3 ", ""}) {
      assertThrows(IllegalStateException.class, () -> index.select(version, 25), version);
    }
  }

  @Test
  void selectedJavaSeventeenAdapterDoesNotRequireJavaTwentyFive() throws Exception {
    AdapterIndex index = read(indexText());
    assertEquals(17, index.select("1.20", 17).javaFeature);
    assertEquals(25, index.select("26.3", 25).javaFeature);
    assertEquals(index.select("26.3", 25), index.select("26.3", 26));
  }

  @Test
  void incompleteOrDuplicatedIndexCannotSilentlyFallBack() {
    assertThrows(IllegalStateException.class, () -> AdapterIndex.read(null));
    assertThrows(IllegalStateException.class,
        () -> read(indexText().replace("versions=1.20,", "versions=")));
    assertThrows(IllegalStateException.class,
        () -> read(indexText().replace("versions=1.20,", "versions=1.20,1.20,")));
    assertThrows(IllegalStateException.class, () -> read(indexText() + "schemaVersion=1\n"));
    assertThrows(IllegalStateException.class,
        () -> read(indexText().replace("schemaVersion=1", "schemaVersion=2")));
    assertThrows(IllegalStateException.class, () -> read(indexText() + "ignoredProperty=true\n"));
  }

  @Test
  void namespacesAndMixinListsCannotEscapeSelectedAdapter() {
    for (String malformed :
        new String[] {indexText().replaceFirst(
                          "entrypoint=dev.nightclient.adapter", "entrypoint=other.adapter"),
            indexText().replaceFirst("mixinPackage=dev.nightclient.universal.mixin",
                "mixinPackage=dev.nightclient.adapter"),
            indexText().replaceFirst("mixins=FrameMixin,InputMixin", "mixins=../OtherMixin"),
            indexText().replaceFirst("mixins=FrameMixin,InputMixin", "mixins=other.OtherMixin"),
            indexText().replaceFirst(
                "mixins=FrameMixin,InputMixin", "mixins=FrameMixin,FrameMixin"),
            indexText().replaceFirst("compatibilityLevel=JAVA_17", "compatibilityLevel=JAVA_25")})
      assertThrows(IllegalStateException.class, () -> read(malformed));
  }

  @Test
  void adapterIdsAreReadableReleaseNamesWithoutGeneratedGarbage() throws Exception {
    read(indexText());
    for (String alias : new String[] {"mc1_21_6", "mc26_3"}) {
      read(indexText(alias));
    }
    for (String malformed : new String[] {
             "a0123456789ab", "m26_3", "mc9_99_9", "mc1_16_5", "mc1_20.shared", "MC1_20", "1_20",
             ""}) {
      String variant = indexText(malformed);
      assertThrows(IllegalStateException.class, () -> read(variant), malformed);
    }
  }

  @Test
  void javaSpecificationParserRequiresJavaSeventeenOrNewer() {
    assertEquals(17, AdapterIndex.javaFeature("17"));
    assertEquals(21, AdapterIndex.javaFeature("21"));
    assertEquals(25, AdapterIndex.javaFeature("25"));
    for (String version :
        new String[] {"1.8", "16", "17.0.2", "25-ea", "", "999999999999999999999"}) {
      assertThrows(IllegalStateException.class, () -> AdapterIndex.javaFeature(version));
    }
  }
}

package dev.nightclient.cosmetics;

import java.util.Collections;
import java.util.List;

final class OfficialCapesCatalog {
  private OfficialCapesCatalog() {}

  static boolean enabled() {
    return false;
  }

  static String texturePrefix() {
    return "";
  }

  static List<OfficialCapes.Cape> createCatalog() {
    return Collections.emptyList();
  }
}

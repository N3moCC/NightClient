package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.*;

import dev.nightclient.config.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HwHelperManagerTest {
  @Test
  public void testRateCalculation() {
    List<Long> trades = new ArrayList<Long>();
    for (int i = 0; i < 30; i++) {
      trades.add(100_000L + i * 1_000L);
    }
    long rate = HwHelperManager.calculateRate(trades);
    assertTrue(rate >= 100_000L && rate <= 130_000L);
    assertEquals(0, rate % 1000);
  }

  @Test
  public void testCompact() {
    assertEquals("100K", HwHelperManager.compact(100_000L));
    assertEquals("1.25M", HwHelperManager.compact(1_250_000L));
    assertEquals("2.00B", HwHelperManager.compact(2_000_000_000L));
  }

  @Test
  public void testAnSwapCategoriesAndKeys() {
    assertEquals("solo", HwHelperManager.catOf(1));
    assertEquals("solo", HwHelperManager.catOf(15));
    assertEquals("duo", HwHelperManager.catOf(16));
    assertEquals("duo", HwHelperManager.catOf(31));
    assertEquals("trio", HwHelperManager.catOf(32));
    assertEquals("trio", HwHelperManager.catOf(47));
    assertEquals("clans", HwHelperManager.catOf(48));
    assertEquals("clans", HwHelperManager.catOf(66));

    assertEquals("lanarchy", HwHelperManager.keyOf(1));
    assertEquals("lanarchy10", HwHelperManager.keyOf(10));
    assertEquals("lanarchy36", HwHelperManager.keyOf(36));

    assertEquals("СолоЛайт", HwHelperManager.catName(10));
    assertEquals("ДуоЛайт", HwHelperManager.catName(20));
    assertEquals("ТриоЛайт", HwHelperManager.catName(35));
    assertEquals("КланЛайт", HwHelperManager.catName(50));
  }

  @Test
  public void testAnSwapWholeNumberMenuFallbackAndAbort() {
    assertTrue(HwHelperManager.matchesExactAnarchyTag("Лайт #1 [1.16.5]", 1));
    assertFalse(HwHelperManager.matchesExactAnarchyTag("Лайт #10 [1.16.5]", 1));
    assertFalse(HwHelperManager.matchesExactAnarchyTag("Лайт #16 [1.16.5]", 1));
    assertTrue(HwHelperManager.matchesExactAnarchyTag("Лайт #10 [1.16.5]", 10));
    assertFalse(HwHelperManager.matchesServerStandaloneNumber("Лайт анархия 1.16.5 (онлайн 1/100)", 16));
    assertTrue(HwHelperManager.matchesServerStandaloneNumber("Лайт анархия 10 [1.16.5]", 10));

    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final List<String> cmds = new ArrayList<String>();
    final List<String> feedbacks = new ArrayList<String>();
    final List<Integer> clicked = new ArrayList<Integer>();
    final boolean[] usedCompass = {false};
    final int[] stage = {0};

    PlatformServices fallbackPlatform = new PlatformServices() {
      @Override public boolean isInCombat() { return false; }
      @Override public Object currentWorldToken() { return stage[0] >= 1 ? "hub" : "game"; }
      @Override public void sendCommandToServer(String c) {
        cmds.add(c);
        if ("hub".equals(c)) stage[0] = 1;
      }
      @Override public void sendFeedback(String m) { feedbacks.add(m); }
      @Override public boolean useSelectorItem() {
        usedCompass[0] = true;
        stage[0] = 2;
        return true;
      }
      @Override public String openContainerTitle() {
        return stage[0] >= 2 ? "Выбор сервера" : null;
      }
      @Override public List<ContainerSlot> containerSlots() {
        if (stage[0] == 2) {
          return java.util.Arrays.asList(
              new ContainerSlot(10, "§fГриф #1", "", ""),
              new ContainerSlot(11, "§aЛайт #10", "", ""),
              new ContainerSlot(12, "§aЛайт #1", "", ""));
        }
        return java.util.Collections.emptyList();
      }
      @Override public boolean clickContainerSlot(int idx) {
        clicked.add(idx);
        return true;
      }
    };

    HwHelperManager mgr = new HwHelperManager(fallbackPlatform, settings);
    try {
      mgr.handleCommand("/an 1");
      for (int t = 0; t < 60; t++) mgr.tick();
      assertTrue(cmds.contains("hub"));
      assertTrue(cmds.contains("lite"));
      assertTrue(cmds.contains("menu"));
      assertTrue(usedCompass[0]);
      assertEquals(java.util.Collections.singletonList(12), clicked);

      feedbacks.clear();
      clicked.clear();
      cmds.clear();
      usedCompass[0] = false;
      PlatformServices changedMenuPlatform = new PlatformServices() {
        @Override public boolean isInCombat() { return false; }
        @Override public Object currentWorldToken() { return "hub"; }
        @Override public void sendCommandToServer(String c) { cmds.add(c); }
        @Override public void sendFeedback(String m) { feedbacks.add(m); }
        @Override public String openContainerTitle() { return "Другое меню"; }
        @Override public List<ContainerSlot> containerSlots() {
          return java.util.Collections.singletonList(new ContainerSlot(0, "Новости", "", ""));
        }
        @Override public boolean clickContainerSlot(int idx) {
          clicked.add(idx);
          return true;
        }
      };
      HwHelperManager changedMgr = new HwHelperManager(changedMenuPlatform, settings);
      try {
        changedMgr.handleCommand("/an 5");
        for (int t = 0; t < 25; t++) changedMgr.tick();
        assertTrue(clicked.isEmpty());
        assertFalse(feedbacks.isEmpty());
      } finally {
        changedMgr.shutdown();
      }
    } finally {
      mgr.shutdown();
    }
  }

  @Test
  public void testBetterSellAndAnSwap() {
    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.betterSell.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final List<String> serverCommands = new ArrayList<String>();
    final List<Integer> clickedSlots = new ArrayList<Integer>();

    PlatformServices mockPlatform = new PlatformServices() {
      public int mainHandCount() {
        return 64;
      }

      public boolean isInCombat() {
        return false;
      }

      public Object currentWorldToken() {
        return "world";
      }

      public void sendCommandToServer(String command) {
        serverCommands.add(command);
      }

      public boolean clickContainerSlot(int slotIndex) {
        clickedSlots.add(slotIndex);
        return true;
      }

      public String openContainerTitle() {
        return "Меню выбора";
      }
    };

    HwHelperManager manager = new HwHelperManager(mockPlatform, settings);
    try {
      assertEquals("/ah sell 1000000", manager.handleCommand("/ah sell 1m"));
      assertEquals("/ah sell 500000", manager.handleCommand("/ah sell 500k"));
      assertEquals("/ah sell 2500000", manager.handleCommand("/ah sell 2.5kk"));

      assertEquals("/ah sell 64000000", manager.handleCommand("/ah sell 1 1m"));
      assertEquals("/ah sell 64000000", manager.handleCommand("/ah sell |1| 1m"));
      assertEquals("/ah sell 64000000", manager.handleCommand("/ah sell 1m each"));
      assertEquals("/ah sell 64", manager.handleCommand("/ah sells 1"));
      assertEquals("/ah sell 160000", manager.handleCommand("/ah sells 2.5k"));
      assertEquals("/ah sell 160000", manager.handleCommand("/ah sells |2,5k|"));
      assertEquals("/ah sell 64000", manager.handleCommand("/ah sells 1000"));
      assertEquals("/ah sell 100000 64", manager.handleCommand("/ah sell 100000 64"));
      assertEquals("/ah sell 100000 32", manager.handleCommand("/ah sell 100k 32"));

      serverCommands.clear();
      assertEquals("", manager.handleCommand("/an 10"));
      assertEquals(java.util.Collections.singletonList("hub"), serverCommands);

      PlatformServices hubPlatform = new PlatformServices() {
        public boolean isInCombat() {
          return false;
        }

        public Object currentWorldToken() {
          return "world";
        }

        public void sendCommandToServer(String command) {
          serverCommands.add(command);
        }

        public String openContainerTitle() {
          return null;
        }
      };
      HwHelperManager hubManager = new HwHelperManager(hubPlatform, settings);
      serverCommands.clear();
      assertEquals("", hubManager.handleCommand("/lt 25"));
      assertEquals(java.util.Collections.singletonList("hub"), serverCommands);
      hubManager.shutdown();
    } finally {
      manager.shutdown();
    }
  }

  @Test
  public void testAnSwapFullStateMachine() {
    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final List<String> commands = new ArrayList<String>();
    final List<Integer> clicks = new ArrayList<Integer>();
    final List<PlatformServices.ContainerSlot> slots =
        new ArrayList<PlatformServices.ContainerSlot>();
    final Object[] world = new Object[] {"world_anarchy"};
    final boolean[] chatHasConnected = new boolean[] {false};

    PlatformServices platform = new PlatformServices() {
      public boolean isInCombat() {
        return false;
      }

      public void sendCommandToServer(String command) {
        commands.add(command);
      }

      public boolean clickContainerSlot(int slotIndex) {
        clicks.add(slotIndex);
        return true;
      }

      public List<PlatformServices.ContainerSlot> containerSlots() {
        return slots;
      }

      public Object currentWorldToken() {
        return world[0];
      }

      public boolean hasRecentChatMessage(String needle) {
        return chatHasConnected[0] && needle.contains("уже подключен");
      }
    };

    HwHelperManager manager = new HwHelperManager(platform, settings);
    try {
      manager.handleCommand("/an 10");
      assertEquals(java.util.Collections.singletonList("hub"), commands);

      manager.tick();
      assertEquals(1, commands.size());

      world[0] = "world_hub";
      manager.tick();
      assertEquals(2, commands.size());
      assertEquals("lite", commands.get(1));

      slots.add(new PlatformServices.ContainerSlot(11, "Соло (1-15)", "solo", ""));
      slots.add(new PlatformServices.ContainerSlot(12, "Дуо (16-31)", "duo", ""));
      manager.tick();
      assertEquals(1, clicks.size());
      assertEquals(11, clicks.get(0).intValue());

      slots.clear();
      for (int i = 1; i <= 15; i++) {
        slots.add(new PlatformServices.ContainerSlot(
            i - 1, "#" + i, "", i == 1 ? "lanarchy" : "lanarchy" + i));
      }
      for (int i = 0; i < 4; i++) manager.tick();
      manager.tick();
      assertEquals(2, clicks.size());
      assertEquals(9, clicks.get(1).intValue());

      manager.tick();
      assertEquals(2, clicks.size());
    } finally {
      manager.shutdown();
    }
  }

  @Test
  public void testAnSwapScansAllTabsOncePersistsAndFallsBackDynamically() {
    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final int[] saveCount = new int[] {0};
    final List<String> openedTabs = new ArrayList<String>();
    final List<PlatformServices.ContainerSlot> slots =
        new ArrayList<PlatformServices.ContainerSlot>();
    final String[] title = new String[] {"Выбор режима"};
    final int[] soloMax = new int[] {9};
    final int[] trioTenOwner = new int[] {0};

    Runnable openCategoryMenu = () -> {
      slots.clear();
      title[0] = "Лайт Анархия";
      slots.add(new PlatformServices.ContainerSlot(10, "СолоЛайт", "solo", ""));
      slots.add(new PlatformServices.ContainerSlot(11, "ДуоЛайт", "duo", ""));
      slots.add(new PlatformServices.ContainerSlot(12, "ТриоЛайт", "trio", ""));
      slots.add(new PlatformServices.ContainerSlot(13, "КланЛайт", "clans", ""));
    };

    final List<String> connectedServers = new ArrayList<String>();
    PlatformServices platform = new PlatformServices() {
      public boolean isInCombat() {
        return false;
      }

      public Object currentWorldToken() {
        return "hub";
      }

      public String openContainerTitle() {
        return title[0];
      }

      public List<PlatformServices.ContainerSlot> containerSlots() {
        return new ArrayList<PlatformServices.ContainerSlot>(slots);
      }

      public boolean clickContainerSlot(int slotIndex) {
        PlatformServices.ContainerSlot clicked = null;
        for (PlatformServices.ContainerSlot s : slots) {
          if (s.index == slotIndex) {
            clicked = s;
            break;
          }
        }
        if (clicked == null) return false;
        if ("Назад".equals(clicked.name)) {
          openCategoryMenu.run();
          return true;
        }
        if (!clicked.serverType.isEmpty() && clicked.server.isEmpty()) {
          openedTabs.add(clicked.serverType);
          slots.clear();
          if ("solo".equals(clicked.serverType)) {
            title[0] = "СолоЛайт";
            for (int i = 1; i <= soloMax[0]; i++) {
              slots.add(new PlatformServices.ContainerSlot(
                  i - 1, "Лайт #" + i, "", i == 1 ? "lanarchy" : "lanarchy" + i));
            }
          } else if ("duo".equals(clicked.serverType)) {
            title[0] = "ДуоЛайт";
            int start = trioTenOwner[0] == 0 ? 10 : 11;
            for (int i = start; i <= 20; i++) {
              slots.add(new PlatformServices.ContainerSlot(
                  i - start, "Лайт #" + i, "", "lanarchy" + i));
            }
          } else if ("trio".equals(clicked.serverType)) {
            title[0] = "ТриоЛайт";
            if (trioTenOwner[0] == 1) {
              slots.add(new PlatformServices.ContainerSlot(0, "Лайт #10", "", "lanarchy10"));
            }
            for (int i = 21; i <= 30; i++) {
              slots.add(new PlatformServices.ContainerSlot(
                  i - 20, "Лайт #" + i, "", "lanarchy" + i));
            }
          } else if ("clans".equals(clicked.serverType)) {
            title[0] = "КланЛайт";
            for (int i = 31; i <= 40; i++) {
              slots.add(new PlatformServices.ContainerSlot(
                  i - 31, "Лайт #" + i, "", "lanarchy" + i));
            }
          }
          slots.add(new PlatformServices.ContainerSlot(
              49, "Назад", "", "", "Вернуться к категориям", "minecraft:arrow"));
          return true;
        }
        connectedServers.add(clicked.server);
        slots.clear();
        return true;
      }
    };

    HwHelperManager manager =
        new HwHelperManager(platform, settings, () -> saveCount[0]++);
    try {
      openCategoryMenu.run();
      manager.handleCommand("/an 10");
      for (int i = 0; i < 60 && connectedServers.isEmpty(); i++) manager.tick();

      assertEquals(java.util.Collections.singletonList("lanarchy10"), connectedServers);
      assertTrue(saveCount[0] > 0, "Learned catalog must trigger persistence callback");
      assertEquals("solo", settings.hwhelper.answap.catalog.get("9"));
      assertEquals("duo", settings.hwhelper.answap.catalog.get("10"));
      assertEquals(java.util.Arrays.asList("solo", "duo"), openedTabs);

      openedTabs.clear();
      connectedServers.clear();
      openCategoryMenu.run();
      manager.handleCommand("/an 10");
      for (int i = 0; i < 20 && connectedServers.isEmpty(); i++) manager.tick();
      assertEquals(java.util.Collections.singletonList("duo"), openedTabs);
      assertEquals(java.util.Collections.singletonList("lanarchy10"), connectedServers);

      trioTenOwner[0] = 1;
      openedTabs.clear();
      connectedServers.clear();
      openCategoryMenu.run();
      manager.handleCommand("/an 10");
      for (int i = 0; i < 60 && connectedServers.isEmpty(); i++) manager.tick();
      assertEquals(java.util.Collections.singletonList("lanarchy10"), connectedServers);
      assertEquals("trio", settings.hwhelper.answap.catalog.get("10"),
          "Reorganized anarchy #10 must be updated permanently in catalog");
      assertEquals("duo", openedTabs.get(0));
      assertTrue(openedTabs.contains("trio"));
    } finally {
      manager.shutdown();
    }
  }

  @Test
  public void testAnSwapScansAllTabsViaLiteReopenWithoutBackButton() {
    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final List<String> openedTabs = new ArrayList<String>();
    final List<String> connectedServers = new ArrayList<String>();
    final List<String> feedbacks = new ArrayList<String>();
    final List<PlatformServices.ContainerSlot> slots =
        new ArrayList<PlatformServices.ContainerSlot>();
    final String[] title = new String[] {"Выбор режима"};
    final boolean[] inHub = new boolean[] {false};

    Runnable openCategoryMenu = () -> {
      slots.clear();
      title[0] = "Лайт Анархия";
      slots.add(new PlatformServices.ContainerSlot(10, "§aСолоЛайт (#1-#9)", "", "", "Нажмите", "minecraft:player_head"));
      slots.add(new PlatformServices.ContainerSlot(11, "§bДуоЛайт (#10-#20)", "", "", "Нажмите", "minecraft:player_head"));
      slots.add(new PlatformServices.ContainerSlot(12, "§eТриоЛайт (#21-#30)", "", "", "Нажмите", "minecraft:player_head"));
      slots.add(new PlatformServices.ContainerSlot(13, "§cКланЛайт (#31-#40)", "", "", "Нажмите", "minecraft:player_head"));
      slots.add(new PlatformServices.ContainerSlot(22, "§cЗакрыть", "", "close", "", "minecraft:barrier"));
    };

    PlatformServices platform = new PlatformServices() {
      public boolean isInCombat() {
        return false;
      }

      public Object currentWorldToken() {
        return inHub[0] ? "hub" : "anarchy";
      }

      public void sendFeedback(String message) {
        feedbacks.add(message);
      }

      public void sendCommandToServer(String command) {
        if ("hub".equals(command)) {
          inHub[0] = true;
        } else if ("lite".equals(command)) {
          openCategoryMenu.run();
        }
      }

      public String openContainerTitle() {
        return title[0];
      }

      public List<PlatformServices.ContainerSlot> containerSlots() {
        return new ArrayList<PlatformServices.ContainerSlot>(slots);
      }

      public boolean closeContainer() {
        if (slots.isEmpty()) return false;
        slots.clear();
        title[0] = null;
        return true;
      }

      public boolean clickContainerSlot(int slotIndex) {
        PlatformServices.ContainerSlot clicked = null;
        for (PlatformServices.ContainerSlot s : slots) {
          if (s.index == slotIndex) {
            clicked = s;
            break;
          }
        }
        if (clicked == null) return false;
        String name = clicked.name;
        if (name.contains("Соло")) {
          openedTabs.add("solo");
          slots.clear();
          title[0] = "СолоЛайт";
          for (int i = 1; i <= 9; i++) {
            slots.add(new PlatformServices.ContainerSlot(i - 1, "Лайт #" + i, "", ""));
          }
          slots.add(new PlatformServices.ContainerSlot(49, "§cЗакрыть меню", "", "", "", "minecraft:barrier"));
          return true;
        }
        if (name.contains("Дуо")) {
          openedTabs.add("duo");
          slots.clear();
          title[0] = "ДуоЛайт";
          for (int i = 10; i <= 20; i++) {
            slots.add(new PlatformServices.ContainerSlot(i - 10, "Лайт #" + i, "", ""));
          }
          return true;
        }
        if (name.contains("Трио")) {
          openedTabs.add("trio");
          slots.clear();
          title[0] = "ТриоЛайт";
          for (int i = 21; i <= 30; i++) {
            slots.add(new PlatformServices.ContainerSlot(i - 21, "Лайт #" + i, "", ""));
          }
          return true;
        }
        if (name.contains("Клан")) {
          openedTabs.add("clans");
          slots.clear();
          title[0] = "КланЛайт";
          for (int i = 31; i <= 40; i++) {
            slots.add(new PlatformServices.ContainerSlot(i - 31, "Лайт #" + i, "", ""));
          }
          return true;
        }
        connectedServers.add(clicked.name);
        slots.clear();
        return true;
      }
    };

    HwHelperManager manager = new HwHelperManager(platform, settings);
    try {
      manager.handleCommand("/an 1");
      for (int i = 0; i < 60 && connectedServers.isEmpty(); i++) manager.tick();

      assertEquals(java.util.Collections.singletonList("solo"), openedTabs);
      assertEquals(java.util.Collections.singletonList("Лайт #1"), connectedServers);
      assertEquals("solo", settings.hwhelper.answap.catalog.get("9"));

      openedTabs.clear();
      connectedServers.clear();
      manager.handleCommand("/an 10");
      for (int i = 0; i < 80 && connectedServers.isEmpty(); i++) manager.tick();
      assertEquals(java.util.Arrays.asList("solo", "duo"), openedTabs);
      assertEquals(java.util.Collections.singletonList("Лайт #10"), connectedServers);
      assertEquals("duo", settings.hwhelper.answap.catalog.get("10"));

      openedTabs.clear();
      connectedServers.clear();
      manager.handleCommand("/an 10");
      for (int i = 0; i < 40 && connectedServers.isEmpty(); i++) manager.tick();
      assertEquals(java.util.Collections.singletonList("duo"), openedTabs);
      assertEquals(java.util.Collections.singletonList("Лайт #10"), connectedServers);
    } finally {
      manager.shutdown();
    }
  }

  @Test
  public void testAnSwapSupportsSkullMenuBackButtonAndInlineTabs() {
    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final List<String> openedTabs = new ArrayList<String>();
    final List<String> connectedServers = new ArrayList<String>();
    final List<PlatformServices.ContainerSlot> slots =
        new ArrayList<PlatformServices.ContainerSlot>();
    final String[] title = new String[] {"Лайт Анархия"};

    Runnable openCategoryMenu = () -> {
      slots.clear();
      title[0] = "Лайт Анархия";
      slots.add(new PlatformServices.ContainerSlot(10, "СолоЛайт", "", ""));
      slots.add(new PlatformServices.ContainerSlot(11, "ДуоЛайт", "", ""));
      slots.add(new PlatformServices.ContainerSlot(12, "ТриоЛайт", "", ""));
      slots.add(new PlatformServices.ContainerSlot(13, "КланЛайт", "", ""));
    };

    PlatformServices platform = new PlatformServices() {
      public boolean isInCombat() {
        return false;
      }

      public Object currentWorldToken() {
        return "hub";
      }

      public String openContainerTitle() {
        return title[0];
      }

      public List<PlatformServices.ContainerSlot> containerSlots() {
        return new ArrayList<PlatformServices.ContainerSlot>(slots);
      }

      public boolean clickContainerSlot(int slotIndex) {
        PlatformServices.ContainerSlot clicked = null;
        for (PlatformServices.ContainerSlot s : slots) {
          if (s.index == slotIndex) {
            clicked = s;
            break;
          }
        }
        if (clicked == null) return false;
        if ("◀ В меню".equals(clicked.name)) {
          openCategoryMenu.run();
          return true;
        }
        if (clicked.name.contains("Соло")) {
          openedTabs.add("solo");
          slots.clear();
          title[0] = "СолоЛайт";
          slots.add(new PlatformServices.ContainerSlot(0, "Лайт #1", "", "lanarchy"));
          slots.add(new PlatformServices.ContainerSlot(49, "◀ В меню", "categories", "", "", "minecraft:player_head"));
          return true;
        }
        if (clicked.name.contains("Дуо")) {
          openedTabs.add("duo");
          slots.clear();
          title[0] = "ДуоЛайт";
          slots.add(new PlatformServices.ContainerSlot(0, "Лайт #10", "", "lanarchy10"));
          slots.add(new PlatformServices.ContainerSlot(49, "◀ В меню", "categories", "", "", "minecraft:player_head"));
          return true;
        }
        if (clicked.name.contains("Трио")) {
          openedTabs.add("trio");
          slots.clear();
          title[0] = "ТриоЛайт";
          slots.add(new PlatformServices.ContainerSlot(0, "Лайт #21", "", "lanarchy21"));
          slots.add(new PlatformServices.ContainerSlot(49, "◀ В меню", "categories", "", "", "minecraft:player_head"));
          return true;
        }
        if (clicked.name.contains("Клан")) {
          openedTabs.add("clans");
          slots.clear();
          title[0] = "КланЛайт";
          slots.add(new PlatformServices.ContainerSlot(0, "Лайт #31", "", "lanarchy31"));
          slots.add(new PlatformServices.ContainerSlot(49, "◀ В меню", "categories", "", "", "minecraft:player_head"));
          return true;
        }
        connectedServers.add(clicked.server);
        slots.clear();
        return true;
      }
    };

    HwHelperManager manager = new HwHelperManager(platform, settings);
    try {
      openCategoryMenu.run();
      manager.handleCommand("/an 10");
      for (int i = 0; i < 60 && connectedServers.isEmpty(); i++) manager.tick();

      assertEquals(java.util.Arrays.asList("solo", "duo"), openedTabs);
      assertEquals(java.util.Collections.singletonList("lanarchy10"), connectedServers);
      assertEquals("duo", settings.hwhelper.answap.catalog.get("10"));
    } finally {
      manager.shutdown();
    }
  }

  @Test
  public void testAnSwapLobbySkipModeSelectionLoreAndRestartDetection() {
    Settings settings = new Settings();
    settings.hwhelper.enabled = true;
    settings.hwhelper.answap.enabled = true;

    final List<String> cmds = new ArrayList<String>();
    final List<Integer> clicked = new ArrayList<Integer>();
    final List<String> feedbacks = new ArrayList<String>();
    final List<PlatformServices.ContainerSlot> slots =
        new ArrayList<PlatformServices.ContainerSlot>();
    final String[] title = new String[] {null};

    PlatformServices lobbyPlatform = new PlatformServices() {
      @Override public boolean isInLobby() { return true; }
      @Override public Object currentWorldToken() { return "lobby"; }
      @Override public void sendCommandToServer(String c) {
        cmds.add(c);
        if ("lite".equals(c)) {
          title[0] = "Выберите режим";
          slots.clear();
          slots.add(new PlatformServices.ContainerSlot(
              10, "Анархия Лайт 1.21", "", "", "Версия 1.21", "minecraft:player_head"));
          slots.add(new PlatformServices.ContainerSlot(
              11, "Анархия 1.16", "", "", "Режим Лайт 1.16.5", "minecraft:player_head"));
        }
      }
      @Override public void sendFeedback(String m) { feedbacks.add(m); }
      @Override public String openContainerTitle() { return title[0]; }
      @Override public List<PlatformServices.ContainerSlot> containerSlots() {
        return new ArrayList<PlatformServices.ContainerSlot>(slots);
      }
      @Override public boolean clickContainerSlot(int idx) {
        clicked.add(idx);
        if (idx == 11) {
          title[0] = "СолоЛайт";
          slots.clear();
          slots.add(new PlatformServices.ContainerSlot(
              2, "СолоЛайт", "", "", "Сервер #7\nОнлайн: 42/100", "minecraft:player_head"));
          slots.add(new PlatformServices.ContainerSlot(
              3, "СолоЛайт", "", "", "Сервер #8\nОбновить статус (перезагружается)", "minecraft:player_head"));
        }
        return true;
      }
    };

    HwHelperManager mgr = new HwHelperManager(lobbyPlatform, settings);
    try {
      mgr.handleCommand("/an 7");
      for (int i = 0; i < 20 && clicked.size() < 2; i++) mgr.tick();
      assertFalse(cmds.contains("hub"), "Already in lobby — must not send /hub");
      assertTrue(cmds.contains("lite"));
      assertEquals(java.util.Arrays.asList(11, 2), clicked);

      clicked.clear();
      feedbacks.clear();
      slots.clear();
      title[0] = null;
      mgr.handleCommand("/an 8");
      for (int i = 0; i < 20; i++) mgr.tick();
      assertEquals(java.util.Collections.singletonList(11), clicked);
      assertFalse(feedbacks.isEmpty(), "Restarting server should report feedback without clicking");
    } finally {
      mgr.shutdown();
    }
  }
}

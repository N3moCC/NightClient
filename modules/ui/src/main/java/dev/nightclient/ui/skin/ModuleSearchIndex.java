package dev.nightclient.ui.skin;

import dev.nightclient.config.FeaturePolicy;
import dev.nightclient.i18n.I18n;
import dev.nightclient.skin.SkinDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ModuleSearchIndex {
  private static final int MAX_AUTO_EXPAND_MODULES = 8;

  static final class SearchPlan {
    static final SearchPlan EMPTY = new SearchPlan("", Collections.<String>emptySet(),
        Collections.<String>emptySet(), Collections.<String, Set<SkinDocument.Node>>emptyMap(),
        Collections.<String>emptySet(), Collections.<String>emptyList(), 0L);

    final String query;
    final Set<String> matchedModules;
    final Set<String> expandedScopes;
    final Map<String, Set<SkinDocument.Node>> revealedNodesByModule;
    final Set<String> revealedControlBinds;
    final List<String> matchedTargetIds;
    final long signature;

    SearchPlan(String query, Set<String> matchedModules, Set<String> expandedScopes,
        Map<String, Set<SkinDocument.Node>> revealedNodesByModule, Set<String> revealedControlBinds,
        List<String> matchedTargetIds, long signature) {
      this.query = query;
      this.matchedModules = matchedModules;
      this.expandedScopes = expandedScopes;
      this.revealedNodesByModule = revealedNodesByModule;
      this.revealedControlBinds = revealedControlBinds;
      this.matchedTargetIds = matchedTargetIds;
      this.signature = signature;
    }

    boolean isEmpty() {
      return query.isEmpty();
    }

    boolean matchesModule(String moduleId) {
      return query.isEmpty() || matchedModules.contains(moduleId);
    }

    boolean isScopeExpanded(String scope) {
      return scope != null && expandedScopes.contains(scope);
    }

    boolean isNodeRevealed(String moduleId, SkinDocument.Node node) {
      if (moduleId == null || node == null || revealedNodesByModule.isEmpty()) return false;
      Set<SkinDocument.Node> nodes = revealedNodesByModule.get(moduleId);
      return nodes != null && nodes.contains(node);
    }

    boolean isControlRevealed(String bind) {
      return bind != null && revealedControlBinds.contains(bind);
    }
  }

  private static final class SearchItem {
    final String ownText;
    final String text;
    final String[] expandedScopes;
    final SkinDocument.Node[] gatedNodes;
    final String controlBind;
    final String targetId;
    final boolean leaf;

    SearchItem(String ownText, String text, List<String> expandedScopes,
        List<SkinDocument.Node> gatedNodes, String controlBind, String targetId, boolean leaf) {
      this.ownText = ownText;
      this.text = text;
      this.expandedScopes = expandedScopes.isEmpty()
          ? EMPTY_STRINGS
          : expandedScopes.toArray(new String[expandedScopes.size()]);
      this.gatedNodes = gatedNodes.isEmpty()
          ? EMPTY_NODES
          : gatedNodes.toArray(new SkinDocument.Node[gatedNodes.size()]);
      this.controlBind = controlBind;
      this.targetId = targetId;
      this.leaf = leaf;
    }
  }

  private static final class ModuleEntry {
    final String id;
    final String titleText;
    final String summaryText;
    final String headerText;
    final List<SearchItem> items;

    ModuleEntry(String id, String titleText, String summaryText, List<SearchItem> items) {
      this.id = id;
      this.titleText = titleText;
      this.summaryText = summaryText;
      this.headerText = summaryText.isEmpty() ? titleText : (titleText + " " + summaryText);
      this.items = items;
    }
  }

  private static final String[] EMPTY_STRINGS = new String[0];
  private static final SkinDocument.Node[] EMPTY_NODES = new SkinDocument.Node[0];

  private SkinDocument cachedDocument;
  private String cachedLanguage;
  private long cachedPolicyRevision = -1L;
  private Map<String, ModuleEntry> entries = Collections.emptyMap();

  private String lastPlanQuery = "";
  private String lastPlanScopeKey = "";
  private SearchPlan lastPlan = SearchPlan.EMPTY;

  void ensureBuilt(SkinDocument document, UiCatalog catalog, SkinBindings bindings, String lang) {
    String normalizedLang = I18n.language(lang);
    long policyRev = FeaturePolicy.revision() * 31L + dev.nightclient.config.FeatureSupport.revision();
    if (document == cachedDocument && normalizedLang.equals(cachedLanguage)
        && policyRev == cachedPolicyRevision) {
      return;
    }
    cachedDocument = document;
    cachedLanguage = normalizedLang;
    cachedPolicyRevision = policyRev;
    lastPlanQuery = "";
    lastPlanScopeKey = "";
    lastPlan = SearchPlan.EMPTY;
    entries = buildEntries(document, catalog, bindings, normalizedLang);
  }

  boolean matchesModuleTitle(String moduleId, String rawQuery) {
    String normalized = normalize(rawQuery);
    if (normalized.isEmpty()) return false;
    ModuleEntry entry = entries.get(moduleId);
    if (entry == null) return false;
    String[] tokens = splitTokens(normalized);
    return tokens.length > 0 && allMatch(tokens, entry.titleText);
  }

  boolean matchesModule(String moduleId, String rawQuery) {
    String normalized = normalize(rawQuery);
    if (normalized.isEmpty()) return true;
    ModuleEntry entry = entries.get(moduleId);
    if (entry == null) return false;
    String[] tokens = splitTokens(normalized);
    if (tokens.length == 0) return true;
    if (allMatch(tokens, entry.titleText)) return true;
    if (allMatchCombined(tokens, entry.titleText, entry.summaryText)) return true;
    for (int i = 0, size = entry.items.size(); i < size; i++) {
      if (allMatchCombined(tokens, entry.headerText, entry.items.get(i).text)) return true;
    }
    return false;
  }

  SearchPlan plan(String rawQuery, String pageKey, List<UiCatalog.Module> pageModules) {
    String normalized = normalize(rawQuery);
    if (normalized.isEmpty()) return SearchPlan.EMPTY;
    String scopeKey = pageKey == null ? "" : pageKey;
    if (normalized.equals(lastPlanQuery) && scopeKey.equals(lastPlanScopeKey)) return lastPlan;

    String[] tokens = splitTokens(normalized);
    if (tokens.length == 0) return SearchPlan.EMPTY;

    Set<String> pageIds = new HashSet<String>(pageModules.size() * 2 + 1);
    for (int i = 0, size = pageModules.size(); i < size; i++) pageIds.add(pageModules.get(i).id);

    Set<String> matched = new HashSet<String>();
    Set<String> deepPageModules = new HashSet<String>();
    boolean hasPageTitleMatch = false;
    Map<String, List<SearchItem>> deepHits = new HashMap<String, List<SearchItem>>();

    for (ModuleEntry entry : entries.values()) {
      boolean titleMatched = allMatch(tokens, entry.titleText);
      boolean summaryMatched =
          !titleMatched && allMatchCombined(tokens, entry.titleText, entry.summaryText);
      List<SearchItem> itemHits = null;
      if (!titleMatched) {
        for (int i = 0, size = entry.items.size(); i < size; i++) {
          SearchItem item = entry.items.get(i);
          if (allMatchCombined(tokens, entry.headerText, item.text)) {
            if (itemHits == null) itemHits = new ArrayList<SearchItem>();
            itemHits.add(item);
          }
        }
      }
      if (titleMatched || summaryMatched || itemHits != null) {
        matched.add(entry.id);
        if (titleMatched && pageIds.contains(entry.id)) hasPageTitleMatch = true;
        if (itemHits != null) {
          deepHits.put(entry.id, itemHits);
          if (pageIds.contains(entry.id)) deepPageModules.add(entry.id);
        }
      }
    }

    boolean allowAutoExpand =
        normalized.length() >= 2 && !deepPageModules.isEmpty()
            && deepPageModules.size() <= MAX_AUTO_EXPAND_MODULES;

    Set<String> expandedScopes = new HashSet<String>();
    Map<String, Set<SkinDocument.Node>> revealedNodes = new HashMap<String, Set<SkinDocument.Node>>();
    Set<String> revealedBinds = new HashSet<String>();
    LinkedHashSet<String> directLeafIds = new LinkedHashSet<String>();
    LinkedHashSet<String> leafTargetIds = new LinkedHashSet<String>();
    LinkedHashSet<String> headerTargetIds = new LinkedHashSet<String>();

    if (allowAutoExpand) {
      for (int p = 0, pSize = pageModules.size(); p < pSize; p++) {
        String modId = pageModules.get(p).id;
        if (!deepPageModules.contains(modId)) continue;
        ModuleEntry modEntry = entries.get(modId);
        List<SearchItem> hits = deepHits.get(modId);
        if (hits == null) continue;
        for (int i = 0, size = hits.size(); i < size; i++) {
          SearchItem item = hits.get(i);
          for (String s : item.expandedScopes) expandedScopes.add(s);
          if (item.gatedNodes.length > 0) {
            Set<SkinDocument.Node> set = revealedNodes.get(modId);
            if (set == null) {
              set = Collections.newSetFromMap(new IdentityHashMap<SkinDocument.Node, Boolean>());
              revealedNodes.put(modId, set);
            }
            for (SkinDocument.Node n : item.gatedNodes) set.add(n);
          }
          if (item.controlBind != null) revealedBinds.add(item.controlBind);
          if (!hasPageTitleMatch && item.targetId != null && !item.targetId.isEmpty()) {
            if (item.leaf) {
              boolean direct = allMatch(tokens, item.ownText)
                  || (modEntry != null && allMatchCombined(tokens, modEntry.headerText, item.ownText));
              (direct ? directLeafIds : leafTargetIds).add(item.targetId);
            } else {
              headerTargetIds.add(item.targetId);
            }
          }
        }
      }
    }
    List<String> targetIds;
    if (directLeafIds.isEmpty() && leafTargetIds.isEmpty() && headerTargetIds.isEmpty()) {
      targetIds = Collections.emptyList();
    } else {
      LinkedHashSet<String> ordered = new LinkedHashSet<String>();
      ordered.addAll(directLeafIds);
      ordered.addAll(leafTargetIds);
      ordered.addAll(headerTargetIds);
      targetIds = new ArrayList<String>(ordered);
    }

    long sig = 0xCBF29CE484222325L;
    sig = mix(sig, normalized.hashCode());
    sig = mix(sig, scopeKey.hashCode());
    sig = mix(sig, matched.size());
    sig = mix(sig, expandedScopes.hashCode());
    sig = mix(sig, revealedBinds.hashCode());
    sig = mix(sig, revealedNodes.size());

    lastPlanQuery = normalized;
    lastPlanScopeKey = scopeKey;
    lastPlan = new SearchPlan(
        normalized, matched, expandedScopes, revealedNodes, revealedBinds, targetIds, sig);
    return lastPlan;
  }

  private static long mix(long h, int value) {
    return (h ^ value) * 0x9E3779B185EBCA87L;
  }

  private Map<String, ModuleEntry> buildEntries(
      SkinDocument document, UiCatalog catalog, SkinBindings bindings, String lang) {
    Map<String, String> activeI18n = I18n.catalog(lang);
    Map<String, String> enI18n = I18n.catalog("en");

    List<UiCatalog.Module> allModules = new ArrayList<UiCatalog.Module>();
    allModules.addAll(catalog.modules(false));
    allModules.addAll(catalog.modules(true));

    List<SkinDocument.Node> moduleRepeats = new ArrayList<SkinDocument.Node>();
    if (document != null && document.menu() != null) {
      collectModuleRepeats(document.menu(), moduleRepeats);
    }

    Map<String, ModuleEntry> map = new LinkedHashMap<String, ModuleEntry>(allModules.size() * 2 + 1);
    for (int i = 0, size = allModules.size(); i < size; i++) {
      UiCatalog.Module m = allModules.get(i);
      StringBuilder title = new StringBuilder();
      appendWord(title, m.id);
      appendWord(title, splitIdentifier(m.id));
      appendI18nKey(title, m.labelKey, activeI18n, enI18n);

      StringBuilder summary = new StringBuilder();
      appendI18nKey(summary, m.summaryKey, activeI18n, enI18n);
      appendI18nKey(summary, "desc." + m.id + ".module", activeI18n, enI18n);

      List<SearchItem> items = new ArrayList<SearchItem>();
      Set<String> indexedBinds = new HashSet<String>();
      Map<String, Object> modContext = Collections.<String, Object>singletonMap("module", m);

      for (int r = 0; r < moduleRepeats.size(); r++) {
        SkinDocument.Node repeat = moduleRepeats.get(r);
        for (int c = 0; c < repeat.children.size(); c++) {
          walkNode(repeat.children.get(c), m, catalog, bindings, modContext, activeI18n, enI18n,
              new ArrayList<String>(), new ArrayList<String>(), new ArrayList<SkinDocument.Node>(),
              "", items, indexedBinds);
        }
      }

      List<UiCatalog.Control> fallbackControls = catalog.controls(m.id);
      if (!fallbackControls.isEmpty()) {
        List<String> modScope = Collections.singletonList("module." + m.id);
        for (int c = 0; c < fallbackControls.size(); c++) {
          UiCatalog.Control ctrl = fallbackControls.get(c);
          if (indexedBinds.contains(ctrl.bind)) continue;
          String text = buildControlText(m.id, ctrl, activeI18n, enI18n, "");
          if (!text.isEmpty()) {
            items.add(new SearchItem(text, text, modScope, Collections.<SkinDocument.Node>emptyList(),
                ctrl.condition.isEmpty() ? null : ctrl.bind, ctrl.bind, true));
          }
        }
      }

      map.put(m.id,
          new ModuleEntry(m.id, normalize(title.toString()), normalize(summary.toString()), items));
    }
    return map;
  }

  private static void collectModuleRepeats(SkinDocument.Node node, List<SkinDocument.Node> out) {
    if (node == null) return;
    if ("repeat".equals(node.tag)) {
      String src = node.attributes.get("source");
      if ("categoryModules".equals(src) || "filteredModules".equals(src)
          || "filteredHudModules".equals(src) || "renderModules".equals(src)
          || "playerModules".equals(src)) {
        out.add(node);
        return;
      }
    }
    for (int i = 0, size = node.children.size(); i < size; i++) {
      collectModuleRepeats(node.children.get(i), out);
    }
  }

  private static void walkNode(SkinDocument.Node node, UiCatalog.Module module, UiCatalog catalog,
      SkinBindings bindings, Map<String, Object> modContext, Map<String, String> activeI18n,
      Map<String, String> enI18n, List<String> scopeStack, List<String> expandedScopes,
      List<SkinDocument.Node> gatedNodes, String groupContext, List<SearchItem> out,
      Set<String> indexedBinds) {
    if (node == null) return;
    String idAttr = node.attributes.get("id");
    String nodeBindAttr = node.attributes.get("bind");
    if (nodeBindAttr != null && !nodeBindAttr.isEmpty() && !nodeBindAttr.contains("{{")
        && (FeaturePolicy.isControlBlocked(nodeBindAttr)
            || !dev.nightclient.config.FeatureSupport.isControlSupported(nodeBindAttr))) {
      return;
    }
    String nodeActionAttr = node.attributes.get("action");
    if (nodeActionAttr != null && !nodeActionAttr.isEmpty() && !nodeActionAttr.contains("{{")
        && FeaturePolicy.isActionBlocked(nodeActionAttr)) {
      return;
    }
    if (idAttr != null
        && (idAttr.startsWith("bind-module.") || idAttr.startsWith("module-toggle.")
            || idAttr.startsWith("expand."))) {
      return;
    }

    String ifAttr = node.attributes.get("if");
    boolean addedExpandedScope = false;
    boolean addedGatedNode = false;
    if (ifAttr != null && !ifAttr.isEmpty()) {
      if (isTransientUiGuard(ifAttr)) return;
      if (ifAttr.contains("module.id")) {
        if (!bindings.condition(ifAttr, modContext)) return;
      } else if (isLocalExpandedGuard(ifAttr)) {
        if (!scopeStack.isEmpty()) {
          String enclosing = scopeStack.get(scopeStack.size() - 1);
          if (!expandedScopes.contains(enclosing)) {
            expandedScopes.add(enclosing);
            addedExpandedScope = true;
          }
        }
      } else if (isSettingsGuard(ifAttr)) {
        gatedNodes.add(node);
        addedGatedNode = true;
      } else {
        return;
      }
    }

    String pushedScope = null;
    boolean isSubmodulePanel = false;
    String scopeAttr = node.attributes.get("scope");
    if (scopeAttr != null && !scopeAttr.isEmpty()) {
      pushedScope = scopeAttr.replace("{{module.id}}", module.id);
      scopeStack.add(pushedScope);
      isSubmodulePanel = "expanded".equals(node.attributes.get("selection-state"))
          && !pushedScope.equals("module." + module.id);
    }

    try {
      if ("repeat".equals(node.tag) && "controls".equals(node.attributes.get("source"))) {
        List<UiCatalog.Control> controls = catalog.controls(module.id);
        for (int i = 0, size = controls.size(); i < size; i++) {
          UiCatalog.Control ctrl = controls.get(i);
          indexedBinds.add(ctrl.bind);
          String own = buildControlText(module.id, ctrl, activeI18n, enI18n, "");
          String text = buildControlText(module.id, ctrl, activeI18n, enI18n, groupContext);
          if (!text.isEmpty()) {
            out.add(new SearchItem(own, text, expandedScopes, gatedNodes,
                ctrl.condition.isEmpty() ? null : ctrl.bind, ctrl.bind, true));
          }
        }
        return;
      }

      String nextGroupContext = groupContext;
      String headerText = collectDirectHeaderText(node, activeI18n, enI18n);
      if (!headerText.isEmpty()) {
        nextGroupContext = groupContext.isEmpty() ? headerText : (groupContext + " " + headerText);
      }

      if (!expandedScopes.isEmpty()) {
        String bindAttr = node.attributes.get("bind");
        StringBuilder ownText = new StringBuilder();
        appendTemplateText(ownText, node.text, activeI18n, enI18n);
        appendTemplateText(ownText, node.attributes.get("text"), activeI18n, enI18n);
        appendTemplateText(ownText, node.attributes.get("tooltip"), activeI18n, enI18n);
        appendTemplateText(ownText, node.attributes.get("option-labels"), activeI18n, enI18n);
        appendTemplateText(ownText, node.attributes.get("placeholder"), activeI18n, enI18n);

        String optionsAttr = node.attributes.get("options");
        if (optionsAttr != null && !optionsAttr.contains("{{")) {
          appendWord(ownText, optionsAttr.replace('|', ' ').replace('_', ' '));
        }

        if (bindAttr != null && !bindAttr.isEmpty() && !bindAttr.contains("{{")
            && !bindAttr.startsWith("ui.") && !bindAttr.startsWith("local.")) {
          indexedBinds.add(bindAttr);
          UiCatalog.Control ctrl = catalog.control(bindAttr);
          if (ctrl != null) {
            appendControlMetadata(ownText, module.id, ctrl, activeI18n, enI18n);
          } else {
            appendBindTokens(ownText, module.id, bindAttr);
            appendI18nKey(ownText, "desc." + bindAttr, activeI18n, enI18n);
          }
        }

        String normalizedOwn = normalize(ownText.toString());
        String normalizedGroup = normalize(nextGroupContext);
        String normalizedItem = normalizedGroup.isEmpty() ? normalizedOwn
            : (normalizedOwn.isEmpty() ? normalizedGroup : (normalizedGroup + " " + normalizedOwn));
        if (!normalizedItem.isEmpty() && isIndexableLeafOrHeader(node, isSubmodulePanel)) {
          List<String> targetScopes = expandedScopes;
          if (isSubmodulePanel && pushedScope != null && !expandedScopes.contains(pushedScope)) {
            targetScopes = new ArrayList<String>(expandedScopes.size() + 1);
            targetScopes.addAll(expandedScopes);
            targetScopes.add(pushedScope);
          } else if (!scopeStack.isEmpty()) {
            String inner = scopeStack.get(scopeStack.size() - 1);
            if (!inner.equals("module." + module.id) && !expandedScopes.contains(inner)
                && isSubmoduleHeaderButton(node, inner)) {
              targetScopes = new ArrayList<String>(expandedScopes.size() + 1);
              targetScopes.addAll(expandedScopes);
              targetScopes.add(inner);
            }
          }
          out.add(new SearchItem(normalizedOwn, normalizedItem, targetScopes, gatedNodes, null,
              resolveTargetId(node, bindAttr), isLeafControlTag(node.tag)));
        }
      }

      for (int i = 0, size = node.children.size(); i < size; i++) {
        walkNode(node.children.get(i), module, catalog, bindings, modContext, activeI18n, enI18n,
            scopeStack, expandedScopes, gatedNodes, nextGroupContext, out, indexedBinds);
      }
    } finally {
      if (pushedScope != null) scopeStack.remove(scopeStack.size() - 1);
      if (addedGatedNode) gatedNodes.remove(gatedNodes.size() - 1);
      if (addedExpandedScope) expandedScopes.remove(expandedScopes.size() - 1);
    }
  }

  private static String resolveTargetId(SkinDocument.Node node, String bindAttr) {
    String idAttr = node.attributes.get("id");
    if (idAttr != null && !idAttr.isEmpty() && !idAttr.contains("{{")) return idAttr;
    if (node.id != null && !node.id.isEmpty() && !node.id.contains("{{")) return node.id;
    if (bindAttr != null && !bindAttr.isEmpty() && !bindAttr.contains("{{")) {
      return node.tag + "." + bindAttr;
    }
    return null;
  }

  private static boolean isLeafControlTag(String tag) {
    return "toggle".equals(tag) || "switch".equals(tag) || "slider".equals(tag)
        || "choice".equals(tag) || "color".equals(tag) || "input".equals(tag)
        || "color-plane".equals(tag) || "pixel-grid".equals(tag);
  }

  private static boolean isIndexableLeafOrHeader(SkinDocument.Node node, boolean isSubmodulePanel) {
    if (isSubmodulePanel) return true;
    String tag = node.tag;
    if ("toggle".equals(tag) || "switch".equals(tag) || "slider".equals(tag) || "choice".equals(tag)
        || "color".equals(tag) || "input".equals(tag) || "color-plane".equals(tag)
        || "color-swatch".equals(tag) || "pixel-grid".equals(tag)) {
      return true;
    }
    if ("button".equals(tag)) {
      String action = node.attributes.get("action");
      return hasTemplateText(node.text) || hasTemplateText(node.attributes.get("tooltip"))
          || "local-toggle:expanded".equals(action);
    }
    if ("text".equals(tag) || "label".equals(tag)) {
      return hasTemplateText(node.text);
    }
    return false;
  }

  private static boolean isSubmoduleHeaderButton(SkinDocument.Node node, String scope) {
    if ("button".equals(node.tag) && "local-toggle:expanded".equals(node.attributes.get("action"))) {
      return scope != null && !scope.startsWith("atmo.pair.");
    }
    return ("text".equals(node.tag) || "label".equals(node.tag))
        && scope != null && !scope.startsWith("atmo.pair.");
  }

  private static String collectDirectHeaderText(
      SkinDocument.Node node, Map<String, String> activeI18n, Map<String, String> enI18n) {
    if (!"panel".equals(node.tag) && !"column".equals(node.tag)) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0, size = node.children.size(); i < size; i++) {
      SkinDocument.Node child = node.children.get(i);
      if ("text".equals(child.tag)) {
        String st = child.attributes.get("style");
        if (st != null && st.endsWith("-section")) {
          appendTemplateText(sb, child.text, activeI18n, enI18n);
        }
      } else if ("button".equals(child.tag)
          && "local-toggle:expanded".equals(child.attributes.get("action"))) {
        appendTemplateText(sb, child.attributes.get("tooltip"), activeI18n, enI18n);
        for (int j = 0; j < child.children.size(); j++) {
          SkinDocument.Node gc = child.children.get(j);
          if ("text".equals(gc.tag)) appendTemplateText(sb, gc.text, activeI18n, enI18n);
        }
      }
    }
    return sb.toString().trim();
  }

  private static boolean hasTemplateText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static boolean isLocalExpandedGuard(String expr) {
    String t = expr.trim();
    return "local.expanded".equals(t) || "{{= local.expanded}}".equals(t);
  }

  private static boolean isTransientUiGuard(String expr) {
    return expr.contains("bindingCapture") || expr.contains("soundMutedEmpty")
        || expr.contains("tntFuseMp3Loaded") || expr.contains("tntExplodeMp3Loaded")
        || expr.contains("cardBindingLabel") || expr.contains("vars.k_cap")
        || expr.contains("retainedCount") || expr.contains("!local.expanded")
        || expr.contains("control.kind") || expr.contains("cosmetics")
        || expr.contains("page=") || expr.contains("page!");
  }

  private static boolean isSettingsGuard(String expr) {
    if (expr.contains("local.") || expr.contains("ui.") || expr.contains("module.")
        || expr.contains("control.")) {
      return false;
    }
    return true;
  }

  private static String buildControlText(String moduleId, UiCatalog.Control ctrl,
      Map<String, String> activeI18n, Map<String, String> enI18n, String groupContext) {
    StringBuilder sb = new StringBuilder();
    if (!groupContext.isEmpty()) appendWord(sb, groupContext);
    appendControlMetadata(sb, moduleId, ctrl, activeI18n, enI18n);
    return normalize(sb.toString());
  }

  private static void appendControlMetadata(StringBuilder out, String moduleId,
      UiCatalog.Control ctrl, Map<String, String> activeI18n, Map<String, String> enI18n) {
    appendI18nKey(out, ctrl.labelKey, activeI18n, enI18n);
    appendI18nKey(out, "desc." + ctrl.bind, activeI18n, enI18n);
    appendBindTokens(out, moduleId, ctrl.bind);
    if (ctrl.optionLabels != null && !ctrl.optionLabels.isEmpty()) {
      appendWord(out, ctrl.optionLabels.replace('|', ' '));
    }
    if (ctrl.options != null && !ctrl.options.isEmpty()) {
      appendWord(out, ctrl.options.replace('|', ' ').replace('_', ' '));
    }
  }

  private static void appendBindTokens(StringBuilder out, String moduleId, String bind) {
    String trimmed = bind;
    String prefix = moduleId + ".";
    if (trimmed.startsWith(prefix)) trimmed = trimmed.substring(prefix.length());
    if (trimmed.endsWith(".enabled")) trimmed = trimmed.substring(0, trimmed.length() - 8);
    if (!trimmed.isEmpty()) {
      appendWord(out, trimmed.replace('.', ' '));
      appendWord(out, splitIdentifier(trimmed));
    }
  }

  private static String splitIdentifier(String value) {
    if (value == null || value.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(value.length() + 8);
    char prev = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '.' || c == '_' || c == '-') {
        sb.append(' ');
      } else {
        if (Character.isUpperCase(c) && prev != 0 && Character.isLowerCase(prev)) sb.append(' ');
        sb.append(c);
      }
      prev = c;
    }
    return sb.toString();
  }

  private static void appendI18nKey(StringBuilder out, String key,
      Map<String, String> activeI18n, Map<String, String> enI18n) {
    if (key == null || key.isEmpty()) return;
    String a = activeI18n == null ? null : activeI18n.get(key);
    if (a != null) appendWord(out, a);
    if (enI18n != null && enI18n != activeI18n) {
      String e = enI18n.get(key);
      if (e != null && !e.equals(a)) appendWord(out, e);
    }
  }

  private static void appendTemplateText(StringBuilder out, String template,
      Map<String, String> activeI18n, Map<String, String> enI18n) {
    if (template == null || template.isEmpty()) return;
    int pos = 0, len = template.length();
    while (pos < len) {
      int open = template.indexOf("{{", pos);
      if (open < 0) {
        appendWord(out, template.substring(pos).replace('|', ' '));
        break;
      }
      if (open > pos) appendWord(out, template.substring(pos, open).replace('|', ' '));
      int close = template.indexOf("}}", open + 2);
      if (close < 0) break;
      String expr = template.substring(open + 2, close).trim();
      if (expr.startsWith("i18n.")) {
        appendI18nKey(out, expr.substring(5), activeI18n, enI18n);
      }
      pos = close + 2;
    }
  }

  private static void appendWord(StringBuilder out, String value) {
    if (value == null) return;
    String t = value.trim();
    if (t.isEmpty()) return;
    if (out.length() > 0) out.append(' ');
    out.append(t);
  }

  static String normalize(String raw) {
    if (raw == null) return "";
    String t = raw.trim();
    if (t.isEmpty()) return "";
    return t.toLowerCase(Locale.ROOT).replace('ё', 'е');
  }

  private static String[] splitTokens(String normalized) {
    String[] parts = normalized.split("\\s+");
    int valid = 0;
    for (String p : parts) if (!p.isEmpty()) valid++;
    if (valid == parts.length) return parts;
    String[] out = new String[valid];
    int idx = 0;
    for (String p : parts) if (!p.isEmpty()) out[idx++] = p;
    return out;
  }

  private static boolean allMatch(String[] tokens, String text) {
    if (text == null || text.isEmpty()) return false;
    for (int i = 0; i < tokens.length; i++) {
      if (!text.contains(tokens[i])) return false;
    }
    return true;
  }

  private static boolean allMatchCombined(String[] tokens, String primary, String secondary) {
    if (secondary == null || secondary.isEmpty()) return false;
    boolean hitSecondary = false;
    for (int i = 0; i < tokens.length; i++) {
      String tok = tokens[i];
      boolean inSec = secondary.contains(tok);
      if (inSec) {
        hitSecondary = true;
      } else if (primary == null || !primary.contains(tok)) {
        return false;
      }
    }
    return hitSecondary;
  }
}

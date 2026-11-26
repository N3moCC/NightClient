package dev.nightclient.skin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NcParser {
  public static final int MAX_BYTES = 524288, MAX_NODES = 8192, MAX_DEPTH = 32, MAX_RULES = 1024,
                          MAX_PROPERTIES = 128, MAX_VALUE = 4096;
  private static final java.util.regex.Pattern STATE_NAME =
      java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");
  private static final java.util.regex.Pattern IDENTIFIER =
      java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,95}");
  private static final java.util.regex.Pattern DOTTED_IDENTIFIER =
      java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,95}");
  private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
  private static final java.util.regex.Pattern HEX4 =
      java.util.regex.Pattern.compile("[0-9A-Fa-f]{4}");
  private static final Set<String> TAGS = new LinkedHashSet<String>(Arrays.asList("menu", "hud",
      "component", "div", "panel", "row", "column", "text", "game-text", "viewmodel-outline",
      "viewmodel-gizmo", "label", "icon", "image", "texture", "sprite", "entity-head",
      "player-preview", "cape-preview", "account-face", "button", "toggle", "switch", "slider", "input", "repeat", "item",
      "progress", "spacer", "crosshair", "rect", "circle", "line", "cooldowns", "inventory", "tnt",
      "waypoints", "armor", "potions", "food", "target", "scoreboard", "keystrokes", "coordinates",
      "ping", "fps", "betterChat", "minimap", "choice", "color", "pixel-grid",
      "color-plane", "hue-strip", "alpha-strip", "color-swatch"));
  private static final Set<String> ATTRIBUTES = new LinkedHashSet<String>(Arrays.asList("id",
      "auto-layout", "style", "class", "bind", "commit", "action", "dismiss-action",
      "secondary-action", "middle-action", "src", "source", "as", "key", "scope", "selection-state",
      "anchor", "if", "min", "max", "step", "value-scale", "value-offset", "maxlength", "format",
      "display-format", "name", "text", "unit", "options", "option-labels", "tooltip",
      "placeholder", "value", "visual", "search-input", "search-pages", "search-page", "wheel-next",
      "wheel-previous", "u", "v", "region-width", "region-height", "source-width", "source-height",
      "thickness", "axis-x-color", "axis-y-color", "axis-z-color"));

  private NcParser() {}

  public static SkinDocument parse(String source) throws IOException {
    if (source == null) throw new IOException("NC line 1, column 1: Empty skin");
    return parse(source.getBytes(StandardCharsets.UTF_8));
  }

  public static SkinDocument parse(byte[] source) throws IOException {
    if (source == null || source.length == 0)
      throw new IOException("NC line 1, column 1: Empty skin");
    if (source.length > MAX_BYTES)
      throw new IOException("NC line 1, column 1: Skin exceeds 512 KiB");
    String text;
    try {
      text = StandardCharsets.UTF_8.newDecoder()
                 .onMalformedInput(CodingErrorAction.REPORT)
                 .onUnmappableCharacter(CodingErrorAction.REPORT)
                 .decode(ByteBuffer.wrap(source))
                 .toString();
    } catch (CharacterCodingException error) {
      throw new IOException("NC line 1, column 1: Invalid UTF-8", error);
    }
    if (text.startsWith("\ufeff")) text = text.substring(1);
    return new Parser(new Lexer(text).tokens()).document();
  }

  private static final class Parser {
    private final List<Token> tokens;
    private final List<SkinStylesheet.Rule> rules = new ArrayList<SkinStylesheet.Rule>();
    private final List<RuleSource> ruleSources = new ArrayList<RuleSource>();
    private int at, nodes = 1;

    Parser(List<Token> tokens) {
      this.tokens = tokens;
    }

    SkinDocument document() throws IOException {
      List<SkinDocument.Node> children = new ArrayList<SkinDocument.Node>();
      Map<String, String> theme = new LinkedHashMap<String, String>();
      Map<String, String> defaults = new LinkedHashMap<String, String>();
      Map<String, String> state = new LinkedHashMap<String, String>();
      Map<Integer, Map<String, String>> themeStates =
          new LinkedHashMap<Integer, Map<String, String>>();
      boolean themeSeen = false, defaultsSeen = false;
      separators();
      while (!peek().is("EOF")) {
        Token start = peek();
        if (start.isWord("defaults")) {
          if (defaultsSeen) throw error(start, "Only one defaults block is allowed");
          defaultsSeen = true;
          next();
          newlines();
          expect("{");
          Block block = block(false, 1, 0);
          if (!block.states.isEmpty())
            throw error(start, "Defaults cannot have interaction states");
          defaults.putAll(block.values);
        } else if (start.isWord("theme")) {
          if (themeSeen) throw error(start, "Only one theme is allowed");
          themeSeen = true;
          next();
          newlines();
          expect("{");
          Block block = block(false, 1, 0);
          theme.putAll(block.values);
          themeStates.putAll(block.states);
        } else if (start.isWord("state")) {
          next();
          newlines();
          expect("{");
          separators();
          while (!peek().is("}")) {
            Token key = word("Expected a state name");
            if (!STATE_NAME.matcher(key.text).matches() || state.size() >= 64
                || state.containsKey("state:" + key.text))
              throw error(key, "Invalid, duplicate or excessive state name");
            expect("=");
            Token v = value();
            if (v.text.length() > 128) throw error(v, "State value exceeds 128 characters");
            String encoded;
            if (v.kind.equals("STRING"))
              encoded = "s:" + v.text;
            else if (v.text.equals("true") || v.text.equals("false"))
              encoded = "b:" + v.text;
            else {
              try {
                float n = Float.parseFloat(v.text);
                if (!Float.isFinite(n)) throw new NumberFormatException();
                encoded = "n:" + v.text;
              } catch (NumberFormatException e) {
                throw error(v, "State requires a string, boolean or finite number");
              }
            }
            state.put("state:" + key.text, encoded);
            endDeclaration();
            separators();
          }
          expect("}");
        } else if (start.isWord("style"))
          style();
        else if (start.isWord("menu") || start.isWord("hud") || start.isWord("component"))
          children.add(node(2, true));
        else
          throw error(start, "Expected defaults, theme, style, menu, hud or component");
        separators();
      }
      if (!themeSeen && !defaultsSeen && children.isEmpty() && rules.isEmpty())
        throw error(peek(), "Empty skin");
      SkinDocument.Node root =
          new SkinDocument.Node("nightclient", state, "", children, theme, themeStates, 1, 1);

      for (RuleSource entry : ruleSources) {
        Map<String, String> scope = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> value : theme.entrySet())
          if (value.getKey().startsWith("--")) scope.put(value.getKey(), value.getValue());
        scope.putAll(entry.values);
        try {
          SkinDocument.resolveVariables(scope);
          new ThemePatch(scope);
        } catch (IOException failure) {
          throw error(entry.start, nativeMessage(failure.getMessage()));
        }
      }
      try {
        return new SkinDocument(root, new SkinStylesheet(rules), defaults);
      } catch (IOException failure) {
        throw error(peek(), nativeMessage(failure.getMessage()));
      }
    }

    private void style() throws IOException {
      Token start = next(), kind = word("Style target must be element, class or id");
      if (!kind.isWord("element") && !kind.isWord("class") && !kind.isWord("id"))
        throw error(kind, "Style target must be element, class or id");
      Token name = value();
      if (kind.isWord("element")) {
        if (!TAGS.contains(name.text)) throw error(name, "Unknown element: " + name.text);
      } else
        validateIdentifier(name, "id".equals(kind.text));
      newlines();
      expect("{");
      Block block = block(false, 1, 0);
      addRule(kind.text, name.text, 0, block.values, start);
      for (Map.Entry<Integer, Map<String, String>> state : block.states.entrySet())
        addRule(kind.text, name.text, state.getKey(), state.getValue(), start);
    }

    private void addRule(String kind, String name, int state, Map<String, String> values,
        Token start) throws IOException {
      if (rules.size() >= MAX_RULES) throw error(start, "Skin exceeds 1024 style rules");
      rules.add(SkinStylesheet.nativeRule(kind, name, state, values, rules.size()));
      ruleSources.add(new RuleSource(values, start));
    }

    private SkinDocument.Node node(int depth, boolean top) throws IOException {
      Token start = word("Expected an element");
      String tag = start.text;
      if ("container".equals(tag)) {
        tag = "div";
        if (peek().kind.equals("WORD")
            && (peek().isWord("row") || peek().isWord("column") || peek().isWord("panel")
                || peek().isWord("div")))
          tag = next().text;
      }
      if (!TAGS.contains(tag)) throw error(start, "Unknown element: " + start.text);
      if (!top && (tag.equals("menu") || tag.equals("hud") || tag.equals("component")))
        throw error(start, "menu, hud and component declarations belong at the top level");
      if (++nodes > MAX_NODES) throw error(start, "Skin exceeds 4096 nodes");
      if (depth > MAX_DEPTH) throw error(start, "Skin exceeds 32 levels");
      Map<String, String> attributes = new LinkedHashMap<String, String>();
      String text = "";
      if ("component".equals(tag)) {
        Token name = value();
        validateIdentifier(name, true);
        attributes.put("name", name.text);
      } else if (peek().kind.equals("STRING"))
        text = next().text;
      if (peek().is("(")) parameters(attributes);
      if (peek().kind.equals("STRING")) {
        if (!text.isEmpty()) throw error(peek(), "An element can have one text label");
        text = next().text;
      }
      Map<String, String> styles = Collections.emptyMap();
      Map<Integer, Map<String, String>> states = Collections.emptyMap();
      List<SkinDocument.Node> children = Collections.emptyList();
      int saved = at;
      newlines();
      if (peek().is("{")) {
        next();
        Block block = block(true, depth, 0);
        styles = block.values;
        states = block.states;
        children = block.children;
      } else {
        at = saved;
        if (top) throw error(peek(), tag + " requires a block");
        if (!peek().is("NEWLINE") && !peek().is(";") && !peek().is("}") && !peek().is("EOF"))
          throw error(peek(), "Separate elements with a newline or semicolon");
      }
      return new SkinDocument.Node(
          tag, attributes, text, children, styles, states, start.line, start.column);
    }

    private void parameters(Map<String, String> attributes) throws IOException {
      expect("(");
      newlines();
      while (!peek().is(")")) {
        Token key = word("Expected an element parameter");
        String name = propertyName(key.text);
        if (!ATTRIBUTES.contains(name)) throw error(key, "Unknown element parameter: " + key.text);
        expect("=");
        Token value = value();
        String text = value.text;
        if ("dismiss-action".equals(name) && text.length() > 512)
          throw error(value, "Dismiss action exceeds 512 characters");
        if (("id".equals(name) || "name".equals(name)) && !text.contains("{{"))
          validateIdentifier(value, true);
        if ("style".equals(name) || "class".equals(name)) {
          String trimmed = text.trim();
          if (trimmed.isEmpty()) throw error(value, "Style class cannot be empty");
          for (String item : WHITESPACE.split(trimmed))
            if (!IDENTIFIER.matcher(item).matches())
              throw error(value, "Invalid style class: " + item);
          name = "class";
          text = trimmed;
        }
        if (attributes.containsKey(name))
          throw error(key, "Duplicate element parameter: " + key.text);
        attributes.put(name, text);
        newlines();
        if (peek().is(",")) {
          next();
          newlines();
          if (peek().is(")")) break;
        } else if (!peek().is(")"))
          throw error(peek(), "Expected ',' or ')' after a parameter");
      }
      expect(")");
    }

    private Block block(boolean childrenAllowed, int depth, int state) throws IOException {
      Block result = new Block();
      separators();
      while (!peek().is("}")) {
        if (peek().is("EOF")) throw error(peek(), "Unclosed block");
        Token start = peek();
        int interaction = interaction(start.text);
        if (start.kind.equals("WORD") && interaction > 0 && followedByBlock()) {
          next();
          newlines();
          expect("{");
          if (depth >= MAX_DEPTH) throw error(start, "Skin exceeds 32 levels");
          Block nested = block(false, depth + 1, state | interaction);
          mergeState(result, state | interaction, nested.values, start);
          for (Map.Entry<Integer, Map<String, String>> variant : nested.states.entrySet())
            mergeState(result, variant.getKey(), variant.getValue(), start);
        } else if (start.isWord("let")) {
          next();
          Token name = word("Expected a variable name");
          validateIdentifier(name, false);
          expect("=");
          declaration(result.values, "--" + name.text, value(), name);
          endDeclaration();
        } else if (start.kind.equals("WORD") && look(1).is("=")) {
          next();
          expect("=");
          String key = propertyName(start.text);
          if (!SkinStylesheet.PROPERTIES.contains(key))
            throw error(start, "Unknown style property: " + start.text);
          declaration(result.values, key, value(), start);
          endDeclaration();
        } else if (childrenAllowed)
          result.children.add(node(depth + 1, false));
        else
          throw error(start, "Expected a style property or an interaction block");
        separators();
      }
      expect("}");
      return result;
    }

    private void mergeState(Block target, int state, Map<String, String> values, Token token)
        throws IOException {
      Map<String, String> previous = target.states.get(state);
      if (previous == null) {
        previous = new LinkedHashMap<String, String>();
        target.states.put(state, previous);
      }
      for (Map.Entry<String, String> entry : values.entrySet()) {
        if (previous.containsKey(entry.getKey()))
          throw error(token, "Duplicate interaction property: " + entry.getKey());
        if (previous.size() >= MAX_PROPERTIES) throw error(token, "A block exceeds 128 properties");
        previous.put(entry.getKey(), entry.getValue());
      }
    }

    private void declaration(Map<String, String> target, String key, Token value, Token property)
        throws IOException {
      if (target.containsKey(key))
        throw error(property, "Duplicate style property: " + property.text);
      if (target.size() >= MAX_PROPERTIES) throw error(property, "A block exceeds 128 properties");
      String raw = value.text;
      if (raw.startsWith("$")) {
        String name = raw.substring(1);
        if (!IDENTIFIER.matcher(name).matches())
          throw error(value, "Invalid variable reference: " + raw);
        raw = "var(--" + name + ")";
      } else if (raw.contains("var(") || raw.toLowerCase(Locale.ROOT).contains("url(")
          || raw.toLowerCase(Locale.ROOT).contains("expression(")
          || raw.toLowerCase(Locale.ROOT).contains("javascript:"))
        throw error(value,
            "Use native values and $variables; executable or external values are"
                + " unsupported");
      if (!raw.contains("var(") && !key.startsWith("--")) {
        try {
          new ThemePatch(Collections.singletonMap(key, raw));
        } catch (IOException failure) {
          throw error(value, nativeMessage(failure.getMessage()));
        }
      }
      target.put(key, raw);
    }

    private void endDeclaration() throws IOException {
      if (!peek().is("NEWLINE") && !peek().is(";") && !peek().is("}"))
        throw error(peek(), "Separate properties with a newline or semicolon");
    }

    private boolean followedByBlock() {
      int index = at + 1;
      while (tokens.get(index).is("NEWLINE")) index++;
      return tokens.get(index).is("{");
    }

    private static int interaction(String name) {
      return "hover".equals(name) ? 1 : "focus".equals(name) ? 2 : "active".equals(name) ? 4 : 0;
    }

    private static String propertyName(String name) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (Character.isUpperCase(c)) {
          if (i > 0) out.append('-');
          out.append(Character.toLowerCase(c));
        } else
          out.append(c);
      }
      return out.toString();
    }

    private void validateIdentifier(Token token, boolean dotted) throws IOException {
      if (!(dotted ? DOTTED_IDENTIFIER : IDENTIFIER).matcher(token.text).matches())
        throw error(token, "Invalid identifier: " + token.text);
    }

    private Token value() throws IOException {
      Token token = peek();
      if (!token.kind.equals("WORD") && !token.kind.equals("STRING"))
        throw error(token, "Expected a value");
      next();
      if (token.text.isEmpty() && token.kind.equals("WORD")) throw error(token, "Empty value");
      return token;
    }

    private Token word(String message) throws IOException {
      Token token = peek();
      if (!token.kind.equals("WORD")) throw error(token, message);
      return next();
    }

    private Token expect(String kind) throws IOException {
      Token token = peek();
      if (!token.is(kind)) throw error(token, "Expected '" + kind + "'");
      return next();
    }

    private Token next() {
      return tokens.get(at++);
    }

    private Token peek() {
      return tokens.get(at);
    }

    private Token look(int distance) {
      return tokens.get(Math.min(tokens.size() - 1, at + distance));
    }

    private void separators() {
      while (peek().is("NEWLINE") || peek().is(";")) next();
    }

    private void newlines() {
      while (peek().is("NEWLINE")) next();
    }
  }

  private static final class Block {
    final Map<String, String> values = new LinkedHashMap<String, String>();
    final Map<Integer, Map<String, String>> states =
        new LinkedHashMap<Integer, Map<String, String>>();
    final List<SkinDocument.Node> children = new ArrayList<SkinDocument.Node>();
  }

  private static final class RuleSource {
    final Map<String, String> values;
    final Token start;

    RuleSource(Map<String, String> values, Token start) {
      this.values = values;
      this.start = start;
    }
  }

  private static final class Token {
    final String kind, text;
    final int line, column;

    Token(String kind, String text, int line, int column) {
      this.kind = kind;
      this.text = text;
      this.line = line;
      this.column = column;
    }

    boolean is(String value) {
      return kind.equals(value);
    }

    boolean isWord(String value) {
      return kind.equals("WORD") && text.equals(value);
    }
  }

  private static final class Lexer {
    private final String source;
    private int at, line = 1, column = 1;

    Lexer(String source) {
      this.source = source;
    }

    List<Token> tokens() throws IOException {
      List<Token> tokens = new ArrayList<Token>();
      while (at < source.length()) {
        char c = source.charAt(at);
        int startLine = line, startColumn = column;
        if (c == '\n' || c == '\r') {
          advance();
          if (c == '\r' && at < source.length() && source.charAt(at) == '\n') at++;
          tokens.add(new Token("NEWLINE", "", startLine, startColumn));
          continue;
        }
        if (Character.isWhitespace(c)) {
          advance();
          continue;
        }
        if (c == '/' && at + 1 < source.length() && source.charAt(at + 1) == '/') {
          while (at < source.length() && source.charAt(at) != '\n' && source.charAt(at) != '\r')
            advance();
          continue;
        }
        if ("{}()=,;".indexOf(c) >= 0) {
          advance();
          tokens.add(new Token(String.valueOf(c), String.valueOf(c), startLine, startColumn));
          continue;
        }
        if (c == '"' || c == '\'') {
          tokens.add(string(c, startLine, startColumn));
          continue;
        }
        StringBuilder text = new StringBuilder();
        while (at < source.length()) {
          c = source.charAt(at);
          if (Character.isWhitespace(c) || "{}()=,;\"'".indexOf(c) >= 0
              || (c == '/' && at + 1 < source.length() && source.charAt(at + 1) == '/'))
            break;
          if (Character.isISOControl(c))
            throw error(new Token("", "", line, column), "Unexpected control character");
          text.append(c);
          advance();
          if (text.length() > MAX_VALUE)
            throw error(
                new Token("", "", startLine, startColumn), "A value exceeds 4096 characters");
        }
        if (text.length() == 0)
          throw error(new Token("", "", line, column), "Unexpected character");
        tokens.add(new Token("WORD", text.toString(), startLine, startColumn));
      }
      tokens.add(new Token("EOF", "", line, column));
      return tokens;
    }

    private Token string(char quote, int startLine, int startColumn) throws IOException {
      advance();
      StringBuilder out = new StringBuilder();
      while (at < source.length()) {
        char c = source.charAt(at);
        advance();
        if (c == quote) return new Token("STRING", out.toString(), startLine, startColumn);
        if (c == '\r' || c == '\n')
          throw error(new Token("", "", startLine, startColumn),
              "Unclosed string; use \\n for a text line break");
        if (c == '\\') {
          if (at == source.length()) break;
          c = source.charAt(at);
          advance();
          if (c == 'n')
            c = '\n';
          else if (c == 'r')
            c = '\r';
          else if (c == 't')
            c = '\t';
          else if (c == 'u') {
            if (at + 4 > source.length())
              throw error(new Token("", "", line, column), "Incomplete Unicode escape");
            String digits = source.substring(at, at + 4);
            if (!HEX4.matcher(digits).matches())
              throw error(new Token("", "", line, column), "Invalid Unicode escape");
            c = (char) Integer.parseInt(digits, 16);
            for (int i = 0; i < 4; i++) advance();
          } else if (c != '\\' && c != '"' && c != '\'')
            throw error(new Token("", "", line, column - 1), "Unsupported string escape");
        } else if (Character.isISOControl(c))
          throw error(
              new Token("", "", line, column - 1), "Unexpected control character in string");
        out.append(c);
        if (out.length() > MAX_VALUE)
          throw error(new Token("", "", startLine, startColumn), "A value exceeds 4096 characters");
      }
      throw error(new Token("", "", startLine, startColumn), "Unclosed string");
    }

    private void advance() {
      char c = source.charAt(at++);
      if (c == '\n' || c == '\r') {
        line++;
        column = 1;
      } else
        column++;
    }
  }

  private static IOException error(Token token, String message) {
    return new IOException("NC line " + token.line + ", column " + token.column + ": " + message);
  }

  private static String nativeMessage(String value) {
    return value == null ? "Invalid skin"
                         : value.replace("CSS ", "").replace("computed CSS", "computed");
  }
}

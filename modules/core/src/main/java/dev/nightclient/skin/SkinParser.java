package dev.nightclient.skin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public final class SkinParser {
  public static final int MAX_BYTES = 65536, MAX_NODES = 512, MAX_DEPTH = 32;
  private static final Set<String> TAGS = new LinkedHashSet<String>(Arrays.asList("nightclient",
      "style", "menu", "hud", "component", "div", "panel", "row", "column", "text", "label", "icon",
      "button", "toggle", "switch", "slider", "input", "repeat", "item", "progress", "spacer",
      "crosshair", "rect", "circle", "line", "cooldowns", "inventory", "tnt", "scoreboard",
      "keystrokes", "coordinates", "ping", "fps", "betterChat", "minimap"));

  private SkinParser() {}

  public static SkinDocument parse(String source) throws IOException {
    if (source == null) throw new IOException("Empty skin");
    return parse(source.getBytes(StandardCharsets.UTF_8));
  }

  public static SkinDocument parse(byte[] source) throws IOException {
    if (source == null || source.length == 0) throw new IOException("Empty skin");
    if (source.length > MAX_BYTES) throw new IOException("Skin exceeds 64 KiB");
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      XMLReader reader = factory.newSAXParser().getXMLReader();
      reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Builder builder = new Builder();
      reader.setContentHandler(builder);
      reader.setErrorHandler(builder);
      reader.setEntityResolver(
          (publicId, systemId) -> { throw new SAXException("External entities are forbidden"); });
      reader.parse(new InputSource(new ByteArrayInputStream(source)));
      if (builder.root == null || !"nightclient".equals(builder.root.tag))
        throw new IOException("Root element must be <nightclient>");
      return new SkinDocument(builder.root.freeze());
    } catch (IOException error) {
      throw error;
    } catch (Exception error) {
      throw new IOException("Invalid skin: " + error.getMessage(), error);
    }
  }

  private static final class Builder extends DefaultHandler {
    final Deque<Mutable> stack = new ArrayDeque<Mutable>();
    Mutable root;
    int nodes;

    @Override
    public void startElement(String uri, String local, String q, Attributes attrs)
        throws SAXException {
      if (!uri.isEmpty()) throw new SAXException("XML namespaces are not supported");
      String tag = local.toLowerCase(Locale.ROOT);
      if ("betterchat".equals(tag)) tag = "betterChat";
      if (!TAGS.contains(tag)) throw new SAXException("Unsupported element: " + tag);
      if (++nodes > MAX_NODES) throw new SAXException("Skin exceeds 512 nodes");
      if (stack.size() >= MAX_DEPTH) throw new SAXException("Skin exceeds 32 levels");
      Map<String, String> a = new LinkedHashMap<String, String>();
      for (int i = 0; i < attrs.getLength(); i++) {
        if (!attrs.getURI(i).isEmpty())
          throw new SAXException("Namespaced attributes are not supported");
        String name = attrs.getLocalName(i).toLowerCase(Locale.ROOT);
        if (name.startsWith("on"))
          throw new SAXException("Use declarative action instead of event scripts");
        if ("dismiss-action".equals(name) && attrs.getValue(i).length() > 512)
          throw new SAXException("Dismiss action exceeds 512 characters");
        a.put(name, attrs.getValue(i));
      }
      Mutable node = new Mutable(tag, a);
      if (stack.isEmpty()) {
        if (root != null) throw new SAXException("Multiple roots");
        root = node;
      } else
        stack.peek().children.add(node);
      stack.push(node);
    }

    @Override
    public void endElement(String uri, String local, String q) {
      stack.pop();
    }

    @Override
    public void characters(char[] data, int start, int length) {
      if (!stack.isEmpty()) stack.peek().text.append(data, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
      throw new SAXException("Processing instructions are not supported");
    }

    @Override
    public void error(org.xml.sax.SAXParseException e) throws SAXException {
      throw e;
    }

    @Override
    public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
      throw e;
    }
  }

  private static final class Mutable {
    final String tag;
    final Map<String, String> attrs;
    final StringBuilder text = new StringBuilder();
    final List<Mutable> children = new ArrayList<Mutable>();

    Mutable(String tag, Map<String, String> attrs) {
      this.tag = tag;
      this.attrs = attrs;
    }

    SkinDocument.Node freeze() {
      List<SkinDocument.Node> nodes = new ArrayList<SkinDocument.Node>();
      for (Mutable child : children) nodes.add(child.freeze());
      return new SkinDocument.Node(tag, attrs, text.toString().trim(), nodes);
    }
  }
}

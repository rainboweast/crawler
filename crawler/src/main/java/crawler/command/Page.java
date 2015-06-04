package crawler.command;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author chi
 */
public class Page implements Iterable<Element> {
    private final Document document;
    private final Map<String, Element> cache = Maps.newHashMap();
    private final Map<String, List<Element>> idCache = Maps.newHashMap();
    private final Map<String, List<Element>> classNameCache = Maps.newHashMap();
    private final Map<String, List<Element>> tagCache = Maps.newHashMap();
    private final List<Element> nodes = Lists.newArrayList();

    public Page(Document document) {
        this.document = document;

        Deque<Element> nodes = new LinkedList<>();
        nodes.add(document);

        while (!nodes.isEmpty()) {
            Element node = nodes.pollFirst();
            add(node);
            Lists.reverse(node.children()).forEach(nodes::addFirst);
        }

        for (Element node : this) {
            cache.put(layoutKey(node), node);
        }
    }

    public static void help(String url) throws Exception {
        Page page = Page.of(Jsoup.parse(new URL(url), 10 * 1000));

        Set<String> excludeTags = new HashSet<>();
        excludeTags.add("#root");
        excludeTags.add("html");
        excludeTags.add("body");

        for (Element node : page) {
            if (!excludeTags.contains(node.tagName())) {
                System.out.println(String.format("%s=%s", page.layoutKey(node), node.text()));
            }
        }
    }

    public static Page of(Document document) {
        return new Page(document);
    }

    public Element element(String key) {
        return cache.get(key);
    }

    public Elements select(String key) {
        return document.select(key);
    }

    Page add(Element node) {
        nodes.add(node);

        if (node.hasAttr("id")) {
            if (idCache.containsKey(node.attr("id"))) {
                idCache.get(node.attr("id")).add(node);
            } else {
                idCache.put(node.attr("id"), Lists.newArrayList(node));
            }
        }

        if (node.hasAttr("class")) {
            String classNames = node.attr("class");
            for (String className : classNames.split("\\s")) {
                if (classNameCache.containsKey(className)) {
                    classNameCache.get(className).add(node);
                } else {
                    classNameCache.put(className, Lists.newArrayList(node));
                }
            }
        }

        String tagName = node.tagName();
        if (tagCache.containsKey(tagName)) {
            tagCache.get(tagName).add(node);
        } else {
            tagCache.put(tagName, Lists.newArrayList(node));
        }

        return this;
    }

    String layoutKey(Element node) {
        StringBuilder b = new StringBuilder();

        Element current = node;

        while (current != null) {
            String id = getUniqueId(current);

            if (!Strings.isNullOrEmpty(id)) {
                b.insert(0, "#" + id);
                break;
            }

            String className = getUniqueClassName(current);

            if (!Strings.isNullOrEmpty(className)) {
                b.insert(0, "." + className);
                break;
            }

            String tagName = getUniqueTag(node);

            if (!Strings.isNullOrEmpty(tagName)) {
                b.insert(0, tagName);
                break;
            }

            b.insert(0, " " + nodeKey(current));
            current = current.parent();
        }

        return b.toString();
    }

    String getUniqueId(Element node) {
        String id = node.attr("id");
        if (idCache.containsKey(id) && idCache.get(id).size() == 1) {
            return id;
        }
        return null;
    }

    String getUniqueClassName(Element node) {
        String classNames = node.attr("class");
        for (String className : classNames.split("\\s")) {
            if (classNameCache.containsKey(className)) {
                if (classNameCache.get(className).size() == 1) {
                    return className;
                }
            }
        }
        return null;
    }

    String getUniqueTag(Element element) {
        if (tagCache.containsKey(element.tagName()) && tagCache.get(element.tagName()).size() == 1) {
            return element.tagName();
        }
        return null;
    }

    String nodeKey(Element node) {
        if (node.parent() == null) {
            return node.nodeName();
        }

        Node parent = node.parent();
        for (int i = 0; i < parent.childNodes().size(); i++) {
            Node n = parent.childNodes().get(i);
            if (node.equals(n)) {
                return node.tagName() + '[' + i + ']';
            }
        }

        return null;
    }

    public String text(String key) {
        return text(key, null);
    }


    public String text(String key, String defaultValue) {
        Element element = element(key);
        if (element == null) {
            return defaultValue;
        }
        return element.text();
    }

    public String cleanHtml(Element element) {
        Deque<Element> deque = new LinkedList<>();

        deque.push(element);

        while (!deque.isEmpty()) {
            Element e = deque.poll();

            if (e.tagName().equals("img") || e.tagName().equals("script") || e.tagName().equals("link")) {
                e.remove();
            } else if (e.tagName().equals("br")) {
                if (e.nextElementSibling() != null && e.nextElementSibling().tagName().equals("br")) {
                    e.remove();
                }
            } else {
                for (Attribute attribute : e.attributes()) {
                    e.removeAttr(attribute.getKey());
                }

                for (Element child : e.children()) {
                    deque.push(child);
                }
            }
        }

        return element.html();
    }


    public String attr(String key, String name) {
        Element element = element(key);
        if (element == null) {
            return null;
        }

        return element.attr(name);
    }

    @Override
    public Iterator<Element> iterator() {
        return nodes.iterator();
    }
}

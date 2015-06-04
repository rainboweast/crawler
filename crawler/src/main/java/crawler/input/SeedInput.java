package crawler.input;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import crawler.Context;
import crawler.Input;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author chi
 */
public class SeedInput implements Input {
    private final Queue<Context> queue = new ConcurrentLinkedQueue<>();
    private final List<Pattern> urlPatterns = Lists.newArrayList();
    private final int waitSeconds = 5;
    private DB db;

    public SeedInput(String... urls) {
        for (String url : urls) {
            queue.add(new Context(url));
        }

        db = DBMaker.newMemoryDB()
            .closeOnJvmShutdown()
            .make();
    }

    public SeedInput includes(String... urlPatterns) {
        for (String urlPattern : urlPatterns) {
            this.urlPatterns.add(Pattern.compile(urlPattern));
        }
        return this;
    }

    public SeedInput file(File file) {
        db = DBMaker.newFileDB(file)
            .closeOnJvmShutdown()
            .make();
        return this;
    }

    @Override
    public Iterator<Context> iterator() {
        return new Iterator<Context>() {
            Context context;

            @Override
            public boolean hasNext() {
                context = queue.poll();

                if (context == null) {
                    try {
                        TimeUnit.SECONDS.sleep(waitSeconds);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    context = queue.poll();
                }

                return context != null;
            }

            @Override
            public Context next() {
                crawled(context.url());
                return context;
            }
        };
    }

    protected boolean isInclude(String url) {
        if (Strings.isNullOrEmpty(url)) {
            return false;
        }

        if (urlPatterns.isEmpty()) {
            return true;
        }

        for (Pattern urlPattern : urlPatterns) {
            if (urlPattern.matcher(url).matches()) {
                return true;
            }
        }

        return false;
    }

    boolean isCrawled(String url) {
        return db.getHashMap("history").get(url) != null;
    }

    void crawled(String url) {
        db.getHashMap("history").put(url, "");
    }

    @Override
    public void end(Context context) {
        Document document = context.get(Document.class);
        if (document != null) {
            for (Element element : document.select("a")) {
                String url = element.attr("abs:href");
                if (isInclude(url) && !isCrawled(url)) {
                    queue.add(new Context(url));
                    crawled(url);
                }
            }
        }
    }
}

package crawler.input;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import crawler.Context;
import crawler.Input;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * @author chi
 */
public class SeedInput implements Input {
    final Logger logger = LoggerFactory.getLogger(SeedInput.class);

    private final Queue<Context> queue = new ConcurrentLinkedQueue<>();
    private final List<Pattern> urlPatterns = Lists.newArrayList();
    private final int waitSeconds = 5;
    private BloomFilter<CharSequence> filter;
    private AtomicInteger count = new AtomicInteger();
    private static final int resetCount = 1000;
    private final File backupFile;

    public SeedInput(File backupFile, String... urls) {
        for (String url : urls) {
            queue.add(new Context(url));
        }
        this.backupFile = backupFile;
        try {
            filter = backupFile.exists() ? BloomFilter.readFrom(new FileInputStream(backupFile), Funnels.stringFunnel(Charset.defaultCharset())) :
                    BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), 10000 * 1000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SeedInput includes(String... urlPatterns) {
        for (String urlPattern : urlPatterns) {
            this.urlPatterns.add(Pattern.compile(urlPattern));
        }
        return this;
    }

    public SeedInput file(File file) {
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

    boolean notCrawled(String url) {
        return !filter.mightContain(url);
    }

    void crawled(String url) {
        filter.put(url);
        if (count.incrementAndGet() == resetCount) {
            count.set(0);
            try {
                filter.writeTo(new FileOutputStream(backupFile));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public void end(Context context) {
        Document document = context.get(Document.class);
        if (document != null) {
            for (Element element : document.select("a")) {
                String url = element.attr("abs:href");
                if (isInclude(url) && notCrawled(url)) {
                    queue.add(new Context(url));
                    crawled(url);
                }
            }
        }
    }
}

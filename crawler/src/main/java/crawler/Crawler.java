package crawler;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BestMatchSpecFactory;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * @author chi
 */
public class Crawler {
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.101 Safari/537.36";
    private final List<Pipe> pipes = Lists.newArrayList();
    private final Logger logger = LoggerFactory.getLogger(Crawler.class);
    private final HttpClient httpClient = httpClient();
    private final int concurrencyLevel;
    private final Command get;
    private Input input;

    public Crawler(int concurrencyLevel, String userAgent, String cookies) {
        Preconditions.checkState(concurrencyLevel > 0, "concurrency level must be larger than 0");
        this.concurrencyLevel = concurrencyLevel;

        get = new Command() {
            HttpClientContext httpClientContext = HttpClientContext.create();

            {
                if (!Strings.isNullOrEmpty(cookies)) {
                    Registry<CookieSpecProvider> r = RegistryBuilder.<CookieSpecProvider>create()
                        .register(CookieSpecs.BEST_MATCH, new BestMatchSpecFactory())
                        .register(CookieSpecs.BROWSER_COMPATIBILITY, new BrowserCompatSpecFactory())
                        .build();

                    CookieStore cookieStore = new BasicCookieStore();
                    parseCookies(cookies).forEach(cookieStore::addCookie);

                    httpClientContext.setCookieSpecRegistry(r);
                    httpClientContext.setCookieStore(cookieStore);
                }
            }

            private List<Cookie> parseCookies(String cookies) {
                List<Cookie> list = Lists.newArrayList();

                for (String cookieStr : cookies.split(";")) {
                    int index = cookieStr.indexOf("=");
                    if (index > 0) {
                        BasicClientCookie cookie = new BasicClientCookie(cookieStr.substring(0, index).trim(),
                            cookieStr.substring(index + 1).trim());
                        cookie.setVersion(0);
//                cookie.setDomain("." + domain);
                        cookie.setPath("/");
                        list.add(cookie);
                    }
                }
                return list;
            }

            @Override
            public boolean execute(Context context) {
                HttpGet get = new HttpGet(context.url());
                try {
                    Stopwatch w = Stopwatch.createStarted();
                    get.setHeader("User-Agent", userAgent);
                    HttpResponse response = httpClient.execute(get, httpClientContext);
                    byte[] content = EntityUtils.toByteArray(response.getEntity());
                    context.put(byte[].class, content);
                    logger.info("get {}, {}ms", context.url(), w.elapsed(TimeUnit.MILLISECONDS));
                    return true;
                } catch (Exception e) {
                    logger.error("failed to crawl {}", context.url(), e);
                    throw new RuntimeException(e);
                } finally {
                    get.releaseConnection();
                }
            }
        };
    }

    public Crawler(int concurrencyLevel) {
        this(concurrencyLevel, DEFAULT_USER_AGENT);
    }

    public Crawler(int concurrencyLevel, String userAgent) {
        this(concurrencyLevel, userAgent, null);
    }

    public static HttpClient httpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(100);

        RequestConfig requestConfig = RequestConfig.custom()
            .setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY)
            .setExpectContinueEnabled(true)
            .setStaleConnectionCheckEnabled(true)
            .build();

        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .disableConnectionState()
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    public Pipe when(String urlPattern) {
        Pipe pipe = new Pipe(Pattern.compile(urlPattern));
        pipe.commands.add(get);
        pipes.add(pipe);
        return pipe;
    }

    public Crawler input(Input input) {
        this.input = input;
        return this;
    }

    public void crawl() {
        Preconditions.checkNotNull(input, "missing input");
        logger.info("start crawling");
        Stopwatch stopwatch = Stopwatch.createStarted();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(concurrencyLevel, concurrencyLevel, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100));

        int total = 0;
        AtomicInteger totalSucceed = new AtomicInteger(0);

        for (final Context context : input) {
            total++;
            pool.execute(() -> {
                try {
                    doCrawl(context);
                    totalSucceed.incrementAndGet();
                } catch (Exception e) {
                    logger.error("failed to crawl {}, {}", context.url(), e.getMessage());
                }
            });
        }

        try {
            while (pool.getCompletedTaskCount() < pool.getTaskCount()) {
                TimeUnit.MILLISECONDS.sleep(100);
            }

            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            logger.info("done, {}/{}, in {}ms", totalSucceed.get(), total, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("unchecked")
    protected void doCrawl(Context context) {
        boolean processed = false;

        for (Pipe pipe : pipes) {
            if (pipe.accept(context.url())) {
                processed = true;
                pipe.execute(context);
                input.end(context);
            }
        }

        if (!processed) {
            logger.info("skip url {}", context.url());
        }
    }

    public class Pipe {
        Pattern urlPattern;
        List<Command> commands = Lists.newArrayList();

        public Pipe(Pattern urlPattern) {
            this.urlPattern = urlPattern;
        }

        public Crawler then(Command... commands) {
            this.commands.addAll(Arrays.asList(commands));
            return Crawler.this;
        }

        public boolean accept(String url) {
            return urlPattern.matcher(url).matches();
        }

        public void execute(Context context) {
            for (Command command : commands) {
                boolean success = command.execute(context);
                if (!success) {
                    break;
                }
            }
        }
    }
}

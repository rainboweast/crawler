package crawler.command;

import crawler.Command;
import crawler.Context;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author chi
 */
public abstract class Parse implements Command {
    private final Logger logger = LoggerFactory.getLogger(Parse.class);

    @Override
    public boolean execute(Context context) {
        logger.info("parse {}", context.url());
        Map<String, Object> values = parse(context);
        context.put(Map.class, values);
        return true;
    }

    protected abstract Map<String, Object> parse(Context context);

    protected Page page(Context context) {
        return new Page(context.get(Document.class));
    }
}

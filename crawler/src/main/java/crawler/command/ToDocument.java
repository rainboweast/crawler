package crawler.command;

import crawler.Command;
import crawler.Context;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.charset.Charset;

/**
 * @author chi
 */
public class ToDocument implements Command {
    private final Charset charset;

    public ToDocument(Charset charset) {
        this.charset = charset;
    }

    @Override
    public boolean execute(Context context) {
        Document document = Jsoup.parse(new String(context.get(byte[].class), charset));
        document.setBaseUri(context.url());
        context.put(Document.class, document);
        return true;
    }
}

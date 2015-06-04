package crawler.input;

import crawler.Context;
import crawler.Input;
import org.slf4j.helpers.MessageFormatter;

import java.util.Iterator;

/**
 * @author chi
 */
public class RangeInput implements Input {
    private final String urlTemplate;
    private final int start;
    private final int end;

    public RangeInput(String urlTemplate, int start, int end) {
        this.urlTemplate = urlTemplate;
        this.start = start;
        this.end = end;
    }

    @Override
    public Iterator<Context> iterator() {
        return new Iterator<Context>() {
            int index = start;

            @Override
            public boolean hasNext() {
                return index < end;
            }

            @Override
            public Context next() {
                String url = MessageFormatter.arrayFormat(urlTemplate, new Object[]{index}).getMessage();
                index++;
                return new Context(url);
            }
        };
    }

    @Override
    public void end(Context context) {
    }
}

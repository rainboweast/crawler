package crawler.input;


import com.google.common.collect.Lists;
import crawler.Context;
import crawler.Input;

import java.util.Iterator;
import java.util.List;

/**
 * @author chi
 */
public class ArrayInput implements Input {
    public final List<String> urls;

    public ArrayInput(String... urls) {
        this.urls = Lists.newArrayList(urls);
    }

    @Override
    public Iterator<Context> iterator() {
        final Iterator<String> iterator = urls.iterator();

        return new Iterator<Context>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Context next() {
                return new Context(iterator.next());
            }
        };
    }

    @Override
    public void end(Context context) {
    }
}

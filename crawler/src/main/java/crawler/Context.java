package crawler;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chi
 */
public class Context {
    private final String url;
    private final Map<Class<?>, Object> values = new HashMap<>();

    public Context(String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        return (T) values.get(type);
    }

    public <T> void put(Class<T> type, T value) {
        values.put(type, value);
    }
}

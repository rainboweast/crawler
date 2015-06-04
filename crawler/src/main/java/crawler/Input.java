package crawler;

/**
 * @author chi
 */
public interface Input extends Iterable<Context> {
    void end(Context context);
}

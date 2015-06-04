package crawler;

import java.util.Map;

/**
 * @author chi
 */
public class Print implements Command {
    @Override
    public boolean execute(Context context) {
        System.out.println(context.get(Map.class));
        return false;
    }
}

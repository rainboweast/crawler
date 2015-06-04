package crawler;

import crawler.command.Page;
import crawler.command.Parse;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chi
 */
public class ParseProduct extends Parse {
    @Override
    public Map<String, Object> parse(Context context) {
        Page page = page(context);

        Map<String, Object> product = new HashMap<>();
        product.put("name", page.element("h1").text());
        product.put("price", page.select("#guys  h2 .active_price").text());
        product.put("description", page.select("#guys .select_desc").text());

        return product;
    }
}

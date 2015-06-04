package crawler;

import com.google.common.base.Charsets;
import crawler.command.ToDocument;
import crawler.input.RangeInput;

/**
 * @author chi
 */
public class CrawlerTest {
    public static void main(String[] args) {
//        SeedInput input = new SeedInput("https://www.threadless.com/product/6503/");

        RangeInput input = new RangeInput("https://www.threadless.com/product/{}/", 6000, 6100);

        new Crawler(2).input(input)
            .when("https://www.threadless.com/product/\\d+/")
            .then(
                new ToDocument(Charsets.UTF_8),
                new ParseProduct(),
                new Print())
            .crawl();
    }
}
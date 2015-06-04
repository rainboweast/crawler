package crawler;

import crawler.command.Page;
import org.junit.Test;

/**
 * @author chi
 */
public class PageTest {
    @Test
    public void help() throws Exception {
        Page.help("https://www.threadless.com/product/6503/");
    }

}
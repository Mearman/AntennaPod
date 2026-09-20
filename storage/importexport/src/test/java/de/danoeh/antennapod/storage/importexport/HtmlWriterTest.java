package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.Feed;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class HtmlWriterTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private Feed createFeed(String title, String downloadUrl, String link, String imageUrl) {
        return new Feed(0, null, title, link, null, null, null, null, null, null, imageUrl, null, downloadUrl, 0);
    }

    private String write(Feed... feeds) throws Exception {
        StringWriter writer = new StringWriter();
        HtmlWriter.writeDocument(Arrays.asList(feeds), writer, context);
        return writer.toString();
    }

    @Test
    public void templateTitleIsFilledAndFeedPlaceholderIsReplaced() throws Exception {
        String html = write(createFeed("Feed", "https://example.com/feed.xml", "https://example.com",
                "https://example.com/image.png"));

        assertTrue(html.contains("<title>AntennaPod Subscriptions</title>"));
        assertTrue(html.contains("<h1>AntennaPod Subscriptions</h1>"));
        assertFalse(html.contains("{TITLE}"));
        assertFalse(html.contains("{FEEDS}"));
    }

    @Test
    public void subscribedFeedIsWrittenWithImageTitleAndLinks() throws Exception {
        String html = write(createFeed("My Podcast", "https://example.com/feed.xml", "https://example.com/site",
                "https://example.com/image.png"));

        assertTrue(html.contains("<li><div><img src=\"https://example.com/image.png\" /><p>My Podcast"
                + " <span><a href=\"https://example.com/site\">Website</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/feed.xml\">Feed</a></span></p></div></li>"));
    }

    @Test
    public void feedsAreWrittenInGivenOrder() throws Exception {
        String html = write(
                createFeed("First Feed", "https://example.com/1.xml", "https://example.com/1", "https://example.com/1.png"),
                createFeed("Second Feed", "https://example.com/2.xml", "https://example.com/2", "https://example.com/2.png"));

        assertTrue(html.indexOf("First Feed") < html.indexOf("Second Feed"));
    }

    @Test
    public void feedsThatAreNotSubscribedAreSkipped() throws Exception {
        Feed notSubscribed = createFeed("Not Subscribed", "https://example.com/n.xml", "https://example.com/n",
                "https://example.com/n.png");
        notSubscribed.setState(Feed.STATE_NOT_SUBSCRIBED);
        Feed archived = createFeed("Archived", "https://example.com/a.xml", "https://example.com/a",
                "https://example.com/a.png");
        archived.setState(Feed.STATE_ARCHIVED);
        Feed subscribed = createFeed("Subscribed", "https://example.com/s.xml", "https://example.com/s",
                "https://example.com/s.png");

        String html = write(notSubscribed, archived, subscribed);

        assertTrue(html.contains("Subscribed"));
        assertFalse(html.contains("Not Subscribed"));
        assertFalse(html.contains("Archived"));
    }

    @Test
    public void emptyFeedListProducesTemplateWithoutEntries() throws Exception {
        StringWriter writer = new StringWriter();
        HtmlWriter.writeDocument(Collections.emptyList(), writer, context);

        assertTrue(writer.toString().contains("<ul>"));
        assertFalse(writer.toString().contains("<li><div>"));
    }
}

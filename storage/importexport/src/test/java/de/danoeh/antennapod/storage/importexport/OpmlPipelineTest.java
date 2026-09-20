package de.danoeh.antennapod.storage.importexport;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class OpmlPipelineTest extends ImportExportPipelineTestBase {

    private String export() throws Exception {
        StringWriter writer = new StringWriter();
        OpmlWriter.writeDocument(storedFeeds(), writer);
        return writer.toString();
    }

    private List<OpmlElement> read(String opml) throws Exception {
        return new OpmlReader().readDocument(new StringReader(opml));
    }

    private void importInto(String opml) throws Exception {
        for (OpmlElement element : read(opml)) {
            Feed feed = new Feed(element.getXmlUrl(), null, element.getText());
            feed.setItems(Collections.emptyList());
            FeedDatabaseWriter.updateFeed(context, feed, false);
        }
    }

    @Test
    public void exportedSubscriptionsAreReadBackWithTitleAndUrls() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        subscribe("https://example.com/two.xml", "Second podcast", "https://example.com/two", "https://example.com/2.png");

        List<OpmlElement> elements = read(export());

        assertEquals(2, elements.size());
        OpmlElement first = elements.get(0);
        assertEquals("First podcast", first.getText());
        assertEquals("https://example.com/one.xml", first.getXmlUrl());
        assertEquals("https://example.com/one", first.getHtmlUrl());
        assertEquals(Feed.TYPE_RSS2, first.getType());
        assertEquals("Second podcast", elements.get(1).getText());
        assertEquals("https://example.com/two.xml", elements.get(1).getXmlUrl());
    }

    @Test
    public void feedsThatAreNotSubscribedAreLeftOutOfTheExport() throws Exception {
        subscribe("https://example.com/subscribed.xml", "Subscribed", "https://example.com/s", "https://example.com/s.png");
        store("https://example.com/browsed.xml", "Browsed", "https://example.com/b", "https://example.com/b.png",
                Feed.STATE_NOT_SUBSCRIBED);
        store("https://example.com/archived.xml", "Archived", "https://example.com/a", "https://example.com/a.png",
                Feed.STATE_ARCHIVED);

        List<OpmlElement> elements = read(export());

        assertEquals(1, elements.size());
        assertEquals("https://example.com/subscribed.xml", elements.get(0).getXmlUrl());
    }

    @Test
    public void titlesWithMarkupCharactersSurviveTheRoundTrip() throws Exception {
        Feed feed = new Feed("https://example.com/markup.xml?a=1&b=2", null, "Fish & \"Chips\" <live>");
        feed.setItems(Collections.emptyList());
        FeedDatabaseWriter.updateFeed(context, feed, false);

        List<OpmlElement> elements = read(export());

        assertEquals(1, elements.size());
        assertEquals("Fish & \"Chips\" <live>", elements.get(0).getText());
        assertEquals("https://example.com/markup.xml?a=1&b=2", elements.get(0).getXmlUrl());
    }

    @Test
    public void exportOfAnEmptyLibraryContainsNoOutlines() throws Exception {
        String opml = export();

        assertTrue(opml.contains("<opml"));
        assertTrue(read(opml).isEmpty());
    }

    @Test
    public void importedOutlinesBecomeSubscriptionsIncludingNestedOnesAndSkippingBrokenOnes() throws Exception {
        importInto("<?xml version=\"1.0\" encoding=\"UTF-8\"?><opml version=\"2.0\"><body>"
                + "<outline text=\"Category\">"
                + "<outline text=\"Nested\" type=\"rss\" xmlUrl=\"https://example.com/nested.xml\"/>"
                + "</outline>"
                + "<outline text=\"No address\"/>"
                + "<outline xmlUrl=\"https://example.com/untitled.xml\"/>"
                + "<outline title=\"Preferred title\" text=\"Text\" xmlUrl=\"https://example.com/titled.xml\"/>"
                + "</body></opml>");

        Map<String, String> titlesByUrl = new HashMap<>();
        for (Feed feed : storedFeeds()) {
            titlesByUrl.put(feed.getDownloadUrl(), feed.getTitle());
            assertEquals(Feed.STATE_SUBSCRIBED, feed.getState());
        }

        assertEquals(3, titlesByUrl.size());
        assertEquals("Nested", titlesByUrl.get("https://example.com/nested.xml"));
        assertEquals("https://example.com/untitled.xml", titlesByUrl.get("https://example.com/untitled.xml"));
        assertEquals("Preferred title", titlesByUrl.get("https://example.com/titled.xml"));
    }

    @Test
    public void importingTheSameOpmlTwiceDoesNotDuplicateSubscriptions() throws Exception {
        String opml = "<opml version=\"2.0\"><body>"
                + "<outline text=\"Only\" xmlUrl=\"https://example.com/only.xml\"/></body></opml>";

        importInto(opml);
        importInto(opml);

        assertEquals(1, storedFeeds().size());
    }

    @Test
    public void exportAfterRestoringFromAnExportListsTheSameSubscriptions() throws Exception {
        subscribe("https://example.com/one.xml", "First podcast", "https://example.com/one", "https://example.com/1.png");
        subscribe("https://example.com/two.xml", "Second podcast", "https://example.com/two", "https://example.com/2.png");
        List<OpmlElement> original = read(export());
        String opml = export();

        resetDatabase();
        assertTrue(storedFeeds().isEmpty());
        importInto(opml);
        List<OpmlElement> restored = read(export());

        assertEquals(original.size(), restored.size());
        List<String> originalUrls = new ArrayList<>();
        List<String> restoredUrls = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            originalUrls.add(original.get(i).getXmlUrl() + "|" + original.get(i).getText());
            restoredUrls.add(restored.get(i).getXmlUrl() + "|" + restored.get(i).getText());
        }
        assertEquals(originalUrls, restoredUrls);
    }
}

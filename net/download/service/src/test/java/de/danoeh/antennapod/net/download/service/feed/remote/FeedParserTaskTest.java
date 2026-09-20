package de.danoeh.antennapod.net.download.service.feed.remote;

import android.os.Bundle;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.parser.feed.FeedHandler;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;
import de.danoeh.antennapod.parser.feed.UnsupportedFeedtypeException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedParserTaskTest {
    private static final String SOURCE = "http://example.com/feed.xml";
    private static final String DESTINATION = "/nonexistent-directory/feed.xml";

    private interface Parser {
        FeedHandlerResult parse(Feed feed) throws Exception;
    }

    private Parser parser;
    private final List<Feed> parsedFeeds = new ArrayList<>();
    private MockedConstruction<FeedHandler> handler;

    @Before
    public void setUp() {
        handler = Mockito.mockConstruction(FeedHandler.class, (mock, context) ->
                Mockito.when(mock.parseFeed(Mockito.any(Feed.class))).thenAnswer(invocation -> {
                    Feed feed = invocation.getArgument(0);
                    parsedFeeds.add(feed);
                    return parser.parse(feed);
                }));
    }

    @After
    public void tearDown() {
        handler.close();
    }

    private static DownloadRequest request(Bundle arguments) {
        return new DownloadRequest(DESTINATION, SOURCE, "Feed title", 5, Feed.FEEDFILETYPE_FEED, "etag-1", "alice",
                "secret", false, arguments, true);
    }

    private static FeedHandlerResult resultFor(Feed feed) {
        return new FeedHandlerResult(feed, Collections.emptyMap(), null);
    }

    private static FeedItem itemTitled(String title) {
        return new FeedItem(1, title, "guid", "http://example.com/item", new Date(0), FeedItem.UNPLAYED, null);
    }

    private FeedHandlerResult parseWith(Parser feedParser) {
        parser = feedParser;
        return new FeedParserTask(request(new Bundle())).call();
    }

    @Test
    public void statusIsAnErrorUntilTheTaskRuns() {
        FeedParserTask task = new FeedParserTask(request(new Bundle()));

        assertTrue(task.isSuccessful());
        assertFalse(task.getDownloadStatus().isSuccessful());
        assertEquals(DownloadError.ERROR_REQUEST_ERROR, task.getDownloadStatus().getReason());
    }

    @Test
    public void feedIsParsedWithRequestDataAndCredentials() {
        Bundle arguments = new Bundle();
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 4);
        parser = feed -> {
            feed.setTitle("Podcast");
            feed.setItems(new ArrayList<>());
            return resultFor(feed);
        };

        new FeedParserTask(request(arguments)).call();

        Feed feed = parsedFeeds.get(0);
        assertEquals(SOURCE, feed.getDownloadUrl());
        assertEquals("etag-1", feed.getLastModified());
        assertEquals(DESTINATION, feed.getLocalFileUrl());
        assertEquals(5, feed.getId());
        assertEquals(4, feed.getPageNr());
        assertEquals("alice", feed.getPreferences().getUsername());
        assertEquals("secret", feed.getPreferences().getPassword());
    }

    @Test
    public void validFeedIsReturnedWithASuccessfulStatus() {
        FeedParserTask task = new FeedParserTask(request(new Bundle()));
        Feed[] parsed = new Feed[1];
        parser = feed -> {
            feed.setTitle("Podcast");
            feed.setItems(new ArrayList<>(Collections.singletonList(itemTitled("Episode"))));
            parsed[0] = feed;
            return resultFor(feed);
        };

        FeedHandlerResult result = task.call();

        assertSame(parsed[0], result.feed);
        assertTrue(task.isSuccessful());
        DownloadResult status = task.getDownloadStatus();
        assertTrue(status.isSuccessful());
        assertEquals(DownloadError.SUCCESS, status.getReason());
        assertEquals("Podcast", status.getTitle());
        assertEquals(5, status.getFeedfileId());
        assertEquals(Feed.FEEDFILETYPE_FEED, status.getFeedfileType());
    }

    @Test
    public void feedWithoutImageGetsGenerativeCover() {
        FeedHandlerResult result = parseWith(feed -> {
            feed.setTitle("Podcast");
            feed.setItems(new ArrayList<>());
            return resultFor(feed);
        });

        assertEquals(Feed.PREFIX_GENERATIVE_COVER + SOURCE, result.feed.getImageUrl());
    }

    @Test
    public void feedKeepsItsOwnImage() {
        FeedHandlerResult result = parseWith(feed -> {
            feed.setTitle("Podcast");
            feed.setItems(new ArrayList<>());
            feed.setImageUrl("http://example.com/cover.png");
            return resultFor(feed);
        });

        assertEquals("http://example.com/cover.png", result.feed.getImageUrl());
    }

    @Test
    public void feedWithoutTitleIsRejected() {
        FeedParserTask task = new FeedParserTask(request(new Bundle()));
        parser = FeedParserTaskTest::resultFor;

        assertNull(task.call());

        assertFalse(task.isSuccessful());
        assertFalse(task.getDownloadStatus().isSuccessful());
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, task.getDownloadStatus().getReason());
        assertEquals("Feed has no title", task.getDownloadStatus().getReasonDetailed());
    }

    @Test
    public void feedWithUntitledItemIsRejected() {
        FeedParserTask task = new FeedParserTask(request(new Bundle()));
        parser = feed -> {
            feed.setTitle("Podcast");
            feed.setItems(new ArrayList<>(Collections.singletonList(itemTitled(null))));
            return resultFor(feed);
        };

        assertNull(task.call());

        assertFalse(task.isSuccessful());
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, task.getDownloadStatus().getReason());
        assertTrue(task.getDownloadStatus().getReasonDetailed().startsWith("Item has no title"));
    }

    private DownloadResult failedStatusOf(Parser failingParser) {
        FeedParserTask task = new FeedParserTask(request(new Bundle()));
        parser = failingParser;
        assertNull(task.call());
        assertFalse(task.isSuccessful());
        assertFalse(task.getDownloadStatus().isSuccessful());
        return task.getDownloadStatus();
    }

    @Test
    public void saxErrorIsReportedAsParserException() {
        DownloadResult status = failedStatusOf(feed -> {
            throw new SAXException("bad xml");
        });

        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, status.getReason());
        assertEquals("bad xml", status.getReasonDetailed());
    }

    @Test
    public void ioErrorIsReportedAsParserException() {
        DownloadResult status = failedStatusOf(feed -> {
            throw new IOException("read failed");
        });

        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, status.getReason());
        assertEquals("read failed", status.getReasonDetailed());
    }

    @Test
    public void parserConfigurationErrorIsReportedAsParserException() {
        DownloadResult status = failedStatusOf(feed -> {
            throw new ParserConfigurationException("no parser");
        });

        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION, status.getReason());
        assertEquals("no parser", status.getReasonDetailed());
    }

    @Test
    public void unsupportedFeedTypeIsReportedWithItsMessage() {
        DownloadResult status = failedStatusOf(feed -> {
            throw new UnsupportedFeedtypeException("json", "Unsupported: json");
        });

        assertEquals(DownloadError.ERROR_UNSUPPORTED_TYPE, status.getReason());
        assertEquals("Unsupported: json", status.getReasonDetailed());
    }

    @Test
    public void htmlDocumentIsReportedAsHtmlNotAFeed() {
        DownloadResult status = failedStatusOf(feed -> {
            throw new UnsupportedFeedtypeException("HTML", null);
        });

        assertEquals(DownloadError.ERROR_UNSUPPORTED_TYPE_HTML, status.getReason());
        assertEquals("Server returned HTML", status.getReasonDetailed());
    }
}

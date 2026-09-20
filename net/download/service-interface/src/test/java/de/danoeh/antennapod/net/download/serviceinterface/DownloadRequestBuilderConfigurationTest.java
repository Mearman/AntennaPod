package de.danoeh.antennapod.net.download.serviceinterface;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class DownloadRequestBuilderConfigurationTest {
    private static final String DESTINATION = "/data/cache/feed-Title1";

    private static Feed createFeed(String url, String title) {
        Feed feed = new Feed(url, null, title);
        feed.setId(4);
        return feed;
    }

    private static FeedMedia createMedia(String url) {
        return new FeedMedia(9, null, 0, 0, 0, "audio/mpeg", null, url, 0, null, 0, 0);
    }

    @Test
    public void remoteFeedUrlIsNormalisedToHttp() {
        Feed feed = createFeed("feed://example.com/rss.xml", "Title");
        DownloadRequest request = new DownloadRequestBuilder(DESTINATION, feed).build();
        assertEquals("http://example.com/rss.xml", request.getSource());
    }

    @Test
    public void localFeedUrlIsLeftUntouched() {
        Feed feed = createFeed(Feed.PREFIX_LOCAL_FOLDER + "content://tree/folder", "Local");
        DownloadRequest request = new DownloadRequestBuilder(DESTINATION, feed).build();
        assertEquals(Feed.PREFIX_LOCAL_FOLDER + "content://tree/folder", request.getSource());
    }

    @Test
    public void feedRequestCarriesIdentityAndPageNumber() {
        Feed feed = createFeed("http://example.com/rss.xml", "Podcast Title");
        feed.setPageNr(3);
        DownloadRequest request = new DownloadRequestBuilder(DESTINATION, feed).build();
        assertEquals(DESTINATION, request.getDestination());
        assertEquals("Podcast Title", request.getTitle());
        assertEquals(4, request.getFeedfileId());
        assertEquals(Feed.FEEDFILETYPE_FEED, request.getFeedfileType());
        assertEquals(3, request.getArguments().getInt(DownloadRequest.REQUEST_ARG_PAGE_NR));
    }

    @Test
    public void feedWithoutTitleIsIdentifiedByItsUrl() {
        Feed feed = createFeed("http://example.com/rss.xml", null);
        DownloadRequest request = new DownloadRequestBuilder(DESTINATION, feed).build();
        assertEquals("http://example.com/rss.xml", request.getTitle());
    }

    @Test
    public void mediaRequestNormalisesUrlAndCarriesIdentity() {
        FeedMedia media = createMedia("example.com/episode.mp3");
        DownloadRequest request = new DownloadRequestBuilder("/data/media/episode.mp3", media).build();
        assertEquals("http://example.com/episode.mp3", request.getSource());
        assertEquals("example.com/episode.mp3", request.getTitle());
        assertEquals(9, request.getFeedfileId());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, request.getFeedfileType());
    }

    @Test
    public void mediaRequestHasNoPageNumberArgument() {
        FeedMedia media = createMedia("http://example.com/episode.mp3");
        DownloadRequest request = new DownloadRequestBuilder("/data/media/episode.mp3", media).build();
        assertEquals(-1, request.getArguments().getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, -1));
    }

    @Test
    public void authenticationIsPassedToRequest() {
        FeedMedia media = createMedia("http://example.com/episode.mp3");
        DownloadRequest request = new DownloadRequestBuilder("/data/media/episode.mp3", media)
                .withAuthentication("alice", "secret")
                .build();
        assertEquals("alice", request.getUsername());
        assertEquals("secret", request.getPassword());
    }

    @Test
    public void requestIsUnauthenticatedByDefault() {
        FeedMedia media = createMedia("http://example.com/episode.mp3");
        DownloadRequest request = new DownloadRequestBuilder("/data/media/episode.mp3", media).build();
        assertNull(request.getUsername());
        assertNull(request.getPassword());
    }

    @Test
    public void lastModifiedIsPassedToRequest() {
        Feed feed = createFeed("http://example.com/rss.xml", "Title");
        DownloadRequest request = new DownloadRequestBuilder(DESTINATION, feed)
                .lastModified("Wed, 21 Oct 2015 07:28:00 GMT")
                .build();
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", request.getLastModified());
    }

    @Test
    public void forcingRefreshDiscardsLastModified() {
        Feed feed = createFeed("http://example.com/rss.xml", "Title");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DESTINATION, feed).lastModified("etag-1");
        builder.setForce(true);
        assertNull(builder.build().getLastModified());
    }

    @Test
    public void notForcingRefreshKeepsLastModified() {
        Feed feed = createFeed("http://example.com/rss.xml", "Title");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DESTINATION, feed).lastModified("etag-1");
        builder.setForce(false);
        assertEquals("etag-1", builder.build().getLastModified());
    }

    @Test
    public void sourceCanBeOverridden() {
        Feed feed = createFeed("http://example.com/rss.xml", "Title");
        DownloadRequestBuilder builder = new DownloadRequestBuilder(DESTINATION, feed);
        builder.setSource("http://example.com/rss.xml?page=2");
        assertEquals("http://example.com/rss.xml?page=2", builder.build().getSource());
    }

    @Test
    public void initiatedByUserFlagDistinguishesRequests() {
        Feed feed = createFeed("http://example.com/rss.xml", "Title");
        DownloadRequest byUser = new DownloadRequestBuilder(DESTINATION, feed).build();
        DownloadRequest automatic = new DownloadRequestBuilder(DESTINATION, feed)
                .withInitiatedByUser(false)
                .build();
        assertNotEquals(byUser, automatic);
        assertEquals(byUser, new DownloadRequestBuilder(DESTINATION, feed).withInitiatedByUser(true).build());
    }
}

package de.danoeh.antennapod.model.playback;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class RemoteMediaTest {
    private static final String DOWNLOAD_URL = "http://example.com/episode.mp3";
    private static final String FEED_URL = "http://example.com/feed.xml";

    private RemoteMedia media;

    @Before
    public void setUp() {
        media = new RemoteMedia(DOWNLOAD_URL, "guid", FEED_URL, "Feed title", "Episode title",
                "http://example.com/episode", "Author", "http://example.com/image.png", "http://example.com",
                "audio/mpeg", new Date(5000), "Shownotes");
    }

    private static FeedItem feedItem() {
        Feed feed = new Feed(1, null, "Feed title", "http://example.com", "d", null, "Author", "en", null, "id",
                "http://example.com/feed-image.png", null, FEED_URL, 0);
        FeedItem item = new FeedItem(2, "Episode title", "guid", "http://example.com/episode", new Date(5000),
                FeedItem.UNPLAYED, feed);
        item.setDescriptionIfLonger("Shownotes");
        item.setMedia(new FeedMedia(item, DOWNLOAD_URL, 0, "audio/mpeg"));
        return item;
    }

    @Test
    public void constructor_keepsValues() {
        assertEquals(DOWNLOAD_URL, media.getDownloadUrl());
        assertEquals(DOWNLOAD_URL, media.getStreamUrl());
        assertEquals("guid", media.getEpisodeIdentifier());
        assertEquals(FEED_URL, media.getFeedUrl());
        assertEquals("Feed title", media.getFeedTitle());
        assertEquals("Episode title", media.getEpisodeTitle());
        assertEquals("http://example.com/episode", media.getEpisodeLink());
        assertEquals("Author", media.getFeedAuthor());
        assertEquals("http://example.com/image.png", media.getImageUrl());
        assertEquals("http://example.com/image.png", media.getImageLocation());
        assertEquals("http://example.com", media.getFeedLink());
        assertEquals("audio/mpeg", media.getMimeType());
        assertEquals(5000, media.getPubDate().getTime());
        assertEquals("Shownotes", media.getNotes());
        assertEquals("Shownotes", media.getDescription());
    }

    @Test
    public void feedItemConstructor_copiesValuesFromItemAndFeed() {
        RemoteMedia fromItem = new RemoteMedia(feedItem());
        assertEquals(DOWNLOAD_URL, fromItem.getDownloadUrl());
        assertEquals("guid", fromItem.getEpisodeIdentifier());
        assertEquals(FEED_URL, fromItem.getFeedUrl());
        assertEquals("Feed title", fromItem.getFeedTitle());
        assertEquals("Episode title", fromItem.getEpisodeTitle());
        assertEquals("http://example.com/episode", fromItem.getEpisodeLink());
        assertEquals("Author", fromItem.getFeedAuthor());
        assertEquals("http://example.com", fromItem.getFeedLink());
        assertEquals("audio/mpeg", fromItem.getMimeType());
        assertEquals(5000, fromItem.getPubDate().getTime());
        assertEquals("Shownotes", fromItem.getNotes());
    }

    @Test
    public void feedItemConstructor_prefersItemImageOverFeedImage() {
        FeedItem item = feedItem();
        assertEquals("http://example.com/feed-image.png", new RemoteMedia(item).getImageUrl());
        item.setImageUrl("http://example.com/item-image.png");
        assertEquals("http://example.com/item-image.png", new RemoteMedia(item).getImageUrl());
        item.setImageUrl("");
        assertEquals("http://example.com/feed-image.png", new RemoteMedia(item).getImageUrl());
    }

    @Test
    public void identifier_combinesEpisodeIdentifierAndFeedUrl() {
        assertEquals("guid@" + FEED_URL, media.getIdentifier());
    }

    @Test
    public void websiteLink_prefersEpisodeLinkElseFeedUrl() {
        assertEquals("http://example.com/episode", media.getWebsiteLink());
        RemoteMedia withoutLink = new RemoteMedia(DOWNLOAD_URL, "guid", FEED_URL, "f", "e", null, null, null, null,
                null, null, null);
        assertEquals(FEED_URL, withoutLink.getWebsiteLink());
    }

    @Test
    public void mediaType_followsMimeType() {
        assertEquals(MediaType.AUDIO, media.getMediaType());
        RemoteMedia video = new RemoteMedia(DOWNLOAD_URL, "guid", FEED_URL, "f", "e", null, null, null, null,
                "video/mp4", null, null);
        assertEquals(MediaType.VIDEO, video.getMediaType());
    }

    @Test
    public void neverHasLocalFile() {
        assertNull(media.getLocalFileUrl());
        assertFalse(media.localFileAvailable());
    }

    @Test
    public void playbackState_isStoredAndPlaybackStartDoesNotChangeIt() {
        media.setPosition(1200);
        media.setDuration(60000);
        media.setLastPlayedTimeStatistics(99);
        media.onPlaybackStart();
        assertEquals(1200, media.getPosition());
        assertEquals(60000, media.getDuration());
        assertEquals(99, media.getLastPlayedTimeStatistics());
    }

    @Test
    public void chapters_areStoredAsGiven() {
        assertNull(media.getChapters());
        List<Chapter> chapters = Collections.singletonList(new Chapter(0, "Intro", null, null));
        media.setChapters(chapters);
        assertSame(chapters, media.getChapters());
    }

    @Test
    public void playableType_identifiesRemoteMedia() {
        assertEquals(RemoteMedia.PLAYABLE_TYPE_REMOTE_MEDIA, media.getPlayableType());
        assertNotEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, media.getPlayableType());
    }

    @Test
    public void equals_comparesDownloadUrlFeedUrlAndIdentifier() {
        RemoteMedia same = new RemoteMedia(DOWNLOAD_URL, "guid", FEED_URL, "other", "other", null, null, null, null,
                null, null, null);
        assertEquals(media, same);
        assertEquals(media.hashCode(), same.hashCode());
        assertNotEquals(media, new RemoteMedia("http://example.com/other.mp3", "guid", FEED_URL, "f", "e", null, null,
                null, null, null, null, null));
        assertNotEquals(media, new RemoteMedia(DOWNLOAD_URL, "other-guid", FEED_URL, "f", "e", null, null, null,
                null, null, null, null));
        assertNotEquals(media, new RemoteMedia(DOWNLOAD_URL, "guid", "http://example.com/other.xml", "f", "e", null,
                null, null, null, null, null, null));
        assertNotEquals(media, null);
        assertNotEquals(media, "media");
    }

    @Test
    public void equals_matchesFeedMediaOfSameEpisodeInBothDirections() {
        FeedMedia feedMedia = feedItem().getMedia();
        assertEquals(media, feedMedia);
        assertEquals(feedMedia, media);
    }

    @Test
    public void equals_feedMediaOfDifferentEpisodeDoesNotMatch() {
        FeedItem otherIdentifier = feedItem();
        otherIdentifier.setItemIdentifier("other-guid");
        assertNotEquals(media, otherIdentifier.getMedia());

        FeedItem otherFeed = feedItem();
        otherFeed.getFeed().setDownloadUrl("http://example.com/other.xml");
        assertNotEquals(media, otherFeed.getMedia());

        FeedItem otherUrl = feedItem();
        otherUrl.setMedia(new FeedMedia(otherUrl, "http://example.com/other.mp3", 0, "audio/mpeg"));
        assertNotEquals(media, otherUrl.getMedia());
    }

    @Test
    public void equals_feedMediaWithoutItemOrFeedDoesNotMatch() {
        FeedMedia detached = new FeedMedia(null, DOWNLOAD_URL, 0, "audio/mpeg");
        assertNotEquals(media, detached);

        FeedItem itemWithoutFeed = feedItem();
        itemWithoutFeed.setFeed(null);
        assertNotEquals(media, itemWithoutFeed.getMedia());
    }
}

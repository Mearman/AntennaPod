package de.danoeh.antennapod.storage.database;

import android.os.Parcel;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.RemoteMedia;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class RemoteMediaPipelineTest extends FeedPipelineTestBase {
    private Feed feed;
    private FeedItem withLink;
    private FeedItem withoutLink;

    @Before
    public void storeEpisodes() throws Exception {
        feed = parseAndStore(rss("""
                <title>Cast pipeline</title>
                <link>https://example.com/show</link>
                <itunes:author>Show Author</itunes:author>
                <image><url>https://example.com/show.png</url></image>
                <item>
                  <guid>with-link</guid><title>With link</title>
                  <link>https://example.com/with-link</link>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>
                  <description>Show notes</description>
                  <itunes:image href="https://example.com/episode.png"/>
                  <enclosure url="https://example.com/with-link.mp3" length="5000000" type="audio/mpeg"/>
                </item>
                <item>
                  <guid>without-link</guid><title>Without link</title>
                  <pubDate>Tue, 03 Jan 2006 15:04:05 +0000</pubDate>
                  <enclosure url="https://example.com/without-link.mp4" length="5000000" type="video/mp4"/>
                </item>
                """));
        withLink = storedItem(feed, "with-link");
        withoutLink = storedItem(feed, "without-link");
        DBReader.loadDescriptionOfFeedItem(withLink);
    }

    @Test
    public void remoteMediaCopiesTheStoredEpisodeAndFeedDetails() {
        RemoteMedia remote = new RemoteMedia(withLink);

        assertEquals("https://example.com/with-link.mp3", remote.getDownloadUrl());
        assertEquals("https://example.com/with-link.mp3", remote.getStreamUrl());
        assertEquals("with-link", remote.getEpisodeIdentifier());
        assertEquals(FEED_URL, remote.getFeedUrl());
        assertEquals("Cast pipeline", remote.getFeedTitle());
        assertEquals("With link", remote.getEpisodeTitle());
        assertEquals("https://example.com/with-link", remote.getEpisodeLink());
        assertEquals("Show Author", remote.getFeedAuthor());
        assertEquals("https://example.com/show", remote.getFeedLink());
        assertEquals("audio/mpeg", remote.getMimeType());
        assertEquals(MediaType.AUDIO, remote.getMediaType());
        assertEquals(withLink.getPubDate(), remote.getPubDate());
        assertEquals("Show notes", remote.getNotes());
        assertEquals("Show notes", remote.getDescription());
        assertEquals(RemoteMedia.PLAYABLE_TYPE_REMOTE_MEDIA, remote.getPlayableType());
    }

    @Test
    public void remoteMediaPrefersTheEpisodeImageAndFallsBackToTheFeedImage() {
        assertEquals("https://example.com/episode.png", new RemoteMedia(withLink).getImageLocation());
        assertEquals("https://example.com/episode.png", new RemoteMedia(withLink).getImageUrl());
        assertEquals("https://example.com/show.png", new RemoteMedia(withoutLink).getImageLocation());
    }

    @Test
    public void remoteMediaIsNeverAvailableLocallyAndIsIdentifiedByEpisodeAndFeed() {
        RemoteMedia remote = new RemoteMedia(withLink);

        assertFalse(remote.localFileAvailable());
        assertNull(remote.getLocalFileUrl());
        assertEquals("with-link@" + FEED_URL, remote.getIdentifier());
    }

    @Test
    public void websiteLinkFallsBackToTheFeedAddressWhenTheEpisodeHasNone() {
        assertEquals("https://example.com/with-link", new RemoteMedia(withLink).getWebsiteLink());
        assertEquals(FEED_URL, new RemoteMedia(withoutLink).getWebsiteLink());
        assertEquals(MediaType.VIDEO, new RemoteMedia(withoutLink).getMediaType());
    }

    @Test
    public void parcelledRemoteMediaKeepsEverythingIncludingPlaybackState() {
        RemoteMedia remote = new RemoteMedia(withLink);
        remote.setPosition(3000);
        remote.setDuration(60000);
        remote.setLastPlayedTimeStatistics(42L);

        Parcel parcel = Parcel.obtain();
        remote.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RemoteMedia restored = RemoteMedia.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(remote, restored);
        assertEquals(remote.hashCode(), restored.hashCode());
        assertEquals("With link", restored.getEpisodeTitle());
        assertEquals("Show Author", restored.getFeedAuthor());
        assertEquals("https://example.com/episode.png", restored.getImageUrl());
        assertEquals(withLink.getPubDate(), restored.getPubDate());
        assertEquals("Show notes", restored.getNotes());
        assertEquals(3000, restored.getPosition());
        assertEquals(60000, restored.getDuration());
        assertEquals(42L, restored.getLastPlayedTimeStatistics());
    }

    @Test
    public void remoteMediaEqualsTheStoredMediaItWasCreatedFromInBothDirections() {
        RemoteMedia remote = new RemoteMedia(withLink);
        FeedMedia stored = withLink.getMedia();

        assertEquals(remote, stored);
        assertEquals(stored, remote);
        assertNotEquals(remote, withoutLink.getMedia());
        assertNotEquals(withoutLink.getMedia(), remote);
    }

    @Test
    public void remoteMediaCreatedFromExplicitDetailsBehavesLikeOneCreatedFromAnEpisode() {
        RemoteMedia remote = new RemoteMedia("https://example.com/with-link.mp3", "with-link", FEED_URL,
                "Cast pipeline", "With link", "https://example.com/with-link", "Show Author",
                "https://example.com/episode.png", "https://example.com/show", "audio/mpeg", withLink.getPubDate(),
                "Show notes");

        assertEquals(new RemoteMedia(withLink), remote);
        assertTrue(remote.equals(withLink.getMedia()));
    }
}

package de.danoeh.antennapod.net.download.serviceinterface;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DownloadRequestCreatorTest {
    private static final String DATA_ROOT = "/simulated-data-root";

    private MockedStatic<UserPreferences> preferences;

    @Before
    public void setUp() {
        preferences = Mockito.mockStatic(UserPreferences.class);
        preferences.when(() -> UserPreferences.getDataFolder(Mockito.anyString()))
                .thenAnswer(invocation -> new File(DATA_ROOT, invocation.<String>getArgument(0)));
    }

    @After
    public void tearDown() {
        preferences.close();
    }

    private static FeedPreferences credentials(String username, String password) {
        return new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, username, password);
    }

    private static FeedMedia createMedia(String feedTitle, String episodeTitle, String url, long mediaId) {
        Feed feed = new Feed("http://example.com/feed.xml", null, feedTitle);
        FeedItem item = new FeedItem(1, episodeTitle, "guid", "http://example.com/link", new Date(),
                FeedItem.UNPLAYED, feed);
        FeedMedia media = new FeedMedia(mediaId, item, 0, 0, 0, "audio/mpeg", null, url, 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    @Test
    public void feedDestinationIsNamedAfterTitleAndId() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "My Podcast");
        feed.setId(7);
        DownloadRequest request = DownloadRequestCreator.create(feed).build();
        assertEquals(DATA_ROOT + "/cache/feed-My Podcast7", request.getDestination());
    }

    @Test
    public void feedWithoutTitleIsNamedAfterSanitisedUrl() {
        Feed feed = new Feed("http://example.com/feed.xml", null);
        feed.setId(7);
        DownloadRequest request = DownloadRequestCreator.create(feed).build();
        assertEquals(DATA_ROOT + "/cache/feed-httpexamplecomfeedxml7", request.getDestination());
    }

    @Test
    public void feedWithEmptyTitleIsNamedAfterSanitisedUrl() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "");
        feed.setId(2);
        DownloadRequest request = DownloadRequestCreator.create(feed).build();
        assertEquals(DATA_ROOT + "/cache/feed-httpexamplecomfeedxml2", request.getDestination());
    }

    @Test
    public void feedRequestCarriesCredentialsFromPreferences() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Title");
        feed.setPreferences(credentials("alice", "secret"));
        DownloadRequest request = DownloadRequestCreator.create(feed).build();
        assertEquals("alice", request.getUsername());
        assertEquals("secret", request.getPassword());
    }

    @Test
    public void feedWithoutPreferencesIsRequestedWithoutCredentials() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Title");
        DownloadRequest request = DownloadRequestCreator.create(feed).build();
        assertNull(request.getUsername());
        assertNull(request.getPassword());
    }

    @Test
    public void feedRequestCarriesLastModified() {
        Feed feed = new Feed("http://example.com/feed.xml", "etag-42", "Title");
        DownloadRequest request = DownloadRequestCreator.create(feed).build();
        assertEquals("etag-42", request.getLastModified());
    }

    @Test
    public void mediaDestinationIsInFeedFolderNamedAfterEpisodeAndMediaId() {
        FeedMedia media = createMedia("Feed: Title", "Episode: One", "http://example.com/audio/ep.mp3", 5);
        DownloadRequest request = DownloadRequestCreator.create(media).build();
        assertEquals(DATA_ROOT + "/media/Feed Title/Episode One.5.mp3", request.getDestination());
    }

    @Test
    public void mediaWithoutEpisodeTitleIsNamedAfterUrlFilename() {
        FeedMedia media = createMedia("Feed", null, "http://example.com/audio/ep.mp3", 5);
        DownloadRequest request = DownloadRequestCreator.create(media).build();
        assertTrue(request.getDestination(), request.getDestination().startsWith(DATA_ROOT + "/media/Feed/ep"));
        assertTrue(request.getDestination(), request.getDestination().endsWith(".5.mp3"));
    }

    @Test
    public void overlongEpisodeTitleIsTruncatedBeforeIdAndExtension() {
        String longTitle = "a".repeat(400);
        FeedMedia media = createMedia("Feed", longTitle, "http://example.com/audio/ep.mp3", 5);
        DownloadRequest request = DownloadRequestCreator.create(media).build();
        String filename = new File(request.getDestination()).getName();
        assertEquals(220 + ".5.mp3".length(), filename.length());
        assertTrue(filename.endsWith(".5.mp3"));
    }

    @Test
    public void mediaRequestCarriesCredentialsFromFeedPreferences() {
        FeedMedia media = createMedia("Feed", "Episode", "http://example.com/audio/ep.mp3", 5);
        media.getItem().getFeed().setPreferences(credentials("bob", "hunter2"));
        DownloadRequest request = DownloadRequestCreator.create(media).build();
        assertEquals("bob", request.getUsername());
        assertEquals("hunter2", request.getPassword());
    }

    @Test
    public void mediaWithoutFeedPreferencesIsRequestedWithoutCredentials() {
        FeedMedia media = createMedia("Feed", "Episode", "http://example.com/audio/ep.mp3", 5);
        DownloadRequest request = DownloadRequestCreator.create(media).build();
        assertNull(request.getUsername());
        assertNull(request.getPassword());
    }

    @Test
    public void mediaRequestIdentifiesTheMediaAndNormalisesItsUrl() {
        FeedMedia media = createMedia("Feed", "Episode", "example.com/audio/ep.mp3", 5);
        DownloadRequest request = DownloadRequestCreator.create(media).build();
        assertEquals("http://example.com/audio/ep.mp3", request.getSource());
        assertEquals(5, request.getFeedfileId());
        assertEquals("Episode", request.getTitle());
    }
}

package de.danoeh.antennapod.playback.base;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class MediaItemAdapterTest {
    private static final String FEED_URL = "http://example.com/feed";
    private static final String FEED_TITLE = "The Feed";
    private Context context;
    private Feed feed;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        feed = storeFeed(2);
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
    }

    private Feed storeFeed(int numItems) {
        Feed newFeed = new Feed(FEED_URL, null, FEED_TITLE);
        newFeed.setImageUrl("http://example.com/feed.png");
        newFeed.setAuthor("The Author");
        newFeed.setItems(new ArrayList<>());
        for (int i = 0; i < numItems; i++) {
            FeedItem item = new FeedItem(0, "Episode " + i, "id-" + i, "link",
                    new Date(1000L * (i + 1)), FeedItem.UNPLAYED, newFeed);
            item.setMedia(new FeedMedia(item, FEED_URL + "/media" + i + ".mp3", 1024, "audio/mp3"));
            newFeed.getItems().add(item);
        }
        return FeedDatabaseWriter.updateFeed(context, newFeed, false);
    }

    private FeedMedia storedMedia(int index) {
        for (FeedItem item : DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE).getItems()) {
            if (item.getItemIdentifier().equals("id-" + index)) {
                return item.getMedia();
            }
        }
        throw new AssertionError("No stored episode with index " + index);
    }

    @Test
    public void aStubCarriesOnlyTheMediaIdAndIsPlayableButNotBrowsable() {
        MediaItem item = MediaItemAdapter.fromMediaIdStub(42);

        assertEquals("42", item.mediaId);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isPlayable);
        assertEquals(Boolean.FALSE, item.mediaMetadata.isBrowsable);
        assertNull(item.mediaMetadata.title);
    }

    @Test
    public void aStoredEpisodeBecomesAPlayableItemTitledAfterTheEpisodeAndAttributedToTheFeed() {
        FeedMedia media = storedMedia(0);

        MediaItem item = MediaItemAdapter.fromPlayable(context, media, true);

        assertEquals(String.valueOf(media.getId()), item.mediaId);
        assertEquals("Episode 0", item.mediaMetadata.title);
        assertEquals(FEED_TITLE, item.mediaMetadata.subtitle);
        assertEquals(FEED_TITLE, item.mediaMetadata.artist);
        assertEquals(Integer.valueOf(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE), item.mediaMetadata.mediaType);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isPlayable);
        assertEquals(Boolean.FALSE, item.mediaMetadata.isBrowsable);
    }

    @Test
    public void anEpisodeWithoutALocalFileIsPlayedFromItsStreamUrl() {
        FeedMedia media = storedMedia(0);

        MediaItem item = MediaItemAdapter.fromPlayable(context, media, false);

        assertFalse(media.localFileAvailable());
        assertEquals(Uri.parse(media.getStreamUrl()), item.localConfiguration.uri);
        assertEquals(media.getStreamUrl(),
                item.mediaMetadata.extras.getString(MediaItemAdapter.KEY_STREAM_URL));
    }

    @Test
    public void anEpisodeWithADownloadedFileIsPlayedFromThatFileInsteadOfTheStream()
            throws ExecutionException, InterruptedException {
        FeedMedia media = storedMedia(0);
        media.setLocalFileUrl("/tmp/downloaded.mp3");
        media.setDownloaded(true, System.currentTimeMillis());
        DBWriter.setFeedMedia(media).get();

        MediaItem item = MediaItemAdapter.fromPlayable(context, storedMedia(0), false);

        assertEquals(Uri.parse(media.getLocalFileUrl()), item.localConfiguration.uri);
    }

    @Test
    public void aStreamedEpisodeOfAFeedWithCredentialsCarriesABasicAuthorizationHeader()
            throws ExecutionException, InterruptedException {
        FeedPreferences preferences = feed.getPreferences();
        preferences.setUsername("user");
        preferences.setPassword("secret");
        DBWriter.setFeedPreferences(preferences).get();

        MediaItem item = MediaItemAdapter.fromPlayable(context, storedMedia(0), false);

        assertEquals("Basic dXNlcjpzZWNyZXQ=",
                item.requestMetadata.extras.getString(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void aStreamedEpisodeOfAFeedWithoutCredentialsCarriesNoAuthorizationHeader() {
        MediaItem item = MediaItemAdapter.fromPlayable(context, storedMedia(0), false);

        assertNull(item.requestMetadata.extras.getString(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void aRemoteEpisodeImageIsExposedAsAnArtworkUri() throws ExecutionException, InterruptedException {
        FeedMedia media = storedMedia(0);
        media.getItem().setImageUrl("http://example.com/episode.png");
        DBWriter.setFeedItem(media.getItem(), false).get();

        MediaItem item = MediaItemAdapter.fromPlayable(context, storedMedia(0), true);

        assertEquals(Uri.parse("http://example.com/episode.png"), item.mediaMetadata.artworkUri);
    }

    @Test
    public void aFeedBecomesABrowsableItemTitledAfterTheFeedAndSubtitledWithItsAuthor() {
        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 0);

        MediaItem item = MediaItemAdapter.fromFeed(context, stored);

        assertEquals(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + stored.getId(), item.mediaId);
        assertEquals(FEED_TITLE, item.mediaMetadata.title);
        assertEquals("The Author", item.mediaMetadata.subtitle);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
        assertEquals(Boolean.FALSE, item.mediaMetadata.isPlayable);
        assertEquals(Uri.parse("http://example.com/feed.png"), item.mediaMetadata.artworkUri);
    }

    @Test
    public void aFeedWithoutAnImageBecomesABrowsableItemWithoutArtwork()
            throws ExecutionException, InterruptedException {
        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 0);
        stored.setImageUrl(null);

        MediaItem item = MediaItemAdapter.fromFeed(context, stored);

        assertNull(item.mediaMetadata.artworkUri);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
    }

    @Test
    public void aBrowsableCategoryPointsAtItsIconResourceAndKeepsTheGivenSubtitle() {
        MediaItem item = MediaItemAdapter.from(context, "queue", "Queue",
                android.R.drawable.ic_media_play, "3 episodes");

        assertEquals("queue", item.mediaId);
        assertEquals("Queue", item.mediaMetadata.title);
        assertEquals("3 episodes", item.mediaMetadata.subtitle);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
        assertEquals(Boolean.FALSE, item.mediaMetadata.isPlayable);
        assertNotNull(item.mediaMetadata.artworkUri);
        assertEquals("android.resource", item.mediaMetadata.artworkUri.getScheme());
        assertEquals("android", item.mediaMetadata.artworkUri.getAuthority());
        assertTrue(item.mediaMetadata.artworkUri.toString().endsWith("/drawable/ic_media_play"));
    }

    @Test
    public void aBrowsableCategoryWithoutASubtitleLeavesTheSubtitleUnset() {
        MediaItem item = MediaItemAdapter.from(context, "subscriptions", "Subscriptions",
                android.R.drawable.ic_media_play, null);

        assertNull(item.mediaMetadata.subtitle);
        assertEquals("Subscriptions", item.mediaMetadata.title);
    }

    @Test
    public void anItemListTurnsEveryEpisodeWithAStreamUrlIntoAMediaItem() {
        List<FeedItem> stored = DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE).getItems();

        ImmutableList<MediaItem> items = MediaItemAdapter.fromItemList(context, stored);

        assertEquals(2, items.size());
        assertEquals(String.valueOf(stored.get(0).getMedia().getId()), items.get(0).mediaId);
        assertEquals(String.valueOf(stored.get(1).getMedia().getId()), items.get(1).mediaId);
    }

    @Test
    public void anItemListSkipsEpisodesThatHaveNothingToPlay() {
        List<FeedItem> stored = new ArrayList<>(
                DBReader.getFeed(feed.getId(), true, 0, Integer.MAX_VALUE).getItems());
        FeedItem withoutMedia = new FeedItem(0, "No media", "id-none", "link", new Date(0),
                FeedItem.UNPLAYED, feed);
        stored.add(withoutMedia);

        ImmutableList<MediaItem> items = MediaItemAdapter.fromItemList(context, stored);

        assertEquals(2, items.size());
    }
}

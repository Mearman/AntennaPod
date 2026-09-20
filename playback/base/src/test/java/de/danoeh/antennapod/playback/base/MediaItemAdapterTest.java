package de.danoeh.antennapod.playback.base;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.R;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
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
        PlaybackBaseTestDatabase.setUp(context);
        feed = PlaybackBaseTestDatabase.storeFeed(context, FEED_URL, FEED_TITLE, 2);
    }

    @After
    public void tearDown() {
        PlaybackBaseTestDatabase.tearDown();
    }

    private FeedMedia storedMedia(int index) {
        return PlaybackBaseTestDatabase.storedMedia(feed.getId(), "id-" + index);
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
        assertEquals("Author of " + FEED_TITLE, item.mediaMetadata.subtitle);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
        assertEquals(Boolean.FALSE, item.mediaMetadata.isPlayable);
        assertEquals(Uri.parse(FEED_URL + "/image.png"), item.mediaMetadata.artworkUri);
    }

    @Test
    public void aFeedWithoutAnImageBecomesABrowsableItemWithoutArtwork() {
        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 0);
        stored.setImageUrl(null);

        MediaItem item = MediaItemAdapter.fromFeed(context, stored);

        assertNull(item.mediaMetadata.artworkUri);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
    }

    @Test
    public void aBrowsableCategoryPointsAtItsIconResourceAndKeepsTheGivenSubtitle() {
        MediaItem item = MediaItemAdapter.from(context, "queue", "Queue",
                R.drawable.media3_icon_play, "3 episodes");

        assertEquals("queue", item.mediaId);
        assertEquals("Queue", item.mediaMetadata.title);
        assertEquals("3 episodes", item.mediaMetadata.subtitle);
        assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
        assertEquals(Boolean.FALSE, item.mediaMetadata.isPlayable);
        assertNotNull(item.mediaMetadata.artworkUri);
        assertEquals("android.resource", item.mediaMetadata.artworkUri.getScheme());
        assertEquals(context.getPackageName(), item.mediaMetadata.artworkUri.getAuthority());
        assertTrue(item.mediaMetadata.artworkUri.toString().endsWith("/drawable/media3_icon_play"));
    }

    @Test
    public void aBrowsableCategoryWithoutASubtitleLeavesTheSubtitleUnset() {
        MediaItem item = MediaItemAdapter.from(context, "subscriptions", "Subscriptions",
                R.drawable.media3_icon_play, null);

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

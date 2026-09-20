package de.danoeh.antennapod.playback.base;

import android.R;
import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.playback.RemoteMedia;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MediaItemAdapterTest {
    private static final long MEDIA_ID = 42;
    private static final String STREAM_URL = "https://example.com/episode.mp3";
    private static final String LOCAL_FILE = "/data/episodes/episode.mp3";

    private Context context;
    private Feed feed;
    private FeedItem item;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        feed = new Feed("https://example.com/feed.xml", null, "Podcast");
        feed.setId(7);
        item = new FeedItem();
        item.setTitle("Episode");
        item.setFeed(feed);
    }

    private FeedMedia streamedMedia() {
        FeedMedia media = new FeedMedia(MEDIA_ID, item, 0, 0, 1000, "audio/mpeg", null, STREAM_URL, 0, null, 0, 0);
        item.setMedia(media);
        return media;
    }

    private FeedMedia downloadedMedia() {
        FeedMedia media = new FeedMedia(MEDIA_ID, item, 0, 0, 1000, "audio/mpeg", LOCAL_FILE, STREAM_URL, 1,
                null, 0, 0);
        item.setMedia(media);
        return media;
    }

    private void setCredentials(String username, String password) {
        feed.setPreferences(new FeedPreferences(feed.getId(), FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, username, password));
    }

    @Test
    public void stubItemCarriesOnlyIdAndIsPlayableButNotBrowsable() {
        MediaItem mediaItem = MediaItemAdapter.fromMediaIdStub(MEDIA_ID);
        assertEquals("42", mediaItem.mediaId);
        assertEquals(Boolean.TRUE, mediaItem.mediaMetadata.isPlayable);
        assertEquals(Boolean.FALSE, mediaItem.mediaMetadata.isBrowsable);
        assertNull(mediaItem.localConfiguration);
    }

    @Test
    public void playableItemCarriesIdTitleAndFeedTitle() {
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertEquals("42", mediaItem.mediaId);
        assertEquals("Episode", mediaItem.mediaMetadata.title.toString());
        assertEquals("Podcast", mediaItem.mediaMetadata.subtitle.toString());
        assertEquals("Podcast", mediaItem.mediaMetadata.artist.toString());
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, (int) mediaItem.mediaMetadata.mediaType);
        assertEquals(Boolean.TRUE, mediaItem.mediaMetadata.isPlayable);
        assertEquals(Boolean.FALSE, mediaItem.mediaMetadata.isBrowsable);
    }

    @Test
    public void streamedMediaIsPlayedFromStreamUrl() {
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertEquals(Uri.parse(STREAM_URL), mediaItem.localConfiguration.uri);
        assertEquals(STREAM_URL, mediaItem.mediaMetadata.extras.getString(MediaItemAdapter.KEY_STREAM_URL));
    }

    @Test
    public void downloadedMediaIsPlayedFromLocalFileButKeepsStreamUrlExtra() {
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, downloadedMedia(), true);
        assertEquals(Uri.parse(LOCAL_FILE), mediaItem.localConfiguration.uri);
        assertEquals(STREAM_URL, mediaItem.mediaMetadata.extras.getString(MediaItemAdapter.KEY_STREAM_URL));
    }

    @Test
    public void httpImageLocationBecomesArtworkUri() {
        feed.setImageUrl("https://example.com/cover.png");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertEquals(Uri.parse("https://example.com/cover.png"), mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void nonHttpImageLocationIsNotUsedAsArtworkUri() {
        feed.setImageUrl("file:///data/cover.png");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertNull(mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void browseItemsDoNotEmbedArtworkData() {
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertNull(mediaItem.mediaMetadata.artworkData);
    }

    @Test
    public void playbackItemWithoutCachedArtworkHasNoArtworkData() {
        feed.setImageUrl("https://example.com/cover.png");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), false);
        assertNull(mediaItem.mediaMetadata.artworkData);
        assertEquals(Uri.parse("https://example.com/cover.png"), mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void playbackItemForMediaWithoutFeedHasNoArtworkData() {
        FeedMedia media = new FeedMedia(MEDIA_ID, null, 0, 0, 1000, "audio/mpeg", null, STREAM_URL, 0, null, 0, 0);
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, media, false);
        assertNull(mediaItem.mediaMetadata.artworkData);
    }

    @Test
    public void playbackItemForNonFeedMediaHasNoArtworkData() {
        RemoteMedia media = new RemoteMedia(STREAM_URL, "guid", "https://example.com/feed.xml", "Remote feed",
                "Remote episode", "https://example.com/episode", "Author", "https://example.com/remote.png",
                "https://example.com/feed", "audio/mpeg", new Date(0), "notes");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, media, false);
        assertNull(mediaItem.mediaMetadata.artworkData);
    }

    @Test
    public void streamedMediaOfFeedWithCredentialsCarriesAuthorizationHeader() {
        setCredentials("user", "pass");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertEquals("Basic dXNlcjpwYXNz",
                mediaItem.requestMetadata.extras.getString(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void downloadedMediaCarriesNoAuthorizationHeader() {
        setCredentials("user", "pass");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, downloadedMedia(), true);
        assertFalse(mediaItem.requestMetadata.extras.containsKey(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void incompleteCredentialsCarryNoAuthorizationHeader() {
        setCredentials("user", "");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertFalse(mediaItem.requestMetadata.extras.containsKey(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void feedWithoutPreferencesCarriesNoAuthorizationHeader() {
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, streamedMedia(), true);
        assertFalse(mediaItem.requestMetadata.extras.containsKey(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void mediaWithoutItemCarriesNoAuthorizationHeader() {
        FeedMedia media = new FeedMedia(MEDIA_ID, null, 0, 0, 1000, "audio/mpeg", null, STREAM_URL, 0, null, 0, 0);
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, media, true);
        assertFalse(mediaItem.requestMetadata.extras.containsKey(MediaItemAdapter.KEY_AUTHORIZATION_HEADER));
    }

    @Test
    public void nonFeedMediaUsesZeroAsMediaId() {
        RemoteMedia media = new RemoteMedia(STREAM_URL, "guid", "https://example.com/feed.xml", "Remote feed",
                "Remote episode", "https://example.com/episode", "Author", "https://example.com/remote.png",
                "https://example.com/feed", "audio/mpeg", new Date(0), "notes");
        MediaItem mediaItem = MediaItemAdapter.fromPlayable(context, media, true);
        assertEquals("0", mediaItem.mediaId);
        assertEquals("Remote episode", mediaItem.mediaMetadata.title.toString());
        assertNull(mediaItem.mediaMetadata.subtitle);
        assertEquals(Uri.parse("https://example.com/remote.png"), mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void feedItemIsBrowsableAndNotPlayable() {
        feed.setAuthor("Feed author");
        MediaItem mediaItem = MediaItemAdapter.fromFeed(context, feed);
        assertEquals(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + "7", mediaItem.mediaId);
        assertEquals("Podcast", mediaItem.mediaMetadata.title.toString());
        assertEquals("Feed author", mediaItem.mediaMetadata.subtitle.toString());
        assertEquals(Boolean.TRUE, mediaItem.mediaMetadata.isBrowsable);
        assertEquals(Boolean.FALSE, mediaItem.mediaMetadata.isPlayable);
    }

    @Test
    public void feedItemWithHttpImageUsesArtworkUriWhenNothingIsCached() {
        feed.setImageUrl("https://example.com/cover.png");
        MediaItem mediaItem = MediaItemAdapter.fromFeed(context, feed);
        assertNull(mediaItem.mediaMetadata.artworkData);
        assertEquals(Uri.parse("https://example.com/cover.png"), mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void feedItemWithoutHttpImageHasNoArtwork() {
        feed.setImageUrl("file:///data/cover.png");
        MediaItem mediaItem = MediaItemAdapter.fromFeed(context, feed);
        assertNull(mediaItem.mediaMetadata.artworkData);
        assertNull(mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void feedItemWithoutImageHasNoArtwork() {
        MediaItem mediaItem = MediaItemAdapter.fromFeed(context, feed);
        assertNull(mediaItem.mediaMetadata.artworkData);
        assertNull(mediaItem.mediaMetadata.artworkUri);
    }

    @Test
    public void folderItemPointsAtResourceIcon() {
        MediaItem mediaItem = MediaItemAdapter.from(context, "folder", "Folder",
                R.drawable.ic_menu_add, "Subtitle");
        assertEquals("folder", mediaItem.mediaId);
        assertEquals("Folder", mediaItem.mediaMetadata.title.toString());
        assertEquals("Subtitle", mediaItem.mediaMetadata.subtitle.toString());
        assertEquals(Uri.parse("android.resource://android/drawable/ic_menu_add"),
                mediaItem.mediaMetadata.artworkUri);
        assertEquals(Boolean.TRUE, mediaItem.mediaMetadata.isBrowsable);
        assertEquals(Boolean.FALSE, mediaItem.mediaMetadata.isPlayable);
    }

    @Test
    public void folderItemWithoutSubtitleHasNoSubtitle() {
        MediaItem mediaItem = MediaItemAdapter.from(context, "folder", "Folder",
                R.drawable.ic_menu_add, null);
        assertNull(mediaItem.mediaMetadata.subtitle);
    }

    @Test
    public void streamingConfirmationItemPlaysTheGivenResource() {
        MediaItem mediaItem = MediaItemAdapter.buildStreamingConfirmationItem(context,
                R.drawable.ic_menu_add, "Confirm", "Streaming uses mobile data");
        assertEquals(MediaItemAdapter.MEDIA_ID_CONFIRM_STREAMING, mediaItem.mediaId);
        assertEquals(Uri.parse("android.resource://android/drawable/ic_menu_add"), mediaItem.localConfiguration.uri);
        assertEquals("Confirm", mediaItem.mediaMetadata.title.toString());
        assertEquals("Streaming uses mobile data", mediaItem.mediaMetadata.description.toString());
        assertEquals(Boolean.TRUE, mediaItem.mediaMetadata.isPlayable);
        assertEquals(Boolean.FALSE, mediaItem.mediaMetadata.isBrowsable);
    }

    @Test
    public void itemListContainsOnlyItemsWithPlayableMedia() {
        FeedItem withoutMedia = new FeedItem();
        withoutMedia.setFeed(feed);

        FeedItem streamed = new FeedItem();
        streamed.setFeed(feed);
        streamed.setMedia(new FeedMedia(1, streamed, 0, 0, 1000, "audio/mpeg", null, STREAM_URL, 0, null, 0, 0));

        FeedItem withoutUrl = new FeedItem();
        withoutUrl.setFeed(feed);
        withoutUrl.setMedia(new FeedMedia(2, withoutUrl, 0, 0, 1000, "audio/mpeg", null, null, 0, null, 0, 0));

        FeedItem downloaded = new FeedItem();
        downloaded.setFeed(feed);
        downloaded.setMedia(new FeedMedia(3, downloaded, 0, 0, 1000, "audio/mpeg", LOCAL_FILE, null, 1, null, 0, 0));

        ImmutableList<MediaItem> mediaItems = MediaItemAdapter.fromItemList(context,
                Arrays.asList(withoutMedia, streamed, withoutUrl, downloaded));
        assertEquals(2, mediaItems.size());
        assertEquals("1", mediaItems.get(0).mediaId);
        assertEquals("3", mediaItems.get(1).mediaId);
        assertNotNull(mediaItems.get(1).localConfiguration);
        assertEquals(Uri.parse(LOCAL_FILE), mediaItems.get(1).localConfiguration.uri);
    }

    @Test
    public void emptyItemListGivesEmptyResult() {
        assertTrue(MediaItemAdapter.fromItemList(context, Collections.emptyList()).isEmpty());
    }
}

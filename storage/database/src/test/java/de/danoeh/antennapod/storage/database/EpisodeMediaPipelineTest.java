package de.danoeh.antennapod.storage.database;

import android.os.Parcel;
import android.support.v4.media.MediaBrowserCompat;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class EpisodeMediaPipelineTest extends FeedPipelineTestBase {
    private static final String DOCUMENT = rss("""
            <title>Media pipeline</title>
            <link>https://example.com/show</link>
            <image><url>https://example.com/show.png</url></image>
            <item>
              <guid>episode</guid><title>The episode</title>
              <link>https://example.com/episode</link>
              <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>
              <description>Show notes</description>
              <enclosure url="https://example.com/episode.mp3" length="5000000" type="audio/mpeg"/>
              <itunes:duration>10:00</itunes:duration>
            </item>
            <item>
              <guid>video</guid><title>The video</title>
              <pubDate>Tue, 03 Jan 2006 15:04:05 +0000</pubDate>
              <itunes:image href="https://example.com/video.png"/>
              <enclosure url="https://example.com/video.mp4" length="9000000" type="video/mp4"/>
            </item>
            """);

    private FeedMedia storedMedia(String guid) throws Exception {
        return storedItem(parseAndStore(DOCUMENT), guid).getMedia();
    }

    private FeedMedia reloadMedia(FeedMedia media) {
        return DBReader.getFeedMedia(media.getId());
    }

    @Test
    public void downloadedEpisodeKeepsItsFileLocationAndDownloadDate() throws Exception {
        FeedMedia media = storedMedia("episode");
        assertFalse(media.isDownloaded());
        assertFalse(media.localFileAvailable());
        assertNull(media.getTranscriptFileUrl());
        media.setLocalFileUrl("/downloads/episode.mp3");
        media.setDownloaded(true, 1234567L);

        DBWriter.setMediaDownloadInformation(media);
        DBWriter.tearDownTests();
        FeedMedia restored = reloadMedia(media);

        assertTrue(restored.isDownloaded());
        assertTrue(restored.localFileAvailable());
        assertEquals(1234567L, restored.getDownloadDate());
        assertEquals("/downloads/episode.mp3", restored.getLocalFileUrl());
        assertEquals("/downloads/episode.mp3.transcript", restored.getTranscriptFileUrl());
        assertEquals("https://example.com/episode.mp3", restored.getStreamUrl());
    }

    @Test
    public void removingTheLocalFileMarksTheEpisodeAsNotDownloaded() throws Exception {
        FeedMedia media = storedMedia("episode");
        media.setLocalFileUrl("/downloads/episode.mp3");
        media.setDownloaded(true, 1234567L);
        DBWriter.setMediaDownloadInformation(media);
        DBWriter.tearDownTests();

        media.setLocalFileUrl(null);
        DBWriter.setMediaDownloadInformation(media);
        DBWriter.tearDownTests();
        FeedMedia restored = reloadMedia(media);

        assertFalse(restored.isDownloaded());
        assertFalse(restored.localFileAvailable());
        assertNull(restored.getLocalFileUrl());
        assertEquals(0, restored.getDownloadDate());
    }

    @Test
    public void fileExistsOnlyWhenTheDownloadedFileIsActuallyOnDisk() throws Exception {
        FeedMedia media = storedMedia("episode");
        assertFalse(media.fileExists());
        File file = new File(context.getCacheDir(), "episode.mp3");
        media.setLocalFileUrl(file.getAbsolutePath());
        assertFalse(media.fileExists());

        Files.write(file.toPath(), new byte[] {1, 2, 3});

        assertTrue(media.fileExists());
    }

    @Test
    public void playbackProgressIsStoredAndStartPositionFollowsThePosition() throws Exception {
        FeedMedia media = storedMedia("episode");
        media.setPosition(90000);
        media.setPlayedDuration(30000);
        media.setLastPlayedTimeStatistics(555L);
        media.setLastPlayedTimeHistory(new Date(777L));
        media.setDuration(600000);

        DBWriter.setFeedMediaPlaybackInformation(media);
        DBWriter.tearDownTests();
        FeedMedia restored = reloadMedia(media);

        assertEquals(90000, restored.getPosition());
        assertEquals(30000, restored.getPlayedDuration());
        assertEquals(555L, restored.getLastPlayedTimeStatistics());
        assertEquals(new Date(777L), restored.getLastPlayedTimeHistory());
        assertEquals(600000, restored.getDuration());
        assertTrue(restored.isInProgress());
        assertEquals(-1, restored.getStartPosition());
        restored.onPlaybackStart();
        assertEquals(90000, restored.getStartPosition());
        assertEquals(30000, restored.getPlayedDurationWhenStarted());
    }

    @Test
    public void startingToPlayANewEpisodeMakesItUnplayedInsteadOfNew() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedItem item = storedItem(stored, "episode");
        DBWriter.markItemsPlayed(FeedItem.NEW, false, List.of(item));
        DBWriter.tearDownTests();
        FeedItem fresh = storedItem(reload(stored), "episode");
        assertTrue(fresh.isNew());

        fresh.getMedia().setPosition(1000);

        assertFalse(fresh.isNew());
        assertFalse(fresh.isPlayed());
    }

    @Test
    public void downloadingANewEpisodeMakesItUnplayedInsteadOfNew() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        DBWriter.markItemsPlayed(FeedItem.NEW, false, List.of(storedItem(stored, "episode")));
        DBWriter.tearDownTests();
        FeedItem fresh = storedItem(reload(stored), "episode");

        fresh.getMedia().setDownloaded(true, 1L);

        assertFalse(fresh.isNew());
    }

    @Test
    public void unknownSizeMarkerSurvivesStorage() throws Exception {
        FeedMedia media = storedMedia("episode");
        assertFalse(media.checkedOnSizeButUnknown());

        media.setCheckedOnSizeButUnknown();
        DBWriter.setFeedMedia(media);
        DBWriter.tearDownTests();

        assertTrue(reloadMedia(media).checkedOnSizeButUnknown());
    }

    @Test
    public void storedMediaExposesTheEpisodeAndFeedItBelongsTo() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedMedia media = storedItem(stored, "episode").getMedia();

        assertEquals("The episode", media.getEpisodeTitle());
        assertEquals("Media pipeline", media.getFeedTitle());
        assertEquals("https://example.com/episode", media.getWebsiteLink());
        assertEquals(new Date(1136214245000L), media.getPubDate());
        assertEquals(MediaType.AUDIO, media.getMediaType());
        assertEquals("The episode", media.getHumanReadableIdentifier());
        assertEquals(media.getId(), media.getIdentifier());
        assertEquals(media.getItem().getId(), media.getItemId());
        assertEquals(FeedMedia.PLAYABLE_TYPE_FEEDMEDIA, media.getPlayableType());
        assertEquals(MediaType.VIDEO, storedItem(stored, "video").getMedia().getMediaType());
    }

    @Test
    public void mediaDescriptionIsLoadedThroughTheItemOnceTheItemDescriptionIsLoaded() throws Exception {
        FeedItem item = storedItem(parseAndStore(DOCUMENT), "episode");
        assertNull(item.getMedia().getDescription());

        DBReader.loadDescriptionOfFeedItem(item);

        assertEquals("Show notes", item.getMedia().getDescription());
    }

    @Test
    public void imageLocationPrefersTheEpisodeImageAndFallsBackToTheFeedImage() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);

        assertEquals("https://example.com/video.png", storedItem(stored, "video").getMedia().getImageLocation());
        assertEquals("https://example.com/show.png", storedItem(stored, "episode").getMedia().getImageLocation());
    }

    @Test
    public void mediaWithoutALocalFileHasNoEmbeddedPicture() throws Exception {
        FeedMedia media = storedMedia("episode");

        assertFalse(media.hasEmbeddedPicture());
    }

    @Test
    public void mediaWithAnUnreadableLocalFileHasNoEmbeddedPicture() throws Exception {
        FeedMedia media = storedMedia("episode");
        File file = new File(context.getCacheDir(), "not-audio.mp3");
        Files.write(file.toPath(), "definitely not audio".getBytes());
        media.setLocalFileUrl(file.getAbsolutePath());
        media.setDownloaded(true, 1L);

        media.checkEmbeddedPicture();

        assertFalse(media.hasEmbeddedPicture());
    }

    @Test
    public void parcelledMediaRestoresItsPlaybackStateAndDownloadInformation() throws Exception {
        FeedMedia media = storedMedia("episode");
        media.setPosition(4000);
        media.setPlayedDuration(2000);
        media.setLastPlayedTimeStatistics(99L);
        media.setLastPlayedTimeHistory(new Date(1000L));
        media.setLocalFileUrl("/downloads/episode.mp3");
        media.setDownloaded(true, 5000L);
        media.setDuration(600000);

        Parcel parcel = Parcel.obtain();
        media.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FeedMedia restored = FeedMedia.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(media, restored);
        assertEquals(media.hashCode(), restored.hashCode());
        assertEquals(media.getItemId(), restored.getItemId());
        assertEquals(4000, restored.getPosition());
        assertEquals(2000, restored.getPlayedDuration());
        assertEquals(99L, restored.getLastPlayedTimeStatistics());
        assertEquals(new Date(1000L), restored.getLastPlayedTimeHistory());
        assertEquals("/downloads/episode.mp3", restored.getLocalFileUrl());
        assertEquals(5000L, restored.getDownloadDate());
        assertEquals(600000, restored.getDuration());
        assertEquals(5000000, restored.getSize());
        assertEquals("audio/mpeg", restored.getMimeType());
    }

    @Test
    public void mediaEqualityIsBasedOnTheDatabaseIdentity() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedMedia episode = storedItem(stored, "episode").getMedia();
        FeedMedia video = storedItem(stored, "video").getMedia();

        assertEquals(episode, DBReader.getFeedMedia(episode.getId()));
        assertNotEquals(episode, video);
        assertNotEquals(episode, null);
        assertNotEquals(episode, "not media");
    }

    @Test
    public void mediaBrowserItemDescribesTheEpisodeForExternalControllers() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedMedia video = storedItem(stored, "video").getMedia();
        FeedMedia episode = storedItem(stored, "episode").getMedia();

        MediaBrowserCompat.MediaItem videoItem = video.getMediaItem();
        MediaBrowserCompat.MediaItem episodeItem = episode.getMediaItem();

        assertTrue(videoItem.isPlayable());
        assertEquals(String.valueOf(video.getId()), videoItem.getMediaId());
        assertEquals("The video", videoItem.getDescription().getTitle().toString());
        assertEquals("Media pipeline", videoItem.getDescription().getSubtitle().toString());
        assertEquals("https://example.com/video.png", videoItem.getDescription().getIconUri().toString());
        assertEquals("https://example.com/show.png", episodeItem.getDescription().getIconUri().toString());
    }

    @Test
    public void chaptersAndTranscriptOfTheMediaLiveOnTheItem() throws Exception {
        FeedItem item = storedItem(parseAndStore(DOCUMENT), "episode");
        FeedMedia media = item.getMedia();
        assertNull(media.getChapters());
        assertFalse(media.hasTranscript());

        media.setChapters(List.of(new Chapter(0, "Start", null, null)));

        assertNotNull(item.getChapters());
        assertEquals("Start", media.getChapters().get(0).getTitle());
    }

    @Test
    public void refreshedFeedUpdatesTheStoredEnclosureSizeAndMimeTypeButKeepsTheMeasuredDuration() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedMedia media = storedItem(stored, "episode").getMedia();
        media.setDuration(601000);
        media.setLastPlayedTimeHistory(new Date(0));
        DBWriter.setFeedMediaPlaybackInformation(media);
        DBWriter.tearDownTests();

        String withNewEnclosure = DOCUMENT.replace("length=\"5000000\" type=\"audio/mpeg\"/>\n"
                + "  <itunes:duration>10:00", "length=\"6000000\" type=\"audio/mp4\"/>\n  <itunes:duration>09:00");
        Feed refreshed = refresh(withNewEnclosure.replace("https://example.com/episode.mp3",
                "https://cdn.example.com/episode.mp3"));

        FeedMedia updated = storedItem(refreshed, "episode").getMedia();
        assertEquals("https://cdn.example.com/episode.mp3", updated.getDownloadUrl());
        assertEquals(6000000, updated.getSize());
        assertEquals("audio/mp4", updated.getMimeType());
        assertEquals(601000, updated.getDuration());
    }
}

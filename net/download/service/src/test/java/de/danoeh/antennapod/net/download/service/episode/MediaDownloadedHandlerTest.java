package de.danoeh.antennapod.net.download.service.episode;

import android.media.MediaMetadataRetriever;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.robolectric.shadows.ShadowMediaMetadataRetriever;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Category(IntegrationTest.class)
public class MediaDownloadedHandlerTest extends DownloadIntegrationTestBase {
    private static final byte[] AUDIO_BYTES = "not really audio, just bytes".getBytes(StandardCharsets.UTF_8);

    private File downloadedFile() throws Exception {
        File file = temporaryFolder.newFile("episode.mp3");
        Files.write(file.toPath(), AUDIO_BYTES);
        return file;
    }

    private DownloadRequest requestFor(FeedMedia media, File file) {
        return new DownloadRequest(file.getAbsolutePath(), media.getDownloadUrl(), "Episode One", media.getId(),
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);
    }

    private DownloadResult successfulStatus(FeedMedia media) {
        return new DownloadResult("Episode One", media.getId(), FeedMedia.FEEDFILETYPE_FEEDMEDIA, true,
                DownloadError.SUCCESS, null);
    }

    private MediaDownloadedHandler runHandler(FeedMedia media, File file) {
        MediaDownloadedHandler handler = new MediaDownloadedHandler(context, successfulStatus(media),
                requestFor(media, file));
        handler.run();
        DBWriter.tearDownTests();
        return handler;
    }

    private FeedMedia saveEpisodeWith(Feed feed, String chapterUrl, String transcriptUrl) {
        FeedItem item = new FeedItem(0, "Episode One", "guid-1", "link", new Date(), FeedItem.NEW, feed);
        item.setMedia(new FeedMedia(item, server.url("/episode.mp3").toString(), 0, "audio/mpeg"));
        if (chapterUrl != null) {
            item.setPodcastIndexChapterUrl(chapterUrl);
        }
        if (transcriptUrl != null) {
            item.setTranscriptUrl("text/vtt", transcriptUrl);
        }
        feed.getItems().add(item);
        saveFeed(feed);
        return DBReader.getFeedMedia(item.getMedia().getId());
    }

    @Test
    public void completedDownloadIsRecordedOnMediaAndItem() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        File file = downloadedFile();

        runHandler(media, file);

        FeedMedia stored = DBReader.getFeedMedia(media.getId());
        assertTrue(stored.isDownloaded());
        assertEquals(file.getAbsolutePath(), stored.getLocalFileUrl());
        assertEquals(AUDIO_BYTES.length, stored.getSize());
        assertTrue(stored.getDownloadDate() > 0);
        FeedItem item = DBReader.getFeedItem(media.getItem().getId());
        assertFalse(item.isAutoDownloadEnabled());
    }

    @Test
    public void completedDownloadOfNewEpisodeLeavesInbox() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        assertTrue(media.getItem().isNew());

        runHandler(media, downloadedFile());

        assertFalse(DBReader.getFeedItem(media.getItem().getId()).isNew());
    }

    @Test
    public void successfulStatusIsPassedThroughUnchanged() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        MediaDownloadedHandler handler = runHandler(media, downloadedFile());

        assertTrue(handler.getUpdatedStatus().isSuccessful());
        assertEquals(DownloadError.SUCCESS, handler.getUpdatedStatus().getReason());
    }

    @Test
    public void downloadOfSubscribedFeedIsQueuedForSynchronisation() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());

        runHandler(media, downloadedFile());

        ArgumentCaptor<EpisodeAction> captor = ArgumentCaptor.forClass(EpisodeAction.class);
        verify(synchronizationQueue).enqueueEpisodeAction(captor.capture());
        assertEquals(EpisodeAction.DOWNLOAD, captor.getValue().getAction());
        assertEquals("guid-1", captor.getValue().getGuid());
    }

    @Test
    public void downloadOfUnsubscribedFeedIsNotSynchronised() throws Exception {
        Feed feed = newFeed("Unsubscribed", server.url("/feed.xml").toString());
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        FeedMedia media = saveEpisodeWith(feed, null, null);

        runHandler(media, downloadedFile());

        verify(synchronizationQueue, never()).enqueueEpisodeAction(any());
        assertTrue(DBReader.getFeedMedia(media.getId()).isDownloaded());
    }

    @Test
    public void unknownMediaLeavesDatabaseAndSynchronisationUntouched() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        DownloadRequest request = new DownloadRequest(downloadedFile().getAbsolutePath(), media.getDownloadUrl(),
                "Episode One", 4711, FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, null, true);
        MediaDownloadedHandler handler = new MediaDownloadedHandler(context, successfulStatus(media), request);

        handler.run();
        DBWriter.tearDownTests();

        assertFalse(DBReader.getFeedMedia(media.getId()).isDownloaded());
        verify(synchronizationQueue, never()).enqueueEpisodeAction(any());
        assertTrue(handler.getUpdatedStatus().isSuccessful());
    }

    @Test
    public void podcastIndexChaptersAreFetchedFromTheAnnouncedUrl() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"version\":\"1.2.0\",\"chapters\":["
                + "{\"startTime\":0,\"title\":\"Intro\"},{\"startTime\":60,\"title\":\"Main topic\"}]}")
                .addHeader("Content-Type", "application/json+chapters"));
        FeedMedia media = saveEpisodeWith(newFeed("Chapters", server.url("/feed.xml").toString()),
                server.url("/chapters.json").toString(), null);

        runHandler(media, downloadedFile());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/chapters.json", recorded.getPath());
        assertTrue(DBReader.getFeedMedia(media.getId()).isDownloaded());
    }

    @Test
    public void failingChapterRequestDoesNotPreventCompletion() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        FeedMedia media = saveEpisodeWith(newFeed("Chapters", server.url("/feed.xml").toString()),
                server.url("/chapters.json").toString(), null);

        runHandler(media, downloadedFile());

        assertTrue(DBReader.getFeedMedia(media.getId()).isDownloaded());
    }

    @Test
    public void transcriptIsDownloadedAndStoredNextToMediaFile() throws Exception {
        server.enqueue(new MockResponse().setBody("Hello and welcome to the show.")
                .addHeader("Content-Type", "text/vtt"));
        FeedMedia media = saveEpisodeWith(newFeed("Transcripts", server.url("/feed.xml").toString()), null,
                server.url("/transcript.vtt").toString());
        File file = downloadedFile();

        runHandler(media, file);

        File transcriptFile = new File(file.getAbsolutePath() + ".transcript");
        assertTrue(transcriptFile.exists());
        assertEquals("Hello and welcome to the show.",
                new String(Files.readAllBytes(transcriptFile.toPath()), StandardCharsets.UTF_8));
        assertEquals("/transcript.vtt", server.takeRequest().getPath());
    }

    @Test
    public void emptyTranscriptResponseStoresNoFile() throws Exception {
        server.enqueue(new MockResponse().setBody("").addHeader("Content-Type", "text/vtt"));
        FeedMedia media = saveEpisodeWith(newFeed("Transcripts", server.url("/feed.xml").toString()), null,
                server.url("/transcript.vtt").toString());
        File file = downloadedFile();

        runHandler(media, file);

        assertFalse(new File(file.getAbsolutePath() + ".transcript").exists());
        assertNotNull(DBReader.getFeedMedia(media.getId()).getLocalFileUrl());
    }

    @Test
    public void durationOfDownloadedFileIsStoredWhenItsMetadataIsReadable() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        File file = downloadedFile();
        ShadowMediaMetadataRetriever.addMetadata(file.getAbsolutePath(),
                MediaMetadataRetriever.METADATA_KEY_DURATION, "123456");

        runHandler(media, file);

        assertEquals(123456, DBReader.getFeedMedia(media.getId()).getDuration());
    }

    @Test
    public void unreadableDurationLeavesStoredDurationUntouched() throws Exception {
        FeedMedia media = saveEpisode(server.url("/episode.mp3").toString());
        File file = downloadedFile();
        ShadowMediaMetadataRetriever.addMetadata(file.getAbsolutePath(),
                MediaMetadataRetriever.METADATA_KEY_DURATION, "unknown");

        runHandler(media, file);

        FeedMedia stored = DBReader.getFeedMedia(media.getId());
        assertTrue(stored.isDownloaded());
        assertEquals(media.getDuration(), stored.getDuration());
    }
}

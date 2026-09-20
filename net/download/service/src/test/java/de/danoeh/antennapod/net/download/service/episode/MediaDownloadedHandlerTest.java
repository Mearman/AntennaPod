package de.danoeh.antennapod.net.download.service.episode;

import android.content.Context;
import android.os.Bundle;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.chapters.ChapterUtils;
import de.danoeh.antennapod.ui.transcript.TranscriptUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(RobolectricTestRunner.class)
public class MediaDownloadedHandlerTest {
    private static final String DESTINATION = "/nonexistent-directory/episode.mp3";
    private static final long MEDIA_ID = 8;

    private final Context context = RuntimeEnvironment.getApplication();
    private final SynchronizationQueue syncQueue = Mockito.mock(SynchronizationQueue.class);
    private MockedStatic<DBReader> reader;
    private MockedStatic<DBWriter> writer;
    private MockedStatic<ChapterUtils> chapters;
    private MockedStatic<TranscriptUtils> transcripts;
    private MockedStatic<SynchronizationQueue> queue;

    @Before
    public void setUp() {
        reader = Mockito.mockStatic(DBReader.class);
        writer = Mockito.mockStatic(DBWriter.class);
        chapters = Mockito.mockStatic(ChapterUtils.class);
        transcripts = Mockito.mockStatic(TranscriptUtils.class);
        queue = Mockito.mockStatic(SynchronizationQueue.class);
        queue.when(SynchronizationQueue::getInstance).thenReturn(syncQueue);
        writer.when(() -> DBWriter.setFeedMedia(any(FeedMedia.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(null));
        writer.when(() -> DBWriter.setFeedItem(any(FeedItem.class), anyBoolean()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(null));
    }

    @After
    public void tearDown() {
        queue.close();
        transcripts.close();
        chapters.close();
        writer.close();
        reader.close();
    }

    private static FeedItem item(int state, boolean hasChapters, String chapterUrl, String transcriptUrl, Feed feed) {
        FeedItem item = new FeedItem(3, "Episode", "http://example.com/link", new Date(0), null, 1, hasChapters, null,
                state, "guid", true, chapterUrl, "text/vtt", transcriptUrl, null);
        item.setFeed(feed);
        return item;
    }

    private static Feed subscribedFeed() {
        return new Feed("http://example.com/feed.xml", null, "Feed");
    }

    private FeedMedia provideMedia(FeedItem item) {
        FeedMedia media = new FeedMedia(MEDIA_ID, item, 0, 0, 0, "audio/mpeg", null, "http://example.com/a.mp3", 0,
                null, 0, 0);
        item.setMedia(media);
        reader.when(() -> DBReader.getFeedMedia(MEDIA_ID)).thenReturn(media);
        return media;
    }

    private DownloadRequest request() {
        return new DownloadRequest(DESTINATION, "http://example.com/a.mp3", "Episode", MEDIA_ID,
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, null, null, null, false, new Bundle(), true);
    }

    private static DownloadResult successfulStatus() {
        return new DownloadResult("Episode", MEDIA_ID, FeedMedia.FEEDFILETYPE_FEEDMEDIA, true, DownloadError.SUCCESS,
                null);
    }

    private MediaDownloadedHandler handler(DownloadResult status) {
        return new MediaDownloadedHandler(context, status, request());
    }

    @Test
    public void nothingIsWrittenWhenTheMediaNoLongerExists() {
        reader.when(() -> DBReader.getFeedMedia(MEDIA_ID)).thenReturn(null);
        DownloadResult status = successfulStatus();

        MediaDownloadedHandler handler = handler(status);
        handler.run();

        writer.verify(() -> DBWriter.setFeedMedia(any(FeedMedia.class)), Mockito.never());
        assertSame(status, handler.getUpdatedStatus());
    }

    @Test
    public void mediaIsMarkedDownloadedWithItsDestination() {
        FeedMedia media = provideMedia(item(FeedItem.UNPLAYED, true, null, null, subscribedFeed()));

        handler(successfulStatus()).run();

        assertTrue(media.isDownloaded());
        assertEquals(DESTINATION, media.getLocalFileUrl());
        writer.verify(() -> DBWriter.setFeedMedia(media));
    }

    @Test
    public void itemIsExcludedFromFurtherAutoDownloadAndSaved() {
        FeedMedia media = provideMedia(item(FeedItem.UNPLAYED, true, null, null, subscribedFeed()));

        handler(successfulStatus()).run();

        assertFalse(media.getItem().isAutoDownloadEnabled());
        writer.verify(() -> DBWriter.setFeedItem(eq(media.getItem()), eq(false)));
    }

    @Test
    public void newItemBecomesUnplayedAndSignalsTheUnreadStateChange() {
        FeedMedia media = provideMedia(item(FeedItem.NEW, true, null, null, subscribedFeed()));

        handler(successfulStatus()).run();

        assertFalse(media.getItem().isNew());
        writer.verify(() -> DBWriter.setFeedItem(eq(media.getItem()), eq(true)));
    }

    @Test
    public void successfulStatusIsKeptWhenTheDatabaseAcceptsTheChanges() {
        provideMedia(item(FeedItem.UNPLAYED, true, null, null, subscribedFeed()));
        DownloadResult status = successfulStatus();

        MediaDownloadedHandler handler = handler(status);
        handler.run();

        assertSame(status, handler.getUpdatedStatus());
    }

    @Test
    public void databaseFailureTurnsTheStatusIntoADatabaseAccessError() {
        provideMedia(item(FeedItem.UNPLAYED, true, null, null, subscribedFeed()));
        writer.when(() -> DBWriter.setFeedMedia(any(FeedMedia.class)))
                .thenAnswer(invocation -> CompletableFuture.failedFuture(new IllegalStateException("db closed")));

        MediaDownloadedHandler handler = handler(successfulStatus());
        handler.run();

        DownloadResult updated = handler.getUpdatedStatus();
        assertFalse(updated.isSuccessful());
        assertEquals(DownloadError.ERROR_DB_ACCESS_ERROR, updated.getReason());
        assertEquals(MEDIA_ID, updated.getFeedfileId());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, updated.getFeedfileType());
    }

    @Test
    public void downloadOfASubscribedFeedIsQueuedForSynchronisation() {
        FeedMedia media = provideMedia(item(FeedItem.UNPLAYED, true, null, null, subscribedFeed()));

        handler(successfulStatus()).run();

        ArgumentCaptor<EpisodeAction> captor = ArgumentCaptor.forClass(EpisodeAction.class);
        Mockito.verify(syncQueue).enqueueEpisodeAction(captor.capture());
        assertEquals(EpisodeAction.DOWNLOAD, captor.getValue().getAction());
        assertEquals(media.getItem().getItemIdentifier(), captor.getValue().getGuid());
        assertNotNull(captor.getValue().getTimestamp());
    }

    @Test
    public void downloadOfANonSubscribedFeedIsNotQueuedForSynchronisation() {
        Feed feed = subscribedFeed();
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        provideMedia(item(FeedItem.UNPLAYED, true, null, null, feed));

        handler(successfulStatus()).run();

        Mockito.verify(syncQueue, Mockito.never()).enqueueEpisodeAction(any(EpisodeAction.class));
    }

    @Test
    public void chaptersAreCachedFromTheFileWhenTheItemHasNone() throws InterruptedIOException {
        FeedMedia media = provideMedia(item(FeedItem.UNPLAYED, false, null, null, subscribedFeed()));
        List<Chapter> fromFile = Collections.singletonList(new Chapter(0, "Intro", null, null));
        chapters.when(() -> ChapterUtils.loadChaptersFromMediaFile(any(FeedMedia.class), any(Context.class)))
                .thenReturn(fromFile);

        handler(successfulStatus()).run();

        assertSame(fromFile, media.getChapters());
    }

    @Test
    public void chaptersAreNotReadFromTheFileWhenTheItemAlreadyHasThem() throws InterruptedIOException {
        provideMedia(item(FeedItem.UNPLAYED, true, null, null, subscribedFeed()));

        handler(successfulStatus()).run();

        chapters.verify(() -> ChapterUtils.loadChaptersFromMediaFile(any(FeedMedia.class), any(Context.class)),
                Mockito.never());
    }

    @Test
    public void podcastIndexChaptersAreCachedFromTheirUrl() throws InterruptedIOException {
        provideMedia(item(FeedItem.UNPLAYED, true, "http://example.com/chapters.json", null, subscribedFeed()));

        handler(successfulStatus()).run();

        chapters.verify(() -> ChapterUtils.loadChaptersFromUrl("http://example.com/chapters.json", false));
    }

    @Test
    public void interruptedChapterLoadingDoesNotPreventSavingTheMedia() throws InterruptedIOException {
        FeedMedia media = provideMedia(item(FeedItem.UNPLAYED, false, null, null, subscribedFeed()));
        chapters.when(() -> ChapterUtils.loadChaptersFromMediaFile(any(FeedMedia.class), any(Context.class)))
                .thenThrow(new InterruptedIOException("interrupted"));

        handler(successfulStatus()).run();

        writer.verify(() -> DBWriter.setFeedMedia(media));
    }

    @Test
    public void transcriptIsDownloadedAndStored() throws InterruptedIOException {
        FeedMedia media = provideMedia(item(FeedItem.UNPLAYED, true, null, "http://example.com/t.vtt",
                subscribedFeed()));
        transcripts.when(() -> TranscriptUtils.loadTranscriptFromUrl(anyString(), anyBoolean()))
                .thenReturn("WEBVTT");

        handler(successfulStatus()).run();

        transcripts.verify(() -> TranscriptUtils.loadTranscriptFromUrl("http://example.com/t.vtt", true));
        transcripts.verify(() -> TranscriptUtils.storeTranscript(media, "WEBVTT"));
    }

    @Test
    public void emptyTranscriptIsNotStored() throws InterruptedIOException {
        provideMedia(item(FeedItem.UNPLAYED, true, null, "http://example.com/t.vtt", subscribedFeed()));
        transcripts.when(() -> TranscriptUtils.loadTranscriptFromUrl(anyString(), anyBoolean())).thenReturn("");

        handler(successfulStatus()).run();

        transcripts.verify(() -> TranscriptUtils.storeTranscript(any(FeedMedia.class), anyString()), Mockito.never());
    }
}

package de.test.antennapod.storage.database;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Changes the state of episodes (played, favourite, history, download log) and reads it back from the database.
 */
@RunWith(AndroidJUnit4.class)
public class EpisodeStateDatabaseTest {
    private static final long HOUR_MILLIS = 3600 * 1000L;
    private static final int LOG_ENTRIES = 3;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private Feed feed;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        feed = fixture.subscribe("States", 4);
    }

    @After
    public void tearDown() throws Exception {
        fixture.tearDown();
    }

    private FeedItem item(int index) {
        return DBReader.getFeedItem(feed.getItemAtIndex(index).getId());
    }

    private FeedMedia media(int index) {
        return DBReader.getFeedMedia(feed.getItemAtIndex(index).getMedia().getId());
    }

    private int count(String... filter) {
        return DBReader.getTotalEpisodeCount(new FeedItemFilter(filter));
    }

    @Test
    public void episodesCanBeMarkedPlayedUnplayedAndNew() throws Exception {
        FeedItem first = item(0);

        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(first)).get();
        assertTrue(item(0).isPlayed());
        assertEquals(1, count(FeedItemFilter.PLAYED));
        assertEquals(3, count(FeedItemFilter.UNPLAYED));

        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(item(0))).get();
        assertTrue(item(0).isNew());
        assertEquals(1, count(FeedItemFilter.NEW));

        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, Collections.singletonList(item(0))).get();
        assertFalse(item(0).isPlayed());
        assertFalse(item(0).isNew());
    }

    @Test
    public void markingAnEpisodePlayedCanResetItsPosition() throws Exception {
        FeedMedia started = media(0);
        started.setPosition(90_000);
        started.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(started).get();
        assertEquals(1, count(FeedItemFilter.PAUSED));

        DBWriter.markItemsPlayed(FeedItem.PLAYED, true, Collections.singletonList(item(0))).get();

        assertEquals(0, media(0).getPosition());
        assertEquals(0, count(FeedItemFilter.PAUSED));
        assertEquals(4, count(FeedItemFilter.NOT_PAUSED));
    }

    @Test
    public void newFlagOfASingleFeedCanBeRemoved() throws Exception {
        Feed other = fixture.subscribe("Other new", 2, FeedItem.NEW);
        DBWriter.markItemsPlayed(FeedItem.NEW, false, Arrays.asList(item(0), item(1))).get();
        assertEquals(4, count(FeedItemFilter.NEW));

        DBWriter.removeFeedNewFlag(feed.getId()).get();

        assertEquals(2, count(FeedItemFilter.NEW));
        assertTrue(DBReader.getFeedItem(other.getItemAtIndex(0).getId()).isNew());
        assertFalse(item(0).isNew());
    }

    @Test
    public void newFlagOfEveryFeedCanBeRemoved() throws Exception {
        fixture.subscribe("Other new", 2, FeedItem.NEW);
        DBWriter.markItemsPlayed(FeedItem.NEW, false, Collections.singletonList(item(0))).get();

        DBWriter.removeAllNewFlags().get();

        assertEquals(0, count(FeedItemFilter.NEW));
    }

    @Test
    public void favouritesCanBeAddedRemovedAndToggled() throws Exception {
        DBWriter.addFavoriteItems(Arrays.asList(item(0), item(1))).get();
        assertEquals(2, count(FeedItemFilter.IS_FAVORITE));
        assertTrue(item(0).isTagged(FeedItem.TAG_FAVORITE));
        assertEquals(2, count(FeedItemFilter.NOT_FAVORITE));

        DBWriter.removeFavoriteItems(Collections.singletonList(item(0))).get();
        assertEquals(1, count(FeedItemFilter.IS_FAVORITE));

        DBWriter.toggleFavoriteItem(item(2)).get();
        assertEquals(2, count(FeedItemFilter.IS_FAVORITE));
        DBWriter.toggleFavoriteItem(item(2)).get();
        assertEquals(1, count(FeedItemFilter.IS_FAVORITE));
    }

    @Test
    public void playbackHistoryIsOrderedByCompletionDate() throws Exception {
        long now = System.currentTimeMillis();
        DBWriter.addItemToPlaybackHistory(media(0), new Date(now - 3 * HOUR_MILLIS)).get();
        DBWriter.addItemToPlaybackHistory(media(1), new Date(now - HOUR_MILLIS)).get();
        DBWriter.addItemToPlaybackHistory(media(2), new Date(now - 2 * HOUR_MILLIS)).get();
        DBWriter.addItemToPlaybackHistory(media(3)).get();

        List<FeedItem> history = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY), SortOrder.COMPLETION_DATE_NEW_OLD);

        assertEquals(4, history.size());
        assertEquals(item(3).getId(), history.get(0).getId());
        assertEquals(item(1).getId(), history.get(1).getId());
        assertEquals(item(2).getId(), history.get(2).getId());
        assertEquals(item(0).getId(), history.get(3).getId());
    }

    @Test
    public void episodesCanBeRemovedFromTheHistoryOneByOne() throws Exception {
        DBWriter.addItemToPlaybackHistory(media(0)).get();
        DBWriter.addItemToPlaybackHistory(media(1)).get();

        DBWriter.deleteFromPlaybackHistory(item(0)).get();

        assertEquals(1, count(FeedItemFilter.IS_IN_HISTORY));
    }

    @Test
    public void wholeHistoryCanBeCleared() throws Exception {
        DBWriter.addItemToPlaybackHistory(media(0)).get();
        DBWriter.addItemToPlaybackHistory(media(1)).get();

        DBWriter.clearPlaybackHistory().get();

        assertEquals(0, count(FeedItemFilter.IS_IN_HISTORY));
    }

    @Test
    public void playbackInformationIsStored() throws Exception {
        FeedMedia stored = media(1);
        stored.setPosition(42_000);
        stored.setDuration(180_000);
        stored.setPlayedDuration(30_000);
        stored.setLastPlayedTimeStatistics(1_000_000);
        stored.setLastPlayedTimeHistory(new Date(2_000_000));
        DBWriter.setFeedMediaPlaybackInformation(stored).get();

        FeedMedia loaded = media(1);

        assertEquals(42_000, loaded.getPosition());
        assertEquals(180_000, loaded.getDuration());
        assertEquals(30_000, loaded.getPlayedDuration());
        assertEquals(1_000_000, loaded.getLastPlayedTimeStatistics());
        assertEquals(2_000_000, loaded.getLastPlayedTimeHistory().getTime());
    }

    @Test
    public void downloadInformationIsStoredAndDeletedWithTheFile() throws Exception {
        fixture.markDownloaded(item(2));
        assertTrue(item(2).isDownloaded());
        assertEquals(1, count(FeedItemFilter.DOWNLOADED));
        assertEquals(3, count(FeedItemFilter.NOT_DOWNLOADED));
        File file = new File(media(2).getLocalFileUrl());
        assertTrue(file.exists());

        DBWriter.deleteFeedMediaOfItem(context, media(2)).get();

        assertFalse(file.exists());
        assertFalse(item(2).isDownloaded());
        assertNull(media(2).getLocalFileUrl());
    }

    @Test
    public void deletingAMissingMediaDoesNothing() throws Exception {
        DBWriter.deleteFeedMediaOfItem(context, null).get();

        assertEquals(4, DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE).getItems().size());
    }

    @Test
    public void itemsAreStoredWithTheirDescriptionAndChapters() throws Exception {
        FeedItem edited = item(0);
        edited.setDescriptionIfLonger("A much longer description of the episode");
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(new Chapter(0, "Intro", "http://example.com/intro", "http://example.com/intro.png"));
        chapters.add(new Chapter(60_000, "Main", null, null));
        edited.setChapters(chapters);
        DBWriter.setFeedItem(edited, false).get();

        FeedItem loaded = item(0);
        DBReader.loadDescriptionOfFeedItem(loaded);
        List<Chapter> storedChapters = DBReader.loadChaptersOfFeedItem(loaded);

        assertEquals("A much longer description of the episode", loaded.getDescription());
        assertEquals(2, storedChapters.size());
        assertEquals("Intro", storedChapters.get(0).getTitle());
        assertEquals("http://example.com/intro", storedChapters.get(0).getLink());
        assertEquals("http://example.com/intro.png", storedChapters.get(0).getImageUrl());
        assertEquals(60_000, storedChapters.get(1).getStart());
    }

    @Test
    public void episodesWithoutChaptersHaveNone() throws Exception {
        assertNull(DBReader.loadChaptersOfFeedItem(item(1)));
    }

    @Test
    public void itemListCanBeStoredAtOnce() throws Exception {
        FeedItem first = item(0);
        FeedItem second = item(1);
        first.setPlayState(FeedItem.PLAYED);
        second.setPlayState(FeedItem.NEW);

        DBWriter.setItemList(Arrays.asList(first, second)).get();

        assertTrue(item(0).isPlayed());
        assertTrue(item(1).isNew());
    }

    @Test
    public void downloadLogIsOrderedByCompletionAndCanBeCleared() throws Exception {
        for (int i = 0; i < LOG_ENTRIES; i++) {
            DBWriter.addDownloadStatus(new DownloadResult(0, "Entry " + i, feed.getId(), Feed.FEEDFILETYPE_FEED,
                    i % 2 == 0, i % 2 == 0 ? DownloadError.SUCCESS : DownloadError.ERROR_IO_ERROR,
                    new Date(1_000_000L + i), "detail " + i)).get();
        }

        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(LOG_ENTRIES, log.size());
        assertEquals("Entry 2", log.get(0).getTitle());
        assertEquals("detail 1", log.get(1).getReasonDetailed());
        assertFalse(log.get(1).isSuccessful());
        assertEquals(2, DBReader.getFeedDownloadLog(feed.getId(), 2).size());
        assertTrue(DBReader.getFeedDownloadLog(feed.getId() + 1, 10).isEmpty());

        DBWriter.clearDownloadLog().get();

        assertTrue(DBReader.getDownloadLog().isEmpty());
    }

    @Test
    public void deletingMediaAlsoRemovesItsDownloadLog() throws Exception {
        FeedItem downloaded = item(0);
        fixture.markDownloaded(downloaded);
        DBWriter.addDownloadStatus(new DownloadResult(0, "Episode entry", media(0).getId(),
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, true, DownloadError.SUCCESS, new Date(), null)).get();
        DBWriter.addDownloadStatus(new DownloadResult(0, "Feed entry", feed.getId(), Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, new Date(), null)).get();
        assertEquals(2, DBReader.getDownloadLog().size());

        DBWriter.deleteFeedItems(context, Collections.singletonList(DBReader.getFeedItem(downloaded.getId()))).get();

        assertNull(DBReader.getFeedItem(downloaded.getId()));
        assertEquals(1, DBReader.getDownloadLog().size());
        assertEquals("Feed entry", DBReader.getDownloadLog().get(0).getTitle());
    }

    @Test
    public void deletingEpisodesRemovesThemFromTheQueueAndDeletesTheirFiles() throws Exception {
        fixture.markDownloaded(item(1));
        File file = new File(media(1).getLocalFileUrl());
        DBWriter.addQueueItem(context, item(0), item(1)).get();

        FeedItem toDelete = DBReader.getFeedItem(item(1).getId());
        toDelete.setFeed(DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE));
        DBWriter.deleteFeedItems(context, Collections.singletonList(toDelete)).get();

        assertFalse(file.exists());
        assertEquals(1, DBReader.getQueue().size());
        assertNull(DBReader.getFeedItem(toDelete.getId()));
        assertNotNull(DBReader.getFeedItem(item(0).getId()));
    }

    @Test
    public void resettingStatisticsClearsThePlayedTime() throws Exception {
        FeedMedia played = media(0);
        played.setPlayedDuration(50_000);
        played.setLastPlayedTimeStatistics(System.currentTimeMillis());
        played.setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(played).get();
        assertEquals(50_000, media(0).getPlayedDuration());

        DBWriter.resetStatistics().get();

        assertEquals(0, media(0).getPlayedDuration());
    }
}

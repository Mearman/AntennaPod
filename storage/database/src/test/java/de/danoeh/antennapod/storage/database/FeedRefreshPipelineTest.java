package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedRefreshPipelineTest extends FeedPipelineTestBase {

    private static String episode(String guid, String title, String pubDate) {
        return "<item><guid>" + guid + "</guid><title>" + title + "</title><pubDate>" + pubDate + "</pubDate>"
                + "<enclosure url=\"https://example.com/" + guid + ".mp3\" length=\"5000000\" type=\"audio/mpeg\"/>"
                + "</item>\n";
    }

    private static String feedWith(String title, String... episodes) {
        return rss("<title>" + title + "</title>\n" + String.join("", episodes));
    }

    private static final String OLDER = "Mon, 02 Jan 2006 15:04:05 +0000";
    private static final String OLDEST = "Sun, 01 Jan 2006 15:04:05 +0000";
    private static final String NEWER = "Tue, 03 Jan 2006 15:04:05 +0000";

    @Test
    public void refreshUpdatesChangedMetadataAndKeepsEpisodeIdentity() throws Exception {
        Feed stored = parseAndStore(feedWith("Original", episode("a", "Original title", OLDER)));
        long itemId = storedItem(stored, "a").getId();

        Feed refreshed = refresh(feedWith("Renamed", episode("a", "Renamed title", OLDER)));

        assertEquals(stored.getId(), refreshed.getId());
        assertEquals("Renamed", refreshed.getTitle());
        assertEquals(1, storedItems(refreshed).size());
        FeedItem item = storedItem(refreshed, "a");
        assertEquals(itemId, item.getId());
        assertEquals("Renamed title", item.getTitle());
    }

    @Test
    public void refreshKeepsPlayStateAndPositionOfExistingEpisodes() throws Exception {
        Feed stored = parseAndStore(feedWith("Progress", episode("a", "Episode", OLDER)));
        FeedItem item = storedItem(stored, "a");
        item.getMedia().setPosition(45000);
        item.getMedia().setLastPlayedTimeHistory(new Date());
        DBWriter.setFeedMediaPlaybackInformation(item.getMedia());
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(item));
        DBWriter.tearDownTests();

        Feed refreshed = refresh(feedWith("Progress", episode("a", "Episode", OLDER)));

        FeedItem after = storedItem(refreshed, "a");
        assertTrue(after.isPlayed());
        assertEquals(45000, after.getMedia().getPosition());
    }

    @Test
    public void refreshKeepsPreferencesAndCustomTitleChosenByTheUser() throws Exception {
        Feed stored = parseAndStore(feedWith("Preferences", episode("a", "Episode", OLDER)));
        FeedPreferences preferences = stored.getPreferences();
        preferences.setAutoDeleteAction(FeedPreferences.AutoDeleteAction.ALWAYS);
        preferences.setFeedPlaybackSpeed(1.5f);
        DBWriter.setFeedPreferences(preferences);
        stored.setCustomTitle("My name for it");
        DBWriter.setFeedCustomTitle(stored);
        DBWriter.tearDownTests();

        Feed refreshed = refresh(feedWith("Preferences", episode("a", "Episode", OLDER)));

        assertEquals("My name for it", refreshed.getTitle());
        assertEquals("Preferences", refreshed.getFeedTitle());
        assertEquals(FeedPreferences.AutoDeleteAction.ALWAYS, refreshed.getPreferences().getAutoDeleteAction());
        assertEquals(1.5f, refreshed.getPreferences().getFeedPlaybackSpeed(), 0.0001f);
    }

    @Test
    public void newestEpisodeAppearingInRefreshLandsInTheInbox() throws Exception {
        parseAndStore(feedWith("Inbox", episode("a", "Older", OLDER)));

        Feed refreshed = refresh(feedWith("Inbox", episode("b", "Newer", NEWER), episode("a", "Older", OLDER)));

        assertEquals(2, storedItems(refreshed).size());
        assertTrue(storedItem(refreshed, "b").isNew());
        assertFalse(storedItem(refreshed, "a").isNew());
    }

    @Test
    public void episodeOlderThanTheNewestStoredOneIsNotAddedToTheInbox() throws Exception {
        parseAndStore(feedWith("Backfill", episode("a", "Older", OLDER)));

        Feed refreshed = refresh(feedWith("Backfill", episode("a", "Older", OLDER),
                episode("z", "Oldest", OLDEST)));

        assertEquals(2, storedItems(refreshed).size());
        assertFalse(storedItem(refreshed, "z").isNew());
    }

    @Test
    public void newEpisodeIsQueuedWhenTheFeedRequestsIt() throws Exception {
        Feed stored = parseAndStore(feedWith("Queueing", episode("a", "Older", OLDER)));
        stored.getPreferences().setNewEpisodesAction(FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE);
        DBWriter.setFeedPreferences(stored.getPreferences());
        DBWriter.tearDownTests();

        Feed refreshed = refresh(feedWith("Queueing", episode("b", "Newer", NEWER), episode("a", "Older", OLDER)));

        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(1, queue.size());
        assertEquals(storedItem(refreshed, "b").getId(), queue.get(0).getId());
        assertFalse(storedItem(refreshed, "b").isNew());
    }

    @Test
    public void newEpisodeIsLeftAloneWhenTheFeedDoesNothingWithNewEpisodes() throws Exception {
        Feed stored = parseAndStore(feedWith("Quiet", episode("a", "Older", OLDER)));
        stored.getPreferences().setNewEpisodesAction(FeedPreferences.NewEpisodesAction.NOTHING);
        DBWriter.setFeedPreferences(stored.getPreferences());
        DBWriter.tearDownTests();

        Feed refreshed = refresh(feedWith("Quiet", episode("b", "Newer", NEWER), episode("a", "Older", OLDER)));

        assertFalse(storedItem(refreshed, "b").isNew());
        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void episodesMissingFromTheRefreshedFeedAreKeptUnlessRemovalIsRequested() throws Exception {
        parseAndStore(feedWith("Removal", episode("a", "First", OLDER), episode("b", "Second", NEWER)));

        Feed kept = refresh(feedWith("Removal", episode("b", "Second", NEWER)), false);
        assertEquals(2, storedItems(kept).size());

        Feed removed = refresh(feedWith("Removal", episode("b", "Second", NEWER)), true);
        List<FeedItem> items = storedItems(removed);
        assertEquals(1, items.size());
        assertEquals("b", items.get(0).getItemIdentifier());
    }

    @Test
    public void episodeWithChangedGuidIsRepairedAndReportedInTheDownloadLog() throws Exception {
        Feed stored = parseAndStore(feedWith("Repair", episode("old-guid", "Same episode", OLDER)));
        FeedItem original = storedItem(stored, "old-guid");
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(original));
        DBWriter.tearDownTests();

        Feed refreshed = refresh(feedWith("Repair", episode("new-guid", "Same episode", OLDER)));

        List<FeedItem> items = storedItems(refreshed);
        assertEquals(1, items.size());
        assertEquals(original.getId(), items.get(0).getId());
        assertEquals("new-guid", items.get(0).getItemIdentifier());
        assertTrue(items.get(0).isPlayed());
        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, log.get(0).getReason());
        assertFalse(log.get(0).isSuccessful());
        assertEquals(stored.getId(), log.get(0).getFeedfileId());
        assertEquals(Feed.FEEDFILETYPE_FEED, log.get(0).getFeedfileType());
        assertTrue(log.get(0).getReasonDetailed().contains("changed the ID of an existing episode"));
    }

    @Test
    public void episodeListedTwiceInARefreshIsStoredOnceAndReportedAsDuplicate() throws Exception {
        parseAndStore(feedWith("Twice", episode("a", "Episode", OLDER)));

        Feed refreshed = refresh(feedWith("Twice", episode("a", "Episode", OLDER),
                episode("a-again", "Episode", OLDER)));

        assertEquals(1, storedItems(refreshed).size());
        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, log.get(0).getReason());
    }

    @Test
    public void refreshingASecondPageOnlyUpdatesTheNextPageLink() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Paged</title>
                <atom:link rel="next" href="https://example.com/feed.xml?page=2"/>
                """) );
        assertEquals("https://example.com/feed.xml?page=2", stored.getNextPageLink());

        Feed secondPage = parse(rss("""
                <title>Different title on page two</title>
                <atom:link rel="next" href="https://example.com/feed.xml?page=3"/>
                """)).feed;
        secondPage.setPageNr(1);
        FeedDatabaseWriter.updateFeed(context, secondPage, false);
        DBWriter.tearDownTests();

        Feed refreshed = reload(stored);
        assertEquals("Paged", refreshed.getTitle());
        assertEquals("https://example.com/feed.xml?page=3", refreshed.getNextPageLink());
    }

    @Test
    public void refreshClearsTheFailedUpdateFlag() throws Exception {
        Feed stored = parseAndStore(feedWith("Failing", episode("a", "Episode", OLDER)));
        DBWriter.setFeedLastUpdateFailed(stored.getId(), true);
        DBWriter.tearDownTests();
        assertTrue(reload(stored).hasLastUpdateFailed());

        Feed refreshed = refresh(feedWith("Failing", episode("a", "Episode", OLDER)));

        assertFalse(refreshed.hasLastUpdateFailed());
    }

    @Test
    public void refreshFillsInMissingEpisodeDetailsWithoutLosingExistingOnes() throws Exception {
        Feed stored = parseAndStore(feedWith("Details", episode("a", "Episode", OLDER)));
        assertNull(storedItem(stored, "a").getPodcastIndexChapterUrl());

        Feed refreshed = refresh(rss("""
                <title>Details</title>
                <item>
                  <guid>a</guid><title>Episode</title>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>
                  <enclosure url="https://example.com/a.mp3" length="5000000" type="audio/mpeg"/>
                  <itunes:duration>10:00</itunes:duration>
                  <podcast:chapters url="https://example.com/a-chapters.json" type="application/json+chapters"/>
                </item>
                """));

        FeedItem item = storedItem(refreshed, "a");
        assertEquals("https://example.com/a-chapters.json", item.getPodcastIndexChapterUrl());
        assertNotNull(item.getMedia());
        assertEquals(600000, item.getMedia().getDuration());
        assertEquals("https://example.com/a.mp3", item.getMedia().getDownloadUrl());
    }
}

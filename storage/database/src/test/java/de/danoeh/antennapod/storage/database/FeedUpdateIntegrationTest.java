package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class FeedUpdateIntegrationTest extends DatabaseTestBase {
    private static final String FEED_URL = "https://example.com/podcast.xml";
    private static final long DAY = 24L * 3600L * 1000L;
    private static final long MINUTE = 60L * 1000L;

    private static Feed newFeed() {
        Feed feed = new Feed(FEED_URL, null, "Podcast");
        feed.setItems(new ArrayList<>());
        return feed;
    }

    private static FeedItem addEpisode(Feed feed, String guid, String title, long pubDate, String mediaUrl,
                                       int durationMillis, String mimeType) {
        FeedItem item = new FeedItem(0, title, guid, "https://example.com/" + guid, new Date(pubDate),
                FeedItem.UNPLAYED, feed);
        FeedMedia media = new FeedMedia(item, mediaUrl, 1000, mimeType);
        media.setDuration(durationMillis);
        item.setMedia(media);
        feed.getItems().add(item);
        return item;
    }

    private static FeedItem addEpisode(Feed feed, String guid, String title, long pubDate) {
        return addEpisode(feed, guid, title, pubDate, "https://example.com/" + guid + ".mp3", 60000, "audio/mpeg");
    }

    private Feed subscribeWithEpisode(String guid, String title, long pubDate) {
        Feed feed = newFeed();
        addEpisode(feed, guid, title, pubDate);
        Feed stored = FeedDatabaseWriter.updateFeed(context, feed, false);
        DBWriter.tearDownTests();
        return stored;
    }

    private List<String> storedGuids(Feed feed) {
        List<String> guids = new ArrayList<>();
        for (FeedItem item : DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE).getItems()) {
            guids.add(item.getItemIdentifier());
        }
        Collections.sort(guids);
        return guids;
    }

    private List<DownloadResult> duplicateWarnings(Feed feed) {
        DBWriter.tearDownTests();
        List<DownloadResult> warnings = new ArrayList<>();
        for (DownloadResult result : DBReader.getFeedDownloadLog(feed.getId(), 50)) {
            if (result.getReason() == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE) {
                warnings.add(result);
            }
        }
        return warnings;
    }

    private FeedItem storedItem(Feed feed, String guid) {
        for (FeedItem item : DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE).getItems()) {
            if (guid.equals(item.getItemIdentifier())) {
                return item;
            }
        }
        return null;
    }

    private void setNewEpisodesAction(Feed feed, FeedPreferences.NewEpisodesAction action,
                                      FeedPreferences.AutoDownloadSetting autoDownload) {
        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 1);
        stored.getPreferences().setNewEpisodesAction(action);
        stored.getPreferences().setAutoDownload(autoDownload);
        await(DBWriter.setFeedPreferences(stored.getPreferences()));
    }

    private Feed updateWithEpisode(Feed subscribed, String guid, String title, long pubDate) {
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, guid, title, pubDate);
        Feed result = FeedDatabaseWriter.updateFeed(context, update, false);
        DBWriter.tearDownTests();
        return result;
    }

    @Test
    public void updateWithKnownFeedIdReplacesDescriptiveAttributes() {
        Feed subscribed = subscribeWithEpisode("one", "One", 1000);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        update.setTitle("Renamed podcast");
        update.setDescription("Fresh description");
        update.setAuthor("New author");

        FeedDatabaseWriter.updateFeed(context, update, false);

        Feed stored = DBReader.getFeed(subscribed.getId(), false, 0, 10);
        assertEquals("Renamed podcast", stored.getFeedTitle());
        assertEquals("Fresh description", stored.getDescription());
        assertEquals("New author", stored.getAuthor());
        assertEquals(1, stored.getItems().size());
    }

    @Test
    public void updateOfHigherFeedPageOnlyMovesToNextPage() {
        Feed subscribed = subscribeWithEpisode("one", "One", 1000);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        update.setTitle("Title from later page");
        update.setPageNr(1);
        update.setNextPageLink("https://example.com/podcast.xml?page=2");
        addEpisode(update, "two", "Two", 2000);

        FeedDatabaseWriter.updateFeed(context, update, false);

        Feed stored = DBReader.getFeed(subscribed.getId(), false, 0, 10);
        assertEquals("Podcast", stored.getFeedTitle());
        assertEquals("https://example.com/podcast.xml?page=2", stored.getNextPageLink());
        assertEquals(Arrays.asList("one", "two"), storedGuids(subscribed));
    }

    @Test
    public void newerEpisodesGoToInboxByDefault() {
        Feed subscribed = subscribeWithEpisode("old", "Old", 1000);

        updateWithEpisode(subscribed, "fresh", "Fresh", 2000);

        assertTrue(storedItem(subscribed, "fresh").isNew());
        assertFalse(storedItem(subscribed, "old").isNew());
    }

    @Test
    public void episodesOlderThanExistingOnesAreNotMarkedNew() {
        Feed subscribed = subscribeWithEpisode("recent", "Recent", 5000);

        updateWithEpisode(subscribed, "backlog", "Backlog", 1000);

        assertFalse(storedItem(subscribed, "backlog").isNew());
    }

    @Test
    public void newEpisodesAreAddedToQueueWhenFeedSaysSo() {
        Feed subscribed = subscribeWithEpisode("old", "Old", 1000);
        setNewEpisodesAction(subscribed, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
                FeedPreferences.AutoDownloadSetting.DISABLED);

        updateWithEpisode(subscribed, "fresh", "Fresh", 2000);

        List<FeedItem> queue = DBReader.getQueue();
        assertEquals(1, queue.size());
        assertEquals("fresh", queue.get(0).getItemIdentifier());
        assertFalse(storedItem(subscribed, "fresh").isNew());
    }

    @Test
    public void newEpisodesAreLeftAloneWhenFeedSaysNothing() {
        Feed subscribed = subscribeWithEpisode("old", "Old", 1000);
        setNewEpisodesAction(subscribed, FeedPreferences.NewEpisodesAction.NOTHING,
                FeedPreferences.AutoDownloadSetting.DISABLED);

        updateWithEpisode(subscribed, "fresh", "Fresh", 2000);

        assertTrue(DBReader.getQueue().isEmpty());
        assertFalse(storedItem(subscribed, "fresh").isNew());
    }

    @Test
    public void enabledAutoDownloadOverridesQueueActionAndUsesInbox() {
        Feed subscribed = subscribeWithEpisode("old", "Old", 1000);
        setNewEpisodesAction(subscribed, FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE,
                FeedPreferences.AutoDownloadSetting.ENABLED);

        updateWithEpisode(subscribed, "fresh", "Fresh", 2000);

        assertTrue(DBReader.getQueue().isEmpty());
        assertTrue(storedItem(subscribed, "fresh").isNew());
    }

    @Test
    public void episodesOfFeedsThatAreNotSubscribedAreNotMarkedNew() {
        Feed subscribed = subscribeWithEpisode("old", "Old", 1000);
        await(DBWriter.setFeedState(context, DBReader.getFeed(subscribed.getId(), false, 0, 1),
                Feed.STATE_NOT_SUBSCRIBED));

        updateWithEpisode(subscribed, "fresh", "Fresh", 2000);

        assertFalse(storedItem(subscribed, "fresh").isNew());
    }

    @Test
    public void changedEpisodeIdIsRepairedInsteadOfDuplicatingTheEpisode() {
        Feed subscribed = subscribeWithEpisode("old-guid", "Episode title", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "new-guid", "Episode title", DAY + MINUTE, "https://cdn.example.com/other.mp3", 61000,
                "audio/mp3");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Collections.singletonList("new-guid"), storedGuids(subscribed));
        List<DownloadResult> warnings = duplicateWarnings(subscribed);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getReasonDetailed().contains("changed the ID of an existing episode"));
        assertTrue(warnings.get(0).getReasonDetailed().contains("ID: old-guid"));
        assertTrue(warnings.get(0).getReasonDetailed().contains("ID: new-guid"));
    }

    @Test
    public void repairedPlayedEpisodeIsReportedAsPlayedForSync() {
        Feed subscribed = subscribeWithEpisode("old-guid", "Episode title", DAY);
        FeedItem played = storedItem(subscribed, "old-guid");
        await(DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(played)));
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "new-guid", "Episode title", DAY, "https://cdn.example.com/other.mp3", 60000,
                "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(1, synchronizationQueue.getEpisodeActions().size());
        EpisodeAction action = synchronizationQueue.getEpisodeActions().get(0);
        assertEquals(EpisodeAction.PLAY, action.getAction());
        assertEquals("new-guid", action.getGuid());
    }

    @Test
    public void episodeWithSameStreamUrlAndNewIdIsTreatedAsSameEpisode() {
        Feed subscribed = subscribeWithEpisode("old-guid", "Original title", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "new-guid", "Completely different title", 30 * DAY,
                storedItem(subscribed, "old-guid").getMedia().getDownloadUrl(), 120000, "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Collections.singletonList("new-guid"), storedGuids(subscribed));
    }

    @Test
    public void titleQuotesAndDashesAreNormalisedWhenComparingEpisodes() {
        Feed subscribed = subscribeWithEpisode("old-guid", "\u201cQuoted\u201d \u2014 episode", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "new-guid", "\"Quoted\" - episode", DAY, "https://cdn.example.com/other.mp3", 60000,
                "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Collections.singletonList("new-guid"), storedGuids(subscribed));
    }

    @Test
    public void episodesWithSameTitleOnDifferentDaysAreKeptSeparate() {
        Feed subscribed = subscribeWithEpisode("first", "Weekly show", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "second", "Weekly show", 5 * DAY, "https://cdn.example.com/other.mp3", 60000,
                "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Arrays.asList("first", "second"), storedGuids(subscribed));
        assertTrue(duplicateWarnings(subscribed).isEmpty());
    }

    @Test
    public void episodesWithVeryDifferentDurationAreKeptSeparate() {
        Feed subscribed = subscribeWithEpisode("first", "Weekly show", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "second", "Weekly show", DAY, "https://cdn.example.com/other.mp3", 60000 + 11 * 60000,
                "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Arrays.asList("first", "second"), storedGuids(subscribed));
    }

    @Test
    public void episodesWithDifferentMediaTypeAreKeptSeparate() {
        Feed subscribed = subscribeWithEpisode("first", "Weekly show", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "second", "Weekly show", DAY, "https://cdn.example.com/other.mp4", 60000, "video/mp4");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Arrays.asList("first", "second"), storedGuids(subscribed));
    }

    @Test
    public void episodesWithoutMimeTypeCanStillBeMatchedAsDuplicates() {
        Feed subscribed = subscribeWithEpisode("first", "Weekly show", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "second", "Weekly show", DAY, "https://cdn.example.com/other.mp3", 60000, null);

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Collections.singletonList("second"), storedGuids(subscribed));
    }

    @Test
    public void episodeListedTwiceInSameFeedIsStoredOnceAndReported() {
        Feed subscribed = subscribeWithEpisode("existing", "Existing", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        addEpisode(update, "twin-a", "Twin", 3 * DAY, "https://cdn.example.com/twin.mp3", 60000, "audio/mpeg");
        addEpisode(update, "twin-b", "Twin", 3 * DAY, "https://cdn.example.com/twin.mp3", 60000, "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        List<String> guids = storedGuids(subscribed);
        assertEquals(2, guids.size());
        List<DownloadResult> warnings = duplicateWarnings(subscribed);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).getReasonDetailed().contains("added the same episode twice"));
    }

    @Test
    public void duplicateEpisodesWithoutMediaAreReportedWithoutMediaDetails() {
        Feed subscribed = subscribeWithEpisode("existing", "Existing", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        for (int i = 0; i < 2; i++) {
            update.getItems().add(new FeedItem(0, "Text post", "same-guid", "link", new Date(2 * DAY),
                    FeedItem.UNPLAYED, update));
        }

        FeedDatabaseWriter.updateFeed(context, update, false);

        List<DownloadResult> warnings = duplicateWarnings(subscribed);
        assertEquals(1, warnings.size());
        assertFalse(warnings.get(0).getReasonDetailed().contains("URL:"));
        assertEquals(Arrays.asList("existing", "same-guid"), storedGuids(subscribed));
    }

    @Test
    public void localFeedsNeverGuessDuplicateEpisodes() {
        Feed local = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://folder", null, "Local");
        local.setItems(new ArrayList<>());
        addEpisode(local, "first", "Same title", DAY);
        Feed subscribed = FeedDatabaseWriter.updateFeed(context, local, false);
        Feed update = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://folder", null, "Local");
        update.setItems(new ArrayList<>());
        update.setId(subscribed.getId());
        addEpisode(update, "second", "Same title", DAY, "content://folder/other.mp3", 60000, "audio/mpeg");

        FeedDatabaseWriter.updateFeed(context, update, false);

        assertEquals(Arrays.asList("first", "second"), storedGuids(subscribed));
        assertTrue(duplicateWarnings(subscribed).isEmpty());
    }

    @Test
    public void episodesWithoutPublicationDateAreStoredWithCurrentDate() {
        Feed subscribed = subscribeWithEpisode("dated", "Dated", DAY);
        Feed update = newFeed();
        update.setId(subscribed.getId());
        FeedItem undated = new FeedItem(0, "Undated", "undated", "link", null, FeedItem.UNPLAYED, update);
        update.getItems().add(undated);
        addEpisode(update, "newer", "Newer", 3 * DAY);
        addEpisode(update, "newest", "Newest", 4 * DAY);
        long before = System.currentTimeMillis();

        Feed result = FeedDatabaseWriter.updateFeed(context, update, false);

        assertNotNull(result);
        assertEquals(Arrays.asList("dated", "newer", "newest", "undated"), storedGuids(subscribed));
        assertTrue(storedItem(subscribed, "undated").getPubDate().getTime() >= before);
    }
}

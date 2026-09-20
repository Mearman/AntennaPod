package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NonSubscribedFeedsCleanerDecisionTest {

    @Test
    public void subscribedAndArchivedFeedsAreNeverDeleted() {
        Feed subscribed = feed(Feed.STATE_SUBSCRIBED, daysAgo(200));
        Feed archived = feed(Feed.STATE_ARCHIVED, daysAgo(200));
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(subscribed));
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(archived));
    }

    @Test
    public void feedWhoseItemsWereNotLoadedIsKept() {
        Feed feed = new Feed("url", null, "title");
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        feed.setLastRefreshAttempt(daysAgo(200));
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void untouchedFeedIsDeletedOnceItIsOlderThanOneDay() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, hoursAgo(12));
        feed.getItems().add(item(FeedItem.UNPLAYED));
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));

        feed.setLastRefreshAttempt(hoursAgo(36));
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void feedWithoutItemsIsTreatedAsUntouched() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, hoursAgo(36));
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void feedWithPlayedEpisodeIsKeptForThirtyDays() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, daysAgo(10));
        feed.getItems().add(item(FeedItem.PLAYED));
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));

        feed.setLastRefreshAttempt(daysAgo(40));
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void feedWithPartiallyPlayedEpisodeIsKeptForThirtyDays() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, daysAgo(10));
        FeedItem started = item(FeedItem.UNPLAYED);
        started.getMedia().setPosition(5_000);
        feed.getItems().add(started);
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));

        feed.setLastRefreshAttempt(daysAgo(40));
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void feedWithFavoriteEpisodeIsNeverDeleted() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, daysAgo(400));
        FeedItem favorite = item(FeedItem.UNPLAYED);
        favorite.addTag(FeedItem.TAG_FAVORITE);
        feed.getItems().add(favorite);
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void feedWithQueuedEpisodeIsNeverDeleted() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, daysAgo(400));
        FeedItem queued = item(FeedItem.UNPLAYED);
        queued.addTag(FeedItem.TAG_QUEUE);
        feed.getItems().add(queued);
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void feedWithDownloadedEpisodeIsNeverDeleted() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, daysAgo(400));
        FeedItem downloaded = new FeedItem(1, "title", "guid", "link", null, FeedItem.UNPLAYED, feed);
        downloaded.setMedia(new FeedMedia(1, downloaded, 0, 0, 0, "audio/mpeg", "/file.mp3", "url",
                System.currentTimeMillis(), null, 0, 0));
        feed.getItems().add(downloaded);
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    @Test
    public void refreshInTheFutureIsNeverOldEnoughToDelete() {
        Feed feed = feed(Feed.STATE_NOT_SUBSCRIBED, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(200));
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed));
    }

    private static long daysAgo(long days) {
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
    }

    private static long hoursAgo(long hours) {
        return System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours);
    }

    private static Feed feed(int state, long lastRefreshAttempt) {
        Feed feed = new Feed("http://example.com/feed", null, "title");
        feed.setState(state);
        feed.setLastRefreshAttempt(lastRefreshAttempt);
        feed.setItems(new ArrayList<>());
        return feed;
    }

    private static FeedItem item(int playState) {
        FeedItem item = new FeedItem(1, "title", "guid", "link", null, playState, null);
        item.setMedia(new FeedMedia(item, "http://example.com/episode.mp3", 0, "audio/mpeg"));
        return item;
    }
}

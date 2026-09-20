package de.danoeh.antennapod.model.feed;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class FeedItemFilterTest {
    private Feed feed;
    private FeedItem item;

    @Before
    public void setUp() {
        feed = FeedMother.anyFeed();
        item = new FeedItem(1, "Item", "guid", "http://example.com/item", new Date(), FeedItem.UNPLAYED, feed);
    }

    private static FeedMedia mediaFor(FeedItem item) {
        FeedMedia media = FeedMediaMother.anyFeedMedia();
        item.setMedia(media);
        return media;
    }

    @Test
    public void unfiltered_hasNoPropertiesAndMatchesSubscribedItems() {
        FeedItemFilter filter = FeedItemFilter.unfiltered();
        assertEquals(0, filter.getValues().length);
        assertTrue(filter.getValuesList().isEmpty());
        assertTrue(filter.matches(item));
    }

    @Test
    public void stringConstructor_splitsProperties() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED + "," + FeedItemFilter.DOWNLOADED);
        assertArrayEquals(new String[] {"played", "downloaded"}, filter.getValues());
        assertTrue(filter.showPlayed);
        assertTrue(filter.showDownloaded);
        assertFalse(filter.showUnplayed);
    }

    @Test
    public void stringConstructor_emptyString_hasNoProperties() {
        assertEquals(0, new FeedItemFilter("").getValues().length);
    }

    @Test
    public void varargsConstructor_joinsPropertiesAndSplitsCommaSeparatedEntries() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.NEW, FeedItemFilter.QUEUED + ","
                + FeedItemFilter.HAS_MEDIA);
        assertEquals(Arrays.asList("new", "queued", "has_media"), filter.getValuesList());
        assertTrue(filter.showNew);
        assertTrue(filter.showQueued);
        assertTrue(filter.showHasMedia);
    }

    @Test
    public void extendingConstructor_keepsExistingPropertiesAndAddsNewOnes() {
        FeedItemFilter base = new FeedItemFilter(FeedItemFilter.PLAYED);
        FeedItemFilter extended = new FeedItemFilter(base, FeedItemFilter.DOWNLOADED, FeedItemFilter.IS_FAVORITE);
        assertEquals(Arrays.asList("played", "downloaded", "is_favorite"), extended.getValuesList());
        assertEquals(Arrays.asList("played"), base.getValuesList());
    }

    @Test
    public void flags_mapEveryProperty() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED, FeedItemFilter.UNPLAYED,
                FeedItemFilter.NEW, FeedItemFilter.PAUSED, FeedItemFilter.NOT_PAUSED, FeedItemFilter.IS_FAVORITE,
                FeedItemFilter.NOT_FAVORITE, FeedItemFilter.HAS_MEDIA, FeedItemFilter.NO_MEDIA,
                FeedItemFilter.QUEUED, FeedItemFilter.NOT_QUEUED, FeedItemFilter.DOWNLOADED,
                FeedItemFilter.NOT_DOWNLOADED, FeedItemFilter.IS_IN_HISTORY, FeedItemFilter.INCLUDE_SUBSCRIBED,
                FeedItemFilter.INCLUDE_ARCHIVED, FeedItemFilter.INCLUDE_NOT_SUBSCRIBED);
        assertTrue(filter.showPlayed);
        assertTrue(filter.showUnplayed);
        assertTrue(filter.showNew);
        assertTrue(filter.showPaused);
        assertTrue(filter.showNotPaused);
        assertTrue(filter.showIsFavorite);
        assertTrue(filter.showNotFavorite);
        assertTrue(filter.showHasMedia);
        assertTrue(filter.showNoMedia);
        assertTrue(filter.showQueued);
        assertTrue(filter.showNotQueued);
        assertTrue(filter.showDownloaded);
        assertTrue(filter.showNotDownloaded);
        assertTrue(filter.showInHistory);
        assertTrue(filter.includeSubscribed);
        assertTrue(filter.includeArchived);
        assertTrue(filter.includeNotSubscribed);
    }

    @Test
    public void includeAllFeedStates_enablesAllFeedStateFlags() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES);
        assertTrue(filter.includeSubscribed);
        assertTrue(filter.includeArchived);
        assertTrue(filter.includeNotSubscribed);
    }

    @Test
    public void getValues_returnsDefensiveCopy() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED);
        String[] values = filter.getValues();
        values[0] = "changed";
        assertNotSame(values, filter.getValues());
        assertEquals("played", filter.getValues()[0]);
    }

    @Test
    public void without_removesOnlyGivenProperty() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED, FeedItemFilter.DOWNLOADED);
        FeedItemFilter reduced = filter.without(FeedItemFilter.PLAYED);
        assertEquals(Arrays.asList("downloaded"), reduced.getValuesList());
        assertFalse(reduced.showPlayed);
        assertTrue(reduced.showDownloaded);
        assertTrue(filter.showPlayed);
    }

    @Test
    public void without_absentProperty_keepsFilterContents() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED);
        assertEquals(filter.getValuesList(), filter.without(FeedItemFilter.NEW).getValuesList());
    }

    @Test
    public void matches_newFilter_requiresNewItem() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.NEW);
        assertFalse(filter.matches(item));
        item.setNew();
        assertTrue(filter.matches(item));
    }

    @Test
    public void matches_playedFilter_requiresPlayedItem() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED);
        assertFalse(filter.matches(item));
        item.setPlayed(true);
        assertTrue(filter.matches(item));
    }

    @Test
    public void matches_unplayedFilter_rejectsPlayedItem() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.UNPLAYED);
        assertTrue(filter.matches(item));
        item.setPlayed(true);
        assertFalse(filter.matches(item));
    }

    @Test
    public void matches_pausedFilters_dependOnPlaybackProgress() {
        FeedItemFilter paused = new FeedItemFilter(FeedItemFilter.PAUSED);
        FeedItemFilter notPaused = new FeedItemFilter(FeedItemFilter.NOT_PAUSED);
        FeedMedia media = mediaFor(item);
        assertFalse(paused.matches(item));
        assertTrue(notPaused.matches(item));

        media.setPosition(5000);
        assertTrue(paused.matches(item));
        assertFalse(notPaused.matches(item));
    }

    @Test
    public void matches_queueFilters_dependOnQueueTag() {
        FeedItemFilter queued = new FeedItemFilter(FeedItemFilter.QUEUED);
        FeedItemFilter notQueued = new FeedItemFilter(FeedItemFilter.NOT_QUEUED);
        assertFalse(queued.matches(item));
        assertTrue(notQueued.matches(item));

        item.addTag(FeedItem.TAG_QUEUE);
        assertTrue(queued.matches(item));
        assertFalse(notQueued.matches(item));
    }

    @Test
    public void matches_favoriteFilters_dependOnFavoriteTag() {
        FeedItemFilter favorite = new FeedItemFilter(FeedItemFilter.IS_FAVORITE);
        FeedItemFilter notFavorite = new FeedItemFilter(FeedItemFilter.NOT_FAVORITE);
        assertFalse(favorite.matches(item));
        assertTrue(notFavorite.matches(item));

        item.addTag(FeedItem.TAG_FAVORITE);
        assertTrue(favorite.matches(item));
        assertFalse(notFavorite.matches(item));
    }

    @Test
    public void matches_downloadFilters_dependOnDownloadState() {
        FeedItemFilter downloaded = new FeedItemFilter(FeedItemFilter.DOWNLOADED);
        FeedItemFilter notDownloaded = new FeedItemFilter(FeedItemFilter.NOT_DOWNLOADED);
        FeedMedia media = mediaFor(item);
        assertFalse(downloaded.matches(item));
        assertTrue(notDownloaded.matches(item));

        media.setDownloaded(true, 1000);
        assertTrue(downloaded.matches(item));
        assertFalse(notDownloaded.matches(item));
    }

    @Test
    public void matches_mediaFilters_dependOnMediaPresence() {
        FeedItemFilter hasMedia = new FeedItemFilter(FeedItemFilter.HAS_MEDIA);
        FeedItemFilter noMedia = new FeedItemFilter(FeedItemFilter.NO_MEDIA);
        assertFalse(hasMedia.matches(item));
        assertTrue(noMedia.matches(item));

        mediaFor(item);
        assertTrue(hasMedia.matches(item));
        assertFalse(noMedia.matches(item));
    }

    @Test
    public void matches_historyFilter_rejectsMediaThatWasNeverPlayed() {
        FeedItemFilter history = new FeedItemFilter(FeedItemFilter.IS_IN_HISTORY);
        FeedMedia media = mediaFor(item);
        media.setLastPlayedTimeHistory(new Date(0));
        assertFalse(history.matches(item));

        media.setLastPlayedTimeHistory(new Date(123456));
        assertTrue(history.matches(item));
    }

    @Test
    public void matches_combinedFilters_requireAllConditions() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.PLAYED, FeedItemFilter.IS_FAVORITE);
        item.setPlayed(true);
        assertFalse(filter.matches(item));
        item.addTag(FeedItem.TAG_FAVORITE);
        assertTrue(filter.matches(item));
        item.setPlayed(false);
        assertFalse(filter.matches(item));
    }

    @Test
    public void matches_withoutFeedStateFlags_onlySubscribedFeedsMatch() {
        FeedItemFilter filter = FeedItemFilter.unfiltered();
        feed.setState(Feed.STATE_SUBSCRIBED);
        assertTrue(filter.matches(item));
        feed.setState(Feed.STATE_ARCHIVED);
        assertFalse(filter.matches(item));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        assertFalse(filter.matches(item));
    }

    @Test
    public void matches_feedStateFlags_matchOnlyRequestedStates() {
        FeedItemFilter archivedOnly = new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED);
        feed.setState(Feed.STATE_ARCHIVED);
        assertTrue(archivedOnly.matches(item));
        feed.setState(Feed.STATE_SUBSCRIBED);
        assertFalse(archivedOnly.matches(item));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        assertFalse(archivedOnly.matches(item));

        FeedItemFilter notSubscribedOnly = new FeedItemFilter(FeedItemFilter.INCLUDE_NOT_SUBSCRIBED);
        assertTrue(notSubscribedOnly.matches(item));
        feed.setState(Feed.STATE_ARCHIVED);
        assertFalse(notSubscribedOnly.matches(item));

        FeedItemFilter subscribedOnly = new FeedItemFilter(FeedItemFilter.INCLUDE_SUBSCRIBED);
        feed.setState(Feed.STATE_SUBSCRIBED);
        assertTrue(subscribedOnly.matches(item));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        assertFalse(subscribedOnly.matches(item));
    }

    @Test
    public void matches_allFeedStatesIncluded_matchesEveryState() {
        FeedItemFilter filter = new FeedItemFilter(FeedItemFilter.INCLUDE_ALL_FEED_STATES);
        for (int state : new int[] {Feed.STATE_SUBSCRIBED, Feed.STATE_ARCHIVED, Feed.STATE_NOT_SUBSCRIBED}) {
            feed.setState(state);
            assertTrue(filter.matches(item));
        }
    }

    @Test
    public void matches_itemWithoutFeed_ignoresFeedStateRules() {
        item.setFeed(null);
        assertTrue(FeedItemFilter.unfiltered().matches(item));
        assertTrue(new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED).matches(item));
    }
}

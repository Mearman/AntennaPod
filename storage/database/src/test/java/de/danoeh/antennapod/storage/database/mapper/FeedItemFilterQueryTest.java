package de.danoeh.antennapod.storage.database.mapper;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedItemFilterQueryTest {
    private static final String ALL_STATES = FeedItemFilter.INCLUDE_ALL_FEED_STATES;

    private static String query(String... properties) {
        return FeedItemFilterQuery.generateFrom(new FeedItemFilter(properties));
    }

    private static String queryWithAllStates(String... properties) {
        return FeedItemFilterQuery.generateFrom(new FeedItemFilter(new FeedItemFilter(properties), ALL_STATES));
    }

    @Test
    public void filterWithAllStatesAndNoOtherCriteriaProducesNoCondition() {
        assertEquals("", queryWithAllStates());
    }

    @Test
    public void unfilteredOnlyRestrictsToSubscribedFeeds() {
        String query = FeedItemFilterQuery.generateFrom(FeedItemFilter.unfiltered());
        assertTrue(query.contains("FeedItems.feed IN (SELECT id FROM Feeds WHERE state IN ("
                + Feed.STATE_SUBSCRIBED + "))"));
        assertFalse(query.contains("AND"));
    }

    @Test
    public void statesAreRestrictedToTheIncludedOnes() {
        String subscribedOnly = query(FeedItemFilter.INCLUDE_SUBSCRIBED);
        assertTrue(subscribedOnly.contains("state IN (" + Feed.STATE_SUBSCRIBED + ")"));

        String archivedAndNotSubscribed = query(FeedItemFilter.INCLUDE_ARCHIVED,
                FeedItemFilter.INCLUDE_NOT_SUBSCRIBED);
        assertTrue(archivedAndNotSubscribed.contains(
                "state IN (" + Feed.STATE_ARCHIVED + "," + Feed.STATE_NOT_SUBSCRIBED + ")"));

        String subscribedAndArchived = query(FeedItemFilter.INCLUDE_SUBSCRIBED, FeedItemFilter.INCLUDE_ARCHIVED);
        assertTrue(subscribedAndArchived.contains(
                "state IN (" + Feed.STATE_SUBSCRIBED + "," + Feed.STATE_ARCHIVED + ")"));
    }

    @Test
    public void playedStateTakesPrecedenceOverUnplayedAndNew() {
        assertEquals(" (FeedItems.read = 1 ) ",
                queryWithAllStates(FeedItemFilter.PLAYED, FeedItemFilter.UNPLAYED, FeedItemFilter.NEW));
    }

    @Test
    public void unplayedIncludesNewItems() {
        assertEquals(" ( NOT FeedItems.read = 1 ) ",
                queryWithAllStates(FeedItemFilter.UNPLAYED, FeedItemFilter.NEW));
    }

    @Test
    public void newItemsHaveReadStateMinusOne() {
        assertEquals(" (FeedItems.read = -1 ) ", queryWithAllStates(FeedItemFilter.NEW));
    }

    @Test
    public void pausedItemsHavePositivePosition() {
        assertEquals(" ( (FeedMedia.position NOT NULL AND FeedMedia.position > 0 ) ) ",
                queryWithAllStates(FeedItemFilter.PAUSED, FeedItemFilter.NOT_PAUSED));
    }

    @Test
    public void notPausedItemsHaveNoPosition() {
        assertEquals(" ( (FeedMedia.position IS NULL OR FeedMedia.position = 0 ) ) ",
                queryWithAllStates(FeedItemFilter.NOT_PAUSED));
    }

    @Test
    public void queuedFiltersUseQueueTable() {
        assertEquals(" (FeedItems.id IN (SELECT feeditem FROM Queue) ) ",
                queryWithAllStates(FeedItemFilter.QUEUED, FeedItemFilter.NOT_QUEUED));
        assertEquals(" (FeedItems.id NOT IN (SELECT feeditem FROM Queue) ) ",
                queryWithAllStates(FeedItemFilter.NOT_QUEUED));
    }

    @Test
    public void downloadedFilterAlsoAcceptsItemsOfLocalFeeds() {
        String downloaded = queryWithAllStates(FeedItemFilter.DOWNLOADED, FeedItemFilter.NOT_DOWNLOADED);
        String localFeeds = "FeedItems.feed IN (SELECT id FROM Feeds WHERE download_url LIKE '"
                + Feed.PREFIX_LOCAL_FOLDER + "%')";
        assertEquals(" ((FeedMedia.downloaded > 0 OR (" + localFeeds + ")) ) ", downloaded);
    }

    @Test
    public void notDownloadedFilterExcludesItemsOfLocalFeeds() {
        String localFeeds = "FeedItems.feed IN (SELECT id FROM Feeds WHERE download_url LIKE '"
                + Feed.PREFIX_LOCAL_FOLDER + "%')";
        assertEquals(" ((FeedMedia.downloaded = 0 AND NOT (" + localFeeds + ")) ) ",
                queryWithAllStates(FeedItemFilter.NOT_DOWNLOADED));
    }

    @Test
    public void mediaFiltersCheckExistenceOfMediaRow() {
        assertEquals(" (FeedMedia.id NOT NULL ) ",
                queryWithAllStates(FeedItemFilter.HAS_MEDIA, FeedItemFilter.NO_MEDIA));
        assertEquals(" (FeedMedia.id IS NULL ) ", queryWithAllStates(FeedItemFilter.NO_MEDIA));
    }

    @Test
    public void favoriteFiltersUseFavoritesTable() {
        assertEquals(" (FeedItems.id IN (SELECT feeditem FROM Favorites) ) ",
                queryWithAllStates(FeedItemFilter.IS_FAVORITE, FeedItemFilter.NOT_FAVORITE));
        assertEquals(" (FeedItems.id NOT IN (SELECT feeditem FROM Favorites) ) ",
                queryWithAllStates(FeedItemFilter.NOT_FAVORITE));
    }

    @Test
    public void historyFilterRequiresCompletionDate() {
        assertEquals(" (FeedMedia.playback_completion_date > 0 ) ",
                queryWithAllStates(FeedItemFilter.IS_IN_HISTORY));
    }

    @Test
    public void multipleConditionsAreCombinedWithAnd() {
        String query = queryWithAllStates(FeedItemFilter.NEW, FeedItemFilter.QUEUED, FeedItemFilter.HAS_MEDIA);
        assertEquals(" (FeedItems.read = -1  AND FeedItems.id IN (SELECT feeditem FROM Queue)  AND "
                + "FeedMedia.id NOT NULL ) ", query);
    }

    @Test
    public void stateRestrictionIsAppendedAfterOtherConditions() {
        String query = query(FeedItemFilter.PLAYED, FeedItemFilter.INCLUDE_ARCHIVED);
        assertTrue(query.startsWith(" (FeedItems.read = 1  AND FeedItems.feed IN (SELECT id FROM Feeds "
                + "WHERE state IN (" + Feed.STATE_ARCHIVED + "))"));
    }
}

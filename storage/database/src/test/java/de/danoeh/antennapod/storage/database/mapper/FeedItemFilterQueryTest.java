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

    private static String normalised(String sql) {
        return sql.replaceAll("\\s+", " ").replaceAll("\\s*([()])\\s*", "$1").trim();
    }

    private static void assertSql(String expected, String actual) {
        assertEquals(normalised(expected), normalised(actual));
    }

    @Test
    public void filterWithAllStatesAndNoOtherCriteriaProducesNoCondition() {
        assertSql("", queryWithAllStates());
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
        assertSql(" (FeedItems.read = 1 ) ",
                queryWithAllStates(FeedItemFilter.PLAYED, FeedItemFilter.UNPLAYED, FeedItemFilter.NEW));
    }

    @Test
    public void unplayedIncludesNewItems() {
        assertSql(" ( NOT FeedItems.read = 1 ) ",
                queryWithAllStates(FeedItemFilter.UNPLAYED, FeedItemFilter.NEW));
    }

    @Test
    public void newItemsHaveReadStateMinusOne() {
        assertSql(" (FeedItems.read = -1 ) ", queryWithAllStates(FeedItemFilter.NEW));
    }

    @Test
    public void pausedItemsHavePositivePosition() {
        assertSql(" ( (FeedMedia.position NOT NULL AND FeedMedia.position > 0 ) ) ",
                queryWithAllStates(FeedItemFilter.PAUSED, FeedItemFilter.NOT_PAUSED));
    }

    @Test
    public void notPausedItemsHaveNoPosition() {
        assertSql(" ( (FeedMedia.position IS NULL OR FeedMedia.position = 0 ) ) ",
                queryWithAllStates(FeedItemFilter.NOT_PAUSED));
    }

    @Test
    public void queuedFiltersUseQueueTable() {
        assertSql(" (FeedItems.id IN (SELECT feeditem FROM Queue) ) ",
                queryWithAllStates(FeedItemFilter.QUEUED, FeedItemFilter.NOT_QUEUED));
        assertSql(" (FeedItems.id NOT IN (SELECT feeditem FROM Queue) ) ",
                queryWithAllStates(FeedItemFilter.NOT_QUEUED));
    }

    @Test
    public void downloadedFilterAlsoAcceptsItemsOfLocalFeeds() {
        String downloaded = queryWithAllStates(FeedItemFilter.DOWNLOADED, FeedItemFilter.NOT_DOWNLOADED);
        String localFeeds = "FeedItems.feed IN (SELECT id FROM Feeds WHERE download_url LIKE '"
                + Feed.PREFIX_LOCAL_FOLDER + "%')";
        assertSql(" ((FeedMedia.downloaded > 0 OR (" + localFeeds + ")) ) ", downloaded);
    }

    @Test
    public void notDownloadedFilterExcludesItemsOfLocalFeeds() {
        String localFeeds = "FeedItems.feed IN (SELECT id FROM Feeds WHERE download_url LIKE '"
                + Feed.PREFIX_LOCAL_FOLDER + "%')";
        assertSql(" ((FeedMedia.downloaded = 0 AND NOT (" + localFeeds + ")) ) ",
                queryWithAllStates(FeedItemFilter.NOT_DOWNLOADED));
    }

    @Test
    public void mediaFiltersCheckExistenceOfMediaRow() {
        assertSql(" (FeedMedia.id NOT NULL ) ",
                queryWithAllStates(FeedItemFilter.HAS_MEDIA, FeedItemFilter.NO_MEDIA));
        assertSql(" (FeedMedia.id IS NULL ) ", queryWithAllStates(FeedItemFilter.NO_MEDIA));
    }

    @Test
    public void favoriteFiltersUseFavoritesTable() {
        assertSql(" (FeedItems.id IN (SELECT feeditem FROM Favorites) ) ",
                queryWithAllStates(FeedItemFilter.IS_FAVORITE, FeedItemFilter.NOT_FAVORITE));
        assertSql(" (FeedItems.id NOT IN (SELECT feeditem FROM Favorites) ) ",
                queryWithAllStates(FeedItemFilter.NOT_FAVORITE));
    }

    @Test
    public void historyFilterRequiresCompletionDate() {
        assertSql(" (FeedMedia.playback_completion_date > 0 ) ",
                queryWithAllStates(FeedItemFilter.IS_IN_HISTORY));
    }

    @Test
    public void multipleConditionsAreCombinedWithAnd() {
        String query = queryWithAllStates(FeedItemFilter.NEW, FeedItemFilter.QUEUED, FeedItemFilter.HAS_MEDIA);
        assertSql(" (FeedItems.read = -1  AND FeedItems.id IN (SELECT feeditem FROM Queue)  AND "
                + "FeedMedia.id NOT NULL ) ", query);
    }

    @Test
    public void stateRestrictionIsAppendedAfterOtherConditions() {
        String query = query(FeedItemFilter.PLAYED, FeedItemFilter.INCLUDE_ARCHIVED);
        assertTrue(normalised(query).startsWith(normalised(" (FeedItems.read = 1  AND FeedItems.feed IN "
                + "(SELECT id FROM Feeds WHERE state IN (" + Feed.STATE_ARCHIVED + "))")));
    }
}

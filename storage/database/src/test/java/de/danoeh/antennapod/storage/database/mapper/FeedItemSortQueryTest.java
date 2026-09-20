package de.danoeh.antennapod.storage.database.mapper;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class FeedItemSortQueryTest {

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        UserPreferences.init(context);
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.DATE_NEW_OLD);
    }

    @Test
    public void titleOrdersSortByEpisodeTitle() {
        assertEquals("FeedItems.title ASC", FeedItemSortQuery.generateFrom(SortOrder.EPISODE_TITLE_A_Z));
        assertEquals("FeedItems.title DESC", FeedItemSortQuery.generateFrom(SortOrder.EPISODE_TITLE_Z_A));
    }

    @Test
    public void durationOrdersSortByMediaDuration() {
        assertEquals("FeedMedia.duration ASC", FeedItemSortQuery.generateFrom(SortOrder.DURATION_SHORT_LONG));
        assertEquals("FeedMedia.duration DESC", FeedItemSortQuery.generateFrom(SortOrder.DURATION_LONG_SHORT));
    }

    @Test
    public void sizeOrdersSortByMediaSize() {
        assertEquals("FeedMedia.filesize ASC", FeedItemSortQuery.generateFrom(SortOrder.SIZE_SMALL_LARGE));
        assertEquals("FeedMedia.filesize DESC", FeedItemSortQuery.generateFrom(SortOrder.SIZE_LARGE_SMALL));
    }

    @Test
    public void completionDateSortsNewestFirstByHistoryTime() {
        assertEquals("FeedMedia.playback_completion_date DESC",
                FeedItemSortQuery.generateFrom(SortOrder.COMPLETION_DATE_NEW_OLD));
    }

    @Test
    public void dateOrdersSortByPublicationDate() {
        assertEquals("FeedItems.pubDate ASC", FeedItemSortQuery.generateFrom(SortOrder.DATE_OLD_NEW));
        assertEquals("FeedItems.pubDate DESC", FeedItemSortQuery.generateFrom(SortOrder.DATE_NEW_OLD));
    }

    @Test
    public void filenameOrdersSortByLink() {
        assertEquals("link ASC", FeedItemSortQuery.generateFrom(SortOrder.EPISODE_FILENAME_A_Z));
        assertEquals("link DESC", FeedItemSortQuery.generateFrom(SortOrder.EPISODE_FILENAME_Z_A));
    }

    @Test
    public void orderWithoutDatabaseEquivalentFallsBackToNewestFirst() {
        assertEquals("FeedItems.pubDate DESC", FeedItemSortQuery.generateFrom(SortOrder.RANDOM));
        assertEquals("FeedItems.pubDate DESC", FeedItemSortQuery.generateFrom(SortOrder.FEED_TITLE_A_Z));
    }

    @Test
    public void missingSortOrderUsesGlobalDefault() {
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.EPISODE_TITLE_A_Z);
        assertEquals("FeedItems.title ASC", FeedItemSortQuery.generateFrom(null));
    }

    @Test
    public void globalDefaultSortOrderIsResolvedFromPreferences() {
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.SIZE_LARGE_SMALL);
        assertEquals("FeedMedia.filesize DESC", FeedItemSortQuery.generateFrom(SortOrder.GLOBAL_DEFAULT));
    }
}

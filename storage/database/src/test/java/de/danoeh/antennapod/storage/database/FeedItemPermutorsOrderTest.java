package de.danoeh.antennapod.storage.database;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FeedItemPermutorsOrderTest {

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        UserPreferences.init(context);
    }

    @Test
    public void titleOrderIgnoresCase() {
        List<FeedItem> items = list(titled(1, "banana"), titled(2, "Apple"), titled(3, "cherry"));
        FeedItemPermutors.getPermutor(SortOrder.EPISODE_TITLE_A_Z).reorder(items);
        assertEquals(Arrays.asList(2L, 1L, 3L), ids(items));
    }

    @Test
    public void filenameOrderIgnoresCaseAndPlacesMissingLinksFirst() {
        List<FeedItem> items = list(linked(1, "http://x/B.mp3"), linked(2, null), linked(3, "http://x/a.mp3"));
        FeedItemPermutors.getPermutor(SortOrder.EPISODE_FILENAME_A_Z).reorder(items);
        assertEquals(Arrays.asList(2L, 3L, 1L), ids(items));
    }

    @Test
    public void reverseFilenameOrderPlacesMissingLinksLast() {
        List<FeedItem> items = list(linked(1, "http://x/B.mp3"), linked(2, null), linked(3, "http://x/a.mp3"));
        FeedItemPermutors.getPermutor(SortOrder.EPISODE_FILENAME_Z_A).reorder(items);
        assertEquals(Arrays.asList(1L, 3L, 2L), ids(items));
    }

    @Test
    public void completionDateOrderPutsMostRecentlyFinishedFirst() {
        List<FeedItem> items = list(completed(1, 1000), completed(2, 3000), completed(3, 2000));
        FeedItemPermutors.getPermutor(SortOrder.COMPLETION_DATE_NEW_OLD).reorder(items);
        assertEquals(Arrays.asList(2L, 3L, 1L), ids(items));
    }

    @Test
    public void dateOrderTreatsMissingDateAsOldest() {
        List<FeedItem> items = list(dated(1, 2000L), dated(2, null), dated(3, 1000L));
        FeedItemPermutors.getPermutor(SortOrder.DATE_OLD_NEW).reorder(items);
        assertEquals(Arrays.asList(2L, 3L, 1L), ids(items));
    }

    @Test
    public void globalDefaultUsesTheSortOrderFromPreferences() {
        UserPreferences.setPrefGlobalSortedOrder(SortOrder.EPISODE_TITLE_Z_A);
        List<FeedItem> items = list(titled(1, "a"), titled(2, "c"), titled(3, "b"));
        FeedItemPermutors.getPermutor(SortOrder.GLOBAL_DEFAULT).reorder(items);
        assertEquals(Arrays.asList(2L, 3L, 1L), ids(items));
    }

    @Test
    public void randomOrderKeepsEveryItemExactlyOnce() {
        List<FeedItem> items = new ArrayList<>();
        for (long id = 1; id <= 30; id++) {
            items.add(titled(id, "title" + id));
        }
        List<Long> before = ids(items);

        FeedItemPermutors.getPermutor(SortOrder.RANDOM).reorder(items);

        assertEquals(before.size(), items.size());
        assertEquals(new HashSet<>(before), new HashSet<>(ids(items)));
    }

    @Test
    public void smartShuffleOldToNewSpreadsFeedsAndKeepsPubdateOrderWithinFeed() {
        List<FeedItem> items = list(
                fromFeed(1, 10, 1000), fromFeed(2, 10, 2000), fromFeed(3, 10, 3000),
                fromFeed(4, 20, 1500), fromFeed(5, 20, 2500));
        Collections.shuffle(items);

        FeedItemPermutors.getPermutor(SortOrder.SMART_SHUFFLE_OLD_NEW).reorder(items);

        assertEquals(Arrays.asList(4L, 1L, 2L, 3L, 5L), ids(items));
    }

    @Test
    public void smartShuffleNewToOldReversesPubdateOrderWithinFeed() {
        List<FeedItem> items = list(
                fromFeed(1, 10, 1000), fromFeed(2, 10, 2000), fromFeed(3, 10, 3000),
                fromFeed(4, 20, 1500), fromFeed(5, 20, 2500));
        Collections.shuffle(items);

        FeedItemPermutors.getPermutor(SortOrder.SMART_SHUFFLE_NEW_OLD).reorder(items);

        assertEquals(Arrays.asList(5L, 3L, 2L, 1L, 4L), ids(items));
    }

    @Test
    public void smartShuffleSpreadsLargeFeedsAndFillsRemainingSlotsWithSingleEpisodeFeeds() {
        String feedPerEpisode = "ABCDDEEEEEEEEEE";
        List<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < feedPerEpisode.length(); i++) {
            items.add(fromFeed(i + 1, feedPerEpisode.charAt(i) - 'A' + 1, (i + 1) * 1000L));
        }

        FeedItemPermutors.getPermutor(SortOrder.SMART_SHUFFLE_OLD_NEW).reorder(items);

        StringBuilder feedSequence = new StringBuilder();
        for (FeedItem item : items) {
            feedSequence.append(item.getFeedId() >= 4 ? (char) ('A' + item.getFeedId() - 1) : '-');
        }
        assertEquals("-EEDEEE-EEEDEE-", feedSequence.toString());
    }

    @Test
    public void smartShuffleOfEmptyQueueLeavesItEmpty() {
        List<FeedItem> items = new ArrayList<>();
        FeedItemPermutors.getPermutor(SortOrder.SMART_SHUFFLE_OLD_NEW).reorder(items);
        assertTrue(items.isEmpty());
    }

    private static List<FeedItem> list(FeedItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<Long> ids(List<FeedItem> items) {
        return items.stream().map(FeedItem::getId).collect(Collectors.toList());
    }

    private static FeedItem titled(long id, String title) {
        return new FeedItem(id, title, null, null, new Date(0), FeedItem.UNPLAYED, null);
    }

    private static FeedItem linked(long id, String link) {
        return new FeedItem(id, "title", null, link, new Date(0), FeedItem.UNPLAYED, null);
    }

    private static FeedItem dated(long id, Long millis) {
        Date date = millis == null ? null : new Date(millis);
        return new FeedItem(id, "title", null, null, date, FeedItem.UNPLAYED, null);
    }

    private static FeedItem completed(long id, long completionMillis) {
        FeedItem item = titled(id, "title");
        FeedMedia media = new FeedMedia(item, "http://download/" + id, 0, "audio/mpeg");
        media.setLastPlayedTimeHistory(new Date(completionMillis));
        item.setMedia(media);
        return item;
    }

    private static FeedItem fromFeed(long id, long feedId, long pubDateMillis) {
        FeedItem item = new FeedItem(id, "title" + id, null, null, new Date(pubDateMillis), FeedItem.UNPLAYED,
                new Feed(null, null, "feed" + feedId));
        item.setFeedId(feedId);
        return item;
    }
}

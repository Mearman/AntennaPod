package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(JUnit4.class)
public class FeedItemDuplicateGuesserPoolTest {

    @Test
    public void testDuplicateIsConsistent() {
        Feed feed = new Feed("url", null, null);
        FeedItem item1 = createItem("id1", "Title", feed);
        FeedItem item2 = createItem("id2", "Title", feed);

        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(new ArrayList<>());
        pool.add(item1);
        assertSame(item1, pool.guessDuplicate(item1));
        assertSame(item1, pool.guessDuplicate(item2));
        pool.add(item2);
        assertSame(item1, pool.guessDuplicate(item1));
        assertSame(item1, pool.guessDuplicate(item2));
    }

    @Test
    public void itemsGivenToConstructorAreIndexed() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem("id1", "Title", feed);
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));

        assertSame(existing, pool.guessDuplicate(createItem("id2", "Title", feed)));
    }

    @Test
    public void sameStreamUrlIsDuplicateEvenWithDifferentTitleAndIdentifier() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem("id1", "Original", feed);
        FeedItem renamed = createItem("id2", "Renamed", feed, "url-Original");
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));

        assertSame(existing, pool.guessDuplicate(renamed));
    }

    @Test
    public void sameTitleWithSameDateAndDurationIsDuplicateEvenWithDifferentUrl() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem("id1", "Title", feed, "url-1");
        existing.setPubDate(new Date(0));
        FeedItem republished = createItem("id2", "Title", feed, "url-2");
        republished.setPubDate(new Date(0));
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));

        assertSame(existing, pool.guessDuplicate(republished));
    }

    @Test
    public void unknownTitleAndUrlHasNoDuplicate() {
        Feed feed = new Feed("url", null, null);
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(
                List.of(createItem("id1", "Title", feed)));

        assertNull(pool.guessDuplicate(createItem("id2", "Other title", feed)));
    }

    @Test
    public void sameTitleButDifferentDateIsNoDuplicate() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem("id1", "Title", feed, "url-1");
        existing.setPubDate(new Date(0));
        FeedItem other = createItem("id2", "Title", feed, "url-2");
        other.setPubDate(new Date(10L * 24 * 60 * 60 * 1000));
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));

        assertNull(pool.guessDuplicate(other));
    }

    @Test
    public void searchItemWithoutMediaIsMatchedByIdentifierOnly() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem("id1", "Title", feed);
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));
        FeedItem withoutMedia = new FeedItem();
        withoutMedia.setTitle("Title");
        withoutMedia.setItemIdentifier("id1");
        FeedItem withoutMediaOrIdentifier = new FeedItem();
        withoutMediaOrIdentifier.setTitle("Title");

        assertSame(existing, pool.guessDuplicate(withoutMedia));
        assertNull(pool.guessDuplicate(withoutMediaOrIdentifier));
    }

    @Test
    public void itemsWithEmptyStreamUrlAreNotMatchedByUrl() {
        Feed feed = new Feed("url", null, null);
        FeedItem first = createItem("id1", "First", feed, "");
        FeedItem second = createItem("id2", "Second", feed, "");
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(first));

        assertNull(pool.guessDuplicate(second));
    }

    @Test
    public void findByIdReturnsItemWithSameIdentifyingValue() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem("id1", "Title", feed);
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));

        assertSame(existing, pool.findById(createItem("id1", "Different title", feed)));
        assertNull(pool.findById(createItem("id2", "Title", feed)));
    }

    @Test
    public void findByIdFallsBackToTitleWhenIdentifierIsMissing() {
        Feed feed = new Feed("url", null, null);
        FeedItem existing = createItem(null, "Title", feed);
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(existing));

        assertSame(existing, pool.findById(createItem(null, "Title", feed)));
    }

    @Test
    public void firstItemWithAnIdentifierIsKept() {
        Feed feed = new Feed("url", null, null);
        FeedItem first = createItem("id1", "First", feed);
        FeedItem second = createItem("id1", "Second", feed);
        FeedItemDuplicateGuesserPool pool = new FeedItemDuplicateGuesserPool(List.of(first, second));

        assertSame(first, pool.findById(second));
    }

    private FeedItem createItem(String identifier, String title, Feed feed) {
        return createItem(identifier, title, feed, "url-" + title);
    }

    private FeedItem createItem(String identifier, String title, Feed feed, String streamUrl) {
        FeedItem item = new FeedItem();
        item.setItemIdentifier(identifier);
        item.setTitle(title);
        item.setMedia(new FeedMedia(item, streamUrl, 2, "mime"));
        item.setFeed(feed);
        return item;
    }
}

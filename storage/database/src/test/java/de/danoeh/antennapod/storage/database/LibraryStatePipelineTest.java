package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedFunding;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class LibraryStatePipelineTest extends FeedPipelineTestBase {
    private static final String DOCUMENT = rss("""
            <title>Library</title>
            <link>https://example.com/show</link>
            <podcast:funding url="https://example.com/donate">Donate</podcast:funding>
            <item>
              <guid>oldest</guid><title>Oldest</title>
              <pubDate>Sun, 01 Jan 2006 15:04:05 +0000</pubDate>
              <enclosure url="https://example.com/oldest.mp3" length="5000000" type="audio/mpeg"/>
            </item>
            <item>
              <guid>linked</guid><title>Linked</title>
              <link>https://example.com/linked</link>
              <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>
              <enclosure url="https://example.com/linked.mp3" length="5000000" type="audio/mpeg"/>
            </item>
            <item>
              <guid>newest</guid><title>Newest</title>
              <pubDate>Tue, 03 Jan 2006 15:04:05 +0000</pubDate>
              <enclosure url="https://example.com/newest.mp3" length="5000000" type="audio/mpeg"/>
              <podcast:transcript url="https://example.com/newest.srt" type="application/srt"/>
              <podcast:transcript url="https://example.com/newest.vtt" type="text/vtt"/>
              <podcast:transcript url="https://example.com/newest-again.srt" type="application/x-subrip"/>
            </item>
            """);

    private Feed loadWithItems(Feed feed) {
        Feed reloaded = reload(feed);
        reloaded.setItems(storedItems(reloaded));
        return reloaded;
    }

    @Test
    public void mostRecentItemIsTheOneWithTheLatestPublicationDate() throws Exception {
        Feed feed = loadWithItems(parseAndStore(DOCUMENT));

        assertEquals("newest", feed.getMostRecentItem().getItemIdentifier());
        assertEquals("newest", feed.getItemAtIndex(0).getItemIdentifier());
        assertEquals("oldest", feed.getItemAtIndex(2).getItemIdentifier());
    }

    @Test
    public void mostRecentItemIsNullForAFeedWithoutEpisodes() throws Exception {
        Feed feed = loadWithItems(parseAndStore(rss("<title>Empty</title>")));

        assertNull(feed.getMostRecentItem());
    }

    @Test
    public void feedIsIdentifiedByItsIdentifierThenDownloadUrlThenTitleThenLink() {
        Feed feed = new Feed(null, null);
        feed.setLink("https://example.com/link");
        assertEquals("https://example.com/link", feed.getIdentifyingValue());

        feed.setTitle("Title");
        assertEquals("Title", feed.getIdentifyingValue());

        feed.setDownloadUrl("https://example.com/feed.xml");
        assertEquals("https://example.com/feed.xml", feed.getIdentifyingValue());

        feed.setFeedIdentifier("urn:feed");
        assertEquals("urn:feed", feed.getIdentifyingValue());
    }

    @Test
    public void humanReadableFeedNameFollowsCustomTitleThenTitleThenAddress() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        assertEquals("Library", stored.getHumanReadableIdentifier());

        stored.setCustomTitle("Mine");
        DBWriter.setFeedCustomTitle(stored);
        DBWriter.tearDownTests();
        Feed renamed = reload(stored);
        assertEquals("Mine", renamed.getHumanReadableIdentifier());
        assertEquals("Mine", renamed.getTitle());

        renamed.setCustomTitle("Library");
        assertNull(renamed.getCustomTitle());
        renamed.setTitle(null);
        assertEquals(FEED_URL, renamed.getHumanReadableIdentifier());
    }

    @Test
    public void itemFilterAndSortOrderAreStoredPerFeedAndCanBeRemoved() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        assertNull(stored.getSortOrder());
        assertFalse(stored.getItemFilter().showUnplayed);

        DBWriter.setFeedItemsFilter(stored.getId(), Set.of(FeedItemFilter.UNPLAYED, FeedItemFilter.HAS_MEDIA));
        DBWriter.setFeedItemSortOrder(stored.getId(), SortOrder.EPISODE_TITLE_A_Z);
        DBWriter.tearDownTests();
        Feed configured = reload(stored);

        assertTrue(configured.getItemFilter().showUnplayed);
        assertTrue(configured.getItemFilter().showHasMedia);
        assertEquals(SortOrder.EPISODE_TITLE_A_Z, configured.getSortOrder());

        DBWriter.setFeedItemsFilter(stored.getId(), Set.of());
        DBWriter.setFeedItemSortOrder(stored.getId(), null);
        DBWriter.tearDownTests();
        Feed reset = reload(stored);

        assertFalse(reset.getItemFilter().showUnplayed);
        assertNull(reset.getSortOrder());
    }

    @Test
    public void filteredFeedLoadingOnlyReturnsEpisodesMatchingTheStoredFilter() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(storedItem(stored, "oldest")));
        DBWriter.setFeedItemsFilter(stored.getId(), Set.of(FeedItemFilter.UNPLAYED));
        DBWriter.tearDownTests();

        Feed unfiltered = DBReader.getFeed(stored.getId(), false, 0, Integer.MAX_VALUE);
        Feed filtered = DBReader.getFeed(stored.getId(), true, 0, Integer.MAX_VALUE);

        assertEquals(3, unfiltered.getItems().size());
        assertEquals(2, filtered.getItems().size());
    }

    @Test
    public void onlyEpisodeSortOrdersOfASingleFeedAreAccepted() {
        Feed feed = new Feed(FEED_URL, null);

        assertThrows(IllegalArgumentException.class, () -> feed.setSortOrder(SortOrder.RANDOM));
        assertThrows(IllegalArgumentException.class, () -> feed.setSortOrder(SortOrder.FEED_TITLE_A_Z));
        feed.setSortOrder(SortOrder.DURATION_LONG_SHORT);
        assertEquals(SortOrder.DURATION_LONG_SHORT, feed.getSortOrder());
    }

    @Test
    public void feedHasEpisodesInTheAppOnceOneIsFavoritedQueuedOrDownloaded() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        assertFalse(loadWithItems(stored).hasEpisodeInApp());
        assertFalse(loadWithItems(stored).hasInteractedWithEpisode());

        DBWriter.addFavoriteItems(List.of(storedItem(stored, "linked")));
        DBWriter.tearDownTests();
        assertTrue(loadWithItems(stored).hasEpisodeInApp());

        DBWriter.removeFavoriteItems(List.of(storedItem(stored, "linked")));
        DBWriter.addQueueItem(context, storedItem(stored, "oldest"));
        DBWriter.tearDownTests();
        assertTrue(loadWithItems(stored).hasEpisodeInApp());
    }

    @Test
    public void playedOrStartedEpisodesCountAsInteractionButNotAsEpisodesInTheApp() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, List.of(storedItem(stored, "oldest")));
        DBWriter.tearDownTests();
        Feed played = loadWithItems(stored);
        assertFalse(played.hasEpisodeInApp());
        assertTrue(played.hasInteractedWithEpisode());

        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, List.of(storedItem(stored, "oldest")));
        FeedItem started = storedItem(stored, "linked");
        started.getMedia().setPosition(5000);
        started.getMedia().setLastPlayedTimeHistory(new Date(0));
        DBWriter.setFeedMediaPlaybackInformation(started.getMedia());
        DBWriter.tearDownTests();
        assertTrue(loadWithItems(stored).hasInteractedWithEpisode());
    }

    @Test
    public void feedWithoutLoadedItemsReportsNoEpisodesInTheApp() {
        Feed feed = new Feed(FEED_URL, null);
        feed.setItems(null);

        assertFalse(feed.hasEpisodeInApp());
        assertFalse(feed.hasInteractedWithEpisode());
    }

    @Test
    public void archivingAFeedChangesItsStateButKeepsItsEpisodes() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        assertEquals(Feed.STATE_SUBSCRIBED, stored.getState());

        DBWriter.setFeedState(context, stored, Feed.STATE_ARCHIVED);
        DBWriter.tearDownTests();
        Feed archived = reload(stored);

        assertEquals(Feed.STATE_ARCHIVED, archived.getState());
        assertEquals(3, DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED), SortOrder.DATE_NEW_OLD).size());
    }

    @Test
    public void episodeLinkFallsBackToTheFeedLinkAndFinallyToNothing() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);

        assertEquals("https://example.com/linked", storedItem(stored, "linked").getLinkWithFallback());
        assertEquals("https://example.com/show", storedItem(stored, "oldest").getLinkWithFallback());

        FeedItem orphan = new FeedItem();
        orphan.setFeed(new Feed(FEED_URL, null));
        assertNull(orphan.getLinkWithFallback());
    }

    @Test
    public void episodeIsIdentifiedByGuidThenTitleThenMediaAddressThenLink() {
        FeedItem item = new FeedItem();
        item.setLink("https://example.com/link");
        assertEquals("https://example.com/link", item.getIdentifyingValue());

        item.setMedia(new FeedMedia(item, "https://example.com/media.mp3", 0, null));
        assertEquals("https://example.com/media.mp3", item.getIdentifyingValue());

        item.setTitle("Title");
        assertEquals("Title", item.getIdentifyingValue());

        item.setItemIdentifier("guid");
        assertEquals("guid", item.getIdentifyingValue());
    }

    @Test
    public void favoriteAndQueueMembershipAreExposedAsTagsOnLoadedEpisodes() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        DBWriter.addFavoriteItems(List.of(storedItem(stored, "linked")));
        DBWriter.addQueueItem(context, storedItem(stored, "newest"));
        DBWriter.tearDownTests();

        FeedItem favorite = storedItem(reload(stored), "linked");
        FeedItem queued = storedItem(reload(stored), "newest");

        assertTrue(favorite.isTagged(FeedItem.TAG_FAVORITE));
        assertFalse(favorite.isTagged(FeedItem.TAG_QUEUE));
        assertTrue(queued.isTagged(FeedItem.TAG_QUEUE));
        assertFalse(queued.isTagged(FeedItem.TAG_FAVORITE));
        queued.removeTag(FeedItem.TAG_QUEUE);
        assertFalse(queued.isTagged(FeedItem.TAG_QUEUE));
    }

    @Test
    public void episodeAutoDownloadCanBeDisabledAndIsRemembered() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedItem item = storedItem(stored, "linked");
        assertTrue(item.isAutoDownloadEnabled());

        item.disableAutoDownload();
        DBWriter.setFeedItem(item, false);
        DBWriter.tearDownTests();

        assertFalse(storedItem(reload(stored), "linked").isAutoDownloadEnabled());
    }

    @Test
    public void preferredTranscriptFormatWinsRegardlessOfTheOrderInTheFeed() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);

        FeedItem newest = storedItem(stored, "newest");

        assertTrue(newest.hasTranscript());
        assertEquals("https://example.com/newest.vtt", newest.getTranscriptUrl());
        assertEquals("text/vtt", newest.getTranscriptType());
        assertFalse(storedItem(stored, "oldest").hasTranscript());
    }

    @Test
    public void transcriptWithoutTypeOrAddressIsIgnored() {
        FeedItem item = new FeedItem();

        item.setTranscriptUrl(null, "https://example.com/transcript");
        item.setTranscriptUrl("text/vtt", "");
        item.setTranscriptUrl("text/plain", "https://example.com/transcript.txt");

        assertFalse(item.hasTranscript());
        assertNull(item.getTranscriptUrl());
    }

    @Test
    public void chapterPositionIsFoundFromStoredChapters() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Chapters</title>
                <item>
                  <guid>chaptered</guid><title>Chaptered</title>
                  <psc:chapters>
                    <psc:chapter start="00:00:00" title="One"/>
                    <psc:chapter start="00:01:00" title="Two"/>
                    <psc:chapter start="00:02:00" title="Three"/>
                  </psc:chapters>
                </item>
                """));
        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(storedItem(stored, "chaptered"));

        assertEquals(-1, Chapter.getAfterPosition(null, 0));
        assertEquals(-1, Chapter.getAfterPosition(new ArrayList<>(), 0));
        assertEquals(0, Chapter.getAfterPosition(chapters, 0));
        assertEquals(0, Chapter.getAfterPosition(chapters, 59999));
        assertEquals(1, Chapter.getAfterPosition(chapters, 60001));
        assertEquals(2, Chapter.getAfterPosition(chapters, 600000));
    }

    @Test
    public void storedChaptersAreIdentifiedByTheirDatabaseIdAndCanBeEdited() throws Exception {
        Feed stored = parseAndStore(rss("""
                <title>Chapters</title>
                <item>
                  <guid>chaptered</guid><title>Chaptered</title>
                  <psc:chapters><psc:chapter start="00:00:10" title="One"/></psc:chapters>
                </item>
                """));
        FeedItem item = storedItem(stored, "chaptered");
        Chapter first = DBReader.loadChaptersOfFeedItem(item).get(0);
        Chapter again = DBReader.loadChaptersOfFeedItem(item).get(0);

        assertTrue(first.getId() > 0);
        assertEquals(first, again);
        assertEquals(first.hashCode(), again.hashCode());
        assertNotEquals(first, new Chapter(10000, "One", null, null));
        assertNotEquals(first, null);
        first.setTitle("Renamed");
        first.setStart(20000);
        first.setLink("https://example.com/chapter");
        first.setImageUrl("https://example.com/chapter.png");
        first.setChapterId("external-id");
        assertEquals("Renamed", first.getTitle());
        assertEquals(20000, first.getStart());
        assertEquals("https://example.com/chapter", first.getLink());
        assertEquals("https://example.com/chapter.png", first.getImageUrl());
        assertEquals("external-id", first.getChapterId());
        assertTrue(first.toString().contains("Renamed"));
    }

    @Test
    public void fundingLinksReadBackFromTheDatabaseEqualTheParsedOnes() throws Exception {
        Feed parsed = parse(DOCUMENT).feed;
        Feed stored = storeParsed(parsed);

        FeedFunding fromFeed = parsed.getPaymentLinks().get(0);
        FeedFunding fromDatabase = stored.getPaymentLinks().get(0);

        assertEquals(fromFeed, fromDatabase);
        assertEquals(fromFeed.hashCode(), fromDatabase.hashCode());
        assertNotEquals(fromFeed, new FeedFunding("https://example.com/donate", "Other label"));
        assertNotEquals(fromFeed, new FeedFunding("https://example.com/other", "Donate"));
        assertNotEquals(fromFeed, null);
        assertEquals(new FeedFunding(null, null), new FeedFunding(null, null));
    }

    @Test
    public void feedsAndEpisodesAreEqualWhenTheyShareTheirDatabaseId() throws Exception {
        Feed stored = parseAndStore(DOCUMENT);
        FeedItem item = storedItem(stored, "linked");

        assertEquals(stored, reload(stored));
        assertEquals(stored.hashCode(), reload(stored).hashCode());
        assertEquals(item, storedItem(reload(stored), "linked"));
        assertEquals(item.hashCode(), storedItem(reload(stored), "linked").hashCode());
        assertNotEquals(item, storedItem(stored, "newest"));
        assertNotEquals(stored, new Feed(FEED_URL, null));
        assertTrue(item.toString().contains("Linked"));
    }
}

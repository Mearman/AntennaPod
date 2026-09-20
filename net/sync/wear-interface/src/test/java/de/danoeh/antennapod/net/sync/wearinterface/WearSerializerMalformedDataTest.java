package de.danoeh.antennapod.net.sync.wearinterface;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class WearSerializerMalformedDataTest {
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void episodesFromNonJsonBytesAreEmpty() {
        assertTrue(WearSerializer.episodesFromBytes(bytes("not json")).isEmpty());
    }

    @Test
    public void episodesBeforeTheFirstMalformedEntryAreKept() {
        byte[] data = bytes("[{\"episode_id\": 5, \"title\": \"Kept\"}, 17]");

        List<FeedItem> items = WearSerializer.episodesFromBytes(data);

        assertEquals(1, items.size());
        assertEquals(5, items.get(0).getId());
        assertEquals("Kept", items.get(0).getTitle());
    }

    @Test
    public void episodeWithoutFieldsGetsNeutralDefaults() {
        List<FeedItem> items = WearSerializer.episodesFromBytes(bytes("[{}]"));

        assertEquals(1, items.size());
        FeedItem item = items.get(0);
        assertEquals(-1, item.getId());
        assertEquals("", item.getTitle());
        assertNull(item.getPubDate());
        assertEquals(0, item.getMedia().getDuration());
        assertEquals(0, item.getMedia().getPosition());
    }

    @Test
    public void episodeWithoutMediaAndPublicationDateIsSerialisedWithZeroValues() {
        FeedItem item = new FeedItem();
        item.setId(3);
        item.setTitle("Bare");

        List<FeedItem> items = WearSerializer.episodesFromBytes(
                WearSerializer.episodesToBytes(Collections.singletonList(item)));

        assertEquals(1, items.size());
        assertNull(items.get(0).getPubDate());
        assertEquals(0, items.get(0).getMedia().getDuration());
        assertEquals(0, items.get(0).getMedia().getPosition());
    }

    @Test
    public void feedsFromNonJsonBytesAreEmpty() {
        assertTrue(WearSerializer.feedsFromBytes(bytes("{")).isEmpty());
    }

    @Test
    public void feedsBeforeTheFirstMalformedEntryAreKept() {
        List<Feed> feeds = WearSerializer.feedsFromBytes(bytes("[{\"feed_id\": 8, \"title\": \"Kept\"}, \"oops\"]"));

        assertEquals(1, feeds.size());
        assertEquals(8, feeds.get(0).getId());
        assertEquals("Kept", feeds.get(0).getTitle());
    }

    @Test
    public void feedWithoutFieldsGetsNeutralDefaults() {
        List<Feed> feeds = WearSerializer.feedsFromBytes(bytes("[{}]"));

        assertEquals(1, feeds.size());
        assertEquals(-1, feeds.get(0).getId());
        assertEquals("", feeds.get(0).getTitle());
    }

    @Test
    public void feedWithoutTitleIsSerialisedWithEmptyTitle() {
        Feed feed = new Feed(null, null);
        feed.setId(4);

        List<Feed> feeds = WearSerializer.feedsFromBytes(WearSerializer.feedsToBytes(Collections.singletonList(feed)));

        assertEquals("", feeds.get(0).getTitle());
    }

    @Test
    public void nowPlayingFromNonJsonBytesIsAbsent() {
        assertNull(WearSerializer.nowPlayingFromBytes(bytes("garbage")));
    }

    @Test
    public void nowPlayingWithoutPlayingFlagIsNotPlaying() {
        WearNowPlaying nowPlaying = WearSerializer.nowPlayingFromBytes(bytes("{\"episode_id\": 2, \"title\": \"T\"}"));

        assertNotNull(nowPlaying);
        assertEquals(2, nowPlaying.item.getId());
        assertFalse(nowPlaying.isPlaying);
    }

    @Test
    public void pausedNowPlayingStaysPausedAfterRoundTrip() {
        FeedItem item = new FeedItem();
        item.setId(6);

        WearNowPlaying nowPlaying = WearSerializer.nowPlayingFromBytes(WearSerializer.nowPlayingToBytes(item, false));

        assertNotNull(nowPlaying);
        assertFalse(nowPlaying.isPlaying);
    }
}

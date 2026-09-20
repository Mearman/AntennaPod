package de.danoeh.antennapod.storage.database;

import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DownloadLogPipelineTest extends FeedPipelineTestBase {

    private Feed storeFeed() throws Exception {
        return parseAndStore(rss("""
                <title>Logged</title>
                <item>
                  <guid>episode</guid><title>Episode</title>
                  <pubDate>Mon, 02 Jan 2006 15:04:05 +0000</pubDate>
                  <enclosure url="https://example.com/episode.mp3" length="5000000" type="audio/mpeg"/>
                </item>
                """));
    }

    private void log(DownloadResult result) {
        DBWriter.addDownloadStatus(result);
        DBWriter.tearDownTests();
    }

    @Test
    public void downloadResultsAreStoredWithReasonDetailsAndCompletionDate() throws Exception {
        Feed feed = storeFeed();
        Date completed = new Date(1234567890000L);
        log(new DownloadResult(0, "Logged", feed.getId(), Feed.FEEDFILETYPE_FEED, false,
                DownloadError.ERROR_NOT_FOUND, completed, "The server said 404"));

        List<DownloadResult> results = DBReader.getDownloadLog();

        assertEquals(1, results.size());
        DownloadResult result = results.get(0);
        assertTrue(result.getId() > 0);
        assertEquals("Logged", result.getTitle());
        assertEquals(feed.getId(), result.getFeedfileId());
        assertEquals(Feed.FEEDFILETYPE_FEED, result.getFeedfileType());
        assertFalse(result.isSuccessful());
        assertEquals(DownloadError.ERROR_NOT_FOUND, result.getReason());
        assertEquals("The server said 404", result.getReasonDetailed());
        assertEquals(completed, result.getCompletionDate());
    }

    @Test
    public void downloadResultCanBeMarkedAsSucceededFailedOrCancelledBeforeItIsStored() throws Exception {
        Feed feed = storeFeed();
        DownloadResult succeeded = new DownloadResult("Success", feed.getId(), Feed.FEEDFILETYPE_FEED, false,
                DownloadError.ERROR_IO_ERROR, "leftover");
        succeeded.setSuccessful();
        DownloadResult failed = new DownloadResult("Failure", feed.getId(), Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, null);
        failed.setFailed(DownloadError.ERROR_UNAUTHORIZED, "Wrong password");
        DownloadResult cancelled = new DownloadResult("Cancelled", feed.getId(), Feed.FEEDFILETYPE_FEED, true,
                DownloadError.SUCCESS, null);
        cancelled.setCancelled();

        log(succeeded);
        log(failed);
        log(cancelled);
        List<DownloadResult> results = DBReader.getDownloadLog();

        assertEquals(3, results.size());
        for (DownloadResult result : results) {
            switch (result.getTitle()) {
                case "Success":
                    assertTrue(result.isSuccessful());
                    assertEquals(DownloadError.SUCCESS, result.getReason());
                    break;
                case "Failure":
                    assertFalse(result.isSuccessful());
                    assertEquals(DownloadError.ERROR_UNAUTHORIZED, result.getReason());
                    assertEquals("Wrong password", result.getReasonDetailed());
                    break;
                default:
                    assertFalse(result.isSuccessful());
                    assertEquals(DownloadError.ERROR_DOWNLOAD_CANCELLED, result.getReason());
                    break;
            }
        }
    }

    @Test
    public void feedDownloadLogOnlyContainsResultsOfThatFeedNewestFirstUpToTheLimit() throws Exception {
        Feed first = storeFeed();
        Feed second = storeParsed(parse(rss("<title>Other</title>"), "https://example.com/other.xml").feed);
        for (int i = 0; i < 3; i++) {
            log(new DownloadResult(0, "First " + i, first.getId(), Feed.FEEDFILETYPE_FEED, true,
                    DownloadError.SUCCESS, new Date(1000L * (i + 1)), null));
        }
        log(new DownloadResult(0, "Second", second.getId(), Feed.FEEDFILETYPE_FEED, true, DownloadError.SUCCESS,
                new Date(5000L), null));

        List<DownloadResult> firstLog = DBReader.getFeedDownloadLog(first.getId(), 2);

        assertEquals(2, firstLog.size());
        assertEquals("First 2", firstLog.get(0).getTitle());
        assertEquals("First 1", firstLog.get(1).getTitle());
        assertEquals(1, DBReader.getFeedDownloadLog(second.getId(), 10).size());
        assertEquals(4, DBReader.getDownloadLog().size());
    }

    @Test
    public void clearingTheDownloadLogRemovesAllResults() throws Exception {
        Feed feed = storeFeed();
        log(new DownloadResult("Logged", feed.getId(), Feed.FEEDFILETYPE_FEED, true, DownloadError.SUCCESS, null));

        DBWriter.clearDownloadLog();
        DBWriter.tearDownTests();

        assertTrue(DBReader.getDownloadLog().isEmpty());
    }

    @Test
    public void mediaDownloadResultsAreKeptSeparateFromFeedResults() throws Exception {
        Feed feed = storeFeed();
        FeedMedia media = storedItem(feed, "episode").getMedia();
        log(new DownloadResult("Episode", media.getId(), FeedMedia.FEEDFILETYPE_FEEDMEDIA, false,
                DownloadError.ERROR_IO_WRONG_SIZE, "Too small"));

        assertTrue(DBReader.getFeedDownloadLog(feed.getId(), 10).isEmpty());
        List<DownloadResult> all = DBReader.getDownloadLog();
        assertEquals(1, all.size());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, all.get(0).getFeedfileType());
        assertEquals(media.getId(), all.get(0).getFeedfileId());
    }

    @Test
    public void everyDownloadErrorCanBeRestoredFromItsCode() {
        for (DownloadError error : DownloadError.values()) {
            assertEquals(error, DownloadError.fromCode(error.getCode()));
        }
        assertThrows(IllegalArgumentException.class, () -> DownloadError.fromCode(9999));
    }

    @Test
    public void deletingAnEpisodeRemovesItsDownloadResults() throws Exception {
        Feed feed = storeFeed();
        FeedItem item = storedItem(feed, "episode");
        log(new DownloadResult("Episode", item.getMedia().getId(), FeedMedia.FEEDFILETYPE_FEEDMEDIA, false,
                DownloadError.ERROR_IO_ERROR, "Disk full"));
        assertEquals(1, DBReader.getDownloadLog().size());

        DBWriter.deleteFeedItems(context, List.of(item));
        DBWriter.tearDownTests();

        assertTrue(DBReader.getDownloadLog().isEmpty());
        assertTrue(storedItems(reload(feed)).isEmpty());
    }
}

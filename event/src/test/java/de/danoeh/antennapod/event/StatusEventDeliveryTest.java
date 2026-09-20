package de.danoeh.antennapod.event;

import android.content.Context;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class StatusEventDeliveryTest {
    private Context context;
    private final EventRecorder recorder = new EventRecorder();
    private List<FeedItem> items;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        EventTestDatabase.setUp(context);
        Feed feed = EventTestDatabase.storeFeed(context, "http://example.com/feed", "Feed", 2);
        items = EventTestDatabase.itemsOf(feed);
        recorder.register();
    }

    @After
    public void tearDown() {
        recorder.unregister();
        EventTestDatabase.tearDown();
    }

    private DownloadResult failedDownload(FeedItem item) {
        return new DownloadResult(item.getTitle(), item.getMedia().getId(), FeedMedia.FEEDFILETYPE_FEEDMEDIA,
                false, DownloadError.ERROR_IO_ERROR, "Disk is full");
    }

    @Test
    public void aPlainMessageEventCarriesNoActionForTheSubscriberToOffer() {
        MessageEvent event = new MessageEvent("Something happened");

        assertEquals("Something happened", event.message);
        assertNull(event.action);
        assertNull(event.actionText);
    }

    @Test
    public void aMessageEventActionIsRunnableBySubscribersWithTheApplicationContext() {
        AtomicReference<Context> invokedWith = new AtomicReference<>();

        MessageEvent event = new MessageEvent("Undo?", invokedWith::set, "Undo");

        assertEquals("Undo", event.actionText);
        assertNotNull(event.action);
        event.action.accept(context);
        assertSame(context, invokedWith.get());
    }

    @Test
    public void recordingAFailedDownloadPublishesADownloadLogUpdate()
            throws ExecutionException, InterruptedException {
        DBWriter.addDownloadStatus(failedDownload(items.get(0))).get();

        assertNotNull(recorder.single(DownloadLogEvent.class));
        List<DownloadResult> log = DBReader.getDownloadLog();
        assertEquals(1, log.size());
        assertEquals("Disk is full", log.get(0).getReasonDetailed());
    }

    @Test
    public void clearingTheDownloadLogPublishesADownloadLogUpdateAndEmptiesTheLog()
            throws ExecutionException, InterruptedException {
        DBWriter.addDownloadStatus(failedDownload(items.get(0))).get();
        DBWriter.addDownloadStatus(failedDownload(items.get(1))).get();
        recorder.clear();

        DBWriter.clearDownloadLog().get();

        assertNotNull(recorder.single(DownloadLogEvent.class));
        assertTrue(DBReader.getDownloadLog().isEmpty());
    }

    @Test
    public void deletingAnEpisodePublishesADownloadLogUpdateBecauseItsLogEntriesAreGoneToo()
            throws ExecutionException, InterruptedException {
        DBWriter.addDownloadStatus(failedDownload(items.get(0))).get();
        recorder.clear();

        DBWriter.deleteFeedItems(context, Collections.singletonList(items.get(0))).get();

        assertNotNull(recorder.single(DownloadLogEvent.class));
        assertNull(DBReader.getFeedItem(items.get(0).getId()));
    }
}

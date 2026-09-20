package de.danoeh.antennapod.storage.database;

import android.app.Application;
import de.danoeh.antennapod.event.DownloadLogEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class DbWriterDeletionTest extends DatabaseTestBase {

    private File createMediaFile(String name) throws IOException {
        File directory = context.getExternalFilesDir("episodes");
        assertNotNull(directory);
        File file = new File(directory, name);
        assertTrue(file.createNewFile());
        return file;
    }

    private FeedItem storeDownloadedItem(Feed feed, String title, File file) {
        FeedItem item = storeItem(feed, title);
        item.getMedia().setLocalFileUrl(file.getAbsolutePath());
        item.getMedia().setDownloaded(true, 5000);
        await(DBWriter.setFeedMedia(item.getMedia()));
        return item;
    }

    private int broadcastCount() {
        return shadowOf((Application) context).getBroadcastIntents().size();
    }

    @Test
    public void deleteFeedMediaOfItemRemovesFileAndDownloadInformation() throws IOException {
        Feed feed = storeFeed("feed");
        File file = createMediaFile("episode.mp3");
        FeedItem item = storeDownloadedItem(feed, "episode", file);

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertFalse(file.exists());
        FeedMedia stored = DBReader.getFeedItem(item.getId()).getMedia();
        assertFalse(stored.isDownloaded());
        assertNull(stored.getLocalFileUrl());
        assertEquals(item.getId(), events.eventsOfType(FeedItemEvent.class).get(0).items.get(0).getId());
    }

    @Test
    public void deleteFeedMediaOfItemAlsoRemovesTranscriptFile() throws IOException {
        Feed feed = storeFeed("feed");
        File file = createMediaFile("episode.mp3");
        File transcript = createMediaFile("episode.mp3.transcript");
        FeedItem item = storeDownloadedItem(feed, "episode", file);

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertFalse(file.exists());
        assertFalse(transcript.exists());
    }

    @Test
    public void deleteFeedMediaOfItemReportsDeletionForSync() throws IOException {
        Feed feed = storeFeed("feed");
        FeedItem item = storeDownloadedItem(feed, "episode", createMediaFile("episode.mp3"));

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertEquals(1, synchronizationQueue.getEpisodeActions().size());
        EpisodeAction action = synchronizationQueue.getEpisodeActions().get(0);
        assertEquals(EpisodeAction.DELETE, action.getAction());
        assertEquals(item.getItemIdentifier(), action.getGuid());
    }

    @Test
    public void deleteFeedMediaOfItemFromNotSubscribedFeedIsNotReportedForSync() throws IOException {
        Feed feed = storeFeed("feed");
        FeedItem item = storeDownloadedItem(feed, "episode", createMediaFile("episode.mp3"));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertTrue(synchronizationQueue.getEpisodeActions().isEmpty());
    }

    @Test
    public void deleteFeedMediaOfItemRemovesItemFromQueueWhenPreferenceIsSet() throws IOException {
        context.getSharedPreferences(context.getPackageName() + "_preferences", 0).edit()
                .putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true).commit();
        Feed feed = storeFeed("feed");
        FeedItem item = storeDownloadedItem(feed, "episode", createMediaFile("episode.mp3"));
        await(DBWriter.addQueueItem(context, item));

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertTrue(DBReader.getQueue().isEmpty());
    }

    @Test
    public void deleteFeedMediaOfItemKeepsItemInQueueByDefault() throws IOException {
        Feed feed = storeFeed("feed");
        FeedItem item = storeDownloadedItem(feed, "episode", createMediaFile("episode.mp3"));
        await(DBWriter.addQueueItem(context, item));

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertEquals(1, DBReader.getQueue().size());
    }

    @Test
    public void deleteFeedMediaOfCurrentlyPlayingItemStopsPlayback() throws IOException {
        Feed feed = storeFeed("feed");
        FeedItem item = storeDownloadedItem(feed, "episode", createMediaFile("episode.mp3"));
        PlaybackPreferences.writeMediaPlaying(item.getMedia());
        int broadcastsBefore = broadcastCount();

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(broadcastsBefore + 1, broadcastCount());
    }

    @Test
    public void deleteFeedMediaOfOtherItemLeavesPlaybackAlone() throws IOException {
        Feed feed = storeFeed("feed");
        FeedItem playing = storeItem(feed, "playing");
        FeedItem deleted = storeDownloadedItem(feed, "deleted", createMediaFile("deleted.mp3"));
        PlaybackPreferences.writeMediaPlaying(playing.getMedia());
        int broadcastsBefore = broadcastCount();

        await(DBWriter.deleteFeedMediaOfItem(context, deleted.getMedia()));

        assertEquals(playing.getMedia().getId(), PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(broadcastsBefore, broadcastCount());
    }

    @Test
    public void deleteFeedMediaOfItemWithContentUriRefreshesFeed() {
        Feed feed = storeFeed("local");
        FeedItem item = storeItem(feed, "episode");
        item.getMedia().setLocalFileUrl("content://com.example.provider/documents/episode.mp3");
        await(DBWriter.setFeedMedia(item.getMedia()));

        await(DBWriter.deleteFeedMediaOfItem(context, item.getMedia()));

        assertNull(item.getMedia().getLocalFileUrl());
        assertEquals(Collections.singletonList(feed), feedUpdateManager.getUpdatedFeeds());
        assertTrue(synchronizationQueue.getEpisodeActions().isEmpty());
    }

    @Test
    public void deleteFeedMediaOfNullMediaDoesNothing() {
        await(DBWriter.deleteFeedMediaOfItem(context, null));

        assertTrue(events.eventsOfType(FeedItemEvent.class).isEmpty());
    }

    @Test
    public void deleteFeedItemsRemovesDownloadedFilesAndQueueEntries() throws IOException {
        Feed feed = storeFeed("feed");
        File file = createMediaFile("gone.mp3");
        FeedItem gone = storeDownloadedItem(feed, "gone", file);
        FeedItem kept = storeItem(feed, "kept");
        await(DBWriter.addQueueItem(context, gone, kept));
        events.clear();

        await(DBWriter.deleteFeedItems(context, Collections.singletonList(gone)));

        assertFalse(file.exists());
        assertNull(DBReader.getFeedItem(gone.getId()));
        assertNotNull(DBReader.getFeedItem(kept.getId()));
        assertEquals(1, DBReader.getQueue().size());
        QueueEvent queueEvent = events.eventsOfType(QueueEvent.class).get(0);
        assertEquals(QueueEvent.Action.IRREVERSIBLE_REMOVED, queueEvent.action);
        assertEquals(gone.getId(), queueEvent.item.getId());
        assertEquals(1, events.eventsOfType(FeedItemEvent.class).get(0).items.size());
        assertEquals(1, events.eventsOfType(DownloadLogEvent.class).size());
    }

    @Test
    public void deleteFeedItemsRemovesDownloadLogOfDeletedItems() {
        Feed feed = storeFeed("feed");
        FeedItem gone = storeItem(feed, "gone");
        FeedItem kept = storeItem(feed, "kept");
        await(DBWriter.addDownloadStatus(new DownloadResult("gone", gone.getMedia().getId(),
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, false, DownloadError.ERROR_IO_ERROR, "disk")));
        await(DBWriter.addDownloadStatus(new DownloadResult("kept", kept.getMedia().getId(),
                FeedMedia.FEEDFILETYPE_FEEDMEDIA, true, DownloadError.SUCCESS, "")));

        await(DBWriter.deleteFeedItems(context, Collections.singletonList(gone)));

        assertEquals(1, DBReader.getDownloadLog().size());
        assertEquals("kept", DBReader.getDownloadLog().get(0).getTitle());
    }

    @Test
    public void deleteFeedItemsCancelsRunningDownload() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "downloading");
        Map<String, DownloadStatus> running = new HashMap<>();
        running.put(item.getMedia().getDownloadUrl(), new DownloadStatus(DownloadStatus.STATE_RUNNING, 30));
        downloadService.setCurrentDownloads(running);

        await(DBWriter.deleteFeedItems(context, Collections.singletonList(item)));

        assertEquals(1, downloadService.getCancelledMedia().size());
        assertEquals(item.getMedia().getId(), downloadService.getCancelledMedia().get(0).getId());
        assertNull(DBReader.getFeedItem(item.getId()));
    }

    @Test
    public void deleteFeedItemsStopsPlaybackOfDeletedItem() {
        Feed feed = storeFeed("feed");
        FeedItem item = storeItem(feed, "playing");
        PlaybackPreferences.writeMediaPlaying(item.getMedia());
        int broadcastsBefore = broadcastCount();

        await(DBWriter.deleteFeedItems(context, Collections.singletonList(item)));

        assertEquals(PlaybackPreferences.NO_MEDIA_PLAYING, PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        assertEquals(broadcastsBefore + 1, broadcastCount());
    }

    @Test
    public void deleteFeedItemsOfLocalFeedKeepsFilesAndNeverCancelsDownloads() throws IOException {
        Feed feed = new Feed(Feed.PREFIX_LOCAL_FOLDER + "content://folder", null, "Local");
        feed.setItems(new ArrayList<>());
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        File file = createMediaFile("local.mp3");
        FeedItem item = storeItem(feed, "local item");
        item.getMedia().setLocalFileUrl(file.getAbsolutePath());
        await(DBWriter.setFeedMedia(item.getMedia()));

        await(DBWriter.deleteFeedItems(context, Collections.singletonList(item)));

        assertTrue(file.exists());
        assertTrue(downloadService.getCancelledMedia().isEmpty());
        assertNull(DBReader.getFeedItem(item.getId()));
    }

    @Test
    public void deleteFeedDeletesDownloadedFilesOfItsEpisodes() throws IOException {
        Feed feed = storeFeed("feed");
        File file = createMediaFile("feed-episode.mp3");
        storeDownloadedItem(feed, "episode", file);

        await(DBWriter.deleteFeed(context, feed.getId()));

        assertFalse(file.exists());
        assertTrue(DBReader.getFeedList().isEmpty());
    }
}

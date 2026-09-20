package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.media.utils.MediaConstants;
import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class MediaLibraryBrowsingTest {
    private static final String FEED_URL = "http://example.com/feed";
    private static final String OTHER_FEED_URL = "http://example.com/other";
    private Context context;
    private MediaLibrarySessionCallback callback;
    private Feed feed;
    private final MediaLibraryService.MediaLibrarySession session =
            mock(MediaLibraryService.MediaLibrarySession.class);
    private final MediaSession.ControllerInfo browser = mock(MediaSession.ControllerInfo.class);

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PlaybackTestDatabase.setUp(context);
        feed = PlaybackTestDatabase.storeFeed(context, FEED_URL, "Main", 3);
        callback = new MediaLibrarySessionCallback(context);
    }

    @After
    public void tearDown() {
        RxJavaPlugins.reset();
        PlaybackTestDatabase.tearDown();
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }

    private ImmutableList<MediaItem> children(String parentId) {
        return await(callback.onGetChildren(session, browser, parentId, 0, 100, null)).value;
    }

    @Test
    public void anOrdinaryBrowserStartsAtTheLibraryRootWithSearchAdvertised() {
        when(browser.getPackageName()).thenReturn("com.example.car");

        LibraryResult<MediaItem> result = await(callback.onGetLibraryRoot(session, browser, null));

        assertEquals("root", result.value.mediaId);
        assertTrue(result.params.extras.getBoolean(
                MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED));
    }

    @Test
    public void theAssistantStartsDirectlyAtTheContinueListeningShelf() {
        when(browser.getPackageName()).thenReturn("com.google.android.googlequicksearchbox");

        LibraryResult<MediaItem> result = await(callback.onGetLibraryRoot(session, browser, null));

        assertEquals("continue_listening", result.value.mediaId);
    }

    @Test
    public void theRootListsTheFiveTopLevelShelves() {
        ImmutableList<MediaItem> items = children("root");

        assertEquals(ImmutableList.of("continue_listening", "queue", "downloads", "episodes", "subscriptions"),
                ImmutableList.of(items.get(0).mediaId, items.get(1).mediaId, items.get(2).mediaId,
                        items.get(3).mediaId, items.get(4).mediaId));
        for (MediaItem item : items) {
            assertEquals(Boolean.TRUE, item.mediaMetadata.isBrowsable);
        }
    }

    @Test
    public void theQueueShelfListsTheQueueInOrder() throws ExecutionException, InterruptedException {
        List<FeedItem> items = PlaybackTestDatabase.storedItems(feed.getId());
        DBWriter.addQueueItem(context, items.get(0), items.get(1)).get();

        ImmutableList<MediaItem> browsed = children("queue");

        assertEquals(2, browsed.size());
        assertEquals(String.valueOf(items.get(0).getMedia().getId()), browsed.get(0).mediaId);
        assertEquals(String.valueOf(items.get(1).getMedia().getId()), browsed.get(1).mediaId);
    }

    @Test
    public void theQueueShelfIsEmptyWhileNothingIsQueued() {
        assertTrue(children("queue").isEmpty());
    }

    @Test
    public void theDownloadsShelfListsOnlyDownloadedEpisodes() throws ExecutionException, InterruptedException {
        FeedMedia media = PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();
        media.setLocalFileUrl("/tmp/episode.mp3");
        media.setDownloaded(true, System.currentTimeMillis());
        DBWriter.setFeedMedia(media).get();

        ImmutableList<MediaItem> browsed = children("downloads");

        assertEquals(1, browsed.size());
        assertEquals(String.valueOf(media.getId()), browsed.get(0).mediaId);
    }

    @Test
    public void theEpisodesShelfListsEveryEpisodeOfEverySubscription() {
        PlaybackTestDatabase.storeFeed(context, OTHER_FEED_URL, "Other", 2);

        assertEquals(5, children("episodes").size());
    }

    @Test
    public void theSubscriptionsShelfSkipsFeedsTheUserIsNotSubscribedTo()
            throws ExecutionException, InterruptedException {
        Feed other = PlaybackTestDatabase.storeFeed(context, OTHER_FEED_URL, "Other", 1);
        DBWriter.setFeedState(context, other, Feed.STATE_NOT_SUBSCRIBED).get();

        ImmutableList<MediaItem> browsed = children("subscriptions");

        assertEquals(1, browsed.size());
        assertEquals(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + feed.getId(), browsed.get(0).mediaId);
    }

    @Test
    public void openingASubscriptionListsThatSubscriptionsEpisodes() {
        PlaybackTestDatabase.storeFeed(context, OTHER_FEED_URL, "Other", 2);

        ImmutableList<MediaItem> browsed = children(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + feed.getId());

        assertEquals(3, browsed.size());
        for (MediaItem item : browsed) {
            assertTrue(item.mediaMetadata.subtitle.toString().contains("Main"));
        }
    }

    @Test
    public void theContinueListeningShelfOffersRecentlyStartedEpisodes()
            throws ExecutionException, InterruptedException {
        List<FeedItem> items = PlaybackTestDatabase.storedItems(feed.getId());
        DBWriter.addQueueItem(context, items.get(0), items.get(1)).get();
        FeedMedia started = items.get(1).getMedia();
        started.setPosition(60000);
        started.setLastPlayedTimeStatistics(System.currentTimeMillis());
        started.setLastPlayedTimeHistory(new Date(System.currentTimeMillis()));
        DBWriter.setFeedMediaPlaybackInformation(started).get();

        ImmutableList<MediaItem> browsed = children("continue_listening");

        assertEquals(2, browsed.size());
        assertEquals(String.valueOf(started.getId()), browsed.get(0).mediaId);
    }

    @Test
    public void browsingAnUnknownShelfFails() {
        Future<LibraryResult<ImmutableList<MediaItem>>> result =
                callback.onGetChildren(session, browser, "nonsense", 0, 100, null);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> result.get(5, TimeUnit.SECONDS));
        assertTrue(thrown.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void aShelfItemReportsHowManyEpisodesItHolds() throws ExecutionException, InterruptedException {
        DBWriter.addQueueItem(context, PlaybackTestDatabase.storedItems(feed.getId()).get(0)).get();

        LibraryResult<MediaItem> result = await(callback.onGetItem(session, browser, "queue"));

        assertEquals("queue", result.value.mediaId);
        assertEquals(Boolean.TRUE, result.value.mediaMetadata.isBrowsable);
        assertEquals("1 episode", result.value.mediaMetadata.subtitle);
    }

    @Test
    public void aSubscriptionItemIsResolvedFromItsFeedId() {
        LibraryResult<MediaItem> result = await(callback.onGetItem(
                session, browser, MediaItemAdapter.MEDIA_ID_FEED_PREFIX + feed.getId()));

        assertEquals("Main", result.value.mediaMetadata.title);
        assertEquals(Boolean.TRUE, result.value.mediaMetadata.isBrowsable);
    }

    @Test
    public void searchingReturnsTheEpisodesWhoseTitleMatches() {
        PlaybackTestDatabase.storeFeed(context, OTHER_FEED_URL, "Other", 2);

        ImmutableList<MediaItem> results =
                await(callback.onGetSearchResult(session, browser, "Other Episode", 0, 100, null)).value;

        assertEquals(2, results.size());
        for (MediaItem item : results) {
            assertTrue(item.mediaMetadata.title.toString().startsWith("Other Episode"));
        }
    }

    @Test
    public void searchingForSomethingThatIsNotThereReturnsNothing() {
        assertTrue(await(callback.onGetSearchResult(
                session, browser, "no such episode", 0, 100, null)).value.isEmpty());
    }

    @Test
    public void aSearchRequestTellsTheBrowserHowManyResultsAreWaiting() {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());

        await(callback.onSearch(session, browser, "Main Episode", null));

        verify(session).notifySearchResultChanged(eq(browser), eq("Main Episode"), eq(3), any());
    }

    @Test
    public void aSearchRequestThatMatchesNothingTellsTheBrowserThereAreNoResults() {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());

        await(callback.onSearch(session, browser, "no such episode", null));

        verify(session).notifySearchResultChanged(eq(browser), eq("no such episode"), eq(0), any());
    }

    @Test
    public void anEmptySearchRequestReportsNoResultsStraightAway() {
        LibraryResult<Void> result = await(callback.onSearch(session, browser, "", null));

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode);
        verify(session).notifySearchResultChanged(eq(browser), eq(""), eq(0), any());
    }

    @Test
    public void turningBrowsedItemsIntoPlayableOnesDropsIdsThatAreNotInTheDatabase() {
        FeedMedia media = PlaybackTestDatabase.storedItems(feed.getId()).get(0).getMedia();

        List<MediaItem> resolved = await(callback.onAddMediaItems(session, browser, ImmutableList.of(
                MediaItemAdapter.fromMediaIdStub(media.getId()),
                MediaItemAdapter.fromMediaIdStub(Long.MAX_VALUE))));

        assertEquals(1, resolved.size());
        assertEquals(String.valueOf(media.getId()), resolved.get(0).mediaId);
        assertNotNull(resolved.get(0).localConfiguration);
    }

    @Test
    public void turningAnItemWithANonNumericIdIntoAPlayableOneDropsIt() {
        List<MediaItem> resolved = await(callback.onAddMediaItems(session, browser,
                ImmutableList.of(new MediaItem.Builder().setMediaId("not-a-number").build())));

        assertTrue(resolved.isEmpty());
    }

    @Test
    public void thereIsNothingToResolveForAnEmptyRequest() {
        assertTrue(await(callback.onAddMediaItems(session, browser, ImmutableList.of())).isEmpty());
    }

    @Test
    public void settingNoMediaItemsAtAllLeavesTheRequestedStartPositionUntouched() {
        MediaSession.MediaItemsWithStartPosition result = await(callback.onSetMediaItems(
                session, browser, ImmutableList.of(), 0, 4200));

        assertTrue(result.mediaItems.isEmpty());
        assertEquals(4200, result.startPositionMs);
        assertEquals(0, result.startIndex);
    }
}

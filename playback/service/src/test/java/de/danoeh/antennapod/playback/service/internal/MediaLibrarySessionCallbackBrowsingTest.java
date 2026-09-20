package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class MediaLibrarySessionCallbackBrowsingTest {
    private Context context;
    private MediaLibrarySessionCallback callback;
    private MediaSession session;
    private MediaLibraryService.MediaLibrarySession librarySession;
    private MediaSession.ControllerInfo controllerInfo;
    private MockedStatic<DBReader> dbReader;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PlaybackPreferences.writeNoMediaPlaying();
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        dbReader = mockStatic(DBReader.class);
        callback = new MediaLibrarySessionCallback(context);
        session = mock(MediaSession.class);
        librarySession = mock(MediaLibraryService.MediaLibrarySession.class);
        controllerInfo = mock(MediaSession.ControllerInfo.class);
    }

    @After
    public void tearDown() {
        dbReader.close();
        RxJavaPlugins.reset();
        PlaybackPreferences.writeNoMediaPlaying();
    }

    @Test
    public void settingNoMediaItemsAtAllIsPassedThroughUnchanged() throws Exception {
        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(
                session, controllerInfo, Collections.emptyList(), C.INDEX_UNSET, 1234).get();

        assertTrue(result.mediaItems.isEmpty());
        assertEquals(0, result.startIndex);
        assertEquals(1234, result.startPositionMs);
    }

    @Test
    public void settingAnEpisodeByIdLooksUpItsMetadataAndResumePosition() throws Exception {
        FeedMedia media = media(7, 90000);
        dbReader.when(() -> DBReader.getFeedMedia(7)).thenReturn(media);

        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(MediaItemAdapter.fromMediaIdStub(7)), C.INDEX_UNSET, C.TIME_UNSET).get();

        assertEquals(1, result.mediaItems.size());
        assertEquals("7", result.mediaItems.get(0).mediaId);
        assertEquals("Episode 7", result.mediaItems.get(0).mediaMetadata.title);
        assertEquals(90000, result.startPositionMs);
    }

    @Test
    public void settingAnEpisodeThatIsGoneLeavesNothingToPlay() throws Exception {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);

        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(MediaItemAdapter.fromMediaIdStub(7)), C.INDEX_UNSET, 500).get();

        assertTrue(result.mediaItems.isEmpty());
        assertEquals(500, result.startPositionMs);
    }

    @Test
    public void aVoiceSearchPlaysTheFirstMatchThatHasAMediaFile() throws Exception {
        FeedItem withoutMedia = new FeedItem();
        withoutMedia.setTitle("No media");
        dbReader.when(() -> DBReader.searchFeedItems(eq(0L), eq("news"), any()))
                .thenReturn(Arrays.asList(withoutMedia, media(7, 0).getItem()));

        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem("news")), C.INDEX_UNSET, C.TIME_UNSET).get();

        assertEquals(1, result.mediaItems.size());
        assertEquals("7", result.mediaItems.get(0).mediaId);
    }

    @Test
    public void aVoiceSearchWithoutAnyMatchPlaysNothing() throws Exception {
        dbReader.when(() -> DBReader.searchFeedItems(anyLong(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem("nothing")), C.INDEX_UNSET, 42).get();

        assertTrue(result.mediaItems.isEmpty());
        assertEquals(42, result.startPositionMs);
    }

    @Test
    public void aFailingVoiceSearchPlaysNothingRatherThanBreakingTheSession() throws Exception {
        dbReader.when(() -> DBReader.searchFeedItems(anyLong(), anyString(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem("boom")), C.INDEX_UNSET, C.TIME_UNSET).get();

        assertTrue(result.mediaItems.isEmpty());
    }

    @Test
    public void anEmptyVoiceSearchResumesWhatWasPlayedLast() throws Exception {
        FeedMedia media = media(7, 0);
        PlaybackPreferences.writeMediaPlaying(media);
        dbReader.when(() -> DBReader.getFeedMedia(7)).thenReturn(media);

        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem("")), C.INDEX_UNSET, C.TIME_UNSET).get();

        assertEquals(1, result.mediaItems.size());
        assertEquals("7", result.mediaItems.get(0).mediaId);
    }

    @Test
    public void addingNoMediaItemsYieldsNothingToAdd() throws Exception {
        assertTrue(callback.onAddMediaItems(session, controllerInfo, Collections.emptyList()).get().isEmpty());
    }

    @Test
    public void addedItemsAreReplacedByTheFullyLoadedEpisodes() throws Exception {
        dbReader.when(() -> DBReader.getFeedMedia(7)).thenReturn(media(7, 0));
        dbReader.when(() -> DBReader.getFeedMedia(8)).thenReturn(media(8, 0));

        List<MediaItem> result = callback.onAddMediaItems(session, controllerInfo, Arrays.asList(
                MediaItemAdapter.fromMediaIdStub(7), MediaItemAdapter.fromMediaIdStub(8))).get();

        assertEquals(2, result.size());
        assertEquals("Episode 7", result.get(0).mediaMetadata.title);
        assertEquals("Episode 8", result.get(1).mediaMetadata.title);
    }

    @Test
    public void anEpisodeThatNoLongerExistsIsDroppedFromTheAddedItems() throws Exception {
        dbReader.when(() -> DBReader.getFeedMedia(7)).thenReturn(media(7, 0));
        dbReader.when(() -> DBReader.getFeedMedia(8)).thenReturn(null);

        List<MediaItem> result = callback.onAddMediaItems(session, controllerInfo, Arrays.asList(
                MediaItemAdapter.fromMediaIdStub(7), MediaItemAdapter.fromMediaIdStub(8))).get();

        assertEquals(1, result.size());
        assertEquals("7", result.get(0).mediaId);
    }

    @Test
    public void anItemWithAnIdThatIsNotAnEpisodeIsDroppedFromTheAddedItems() throws Exception {
        List<MediaItem> result = callback.onAddMediaItems(session, controllerInfo, Collections.singletonList(
                new MediaItem.Builder().setMediaId("queue").build())).get();

        assertTrue(result.isEmpty());
    }

    @Test
    public void resumptionPrefersTheEpisodeThatWasPlayingLast() throws Exception {
        FeedMedia media = media(7, 120000);
        PlaybackPreferences.writeMediaPlaying(media);
        dbReader.when(() -> DBReader.getFeedMedia(7)).thenReturn(media);

        MediaSession.MediaItemsWithStartPosition result =
                callback.onPlaybackResumption(session, controllerInfo).get();

        assertEquals("7", result.mediaItems.get(0).mediaId);
        assertEquals(120000, result.startPositionMs);
    }

    @Test
    public void resumptionFallsBackToTheMostRecentlyPausedQueueEntry() throws Exception {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);
        dbReader.when(() -> DBReader.getPausedQueue(1))
                .thenReturn(Collections.singletonList(media(9, 0).getItem()));

        MediaSession.MediaItemsWithStartPosition result =
                callback.onPlaybackResumption(session, controllerInfo).get();

        assertEquals("9", result.mediaItems.get(0).mediaId);
    }

    @Test
    public void resumptionFallsBackToTheNewestEpisodeWhenNothingWasPaused() throws Exception {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);
        dbReader.when(() -> DBReader.getPausedQueue(anyInt())).thenReturn(Collections.emptyList());
        dbReader.when(() -> DBReader.getEpisodes(eq(0), eq(1), any(), eq(SortOrder.DATE_NEW_OLD)))
                .thenReturn(Collections.singletonList(media(11, 0).getItem()));

        MediaSession.MediaItemsWithStartPosition result =
                callback.onPlaybackResumption(session, controllerInfo).get();

        assertEquals("11", result.mediaItems.get(0).mediaId);
    }

    @Test
    public void resumptionFailsWhenTheLibraryHoldsNoEpisodeAtAll() {
        dbReader.when(() -> DBReader.getFeedMedia(anyLong())).thenReturn(null);
        dbReader.when(() -> DBReader.getPausedQueue(anyInt())).thenReturn(Collections.emptyList());
        dbReader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());

        try {
            callback.onPlaybackResumption(session, controllerInfo).get();
            fail("Expected the resumption request to fail");
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e instanceof ExecutionException);
        }
    }

    @Test
    public void browsingStartsAtTheLibraryRoot() throws Exception {
        when(controllerInfo.getPackageName()).thenReturn("de.danoeh.antennapod");

        LibraryResult<MediaItem> result =
                callback.onGetLibraryRoot(librarySession, controllerInfo, null).get();

        assertEquals("root", result.value.mediaId);
    }

    @Test
    public void theAssistantIsSentStraightToTheContinueListeningShelf() throws Exception {
        when(controllerInfo.getPackageName()).thenReturn("com.google.android.googlequicksearchbox");

        LibraryResult<MediaItem> result =
                callback.onGetLibraryRoot(librarySession, controllerInfo, null).get();

        assertEquals("continue_listening", result.value.mediaId);
    }

    @Test
    public void theRootOffersTheContinueListeningQueueDownloadsEpisodesAndSubscriptionShelves() throws Exception {
        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetChildren(
                librarySession, controllerInfo, "root", 0, 10, null).get();

        List<String> ids = new ArrayList<>();
        for (MediaItem item : result.value) {
            ids.add(item.mediaId);
        }
        assertEquals(Arrays.asList("continue_listening", "queue", "downloads", "episodes", "subscriptions"), ids);
    }

    @Test
    public void onlySubscribedFeedsAppearUnderSubscriptions() throws Exception {
        Feed subscribed = feed(1, Feed.STATE_SUBSCRIBED);
        Feed notSubscribed = feed(2, Feed.STATE_NOT_SUBSCRIBED);
        dbReader.when(DBReader::getFeedList).thenReturn(Arrays.asList(subscribed, notSubscribed));

        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetChildren(
                librarySession, controllerInfo, "subscriptions", 0, 10, null).get();

        assertEquals(1, result.value.size());
        assertEquals(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + "1", result.value.get(0).mediaId);
    }

    @Test
    public void theContinueListeningShelfShowsThePausedQueue() throws Exception {
        dbReader.when(() -> DBReader.getPausedQueue(anyInt()))
                .thenReturn(Collections.singletonList(media(7, 0).getItem()));

        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetChildren(
                librarySession, controllerInfo, "continue_listening", 0, 10, null).get();

        assertEquals(1, result.value.size());
        assertEquals("7", result.value.get(0).mediaId);
    }

    @Test
    public void aFailingContinueListeningLookupShowsAnEmptyShelfInsteadOfAnError() throws Exception {
        dbReader.when(() -> DBReader.getPausedQueue(anyInt()))
                .thenThrow(new IllegalStateException("database unavailable"));

        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetChildren(
                librarySession, controllerInfo, "continue_listening", 0, 10, null).get();

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode);
        assertTrue(result.value.isEmpty());
    }

    @Test
    public void theQueueShelfShowsTheQueue() throws Exception {
        dbReader.when(DBReader::getQueue).thenReturn(Collections.singletonList(media(7, 0).getItem()));

        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetChildren(
                librarySession, controllerInfo, "queue", 0, 10, null).get();

        assertEquals(1, result.value.size());
        assertEquals("7", result.value.get(0).mediaId);
    }

    @Test
    public void theEpisodesShelfIsPagedThroughTheDatabaseQuery() throws Exception {
        dbReader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(), any()))
                .thenReturn(Collections.singletonList(media(7, 0).getItem()));

        callback.onGetChildren(librarySession, controllerInfo, "episodes", 2, 20, null).get();

        dbReader.verify(() -> DBReader.getEpisodes(eq(40), eq(20), any(), any()));
    }

    @Test
    public void aBrowserAskingForMoreThanTheSafetyLimitIsCappedAtOneHundred() throws Exception {
        dbReader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());

        callback.onGetChildren(librarySession, controllerInfo, "downloads", 0, 5000, null).get();

        dbReader.verify(() -> DBReader.getEpisodes(eq(0), eq(100), any(), any()));
    }

    @Test
    public void aFeedShelfShowsThatFeedsEpisodes() throws Exception {
        Feed feed = feed(5, Feed.STATE_SUBSCRIBED);
        feed.getItems().add(media(7, 0).getItem());
        dbReader.when(() -> DBReader.getFeed(eq(5L), eq(true), anyInt(), anyInt())).thenReturn(feed);

        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetChildren(librarySession, controllerInfo,
                MediaItemAdapter.MEDIA_ID_FEED_PREFIX + "5", 0, 10, null).get();

        assertEquals(1, result.value.size());
        assertEquals("7", result.value.get(0).mediaId);
    }

    @Test
    public void anUnknownShelfIsReportedAsAFailedRequest() {
        try {
            callback.onGetChildren(librarySession, controllerInfo, "nonsense", 0, 10, null).get();
            fail("Expected the request for an unknown shelf to fail");
        } catch (InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void aShelfItselfCanBeLookedUpByItsId() throws Exception {
        dbReader.when(() -> DBReader.getTotalEpisodeCount(any())).thenReturn(12);

        LibraryResult<MediaItem> result = callback.onGetItem(librarySession, controllerInfo, "queue").get();

        assertEquals("queue", result.value.mediaId);
        assertTrue(result.value.mediaMetadata.isBrowsable);
    }

    @Test
    public void aFeedCanBeLookedUpByItsId() throws Exception {
        dbReader.when(() -> DBReader.getFeed(eq(5L), eq(false), anyInt(), anyInt()))
                .thenReturn(feed(5, Feed.STATE_SUBSCRIBED));

        LibraryResult<MediaItem> result = callback.onGetItem(librarySession, controllerInfo,
                MediaItemAdapter.MEDIA_ID_FEED_PREFIX + "5").get();

        assertEquals(MediaItemAdapter.MEDIA_ID_FEED_PREFIX + "5", result.value.mediaId);
    }

    @Test
    public void anIdThatIsNeitherShelfNorFeedIsNotResolvedAsABrowsableItem() throws Exception {
        LibraryResult<MediaItem> result = callback.onGetItem(librarySession, controllerInfo, "7").get();

        assertNotEquals(LibraryResult.RESULT_SUCCESS, result.resultCode);
    }

    @Test
    public void aSearchResultListsTheMatchingEpisodes() throws Exception {
        dbReader.when(() -> DBReader.searchFeedItems(eq(0L), eq("news"), any()))
                .thenReturn(Collections.singletonList(media(7, 0).getItem()));

        LibraryResult<ImmutableList<MediaItem>> result = callback.onGetSearchResult(
                librarySession, controllerInfo, "news", 0, 10, null).get();

        assertEquals(1, result.value.size());
        assertEquals("7", result.value.get(0).mediaId);
    }

    @Test
    public void aSearchTellsTheBrowserHowManyResultsItCanFetch() {
        dbReader.when(() -> DBReader.searchFeedItems(anyLong(), anyString(), any()))
                .thenReturn(Arrays.asList(media(7, 0).getItem(), media(8, 0).getItem()));

        callback.onSearch(librarySession, controllerInfo, "news", null);

        verify(librarySession).notifySearchResultChanged(controllerInfo, "news", 2, null);
    }

    @Test
    public void anEmptySearchQueryIsAnsweredWithoutTouchingTheDatabase() {
        callback.onSearch(librarySession, controllerInfo, "", null);

        verify(librarySession).notifySearchResultChanged(controllerInfo, "", 0, null);
        dbReader.verifyNoInteractions();
    }

    @Test
    public void aFailingSearchIsReportedAsNoResultsRatherThanBreakingTheBrowser() {
        dbReader.when(() -> DBReader.searchFeedItems(anyLong(), anyString(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        callback.onSearch(librarySession, controllerInfo, "news", null);

        verify(librarySession).notifySearchResultChanged(controllerInfo, "news", 0, null);
    }

    @Test
    public void theEpisodeShelfUsesTheConfiguredFilterAndSortOrder() throws Exception {
        UserPreferences.setPrefFilterAllEpisodes(FeedItemFilter.UNPLAYED);
        UserPreferences.setAllEpisodesSortOrder(SortOrder.DATE_OLD_NEW);
        dbReader.when(() -> DBReader.getEpisodes(anyInt(), anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());

        callback.onGetChildren(librarySession, controllerInfo, "episodes", 0, 10, null).get();

        ArgumentCaptor<FeedItemFilter> filter = ArgumentCaptor.forClass(FeedItemFilter.class);
        dbReader.verify(() -> DBReader.getEpisodes(anyInt(), anyInt(), filter.capture(),
                eq(SortOrder.DATE_OLD_NEW)));
        assertTrue(filter.getValue().showUnplayed);
        assertFalse(filter.getValue().showPlayed);
    }

    private static MediaItem searchItem(String query) {
        return MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setSearchQuery(query).build())
                .build();
    }

    private static Feed feed(long id, int state) {
        Feed feed = new Feed(id, null, "Feed " + id, "http://example.com", "d", null, null, null,
                null, "id" + id, null, null, "http://example.com/feed" + id + ".xml", 0);
        feed.setState(state);
        return feed;
    }

    private static FeedMedia media(long id, int position) {
        Feed feed = feed(1, Feed.STATE_SUBSCRIBED);
        FeedItem item = new FeedItem(id, "Episode " + id, "id" + id, "http://example.com",
                new Date(0), FeedItem.UNPLAYED, feed);
        FeedMedia media = new FeedMedia(id, item, 300000, position, 1, "audio/mp3", null,
                "http://example.com/e" + id + ".mp3", 0, null, 0, 0);
        item.setMedia(media);
        feed.getItems().add(item);
        return media;
    }
}

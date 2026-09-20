package de.danoeh.antennapod.playback.service.internal;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.storage.database.DBWriter;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PlayableUtilsTest {

    @Test
    public void savingAPositionRecordsItTogetherWithTheTimeItWasReached() {
        Playable playable = mock(Playable.class);

        try (MockedStatic<DBWriter> writer = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(playable, 12000, 1700000000000L);

            writer.verifyNoInteractions();
        }

        verify(playable).setPosition(12000);
        verify(playable).setLastPlayedTimeStatistics(1700000000000L);
    }

    @Test
    public void savingAPositionOfAFeedMediaPersistsItAndTheListeningHistoryEntry() {
        FeedMedia media = media();
        media.getItem().setPlayState(FeedItem.UNPLAYED);

        try (MockedStatic<DBWriter> writer = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(media, 12000, 1700000000000L);

            writer.verify(() -> DBWriter.setFeedMediaPlaybackInformation(media));
            writer.verify(() -> DBWriter.markItemsPlayed(anyInt(), anyBoolean(), any()), never());
        }

        assertEquals(12000, media.getPosition());
        assertEquals(new Date(1700000000000L), media.getLastPlayedTimeHistory());
    }

    @Test
    public void savingTheStartOfANewEpisodePersistsThatItIsNoLongerNew() {
        FeedMedia media = media();
        FeedItem item = media.getItem();
        item.setPlayState(FeedItem.NEW);

        try (MockedStatic<DBWriter> writer = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(media, 0, 1700000000000L);

            writer.verify(() -> DBWriter.markItemsPlayed(
                    FeedItem.UNPLAYED, false, Collections.singletonList(item)));
        }
    }

    @Test
    public void savingAPositionInsideANewEpisodeTakesItOutOfTheNewStateWithoutASeparateWrite() {
        FeedMedia media = media();
        FeedItem item = media.getItem();
        item.setPlayState(FeedItem.NEW);

        try (MockedStatic<DBWriter> writer = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(media, 12000, 1700000000000L);

            writer.verify(() -> DBWriter.markItemsPlayed(anyInt(), anyBoolean(), any()), never());
        }

        assertEquals(FeedItem.UNPLAYED, item.getPlayState());
    }

    @Test
    public void playedDurationGrowsByTheStretchPlayedSinceThisPlaybackStarted() {
        FeedMedia media = media();
        media.setPosition(30000);
        media.onPlaybackStart();

        try (MockedStatic<DBWriter> ignored = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(media, 50000, 1700000000000L);
        }

        assertEquals(20000, media.getPlayedDuration());
    }

    @Test
    public void seekingBackwardsDoesNotShrinkThePlayedDuration() {
        FeedMedia media = media();
        media.setPosition(30000);
        media.onPlaybackStart();

        try (MockedStatic<DBWriter> ignored = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(media, 10000, 1700000000000L);
        }

        assertEquals(0, media.getPlayedDuration());
    }

    @Test
    public void positionsSavedBeforePlaybackStartedDoNotCountTowardsPlayedDuration() {
        FeedMedia media = media();

        try (MockedStatic<DBWriter> ignored = mockStatic(DBWriter.class)) {
            PlayableUtils.saveCurrentPosition(media, 50000, 1700000000000L);
        }

        assertEquals(0, media.getPlayedDuration());
    }

    private static FeedMedia media() {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Feed");
        FeedItem item = new FeedItem();
        item.setTitle("Episode");
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(item, "http://example.com/e.mp3", 1, "audio/mp3");
        item.setMedia(media);
        media.setDuration(300000);
        return media;
    }
}

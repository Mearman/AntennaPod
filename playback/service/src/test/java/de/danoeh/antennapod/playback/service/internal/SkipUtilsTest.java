package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SkipUtilsTest {
    private Context context;
    private final List<MessageEvent> messages = new ArrayList<>();

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onMessage(MessageEvent event) {
        messages.add(event);
    }

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        messages.clear();
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
    }

    @Test
    public void skipIntroKeepsSavedPositionWhenFeedPreferencesAreMissing() {
        FeedMedia media = mediaWithPreferences(null, 30000, 4000);
        assertEquals(4000, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipIntroKeepsSavedPositionWhenDetachedFromItem() {
        FeedMedia media = new FeedMedia(null, "http://example.com/e.mp3", 1, "audio/mp3");
        media.setPosition(7000);
        assertEquals(7000, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipIntroAdvancesToConfiguredIntroEndAndNotifiesUser() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(10, 0), 300000, 0);
        assertEquals(10000, SkipUtils.skipIntroIfNecessary(context, media));
        assertEquals(1, messages.size());
    }

    @Test
    public void skipIntroKeepsPositionAlreadyPastTheIntro() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(10, 0), 300000, 15000);
        assertEquals(15000, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipIntroLongerThanTheEpisodeIsIgnored() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(600, 0), 300000, 0);
        assertEquals(0, SkipUtils.skipIntroIfNecessary(context, media));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipIntroAppliesWhenDurationIsStillUnknown() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(30, 0), 0, 0);
        assertEquals(30000, SkipUtils.skipIntroIfNecessary(context, media));
        assertEquals(1, messages.size());
    }

    @Test
    public void skipEndingIsNotAppliedWhenFeedPreferencesAreMissing() {
        FeedMedia media = mediaWithPreferences(null, 300000, 0);
        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 290000, 300000, 1.0f));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipEndingTriggersOnlyOnceTheRemainderIsWithinOneSecondOfPlayback() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(0, 20), 300000, 0);
        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 275000, 300000, 1.0f));
        assertTrue(messages.isEmpty());
        assertTrue(SkipUtils.skipEndingIfNecessary(context, media, 279500, 300000, 1.0f));
        assertEquals(1, messages.size());
    }

    @Test
    public void skipEndingTriggersEarlierAtHigherPlaybackSpeed() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(0, 20), 300000, 0);
        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 277000, 300000, 1.0f));
        assertTrue(SkipUtils.skipEndingIfNecessary(context, media, 277000, 300000, 4.0f));
    }

    @Test
    public void skipEndingIsNotAppliedOncePlaybackIsInsideTheSkippedRange() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(0, 20), 300000, 0);
        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 295000, 300000, 1.0f));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipEndingLongerThanTheEpisodeIsIgnored() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(0, 600), 300000, 0);
        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 299500, 300000, 1.0f));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void skipEndingIsDisabledWhenSetToZero() {
        FeedMedia media = mediaWithPreferences(preferencesWithSkip(0, 0), 300000, 0);
        assertFalse(SkipUtils.skipEndingIfNecessary(context, media, 299500, 300000, 1.0f));
        assertTrue(messages.isEmpty());
    }

    private static FeedPreferences preferencesWithSkip(int skipIntro, int skipEnding) {
        FeedPreferences preferences = new FeedPreferences(1, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
        preferences.setFeedSkipIntro(skipIntro);
        preferences.setFeedSkipEnding(skipEnding);
        return preferences;
    }

    private static FeedMedia mediaWithPreferences(FeedPreferences preferences, int duration, int position) {
        Feed feed = new Feed("http://example.com/feed.xml", null, "Feed");
        feed.setPreferences(preferences);
        FeedItem item = new FeedItem();
        item.setTitle("Episode");
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(item, "http://example.com/e.mp3", 1, "audio/mp3");
        item.setMedia(media);
        media.setDuration(duration);
        media.setPosition(position);
        return media;
    }
}

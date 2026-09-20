package de.danoeh.antennapod.event;

import android.content.Context;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class StatusEventDeliveryTest {
    private final List<Object> received = new ArrayList<>();
    private Context context;

    @Subscribe
    public void onMessage(MessageEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onPlayerError(PlayerErrorEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onSyncService(SyncServiceEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onDownloadLog(DownloadLogEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onFeedUpdateRunning(FeedUpdateRunningEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onStatistics(StatisticsEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onPlayerStatus(PlayerStatusEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onDiscoveryDefaultUpdate(DiscoveryDefaultUpdateEvent event) {
        received.add(event);
    }

    @Subscribe
    public void onStreamingConfirmation(StreamingConfirmationEvent event) {
        received.add(event);
    }

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        EventBus.getDefault().removeAllStickyEvents();
        EventBus.getDefault().register(this);
    }

    @After
    public void tearDown() {
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeAllStickyEvents();
    }

    @Test
    public void aPlainMessageEventCarriesNoActionForTheSubscriberToOffer() {
        EventBus.getDefault().post(new MessageEvent("Something happened"));

        MessageEvent event = (MessageEvent) received.get(0);
        assertEquals("Something happened", event.message);
        assertNull(event.action);
        assertNull(event.actionText);
    }

    @Test
    public void aMessageEventActionIsRunnableBySubscribersWithTheApplicationContext() {
        AtomicReference<Context> invokedWith = new AtomicReference<>();
        EventBus.getDefault().post(new MessageEvent("Undo?", invokedWith::set, "Undo"));

        MessageEvent event = (MessageEvent) received.get(0);
        assertEquals("Undo", event.actionText);
        assertNotNull(event.action);
        event.action.accept(context);
        assertSame(context, invokedWith.get());
    }

    @Test
    public void playerErrorEventCarriesTheFailureMessage() {
        EventBus.getDefault().post(new PlayerErrorEvent("Source not reachable"));

        assertEquals("Source not reachable", ((PlayerErrorEvent) received.get(0)).getMessage());
    }

    @Test
    public void syncServiceEventCarriesTheStringResourceToShow() {
        EventBus.getDefault().post(new SyncServiceEvent(android.R.string.ok));

        assertEquals(android.R.string.ok, ((SyncServiceEvent) received.get(0)).getMessageResId());
    }

    @Test
    public void downloadLogEventIsDeliveredAndIdentifiesItselfByName() {
        EventBus.getDefault().post(DownloadLogEvent.listUpdated());

        assertEquals("DownloadLogEvent", received.get(0).toString());
    }

    @Test
    public void feedUpdateRunningEventDistinguishesStartFromEnd() {
        EventBus.getDefault().post(new FeedUpdateRunningEvent(true));
        EventBus.getDefault().post(new FeedUpdateRunningEvent(false));

        assertTrue(((FeedUpdateRunningEvent) received.get(0)).isFeedUpdateRunning);
        assertFalse(((FeedUpdateRunningEvent) received.get(1)).isFeedUpdateRunning);
    }

    @Test
    public void aStickyFeedUpdateRunningEventReachesSubscribersThatRegisterLater() {
        EventBus.getDefault().postSticky(new FeedUpdateRunningEvent(true));

        StickyFeedUpdateRecorder late = new StickyFeedUpdateRecorder();
        EventBus.getDefault().register(late);
        EventBus.getDefault().unregister(late);

        assertNotNull(late.event);
        assertTrue(late.event.isFeedUpdateRunning);
    }

    public static class StickyFeedUpdateRecorder {
        private FeedUpdateRunningEvent event;

        @Subscribe(sticky = true)
        public void onFeedUpdateRunning(FeedUpdateRunningEvent feedUpdateRunningEvent) {
            this.event = feedUpdateRunningEvent;
        }
    }

    @Test
    public void notificationOnlyEventsReachEverySubscriberOfTheirType() {
        EventBus.getDefault().post(new StatisticsEvent());
        EventBus.getDefault().post(new PlayerStatusEvent());
        EventBus.getDefault().post(new DiscoveryDefaultUpdateEvent());
        EventBus.getDefault().post(new StreamingConfirmationEvent());

        assertEquals(4, received.size());
        assertTrue(received.get(0) instanceof StatisticsEvent);
        assertTrue(received.get(1) instanceof PlayerStatusEvent);
        assertTrue(received.get(2) instanceof DiscoveryDefaultUpdateEvent);
        assertTrue(received.get(3) instanceof StreamingConfirmationEvent);
    }

    @Test
    public void anUnregisteredSubscriberNoLongerReceivesEvents() {
        EventBus.getDefault().unregister(this);

        EventBus.getDefault().post(new PlayerStatusEvent());

        assertTrue(received.isEmpty());
        EventBus.getDefault().register(this);
    }
}

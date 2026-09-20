package de.test.antennapod.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.screen.onlinefeedview.OnlineFeedViewActivity;
import de.test.antennapod.service.download.DownloadTestFixture;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Opens podcast links in the online feed view, downloads the feed from the local test server and subscribes to it.
 */
@RunWith(AndroidJUnit4.class)
public class OnlineFeedViewTest {
    private static final long VIEW_TIMEOUT_MILLIS = 15000;
    private static final long TIMEOUT_SECONDS = 30;
    private static final int EPISODES = 2;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private Context context;
    private ActivityScenario<OnlineFeedViewActivity> scenario;
    private String feedUrl;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture.setUp();
        Feed hosted = fixture.newFeed("Online", EPISODES);
        feedUrl = fixture.hostFeed(hosted);
    }

    @After
    public void tearDown() throws Exception {
        if (scenario != null) {
            scenario.close();
        }
        fixture.tearDown();
    }

    private String withoutScheme(String url) {
        return url.substring("http://".length());
    }

    private void open(String link) {
        Intent intent = new Intent(context, OnlineFeedViewActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(link));
        scenario = ActivityScenario.launch(intent);
    }

    private void subscribeAndVerify(String expectedDownloadUrl) {
        waitForViewGlobally(allOf(withId(R.id.butSubscribe), isDisplayed()), VIEW_TIMEOUT_MILLIS);
        assertEquals(1, DBReader.getFeedList().size());
        assertEquals(Feed.STATE_NOT_SUBSCRIBED, DBReader.getFeedList().get(0).getState());

        onView(allOf(withId(R.id.butSubscribe), isDisplayed())).perform(click());

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> DBReader.getFeedList().get(0).getState() == Feed.STATE_SUBSCRIBED);
        Feed subscribed = DBReader.getFeed(DBReader.getFeedList().get(0).getId(), false, 0, Integer.MAX_VALUE);
        assertEquals(expectedDownloadUrl, subscribed.getDownloadUrl());
        assertEquals("Online", subscribed.getTitle());
        assertEquals(EPISODES, subscribed.getItems().size());
    }

    @Test
    public void feedSchemeLinkSubscribesToThePodcast() throws Exception {
        open("feed://" + withoutScheme(feedUrl));

        subscribeAndVerify(feedUrl);
    }

    @Test
    public void itpcSchemeLinkSubscribesToThePodcast() throws Exception {
        open("itpc://" + withoutScheme(feedUrl));

        subscribeAndVerify(feedUrl);
    }

    @Test
    public void pcastSchemeLinkSubscribesToThePodcast() throws Exception {
        open("pcast://" + withoutScheme(feedUrl));

        subscribeAndVerify(feedUrl);
    }

    @Test
    public void antennapodSubscribeSchemeLinkSubscribesToThePodcast() throws Exception {
        open("antennapod-subscribe://" + withoutScheme(feedUrl));

        subscribeAndVerify(feedUrl);
    }

    @Test
    public void deeplinkWithFeedUrlParameterSubscribesToThePodcast() throws Exception {
        open("https://antennapod.org/deeplink/subscribe?url=" + Uri.encode(feedUrl));

        subscribeAndVerify(feedUrl);
    }

    @Test
    public void subscribeOnAndroidLinkSubscribesToThePodcast() throws Exception {
        open("subscribeonandroid.com/" + withoutScheme(feedUrl));

        subscribeAndVerify(feedUrl);
    }

    @Test
    public void linkWithoutAnAddressShowsAnErrorAndSubscribesToNothing() throws Exception {
        open("https://antennapod.org/deeplink/subscribe");

        waitForViewGlobally(withText(R.string.null_value_podcast_error), VIEW_TIMEOUT_MILLIS);
        onView(withText(android.R.string.ok)).perform(click());

        assertTrue(DBReader.getFeedList().isEmpty());
    }

    @Test
    public void podcastThatIsAlreadySubscribedIsOpenedInTheApp() throws Exception {
        Feed hosted = fixture.newFeed("Known", 1);
        hosted.setDownloadUrl(fixture.hostFeed(hosted));
        fixture.subscribe(hosted);

        open(hosted.getDownloadUrl());

        waitForViewGlobally(withText(hosted.getItemAtIndex(0).getTitle()), VIEW_TIMEOUT_MILLIS);
        assertEquals(1, DBReader.getFeedList().size());
    }

    @Test
    public void feedThatNeedsAPasswordAsksForCredentialsAndStoresThem() throws Exception {
        String protectedUrl = fixture.url("/basic-auth-file/user/secret/" + fixture.idOf(feedUrl));
        open(protectedUrl);

        waitForViewGlobally(withText(R.string.authentication_notification_title), VIEW_TIMEOUT_MILLIS);
        int requestsBeforeRetry = fixture.requestsFor(protectedUrl).size();
        typeCredentials("user", "wrong");
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(
                () -> fixture.requestsFor(protectedUrl).size() > requestsBeforeRetry);
        waitForViewGlobally(withText(R.string.authentication_notification_title), VIEW_TIMEOUT_MILLIS);
        typeCredentials("user", "secret");

        subscribeAndVerify(protectedUrl);
        List<Feed> feeds = DBReader.getFeedList();
        Feed stored = DBReader.getFeed(feeds.get(0).getId(), false, 0, Integer.MAX_VALUE);
        assertEquals("user", stored.getPreferences().getUsername());
        assertEquals("secret", stored.getPreferences().getPassword());
    }

    private void typeCredentials(String username, String password) {
        onView(allOf(withId(R.id.usernameEditText), isDisplayed())).perform(replaceText(username));
        onView(allOf(withId(R.id.passwordEditText), isDisplayed())).perform(replaceText(password),
                closeSoftKeyboard());
        onView(allOf(withText(R.string.confirm_label), isDisplayed())).perform(click());
    }

    @Test
    public void missingFeedShowsAnErrorWithTheReason() throws Exception {
        open(fixture.url("/status/404"));

        waitForViewGlobally(withText(R.string.error_label), VIEW_TIMEOUT_MILLIS);
        onView(withText(android.R.string.ok)).perform(click());

        assertTrue(DBReader.getFeedList().isEmpty());
    }

    private AccessibilityNodeInfo findNode(String text) {
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    @Test
    public void webPageInsteadOfAFeedShowsAnError() throws Exception {
        String pageUrl = fixture.hostText("page.html", "<html><head><title>Website</title></head></html>");
        open(pageUrl);

        String message = context.getString(R.string.download_error_unsupported_type_html);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> findNode(message) != null);
        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            AccessibilityNodeInfo ok = findNode(context.getString(android.R.string.ok));
            return ok != null && ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        });

        Awaitility.await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> findNode(message) == null);
        assertTrue(DBReader.getFeedList().isEmpty());
    }
}

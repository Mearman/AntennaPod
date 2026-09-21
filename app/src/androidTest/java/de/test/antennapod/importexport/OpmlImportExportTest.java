package de.test.antennapod.importexport;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.ui.UITestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.test.espresso.intent.rule.IntentsTestRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasType;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickPreference;
import static de.test.antennapod.EspressoTestUtils.waitForViewGlobally;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class OpmlImportExportTest {
    private UITestUtils uiTestUtils;

    @Rule
    public IntentsTestRule<PreferenceActivity> activityTestRule =
            new IntentsTestRule<>(PreferenceActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearDatabase();
        EspressoTestUtils.clearPreferences();
        uiTestUtils = new UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext());
        uiTestUtils.setup();
        uiTestUtils.addHostedFeedData();
        activityTestRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        activityTestRule.finishActivity();
        uiTestUtils.tearDown();
    }

    private File exportTargetFile(String name) throws IOException {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        File file = new File(dir, name);
        if (file.exists()) {
            assertTrue(file.delete());
        }
        return file;
    }

    private String readFile(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        String content = IOUtils.toString(in, StandardCharsets.UTF_8);
        in.close();
        return content;
    }

    private void stubCreateDocument(File target, String type, String titlePart) {
        Intent data = new Intent();
        data.setData(Uri.fromFile(target));
        intending(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT),
                hasType(type),
                hasExtra(Intent.EXTRA_TITLE, containsString(titlePart))))
                .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, data));
    }

    private void addFeedsToDatabase() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(uiTestUtils.hostedFeeds.toArray(new Feed[0]));
        adapter.close();
    }

    private void openImportExportScreen() {
        clickPreference(R.string.import_export_pref);
    }

    @Test
    public void testOpmlExportWritesAllSubscriptionUrls() throws Exception {
        addFeedsToDatabase();
        File target = exportTargetFile("export.opml");
        stubCreateDocument(target, "text/x-opml", "antennapod-feeds");

        openImportExportScreen();
        clickPreference(R.string.opml_export_label);

        waitForViewGlobally(withText(R.string.export_success_title), 15000);
        String opml = readFile(target);
        assertTrue(opml.contains("<opml"));
        for (Feed feed : uiTestUtils.hostedFeeds) {
            assertTrue(opml.contains(feed.getDownloadUrl()));
            assertTrue(opml.contains(feed.getTitle()));
        }
    }

    @Test
    public void testHtmlExportContainsFeedList() throws Exception {
        addFeedsToDatabase();
        File target = exportTargetFile("export.html");
        stubCreateDocument(target, "text/html", "antennapod-feeds");

        openImportExportScreen();
        clickPreference(R.string.html_export_label);

        waitForViewGlobally(withText(R.string.export_success_title), 15000);
        String html = readFile(target);
        assertTrue(html.contains("<html"));
        for (Feed feed : uiTestUtils.hostedFeeds) {
            assertTrue(html.contains(feed.getTitle()));
        }
    }

    @Test
    public void testFavoritesExportOnlyContainsFavoriteEpisodes() throws Exception {
        addFeedsToDatabase();
        List<FeedItem> favorites = new ArrayList<>();
        List<FeedItem> items = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD);
        favorites.add(items.get(0));
        favorites.add(items.get(1));
        DBWriter.addFavoriteItems(favorites).get();

        File target = exportTargetFile("favorites.html");
        stubCreateDocument(target, "text/html", "antennapod-favorites");

        openImportExportScreen();
        clickPreference(R.string.favorites_export_label);

        waitForViewGlobally(withText(R.string.export_success_title), 15000);
        String html = readFile(target);
        assertTrue(html.contains("<html"));
        assertTrue(html.contains(items.get(0).getTitle()));
        assertTrue(html.contains(items.get(1).getTitle()));
        assertFalse(html.contains(items.get(5).getTitle()));
    }

    @Test
    public void testOpmlAndHtmlExportsSkipFeedsThatAreNotSubscribed() throws Exception {
        addFeedsToDatabase();
        Feed unsubscribed = uiTestUtils.hostedFeeds.get(0);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFeedState(unsubscribed.getId(), Feed.STATE_NOT_SUBSCRIBED);
        adapter.close();

        File opmlTarget = exportTargetFile("subscriptions-filtered.opml");
        stubCreateDocument(opmlTarget, "text/x-opml", "antennapod-feeds");
        openImportExportScreen();
        clickPreference(R.string.opml_export_label);
        waitForViewGlobally(withText(R.string.export_success_title), 15000);
        String opml = readFile(opmlTarget);
        assertFalse(opml.contains(unsubscribed.getDownloadUrl()));
        for (Feed feed : uiTestUtils.hostedFeeds) {
            if (feed.getId() != unsubscribed.getId()) {
                assertTrue(opml.contains(feed.getDownloadUrl()));
            }
        }
        pressBack();

        File htmlTarget = exportTargetFile("subscriptions-filtered.html");
        stubCreateDocument(htmlTarget, "text/html", "antennapod-feeds");
        openImportExportScreen();
        clickPreference(R.string.html_export_label);
        waitForViewGlobally(withText(R.string.export_success_title), 15000);
        String html = readFile(htmlTarget);
        assertFalse(html.contains(unsubscribed.getTitle()));
        for (Feed feed : uiTestUtils.hostedFeeds) {
            if (feed.getId() != unsubscribed.getId()) {
                assertTrue(html.contains(feed.getTitle()));
            }
        }
    }

    @Test
    public void testFavoritesExportHandlesItemsWithoutLinkAndMedia() throws Exception {
        addFeedsToDatabase();
        List<FeedItem> items = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD);
        FeedItem withMedia = null;
        for (FeedItem item : items) {
            if (item.getMedia() != null && item.getMedia().getDownloadUrl() != null) {
                withMedia = item;
                break;
            }
        }
        assertTrue(withMedia != null);
        Feed feed = withMedia.getFeed();
        FeedItem withoutMedia = new FeedItem(0, "Favorite without link or media", "no-media-item",
                null, new Date(), FeedItem.UNPLAYED, feed);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setSingleFeedItem(withoutMedia);
        adapter.close();
        DBWriter.addFavoriteItems(Arrays.asList(withMedia, withoutMedia)).get();

        File target = exportTargetFile("favorites-missing-fields.html");
        stubCreateDocument(target, "text/html", "antennapod-favorites");

        openImportExportScreen();
        clickPreference(R.string.favorites_export_label);

        waitForViewGlobally(withText(R.string.export_success_title), 15000);
        String html = readFile(target);
        assertTrue(html.contains(withMedia.getTitle()));
        assertTrue(html.contains(withoutMedia.getTitle()));
        assertTrue(html.contains(withMedia.getMedia().getDownloadUrl()));
        assertFalse(html.contains("{FAV_WEBSITE}"));
        assertFalse(html.contains("{FAV_MEDIA}"));
    }

    private File writeOpmlFile(String name, String xml) throws IOException {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        File file = new File(dir, name);
        FileOutputStream out = new FileOutputStream(file);
        IOUtils.write(xml, out, StandardCharsets.UTF_8);
        out.close();
        return file;
    }

    private void stubGetContent(File file) {
        Intent data = new Intent();
        data.setData(Uri.fromFile(file));
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_OK, data));
    }

    @Test
    public void testOpmlImportAddsSelectedFeeds() throws Exception {
        Feed importFeedA = new Feed(0, null, "Imported feed A", "http://example.com/a", "Description",
                "http://example.com/pay/a", "author", "en", Feed.TYPE_RSS2, "importa", null, null,
                "http://example.com/a/src", System.currentTimeMillis());
        importFeedA.setItems(new ArrayList<>());
        String urlA = uiTestUtils.hostFeed(importFeedA);
        Feed importFeedB = new Feed(0, null, "Imported feed B", "http://example.com/b", "Description",
                "http://example.com/pay/b", "author", "en", Feed.TYPE_RSS2, "importb", null, null,
                "http://example.com/b/src", System.currentTimeMillis());
        importFeedB.setItems(new ArrayList<>());
        String urlB = uiTestUtils.hostFeed(importFeedB);

        String opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<opml version=\"2.0\"><head><title>Subscriptions</title></head><body>"
                + "<outline text=\"Imported feed A\" xmlUrl=\"" + urlA + "\" type=\"rss\"/>"
                + "<outline text=\"Imported feed B\" xmlUrl=\"" + urlB + "\" type=\"rss\"/>"
                + "<outline xmlUrl=\"" + urlB + "\" type=\"rss\"/>"
                + "<outline title=\"Title only feed\" xmlUrl=\"" + urlB + "\" type=\"rss\"/>"
                + "<outline text=\"Feed without a url\" type=\"rss\"/>"
                + "</body></opml>";
        File opmlFile = writeOpmlFile("import.opml", opml);
        stubGetContent(opmlFile);

        openImportExportScreen();
        clickPreference(R.string.opml_import_label);

        waitForViewGlobally(withText("Imported feed A"), 15000);
        onView(withText("Imported feed A")).perform(click());
        onView(withId(R.id.butConfirm)).perform(click());

        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> feedUrlExists(urlA));
        assertFalse(feedUrlExists(urlB));
    }

    private boolean feedUrlExists(String downloadUrl) {
        for (Feed feed : DBReader.getFeedList()) {
            if (downloadUrl.equals(feed.getDownloadUrl())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testOpmlImportWithBrokenFileShowsError() throws Exception {
        File opmlFile = writeOpmlFile("broken.opml", "this is not xml at all >>>");
        stubGetContent(opmlFile);

        openImportExportScreen();
        clickPreference(R.string.opml_import_label);

        String readerError = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.opml_reader_error);
        waitForViewGlobally(withText(containsString(readerError)), 15000);
        assertTrue(feedUrlsEmpty());
    }

    private boolean feedUrlsEmpty() {
        return DBReader.getFeedList().isEmpty();
    }

    @Test
    public void testOpmlImportWithCancelledPickerStaysOnScreen() throws Exception {
        Intent cancelled = new Intent();
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, cancelled));

        openImportExportScreen();
        clickPreference(R.string.opml_import_label);

        onView(withText(R.string.opml_import_label)).check(matches(isDisplayed()));
        assertTrue(feedUrlsEmpty());
    }
}

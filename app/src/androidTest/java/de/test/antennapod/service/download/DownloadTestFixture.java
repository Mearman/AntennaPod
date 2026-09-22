package de.test.antennapod.service.download;

import android.content.Context;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.service.download.HTTPBin;
import de.test.antennapod.util.syndication.feedgenerator.Rss2Generator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.awaitility.Awaitility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DownloadTestFixture {
    public static final String MEDIA_ASSET = "3sec.mp3";
    public static final String MIME_TYPE = "audio/mpeg";
    private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final HTTPBin server = new HTTPBin();
    private File hostedDir;

    public void setUp() throws IOException {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        hostedDir = new File(context.getFilesDir(), "test/DownloadTestFixture");
        FileUtils.deleteDirectory(hostedDir);
        if (!hostedDir.mkdirs()) {
            throw new IOException("Unable to create " + hostedDir);
        }
        server.start();
    }

    public void tearDown() throws IOException {
        server.stop();
        FileUtils.deleteDirectory(hostedDir);
        FileUtils.deleteQuietly(UserPreferences.getDataFolder("media"));
        FileUtils.deleteQuietly(UserPreferences.getDataFolder("cache"));
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
    }

    public HTTPBin server() {
        return server;
    }

    public Context context() {
        return context;
    }

    public File file(String name) {
        return new File(hostedDir, name);
    }

    public File newMediaFile(String name) throws IOException {
        File mediaFile = new File(hostedDir, name);
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(MEDIA_ASSET);
             FileOutputStream out = new FileOutputStream(mediaFile)) {
            IOUtils.copy(in, out);
        }
        return mediaFile;
    }

    public String hostFile(File file) {
        int id = server.serveFile(file);
        if (id == -1) {
            throw new IllegalStateException("Unable to host " + file);
        }
        return String.format(Locale.US, "%s/files/%d", server.getBaseUrl(), id);
    }

    public int fileId(File file) {
        return server.serveFile(file);
    }

    public int idOf(String hostedUrl) {
        return Integer.parseInt(hostedUrl.substring(hostedUrl.lastIndexOf('/') + 1));
    }

    public File hostedFile(String hostedUrl) {
        return server.accessFile(idOf(hostedUrl));
    }

    public List<HTTPBin.RecordedRequest> requestsFor(String hostedUrl) {
        return server.getRequestsForPrefix(hostedUrl.substring(server.getBaseUrl().length()));
    }

    public String hostText(String name, String content) throws IOException {
        File file = file(name);
        FileUtils.writeStringToFile(file, content, "UTF-8");
        return hostFile(file);
    }

    public String url(String path) {
        return server.getBaseUrl() + path;
    }

    public Feed newFeed(String title, int episodes) throws IOException {
        return newFeed(title, episodes, FeedItem.UNPLAYED);
    }

    public Feed newFeed(String title, int episodes, int playState) throws IOException {
        return newFeed(title, episodes, playState, DAY_MILLIS);
    }

    public Feed newFeed(String title, int episodes, int playState, long spacingMillis) throws IOException {
        Feed feed = new Feed(0, null, title, "http://example.com/" + title, "Description of " + title,
                null, "Author of " + title, "en", Feed.TYPE_RSS2, title + "-identifier", null, null, null, 0);
        List<FeedItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < episodes; i++) {
            FeedItem item = new FeedItem(0, title + " episode " + i, title + "-episode-" + i,
                    "http://example.com/" + title + "/" + i, new Date(now - i * spacingMillis), playState, feed);
            File mediaFile = newMediaFile(title + "-episode-" + i + ".mp3");
            item.setMedia(new FeedMedia(item, hostFile(mediaFile), mediaFile.length(), MIME_TYPE));
            items.add(item);
        }
        feed.setItems(items);
        return feed;
    }

    public String hostFeed(Feed feed) throws IOException {
        File feedFile = new File(hostedDir, "feed-" + feed.getTitle() + ".xml");
        try (FileOutputStream out = new FileOutputStream(feedFile)) {
            new Rss2Generator().writeFeed(feed, out, "UTF-8", Rss2Generator.FEATURE_WRITE_GUID);
        }
        return hostFile(feedFile);
    }

    public Feed subscribe(Feed feed) {
        Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
        return DBReader.getFeed(saved.getId(), false, 0, Integer.MAX_VALUE);
    }

    public Feed subscribe(String title, int episodes) throws IOException {
        return subscribe(title, episodes, FeedItem.UNPLAYED);
    }

    public Feed subscribe(String title, int episodes, int playState) throws IOException {
        Feed feed = newFeed(title, episodes, playState);
        feed.setDownloadUrl(hostFeed(feed));
        return subscribe(feed);
    }

    public void markDownloaded(FeedItem item) throws Exception {
        FeedMedia media = DBReader.getFeedMedia(item.getMedia().getId());
        File downloaded = file("downloaded-" + media.getId() + ".mp3");
        FileUtils.writeByteArrayToFile(downloaded, new byte[] {1, 2, 3});
        media.setLocalFileUrl(downloaded.getAbsolutePath());
        media.setDownloaded(true, System.currentTimeMillis());
        DBWriter.setFeedMedia(media).get();
    }

    public void setDurationAndSize(FeedItem item, int duration, long size) throws Exception {
        FeedMedia media = DBReader.getFeedMedia(item.getMedia().getId());
        media.setDuration(duration);
        media.setSize(size);
        DBWriter.setFeedMedia(media).get();
    }

    public void markPlayed(FeedItem item, long playedAtMillis) throws Exception {
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(item)).get();
        DBWriter.addItemToPlaybackHistory(item.getMedia(), new Date(playedAtMillis)).get();
    }

    public static FeedMedia reload(FeedMedia media) {
        return DBReader.getFeedMedia(media.getId());
    }

    public static void awaitDownloaded(FeedMedia media) {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> reload(media).isDownloaded());
    }

    public static void putBoolean(String key, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit().putBoolean(key, value).commit();
    }

    public static void putString(String key, String value) {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit().putString(key, value).commit();
    }
}

package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.parser.feed.FeedHandler;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public abstract class ImportExportPipelineTestBase {
    protected Context context;
    private int documentCounter = 0;

    @Before
    public void setUpDatabase() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.tearDownTests();
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
    }

    @After
    public void closeDatabase() {
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    protected static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    protected Feed subscribe(String downloadUrl, String title, String website, String imageUrl,
                             String... episodeTitles) throws Exception {
        return store(downloadUrl, title, website, imageUrl, Feed.STATE_SUBSCRIBED, episodeTitles);
    }

    protected Feed store(String downloadUrl, String title, String website, String imageUrl, int state,
                         String... episodeTitles) throws Exception {
        StringBuilder document = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rss version=\"2.0\"><channel><title>" + escapeXml(title) + "</title>"
                + "<link>" + website + "</link><image><url>" + imageUrl + "</url></image>\n");
        for (int i = 0; i < episodeTitles.length; i++) {
            document.append("<item><guid>").append(downloadUrl).append('#').append(i).append("</guid><title>")
                    .append(escapeXml(episodeTitles[i])).append("</title><link>").append(website).append("/episode-")
                    .append(i).append("</link><pubDate>Mon, 0").append(i + 1)
                    .append(" Jan 2006 15:04:05 +0000</pubDate><enclosure url=\"").append(downloadUrl)
                    .append("/episode-").append(i)
                    .append(".mp3\" length=\"5000000\" type=\"audio/mpeg\"/></item>\n");
        }
        document.append("</channel></rss>\n");
        Feed feed = new Feed(downloadUrl, null);
        feed.setLocalFileUrl(writeDocument(document.toString()).getAbsolutePath());
        Feed parsed = new FeedHandler().parseFeed(feed).feed;
        parsed.setState(state);
        Feed stored = FeedDatabaseWriter.updateFeed(context, parsed, false);
        DBWriter.tearDownTests();
        return DBReader.getFeed(stored.getId(), false, 0, Integer.MAX_VALUE);
    }

    protected List<Feed> storedFeeds() {
        return DBReader.getFeedList();
    }

    protected void reopenDatabase() {
        PodDBAdapter.tearDownTests();
    }

    protected void resetDatabase() {
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    private File writeDocument(String document) throws IOException {
        documentCounter++;
        File file = new File(context.getCacheDir(), "import-export-feed-" + documentCounter + ".xml");
        Files.write(file.toPath(), document.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}

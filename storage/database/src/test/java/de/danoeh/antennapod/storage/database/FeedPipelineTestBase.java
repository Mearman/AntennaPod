package de.danoeh.antennapod.storage.database;

import android.content.Context;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.parser.feed.FeedHandler;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public abstract class FeedPipelineTestBase {
    protected static final String FEED_URL = "https://example.com/feed.xml";
    private static final String RSS_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rss version=\"2.0\""
            + " xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\""
            + " xmlns:media=\"http://search.yahoo.com/mrss/\""
            + " xmlns:content=\"http://purl.org/rss/1.0/modules/content/\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
            + " xmlns:podcast=\"https://podcastindex.org/namespace/1.0\""
            + " xmlns:psc=\"http://podlove.org/simple-chapters\""
            + " xmlns:atom=\"http://www.w3.org/2005/Atom\">\n";

    protected Context context;
    private int documentCounter = 0;

    @Before
    public void setUpDatabase() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        AutoDownloadManager.setInstance(new NoOpAutoDownloadManager());
    }

    protected static String rss(String channelBody) {
        return RSS_HEADER + "<channel>\n" + channelBody + "\n</channel>\n</rss>\n";
    }

    protected FeedHandlerResult parse(String document) throws Exception {
        return parse(document, StandardCharsets.UTF_8);
    }

    protected FeedHandlerResult parse(String document, Charset charset) throws Exception {
        Feed feed = new Feed(FEED_URL, null);
        feed.setLocalFileUrl(writeDocument(document, charset).getAbsolutePath());
        return new FeedHandler().parseFeed(feed);
    }

    protected Feed parseAndStore(String document) throws Exception {
        return storeParsed(parse(document).feed);
    }

    protected Feed storeParsed(Feed parsed) {
        Feed stored = FeedDatabaseWriter.updateFeed(context, parsed, false);
        return DBReader.getFeed(stored.getId(), false, 0, Integer.MAX_VALUE);
    }

    protected Feed refresh(String document) throws Exception {
        return refresh(document, false);
    }

    protected Feed refresh(String document, boolean removeUnlistedItems) throws Exception {
        Feed updated = FeedDatabaseWriter.updateFeed(context, parse(document).feed, removeUnlistedItems);
        DBWriter.tearDownTests();
        return reload(updated);
    }

    protected Feed reload(Feed feed) {
        return DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
    }

    protected List<FeedItem> storedItems(Feed feed) {
        return DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0,
                Integer.MAX_VALUE);
    }

    protected FeedItem storedItem(Feed feed, String itemIdentifier) {
        for (FeedItem item : storedItems(feed)) {
            if (itemIdentifier.equals(item.getItemIdentifier())) {
                return item;
            }
        }
        throw new AssertionError("No stored item with identifier " + itemIdentifier);
    }

    private File writeDocument(String document, Charset charset) throws IOException {
        documentCounter++;
        File file = new File(context.getCacheDir(), "pipeline-feed-" + documentCounter + ".xml");
        Files.write(file.toPath(), document.getBytes(charset));
        return file;
    }

    private static class NoOpAutoDownloadManager extends AutoDownloadManager {
        @Override
        public Future<?> autodownloadUndownloadedItems(Context context) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void performAutoCleanup(Context context) {
        }
    }
}

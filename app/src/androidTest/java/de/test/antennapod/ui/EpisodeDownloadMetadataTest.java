package de.test.antennapod.ui;

import android.content.Intent;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.download.DownloadError;
import de.danoeh.antennapod.model.download.DownloadResult;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.chapters.ChapterUtils;
import de.danoeh.antennapod.ui.screen.AddFeedFragment;
import de.test.antennapod.EspressoTestUtils;
import de.test.antennapod.util.TestAssets;
import de.test.antennapod.util.media.MediaFixtures;
import de.test.antennapod.util.media.MediaFixtures.ChapterSpec;
import de.test.antennapod.util.service.download.StaticContentServer;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.test.antennapod.EspressoTestUtils.clickChildViewWithId;
import static de.test.antennapod.ui.FeedRobot.waitUntilDisplayed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EpisodeDownloadMetadataTest {
    private static final String FEED_PATH = "/feeds/media.xml";
    private static final String MIDDLE_LINK = "https://example.com/middle";
    private static final String MIDDLE_IMAGE = "https://example.com/middle.jpg";

    private static final List<ChapterSpec> CHAPTERS = Arrays.asList(
            new ChapterSpec(0, "Introduction"),
            new ChapterSpec(30000, "Middle part", MIDDLE_LINK, MIDDLE_IMAGE),
            new ChapterSpec(60000, "Closing"));

    private StaticContentServer server;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        EspressoTestUtils.clearPreferences();
        EspressoTestUtils.clearDatabase();
        UserPreferences.setAllowMobileFeedRefresh(true);
        UserPreferences.setAllowMobileEpisodeDownload(true);
        EspressoTestUtils.setLaunchScreen(AddFeedFragment.TAG);
        server = new StaticContentServer();
        server.start();
        activityRule.launchActivity(new Intent());
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        for (Feed candidate : DBReader.getFeedList()) {
            Feed feed = DBReader.getFeed(candidate.getId(), false, 0, Integer.MAX_VALUE);
            for (FeedItem item : feed.getItems()) {
                if (item.hasMedia() && item.getMedia().getLocalFileUrl() != null) {
                    FileUtils.deleteQuietly(new File(item.getMedia().getLocalFileUrl()));
                    FileUtils.deleteQuietly(new File(item.getMedia().getTranscriptFileUrl()));
                }
            }
        }
        PodDBAdapter.deleteDatabase();
    }

    private static class Episode {
        final String guid;
        final String title;
        final String mime;
        final byte[] content;
        final String extraTags;

        Episode(String guid, String title, String mime, byte[] content, String extraTags) {
            this.guid = guid;
            this.title = title;
            this.mime = mime;
            this.content = content;
            this.extraTags = extraTags;
        }
    }

    private static Episode episode(String guid, String mime, byte[] content) {
        return new Episode(guid, "Episode " + guid, mime, content, "");
    }

    private String publishFeed(Episode... episodes) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\" xmlns:podcast=\"https://podcastindex.org/namespace/1.0\"><channel>"
                + "<title>Media Feed</title><link>https://example.com/media</link>");
        int day = 10;
        for (Episode episode : episodes) {
            String path = "/media/" + episode.guid + ".bin";
            server.publish(path, episode.mime, episode.content);
            xml.append("<item><guid>").append(episode.guid).append("</guid><title>").append(episode.title)
                    .append("</title><pubDate>").append(day--).append(" Jan 2023 10:00:00 +0000</pubDate>")
                    .append("<enclosure url=\"").append(server.getBaseUrl()).append(path).append("\" length=\"")
                    .append(episode.content.length).append("\" type=\"").append(episode.mime).append("\"/>")
                    .append(episode.extraTags).append("</item>");
        }
        xml.append("</channel></rss>");
        return server.publish(FEED_PATH, "application/rss+xml", xml.toString());
    }

    private Feed subscribe(String url) {
        FeedRobot.subscribeByUrl(url);
        return FeedRobot.awaitFeed(url);
    }

    private FeedItem downloadEpisode(Feed feed, Episode episode) {
        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText(episode.title)), clickChildViewWithId(R.id.secondaryActionButton)));
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> {
            List<DownloadResult> log = DBReader.getDownloadLog();
            return !log.isEmpty() && log.get(0).isSuccessful();
        });
        return FeedRobot.itemByGuid(FeedRobot.reload(feed), episode.guid);
    }

    private List<Chapter> storedChapters(FeedItem item) {
        assertTrue(item.hasChapters());
        List<Chapter> chapters = DBReader.loadChaptersOfFeedItem(item);
        assertNotNull(chapters);
        return chapters;
    }

    private void assertStandardChapters(List<Chapter> chapters) {
        assertEquals(3, chapters.size());
        assertEquals("Introduction", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("Middle part", chapters.get(1).getTitle());
        assertEquals(30000, chapters.get(1).getStart());
        assertEquals("Closing", chapters.get(2).getTitle());
        assertEquals(60000, chapters.get(2).getStart());
    }

    @Test
    public void downloadedFileIsStoredWithItsRealSize() throws Exception {
        byte[] audio = MediaFixtures.plainAudio();
        Episode episode = episode("plain", "audio/mpeg", audio);
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        File file = new File(item.getMedia().getLocalFileUrl());
        assertTrue(file.exists());
        assertEquals(audio.length, file.length());
        assertEquals(audio.length, item.getMedia().getSize());
        assertTrue(item.getMedia().getDuration() > 0);
        assertFalse(item.hasChapters());
    }

    @Test
    public void id3ChaptersOfVersion23AreStoredAfterDownload() throws Exception {
        Episode episode = episode("id3v23", "audio/mpeg", MediaFixtures.mp3WithChapters(3, CHAPTERS, null));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        List<Chapter> chapters = storedChapters(item);
        assertStandardChapters(chapters);
        assertEquals(MIDDLE_LINK, chapters.get(1).getLink());
        assertEquals(MIDDLE_IMAGE, chapters.get(1).getImageUrl());
        assertNull(chapters.get(0).getLink());
    }

    @Test
    public void id3ChaptersOfVersion24AreStoredAfterDownload() throws Exception {
        Episode episode = episode("id3v24", "audio/mpeg", MediaFixtures.mp3WithChapters(4, CHAPTERS, null));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        List<Chapter> chapters = storedChapters(item);
        assertStandardChapters(chapters);
        assertEquals(MIDDLE_LINK, chapters.get(1).getLink());
    }

    @Test
    public void id3ChaptersWithoutTitleAreNumbered() throws Exception {
        List<ChapterSpec> untitled = Arrays.asList(
                new ChapterSpec(0, "Named"), new ChapterSpec(10000, null), new ChapterSpec(20000, null));
        Episode episode = episode("untitled", "audio/mpeg", MediaFixtures.mp3WithChapters(3, untitled, null));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        List<Chapter> chapters = storedChapters(item);
        assertEquals("Named", chapters.get(0).getTitle());
        assertEquals("1", chapters.get(1).getTitle());
        assertEquals("2", chapters.get(2).getTitle());
    }

    @Test
    public void embeddedChapterPictureIsStoredAsAnOffsetIntoTheDownloadedFile() throws Exception {
        byte[] picture = MediaFixtures.redPng();
        List<ChapterSpec> chapters = Arrays.asList(
                new ChapterSpec(0, "Without picture"), new ChapterSpec(30000, "With picture", picture));
        Episode episode = episode("embedded", "audio/mpeg", MediaFixtures.mp3WithChapters(3, chapters, null));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        List<Chapter> stored = storedChapters(item);
        assertNull(stored.get(0).getImageUrl());
        byte[] file = FileUtils.readFileToByteArray(new File(item.getMedia().getLocalFileUrl()));
        int offset = MediaFixtures.indexOf(file, picture);
        assertEquals(EmbeddedChapterImage.makeUrl(offset, picture.length), stored.get(1).getImageUrl());
        assertTrue(offset > 0);
    }

    @Test
    public void oggVorbisChaptersAreStoredAfterDownload() throws Exception {
        List<String> comments = new ArrayList<>(Arrays.asList("TITLE=Ogg episode", "ARTIST=Someone"));
        comments.addAll(MediaFixtures.vorbisChapterComments(CHAPTERS));
        Episode episode = episode("vorbis", "audio/ogg", MediaFixtures.oggVorbis(comments));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        List<Chapter> chapters = storedChapters(item);
        assertStandardChapters(chapters);
        assertEquals(MIDDLE_LINK, chapters.get(1).getLink());
    }

    @Test
    public void opusChaptersAreStoredAfterDownload() throws Exception {
        Episode episode = episode("opus", "audio/opus",
                MediaFixtures.oggOpus(MediaFixtures.vorbisChapterComments(CHAPTERS)));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertStandardChapters(storedChapters(item));
    }

    @Test
    public void flacChaptersAreStoredAfterDownload() throws Exception {
        Episode episode = episode("flac", "audio/flac",
                MediaFixtures.flac(MediaFixtures.vorbisChapterComments(CHAPTERS)));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertStandardChapters(storedChapters(item));
    }

    @Test
    public void flacInsideOggChaptersAreStoredAfterDownload() throws Exception {
        Episode episode = episode("flacogg", "audio/ogg",
                MediaFixtures.flacInOgg(MediaFixtures.vorbisChapterComments(CHAPTERS)));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertStandardChapters(storedChapters(item));
    }

    @Test
    public void neroChaptersOfM4aAreStoredAfterDownload() throws Exception {
        Episode episode = episode("m4a", "audio/mp4", MediaFixtures.m4aWithChapters(CHAPTERS));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertStandardChapters(storedChapters(item));
    }

    @Test
    public void chaptersAreFoundWhenTheMimeTypeIsMisleading() throws Exception {
        Episode episode = episode("mislabelled", "application/octet-stream",
                MediaFixtures.mp3WithChapters(3, CHAPTERS, null));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertStandardChapters(storedChapters(item));
    }

    @Test
    public void duplicateVorbisChapterNumbersAreRejected() throws Exception {
        List<String> comments = Arrays.asList("CHAPTER000=00:00:00.000", "CHAPTER000NAME=First",
                "CHAPTER000=00:00:10.000");
        Episode episode = episode("duplicate", "audio/ogg", MediaFixtures.oggVorbis(comments));
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertNull(DBReader.loadChaptersOfFeedItem(item));
    }

    @Test
    public void id3TagWithoutChapterFramesYieldsNoChapters() throws Exception {
        Episode episode = episode("padded", "audio/mpeg", MediaFixtures.mp3WithPaddedTagWithoutChapters());
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        assertNull(DBReader.loadChaptersOfFeedItem(item));
        assertTrue(new File(item.getMedia().getLocalFileUrl()).exists());
    }

    @Test
    public void transcriptIsStoredNextToTheDownloadedFile() throws Exception {
        String transcript = TestAssets.readText("transcripts/ep1.vtt");
        server.publish("/transcripts/episode.vtt", "text/vtt", transcript);
        String tag = "<podcast:transcript url=\"" + server.getBaseUrl() + "/transcripts/episode.vtt\""
                + " type=\"text/vtt\"/>";
        Episode episode = new Episode("transcribed", "Episode transcribed", "audio/mpeg",
                MediaFixtures.plainAudio(), tag);
        Feed feed = subscribe(publishFeed(episode));

        FeedItem item = downloadEpisode(feed, episode);

        File transcriptFile = new File(item.getMedia().getTranscriptFileUrl());
        assertTrue(transcriptFile.exists());
        assertEquals(transcript, FileUtils.readFileToString(transcriptFile, "UTF-8"));
        assertEquals(1, server.requestsFor("/transcripts/episode.vtt").size());
    }

    @Test
    public void chaptersFromTheFeedUrlAreFetchedDuringDownload() throws Exception {
        String chaptersUrl = server.getBaseUrl() + "/chapters/episode.json";
        server.publish("/chapters/episode.json", "application/json", TestAssets.readText("chapters/ep1.json"));
        String tag = "<podcast:chapters url=\"" + chaptersUrl + "\""
                + " type=\"application/json+chapters\"/>";
        Episode episode = new Episode("webchapters", "Episode webchapters", "audio/mpeg",
                MediaFixtures.plainAudio(), tag);
        Feed feed = subscribe(publishFeed(episode));

        downloadEpisode(feed, episode);

        FeedRobot.awaitCondition(() -> !server.requestsFor("/chapters/episode.json").isEmpty());
        List<Chapter> chapters = ChapterUtils.loadChaptersFromUrl(chaptersUrl, false);
        assertEquals(3, chapters.size());
        assertEquals("Cold open", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("Interview", chapters.get(1).getTitle());
        assertEquals(95000, chapters.get(1).getStart());
        assertEquals("https://example.com/interview", chapters.get(1).getLink());
        assertEquals("https://example.com/interview.jpg", chapters.get(1).getImageUrl());
        assertEquals("Credits", chapters.get(2).getTitle());
        assertEquals(3600000, chapters.get(2).getStart());
        assertEquals(1, server.requestsFor("/chapters/episode.json").size());
    }

    @Test
    public void announcedSizeIsReplacedByTheSizeOfTheDownloadedFile() throws Exception {
        byte[] audio = MediaFixtures.plainAudio();
        String path = "/media/short.mp3";
        server.publish(path, "audio/mpeg", audio);
        String feedXml = "<rss version=\"2.0\"><channel><title>Wrong Size</title><item><guid>short</guid>"
                + "<title>Short episode</title><pubDate>10 Jan 2023 10:00:00 +0000</pubDate>"
                + "<enclosure url=\"" + server.getBaseUrl() + path + "\" length=\"" + (audio.length * 2)
                + "\" type=\"audio/mpeg\"/></item></channel></rss>";
        String url = server.publish("/feeds/wrong-size.xml", "application/rss+xml", feedXml);
        Feed feed = subscribe(url);
        assertEquals(audio.length * 2, FeedRobot.itemByGuid(feed, "short").getMedia().getSize());

        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText("Short episode")), clickChildViewWithId(R.id.secondaryActionButton)));

        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() ->
                FeedRobot.itemByGuid(FeedRobot.reload(feed), "short").getMedia().isDownloaded());
        assertEquals(audio.length, FeedRobot.itemByGuid(FeedRobot.reload(feed), "short").getMedia().getSize());
    }

    @Test
    public void missingMediaFileIsReportedAsNotFound() throws Exception {
        Episode episode = episode("missing", "audio/mpeg", MediaFixtures.plainAudio());
        Feed feed = subscribe(publishFeed(episode));
        server.remove("/media/missing.bin");

        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText(episode.title)), clickChildViewWithId(R.id.secondaryActionButton)));

        assertEquals(DownloadError.ERROR_NOT_FOUND, awaitFailedDownload().getReason());
        assertFalse(FeedRobot.itemByGuid(FeedRobot.reload(feed), "missing").getMedia().isDownloaded());
    }

    @Test
    public void textResponseInsteadOfMediaIsRejected() throws Exception {
        Episode episode = episode("text", "text/html", "<html>Not audio</html>".getBytes());
        subscribe(publishFeed(episode));

        onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.actionOnItem(
                hasDescendant(withText(episode.title)), clickChildViewWithId(R.id.secondaryActionButton)));

        assertEquals(DownloadError.ERROR_FILE_TYPE, awaitFailedDownload().getReason());
    }

    private DownloadResult awaitFailedDownload() {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> !DBReader.getDownloadLog().isEmpty());
        DownloadResult result = DBReader.getDownloadLog().get(0);
        assertFalse(result.isSuccessful());
        waitUntilDisplayed(withId(R.id.recyclerView), FeedRobot.UI_TIMEOUT_MS);
        return result;
    }
}

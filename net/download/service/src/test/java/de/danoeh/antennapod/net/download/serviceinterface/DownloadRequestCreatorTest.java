package de.danoeh.antennapod.net.download.serviceinterface;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.service.DownloadIntegrationTestBase;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.test.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class DownloadRequestCreatorTest extends DownloadIntegrationTestBase {
    private static final int MD5_HEX_LENGTH = 32;
    private static final int TRUNCATED_BASE_NAME_LENGTH = 220;

    private FeedItem saveEpisodeItem(String feedTitle, String episodeTitle, String mediaUrl) {
        Feed unsaved = newFeed(feedTitle, server.url("/feed.xml").toString());
        FeedItem item = new FeedItem(0, episodeTitle, "guid", "link", new Date(), FeedItem.NEW, unsaved);
        item.setMedia(new FeedMedia(item, mediaUrl, 0, "audio/mpeg"));
        unsaved.getItems().add(item);
        saveFeed(unsaved);
        return DBReader.getFeedItem(item.getId());
    }

    private void storeCredentials(Feed feed, String username, String password) {
        FeedPreferences preferences = feed.getPreferences();
        preferences.setUsername(username);
        preferences.setPassword(password);
        DBWriter.setFeedPreferences(preferences);
        DBWriter.tearDownTests();
    }

    @Test
    public void feedRequestTargetsCacheFolderWithSanitisedTitleAndId() {
        Feed feed = saveFeed(newFeed("Tech: News & More!", "https://example.com/feed.xml"));

        DownloadRequest request = DownloadRequestCreator.create(feed).build();

        File expected = new File(UserPreferences.getDataFolder("cache/"), "feed-Tech News More" + feed.getId());
        assertEquals(expected.getAbsolutePath(), new File(request.getDestination()).getAbsolutePath());
        assertEquals(Feed.FEEDFILETYPE_FEED, request.getFeedfileType());
        assertEquals(feed.getId(), request.getFeedfileId());
        assertEquals("https://example.com/feed.xml", request.getSource());
    }

    @Test
    public void feedRequestDeletesLeftoverFileAtDestination() throws Exception {
        Feed feed = saveFeed(newFeed("Leftover", "https://example.com/feed.xml"));
        File leftover = new File(DownloadRequestCreator.create(feed).build().getDestination());
        assertTrue(leftover.createNewFile());

        DownloadRequestCreator.create(feed);

        assertFalse(leftover.exists());
    }

    @Test
    public void feedRequestCarriesStoredCredentialsAndLastModified() {
        Feed unsaved = newFeed("Private", "https://example.com/feed.xml");
        unsaved.setLastModified("\"etag-1\"");
        Feed feed = saveFeed(unsaved);
        storeCredentials(feed, "user", "secret");
        Feed reloaded = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);

        DownloadRequest request = DownloadRequestCreator.create(reloaded).build();

        assertEquals("user", request.getUsername());
        assertEquals("secret", request.getPassword());
        assertEquals("\"etag-1\"", request.getLastModified());
    }

    @Test
    public void feedRequestWithoutStoredCredentialsHasNone() {
        Feed feed = saveFeed(newFeed("Public", "https://example.com/feed.xml"));

        DownloadRequest request = DownloadRequestCreator.create(feed).build();

        assertNull(request.getUsername());
        assertNull(request.getPassword());
    }

    @Test
    public void mediaRequestTargetsFolderOfFeedWithTitleIdAndExtension() {
        FeedItem item = saveEpisodeItem("My Podcast", "Episode: One?", "https://example.com/audio/file.mp3");

        DownloadRequest request = DownloadRequestCreator.create(item.getMedia()).build();

        File expected = new File(UserPreferences.getDataFolder("media/My Podcast"),
                "Episode One." + item.getMedia().getId() + ".mp3");
        assertEquals(expected.getAbsolutePath(), new File(request.getDestination()).getAbsolutePath());
        assertEquals(FeedMedia.FEEDFILETYPE_FEEDMEDIA, request.getFeedfileType());
        assertEquals(item.getMedia().getId(), request.getFeedfileId());
        assertEquals("https://example.com/audio/file.mp3", request.getSource());
    }

    @Test
    public void mediaRequestFallsBackToUrlFileNameWhenTitleHasNoUsableCharacters() {
        FeedItem item = saveEpisodeItem("My Podcast", "???", "https://example.com/audio/original.mp3");

        DownloadRequest request = DownloadRequestCreator.create(item.getMedia()).build();

        String name = new File(request.getDestination()).getName();
        assertTrue(name, name.endsWith("." + item.getMedia().getId() + ".mp3"));
        assertFalse(name, name.startsWith("?"));
    }

    @Test
    public void mediaRequestPicksUnusedFileNameWhenTargetAlreadyExists() throws Exception {
        FeedItem item = saveEpisodeItem("My Podcast", "Episode One", "https://example.com/file.mp3");
        File first = new File(DownloadRequestCreator.create(item.getMedia()).build().getDestination());
        assertTrue(first.createNewFile());

        File second = new File(DownloadRequestCreator.create(item.getMedia()).build().getDestination());

        assertFalse(first.equals(second));
        assertEquals("Episode One." + item.getMedia().getId() + "-1.mp3", second.getName());
        assertEquals(first.getParentFile(), second.getParentFile());
    }

    @Test
    public void mediaRequestSkipsNamesThatAreAlreadyTaken() throws Exception {
        FeedItem item = saveEpisodeItem("My Podcast", "Episode One", "https://example.com/file.mp3");
        File first = new File(DownloadRequestCreator.create(item.getMedia()).build().getDestination());
        assertTrue(first.createNewFile());
        File second = new File(DownloadRequestCreator.create(item.getMedia()).build().getDestination());
        assertTrue(second.createNewFile());

        File third = new File(DownloadRequestCreator.create(item.getMedia()).build().getDestination());

        assertEquals("Episode One." + item.getMedia().getId() + "-2.mp3", third.getName());
    }

    @Test
    public void mediaRequestResumesPartiallyDownloadedFile() throws Exception {
        FeedItem item = saveEpisodeItem("My Podcast", "Episode One", "https://example.com/file.mp3");
        File partial = temporaryFolder.newFile("partial.mp3");
        item.getMedia().setLocalFileUrl(partial.getAbsolutePath());

        DownloadRequest request = DownloadRequestCreator.create(item.getMedia()).build();

        assertEquals(partial.getAbsolutePath(), request.getDestination());
    }

    @Test
    public void mediaRequestIgnoresLocalFileUrlOfMissingFile() {
        FeedItem item = saveEpisodeItem("My Podcast", "Episode One", "https://example.com/file.mp3");
        item.getMedia().setLocalFileUrl(new File(temporaryFolder.getRoot(), "gone.mp3").getAbsolutePath());

        DownloadRequest request = DownloadRequestCreator.create(item.getMedia()).build();

        assertEquals("Episode One." + item.getMedia().getId() + ".mp3", new File(request.getDestination()).getName());
    }

    @Test
    public void mediaRequestTruncatesVeryLongTitles() {
        String longTitle = "a".repeat(400);
        FeedItem item = saveEpisodeItem("My Podcast", longTitle, "https://example.com/file.mp3");

        DownloadRequest request = DownloadRequestCreator.create(item.getMedia()).build();

        String name = new File(request.getDestination()).getName();
        String suffix = "." + item.getMedia().getId() + ".mp3";
        assertTrue(name, name.endsWith(suffix));
        String base = name.substring(0, name.length() - suffix.length());
        assertEquals(TRUNCATED_BASE_NAME_LENGTH, base.length());
        assertTrue(base, base.startsWith("a".repeat(FileNameGenerator.MAX_FILENAME_LENGTH - MD5_HEX_LENGTH - 1) + "_"));
    }

    @Test
    public void mediaRequestCarriesCredentialsOfItsFeed() {
        Feed unsaved = newFeed("Private", "https://example.com/feed.xml");
        FeedItem item = new FeedItem(0, "Episode", "guid", "link", new Date(), FeedItem.NEW, unsaved);
        item.setMedia(new FeedMedia(item, "https://example.com/file.mp3", 0, "audio/mpeg"));
        unsaved.getItems().add(item);
        Feed feed = saveFeed(unsaved);
        storeCredentials(feed, "user", "secret");
        FeedItem reloaded = DBReader.getFeedItem(item.getId());

        DownloadRequest request = DownloadRequestCreator.create(reloaded.getMedia()).build();

        assertEquals("user", request.getUsername());
        assertEquals("secret", request.getPassword());
    }
}

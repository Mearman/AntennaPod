package de.test.antennapod.net;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.common.RedirectChecker;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.test.antennapod.service.download.DownloadTestFixture;
import de.test.antennapod.util.PlatformNetwork;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class NetworkHelpersTest {
    private static final int UNUSED_PORT_URL_ID = 12345;

    private final DownloadTestFixture fixture = new DownloadTestFixture();
    private String mediaUrl;

    @Before
    public void setUp() throws Exception {
        fixture.setUp();
        File media = fixture.newMediaFile("redirected.mp3");
        mediaUrl = fixture.hostFile(media);
    }

    @After
    public void tearDown() throws Exception {
        fixture.tearDown();
        PlatformNetwork.activateUnmeteredWifiNetwork();
    }

    private void allowMobile(boolean feedRefresh, boolean episodeDownload, boolean streaming, boolean images) {
        UserPreferences.setAllowMobileFeedRefresh(feedRefresh);
        UserPreferences.setAllowMobileEpisodeDownload(episodeDownload);
        UserPreferences.setAllowMobileStreaming(streaming);
        UserPreferences.setAllowMobileImages(images);
    }

    @Test
    public void networkAvailabilityReportsTrueWhileTheDeviceIsTransferringData() throws Exception {
        OkHttpClient client = new OkHttpClient();

        try (Response response = client.newCall(new Request.Builder().url(mediaUrl).build()).execute()) {
            assertTrue(response.isSuccessful());
            assertEquals(fixture.hostedFile(mediaUrl).length(), response.body().contentLength());
        }

        assertTrue(NetworkUtils.networkAvailable());
    }

    @Test
    public void networkRestrictionFollowsTheMeteredAndCellularStateOfThePlatform() throws Exception {
        PlatformNetwork.assumeNetworkStateCanBeSwitched();
        PlatformNetwork.activateUnmeteredWifiNetwork();
        assertFalse(NetworkUtils.isNetworkRestricted());
        assertFalse(NetworkUtils.isVpnOverWifi());

        PlatformNetwork.activateMeteredCellularNetwork();
        assertTrue(NetworkUtils.isNetworkRestricted());
    }

    @Test
    public void everyTransferIsAllowedWhenMobileDataIsAllowedForAllOfThem() {
        allowMobile(true, true, true, true);

        assertTrue(NetworkUtils.isFeedRefreshAllowed());
        assertTrue(NetworkUtils.isEpisodeDownloadAllowed());
        assertTrue(NetworkUtils.isStreamingAllowed());
        assertTrue(NetworkUtils.isImageAllowed());
        assertTrue(NetworkUtils.isEpisodeHeadDownloadAllowed());
    }

    @Test
    public void mobileDataSettingsAllowOnlyTheirOwnTransferOnARestrictedNetwork() throws Exception {
        PlatformNetwork.assumeNetworkStateCanBeSwitched();
        PlatformNetwork.activateMeteredCellularNetwork();

        allowMobile(true, false, false, false);
        assertTrue(NetworkUtils.isFeedRefreshAllowed());
        assertFalse(NetworkUtils.isEpisodeDownloadAllowed());
        assertFalse(NetworkUtils.isStreamingAllowed());
        assertFalse(NetworkUtils.isImageAllowed());
        assertFalse(NetworkUtils.isEpisodeHeadDownloadAllowed());

        allowMobile(false, true, false, false);
        assertFalse(NetworkUtils.isFeedRefreshAllowed());
        assertTrue(NetworkUtils.isEpisodeDownloadAllowed());
        assertFalse(NetworkUtils.isStreamingAllowed());
        assertFalse(NetworkUtils.isImageAllowed());

        allowMobile(false, false, true, false);
        assertFalse(NetworkUtils.isFeedRefreshAllowed());
        assertFalse(NetworkUtils.isEpisodeDownloadAllowed());
        assertTrue(NetworkUtils.isStreamingAllowed());
        assertFalse(NetworkUtils.isImageAllowed());

        allowMobile(false, false, false, true);
        assertFalse(NetworkUtils.isFeedRefreshAllowed());
        assertFalse(NetworkUtils.isEpisodeDownloadAllowed());
        assertFalse(NetworkUtils.isStreamingAllowed());
        assertTrue(NetworkUtils.isImageAllowed());
        assertTrue(NetworkUtils.isEpisodeHeadDownloadAllowed());

        PlatformNetwork.activateUnmeteredWifiNetwork();
        assertTrue(NetworkUtils.isFeedRefreshAllowed());
        assertTrue(NetworkUtils.isEpisodeDownloadAllowed());
        assertTrue(NetworkUtils.isStreamingAllowed());
        assertTrue(NetworkUtils.isImageAllowed());
        assertTrue(NetworkUtils.isEpisodeHeadDownloadAllowed());
    }

    @Test
    public void blockedDownloadsAreRecognisedByTheLocalAddressInTheError() {
        assertTrue(NetworkUtils.wasDownloadBlocked(new IOException("Failed to connect to /127.0.0.1:8080")));
        assertTrue(NetworkUtils.wasDownloadBlocked(new IOException("Unable to reach 0.0.0.0")));
        assertTrue(NetworkUtils.wasDownloadBlocked(new IOException("outer",
                new IOException("Failed to connect to /127.0.0.1:1"))));
        assertFalse(NetworkUtils.wasDownloadBlocked(new IOException("Failed to connect to /93.184.216.34:80")));
        assertFalse(NetworkUtils.wasDownloadBlocked(new IOException("no address in this message")));
        assertFalse(NetworkUtils.wasDownloadBlocked(new IOException()));
    }

    @Test
    public void permanentRedirectIsDetectedAndReturnsTheNewUrl() {
        String redirecting = fixture.url("/moved/301/" + fixture.idOf(mediaUrl));

        assertEquals(mediaUrl, RedirectChecker.getNewUrlIfPermanentRedirect(redirecting));
    }

    @Test
    public void temporaryRedirectIsNotTreatedAsPermanent() {
        String redirecting = fixture.url("/moved/302/" + fixture.idOf(mediaUrl));

        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(redirecting));
    }

    @Test
    public void urlWithoutRedirectIsNotReportedAsMoved() {
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(mediaUrl));
    }

    @Test
    public void unreachableUrlIsNotReportedAsMoved() {
        String unreachable = fixture.url("/files/" + UNUSED_PORT_URL_ID);
        fixture.server().stop();

        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(unreachable));
        assertEquals(unreachable, RedirectChecker.getFinalUrl(unreachable));
    }

    @Test
    public void finalUrlFollowsEveryRedirect() {
        assertEquals(mediaUrl, RedirectChecker.getFinalUrl(fixture.url("/moved/302/" + fixture.idOf(mediaUrl))));
        assertEquals(mediaUrl, RedirectChecker.getFinalUrl(fixture.url("/moved/301/" + fixture.idOf(mediaUrl))));
        assertEquals(mediaUrl, RedirectChecker.getFinalUrl(mediaUrl));
    }

    @Test
    public void finalUrlLeavesLocalAndEmptyAddressesAlone() {
        assertEquals("", RedirectChecker.getFinalUrl(""));
        assertEquals("file:///sdcard/episode.mp3", RedirectChecker.getFinalUrl("file:///sdcard/episode.mp3"));
    }
}

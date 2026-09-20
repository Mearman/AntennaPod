package de.danoeh.antennapod.net.common;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
public class RedirectCheckerTest {
    private MockedStatic<AntennapodHttpClient> httpClientMock;
    private final List<Request> sentRequests = new ArrayList<>();

    @Before
    public void setUp() {
        httpClientMock = mockStatic(AntennapodHttpClient.class);
    }

    @After
    public void tearDown() {
        httpClientMock.close();
    }

    private void serveWith(FakeHttp.Responder responder) {
        OkHttpClient client = FakeHttp.client(request -> {
            sentRequests.add(request);
            return responder.respond(request);
        });
        httpClientMock.when(AntennapodHttpClient::getHttpClient).thenReturn(client);
    }

    private static Response redirectChain(int firstCode, String firstUrl, String secondUrl) {
        Request first = FakeHttp.request(firstUrl);
        Request second = FakeHttp.request(secondUrl);
        Response redirect = FakeHttp.priorResponse(first, firstCode);
        return FakeHttp.responseBuilder(second, 200).priorResponse(redirect).build();
    }

    @Test
    public void testResponseWithoutRedirectIsNotPermanentRedirect() {
        Response response = FakeHttp.response(FakeHttp.request("http://example.com/feed"), 200);
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(response));
    }

    @Test
    public void testMovedPermanentlyReturnsUrlOfSecondRequest() {
        Response response = redirectChain(301, "http://example.com/old", "http://example.com/new");
        assertEquals("http://example.com/new", RedirectChecker.getNewUrlIfPermanentRedirect(response));
    }

    @Test
    public void testPermanentRedirectReturnsUrlOfSecondRequest() {
        Response response = redirectChain(308, "https://example.com/old", "https://other.example.org/new");
        assertEquals("https://other.example.org/new", RedirectChecker.getNewUrlIfPermanentRedirect(response));
    }

    @Test
    public void testTemporaryRedirectToDifferentUrlIsNotPermanent() {
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(
                redirectChain(302, "https://example.com/old", "https://example.com/new")));
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(
                redirectChain(307, "https://example.com/old", "https://example.com/new")));
    }

    @Test
    public void testTemporaryRedirectFromHttpToHttpsOfSameUrlIsTreatedAsPermanent() {
        Response response = redirectChain(302, "http://example.com/feed", "https://example.com/feed");
        assertEquals("https://example.com/feed", RedirectChecker.getNewUrlIfPermanentRedirect(response));
    }

    @Test
    public void testTemporaryRedirectFromHttpToHttpsOfDifferentPathIsNotPermanent() {
        Response response = redirectChain(302, "http://example.com/feed", "https://example.com/other");
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(response));
    }

    @Test
    public void testOnlyFirstHopDecidesWhetherRedirectIsPermanent() {
        Request first = FakeHttp.request("http://example.com/a");
        Request second = FakeHttp.request("http://example.com/b");
        Request third = FakeHttp.request("http://example.com/c");
        Response firstRedirect = FakeHttp.priorResponse(first, 301);
        Response secondRedirect = FakeHttp.responseBuilder(second, 302).body(null).priorResponse(firstRedirect).build();
        Response finalResponse = FakeHttp.responseBuilder(third, 200).priorResponse(secondRedirect).build();
        assertEquals("http://example.com/b", RedirectChecker.getNewUrlIfPermanentRedirect(finalResponse));

        Response temporaryFirst = FakeHttp.priorResponse(first, 302);
        Response permanentSecond = FakeHttp.responseBuilder(second, 301).body(null).priorResponse(temporaryFirst)
                .build();
        Response finalAfterTemporary = FakeHttp.responseBuilder(third, 200).priorResponse(permanentSecond).build();
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect(finalAfterTemporary));
    }

    @Test
    public void testUrlVariantSendsHeadRequestAndReturnsNewUrlOfPermanentRedirect() {
        serveWith(request -> redirectChain(301, "http://example.com/old", "http://example.com/new"));
        assertEquals("http://example.com/new",
                RedirectChecker.getNewUrlIfPermanentRedirect("http://example.com/old"));
        assertEquals(1, sentRequests.size());
        assertEquals("HEAD", sentRequests.get(0).method());
        assertEquals("http://example.com/old", sentRequests.get(0).url().toString());
    }

    @Test
    public void testUrlVariantReturnsNullWithoutRedirect() {
        serveWith(request -> FakeHttp.response(request, 200));
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect("http://example.com/feed"));
    }

    @Test
    public void testUrlVariantReturnsNullWhenRequestFails() {
        serveWith(request -> {
            throw new IOException("offline");
        });
        assertNull(RedirectChecker.getNewUrlIfPermanentRedirect("http://example.com/feed"));
    }

    @Test
    public void testFinalUrlReturnsUrlOfLastRequestAfterRedirects() {
        serveWith(request -> redirectChain(302, "http://example.com/short", "https://example.com/long"));
        assertEquals("https://example.com/long", RedirectChecker.getFinalUrl("http://example.com/short"));
        assertEquals("HEAD", sentRequests.get(0).method());
    }

    @Test
    public void testFinalUrlReturnsSameUrlWithoutRedirect() {
        serveWith(request -> FakeHttp.response(request, 200));
        assertEquals("https://example.com/feed", RedirectChecker.getFinalUrl("https://example.com/feed"));
    }

    @Test
    public void testFinalUrlReturnsInputWhenRequestFails() {
        serveWith(request -> {
            throw new IOException("offline");
        });
        assertEquals("https://example.com/feed", RedirectChecker.getFinalUrl("https://example.com/feed"));
    }

    @Test
    public void testFinalUrlDoesNotContactServerForEmptyOrNonHttpUrls() {
        serveWith(request -> FakeHttp.response(request, 200));
        assertEquals("", RedirectChecker.getFinalUrl(""));
        assertEquals("ftp://example.com/feed", RedirectChecker.getFinalUrl("ftp://example.com/feed"));
        assertEquals("example.com/feed", RedirectChecker.getFinalUrl("example.com/feed"));
        assertEquals(0, sentRequests.size());
    }
}

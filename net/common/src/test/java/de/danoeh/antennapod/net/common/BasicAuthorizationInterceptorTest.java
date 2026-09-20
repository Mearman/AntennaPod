package de.danoeh.antennapod.net.common;

import de.danoeh.antennapod.model.download.DownloadRequest;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class BasicAuthorizationInterceptorTest {
    private static final String FEED_URL = "http://example.com/feed";
    private static final String USER_PASS_HEADER = "Basic dXNlcjpwYXNz";
    private static final String NON_ASCII_USERNAME = "jürgen";
    private static final String NON_ASCII_PASSWORD = "pä:ss";
    private static final String NON_ASCII_ISO_HEADER = "Basic avxyZ2VuOnDkOnNz";
    private static final String NON_ASCII_UTF8_HEADER = "Basic asO8cmdlbjpww6Q6c3M=";

    private final Interceptor.Chain chain = mock(Interceptor.Chain.class);

    private static DownloadRequest downloadRequest(String source, String username, String password) {
        return new DownloadRequest("destination", source, "title", 0, 0, username, password, null, false);
    }

    private static Request requestWithTag(String url, Object tag) {
        return new Request.Builder().url(url).tag(tag).build();
    }

    private Response intercept(Request request, Response first, Response... others) throws IOException {
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any())).thenReturn(first, others);
        return new BasicAuthorizationInterceptor().intercept(chain);
    }

    private List<Request> proceededRequests(int expectedCount) throws IOException {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(chain, times(expectedCount)).proceed(captor.capture());
        return captor.getAllValues();
    }

    @Test
    public void testResponseThatIsNotUnauthorizedIsReturnedWithoutRetry() throws IOException {
        Request request = requestWithTag(FEED_URL, downloadRequest(FEED_URL, "user", "pass"));
        Response ok = FakeHttp.response(request, 200);
        assertSame(ok, intercept(request, ok));
        assertEquals(1, proceededRequests(1).size());
    }

    @Test
    public void testUnauthorizedWithoutDownloadRequestTagIsNotRetried() throws IOException {
        Request request = FakeHttp.request(FEED_URL);
        Response unauthorized = FakeHttp.response(request, 401);
        assertSame(unauthorized, intercept(request, unauthorized));
        assertEquals(1, proceededRequests(1).size());
    }

    @Test
    public void testUnauthorizedWithTagOfOtherTypeIsNotRetried() throws IOException {
        Request request = requestWithTag(FEED_URL, "not a download request");
        Response unauthorized = FakeHttp.response(request, 401);
        assertSame(unauthorized, intercept(request, unauthorized));
        assertEquals(1, proceededRequests(1).size());
    }

    @Test
    public void testUnauthorizedWithUserInfoInSourceRetriesWithBasicAuthorization() throws IOException {
        String source = "http://user:pass@example.com/feed";
        Request request = requestWithTag(FEED_URL, downloadRequest(source, null, null));
        Response unauthorized = FakeHttp.response(request, 401);
        Response ok = FakeHttp.response(request, 200);
        assertSame(ok, intercept(request, unauthorized, ok));
        List<Request> proceeded = proceededRequests(2);
        assertNull(proceeded.get(0).header("Authorization"));
        assertEquals(USER_PASS_HEADER, proceeded.get(1).header("Authorization"));
        assertEquals(FEED_URL, proceeded.get(1).url().toString());
    }

    @Test
    public void testUnauthorizedWithUsernameAndPasswordFieldsRetriesWithBasicAuthorization() throws IOException {
        Request request = requestWithTag(FEED_URL, downloadRequest(FEED_URL, "user", "pass"));
        Response unauthorized = FakeHttp.response(request, 401);
        Response ok = FakeHttp.response(request, 200);
        assertSame(ok, intercept(request, unauthorized, ok));
        assertEquals(USER_PASS_HEADER, proceededRequests(2).get(1).header("Authorization"));
    }

    @Test
    public void testUserInfoInSourceTakesPrecedenceOverUsernameAndPasswordFields() throws IOException {
        String source = "http://user:pass@example.com/feed";
        Request request = requestWithTag(FEED_URL, downloadRequest(source, "other", "credentials"));
        Response unauthorized = FakeHttp.response(request, 401);
        Response ok = FakeHttp.response(request, 200);
        intercept(request, unauthorized, ok);
        assertEquals(USER_PASS_HEADER, proceededRequests(2).get(1).header("Authorization"));
    }

    @Test
    public void testPasswordContainingColonIsKeptCompleteAndNonAsciiIsSentAsIso88591First() throws IOException {
        Request request = requestWithTag(FEED_URL,
                downloadRequest(FEED_URL, NON_ASCII_USERNAME, NON_ASCII_PASSWORD));
        Response unauthorized = FakeHttp.response(request, 401);
        Response ok = FakeHttp.response(request, 200);
        intercept(request, unauthorized, ok);
        assertEquals(NON_ASCII_ISO_HEADER, proceededRequests(2).get(1).header("Authorization"));
    }

    @Test
    public void testIso88591AttemptRejectedRetriesWithUtf8() throws IOException {
        Request request = requestWithTag(FEED_URL,
                downloadRequest(FEED_URL, NON_ASCII_USERNAME, NON_ASCII_PASSWORD));
        Response first = FakeHttp.response(request, 401);
        Response second = FakeHttp.response(request, 401);
        Response third = FakeHttp.response(request, 200);
        assertSame(third, intercept(request, first, second, third));
        List<Request> proceeded = proceededRequests(3);
        assertEquals(NON_ASCII_ISO_HEADER, proceeded.get(1).header("Authorization"));
        assertEquals(NON_ASCII_UTF8_HEADER, proceeded.get(2).header("Authorization"));
    }

    @Test
    public void testAllEncodingsRejectedReturnsLastUnauthorizedResponse() throws IOException {
        Request request = requestWithTag(FEED_URL, downloadRequest(FEED_URL, "user", "wrong"));
        Response first = FakeHttp.response(request, 401);
        Response second = FakeHttp.response(request, 401);
        Response third = FakeHttp.response(request, 401);
        assertSame(third, intercept(request, first, second, third));
        assertEquals(3, proceededRequests(3).size());
    }

    @Test
    public void testUnauthorizedWithoutAnyCredentialsIsNotRetried() throws IOException {
        Request request = requestWithTag(FEED_URL, downloadRequest(FEED_URL, null, null));
        Response unauthorized = FakeHttp.response(request, 401);
        assertSame(unauthorized, intercept(request, unauthorized));
        assertEquals(1, proceededRequests(1).size());
    }

    @Test
    public void testUnauthorizedWithEmptyCredentialFieldsIsNotRetried() throws IOException {
        Request request = requestWithTag(FEED_URL, downloadRequest(FEED_URL, "", ""));
        Response unauthorized = FakeHttp.response(request, 401);
        assertSame(unauthorized, intercept(request, unauthorized));
        assertEquals(1, proceededRequests(1).size());
    }

    @Test
    public void testUserInfoWithoutColonIsNotRetried() throws IOException {
        String source = "http://token@example.com/feed";
        Request request = requestWithTag(FEED_URL, downloadRequest(source, null, null));
        Response unauthorized = FakeHttp.response(request, 401);
        assertSame(unauthorized, intercept(request, unauthorized));
        assertEquals(1, proceededRequests(1).size());
    }

    @Test
    public void testRedirectedUnauthorizedRequestReusesExistingAuthorizationHeaderAtNewLocation() throws IOException {
        Request request = new Request.Builder()
                .url(FEED_URL)
                .header("Authorization", "Basic b3JpZ2luYWw6aGVhZGVy")
                .build();
        Request redirected = FakeHttp.request("https://example.com/moved");
        Response unauthorized = FakeHttp.response(redirected, 401);
        Response ok = FakeHttp.response(redirected, 200);
        assertSame(ok, intercept(request, unauthorized, ok));
        List<Request> proceeded = proceededRequests(2);
        assertEquals("https://example.com/moved", proceeded.get(1).url().toString());
        assertEquals("Basic b3JpZ2luYWw6aGVhZGVy", proceeded.get(1).header("Authorization"));
    }

    @Test
    public void testRedirectedUnauthorizedRequestWithDownloadCredentialsRetriesAtNewLocation() throws IOException {
        Request request = requestWithTag(FEED_URL, downloadRequest(FEED_URL, "user", "pass"));
        Request redirected = FakeHttp.request("https://example.com/moved");
        Response unauthorized = FakeHttp.response(redirected, 401);
        Response ok = FakeHttp.response(redirected, 200);
        assertSame(ok, intercept(request, unauthorized, ok));
        List<Request> proceeded = proceededRequests(2);
        assertEquals("https://example.com/moved", proceeded.get(1).url().toString());
        assertEquals(USER_PASS_HEADER, proceeded.get(1).header("Authorization"));
    }

    @Test
    public void testRedirectedUnauthorizedRequestWithoutCredentialsIsNotRetried() throws IOException {
        Request request = FakeHttp.request(FEED_URL);
        Request redirected = FakeHttp.request("https://example.com/moved");
        Response unauthorized = FakeHttp.response(redirected, 401);
        assertSame(unauthorized, intercept(request, unauthorized));
        assertEquals(1, proceededRequests(1).size());
    }
}

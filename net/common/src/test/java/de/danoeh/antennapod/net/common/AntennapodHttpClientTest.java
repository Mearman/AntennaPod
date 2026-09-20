package de.danoeh.antennapod.net.common;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.download.ProxyConfig;
import de.danoeh.antennapod.net.ssl.AntennaPodSslSocketFactory;
import okhttp3.Authenticator;
import okhttp3.ConnectionSpec;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AntennapodHttpClientTest {
    private static final File CACHE_DIRECTORY = new File("antennapod-http-client-test-cache");
    private String originalUserAgent;

    @Before
    public void setUp() {
        originalUserAgent = UserAgentInterceptor.USER_AGENT;
        AntennapodHttpClient.setCacheDirectory(CACHE_DIRECTORY);
        AntennapodHttpClient.setProxyConfig(null);
    }

    @After
    public void tearDown() {
        UserAgentInterceptor.USER_AGENT = originalUserAgent;
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
    }

    private static OkHttpClient buildClient() {
        return AntennapodHttpClient.newBuilder().build();
    }

    private static Response unauthorizedResponseTo(Request request) {
        return FakeHttp.response(request, 401);
    }

    @Test
    public void testBuilderRegistersBasicAuthorizationBeforeUserAgentInterceptor() {
        List<Interceptor> interceptors = buildClient().interceptors();
        assertEquals(2, interceptors.size());
        assertTrue(interceptors.get(0) instanceof BasicAuthorizationInterceptor);
        assertTrue(interceptors.get(1) instanceof UserAgentInterceptor);
    }

    @Test
    public void testBuilderConfiguresTimeoutsCacheAndRedirects() {
        OkHttpClient client = buildClient();
        assertEquals(10000, client.connectTimeoutMillis());
        assertEquals(30000, client.readTimeoutMillis());
        assertEquals(30000, client.writeTimeoutMillis());
        assertTrue(client.followRedirects());
        assertTrue(client.followSslRedirects());
        assertNotNull(client.cache());
        assertEquals(CACHE_DIRECTORY, client.cache().directory());
        assertEquals(20L * 1000000, client.cache().maxSize());
    }

    @Test
    public void testBuilderInstallsTlsSocketFactoryAndModernTlsOrCleartextSpecs() {
        OkHttpClient client = buildClient();
        assertTrue(client.sslSocketFactory() instanceof AntennaPodSslSocketFactory);
        assertEquals(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT), client.connectionSpecs());
    }

    @Test
    public void testBuilderLimitsMaximumConnectionsSystemProperty() {
        System.clearProperty("http.maxConnections");
        buildClient();
        assertEquals("8", System.getProperty("http.maxConnections"));
    }

    @Test
    public void testCookieJarKeepsCookiesOfRequestedHostAndRejectsForeignDomains() {
        OkHttpClient client = buildClient();
        HttpUrl url = HttpUrl.get("http://example.com/feed");
        Cookie own = new Cookie.Builder().name("session").value("1").domain("example.com").build();
        Cookie foreign = new Cookie.Builder().name("tracker").value("2").domain("other.org").build();
        client.cookieJar().saveFromResponse(url, Arrays.asList(own, foreign));
        assertTrue(client.cookieJar() instanceof JavaNetCookieJar);
        List<Cookie> loaded = client.cookieJar().loadForRequest(url);
        assertEquals(1, loaded.size());
        assertEquals("session", loaded.get(0).name());
        assertEquals("1", loaded.get(0).value());
    }

    @Test
    public void testGetHttpClientReturnsSameInstanceUntilReinit() {
        AntennapodHttpClient.reinit();
        OkHttpClient first = AntennapodHttpClient.getHttpClient();
        assertSame(first, AntennapodHttpClient.getHttpClient());
        AntennapodHttpClient.reinit();
        assertNotSame(first, AntennapodHttpClient.getHttpClient());
    }

    @Test
    public void testNewBuilderCreatesIndependentClients() {
        OkHttpClient first = buildClient();
        OkHttpClient second = buildClient();
        assertNotSame(first, second);
        assertNotSame(first.cookieJar(), second.cookieJar());
    }

    @Test
    public void testReinitPicksUpChangedProxyConfiguration() {
        AntennapodHttpClient.reinit();
        assertNull(AntennapodHttpClient.getHttpClient().proxy());
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, null, null));
        assertNull(AntennapodHttpClient.getHttpClient().proxy());
        AntennapodHttpClient.reinit();
        assertNotNull(AntennapodHttpClient.getHttpClient().proxy());
    }

    @Test
    public void testClientSendsConfiguredUserAgent() throws IOException {
        UserAgentInterceptor.USER_AGENT = "AntennaPod/9.9.9";
        List<Request> seen = new ArrayList<>();
        OkHttpClient client = AntennapodHttpClient.newBuilder().addInterceptor(chain -> {
            seen.add(chain.request());
            return FakeHttp.response(chain.request(), 200);
        }).build();
        client.newCall(FakeHttp.request("http://example.com/feed")).execute().close();
        assertEquals(1, seen.size());
        assertEquals("AntennaPod/9.9.9", seen.get(0).header("User-Agent"));
    }

    @Test
    public void testClientRetriesUnauthorizedDownloadWithCredentialsOfDownloadRequest() throws IOException {
        List<Request> seen = new ArrayList<>();
        OkHttpClient client = AntennapodHttpClient.newBuilder().addInterceptor(chain -> {
            Request request = chain.request();
            seen.add(request);
            if (request.header("Authorization") == null) {
                return unauthorizedResponseTo(request);
            }
            return FakeHttp.response(request, 200);
        }).build();
        DownloadRequest download = new DownloadRequest("destination", "http://example.com/feed", "title", 0, 0,
                "user", "pass", null, false);
        Request request = new Request.Builder().url("http://example.com/feed").tag(download).build();
        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
        }
        assertEquals(2, seen.size());
        assertEquals("Basic dXNlcjpwYXNz", seen.get(1).header("Authorization"));
        assertEquals(UserAgentInterceptor.USER_AGENT, seen.get(1).header("User-Agent"));
    }

    @Test
    public void testNoProxyWithoutConfigurationOrForDirectType() {
        assertNull(buildClient().proxy());
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.DIRECT, "proxy.example.com", 3128, null, null));
        assertNull(buildClient().proxy());
    }

    @Test
    public void testNoProxyWhenHostIsMissingOrEmpty() {
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, null, 3128, null, null));
        assertNull(buildClient().proxy());
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "", 3128, null, null));
        assertNull(buildClient().proxy());
    }

    @Test
    public void testHttpProxyUsesConfiguredUnresolvedHostAndPort() {
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, null, null));
        Proxy proxy = buildClient().proxy();
        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals("proxy.example.com", address.getHostString());
        assertEquals(3128, address.getPort());
        assertTrue(address.isUnresolved());
    }

    @Test
    public void testSocksProxyTypeIsPreserved() {
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.SOCKS, "proxy.example.com", 1080, null, null));
        assertEquals(Proxy.Type.SOCKS, buildClient().proxy().type());
    }

    @Test
    public void testProxyWithoutPortFallsBackToDefaultPort() {
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 0, null, null));
        InetSocketAddress address = (InetSocketAddress) buildClient().proxy().address();
        assertEquals(ProxyConfig.DEFAULT_PORT, address.getPort());
    }

    @Test
    public void testProxyCredentialsAreSentAsProxyAuthorizationHeader() throws IOException {
        AntennapodHttpClient.setProxyConfig(
                new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, "user", "pass"));
        Authenticator authenticator = buildClient().proxyAuthenticator();
        Request challenged = FakeHttp.request("http://example.com/feed");
        Request retry = authenticator.authenticate(null, FakeHttp.response(challenged, 407));
        assertEquals("Basic dXNlcjpwYXNz", retry.header("Proxy-Authorization"));
        assertEquals("http://example.com/feed", retry.url().toString());
    }

    @Test
    public void testProxyWithoutUsernameOrPasswordDoesNotAuthenticate() throws IOException {
        Request challenged = FakeHttp.request("http://example.com/feed");
        Response response = FakeHttp.response(challenged, 407);
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, "", "pass"));
        assertNull(buildClient().proxyAuthenticator().authenticate(null, response));
        AntennapodHttpClient.setProxyConfig(new ProxyConfig(Proxy.Type.HTTP, "proxy.example.com", 3128, "user", null));
        assertNull(buildClient().proxyAuthenticator().authenticate(null, response));
    }
}

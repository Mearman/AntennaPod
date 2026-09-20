package de.danoeh.antennapod.net.sync.gpoddernet;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class GpodnetServiceLoginTest {
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private String hostUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort();
    }

    private GpodnetService newService(String hostUrl) {
        return new GpodnetService(new OkHttpClient(), hostUrl, "device1", "alice", "secret");
    }

    @Test
    public void loginPostsBasicCredentialsToLoginEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        newService(hostUrl()).login();

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/api/2/auth/alice/login.json", request.getPath());
        assertEquals("Basic YWxpY2U6c2VjcmV0", request.getHeader("Authorization"));
    }

    @Test
    public void loginKeepsSubfolderOfConfiguredHost() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        newService(hostUrl() + "/gpodder/").login();

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/gpodder/api/2/auth/alice/login.json", request.getPath());
    }

    @Test
    public void loginWithNewCredentialsAfterSetCredentialsUsesThem() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        GpodnetService service = newService(hostUrl());

        service.setCredentials("bob", "hunter2");
        service.login();

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/2/auth/bob/login.json", request.getPath());
        assertEquals("Basic Ym9iOmh1bnRlcjI=", request.getHeader("Authorization"));
    }

    @Test
    public void loginRejectedWithUnauthorizedReportsAuthenticationFailure() {
        server.enqueue(new MockResponse().setResponseCode(401));

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class,
                () -> newService(hostUrl()).login());

        assertTrue(exception.getCause() instanceof GpodnetServiceAuthenticationException);
    }

    @Test
    public void loginWithServerErrorReportsServiceUnavailableWithStatusCode() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("maintenance"));

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class,
                () -> newService(hostUrl()).login());

        assertTrue(exception.getCause() instanceof GpodnetServiceBadStatusCodeException);
        assertTrue(exception.getCause().getMessage().contains("currently unavailable"));
        assertTrue(exception.getCause().getMessage().contains("503"));
    }

    @Test
    public void loginWithClientErrorReportsUnableToConnectWithStatusCode() {
        server.enqueue(new MockResponse().setResponseCode(404));

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class,
                () -> newService(hostUrl()).login());

        assertTrue(exception.getCause() instanceof GpodnetServiceBadStatusCodeException);
        assertTrue(exception.getCause().getMessage().contains("Unable to connect"));
        assertTrue(exception.getCause().getMessage().contains("404"));
    }

    @Test
    public void loginFailsWhenServerIsUnreachable() throws IOException {
        String unreachableHost = hostUrl();
        server.shutdown();

        GpodnetServiceException exception = assertThrows(GpodnetServiceException.class,
                () -> newService(unreachableHost).login());

        assertTrue(exception.getCause() instanceof IOException);
    }

    @Test
    public void requestsBeforeLoginAreRejectedWithoutContactingServer() {
        GpodnetService service = newService(hostUrl());

        assertThrows(IllegalStateException.class, service::getDevices);
        assertThrows(IllegalStateException.class, () -> service.getSubscriptionChanges(0));
        assertThrows(IllegalStateException.class, () -> service.getEpisodeActionChanges(0));
        assertThrows(IllegalStateException.class,
                () -> service.uploadSubscriptionChanges(Collections.emptyList(), Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> service.uploadEpisodeActions(Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> service.configureDevice("device1", "caption", null));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void failedLoginDoesNotAllowFurtherRequests() {
        server.enqueue(new MockResponse().setResponseCode(401));
        GpodnetService service = newService(hostUrl());

        assertThrows(GpodnetServiceException.class, service::login);

        assertThrows(IllegalStateException.class, service::getDevices);
        assertEquals(1, server.getRequestCount());
    }
}

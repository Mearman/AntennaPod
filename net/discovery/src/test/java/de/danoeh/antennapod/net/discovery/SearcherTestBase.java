package de.danoeh.antennapod.net.discovery;

import android.content.Context;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class SearcherTestBase {
    static final long REQUEST_TIMEOUT_SECONDS = 5;
    Context context;
    MockWebServer server;

    @Before
    public void setUpServerAndSchedulers() throws IOException {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(scheduler -> Schedulers.trampoline());
        context = RuntimeEnvironment.getApplication();
        AntennapodHttpClient.setCacheDirectory(new File(context.getCacheDir(), "discovery-test"));
        AntennapodHttpClient.setProxyConfig(null);
        AntennapodHttpClient.reinit();
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDownServerAndSchedulers() throws IOException {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        server.shutdown();
    }

    String urlOf(String pathAndQuery) {
        return server.url(pathAndQuery).toString();
    }

    RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    static List<PodcastSearchResult> resultsOf(TestObserver<List<PodcastSearchResult>> observer) {
        observer.assertNoErrors();
        assertEquals(1, observer.values().size());
        return observer.values().get(0);
    }
}

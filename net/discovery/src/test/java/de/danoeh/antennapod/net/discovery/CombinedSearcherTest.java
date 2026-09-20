package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import io.reactivex.rxjava3.observers.TestObserver;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class CombinedSearcherTest extends SearcherTestBase {
    private static final String APPLE_PATH = "/apple";
    private static final String INDEX_PATH = "/index";
    private static final String FYYD_PATH = "/fyyd";
    private List<PodcastSearcherRegistry.SearcherInfo> originalProviders;
    private int appleStatus = 200;
    private final List<String> requestedPaths = new ArrayList<>();

    @Before
    public void installProviders() {
        originalProviders = new ArrayList<>(PodcastSearcherRegistry.getSearchProviders());
        appleStatus = 200;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath().split("\\?")[0];
                requestedPaths.add(path);
                if (APPLE_PATH.equals(path)) {
                    return new MockResponse().setResponseCode(appleStatus).setBody("{\"results\":["
                            + appleEntry("X") + "," + appleEntry("Y") + "]}");
                } else if (INDEX_PATH.equals(path)) {
                    return new MockResponse().setBody("{\"feeds\":[" + indexEntry("Y") + "," + indexEntry("Z") + "]}");
                } else if (FYYD_PATH.equals(path)) {
                    return new MockResponse().setBody("{\"data\":[{\"xmlURL\":\"https://feeds.example/F.xml\"}]}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        providers.add(new PodcastSearcherRegistry.SearcherInfo(new CombinedSearcher(), 1.0f));
        providers.add(new PodcastSearcherRegistry.SearcherInfo(
                new FyydPodcastSearcher(urlOf(FYYD_PATH + "?title=%s")), 0.0f));
        providers.add(new PodcastSearcherRegistry.SearcherInfo(
                new ItunesPodcastSearcher(urlOf(APPLE_PATH + "?term=%s"), urlOf("/lookup?id=")), 1.0f));
        providers.add(new PodcastSearcherRegistry.SearcherInfo(
                new PodcastIndexPodcastSearcher(urlOf(INDEX_PATH + "?q=%s")), 1.0f));
    }

    @After
    public void restoreProviders() {
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        providers.addAll(originalProviders);
    }

    private static String appleEntry(String name) {
        return "{\"collectionName\":\"" + name + "\",\"feedUrl\":\"https://feeds.example/" + name + ".xml\"}";
    }

    private static String indexEntry(String name) {
        return "{\"title\":\"" + name + "\",\"url\":\"https://feeds.example/" + name + ".xml\"}";
    }

    private static List<String> titlesOf(List<PodcastSearchResult> results) {
        List<String> titles = new ArrayList<>();
        for (PodcastSearchResult result : results) {
            titles.add(result.title);
        }
        return titles;
    }

    @Test
    public void resultsOfAllWeightedProvidersAreMergedAndRankedByPositionAndOverlap() {
        List<PodcastSearchResult> results = resultsOf(new CombinedSearcher().search("show").test());

        assertEquals(Arrays.asList("Y", "X", "Z"), titlesOf(results));
    }

    @Test
    public void providersWithoutWeightAndTheCombinedSearcherItselfAreNotQueried() {
        resultsOf(new CombinedSearcher().search("show").test());

        assertFalse(requestedPaths.contains(FYYD_PATH));
        assertEquals(2, requestedPaths.size());
        assertTrue(requestedPaths.contains(APPLE_PATH));
        assertTrue(requestedPaths.contains(INDEX_PATH));
    }

    @Test
    public void failingProviderDoesNotPreventResultsFromOthers() {
        appleStatus = 500;

        List<PodcastSearchResult> results = resultsOf(new CombinedSearcher().search("show").test());

        assertEquals(Arrays.asList("Y", "Z"), titlesOf(results));
    }

    @Test
    public void allProvidersFailingYieldsEmptyResult() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(500);
            }
        });

        TestObserver<List<PodcastSearchResult>> observer = new CombinedSearcher().search("show").test();

        assertTrue(resultsOf(observer).isEmpty());
    }

    @Test
    public void nameListsOnlyTheProvidersThatAreQueried() {
        assertEquals("Apple, Podcast Index", new CombinedSearcher().getName());
    }

    @Test
    public void lookupIsDelegatedToTheProviderResponsibleForTheUrl() {
        CombinedSearcher searcher = new CombinedSearcher();
        String appleUrl = "https://itunes.apple.com/us/podcast/some-show/id123";

        assertTrue(searcher.urlNeedsLookup(appleUrl));
        assertFalse(searcher.urlNeedsLookup("https://feeds.example/X.xml"));
        searcher.lookupUrl("https://feeds.example/X.xml").test().assertValue("https://feeds.example/X.xml");
        assertEquals(0, requestedPaths.size());
    }
}

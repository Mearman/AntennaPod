package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CombinedSearcherTest {
    @Rule
    public final ImmediateSchedulersRule schedulers = new ImmediateSchedulersRule();

    private List<PodcastSearcherRegistry.SearcherInfo> originalProviders;
    private final CombinedSearcher combinedSearcher = new CombinedSearcher();

    @Before
    public void setUp() {
        originalProviders = new ArrayList<>(PodcastSearcherRegistry.getSearchProviders());
    }

    @After
    public void tearDown() {
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        providers.addAll(originalProviders);
    }

    private static PodcastSearcherRegistry.SearcherInfo weighted(PodcastSearcher searcher, float weight) {
        return new PodcastSearcherRegistry.SearcherInfo(searcher, weight);
    }

    private static void register(PodcastSearcherRegistry.SearcherInfo... infos) {
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        providers.addAll(Arrays.asList(infos));
    }

    private List<String> searchFeedUrls(String query) {
        TestObserver<List<PodcastSearchResult>> observer = combinedSearcher.search(query).test();
        observer.assertNoErrors();
        List<String> feedUrls = new ArrayList<>();
        for (PodcastSearchResult result : observer.values().get(0)) {
            feedUrls.add(result.feedUrl);
        }
        return feedUrls;
    }

    @Test
    public void testResultFoundByMultipleProvidersRanksAboveResultsFoundByOne() {
        register(weighted(StubSearcher.returning("a", StubSearcher.result("x"), StubSearcher.result("y")), 1.0f),
                weighted(StubSearcher.returning("b", StubSearcher.result("y"), StubSearcher.result("z")), 1.0f));
        assertEquals(List.of("y", "x", "z"), searchFeedUrls("query"));
    }

    @Test
    public void testResultsAreDeduplicatedByFeedUrl() {
        register(weighted(StubSearcher.returning("a", StubSearcher.result("same"), StubSearcher.result("other")), 1.0f),
                weighted(StubSearcher.returning("b", StubSearcher.result("same")), 1.0f));
        List<String> feedUrls = searchFeedUrls("query");
        assertEquals(2, feedUrls.size());
        assertEquals("same", feedUrls.get(0));
    }

    @Test
    public void testEarlierPositionRanksHigherWithinOneProvider() {
        register(weighted(StubSearcher.returning("a", StubSearcher.result("first"),
                StubSearcher.result("second"), StubSearcher.result("third")), 1.0f));
        assertEquals(List.of("first", "second", "third"), searchFeedUrls("query"));
    }

    @Test
    public void testProviderWithHigherWeightRanksItsResultsHigher() {
        register(weighted(StubSearcher.returning("light", StubSearcher.result("light-result")), 0.5f),
                weighted(StubSearcher.returning("heavy", StubSearcher.result("heavy-result")), 2.0f));
        assertEquals(List.of("heavy-result", "light-result"), searchFeedUrls("query"));
    }

    @Test
    public void testProviderWithZeroWeightIsNotQueried() {
        StubSearcher disabled = StubSearcher.returning("disabled", StubSearcher.result("hidden"));
        StubSearcher enabled = StubSearcher.returning("enabled", StubSearcher.result("shown"));
        register(weighted(disabled, 0.0f),
                weighted(enabled, 1.0f));
        assertEquals(List.of("shown"), searchFeedUrls("query"));
        assertEquals(0, disabled.searchCount);
        assertEquals(1, enabled.searchCount);
        assertEquals("query", enabled.lastQuery);
    }

    @Test
    public void testCombinedSearcherDoesNotQueryItself() {
        StubSearcher stub = StubSearcher.returning("stub", StubSearcher.result("found"));
        register(weighted(new CombinedSearcher(), 1.0f),
                weighted(stub, 1.0f));
        assertEquals(List.of("found"), searchFeedUrls("query"));
        assertEquals(1, stub.searchCount);
    }

    @Test
    public void testFailingProviderDoesNotPreventResultsOfOthers() {
        register(weighted(StubSearcher.failing("broken"), 1.0f),
                weighted(StubSearcher.returning("working", StubSearcher.result("found")), 1.0f));
        assertEquals(List.of("found"), searchFeedUrls("query"));
    }

    @Test
    public void testSearchYieldsEmptyListWhenAllProvidersFail() {
        register(weighted(StubSearcher.failing("first"), 1.0f),
                weighted(StubSearcher.failing("second"), 1.0f));
        assertTrue(searchFeedUrls("query").isEmpty());
    }

    @Test
    public void testSearchYieldsEmptyListWithoutEnabledProviders() {
        register(weighted(StubSearcher.returning("disabled", StubSearcher.result("hidden")), 0.0f));
        assertTrue(searchFeedUrls("query").isEmpty());
    }

    @Test
    public void testNameListsEnabledProvidersInOrder() {
        register(weighted(new CombinedSearcher(), 1.0f),
                weighted(StubSearcher.returning("disabled"), 0.0f),
                weighted(StubSearcher.returning("Alpha"), 1.0f),
                weighted(StubSearcher.returning("Beta"), 0.5f));
        assertEquals("Alpha, Beta", combinedSearcher.getName());
    }

    @Test
    public void testNameIsEmptyWithoutEnabledProviders() {
        register(weighted(StubSearcher.returning("disabled"), 0.0f));
        assertEquals("", combinedSearcher.getName());
    }

    @Test
    public void testLookupAndLookupCheckAreDelegatedToRegistry() {
        register(weighted(StubSearcher.returning("plain"), 1.0f),
                weighted(StubSearcher.resolvingUrlsTo("resolver", "resolved"), 1.0f));
        assertTrue(combinedSearcher.urlNeedsLookup("https://directory.example/show"));
        combinedSearcher.lookupUrl("https://directory.example/show").test().assertValue("resolved");
        register(weighted(StubSearcher.returning("plain"), 1.0f));
        assertFalse(combinedSearcher.urlNeedsLookup("https://directory.example/show"));
    }
}

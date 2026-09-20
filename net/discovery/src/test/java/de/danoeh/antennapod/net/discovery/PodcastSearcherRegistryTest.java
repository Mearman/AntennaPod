package de.danoeh.antennapod.net.discovery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PodcastSearcherRegistryTest {
    private List<PodcastSearcherRegistry.SearcherInfo> originalProviders;

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

    private static void register(PodcastSearcher... searchers) {
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        for (PodcastSearcher searcher : searchers) {
            providers.add(new PodcastSearcherRegistry.SearcherInfo(searcher, 1.0f));
        }
    }

    @Test
    public void testDefaultProvidersAreCombinedFyydItunesAndPodcastIndexWithTheirWeights() {
        assertEquals(CombinedSearcher.class, originalProviders.get(0).searcher.getClass());
        assertEquals(4, originalProviders.size());
        assertEquals(FyydPodcastSearcher.class, originalProviders.get(1).searcher.getClass());
        assertEquals(ItunesPodcastSearcher.class, originalProviders.get(2).searcher.getClass());
        assertEquals(PodcastIndexPodcastSearcher.class, originalProviders.get(3).searcher.getClass());
        assertEquals(0.0f, originalProviders.get(0).weight, 0.0f);
        assertEquals(0.0f, originalProviders.get(1).weight, 0.0f);
        assertEquals(1.0f, originalProviders.get(2).weight, 0.0f);
        assertEquals(1.0f, originalProviders.get(3).weight, 0.0f);
    }

    @Test
    public void testGetSearchProvidersReturnsSameListOnEveryCall() {
        assertSame(PodcastSearcherRegistry.getSearchProviders(), PodcastSearcherRegistry.getSearchProviders());
    }

    @Test
    public void testSearcherInfoKeepsSearcherAndWeight() {
        StubSearcher searcher = StubSearcher.returning("stub");
        PodcastSearcherRegistry.SearcherInfo info = new PodcastSearcherRegistry.SearcherInfo(searcher, 0.25f);
        assertSame(searcher, info.searcher);
        assertEquals(0.25f, info.weight, 0.0f);
    }

    @Test
    public void testLookupUrlDelegatesToFirstSearcherThatNeedsLookup() {
        register(StubSearcher.returning("no lookup"),
                StubSearcher.resolvingUrlsTo("first", "https://feeds.example/first.xml"),
                StubSearcher.resolvingUrlsTo("second", "https://feeds.example/second.xml"));
        PodcastSearcherRegistry.lookupUrl("https://directory.example/show").test()
                .assertValue("https://feeds.example/first.xml");
    }

    @Test
    public void testLookupUrlReturnsUnchangedUrlWhenNoSearcherNeedsLookup() {
        register(StubSearcher.returning("a"), StubSearcher.returning("b"));
        PodcastSearcherRegistry.lookupUrl("https://feeds.example/feed.xml").test()
                .assertValue("https://feeds.example/feed.xml");
    }

    @Test
    public void testLookupUrlIgnoresCombinedSearcherToAvoidDelegatingToItself() {
        register(new CombinedSearcher(), StubSearcher.resolvingUrlsTo("stub", "https://feeds.example/stub.xml"));
        PodcastSearcherRegistry.lookupUrl("https://directory.example/show").test()
                .assertValue("https://feeds.example/stub.xml");
    }

    @Test
    public void testUrlNeedsLookupIsTrueIfAnySearcherNeedsIt() {
        register(StubSearcher.returning("a"), StubSearcher.resolvingUrlsTo("b", "unused"));
        assertTrue(PodcastSearcherRegistry.urlNeedsLookup("https://directory.example/show"));
    }

    @Test
    public void testUrlNeedsLookupIsFalseWhenOnlyCombinedSearcherIsRegistered() {
        register(new CombinedSearcher());
        assertFalse(PodcastSearcherRegistry.urlNeedsLookup("https://directory.example/show"));
    }

    @Test
    public void testUrlNeedsLookupIsFalseWhenNoSearcherNeedsIt() {
        register(StubSearcher.returning("a"), StubSearcher.returning("b"));
        assertFalse(PodcastSearcherRegistry.urlNeedsLookup("https://feeds.example/feed.xml"));
    }
}

package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.test.categories.IntegrationTest;
import okhttp3.mockwebserver.MockResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@RunWith(RobolectricTestRunner.class)
public class PodcastSearcherRegistryTest extends SearcherTestBase {
    private List<PodcastSearcherRegistry.SearcherInfo> originalProviders;

    @Before
    public void rememberProviders() {
        originalProviders = new ArrayList<>(PodcastSearcherRegistry.getSearchProviders());
    }

    @After
    public void restoreProviders() {
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        providers.addAll(originalProviders);
    }

    @Test
    public void defaultProvidersStartWithTheCombinedSearcherAndQueryAppleAndPodcastIndex() {
        List<PodcastSearcherRegistry.SearcherInfo> providers = originalProviders;

        assertEquals(CombinedSearcher.class, providers.get(0).searcher.getClass());
        List<String> weightedNames = new ArrayList<>();
        for (PodcastSearcherRegistry.SearcherInfo provider : providers) {
            if (provider.weight > 0 && provider.searcher.getClass() != CombinedSearcher.class) {
                weightedNames.add(provider.searcher.getName());
            }
        }
        assertEquals(List.of("Apple", "Podcast Index"), weightedNames);
    }

    @Test
    public void lookupOfUrlNoProviderClaimsReturnsItUnchanged() {
        assertFalse(PodcastSearcherRegistry.urlNeedsLookup("https://feeds.example/a.xml"));

        PodcastSearcherRegistry.lookupUrl("https://feeds.example/a.xml").test()
                .assertNoErrors().assertValue("https://feeds.example/a.xml");
    }

    @Test
    public void lookupOfAppleUrlIsAnsweredByTheAppleSearcher() throws Exception {
        List<PodcastSearcherRegistry.SearcherInfo> providers = PodcastSearcherRegistry.getSearchProviders();
        providers.clear();
        providers.add(new PodcastSearcherRegistry.SearcherInfo(new CombinedSearcher(), 1.0f));
        providers.add(new PodcastSearcherRegistry.SearcherInfo(
                new ItunesPodcastSearcher(urlOf("/search?term=%s"), urlOf("/lookup?id=")), 1.0f));
        server.enqueue(new MockResponse().setBody("{\"results\":[{\"feedUrl\":\"https://feeds.example/a.xml\"}]}"));
        String appleUrl = "https://podcasts.apple.com/us/podcast/some-show/id42";

        assertTrue(PodcastSearcherRegistry.urlNeedsLookup(appleUrl));
        PodcastSearcherRegistry.lookupUrl(appleUrl).test()
                .assertNoErrors().assertValue("https://feeds.example/a.xml");

        assertEquals("/lookup?id=42", takeRequest().getPath());
    }
}

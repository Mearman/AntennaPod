package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.core.Single;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class StubSearcher implements PodcastSearcher {
    private final String name;
    private final Single<List<PodcastSearchResult>> outcome;
    private final boolean needsLookup;
    private final String lookupResult;
    int searchCount;
    String lastQuery;

    StubSearcher(String name, Single<List<PodcastSearchResult>> outcome, boolean needsLookup, String lookupResult) {
        this.name = name;
        this.outcome = outcome;
        this.needsLookup = needsLookup;
        this.lookupResult = lookupResult;
    }

    static StubSearcher returning(String name, PodcastSearchResult... results) {
        return new StubSearcher(name, Single.just(Arrays.asList(results)), false, null);
    }

    static StubSearcher failing(String name) {
        return new StubSearcher(name, Single.error(new IllegalStateException("search failed")), false, null);
    }

    static StubSearcher resolvingUrlsTo(String name, String lookupResult) {
        return new StubSearcher(name, Single.just(Collections.<PodcastSearchResult>emptyList()), true, lookupResult);
    }

    static PodcastSearchResult result(String feedUrl) {
        return new PodcastSearchResult("Title of " + feedUrl, null, feedUrl, null);
    }

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        searchCount++;
        lastQuery = query;
        return outcome;
    }

    @Override
    public Single<String> lookupUrl(String resultUrl) {
        return Single.just(lookupResult);
    }

    @Override
    public boolean urlNeedsLookup(String resultUrl) {
        return needsLookup;
    }

    @Override
    public String getName() {
        return name;
    }
}

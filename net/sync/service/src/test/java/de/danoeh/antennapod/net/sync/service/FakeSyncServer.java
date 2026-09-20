package de.danoeh.antennapod.net.sync.service;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

class FakeSyncServer extends Dispatcher {
    private static final String FEED_PATH_PREFIX = "/feeds/";
    private static final long REQUEST_TIMEOUT_SECONDS = 5;
    private final MockWebServer server = new MockWebServer();
    private final Map<String, MockResponse> routes = new ConcurrentHashMap<>();
    private final List<RecordedRequest> recorded = new ArrayList<>();

    void start() throws IOException {
        server.setDispatcher(this);
        server.start();
    }

    void stop() throws IOException {
        server.shutdown();
    }

    String hostUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort();
    }

    String feedUrl(String name) {
        return server.url(FEED_PATH_PREFIX + name + ".xml").toString();
    }

    void route(String method, String path, MockResponse response) {
        routes.put(method + " " + path, response);
    }

    void routeJson(String method, String path, String json) {
        route(method, path, new MockResponse().setBody(json));
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) {
        String path = pathWithoutQuery(request);
        MockResponse response = routes.get(request.getMethod() + " " + path);
        if (response != null) {
            return response.clone();
        }
        if (path.startsWith(FEED_PATH_PREFIX)) {
            return new MockResponse();
        }
        return new MockResponse().setResponseCode(404);
    }

    List<RecordedRequest> requests() throws InterruptedException {
        while (recorded.size() < server.getRequestCount()) {
            recorded.add(server.takeRequest(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        return recorded;
    }

    List<RecordedRequest> requests(String method, String path) throws InterruptedException {
        List<RecordedRequest> matching = new ArrayList<>();
        for (RecordedRequest request : requests()) {
            if (request.getMethod().equals(method) && pathWithoutQuery(request).equals(path)) {
                matching.add(request);
            }
        }
        return matching;
    }

    private static String pathWithoutQuery(RecordedRequest request) {
        String path = request.getPath();
        int queryStart = path.indexOf('?');
        return queryStart >= 0 ? path.substring(0, queryStart) : path;
    }

    static List<String> stringsOf(JSONArray array) throws JSONException {
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            strings.add(array.getString(i));
        }
        return strings;
    }

    static JSONObject bodyOf(RecordedRequest request) throws JSONException {
        return new JSONObject(request.getBody().clone().readUtf8());
    }
}

package de.test.antennapod.util.sync;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import fi.iki.elonen.NanoHTTPD;

/**
 * Minimal in-process gpodder.net server that the synchronization code of the app can log into. Records every incoming request so tests can wait for and assert on the traffic the app produced.
 */
public class GpodderTestServer extends NanoHTTPD {
    public static final String USERNAME = "testuser";
    public static final String PASSWORD = "testpass";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_HTML = "text/html";

    public static class RecordedRequest {
        public final String method;
        public final String path;
        public final String body;

        RecordedRequest(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }

        public boolean matches(String method, String pathFragment) {
            return method.equals(this.method) && path.contains(pathFragment);
        }
    }

    public final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    public final AtomicLong latestTimestamp = new AtomicLong(1);

    private volatile int loginStatus = 200;
    private volatile int deviceUploadStatus = 200;
    private volatile int subscriptionDownloadStatus = 200;
    private volatile int subscriptionUploadStatus = 200;
    private volatile int episodeDownloadStatus = 200;
    private volatile int episodeUploadStatus = 200;
    private volatile String deviceJson = "[{\"id\":\"device1\",\"caption\":\"Existing device\","
            + "\"type\":\"mobile\",\"subscriptions\":3}]";
    private volatile List<String> subscriptionChangesAdded = new ArrayList<>();
    private volatile List<String> subscriptionChangesRemoved = new ArrayList<>();
    private volatile JSONArray episodeActions = new JSONArray();
    public final List<String> uploadedAddedFeeds = Collections.synchronizedList(new ArrayList<>());
    public final List<String> uploadedRemovedFeeds = Collections.synchronizedList(new ArrayList<>());
    public final List<JSONObject> uploadedEpisodeActions = new CopyOnWriteArrayList<>();
    public final List<JSONObject> configuredDevices = new CopyOnWriteArrayList<>();

    public GpodderTestServer() {
        super(0);
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getListeningPort();
    }

    public void setLoginStatus(int status) {
        this.loginStatus = status;
    }

    public void setDeviceUploadStatus(int status) {
        this.deviceUploadStatus = status;
    }

    public void setSubscriptionDownloadStatus(int status) {
        this.subscriptionDownloadStatus = status;
    }

    public void setSubscriptionUploadStatus(int status) {
        this.subscriptionUploadStatus = status;
    }

    public void setEpisodeDownloadStatus(int status) {
        this.episodeDownloadStatus = status;
    }

    public void setEpisodeUploadStatus(int status) {
        this.episodeUploadStatus = status;
    }

    public void setDevicesJson(String json) {
        this.deviceJson = json;
    }

    public void setSubscriptionChanges(List<String> added, List<String> removed) {
        this.subscriptionChangesAdded = added;
        this.subscriptionChangesRemoved = removed;
    }

    public void setEpisodeActions(JSONArray actions) {
        this.episodeActions = actions;
    }

    public void clearRecordedRequests() {
        requests.clear();
        uploadedAddedFeeds.clear();
        uploadedRemovedFeeds.clear();
        uploadedEpisodeActions.clear();
        configuredDevices.clear();
    }

    public boolean hasRequest(String method, String pathFragment) {
        return !getRequests(method, pathFragment).isEmpty();
    }

    public int countRequests(String method, String pathFragment) {
        return getRequests(method, pathFragment).size();
    }

    public List<RecordedRequest> getRequests(String method, String pathFragment) {
        List<RecordedRequest> result = new ArrayList<>();
        for (RecordedRequest request : requests) {
            if (request.matches(method, pathFragment)) {
                result.add(request);
            }
        }
        return result;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String path = session.getUri();
        String query = session.getQueryParameterString();
        if (query != null && query.length() > 0) {
            path = path + "?" + query;
        }
        String body = readBody(session);
        requests.add(new RecordedRequest(method, path, body));

        if (!path.startsWith("/api/2/")) {
            return statusResponse(404);
        }

        try {
            if (path.startsWith("/api/2/auth/") && path.endsWith("/login.json")) {
                return handleLogin(session);
            } else if (path.startsWith("/api/2/devices/")) {
                if ("GET".equals(method)) {
                    return new Response(Response.Status.OK, MIME_JSON, deviceJson);
                } else if ("POST".equals(method)) {
                    if (deviceUploadStatus != 200) {
                        return statusResponse(deviceUploadStatus);
                    }
                    configuredDevices.add(new JSONObject(body));
                    return statusResponse(200);
                }
            } else if (path.startsWith("/api/2/subscriptions/")) {
                if ("GET".equals(method)) {
                    if (subscriptionDownloadStatus != 200) {
                        return statusResponse(subscriptionDownloadStatus);
                    }
                    JSONObject response = new JSONObject();
                    response.put("add", new JSONArray(subscriptionChangesAdded));
                    response.put("remove", new JSONArray(subscriptionChangesRemoved));
                    response.put("timestamp", latestTimestamp.get());
                    return new Response(Response.Status.OK, MIME_JSON, response.toString());
                } else if ("POST".equals(method)) {
                    if (subscriptionUploadStatus != 200) {
                        return statusResponse(subscriptionUploadStatus);
                    }
                    JSONObject upload = new JSONObject(body);
                    JSONArray added = upload.getJSONArray("add");
                    JSONArray removed = upload.getJSONArray("remove");
                    for (int i = 0; i < added.length(); i++) {
                        uploadedAddedFeeds.add(added.getString(i));
                    }
                    for (int i = 0; i < removed.length(); i++) {
                        uploadedRemovedFeeds.add(removed.getString(i));
                    }
                    JSONObject response = new JSONObject();
                    response.put("timestamp", latestTimestamp.incrementAndGet());
                    response.put("update_urls", new JSONArray());
                    return new Response(Response.Status.OK, MIME_JSON, response.toString());
                }
            } else if (path.startsWith("/api/2/episodes/")) {
                if ("GET".equals(method)) {
                    if (episodeDownloadStatus != 200) {
                        return statusResponse(episodeDownloadStatus);
                    }
                    JSONObject response = new JSONObject();
                    response.put("actions", episodeActions);
                    response.put("timestamp", latestTimestamp.incrementAndGet());
                    return new Response(Response.Status.OK, MIME_JSON, response.toString());
                } else if ("POST".equals(method)) {
                    if (episodeUploadStatus != 200) {
                        return statusResponse(episodeUploadStatus);
                    }
                    JSONArray actions = new JSONArray(body);
                    for (int i = 0; i < actions.length(); i++) {
                        uploadedEpisodeActions.add(actions.getJSONObject(i));
                    }
                    JSONObject response = new JSONObject();
                    response.put("timestamp", latestTimestamp.incrementAndGet());
                    response.put("update_urls", new JSONArray());
                    return new Response(Response.Status.OK, MIME_JSON, response.toString());
                }
            }
            return statusResponse(404);
        } catch (Exception e) {
            return statusResponse(500);
        }
    }

    private Response handleLogin(IHTTPSession session) {
        if (loginStatus != 200) {
            return statusResponse(loginStatus);
        }
        Map<String, String> headers = session.getHeaders();
        String authorization = headers.get("authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return statusResponse(401);
        }
        String credentials = new String(Base64.decode(authorization.substring("Basic ".length()), 0));
        String[] parts = credentials.split(":");
        if (parts.length != 2 || !USERNAME.equals(parts[0]) || !PASSWORD.equals(parts[1])) {
            return statusResponse(401);
        }
        return statusResponse(200);
    }

    private Response statusResponse(final int code) {
        Response.IStatus status = new Response.IStatus() {
            @Override
            public int getRequestStatus() {
                return code;
            }

            @Override
            public String getDescription() {
                return code + " Test status";
            }
        };
        return new Response(status, MIME_HTML, "");
    }

    private String readBody(IHTTPSession session) {
        try {
            Map<String, String> headers = session.getHeaders();
            String lengthHeader = headers.get("content-length");
            if (lengthHeader == null) {
                return "";
            }
            int length = Integer.parseInt(lengthHeader);
            if (length <= 0) {
                return "";
            }
            byte[] buffer = new byte[length];
            int read = 0;
            while (read < length) {
                int count = session.getInputStream().read(buffer, read, length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return new String(buffer, 0, read, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}

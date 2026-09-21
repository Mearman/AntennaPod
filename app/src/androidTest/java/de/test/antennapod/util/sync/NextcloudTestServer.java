package de.test.antennapod.util.sync;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import fi.iki.elonen.NanoHTTPD;

public class NextcloudTestServer extends NanoHTTPD {
    public static final String USERNAME = "ncuser";
    public static final String APP_PASSWORD = "nc-app-password";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_HTML = "text/html";

    public final List<String> requests = new CopyOnWriteArrayList<>();
    public final AtomicBoolean approveLogin = new AtomicBoolean(false);
    public final List<String> subscriptionAdded = Collections.synchronizedList(new ArrayList<>());
    public final List<String> subscriptionRemoved = Collections.synchronizedList(new ArrayList<>());
    public final List<JSONObject> uploadedEpisodeActions = new CopyOnWriteArrayList<>();
    private volatile JSONArray episodeActions = new JSONArray();
    private volatile List<String> downloadAdded = new ArrayList<>();
    private volatile List<String> downloadRemoved = new ArrayList<>();
    private volatile int subscriptionsStatus = 200;
    private volatile int episodeActionsStatus = 200;

    public NextcloudTestServer() {
        super(0);
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getListeningPort();
    }

    public void setSubscriptionChanges(List<String> added, List<String> removed) {
        this.downloadAdded = added;
        this.downloadRemoved = removed;
    }

    public void setEpisodeActions(JSONArray actions) {
        this.episodeActions = actions;
    }

    public void setSubscriptionsStatus(int status) {
        this.subscriptionsStatus = status;
    }

    public void setEpisodeActionsStatus(int status) {
        this.episodeActionsStatus = status;
    }

    public boolean hasRequest(String fragment) {
        for (String request : requests) {
            if (request.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String path = session.getUri();
        String body = readBody(session);
        requests.add(method + " " + path + (body.isEmpty() ? "" : " " + body));

        try {
            if (path.endsWith("/index.php/login/v2")) {
                JSONObject response = new JSONObject();
                response.put("login", getBaseUrl() + "/fake-browser-login");
                JSONObject poll = new JSONObject();
                poll.put("token", "poll-token");
                poll.put("endpoint", getBaseUrl() + "/index.php/login/v2/poll");
                response.put("poll", poll);
                return new Response(Response.Status.OK, MIME_JSON, response.toString());
            } else if (path.endsWith("/index.php/login/v2/poll")) {
                if (!approveLogin.get()) {
                    return statusResponse(404);
                }
                JSONObject response = new JSONObject();
                response.put("server", getBaseUrl());
                response.put("loginName", USERNAME);
                response.put("appPassword", APP_PASSWORD);
                return new Response(Response.Status.OK, MIME_JSON, response.toString());
            } else if (path.endsWith("/index.php/apps/gpoddersync/subscriptions")) {
                if (!isAuthenticated(session) || subscriptionsStatus != 200) {
                    return statusResponse(401);
                }
                JSONObject response = new JSONObject();
                response.put("add", new JSONArray(downloadAdded));
                response.put("remove", new JSONArray(downloadRemoved));
                response.put("timestamp", 1234567);
                return new Response(Response.Status.OK, MIME_JSON, response.toString());
            } else if (path.endsWith("/index.php/apps/gpoddersync/subscription_change/create")) {
                if (!isAuthenticated(session)) {
                    return statusResponse(401);
                }
                JSONObject upload = new JSONObject(body);
                JSONArray added = upload.getJSONArray("add");
                JSONArray removed = upload.getJSONArray("remove");
                for (int i = 0; i < added.length(); i++) {
                    subscriptionAdded.add(added.getString(i));
                }
                for (int i = 0; i < removed.length(); i++) {
                    subscriptionRemoved.add(removed.getString(i));
                }
                return statusResponse(200);
            } else if (path.endsWith("/index.php/apps/gpoddersync/episode_action")) {
                if (!isAuthenticated(session) || episodeActionsStatus != 200) {
                    return statusResponse(401);
                }
                JSONObject response = new JSONObject();
                response.put("actions", episodeActions);
                response.put("timestamp", 1234567);
                return new Response(Response.Status.OK, MIME_JSON, response.toString());
            } else if (path.endsWith("/index.php/apps/gpoddersync/episode_action/create")) {
                if (!isAuthenticated(session)) {
                    return statusResponse(401);
                }
                JSONArray actions = new JSONArray(body);
                for (int i = 0; i < actions.length(); i++) {
                    uploadedEpisodeActions.add(actions.getJSONObject(i));
                }
                return statusResponse(200);
            }
            return statusResponse(404);
        } catch (Exception e) {
            return statusResponse(500);
        }
    }

    private boolean isAuthenticated(IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        String authorization = headers.get("authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        String expected = Base64.encodeToString(
                (USERNAME + ":" + APP_PASSWORD).getBytes(), Base64.NO_WRAP);
        return authorization.substring("Basic ".length()).equals(expected);
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

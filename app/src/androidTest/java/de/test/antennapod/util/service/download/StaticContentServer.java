package de.test.antennapod.util.service.download;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import fi.iki.elonen.NanoHTTPD;

public class StaticContentServer extends NanoHTTPD {
    public static final String BASE_PLACEHOLDER = "${BASE}";

    private static final int STATUS_NOT_MODIFIED = 304;
    private static final int STATUS_PARTIAL_CONTENT = 206;
    private static final int STATUS_OK = 200;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_RANGE_NOT_SATISFIABLE = 416;

    public static class RecordedRequest {
        public final String method;
        public final String path;
        public final Map<String, String> headers;

        RecordedRequest(String method, String path, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }
    }

    private static class Document {
        final int status;
        final String contentType;
        final byte[] body;
        final String location;
        final String lastModified;
        final String etag;
        final String username;
        final String password;

        Document(int status, String contentType, byte[] body, String location, String lastModified, String etag,
                 String username, String password) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.location = location;
            this.lastModified = lastModified;
            this.etag = etag;
            this.username = username;
            this.password = password;
        }
    }

    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    public StaticContentServer() {
        super(0);
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getListeningPort();
    }

    public String publish(String path, String contentType, byte[] body) {
        documents.put(path, new Document(STATUS_OK, contentType, body, null, null, null, null, null));
        return getBaseUrl() + path;
    }

    public String publish(String path, String contentType, String template) {
        String text = template.replace(BASE_PLACEHOLDER, getBaseUrl());
        return publish(path, contentType, text.getBytes(StandardCharsets.UTF_8));
    }

    public String publishWithValidators(String path, String contentType, String template,
                                        String lastModified, String etag) {
        String text = template.replace(BASE_PLACEHOLDER, getBaseUrl());
        documents.put(path, new Document(STATUS_OK, contentType, text.getBytes(StandardCharsets.UTF_8),
                null, lastModified, etag, null, null));
        return getBaseUrl() + path;
    }

    public String publishProtected(String path, String contentType, String template,
                                   String username, String password) {
        String text = template.replace(BASE_PLACEHOLDER, getBaseUrl());
        documents.put(path, new Document(STATUS_OK, contentType, text.getBytes(StandardCharsets.UTF_8),
                null, null, null, username, password));
        return getBaseUrl() + path;
    }

    public String redirect(String path, int status, String location) {
        documents.put(path, new Document(status, null, new byte[0], location, null, null, null, null));
        return getBaseUrl() + path;
    }

    public String respondWithStatus(String path, int status) {
        documents.put(path, new Document(status, "text/plain", new byte[0], null, null, null, null, null));
        return getBaseUrl() + path;
    }

    public void remove(String path) {
        documents.remove(path);
    }

    public List<RecordedRequest> requestsFor(String path) {
        List<RecordedRequest> result = new ArrayList<>();
        for (RecordedRequest request : requests) {
            if (request.path.equals(path)) {
                result.add(request);
            }
        }
        return result;
    }

    public static String httpDate(long timestampMillis) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(timestampMillis));
    }

    @Override
    public Response serve(IHTTPSession session) {
        final String path = session.getUri();
        final Map<String, String> headers = session.getHeaders();
        requests.add(new RecordedRequest(session.getMethod().name(), path, new HashMap<>(headers)));

        Document document = documents.get(path);
        if (document == null) {
            return respond(STATUS_NOT_FOUND, "text/plain", new byte[0]);
        }
        if (document.username != null && !hasCredentials(headers.get("authorization"), document)) {
            Response response = respond(STATUS_UNAUTHORIZED, "text/plain", new byte[0]);
            response.addHeader("WWW-Authenticate", "Basic realm=\"Test Realm\"");
            return response;
        }
        if (document.location != null) {
            Response response = respond(document.status, "text/plain", new byte[0]);
            response.addHeader("Location", document.location);
            return response;
        }
        if (document.etag != null && document.etag.equals(headers.get("if-none-match"))
                || document.lastModified != null && document.lastModified.equals(headers.get("if-modified-since"))) {
            return respond(STATUS_NOT_MODIFIED, document.contentType, new byte[0]);
        }
        Response response = serveBody(document, headers.get("range"));
        if (document.lastModified != null) {
            response.addHeader("Last-Modified", document.lastModified);
        }
        if (document.etag != null) {
            response.addHeader("ETag", document.etag);
        }
        return response;
    }

    private Response serveBody(Document document, String range) {
        if (document.status != STATUS_OK) {
            return respond(document.status, document.contentType, document.body);
        }
        if (range == null || !range.startsWith("bytes=")) {
            Response response = respond(STATUS_OK, document.contentType, document.body);
            response.addHeader("Accept-Ranges", "bytes");
            return response;
        }
        String startText = range.substring("bytes=".length()).split("-")[0];
        int start = Integer.parseInt(startText);
        if (start >= document.body.length) {
            return respond(STATUS_RANGE_NOT_SATISFIABLE, "text/plain", new byte[0]);
        }
        byte[] rest = new byte[document.body.length - start];
        System.arraycopy(document.body, start, rest, 0, rest.length);
        Response response = respond(STATUS_PARTIAL_CONTENT, document.contentType, rest);
        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("Content-Range",
                "bytes " + start + "-" + (document.body.length - 1) + "/" + document.body.length);
        return response;
    }

    private static boolean hasCredentials(String authorization, Document document) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(Base64.decode(authorization.substring("Basic ".length()), Base64.DEFAULT),
                StandardCharsets.UTF_8);
        return decoded.equals(document.username + ":" + document.password);
    }

    private static Response respond(int code, String contentType, byte[] body) {
        Response response = new Response(statusFor(code), contentType, new ByteArrayInputStream(body));
        response.addHeader("Content-Length", String.valueOf(body.length));
        return response;
    }

    private static Response.IStatus statusFor(int code) {
        return new Response.IStatus() {
            @Override
            public int getRequestStatus() {
                return code;
            }

            @Override
            public String getDescription() {
                return code + " Test";
            }
        };
    }
}

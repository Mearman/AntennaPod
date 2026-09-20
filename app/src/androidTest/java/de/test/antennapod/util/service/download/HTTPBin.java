package de.test.antennapod.util.service.download;

import android.util.Base64;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import de.danoeh.antennapod.BuildConfig;

/**
 * Http server for testing purposes
 * <p/>
 * Supported features:
 * <p/>
 * /status/code: Returns HTTP response with the given status code
 * /redirect/n:  Redirects n times
 * /delay/n:     Delay response for n seconds
 * /basic-auth/username/password: Basic auth with username and password
 * /gzip/n:      Send gzipped data of size n bytes
 * /files/id:     Accesses the file with the specified ID (this has to be added first via serveFile).
 */
public class HTTPBin extends NanoHTTPD {
    private static final String TAG = "HTTPBin";

    private static final String MIME_HTML = "text/html";
    private static final String MIME_PLAIN = "text/plain";

    private static final int TRUNCATED_MISSING_BYTES = 100;

    private final List<File> servedFiles;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Object stalledLock = new Object();
    private int stalledResponses = 0;
    private final CountDownLatch stallGate = new CountDownLatch(1);

    public HTTPBin() {
        super(0); // Let system pick a free port
        this.servedFiles = new ArrayList<>();
    }

    public static class RecordedRequest {
        public final String method;
        public final String uri;
        public final Map<String, String> headers;

        RecordedRequest(String method, String uri, Map<String, String> headers) {
            this.method = method;
            this.uri = uri;
            this.headers = headers;
        }
    }

    public List<RecordedRequest> getRequests() {
        return new ArrayList<>(requests);
    }

    public List<RecordedRequest> getRequestsForPrefix(String uriPrefix) {
        List<RecordedRequest> matching = new ArrayList<>();
        for (RecordedRequest request : requests) {
            if (request.uri.startsWith(uriPrefix)) {
                matching.add(request);
            }
        }
        return matching;
    }

    public boolean awaitStalled(int responses, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (stalledLock) {
            while (stalledResponses < responses) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(stalledLock, remaining);
            }
            return true;
        }
    }

    public void releaseStalled() {
        stallGate.countDown();
    }

    @Override
    public void stop() {
        releaseStalled();
        super.stop();
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + getListeningPort();
    }

    /**
     * Adds the given file to the server.
     *
     * @return The ID of the file or -1 if the file could not be added to the server.
     */
    public synchronized int serveFile(File file) {
        if (file == null) throw new IllegalArgumentException("file = null");
        if (!file.exists()) {
            return -1;
        }
        for (int i = 0; i < servedFiles.size(); i++) {
            if (servedFiles.get(i).getAbsolutePath().equals(file.getAbsolutePath())) {
                return i;
            }
        }
        servedFiles.add(file);
        return servedFiles.size() - 1;
    }

    public synchronized File accessFile(int id) {
        if (id < 0 || id >= servedFiles.size()) {
            return null;
        } else {
            return servedFiles.get(id);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        Response response = serveRequest(session);
        response.addHeader("Connection", "close");
        return response;
    }

    private Response serveRequest(IHTTPSession session) {

        if (BuildConfig.DEBUG) Log.d(TAG, "Requested url: " + session.getUri());
        requests.add(new RecordedRequest(session.getMethod().name(), session.getUri(),
                new HashMap<>(session.getHeaders())));

        String[] segments = session.getUri().split("/");
        if (segments.length < 3) {
            Log.w(TAG, String.format(Locale.US, "Invalid number of URI segments: %d %s",
                    segments.length, Arrays.toString(segments)));
            get404Error();
        }

        final String func = segments[1];
        final String param = segments[2];
        final Map<String, String> headers = session.getHeaders();

        if (func.equalsIgnoreCase("status")) {
            try {
                int code = Integer.parseInt(param);
                return new Response(getStatus(code), MIME_HTML, "");
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return getInternalError();
            }

        } else if (func.equalsIgnoreCase("redirect")) {
            try {
                int times = Integer.parseInt(param);
                if (times < 0) {
                    throw new NumberFormatException("times <= 0: " + times);
                }

                return getRedirectResponse(times - 1);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return getInternalError();
            }
        } else if (func.equalsIgnoreCase("delay")) {
            try {
                int sec = Integer.parseInt(param);
                if (sec <= 0) {
                    throw new NumberFormatException("sec <= 0: " + sec);
                }

                Thread.sleep(sec * 1000L);
                return getOKResponse();
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return getInternalError();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return getInternalError();
            }
        } else if (func.equalsIgnoreCase("basic-auth")) {
            if (!headers.containsKey("authorization")) {
                Log.w(TAG, "No credentials provided");
                return getUnauthorizedResponse();
            }
            try {
                String credentials = new String(Base64.decode(headers.get("authorization").split(" ")[1], 0), "UTF-8");
                String[] credentialParts = credentials.split(":");
                if (credentialParts.length != 2) {
                    Log.w(TAG, "Unable to split credentials: " + Arrays.toString(credentialParts));
                    return getInternalError();
                }
                if (credentialParts[0].equals(segments[2])
                        && credentialParts[1].equals(segments[3])) {
                    Log.i(TAG, "Credentials accepted");
                    return getOKResponse();
                } else {
                    Log.w(TAG, String.format("Invalid credentials. Expected %s, %s, but was %s, %s",
                            segments[2], segments[3], credentialParts[0], credentialParts[1]));
                    return getUnauthorizedResponse();
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return getInternalError();
            }
        } else if (func.equalsIgnoreCase("gzip")) {
            try {
                int size = Integer.parseInt(param);
                if (size <= 0) {
                    Log.w(TAG, "Invalid size for gzipped data: " + size);
                    throw new NumberFormatException();
                }

                return getGzippedResponse(size);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return getInternalError();
            } catch (IOException e) {
                e.printStackTrace();
                return getInternalError();
            }
        } else if (func.equalsIgnoreCase("files")) {
            try {
                int id = Integer.parseInt(param);
                if (id < 0) {
                    Log.w(TAG, "Invalid ID: " + id);
                    throw new NumberFormatException();
                }
                return getFileAccessResponse(id, headers, false, false);

            } catch (NumberFormatException e) {
                e.printStackTrace();
                return getInternalError();
            }
        } else if (func.equalsIgnoreCase("basic-auth-file")) {
            if (segments.length < 5) {
                return get404Error();
            }
            if (!headers.containsKey("authorization")) {
                return getUnauthorizedResponse();
            }
            String credentials = new String(Base64.decode(headers.get("authorization").split(" ")[1], 0));
            if (!credentials.equals(segments[2] + ":" + segments[3])) {
                return getUnauthorizedResponse();
            }
            return getFileAccessResponse(Integer.parseInt(segments[4]), headers, false, false);
        } else if (func.equalsIgnoreCase("moved")) {
            if (segments.length < 4) {
                return get404Error();
            }
            Response response = new Response(getStatus(Integer.parseInt(param)), MIME_HTML, "");
            response.addHeader("Location", "/files/" + segments[3]);
            return response;
        } else if (func.equalsIgnoreCase("stall")) {
            return getFileAccessResponse(Integer.parseInt(param), headers, true, false);
        } else if (func.equalsIgnoreCase("truncated")) {
            return getFileAccessResponse(Integer.parseInt(param), headers, false, true);
        }

        return get404Error();
    }

    private synchronized Response getFileAccessResponse(int id, Map<String, String> header, boolean stall,
                                                        boolean truncate) {
        File file = accessFile(id);
        if (file == null || !file.exists()) {
            Log.w(TAG, "File not found: " + id);
            return get404Error();
        }
        final String etag = "\"etag-" + file.length() + "-" + file.lastModified() + "\"";
        if (etag.equals(header.get("if-none-match"))) {
            Response notModified = new Response(Response.Status.NOT_MODIFIED, MIME_PLAIN, "");
            notModified.addHeader("ETag", etag);
            return notModified;
        }

        long start = 0;
        long end = file.length() - 1;
        Response.Status status = Response.Status.OK;
        String contentRange = null;
        final String ifRange = header.get("if-range");
        if (header.containsKey("range") && (ifRange == null || ifRange.equals(etag))) {
            final String value = header.get("range");
            final String[] segments = value.split("=");
            if (segments.length != 2) {
                Log.w(TAG, "Invalid segment length: " + Arrays.toString(segments));
                return getInternalError();
            }
            final String type = StringUtils.substringBefore(value, "=");
            if (!type.equalsIgnoreCase("bytes")) {
                Log.w(TAG, "Range is not specified in bytes: " + value);
                return getInternalError();
            }
            try {
                start = Long.parseLong(StringUtils.substringBefore(segments[1], "-"));
                final String endPart = StringUtils.substringAfter(segments[1], "-");
                if (!endPart.isEmpty()) {
                    end = Math.min(Long.parseLong(endPart), end);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return getInternalError();
            }
            if (start >= file.length()) {
                return getRangeNotSatisfiable(file.length());
            }
            contentRange = "bytes " + start + "-" + end + "/" + file.length();
            status = Response.Status.PARTIAL_CONTENT;
        }

        final long length = end - start + 1;
        InputStream inputStream = null;
        boolean successful = false;
        try {
            inputStream = new FileInputStream(file);
            IOUtils.skipFully(inputStream, start);
            final long announcedLength = truncate ? length + TRUNCATED_MISSING_BYTES : length;
            inputStream = new ServedStream(inputStream, length, announcedLength, stall ? length / 2 : -1);
            successful = true;
        } catch (IOException e) {
            e.printStackTrace();
            return getInternalError();
        } finally {
            if (!successful && inputStream != null) {
                IOUtils.closeQuietly(inputStream);
            }
        }

        Response response = new Response(status, URLConnection.guessContentTypeFromName(file.getAbsolutePath()), inputStream);

        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("ETag", etag);
        if (contentRange != null) {
            response.addHeader("Content-Range", contentRange);
        }
        response.addHeader("Content-Length", String.valueOf(truncate ? length + TRUNCATED_MISSING_BYTES : length));
        return response;
    }

    private class ServedStream extends InputStream {
        private final InputStream source;
        private final long length;
        private final long announcedLength;
        private final long stallAfter;
        private long delivered = 0;
        private boolean reportedStall = false;

        ServedStream(InputStream source, long length, long announcedLength, long stallAfter) {
            this.source = source;
            this.length = length;
            this.announcedLength = announcedLength;
            this.stallAfter = stallAfter;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int count = read(single, 0, 1);
            return count == -1 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int len) throws IOException {
            if (delivered >= length) {
                return -1;
            }
            long allowed = length - delivered;
            if (stallAfter >= 0) {
                if (delivered >= stallAfter) {
                    reportStall();
                    try {
                        stallGate.await();
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                } else {
                    allowed = Math.min(allowed, stallAfter - delivered);
                }
            }
            int count = source.read(buffer, offset, (int) Math.min(len, allowed));
            if (count > 0) {
                delivered += count;
            }
            return count;
        }

        private void reportStall() {
            if (reportedStall) {
                return;
            }
            reportedStall = true;
            synchronized (stalledLock) {
                stalledResponses++;
                stalledLock.notifyAll();
            }
        }

        @Override
        public int available() {
            return (int) (announcedLength - delivered);
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }

    private Response getGzippedResponse(int size) throws IOException {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        final byte[] buffer = new byte[size];
        Random random = new Random(System.currentTimeMillis());
        random.nextBytes(buffer);

        ByteArrayOutputStream compressed = new ByteArrayOutputStream(buffer.length);
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(compressed);
        gzipOutputStream.write(buffer);
        gzipOutputStream.close();

        InputStream inputStream = new ByteArrayInputStream(compressed.toByteArray());
        Response response = new Response(Response.Status.OK, MIME_PLAIN, inputStream);
        response.addHeader("Content-Encoding", "gzip");
        response.addHeader("Content-Length", String.valueOf(compressed.size()));
        return response;
    }

    private Response.IStatus getStatus(final int code) {
        switch (code) {
            case 200:
                return Response.Status.OK;
            case 201:
                return Response.Status.CREATED;
            case 206:
                return Response.Status.PARTIAL_CONTENT;
            case 301:
                return Response.Status.REDIRECT;
            case 304:
                return Response.Status.NOT_MODIFIED;
            case 400:
                return Response.Status.BAD_REQUEST;
            case 401:
                return Response.Status.UNAUTHORIZED;
            case 403:
                return Response.Status.FORBIDDEN;
            case 404:
                return Response.Status.NOT_FOUND;
            case 405:
                return Response.Status.METHOD_NOT_ALLOWED;
            case 416:
                return Response.Status.RANGE_NOT_SATISFIABLE;
            case 500:
                return Response.Status.INTERNAL_ERROR;
            default:
                return new Response.IStatus() {
                    @Override
                    public int getRequestStatus() {
                        return code;
                    }

                    @Override
                    public String getDescription() {
                        return code + " Unknown";
                    }
                };
        }
    }

    private Response getRedirectResponse(int times) {
        if (times > 0) {
            Response response = new Response(Response.Status.REDIRECT, MIME_HTML, "This resource has been moved permanently");
            response.addHeader("Location", "/redirect/" + times);
            return response;
        } else if (times == 0) {
            return getOKResponse();
        } else {
            return getInternalError();
        }
    }

    private Response getUnauthorizedResponse() {
        Response response = new Response(Response.Status.UNAUTHORIZED, MIME_HTML, "");
        response.addHeader("WWW-Authenticate", "Basic realm=\"Test Realm\"");
        return response;
    }

    private Response getOKResponse() {
        return new Response(Response.Status.OK, MIME_HTML, "");
    }

    private Response getInternalError() {
        return new Response(Response.Status.INTERNAL_ERROR, MIME_HTML, "The server encountered an internal error");
    }

    private Response getRangeNotSatisfiable(long fileLength) {
        Response response = new Response(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAIN, "");
        response.addHeader("Content-Range", "bytes */" + fileLength);
        return response;
    }

    private Response get404Error() {
        return new Response(Response.Status.NOT_FOUND, MIME_HTML, "The requested URL was not found on this server");
    }
}

package de.danoeh.antennapod.net.sync;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class FakeHttpClient {
    private static final MediaType JSON = MediaType.get("application/json");

    public static class RecordedRequest {
        public final Request request;
        public final String body;

        RecordedRequest(Request request, String body) {
            this.request = request;
            this.body = body;
        }
    }

    private interface Reply {
        Response respond(Request request) throws IOException;
    }

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<RecordedRequest> requests = new ArrayList<>();
    private Reply defaultReply;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request request = chain.request();
                String body = "";
                if (request.body() != null) {
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    body = buffer.readUtf8();
                }
                requests.add(new RecordedRequest(request, body));
                Reply reply = replies.isEmpty() ? defaultReply : replies.poll();
                if (reply == null) {
                    throw new IllegalStateException("No reply queued for " + request.url());
                }
                return reply.respond(request);
            })
            .build();

    public OkHttpClient client() {
        return client;
    }

    public FakeHttpClient enqueue(int code, String message, String body) {
        replies.add(reply(code, message, body));
        return this;
    }

    public FakeHttpClient enqueue(int code, String body) {
        return enqueue(code, "Message " + code, body);
    }

    public FakeHttpClient enqueueFailure(IOException failure) {
        replies.add(request -> {
            throw failure;
        });
        return this;
    }

    public FakeHttpClient replyToEverythingWith(int code, String body) {
        defaultReply = reply(code, "Message " + code, body);
        return this;
    }

    public List<RecordedRequest> requests() {
        return requests;
    }

    public RecordedRequest request(int index) {
        return requests.get(index);
    }

    private static Reply reply(int code, String message, String body) {
        return request -> new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(ResponseBody.create(body, JSON))
                .build();
    }
}

package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.rules.ExternalResource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

final class FakeHttpRule extends ExternalResource {
    interface Responder {
        Response respond(Request request) throws IOException;
    }

    private MockedStatic<AntennapodHttpClient> httpClient;
    private final List<Request> requests = new ArrayList<>();
    private Responder responder;

    @Override
    protected void before() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requests.add(chain.request());
                    return responder.respond(chain.request());
                })
                .build();
        httpClient = mockStatic(AntennapodHttpClient.class);
        httpClient.when(AntennapodHttpClient::getHttpClient).thenReturn(client);
    }

    @Override
    protected void after() {
        httpClient.close();
    }

    void respondWith(int code, String body) {
        responder = request -> response(request, code, body);
    }

    void respondWith(Responder newResponder) {
        responder = newResponder;
    }

    void failWith(IOException failure) {
        responder = request -> {
            throw failure;
        };
    }

    List<Request> requests() {
        return requests;
    }

    Request onlyRequest() {
        if (requests.size() != 1) {
            throw new AssertionError("Expected exactly one request but was " + requests.size());
        }
        return requests.get(0);
    }

    static Response response(Request request, int code, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("HTTP " + code)
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }
}

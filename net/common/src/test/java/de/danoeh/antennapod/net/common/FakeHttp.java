package de.danoeh.antennapod.net.common;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;

final class FakeHttp {
    interface Responder {
        Response respond(Request request) throws IOException;
    }

    private FakeHttp() {
    }

    static OkHttpClient client(Responder responder) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> responder.respond(chain.request()))
                .build();
    }

    static Response.Builder responseBuilder(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("HTTP " + code)
                .body(ResponseBody.create("", MediaType.parse("text/plain")));
    }

    static Response response(Request request, int code) {
        return responseBuilder(request, code).build();
    }

    static Response priorResponse(Request request, int code) {
        return responseBuilder(request, code).body(null).build();
    }

    static Request request(String url) {
        return new Request.Builder().url(url).build();
    }
}

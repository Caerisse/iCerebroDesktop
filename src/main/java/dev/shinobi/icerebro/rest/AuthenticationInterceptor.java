package dev.shinobi.icerebro.rest;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class AuthenticationInterceptor implements Interceptor {
    private final String authToken;

    public AuthenticationInterceptor(String token) {
        this.authToken = token;
    }

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        Request.Builder builder = original.newBuilder()
                .header("Authorization", "Token " + authToken);

        Request request = builder.build();
        return chain.proceed(request);
    }
}

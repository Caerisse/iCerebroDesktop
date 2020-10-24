package dev.shinobi.icerebro.rest;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ServiceGenerator {
//    private static final String BASE_URL = "https://icerebro-testing.herokuapp.com";
    // Location of my computer on local network
    public static final String BASE_URL = "http://192.168.1.101:8000";

    private static final Retrofit.Builder builder =
            new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create());
    //.addCallAdapterFactory(RxJavaCallAdapterFactory.create());

    public static Retrofit retrofit = builder.build();

    private static final HttpLoggingInterceptor logging = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY);

    private static final OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

    public static iCerebroService createService() {
        return createService(null, null);
    }

    public static iCerebroService createService(String username, String password) {
        if (username != null && !username.equals("") && password != null && !password.equals("")) {
            String authToken = Credentials.basic(username, password);
            return createService(authToken);
        }
        return createService(null);
    }

    public static iCerebroService createService(final String authToken) {
        if (authToken != null && !authToken.equals("")) {
            AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authToken);

            if (!httpClient.interceptors().contains(interceptor)) {
                httpClient.addInterceptor(interceptor);

                builder.client(httpClient.build());
                retrofit = builder.build();
            }
        }

        if (!httpClient.interceptors().contains(logging)) {
            httpClient.addInterceptor(logging);
            builder.client(httpClient.build());
            retrofit = builder.build();
        }
        return retrofit.create(iCerebroService.class);
    }
}

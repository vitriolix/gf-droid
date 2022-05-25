package org.fdroid.fdroid.net;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OkHttpPoster extends OkHttpDownloader {

    private static final String TAG = "FOO";

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public OkHttpPoster(String url) throws FileNotFoundException, MalformedURLException {
        this(Uri.parse(url), null);
        Log.d(TAG, "CONSTRUCTOR WITH URL AND NO FILE: " + url);
    }

    OkHttpPoster(Uri uri, File destFile) throws FileNotFoundException, MalformedURLException {
        super(uri, destFile);
        Log.d(TAG, "CONSTRUCTOR WITH URI AND FILE: " + uri.toString() + " / " + destFile.getAbsolutePath());
    }

    public void post(String json) throws IOException {

        Log.d(TAG, "BUILD REQUEST BODY FOR JSON: " + json);

        RequestBody body = RequestBody.create(json, JSON);

        Log.d(TAG, "BUILD POST REQUEST FOR URL: " + sourceUrl);

        OkHttpClient client = getClientBuilder().build(); // new OkHttpClient();
        Request request = getRequestBuilder() // new Request.Builder()
                //.url(sourceUrl)
                .post(body)
                .build();

        Log.d(TAG, "EXECUTE REQUEST");

        response = client.newCall(request).execute();

        Log.d(TAG, "REQUEST RESPONSE: " + response.code());
    }
}

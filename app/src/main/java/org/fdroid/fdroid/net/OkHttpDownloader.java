package org.fdroid.fdroid.net;

import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Utils;
import org.greatfire.envoy.CronetInterceptor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class OkHttpDownloader extends Downloader {

    private static final String TAG = "FOO";

    public static String queryString;

    protected Response response;

    protected URL sourceUrl;
    protected final String username;
    protected final String password;

    protected long fileSize = -1L;
    protected long fileDate = -1L;

    protected boolean resumeDownload = false;

    OkHttpDownloader(Uri uri, File destFile) throws FileNotFoundException, MalformedURLException {
        this(uri, destFile, null, null);

        Log.d(TAG, "CONSTRUCTOR WITH NO AUTH");

    }

    OkHttpDownloader(Uri uri, File destFile, String username, String password) throws FileNotFoundException, MalformedURLException {
        super(uri, destFile);

        Log.d(TAG, "CONSTRUCTOR WITH AUTH");

        this.sourceUrl = new URL(urlString);
        this.username = username;
        this.password = password;
    }

    @Override
    protected InputStream getDownloadersInputStream() throws IOException {

        Log.d(TAG, "GET INPUT STREAM FOR " + sourceUrl);

        Log.d(TAG, "RESUME DOWNLOAD: " + resumeDownload);

        OkHttpClient client = getClientBuilder().build(); // new OkHttpClient();
        Request.Builder builder = getRequestBuilder(); // new Request.Builder();
        if (resumeDownload) {
            builder.addHeader("Range", "bytes=" + outputFile.length() + "-");
        }
        Request request = builder.build(); // .url(urlString).build();

        response = client.newCall(request).execute();
        Log.d(TAG, "GOT RESPONSE");

        InputStream is = response.body().byteStream();
        Log.d(TAG, "GOT STREAM");

        return new BufferedInputStream(is);
    }

    @Override
    protected void close() {
        if (response != null) {
            Log.d(TAG, "CLOSE RESPONSE");
            response.close();
        } else {
            Log.d(TAG, "NO RESPONSE");
        }
    }

    @Override
    public boolean hasChanged() {

        Log.d(TAG, "CHECK FILE CHANGED");
        Log.d(TAG, "BUILD REQUEST FOR " + sourceUrl);

        OkHttpClient client = getClientBuilder().build(); // new OkHttpClient();
        Request request = getRequestBuilder() // new Request.Builder()
                //.url(sourceUrl)
                .head()
                .build();

        // TODO: can this be cached?
        response = null;
        Date tempDate = null;

        try {

            Log.d(TAG, "GET RESPONSE (changed)");

            response = client.newCall(request).execute();
            tempDate = response.headers().getDate("Last-Modified");
        } catch (IOException e) {

            Log.d(TAG, "GOT EXCEPTION");
            e.printStackTrace();

            if (response!=null) {
                response.close();
            }
        }

        if (tempDate != null) {
            if (tempDate.getTime() > fileDate) {
                Log.d(TAG, "NEW DATE: " + tempDate.toString());
                fileDate = tempDate.getTime();
                return true;
            } else {
                Log.d(TAG, "SAME DATE: " + tempDate.toString());
            }
        } else {
            Log.d(TAG, "NO DATE");
        }

        return false;
    }

    @Override
    protected long totalDownloadSize() {

        Log.d(TAG, "CHECK DOWNLOAD SIZE");
        Log.d(TAG, "BUILD REQUEST FOR " + sourceUrl);

        OkHttpClient client = getClientBuilder().build(); // new OkHttpClient();
        Request request = getRequestBuilder() // new Request.Builder()
                //.url(sourceUrl)
                .head()
                .build();

        // TODO: can this be cached?
        response = null;
        fileSize = -1L;

        try {

            Log.d(TAG, "GET RESPONSE (SIZE)");

            response = client.newCall(request).execute();
            fileSize = response.body().contentLength();
        } catch (IOException e) {

            Log.d(TAG, "GOT EXCEPTION");
            e.printStackTrace();

            if (response!=null) {
                response.close();
            }
        }

        Log.d(TAG, "FILE SIZE: " + fileSize);

        return fileSize;
    }

    @Override
    public void download() throws ConnectException, IOException, InterruptedException {

        Log.d(TAG, "DO DOWNLOAD FOR " + sourceUrl + " / " + outputFile.getAbsolutePath());

        OkHttpClient client = getClientBuilder().build(); // new OkHttpClient();
        Request request = getRequestBuilder() // new Request.Builder()
                // .url(sourceUrl)
                .head()
                .build();

        Response tmpResponse = client.newCall(request).execute();

        long contentLength = tmpResponse.body().contentLength();
        int statusCode = tmpResponse.code();

        tmpResponse.close();

        switch (statusCode) {
            case HttpURLConnection.HTTP_OK:
                Log.d(TAG, "FILE OK");
                // TODO - can't get file size, force download for now
                contentLength = 1024;
                break;
            case HttpURLConnection.HTTP_NOT_FOUND:
                Log.d(TAG, "FILE NOT FOUND");
                notFound = true;
                return;
            default:
                Log.d(TAG, "FILE CHECK RETURNED: " + statusCode);
        }

        resumeDownload = false;
        long fileLength = outputFile.length();
        if (fileLength > contentLength) {
            Log.d(TAG, "FILE SIZE MISMATCH: " + fileLength + " / " + outputFile.length() + " CLEAN UP");
            FileUtils.deleteQuietly(outputFile);
        } else if (fileLength == contentLength && outputFile.isFile()) {
            Log.d(TAG, "FILE SIZE MATCH: " + fileLength + " / " + outputFile.length()+ " / " + outputFile.isFile() + " NO DOWNLOAD");
            return;
        } else if (fileLength > 0) {
            Log.d(TAG, "FILE INCOMPLETE: " + fileLength + " RESUME DOWNLOAD");
            resumeDownload = true;
        }

        downloadFromStream(resumeDownload);

    }

    public static boolean isSwapUrl(Uri uri) {
        return isSwapUrl(uri.getHost(), uri.getPort());
    }

    public static boolean isSwapUrl(URL url) {
        return isSwapUrl(url.getHost(), url.getPort());
    }

    public static boolean isSwapUrl(String host, int port) {
        return port > 1023 // only root can use <= 1023, so never a swap repo
                && host.matches("[0-9.]+") // host must be an IP address
                && FDroidApp.subnetInfo.isInRange(host); // on the same subnet as we are
    }

    protected Request.Builder getRequestBuilder() {

        Request.Builder builder = new Request.Builder();

        if (isSwapUrl(sourceUrl)) {
            // swap never works with a proxy, its unrouted IP on the same subnet
            builder = builder
                    .url(sourceUrl)
                    .header("Connection", "Close"); // avoid keep alive
        } else {
            if (queryString != null) {
                builder = builder.url(sourceUrl + "?" + queryString);
            } else {
                builder = builder.url(sourceUrl);
            }
        }

        builder = builder.header("User-Agent", Utils.getUserAgent());

        // required to support gzip encoding on old android versions
        if (Build.VERSION.SDK_INT < 19) {
            builder = builder.header("Accept-Encoding", "identity");
        }

        // add authorization header with username / password if needed
        if (username != null && password != null) {
            String authString = username + ":" + password;
            String authString64 = Base64.encodeToString(authString.getBytes(), Base64.NO_WRAP);
            builder = builder.header("Authorization", "Basic " + authString64);
        }

        return builder;
    }

    protected OkHttpClient.Builder getClientBuilder() {

        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        builder = builder.connectTimeout(getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(getTimeout(), TimeUnit.MILLISECONDS)
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .addInterceptor(new CronetInterceptor());

        return builder;
    }
}

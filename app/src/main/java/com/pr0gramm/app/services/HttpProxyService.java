package com.pr0gramm.app.services;

import android.net.Uri;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.System.currentTimeMillis;

/**
 */
@Singleton
public class HttpProxyService extends NanoHttpServer implements ProxyService {
    private static final Logger logger = LoggerFactory.getLogger(HttpProxyService.class);

    private final String nonce;
    private final OkHttpClient okHttpClient;

    @Inject
    public HttpProxyService(OkHttpClient okHttpClient) {
        super(getRandomPort());

        this.okHttpClient = okHttpClient;
        this.nonce = Hashing.md5().hashLong(currentTimeMillis()).toString();

        logger.info("Open simple proxy on port " + getMyPort());
    }

    /**
     * Tries to get a random free port.
     *
     * @return A random free port number
     */
    private static int getRandomPort() {
        return (int) (20000 + (Math.random() * 40000));
    }

    @Override
    public String proxy(String url) {
        // do not proxy twice!
        if (url.contains(nonce) && url.contains("127.0.0.1"))
            return url;

        String encoded = BaseEncoding.base64Url().encode(url.getBytes(Charsets.UTF_8));
        return new Uri.Builder()
                .scheme("http")
                .encodedAuthority("127.0.0.1:" + getMyPort())
                .appendPath(nonce)
                .appendPath(encoded)
                .toString();
    }

    @Override
    public Response serve(IHTTPSession session) {
        logger.info("New request for {}", session.getUri());

        Uri uri = Uri.parse(session.getUri());
        if (!nonce.equals(Iterables.getFirst(uri.getPathSegments(), null))) {
            logger.info("Got request with invalid nonce: {}", uri);
            return new Response(Response.Status.FORBIDDEN, "text/plain", "");
        }

        String url = null;
        try {
            String encodedUrl = uri.getLastPathSegment();

            logger.info("Decode {} as utf8 string now", encodedUrl);
            url = new String(
                    BaseEncoding.base64Url().decode(encodedUrl),
                    Charsets.UTF_8).trim();

            logger.info("Decoded request to {}", url);

            Request request = buildRequest(url, session);
            com.squareup.okhttp.Response response = okHttpClient.newCall(request).execute();

            Response.IStatus status = translateStatus(response.code(), response.message());
            String contentType = response.header("Content-Type", "application/octet-stream");

            InputStream stream;
            final Integer length = Ints.tryParse(response.header("Content-Length", ""));
            if (length != null && length > 0) {
                stream = new FilterInputStream(response.body().byteStream()) {
//                    int read = 0;
//
//                    @Override
//                    public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
//                        int result = super.read(buffer, byteOffset, byteCount);
//                        if(read / 100_000 != (read + result) / 100_000) {
//                            logger.info(format("Approx %1d%% loaded", 100 * read / length));
//                        }
//
//                        read += result;
//                        return result;
//                    }

                    @Override
                    public int available() throws IOException {
                        // fake a large value here, so ByteStreams.limit can fix it :/
                        return Integer.MAX_VALUE;
                    }
                };

                stream = ByteStreams.limit(stream, length.longValue());
            } else {
                stream = response.body().byteStream();
            }

            Response result = new Response(status, contentType, stream);
            result.addHeader("Accept-Range", "bytes");
            result.addHeader("Connection", "close");
            result.setChunkedTransfer(length == null);

            // forward content range header
            String contentRange = response.header("Content-Range");
            if (contentRange != null)
                result.addHeader("Content-Range", contentRange);

            return result;

        } catch (Exception e) {
            logger.error("Could not proxy for url " + url, e);
            return new Response(Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
        }
    }

    private static Request buildRequest(String url, IHTTPSession session) {
        Request.Builder req = new Request.Builder().url(url);

        // forward the range header
        String range = session.getHeaders().get("range");
        if (range != null)
            req = req.addHeader("Range", range);

        return req.build();
    }

    private static Response.IStatus translateStatus(int code, String description) {
        return new Response.IStatus() {
            @Override
            public int getRequestStatus() {
                return code;
            }

            @Override
            public String getDescription() {
                return code + " " + firstNonNull(description, "unknown");
            }
        };
    }
}

package com.example.sentry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import io.sentry.connection.AbstractConnection;
import io.sentry.connection.ConnectionException;
import io.sentry.connection.EventSampler;
import io.sentry.connection.TooManyRequestsException;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.marshaller.Marshaller;

public class CustomConnection extends AbstractConnection{

    private static final String USER_AGENT = "User-Agent";
    private static final String SENTRY_AUTH = "X-Sentry-Auth";

    EventSampler eventSampler;
    Proxy proxy;
    URL sentryUrl;
    Marshaller marshaller;
    boolean bypassSecurity;
    Integer connectionTimeout;
    Integer readTimeout;

    public CustomConnection(URL sentryUrl, String publicKey, String secretKey, Proxy proxy, EventSampler eventSampler){
        super(publicKey, secretKey);
        this.eventSampler = eventSampler;
        this.proxy = proxy;
        this.sentryUrl = sentryUrl;
    }

    /**
     * HostnameVerifier allowing wildcard certificates to work without adding them to the truststore.
     */
    private static final HostnameVerifier NAIVE_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            return true;
        }
    };

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void setBypassSecurity(boolean bypassSecurity) {
        this.bypassSecurity = bypassSecurity;
    }

    public static URL getSentryApiUrl(URI sentryUri, String projectId) {
        try {
            String url = sentryUri.toString() + "api/" + projectId + "/store/";
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Couldn't build a valid URL from the Sentry API.", e);
        }
    }

    public void setConnectionTimeout(Integer timeout) {
        this.connectionTimeout = timeout;
    }

    public void setReadTimeout(Integer timeout) {
        this.readTimeout = timeout;
    }

     /**
     * Opens a connection to the Sentry API allowing to send new events.
     *
     * @return an HTTP connection to Sentry.
     */
    protected HttpURLConnection getConnection() {
        try {
            HttpURLConnection connection;
            if (proxy != null) {
                connection = (HttpURLConnection) sentryUrl.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) sentryUrl.openConnection();
            }

            if (bypassSecurity && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NAIVE_VERIFIER);
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty(USER_AGENT, SentryEnvironment.getSentryName());
            connection.setRequestProperty(SENTRY_AUTH, getAuthHeader());

            if (marshaller.getContentType() != null) {
                connection.setRequestProperty("Content-Type", marshaller.getContentType());
            }

            if (marshaller.getContentEncoding() != null) {
                connection.setRequestProperty("Content-Encoding", marshaller.getContentEncoding());
            }

            return connection;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't set up a connection to the Sentry server.", e);
        }
    }

    @Override
    protected void doSend(Event event) throws ConnectionException {
        if (eventSampler != null && !eventSampler.shouldSendEvent(event)) {
            return;
        }

        HttpURLConnection connection = getConnection();
        try {
            connection.connect();
            OutputStream outputStream = connection.getOutputStream();
            marshaller.marshall(event, outputStream);
            outputStream.close();
            connection.getInputStream().close();
        } catch (IOException e) {
            Long retryAfterMs = null;
            String retryAfterHeader = connection.getHeaderField("Retry-After");
            if (retryAfterHeader != null) {
                try {
                    retryAfterMs = (long) (Double.parseDouble(retryAfterHeader) * 1000L); // seconds to milliseconds
                } catch (NumberFormatException nfe) {
                    System.out.println("Could not parse retry value");
                }
            }

            Integer responseCode = null;
            try {
                responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    
                    return;
                } else if (responseCode == 429) {
                    /*
                    If the response is a 429 we rethrow as a TooManyRequestsException so that we can
                    avoid logging this is an error.
                    */
                    throw new TooManyRequestsException(
                            "Too many requests to Sentry: https://docs.sentry.io/learn/quotas/",
                            e, retryAfterMs, responseCode);
                }
            } catch (IOException responseCodeException) {
                // pass
            }

            String errorMessage = null;
            final InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                //errorMessage = getErrorMessageFromStream(errorStream);
            }
            if (null == errorMessage || errorMessage.isEmpty()) {
                errorMessage = "An exception occurred while submitting the event to the Sentry server.";
            }

            throw new ConnectionException(errorMessage, e, retryAfterMs, responseCode);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public void close() throws IOException {
    }
    
}

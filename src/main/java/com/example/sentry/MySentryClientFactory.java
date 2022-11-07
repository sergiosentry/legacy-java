package com.example.sentry;

import java.net.Proxy;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.connection.EventSampler;
import io.sentry.connection.ProxyAuthenticator;
import io.sentry.connection.RandomEventSampler;
import io.sentry.dsn.Dsn;
import io.sentry.event.helper.ContextBuilderHelper;
import io.sentry.event.helper.ForwardedAddressResolver;
import io.sentry.event.helper.HttpEventBuilderHelper;
import io.sentry.jvmti.FrameCache;
import io.sentry.marshaller.Marshaller;

public class MySentryClientFactory extends DefaultSentryClientFactory {

    private URL sentryApiURL;
    private CustomConnection connection;
    
    @Override
    public SentryClient createSentryClient(Dsn dsn) {
      connection = createHttpConnection(dsn);
      SentryClient sentryClient = new SentryClient(connection, getContextManager(dsn));
  
      ForwardedAddressResolver forwardedAddressResolver = new ForwardedAddressResolver();
      sentryClient.addBuilderHelper(new HttpEventBuilderHelper(forwardedAddressResolver));
  
      sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));
      return configureSentryClient(sentryClient, dsn);
    }

    private SentryClient configureSentryClient(SentryClient sentryClient, Dsn dsn) {
      String release = getRelease(dsn);
        if (release != null) {
            sentryClient.setRelease(release);
        }

        String dist = getDist(dsn);
        if (dist != null) {
            sentryClient.setDist(dist);
        }

        String environment = getEnvironment(dsn);
        if (environment != null) {
            sentryClient.setEnvironment(environment);
        }

        String serverName = getServerName(dsn);
        if (serverName != null) {
            sentryClient.setServerName(serverName);
        }

        Map<String, String> tags = getTags(dsn);
        if (!tags.isEmpty()) {
            for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
                sentryClient.addTag(tagEntry.getKey(), tagEntry.getValue());
            }
        }

        Set<String> mdcTags = getMdcTags(dsn);
        if (!mdcTags.isEmpty()) {
            for (String mdcTag : mdcTags) {
                sentryClient.addMdcTag(mdcTag);
            }
        }

        Map<String, String> extra = getExtra(dsn);
        if (!extra.isEmpty()) {
            for (Map.Entry<String, String> extraEntry : extra.entrySet()) {
                sentryClient.addExtra(extraEntry.getKey(), extraEntry.getValue());
            }
        }

        if (getUncaughtHandlerEnabled(dsn)) {
            sentryClient.setupUncaughtExceptionHandler();
        }

        for (String inAppPackage : getInAppFrames(dsn)) {
            FrameCache.addAppPackage(inAppPackage);
        }

        return sentryClient;
            
    }

   @Override
    public CustomConnection createHttpConnection(Dsn dsn) {
      URL sentryApiUrl = CustomConnection.getSentryApiUrl(dsn.getUri(), dsn.getProjectId());

      String proxyHost = getProxyHost(dsn);
      String proxyUser = getProxyUser(dsn);
      String proxyPass = getProxyPass(dsn);
      int proxyPort = getProxyPort(dsn);

      Proxy proxy = null;
      if (proxyHost != null) {
          InetSocketAddress proxyAddr = new InetSocketAddress(proxyHost, proxyPort);
          proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
          if (proxyUser != null && proxyPass != null) {
              Authenticator.setDefault(new ProxyAuthenticator(proxyUser, proxyPass));
          }
      }

      Double sampleRate = getSampleRate(dsn);
      EventSampler eventSampler = null;
      if (sampleRate != null) {
          eventSampler = new RandomEventSampler(sampleRate);
      }

      CustomConnection httpConnection = new CustomConnection(sentryApiUrl, dsn.getPublicKey(),
          dsn.getSecretKey(), proxy, eventSampler);

      Marshaller marshaller = createMarshaller(dsn);
      httpConnection.setMarshaller(marshaller);

      int timeout = getTimeout(dsn);
      httpConnection.setConnectionTimeout(timeout);

      int readTimeout = getReadTimeout(dsn);
      httpConnection.setReadTimeout(readTimeout);

      boolean bypassSecurityEnabled = getBypassSecurityEnabled(dsn);
      httpConnection.setBypassSecurity(bypassSecurityEnabled);

      return httpConnection;
    }

    public HttpURLConnection getConnection(){
      URLConnection urlConnection = null;
      try {
        urlConnection = sentryApiURL.openConnection();
      } catch(Exception e) {  
        System.out.println("could not read connection");
      }

      return (HttpURLConnection) urlConnection;
    }
}

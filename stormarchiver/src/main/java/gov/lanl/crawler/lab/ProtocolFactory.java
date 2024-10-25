package gov.lanl.crawler.lab;

import com.digitalpebble.stormcrawler.util.ConfUtils;
import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.storm.Config;
import org.apache.storm.shade.org.apache.http.conn.ssl.X509HostnameVerifier;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;


public class ProtocolFactory {

    private static final org.slf4j.Logger LOG = LoggerFactory
            .getLogger(ProtocolFactory.class);
    private HashMap<String, HttpClientBuilder> clients = new HashMap<>();
    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER = new PoolingHttpClientConnectionManager();

    public ProtocolFactory(Config conf, String userAgent) throws Exception {

        // allow up to 200 connections or same as the number of threads used for
        // fetching
        int maxFetchThreads = ConfUtils.getInt(conf, "fetcher.threads.number",
                200);
        CONNECTION_MANAGER.setMaxTotal(maxFetchThreads);

        CONNECTION_MANAGER.setDefaultMaxPerRoute(20);

        HttpClientBuilder httpBuilder = HttpClients.custom().setUserAgent(userAgent)
                .setConnectionManager(CONNECTION_MANAGER)
                .setConnectionManagerShared(true).disableRedirectHandling()
                .disableAutomaticRetries();

        SSLContextBuilder sslBuilder = SSLContexts.custom();
        sslBuilder.loadTrustMaterial(null, new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                return true;
            }
        });
        SSLContext sslContext = sslBuilder.build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                sslContext, new X509HostnameVerifier() {
            @Override
            public void verify(String host, SSLSocket ssl)
                    throws IOException {
            }

            @Override
            public void verify(String host, X509Certificate cert)
                    throws SSLException {
            }

            @Override
            public void verify(String host, String[] cns,
                               String[] subjectAlts) throws SSLException {
            }

            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
                .<ConnectionSocketFactory> create()
                .register("https", sslsf)
                .register("http", new PlainConnectionSocketFactory())
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry);
        HttpClientBuilder httpsBuilder = HttpClients.custom()
                .setConnectionManager(cm)
                .setConnectionManagerShared(true);

        clients.put("http", httpBuilder);
        clients.put("https", httpsBuilder);

        HttpClientBuilder httpProxyBuilder = HttpClients.custom().setUserAgent(userAgent)
                .setConnectionManager(CONNECTION_MANAGER)
                .setConnectionManagerShared(true).disableRedirectHandling()
                .disableAutomaticRetries();

        HttpClientBuilder httpsProxyBuilder = HttpClients.custom()
                .setConnectionManager(cm)
                .setConnectionManagerShared(true);


        String proxyHost = ConfUtils.getString(conf, "http.proxy.host", null);
        int proxyPort = ConfUtils.getInt(conf, "http.proxy.port", 8080);

        boolean useProxy = proxyHost != null && proxyHost.length() > 0;

        // use a proxy?
        if (useProxy) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(
                    proxy);
            httpProxyBuilder.setRoutePlanner(routePlanner);
            httpsProxyBuilder.setRoutePlanner(routePlanner);
        }
        clients.put("http+proxy", httpProxyBuilder);
        clients.put("https+proxy", httpsProxyBuilder);
    }

    public HttpClientBuilder getClientForProtocol(URL url, Boolean withProxy) {
        LOG.info("Found protocol builder for " + url.getProtocol());
        LOG.info("Using proxy: " + withProxy);
        String key = url.getProtocol();
        if (withProxy) {
            key += "+proxy";
        }
        return clients.get(key);
    }
}

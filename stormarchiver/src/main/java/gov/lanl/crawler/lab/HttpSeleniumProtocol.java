package gov.lanl.crawler.lab;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.protocol.AbstractHttpProtocol;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.ConfUtils;

import org.apache.http.*;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.storm.Config;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

//not used, depricated

public class HttpSeleniumProtocol extends AbstractHttpProtocol {

    private static final org.slf4j.Logger LOG = LoggerFactory
            .getLogger(HttpSeleniumProtocol.class);
    //private HttpClientBuilder builder;
    private RequestConfig requestConfig;
    private BrowserFactory browserFactory;
    private String userAgent;
    private ProtocolFactory protocolFactory;
    private Config config;

    @Override
    public void configure(final Config conf) {

        super.configure(conf);

        userAgent = getAgentString(
                ConfUtils.getString(conf, "http.agent.name"),
                ConfUtils.getString(conf, "http.agent.version"),
                ConfUtils.getString(conf, "http.agent.description"),
                ConfUtils.getString(conf, "http.agent.url"),
                ConfUtils.getString(conf, "http.agent.email"));


        int timeout = ConfUtils.getInt(conf, "http.timeout", 10000);

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
        requestConfigBuilder.setSocketTimeout(timeout);
        requestConfigBuilder.setConnectTimeout(timeout);
        requestConfigBuilder.setConnectionRequestTimeout(timeout);
        requestConfigBuilder.setCookieSpec(CookieSpecs.STANDARD);
        requestConfig = requestConfigBuilder.build();

        try {
            protocolFactory = new ProtocolFactory(conf, userAgent);
        }
        catch (Exception e) {
            LOG.info("PROTOCOL FACTORY Failed! \nException: " + e.getMessage());
        }
        config = conf;
    }

    @Override
    public ProtocolResponse getProtocolOutput(String url, Metadata md)
            throws Exception {

        URL request_url = new URL(url);

        HttpClientBuilder builder;
        try {
            builder = protocolFactory.getClientForProtocol(request_url, false);
        }
        catch (Exception e) {
            LOG.info("PROTOCOL FACTORY Failed! \nException: " + e.getMessage());
            return null;
        }

        HttpGet httpGet = new HttpGet(url);
        httpGet.setConfig(requestConfig);

        LOG.info("Protocol: Fetching url - {}", url);
        try (CloseableHttpClient httpClient = builder.build()) {
            HttpResponse response = httpClient.execute(httpGet);

            StatusLine statusLine = response.getStatusLine();
            int status = statusLine.getStatusCode();
            LOG.info("Protocol: Response status - {}", status);

            // #TODO: Check if status is 200
            // #TODO: does the library follow 302, etc?

            HeaderIterator iter = response.headerIterator();
            String contentType = "";
            while (iter.hasNext()) {
                Header header = iter.nextHeader();
                if (header.getName().toLowerCase(Locale.ROOT).equals("content-type")) {
                    contentType = header.getValue();
                }
            }
            LOG.info("Protocol: Content-Type: {}", contentType);
            if (contentType.contains("text") && !contentType.equals("text/plain")) {
                LOG.info("Protocol: invoking selenium");
                getBrowserResponse(url);
            } else {
                // we already have the body of the response in the warc.
                // (unlikely scenario) check if the body should be analyzed for additional links to scrape.
                LOG.info("Protocol: non text content-type. warcproxy should have this covered.");
                // TODO: use new builder to fetch response WITH PROXY
            }

            return (ProtocolResponse) response;
        } catch (IOException e) {
            LOG.info("Exception: " + e.getMessage());
            return null;
        }
    }

    private void getBrowserResponse(String url) {

        browserFactory = new BrowserFactory(config);
        WebDriver driver = browserFactory.getBrowserDriver();

        driver.get(url);

        LOG.info("SELENIUM: Title - }", driver.getTitle());
        LOG.info("SELENIUM: Executing JS");
        // #TODO: Check if the driver will block until JS execution finishes.
        ((JavascriptExecutor) driver).executeScript(browserFactory.jsAutoScroll);

        LOG.info("SELENIUM: JS execution is complete.");
        //LOG.info("SELENIUM: Body:\n {}\n", driver.getPageSource());

    }
}


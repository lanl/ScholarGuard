package gov.lanl.crawler.lab;

import com.digitalpebble.stormcrawler.util.ConfUtils;
import org.apache.http.HttpHost;
import org.apache.storm.Config;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

//old depricated file
public class BrowserFactory {

    private static final org.slf4j.Logger LOG = LoggerFactory
            .getLogger(BrowserFactory.class);
    private WebDriver driver;
    static String jsCountHref = "var a = document.getElementsByTagName('a'); return a.length;";

    static String jsAutoScroll =
            "function autoScroll() {" +
                    "if (window.scrollY + window.innerHeight < Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)) {" +
                    "window.scrollBy(0, 400);" +
                    "scrollDelay = setTimeout(autoScroll, 1000);" +
                    "}" +
                    "else {" +
                    "return;" +
                    "}" +
                    "}" +
                    "autoScroll();";

    static String pywbAutoScroll =
            "var sid = undefined;" +
                    "var scroll_timeout = 2000;" +
                    "var lastScrolled = undefined;" +

                    "function scroll() {" +
                    "if (window.scrollY + window.innerHeight < Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)) {" +
                    "window.scrollBy(0, 200);" +
                    "}" +
                    "}" +

                    "setTimeout(scroll(), 2000);";

    public BrowserFactory(Config conf) {

        String browserBinary;
        String browserName;
        String warcProxy;
        browserBinary = ConfUtils.getString(conf, "browser.binary");
        browserName = ConfUtils.getString(conf, "browser.name");
        String remoteBrowserString = ConfUtils.getString(conf, "browser.remote.url", null);
        if (!browserName.equals("chrome") && !browserName.equals("firefox")) {
            browserName = "firefox";
            browserBinary = null;
        }
        String browserDriver = ConfUtils.getString(conf, "browser.driver");

        if (remoteBrowserString != null) {
            LOG.info("Remote Docker browser URL found: " + remoteBrowserString);
        }
        else {
            LOG.info("invoking local browser: " + browserName);
            LOG.info("local browser binary path: " + browserBinary);
        }

        String proxyHost = ConfUtils.getString(conf, "http.proxy.host", null);
        int proxyPort = ConfUtils.getInt(conf, "http.proxy.port", 8080);

        boolean useProxy = proxyHost != null && proxyHost.length() > 0;
        if (!useProxy) {
            warcProxy = new HttpHost("localhost", proxyPort).toHostString();
        }
        else {
            warcProxy = new HttpHost(proxyHost, proxyPort).toHostString();
        }

        Proxy wProxy = new Proxy();
        wProxy.setHttpProxy(warcProxy);
        wProxy.setSslProxy(warcProxy);
        wProxy.setNoProxy("localhost");

        URL remoteBrowserUrl = null;
        if (remoteBrowserString != null) {
            try {
                remoteBrowserUrl = new URL(remoteBrowserString);
            }
            catch (MalformedURLException e) {
                LOG.info("Remote Browser URL is malformed: \n" + e.getMessage());
                return;
            }
        }

        if (browserName.equals("firefox")) {

            DesiredCapabilities capabilities = DesiredCapabilities.firefox();
            capabilities.setCapability(CapabilityType.PROXY, wProxy);

            if (remoteBrowserUrl != null) {
                LOG.info("Initiating Remote Web Driver for Firefox.");
                driver = new RemoteWebDriver(remoteBrowserUrl, capabilities);
            }
            else if (browserBinary != null) {
                System.setProperty("webdriver.firefox.bin", browserBinary);
                System.setProperty("webdriver.gecko.driver", browserDriver);
            }

            if (driver == null) {
                LOG.info("Initiating Local Web Driver for Firefox.");
                driver = new FirefoxDriver(capabilities);
            }
        }
        else if (browserName.equals("chrome")) {


            Map<String, Object> chromeOptions = new HashMap<>();
            DesiredCapabilities capabilities = DesiredCapabilities.chrome();
            capabilities.setCapability("proxy", wProxy);

            if (remoteBrowserUrl != null) {
                LOG.info("Initiating Remote Web Driver for Chrome.");
                driver = new RemoteWebDriver(remoteBrowserUrl, capabilities);
            }
            else if (browserBinary != null) {
                chromeOptions.put("binary", browserBinary);
                System.setProperty("webdriver.chrome.driver", browserDriver);
                capabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
            }

            if (driver == null) {
                LOG.info("Initiating Local Web Driver for Chrome.");
                driver = new ChromeDriver(capabilities);
            }
        }
    }

    public WebDriver getBrowserDriver() {
        return driver;
    }
}

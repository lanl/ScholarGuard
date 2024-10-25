package gov.lanl.crawler.proto;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.storm.Config;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.digitalpebble.stormcrawler.Metadata;
import org.apache.commons.cli.Options;
//import org.apache.commons.io.IOUtils;
//import org.apache.commons.lang3.time.FastDateFormat;

import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.protocol.selenium.NavigationFilters;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
//import com.github.bogdanlivadariu.gifwebdriver.GifWebDriver;
import com.opencsv.CSVWriter;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import gov.lanl.crawler.hash.Shingle;
import gov.lanl.crawler.hash.SimHash;
import net.lightbody.bmp.BrowserMobProxy;
//import net.lightbody.bmp.BrowserMobProxyServer;
//import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
//import net.lightbody.bmp.proxy.CaptureType;

//import java.net.InetSocketAddress;

import org.apache.storm.utils.Utils;
//import com.automation.remarks.video.annotations.*;

public class TraceTest {
	protected LinkedBlockingQueue<RemoteWebDriver> drivers;
	protected LinkedBlockingQueue<Integer> ports;
	private NavigationFilters filters;
	Config conf;
	BrowserMobProxy proxyserver;
	String host;
	int port_;
	String warcdir;
	Process p = null;
	Integer iport;
	String aport;
	static String crawlurl;
	String driverdir;
	String urlp = null;
	String tracep = null;

	// CSVWriter cwr = null;

	public static SimpleDateFormat timeTravelMachineFormatter;
	private final static Pattern TYPE_SUBTYPE_EXTRACTION_REGEX = Pattern.compile("(.+)/(.+)");
	private final static Pattern TEXT_SUBTYPES_MATCHER = Pattern.compile(
			"(txt|text|plain|html|atom|xml|xhtml|postscript|rss|vcard|rtf|csv|json|perl|ruby|java|asp|php|doc|py|c|cc|c++|cxx|m|h)");
	static {
		TimeZone tz = TimeZone.getTimeZone("GMT");
		timeTravelMachineFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
		timeTravelMachineFormatter.setTimeZone(tz);

	}

	public static Boolean mimetype_m(String truncatedMimeType) {
		Matcher type_subtype_matcher = TYPE_SUBTYPE_EXTRACTION_REGEX.matcher(truncatedMimeType);

		String primaryType = null;
		String secondaryType = null;

		// if string is a type/subtype record
		if (type_subtype_matcher.matches()) {
			// primary type is first match element
			primaryType = type_subtype_matcher.group(1);
			secondaryType = type_subtype_matcher.group(2);
			// System.out.println("secondary " + secondaryType);
			if (TEXT_SUBTYPES_MATCHER.matcher(secondaryType).matches()) {
				return true;
			}
		}
		// else if string is strictly a type record
		else {
			primaryType = truncatedMimeType;
		}
		// System.out.println("primary " + primaryType);
		if (TEXT_SUBTYPES_MATCHER.matcher(primaryType).matches()) {
			return true;
		}

		return false;
	}

	public static void main(String[] args) throws Exception {

		TraceTest t = new TraceTest();
		if (args != null) {
			if (args.length > 0) {
				t.urlp = args[0];

			}
			if (args.length > 1) {
				t.tracep = args[1];
			}
		}
	
		 //t.urlp ="https://gitlab.com/occam-archive/occam/-/issues";
		
		// t.urlp ="https://gitlab.com/eaasi/eaasi-installer/-/issues";
		 t.urlp ="https://gitlab.com/occam-archive/occam";
				 //https://gitlab.com/ansible-postgres-xl/postgres-xl-cluster/-/issues/?sort=created_date&state=all&first_page_size=20";
         //t.tracep="FILE:///Users/Lyudmila/Downloads/gitlab_issues_pulls_next_2.json";
         t.tracep="FILE:///Users/Lyudmila/Downloads/gitlabmerge.json";
		TimeZone tz = TimeZone.getTimeZone("GMT");
		timeTravelMachineFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
		timeTravelMachineFormatter.setTimeZone(tz);
		Config conf = new Config();
		CSVWriter wr = null;
		// result file
		File resfile = new File("./url.txt");
		try {
			wr = new CSVWriter(new FileWriter(resfile));
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		// loads the default configuration file
		// Map defaultSCConfig = Utils.findAndReadConfigFile("crawler-default.yaml",
		// false);
		Map defaultSCConfig = Utils.findAndReadConfigFile("crawler-conf-tracertest.yaml", false);
		conf.putAll(ConfUtils.extractConfigElement(defaultSCConfig));

		org.apache.commons.cli.Options options = new Options();
		options.addOption("c", true, "configuration file");

		

		t.configure(conf);

		
		Metadata metadata = new Metadata();
		if (t.tracep != null) {
			metadata.addValue("trace", t.tracep);
		}

		t.getProtocolOutput(crawlurl, metadata);
	}

	public void configure(Config conf) {
		this.conf = conf;
		// super.configure(conf);
		host = ConfUtils.getString(conf, "http.proxy.host", "172.17.0.1");
		port_ = ConfUtils.getInt(conf, "http.proxy.port", 0);
		driverdir = ConfUtils.getString(conf, "browser.driver", "/usr/local/bin/chromedriver");
		//driverdir = "/Users/Lyudmila/Downloads/chromedriver_103";
		driverdir = "/Users/Lyudmila/Downloads/chromedriver_106";
		System.out.println(driverdir);
		warcdir = ConfUtils.getString(conf, "http.proxy.dir", "./warcs");
		crawlurl = ConfUtils.getString(conf, "crawlurl", "https://www.heise.de/");

		if (urlp != null) {
			crawlurl = urlp;
		}
		if (tracep == null) {
			conf.put("navigationfilters.config.file", "boundary-filters.json");
		}
		
		filters = NavigationFilters.fromConf(conf);

		drivers = new LinkedBlockingQueue<>();
		ports = new LinkedBlockingQueue<>();
		int i = 5;
		int port = port_;
		for (i = 0; i < 5; i++) {
			ports.add(port);
			port = port + 1;
		}

		File resfile = new File("./stats.txt");

	}

	public JsonNode make_json(String trace_file) {
		String json = "{" + "\"com.digitalpebble.stormcrawler.protocol.selenium.NavigationFilters\": ["
				+ "{\"class\": \"gov.lanl.crawler.boundary.RemotePortalFilter\"," + "\"name\": \"tracetest\","
				+ "\"params\": {" + " \"portalFile\": \"" + trace_file + "\"" + "}" + "}]}";
		JsonNode confNode = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			confNode = mapper.readTree(json);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return confNode;
	}

	public ChromeOptions chrome_options(String dir) {
		ChromeOptions options = new ChromeOptions();
		options.setExperimentalOption("useAutomationExtension", false);
		
		 options.addArguments(
				   "--verbose",
				   "--disable-web-security",
				   "--ignore-certificate-errors",
				   "--allow-running-insecure-content",
				   "--allow-insecure-localhost",
				   "--no-sandbox",
				   "--disable-gpu",
				   //"--proxy-server=http://"+proxyInfo +"\"",
				   "--ignore-ssl-errors",
				   "--disable-notifications",
				   "--disable-extenstions",
				   "disable-infobars",
				   "--disable-extenstions"
				  );

		// options.addArguments(Arrays.asList("--start-maximized"));
		// String chromeDriverPath = "/usr/local/bin/chromedriver";
		// String chromeDriverPath ="/Users/Lyudmila/Downloads/chromedriver";
		// System.setProperty("webdriver.chrome.driver", driverdir);

		// options.addArguments("--headless");
		options.addArguments("--ignore-certificate-errors");
		options.addArguments("--ignore-ssl-errors");
		options.addArguments("--disable-notifications");// Disables the Web Notification and the Push APIs.
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--ignore-gpu-blacklist");
		options.addArguments("--safebrowsing-disable-download-protection");
		//options.addArguments("--headless");
		HashMap<String, Object> prefs = new HashMap<>();

		 prefs.put("download.directory_upgrade",true);
		 prefs.put("download.default_directory",dir);
		prefs.put("download.prompt_for_download", false);
		prefs.put("safebrowsing.enabled",false);
		// prefs.put("download.extensions_to_open", "application/zip");
		prefs.put("profile.default_content_settings.popups", 0);
		prefs.put("profile.default_content_setting_values.automatic_downloads",1);
		// //download multiple files automatically
		// prefs.put("savefile.default_directory",dir );
		//_prefs.put("profile.content_settings.exceptions.automatic_downloads.*.setting", 1);
		
		options.setExperimentalOption("prefs", prefs);

		return options;
	}

	public RemoteWebDriver init_local_driver(String pport) {
		System.out.println("in init driver");
		// see https://github.com/SeleniumHQ/selenium/wiki/DesiredCapabilities
		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities = DesiredCapabilities.chrome();
		capabilities.setJavascriptEnabled(true);

		String userAgentString = "user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.6 Safari/537.36";

		// custom capabilities
		Map<String, Object> confCapabilities = (Map<String, Object>) conf.get("selenium.capabilities");
		System.out.println(confCapabilities.toString());
		if (confCapabilities != null) {
			Iterator<Entry<String, Object>> iter = confCapabilities.entrySet().iterator();
			while (iter.hasNext()) {
				Entry<String, Object> entry = iter.next();
				Object val = entry.getValue();
				// substitute variable $useragent for the real value
				if (val instanceof String && "$useragent".equalsIgnoreCase(val.toString())) {
					val = userAgentString;
				}
				capabilities.setCapability(entry.getKey(), entry.getValue());
				// Object m = capabilities.getCapability("proxy");
			}
		}

		
		// Selenium or HTTP client configuration goes here
		String dir = "/Users/Lyudmila/stormarchiver/warcs/warcstore/";
		ChromeOptions options = chrome_options(dir);

		// String chromeDriverPath = "/usr/local/bin/chromedriver";

		System.setProperty("webdriver.chrome.driver", driverdir);

		
		if (port_ != 0) {
			options.addArguments("--proxy-server=http://" + host + ":" + pport);
		}
		//capabilities.setCapability(ChromeOptions.CAPABILITY, options);
		  capabilities.setCapability("goog:chromeOptions", options);
		// System.setProperty(ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY,
		// System.getProperty("user.dir") + File.separator +
		// "/target/chromedriver.log");

		// ChromeDriverService service = new
		// ChromeDriverService.Builder().usingAnyFreePort().withVerbose(false).build();
		// try { service.start(); } catch (IOException e1) { // TODO Auto-generated
		// catch block e1.printStackTrace(); }

		// System.out.println(service.getUrl());

		

		
		RemoteWebDriver driver = new ChromeDriver(capabilities);
		// RemoteWebDriver driver = new ChromeDriver(options);
		// WebDriver adriver = new GifWebDriver(driver);

		
		// load adresses from config
		List<String> addresses = ConfUtils.loadListFromConf("selenium.addresses", conf);
		if (addresses.size() == 0) {
			throw new RuntimeException("No value found for selenium.addresses");
		}
		try {
			
			System.out.println("returning driver");
			driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
			// drivers.add(driver);
			return driver;
			// }
			// }
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// return null;
	}

	public Process startProxyServer(String pport) {

		// String cmd = "/usr/local/bin/warcprox -b 172.17.0.1 -p 8080 --certs-dir certs
		// -d warcs -g md5 -v --trace -s 2000000000 > out.txt ";
		String warcproxydir = warcdir + "/output" + pport + ".db";
		ProcessBuilder probuilder = new ProcessBuilder("/anaconda2/bin/warcprox", "-b", host, "-p", pport,
				"--certs-dir", "certs", "-d", warcdir, "-g", "md5", "-v", "--trace", "-s", "6000000000",
				"--dedup-db-file=/dev/null", "--stats-db-file=/dev/null");

		// ProcessBuilder probuilder = new ProcessBuilder("warcprox", "-b", host, "-p",
		// pport, "--certs-dir", "certs",
		// "-d", warcdir + pport, "-g", "md5", "-v", "--trace", "-s", "1000000",
		// "--dedup-db-file=/dev/null",
		// "--stats-db-file=/dev/null");

		try {

			File OutputFile = new File(warcdir + "/output" + pport + ".txt");
			probuilder.redirectErrorStream(true);
			// probuilder.directory(new File(warcdir));
			probuilder.redirectOutput(OutputFile);

			Map<String, String> envMap = probuilder.environment();

			// checking map view of environment
			// for (Map.Entry<String, String> entry : envMap.entrySet()) {
			// checking key and value separately
			// System.out.println("Key = " + entry.getKey() + ", Value = " +
			// entry.getValue());
			// }

			p = probuilder.start();
			p.waitFor(5, TimeUnit.SECONDS);
			if (p.isAlive())
				System.out.println("proxy alive");

			return p;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;

	}

	/** Returns the first available driver **/
	private final RemoteWebDriver getDriver() {
		try {

			return drivers.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	private final Integer getPort() {
		try {
			return ports.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	public void processHar(String url) {

		String fname = url;
		fname = fname.replaceAll("http://doi.org/", "");
		fname = fname.replaceAll("https://doi.org/", "");

		fname = fname.replaceAll("/", "");

		// System.out.println(url);
		CSVWriter wr = null;
		// result file
		File resfile = new File("./urls.txt");
		try {
			wr = new CSVWriter(new FileWriter(resfile));
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		// har processing
		Har har = proxyserver.getHar();
		File file = new File(fname + ".har");
		try {
			har.writeTo(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		HarReader harReader = new HarReader();
		de.sstoehr.harreader.model.Har harr = null;
		try {
			harr = harReader.readFromFile(new File(fname + ".har"));
		} catch (HarReaderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<HarEntry> entries = harr.getLog().getEntries();
		for (HarEntry entry : entries) {
			String redirurl = "";
			String sstatus = "";
			String len = "";
			String ctype = "";
			String lu = "";
			String sim = "";
			String etag = "";
			String lm = "";
			String murl = entry.getRequest().getUrl();
			Date stime = entry.getStartedDateTime();
			String method = entry.getRequest().getMethod().name();
			int status = entry.getResponse().getStatus();

			List<HarHeader> hs = entry.getResponse().getHeaders();
			Iterator it = hs.iterator();

			while (it.hasNext()) {
				HarHeader h = (HarHeader) it.next();
				String name = h.getName().toLowerCase();
				if (name.equals("location")) {
					redirurl = h.getValue();
				}
				if (name.equals("last-modified")) {
					lm = h.getValue();
				}
				if (h.getName().equalsIgnoreCase("Content-Length")) {
					len = h.getValue();
				}
				if (h.getName().equalsIgnoreCase("Content-Type")) {
					ctype = h.getValue();
				}
				if (h.getName().equalsIgnoreCase("ETag")) {
					etag = h.getValue();

				}
				if (status == 200) {
					// entry.getRequest().getUrl();

					if (mimetype_m(ctype)) {
						// if (ctype.contains("html")|| ctype.contains("text")) {
						String htext = entry.getResponse().getContent().getText();

						// Base64.Decoder decoder = Base64.getDecoder();

						byte[] rawData = null;

						// byte[] decodedByteArray = decoder.decode(encodedString);
						// rawData = IOUtils.toByteArray(re, re.available());
						rawData = htext.getBytes();
						String html = new String(rawData);
						long simhash1 = SimHash.computeSimHashFromString(Shingle.shingles(html));
						System.out.println(simhash1);
						sim = String.valueOf(simhash1);
					}

				}

			} // while
			String nextLine[] = { String.valueOf(status), murl, redirurl, timeTravelMachineFormatter.format(stime), len,
					ctype, sim, etag, lm };
			wr.writeNext(nextLine);
		}
	}

	// @Video
	public ProtocolResponse getProtocolOutput(String url, Metadata metadata) throws Exception {
		System.out.println(url);
		MyScreenRecorder.startRecording("gitnavTest");
		long start0 = System.currentTimeMillis();
		if (iport == null) {
			while ((iport = getPort()) == null) {
			}
		}
		aport = Integer.toString(iport);
		System.out.println("port" + aport);
		// }
		// p = startProxyServer(aport);
		/*
		 * if (p == null) { p = startProxyServer(aport);
		 * System.out.println("proxy started"); }
		 */
		// System.out.println("initializing selenium driver");
		long start = System.currentTimeMillis();
		RemoteWebDriver driver = init_local_driver(aport);
		//
		// proxyserver.newHar("myhr");
		// RemoteWebDriver driver=null;
		// while ((driver = getDriver()) == null) {

		// }

		try {
			// This will block for the page load and any
			// associated AJAX requests

			driver.get(url);
			String u = driver.getCurrentUrl();
			System.out.println("original url:" + url);
			System.out.println("current url:" + u);
			System.out.println(metadata.asMap().toString());
			// if the URL is different then we must have hit a redirection
			if (!u.equalsIgnoreCase(url)) {
				System.out.println("redirect");
				// String event = metadata.getFirstValue("event");
				// byte[] content = new byte[] {};
				// metadata = new Metadata();
				metadata.addValue("_redirTo", u);
				// metadata.addValue("event", event);
				// return new ProtocolResponse(content, 307, metadata);
			}

			ProtocolResponse response = null;
			System.out.println("applying filters");
			metadata.addValue("slowmode", "true");
			if (!driver.getTitle().contains("404")) {
				response = filters.filter(driver, metadata);
			}
			if (response == null) {
				// if no filters got triggered
				System.out.println("no filters get triggered");
				byte[] content = driver.getPageSource().getBytes();
				response = new ProtocolResponse(content, 200, metadata);
			}
			// har processing
			// processHar(url);
			// proxyserver.stop();
			MyScreenRecorder.stopRecording();
			return response;
		}
		// catch (Exception e) {
		// e.printStackTrace();
		// }
		finally {

			long end = System.currentTimeMillis();
			long dur = (end - start);
			Thread.sleep(3000);
			try {
				driver.quit();

				// DevToolsUtil.webSocket.disconnect();
				// DevToolsUtil.service.stop();
			} catch (Exception ee) {
				System.out.println("have problem to close driver");
			}

			// if (p != null) {
			// p.waitFor(5, TimeUnit.SECONDS);
			// p.getOutputStream().close();
			// p.getInputStream().close();
			// p.destroy();

			// int code = p.exitValue();
			long end0 = System.currentTimeMillis();
			long dur0 = (end0 - start0);
			// p.waitFor(7, TimeUnit.SECONDS);
			// if (p.isAlive()) {
			// p.destroyForcibly();
			// }
			// System.out.println("proxy destroyed");
			// List result = getWarcNames(warcdir + aport);
			// result.forEach((a) -> System.out.println("warc " + a));
			// String commaSeparatedValues = String.join(", ", result);
			// metadata.addValue("warcs", commaSeparatedValues);
			metadata.addValue("selSessionDur", String.valueOf(dur));
			metadata.addValue("proxyDur", String.valueOf(dur0));
			System.out.println("added meta");
			// ports.put(iport);

			// }

			// drivers.put(driver);

		}
	}

}

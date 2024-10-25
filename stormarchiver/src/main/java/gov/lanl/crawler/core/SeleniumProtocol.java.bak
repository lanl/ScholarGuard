package gov.lanl.crawler.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

/*
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * DigitalPebble licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.storm.Config;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.protocol.AbstractHttpProtocol;
import com.digitalpebble.stormcrawler.protocol.HttpHeaders;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.protocol.selenium.NavigationFilters;
import com.digitalpebble.stormcrawler.util.ConfUtils;

//import gov.lanl.crawler.proto.MyScreenRecorder;


public abstract class SeleniumProtocol extends AbstractHttpProtocol {

	protected static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SeleniumProtocol.class);

	protected LinkedBlockingQueue<RemoteWebDriver> drivers;
	protected static  LinkedBlockingQueue<Integer> ports;
	private NavigationFilters filters;
	Config conf;

	String host;
	int port_;
	String warcdir;

	@Override
	public void configure(Config conf) {
		System.out.println("initializing protocol");
		this.conf = conf;
		super.configure(conf);
		host = ConfUtils.getString(conf, "http.proxy.host", "172.17.0.1");
		port_ = ConfUtils.getInt(conf, "http.proxy.port", 0);
		warcdir = ConfUtils.getString(conf, "http.proxy.dir", "./warcs");
		filters = NavigationFilters.fromConf(conf);
		drivers = new LinkedBlockingQueue<>();
		if (ports==null)     {
			System.out.println("initializing ports");	
		ports = new LinkedBlockingQueue<>(5);
		int i = 5;
		int port = port_;
		for (i = 0; i < 5; i++) {
			ports.add(port);
			port = port + 1;
			
		}
		Iterator iter = ports.iterator();
		while (iter.hasNext()) {
		    int po=(int) iter.next();
		    System.out.println(po);
		}
		}
	}

	// HttpProxyServer server;

	public static String shortUUID() {
		UUID uuid = UUID.randomUUID();
		long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
		return Long.toString(l, Character.MAX_RADIX);
	}

	public Process startProxyServer(String pport) {

		// String cmd = "/usr/local/bin/warcprox -b 172.17.0.1 -p 8080 --certs-dir certs
		// -d warcs -g md5 -v --trace -s 2000000000 > out.txt ";
        String warcproxydir = warcdir + "/output" + pport +".db";
		ProcessBuilder probuilder = new ProcessBuilder("warcprox", "-b", host, "-p", pport, "--certs-dir", "certs",
				"-d", warcdir + pport, "-g", "md5", "-v", "--trace", "-s", "8000000000", 
				//"--dedup-db-file=/dev/null",
				"--dedup-db-file="+warcproxydir,
				"--stats-db-file=/dev/null");
		
	//	ProcessBuilder probuilder = new ProcessBuilder("warcprox", "-b", host, "-p", pport, "--certs-dir", "certs",
	//			"-d", warcdir + pport, "-g", "md5", "-v", "--trace", "-s", "1000000", "--dedup-db-file=/dev/null",
	//			"--stats-db-file=/dev/null");
	
		
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

			Process p = probuilder.start();
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

	protected static boolean available(int port) {
		System.out.println("--------------Testing port " + port);
		Socket s = null;
		try {
			s = new Socket("localhost", port);

			// If the code makes it this far without an exception it means
			// something is using the port and has responded.
			System.out.println("--------------Port " + port + " is not available");
			return false;
		} catch (IOException e) {
			System.out.println("--------------Port " + port + " is available");
			return true;
		} finally {
			if (s != null) {
				try {
					s.close();
				} catch (IOException e) {
					throw new RuntimeException("You should handle this error.", e);
				}
			}
		}

	}

	public ProtocolResponse getProtocolOutput(String url, Metadata metadata) throws Exception {
		url = url.replace("robots.txt","");
		//MyScreenRecorder.startRecording("test");
		System.out.println("in protocol:"+url);
		Process p = null;
		Integer iport;
		String aport;
		long start0 = System.currentTimeMillis();

		while ((iport = getPort()) == null) {
			System.out.println("port not available");
		}
		aport = Integer.toString(iport);
		System.out.println("port" + aport);
		p = startProxyServer(aport);
		if (p != null) {
			System.out.println("proxy started");
		}

		System.out.println("initializing selenium driver");
		long start = System.currentTimeMillis();
		RemoteWebDriver driver = init_driver(aport);
		// RemoteWebDriver driver=null;
		// while ((driver = getDriver(url)) == null) {
		// }

		try {
			// This will block for the page load and any
			// associated AJAX requests

			driver.get(url);
			
			String u = driver.getCurrentUrl();
			System.out.println("original url:" + url);
			System.out.println("current url:"+u);
			System.out.println(metadata.asMap().toString());
			// if the URL is different then we must have hit a redirection
			if (!u.equalsIgnoreCase(url)) {
				System.out.println("redirect");
				String event = metadata.getFirstValue("event");
				//byte[] content = new byte[] {};
			   // metadata = new Metadata();
				metadata.addValue("_redirTo", u);				
				//metadata.addValue("event", event);
				//return new ProtocolResponse(content, 307, metadata);
			}

			// call the filters
			// metadata.addValue("navfilters", "suppress");
			// String[] directive = metadata.getValues("navfilters");

			ProtocolResponse response = null;

			System.out.println("applying filters");
			System.out.println(driver.getTitle());
		//	if (!driver.getTitle().contains("404")) {
				response = filters.filter(driver, metadata);
		//	}
			
			

			if (response == null) {
				// if no filters got triggered
				System.out.println("no filters get triggered");
				byte[] content = driver.getPageSource().getBytes();
				response = new ProtocolResponse(content, 200, metadata);
			}
			
			//MyScreenRecorder.stopRecording();
			return response;
		}
		// catch (Exception e) {
		// e.printStackTrace();
		// }
		finally {
			
			long end = System.currentTimeMillis();
			long dur = (end - start);
			Thread.sleep(6000);

			try {

				driver.quit();

			} catch (Exception ee) {

				System.out.println("have problem to close driver");
			}
			if (p != null) {
				// p.waitFor(5, TimeUnit.SECONDS);
				p.getOutputStream().close();
				p.getInputStream().close();
				p.destroy();

				// int code = p.exitValue();
				long end0 = System.currentTimeMillis();
				long dur0 = (end0 - start0);
				p.waitFor(7, TimeUnit.SECONDS);
				if (p.isAlive()) {
					p.destroyForcibly();
				}
				System.out.println("proxy destroyed");
				List result = getWarcNames(warcdir + aport);
				result.forEach((a) -> System.out.println("warc " + a));
				String commaSeparatedValues = String.join(", ", result);
				metadata.addValue("warcs", commaSeparatedValues);
				metadata.addValue("selSessionDur", String.valueOf(dur));
				metadata.addValue("proxyDur", String.valueOf(dur0));
				System.out.println("added meta");
			    File file
		            = new File(warcdir + "/output" + iport +".db");
				 file.delete();
			           
			        
				ports.put(iport);
				
			}

			// drivers.put(driver);

		}
	}

	public List getWarcNames(String dir) {
		System.out.println(dir);
		List<String> results = new ArrayList<String>();
		File[] files = new File(dir).listFiles();
		if (files.length == 0) {

		}
		// If this pathname does not denote a directory, then listFiles() returns null.

		for (File file : files) {
			if (file.isFile()) {
				String name = file.getName();
				System.out.println(name);
				// if (name.endsWith(".warc")) {
				results.add(name);
				if (name.endsWith("open")) {
					name = name.replace(".open", "");
					System.out.println(name);
				}
				boolean status = file.renameTo(new File(warcdir + File.separator + name));
				System.out.println("rename:" + status);
				// }
			}
		}

		return results;
	}

	public RemoteWebDriver init_driver(String pport) {

		// see https://github.com/SeleniumHQ/selenium/wiki/DesiredCapabilities
		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setJavascriptEnabled(true);

		String userAgentString = getAgentString(conf);

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
		
		capabilities.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
		capabilities.setCapability("chrome.switches", Arrays.asList("--proxy-server=http://"+host+":"+pport));
		//capabilities.setCapability("chrome.switches", Arrays.asList("--ignore-certificate-errors"));

		//Proxy proxy = new Proxy();
		//Proxy proxy = new Proxy();
		//proxy.setAutodetect(false);
		//proxy.setProxyType(Proxy.ProxyType.MANUAL);
		//proxy.setHttpProxy("proxyhost:proxyport");
		String proxyInfo = host + ":" + pport;
		//proxy.setHttpProxy(proxyInfo);
		//System.out.println(proxyInfo);
		//proxy.setProxyType(Proxy.ProxyType.MANUAL);
		//proxy.setHttpProxy(proxyInfo).setFtpProxy(proxyInfo).setSocksProxy(proxyInfo).setSslProxy(proxyInfo);
		//proxy.setSocksVersion(5);
		//capabilities.setCapability(CapabilityType.PROXY, proxy);
		 ChromeOptions options = new ChromeOptions();
		
         //options.addArguments("--proxy-server=socks5://" + host + ":" + pport);
          options.addArguments("--proxy-server=http://"+host+":"+pport);
          options.addArguments	("--ignore-certificate-errors"); 
			options.addArguments("--ignore-ssl-errors");
			options.addArguments("--disable-infobars");
			options.addArguments("--disable-notifications");
			options.addArguments("--disable-extenstions");
			options.setExperimentalOption("useAutomationExtension", false);
			//options.addArguments(Arrays.asList("--start-maximized"));
			options.addArguments("--allow-running-insecure-content");
			
			
			HashMap<String, Object> prefs = new HashMap<>();
			 prefs.put("download.prompt_for_download", false);
			 prefs.put("download.directory_upgrade",true);
			 prefs.put("download.default_directory",warcdir);
			 
			 prefs.put("safebrowsing.enabled",false);
			// prefs.put("download.extensions_to_open", "application/zip");
			 prefs.put("profile.default_content_settings.popups", 0);
			 prefs.put("profile.default_content_setting_values.automatic_downloads",1);
			
			options.setExperimentalOption("prefs", prefs);
         capabilities.setCapability(ChromeOptions.CAPABILITY, options);
         //recent change of selenium
         capabilities.setCapability("goog:chromeOptions", options);
		// number of instances to create per connection
		// https://github.com/DigitalPebble/storm-crawler/issues/505
		int numInst = ConfUtils.getInt(conf, "selenium.instances.num", 1);

		// load adresses from config
		List<String> addresses = ConfUtils.loadListFromConf("selenium.addresses", conf);
		if (addresses.size() == 0) {
			throw new RuntimeException("No value found for selenium.addresses");
		}
		try {
			// for (String cdaddress : addresses) {
			// for (int inst = 0; inst < numInst; inst++) {
			
		/*	LoggingPreferences logPrefs = new LoggingPreferences();
			logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
			capabilities.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
			Map<String, Object> perfLogPrefs = new HashMap<String, Object>();
			perfLogPrefs.put("traceCategories", "devtools.network"); // comma-separated trace categories
			ChromeOptions options = new ChromeOptions();
			options.setExperimentalOption("perfLoggingPrefs", perfLogPrefs);
			capabilities.setCapability(ChromeOptions.CAPABILITY, options);

		*/	
			 
			RemoteWebDriver driver = new gov.lanl.crawler.proto.ScreenShotRemoteWebDriver(new URL(addresses.get(0)), capabilities);
			//RemoteWebDriver driver = new RemoteWebDriver(new URL(addresses.get(0)), capabilities);
			  driver.manage().timeouts().implicitlyWait(25, TimeUnit.SECONDS);
			  driver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
			  driver.manage().timeouts().setScriptTimeout(30, TimeUnit.SECONDS);
			 
			//driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
			//Timeouts touts = driver.manage().timeouts();
			//int implicitWait = ConfUtils.getInt(conf, "selenium.implicitlyWait", 0);
			//int pageLoadTimeout = ConfUtils.getInt(conf, "selenium.pageLoadTimeout", -1);
			//int setScriptTimeout = ConfUtils.getInt(conf, "selenium.setScriptTimeout", 0);
			//touts.implicitlyWait(implicitWait, TimeUnit.MILLISECONDS);
			//touts.pageLoadTimeout(pageLoadTimeout, TimeUnit.MILLISECONDS);
			//touts.setScriptTimeout(setScriptTimeout, TimeUnit.MILLISECONDS);
			//driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
			System.out.println("returning driver");
			Capabilities  cap = ((RemoteWebDriver) driver).getCapabilities();
			//String c=driver.getCapabilities().getCapability("version").toString();
			
			String browserName = cap.getBrowserName();
		    String browserVersion = (String)cap.getCapability("browserVersion");
		    //String osName = Platform.fromString((String)cap.getCapability("platformName")).name().toLowerCase();
            System.out.println(browserName + browserVersion );
 		    
 		    System.out.print(driver.getCapabilities().toString());
			//System.out.println("driver version"+c);
			// drivers.add(driver);
			return driver;
			// }
			// }
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// return null;
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
			Iterator iter = ports.iterator();
			while (iter.hasNext()) {
			    int po=(int) iter.next();
			    System.out.println("from get port"+po);
			}
			Integer p = ports.take();
			System.out.println(p);
			return p;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	@Override
	public void cleanup() {
		LOG.info("Cleanup called on Selenium protocol drivers");

		synchronized (drivers) {
			drivers.forEach((d) -> {
				d.close();
			});

		}

	}
}
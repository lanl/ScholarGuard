package gov.lanl.crawler.proto;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
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
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.storm.Config;
import org.jwat.warc.WarcWriter;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.protocol.AbstractHttpProtocol;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.protocol.selenium.NavigationFilters;
import com.digitalpebble.stormcrawler.util.ConfUtils;
//import com.opencsv.CSVWriter;
import com.opencsv.CSVWriter;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import gov.lanl.crawler.hash.Shingle;
import gov.lanl.crawler.hash.SimHash;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;

public abstract class SeleniumProtocolHartoWarc extends AbstractHttpProtocol {

	protected static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SeleniumProtocolHartoWarc.class);

	protected LinkedBlockingQueue<RemoteWebDriver> drivers;
	protected LinkedBlockingQueue<Integer> ports;
	private NavigationFilters filters;
	Config conf;
	static BrowserMobProxy proxyserver;
	static Proxy seleniumProxy;
	String host;
	int port_;
	String warcdir;
	Process p = null;
	Integer iport;
	String aport;
	static CSVWriter wr;
	String harsdir;
	
	
	@Override
	public void configure(Config conf) {
		this.conf = conf;
		super.configure(conf);
		host = ConfUtils.getString(conf, "http.proxy.host", "172.17.0.1");
		port_ = ConfUtils.getInt(conf, "http.proxy.port", 0);
		warcdir = ConfUtils.getString(conf, "http.proxy.dir", "./warcs");
		harsdir = ConfUtils.getString(conf, "hars.dir", "./hars");
		filters = NavigationFilters.fromConf(conf);
		drivers = new LinkedBlockingQueue<>();
		ports = new LinkedBlockingQueue<>();
		int i = 5;
		int port = port_;
		for (i = 0; i < 5; i++) {
			ports.add(port);
			port = port + 1;
		}
		
		 wr = null;
		// result file
		File resfile = new File("./doi_crawls/https_doi_selenium.txt");
		try {
			wr = new CSVWriter(new FileWriter(resfile));
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
	
		
	}

	// HttpProxyServer server;

	public static String shortUUID() {
		UUID uuid = UUID.randomUUID();
		long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
		return Long.toString(l, Character.MAX_RADIX);
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
		System.out.println(url);
		//String id = url.replaceAll("https://doi.org/", "");
		//String durl="https://www.crossref.org/openurl/?pid=ludab@lanl.gov&format=unixref&id=" +id+ "&noredirect=true";	
		long start0 = System.currentTimeMillis();
        if (iport==null)   {
		while ((iport = getPort()) == null) {
		}
        }
		aport = Integer.toString(iport);
		System.out.println("port" + aport);
       
		long start = System.currentTimeMillis();
		Date date = new Date(start);
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
		String time = simpleDateFormat.format(date);
		//Thread.sleep(30000);
		RemoteWebDriver driver = init_driver(aport);
		//String n = make_short_name(url);
		String n =shortUUID()+"-"+time;
		
		
		proxyserver.newHar(n);
		
		// RemoteWebDriver driver=null;
		// while ((driver = getDriver()) == null) {
			 
		 //}

		try {
			// This will block for the page load and any
			// associated AJAX requests

			driver.get(url);
			//Thread.sleep(10000);
			String u = driver.getCurrentUrl();
			//System.out.println("original url:" + url);
			//System.out.println("current url:"+u);
			System.out.println(metadata.asMap().toString());
			// if the URL is different then we must have hit a redirection
			if (!u.equalsIgnoreCase(url)) {
				System.out.println("redirect");
				//String event = metadata.getFirstValue("event");
				//byte[] content = new byte[] {};
			   // metadata = new Metadata();
				metadata.addValue("_redirTo", u);				
				//metadata.addValue("event", event);
				//return new ProtocolResponse(content, 307, metadata);
			}
			
			/*
			DevToolsUtil.takeScreenShot();
			*/
			ProtocolResponse response = null;

			System.out.println("starting har :"+n);
			//processHar(n);
			//proxyserver.endHar();
			response = filters.filter(driver, metadata);
			
			if (response == null) {
				// if no filters got triggered
				System.out.println("no filters get triggered");
				byte[] content = driver.getPageSource().getBytes();
				response = new ProtocolResponse(content, 200, metadata);
			}
			
			return response;
		}
		// catch (Exception e) {
		// e.printStackTrace();
		// }
		finally {
			
			PersistHarWar(n);
			long end = System.currentTimeMillis();
			long dur = (end - start);
			//Thread.sleep(3000);
			try {
				driver.quit();
				System.out.println("ending har :"+url);
				
				proxyserver.endHar();
				proxyserver.stop();
				/*
				DevToolsUtil.webSocket.disconnect();
				DevToolsUtil.service.stop();
				*/
			} catch (Exception ee) {
				System.out.println("have problem to close driver");
			}
			
		
				long end0 = System.currentTimeMillis();
				long dur0 = (end0 - start0);
				
			//	System.out.println("proxy destroyed");
				//List result = getWarcNames(warcdir + aport);
				//result.forEach((a) -> System.out.println("warc " + a));
				//String commaSeparatedValues = String.join(", ", result);
				//metadata.addValue("warcs", commaSeparatedValues);
				metadata.addValue("warcs",n);
				metadata.addValue("selSessionDur", String.valueOf(dur));
				metadata.addValue("proxyDur", String.valueOf(dur0));
				System.out.println("added meta");
				//45 sec
				//Thread.sleep(45000);
			//	ports.put(iport);
				
		//	}

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

	public String make_short_name (String url) {
		String fname = url;
		if ( fname.length()>60) {
		 fname = url.substring(0, 60);
		}
		fname=fname.replaceAll("http://doi.org/", "");
		fname=fname.replaceAll("https://doi.org/", "");
		
		fname=fname.replaceAll("/", "");
		fname=fname.replaceAll(":", "");
		System.out.println(url);
		System.out.println(fname);
		return fname;
	}
	
	
	public void PersistHarWar(String fname) {
		Har _har = proxyserver.getHar();
		File hfile =null;
		try {
			File file = new File(harsdir+fname + ".har");
			
			_har.writeTo(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		HARtoWARC hw = new HARtoWARC();
		File target = new File(warcdir+ fname +  ".warc");
		hw.hartowarc(hfile, target);
	}
	
	
	public Proxy configureMob_proxy(){
		proxyserver = new BrowserMobProxyServer();
		// proxyserver.chainedProxyAuthorization("userid", "password", AuthType.BASIC);
		InetSocketAddress x = new InetSocketAddress("proxyout.lanl.gov", 8080);
		proxyserver.setChainedProxy(x);
		proxyserver.setTrustAllServers(true);
		proxyserver.setMitmManager(ImpersonatingMitmManager.builder().trustAllServers(true).build());

	
		EnumSet<CaptureType> captureTypes = CaptureType.getHeaderCaptureTypes();
		captureTypes.addAll(CaptureType.getAllContentCaptureTypes());
		captureTypes.addAll(CaptureType.getCookieCaptureTypes());
		proxyserver.setHarCaptureTypes(captureTypes);
		proxyserver.start(0);
		int port = proxyserver.getPort(); // get the JVM-assigned port
		System.out.println(" proxy port" + port);
		Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxyserver);
		proxyserver.getClientBindAddress();
		String ipAddress = new NetworkUtils().getIp4NonLoopbackAddressOfThisMachine().getHostAddress();
		System.out.println(ipAddress);
		   
		//System.out.println(seleniumProxy.getHttpProxy());
		//seleniumProxy.setHttpProxy("localhost:" + proxyserver.getPort()); // The port generated by server.start();
		seleniumProxy.setHttpProxy(ipAddress + ":" + port);
		//seleniumProxy.setSslProxy("localhost:" + proxyserver.getPort());
		seleniumProxy.setSslProxy(ipAddress + ":" + port);
		return seleniumProxy;

	}
	public RemoteWebDriver init_driver_local(String pport) {

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
		Proxy seleniumProxy= configureMob_proxy();
		
		capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);

		// Selenium or HTTP client configuration goes here

		ChromeOptions options = new ChromeOptions();
		options.setExperimentalOption("useAutomationExtension", false);
		options.addArguments(Arrays.asList("--start-maximized"));
		String chromeDriverPath = "/usr/local/bin/chromedriver";
		System.setProperty("webdriver.chrome.driver", chromeDriverPath);

		 options.addArguments("--headless");
		options.addArguments("--ignore-certificate-errors");
		options.addArguments("--ignore-ssl-errors");
	
		String prox = "http://" + seleniumProxy.getHttpProxy();
		System.out.println("prox" + prox);
	
		capabilities.setCapability(ChromeOptions.CAPABILITY, options);
		RemoteWebDriver driver = new ChromeDriver(capabilities);
		
		// load adresses from config
		List<String> addresses = ConfUtils.loadListFromConf("selenium.addresses", conf);
		if (addresses.size() == 0) {
			throw new RuntimeException("No value found for selenium.addresses");
		}
		try {
			System.out.println("returning driver");
			// drivers.add(driver);
			return driver;
			// }
			// }
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// return null;
	}

	public RemoteWebDriver init_driver(String pport) {
		//return init_driver_local(pport);
		return init_driver_hub(pport);
	}
	
	public RemoteWebDriver init_driver_hub(String pport) {

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
		
		//if (seleniumProxy==null) {
        seleniumProxy= configureMob_proxy();
		//}
		
		capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);
		//Proxy proxy = new Proxy();
		//String proxyInfo = host + ":" + pport;
		//System.out.println(proxyInfo);
		//proxy.setProxyType(Proxy.ProxyType.MANUAL);
		//proxy.setHttpProxy(proxyInfo).setFtpProxy(proxyInfo).setSocksProxy(proxyInfo).setSslProxy(proxyInfo);
		//proxy.setSocksVersion(5);
		//capabilities.setCapability(CapabilityType.PROXY, proxy);
		 ChromeOptions options = new ChromeOptions();
         //options.addArguments("--proxy-server=socks5://" + host + ":" + pport);
        // options.addArguments("--proxy-server=http://"+host+":"+pport);
         options.addArguments	("--ignore-certificate-errors"); 
			options.addArguments("--ignore-ssl-errors");
			options.addArguments("disable-infobars");
			options.addArguments("--disable-notifications");
			options.addArguments("--disable-extenstions");
			options.setExperimentalOption("useAutomationExtension", false);
			//options.addArguments(Arrays.asList("--start-maximized"));
			options.addArguments("allow-running-insecure-content");
         capabilities.setCapability(ChromeOptions.CAPABILITY, options);
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
			RemoteWebDriver driver = new RemoteWebDriver(new URL(addresses.get(0)), capabilities);
			System.out.println("driver established");
			//driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
			Timeouts touts = driver.manage().timeouts();
			int implicitWait = ConfUtils.getInt(conf, "selenium.implicitlyWait", 0);
			//int pageLoadTimeout = ConfUtils.getInt(conf, "selenium.pageLoadTimeout", -1);
			int setScriptTimeout = ConfUtils.getInt(conf, "selenium.setScriptTimeout", 0);
			touts.implicitlyWait(implicitWait, TimeUnit.MILLISECONDS);
			//touts.pageLoadTimeout(pageLoadTimeout, TimeUnit.MILLISECONDS);
			touts.setScriptTimeout(setScriptTimeout, TimeUnit.MILLISECONDS);
			System.out.println("returning driver");
			// drivers.add(driver);
			return driver;
			// }
			// }
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// return null;
	}

	public RemoteWebDriver init_driver_headless(String pport) {
         //this is headless version of myresearch institute
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
		
	/*	Proxy proxy = new Proxy();
		String proxyInfo = host + ":" + pport;
		System.out.println(proxyInfo);
		proxy.setProxyType(Proxy.ProxyType.MANUAL);
		proxy.setHttpProxy(proxyInfo).setFtpProxy(proxyInfo).setSocksProxy(proxyInfo).setSslProxy(proxyInfo);
		proxy.setSocksVersion(5);
		capabilities.setCapability(CapabilityType.PROXY, proxy);
*/
		

		ChromeOptions options = new ChromeOptions();
		options.setExperimentalOption("useAutomationExtension", false);
		options.addArguments(Arrays.asList("--start-maximized"));
		
		String chromeDriverPath = "/usr/bin/chromedriver" ;  
		System.setProperty("webdriver.chrome.driver", chromeDriverPath);  
		
		options.addArguments("--headless");
				options.addArguments	("--ignore-certificate-errors");  
		options.setBinary("/usr/bin/google-chrome"); 
				
        //options.addArguments("--proxy-server=socks5://" + host + ":" + pport);
        options.addArguments("--proxy-server=http://"+host+":"+pport);
        capabilities.setCapability(ChromeOptions.CAPABILITY, options);
        System.setProperty(ChromeDriverService.CHROME_DRIVER_LOG_PROPERTY,
				System.getProperty("user.dir") + File.separator + "/target/chromedriver.log");
		
       
        
		ChromeDriverService service = new ChromeDriverService.Builder().usingAnyFreePort().withVerbose(true).build();
		try {
			service.start();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		System.out.println(service.getUrl());
		RemoteWebDriver driver = new RemoteWebDriver(service.getUrl(),capabilities);
		// number of instances to create per connection
		// https://github.com/DigitalPebble/storm-crawler/issues/505
		//int numInst = ConfUtils.getInt(conf, "selenium.instances.num", 1);

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
			/*
			RemoteWebDriver driver = new RemoteWebDriver(new URL(addresses.get(0)), capabilities);
			Timeouts touts = driver.manage().timeouts();
			int implicitWait = ConfUtils.getInt(conf, "selenium.implicitlyWait", 0);
			int pageLoadTimeout = ConfUtils.getInt(conf, "selenium.pageLoadTimeout", -1);
			int setScriptTimeout = ConfUtils.getInt(conf, "selenium.setScriptTimeout", 0);
			touts.implicitlyWait(implicitWait, TimeUnit.MILLISECONDS);
			touts.pageLoadTimeout(pageLoadTimeout, TimeUnit.MILLISECONDS);
			touts.setScriptTimeout(setScriptTimeout, TimeUnit.MILLISECONDS);
			*/
			System.out.println("returning driver");
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
			return ports.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	
	
	
	
	
	
	@Override
	public void cleanup() {
		LOG.info("Cleanup called on Selenium protocol drivers");
    //try {
	//wr.close();
    //} catch (IOException e1) {
	// TODO Auto-generated catch block
	//e1.printStackTrace();
     //}
   // proxyserver.stop();	
    if (p != null) {
			 try {
				p.waitFor(5, TimeUnit.SECONDS);
			 
			p.getOutputStream().close();
			p.getInputStream().close();
			p.destroy();

			 int code = p.exitValue();
			//long end0 = System.currentTimeMillis();
			//long dur0 = (end0 - start0);
			p.waitFor(7, TimeUnit.SECONDS);
			if (p.isAlive()) {
				p.destroyForcibly();
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
			System.out.println("proxy destroyed");
		}
		
		synchronized (drivers) {
			drivers.forEach((d) -> {
				d.close();
			});

		}

	}
}
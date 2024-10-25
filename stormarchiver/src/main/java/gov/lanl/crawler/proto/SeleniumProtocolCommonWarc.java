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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
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
import gov.lanl.crawler.hash.Shingle;
import gov.lanl.crawler.hash.SimHash;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;

public abstract class SeleniumProtocolCommonWarc extends AbstractHttpProtocol {

	protected static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SeleniumProtocolCommonWarc.class);

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
	@Override
	public void configure(Config conf) {
		this.conf = conf;
		super.configure(conf);
		host = ConfUtils.getString(conf, "http.proxy.host", "172.17.0.1");
		port_ = ConfUtils.getInt(conf, "http.proxy.port", 0);
		warcdir = ConfUtils.getString(conf, "http.proxy.dir", "./warcs");
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

		
		
		
	
		
		
		/*
		
		 // number of instances to create per connection
        // https://github.com/DigitalPebble/storm-crawler/issues/505
        int numInst = ConfUtils.getInt(conf, "selenium.instances.num", 1);

        // load adresses from config
        List<String> addresses = ConfUtils.loadListFromConf(
                "selenium.addresses", conf);
        if (addresses.size() == 0) {
            throw new RuntimeException("No value found for selenium.addresses");
        }
        
        if (iport==null)   {
    		while ((iport = getPort()) == null) {
    		}
    		aport = Integer.toString(iport);
    		System.out.println("port" + aport);
            }
        
        
        try {
            //for (String cdaddress : addresses) {
                for (int inst = 0; inst < numInst; inst++) {
                	RemoteWebDriver driver = init_driver(aport);
                    drivers.add(driver);
                }
          //  }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
		*/
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
				"-d", warcdir , "-g", "md5", "-v", "--trace", "-s", "6000000000", "--dedup-db-file=/dev/null",
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
		String id = url.replaceAll("https://doi.org/", "");
		//String durl="https://www.crossref.org/openurl/?pid=ludab@lanl.gov&format=unixref&id=" +id+ "&noredirect=true";	
		long start0 = System.currentTimeMillis();
        if (iport==null)   {
		while ((iport = getPort()) == null) {
		}
        }
		aport = Integer.toString(iport);
		System.out.println("port" + aport);
        //}
		//p = startProxyServer(aport);
		//comment out wrcproxy start
		/*
		if (p == null) {		
			p = startProxyServer(aport);
			System.out.println("proxy started");
		}
        */
		//System.out.println("initializing selenium driver");
		long start = System.currentTimeMillis();
		//Thread.sleep(30000);
		RemoteWebDriver driver = init_driver(aport);
		String n = make_short_name(url);
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
			System.out.println("original url:" + url);
			System.out.println("current url:"+u);
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
			// har processing
			//processHar(url);
			//proxyserver.endHar();
			// call the filters
			// metadata.addValue("navfilters", "suppress");
			// String[] directive = metadata.getValues("navfilters");

			//do_screenshot( driver,  metadata);
			/*
			DevToolsUtil.takeScreenShot();
			*/
			ProtocolResponse response = null;

			System.out.println("starting har :"+n);
			processHar(n);
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
			
		//	if (p != null) {
				// p.waitFor(5, TimeUnit.SECONDS);
				//p.getOutputStream().close();
				//p.getInputStream().close();
				//p.destroy();

				// int code = p.exitValue();
				long end0 = System.currentTimeMillis();
				long dur0 = (end0 - start0);
				//p.waitFor(20, TimeUnit.SECONDS);
			//	if (p.isAlive()) {
			//		p.destroyForcibly();
			//	}
			//	System.out.println("proxy destroyed");
				//List result = getWarcNames(warcdir + aport);
				//result.forEach((a) -> System.out.println("warc " + a));
				//String commaSeparatedValues = String.join(", ", result);
				//metadata.addValue("warcs", commaSeparatedValues);
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
	
	
	public void processHar(String fname) {
		//String fname = make_short_name (url);
		// har processing
		Har _har = proxyserver.getHar();
		String har_dir =  "/Users/Lyudmila/stormarchiver/doi_crawls/httpsdoicrawl/";
		try {
			File file = new File(har_dir+fname + ".har");
			
			_har.writeTo(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		HarReader harReader = new HarReader();
		de.sstoehr.harreader.model.Har harr = null;
		try {
			harr = harReader.readFromFile(new File(har_dir+fname + ".har"));
		} catch (HarReaderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<HarEntry> entries = harr.getLog().getEntries();
		for (HarEntry entry : entries) {
			String redirurl = "";String sstatus = "";String len = "";String ctype = "";
			String lu = "";String sim = "";String etag = "";String lm = "";
			String murl = entry.getRequest().getUrl();
			Date stime = entry.getStartedDateTime();
			String st="";
			if (stime!=null) {
			 st = TraceTest.timeTravelMachineFormatter.format(stime);
			System.out.println("st:"+st);
			}
			String method = entry.getRequest().getMethod().name();
			int status = entry.getResponse().getStatus();
			String status_ = String.valueOf(status);
			System.out.println("status"+status_);
			List<HarHeader> hs = entry.getResponse().getHeaders();
			Iterator it = hs.iterator();

			while (it.hasNext()) {
				HarHeader h = (HarHeader) it.next();
				String name = h.getName();
				//System.out.println("name"+name);
				if (name.equalsIgnoreCase("location")) {redirurl = h.getValue();
				System.out.println(redirurl);
				}
				if (name.equalsIgnoreCase("last-modified")) {lm = h.getValue();}
				if (h.getName().equalsIgnoreCase("Content-Length")) {
					len = h.getValue();
				}
				if (h.getName().equalsIgnoreCase("Content-Type")) {
					ctype = h.getValue();
				}
				if (h.getName().equalsIgnoreCase("ETag")) {
					etag = h.getValue();

				}
				
			} // while headers
			
			if (status == 200) {
				// entry.getRequest().getUrl();
					if ( TraceTest.mimetype_m(ctype)) {
						   //if (ctype.contains("html")|| ctype.contains("text")) {
						 String htext = entry.getResponse().getContent().getText();
						
						//Base64.Decoder decoder = Base64.getDecoder();
						   byte[] rawData=null;
								rawData = htext.getBytes();
								String html = new String(rawData); 
								long simhash1 =  SimHash.computeSimHashFromString(Shingle.shingles(html)); 
								System.out.println(simhash1);
								sim = String.valueOf(simhash1);								
					   }
			}
			String nextLine[] = { status_, murl, redirurl, st, len,ctype, sim, etag, lm };
			
			wr.writeNext(nextLine);
			try {
				wr.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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

	
	
	
	//old methods 
	void do_screenshot(RemoteWebDriver driver, Metadata metadata){
		String sdir = (String) conf.get("screenshortdir");
		if (sdir!=null)   { 
			//driver.manage().window().setSize(new Dimension(1000,1000));
			Dimension originalDim = driver.manage().window().getSize();
			int k = originalDim.getHeight();
			int y = originalDim.getWidth();
			System.out.println("hdim"+k);
			System.out.println("wdim"+y);
			WebElement html = driver.findElement(By.tagName("html"));	
			if (html!=null) {
				String[] doi = metadata.getValues("url.path");
				String name = doi[0];
				name=name.replaceAll("http://doi.org/", "");
				name=name.replaceAll("https://doi.org/", "");
				
				name=name.replaceAll("/", "");
			
				 int ht = html.getSize().getHeight();
				        int w = html.getSize().getWidth();
				        System.out.println("ht"+ht);
						System.out.println("w"+w);
					
				        JavascriptExecutor js = (JavascriptExecutor) driver;
				        if (ht>890)   {
				          int ratio= 	ht/1000;
				        js.executeScript("document.body.style.zoom='20%'");
				        System.out.println("zoom");
				     	WebElement htm = driver.findElement(By.tagName("html"));	
				    	
				     	 int _ht = html.getSize().getHeight();
					     int _w = html.getSize().getWidth();
					     System.out.println("_ht"+_ht);
							System.out.println("_w"+_w);
						
					
				        //String script = "var page = this;page.zoomFactor = 0.9;";
				        
				        }
				        
				        
				        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
						
				        try {
							FileUtils.copyFile(screenshot, new File(sdir+name+"screenshot_1.png"));
							byte[] bytes = driver.getScreenshotAs(OutputType.BYTES);
							BufferedImage full = ImageIO.read(new ByteArrayInputStream(bytes));
							BufferedImage f = full.getSubimage(0, 0, 1000, 1000);
							ImageIO.write(f, "PNG", new File(sdir+ name+"sub.png"));
							//Shutterbug.shootPage(driver, ScrollStrategy.BOTH_DIRECTIONS).save(sdir+name+"screenshot_2.png");
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
							        
				        
			//html.sendKeys(Keys.chord(Keys.CONTROL, Keys.ADD));
			}
			
		}
		//Screenshot screenshot = new AShot()
		  //.shootingStrategy(new ViewportPastingStrategy(500))
		  //.takeScreenshot(driver);
	/*
		
		String[] doi = metadata.getValues("url.path");
		String name = doi[0];
		name=name.replaceAll("http://doi.org/", "");
		name=name.replaceAll("https://doi.org/", "");
		
		name=name.replaceAll("/", "");
		//driver.manage().window().fullscreen();
		// driver.manage().window().maximize();
			
	        try {
	        	 WebElement el = driver.findElementByTagName("body");
	        	 int bodyHight = el.getSize().getHeight();
	        	 System.out.println("bodyHight"+bodyHight);
	    		// File screen = el.getScreenshotAs(OutputType.FILE);
	         WebDriver augmentedDriver = new Augmenter().augment(driver);
	      
	     	  File screen = ((TakesScreenshot)augmentedDriver).
	     	                        getScreenshotAs(OutputType.FILE);
	     	 
	        	
				FileUtils.copyFile(screen, new File(sdir+name+"screenshot.png"));
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			}
		 */
		 /*try {
			el.getScreenshotAs(arg0);
			ImageIO.write(screenshot.getImage(), "PNG", new File(sdir+ name+"results.png"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		}
		*/
		//added for crawling experiment
		//String sdir = (String) stormConf.get("screenshortdir");
		//driver.manage().window().fullscreen();
		//scroll(driver);
		/*if (sdir!=null)   { 
		String[] doi = metadata.getValues("url.path");
		String name = doi[0];
		name=name.replaceAll("http://doi.org/", "");
		name=name.replaceAll("https://doi.org/", "");
		
		name=name.replaceAll("/", "");
		//name=name.replaceAll(".", "");
		// slow_scroll (driver);
		
		driver.manage().window().setSize(new Dimension(1920,1200));
		
		File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		
        try {
			FileUtils.copyFile(screenshot, new File(sdir+name+"screenshot.png"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		}
		*/
		
		
		
		
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
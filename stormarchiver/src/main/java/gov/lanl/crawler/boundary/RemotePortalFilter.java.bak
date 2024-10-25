package gov.lanl.crawler.boundary;

import com.digitalpebble.stormcrawler.protocol.selenium.NavigationFilter;
import com.digitalpebble.stormcrawler.sql.Constants;
import com.digitalpebble.stormcrawler.util.ConfUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.support.events.EventFiringWebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
//import com.savoirtech.logging.slf4j.json.LoggerFactory;
//import com.savoirtech.logging.slf4j.json.logger.JsonLogger;

import gov.lanl.crawler.core.StatusUpdaterBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

public class RemotePortalFilter extends NavigationFilter {
	// String portalname = "github.com";
	List<HashMap> Elements = new ArrayList();
	private List<PortalRule> portalrules;
	private JsonNode nodem;
	String filter = "";
	String filterurl;
	//int clickcount = 0;
	String subtrace;
	Map stormConf;
	//String partialexit = null;
	StatusUpdaterBolt su = null;
	String nosubtrace = "false";
	//static Logger statsloger = Logger.getLogger("stats");
	// com.savoirtech.logging.slf4j.json.logger.Logger statsloger =  LoggerFactory.getLogger("JSONLogger");
	    
	TraceUtils bu;
	String slowmode;
	@SuppressWarnings("rawtypes")
	@Override
	public ProtocolResponse filter(RemoteWebDriver driver, Metadata metadata) {
		// ATUTestRecorder recorder = createvideorecorder();
		StringBuilder dummyContent = new StringBuilder("<html>");
		bu = new TraceUtils();
		Map cap = driver.getCapabilities().asMap();
		filterurl = metadata.getFirstValue("trace");
		subtrace = metadata.getFirstValue("subtrace");
		slowmode = metadata.getFirstValue("slowmode");
		if (slowmode==null) slowmode="false";
		//driver.register(new HighLighterEventListener());

		// temporaly comment out
		//String tableName = ConfUtils.getString(stormConf, Constants.MYSQL_TABLE_PARAM_NAME);
		//this is temporary parameter to run test without db
		nosubtrace = ConfUtils.getString(stormConf, "mysql.nosubtrace");
		String tableName = ConfUtils.getString(stormConf, Constants.MYSQL_TABLE_PARAM_NAME);
		if (nosubtrace.equals("true")) {
			System.out.println("nosubtrace");
		} else {
			if (tableName != null) {
				su = new StatusUpdaterBolt();
				su.prepare_ext(stormConf);
			}
		}

		String urlValue = driver.getCurrentUrl().toString();
		//
		System.out.println("filterurl" + filterurl);
		if (filterurl != null) {
			reconfigurewithExternalTrace();			
		}

		if (subtrace != null) {
			// System.out.println(subtrace);
			loadSubtrace();
		}
        //this is normal path with traces from config
		if (!filter.equals("dynamic.json")) {
			System.out.println(urlValue);
			if (applyUrlFilter(urlValue) == null) {
				System.out.println("return from filter");
				return null;
			}
		}

		long start = System.currentTimeMillis();
	
		//driver.manage().window().fullscreen();
		//bu.waitForJSandJQueryToLoad(driver);
		
			
		
		// gentle default scroll but limited to 1.5 min timeoutMilliSeconds
		try {
		bu.scrollwithTimeout( driver,90 * 1000);
		
		bu.scrolltotop(driver);
		}
	     catch (Throwable ee) {
	    	 System.out.println("no scroll element");
	    }
		List<SimpleEntry> urls = new ArrayList();
		
		System.out.println("trace:" + filter);
		// currently will exit if travesal takes more thrn 2 hours
		// TimedExit te= new TimedExit(urlValue, metadata);
		//int ccount = 0;
		// Addressing difference in format between trace and subtrace
		JsonNode current = null;
		if (subtrace != null) {
			System.out.println("subtrace");
			current = nodem;
			// traverseTrace(nodem, driver, urlValue, urls, ccount);
		} else {
			Iterator<JsonNode> e = nodem.elements();
			JsonNode root = null;
			if (e.hasNext()) {
				root = e.next();
				String test = root.toString();
				System.out.println("test:" + test);
				current = root;
			}
		}
		
		//JsonLogger a = statsloger.info().field("url",urlValue);
	
		TracePlayer trplay = new TracePlayer(slowmode,driver);
		EventFiringWebDriver efd = new EventFiringWebDriver(driver);
		efd.register(trplay);
		
		// driver.register(trplay);
		if (current != null) {
			try {
				// 30 minute
				TimeoutBlock timeoutBlock = new TimeoutBlock(30 * 60 * 1000*100);// set timeout in milliseconds
				// timeout for testing
				// TimeoutBlock timeoutBlock = new TimeoutBlock(2000);//set timeout in
				// milliseconds

				Runnable block = new Runnable() {
					String urlValue;
					JsonNode root;
					RemoteWebDriver driver;
					//EventFiringWebDriver driver;
					List<SimpleEntry> urls;
					//int ccount;
					TracePlayer trplay;
					public Runnable init(JsonNode root, String urlValue, RemoteWebDriver driver, List<SimpleEntry> urls,TracePlayer trplay) {
						this.root = root;
						this.urlValue = urlValue;
						this.driver =  driver;
						this.urls = urls;
						this.trplay=trplay;
						//this.ccount = ccount;
						return (this);
					}

					@Override
					public void run() {
						trplay.traverseTrace(root, driver, urlValue, urls);
					}
					
				}.init(current, urlValue, driver, urls, trplay);

				timeoutBlock.addBlock(block);// execute the runnable block

			} catch (Throwable ee) {
				System.out.println("timeout");
				metadata.addValue("timeout", "90min");
				// catch the exception here . Which is block didn't execute within the time
				// limit
			}

			// traverseTrace(root, driver, urlValue, urls, ccount);
		} // root
		// }
		trplay.finish();
		dummyContent.append("</html>");
		metadata.addValue("filter", filter);
		String event = metadata.getFirstValue("event");
		int clickcount=trplay.getTotalClickcount();
		String partialexit=trplay.getPartialexit();
		metadata.addValue("clickCount", String.valueOf(clickcount));
		/*
		 * try { recorder.stop(); } catch (Exception e) { // TODO Auto-generated catch
		 * block e.printStackTrace(); }
		 */
		SessionId si = driver.getSessionId();
		String sid = si.toString();
			//statsloger.info().
			//field("url",urlValue)
			//a.field("func","processlinks")			
			//.field("sesid",sid)
			//.field("filter",filter)
			//.field("linksfound",urls.size()).log();
			
			
		
		if (tableName != null) {
			urls.forEach(link -> processLinks((SimpleEntry) link, driver, event, su));
		}
		long end = System.currentTimeMillis();
		long dur = (end - start);/// (1000L)) ;
		metadata.addValue("traceDur", String.valueOf(dur));
		if (partialexit != null) {
			metadata.addValue("break", partialexit);
		}
		System.out.println("downloaded everything");
		// LogEntries log = driver.manage().logs().get("perfomance");
		// log.forEach(x -> System.out.println(x));

		return new ProtocolResponse(dummyContent.toString().getBytes(), 200, metadata);
	}

	public void processLinks(SimpleEntry entry, RemoteWebDriver driver, String event, StatusUpdaterBolt su) {
		try {

			new FluentWait<RemoteWebDriver>(driver).until(webDriver -> ((JavascriptExecutor) webDriver)
					.executeScript("return document.readyState =='complete'"));
			driver.manage().window().fullscreen();
			String _url = (String) entry.getKey();
			String strace = (String) entry.getValue();
			if (strace == null) {
				driver.get(_url);
				String redir_url = driver.getCurrentUrl();
				if (!redir_url.equals(_url)) {
				driver.navigate().to(redir_url);
				System.out.println(redir_url);
				}
				bu.scroll(driver);
				System.out.println(_url);
			} else {

				System.out.println("adding to q:" + _url);
				// InboxResource ibox = new InboxResource();
				Map<String, String[]> map = new HashMap();
				map.put("url.path", new String[] { _url });
				map.put("depth", new String[] { "0" });
				map.put("event", new String[] { event });
				map.put("subtrace", new String[] { strace });

				Metadata metadata = new Metadata(map);
				Timestamp nextFetch = new Timestamp(new Date().getTime());
				// Status subtrace = Status.valueOf("SUBTRACE");

				// this is tested recursion based on crawler.
				/*
				 * if (_url != null) { if (_url.length() != 0) { su._store(_url, "SUBTRACE",
				 * metadata, nextFetch); }
				 * 
				 * }
				 */
				// this is tested solution with flag

				if (_url != null) {
					if (_url.length() != 0) {
						if (!nosubtrace.equals("true")) {
							su._store(_url, "SUBTRACE", metadata, nextFetch);
						}

					}
				}
				// this is new solution but not tested
				// may be needed new driver object not mess with original state
				// execute subtrace in same session
				/*
				 * if (_url != null) { if (_url.length() != 0) { driver.get(_url); filter(
				 * driver, metadata); } }
				 */
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("problem to download:" + entry.getKey());
			e.printStackTrace();
		}
	}

	

	private String loadProfilefromURL(String profileFile) {

		StringBuffer sb = new StringBuffer();
		try {

			URL url = new URL(profileFile);
			InputStream is = url.openStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);

			}
			in.close();
		} catch (IOException e) {
			// LOG.error("There was an error reading the default-regex-filters file");
			e.printStackTrace();
		}

		return sb.toString();
	}

	private String loadProfile(String profileFile) {

		StringBuffer sb = new StringBuffer();
		try {
			InputStream regexStream = getClass().getClassLoader().getResourceAsStream(profileFile);
			Reader reader = new InputStreamReader(regexStream, StandardCharsets.UTF_8);
			BufferedReader in = new BufferedReader(reader);
			String line;

			while ((line = in.readLine()) != null) {
				// if (line.length() == 0) {
				// continue;
				// }
				sb.append(line);

			}
			in.close();
		} catch (IOException e) {
			// LOG.error("There was an error reading the default-regex-filters file");
			e.printStackTrace();
		}
		return sb.toString();
	}

	public void readProfile(JsonNode filterParams) {

		JsonNode node0 = filterParams.get("uriRegex");
		System.out.println(node0.asText());
		if (node0 != null) {
			/*
			 * if (!node0.isArray()) { // LOG.warn( //
			 * "Failed to configure queryElementsToRemove. Not an array: {}", //
			 * node.toString()); } else { // ArrayNode array = (ArrayNode) node0; //rules =
			 * readRules((ArrayNode) node0);
			 * 
			 * }
			 */
			readRules(node0.asText());

		}

		nodem = filterParams.get("actions");
		if (nodem != null) {
			if (!nodem.isArray()) {
				// LOG.warn(
				// "Failed to configure queryElementsToRemove. Not an array: {}",
				// node.toString());
			} else {
				ArrayNode array = (ArrayNode) nodem;

				for (JsonNode element : array) {
					// System.out.println(element.toString());
					String jsonStr = element.toString();

					HashMap<String, Object> resultMap = new HashMap<String, Object>();
					ObjectMapper mapperObj = new ObjectMapper();

					try {
						resultMap = mapperObj.readValue(jsonStr, new TypeReference<HashMap<String, Object>>() {
						});
						// System.out.println("Output Map: " + resultMap);
						Elements.add(resultMap);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					// HashMap m = new ObjectMapper().convertValue(element,HashMap.class);

				}
			}
		}

	}

	public void readinlineProfile(JsonNode filterParams) {

		// JsonNode node0 = filterParams.get("uriRegex");
		// System.out.println(node0.asText());
		// if (node0 != null) {
		/*
		 * if (!node0.isArray()) { // LOG.warn( //
		 * "Failed to configure queryElementsToRemove. Not an array: {}", //
		 * node.toString()); } else { // ArrayNode array = (ArrayNode) node0; //rules =
		 * readRules((ArrayNode) node0);
		 * 
		 * }
		 */
		// readRules(node0.asText());

		// }
		ObjectNode o = (ObjectNode) filterParams;
		System.out.println(o.path("actionName"));

		o.put("actionName", "load");
		String tmp = o.toString();
		System.out.println(tmp);
		nodem = o;// .get("actions");

		/*
		 * if (nodem != null) { if (!nodem.isArray()) { // LOG.warn( //
		 * "Failed to configure queryElementsToRemove. Not an array: {}", //
		 * node.toString()); } else { ArrayNode array = (ArrayNode) nodem;
		 * 
		 * for (JsonNode element : array) { // System.out.println(element.toString());
		 * String jsonStr = element.toString();
		 * 
		 * HashMap<String, Object> resultMap = new HashMap<String, Object>();
		 * ObjectMapper mapperObj = new ObjectMapper();
		 * 
		 * try { resultMap = mapperObj.readValue(jsonStr, new
		 * TypeReference<HashMap<String, Object>>() { }); //
		 * System.out.println("Output Map: " + resultMap); Elements.add(resultMap); }
		 * catch (IOException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 * 
		 * // HashMap m = new ObjectMapper().convertValue(element,HashMap.class);
		 * 
		 * } } }
		 */
	}


	
	
	

	@SuppressWarnings("unchecked")
	@Override
	public void configure(Map stormConf, JsonNode filterParams) {
		this.stormConf = stormConf;
		JsonNode nodefile = filterParams.get("portalFile");
		String trace = null;
		if (nodefile != null) {
			filter = filterParams.get("portalFile").asText();
			System.out.println("filter:" + filter);
			// if (filter.equals("dynamic.json")){
			// trace = loadProfilefromURL(filterurl) ;

			// }
			// else {
			trace = loadProfile(filterParams.get("portalFile").asText());
			// }
		}
		if (trace == null) {
			System.out.println("trace is null");
			readProfile(filterParams);
		} else {
			ObjectMapper mapper = new ObjectMapper();
			try {
				// we load here from file
				JsonNode actualObj = mapper.readTree(trace);
				readProfile(actualObj);
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	@SuppressWarnings("unchecked")

	public void reconfigurewithExternalTrace() {		
		String trace = null;
		
		System.out.println("filter:" + filter);
		if (filter.equals("dynamic.json")) {
			trace = loadProfilefromURL(filterurl);
		}
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			// we load here from file
			JsonNode actualObj = mapper.readTree(trace);
			readProfile(actualObj);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// }

	}

	public void loadSubtrace() {

		ObjectMapper mapper = new ObjectMapper();
		try {
			// we load here from file
			JsonNode actualObj = mapper.readTree(subtrace);
			readinlineProfile(actualObj);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// }

	}

	/** Populates a List of Rules off of JsonNode. */
	public List<PortalRule> readRules(ArrayNode rulesList) {
		portalrules = new ArrayList<>();
		for (JsonNode urlFilterNode : rulesList) {
			try {
				// System.out.println("rule" + urlFilterNode.asText());
				PortalRule rule = createRule(urlFilterNode.asText());
				if (rule != null) {
					portalrules.add(rule);
				}
			} catch (IOException e) {
				// LOG.error("There was an error reading regex filter {}",
				// urlFilterNode.asText(), e);
			}
		}
		return portalrules;
	}

	/** Populates a List of Rules off of JsonNode. */
	public List<PortalRule> readRules(String urlmatch) {
		portalrules = new ArrayList<>();

		// for (JsonNode urlFilterNode : rulesList) {
		try {
			System.out.println("rule" + urlmatch);
			PortalRule rule = createRule(urlmatch);
			if (rule != null) {
				portalrules.add(rule);
			}
		} catch (IOException e) {
			// LOG.error("There was an error reading regex filter {}",
			// urlFilterNode.asText(), e);
		}
		// }
		return portalrules;
	}

	public PortalRule createRule(String line) throws IOException {
		// char first = line.charAt(0);
		char first = '+';
		boolean sign;
		switch (first) {
		case '+':
			sign = true;
			break;
		case '-':
			sign = false;
			break;
		// case ' ':
		// case '\n':
		// case '#': // skip blank & comment lines
		// return null;
		default:
			throw new IOException("Invalid first character: " + line);
		}

		// String regex = line.substring(1);
		String regex = line;
		// System.out.println("Adding rule [{}]" + regex);
		// LOG.trace("Adding rule [{}]", regex);
		PortalRule rule = createRule(sign, regex);
		return rule;
	}

	public String applyUrlFilter(String url) {

		for (PortalRule rule : portalrules) {
			// System.out.println(rule.regex + rule.sign);

			if (rule.accept()) {
				if (!rule.match(url)) {
					// System.out.println(" not matching include condition");
					return null;
				}

			} else {

				if (rule.match(url)) {
					// System.out.println("matching exclude condition");
					return null;
				}

			}
		}

		return url;

	}

	public PortalRule createRule(boolean sign, String regex) {
		return new PortalRule(sign, regex);
	}



	/**
	 * Represents a function that accepts two arguments and produces a result. This
	 * is the three-arity specialization of {@link Function}.
	 *
	 * <p>
	 * This is a <a href="package-summary.html">functional interface</a> whose
	 * functional method is {@link #apply(Object, Object)}.
	 *
	 * @param <S> the type of the first argument to the function
	 * @param <T> the type of the second argument to the function
	 * @param <U> the type of the third argument to the function
	 * @param <R> the type of the result of the function
	 *
	 * @see Function
	 * @since 1.8
	 */
	@FunctionalInterface
	public interface TriFunction<S, T, U, R> {

		/**
		 * Applies this function to the given arguments.
		 *
		 * @param s the first function argument
		 * @param t the second function argument
		 * @param u the third function argument
		 * @return the function result
		 */
		R apply(S s, T t, U u);

		/**
		 * Returns a composed function that first applies this function to its input,
		 * and then applies the {@code after} function to the result. If evaluation of
		 * either function throws an exception, it is relayed to the caller of the
		 * composed function.
		 *
		 * @param <V>   the type of output of the {@code after} function, and of the
		 *              composed function
		 * @param after the function to apply after this function is applied
		 * @return a composed function that first applies this function and then applies
		 *         the {@code after} function
		 * @throws NullPointerException if after is null
		 */
		default <V> TriFunction<S, T, U, V> andThen(Function<? super R, ? extends V> after) {
			Objects.requireNonNull(after);
			return (S s, T t, U u) -> after.apply(apply(s, t, u));
		}
	}


}

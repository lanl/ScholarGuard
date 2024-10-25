package gov.lanl.crawler.input;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.servlet.DefaultServlet;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.ServletRegistration;
//import javax.servlet.ServletRegistration;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.ArraySet;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
//import org.glassfish.jersey.servlet.ServletContainer;

import gov.lanl.crawler.resource.GrabResource;
import gov.lanl.crawler.resource.InboxResource;
import gov.lanl.crawler.resource.InitMsgServlet;
import gov.lanl.crawler.resource.TransitResource;
import gov.lanl.crawler.resource.ResultsResource;
import gov.lanl.crawler.resource.SubmitResource;

public enum InputServer {
	INSTANCE;

	public static final int DEFAULT_PORT = 80;
	public static final String DEFAULT_DIR = "/var/www";

	private int port = DEFAULT_PORT;
	public static final int DEFAULT_MAX_THREADS = 500;
	public static final int DEFAULT_MIN_THREADS = 5;

	public static final String CONFIG_MIN_THREADS = "rest.min.grizzly.threads";
	public static final String CONFIG_MAX_THREADS = "rest.max.grizzly.threads";
	private int minThreads = DEFAULT_MIN_THREADS;
	private int maxThreads = DEFAULT_MAX_THREADS;
	String JERSEY_SERVLET_CONTEXT_PATH = "";
    //String sdir;
	static {
   	 try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	
	private static String API_PACKAGE = "gov.lanl.crawler.resource";
	private static final Logger logger = Logger.getLogger(InputServer.class.getName());

	public void startServer() {
		startServer(DEFAULT_PORT, DEFAULT_DIR);

	}

	private HttpServer httpServer;
	public Map<String, String> prop;

	//private static final String[] PACKAGES = new String[] { AcceptMessageResource.class.getPackage().getName(),
	//		"com.fasterxml.jackson.jaxrs.json" };

	private static void createDefaultServlet(WebappContext context,String sdir) {
        ArraySet<File> baseDir = new ArraySet<>(File.class);
        //final String pathToTargetDirectory = InputServer.class.getClassLoader().getResource(".").getPath();
        //System.out.println("static:"+pathToTargetDirectory);
        
        baseDir.add(new File(sdir));
        ServletRegistration defaultServletReg 
                = context.addServlet("DefaultServlet", new DefaultServlet(baseDir) {});
        defaultServletReg.addMapping("/capture/wabac/*");
    }
	
	public void startServer(int port, String sdir) {
		this.port = port;
		//this.sdir = sdir;

		try {
			System.out.println("Starting grizzly...");

			URI serverUri = UriBuilder.fromUri("http://localhost/").port(port).build();

			Set myset= new HashSet();
			myset.add(GrabResource.class);
			myset.add(InboxResource.class);
			myset.add(TransitResource.class);
			myset.add(ResultsResource.class);
			myset.add(MultiPartFeature.class);
			
		    myset.add(SubmitResource.class);
			ResourceConfig rc = new ResourceConfig().registerClasses(myset);
					//register(InboxResource.class).register(ESAPIResource.class);
					//.register(AcceptMessageResource.class);
			
			httpServer = GrizzlyHttpServerFactory.createHttpServer(serverUri, rc);
			
			prop = loadConfigFile();
			//setThreadLimits(prop);
			// Initialize and register Jersey Servlet
			WebappContext context = new WebappContext("WebappContext", JERSEY_SERVLET_CONTEXT_PATH);
			context.addListener(InitMsgServlet.class);
			createDefaultServlet(context,sdir);
			FilterRegistration registration = context.addFilter("ServletContainer", ServletContainer.class);
			registration.setInitParameter("javax.ws.rs.Application", ResourceConfig.class.getName());
			registration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), "/*");
			context.deploy(httpServer);
			

			httpServer.start();

			setThreadLimits(prop);

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public String getBaseUri() {
		return getLocalhostBaseUri(port);
	}

	public void stopServer() {
		httpServer.stop();
	}

	public static String getLocalhostBaseUri() {
		return getLocalhostBaseUri(DEFAULT_PORT);
	}

	public static String getLocalhostBaseUri(int port) {
		return "http://localhost:" + port + "/";
	}

	/*
	 * Check if there are thread limits set in the config file, use them if they are
	 * integers. Otherwise fall back to defaults.
	 */

	public static Map<String, String> loadConfigFile() {

		ClassLoader cl = InputServer.class.getClassLoader();

		java.io.InputStream in;

		if (cl != null) {
			in = cl.getResourceAsStream("my.properties");
		} else {
			in = ClassLoader.getSystemResourceAsStream("my.properties");

		}
		System.out.println("Using configuration from classpath");

		return in != null ? loadProperties(in) : new HashMap<String, String>();

	}

	public static Map<String, String> loadProperties(InputStream stream) {
		Properties props = new Properties();
		try {

			try {
				props.load(stream);
			} finally {
				stream.close();
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to load my.properties", e);
		}
		Set<Entry<Object, Object>> entries = props.entrySet();
		Map<String, String> stringProps = new HashMap<String, String>();
		for (Entry<Object, Object> entry : entries) {
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();
			stringProps.put(key, value);

		}
		return stringProps;
	}

	private void setThreadLimits(Map prop) {

		if (prop.containsKey(CONFIG_MIN_THREADS)) {
			try {
				minThreads = Integer.valueOf((String) prop.get(CONFIG_MIN_THREADS));
			} catch (Exception e) {
			}
		}

		if (prop.containsKey(CONFIG_MAX_THREADS)) {
			try {
				maxThreads = Integer.valueOf((String) prop.get(CONFIG_MAX_THREADS));
				System.out.println("test" + maxThreads);
			} catch (Exception e) {
			}
		}

		// Ensure min threads is never larger than max threads
		minThreads = maxThreads >= minThreads ? minThreads : maxThreads;

		ThreadPoolConfig config = ThreadPoolConfig.defaultConfig().setPoolName("mypool").setCorePoolSize(minThreads)
				.setMaxPoolSize(maxThreads);

		NetworkListener listener = httpServer.getListeners().iterator().next();
		GrizzlyExecutorService threadPool = (GrizzlyExecutorService) listener.getTransport().getWorkerThreadPool();
		threadPool.reconfigure(config);
		System.out.println("maxthreads" + maxThreads);
		System.out.println("maxthreads" + minThreads);

	}
	/*
	 * public class MyApplication extends ResourceConfig {
	 * 
	 * public MyApplication() { // Resources. register(PredictorResource.class);
	 * 
	 * // MVC. // register(JspMvcFeature.class); } }
	 */
}

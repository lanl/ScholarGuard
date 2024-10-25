package gov.lanl.crawler.resource;

import java.io.BufferedReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.time.FastDateFormat;
//import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;

import com.digitalpebble.stormcrawler.sql.Constants;
import com.github.jsonldjava.utils.JsonUtils;

//import gov.lanl.crawler.Constants;
import gov.lanl.crawler.input.InputServer;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpHost;
//import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;

//import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
/*
@author Lyudmila Balakireva

*/

public class MsgPuller extends Thread {

	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	private Connection connection;
	private static String tableName;
	private static String template;
	private static String warcbaseurl;
	private static String warcfilesdir;
	// static URLPostClient postclient;
	URLPostClient trackerpostclient;
	private String baseurl;
	String inboxbaseurl = null;

	// static HttpClient hClient;
	static HttpClient httpClient;
	static RequestConfig config;
	static String remoteGeturl;
	static String localorchestratorposturl;
	    private static final int MAX_ALLOCATED_CONNECTIONS = 50;
	    private static final int MAX_ALLOCATED_CONNECTIONS_PER_ROUTE = 10;
	    private static final int DEFAULT_SOCKET_TIMEOUT = 3000;
	    private static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT = 3000;
	    private static final int DEFAULT_CONNECT_TIMEOUT = 3000;
	    private static final String HTTPS = "https";

	static {
		try {
			// new driver can do without it
			Class.forName("com.mysql.jdbc.Driver").newInstance();

			remoteGeturl = (String) conf.get("remotegeturl");
			// localtrackerPosturl= (String) conf.get("localorchestratorposturl");
			// localtrackerPosturl= (String) conf.get("capturebaseurl");
			// localorchestratorposturl = (String) conf.get("posturl");
			// myresearch.institute posted capture to tracker inbox.
			// why is that

			localorchestratorposturl = (String) conf.get("trackerposturl");

			// String lanlinstall = (String) conf.get("lanlinstall");
			// HttpConnectionManager man = new MultiThreadedHttpConnectionManager();
			// HttpConnectionManagerParams params = new HttpConnectionManagerParams();
			// params.setDefaultMaxConnectionsPerHost(16);
			// params.setConnectionTimeout(30000);
			// params.setSoTimeout(30000);

			// man.setParams(params);
			// hClient = new HttpClient(man);
			// if (lanlinstall.equals("true")){
			// hClient.getHostConfiguration().setProxy("proxyout.lanl.gov", 8080);
			// }
			//final Registry<ConnectionSocketFactory> socketFactoryRegistry = createSocketFactoryConfigration();
			//final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
			 PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
	         connManager.setDefaultMaxPerRoute(MAX_ALLOCATED_CONNECTIONS_PER_ROUTE);
	         connManager.setMaxTotal(MAX_ALLOCATED_CONNECTIONS);
	         
	        final RequestConfig requestConfig = RequestConfig.custom()
	                                                         //.setCookieSpec(CookieSpecs.DEFAULT)
	                                                         //.setMaxRedirects(maxRedirects)
	                                                         .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT)
	                                                         .setConnectionRequestTimeout(DEFAULT_CONNECTION_REQUEST_TIMEOUT)
	                                                         .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT)
	                                                         .build();
			httpClient = HttpClients.custom()
					.setDefaultRequestConfig(requestConfig)
					.setConnectionManager(connManager)
					.setRetryHandler(new StandardHttpRequestRetryHandler())
					.setRedirectStrategy(new LaxRedirectStrategy()).build();

			HttpHost proxy = new HttpHost("proxyout.lanl.gov", 8080, "http");
			config = RequestConfig.custom().setProxy(proxy).build();

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 private static Registry<ConnectionSocketFactory> createSocketFactoryConfigration() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
	        final Registry<ConnectionSocketFactory> socketFactoryRegistry;
	        final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustSelfSignedStrategy()).build();
	        final SSLConnectionSocketFactory cnnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
	        socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
	                .register(HTTPS, cnnectionSocketFactory)
	                .build();

	        return socketFactoryRegistry;
	    }
	*/
	private boolean running = true;
	// HttpClient client;

	// public MsgSender(HttpClient client) {
	// this.client = client;

	// }

	public MsgPuller() {
		String trackerinbox = (String) conf.get("trackerposturl");
		trackerpostclient = new URLPostClient(trackerinbox);
		baseurl = (String) conf.get("capturebaseurl");
	}

	@Override
	public void run() {
		// Keeps running indefinitely, until the termination flag is set to false
		while (running) {
			// selectMessage();
			//selectids("test");
		
			selectids_poll_post("false");
			selectids_poll_post("true");
			selectids_poll_post("maybe");
			

			try {
				TimeUnit.SECONDS.sleep(60);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// update_dups();
			// selectFixMessage();
		}
	}

	// Terminates thread execution
	public void halt() {
		this.running = false;
	}

	// using proxy since external server
	public String getMessagefromRemote(String url) {
		// System.out.println("url" + url);
		String remurl = remoteGeturl + url;
		System.out.println("getMessagefromRemote url:" + remurl);
		HttpGet mget = new HttpGet(remurl);
		mget.setConfig(config);
		String msg = null;
		int returnCode = 0;
		try {
			HttpResponse response = httpClient.execute(mget);
			System.out.println("return code from remote capture:" + returnCode);
			returnCode = response.getStatusLine().getStatusCode();
			if (returnCode == 200) {
				Scanner sc = new Scanner(response.getEntity().getContent());
				StringBuffer sb = new StringBuffer();
				while (sc.hasNext()) {
					sb.append(sc.nextLine());
				}
				// msg = response.getEntity(). mget.getResponseBodyAsString();
				msg = sb.toString();
				System.out.println(" obtained  msg for orchestrator:" + msg);
			}
			if (returnCode == 400) {
				updateOldStatus(url, String.valueOf(returnCode));
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		finally {
			mget.releaseConnection();
		}
		return msg;
	}

	/*
	 * public String getMessage(String url) { System.out.println("url"+url);
	 * GetMethod mget = new GetMethod(url); String msg = null; int returnCode = 0;
	 * try { returnCode = hClient.executeMethod(mget);
	 * System.out.println("return code" + returnCode); if (returnCode==200) { msg =
	 * mget.getResponseBodyAsString(); System.out.println("msg"+msg); }
	 * 
	 * } catch (HttpException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); } catch (IOException e) { // TODO Auto-generated catch
	 * block e.printStackTrace(); }
	 * 
	 * finally { mget.releaseConnection(); } return msg; }
	 */

	private void updateMessageStatus(String id, String code, String msg) {
//msg change for prepared statement
		String sql = "update transit_messages set status='SEND', "
				+ "capture_status='Y',"
				//+ " outmsg=\'"+msg+ "\', "
						+ "tracker_code=" + code + " where id =\"" + id + "\";";
		System.out.println("updateMessageStatus:" + sql);
		Statement st = null;
		try {
			st = this.connection.createStatement();
			st.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void updateOldStatus(String id, String code) {
		int foo = Integer.parseInt(code);
		String sql = "update transit_messages set status='DEL', tracker_code=" + code + " where id =\"" + id + "\";";
		System.out.println("updateOldStatus" + sql);
		Statement st = null;
		try {
			st = this.connection.createStatement();
			st.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void updateMessagePoling(String id) {
		String polldate = "";

		String count = "0";
		String sql = "update transit_messages set poll_count=poll_count+1 , polldate='"
				+ new Timestamp(new Date().getTime()) + "' where id =\"" + id + "\";";
		System.out.println("updateMessagePoling:" + sql);
		Statement st = null;
		try {
			st = this.connection.createStatement();
			st.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    //this is 
	private void selectids_poll_post(String polled) {

		prepare(conf);
		String query = "SELECT id, event FROM   transit_messages  " + " where status= 'ARRIVED'  and polldate is null "

				+ "  limit 10;";

		if (polled.equals("true")) {
			query = "SELECT id, event  FROM   transit_messages  "
					+ " where status= 'ARRIVED'  and  poll_count < 5  and polldate < date_sub(now(),interval 30 MINUTE) order by reqdate "

					+ "  limit 10;";
		}
		if (polled.equals("test")) {
			query = "SELECT id, event FROM   transit_messages  "
					+ " where status= 'ARRIVED'  and  poll_count is NULL  order by reqdate "

					+ "  limit 10;";
		}

		if (polled.equals("maybe")) {
			query = "SELECT id, event FROM   transit_messages  "
					+ " where status= 'SEND'  and  tracker_code > 399  order by reqdate "

					+ "  limit 10;";
		}
		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		HttpPost post = new HttpPost(localorchestratorposturl);
		try {
			st = this.connection.createStatement();

			rs = st.executeQuery(query);

			while (rs.next()) {
				Map result = new HashMap();
				String _id = rs.getString("id");
				System.out.println("_id" + _id);
				String event = rs.getString("event");
				// url of remote get message construct;
				// need to add if msg says that no message exists, then add capture status

				String msg = getMessagefromRemote(event);
				if (msg != null) {
					try {
						//HttpPost post = new HttpPost(localorchestratorposturl);
						// no proxy toward orchestrator
						// post.setConfig(config);
						post.addHeader("Content-Type", "application/ld+json");
						StringEntity entity = new StringEntity(msg);
						post.setEntity(entity);
						HttpResponse response = httpClient.execute(post);

						int code = response.getStatusLine().getStatusCode();
						//HeaderIterator head = response.headerIterator();
						//while (head.hasNext()) {
						//	Header h = (Header) head.next();
						//	System.out.println(h.getName() + ":" + h.getValue());
						//}

						// PostMethod pm = new PostMethod(localorchestratorposturl);
						// RequestEntity requestEntity = new StringRequestEntity(msg); // new
						// InputStreamRequestEntity(msg);
						// pm.setRequestEntity(requestEntity);
						// int responseCode = hClient.executeMethod(pm);
						String ocode = String.valueOf(code);
						System.out.println("post to orchestrator" + String.valueOf(code));
						updateMessageStatus(_id, ocode, msg);
					} catch (Exception e) {
						// LOG.error("Exception while querying table", e);
					}

				} else {
					updateMessagePoling(_id);
				}

			} // while

		} catch (SQLException e) {
			// LOG.error("Exception while querying table", e);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			post.releaseConnection();
			try {
				if (rs != null)
					rs.close();
			} catch (SQLException e) {
				// LOG.error("Exception closing resultset", e);
			}
			try {
				if (st != null)
					st.close();
			} catch (SQLException e) {
				// LOG.error("Exception closing statement", e);
			}

			try {
				connection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public String curr() {
		Date gen = new Date();
		TimeZone tz = TimeZone.getTimeZone("GMT");
		FastDateFormat timeTravelJsFormatter = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", tz, Locale.US);
		String genday = timeTravelJsFormatter.format(gen);
		return genday;
	}

	public void prepare(Map stormConf) {// TopologyContext context,
		tableName = (String) stormConf.get(Constants.MYSQL_TABLE_PARAM_NAME);
		System.out.println("table:" + tableName);
		try {

			// SQL connection details
			String url = (String) stormConf.get(Constants.MYSQL_URL_PARAM_NAME);
			// "jdbc:mysql://localhost:3306/crawl");
			String user = (String) stormConf.get(Constants.MYSQL_USER_PARAM_NAME);
			String password = (String) stormConf.get(Constants.MYSQL_PASSWORD_PARAM_NAME);

			connection = DriverManager.getConnection(url, user, password);

		} catch (SQLException ex) {
			// LOG.error(ex.getMessage(), ex);
			ex.printStackTrace();
			// throw new RuntimeException(ex);
		}

	}

}

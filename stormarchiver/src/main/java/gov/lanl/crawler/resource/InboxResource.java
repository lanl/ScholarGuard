package gov.lanl.crawler.resource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.sql.Constants;
import com.digitalpebble.stormcrawler.util.URLPartitioner;
import com.fasterxml.jackson.core.JsonParseException;
import com.github.jsonldjava.utils.JsonUtils;

//import gov.lanl.crawler.Constants;
import gov.lanl.crawler.input.InputServer;

//this is endpoint to post messages

@Path("/capture/inbox")

public class InboxResource {
	private Connection connection;
	private static String tableName;
	private URLPartitioner partitioner;
	private int maxNumBuckets = -1;
	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	private static String baseurl;
	static String redomsg="";
	static HttpClient hClient;
	static {
		try {
			// new driver can do without it
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			baseurl = (String) conf.get("capturebaseurl");
			redomsg = (String) conf.get("redomsg");
			
			 HttpConnectionManager man = new MultiThreadedHttpConnectionManager();
			 HttpConnectionManagerParams params = new HttpConnectionManagerParams();
			    params.setDefaultMaxConnectionsPerHost(16);
			    params.setConnectionTimeout(30000);
			    params.setSoTimeout(30000);
			  
			    man.setParams(params);
			    hClient = new HttpClient(man);
			
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

	
	
	
	
	public String  redoMessage(String msg){
		if (redomsg.equals("false")) return msg;
		String id;
		Object jsonObject;
		System.out.println(msg);
		try {
			jsonObject = JsonUtils.fromString(msg);		
		Map<String, Object> root = (Map) jsonObject;
		Map<String, Object> event = (Map<String, Object>) root.get("event");
		Map<String, Object> object = (Map<String, Object>) event.get("object");
		if (!object.containsKey("id")) return null;
		id = (String) object.get("id");
		
		System.out.println("id" + id);
		
		String posturl = (String) event.get("to");
		String baseurlurl = (String) event.get("tracker:eventBaseUrl");
		System.out.println("to"+posturl);
		System.out.println("base"+baseurlurl);
		//String trackermsg  = loadfromURL(id);	
		String trackermsg  = getMessage(id);
		if (trackermsg==null) return "bad";
		System.out.println("trackermsg"+ trackermsg);
		Object jsontracker = JsonUtils.fromString( trackermsg);
		Map<String, Object> mroot = (Map) jsontracker;
		Map<String, Object> mevent = (Map<String, Object>) mroot.get("event");
		mevent.put("to", posturl);
	    mevent.put("eventBaseUrl", baseurlurl );
	    String tmsg = JsonUtils.toPrettyString(mroot);
	    System.out.println(tmsg);
	   // SimpleEntry tuple = new java.util.AbstractMap.SimpleEntry(mroot,trackermsg);
	    return  tmsg;
	    } catch (JsonParseException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    } catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	     }
		return null;
	}
	
	
	

	
	
	
	public String getMessage(String url) {
		System.out.println("url"+url);
		GetMethod mget = new GetMethod(url);
		String msg = null;
		int returnCode = 0;
		try {		
			returnCode =  hClient.executeMethod(mget);
			System.out.println("return code" + returnCode);
			if (returnCode==200) {
				msg = mget.getResponseBodyAsString();
				System.out.println("msg"+msg);
			}			
			
		} catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		finally {
			mget.releaseConnection();
		}
		return msg;
	}

	
	
	private String loadFromURL(String urlmsg) {

		StringBuffer sb = new StringBuffer();
		try {

			URL url = new URL(urlmsg);
			
			InputStream is = url.openStream();
		
			BufferedReader in = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);
              System.out.println(line);
			}
			in.close();
		} catch (IOException e) {
			// LOG.error("There was an error reading the default-regex-filters file");
			e.printStackTrace();
		}

		return sb.toString().trim();
	}
	
	
	
	@POST
	// @Consumes(MediaType.APPLICATION_JSON)
	@Consumes("application/ld+json")
	public Response notifyPOST(String msg) {
		prepare(conf);
		// System.out.println("message:"+msg);
		// Date nextFetch = new Date();
		Timestamp nextFetch = new Timestamp(new Date().getTime());
		String url = "";
		String id = "0";
		String mess = "";
		Boolean new_message = false;
		//int url_count = 0;
		List tableurls = new ArrayList();
		Integer notprocessed = 0;
		List<String> hrefs = new ArrayList();
		Map<String,String> traces = new HashMap ();
		List<String> oldevents = new ArrayList();
		Boolean archive = true;
		//forgot why redo needed
		
		System.out.print(msg);
		try {
			String tmsg = redoMessage(msg);	
			//String tmsg = null;
			Map<String, Object> root = null;
			if (tmsg==null) {
			Object jsonObject = JsonUtils.fromString(msg);
			             root = (Map) jsonObject;
			}
			else {
				if (tmsg.equals("bad")) {
					ResponseBuilder r = Response.status(500);
					return r.build();
					}
				
				Object jsonObject = JsonUtils.fromString(tmsg);
	             root = (Map) jsonObject;
	             msg = tmsg;
	             
			}
			
			Map<String, Object> event = (Map<String, Object>) root.get("event");
			Map<String, Object> obj = (Map<String, Object>) event.get("object");
			//if (!obj.containsKey("id")) return null;
			
			//id = (String) obj.get("id");
			
			//System.out.println("id" + id);
			
			//String posturl = (String) event.get("to");
			//String baseurlurl = (String) event.get("tracker:eventBaseUrl");
		
			//System.out.println("to"+posturl);
			//System.out.println("base"+baseurlurl);
			
			id = (String) event.get("@id");
			System.out.println("id" + id);
		
			//Map<String, Object> obj = (Map<String, Object>) event.get("object");
			Integer itemcount = (Integer) obj.get("totalItems");
			System.out.println("itemcount" + itemcount.toString());
			List<Map> urls = (List) obj.get("items");
			System.out.println("urls" + urls.toString());
			connection.setAutoCommit(false);
			
			SimpleEntry<String, String> tuple = selectInputMessageStatus(id);
			mess = tuple.getKey();
			String mess_id = tuple.getValue();
			System.out.println("mess_id to process:"+ mess_id);
			
			for (Map element : urls) {
				String href = (String) element.get("href");
				String trace = (String) element.get("tracker:traceUrl");
				      if (trace!=null) {
					     traces.put(href,trace);
				        }
				
				System.out.println(href);
				hrefs.add(href);				
				String old_event_id = getEventIdofURL(href);
				System.out.println("old_ev" + old_event_id);
				if(!old_event_id.equals("x")) {
					//old urls
					//tableurls.add(href);	
				//checking if url already got processed and message was already send  	
				   // String event_id = getEventId(href);
				    System.out.println("event_id"+ old_event_id);
				  
				    Integer scount = checkoutputMessagebyEventId( old_event_id); 
				    System.out.println("is output message exists:"+scount);
					    if (scount.intValue()==0) {
					       tableurls.add(href);	
					       notprocessed = 1; //meaning was not processed
					       System.out.println("notprocessed yet");
				         }
					    else {
					    	//delete  old events from urls table;
					    	 deleteURLS(old_event_id);
					    	 
					    }
				}
			}
				//System.out.println("url_count:"+url_count);
			System.out.println("id" + id);
			
			
			
			if (mess!=null) {
				System.out.print("status"+mess);
			}
			if (mess.equals("")) {
				new_message = true;
				System.out.println("new message id" +id);
			}
			String query = " input_messages" +
			               " (event, reqdate, msg,id,status)" + 
					       " values (?, ?, ?, md5(?),?)";

			if (new_message) {
				query = "INSERT IGNORE INTO " + query;
			} else if (mess.equals("ARRIVED")){
				query = "INSERT IGNORE INTO " + query;
			}
			else
			{
				
				query = "REPLACE INTO " + query;
			}

			PreparedStatement preparedStmt;

			preparedStmt = connection.prepareStatement(query);
			preparedStmt.setString(1, id);
			preparedStmt.setObject(2, nextFetch);
			preparedStmt.setString(3, msg);
			preparedStmt.setString(4, id);
			if ( notprocessed>0) {
				preparedStmt.setString(5, "DUP");	
			}
			else {
			preparedStmt.setString(5, "ARRIVED");
			}
			long start = System.currentTimeMillis();

			preparedStmt.execute();
			preparedStmt.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
        if (mess.equals("SEND")) {
         	archive = false;	
		}
		if ( notprocessed>0) {
			archive = false;	
		}
		
		// if duplicate message, do not archive again
		if (archive) {
			//status for all urls will be updated to "discovered" -refetch will be triggered
			
			for (String href : hrefs) {
				Map<String, String[]> map = new HashMap();
				map.put("url.path", new String[] { href });
				map.put("depth", new String[] { "0" });
				map.put("event", new String[] { id });
				 String customtrace = traces.get(href);
				 if (customtrace!=null) {
				    map.put("trace",new String[] { customtrace} );
				 }
				Metadata metadata = new Metadata(map);
                 System.out.println(" archive href:" + href);
				try {
					if (tableurls.contains(href)) {
						//replace should not be happening anymore
						System.out.println("replace");
						// String redir = null;
						// List redirs = new ArrayList();
						// while( (redir = getRedirectedURL(href))!=null) {
						//	 System.out.println("redir"+ redir);
						//	 redirs.add(redir);
						// }
						 //Iterator it = redirs.iterator();
						 //while (it.hasNext()) {
							// String durl = (String) it.next();
							 //System.out.println("deleted"+durl);
							 //deleteURL(durl);
						 //}
						 update_table(href, Status.FETCH_ERROR, metadata, nextFetch, id);
					}
					else {
						 System.out.println("insert");
						//insert
					update_table(href, Status.DISCOVERED, metadata, nextFetch, id);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		
		ResponseBuilder r = Response.status(201);
		r.header("Location", baseurl + "inbox/" + id);

		try {
			boolean au = connection.getAutoCommit();
			if (au==false) {
			connection.commit();
			}
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return r.build();
	}

	@GET
	@Path("/{id:.*}")

	public Response GetMessage(@Context UriInfo ui, InputStream stream) {
		URI baseurl = ui.getBaseUri();
		System.out.println("baseurl:" + baseurl.toString());
		URI ur = ui.getRequestUri();
		String id = ur.toString().replaceFirst(baseurl.toString() + "capture/inbox/", "");
		System.out.println("url:" + id);
		prepare(conf);
		String msg = selectInputMessage(id);
		try {
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ResponseBuilder r = Response.ok(msg);
		r.header("Content-Type", "application/ld+json");
		return r.build();
	}

	public void prepare(Map stormConf) {// TopologyContext context,
		// OutputCollector collector) {

		tableName = (String) stormConf.get(Constants.MYSQL_TABLE_PARAM_NAME);

		//System.out.println("table:" + tableName);
		try {

			// SQL connection details
			String url = (String) stormConf.get(Constants.MYSQL_URL_PARAM_NAME);
			// "jdbc:mysql://localhost:3306/crawl");
			String user = (String) stormConf.get(Constants.MYSQL_USER_PARAM_NAME);
			String password = (String) stormConf.get(Constants.MYSQL_PASSWORD_PARAM_NAME);
			// return DriverManager.getConnection(url, user, password);

			connection = DriverManager.getConnection(url, user, password);
			// String text = connection.getSchema();
			// System.out.println(text);
		} catch (SQLException ex) {
			// LOG.error(ex.getMessage(), ex);
			ex.printStackTrace();
			// throw new RuntimeException(ex);
		}

	}

	private String selectInputMessage(String id) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT msg FROM  input_messages ";
		query += " WHERE id = md5( \"" + id + "\")";

		String msg = "";

		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset
			 if(!rs.isBeforeFirst()){
	                System.out.println("No Data Found"); //data not exist
	                return msg;
	            }
	           else {
			while (rs.next()) {
				msg = rs.getString("msg");

			}
	           }

		} catch (SQLException e) {
			// LOG.error("Exception while querying table", e);
		} finally {
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
		}
		return msg;
	}

	private String selectOutputMessage(String id) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT msg FROM  output_messages ";
		query += " WHERE id = md5( \"" + id + "\")";

		String msg = "";

		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset
			 if(!rs.isBeforeFirst()){
	                System.out.println("No Data Found"); //data not exist
	                return msg;
	            }
	           else {
			while (rs.next()) {
				msg = rs.getString("msg");

			}
	           }

		} catch (SQLException e) {
			// LOG.error("Exception while querying table", e);
		} finally {
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
		}
		return msg;
	}

	private  SimpleEntry<String, String> selectInputMessageStatus(String id) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT status ,id FROM  input_messages ";
		query += " WHERE id = md5( \"" + id + "\");";

		String msg = "";
        String ev_id = "";
		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset

			while (rs.next()) {
				msg = rs.getString("status");
                ev_id = rs.getString("id");
			}
			
			 if(!rs.isBeforeFirst()){
	                System.out.println("No Data Found"); //data not exist
	                SimpleEntry<String, String> myEntry = new SimpleEntry<String, String>(msg, ev_id);
	                return myEntry;
	            }
	           else {
	        	   
	              // data exist
	        	   
	        	    while (rs.next()) {
	   				msg = rs.getString("status");
         
	   			}
	        	    SimpleEntry<String, String> myEntry = new SimpleEntry<String, String>(msg, ev_id);
	        	    return  myEntry;
	        	   
	              }   

		} catch (SQLException e) {
			System.out.println(e);
			// LOG.error("Exception while querying table", e);
		} finally {
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
		}
		return   new SimpleEntry<String, String>(msg, ev_id);
	}

	
	String getRedir(String metadata) {	
		String url= null;
			String[] fields = metadata.split("\t", -1);
			for (int i = 0; i < fields.length; ++i) {
				System.out.println("fields:" + fields[i]);
				if (fields[i].startsWith("_redirTo")) {
					url = fields[i];
					

				}
			}
			

		return url;
	}

	
	
	private String getRedirectedURL(String url) {
		String query = "SELECT status, metadata  FROM  urls ";
	query += " WHERE id = md5( \"" + url + "\");";

	String status  = "";
	String metadata ="";
	
	long timeStartQuery = System.currentTimeMillis();

	// create the java statement
	Statement st = null;
	ResultSet rs = null;
	try {
		st = this.connection.createStatement();

		// execute the query, and get a java resultset
		rs = st.executeQuery(query);

		long timeTaken = System.currentTimeMillis() - timeStartQuery;
		// queryTimes.addMeasurement(timeTaken);

		// iterate through the java resultset
		if(!rs.isBeforeFirst()){
            System.out.println("No Data Found"); //data not exist
            return null;
        }
       else {
		while (rs.next()) {
			status = rs.getString("status");
			
			if (status.equals("REDIRECTION")) {
				String nurl = getRedir(metadata);
				return nurl;
			}
		}
       }
	} catch (SQLException e) {
		// LOG.error("Exception while querying table", e);
	} finally {
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
	}
	return null;
}

	private void deleteURL(String url) {
		String query = "delete  FROM  urls ";
	query += " WHERE id = md5( \"" + url + "\");";

	long timeStartQuery = System.currentTimeMillis();

	// create the java statement
	Statement st = null;
	int rs = 0;
	try {
		st = this.connection.createStatement();

		// execute the query, 
		rs = st.executeUpdate(query);

		long timeTaken = System.currentTimeMillis() - timeStartQuery;
		// queryTimes.addMeasurement(timeTaken);		
		
	} catch (SQLException e) {
		// LOG.error("Exception while querying table", e);
	} finally {
		
			
		try {
			if (st != null)
				st.close();
		} catch (SQLException e) {
			// LOG.error("Exception closing statement", e);
		}
	}
	
}

	
	private void deleteURLS(String event_id) {
		String query = "delete  FROM  urls ";
	query += " WHERE event_id =  \"" + event_id + "\";";

	long timeStartQuery = System.currentTimeMillis();

	// create the java statement
	System.out.println(query);
	Statement st = null;
	int rs = 0;
	try {
		st = this.connection.createStatement();

		// execute the query, 
		rs = st.executeUpdate(query);

		long timeTaken = System.currentTimeMillis() - timeStartQuery;
		connection.commit();
		// queryTimes.addMeasurement(timeTaken);		
		
	} catch (SQLException e) {
		// LOG.error("Exception while querying table", e);
	} finally {
		
			
		try {
			if (st != null)
				st.close();
		} catch (SQLException e) {
			// LOG.error("Exception closing statement", e);
		}
	}
	
}
	
	
	private String getEventIdofURL(String url) {
			String query = "SELECT event_id FROM  urls ";
		query += " WHERE id = md5( \"" + url + "\");";
         String ev_id = "x";
		Integer count  = 0;
		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);
            System.out.println("query"+ query);
			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset
			if(!rs.isBeforeFirst()){
                System.out.println("No Data Found"); //data not exist
                return "x";
            }
           else {
			while (rs.next()) {
				System.out.println("Data found");
				ev_id = rs.getString(1);
				System.out.println(ev_id);
			}
			
           }
		} catch (SQLException e) {
			// LOG.error("Exception while querying table", e);
		} finally {
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
		}
		return ev_id;
	}
	
	private String getEventId(String id) {
		String query = "SELECT event_id FROM  urls ";
	query += " WHERE id = md5( \"" + id + "\")";

	String event  = "";
	long timeStartQuery = System.currentTimeMillis();

	// create the java statement
	Statement st = null;
	ResultSet rs = null;
	try {
		st = this.connection.createStatement();

		// execute the query, and get a java resultset
		rs = st.executeQuery(query);

		long timeTaken = System.currentTimeMillis() - timeStartQuery;
		// queryTimes.addMeasurement(timeTaken);

		// iterate through the java resultset

		while (rs.next()) {
			event = rs.getString(1);
		}

	} catch (SQLException e) {
		// LOG.error("Exception while querying table", e);
	} finally {
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
	}
	return event;
}

	
	private String selectoutputMessage(String id) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT msg FROM  output_messages ";
		query += " WHERE id = md5( \"" + id + "\")";

		String msg = "";

		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset
			 if(!rs.isBeforeFirst()){
	                System.out.println("No Data Found"); //data not exist
	                return msg;
	            }
	           else {
			while (rs.next()) {
				msg = rs.getString("msg");

			}
	           }

		} catch (SQLException e) {
			// LOG.error("Exception while querying table", e);
		} finally {
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
		}
		return msg;
	}

	private Integer checkoutputMessagebyEventId(String id) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT count(*) FROM  output_messages ";
		query += " WHERE id =  \"" + id + "\"";

		Integer count = 0;

		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset

			while (rs.next()) {
				count = rs.getInt(1);

			}

		} catch (SQLException e) {
			// LOG.error("Exception while querying table", e);
		} finally {
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
		}
		return count;
	}
	
	
	public void update_table(String url, Status status, Metadata metadata, Date nextFetch, String event_id) {
		// the mysql insert statement
		String query = tableName + " (url, status, nextfetchdate, metadata, bucket," + " host,id, event_id)"
				+ " values (?, ?, ?, ?, ?, ?, md5(?), md5(?))";
		//System.out.println(query);
		StringBuffer mdAsString = new StringBuffer();
		for (String mdKey : metadata.keySet()) {
			String[] vals = metadata.getValues(mdKey);
			for (String v : vals) {
				mdAsString.append("\t").append(mdKey).append("=").append(v);
			}
		}
		String host = null;
		int partition = 0;

		/*
		 * String partitionKey = partitioner.getPartition(url, metadata); if
		 * (maxNumBuckets > 1) { // determine which shard to send to based on the host /
		 * domain / IP partition = Math.abs(partitionKey.hashCode() % maxNumBuckets); }
		 */
		String partitionKey = null;
		if (partitionKey == null) {
			URL u;
			try {
				u = new URL(url);
				host = u.getHost();
				//System.out.println("host:" + host);
			} catch (MalformedURLException e1) {
				// LOG.warn("Invalid URL: {}", url);

			}
		}

		// create in table if does not already exist
		if (status.equals(Status.DISCOVERED)) {
			query = "INSERT IGNORE INTO " + query;
		} else {
			query = "REPLACE INTO " + query;
		}

		PreparedStatement preparedStmt;
		try {
			preparedStmt = connection.prepareStatement(query);

			preparedStmt.setString(1, url);
			preparedStmt.setString(2, Status.DISCOVERED.toString());
			preparedStmt.setObject(3, nextFetch);
			preparedStmt.setString(4, mdAsString.toString());
			preparedStmt.setInt(5, partition);
			preparedStmt.setString(6, host);
			preparedStmt.setString(7, url);
			preparedStmt.setString(8, event_id);

			long start = System.currentTimeMillis();
            System.out.println(preparedStmt);
			// execute the preparedstatement

			preparedStmt.execute();
			preparedStmt.close();

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}

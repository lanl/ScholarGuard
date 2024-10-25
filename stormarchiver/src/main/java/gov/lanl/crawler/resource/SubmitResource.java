package gov.lanl.crawler.resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.RandomStringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.sql.Constants;

import gov.lanl.crawler.input.InputServer;

import javax.ws.rs.core.Response.ResponseBuilder;
//import org.glassfish.jersey.media.multipart.MultiPartFeature;

@Path("/submit/")
public class SubmitResource {
	static String indexhtml_tmpl;
	static String navhtml;
	static String  tracedir;
	static String  tracebaseurl;
	private static String tableName;
	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	private Connection connection;
	static {

		// new driver can do without it
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		indexhtml_tmpl = ResultsResource.read_template("./templates/index.html");
		navhtml = ResultsResource.read_template("./templates/nav.html");
		tracebaseurl = (String) conf.get("tracebaseurl");
		tracedir = (String) conf.get("tracedir");
	}
	
	
	Metadata compose_metadata(String url, String eid, String traceurl){
		 Map<String, String[]> map = new HashMap();
			map.put("url.path", new String[] { url });
			map.put("depth", new String[] { "0" });
			map.put("event", new String[] { eid });			
			map.put("trace",new String[] { traceurl} );
			
			Metadata metadata = new Metadata(map);
			return metadata;
	}
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response postJob(@Context UriInfo uriInfo, @FormDataParam("input_url") String url, @FormDataParam("trace") InputStream file, @FormDataParam("trace") FormDataContentDisposition fileMetaData) {
     System.out.println("in post"+url);
    // String html_r = indexhtml_tmpl.replace("{{ NAVBAR }}", navhtml);
     //System.out.println(html_r);
     String gname=fileMetaData.getName();
     //System.out.println(gname);
     int length = 10;
     boolean useLetters = true;
     boolean useNumbers = false;
     String fname = RandomStringUtils.random(length, useLetters, useNumbers);
     String  uploadedFileLocation = tracedir+fname+".json";
     //System.out.println( uploadedFileLocation);
    
     writeToFile(file, uploadedFileLocation);
     String traceurl=tracebaseurl+ fname+".json";
     Timestamp nextFetch = new Timestamp(new Date().getTime());
	// MessageDigest md5 = null;
	 String key =url+traceurl+nextFetch ;
		
	 String eid = make_eid(key);
     Metadata metadata = compose_metadata( url,  eid, traceurl);
		//need logic to check if url already exists
     System.out.println("Before check:");
    
         prepare(conf);
		 int check = checkJobStatus(url);
		 if (check==0) {
			 System.out.println("New record:");
		 submitJob(url,tracebaseurl+ fname+".json",nextFetch,eid,"DISCOVERED") ;
		 update_table(url, Status.DISCOVERED,  metadata,  nextFetch,  eid);
		 }else {
			 System.out.println("HOLD:");
		 submitJob(url,tracebaseurl+ fname+".json",nextFetch,eid,"HOLD") ;
				
		 }
		 try {
				connection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		 
     //ResultsResource rs =new ResultsResource();
     //Response res=rs.GetResults() ;
	
		//ResponseBuilder r = Response.ok(html_r);
		
		//r.header("Content-Type", "text/html");
		//return r.build();
      URI uri = uriInfo.getBaseUriBuilder().path("../results").build();
     
      return Response.seeOther(uri).build();
    // return res;
	}
	
	String make_eid(String key){
		 MessageDigest md5 = null;
		 //String key =url+traceurl+nextFetch ;
			try {
				md5 = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // you can change it to SHA1 if needed!
			md5.update((key).getBytes(), 0, key.length());
		    String eid = new BigInteger(1, md5.digest()).toString(16);	
		return eid;
	}
	
	public void submitJob(String url,String traceurl,Timestamp nextFetch,String eid,String status) {
		System.out.println("submitjob");
		//prepare(conf);
		
		String query = "input_jobs" +
	               " (event_id, reqdate,url,trace_url,id,status)" + 
			       " values (?, ?, ?, ?,md5(?),?)";

	query = "INSERT IGNORE INTO " + query;
	
	try {

	PreparedStatement preparedStmt;

	preparedStmt = connection.prepareStatement(query);
	preparedStmt.setString(1, eid);
	preparedStmt.setObject(2, nextFetch);
	preparedStmt.setString(3, url);
	preparedStmt.setString(4, traceurl);
	preparedStmt.setString(5, url);//nado li eto mne
	preparedStmt.setString(6, status);//nado li eto mne
	long start = System.currentTimeMillis();

	preparedStmt.execute();
	preparedStmt.close();

} catch (Exception e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
	
	/*
	try {
		connection.close();
	} catch (SQLException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	*/
	
	}
	// save uploaded file to new location
	private void writeToFile(InputStream uploadedInputStream,
	                   String uploadedFileLocation) {
	   try {
	      OutputStream out = new FileOutputStream(new File(
	            uploadedFileLocation));
	      int read = 0;
	      byte[] bytes = new byte[1024];

	      out = new FileOutputStream(new File(uploadedFileLocation));
	      while ((read = uploadedInputStream.read(bytes)) != -1) {
	         out.write(bytes, 0, read);
	      }
	      out.flush();
	      out.close();
	   } catch (IOException e) {
	      e.printStackTrace();
	   }
	}
	
	public void update_table(String url, Status status, Metadata metadata, Date nextFetch, String event_id) {
		// the mysql insert statement
		//prepare(conf);
		System.out.println("update table");
		String query = tableName + " (url, status, nextfetchdate, metadata, bucket," + " host,id, event_id)"
				+ " values (?, ?, ?, ?, ?, ?, md5(?), ?)";
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
		String ev = getEventId(url) ;
		if (ev.equals("")) {
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

			//long start = System.currentTimeMillis();
            System.out.println(preparedStmt);
			// execute the preparedstatement

			preparedStmt.execute();
			preparedStmt.close();

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/*try {
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
	}
	
	
	private String getEventId(String url) {
		String query = "SELECT event_id FROM  urls ";
	query += " WHERE id = md5( \"" + url + "\")";

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

	
	
	private  int checkJobStatus(String url) {
		//prepare(conf);
		String query = "SELECT count(*) FROM  input_jobs ";
		query += " WHERE id = md5( \"" + url + "\") and status in (\"HOLD\", \"DISCOVERED\");";

		int count = 0;
       
		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			rs = st.executeQuery(query);

			while (rs.next()) {
				count = rs.getInt(1);
                
			}
						   
	       	    return  count;
	        	   	             
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
	/*	try {
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	*/	
		return  count;
		
		
	}
	//not used
	private  SimpleEntry<String, String> selectJobStatus(String url) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT status ,id FROM  input_jobs ";
		query += " WHERE id = md5( \"" + url + "\") and status in (\"HOLD\", \"DISCOVERED\");";

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
	
	public  void prepare(Map stormConf) {// TopologyContext context,
		tableName = (String) stormConf.get(Constants.MYSQL_TABLE_PARAM_NAME);
		//System.out.println("table:" + tableName);
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

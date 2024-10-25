package gov.lanl.crawler.resource;

import java.io.BufferedOutputStream;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.time.FastDateFormat;

import com.digitalpebble.stormcrawler.sql.Constants;
import com.github.jsonldjava.utils.JsonUtils;

//import gov.lanl.crawler.Constants;
import gov.lanl.crawler.input.InputServer;

/*
@author Lyudmila Balakireva

*/
public class MsgSender extends Thread {

	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	private Connection connection;
	private static String tableName;
	private static String template;
	private static String warcbaseurl;
	private static String warcfilesdir;
	//static URLPostClient postclient;
	URLPostClient trackerpostclient;
	private  String baseurl;
	static String captureownurl;
	String inboxbaseurl = null;
	static {
		try {
			// new driver can do without it
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			template = loadProfile("messagev2");
			warcbaseurl = (String) conf.get("warcbaseurl");
			warcfilesdir = (String) conf.get("warcfilesdir");
			captureownurl = conf.get("capturebaseurl");
			//String posturl = (String) conf.get("posturl");
			//postclient = new URLPostClient(posturl);
		
			//String trackerinbox = (String) conf.get("trackerposturl");
			//trackerpostclient = new URLPostClient(trackerinbox);
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

	private boolean running = true;
	// HttpClient client;

	// public MsgSender(HttpClient client) {
	// this.client = client;

	// }

	public MsgSender() {
		String trackerinbox = (String) conf.get("trackerposturl");
		trackerpostclient = new URLPostClient(trackerinbox);
		baseurl = (String) conf.get("capturebaseurl");
	}

	@Override
	public void run() {
		// Keeps running indefinitely, until the termination flag is set to false
		while (running) {
			selectMessage();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			update_dups();
			selectFixMessage();
		}
	}

	// Terminates thread execution
	public void halt() {
		this.running = false;
	}

	private void updateMessage(String id) {
		String sql = "update input_messages set status='SEND' where id =\"" + id + "\";";
		Statement st = null;
		try {
			st = this.connection.createStatement();
			st.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void update_dups(){
		prepare(conf);
		String query = "SELECT id, msg FROM  input_messages "
	                   + " where status = 'DUP' ;";
		// create the java statement
		Statement st = null;
		InboxResource ir = new InboxResource();
		ResultSet rs = null;		
		try {
			st = this.connection.createStatement();
		
			// execute the query, and get a java resultset
			rs = st.executeQuery(query);
			while (rs.next()) {
				Map result = new HashMap();
			    String	_id = rs.getString("id");
				System.out.println("_id" + _id);
				String msg = rs.getString("msg");
				ir.notifyPOST(msg);
			}
				
		}
	
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
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
	private void insertMessage(String id, String input_id, String msg, String sdate) {

		String query = "insert IGNORE into output_messages" + " (event, reqdate, msg,id,status)"
				+ " values (?, ?, ?, ?,?);";

		try {
			PreparedStatement preparedStmt;

			preparedStmt = connection.prepareStatement(query);

			preparedStmt.setString(1, id);
			preparedStmt.setObject(2, sdate);
			preparedStmt.setString(3, msg);
			preparedStmt.setString(4, input_id);
			preparedStmt.setString(5, "SEND");
			long start = System.currentTimeMillis();

			preparedStmt.execute();
			preparedStmt.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	// not used 
	private String selectFixMessage() {
		prepare(conf);
		// lastQueryTime = Instant.now().toEpochMilli();
		// Map result = new HashMap();
		// select entries from mysql
		//String query = "SELECT id, event, msg FROM  input_messages ";
		//query += " WHERE status = 'ARRIVED' limit 20";
		
		String query = "SELECT a.id, a.event, a.msg FROM  input_messages a, output_messages  u  where a.status= 'FIX' and u.id = a.id;";
		//String query = "SELECT a.id, event, msg FROM  input_messages a,urls u "
		//		+ " where a.status= 'ARRIVED' and u.event_id = a.id and "
		//		+ " u.status='FETCHED' limit 20;";
		String msg = "";
		String _id = "0";
		String event = "";
		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		Statement st2 = null;
		ResultSet rs = null;
		ResultSet rs2 = null;
		try {
			st = this.connection.createStatement();
			st2 = this.connection.createStatement();
			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset

			while (rs.next()) {
				Map result = new HashMap();
				_id = rs.getString("id");
				System.out.println("_id" + _id);
				event = rs.getString("event");
				msg = rs.getString("msg");
				
				
				//String query2 = "SELECT url, status, metadata, nextfetchdate  FROM  urls ";
				//query2 += " WHERE event_id = \"" + _id + "\" ;";
				//rs2 = st2.executeQuery(query2);
				//boolean done = false;
				//String pdate = "";
                 // int fetched = 0;
                  
                 String alredymsg = selectoutputMessage(_id);
          		if (alredymsg!=null) { 
          			if (inboxbaseurl!=null) {
          				System.out.println("dynamic"+ inboxbaseurl);
          				trackerpostclient = new URLPostClient(inboxbaseurl);
          		     }
          			//int code = postclient.send_message(alredymsg);
					//System.out.println("archiver code" + code);
					int code = trackerpostclient.send_message(alredymsg);
					System.out.println("tracker code" + code);
                   	updateMessage(_id);          			
          			continue;
          		}
				/*while (rs2.next()) {
					String status = rs2.getString("status");
					System.out.println("status" + status);
					String metadata = rs2.getString("metadata");
					String url = rs2.getString("url");
					pdate = rs2.getString("nextfetchdate") + 'Z';
					pdate = pdate.replaceFirst(" ", "T");
					System.out.println("published" + pdate);
					System.out.println("url" + url);
					System.out.println("metadata" + metadata);
					if (status.equals("DISCOVERED")) {
						done = false;
						break;
					} else {
						done = true;
						if (status.equals("FETCHED")) {
							fetched = fetched + 1;
						}
						result.put(url, metadata);
					}
				} // while
			        Integer items =	get_item_count(msg); 
			        System.out.println("items" +items);
				if (items.intValue()!=fetched) {
					done = false;
				}
				if (done) { // tmp
					Date gen = new Date();
					TimeZone tz = TimeZone.getTimeZone("GMT");
					FastDateFormat timeTravelJsFormatter = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", tz,
							Locale.US);
					String genday = timeTravelJsFormatter.format(gen);
					String out = composeMessage(_id, msg, result, genday);
					int code = postclient.send_message(out);
					System.out.println("code" + code);
					code = trackerpostclient.send_message(out);
					System.out.println("code" + code);
                    // if (code<200 && code > 202) {
                    	 //code = trackerpostclient.send_message(out);
                    // }
					updateMessage(_id);
				}
				*/
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
			try {
				if (rs2 != null)
					rs2.close();
			} catch (SQLException e) {
				// LOG.error("Exception closing resultset", e);
			}
			try {
				if (st2 != null)
					st2.close();
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
		return msg;
	}


	private String selectMessage() {
		prepare(conf);
		// lastQueryTime = Instant.now().toEpochMilli();
		// Map result = new HashMap();
		// select entries from mysql
		//String query = "SELECT id, event, msg FROM  input_messages ";
		//query += " WHERE status = 'ARRIVED' limit 20";
		String query = "SELECT a.id, event, msg FROM  input_messages a,urls u "
				+ " where a.status= 'ARRIVED' and u.event_id = a.id and "
			//	+ " u.status='FETCHED' limit 20;";
				+ " u.status like '%FETCH%' limit 20;";
		String msg = "";
		String _id = "0";
		String event = "";
		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		Statement st2 = null;
		ResultSet rs = null;
		ResultSet rs2 = null;
		try {
			st = this.connection.createStatement();
			st2 = this.connection.createStatement();
			// execute the query, and get a java resultset
			rs = st.executeQuery(query);

			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset

			while (rs.next()) {
				Map result = new HashMap();
				_id = rs.getString("id");
				System.out.println("_id" + _id);
				event = rs.getString("event");
				msg = rs.getString("msg");
				
				
				String query2 = "SELECT url, status, metadata, nextfetchdate  FROM  urls ";
				query2 += " WHERE event_id = \"" + _id + "\" ;";
				rs2 = st2.executeQuery(query2);
				boolean done = false;
				String pdate = "";
                  int fetched = 0;
                  
                 String alredymsg = selectoutputMessage(_id);
          		if (alredymsg!=null) {  
          			if (inboxbaseurl!=null) {
          				System.out.println("dynamic1"+ inboxbaseurl);
          				trackerpostclient = new URLPostClient(inboxbaseurl);
          		     }
          			
          			//int code = postclient.send_message(alredymsg);
					//System.out.println("archiver code" + code);
					int code = trackerpostclient.send_message(alredymsg);
					System.out.println("tracker code" + code);
                   	updateMessage(_id);          			
          			continue;
          		}
				while (rs2.next()) {
					String status = rs2.getString("status");
					System.out.println("status" + status);
					String metadata = rs2.getString("metadata");
					String url = rs2.getString("url");
					pdate = rs2.getString("nextfetchdate") + 'Z';
					pdate = pdate.replaceFirst(" ", "T");
					System.out.println("published" + pdate);
					System.out.println("url" + url);
					System.out.println("metadata" + metadata);
					if (status.equals("DISCOVERED")) {
						done = false;
						break;
					} else {
						done = true;
						//if (status.equals("FETCHED")) {
						 if (status.startsWith("FETCH")) {
						fetched = fetched + 1;
						}
						result.put(url, metadata);
					}
				} // while
			        Integer items =	get_item_count(msg); 
			        System.out.println("items" +items);
				//if (items.intValue() > fetched) {
				//	done = false;
				//}
				if (done) { // tmp
					Date gen = new Date();
					TimeZone tz = TimeZone.getTimeZone("GMT");
					FastDateFormat timeTravelJsFormatter = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", tz,
							Locale.US);
					String genday = timeTravelJsFormatter.format(gen);
					String out = composeMessage(_id, msg, result, genday);
					if (inboxbaseurl!=null) {
						System.out.println("dynamic"+ inboxbaseurl);
          				trackerpostclient = new URLPostClient(inboxbaseurl);
          		     }
					//int code = postclient.send_message(out);
					//System.out.println("code" + code);
					int code = trackerpostclient.send_message(out);
					System.out.println("code" + code);
                    // if (code<200 && code > 202) {
                    	 //code = trackerpostclient.send_message(out);
                    // }
					updateMessage(_id);
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
			try {
				if (rs2 != null)
					rs2.close();
			} catch (SQLException e) {
				// LOG.error("Exception closing resultset", e);
			}
			try {
				if (st2 != null)
					st2.close();
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
		return msg;
	}

	List<Map> format_result(Map<String, String> map) {
		List<Map> result = new ArrayList();
		for (Map.Entry<String, String> entry : map.entrySet()) {
			String[] a = null;
			String metadata = entry.getValue();
			System.out.println("metadata" + metadata);

			String[] fields = metadata.split("\t", -1);
			for (int i = 0; i < fields.length; ++i) {
				System.out.println("fields:" + fields[i]);
				if (fields[i].startsWith("warcs")) {
					String warcs = fields[i];
					warcs = warcs.replaceAll("warcs=", "");
					a = warcs.split(",");

				}
			}
			if (a != null) {
				for (int i = 0; i < a.length; i++) {
					Map<String, Object> tmp = new HashMap();
					tmp.put("href", warcbaseurl + a[i]);
					tmp.put("type", new String[] { "Link", "schema:MediaObject" });
					result.add(tmp);
				}
			}
		}

		return result;
	}
	List<Map> format_result_withzip (Map<String, String> map) {
		List<Map> result = new ArrayList();
		for (Map.Entry<String, String> entry : map.entrySet()) {
			String[] a = null;
			String metadata = entry.getValue();
			System.out.println("metadata" + metadata);

			String[] fields = metadata.split("\t", -1);
			for (int i = 0; i < fields.length; ++i) {
				System.out.println("fields:" + fields[i]);
				if (fields[i].startsWith("warcs")) {
					String warcs = fields[i];
					warcs = warcs.replaceAll("warcs=", "");
					a = warcs.split(",");

				}
			}
			if (a != null) {
				for (int i = 0; i < a.length; i++) {
					Map<String, Object> tmp = new HashMap();
					gzipbyJ(warcfilesdir+a[i]);
					tmp.put("href", warcbaseurl + a[i]+".gz");
					tmp.put("type", new String[] { "Link", "schema:MediaObject" });
					result.add(tmp);
				}
			}
		}

		return result;
	}

	
	public String curr() {
		Date gen = new Date();
		TimeZone tz = TimeZone.getTimeZone("GMT");
		FastDateFormat timeTravelJsFormatter = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", tz, Locale.US);
		String genday = timeTravelJsFormatter.format(gen);
		return genday;
	}

	public Integer get_item_count(String msg) {
		Integer count = 0;
		try {
			Object TrackerMsg = JsonUtils.fromString(msg);
			Map<String, Object> troot = (Map) TrackerMsg;
			Map<String, Object> tevent = (Map<String, Object>) troot.get("event");
			String msgid = (String) tevent.get("@id");
			Map<String, Object> obj = (Map<String, Object>) tevent.get("object");
			
			count = (Integer) obj.get("totalItems");
			return count;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return count;
	}
	
	private String getInputAsString(InputStream is)
	{
	   try(java.util.Scanner s = new java.util.Scanner(is)) 
	   { 
	       return s.useDelimiter("\\A").hasNext() ? s.next() : ""; 
	   }
	}
	

	
	List merge(Map<String, String> map) {
		List<Map> result = new ArrayList();
		//String cmd = " cat ";
		List <String> cmd = new ArrayList();
		cmd.add("cat");
		String bigwarcname = null;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			String[] fileNames = null;
			String metadata = entry.getValue();
			System.out.println("metadata" + metadata);

			String[] fields = metadata.split("\t", -1);
			for (int i = 0; i < fields.length; ++i) {
				System.out.println("fields:" + fields[i]);
				if (fields[i].startsWith("warcs")) {
					String warcs = fields[i];
					warcs = warcs.replaceAll("warcs=", "");
					fileNames = warcs.split(",");
                     if  (bigwarcname==null) {
                    	 bigwarcname= "M" +fileNames[0];
                    	 if ( bigwarcname.contains(".open")) {
                    		 bigwarcname.replaceAll(".open", ""); 
                    	 }
                     }
				}
			}
			if (fileNames != null) {
				for (int i = 0; i < fileNames.length; i++) {
					
					cmd.add(warcfilesdir+fileNames [i].trim());
					//Map<String, Object> tmp = new HashMap();
					//tmp.put("href", warcbaseurl + a[i]);
					//tmp.put("type", new String[] { "Link", "schema:MediaObject" });
					//result.add(tmp);
				}
			}
		}

		//cmd.add( ">>");
		//cmd.add( warcfilesdir+bigwarcname );
		
		ProcessBuilder probuilder = new ProcessBuilder(cmd);
		File combinedFile = new File( warcfilesdir+bigwarcname);
		probuilder.redirectOutput(combinedFile);
		//cmd.forEach(x -> System.out.println(":"+x+":"));
		Process p;
		try {
			p = probuilder.start();
			//String stdErr = getInputAsString(p.getErrorStream());
			//if (stdErr!=null) {
			//System.out.println(stdErr);
			//}
			//File OutputFile = new File(warcdir + "/output" + pport + ".txt");
			//probuilder.redirectErrorStream(true);
			// probuilder.directory(new File(warcdir));
			//probuilder.redirectOutput();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//gzipbyJ(warcfilesdir+bigwarcname);
	
		Map<String, Object> tmp = new HashMap();
		//changed sept142022
		//tmp.put("href", warcbaseurl + bigwarcname+".gz");
		tmp.put("href", warcbaseurl + bigwarcname);
		tmp.put("type", new String[] { "Link", "schema:MediaObject" });
		result.add(tmp);
		
		return result;
		
		
	}
	public  void gzipbyJ(String name) {
	Path input = Paths.get(name);
	String zippedFilename =name+".gz";
	Path zipped = Paths.get(zippedFilename);

	try (OutputStream out = new GZIPOutputStream(
	    new BufferedOutputStream(
	        Files.newOutputStream(zipped)))) {

	    Files.copy(input, out);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}
	public  void gzip(String name) {
		
		List <String> cmd = new ArrayList();
		cmd.add("gzip");
		cmd.add("-9");
		cmd.add(name);
		ProcessBuilder probuilder = new ProcessBuilder(cmd);	
		//cmd.forEach(x -> System.out.println(":"+x+":"));
		Process p;
		try {
			p = probuilder.start();
            p.getErrorStream();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	public String composeMessage(String event_id, String trmessage, Map map, String pdate) {
		//String msg = selectoutputMessage(event_id);
		//if (msg!=null) {
		//	return msg;
		//}
		Object jsonObject;
		try {
			String genday = curr();

			// incoming message
			Object TrackerMsg = JsonUtils.fromString(trmessage);
			Map<String, Object> troot = (Map) TrackerMsg;
			Map<String, Object> tevent = (Map<String, Object>) troot.get("event");
			String msgid = (String) tevent.get("@id");
			inboxbaseurl = (String) tevent.get("to");
			String eventbaseurl = (String) tevent.get("eventBaseUrl");
			//commented out
			
			//if (eventbaseurl != null) {
			//	baseurl =	eventbaseurl;
			//}
			//added
			eventbaseurl= captureownurl+"event/";
			
			Map<String, Object> obj = (Map<String, Object>) tevent.get("object");

			// template
			jsonObject = JsonUtils.fromString(template);
			Map<String, Object> root = (Map) jsonObject;

			Map<String, Object> event = (Map<String, Object>) root.get("event");
			//String _id = baseurl + "cap" + event_id;
			String _id = eventbaseurl + "cap" + event_id;
			System.out.println("_id"+_id);
			event.put("@id", _id);
			event.put("object", obj);
			event.put("prov:generatedAtTime", genday);
			event.put("published", genday);
			event.put("prov:wasGeneratedBy", _id + "#activity");
			          
			
			List prov = (List) event.get("prov:wasInformedBy");
			Iterator itprov = prov.iterator();
			while (itprov.hasNext()) {
				Map m = (Map) itprov.next();
				m.put("id", msgid);
			}
			Map actm = (Map) tevent.get("actor");
			String	orcid= (String) actm.get("id");
			//	m.put("id", msgid);
			     
			event.put("prov:Agent", orcid);    
			Map<String, Object> activity = (Map<String, Object>) root.get("activity");
			activity.put("@id", _id + "#activity");

			Map<String, Object> result = (Map<String, Object>) event.get("result");
			result.put("totalItems", map.size());
			// format_result(map);
			List warcs = format_result(map);
			if (warcs.size()>1) {
			    warcs = merge(map);
			}
			else {
				//warcs = format_result_withzip (map);
				
			}
			result.put("items", warcs);
			insertMessage(_id, event_id, JsonUtils.toPrettyString(root), genday);
			 System.out.println(JsonUtils.toPrettyString(root));
			return JsonUtils.toPrettyString(root);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private String selectoutputMessage(String id) {

		// lastQueryTime = Instant.now().toEpochMilli();

		// select entries from mysql
		String query = "SELECT msg FROM  output_messages ";
		query += " WHERE id = \"" + id + "\";";

		String msg = null;

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
				msg = rs.getString("msg");

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

	
	private static String loadProfile(String profileFile) {

		StringBuffer sb = new StringBuffer();
		try {
			InputStream regexStream = MsgSender.class.getClassLoader().getResourceAsStream(profileFile);
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
		System.out.println(template);
		System.out.println(sb.toString());
		return sb.toString();
	}

	private static String loadProfilefromURL(String profileFile) {

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

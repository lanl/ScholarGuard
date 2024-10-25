package gov.lanl.crawler.resource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.digitalpebble.stormcrawler.sql.Constants;

import gov.lanl.crawler.input.InputServer;

public class QUpdater extends Thread {
	private boolean running = true;

	private static String tableName;
	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	private Connection connection;
	String warcfilesdir = (String) conf.get("warcfilesdir");
	String warcbaseurl = (String) conf.get("warcbaseurl");
	@Override
	public void run() {
		// Keeps running indefinitely, until the termination flag is set to false
		while (running) {
			selectDone();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	// Terminates thread execution
	public void halt() {
		this.running = false;
	}

	void selectDone(){
		prepare(conf);
		String query = "SELECT a.id, a.event_id FROM  input_jobs a,urls u "
				+ " where a.status= 'DISCOVERED' and u.event_id = a.event_id and "
				+ " u.status like '%FETCH%' limit 20;";
		Statement st = null;
		Statement st2 = null;
		ResultSet rs = null;
		ResultSet rs2 = null;
		String _id = "0";
		String event = "";
		String status="";
		String metadata="";
		int fetched = 0;
		try {
			st = this.connection.createStatement();
			st2 = this.connection.createStatement();
			rs = st.executeQuery(query);

			boolean done = false;
			while (rs.next()) {
				Map result = new HashMap();
				_id = rs.getString("id");
				System.out.println("_id" + _id);
				event = rs.getString("event_id");
		        String query2 = "SELECT url, status, metadata, nextfetchdate  FROM  urls ";
		        query2 += " WHERE event_id = \"" + event + "\" ;";
		        rs2 = st2.executeQuery(query2);
		
		              while (rs2.next()) {
			             status = rs2.getString("status");
			             System.out.println("status" + status);
			              metadata = rs2.getString("metadata");
			             String url = rs2.getString("url");
			             System.out.println("url" + url);
			             System.out.println("metadata" + metadata);
			             if (status.equals("DISCOVERED")) {
				             done = false;
				             break;
			                } else {
				             done = true;
				             if (status.startsWith("FETCH")) {
									fetched = fetched + 1;
									}
									result.put(url, metadata);
				
			              }
		                }
		              if (done) {
		            	  
		            	  List warcs = format_result(result);
		            	  if (warcs.size()>1) {
		      			    warcs = merge(result);
		      			   }
		            	 Map m= (Map) warcs.get(0);
		            			String warc= (String) m.get("href");
		            	  updateJob(event, status, metadata, warc);
		              }
			}
			}
			catch (SQLException e) {
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
				}}
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
	private void updateJob(String event_id,String status,String metadata,String warc) {
		String sql = "update input_jobs set status='"+status+ "',metadata='"+metadata+"',warc_file='"+
	        warc+ "' ,capdate = NOW() "+" where event_id =\"" + event_id + "\";";
		Statement st = null;
		try {
			st = this.connection.createStatement();
			st.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//gzipbyJ(warcfilesdir+bigwarcname);
	
		Map<String, Object> tmp = new HashMap();
		//tmp.put("href", warcbaseurl + bigwarcname+".gz");
		tmp.put("href", warcbaseurl + bigwarcname);
		tmp.put("type", new String[] { "Link", "schema:MediaObject" });
		result.add(tmp);
		
		return result;
	}
	
	public void prepare(Map stormConf) {// TopologyContext context,
		tableName = (String) stormConf.get(Constants.MYSQL_TABLE_PARAM_NAME);
		// System.out.println("table:" + tableName);
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

package gov.lanl.crawler.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.ws.rs.GET;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.Path;

import com.digitalpebble.stormcrawler.sql.Constants;

import gov.lanl.crawler.input.InputServer;

@Path("/results")

public class ResultsResource {
	private Connection connection;
	private static String tableName;
	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	String new_row_tmpl = "<tr scope=\"row\" class=\"d-flex {}\">";
	String row_tmpl = "<td class=\"inline-block text-truncate {}\">{1}</td>";
	String a_tmpl = "<a href=\"{}\">{}</a>";
	static String reshtml_tmpl;
	static String navhtml;
	private static String warcbaseurl;
	private static String warcfilesdir;
	private static String wabacbase = "https://timetravel.mementoweb.org/wabac/index.html?coll_warc=warcs/";
	static {

		// new driver can do without it
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		reshtml_tmpl = read_template("./templates/results.html");
		navhtml = read_template("./templates/nav.html");
		warcbaseurl = (String) conf.get("warcbaseurl");
		warcfilesdir = (String) conf.get("warcfilesdir");
		wabacbase=(String) conf.get("wabacurl");
	}

	@GET
	public Response GetResults() {
     System.out.println("in get");
     String html_r =reshtml_tmpl.replace("{{ NAVBAR }}", navhtml);
     //System.out.println(html_r);
		String rows = compose_result("fetched")+compose_result("discovered");
		
		
		//System.out.println("rows:"+rows);
		 html_r = html_r.replace("{{ RESULT_ROWS }}", rows);
		
		try {
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ResponseBuilder r = Response.ok(html_r);
		
		r.header("Content-Type", "text/html");
		return r.build();
	}

	public String compose_result(String status) {
		StringBuilder rows = new StringBuilder();
		Map<String, String> hmap = select_fetched(status);
		for (String url : hmap.keySet()) {
			String meta = hmap.get(url);
			String[] fields = meta.split("\t", -1);
			String warcs = "";
			String trace = "";
			String ldate = "";
            String _status="";
            
			for (int i = 0; i < fields.length; ++i) {
				//System.out.println("fields:" + fields[i]);
				/*if (fields[i].startsWith("warcs")) {
					warcs = fields[i];
					warcs = warcs.replaceAll("warcs=", "");
				}*/
				
				if (fields[i].startsWith("warc_file")) {
					warcs = fields[i];
					warcs = warcs.replaceAll("warc_file=", "");
				}
				
				if (fields[i].startsWith("trace_url")) {	
					
					trace = fields[i];
					trace = trace.replaceAll("trace_url=", "");
				}
                if (fields[i].startsWith("status")) {	
					
					_status = fields[i];
					_status = trace.replaceAll("status=", "");
				}
				
				/*
				if (fields[i].startsWith("trace")) {
					
					trace = fields[i];
					trace = trace.replaceAll("trace=", "");
				}
				*/
				if (fields[i].startsWith("lastProcessedDate")) {
					ldate = fields[i];
					ldate = ldate.replaceAll("lastProcessedDate=", "");
				}
				if (fields[i].startsWith("reqdate")) {
					ldate = fields[i];
					ldate = ldate.replaceAll("reqdate=", "");
				}
			}
			if (status.equals("discovered")) {
				rows.append(new_row_tmpl.replace("{}","table-warning")); 
			}
			else {
			rows.append(new_row_tmpl.replace("{}","table-success")); }
			
			String a = row_tmpl.replace("{}","col-2");
			String b = a_tmpl.replaceAll(Pattern.quote("{}"), ldate);
			a = a.replace("{1}", b);
			//System.out.println(a);
			rows.append(a);
			a = row_tmpl.replace("{}","col-4");
			b = a_tmpl.replaceAll(Pattern.quote("{}"), url);
			a = a.replace("{1}", b);
			//System.out.println(a);
			rows.append(a);
			a = row_tmpl.replace("{}","col-4");
			b = a_tmpl.replaceAll(Pattern.quote("{}"), trace);
			a = a.replace("{1}", b);
			//System.out.println(a);
			rows.append(a);
			a = row_tmpl.replace("{}","col-2");
			b = a_tmpl.replaceAll(Pattern.quote("{}"), warcbaseurl + warcs);
			a = a.replace("{1}", b);
			//System.out.println(a);
			rows.append(a);
			a = row_tmpl.replace("{}","col-2");
			b = a_tmpl.replaceAll(Pattern.quote("{}"), wabacbase + warcs);
			a = a.replace("{1}", b);
			rows.append(a);
			
			rows.append("</tr>");
		}
		return rows.toString();
	}

	public Map select_fetched(String stat) {
		prepare(conf);
		ResultSet rs2 = null;
		Statement st2 = null;
		//String query2 = "SELECT url, status, metadata, nextfetchdate  FROM  urls ";
		String query2 = "SELECT event_id, url, trace_url,status, meta, reqdate, capdate,"
				+ "  warc_file FROM  input_jobs ";
		if (stat.equals("discovered")) {
			query2 += " WHERE status='DISCOVERED and reqdate >= DATE_ADD(CURDATE(), INTERVAL -5 DAY)  ;";
		}else {
		
		query2 += " WHERE status not in ('DISCOVERED')  and reqdate >= DATE_ADD(CURDATE(), INTERVAL -5 DAY) ;";
		}
		//System.out.println(query2);
		Map result = new HashMap();
		try {
			st2 = this.connection.createStatement();
			rs2 = st2.executeQuery(query2);
			String pdate = "";

			while (rs2.next()) {
				String status = rs2.getString("status");
				
				//System.out.println("status" + status);
				String metadata = rs2.getString("meta");
				String turl = rs2.getString("trace_url");
				String reqdate = rs2.getString("reqdate");
				String capdate = rs2.getString("capdate");
				String jid = rs2.getString("event_id");
				String warc_file = rs2.getString("warc_file");
				metadata = metadata+ " status="+status;
				metadata = metadata+ " trace_url="+turl;
				metadata = metadata+ " reqdate="+reqdate;
				metadata = metadata+ " capdate="+capdate;
				metadata = metadata+ " jid="+jid;
				metadata = metadata+ " warc_file="+warc_file;
				
				String url = rs2.getString("url");
				pdate = rs2.getString("capdate") + 'Z';
				pdate = pdate.replaceFirst(" ", "T");
				//System.out.println("published" + pdate);
				//System.out.println("url" + url);
				//System.out.println("metadata" + metadata);
				result.put(url, metadata);
				
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	/*
	 * public static String read_template(String filepath) { Path path =
	 * Paths.get(filepath);
	 * 
	 * //Path filePath = Paths.of("c:/temp/demo.txt"); StringBuilder contentBuilder
	 * = new StringBuilder(); //Stream<String> lines = Files.lines(path,
	 * StandardCharsets.UTF_8); try (Stream<String> stream = Files.lines(path,
	 * StandardCharsets.UTF_8)) { //Read the content with Stream stream.forEach(s ->
	 * contentBuilder.append(s).append("\n")); } catch (IOException e) {
	 * e.printStackTrace(); }
	 * 
	 * String fileContent = contentBuilder.toString(); return fileContent; }
	 */
	public static String read_template(String profileFile) {

		StringBuffer sb = new StringBuffer();
		try {
			InputStream regexStream = ResultsResource.class.getClassLoader().getResourceAsStream(profileFile);
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

		//System.out.println(sb.toString());
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
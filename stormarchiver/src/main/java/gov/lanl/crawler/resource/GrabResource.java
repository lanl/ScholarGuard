package gov.lanl.crawler.resource;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.*;

import gov.lanl.crawler.input.InputServer;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.http.HttpHost;

import javax.ws.rs.core.Response.ResponseBuilder;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Search;
import io.searchbox.core.search.sort.Sort;


@Path("/capture/grab")


//service http://pod-es:9200/pod/_search?pretty
	//https://orcid.org/0000-0002-1470-7723

public class GrabResource {
	
	
	private static Map<String, String> conf = InputServer.INSTANCE.prop;
	static String elasticurl;
	static {
		System.out.println("api loaded");
		elasticurl = (String) conf.get("elastic_url");
	}
	
	HashMap masterlist = new HashMap();
	public String build_query(String orcid) {
	String q =
		"{\n" +
				"\"size\" : \"20000\","+
				"\"query\": {" +
	                "\"match\":  { \"user_id\":\"" + orcid + "\"}}" +      
				"," +
				"\"_source\": [\"msg_published_at\",\"tracker_name\", \"_id\"]"+
				 
				"}";
	System.out.println(q);
	return q;
	
	}
	public String build_idquery() {
		List  idlist = makeLiteralList();
		String idl= (String) idlist.get(0);
		String q =
		"{\"size\" : \"20000\","+
			"\"query\" : {"+
			            "\"bool\": {"+
			                "\"must\": ["+
			                         "{\"term\" : {\"activity.event.type\" : \"tracker:Archiver\"}}, \n"+                        
			                         "{\"terms\" : {\"tracker_parent_id\" : "+ idl +
			                        		 "}}" +
			               " ]"+
			            "}"+
			        "},"+
			"\"sort\" : {\"msg_published_at\":{\"order\": \"desc\"}},"+
			"\"_source\" : [\"_id\",\"msg_received_at\",\"activity.event.object\",\"activity.event.result\"]"+

			"}";
		//System.out.println(q);
		return q;
	}
	
	
	
	@GET	
	public Response  getALL( @Context HttpHeaders hh, @Context UriInfo ui, @QueryParam("orcid") String orcid, @QueryParam("from") String from) {
		  URI baseurl = ui.getBaseUri();
		  System.out.println("orcid:"+orcid);
		  Sort sort = new Sort("msg_published_at",Sort.Sorting.DESC);
		  
		  String search = build_query("https://orcid.org/"+orcid);
		  
		  HttpHost proxy = new HttpHost("proxyout.lanl.gov", 8080);
		  HttpClientConfig builder = new HttpClientConfig.Builder(elasticurl)
                  .connTimeout(5000)
                  .readTimeout(3000)
                  .maxTotalConnection(10)
                  .defaultMaxTotalConnectionPerRoute(10)
	                .multiThreaded(true)
	                //.proxy(proxy)
	                .build();
		  builder.getProxyAuthenticationStrategy();
		  JestClientFactory factory = new JestClientFactory();
	        factory.setHttpClientConfig(builder);
	        
	     
	     
	        JestClient client = factory.getObject();	
	        try {
				//JestResult result = client.execute(new IndicesExists.Builder("pod").sort("id", SortOrder.ASC).build());
				
	        	JestResult result = client.execute(new Search.Builder(search).addIndex("pod").addSort(sort).build());
	        	String s = result.getErrorMessage();
	        	if (s!=null) {
	        	System.out.println("error message:"+s);
	        	}
	        	JsonObject object = (JsonObject) result.getJsonObject().get("hits");
	        	JsonArray hits = (JsonArray) object.get("hits");
	        	System.out.println("hits:"+hits.size());
	        	Iterator it=hits.iterator();
	        	while (it.hasNext()) {
	        		JsonObject  e =(JsonObject) it.next();
	        		String id = e.get("_id").getAsString();
	        		          JsonElement sourceElement = e.get("_source");
	        		            HashMap meta = new HashMap();  
	        		            if (sourceElement.isJsonObject()) {
	        		                JsonObject source = sourceElement.getAsJsonObject();
	        		                String pubdate =source.get("msg_published_at").getAsString();
	        		                String tracker = source.get("tracker_name").getAsString();
	        		                meta.put("pubdate",pubdate);
	        		                meta.put("tracker", tracker);
	        		                masterlist.put(id,meta);
	        		            }
	        	}
	        	
	        	String q = build_idquery();
	        	JestResult fresult = client.execute(new Search.Builder(q).addIndex("pod").build());
	        	JsonObject fobject = (JsonObject) fresult.getJsonObject().get("hits");
	        	String total = fobject.get("total").getAsString();
	        	System.out.println("total"+total);
	        	
	        	JsonArray fhits = (JsonArray) fobject.get("hits");
	        	
	        	 //LocalDate date = LocalDate.now();
                // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd", Locale.ENGLISH);
                 //String dateString = formatter.format(date);
                 Instant instant = Instant.now();
                 String dateString = DateTimeFormatter.ISO_INSTANT.format(instant);

	        	String top = "{ \"total\":"+total +", \"orcid\": \"https://orcid.org/"+orcid+"\",\"req_date\":\""+dateString+"\"}";
	        	System.out.println(top);
	        	JsonObject convertedObject = null;
	        	try {
	        	 convertedObject = new Gson().fromJson(top, JsonObject.class);
	        	}
	        	catch ( Exception e){
                	 e.printStackTrace();
                 }
	        	JsonArray fhits_tilda = new JsonArray();
	        	System.out.println(fhits.size());
	        	 fhits.forEach(item -> {
	                 if (item.isJsonObject()) {
	                     JsonObject node = item.getAsJsonObject();
	                     JsonObject node_t = new JsonObject();
	                     node_t.add("archival_event", node.getAsJsonObject().get("_source").getAsJsonObject().get("activity").getAsJsonObject().get("event").getAsJsonObject());
	                     //node_t = node.getAsJsonObject().get("_source").getAsJsonObject().get("activity").getAsJsonObject().get("event").getAsJsonObject();
	                    
	                  
	                     JsonElement _id = node.get("_id");
	                     //System.out.println( "_id"+_id);
	                     node_t.add("id", _id);
	                    // try {
	                    // node_t.getAsJsonObject("object").remove("type");
	                     //node_t.getAsJsonObject("result").remove("type");
	                     //}
	                     //catch ( Exception e){
	                    	// e.printStackTrace();
	                     //}
	                    // node_t.get("result").getAsJsonObject().remove("type");
	                     fhits_tilda.add(node_t);
	                                     // JsonElement v = node.get("_source");
	                    /* if (v.isJsonObject()) {
	                    	 JsonElement     a = v.getAsJsonObject().get("activity");
	                    	 if (a.isJsonObject()) {
	                    		 JsonElement  o = a.getAsJsonObject().get("object");
	                    		 if (o.isJsonObject()) {
	                    			 o.getAsJsonObject().remove("type");
	                    			 JsonElement u = o.getAsJsonObject().get("items");
	                    			 
	                    		 }
	                    		 a.getAsJsonObject().add("archive", o);
	                    		 a.getAsJsonObject().remove("object");
	                    		 
	                    		 JsonElement  oo = a.getAsJsonObject().get("event");
	                    	  	            if (oo.isJsonObject()) {			 
	                    	  		 JsonElement res =oo.getAsJsonObject().get("result");
	                    	  		 res.getAsJsonObject().remove("type");
	                    			 
	                    		            }
	                    	  	 // node.add("archival_event",oo);
	                    	  	 
	                    	  	 // node_t.add("id", _id);
	                    	  	 // node.remove("_source");
	                    	  	 // fhits_tilda.add(node_t);
	                    		  
	                    	 }
	                         
	                     } */
	                 }
	             });
	         //convertedObject.add(fhits.getAsJsonObject());
	        	 if (convertedObject!=null) {
	             convertedObject.add("artifacts", fhits_tilda);
	        	 }
	        	// JsonObject a= res.get("artifacts").getAsJsonObject();
	        	
	        	//Iterator hiter = fhits.iterator();
	        	
	        	//while (hiter.hasNext()) {
	        	//	JsonObject  e =(JsonObject) hiter.next();
	        	//	String id = e.get("_id").getAsString();
	        		
	        	//}
	        	//Gson gson = new Gson();
	        	Gson gson = new GsonBuilder().setPrettyPrinting().create();
	        	//String finalresult = gson.toJson(fhits_tilda);
	        	String finalresult = gson.toJson(convertedObject);
	        	//System.out.println("fhits"+fhits.size());
	        	//String f = fhits.getAsJsonObject().getAsString();
	        	//System.out.println("flength"+f.length());
	        	//StringBuilder sb =new StringBuilder();
	        	//Iterator it1=fhits.iterator();
	        	//while (it1.hasNext()) {
	        	//	JsonObject  e =(JsonObject) it1.next();
	        	//	String id = e.get("_id").getAsString();
	        	//	          String sElement = e.get("_source").getAsString();
	        	//	          sb.append(sElement);
	        	//}
	        	
	        	//System.out.println(sb.toString());
	        	ResponseBuilder r = Response.ok(finalresult);
	    		r.header("Content-Type", "text/plain");
	    		return r.build();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		  return null;
	}
	
	public List makeLiteralList(){
		Iterator it = masterlist.keySet().iterator();
		List list = new ArrayList();
		StringBuilder sb = new StringBuilder();
		sb.append(" [");
		int count = 0;
		while (it.hasNext()) {
			String id=(String) it.next();
			if (count!=0) {
			sb.append(",\""+id+"\"");
			}
			else {
			sb.append("\""+id+"\"");	
			}
			count = count+1;
			if (count==50000) {
				break;
			}
		}
		sb.append("]");
		list.add(sb.toString());
		return list;
	}
	
}

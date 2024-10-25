package gov.lanl.crawler;

import java.io.FileNotFoundException;

import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.storm.spout.Scheme;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

import com.digitalpebble.stormcrawler.ConfigurableTopology;
import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.opencsv.CSVReader;

import gov.lanl.crawler.core.InputArgs;
import gov.lanl.crawler.core.StatusUpdaterBolt;
import gov.lanl.crawler.core.StringTabScheme;

import com.digitalpebble.stormcrawler.spout.FileSpout;


public class SeedInjector extends ConfigurableTopology {
	  public static void main(String[] args) throws Exception {
		  ConfigurableTopology.start(new SeedInjector(), args);
	  }
	  public void do_withoutstorm(String[] args) throws IOException{
		  
		  InputArgs inargs = new InputArgs( args );
		  
		   Map stormConf = Utils.findAndReadConfigFile("crawler-conf.yaml", true);
		   String sdir = inargs.get("seed_dir", "./seeds");
		   String filename = inargs.get("file_name", "seeds.txt"); 
		   CSVReader wr = null;
		   StatusUpdaterBolt su = null;
		    su = new StatusUpdaterBolt();
			su.prepare_ext(stormConf);
			
		    wr = new CSVReader(new FileReader( sdir+ filename));
		
			String[] line;
			
			while ((line = wr.readNext()) != null) {
							
				String url = line[0].trim().toLowerCase();
		 		//System.out.println("url1"+url1);
				String trace="";
				if (line.length>1) {
				 trace = line[1].trim().toLowerCase();
			    }
				
				Map<String, String[]> map = new HashMap();
				map.put("url.path", new String[] { url });
				if (!trace.equals("")) {
					map.put("trace", new String[] {trace});	
				}
				Metadata metadata = new Metadata(map);
				Timestamp nextFetch = new Timestamp(new Date().getTime());
				su._store(url, "DISCOVERED", metadata, nextFetch);
			
				
			}
			
			wr.close();
		  
		  
	  
	  }
	@Override
	protected int run(String[] args) {
		
		 if (args.length == 0) {
	            System.err.println("SeedInjector seed_dir filer_file");
	            return -1;
	        }
		 
		    TopologyBuilder builder = new TopologyBuilder();
		    Scheme scheme = new StringTabScheme();
		    
		    FileSpout fs = new FileSpout(args[0], args[1], true);
		    fs.setScheme(scheme);
	       
	        builder.setSpout("spout", fs);
	        //Fields key = new Fields("url");
	        builder.setBolt("status", new StatusUpdaterBolt(), 1).localOrShuffleGrouping("spout", Constants.StatusStreamName).setNumTasks(1);
	        
		// TODO Auto-generated method stub
	      return submit("SeedInjector", conf, builder);
	}
	
	
	
}

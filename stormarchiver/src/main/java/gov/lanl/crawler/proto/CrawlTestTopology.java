package gov.lanl.crawler.proto;

import org.apache.storm.topology.TopologyBuilder;

import com.digitalpebble.stormcrawler.ConfigurableTopology;
import com.digitalpebble.stormcrawler.bolt.FetcherBolt;
import com.digitalpebble.stormcrawler.bolt.JSoupParserBolt;
import com.digitalpebble.stormcrawler.bolt.SiteMapParserBolt;
import com.digitalpebble.stormcrawler.bolt.URLPartitionerBolt;
import com.digitalpebble.stormcrawler.indexing.DummyIndexer;
import com.digitalpebble.stormcrawler.util.ConfUtils;

import gov.lanl.crawler.core.SQLSpoutInput;
import gov.lanl.crawler.core.StatusUpdaterBolt;

import org.apache.storm.tuple.Fields;
import com.digitalpebble.stormcrawler.Constants;

public class CrawlTestTopology extends ConfigurableTopology {
	 public static void main(String[] args) throws Exception {
	        ConfigurableTopology.start(new CrawlTestTopology(), args);
	    }
	  @Override
	    protected int run(String[] args) {
		  
		  // if we are going to dokerize it better to omit flux and use topology
	        TopologyBuilder builder = new TopologyBuilder();
	        String[] testURLs = new String[] { "https://www.slideshare.net/hvdsomp/paul-evan-peters-lecture" };
	        
	        int numWorkers = ConfUtils.getInt(getConf(), "topology.workers", 1);
	        int numShards = 1;
	        
	        builder.setSpout("spout", new SQLSpoutInput(), numShards);
	        builder.setBolt("partitioner", new URLPartitionerBolt(), numWorkers).shuffleGrouping("spout");
	       
	        builder.setBolt("fetch", new FetcherBolt(), numWorkers).fieldsGrouping("partitioner", new Fields("key"));
            
	        builder.setBolt("sitemap", new SiteMapParserBolt()).localOrShuffleGrouping("fetch");
            builder.setBolt("parse", new JSoupParserBolt()).localOrShuffleGrouping( "sitemap");
            builder.setBolt("index", new DummyIndexer(), numWorkers) .localOrShuffleGrouping("fetch");
            
            builder.setBolt("status", new StatusUpdaterBolt(), numWorkers)
            .localOrShuffleGrouping("fetch", Constants.StatusStreamName)
            .localOrShuffleGrouping("parse", Constants.StatusStreamName)
            .localOrShuffleGrouping("index", Constants.StatusStreamName)
            .localOrShuffleGrouping("sitemap", Constants.StatusStreamName)
            .setNumTasks(numShards);
            return submit("CrawlTestTopology", conf, builder);
	       
	  }

}

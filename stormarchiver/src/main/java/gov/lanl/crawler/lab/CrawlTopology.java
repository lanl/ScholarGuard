package gov.lanl.crawler.lab;

/**
 * Licensed to DigitalPebble Ltd under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * DigitalPebble licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.digitalpebble.stormcrawler.*;
import com.digitalpebble.stormcrawler.bolt.FetcherBolt;
import com.digitalpebble.stormcrawler.bolt.JSoupParserBolt;
import com.digitalpebble.stormcrawler.bolt.SiteMapParserBolt;
import com.digitalpebble.stormcrawler.bolt.URLPartitionerBolt;
import com.digitalpebble.stormcrawler.elasticsearch.bolt.IndexerBolt;
import com.digitalpebble.stormcrawler.elasticsearch.metrics.MetricsConsumer;
import com.digitalpebble.stormcrawler.elasticsearch.persistence.StatusUpdaterBolt;
import com.digitalpebble.stormcrawler.indexing.StdOutIndexer;
import com.digitalpebble.stormcrawler.persistence.StdOutStatusUpdater;
import com.digitalpebble.stormcrawler.spout.MemorySpout;
//import com.digitalpebble.stormcrawler.elasticsearch.persistence.ElasticSearchSpout;
//import com.digitalpebble.stormcrawler.elasticsearch.ElasticSearchConnection;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.metric.LoggingMetricsConsumer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import org.apache.storm.utils.Utils;

/**
 * Dummy topology to play with the spouts and bolts
 */
public class CrawlTopology extends ConfigurableTopology {

  /*  public static void main(String[] args) throws Exception {
        ConfigurableTopology.start(new CrawlTopology(), args);
    }
    */
    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();
      //  String[] testURLs = new String[] { "https://www.slideshare.net/hvdsomp/paul-evan-peters-lecture" };
     
        /*
        builder.setSpout("spout", new MemorySpout(args));
        builder.setBolt("partitioner", new URLPartitionerBolt())
        .shuffleGrouping("spout");

        builder.setBolt("fetch", new FetcherBolt()).fieldsGrouping(
        "partitioner", new Fields("key"));

        builder.setBolt("sitemap", new SiteMapParserBolt())
        .localOrShuffleGrouping("fetch");

        builder.setBolt("parse", new JSoupParserBolt()).localOrShuffleGrouping(
        "sitemap");

        builder.setBolt("index", new StdOutIndexer()).localOrShuffleGrouping(
        "parse");
        
        
        Fields furl = new Fields("url");
      //  builder.setBolt("status", new StdOutStatusUpdater())
	    //  .fieldsGrouping("fetch", Constants.StatusStreamName, furl)
	     // .fieldsGrouping("sitemap", Constants.StatusStreamName, furl)
	      //.fieldsGrouping("parse", Constants.StatusStreamName, furl)
	      //.fieldsGrouping("indexer", Constants.StatusStreamName, furl);
        
        //builder.setSpout("word", new TestWordSpout(), 10);
       // builder.setBolt("exclaim1", new ExclamationBolt(), 3).shuffleGrouping("word");
       // builder.setBolt("exclaim2", new ExclamationBolt(), 2).shuffleGrouping("exclaim1");

        Config conf = new Config();
        conf.setDebug(true);
        conf.registerMetricsConsumer(LoggingMetricsConsumer.class);
       // if (args != null && args.length > 0) {
         // conf.setNumWorkers(3);

          //StormSubmitter.submitTopology(args[0], conf, builder.createTopology());
        //}
        //else {

          LocalCluster cluster = new LocalCluster();
          cluster.submitTopology("test", conf, builder.createTopology());
          Utils.sleep(10000);
          cluster.killTopology("test");
          cluster.shutdown();
        //}
         * 
         */
      }
    
/*
    private final boolean topologyExists(final String topologyName) {

    	   // list all the topologies on the local cluster
    	   final List<TopologySummary> topologies = localCluster.getClusterInfo().get_topologies();

    	   // search for a topology with the topologyName
    	   if (null != topologies && !topologies.isEmpty()) {
    	       final List<TopologySummary> collect = topologies.stream()
    	               .filter(p -> p.get_name().equals(topologyName)).collect(Collectors.toList());
    	       if (null != collect && !collect.isEmpty()) {
    	           return true;
    	       }
    	   }
    	   return false;
    	}
  */
    
    @Override
    protected int run(String[] args) {
    	LocalCluster localCluster = new LocalCluster();

        TopologyBuilder builder = new TopologyBuilder();
       /* int numBuckets = 10;
        String[] testURLs = new String[] { "https://www.slideshare.net/hvdsomp/paul-evan-peters-lecture" };
        builder.setSpout("spout", new MemorySpout(testURLs));
  //      builder.setSpout("spout", new SQLSpout()).setNumTasks(numBuckets);
   
        builder.setBolt("partitioner", new URLPartitionerBolt())
                .shuffleGrouping("spout");

        builder.setBolt("fetch", new FetcherBolt()).fieldsGrouping(
                "partitioner", new Fields("key"));

        builder.setBolt("sitemap", new SiteMapParserBolt())
                .localOrShuffleGrouping("fetch");

        builder.setBolt("parse", new JSoupParserBolt()).localOrShuffleGrouping(
                "sitemap");

        builder.setBolt("index", new StdOutIndexer()).localOrShuffleGrouping(
                "parse");

	// builder.setBolt("indexer", new IndexerBolt()).localOrShuffleGrouping("parse");

        Fields furl = new Fields("url");
        
        // can also use MemoryStatusUpdater for simple recursive crawls
	  //builder.setBolt("status", new StdOutStatusUpdater())
	    //  .fieldsGrouping("fetch", Constants.StatusStreamName, furl)
	     // .fieldsGrouping("sitemap", Constants.StatusStreamName, furl)
	     // .fieldsGrouping("parse", Constants.StatusStreamName, furl)
	      //.fieldsGrouping("indexer", Constants.StatusStreamName, furl);

	//builder.setBolt("status", new StatusUpdaterBolt(numBuckets))
	  //  .localOrShuffleGrouping("fetch", com.digitalpebble.stormcrawler.Constants.StatusStreamName)
	    //.localOrShuffleGrouping("sitemap", com.digitalpebble.stormcrawler.Constants.StatusStreamName)
	    //.localOrShuffleGrouping("index", com.digitalpebble.stormcrawler.Constants.StatusStreamName)
	    //.localOrShuffleGrouping("parse", com.digitalpebble.stormcrawler.Constants.StatusStreamName);

	//  conf.registerMetricsConsumer(MetricsConsumer.class); //elastic search
        conf.registerMetricsConsumer(LoggingMetricsConsumer.class);
        */
        //localCluster.submitTopology("test", conf, builder.createTopology());
        return submit("crawl", conf, builder);
    }
    
}

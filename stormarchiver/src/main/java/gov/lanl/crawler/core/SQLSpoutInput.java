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

package gov.lanl.crawler.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.sql.Constants;
import com.digitalpebble.stormcrawler.util.CollectionMetric;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.StringTabScheme;

import org.apache.storm.metric.api.MultiCountMetric;
import org.apache.storm.spout.Scheme;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.utils.Utils;

@SuppressWarnings("serial")
public class SQLSpoutInput extends BaseRichSpout {

    public static final Logger LOG = LoggerFactory.getLogger(SQLSpoutInput.class);

    private static final Scheme SCHEME = new StringTabScheme();

    private SpoutOutputCollector _collector;

    private String tableName;

    private Connection connection;

    private int bufferSize = 1;

    private Queue<List<Object>> buffer = new LinkedList<>();

    /**
     * Keeps track of the URLs in flight so that we don't add them more than
     * once when the table contains just a few URLs
     **/
    private Set<String> beingProcessed = new HashSet<>();

    private boolean active;

    private MultiCountMetric eventCounter;

    private int minWaitBetweenQueriesMSec = 15000;

    private long lastQueryTime = System.currentTimeMillis();

    /**
     * if more than one instance of the spout exist, each one is in charge of a
     * separate bucket value. This is used to ensure a good diversity of URLs.
     **/
    private int bucketNum = -1;
    private Map stormConf;
    private CollectionMetric queryTimes;

    /** Used to distinguish between instances in the logs **/
    protected String logIdprefix = "";

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void open(Map conf, TopologyContext context,
            SpoutOutputCollector collector) {
        _collector = collector;
        this.stormConf = conf;
        this.eventCounter = context.registerMetric("spout",
                new MultiCountMetric(), 5);

        bufferSize = ConfUtils.getInt(conf,
                Constants.MYSQL_BUFFERSIZE_PARAM_NAME, 100);

        minWaitBetweenQueriesMSec = ConfUtils.getInt(conf,
                Constants.MYSQL_MIN_QUERY_INTERVAL_PARAM_NAME, 20000);

        tableName = ConfUtils.getString(conf, Constants.MYSQL_TABLE_PARAM_NAME);

        try {
            connection = SQLUtil.getConnection(conf);
        } catch (SQLException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }

        // determine bucket this spout instance will be in charge of
        int totalTasks = context
                .getComponentTasks(context.getThisComponentId()).size();
        if (totalTasks > 1) {
            logIdprefix = "[" + context.getThisComponentId() + " #"
                    + context.getThisTaskIndex() + "] ";
            bucketNum = context.getThisTaskIndex();
        }

        queryTimes = new CollectionMetric();
        context.registerMetric("query_time_msec", queryTimes, 20);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(SCHEME.getOutputFields());
    }

    @Override
    public synchronized void nextTuple() {
        if (!active)
            return;

       if (!buffer.isEmpty()) {
            List<Object> fields = buffer.remove();
            String url = fields.get(0).toString();
            
            this._collector.emit(fields, url);
            beingProcessed.add(url);
            
            return;
        }

        if (throttleQueries()) {
            // sleep for a bit but not too much in order to give ack/fail a
            // chance
            Utils.sleep(10);
            return;
        }

        // re-populate the buffer
        populateBuffer();
    }

    /** Returns true if SQL was queried too recently and needs throttling **/
    protected boolean throttleQueries() {
        if (lastQueryTime != 0) {
            // check that we allowed some time between queries
            long difference = Instant.now().toEpochMilli() - lastQueryTime;
            if (difference < minWaitBetweenQueriesMSec) {
                long sleepTime = minWaitBetweenQueriesMSec - difference;
                LOG.debug(
                        "{} Not enough time elapsed since {} - should try again in {}",
                        logIdprefix, lastQueryTime, sleepTime);
                return true;
            }
        }
        return false;
    }

    
    public void update_table(String url,  Date nextFetch)  {
   	
       refreshConnection();
       String query = "update IGNORE  " + tableName+ " set status='FETCHED',  nextfetchdate=DATE_ADD('"+ nextFetch +"', INTERVAL 24 MONTH) where url='"+url+"'";
       System.out.println(query);
       StringBuffer mdAsString = new StringBuffer();
      
	   Statement st;
       try {
    	    st = connection.createStatement();
		

       long start = System.currentTimeMillis();

      
             st.execute( query); 
             st.close();
			
   	
   } catch (SQLException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
   }

    
    private void populateBuffer() {

        lastQueryTime = Instant.now().toEpochMilli();
        refreshConnection();
        // select entries from mysql
        String query = "SELECT * FROM " + tableName;
        query += " WHERE nextfetchdate <= '"
                + new Timestamp(new Date().getTime()) + "'";

        // constraint on bucket num
        if (bucketNum >= 0) {
            query += " AND bucket = '" + bucketNum + "'";
        }

        query += " LIMIT " + this.bufferSize;

        int alreadyprocessed = 0;
        int numhits = 0;

        long timeStartQuery = System.currentTimeMillis();

        // create the java statement
        Statement st = null;
        ResultSet rs = null;
        try {
            st = this.connection.createStatement();

            // dump query to log
            LOG.debug("{} SQL query {}", logIdprefix, query);

            // execute the query, and get a java resultset
            rs = st.executeQuery(query);

            long timeTaken = System.currentTimeMillis() - timeStartQuery;
            queryTimes.addMeasurement(timeTaken);

            // iterate through the java resultset
            while (rs.next()) {
                String url = rs.getString("url").trim();
               // System.out.println("url:"+url+":end");
                Timestamp dt = new Timestamp(new Date().getTime());
                //update_table( url, dt);
                numhits++;
                // already processed? skip
                if (beingProcessed.contains(url)) {
               // 	System.out.println("already_being_processed");
                    alreadyprocessed++;
                    continue;
                }
                String metadata = rs.getString("metadata");
                if (metadata == null) {
                    metadata = "";
                } else if (!metadata.startsWith("\t")) {
                    metadata = "\t" + metadata;
                }
                String URLMD = url + metadata;
                List<Object> v = SCHEME.deserialize(ByteBuffer.wrap(URLMD
                        .getBytes()));
                System.out.println("adding url"+url);
                buffer.add(v);
            }

            eventCounter.scope("already_being_processed").incrBy(
                    alreadyprocessed);
            eventCounter.scope("queries").incrBy(1);
            eventCounter.scope("docs").incrBy(numhits);

            LOG.info(
                    "{} SQL query returned {} hits in {} msec with {} already being processed",
                    logIdprefix, numhits, timeTaken, alreadyprocessed);

        } catch (SQLException e) {
            LOG.error("Exception while querying table", e);
        } finally {
            try {
                if (rs != null)
                    rs.close();
            } catch (SQLException e) {
                LOG.error("Exception closing resultset", e);
            }
            try {
                if (st != null)
                    st.close();
            } catch (SQLException e) {
                LOG.error("Exception closing statement", e);
            }
        }
    }

    @Override
    public void activate() {
        super.activate();
        active = true;
    }

    @Override
    public void deactivate() {
        super.deactivate();
        active = false;
    }

    @Override
    public void ack(Object msgId) {
        LOG.debug("{}  Ack for {}", logIdprefix, msgId);
        beingProcessed.remove(msgId);
        eventCounter.scope("acked").incrBy(1);
    }

    @Override
    public void fail(Object msgId) {
        LOG.info("{}  Fail for {}", logIdprefix, msgId);
        beingProcessed.remove(msgId);
        eventCounter.scope("failed").incrBy(1);
    }

    public void refreshConnection() {
   	 
    	PreparedStatement st = null;
    	ResultSet valid = null;
    	try
    	{
    	st = connection.prepareStatement("SELECT 1 ;");
    	valid = st.executeQuery();
    	if (valid.next())
    	return;
    	} catch (SQLException e2)
    	{
    	try {
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
    	System.out.println("Connection is idle or terminated. Reconnecting...");
    	} finally
    	{
    // try {
	// st.close();
    // } catch (SQLException e) {
	 // TODO Auto-generated catch block
	 //e.printStackTrace();
     //}
     
    	}
    	 
    	long start = 0;
    	long end = 0;
    	 
    	try
    	{
    	start = System.currentTimeMillis();
    	System.out.println("Attempting to establish a connection the MySQL server!");
  
    	connection = SQLUtil.getConnection(stormConf);
    	end = System.currentTimeMillis();
    	System.out.println("Connection took " + ((end - start)) + "ms!");
    	} catch (Exception e)
    	{
    	System.out.println("Could not connect to MySQL server! because: " + e.getMessage());
    	}
    	}
    	 
  
    @Override
    public void close() {
        super.close();
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.error("Exception caught while closing SQL connection", e);
        }
    }
}

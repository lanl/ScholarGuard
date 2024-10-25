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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.storm.metric.api.MeanReducer;
import org.apache.storm.metric.api.MultiCountMetric;
import org.apache.storm.metric.api.MultiReducedMetric;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.AbstractStatusUpdaterBolt;
import com.digitalpebble.stormcrawler.persistence.Status;
import com.digitalpebble.stormcrawler.sql.Constants;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.digitalpebble.stormcrawler.util.URLPartitioner;

@SuppressWarnings("serial")
public class StatusUpdaterBolt extends AbstractStatusUpdaterBolt {

	public static final Logger LOG = LoggerFactory.getLogger(StatusUpdaterBolt.class);

	private MultiReducedMetric averagedMetrics;
	private MultiCountMetric eventCounter;

	private Connection connection;
	private String tableName;
	// private String tableName0;
	private URLPartitioner partitioner;
	private int maxNumBuckets = -1;

	public StatusUpdaterBolt(int maxNumBuckets) {
		this.maxNumBuckets = maxNumBuckets;
	}

	/** Does not shard based on the total number of queues **/
	public StatusUpdaterBolt() {
	}

	Map stormConf;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		super.prepare(stormConf, context, collector);

		partitioner = new URLPartitioner();
		partitioner.configure(stormConf);
		this.stormConf = stormConf;
		this.averagedMetrics = context.registerMetric("SQLStatusUpdater", new MultiReducedMetric(new MeanReducer()),
				10);

		this.eventCounter = context.registerMetric("counter", new MultiCountMetric(), 10);

		tableName = ConfUtils.getString(stormConf, Constants.MYSQL_TABLE_PARAM_NAME);

		try {
			connection = SQLUtil.getConnection(stormConf);
		} catch (SQLException ex) {
			LOG.error(ex.getMessage(), ex);
			throw new RuntimeException(ex);
		}

	}

	public void prepare_ext(Map stormConf) {
		partitioner = new URLPartitioner();
		partitioner.configure(stormConf);
		this.stormConf = stormConf;
		tableName = ConfUtils.getString(stormConf, Constants.MYSQL_TABLE_PARAM_NAME);

		try {
			connection = SQLUtil.getConnection(stormConf);
		} catch (SQLException ex) {
			LOG.error(ex.getMessage(), ex);
			throw new RuntimeException(ex);
		}

	}

	public void refreshConnection() {

		PreparedStatement st = null;
		ResultSet valid = null;
		try {
			st = connection.prepareStatement("SELECT 1 ;");
			valid = st.executeQuery();
			if (valid.next())
				return;
		} catch (SQLException e2) {
			try {
				connection.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Connection is idle or terminated. Reconnecting...");
		} finally {
			// try {
			// st.close();
			// } catch (SQLException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			// }

		}

		long start = 0;
		long end = 0;

		try {
			start = System.currentTimeMillis();
			System.out.println("Attempting to establish a connection the MySQL server!");
			

			connection = SQLUtil.getConnection(stormConf);
			end = System.currentTimeMillis();
			System.out.println("Connection took " + ((end - start)) + "ms!");
		} catch (Exception e) {
			System.out.println("Could not connect to MySQL server! because: " + e.getMessage());
		}
	}

	private Connection getConnection() {
		if (connection != null) {
			return connection;
		} else {
			try {
				connection = SQLUtil.getConnection(stormConf);
			} catch (SQLException ex) {
				LOG.error(ex.getMessage(), ex);
				throw new RuntimeException(ex);
			}
			return connection;

		}
	}

	private String selectMessage(String url) {

		// lastQueryTime = Instant.now().toEpochMilli();
		refreshConnection();
		// select entries from mysql
		String query = "SELECT event_id FROM  urls ";
		query += " WHERE id = md5( \"" + url + "\")";

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

			while (rs.next()) {
				msg = rs.getString("event_id");

			}

		} catch (SQLException e) {
			e.printStackTrace();
			// System.out.println("Exception while querying
			// table:"+e.getLocalizedMessage());
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

	private String getEventIdofURL(String url) {
		String query = "SELECT event_id FROM  urls ";
		query += " WHERE id = md5( \"" + url + "\");";
		String ev_id = "x";
		Integer count = 0;
		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);
			System.out.println("query" + query);
			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset
			if (!rs.isBeforeFirst()) {
				System.out.println("No Data Found"); // data not exist
				return "x";
			} else {
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

	private String computeIdofURL(String url) {
		String query = "SELECT md5( \"" + url + "\");";
		String ev_id = "x";
		Integer count = 0;
		long timeStartQuery = System.currentTimeMillis();

		// create the java statement
		Statement st = null;
		ResultSet rs = null;
		try {
			st = this.connection.createStatement();

			// execute the query, and get a java resultset
			rs = st.executeQuery(query);
			System.out.println("query" + query);
			long timeTaken = System.currentTimeMillis() - timeStartQuery;
			// queryTimes.addMeasurement(timeTaken);

			// iterate through the java resultset
			if (!rs.isBeforeFirst()) {
				System.out.println("No Data Found"); // data not exist
				return "x";
			} else {
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

	public void _store(String url, String _status, Metadata metadata, Date nextFetch) {
		refreshConnection();
		String eventurl = "";
		// the mysql insert statement
		String query = tableName + " (url, status, nextfetchdate, metadata, bucket, host,id,event_id)"
				+ " values (?, ?, ?, ?, ?, ?,md5(?),md5(?))";

		String query1 = tableName + " (url, status, nextfetchdate, metadata, bucket, host,id,event_id)"
				+ " values (?, ?, ?, ?, ?, ?,md5(?),?)";

		StringBuffer mdAsString = new StringBuffer();
		String parent = null;
		for (String mdKey : metadata.keySet()) {

			String[] vals = metadata.getValues(mdKey);
			for (String v : vals) {

				if (mdKey.equals("event")) {
					eventurl = v;
				}
				if (mdKey.equals("url.path")) {
					parent = v;

				}
				mdAsString.append("\t").append(mdKey).append("=").append(v);
			}
		}
		if (mdAsString.toString() != null) {
			System.out.println("metadata:" + mdAsString.toString());
		} else {
			System.out.println("metadata null:");
		}
		if (eventurl.equals("")) {
			eventurl = selectMessage(parent);
			query = query1;
		}
		int partition = 0;
		String partitionKey = partitioner.getPartition(url, metadata);
		if (maxNumBuckets > 1) {
			// determine which shard to send to based on the host / domain / IP
			partition = Math.abs(partitionKey.hashCode() % maxNumBuckets);
		}

		// String _status = status.toString();

		// create in table if does not already exist

		if (_status.equals("SUBTRACE")) {
			_status = "DISCOVERED";
			// String id = computeIdofURL(url);
			String event_id = computeIdofURL(eventurl);
			String old_event_id = getEventIdofURL(url);
			System.out.println(" old_event_id:" + old_event_id);
			if (old_event_id.equals("x")) {
				// new message
				System.out.println("new message");
				query = "INSERT  IGNORE INTO " + query;
			} else {
				int count = checkoutputMessagebyEventId(old_event_id);
				     if (count > 0) {
					 System.out.println("prevoius case already processed");
					// prevoius case already processed
					  query = "REPLACE INTO " + query;
				     } else {
					// privios case not processed
				    	 
					if (event_id.equals(old_event_id)) {
						 System.out.println("same message reprocessed");
						// same message reprocessed
						query = "INSERT  IGNORE INTO " + query;
					} else {
						System.out.println("on hold :" +url);
						// url from different message
						_status = "ONHOLD";
						url = event_id + ":" + url;
						query = "INSERT  IGNORE INTO " + query;
					}

				}
			}

		}

		PreparedStatement preparedStmt;
		try {

			preparedStmt = connection.prepareStatement(query);
			preparedStmt.setString(1, url);
			preparedStmt.setString(2, _status);
			preparedStmt.setObject(3, nextFetch);
			preparedStmt.setString(4, mdAsString.toString());
			preparedStmt.setInt(5, partition);
			preparedStmt.setString(6, partitionKey);
			preparedStmt.setString(7, url);
			preparedStmt.setString(8, eventurl);
			long start = System.currentTimeMillis();
			System.out.println(preparedStmt);
			// execute the preparedstatement
			preparedStmt.execute();
			preparedStmt.close();
			if (eventCounter != null) {
				eventCounter.scope("sql_query_number").incrBy(1);
			}
			if (averagedMetrics != null) {
				averagedMetrics.scope("sql_execute_time").update(System.currentTimeMillis() - start);
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void store(String url, Status status, Metadata metadata, Date nextFetch) {
		refreshConnection();
		String eventurl = "";
		// the mysql insert statement
		String query = tableName + " (url, status, nextfetchdate, metadata, bucket, host,id,event_id)"
				+ " values (?, ?, ?, ?, ?, ?,md5(?),md5(?))";

		String query1 = tableName + " (url, status, nextfetchdate, metadata, bucket, host,id,event_id)"
				+ " values (?, ?, ?, ?, ?, ?,md5(?),?)";

		StringBuffer mdAsString = new StringBuffer();
		String parent = null;
		for (String mdKey : metadata.keySet()) {

			String[] vals = metadata.getValues(mdKey);
			for (String v : vals) {

				if (mdKey.equals("event")) {
					eventurl = v;
				}
				if (mdKey.equals("url.path")) {
					parent = v;

				}
				mdAsString.append("\t").append(mdKey).append("=").append(v);
			}
		}
		if (mdAsString.toString() != null) {
			System.out.println("metadata:" + mdAsString.toString());
		} else {
			System.out.println("metadata null:");
		}
		if (eventurl.equals("")) {
			eventurl = selectMessage(parent);
			query = query1;
		}
		int partition = 0;
		String partitionKey = partitioner.getPartition(url, metadata);
		if (maxNumBuckets > 1) {
			// determine which shard to send to based on the host / domain / IP
			partition = Math.abs(partitionKey.hashCode() % maxNumBuckets);
		}

		String _status = status.toString();

		// create in table if does not already exist

		if (status.equals(Status.DISCOVERED)) {
			query = "INSERT  IGNORE INTO " + query;
		} else
			query = "REPLACE INTO " + query;

		PreparedStatement preparedStmt;
		try {

			preparedStmt = connection.prepareStatement(query);
			preparedStmt.setString(1, url);
			preparedStmt.setString(2, _status);
			preparedStmt.setObject(3, nextFetch);
			preparedStmt.setString(4, mdAsString.toString());
			preparedStmt.setInt(5, partition);
			preparedStmt.setString(6, partitionKey);
			preparedStmt.setString(7, url);
			preparedStmt.setString(8, eventurl);
			long start = System.currentTimeMillis();
			System.out.println(preparedStmt);
			// execute the preparedstatement
			preparedStmt.execute();
			preparedStmt.close();
			if (eventCounter != null) {
				eventCounter.scope("sql_query_number").incrBy(1);
			}
			if (averagedMetrics != null) {
				averagedMetrics.scope("sql_execute_time").update(System.currentTimeMillis() - start);
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
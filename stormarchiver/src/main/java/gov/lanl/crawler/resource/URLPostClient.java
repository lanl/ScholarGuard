package gov.lanl.crawler.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

public class URLPostClient {

	String postpoint = null;
	
	     
	HttpClient mClient;

	public URLPostClient(String endpoint) {
		postpoint = endpoint;
		 HttpConnectionManager man = new MultiThreadedHttpConnectionManager();
		 HttpConnectionManagerParams params = new HttpConnectionManagerParams();
		    params.setDefaultMaxConnectionsPerHost(16);
		    params.setConnectionTimeout(30000);
		    params.setSoTimeout(30000);
		  
		    man.setParams(params);
		    mClient = new HttpClient(man);
	}


	public int send_message(String pload) {
		PostMethod mPut = new PostMethod(postpoint);
		mPut.addRequestHeader("Content-Type", "application/ld+json");
		int returnCode = 0;
		try {
			InputStream is = new ByteArrayInputStream(pload.getBytes());
			mPut.setRequestBody(is);
			 returnCode = mClient.executeMethod(mPut);
			System.out.println(mPut.getResponseHeaders().toString());
			//System.out.println("publisher returncode:" + returnCode);
			is.close();
		} catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Header[] headers = mPut.getResponseHeaders();
		// for ( int i = 0; i < headers.length; ++i){
		// System.out.println( headers[i]);
		// }

		finally {
			mPut.releaseConnection();
		}
		return returnCode;
	}

}

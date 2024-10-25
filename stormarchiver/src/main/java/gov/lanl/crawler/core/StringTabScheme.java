package gov.lanl.crawler.core;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.storm.spout.Scheme;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.persistence.Status;


public class StringTabScheme implements Scheme , Serializable {
	private static final long serialversionUID = 
            129348938L; 
    private Status withStatus = null;

    public StringTabScheme() {
        withStatus = null;
    }

    public StringTabScheme(Status status) {
        withStatus = status;
    }

    @Override
    public List<Object> deserialize(ByteBuffer bytes) {
        String input = new String(bytes.array(), StandardCharsets.UTF_8);

        String[] tokens = input.split("\t");
        if (tokens.length < 1)
            return new Values();

        String url = tokens[0];
        Metadata metadata =   new Metadata();
        metadata.addValue("url.path", url);
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            System.out.println(token);
            // split into key & value
            int firstequals = token.indexOf("=");
            String value = null;
            String key = token;
            if (firstequals != -1) {
                key = token.substring(0, firstequals).trim();
                System.out.println("key:"+value);
                value = token.substring(firstequals + 1).trim();
                System.out.println("value::"+value);
            }
           // if (metadata == null)
             //   metadata = new Metadata();
            metadata.addValue(key, value);
        }

       // if (metadata == null)
         //   metadata = new Metadata();

        if (withStatus != null)
            return new Values(url, metadata, withStatus);

        return new Values(url, metadata);
    }

    @Override
    public Fields getOutputFields() {
        if (withStatus != null)
            return new Fields("url", "metadata", "status");
        return new Fields("url", "metadata");
    }

	
}
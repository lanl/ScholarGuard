package gov.lanl.crawler.proto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.protocol.selenium.NavigationFilter;
import com.digitalpebble.stormcrawler.sql.Constants;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.fasterxml.jackson.databind.JsonNode;
//import com.hubspot.chrome.devtools.client.ChromeDevToolsClient;
//import com.hubspot.chrome.devtools.client.ChromeDevToolsSession;

import gov.lanl.crawler.boundary.PortalRule;

public class JSFilter  extends NavigationFilter { 
	Map stormConf;
	String jstemplate;
	private List<PortalRule> portalrules;
	
	String selector = "img.img-responsive" ;
	public void configure(Map stormConf, JsonNode filterParams) {
		this.stormConf = stormConf;
		JsonNode nodefile = filterParams.get("portalFile");
		
		if (nodefile != null) {
			jstemplate = filterParams.get("portalFile").asText();
			System.out.println("filter:" + jstemplate);
			
			String urlmatch = filterParams.get("uriRegex").asText();
			selector = filterParams.get("selector").asText();
			portalrules = new ArrayList<>();
			// for (JsonNode urlFilterNode : rulesList) {
			try {
				
				PortalRule rule = createRule(urlmatch);
				if (rule != null) {
					portalrules.add(rule);
				}
			} catch (IOException e) {
				// LOG.error("There was an error reading regex filter {}",
				// urlFilterNode.asText(), e);
			}
			
		}
		

	}

	public PortalRule createRule(String line) throws IOException {
		// char first = line.charAt(0);
		char first = '+';
		boolean sign;
		switch (first) {
		case '+':
			sign = true;
			break;
		case '-':
			sign = false;
			break;
		// case ' ':
		// case '\n':
		// case '#': // skip blank & comment lines
		// return null;
		default:
			throw new IOException("Invalid first character: " + line);
		}

		// String regex = line.substring(1);
		String regex = line;
		// System.out.println("Adding rule [{}]" + regex);
		// LOG.trace("Adding rule [{}]", regex);
		PortalRule rule = createRule(sign, regex);
		return rule;
	}

	public String _filter(String url) {

		for (PortalRule rule : portalrules) {
			// System.out.println(rule.regex + rule.sign);

			if (rule.accept()) {
				if (!rule.match(url)) {
					// System.out.println(" not matching include condition");
					return null;
				}

			} else {

				if (rule.match(url)) {
					// System.out.println("matching exclude condition");
					return null;
				}

			}
		}

		return url;

	}

	public PortalRule createRule(boolean sign, String regex) {
		return new PortalRule(sign, regex);
	}

@Override
//experimental not used
public ProtocolResponse filter(RemoteWebDriver driver, Metadata metadata) {
	// TODO Auto-generated method stub
	String urlValue = driver.getCurrentUrl().toString();
	if (_filter(urlValue) == null) {
		System.out.println("return from filter");
		return null;
	}
	StringBuilder dummyContent = new StringBuilder("<html>");
	String tableName = ConfUtils.getString(stormConf, Constants.MYSQL_TABLE_PARAM_NAME);
	JavascriptExecutor jse = (JavascriptExecutor) driver;
	//jse.executeScript("var s=window.document.createElement('script'); s.src="+jstemplate+";window.document.head(s);");
	Scanner sc;
	try {
		sc = new Scanner(new FileInputStream(new File(jstemplate)));	
	String inject = ""; 
	    while (sc.hasNext()) {          
	        String[] s = sc.next().split("\r\n");   
	        for (int i = 0; i < s.length; i++) {
	            inject += s[i];
	            inject += " ";
	        }           
	    }
	    inject = inject.replace("json", "{'selector': 'img.img-responsive'}");
	    jse.executeAsyncScript(inject, "");
	   // jse .executeScript("document.getElementById(\"video\").play()");
	    System.out.println(inject);
	     DevToolsUtil dt= new  DevToolsUtil();
	    
	    //ChromeDevToolsClient client = ChromeDevToolsClient.defaultClient();
	   /* try (ChromeDevToolsSession session = client.connect("127.0.0.1", 9292)) {
	    	  // Control Chrome remotely
	    	  session.navigate("urlValue");
	    	  session.waitDocumentReady(5000);
	    	  
	    	  session.getPage().addScriptToEvaluateOnLoad(inject);
	    	} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			*/
	   
	   // jse.executeScript("var s=window.document.createElement('script'); s.src="+jstemplate+";window.document.head(s);");
/*
	    driver.get("https://www.wonderplugin.com/wordpress-lightbox");
	    WebElement element=driver.findElement(By.xpath("//a[contains(text(),'Open a Div in Lightbox')]"));
	    element.click();
	    WebElement frameElement=driver.findElement(By.xpath("//iframe[@src='https://www.youtube.com/embed/wswxQ3mhwqQ']"));
	            driver.switchTo().frame(frameElement);
	           // driver.findElement(By.xpath("//button[@aria-label=\'Play\']")).click();
*/
	    jse.executeScript(inject);
	    //jse.executeScript("return ourFunction");
	
	    dummyContent.append("</html>");
} catch (FileNotFoundException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
	return new ProtocolResponse(dummyContent.toString().getBytes(), 200, metadata);
}
}
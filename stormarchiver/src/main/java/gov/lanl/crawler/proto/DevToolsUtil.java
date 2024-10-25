package gov.lanl.crawler.proto;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.Date;
import java.util.Scanner;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.chrome.ChromeDriverService;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
//import sun.misc.BASE64Decoder;
import org.apache.commons.codec.binary.Base64;
public class DevToolsUtil {
	static WebSocket webSocket = null;
	static ChromeDriverService service;
	final static Object waitCoordinator = new Object();
	final static int timeoutValue = 5;
	public static String response;
	static DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy_h-m-s");

	private static String getWebSocketDebuggerUrl() throws IOException {
		String webSocketDebuggerUrl = "";
		File file = new File(System.getProperty("user.dir") + "/target/chromedriver.log");
		try {

			Scanner sc = new Scanner(file);
			String urlString = "";
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.contains("DevTools request: http://localhost")) {
					urlString = line.substring(line.indexOf("http"), line.length()).replace("/version", "");
					break;
				}
			}
			sc.close();

			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String json = org.apache.commons.io.IOUtils.toString(reader);
			JSONArray jsonArray = new JSONArray(json);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				if (jsonObject.getString("type").equals("page")) {
					webSocketDebuggerUrl = jsonObject.getString("webSocketDebuggerUrl");
					break;
				}
			}
		} catch (FileNotFoundException e) {
			throw e;
		}
		if (webSocketDebuggerUrl.equals(""))
			throw new RuntimeException("webSocketDebuggerUrl not found");
		return webSocketDebuggerUrl;
	}

	public static String sendWSMessage(String url, String message)
			throws IOException, WebSocketException, InterruptedException {

		final int matchJSONId = new JSONObject(message).getInt("id");
		if (webSocket == null) {
			webSocket = new WebSocketFactory().createSocket(url).addListener(new WebSocketAdapter() {
				@Override
				public void onTextMessage(WebSocket ws, String message) {
					response = message;
					// Received response.
					if (new JSONObject(message).getInt("id") == matchJSONId) {
						synchronized (waitCoordinator) {
							waitCoordinator.notifyAll();
						}
					}
				}
			}).connect();
		}
		webSocket.sendText(message);
		synchronized (waitCoordinator) {
			waitCoordinator.wait(timeoutValue * 1000);
		}
		return response;
	}

	protected static String getDeviceMetrics(String wsURL)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":0,\"method\" : \"Runtime.evaluate\", \"params\" : {\"returnByValue\" : true, \"expression\" : \"({width: Math.max(window.innerWidth,document.body.scrollWidth,document.documentElement.scrollWidth)|0,height: Math.max(window.innerHeight,document.body.scrollHeight,document.documentElement.scrollHeight)|0,deviceScaleFactor: window.devicePixelRatio || 1,mobile: typeof window.orientation !== 'undefined'})\"}}";
		JSONObject responseParser = new JSONObject(sendWSMessage(wsURL, msg));
		JSONObject result1Parser = responseParser.getJSONObject("result");
		JSONObject result2Parser = result1Parser.getJSONObject("result");
		return result2Parser.getJSONObject("value").toString();
	}

	protected static void setDeviceMetrics(String wsURL, String devicePropertiesJSON)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":1,\"method\":\"Emulation.setDeviceMetricsOverride\", \"params\":" + devicePropertiesJSON
				+ "}";
		sendWSMessage(wsURL, msg);
	}

	protected static String getbase64ScreenShotData(String wsURL)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":2,\"method\":\"Page.captureScreenshot\", \"params\":{\"format\":\"png\", \"fromSurface\":true}}";
		JSONObject responseParser = new JSONObject(sendWSMessage(wsURL, msg));
		JSONObject resultParser = responseParser.getJSONObject("result");
		return resultParser.getString("data");
	}

	protected static void clearDeviceMetrics(String wsURL)
			throws IOException, WebSocketException, InterruptedException {
		String msg = "{\"id\":3,\"method\":\"Emulation.clearDeviceMetricsOverride\", \"params\":{}}";
		sendWSMessage(wsURL, msg);
	}

	/*protected static void takeScreenShot() throws IOException, WebSocketException, InterruptedException {
		String webSocketURL = getWebSocketDebuggerUrl();
		System.out.println("Web Socket Debug URL: " + webSocketURL);

		String deviceJson = getDeviceMetrics(webSocketURL);
		setDeviceMetrics(webSocketURL, deviceJson);
		String base64Data = getbase64ScreenShotData(webSocketURL);
		clearDeviceMetrics(webSocketURL);

		Date date = new Date();
		File ScreenShotDestFile = new File(
				System.getProperty("user.dir") + File.separator + dateFormat.format(date) + ".png");

		BASE64Decoder base64Decoder = new BASE64Decoder();
		byte[] decodedBytes = base64Decoder.decodeBuffer(base64Data);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodedBytes));

		ImageIO.write(image, "png", ScreenShotDestFile);
	}
*/
	 private String buildNetWorkEnableMessage(){
	        String message = "{\"id\":1,\"method\":\"Network.enable\",\"params\":{\"maxTotalBufferSize\":10000000,\"maxResourceBufferSize\":5000000}}";
	        System.out.println(message);
	        return message;
	    }

	    private String buildGeoLocationMessage(String latitude, String longitude){
	        String message = String.format("{\"id\":3,\"method\":\"Emulation.setGeolocationOverride\",\"params\":{\"latitude\":%s,\"longitude\":%s,\"accuracy\":100}}",latitude,longitude);
	        System.out.println(message);
	        return message;
	    }

	    private String buildRequestInterceptorEnabledMessage(){
	        String message = String.format("{\"id\":4,\"method\":\"Network.setRequestInterceptionEnabled\",\"params\":{\"enabled\":true}}");
	        System.out.println(message);
	        return message;
	    }

	    private String buildRequestInterceptorPatternMessage(String pattern, String documentType){
	        String message = String.format("{\"id\":5,\"method\":\"Network.setRequestInterception\",\"params\":{\"patterns\":[{\"urlPattern\":\"%s\",\"resourceType\":\"%s\"}]}}",pattern,documentType);
	        System.out.println(message);
	        return message;
	    }

	    private String buildBasicHttpAuthenticationMessage(String username,String password){
	        byte[] encodedBytes = Base64.encodeBase64(String.format("%s:%s",username,password).getBytes());
	        String base64EncodedCredentials = new String(encodedBytes);
	         String message = String.format("{\"id\":2,\"method\":\"Network.setExtraHTTPHeaders\",\"params\":{\"headers\":{\"Authorization\":\"Basic %s\"}}}",base64EncodedCredentials);
	        System.out.println(message);
	        return message;
	    }
	
}

package gov.lanl.crawler.boundary;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

//import atu.testrecorder.ATUTestRecorder;

public class TraceUtils {

	
	public void click_to_center(WebDriver driver) {	
		Dimension window = driver.manage().window().getSize();
		new Actions(driver)
		        .moveByOffset(window.getHeight() / 2, window.getWidth() / 2)
		        .click()
		        .build()
		        .perform();
	}
		public void zoomOut(RemoteWebDriver driver) {
			WebElement html = driver.findElement(By.tagName("html"));
			html.sendKeys(Keys.chord(Keys.CONTROL, Keys.SUBTRACT));
		}

		public void slowScroll(RemoteWebDriver driver) {
			for (int second = 0;; second++) {
				if (second >= 60) {
					break;
				}
				((JavascriptExecutor) driver).executeScript("window.scrollBy(0,400)", ""); // y value '400' can be altered
				
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		public void _Scroll(RemoteWebDriver driver) {
			boolean readyStateComplete = false;
			//boolean pageEnded = false;
			while (!readyStateComplete) {
				((JavascriptExecutor) driver).executeScript("window.scrollBy(0,document.body.offsetHeight)", "");
				//pageEnded = (String)js.executeScript("return document.readyState;") ? true;
				readyStateComplete = ((JavascriptExecutor) driver).executeScript("return document.readyState") == "complete";
			}
			
		}
		
		 /**
	     * Attempts to click on an element multiple times (to avoid stale element
	     * exceptions caused by rapid DOM refreshes)
	     *
	     * @param d
	     *            The WebDriver
	     * @param by
	     *            By element locator
	     */
	    public static void dependableClick(WebDriver d, By by)
	    {
	        final int MAXIMUM_WAIT_TIME = 10;
	        final int MAX_STALE_ELEMENT_RETRIES = 5;

	        WebDriverWait wait = new WebDriverWait(d, MAXIMUM_WAIT_TIME);
	        int retries = 0;
	        while (true)
	        {
	            try
	            {
	                wait.until(ExpectedConditions.elementToBeClickable(by)).click();

	                return;
	            }
	            catch (StaleElementReferenceException e)
	            {
	                if (retries < MAX_STALE_ELEMENT_RETRIES)
	                {
	                    retries++;
	                    continue;
	                }
	                else
	                {
	                    throw e;
	                }
	            }
	        }
	    }
	    public static void presClick(WebDriver d, By by)
	    {
	        final int MAXIMUM_WAIT_TIME = 10;
	        final int MAX_STALE_ELEMENT_RETRIES = 5;

	        WebDriverWait wait = new WebDriverWait(d, MAXIMUM_WAIT_TIME);
	        int retries = 0;
	        while (true)
	        {
	            try
	            {
	                wait.until(ExpectedConditions.presenceOfElementLocated(by)).click();

	                return;
	            }
	            catch (StaleElementReferenceException e)
	            {
	                if (retries < MAX_STALE_ELEMENT_RETRIES)
	                {
	                    retries++;
	                    continue;
	                }
	                else
	                {
	                    throw e;
	                }
	            }
	        }
	    }
	   
	    /*
	    public  ATUTestRecorder createvideorecorder() {
	    	
	  	  ATUTestRecorder recorder =null;
	  	  
	  	  DateFormat dateFormat = new SimpleDateFormat("yy-MM-dd HH-mm-ss"); 
	  	  Date date = new Date(); //Created object of ATUTestRecorder //Provide path to store
	  	  // videos and file name format.
	  	  try { String dir=System.getProperty("user.dir");
	  	  // System.setProperty(“java.awt.headless”, “false”);
	  	  recorder = new ATUTestRecorder(System.getProperty("user.dir") + File.separator +"/target","testvideo",false);
	  	 
	  	  //To start video recording.
	  	  recorder.start(); } 
	  	  catch (Exception e1) { //
	  	  //TODO Auto-generated catch block 
	  		  e1.printStackTrace(); 
	  	  }
	  	 return recorder;
	  	}
*/
		public Boolean RetryingFindClick(WebElement webElement, WebDriver driver) {
			Boolean result = false;
			int attempts = 0;
			while (attempts < 2) {
				try {
					// webElement.click();
					Actions builder = new Actions(driver);
					builder.moveToElement(webElement).click(webElement);
					builder.perform();
					result = true;
					break;
				} catch (StaleElementReferenceException e) {
					System.out.println(e.getMessage());
				}
				attempts++;
			}
			return result;
		}

		public static boolean isloadComplete(WebDriver driver) {
			return ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("loaded")
					|| ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete");
		}
		
		public void waitForAjaxLoad(WebDriver driver) throws InterruptedException {
			JavascriptExecutor executor = (JavascriptExecutor) driver;
			if ((Boolean) executor.executeScript("return window.jQuery != undefined")) {
				while (!(Boolean) executor.executeScript("return jQuery.active == 0")) {
					Thread.sleep(1000);
				}
			}
			return;
		}
       public void scrollwithTimeout(RemoteWebDriver driver,long timeoutMilliSeconds) {
    	   TimeoutBlock tBlock = new TimeoutBlock(timeoutMilliSeconds);
   		   Runnable block0 = new Runnable() {
   			RemoteWebDriver driver;
   			//BehaviorsUtils bu;

   			@Override
   			public void run() {
   				backscroll500(driver);
   			}

   			public Runnable init( RemoteWebDriver driver) {
   			//	this.bu = bu;
   				this.driver = driver;
   				return (this);
   			}
   		}.init( driver);
    	   
   	    // this is default scroll -avoiding infinite scroll
   			try {
   				tBlock.addBlock(block0);
   			} catch (Throwable e1) {
   				// TODO Auto-generated catch block
   				e1.printStackTrace();
   			}
       }
		
		public void scroll(RemoteWebDriver driver) {
			((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
		}
		public void scrolltotop(RemoteWebDriver driver) {
		((JavascriptExecutor) driver)
	    .executeScript("window.scrollTo(0,0)");
		}
		
		public void backscroll0 (  RemoteWebDriver driver){
			//what if it never completes?
			boolean readyStateComplete = false;
			JavascriptExecutor js = ((JavascriptExecutor) driver);
		    while (!readyStateComplete) {
			js.executeScript("window.scrollTo(0,document.body.scrollHeight)", "");				
			readyStateComplete = waitForJSandJQueryToLoad(driver);			
		  }
		    js.executeScript("window.scrollTo(0,-document.body.scrollHeight)");
          }
		
		public void infinitescroll ( RemoteWebDriver driver) {
			boolean readyStateComplete = false;
			JavascriptExecutor js = ((JavascriptExecutor) driver);			
			Object height = js.executeScript("return document.body.scrollHeight");
			System.out.println ("height"+height);
			boolean reachedbottom = false ;
			while (true) {
				 js.executeScript("window.scrollTo(0,Math.max(document.documentElement.scrollHeight,document.body.scrollHeight,document.documentElement.clientHeight));");
				 readyStateComplete = waitForJSandJQueryToLoad(driver);  
				 Long a = (Long) js.executeScript("return document.documentElement.scrollTop;");
					System.out.println ("top"+a);
					  Long      b = (Long) js.executeScript("return document.documentElement.scrollHeight - document.documentElement.clientHeight;");
						System.out.println ("height"+b);
					       Long relativeHeight = a / b;
					       System.out.println ("rel"+relativeHeight);
					        if(relativeHeight.equals(1l)) {break;}
			}
			//js.executeScript("window.scrollTo(0,-document.body.scrollHeight)");
		}
		
		public void backscroll500 ( RemoteWebDriver driver) {
			boolean readyStateComplete = false;
			JavascriptExecutor js = ((JavascriptExecutor) driver);			
			Object height = js.executeScript("return document.body.scrollHeight");
			//System.out.println ("height"+height);
			boolean reachedbottom = false ;
			while (true) {
				 js.executeScript("window.scrollBy(0,500);");
				 readyStateComplete = waitForJSandJQueryToLoad(driver);  
				 Long a = (Long) js.executeScript("return document.documentElement.scrollTop;");
					//System.out.println ("top"+a);
					  Long      b = (Long) js.executeScript("return document.documentElement.scrollHeight - document.documentElement.clientHeight;");
					  if (b.equals(0L)) { break;}
						//System.out.println ("height"+b);
					       Long relativeHeight = a / b;
					     //  System.out.println ("rel"+relativeHeight);
					        if(relativeHeight.equals(1l)) {break;}
			}
			js.executeScript("window.scrollTo(0,-document.body.scrollHeight)");
		}
		//scroll by offset
		public void backscroll ( RemoteWebDriver driver) {
			boolean readyStateComplete = false;
			JavascriptExecutor js = ((JavascriptExecutor) driver);			
			Object height = js.executeScript("return document.body.scrollHeight");
			//System.out.println ("height"+height);
			Object offset = js.executeScript("return document.body.offsetHeight");
			//System.out.println ("height"+offset);			
			boolean reachedbottom = false ;
			while (true) {
				 js.executeScript("window.scrollBy(0,document.body.offsetHeight);");
				 readyStateComplete = waitForJSandJQueryToLoad(driver);  
				 Long a = (Long) js.executeScript("return document.documentElement.scrollTop;");
					//System.out.println ("top"+a);
					  Long      b = (Long) js.executeScript("return document.documentElement.scrollHeight - document.documentElement.clientHeight;");
						//System.out.println ("height"+b);
					       Long relativeHeight = a / b;
					       //System.out.println ("rel"+relativeHeight);
					        if(relativeHeight.equals(1l)) {break;}
			}
			js.executeScript("window.scrollTo(0,-document.body.scrollHeight)");
		}
		
		
		public static String captureScreen(RemoteWebDriver driver) {
			String path;
			try {
				WebDriver augmentedDriver = new Augmenter().augment(driver);
				File source = ((TakesScreenshot) augmentedDriver).getScreenshotAs(OutputType.FILE);
				path = "./target/screenshots/" + source.getName();
				FileUtils.copyFile(source, new File(path));
			} catch (IOException e) {
				path = "Failed to capture screenshot: " + e.getMessage();
			}
			return path;
		}
/*
 *currently not used
		public  void doDrag(RemoteWebDriver driver) {

			// driver.get(urlValue);
			// try {
			// TimeUnit.MINUTES.sleep(2);
			// } catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			// e1.printStackTrace();
			// }

			WebElement we = getElementByLocator(By.cssSelector("canvas.widget-scene-canvas"), driver);

			// Boolean isPresent =
			// driver.findElements(By.cssSelector("canvas.widget-scene-canvas")).size() > 0;

			Point start = we.getLocation();
			Dimension cs = we.getSize();
			int w = cs.getWidth();
			int h = cs.getHeight();
			System.out.println("w" + w + "," + "h" + h);
			System.out.println(we.getLocation().getX());
			System.out.println(we.getLocation().getY());

			// List<WebElement> e = driver.findElements(By.tagName("canvas"));
			if (we != null) {

				captureScreen(driver);
				// int x=1000, y=0;
				// new Actions(driver).dragAndDropBy(we, 0, 1000).build() .perform();
				new Actions(driver).moveToElement(we).pause(Duration.ofSeconds(1)).clickAndHold(we).moveByOffset(0, 1000)
						.pause(Duration.ofSeconds(2)).release().perform();
				System.out.println(we.getLocation().getX());
				System.out.println(we.getLocation().getY());
				// new
				// Actions(driver).clickAndHold(we).moveByOffset(0,1000).release().perform();
				captureScreen(driver);
				// driver.navigate().back();
			}
			// we = getElementByLocator(By.cssSelector("canvas.widget-scene-canvas"),
			// driver);

			// if( we!=null) {
			// new Actions(driver).dragAndDropBy(we, 0, -1000).build() .perform();
			new Actions(driver).moveToElement(we).pause(Duration.ofSeconds(1)).clickAndHold(we).moveByOffset(0, -1000)
					.pause(Duration.ofSeconds(2)).release().perform();

			captureScreen(driver);
			// driver.navigate().back();
			// }
			// we = getElementByLocator(By.cssSelector("canvas.widget-scene-canvas"),
			// driver);
			if (we != null) {
				new Actions(driver).dragAndDropBy(we, 1000, 0).build().perform();
				captureScreen(driver);
				// driver.navigate().back();
			}
			// we = getElementByLocator(By.cssSelector("canvas.widget-scene-canvas"),
			// driver);
			if (we != null) {
				new Actions(driver).dragAndDropBy(we, -1000, 0).build().perform();
				captureScreen(driver);
				// driver.navigate().back();
			}
			// Actions actions = new Actions(driver);
			// Actions move = actions.moveByOffset(x, y);

			// actions.dragAndDropBy(source, x, y)
			// move.perform();
			// driver.manage().timeouts().implicitlyWait(4,TimeUnit.MINUTES);
			captureScreen(driver);

			driver.navigate().back();

		}

	*/	
		
		public boolean waitForJSandJQueryToLoad(WebDriver driver) {

			WebDriverWait wait = new WebDriverWait(driver, 60);

			// wait for jQuery to load
			ExpectedCondition<Boolean> jQueryLoad = new ExpectedCondition<Boolean>() {
			//	 @Override
			  //      public Boolean apply(WebDriver driver) {
			    //        return (Boolean) ((JavascriptExecutor) driver).executeScript("return (window.jQuery != null) && (jQuery.active === 0);");
			      //  }
			    //};
				
			    @Override
				public Boolean apply(WebDriver driver) {
					try {
						return ((Long) ((JavascriptExecutor) driver).executeScript("return jQuery.active") == 0);
					} catch (Exception e) {
						// no jQuery present
						return true;
					}
				}
			};
			
			// wait for Javascript to load
			ExpectedCondition<Boolean> jsLoad = new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver driver) {
					return ((JavascriptExecutor) driver).executeScript("return document.readyState").toString()
							.equals("complete");
				}
			};

			
			
			
			/*if  ((Boolean) ((JavascriptExecutor) driver).executeScript("return window.jQuery != undefined")) {
				System.out.println("waited for jquery");
				return wait.until(jQueryLoad) && wait.until(jsLoad);
			}
			else {
			return  wait.until(jsLoad);
			}
			*/
			return wait.until(jQueryLoad) && wait.until(jsLoad);
		}
		
		public void testFacebookLogin(WebDriver driver) throws Exception {
		    driver.get("https://www.facebook.com/");
		    driver.findElement(By.id("email")).click();
		    driver.findElement(By.id("email")).clear();
		    driver.findElement(By.id("email")).sendKeys("in28minutes");
		    driver.findElement(By.id("pass")).clear();
		    driver.findElement(By.id("pass")).sendKeys("dummy");
		    driver.findElement(By.id("pass")).sendKeys(Keys.ENTER);
		  }
		
		public void getScrollH(WebDriver driver) {
		JavascriptExecutor javascript = (JavascriptExecutor) driver;
		Boolean VertscrollStatus = (Boolean) javascript.executeScript("return document.documentElement.scrollHeight>document.documentElement.clientHeight;");
		}

		/*
		public static void playHTML5(RemoteWebDriver driver, String urlValue, String locatortype, String tn) {
			// {
			// "action_order":"2","value":"ytp-play-button","type":"CSSselector","action":"click"}]
			// List<WebElement> iframe = driver.findElements(By.tagName("iframe"));
			// driver.switchTo().frame(iframe.get(0));
			// System.out.println("iframe");
			System.out.println("video");

			// driver.get(urlValue);
			// driver.manage().timeouts().implicitlyWait(50,TimeUnit.SECONDS);
			// driver.getScreenshotAs("my_screenshot.png");
			// String length_str =
			// driver.findElementByClassName("ytp-time-duration").getText();
			// String current_time_str =
			// driver.findElementByClassName("ytp-time-current").getText();
			// if (length_str!=null) {
			// System.out.println("total length:"+length_str)
			// ;
			// }
			// WebElement video = getElementByLocator(By.tagName("video"), driver);
			WebElement video = null;
			List<WebElement> videos = getElementsBy(locatortype, tn, driver);
			if (videos != null) {
				video = videos.get(0);
			}

			String id = video.getAttribute("id");

			// WebElement video = driver.findElement(By.tagName("video"));
			JavascriptExecutor js = driver;

			js.executeScript("arguments[0].play();", video);
			// driver.manage().timeouts().implicitlyWait(120,TimeUnit.SECONDS);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			// pausing the video
			js.executeScript("arguments[0].pause();", video);
			// driver.manage().timeouts().implicitlyWait(5,TimeUnit.SECONDS);

			js.executeScript("arguments[0].load();", video);

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			// playing the video
			// js.executeScript("arguments[0].play();", video);
			// driver.manage().timeouts().implicitlyWait(120,TimeUnit.SECONDS);

			// muting the video
			// js.executeScript("arguments[0].mute();", video);
			// try {
			// Thread.sleep(5000L);
			// } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			// }

			// unmuting the video
			// js.executeScript("arguments[0].unMute();", video);
			// try {
			// Thread.sleep(5000L);
			// } catch (InterruptedException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			// }

			// Dragging the video
			js.executeScript("arguments[0].currentTime = 600;", video);
			try {
				Thread.sleep(5000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			js.executeScript("alert(arguments[0].readyState);", video);
		}

	*/
	
}

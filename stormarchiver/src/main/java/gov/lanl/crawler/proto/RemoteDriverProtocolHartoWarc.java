package gov.lanl.crawler.proto;

import java.io.IOException;
import java.net.Socket;

/*
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



import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.storm.Config;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

//import com.digitalpebble.stormcrawler.protocol.selenium.SeleniumProtocol;
import com.digitalpebble.stormcrawler.util.ConfUtils;




public class RemoteDriverProtocolHartoWarc extends SeleniumProtocolCommonWarc {
	
	
	/**
	 * Delegates the requests to one or more remote selenium servers. The processes
	 * must be started / stopped separately. The URLs to connect to are specified
	 * with the config 'selenium.addresses'.
	 **/
	
    @Override
    public void configure(Config conf) {
        super.configure(conf);
    }
    public static void main(String[] args) throws Exception {
        RemoteDriverProtocolHartoWarc.main(new RemoteDriverProtocolHartoWarc(), args);
    }

}

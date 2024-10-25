# Selenium Storm Crawler

## Requirements:
* Apache Storm >= 1.0.2
    * https://storm.apache.org/downloads.html
* Web Browsers
    * Selenium Docker (preferred)
        * https://github.com/SeleniumHQ/docker-selenium
* Web Browsers (Alternate):
    * The docker containers are the easiest way to setup and run the browsers in servers. If that does not succeed, the appropriate browsers will have to be compiled and provided as a binary.
    * Google Chromium and/or Firefox binaries
    * Selenium WebDriver for Chrome >= 2.28
        * https://sites.google.com/a/chromium.org/chromedriver/downloads
    * Gecko WebDriver for Firefox >= 0.15.0
        * https://github.com/mozilla/geckodriver/releases

* warcprox
    * Dependencies: yum install libffi-devel openssl-devel (for python 3.5)-> python35-devel
    * pip install warcprox (Python 2.7 or 3.4+)
    * https://github.com/internetarchive/warcprox
  
## Apache Storm instalation
 * currently storm library installed at /usr/local 
  
```sh
# nice blog https://vincenzogulisano.com/2015/07/30/5-minutes-storm-installation-guide-single-node-setup/
# minimal steps below to get it working
# download storm and unzip it
$ cd /usr/local
$ wget http://apache.claz.org/storm/apache-storm-1.2.2/apache-storm-1.2.2.tar.gz
$ gzip -d  apache-storm-1.2.2.tar.gz
$ tar -xf apache-storm-1.2.2.tar
# download zookeeper and unzip it
$ sudo wget http://apache.claz.org/zookeeper/zookeeper-3.4.13/zookeeper-3.4.13.tar.gz
$ gzip -d zookeeper-3.4.13.tar.gz
$ sudo tar   -xf zookeeper-3.4.13.tar
$ sudo chown -R  ludab:users zookeeper-3.4.13
$ cd zookeeper-3.4.13/conf
$ cp zoo_sample.cfg zoo.cfg
# Configure ZooKeeper
# Add the following to zookeeper-3.4.13/conf/zoo.cfg
tickTime=2000
dataDir=/data/web/tmp/zookeeper
clientPort=2181
# Start ZooKeeper
zookeeper-3.4.6/bin/zkServer.sh start
# mkdir /data/web/tmp/zookeeper
# mkdir /data/tmp
```
 * For the standalone storm execution instalation of the library is enough, no need to configure  cluster.

To setup the archiver, please follow the steps below in order:

## Selenium-Docker Browsers

The Dockerfile to create the browser containers is in the `docker-selenium` directory of the source. To build and start the browser container,

```sh
$ cd stormarchiver/docker-selenium

# building the container
$ sudo docker build -t lanlproto/selenium-chrome .

# starting the browser
$ sudo docker run -d -p 4444:4444  --shm-size 8G lanlproto/selenium-chrome
```

## Local Browsers (Binaries)
The amazon servers do not contain the kernel and libraries to run the latest version of browsers. The OS will have to upgraded for the browsers to run locally in these servers. Hence, this option is not viable. The docker containers are the only working setup for now.
## Create directory to store warc files
```
 currently at /data/web/warcs/warcstore
```
## Start warcprox -- this step is not needed, but it is good to check that you can start/ stop warcproxy

### Selenium Docker Browsers
For docker instances, the hostname will have to be the IP address of the host as seen by the container.
```sh
$ cd path/to/warc/storage/location
$ warcprox -b $(sudo docker inspect --format "{{ .NetworkSettings.Gateway }}" $(sudo docker ps -ql)) -p 8080 --certs-dir certs -d warcs -g md5 -v --trace  -s 2000000000 
```
 
### Start warcprox -- Local Browsers (Binaries)

```sh
$ cd path/to/warc/storage/location
$ warcprox -b localhost -p 8080 --certs-dir certs -d warcs -g md5 -v --trace
```


## Configure strom-crawler

* Add the warcprox port and domain name in storm-crawler. The host name of the proxy should be the same as the host name provided to `warcprox` above. i.e, the output of the command:
```sh
$ sudo docker inspect --format "{{ .NetworkSettings.Gateway }}" $(sudo docker ps -ql)
```
    Eg:
      - `http.proxy.host: 172.17.0.1`
      - `http.proxy.port: 8080`

* Add the browser name `chrome` or `firefox` to the property `browser.name`.
* The browser running as a docker container will be listening in an IP address and port for requests from selenium. So, this information will have to be entered in the property `browser.remote.url`. The URL will be of the form `http://<container-ip>:<container-port>/wd/hub`.
The container IP can be obtained by executing the command:
```sh
$ sudo docker inspect --format "{{ .NetworkSettings.IPAddress }}" $(sudo docker ps -ql)
```
The container port is the port number that was used to start the container in the command above (4444).
Eg: `browser.remote.url: "http://172.17.0.2:4444/wd/hub"`

**Optional**: If using a local browser binary instead of the docker instance:

* Update the `browser.binary` property with the path to the binary of the browser.
* For `chrome`, update the `browser.driver` with the path to the downloaded Selenium WebDriver for Chrome.


**Add directory to store warcfiles**:
```
http.proxy.dir: "/data/web/warcs/warcstore"
``` 

** Essential parameters from crawler-conf.yaml**
``` 
  #this to avoid duplicate processing, since if selenium is not finished for long time, url returns to queue 
  topology.message.timeout.secs: 6000
  # to disable robots.txt
  http.skip.robots: true
  # point out to traces config file
  navigationfilters.config.file: "boundary-filters.json"
  # make sure you have autoreconnect 
  mysql.url: "jdbc:mysql://localhost:3306/crawl?autoReconnect=true"
  mysql.table: "urls"
  mysql.user: "cache"
  mysql.password: "plentyPl3nty"
  #mysql.password: "plenty" timetravel password
  mysql.buffer.size: 5
  mysql.min.query.interval: 5000
```
This parameters will be stored in mysqldb and needed for inbox to operate (metadata.transfer -- parameters to transfer to children urls, in our case to redirects.
```
  metadata.persist:
   - warcs
   - event
   - trace
   - discoveryDate
   - filter
  metadata.transfer:
   - event
   - trace
   
```


### Compile and Deploy

With Storm installed, you must first generate an uberjar:

``` sh
$ mvn clean package
```
set  urlfilters.json    "maxDepth": 3 -- it is controls how many redirects allowed, redirects treated as children
```sh
```
create urls table using 
```sh
mysql/tableCreation.script
```
before submitting the topology using the storm command:

``` sh
storm jar target/stormcrawler-0.1.jar gov.lanl.crawler.CrawlTopology -conf crawler-conf.yaml -local 
```

### Compile and Deploy with flux and elastic search -- this is not used
package   jar stormcrawler-0.1.jar 
```
mvn clean package
```
with elasticsearch installed, you must first create index  (works also to empty it) 
```
 ./ES_IndexInit.sh     
```
check
```
curl http://localhost:9200/status/_search?pretty
```
add you crawl seeds to seeds.txt (tab separated) and then inject seeds to elastic search index
```
storm jar target/stormcrawler-0.1.jar  org.apache.storm.flux.Flux --sleep 30000 --local es-injector.flux
```
start crawl
```
storm jar target/stormcrawler-0.1.jar  org.apache.storm.flux.Flux  es-crawler.flux
storm jar target/stormcrawler-0.1.jar  -Djava.io.tmpdir=/data/tmp  org.apache.storm.flux.Flux  -local  crawler.flux -s 10000000000
```
### To start crawler with flux and Mysql --current aproach
Myresearch.institute crawler installed at
``` 
/data/web/stormarchiver
```
and tracer_demo crawler at timetravel.mementoweb.org
``` 
/data/web/tracer_demo/stormarchiver
```
run crawler to read/write from mysql: 
```sh
 nohup storm jar target/stormcrawler-0.1.jar  -Djava.io.tmpdir=/data/tmp  org.apache.storm.flux.Flux    crawler.flux -s 10000000000 > storm.txt &
```
### To stop  crawler and prepare to start 

```sh
 ps aux|grep flux
 kill -9  <pid>
```
check also that no hanging sessions of warcprox, if crawler killed 
```sh
ps aux |grep warcprox
ludab     65245 38.0  0.0 5506560 40728 pts/5   Sl   19:53   0:00 /usr/bin/python2.7 /usr/local/bin/warcprox -b 172.17.0.1 -p 8072 --certs-dir certs -d /data/web/warcs/warcstore8072 -g md5 -v --trace -s 3000000000 --dedup-db-file=/dev/null --stats-db-file=/dev/null
ludab     65322  0.0  0.0 110512  2264 pts/5    S+   19:53   0:00 grep --color=auto warcprox
```

to be on the save side also clean tmp directories - 
to ensure no half cooked files left (sometimes permissions are broke if you delete directories and crawler restarted by different user).
 
```sh 
  check that no files in  /data/web/warcs/warcstore80*
  for the tracer_demo check that no files in the 
  ls -la /data/web/tracer_demo/warcs80*/
  if any files in /data/web/tracer_demo/warcs80*/ delete them
```
### How to load data to db directly to use crawler without  inbox
go to mysql shell with local-infile option
```sh
 
 mysql --local-infile=1 -u root -p
 
 use crawl;
 SET GLOBAL local_infile = 'ON';
 LOAD DATA LOCAL INFILE '/Users/Lyudmila/stormarchiver/10000_DOIs.txt' INTO TABLE urls FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n' (@col1, @col2, @col3, @col4, @col5,@col6) set url = @col1,status='DISCOVERED', nextfetchdate =now(), id=md5(@col1);
 update  urls  set metadata= concat("url.path=",url) ;
 
```
### Troubleshouting Tracer_Demo crawler
mysql -u cache -p
```sh
  use crawl;
  show tables;
  select * from urls\G; 
  also overall picture
  select count(*),status from urls  group by status;
```
  check urls status. 
  
*  Status "FETCHED" means that crawler already worked on url. 
*  "DISCOVERED" means url in queue.
*  "FETCH_ERROR" and "ERROR" - transient and permanent errors. 

  For Tracer_Demo crawler is not going to refetch FETCH_ERROR.
  Currently timeout set to 0.5 hours for trace execution.  
  if you want to take out url from crawl queue:
```sh  
  stop crawler. 
  update urls set status='FETCHED', nextfetchdate='2029-03-27 15:41:12' where url='https://wormbase.org/species/c_elegans/gene/WBGene00006604#0-9g-3';
  or delete from urls where id = '6827b85494cac389559e57b38890cd9f';
  start crawler 
```  
  if you want to bring back url to query for testing (if message already sent it is not going to resent it):
```sh
  update urls set status='DISCOVERED',nextfetchdate='2018-01-21 15:41:22' where url='https://wormbase.org/species/c_elegans/gene/WBGene00006604#0-9g-3';
   
 
```
### Replay trace over live page demo

* install chrome driver, see https://github.com/SeleniumHQ/selenium/wiki/ChromeDriver  or http://chromedriver.chromium.org/downloads
* clone stormarchiver repository   git clone https://bitbucket.org/lanlprototeam/stormarchiver
* java 1.8 required ; adjust JAVA_HOME in tracetest.sh if it is  not working for you
* edit  crawler-conf-tracertest.yaml  : change or comment out . (Yaml - carefull with indentation)    
```sh
     http.proxy.host: proxyout.lanl.gov
     http.proxy.port: 8080
     browser.driver: "/usr/local/bin/chromedriver"
```
start the trace replay
```sh
  ./tracetest.sh https://www.heise.de/  file:///Users/Lyudmila/stormarchiver/bihiyolgbh.json
  
```
 

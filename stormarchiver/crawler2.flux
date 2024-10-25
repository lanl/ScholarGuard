name: "crawler"

includes:
    - resource: true
      file: "/crawler-default.yaml"
      override: false

    - resource: false
      file: "crawler-conf2.yaml"
      override: true

spouts:
   - id: "spout"
     className: "gov.lanl.crawler.SQLSpout"
     parallelism: 1
#    constructorArgs:
#      - ["https://github.com/mementoweb/rfc-extensions/"]
#https://www.usnews.com/education/best-global-universities/rankings","https://figshare.com/articles/Beyond_Throughput_a_4G_LTE_Dataset_with_Channel_and_Context_Metrics/6153497","https://www.google.com/maps/@35.8754067,-106.3296641,15z","https://figshare.com/articles/Exhibition_Film_of_Mamiko_Markham_s_Katagami_research/6210281","https://www.youtube.com/watch?v=YG184V4gCRs", "https://www.slideshare.net/hvdsomp/paul-evan-peters-lecture","https://figshare.com/articles/RobustLinks/5362354", "https://github.com/mementoweb/rfc-extensions/", "http://ws-dl.blogspot.com/2015/12/2015-12-08-evaluating-temporal.html"]
bolts:
  - id: "partitioner"
    className: "com.digitalpebble.stormcrawler.bolt.URLPartitionerBolt"
    parallelism: 1
  - id: "fetcher"
    className: "com.digitalpebble.stormcrawler.bolt.FetcherBolt"
    parallelism: 1
  - id: "sitemap"
    className: "com.digitalpebble.stormcrawler.bolt.SiteMapParserBolt"
    parallelism: 1
  - id: "parse"
    className: "com.digitalpebble.stormcrawler.bolt.JSoupParserBolt"
    parallelism: 1
  - id: "index"
    className: "com.digitalpebble.stormcrawler.indexing.StdOutIndexer"
    parallelism: 1
  - id: "status"
    #className: "com.digitalpebble.stormcrawler.persistence.StdOutStatusUpdater"
    #className: "com.digitalpebble.stormcrawler.persistence.MemoryStatusUpdater"
    className:  "gov.lanl.crawler.StatusUpdaterBolt"
              
    parallelism: 1

streams:
  - from: "spout"
    to: "partitioner"
    grouping:
      type: SHUFFLE

  - from: "partitioner"
    to: "fetcher"
    grouping:
      type: FIELDS
      args: ["key"]

  - from: "fetcher"
    to: "sitemap"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "sitemap"
    to: "parse"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "parse"
    to: "index"
    grouping:
      type: LOCAL_OR_SHUFFLE

  - from: "fetcher"
    to: "status"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "sitemap"
    to: "status"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "parse"
    to: "status"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

  - from: "index"
    to: "status"
    grouping:
      type: FIELDS
      args: ["url"]
      streamId: "status"

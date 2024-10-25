The Capture Inbox (CI) is a server and deployed in the directory `/data/web/capture` (CI directory).

and logs to the ` /data/web/capture/log/wrapper.log ` .
### installation
* clone the repository
* change the my.properties at lib directory with your endpoints  and mysql info

```
    warcbaseurl = https://myresearch.institute/capture/warc/
    capturebaseurl=https://myresearch.institute/capture/
```


### update
The code is part of stormarchiver code base. To upgrade copy  stormcrawler-0.1.jar  to lib directory
### start stop

to start Capture Inbox: go to the CI directory.
```
$./bin/trxapp stop
$./bin/trxapp start
```
### how to post message from file
```
curl --header "Content-Type: application/ld+json" --request POST  --data @miss.json https://myresearch.institute/capture/inbox 
```
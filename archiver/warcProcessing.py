import sys
import os
import time
from datetime import datetime
import random
# import ipwb
# import indexer2
import uuid
import pathlib

# from rdflib import Graph, URIRef, Literal
import json,ast

import pymysql.cursors

import urllib3.request
import ssl

import itertools
import logging

# Logging
LOG_FORMAT = '%(asctime)-15s [%(levelname)s] (%(module)s.%(funcName)s) %(message)s'
logging.basicConfig(filename = '/data/var/logs/archiver.log', level=logging.DEBUG, format=LOG_FORMAT)
# archiverlog = logging.getLogger(__name__)

autoindexPauseDuration = 30 # in seconds

def getWarc (warcUri, http):
  print(warcUri)
  print('----Retrieving----')
  warcReq = doHttpGet(warcUri, http)
  logging.debug(warcUri)
  logging.debug('status: ' + warcReq.status)
  print ('----status----')
  return '200', warcReq.data

def doHttpGet(resourceUri, http):
    logging.info('making request for ' + resourceUri)
    print(resourceUri)
    response = http.request('GET', resourceUri, timeout=.5)
    print (response)
    logging.info(response.status)
    return response.status, response.data

def testForFile(directory, filename):
  print (directory + filename)
  file = pathlib.Path(directory + filename)
  # return str(os.path.exists(directory + filename))
  return file.exists()

def processCaptureEvent(captureEvent, pywbWarcsDir, pywbCdxApi, collectionUri, processedTime, http):
  warcsList = []
  captureObject = captureEvent["object"]
  captureResult = captureEvent["result"]
  warcsExist = False

  obj = {}
  obj["totalItems"] = captureResult["totalItems"]
  obj["type"] = captureResult["type"]
  items = []
  for item in captureResult.get("items"):
    items.append({"type": item["type"], "href": item["href"]})
    warcUri = item["href"].strip()
    logging.debug(warcUri)
    warcUri = warcUri.replace(' ','')
    warcUri = warcUri.replace('.open','')
    warcFilename = warcUri.split('/')[-1].strip()
    warcFilename = warcFilename.replace(' ','')
    logging.debug(pywbWarcsDir + warcFilename)
    # if testForFile(pywbWarcsDir, warcFilename):
    #     warcsExist = True
    #     fname = os.path.join(pywbWarcsDir, warcFilename.rstrip())
    #     if os.path.isfile(fname): # this makes the code more robust
    #       os.remove(fname)

    warcReqStatus, warcBody = doHttpGet(warcUri,http)
    if warcReqStatus != 404:
          saveWarc(warcBody, pywbWarcsDir + warcFilename)
          logging.debug('Retrieved file anyway')
    elif 'MWARC' in warcFilename:
          warcFilename = warcFilename.replace('MWARC','WARC')
          logging.debug('Trying alternate filename ' + warcFilename)
          warcUri = warcUri.replace('MWARC','WARC')
          warcReqStatus, warcBody = doHttpGet(warcUri,http)
          saveWarc(warcBody, pywbWarcsDir + warcFilename)
    warcsList.append(warcFilename)
    logging.debug(warcFilename)
        
  obj["items"] = items

  # if warcsExist == False:
  #   logging.info('Sleeping ' + str(autoindexPauseDuration) + ' seconds to give pywb a chance to autoindex the newly retrieved warc(s)')
  #   time.sleep(autoindexPauseDuration) # wait 45 seconds, maybe pywb autoindex will have happened
  # time.sleep(5) # wait 45 seconds, maybe pywb autoindex will have happened

  result = {}
  result["totalItems"] = captureObject["totalItems"]
  result["type"] = captureObject["type"]
  items = []
  uris = captureObject["items"]
  for uri in uris:
      logging.debug (uri["href"])
      lastMemento= ''
      entry = {}
      for warcFilename in warcsList:
        try:
          logging.debug('Getting memento(s) for ' + warcFilename)
          thisItemMementoDatetime = getMemento(http, warcFilename, pywbCdxApi, uri["href"])
          if thisItemMementoDatetime != '' and thisItemMementoDatetime!=lastMemento:
            memento = collectionUri + thisItemMementoDatetime + '/' + uri["href"]
            rawDatetime = datetime.strptime(thisItemMementoDatetime, '%Y%m%d%H%M%S')
            formattedDatetime = rawDatetime.strftime('%Y-%m-%dT%H:%M:%SZ')

            entry = {"type": [uri["type"]],
               "href": memento, 
               "OriginalResource": uri["href"],
               "mementoDatetime" : formattedDatetime,
               "Memento": memento
            }
            logging.debug ('Memento returned was ' + memento)
            items.append(entry)
          lastMemento = thisItemMementoDatetime
        except:
          pass
    
  result["items"] = items

  return obj, result

def getMemento(http, warcFilename, pywbCdxApi, origResUri):
  mementoDatetime = ''
  payload = {'url':origResUri, 'matchType': 'exact', 'output':'json'}
  requri = pywbCdxApi + '?url=' + origResUri + '&matchType=exact&output=json'
  print (requri)
  logging.debug('Memento request ' + requri)
  response = http.request('GET', pywbCdxApi, payload, timeout=.5)
  # logging.debug (response.data)
  headers = response.headers
  print (headers)
  list_resp = response.data.splitlines()
  print(list_resp)
  for x in list_resp:
      cdxjEntry = json.loads(x)
      if str(cdxjEntry['filename']) == warcFilename:
        mementoDatetime = str(cdxjEntry['timestamp'])
  print(mementoDatetime)
  return mementoDatetime
 
def saveWarc(warcContents, resultsFilename):
  logging.debug(str(warcContents)[0:6])
  if str(warcContents)[0:6]=='b\'WARC':
    logging.debug('Starts with WARC')
  if not os.path.isfile(resultsFilename) and resultsFilename.endswith('.warc') and str(warcContents)[0:6]=='b\'WARC':
    with open(resultsFilename, "wb") as results:
      results.write(warcContents)
    results.close()
  logging.debug('Saved warc')

def indexWarcs(warcUri):
    idx.indexFileAt(warcUri)

def grabAllWarcsForCaptureEvent(captureEvent, pywbWarcsDir, pywbCdxApi, collectionUri, processedTime, http):
  warcsList = []
  captureObject = captureEvent["object"]
  captureResult = captureEvent["result"]

  obj = {}
  obj["totalItems"] = captureResult["totalItems"]
  obj["type"] = captureResult["type"]
  items = []
  for item in captureResult.get("items"):
    items.append({"type": item["type"], "href": item["href"]})
    warcUri = item["href"].strip()
    warcUri = warcUri.replace(' ','')
    warcUri = warcUri.replace('.open','')
    logging.debug(warcUri)
    warcFilename = warcUri.split('/')[-1].strip()
    warcFilename = warcFilename.replace(' ','')
    logging.debug(warcFilename)
    # here is where I want to check if the file already exists before I try to retrieve it
    print ('Getting ' + warcUri)
    warcReqStatus, warcBody = doHttpGet(warcUri,http)
    if warcReqStatus != 404:
       saveWarc(warcBody, pywbWarcsDir + warcFilename)
    elif 'MWARC' in warcFilename:
       warcFilename = warcFilename.replace('MWARC','WARC')
       warcUri = warcUri.replace('MWARC','WARC')
       warcReqStatus, warcBody = doHttpGet(warcUri,http)
       saveWarc(warcBody, pywbWarcsDir + warcFilename)
    warcsList.append(warcFilename)
    logging.debug(pywbWarcsDir + warcFilename)
    # time.sleep(2) # wait 2 seconds
  return warcsList


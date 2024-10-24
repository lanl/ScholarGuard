import uuid
import configparser
import json

import sys
import os
import time
from datetime import datetime

import pymysql.cursors
import urllib3.request
import ssl
import logging

import warcProcessing
import pyldnDb
import eventSerializer

class ArchiveEvent:
    captureEventNotification = {}
    orchestratorMsg = {}
    archiveEventId = None
    captureEventId = None
    processedTime = None
    recipient = None
    eventBaseUrl = None
    sources = {}
    origUrls = []
    warcs = []
    archiveEvent = {}
    captureEvent = {}
    archiveObject = {}
    archiveResult = {}

    def __init__(self, baseUrl):
        # Initialize with new hex uuid archive event id
        event_id = baseUrl + 'arc' + str(uuid.uuid4().hex)
        self.archiveEventId = event_id
        logging.info(event_id)

    def getEventId(self):
        return self.archiveEventId

    def setCaptureEventId(self,capture_event_id):
        self.captureEventId = capture_event_id

    def getCaptureEventId(self):
        return self.captureEventId

    def setRecipient(self,recipient):
        self.recipient = recipient

    def getRecipient(self):
        return self.recipient

    def setCaptureEventNotification(self,captureNotification):
        try:
          self.sources['captureId'] = captureNotification['event']['@id']
          self.sources['trackerId'] = captureNotification['event']['prov:wasInformedBy'][0]['id']
          self.captureEventNotification = captureNotification
        except:
          pass

    def setOrchestratorMsg(self, orchestratorMsg):
        try:
          self.orchestratorMsg = json.loads(orchestratorMsg)
          self.captureEventId = orchestratorMsg['event']['object']['id']
          self.recipient = orchestratorMsg['event']['to']
          self.eventBaseUrl = orchestratorMsg['event']['to']['tracker:eventBaseUrl']
        except:
          pass

    def getCaptureEventNotification(self):
        return self.captureEventNotification

    def setProcessedTime(self,procDatetime):
        self.processedTime = procDatetime

    def getProcessedTime(self):
        return self.processedTime

    def getCaptureId(self):
        return self.sources['captureId']

    def getTrackerId(self):
        return self.sources['trackerId']

    def setArchiveObject(self, archiveObject):
        self.archiveObject = archiveObject

    def getArchiveObject(self):
        return self.archiveObject

    def setArchiveResult(self, archiveResult):
        self.archiveResult = archiveResult

    def getArchiveResult(self):
        return self.archiveResult

def getJsonMsg(resourceUri, http):
    response = http.request('GET', resourceUri, timeout=.5)    
    jsonResponse = json.loads(response.data.decode('utf-8'))
    return jsonResponse

def main():
    # Logging
    LOG_FORMAT = '%(asctime)-15s [%(levelname)s] (%(module)s.%(funcName)s) %(message)s'
    logging.basicConfig(filename = '/data/var/logs/archiver.log', level=logging.DEBUG, format=LOG_FORMAT)

    config = configparser.ConfigParser()
    config.read('archiver.ini')

    pywbWarcsDir = config['PYWB']['WARCS_DIR']
    pywbCdxApi = config['PYWB']['CDXJ_API']
    collectionUri = config['PYWB']['COLLECTION_URI']

    sleepDuration = int(config['ARCHIVER']['PAUSE'])

    dbHost = config['PYLDN_DATABASE']['HOST']
    dbUser = config['PYLDN_DATABASE']['USERID']
    dbPass = config['PYLDN_DATABASE']['PASSWORD']
    dbName = config['PYLDN_DATABASE']['DBNAME']
    dbTable = config['PYLDN_DATABASE']['INBOX_TABLENAME']

    while 1:
        logging.info ('Archiver is waking up...')

        conn = pyldnDb.create_connection(dbHost, dbUser, dbPass, dbName) 
        cursor = conn.cursor()
        http = urllib3.PoolManager()

        dbResults = pyldnDb.getUnprocessedRows(dbTable, conn)
        logging.info(len(dbResults))
        logging.info('First pass, get all WARCs for waiting messages')
        badMessages = []
        noWarcs = []
        for dbResult in dbResults:
            archiveObject = {}
            archiveResult = {}
            try:
                thisRowId = dbResult['rowid']
                logging.info ('Processing rowid ' + str(thisRowId))

                orchestratorMsgBody = dbResult['orchestrator_message']
                logging.debug(orchestratorMsgBody)
                orchestratorMsg = json.loads(orchestratorMsgBody)
                orchestratorActorId = orchestratorMsg['event']['actor']['id']
                eventObjectId = orchestratorMsg['event']['object']['id']
                # logging.info (eventObjectId)

                responseInboxUri = orchestratorMsg['event']['to']
                responseEventBaseUrl = orchestratorMsg['event']['tracker:eventBaseUrl']
                # logging.info(orchestratorActorId + ' reported event id ' + eventObjectId)

                captureMsg = getJsonMsg(eventObjectId, http)
   
                thisArchiveEvent = ArchiveEvent(responseEventBaseUrl)
                thisArchiveEvent.setOrchestratorMsg(orchestratorMsgBody)
                thisArchiveEvent.setCaptureEventNotification(captureMsg)
                captureMsgBody = json.dumps(captureMsg)
                # logging.debug(captureMsgBody)
                thisArchiveEvent.setRecipient(responseInboxUri)

                processedTime = datetime.now()
                # logging.debug(processedTime)
                thisArchiveEvent.setProcessedTime(processedTime)
                warcList = []
                warcList = warcProcessing.grabAllWarcsForCaptureEvent(thisArchiveEvent.getCaptureEventNotification()['event'], pywbWarcsDir, pywbCdxApi, collectionUri, processedTime, http)
                if len(warcList)==0 or len(captureMsg) ==0:
                  noWarcs.append(thisRowId)
                  logging.debug('No warcs: ' + str(thisRowId))
                if len(captureMsg) == 0:
                  badMessages.append(thisRowId)
                  logging.debug('Missing capture msg: ' + str(thisRowId))
                logging.debug(warcList)
            except Exception as exc:
                logging.debug (exc)

        # time.sleep(30)
        dbResults = pyldnDb.getUnprocessedRows(dbTable, conn)
        logging.info('Second pass, process waiting capture messages')
        for dbResult in dbResults:
            archiveObject = {}
            archiveResult = {}
            try:
             thisRowId = dbResult['rowid']
             if not thisRowId in badMessages and not thisRowId in noWarcs:
                logging.info ('Processing rowid ' + str(thisRowId))
                print(thisRowId)

                orchestratorMsgBody = dbResult['orchestrator_message']
                logging.debug(orchestratorMsgBody)
                orchestratorMsg = json.loads(orchestratorMsgBody)
                orchestratorActorId = orchestratorMsg['event']['actor']['id']
                eventObjectId = orchestratorMsg['event']['object']['id']
                logging.info (eventObjectId)

                responseInboxUri = orchestratorMsg['event']['to']
                responseEventBaseUrl = orchestratorMsg['event']['tracker:eventBaseUrl']
                logging.info(orchestratorActorId + ' reported event id ' + eventObjectId)

                captureMsg = getJsonMsg(eventObjectId, http)

                thisArchiveEvent = ArchiveEvent(responseEventBaseUrl)
                thisArchiveEvent.setOrchestratorMsg(orchestratorMsgBody)
                thisArchiveEvent.setCaptureEventNotification(captureMsg)
                captureMsgBody = json.dumps(captureMsg)
                logging.debug(captureMsgBody)
                thisArchiveEvent.setRecipient(responseInboxUri)

                processedTime = datetime.now()
                logging.debug(processedTime)
                thisArchiveEvent.setProcessedTime(processedTime)

                archiveObject, archiveResult = warcProcessing.processCaptureEvent(thisArchiveEvent.getCaptureEventNotification()['event'], pywbWarcsDir, pywbCdxApi, collectionUri, processedTime, http)
                thisArchiveEvent.setArchiveObject(archiveObject)
                thisArchiveEvent.setArchiveResult(archiveResult)
                archiveNotificationMsg = eventSerializer.make_as2_payload(thisArchiveEvent, config, "https://myresearch.institute", "archiver", "http://www.w3.org/ns/prov#")

                if len(archiveResult["items"])>0:
                    # an empty result.items block may indicate that mementos were not yet available via cdxj api
                    # in that case, no notification archiving notification will be generated and the capture 
                    # notification will remain unprocessed so another attempt can be made later
                    postResults = http.urlopen('POST', responseInboxUri, headers={'Content-Type': 'application/ld+json'}, body=json.dumps(archiveNotificationMsg))
                    logging.debug (archiveNotificationMsg)
                    logging.debug (postResults.status)
                    if postResults.status==201 or postResults.status==202:
                      pyldnDb.updateRowEntry(conn, dbTable, thisRowId, thisArchiveEvent, captureMsgBody, archiveNotificationMsg)
             elif thisRowId in noWarcs:
                logging.debug(str(thisRowId) + ' had no warcs')
                note = 'no warcs'
                pyldnDb.updateBadRowEntry(conn, dbTable, thisRowId, captureMsgBody, note)
             elif thisRowId in badMessages:
                note = 'no capture message'
                logging.debug(str(thisRowId) + ' had no capture message')
                # pyldnDb.updateBadRowEntry(conn, dbTable, thisRowId, '', note)
            except Exception as exc:
                logging.debug (exc)
      
        conn.close()

        time.sleep(sleepDuration)

if __name__ == "__main__":
    main()


import sys
import os

import pymysql.cursors

import itertools
import logging

import json

# Logging
LOG_FORMAT = '%(asctime)-15s [%(levelname)s] (%(module)s.%(funcName)s) %(message)s'
logging.basicConfig(filename = '/data/var/logs/archiver.log', level=logging.DEBUG, format=LOG_FORMAT)
# archiverlog = logging.getLogger(__name__)

def create_connection(host, user, password, db):
    """ create a database connection to the SQLite database
        specified by the db_file
    :param db_file: database file
    :return: Connection object or None
    """
    try:
      dbConn = pymysql.connect(host=host, user=user, password=password, db = db, charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor)
      return dbConn
    except pymysql.Error as e:
        logging.debug(e)
 
    return None

def getUnprocessedRows(tableName, conn):
    cursor = conn.cursor()
    cursor.execute("SELECT rowid, orchestrator_message FROM " + tableName + " where date_processed is null or date_processed = '' ORDER BY rowid DESC;")
    dbResults = cursor.fetchall()
    return dbResults

def getOrchestratorMsg(tableName, rowid):
    orchestratorMsg = {}
    try:
      cursor = conn.cursor()
      dbResults = cursor.execute("SELECT orchestrator_message FROM " + tableName + " where rowid=" + rowid + ";")
      row = dbRows.fetchOne()
      orchestratorMsg = row['orchestrator_message']
    except pymysql.Error as e:
        logging.debug(e)
    return orchestratorMsg
    
def updateRowEntry(conn, tableName, thisRowId, archiveEvent, captureMsg, notificationMsg):
    try:
      processedAt = archiveEvent.getProcessedTime()
      captureId = archiveEvent.getCaptureId()
      trackerId = archiveEvent.getTrackerId()
      archiverId = archiveEvent.getEventId()

      updateCursor = conn.cursor()
      updateStr = 'UPDATE ' + tableName + ' SET date_processed="' + str(processedAt) + '" WHERE ROWID=' + str(thisRowId) 
      updateCursor.execute(updateStr)
      updateStr = 'UPDATE ' + tableName + ' SET captureid="' + str(captureId) + '" WHERE ROWID=' + str(thisRowId)
      updateCursor.execute(updateStr)
      updateStr = 'UPDATE ' + tableName + ' SET trackerid="' + str(trackerId) + '" WHERE ROWID=' + str(thisRowId)
      updateCursor.execute(updateStr)
      updateStr = 'UPDATE ' + tableName + ' SET archiverid="' + archiverId + '" WHERE ROWID=' + str(thisRowId)
      updateCursor.execute(updateStr)
      conn.commit()

      updateStr = ('UPDATE ' + tableName + ' SET capture_message = QUOTE("%s") WHERE ROWID = (%s)')
      updateCursor.execute(updateStr, (captureMsg, str(thisRowId)))
      conn.commit() 
      updateStr = ('UPDATE ' + tableName + ' SET archive_message = QUOTE("%s") WHERE ROWID = (%s)')
      updateCursor.execute(updateStr, (json.dumps(notificationMsg), str(thisRowId)))

      conn.commit() 
      logging.info("archiver inbox row was updated")
    except Exception as exc:
      logging.debug (exc)

def updateBadRowEntry(conn, tableName, thisRowId, captureMsgBody, note):
    try:

      updateCursor = conn.cursor()
      updateStr = 'UPDATE ' + tableName + ' SET date_processed="' + note + '" WHERE ROWID=' + str(thisRowId)
      updateCursor.execute(updateStr)

      updateStr = ('UPDATE ' + tableName + ' SET capture_message = QUOTE("%s") WHERE ROWID = (%s)')
      updateCursor.execute(updateStr, (captureMsgBody, str(thisRowId)))
      conn.commit()

    except Exception as exc:
      logging.debug (exc)



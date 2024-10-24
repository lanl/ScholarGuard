# -*- coding: utf-8 -*-

from flask import request, \
    Blueprint, make_response, Response
from artifact_tracker import tracker_app
from artifact_tracker.utils.as2_to_user import queue_tasks
from rdflib import Graph, URIRef, RDF, Namespace
from artifact_tracker.store.message import InboxMessage
from datetime import datetime


ldn_inbox = Blueprint("ldn_inbox", __name__,
                      template_folder="templates")
LOG = tracker_app.log

INBOX_URL = "/tracker/inbox/"
LINK_HEADER = '<http://www.w3.org/ns/ldp#Resource>; rel="type",' + \
              '<http://www.w3.org/ns/ldp#RDFSource>; rel="type",' + \
              '<http://www.w3.org/ns/ldp#Container>; rel="type",' + \
              '<http://www.w3.org/ns/ldp#BasicContainer>; rel="type"'
# Accepted content types
ACCEPTED_TYPES = ['application/ld+json',
                  'application/ld+json; '
                  'profile="http://www.w3.org/ns/activitystreams',
                  'json-ld']

# Graph of the local inbox
ldp_url = URIRef("http://www.w3.org/ns/ldp#")
ldp = Namespace(ldp_url)

inbox_graph = Graph()
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['Resource']))
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['RDFSource']))
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['Container']))
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['BasicContainer']))
inbox_graph.bind('ldp', ldp)


@ldn_inbox.route("/tracker/inbox/", methods=["HEAD", "OPTIONS"])
@ldn_inbox.route("/tracker/inbox/<notification_id>/",
                 methods=["HEAD", "OPTIONS"])
def head_inbox(notification_id=None):
    """
    Responds with the link, allow-post and allow headers
    as per LDN spec.
    :param notification_id: The optional activity id. Not supported yet.
    :return: The Flask response.
    """
    resp: Response = make_response()
    resp.headers["Allow"] = "GET, HEAD, OPTIONS, POST"
    resp.headers["Link"] = LINK_HEADER

    resp.headers["Accept-Post"] = "application/ld+json"
    return resp


@ldn_inbox.route("/tracker/inbox/", methods=["GET"])
@ldn_inbox.route("/tracker/inbox/<notification_id>/", methods=["GET"])
def get_inbox(notification_id=None):
    """
    Returns basic LDN body/headers as specified in the specs.
    No support for activity id yet.
    :param notification_id: the activity (by id) to display.
    :return: flask response
    """
    accept_hdr = request.headers.get("Accept")
    if not accept_hdr or \
            accept_hdr == '*/*' or \
            'text/html' in accept_hdr:
        resp = make_response(
            inbox_graph.serialize(format='application/ld+json'))
        resp.headers['Content-Type'] = 'application/ld+json'
    elif request.headers['Accept'] in ACCEPTED_TYPES:
        resp = make_response(
            inbox_graph.serialize(format=request.headers['Accept']))
        resp.headers['Content-Type'] = request.headers['Accept']
    else:
        return 'Requested format unavailable', 415

    resp.headers['Allow'] = "GET, HEAD, OPTIONS, POST"
    resp.headers['Link'] = LINK_HEADER
    resp.headers['Accept-Post'] = 'application/ld+json, text/turtle'

    if bool(notification_id):
        # TODO: get and show notification from db
        pass

    return resp


@ldn_inbox.route("/tracker/inbox/", methods=["POST"])
def post_inbox():
    """
    The POST endpoint for the LDN inbox. Can only process
    ActivityStream2 payload in JSON-LD. Will return error codes
    for all other input data.

    For valid AS2 payload, a hash digest of the payload is computed
    and checked against the db for duplicates of the same activity
    before saving to the db. If a duplicate is found, HTTP 202 is
    returned as per the LDN spec, else, HTTP 201 is returned after
    saving the activity to the db. The activity is stored as is in
    the JSON format using the JSON field type in the db.
    :return: Flask response object.
    """
    content_type = [s for s in ACCEPTED_TYPES
                    if s in request.headers.get("Content-Type", "")]
    LOG.debug("Ct: %s" % content_type)
    LOG.debug("rd: %s" % request.data)
    if not content_type:
        return 'Content type not accepted', 500
    if not request.data:
        return 'Received empty payload', 500

    resp = make_response()
    payload = request.json

    event_users = payload.get("event", {}).get("object", {})\
        .get("describes", [])
    if not event_users:
        LOG.debug("No users. exiting")
        return "Cannot process payload. No users.", 500

    # TODO: Store message for received message or process audit
    inbox_msg = InboxMessage()
    inbox_msg.message = request.data
    inbox_msg.created_at = datetime.now()
    inbox_msg.message_type = "in"
    tracker_app.db.session.add(inbox_msg)
    tracker_app.db.session.commit()

    # Queue tasks in the background using celery
    proccessed_no_errors = queue_tasks(payload)
    if not proccessed_no_errors:
        return "Could not process payload", 500

    ldn_url = INBOX_URL
    resp.headers['Location'] = ldn_url

    return resp, 201

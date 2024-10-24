# -*- coding: utf-8 -*-

"""
Linked Data Notification Inbox
From pyldn (https://github.com/albertmeronyo/pyldn)
"""

from flask import request, \
    Blueprint, make_response, Response
from researcher_pod import pod
from researcher_pod.store.es import activity
from researcher_pod.store.user import Portal
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod.store.tracker import TrackerState
from researcher_pod.pod.utils import post_to_ldn_inbox, dereference_as2
from rdflib import Graph, URIRef, RDF, Namespace
from urllib.parse import urlparse
from datetime import datetime

ldn_inbox = Blueprint("ldn_inbox", __name__,
                      template_folder="templates")
app = pod.app
LOG = pod.log

# Accepted content types
ACCEPTED_TYPES = ['application/ld+json',
                  'text/turtle',
                  'application/ld+json; '
                  'profile="http://www.w3.org/ns/activitystreams',
                  'turtle',
                  'json-ld']
# TODO: make this url absolute
INBOX_URL = "/tracker/inbox/"
LINK_HEADER = '<http://www.w3.org/ns/ldp#Resource>; rel="type",' + \
              '<http://www.w3.org/ns/ldp#RDFSource>; rel="type",' + \
              '<http://www.w3.org/ns/ldp#Container>; rel="type",' + \
              '<http://www.w3.org/ns/ldp#BasicContainer>; rel="type"'

# Graph of the local inbox
ldp_url = URIRef("http://www.w3.org/ns/ldp#")
ldp = Namespace(ldp_url)

inbox_graph = Graph()
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['Resource']))
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['RDFSource']))
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['Container']))
inbox_graph.add((URIRef(INBOX_URL), RDF.type, ldp['BasicContainer']))
inbox_graph.bind('ldp', ldp)


@ldn_inbox.route("/orchestrator/capture/inbox/", methods=["HEAD", "OPTIONS"])
@ldn_inbox.route("/orchestrator/tracker/inbox/", methods=["HEAD", "OPTIONS"])
@ldn_inbox.route("/orchestrator/archiver/inbox/", methods=["HEAD", "OPTIONS"])
@ldn_inbox.route("/orchestrator/archiver/inbox/<notification_id>/",
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


@ldn_inbox.route("/orchestrator/tracker/inbox/", methods=["GET"])
@ldn_inbox.route("/orchestrator/capture/inbox/", methods=["GET"])
@ldn_inbox.route("/orchestrator/archiver/inbox/", methods=["GET"])
@ldn_inbox.route("/orchestrator/archiver/inbox/<notification_id>/",
                 methods=["GET"])
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


def get_provenance_tracker(url):
    """
    TODO: This is a hacky way of handling tracker_name, as well
    as the one above. This parses the provenance github link
    and picks out the tracker name.

    Q: The question is how do we handle hypothes.is -> hypothesis
    and make it generic for every tracker.

    A: When posting to the inbox we require a tracker name as part
    of the HTTP POST request.

    :param url: (str) the url from which to find the tracker
    :return: (str) the tracker name
    """
    tracker_path = urlparse(url).path
    tracker_name = tracker_path.rsplit("/", 1)[1]
    tracker_name = tracker_name.replace(".py", "")
    return tracker_name


def get_event_uuid(event_id):
    """
    Helper function to parse the uuid created from AS2
    event id.

    :return: (str) event uuid
    """
    es_id = event_id.rsplit("/", 1)[1]
    return es_id


def is_duplicate_activity(object_id,
                          event_published,
                          tracker_name: str=""):
    """
    Checks if the activity with a given digest already exists
    in the db. This is a sanity check to avoid storing duplicate
    activities in the db in case the tracker fails to check for
    them.

    :param tracker_name:
    :return: (bool)
    """
    if tracker_name in ["publons"]:
        year = datetime.strptime(event_published,
                                 "%Y-%m-%dT%H:%M:%SZ").strftime("%Y")

        return activity.exists_in_year(es=pod.es,
                                       object_id=object_id,
                                       year_published=year)
    else:
        return activity.exists(es=pod.es,
                               object_id=object_id,
                               event_published=event_published)


def is_duplicate_informed(tracker_id, event_type):
    """
    Check if an Archiver or capture
    """
    return activity.event_exists(es=pod.es,
                                 tracker_id=tracker_id,
                                 event_type=event_type)


def save_activity_to_db(user_id, payload, tracker_name, event_id):
    """
    Saves the activity to the db.
    :param payload: (dict) the as2 payload.
    :param tracker_name: (str) the tracker name
    :param digest: (str) the payload hash digest
    :return: (bool)
    """
    if not user_id:
        return False
    return activity.index(
        es=pod.es,
        user_id=user_id,
        tracker_name=tracker_name,
        event_id=event_id,
        activity=payload)


def save_external_activity(payload, tracker_parent_id, event_id):
    """
    Save archiver or capture as2 to elasticsearch
    """
    return activity.index_external(
        es=pod.es,
        event_id=event_id,
        tracker_parent_id=tracker_parent_id,
        activity=payload
    )


def update_tracker_state(
        user_id: str,
        portal_name: str,
        prov_generated_at: str,
        last_token: str=None):
    with pod.app.app_context():
        state = TrackerState.query\
            .filter(Portal.id == TrackerState.portal_id)\
            .filter(PortalConfig.id == Portal.portal_config_id)\
            .filter(PortalConfig.portal_name == portal_name)\
            .filter(Portal.user_id == user_id)\
            .first()

        if not state:
            state = TrackerState()
            portal = pod.db.session.query(
                Portal, PortalConfig)\
                .filter(PortalConfig.id == Portal.portal_config_id)\
                .filter(PortalConfig.portal_name == portal_name)\
                .filter(Portal.user_id == user_id)\
                .first()
            if not portal:
                # Throw error
                return
            portal, _ = portal
            state.portal_id = portal.id
        state.tracker_state = last_token if not last_token\
            else last_token.encode('utf-8')
        state.last_updated = datetime.strptime(
                                prov_generated_at,
                                "%Y-%m-%dT%H:%M:%SZ")
        state.last_status_code = None
        state.update_count = state.update_count + 1 \
            if bool(state.update_count) \
            else 1

        pod.db.session.add(state)
        pod.db.session.commit()


def send_to_component(component_name: str, object_id: str):
    """
    Send received tracker as2 message to capture component
    """
    from researcher_pod import app
    config = app.config
    component_inbox = config.get(
        "COMPONENT_INBOX_ENDPOINT").format(component_name)
    to_inbox = config.get(
        "ORCHESTRATOR_INBOX_ENDPOINT").format(component_name)
    event_base_url = config.get(
        "ORCHESTRATOR_EVENT_BASE_URL").format(component_name)
    actor_id = "{}://{}/".format(
        config.get("PREFERRED_URL_SCHEME"),
        config.get("SERVER_NAME"))
    org_name = config.get("ORGANIZATION_FULL_NAME")
    as2_msg = dereference_as2(
        to_inbox_url=to_inbox,
        event_base_url=event_base_url,
        actor_id=actor_id,
        object_id=object_id,
        organization_full_name=org_name)
    post_to_ldn_inbox(message=as2_msg, inbox_url=component_inbox)


def get_informed_by_tracker_id(prov_informed_by):
    """
    Helper function to get initial tracker_id
    """
    for inf_by in prov_informed_by:
        if inf_by.get("type") == "tracker:Tracker":
            return get_event_uuid(inf_by.get("id"))


@ldn_inbox.route("/orchestrator/archiver/inbox/", methods=["POST"])
@ldn_inbox.route("/orchestrator/capture/inbox/", methods=["POST"])
@ldn_inbox.route("/orchestrator/tracker/inbox/", methods=["POST"])
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
                    if s in request.headers['Content-Type']]
    LOG.debug("Ct: %s" % content_type)
    LOG.debug("rd: %s" % request.data)
    if not content_type:
        return 'Content type not accepted', 500
    if not request.data:
        return 'Received empty payload', 500

    resp = make_response()

    payload = request.json

    try:
        object_id = payload.get("event", {}).get("object", {})\
            .get("items")[0].get("href")
    except Exception:
        LOG.debug("No object items")
        return "Cannot process payload. No items.", 500

    event_type = payload.get("event", {}).get("type")
    event_id = payload.get("event", {}).get("@id")
    event_published = payload.get("event", {}).get("published")
    event_uuid = get_event_uuid(event_id)

    if "tracker:Tracker" in event_type:
        tracker = get_provenance_tracker(payload.get(
            "activity", {}).get("prov:used")[0]["@id"])
        activity = is_duplicate_activity(object_id, event_published, tracker)

        user_id = payload.get("event", {}).get("actor", {}).get("id")
        portal: Portal = Portal.query.filter(
                Portal.user_id == user_id,
                PortalConfig.portal_name == tracker,
                PortalConfig.id == Portal.portal_config_id
            ).first()
        if not portal:
            LOG.debug("portal for user not found. not saving tracker entry.")
            return "portal not found", 500

        prov_generated_at = payload.get("event", {}).get(
            "prov:generatedAtTime")
        last_token = payload.get("activity", {}).get("tracker:lastToken")
        update_tracker_state(
            user_id=user_id,
            portal_name=tracker,
            prov_generated_at=prov_generated_at,
            last_token=last_token)

        if activity.get("hits", {}).get("total") > 0:
            LOG.debug("Duplicate activity found. Not saving to db.")
            resp.headers['Location'] = event_id
            return resp, 202

        save_activity_to_db(user_id, payload, tracker, event_uuid)
        send_to_component("capture", event_id)
    elif "tracker:Capture" in event_type:
        prov_informed_by = payload.get("event", {}).get("prov:wasInformedBy")
        tracker_id = get_informed_by_tracker_id(prov_informed_by)

        activity = is_duplicate_informed(tracker_id, "tracker:Capture")
        if activity.get("hits", {}).get("total") > 0:
            LOG.debug("Duplicate activity found. Not saving to db.")
            resp.headers['Location'] = event_id
            return resp, 202

        save_external_activity(payload, tracker_id, event_uuid)
        send_to_component("archiver", event_id)
    elif "tracker:Archiver" in event_type:
        prov_informed_by = payload.get("event", {}).get("prov:wasInformedBy")
        tracker_id = get_informed_by_tracker_id(prov_informed_by)

        activity = is_duplicate_informed(tracker_id, "tracker:Archiver")
        if activity.get("hits", {}).get("total") > 0:
            LOG.debug("Duplicate activity found. Not saving to db.")
            resp.headers['Location'] = event_id
            return resp, 202

        save_external_activity(payload, tracker_id, event_uuid)
    else:
        return "Cannot process payload.", 500

    # TODO: build ldn_url from db insert id
    # ldn_url = INBOX_URL + db_insert_id
    ldn_url = INBOX_URL
    inbox_graph.add((URIRef(INBOX_URL),
                     ldp['contains'],
                     URIRef(ldn_url)))
    resp.headers['Location'] = ldn_url

    return resp, 201

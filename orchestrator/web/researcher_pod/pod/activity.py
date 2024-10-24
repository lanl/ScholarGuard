# -*- coding: utf-8 -*-
from flask import Blueprint, render_template, jsonify, request, make_response
from flask_security import current_user
from researcher_pod.store.es import activity
from researcher_pod.store.user import User, Portal
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod.ldn.inbox import send_to_component
from researcher_pod import pod
from datetime import datetime
import json


tracker_page = Blueprint("tracker_page", __name__,
                         template_folder="templates")
LOG = pod.log


@tracker_page.route("/event/<path:event_id>/", methods=["GET"])
def on_event(event_id):
    """
    Tracker route for retrieving HTML or JSON response
    of tracker event.

    :return: (dict) AS2 for id
    """
    try:
        payload = activity.get(es=pod.es, payload_hash=event_id)
        parent_id = payload.get("_source", {}).get("tracker_parent_id")
        if parent_id:
            # get all events associated with parent tracker_id
            payload = activity.get_pipeline_events(
                es=pod.es, event_id=parent_id)
        else:
            # get all events associated with tracker_id
            payload = activity.get_pipeline_events(
                es=pod.es, event_id=event_id)
    except Exception:
        LOG.debug("Failed to find tracker with id: {}".format(event_id))
        return render_template("404.html")

    events = payload.get("hits", {}).get("hits")

    if not events:
        return render_template("404.html")

    tracker_as2 = {}
    capture_as2 = {}
    archiver_as2 = {}
    as2_strs = {"tracker": "", "capture": "", "archiver": ""}

    for event in events:
        as2_msg = event.get("_source", {}).get("activity", {})
        event_type = event.get(
            "_source", {}).get("activity", {}).get("event", {}).get("type")
        if "tracker:Tracker" in event_type:
            tracker_as2 = as2_msg
            as2_strs["tracker"] = json.dumps(as2_msg, indent=2)
            user_id = event.get("_source", {}).get("user_id")
            tracker_name: str = event.get("_source", {}).get("tracker_name")
            event_type = [
                item for item in event_type
                if not item.startswith("tracker:")][0].title()
            artifact_type = as2_msg.get(
                "event", {}).get("object", {}).get("items", [])[0].get("type")
            artifact_type = [
                item for item in artifact_type
                if item.startswith("schema:")
            ][0].split("schema:")[1].title()
            event_info = {
                "user_id": user_id,
                "tracker_name": tracker_name,
                "event_id": event_id,
                "event_type": event_type,
                "artifact_type": artifact_type
            }
        elif "tracker:Capture" in event_type:
            capture_as2 = as2_msg
            as2_strs["capture"] = json.dumps(as2_msg, indent=2)
        elif "tracker:Archiver" in event_type:
            as2_strs["archiver"] = json.dumps(as2_msg, indent=2)
            archiver_as2 = as2_msg

    portal_url = pod.config.get("{}_PORTAL_URL".format(tracker_name.upper()))
    if portal_url:
        event_info["portal_url"] = portal_url
    event_info["capture_id"] = ""
    event_info["archiver_id"] = ""

    if capture_as2:
        event_info["capture_id"] = format_as2_event_id(
            capture_as2.get("event", {}).get("@id"))
    if archiver_as2:
        event_info["archiver_id"] = format_as2_event_id(
            archiver_as2.get("event", {}).get("@id"))

    roles = [roles.name for roles in current_user.roles]
    is_admin = False
    if "owner" in roles or "admin" in roles:
        is_admin = True

    user: User = User.query.filter_by(id=user_id).first()
    user, portal = pod.db.session.query(User, Portal)\
        .filter(User.id == user_id)\
        .filter(Portal.user_id == user_id)\
        .filter(Portal.portal_config_id == PortalConfig.id)\
        .filter(PortalConfig.portal_name == tracker_name.lower()).first()
    resp = make_response(render_template("tracker.html",
                                         event_info=event_info,
                                         as2_strs=as2_strs,
                                         tracker_as2=tracker_as2,
                                         capture_as2=capture_as2,
                                         archiver_as2=archiver_as2,
                                         is_admin=is_admin,
                                         portal=portal,
                                         user=user))
    resp.headers['Link'] = '<{}> ; rel="author"'.format(user.id)

    for source in tracker_as2.get("event", {}).get(
            "object", {}).get("items", []):
        resp.headers['Link'] += ', <{}> ; rel="cite-as"'.format(
            source.get("href").strip())
        break
    return resp


@tracker_page.route("/event/<path:event_id>/capture/", methods=["GET"])
def trigger_capture(event_id):
    """
    A manual trigger sent via HTTP request to send an event
    to the capture process.
    Respond in json
    """
    roles = [roles.name for roles in current_user.roles]
    if "owner" not in roles and "admin" not in roles:
        return jsonify({
            "error": "unauthorized",
            "success": False
        }), 401

    try:
        payload = activity.get(es=pod.es, payload_hash=event_id)
        parent_id = payload.get("_source", {}).get("tracker_parent_id")
        if parent_id:
            # get all events associated with parent tracker_id
            payload = activity.get_pipeline_events(
                es=pod.es, event_id=parent_id)
        else:
            # get all events associated with tracker_id
            payload = activity.get_pipeline_events(
                es=pod.es, event_id=event_id)
    except Exception:
        LOG.debug("Failed to find tracker with id: {}".format(event_id))
        return jsonify({
            "error": "no tracker record",
            "success": False
        }), 404

    events = payload.get("hits", {}).get("hits")

    if not events:
        return jsonify({
            "error": "no tracker record",
            "success": False
        }), 404

    tracker_as2 = {}
    for event in events:
        as2_msg = event.get("_source", {}).get("activity", {})
        event_type = event.get(
            "_source", {}).get("activity", {}).get("event", {}).get("type")
        if "tracker:Tracker" in event_type:
            tracker_as2 = as2_msg
            break
    object_id = tracker_as2.get("event", {}).get("@id")
    send_to_component("capture", object_id)

    return jsonify({
        "error": "",
        "success": True
    })


def format_as2_event_id(event_id: str):
    """
    Helper to extract event_id from as2 message
    """
    id_ = event_id.rsplit("/", 1)[1]
    return id_


def format_date_argument(date):
    """
    Parse url parameter `date`.
    Return None if incorrect format.
    """
    if not date:
        return None
    try:
        return datetime.strptime(date, "%Y%m%d").strftime("%Y%m%d")
    except Exception:
        return None


def pick_resp(event_id: str=None,
              event_type: str=None,
              single_entry: bool=True,
              args: dict={}):
    """
    Helper to return the correct response based on event_id, event_type,
    and list or single entry responses.
    If list requested process arguments.
    """
    if single_entry:
        payload = activity.get_activity_by_id(
            es=pod.es,
            event_id=event_id,
            event_type=event_type
        )
    else:
        size = args.get("count", 200)
        date = args.get("date")
        if isinstance(date, str):
            if not format_date_argument(date):
                return jsonify([])
        payload = activity.get_activity_by_id(
            es=pod.es,
            event_type=event_type,
            size=size,
            date=date
        )

    events = payload.get("hits", {}).get("hits", [])
    if single_entry:
        if not events:
            return jsonify({}), 404

        event = events[0].get("_source", {}).get("activity")
        if not event:
            return jsonify({}), 404

        return jsonify(event)
    else:
        if not events:
            return jsonify(events)
        events = [event.get("_source", {}).get("activity") for event in events]
        return jsonify(events)


# Single event responses
@tracker_page.route("/tracker/event/<path:event_id>/", methods=["GET"])
def on_tracker(event_id):
    return pick_resp(event_id=event_id, event_type="tracker:Tracker")


@tracker_page.route("/capture/event/<path:event_id>/", methods=["GET"])
def on_capture(event_id):
    return pick_resp(event_id=event_id, event_type="tracker:Capture")


@tracker_page.route("/archiver/event/<path:event_id>/", methods=["GET"])
def on_archiver(event_id):
    return pick_resp(event_id=event_id, event_type="tracker:Archiver")


# List event responses
@tracker_page.route("/tracker/event/", methods=["GET"])
def on_tracker_list():
    return pick_resp(event_type="tracker:Tracker",
                     single_entry=False,
                     args=request.args)


@tracker_page.route("/capture/event/", methods=["GET"])
def on_capture_list():
    return pick_resp(event_type="tracker:Capture",
                     single_entry=False,
                     args=request.args)


@tracker_page.route("/archiver/event/", methods=["GET"])
def on_archiver_list():
    return pick_resp(event_type="tracker:Archiver",
                     single_entry=False,
                     args=request.args)

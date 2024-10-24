# -*- coding: utf-8 -*-

from flask import (Blueprint, render_template,
                   request, send_from_directory,
                   jsonify)
from flask_security import current_user
from researcher_pod.store.es.activity import search
from researcher_pod.store.user import User, Role, roles_users
from researcher_pod import pod
from datetime import datetime
import elasticsearch

index_page = Blueprint("index_page", __name__,
                       template_folder="templates")
LOG = pod.log


@index_page.route('/tracker/static/<path:path>')
@index_page.route('/static/<path:path>')
def send_static(path):
    return send_from_directory('static', path)


@index_page.app_errorhandler(404)
def example404(e):
    return render_template("404.html"), 404


@index_page.route("/")
@index_page.route("/all/researchers/")
@index_page.route("/all/")
def on_index():
    """
    Default route:
    /all/researchers == /all == default
    """
    return pick_resp(route="researchers",
                     args=request.args)


@index_page.route("/all/portals/")
def get_org_portals():
    return pick_resp(route="portals",
                     args=request.args)


@index_page.route("/all/artifacts/")
def get_org_artifacts():
    return pick_resp(route="artifacts",
                     args=request.args)


@index_page.route("/all/activities/")
def get_org_activities():
    return pick_resp(route="activities",
                     args=request.args)


@index_page.route("/about/")
def get_about():
    return render_template("about.html")


def get_view(args):
    view = args.get("view")
    if not view:
        return "grid"
    elif view in ["grid", "list", "json-ld"]:
        return view
    else:
        return ""


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


def get_ui_elements(event, user_cache: dict, view_type: str):
    """
    Helper to return a formated dictionary of needed ui variables
    for a given event.
    """
    tracker_name = event.get("_source", {}).get("tracker_name")
    event_id = event.get("_id")
    artifact_type = [
        item for item in event.get("_source", {})
        .get("activity", {}).get("event", {})
        .get("object", {}).get("items", [])[0].get("type")
        if item.startswith("schema:")
    ][0].split("schema:")[1].title()
    event = event.get("_source", {}).get("activity", {}).get("event", {})
    published = event.get("published")
    if view_type == "list":
        published = published.replace("T", "<br>")
        published = published.replace("Z", "")
    event_type = [item for item in event.get("type")
                  if not item.startswith("tracker:")][0].title()
    user_id = event.get("actor", {}).get("id")
    if user_id not in user_cache:
        user: User = User.query.filter_by(id=user_id).first()
        user_cache.setdefault(user_id, {})
        user_cache[user_id].setdefault("full_name", user.full_name)
        user_cache[user_id].setdefault("profile_image", user.profile_image)

    return {
        "event_id": event_id,
        "tracker_name": tracker_name,
        "event_type": event_type,
        "artifact_type": artifact_type,
        "published": published,
        "user": user_cache.get(user_id, {})
    }


def format_user_id(user_id: str):
    """
    Helper to extract event_id from as2 message
    """
    id_ = user_id.rsplit("/", 1)[1]
    return id_


def get_tracked_users() -> list:
    """
    Return a list of tracked users
    """
    try:
        users = pod.db.session.query(User).\
            join(roles_users).\
            join(Role).\
            order_by(User.full_name).\
            filter(Role.name == "tracked").all()

        users = [{"id": format_user_id(user.id), "full_name": user.full_name}
                 for user in users]
        return users
    except Exception:
        return []


def format_args(args: dict):
    """
    Helper to transform args dictionary to string.
    Return empty string if all args None.
    """
    if not args:
        return ""
    args_str = "?"
    i = 0
    for key, val in args.items():
        if i == 0:
            if val:
                args_str += "{}={}".format(key, val)
        else:
            if val:
                args_str += "&{}={}".format(key, val)
        i += 1
    return args_str


def pick_resp(route: str=None,
              args: dict={}):
    """
    Helper to return the correct response based on organization
    and list entry responses.
    If list requested process arguments.
    """
    try:
        size = int(args.get("count", 100))
    except Exception:
        size = 100
    date = args.get("date")
    view = get_view(args)

    roles = [role.name for role in current_user.roles]
    is_admin = False
    error_msg = None
    if "owner" in roles or "admin" in roles:
        is_admin = True

    if isinstance(date, str):
        if not format_date_argument(date):
            if view == "json-ld":
                return jsonify([])
            date = None

    try:
        payload = search(es=pod.es,
                         date=date,
                         size=size)
    except elasticsearch.TransportError as e:
        error_type = e.info.get("error", {}).get("type", "")
        if error_type == "search_phase_execution_exception":
            error_msg = "Error: input size of {} too large.".format(size)
        payload = {}

    events = payload.get("hits", {}).get("hits", [])
    if date:
        events.reverse()
    user_cache = {}
    events = [get_ui_elements(event, user_cache, view) for event in events]
    args_str = format_args(args)
    tracked_users = get_tracked_users()
    return render_template("index.html",
                           route=route,
                           base_route="all",
                           tracked_users=tracked_users,
                           activities=events,
                           error_msg=error_msg,
                           is_admin=is_admin,
                           view_type=view,
                           args_str=args_str,
                           args=args
                           )

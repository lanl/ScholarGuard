# -*- coding: utf-8 -*-
from flask import (Blueprint,
                   request,
                   render_template,
                   redirect,
                   url_for,
                   jsonify)
from flask_security import current_user
from researcher_pod.store.es.activity import user_search
from researcher_pod import pod
from researcher_pod.store.es import activity
from researcher_pod.store.user import User, Portal
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod.pod.index import (format_args,
                                      format_date_argument,
                                      get_view,
                                      get_ui_elements,
                                      get_tracked_users,
                                      format_user_id)
import json
import elasticsearch


app = pod.app
LOG = pod.log
db = pod.db

users_page = Blueprint("users_page",
                       __name__, template_folder="templates")

user_page = Blueprint("user_page",
                      __name__, template_folder="templates")


@users_page.route("/<path:user_id>/")
@users_page.route("/<path:user_id>/portals/")
def get_user(user_id):
    """
    Default route provided a user id:
    /[ORCID]/portals == /[ORCID] == default
    """
    return pick_resp(user_id=user_id,
                     route="portals",
                     args=request.args)


@users_page.route("/<path:user_id>/artifacts/")
def get_user_artifacts(user_id):
    """
    Default route provided a user id:
    /[ORCID]/artifacts/
    """
    return pick_resp(user_id=user_id,
                     route="artifacts",
                     args=request.args)


@users_page.route("/<path:user_id>/activities/")
def get_user_activities(user_id):
    """
    Default route provided a user id:
    /[ORCID]/activities/
    """
    return pick_resp(user_id=user_id,
                     route="activities",
                     args=request.args)


def pick_resp(user_id: str=None,
              route: str=None,
              args: dict={}):
    """
    Helper to return the correct response based on event_id, event_type,
    and list or single entry responses.
    If list requested process arguments.
    """
    if "https://orcid.org/" not in user_id:
        user_id_url = "https://orcid.org/" + user_id
    else:
        user_id_url = user_id

    user = User.query.filter_by(id=user_id_url).first()
    if not user:
        return render_template("404.html"), 404

    roles = [role.name for role in current_user.roles]
    is_admin = False
    error_msg = None
    if "owner" in roles or "admin" in roles:
        is_admin = True

    try:
        size = int(args.get("count", 100))
    except Exception:
        size = 100
    date = args.get("date")
    view = get_view(args)

    if isinstance(date, str):
        if not format_date_argument(date):
            if view == "json-ld":
                return jsonify([])
            date = None

    try:
        payload = activity.user_search(
            es=pod.es,
            user_id=user_id_url,
            size=size,
            date=date
        )
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
    user = {"id": format_user_id(user.id), "full_name": user.full_name}
    if view == "json-ld":
        return jsonify(events)
    else:
        return render_template("index.html",
                               route=route,
                               base_route=user_id,
                               tracked_users=tracked_users,
                               user=user,
                               activities=events,
                               error_msg=error_msg,
                               is_admin=is_admin,
                               view_type=view,
                               args_str=args_str,
                               args=args
                               )


@users_page.route("/<path:user_id>/all-events/", methods=["GET"])
def get_all_events(user_id):
    """
    Get all user events.

    Response is in json.
    """
    if "https://orcid.org/" not in user_id:
        user_id_url = "https://orcid.org/" + user_id
    else:
        user_id_url = user_id

    user: User = User.query.filter_by(id=user_id_url).first()
    if not user:
        return render_template("404.html"), 404

    portals: Portal = Portal.query.filter(
        Portal.user_id == user.id,
        PortalConfig.id == Portal.portal_config_id
    ).distinct(PortalConfig.portal_name)\
     .order_by(Portal.portal_config_id).all()

    available_portals = []
    for p in pod.app.config.get("CELERY_TASKS_IMPORT"):
        p = p.split(".")[-1]
        available_portals.append(p)

    portals = list(set(
        [portal.portal_config.portal_name
         for portal in portals
         if portal.portal_config.portal_name in available_portals]))

    return jsonify(portals)


@users_page.route("/<path:user_id>/portals-list/", methods=["GET"])
def get_user_portals(user_id):
    """
    Get all user portal.

    Response is in json.
    """
    roles = [role.name for role in current_user.roles]
    if "owner" not in roles and "admin" not in roles:
        return "", 403

    if "https://orcid.org/" not in user_id:
        user_id_url = "https://orcid.org/" + user_id
    else:
        user_id_url = user_id

    user: User = User.query.filter_by(id=user_id_url).first()
    if not user:
        return render_template("404.html"), 404

    portals: Portal = Portal.query.filter(
        Portal.user_id == user.id,
        PortalConfig.id == Portal.portal_config_id
    ).distinct(PortalConfig.portal_name)\
     .order_by(Portal.portal_config_id).all()

    available_portals = []
    for p in pod.app.config.get("CELERY_TASKS_IMPORT"):
        p = p.split(".")[-1]
        available_portals.append(p)

    portals = list(set(
        [portal.portal_config.portal_name
         for portal in portals
         if portal.portal_config.portal_name in available_portals]))

    return jsonify(portals)


@users_page.route("/<path:user_id>/trigger-tracker/", methods=["POST"])
def trigger_portal_for(user_id):
    """
    Get all user portal.

    Response is in json.
    """
    roles = [role.name for role in current_user.roles]
    if "owner" not in roles and "admin" not in roles:
        return "", 403

    portal_name = request.form.get("portal", "")

    if "https://orcid.org/" not in user_id:
        user_id_url = "https://orcid.org/" + user_id
    else:
        user_id_url = user_id

    user: User = User.query.filter_by(id=user_id_url).first()
    if not user:
        return render_template("404.html"), 404

    portals: Portal = Portal.query.filter(
        Portal.user_id == user.id,
        PortalConfig.id == Portal.portal_config_id
    ).distinct(PortalConfig.portal_name)\
     .order_by(Portal.portal_config_id).all()
    resp = {"error": "", "success": True}

    available_portals = []
    for p in pod.app.config.get("CELERY_TASKS_IMPORT"):
        p = p.split(".")[-1]
        available_portals.append(p)

    portals = set(
        [portal.portal_config.portal_name
         for portal in portals
         if portal.portal_config.portal_name in available_portals])

    if portal_name not in portals:
        LOG.debug("user requested portal not available for user")
        resp["error"] = "Portal not available to user."
        resp["success"] = False
        return jsonify(resp)

    trigger_tracker(user_id_url, portal_name)

    return jsonify(resp)


@users_page.route("/admin/")
def get_admin_options():
    """
    Admin Route
    """
    roles = [role.name for role in current_user.roles]
    if "owner" not in roles and "admin" not in roles:
        return redirect(url_for("index_page.on_index", _external=True))

    tracked_users = get_tracked_users()
    return render_template("admin.html",
                           is_admin=True,
                           tracked_users=tracked_users
                           )


def trigger_tracker(user_id, portal_name):
    """
    Start a tracker using a user_id and portal_id
    """
    # import celery
    from researcher_pod.pod.utils import run_user_portals
    # if portal_name == "figshare":
    #     beat_name = "{}-beat".format(portal_name)
    # else:
    #     beat_name = "{}-{}-beat".format(portal_name,
    #                                     user_id)
    # TODO: revoke current celery process and reschedule asynchronously.
    # Cannot test when broker is not available - it hangs.
    # celery.task.control.revoke(beat_name, retry_policy={
    #     'max_retries': 3,
    #     'interval_start': 0,
    #     'interval_step': 0.2,
    #     'interval_max': 0.2,
    # }, timeout=1.0)
    if portal_name not in ["figshare"]:
        try:
            run_user_portals(user_id, portal_name=portal_name)
        except Exception:
            LOG.debug("exception occured while manually triggering tracker")
            return


# @users_page.route("/users/", methods=["GET"])
def get_users():
    """
    Get all users saved in the application

    :return: (List) Users with respective user_ids
    """

    user_portals = []
    users = User.query.all()
    available_trackers = []
    for p in pod.app.config.get("CELERY_TASKS_IMPORT"):
        p = p.split(".")[-1]
        available_trackers.append(p)
    for user in users:
        temp = {}
        temp["id"] = user.id
        temp["full_name"] = user.full_name
        temp["portals"] = []
        portals = Portal.query.join(PortalConfig)\
            .filter(PortalConfig.id == Portal.portal_config_id)\
            .distinct(Portal.portal_config_id)\
            .filter(Portal.user_id == user.id)\
            .order_by(Portal.portal_config_id).all()

        for portal in portals:
            if portal.portal_config.portal_name not in available_trackers:
                continue
            temp_port = {}
            temp_port["id"] = portal.id
            temp_port["name"] = portal.portal_config.portal_name
            temp_port["portal_config_id"] = portal.portal_config_id
            temp["portals"].append(temp_port)
        user_portals.append(temp)

    return render_template("users.html", users=user_portals)


# @user_page.route("/users/<path:user_id>/",
#                  methods=["GET"])
def get_user_by_id(user_id=None):
    """
    Get a single user profile by id

    :param user_id: (String) Orcid user id
    :return: user profile
    """
    if "https://orcid.org/" not in user_id:
        user_id = "https://orcid.org/" + user_id

    user = User.query.filter_by(id=user_id).first()
    if not user:
        return render_template("404.html"), 404

    LOG.debug(f"Retrieving acts for {user_id}")
    activities = user_search(pod.es, user_id=user_id)

    acts = []
    for activity in activities.get("hits", {}).get("hits"):
        activity = activity.get("_source")
        abstract = _make_abstract(
            activity.get("activity"),
            activity.get("tracker_name"))
        if not abstract:
            LOG.debug(f"no abstract was created for {activity.get('tracker_name')}")
            continue
        event_id = activity.get("activity", {}).get("event", {}).get("@id")
        event_id = event_id.rsplit("/", 1)[1]
        acts.append(
            (
                abstract,
                json.dumps(activity.get("activity")),
                _get_logo(activity.get("tracker_name")),
                event_id
            )
        )
    LOG.debug(f"acts count: {len(acts)}")
    return render_template("user.html", activities=acts, user=user)


def _make_abstract(as2_payload, tracker_name):
    abstract = []
    verb = as2_payload.get("event", {}).get("type")[0].lower()
    if verb.endswith('e'):
        verb = verb[:-1]

    as2_payload = as2_payload.get("event", {})
    actor_img = as2_payload.get("actor", {}).get("image")
    abstract.append(
        f"On <b>{as2_payload.get('published')}</b> &nbsp;"
        f"<a href=\"{as2_payload.get('actor').get('url')}\">")
    if actor_img:
        abstract.append(
            f'<img class="actor_img"'
            f'src=\'{as2_payload.get("actor").get("image").get("href")}\' />'
        )
    abstract.append(
        f"{as2_payload.get('actor', {}).get('name')}</a>"
    )

    art = _get_article(tracker_name)
    abstract.append(f"{verb}ed {art}")
    # Make list of urls
    object_url = as2_payload.get("object", {}).get("items")[0]["href"]

    abstract.append(
        f"<a href=\"{object_url}\">"
        f"{as2_payload.get('object').get('type')}</a>"
    )

    return " ".join(abstract)


def _get_article(subj):
    art = "a"
    if subj[0] in ["a", "e", "i", "o", "u"]:
        art = "an"
    return art


def _get_logo(tracker_name):
    abstract = []
    tracker_logo_path = f"img/{tracker_name}_logo.png"

    abstract.append(
        f"<object data=\"/tracker/static/{tracker_logo_path}\" type=\"image/png\" class='actor_img'>"
        f"<img class='actor_img' src=\"/tracker/static/img/activity_logo.svg\" />"
        "</object>"
    )
    return "".join(abstract)

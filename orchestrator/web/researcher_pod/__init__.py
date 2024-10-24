# -*- coding: utf-8 -*-
"""
Initiating the pod.
"""

from researcher_pod.application import Application
from flask_security import Security, SQLAlchemyUserDatastore
from researcher_pod.store.user import User, Role, roles_users
from researcher_pod.pod.utils import get_login_portals
from flask import url_for
import os


# TODO: Remove in production
# for testing locally, allow oauth without https
os.environ['OAUTHLIB_INSECURE_TRANSPORT'] = '1'

# load the config filename set by env variable.
# used for testing again.
config_filename = os.getenv("RESEARCHER_POD_CONFIG")

# the pod application initialized
pod = Application(config_filename=config_filename)

# create a test app for unit testing
# else create a regular app
if os.getenv("RESEARCHER_POD_TYPE") == "test":
    app = pod.create_test_app()
else:
    app = pod.create_app()

pod.log.debug("SQL_URI: %s" % app.config.get("SQLALCHEMY_DATABASE_URI"))

# initializes user database using flask-security
pod.user_database = SQLAlchemyUserDatastore(
    pod.db, User, Role)
pod.security = Security(pod.app,
                        pod.user_database
                        )

pod.log.debug("Logger Started with level: %s"
              % app.config.get("LOG_LEVEL", "").upper())

# registers all the views for flask to recognize and serve
pod.register_blueprints(app)
pod.log.debug("Pod initiated.")

# initiating celery and configuring celery tasks
celery = pod.create_celery_app(app=app)
beat_sched = {}
pod.log.debug(f"tasks import: "
              f"{app.config.get('CELERY_TASKS_IMPORT')}")

celery.conf.timezone = "UTC"


def setup_celery_scheduler():
    """
    Starts the appropriate trackers' get_events method after first configuring
    it. The events are configured to happen in intervals, therefore the user
    may not see the activities of a portal as soon as the portal is configured.

    :return: yields the tracker's ``get_event``
    """
    beat_sched = {}
    batch_apis = ["figshare"]
    with pod.app.app_context():
        users: [User] = pod.db.session.query(User).\
            join(roles_users).\
            join(Role).\
            order_by(User.full_name).\
            filter(Role.name == "tracked").all()
        time_until_start = 0
        for u in users:
            beat_name = "{}-beat".format(u.id)
            task_event = "researcher_pod.pod.utils.run_user_portals"
            beat_sched[beat_name] = {}
            beat_sched[beat_name]["task"] = task_event
            beat_sched[beat_name]["args"] = [u.id]
            beat_sched[beat_name]["schedule"] = 43200
            options = {"countdown": time_until_start}
            beat_sched[beat_name]["options"] = options
            time_until_start += 3600
        for p in batch_apis:
            beat_name = "{}-beat".format(p)
            task_event = "researcher_pod.pod.utils.run_batch_portal"
            beat_sched[beat_name] = {}
            beat_sched[beat_name]["task"] = task_event
            beat_sched[beat_name]["args"] = [p]
            beat_sched[beat_name]["schedule"] = 43200
    celery.conf.broker_transport_options = {"visibility_timeout": (43200 * 2)}
    celery.conf.beat_schedule = beat_sched


@pod.login_manager.user_loader
def load_user(user_id):
    """
    the user loader utility for flask_security.
    :param user_id: the user id
    :return: the `researcher_pod.store.user.User` object
    for the corresponding user_id.
    """
    from researcher_pod.store.user import User
    return User.query.filter_by(id=user_id).first()


@pod.app.context_processor
def inject_defaults():
    """
    Sets default values for use by views.
    :return (dict): a dictionary of pod's defaults.
    """

    context = {}
    context["default_menu_name"] = "Researcher"

    from researcher_pod.user.utils import get_owner
    owner = get_owner()
    if not owner:
        context["first_user"] = True
    else:
        context["first_user"] = False

    org_name = app.config.get("ORGANIZATION_FULL_NAME")
    if org_name:
        context["default_menu_name"] = org_name
    context["available_portals"] = get_login_portals()
    pod.log.debug(f"DEFAULTS: {context}")
    return context


@pod.app.before_first_request
def setup_es():
    """
    Elasticsearch started externally and therefore needs
    time before initialized.
    """
    from researcher_pod.store.es import index
    if pod.es:
        if not index.exists(pod.es):
            index.create(pod.es)
        pod.log.debug(f"ES Index: \n{index.get(pod.es)}")


def create_db():
    """
    Creates all the database tables. Also initializes supported authentication
    types in the db and initializes portal settings from the config file in
    the db.

    :return: None
    """
    from researcher_pod.store.portal_config import PortalConfig, AuthType
    from researcher_pod.store.tracker import TrackerState
    from researcher_pod.store.utils import find_or_create_auth_type

    pod.db.create_all()
    pod.user_database.find_or_create_role(name="owner")
    pod.user_database.find_or_create_role(name="visitor")
    pod.user_database.find_or_create_role(name="admin")
    pod.user_database.find_or_create_role(name="tracked")

    find_or_create_auth_type("oauth1")
    find_or_create_auth_type("oauth2")
    find_or_create_auth_type("apikey_ts_hash")
    find_or_create_auth_type("none")

    for portal_name in app.config.get("PORTALS"):
        portal_config = PortalConfig.query.filter_by(
            portal_name=portal_name).first()
        u_pn = portal_name.upper()
        if not app.config.get(f"{u_pn}_AUTH_TYPE"):
            app.config[f"{u_pn}_AUTH_TYPE"] = "none"
        if not portal_config:
            portal_config = PortalConfig()
            portal_config.portal_name = portal_name
            portal_config.portal_url = portal_config.portal_url or \
                app.config.get("%s_PORTAL_URL" % u_pn)
            portal_config.request_token_url = portal_config.request_token_url or \
                app.config.get("%s_REQUEST_TOKEN_URL" % u_pn)
            portal_config.base_authorization_url = portal_config.base_authorization_url or \
                app.config.get("%s_BASE_AUTHORIZATION_URL" % u_pn)
            portal_config.access_token_url = portal_config.access_token_url or \
                app.config.get("%s_ACCESS_TOKEN_URL" % u_pn)
            portal_config.scope = portal_config.scope or \
                app.config.get("%s_SCOPE" % u_pn)

            at = AuthType.query.filter_by(
                name=app.config.get("%s_AUTH_TYPE" % u_pn)).first()
            if not at:
                raise KeyError(f"unkown authentication type provided in config file auth_type:"
                               f" {app.config.get('%s_AUTH_TYPE' % u_pn)}")
            portal_config.auth_type = at
            pod.log.debug(f'saving config for {portal_name}')
            pod.db.session.add(portal_config)

    pod.db.session.commit()


@pod.app.teardown_appcontext
def shutdown_db_session(exception=None):
    """
    Invoked when the app is about to shutdown.
    Closes the db connection.
    :param exception:
    :return:
    """
    pod.db.session.remove()


@pod.app.after_request
def append_header(response):
    """
    Invoked after a response is prepared.
    Appends a link header with the LDN inbox URL to every response.
    :param response: the WSGI response object from flask.
    :return: the WSGI response object after modification.
    """
    ldn_inbox_link = '<%s>; rel="%s"' % \
                     (url_for("ldn_inbox.get_inbox", _external=True),
                      "https://www.w3.org/ns/ldp#inbox")
    link = ldn_inbox_link
    if bool(response.headers.get("Link")):
        link += ", "
        link += response.headers["Link"]

    response.headers["Link"] = link
    return response


with app.app_context():
    create_db()
    setup_celery_scheduler()

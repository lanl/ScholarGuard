# -*- coding: utf-8 -*-
"""
Initiating the artifact_tracker.
"""

from artifact_tracker.application import Application
from flask import url_for
import os


# load the config filename set by env variable.
# used for testing again.
config_filename = os.getenv("ARTIFACT_TRACKER_CONFIG")

# the artifact_tracker application initialized
tracker_app = Application(config_filename=config_filename)

# create a test app for unit testing
# else create a regular app
if os.getenv("ARTIFACT_TRACKER_TYPE") == "test":
    app = tracker_app.create_test_app()
else:
    app = tracker_app.create_app()

tracker_app.log.debug(
    "SQL_URI: %s" % app.config.get("SQLALCHEMY_DATABASE_URI"))

tracker_app.log.debug("Logger Started with level: %s"
                      % app.config.get("LOG_LEVEL", "").upper())

# registers all the views for flask to recognize and serve
tracker_app.register_blueprints(app)
tracker_app.log.debug("Artifact Tracker initiated.")

# initiating celery and configuring celery tasks
celery = tracker_app.create_celery_app(app=app)
tracker_app.log.debug(f"tasks import: "
                      f"{app.config.get('CELERY_TASKS_IMPORT')}")

celery.conf.timezone = "UTC"


def create_db():
    """
    Creates all the database tables. Also initializes supported authentication
    types in the db and initializes portal settings from the config file in
    the db.

    :return: None
    """
    from artifact_tracker.store.tracker_task import TrackerTask # noqa: ignore=F401
    from artifact_tracker.store.message import InboxMessage
    tracker_app.db.create_all()
    tracker_app.db.session.commit()


@tracker_app.app.teardown_appcontext
def shutdown_db_session(exception=None):
    """
    Invoked when the app is about to shutdown.
    Closes the db connection.
    :param exception:
    :return:
    """
    tracker_app.db.session.remove()


@tracker_app.app.after_request
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

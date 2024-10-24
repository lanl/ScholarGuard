# -*- coding: utf-8 -*-
"""
The Artifact Tracker WSGI application.
"""

import logging
import os
from celery import Celery
from flask import Flask
from artifact_tracker.store import db
from flask_sqlalchemy import SQLAlchemy
from .config import Config


class ReverseProxied(object):
    """
    A proxy that adds url scheme to the app's internal URLs by
    reading the x-forwarded-proto header set by nginx.
    """
    def __init__(self, app):
        self.app = app

    def __call__(self, environ, start_response):
        """
        attaching the url_scheme to the wsgi environment.
        :param environ:
        :param start_response:
        :return:
        """
        environ["wsgi.url_scheme"] = environ.get("HTTP_X_FORWARDED_PROTO",
                                                 "http")
        return self.app(environ, start_response)


class Application(object):
    """
    Application creator. Contains utilities to
    create the Flask app, Celery app, etc.
    """

    def __init__(self, config_filename=None):
        """
        Accepts the app's configuration filename.

        :param config_filename: the absolute path for the
        configuration file.
        """
        self._app = None
        self._db = db
        self._config = None
        self._log = None
        self.config_filename = config_filename or \
            os.path.join(
                os.path.dirname(
                    os.path.realpath(__file__)),
                "..", "", "config.yaml")

    def create_app(self):
        """
        Reads and initiates values from the config file and
        creates the Flask app.

        :return: the flask app.
        """
        self._app = Flask(__name__, static_url_path='')
        self._config = Config()

        self._config.from_file(self.config_filename)
        self._app.config.update(self._config)

        self._app.config["SECRET_KEY"],\
            self._app.config["SECURITY_PASSWORD_SALT"] = self.get_secrets()
        self._app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False

        self._db = SQLAlchemy(self._app)
        self._log = self.get_logger()

        self.app.wsgi_app = ReverseProxied(self.app.wsgi_app)
        return self.app

    def create_test_app(self):
        """
        Creates a flask app for testing purposes.

        :return: a test flask app
        """
        self._app = Flask(__name__, static_url_path='')
        self._config = Config()

        self._config.from_file(self.config_filename)
        self._app.config.update(self._config)

        self._app.config["SECRET_KEY"], \
            self._app.config["SECURITY_PASSWORD_SALT"] = self.get_secrets()
        self._app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False

        self._app.config["SQLALCHEMY_DATABASE_URI"] = self._app.config.\
            get("SQLALCHEMY_DATABASE_URI") + "_test"

        self._db.init_app(self._app)

        self._log = self.get_logger()
        self._log.debug("Created test app.")

        self.app.wsgi_app = ReverseProxied(self.app.wsgi_app)
        return self.app

    def create_celery_app(self, app: Flask=None):
        """
        Creates a celery app that can be used to execute the
        periodic tasks of the tracker.

        :param app (Flask|obj): the flask app obtained from
        :func:`artifact_tracker.application.Application.create_app`.
        :return (Celery|obj): The celery app.
        """

        app: Flask = app or self.create_app()
        imports = app.config.get("CELERY_TASKS_IMPORT")
        celery = Celery(app.import_name,
                        broker=app.config.get("CELERY_BROKER_URL"),
                        include=imports)

        celery.conf.update(app.config)
        TaskBase = celery.Task

        class ContextTask(TaskBase):
            abstract = True

            def __call__(self, *args, **kwargs):
                with app.app_context():
                    return TaskBase.__call__(self, *args, **kwargs)
        celery.Task = ContextTask
        return celery

    @property
    def config(self):
        """
        The configuration object.
        """
        return self._config

    @property
    def db(self):
        """
        The SQL-Alchemy database engine object.
        """
        return self._db

    @property
    def app(self):
        """
        The Flask app.
        """
        return self._app

    @property
    def log(self):
        """
        The app's logger object.
        """
        return self._log

    @staticmethod
    def get_secrets():
        """
        Reads the private key and password salt stored in the secrets file.
        :return (tuple): the private key and salt.
        """
        secret_fn = os.getenv("ARTIFACT_TRACKER_SECRETS")
        secret_file_name = secret_fn or os.path.join(
            os.path.dirname(os.path.realpath(__file__)),
            "..", "secrets", "secrets")

        if not os.path.isfile(secret_file_name):
            from artifact_tracker.utils.secrets import make_secrets
            make_secrets()

        with open(secret_file_name) as sf:
            key = sf.readline().strip()
            salt = sf.readline().strip()
            if not key or not salt:
                raise ValueError(f"Error, unexpected data in the"
                                 f" secrets file: {secret_file_name}."
                                 f"If you are starting for the first time,"
                                 f"please run the create_secrets file.")
            return key, salt

    @staticmethod
    def register_blueprints(app: Flask):
        """
        Registers all view pages within the flask app.
        :param app: the flask app.
        :return: None
        """
        from artifact_tracker.ldn.inbox import ldn_inbox

        app.register_blueprint(ldn_inbox)

    def get_logger(self):
        """
        Configures the app's logger.
        :return: the logger object.
        """
        try:
            log_level = getattr(
                logging,
                self._app.config.get("LOG_LEVEL", "").upper())
        except AttributeError:
            log_level = logging.ERROR

        log_format = "%(funcName)s: %(message)s"
        logging.basicConfig(level=log_level, format=log_format)
        log = logging.getLogger(__name__)
        return log

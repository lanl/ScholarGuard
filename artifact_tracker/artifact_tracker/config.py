# -*- coding: utf-8 -*-


"""
Implement default configuration loaders and writers.
"""

import yaml


class Config(dict):
    """
    Custom config loaders and writers.
    """

    _instance = None

    def __new__(cls, *args, **kwargs):
        """
        Converting into a singleton.
        :param args:
        :param kwargs:
        :return:
        """
        if not cls._instance:
            cls._instance = super(Config, cls).__new__(cls)
        return cls._instance

    def __init__(self, defaults=None):
        """

        :param defaults:
        """

        dict.__init__(self, defaults or {})

    def from_file(self, filename):
        """

        :param filename:
        :return:
        """
        config = {}
        with open(filename, "rb") as f:
            config = yaml.load(f)
        self.from_object(config)

    def from_object(self, config):
        if not isinstance(config, dict):
            return
        self["SERVER_NAME"] = config.get("artifact_tracker", {})\
            .get("server_name")
        self["PREFERRED_URL_SCHEME"] = config.get("artifact_tracker", {})\
            .get("preferred_url_scheme")
        self["DEBUG"] = config.get("artifact_tracker", {}).get("debug")
        self["LOG_LEVEL"] = config.get("artifact_tracker", {}).get("log_level")

        self["SQLALCHEMY_DATABASE_URI"] = config.get("db", {})\
            .get("sqlalchemy_database_uri", "").strip()

        self["DISALLOW_EVENTS_BEFORE"] = config.get(
            "artifact_tracker", {}).get("disallow_events_before")

        self["PORTALS"] = {}
        for name, portal in config.get("portals", {}).items():
            self["PORTALS"].setdefault(name, {})
            for prop, value in portal.items():
                self["PORTALS"][name][prop] = value
        self["CELERY_BROKER_URL"] = config.get("tracker", {})\
            .get("celery", {}).get("broker_url", "").strip()
        self["CELERY_BACKEND_URL"] = self["SQLALCHEMY_DATABASE_URI"]
        self["CELERY_TASKS_IMPORT"] = config.get("tracker", {})\
            .get("celery", {}).get("import", [])

    def validate(self) -> (bool, [str]):
        error_tmpl = "{} Please set parameter {} in section {}"
        msgs = []
        if not self.get("SQLALCHEMY_DATABASE_URI"):
            msgs.append(
                error_tmpl.format(
                    "Missing SQL Alchemy database URI.",
                    "sqlalchemy_database_uri"
                    "db")
            )
        if len(self.get("PORTALS")) == 0:
            msgs.append(
                error_tmpl.format(
                    "No Portals have been configured.",
                    "<portal_name>"
                    "portals")
            )
        for portal in self.get("PORTALS"):
            u_portal = portal.upper()
            if not self.get("%s_AUTH_TYPE" % u_portal):
                msgs.append(
                    "Missing parameter auth_type for portal {}".format(portal)
                )

        if len(msgs) > 0:
            return False, msgs
        else:
            return True, msgs

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
        self["SERVER_NAME"] = config.get("pod", {}).get("server_name")
        self["ORGANIZATION_FULL_NAME"] = config.get("pod", {})\
            .get("organization_full_name")
        self["ORGANIZATION_SHORT_NAME"] = config.get("pod", {})\
            .get("organization_short_name")
        self["PREFERRED_URL_SCHEME"] = config.get("pod", {})\
            .get("preferred_url_scheme")
        self["DEBUG"] = config.get("pod", {}).get("debug")
        self["LOG_LEVEL"] = config.get("pod", {}).get("log_level")
        self["COMPONENT_INBOX_ENDPOINT"] = config.get(
            "pod", {}).get("component_inbox_endpoint")
        self["ORCHESTRATOR_INBOX_ENDPOINT"] = config.get(
            "pod", {}).get("orchestrator_inbox_endpoint")
        self["ORCHESTRATOR_EVENT_BASE_URL"] = config.get(
            "pod", {}).get("orchestrator_event_base_url")

        self["SQLALCHEMY_DATABASE_URI"] = config.get("db", {})\
            .get("sqlalchemy_database_uri", "").strip()

        self["ELASTICSEARCH_HOST"] = config.get("db", {}) \
            .get("es", {}).get("host", "").strip()
        self["ELASTICSEARCH_PORT"] = config.get("db", {}) \
            .get("es", {}).get("port", 9200)

        self["DISALLOW_EVENTS_BEFORE"] = config.get(
            "pod", {}).get("disallow_events_before")

        self["CAPTURE_URL"] = config.get(
            "pod", {}).get("capture_endpoint")

        self["SECRET_KEY"] = config.get("security", {})\
            .get("secret_key", "").strip()
        self["SECURITY_REGISTERABLE"] = config.get("security", {})\
            .get("registerable")
        self["SECURITY_TRACKABLE"] = config.get("security", {})\
            .get("trackable")
        self["SECURITY_SEND_REGISTER_EMAIL"] = config.get("security", {})\
            .get("send_register_email")
        self["SECURITY_PASSWORD_HASH"] = config.get("security", {})\
            .get("password_hash")
        self["SECURITY_PASSWORD_SALT"] = config.get("security", {})\
            .get("password_salt", "").strip()

        self["PORTALS"] = []
        for name, portal in config.get("portals", {}).items():
            self["PORTALS"].append(name)
            for prop, value in portal.items():
                attr_name = name.upper() + "_" + prop.replace(" ", "_").upper()
                self[attr_name] = value

        self["CELERY_BROKER_URL"] = config.get("tracker", {})\
            .get("celery", {}).get("broker_url", "").strip()
        self["CELERY_BACKEND_URL"] = self["SQLALCHEMY_DATABASE_URI"]
        self["CELERY_TASKS_IMPORT"] = config.get("tracker", {})\
            .get("celery", {}).get("import", [])

        self["AP_OUTBOX_URL"] = config.get("pod", {}).get("ap_outbox_url")
        self["AP_INBOX_URL"] = config.get("pod", {}).get("ap_inbox_url")

    def validate(self) -> (bool, [str]):
        error_tmpl = "%s Please set parameter %s in section %s"
        msgs = []
        if not self.get("SQLALCHEMY_DATABASE_URI"):
            msgs.append(error_tmpl %
                        ("Missing SQL Alchemy database URI.",
                         "sqlalchemy_database_uri", "db"))
        if len(self.get("PORTALS")) == 0:
            msgs.append(error_tmpl %
                        ("No Portals have been configured.",
                         "<portal_name>", "portals"))
        for portal in self.get("PORTALS"):
            u_portal = portal.upper()
            if not self.get("%s_AUTH_TYPE" % u_portal):
                msgs.append("Missing parameter auth_type for portal %s"
                            % portal)

        if len(msgs) > 0:
            return False, msgs
        else:
            return True, msgs

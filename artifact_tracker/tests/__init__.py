# -*- coding: utf-8 -*-
import os
import unittest  # pytest in future


config_filename = os.path.join(os.path.dirname(__file__),
                               "../config.yaml")
secrets_filename = os.path.join(os.path.dirname(__file__),
                                "../secrets/secrets")
os.environ["ARTIFACT_TRACKER_TYPE"] = "test"
os.environ["ARTIFACT_TRACKER_CONFIG"] = config_filename
os.environ["ARTIFACT_TRACKER_SECRETS"] = secrets_filename

from artifact_tracker import tracker_app  # noqa E402


class ArtifactTrackerTests(unittest.TestCase):

    def setUp(self):
        self.tracker_app = tracker_app
        self.tracker_app.create_test_app()
        self.app = self.tracker_app.app
        self.tracker_app.log.debug("initing test tracker_app")
        self.app.config["TESTING"] = True
        self.app.config["WTF_CSRF_ENABLED"] = True
        self.app.testing = True
        self.client = self.app.test_client()

        self.app_context = self.app.app_context
        self.app.config = self.client.application.config
        self._ctx = self.app.test_request_context()
        self._ctx.push()
        from artifact_tracker import create_db
        from sqlalchemy_utils import database_exists, create_database
        with self.app_context():
            if not database_exists(self.app.config["SQLALCHEMY_DATABASE_URI"]):
                create_database(self.app.config["SQLALCHEMY_DATABASE_URI"])
            create_db()

    def tearDown(self):
        from sqlalchemy_utils import database_exists, drop_database
        with self.app_context():
            self.tracker_app.log.debug("tearing down...")
            self.tracker_app.db.session.close()
            self.tracker_app.db.engine.dispose()
            if database_exists(self.app.config["SQLALCHEMY_DATABASE_URI"]):
                drop_database(self.app.config["SQLALCHEMY_DATABASE_URI"])
        self._ctx.pop()

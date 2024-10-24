# -*- coding: utf-8 -*-
import os
import unittest


config_filename = os.path.join(os.path.dirname(__file__),
                               "../../conf/config_standalone.yaml")
secrets_filename = os.path.join(os.path.dirname(__file__),
                                "../../secrets/secrets")
os.environ["RESEARCHER_POD_TYPE"] = "test"
os.environ["RESEARCHER_POD_CONFIG"] = config_filename
os.environ["RESEARCHER_POD_SECRETS"] = secrets_filename

from researcher_pod import pod


class ResearcherPodTests(unittest.TestCase):

    def setUp(self):
        self.pod = pod
        # app = self.pod.create_test_app()
        self.app = self.pod.app
        self.pod.log.debug("initing test pod")
        self.app.config["TESTING"] = True
        self.app.config["WTF_CSRF_ENABLED"] = True
        self.app.testing = True
        self.client = self.app.test_client()

        self.app_context = self.app.app_context
        self.app.config = self.client.application.config
        self.app.config["SECURITY_USER_IDENTITY_ATTRIBUTES"] = ["email"]
        self._ctx = self.app.test_request_context()
        self._ctx.push()
        from researcher_pod import create_db
        from sqlalchemy_utils import database_exists, create_database
        with self.app_context():
            if not database_exists(self.app.config["SQLALCHEMY_DATABASE_URI"]):
                create_database(self.app.config["SQLALCHEMY_DATABASE_URI"])
            create_db()

        @self.pod.login_manager.user_loader
        def load_user(user_id):
            from researcher_pod.store.user import User
            return User.query.filter_by(id=user_id).first()

    def tearDown(self):
        from sqlalchemy_utils import database_exists, drop_database
        with self.app_context():
            self.pod.log.debug("tearing down...")
            self.pod.db.session.close()
            self.pod.db.engine.dispose()
            if database_exists(self.app.config["SQLALCHEMY_DATABASE_URI"]):
                drop_database(self.app.config["SQLALCHEMY_DATABASE_URI"])
        self._ctx.pop()

    @staticmethod
    def _create_user(user_id=None, email=None, role=None):
        from researcher_pod.user.utils import create_user_in_db
        user = create_user_in_db(user_id=user_id or "0000-0002-7585-5009",
                                 email=email or "user@portal.com",
                                 full_name="new user",
                                 password="somepassword",
                                 roles=role or ["owner"])
        assert user.id == user_id or "https://orcid.org/0000-0002-7585-5009"
        assert user.email == email or "user@portal.com"
        assert role or "owner" in user.roles
        return user

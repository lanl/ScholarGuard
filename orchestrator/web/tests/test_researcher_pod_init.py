# -*- coding: utf-8 -*-

from tests import ResearcherPodTests
from researcher_pod import load_user, inject_defaults
from researcher_pod.store.portal_config import PortalConfig, AuthType
from researcher_pod.store.user import Role


class TestResearcherPodInit(ResearcherPodTests):

    def test_load_user(self):

        with self.app_context():

            user = self._create_user()
            assert load_user(user.id) == user

            assert load_user(234525252524532) is None
            assert load_user("sss") is None
            assert load_user(0) is None

    def test_inject_defaults(self):

        with self.app_context():
            # no user in db
            defaults = inject_defaults()
            assert defaults["first_user"]
            assert isinstance(defaults["available_portals"], list)
            assert len(defaults["available_portals"]) == 0

            # one user in db
            self._create_user()
            defaults = inject_defaults()
            assert not defaults["first_user"]
            assert isinstance(defaults["available_portals"], list)
            assert len(defaults["available_portals"]) == 0

            # one complete portal
            github_pc = PortalConfig.query\
                .filter_by(portal_name="github").first()
            github_pc.client_id = b"_id"
            github_pc.client_secret = b"secret"
            self.pod.db.session.add(github_pc)
            self.pod.db.session.commit()
            defaults = inject_defaults()
            assert not defaults["first_user"]
            assert isinstance(defaults["available_portals"], list)
            # only orcid login is allowed. so github should not show up.
            assert len(defaults["available_portals"]) == 0

            pc = PortalConfig.query.filter_by(portal_name="orcid").first()
            pc.client_id = b"_id"
            pc.client_secret = b"secret"
            self.pod.db.session.add(pc)
            self.pod.db.session.commit()
            defaults = inject_defaults()
            assert not defaults["first_user"]
            assert isinstance(defaults["available_portals"], list)
            assert len(defaults["available_portals"]) == 1

    def test_create_db(self):
        with self.app_context():
            org_pc = PortalConfig.query.all()
            assert len(org_pc) > 0

            auth_types = AuthType.query.all()
            assert len(auth_types) == 4

            roles = Role.query.all()
            assert len(roles) == 3

    def test_append_header(self):
        resp = self.client.get("/")
        lh = resp.headers.get("Link")
        assert lh is not None

# -*- coding: utf-8 -*-
from tests import ResearcherPodTests
from researcher_pod.user.utils import create_user_in_db, \
    user_authorised_for_settings_page
from flask import url_for, request, current_app, make_response
from flask_security import login_user, current_user


class TestUserSettings(ResearcherPodTests):

    def test_on_settings(self):
        # no users/owner created..
        # should show list of portals to be configured for login
        resp = self.client.get("/settings/")
        body = resp.data.decode("utf8").lower()
        assert resp.status_code == 200
        for p_name in self.app.config.get("PORTALS"):
            if self.app.config.get("%s_ALLOW_LOGIN" % p_name.upper()):
                assert p_name.lower() in body
                with self.app_context():
                    assert url_for("settings_page.on_settings_portal",
                                   portal_name=p_name) in body

        # create a user and check if no portals are listed
        # and the user is redirected to the login page
        with self.app_context():
            user = self._create_user()
            resp = self.client.get("/settings/")
            assert resp.status_code == 302
            assert resp.headers.get("Location") == \
                url_for("security.login", _external=True)

        # TODO: figure how to test settings page output after user login
        # problem: the settings page does not seem to get the logged in
        # context
        """
        with self.app_context():
            from flask import has_request_context, copy_current_request_context
            print(f"req_ctx: {has_request_context()}")
            logged_in = login_user(user)
            self.pod.log.debug(current_user)
            assert logged_in
            print("ua: %s" % user_authorised(current_user))
            #print(user_authorised(user))
            #print(get_owner())
            resp = self.client.get("/settings/")
            print(resp.headers)
            print(resp.status_code)
            assert resp.status_code == 200
            #ctx.pop()
        """

    def test_on_settings_portal(self):
        # TODO: figure how to test settings page output after user login
        # problem: the settings page does not seem to get the logged in
        # context
        pass

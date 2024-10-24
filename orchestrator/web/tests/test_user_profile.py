# -*- coding: utf-8 -*-
from tests import ResearcherPodTests
from researcher_pod.user.utils import create_user_in_db, \
    get_owner
from flask import url_for, request, current_app, make_response
from flask_security import login_user, current_user


class TestUserSettings(ResearcherPodTests):

    def test_on_user_profile(self):
        # TODO: figure how to test settings page output after user login
        # problem: the settings page does not seem to get the logged in
        # context
        pass


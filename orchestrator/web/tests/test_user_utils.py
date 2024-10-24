# -*- coding: utf-8 -*-

from tests import ResearcherPodTests
from researcher_pod.user.utils import create_user_in_db,\
    add_portal_for_user, get_portal_from_db, get_token_secret_for_portal,\
    get_user_profile, encrypt, decrypt, should_create_owner,\
    should_create_new_user, should_login_user, \
    should_update_existing_portal, get_owner, user_authorised_for_settings_page
from flask_security import login_user


class TestUserUtils(ResearcherPodTests):

    def test_get_owner(self):
        with self.app_context():
            # empty db
            assert not get_owner()
            # visitor first..
            self._create_user(
                user_id="0000-0002-7585-5010",
                email="visitor@portal.com",
                role=["visitor"])
            assert not get_owner()

            # valid owner
            user = self._create_user()
            owner = get_owner()
            self.assertEqual(owner, user)

    def test_valid_user_authorised_for_settings_page(self):
        with self.app_context():
            # owner logged in
            user = self._create_user()
            logged_in = login_user(user)
            assert logged_in
            assert user_authorised_for_settings_page()

    def test_visitor_user_authorised_for_settings_page(self):
        with self.app_context():
            # owner logged in
            user = self._create_user(role=["visitor"])
            logged_in = login_user(user)
            assert logged_in
            assert not user_authorised_for_settings_page()

    def test_no_user_authorised_for_settings_page(self):
        with self.app_context():
            # no user in db. fresh install
            assert user_authorised_for_settings_page()

    def test_not_logged_in_user_authorised_for_settings_page(self):
        with self.app_context():
            # owner created but not logged in
            self._create_user()
            assert not user_authorised_for_settings_page()

    def test_create_user_in_db(self):
        with self.app_context():
            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            assert len(user.id) > 0
            assert user.email == "user@portal.com"
            assert "owner" in user.roles

            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5010",
                email="user2@portal.com",
                full_name="new user",
                password="somepassword")
            assert len(user.id) > 0
            assert user.email == "user2@portal.com"
            assert "visitor" in user.roles

            assert not create_user_in_db(email="",
                                         full_name=None,
                                         password=0,
                                         roles=["owner"])
            assert not create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5011",
                email="user2@portal.com",
                full_name="",
                password="somepassword")
            assert not create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5012",
                email="user@portal.com",
                full_name="new user",
                password="")
            assert not create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5013",
                email="user@portal.com",
                full_name="new user",
                password="",
                roles=["owner"])

    def test_add_portal_for_user(self):
        user_profile = {
            "username": "username",
            "email": "user@portal.com",
            "portal_user_id": "1"
        }
        with self.app_context():
            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])

            assert add_portal_for_user(user=user,
                                       user_profile=user_profile,
                                       portal_name="github",
                                       token="token",
                                       secret="secret")

            assert not add_portal_for_user(user="AA",
                                           user_profile=user_profile,
                                           portal_name="github",
                                           token="token",
                                           secret="secret")

            assert not add_portal_for_user(user=user,
                                           user_profile=user_profile,
                                           portal_name="",
                                           token="token",
                                           secret="secret")

            assert not add_portal_for_user(user=user,
                                           user_profile={},
                                           portal_name="github",
                                           token="",
                                           secret="secret")

            assert not add_portal_for_user(user=user,
                                           user_profile={},
                                           portal_name="github",
                                           token="token",
                                           secret="")
            assert not add_portal_for_user(user=user,
                                           user_profile=user_profile)

    def test_get_portal_from_db(self):
        user_profile = {
            "username": "username",
            "email": "user@portal.com",
            "portal_user_id": "1"
        }
        with self.app_context():
            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            assert add_portal_for_user(user=user,
                                       user_profile=user_profile,
                                       portal_name="github",
                                       token="token",
                                       secret="secret")
            portal = get_portal_from_db(user_id=user.id,
                                        portal_name="github")
            assert portal is not None
            assert portal.user_id == user.id
            assert portal.portal_username == "username"

            portal = get_portal_from_db(username=user_profile.get("username"),
                                        portal_name="github")
            assert portal is not None
            assert portal.user_id == user.id
            assert portal.portal_username == "username"

            portal = get_portal_from_db(portal_name="portal")
            assert not portal

            assert not get_portal_from_db()
            assert not get_portal_from_db(user_id="10000",
                                          portal_name="github")
            assert not get_portal_from_db(user_id=user.id)

    def test_get_token_secret_for_portal(self):
        user_profile = {
            "username": "username",
            "email": "user@portal.com",
            "portal_user_id": "1"
        }
        with self.app_context():
            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            success = add_portal_for_user(user=user,
                                          user_profile=user_profile,
                                          portal_name="github",
                                          token="token",
                                          secret="secret")
            assert success
            portal = get_portal_from_db(user_id=user.id,
                                        portal_name="github")
            assert portal is not None
            assert portal.user_id == user.id
            assert portal.portal_username == "username"
            token, secret = get_token_secret_for_portal(portal)
            assert token.decode("utf8") == "token"
            assert secret.decode("utf8") == "secret"

            token, secret = get_token_secret_for_portal(None)
            assert not token and not secret

            token, secret = get_token_secret_for_portal("SSSS")
            assert not token and not secret

    def test_get_user_profile(self):
        user_profile = {
            "username": "username",
            "email": "user@portal.com",
            "portal_user_id": "1"
        }
        with self.app_context():
            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            success = add_portal_for_user(user=user,
                                          user_profile=user_profile,
                                          portal_name="github",
                                          token="token",
                                          secret="secret")
            assert success
            portal = get_portal_from_db(user_id=user.id,
                                        portal_name="github")

            # testing user profile retrieval from db only.
            up = get_user_profile(db_portal=portal)
            assert isinstance(up, dict)
            assert up.get("username") == "username"
            assert up.get("full_name") == "new user"

            up = get_user_profile()
            assert not bool(up)

    def test_encrypt_decrypt(self):
        text = "SOME random text and 122435151515"
        e_text = encrypt(text)
        assert bool(e_text)
        d_text = decrypt(e_text)
        assert bool(d_text)
        assert d_text.decode("utf8") == text

        dictionary = {"text": text, "a": 1, "xx": True}
        e_dict = encrypt(dictionary)
        assert bool(e_dict)
        d_dict = decrypt(e_dict)
        assert isinstance(d_dict, dict)
        assert d_dict == dictionary

        assert not encrypt(True)
        assert not decrypt(True)
        assert not encrypt(None)
        assert not decrypt(None)
        assert not encrypt("")
        assert not decrypt("")

    def test_should_create_owner(self):
        with self.app_context():
            assert should_create_owner(None)
            assert should_create_owner(0)
            assert not should_create_owner(1)

            create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            assert not should_create_owner(1)
            assert not should_create_owner(None)
            assert not should_create_owner(0)

    def test_should_create_new_user(self):
        with self.app_context():
            assert not should_create_new_user("")
            assert not should_create_new_user(None)
            assert should_create_new_user({
                "username": "user@portal.com"},
                current_user_id=""
            )

            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            user_profile = {
                "username": "username",
                "email": "user@portal.com",
                "portal_user_id": "1"
            }
            success = add_portal_for_user(user=user,
                                          user_profile=user_profile,
                                          portal_name="github",
                                          token="token",
                                          secret="secret")
            assert success
            assert not should_create_new_user(
                {"username": "username",
                 "portal_user_id": "1"},
                current_user_id="https://orcid.org/0000-0002-7585-5009"
            )

    def test_should_update_existing_portal(self):
        user_profile = {
            "username": "username",
            "email": "user@portal.com",
            "portal_user_id": "1"
        }
        with self.app_context():
            assert should_update_existing_portal()
            assert should_update_existing_portal("")

            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            success = add_portal_for_user(user=user,
                                          user_profile=user_profile,
                                          portal_name="github",
                                          token="token",
                                          secret="secret")
            assert success
            portal = get_portal_from_db(user_id=user.id,
                                        portal_name="github")

            assert not should_update_existing_portal(portal)
            assert not should_update_existing_portal(user)

    def test_should_login_user(self):
        user_profile = {
            "username": "username",
            "email": "user@portal.com",
            "portal_user_id": "1"
        }
        with self.app_context():
            assert not should_login_user()
            assert not should_login_user(current_user_id=1)
            assert not should_login_user(user_portal=1,
                                         current_user_id=0)
            assert not should_login_user(user_portal=1)
            user = create_user_in_db(
                user_id="https://orcid.org/0000-0002-7585-5009",
                email="user@portal.com",
                full_name="new user",
                password="somepassword",
                roles=["owner"])
            success = add_portal_for_user(user=user,
                                          user_profile=user_profile,
                                          portal_name="github",
                                          token="token",
                                          secret="secret")
            assert success
            portal = get_portal_from_db(user_id=user.id,
                                        portal_name="github")

            assert should_login_user(user_portal=portal,
                                     current_user_id="")
            assert should_login_user(user_portal=portal)
            assert not should_login_user(user_portal=user,
                                         current_user_id="")

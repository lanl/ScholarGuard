# -*- coding: utf-8 -*-

from flask import request, session, redirect, url_for, Blueprint
from requests_oauthlib import OAuth1Session
from researcher_pod import pod
from flask_security import current_user, login_user
from researcher_pod.store.user import Portal
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod.user.utils import \
    get_portal_from_db, \
    get_user_profile, should_login_user,\
    get_lower_upper_portal_name, get_save_user_profile
from urllib.parse import parse_qs
import os

os.environ['OAUTHLIB_INSECURE_TRANSPORT'] = '1'  # TODO: Remove in production

oauth1_portal = Blueprint("oauth1_portal", __name__,
                          template_folder="templates")
app = pod.app
LOG = pod.log


@oauth1_portal.route("/oauth1/<portal_name>/")
def get_request_token(portal_name=None):

    if not portal_name:
        # TODO: Error Page with message
        return

    l_pname = portal_name.lower()

    portal_config: PortalConfig = PortalConfig.query.filter_by(
        portal_name=l_pname).first()

    if not portal_config.client_id \
            or not portal_config.client_secret:
        return redirect(url_for("settings_page.on_settings_portal",
                        portal_name=l_pname, _external=True))

    LOG.debug("OAuth1 for %s" % l_pname)

    current_user_id = None
    try:
        current_user_id = current_user.id
    except AttributeError:
        pass

    if not current_user_id and not portal_name == "orcid":
        return redirect(url_for("security.login", _external=True))

    oauth = OAuth1Session(
        portal_config.client_id,
        client_secret=portal_config.client_secret
    )

    LOG.debug(portal_config.request_token_url)
    oauth_tokens = oauth.fetch_request_token(
        "https://api.twitter.com/oauth/request_token")

    authorization_url = oauth.authorization_url(
        portal_config.base_authorization_url)

    resource_owner_key = oauth_tokens.get("oauth_token")
    resource_owner_secret = oauth_tokens.get("oauth_token_secret")

    session["%s_resource_owner_key" % l_pname] = resource_owner_key
    session["%s_resource_token_secret" % l_pname] = resource_owner_secret

    return redirect(authorization_url)


@oauth1_portal.route("/oauth1/<portal_name>/callback/", methods=["GET"])
def get_authorization(portal_name=None):

    if not portal_name:
        # TODO: Error Page with message
        return

    l_pname = portal_name.lower()

    LOG.debug("OAuth1 for %s" % l_pname)

    oauth_response = parse_qs(request.url)
    verifier = oauth_response.get("oauth_verifier")[0]

    portal_config: PortalConfig = PortalConfig.query.filter_by(
        portal_name=l_pname).first()
    if not portal_config.client_id \
            or not portal_config.client_secret:
        return redirect(url_for("settings_page.on_settings_portal",
                                portal_name=l_pname, _external=True))

    oauth = OAuth1Session(
        portal_config.client_id,
        client_secret=portal_config.client_secret,
        resource_owner_key=session.get("%s_resource_owner_key" % l_pname),
        resource_owner_secret=session.get(
            "%s_resource_owner_secret" % l_pname),
        verifier=verifier
    )
    oauth_tokens = oauth.fetch_access_token(
        portal_config.access_token_url)

    session["%s_resource_token" % l_pname] = \
        oauth_tokens.get("oauth_token")
    session["%s_resource_token_secret" % l_pname] = \
        oauth_tokens.get("oauth_token_secret")

    LOG.debug("Saved auth tokens in session. \n%s\n%s"
              % (session["%s_resource_token" % l_pname],
                 session["%s_resource_token_secret" % l_pname]))

    return redirect(url_for("oauth1_portal.get_profile",
                            portal_name=portal_name, _external=True))


@oauth1_portal.route("/oauth1/<portal_name>/profile/", methods=["GET", "POST"])
def get_profile(portal_name=None):
    if not portal_name:
        # TODO: Error Page with message
        return redirect(url_for("security.login", _external=True))

    l_pname, u_pname = get_lower_upper_portal_name(portal_name)
    LOG.debug("OAuth1 %s for %s" % ("get_profile", l_pname))

    portal_config: PortalConfig = PortalConfig.query.filter_by(
        portal_name=l_pname).first()
    if not portal_config.client_id \
            or not portal_config.client_secret:
        return redirect(url_for("settings_page.on_settings_portal",
                                portal_name=l_pname, _external=True))

    try:
        current_user_id = current_user.id
    except AttributeError:
        current_user_id = 0

    if not current_user_id and not portal_name == "orcid":
        return redirect(url_for("security.login", _external=True))

    LOG.debug("current user id: %s" % current_user_id)

    saved_portal: Portal = get_portal_from_db(user_id=current_user_id,
                                              portal_name=l_pname)
    LOG.debug("Saved Portal: %s" % saved_portal)
    LOG.debug(session.get("twitter_resource_token"))
    LOG.debug(session.get("twitter_resource_token_secret"))

    oauth = OAuth1Session(
        client_key=portal_config.client_id,
        client_secret=portal_config.client_secret,
        resource_owner_key=session.get("%s_resource_token" % l_pname),
        resource_owner_secret=session.get("%s_resource_token_secret" % l_pname)
                                          )

    user_profile = get_user_profile(
        db_portal=saved_portal,
        portal_name=portal_name,
        oauth=oauth
    )

    if not user_profile.get("username"):
        LOG.debug("No username found. Something's wrong!")
        return redirect(url_for("security.login", _external=True))

    user, portal = get_save_user_profile(
        user_profile=user_profile,
        portal_name=portal_name,
        current_user_id=current_user_id,
        token=session.get("%s_resource_token" % l_pname),
        secret=session.get("%s_resource_token_secret" % l_pname))

    if not user and not portal:
        LOG.debug("Error creating/updating user profile!")
        # TODO: create error page.
        return redirect(url_for("security.login", _external=True))

    if should_login_user(
            user_portal=portal,
            current_user_id=current_user_id):
        LOG.debug("logging in user")
        logged_in = login_user(user, remember=True)
        if logged_in:
            LOG.debug("login success")
        else:
            LOG.debug("loging failure")

    LOG.debug("user logged in: %s " % current_user.id)
    return redirect(url_for("user_profile_page.on_user_profile",
                            _external=True))

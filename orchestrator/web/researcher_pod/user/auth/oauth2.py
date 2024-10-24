# -*- coding: utf-8 -*-

from flask import request, session, redirect, \
    url_for, Blueprint, flash
from requests_oauthlib import OAuth2Session
from researcher_pod import pod
from researcher_pod.store.portal_config import PortalConfig
from flask_security import current_user, login_user
from researcher_pod.user.utils import \
    get_portal_from_db, get_token_secret_for_portal, \
    get_user_profile, should_login_user, \
    get_lower_upper_portal_name, get_save_user_profile
import os

os.environ['OAUTHLIB_INSECURE_TRANSPORT'] = '1'  # TODO: Remove in production

oauth2_portal = Blueprint("oauth2_portal", __name__,
                          template_folder="templates")
app = pod.app
LOG = pod.log


@oauth2_portal.route("/oauth2/<portal_name>/")
def get_request_token(portal_name=None):

    if not portal_name:
        # TODO: Error Page with message
        return "Error"

    l_pname = portal_name.lower()

    portal_config: PortalConfig = PortalConfig.query.filter_by(
        portal_name=l_pname).first()

    if not portal_config.client_id \
            or not portal_config.client_secret:
        return redirect(url_for("settings_page.on_settings_portal",
                                portal_name=l_pname, _external=True))

    LOG.debug("OAuth2 for %s" % l_pname)

    current_user_id = None
    try:
        current_user_id = current_user.id
    except AttributeError:
        pass

    if not current_user_id and not portal_name == "orcid":
        return redirect(url_for("security.login", _external=True))

    portal = get_portal_from_db(user_id=current_user_id,
                                portal_name=l_pname)
    token, secret = get_token_secret_for_portal(portal)
    if token:
        session["%s_oauth_token" % l_pname] = token
        return redirect(url_for("oauth2_portal.get_profile",
                                portal_name=l_pname, _external=True))

    scope = []
    if bool(portal_config.scope):
        scope = portal_config.scope.split()
    LOG.debug(f"scope: {scope}")
    if len(scope) > 0:
        oauth = OAuth2Session(portal_config.client_id,
                              scope=portal_config.scope)
    else:
        oauth = OAuth2Session(portal_config.client_id)
    auth_url, state = oauth.authorization_url(
        portal_config.base_authorization_url)

    LOG.debug(f"auth_url: {auth_url}")
    session["%s_oauth_state" % l_pname] = state

    return redirect(auth_url)


@oauth2_portal.route("/oauth2/<portal_name>/callback/", methods=["GET"])
def get_authorization(portal_name=None):

    if not portal_name:
        # TODO: Error Page with message
        return
    l_pname = portal_name.lower()
    LOG.debug("OAuth2 for %s" % l_pname)

    portal_config: PortalConfig = PortalConfig.query.filter_by(
        portal_name=l_pname).first()
    if not portal_config.client_id \
            or not portal_config.client_secret:
        return redirect(url_for("settings_page.on_settings_portal",
                                portal_name=l_pname, _external=True))

    LOG.debug("session: \n%s" % session)
    scope = []
    if bool(portal_config.scope):
        scope = portal_config.scope.split()
    if len(scope) > 0:
        oauth = OAuth2Session(
            portal_config.client_id,
            state=session["%s_oauth_state" % l_pname],
            scope=portal_config.scope
        )
    else:
        oauth = OAuth2Session(
            portal_config.client_id,
            state=session["%s_oauth_state" % l_pname]
        )

    try:
        token = oauth.fetch_token(
            portal_config.access_token_url,
            client_secret=portal_config.client_secret,
            authorization_response=request.url)
    except Exception:
        flash(f"{portal_name.title()} returned with an error "
              "when authenticating. Please verify the Client Keys and "
              f"Secrets again.")
        return redirect(url_for("settings_page.on_settings_portal",
                                portal_name=portal_name, _external=True))

    session["%s_oauth_token" % l_pname] = token
    return redirect(url_for("oauth2_portal.get_profile",
                            portal_name=l_pname,
                            _external=True))


@oauth2_portal.route("/oauth2/<portal_name>/profile/", methods=["GET"])
def get_profile(portal_name=None):
    if not portal_name:
        # TODO: Error Page with message
        return redirect(url_for("security.login", _external=True))

    l_pname, u_pname = get_lower_upper_portal_name(portal_name)
    LOG.debug("OAuth2 for %s" % l_pname)

    portal_config: PortalConfig = PortalConfig.query.filter_by(
        portal_name=l_pname).first()
    if not portal_config.client_id \
            or not portal_config.client_secret:
        return redirect(url_for("settings_page.on_settings_portal",
                                portal_name=l_pname, _external=True))

    current_user_id = None
    try:
        current_user_id = current_user.id
    except AttributeError:
        pass

    if not current_user_id and not portal_name == "orcid":
        return redirect(url_for("security.login", _external=True))

    LOG.debug("current user id: %s" % current_user_id)
    saved_portal = None
    if current_user_id:
        saved_portal = get_portal_from_db(
            user_id=current_user_id,
            portal_name=l_pname
        )

    LOG.debug("Saved Portal: %s" % saved_portal)

    scope = []
    if bool(portal_config.scope):
        scope = portal_config.scope.split()
    if len(scope) > 0:
        oauth = OAuth2Session(
            portal_config.client_id,
            token=session["%s_oauth_token" % l_pname],
            scope=portal_config.scope
        )
    else:
        oauth = OAuth2Session(
            portal_config.client_id,
            token=session["%s_oauth_token" % l_pname]
        )

    user_profile = get_user_profile(
        db_portal=saved_portal,
        oauth=oauth,
        portal_name=u_pname,
        token=session["%s_oauth_token" % l_pname]
    )

    if not user_profile.get("username"):
        LOG.debug("No username found from api or tokens.")
        user_profile["username"] = "username"
        user_profile["email"] = user_profile["username"]
        user_profile["full_name"] = "full name"
        # return redirect(url_for("security.login", _external=True))

    user, portal = get_save_user_profile(
        user_profile=user_profile,
        portal_name=portal_name,
        current_user_id=current_user_id,
        token=session.get("%s_oauth_token" % l_pname)
    )

    if not user and not portal:
        LOG.debug("Error creating/updating user profile!")
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
    LOG.debug("User: \n%s" % user)

    # get_events(username, user.get("html_url"), current_user.id)
    # return render_template("%s.html" % l_pname, profile=user_profile)
    return redirect(url_for("user_profile_page.on_user_profile",
                            _external=True))

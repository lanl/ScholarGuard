# -*- coding: utf-8 -*-
from flask import (Blueprint, request,
                   render_template, redirect, url_for, flash, Markup)
from flask_wtf import FlaskForm
from wtforms import StringField, validators, ValidationError
from researcher_pod import pod
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod.store.user import Portal
from researcher_pod.user.utils import (user_authorised_for_settings_page,
                                       get_owner)
from flask_security import current_user


app = pod.app
LOG = pod.log
db = pod.db

settings_page = Blueprint("settings_page",
                          __name__, template_folder="templates")


def required_for_auth_type_check(form, field):
    if not bool(field.data):
        raise ValidationError(f"{field.label} cannot be empty.")
    return


class PortalConfigForm(FlaskForm):

    # auth_type = QuerySelectField(query_factory=get_auth_types())
    client_id = StringField(validators=[validators.DataRequired()])
    client_secret = StringField(validators=[validators.DataRequired()])

    base_authorization_url = StringField(
        validators=[required_for_auth_type_check])
    access_token_url = StringField(validators=[required_for_auth_type_check])
    scope = StringField(validators=[required_for_auth_type_check])


@settings_page.route("/settings/", methods=["GET", "HEAD"])
def on_settings():
    if not user_authorised_for_settings_page():
        return redirect(url_for("security.login", _external=True))
    if get_owner():
        portal_names = [pc.portal_name for pc in PortalConfig.query.all()
                        if app.config.get(f"{pc.portal_name.upper()}_AUTH_TYPE") not in ["none"]]
    else:
        portal_names = [pc.portal_name for pc in PortalConfig.query.all()
                        if app.config.get(f"{pc.portal_name.upper()}_ALLOW_LOGIN")
                        and app.config.get(f"{pc.portal_name.upper()}_AUTH_TYPE") not in ["none"]]
    return render_template("settings.html",
                           portal_names=portal_names)


@settings_page.route("/settings/portal/<portal_name>/", methods=["GET", "POST"])
def on_settings_portal(portal_name=None):

    LOG.debug(f"user_auth: {user_authorised_for_settings_page()}")
    LOG.debug("auth type: %s" % app.config.get(f"{portal_name.upper()}_AUTH_TYPE"))
    if not user_authorised_for_settings_page():
        LOG.debug(f"redirecting to {url_for('security.login')}")
        return redirect(url_for("security.login", _external=True))

    if app.config.get(f"{portal_name.upper()}_AUTH_TYPE") in ["none"]:
        return redirect(url_for("settings_page.on_settings", _external=True))

    portal_configs = PortalConfig.query.\
        filter_by(portal_name=portal_name).first()
    if request.form:
        form = PortalConfigForm(request.form)
    else:
        form = PortalConfigForm(obj=portal_configs)
        if portal_configs.client_id:
            form.client_id.data = portal_configs.client_id.decode("utf8")
            form.client_secret.data = portal_configs.client_secret.decode("utf8")

    if not "%s_BASE_AUTHORIZATION_URL" % portal_name.upper() in app.config:
        del form.base_authorization_url
    if not "%s_ACCESS_TOKEN_URL" % portal_name.upper() in app.config:
        del form.access_token_url
    if not "%s_SCOPE" % portal_name.upper() in app.config:
        del form.scope

    LOG.debug(portal_configs.auth_type.name)

    if form.validate_on_submit():
        LOG.debug("POST data received and validated.")
        form.client_secret.data = form.client_secret.data.strip().encode("utf8")
        form.client_id.data = form.client_id.data.strip().encode("utf8")
        form.populate_obj(portal_configs)
        db.session.add(portal_configs)
        db.session.commit()
        if portal_configs.auth_type.name in ["oauth1", "oauth2"]:
            cid = ""
            try:
                cid = current_user.id
            except AttributeError:
                cid = ""
            if len(cid) > 0:
                flash(Markup(
                    "Settings for %s saved successfully. You may login to this portal and add it to your "
                    "pod in the 'Add More Portals to the Pod' section in the <a href=%s>User Profile Page</a>."
                    % (portal_name.title(), url_for("user_profile_page.on_user_profile", _external=True))
                ))
            else:
                flash(Markup(
                    "Settings for %s saved successfully. You may <a href=%s>Login</a>."
                    % (portal_name.title(), url_for("security.login", _external=True))
                ))
        elif portal_configs.auth_type.name in ["apikey_ts_hash"]:
            portal: Portal = Portal.query\
                .filter(Portal.user_id == current_user.id,
                        PortalConfig.portal_name == portal_name,
                        PortalConfig.id == Portal.portal_config_id).first()

        return redirect(url_for("settings_page.on_settings", _external=True))

    return render_template("settings_portal_config.html",
                           form=form,
                           portal_name=portal_name)

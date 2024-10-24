# -*- coding: utf-8 -*-

from flask import Blueprint, request, render_template, url_for, flash
from flask_security import login_required, current_user
from flask_wtf import FlaskForm
from wtforms import StringField, HiddenField,\
    FormField, FieldList, validators
from researcher_pod import pod
from researcher_pod.store.user import User

app = pod.app
LOG = pod.log
db = pod.db

user_profile_page = Blueprint("user_profile_page",
                              __name__, template_folder="templates")


class PortalForm(FlaskForm):
    portal_username = StringField(validators=[validators.DataRequired()])
    portal_user_id = StringField(validators=[validators.DataRequired()])
    portal_name = HiddenField()


class UserProfileForm(FlaskForm):
    full_name = StringField(validators=[validators.DataRequired()])
    portals = FieldList(FormField(PortalForm), validators=[
                        validators.Optional()])


@user_profile_page.route("/profile/", methods=["GET", "POST"])
@login_required
def on_user_profile():
    LOG.debug("current_user: %s" % current_user.id)

    user = User.query.filter_by(id=current_user.id).first()
    if request.form:
        user_form = UserProfileForm(request.form, obj=user)
    else:
        user_form = UserProfileForm(obj=user)
    portal_titles = {}
    for i, portal in enumerate(user_form.portals):
        index = i + 1
        for field in portal:
            if field.type == "HiddenField":
                portal_titles[index] = {}
                portal_titles[index]["title"] = portal.object_data.portal_config.portal_name
                if portal_titles[index]["title"] == "figshare":
                    portal_titles[index]["notes"] = "Portal User ID and Username are needed for the tracker to work. " \
                        "https://figshare.com/authors/<user_name>/<user_id>"
                elif portal_titles[index]["title"] == "stackoverflow":
                    portal_titles[index]["notes"] = "Portal User ID and Username are needed for the tracker to work. " \
                        "https://stackoverflow.com/users/<user_id>/<user_name>"
                elif portal_titles[index]["title"] in ["slideshare", "dblp"]:
                    portal_titles[index]["notes"] = "Portal User ID is optional."

    if request.method == "POST" and user_form.validate():
        error = False
        for portal in user_form.portals:
            if portal.portal_username == "username":
                flash("Error. Please enter a valid username for {}".format(
                    portal.object_data.portal_config.portal_name))
                error = True
        if not error:
            user_form.populate_obj(user)
            db.session.add(user)
            db.session.commit()

            flash("Profile Saved.")
    elif request.method == "POST" and not user_form.validate():
        flash("Error saving profile. ")

    avail_portals = {}
    pt = [p['title'].lower() for k, p in portal_titles.items()]
    LOG.debug(pt)
    for portal in app.config.get("PORTALS"):
        if portal in pt:
            continue

        auth_type = app.config.get("%s_AUTH_TYPE" % portal.upper())
        portal_url = None
        if auth_type == "oauth1":
            portal_url = url_for("oauth1_portal.get_request_token",
                                 portal_name=portal, _external=True)
        elif auth_type == "oauth2":
            portal_url = url_for("oauth2_portal.get_request_token",
                                 portal_name=portal, _external=True)

        if portal_url:
            avail_portals[portal] = portal_url

    return render_template("user_profile.html",
                           form=user_form,
                           portal_titles=portal_titles,
                           avail_portals=avail_portals)

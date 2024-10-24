# -*- coding: utf-8 -*-

from researcher_pod.store import db


class PortalConfig(db.Model):

    id = db.Column(db.Integer(), primary_key=True)

    portal_name = db.Column(db.String(255), unique=True)
    portal_url = db.Column(db.Text())

    auth_id = db.Column(db.Integer(), db.ForeignKey("auth_type.id"))
    auth_type = db.relationship("AuthType", backref="portal_config")
    client_id = db.Column(db.LargeBinary())
    client_secret = db.Column(db.LargeBinary())

    request_token_url = db.Column(db.Text())
    base_authorization_url = db.Column(db.Text())
    access_token_url = db.Column(db.Text())
    callback_url = db.Column(db.Text())
    scope = db.Column(db.Text())

    oauth_token = db.Column(db.LargeBinary())
    oauth_secret = db.Column(db.LargeBinary())


class AuthType(db.Model):
    id = db.Column(db.Integer(), primary_key=True)
    name = db.Column(db.String(255), unique=True, nullable=False)

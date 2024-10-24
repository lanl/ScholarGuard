# -*- coding: utf-8 -*-
from researcher_pod.store import db
from flask_security import UserMixin, RoleMixin
from sqlalchemy.orm import class_mapper, ColumnProperty
from flask_security import SQLAlchemyUserDatastore


roles_users = db.Table("roles_users",
                       db.Column("user_id", db.Integer(),
                                 db.ForeignKey("user.id")),
                       db.Column("role_id", db.Integer(),
                                 db.ForeignKey("role.id")),
                       )


class Role(db.Model, RoleMixin):
    id = db.Column(db.Integer(), primary_key=True)
    name = db.Column(db.String(80), unique=True)
    description = db.Column(db.String(255))


class User(db.Model, UserMixin):
    id = db.Column(db.String(255), primary_key=True)
    # flask_security expects email. cannot change this to any other name
    email = db.Column(db.String(255), unique=True)
    full_name = db.Column(db.String(255))
    password = db.Column(db.LargeBinary())
    profile_image = db.Column(db.String(255))

    confirmed_at = db.Column(db.DateTime())
    last_login_at = db.Column(db.DateTime())
    current_login_at = db.Column(db.DateTime())

    last_login_ip = db.Column(db.String(50))
    current_login_ip = db.Column(db.String(50))

    login_count = db.Column(db.Integer())
    active = db.Column(db.Boolean())

    roles = db.relationship("Role", secondary=roles_users,
                            backref=db.backref("users", lazy="dynamic"))
    portals = db.relationship("Portal", backref="users", lazy="dynamic")


class Portal(db.Model):
    id = db.Column(db.Integer(), primary_key=True)
    user_id = db.Column(db.String(255), db.ForeignKey("user.id"))
    portal_config_id = db.Column(db.Integer(),
                                 db.ForeignKey("portal_config.id"))
    portal_config = db.relationship("PortalConfig",
                                    backref=db.backref("portal_config",
                                                       uselist=False))

    portal_url = db.Column(db.String(250))
    portal_username = db.Column(db.String(255))
    portal_user_id = db.Column(db.String(255))
    portal_email = db.Column(db.String(255))

    last_sent = db.Column(db.DateTime())

    def columns(self):
        return [field.key for field in class_mapper(self.__class__).
                iterate_properties
                if isinstance(field, ColumnProperty)]


user_database = SQLAlchemyUserDatastore(
    db, User, Role)

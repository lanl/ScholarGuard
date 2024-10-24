# -*- coding: utf-8 -*-
from researcher_pod.store.portal_config import AuthType
from researcher_pod import pod

db = pod.db


def find_or_create_auth_type(atype=None):
    return find_auth_type(atype) or create_auth_type(atype)


def create_auth_type(atype):
    if isinstance(atype, str) and bool(atype):
        auth_type = AuthType()
        auth_type.name = atype
        db.session.add(auth_type)
        db.session.commit()


def find_auth_type(atype):
    return AuthType.query.filter_by(name=atype).first()


def get_auth_types():
    return AuthType.query.all()

# -*- coding: utf-8 -*-

from researcher_pod.store import db


class TrackerState(db.Model):

    id = db.Column(db.Integer(), primary_key=True)
    portal_id = db.Column(db.Integer(), db.ForeignKey("portal.id"))

    tracker_state = db.Column(db.LargeBinary())
    last_updated = db.Column(db.DateTime())
    last_status_code = db.Column(db.Integer())

    update_count = db.Column(db.Integer())

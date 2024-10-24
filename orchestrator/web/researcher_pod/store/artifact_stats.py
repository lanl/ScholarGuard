# -*- coding: utf-8 -*-
from researcher_pod.store import db


class ArtifactStats(db.Model):

    id = db.Column(db.Integer(), primary_key=True)

    portal_id = db.Column(db.Integer(), db.ForeignKey("portal.id"))

    data = db.Column(db.LargeBinary())
    data_type = db.Column(db.String(255), nullable=False)

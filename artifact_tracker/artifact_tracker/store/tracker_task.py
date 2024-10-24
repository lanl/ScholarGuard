from artifact_tracker import tracker_app

db = tracker_app.db


class TrackerTask(db.Model):
    """
    Used to maintain tracker task queue based on user_id and portal_name
    """

    actor_id = db.Column(db.String(255), primary_key=True)
    portal_name = db.Column(db.String(255), primary_key=True)

    created_at = db.Column(db.DateTime())

    last_updated = db.Column(db.DateTime())
    last_status_code = db.Column(db.Integer())

    update_count = db.Column(db.Integer())
    # Status of task
    completed = db.Column(db.Boolean(), default=False)

    # Third primary key, but can't be null. We treat portals with portal_urls
    # as batch portals - synchronous per portal_url
    # portal_url = db.Column(db.String(2000))

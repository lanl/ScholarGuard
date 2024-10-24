# -*- coding: utf-8 -*-

from abc import ABCMeta
from artifact_tracker import tracker_app
from artifact_tracker.store.tracker_task import TrackerTask
from datetime import datetime

LOG = tracker_app.log


class Tracker(metaclass=ABCMeta):

    def __init__(self,
                 portal_name: str=None,
                 users: list=None,
                 ldn_inbox_url: str=None,
                 event_base_url: str=None
                 ):
        self._users = users
        self._portal_name = portal_name
        self._portal = None
        self._ldn_inbox_url = ldn_inbox_url
        self._event_base_url = event_base_url

        self._set_portal()

    def _set_portal(self):
        """
        Set portal from config
        """
        self._portal = tracker_app.app.config.get("PORTALS", {}).\
            get(self.portal_name, {})

        if not self._portal:
            raise ValueError(
                'Tracker for "{}" does not exist.'.format(self.portal_name)
            )

    @property
    def users(self) -> list:
        return self._users

    @property
    def portal(self) -> dict:
        return self._portal

    @property
    def portal_name(self) -> str:
        return self._portal_name

    @property
    def ldn_inbox_url(self) -> str:
        return self._ldn_inbox_url

    @property
    def event_base_url(self) -> str:
        return self._event_base_url

    def get_events(self):
        """
        Get portal events for parameters
        """
        raise NotImplementedError

    def valid_params(self) -> bool:
        """
        Validate parameters per tracker
        """
        raise NotImplementedError

    def tracker_queued(self,
                       actor_id: str,
                       portal_url: str=None):
        """
        Check status of tracker for actor before starting
        tracker. Used to prevents duplicate requests for an
        actor for a certain portal.
        """
        with tracker_app.app.app_context():
            task: TrackerTask = TrackerTask.query.filter_by(
                actor_id=actor_id,
                portal_name=self.portal_name).first()
            if not task:
                return False
            if task.completed:
                return False

            return True

    def update_tracker_status(self,
                              actor_id,
                              status_code=None,
                              last_updated=None,
                              increment_count=True,
                              completed=False):
        """
        Method for updating queue status of tracker
        """
        with tracker_app.app.app_context():
            task = TrackerTask.query.filter_by(
                actor_id=actor_id,
                portal_name=self.portal_name).first()
            if not task:
                task = TrackerTask()
                task.created_at = datetime.now()
                task.actor_id = actor_id
                task.portal_name = self.portal_name
            if last_updated:
                task.last_updated = last_updated
            else:
                task.last_updated = datetime.now()
            task.last_status_code = status_code
            task.completed = completed
            if increment_count:
                task.update_count = task.update_count + 1 if \
                    (task.update_count) \
                    else 1

            tracker_app.db.session.add(task)
            tracker_app.db.session.commit()

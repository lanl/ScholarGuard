# -*- coding: utf-8 -*-

from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from sickle import Sickle, oaiexceptions
from datetime import datetime, timedelta

PORTAL_NAME = "figshare"
LOG = tracker_app.log


class FigshareTracker(Tracker):

    def get_events(self, **kwargs):
        LOG.debug(f"Executing {PORTAL_NAME} get events")
        if not self.users:
            LOG.debug("no users. exiting.")
            return False

        records_url = self.portal.get("event_urls", {}).get("oai_pmh_url")

        last_run = datetime.now()
        most_recent_datetime = self.get_most_recent_date(self.users)

        if most_recent_datetime:
            LOG.debug("start date value found in tracker state db entry.")
            from_datetime_str = most_recent_datetime.strftime(
                "%Y-%m-%dT%H:%M:%SZ")
            from_datetime = most_recent_datetime
            LOG.debug("earliest date allowed: {}".format(from_datetime_str))
        else:
            until = tracker_app.app.config.get("DISALLOW_EVENTS_BEFORE")
            if until:
                from_datetime = datetime.strptime(
                    until, "%Y-%m-%dT%H:%M:%SZ")
                from_datetime_str = from_datetime.strftime(
                    "%Y-%m-%dT%H:%M:%SZ")
            else:
                from_datetime = datetime.now() - timedelta(days=1)
                from_datetime_str = from_datetime.strftime(
                    "%Y-%m-%dT%H:%M:%SZ")

        LOG.debug("searching oai-pmh interface: %s" % records_url)
        try:
            sickle = Sickle(records_url)
            records = sickle.ListRecords(**{
                'metadataPrefix': 'oai_dc',
                'from': from_datetime_str
            })
            if records.oai_response.http_response.status_code != 200:
                LOG.debug(
                    "non-200 response code received. "
                    "updating tracker status and exiting.")
                self.complete_tracker(
                    records.oai_response.http_response.status_code)
                return False
        except oaiexceptions.NoRecordsMatch:
            LOG.debug("end of records in oai-pmh response")
            self.complete_tracker(
                records.oai_response.http_response.status_code)
            return False

        self.parse_records(
            records,
            from_datetime,
            last_run)

    def start_tracker(self):
        """
        Iterate through all user's and mark the tracker as not completed
        """
        for user in self.users:
            actor_id = user.get("id")
            self.update_tracker_status(
                actor_id=actor_id)

    def complete_tracker(self, status_code=None, last_tracked=None):
        """
        Iterate through all user's and mark the tracker as completed
        """
        for user in self.users:
            actor_id = user.get("id")
            self.update_tracker_status(
                actor_id=actor_id,
                status_code=status_code,
                last_updated=last_tracked,
                completed=True)

    def get_most_recent_date(self, users: list):
        """
        Iterate through all user's and find the most recent
        `last_tracked` date.
        """
        most_recent_date = None
        for user in users:
            last_tracked = user.get("lastTracked")
            from_datetime = datetime.strptime(
                last_tracked, "%Y-%m-%dT%H:%M:%SZ")
            if not most_recent_date:
                most_recent_date = from_datetime
            elif most_recent_date < from_datetime:
                most_recent_date = from_datetime

        return most_recent_date

    def extract_user_id(self, author_str):
        """
        Given an author string for an OAI-PMH record from Figshare,
        extract the user_id: Some O. Author (123456)

        :return: (str) Author user_id
        """
        start = author_str.find(" (")
        end = author_str.find(")")
        if start == -1 or end == -1:
            return ""
        return author_str[start + 2:end]

    def parse_records(self,
                      records,
                      from_datetime,
                      last_run):
        """
        Iterates through OAI-PMH XML response and check if any of the figshare
        portal users are included in the response. Then create the activity
        streams for this user.
        """
        for count, record in enumerate(records):
            date = ""
            try:
                date = datetime.strptime(
                    record.metadata.get("date")[0], "%Y-%m-%dT%H:%M:%SZ")
                if date < from_datetime:
                    LOG.debug("entry date earlier than allowed.")
                    break
            except Exception:
                LOG.debug("no date found")
                return
            LOG.debug("checking record {} - {}".format(count, date))

            authors = record.metadata["creator"]
            author_user_ids = set()
            # get author ids
            for author in authors:
                user_id = self.extract_user_id(author)
                if user_id:
                    author_user_ids.add(user_id)
            # check if author ids are in the database
            users_found = [
                x for x in self.users
                if x.get("userId", "") in author_user_ids]
            if users_found:
                act = self.make_as2_payload(record.metadata, users_found)
                post_to_ldn_inbox(
                    events=act,
                    from_datetime=from_datetime,
                    inbox_url=self.ldn_inbox_url)

        self.complete_tracker(
            records.oai_response.http_response.status_code,
            last_tracked=last_run)

    def make_as2_payload(self,
                         event,
                         users_found: list):
        as2_payload = template_as2(self.event_base_url,
                                   self.portal_name)
        oai_pmh_url = self.portal.get("event_urls", {}).\
            get("oai_pmh_url")
        as2_payload["activity"]["prov:used"].\
            append({"@id": oai_pmh_url})
        as2_payload["activity"]["prov:used"][0]["prov:used"].append(
            {"id": "https://github.com/mloesch/sickle"})

        for user in users_found:
            actor_id = user.get("id")
            portal_username = user.get("username")
            portal_user_id = user.get("userId")

            if not actor_id or not portal_user_id or not portal_username:
                LOG.debug(
                    """
                    Unable to format as2 message without one of the following
                    variables: actor_id, user_id, or username. skipping.
                    """)
                return False

            actor = {}
            actor["url"] = "https://figshare.com/authors/{}/{}"\
                .format(
                    portal_username, portal_user_id)
            actor["type"] = "Person"
            actor["id"] = actor_id
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = self.portal.get("portal_url")
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            pubtime = datetime.strptime(
                event.get("date")[0], "%Y-%m-%dT%H:%M:%SZ")
            pubtime = pubtime.strftime("%Y-%m-%dT%H:%M:%SZ")
            as2_payload["event"]["published"] = pubtime
            as2_payload["event"]["type"] = ["Create",
                                            "tracker:ArtifactCreation",
                                            "tracker:Tracker"]

            obj = {}
            items = []
            for link in event.get("relation", []):
                items.append({
                    "type": ["Link", "Document", "schema:Dataset"],
                    "href": link
                })
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = FigshareTracker(**kwargs)
    tracker.get_events()
